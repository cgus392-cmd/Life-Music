// GET /api/artista?id=UC… → el artista: foto, sus canciones más escuchadas y sus filas (álbumes, singles…).
import { yt, regionDe, json, fallo, conCache, filasDe, seccionesDe, cancionDeFila } from "../../lib/yt.js";

const ID = /^UC[\w-]{10,}$/;

export async function onRequestGet(context) {
  const id = new URL(context.request.url).searchParams.get("id") || "";
  if (!ID.test(id)) return json({ error: "Falta el artista" }, { estado: 400 });
  const region = regionDe(context.request);
  return conCache(context, region, async () => {
    try {
      const r = await yt("browse", { browseId: id }, region);
      const cab = r.header?.musicImmersiveHeaderRenderer || r.header?.musicVisualHeaderRenderer;
      const nombre = (cab?.title?.runs || []).map((x) => x.text).join("");
      if (!nombre) throw new Error("YouTube no devolvió el artista");
      const fotos = cab?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails || [];
      const secciones = seccionesDe(r);
      const estante = secciones.find((s) => s.musicShelfRenderer)?.musicShelfRenderer;
      const populares = (estante?.contents || [])
        .map((i) => i.musicResponsiveListItemRenderer && cancionDeFila(i.musicResponsiveListItemRenderer))
        .filter(Boolean)
        .map((c) => ({ ...c, artista: c.artista || nombre, artistaId: c.artistaId || id }));
      return json(
        {
          id,
          nombre,
          foto: fotos[fotos.length - 1]?.url || null,
          oyentes: (cab?.monthlyListenerCount?.runs || []).map((x) => x.text).join("") || null,
          populares,
          filas: filasDe(secciones.filter((s) => !s.musicShelfRenderer)),
        },
        { cache: 21600 },
      );
    } catch (e) {
      return fallo(e);
    }
  });
}
