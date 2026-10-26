// GET /api/inicio → las filas del Inicio para el país de quien entra: lo de la portada de YouTube Music,
// lo nuevo y las tendencias de Explorar, y los artistas principales de las listas del país.
import { yt, regionDe, json, fallo, conCache, filasDe, seccionesDe } from "../../lib/yt.js";

/** Media hora: el Inicio cambia poco y así se cuida el tope gratis de Cloudflare. */
const CACHE_S = 1800;

export async function onRequestGet(context) {
  const region = regionDe(context.request);
  return conCache(context, region, async () => {
    try {
      const respuestas = await Promise.allSettled([
        yt("browse", { browseId: "FEmusic_home" }, region),
        yt("browse", { browseId: "FEmusic_explore" }, region),
        yt("browse", { browseId: "FEmusic_charts", formData: { selectedValues: [region.gl] } }, region),
      ]);
      const filas = respuestas.flatMap((r) => (r.status === "fulfilled" ? filasDe(seccionesDe(r.value)) : []));
      if (!filas.length) throw new Error("YouTube no devolvió filas");
      return json({ filas, pais: region.gl }, { cache: CACHE_S });
    } catch (e) {
      return fallo(e);
    }
  });
}
