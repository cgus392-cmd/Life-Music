// GET /api/album?id=MPREb_… → el álbum con su cabecera y sus canciones.
import { yt, regionDe, json, fallo, conCache, coleccionDe } from "../../lib/yt.js";

const ID = /^MPRE[\w-]+$/;

export async function onRequestGet(context) {
  const id = new URL(context.request.url).searchParams.get("id") || "";
  if (!ID.test(id)) return json({ error: "Falta el álbum" }, { estado: 400 });
  const region = regionDe(context.request);
  return conCache(context, region, async () => {
    try {
      const album = coleccionDe(await yt("browse", { browseId: id }, region), id);
      if (!album.titulo) throw new Error("YouTube no devolvió el álbum");
      return json(album, { cache: 21600 });
    } catch (e) {
      return fallo(e);
    }
  });
}
