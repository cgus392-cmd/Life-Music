// GET /api/lista?id=VL…|PL…|RD…|OLAK… → la lista con su cabecera y sus canciones (las primeras 100).
import { yt, regionDe, json, fallo, conCache, coleccionDe } from "../../lib/yt.js";

const ID = /^[\w-]{10,}$/;

export async function onRequestGet(context) {
  const id = new URL(context.request.url).searchParams.get("id") || "";
  if (!ID.test(id)) return json({ error: "Falta la lista" }, { estado: 400 });
  const browseId = id.startsWith("VL") ? id : `VL${id}`;
  const region = regionDe(context.request);
  return conCache(context, region, async () => {
    try {
      const lista = coleccionDe(await yt("browse", { browseId }, region), id);
      if (!lista.titulo) throw new Error("YouTube no devolvió la lista");
      return json(lista, { cache: 3600 });
    } catch (e) {
      return fallo(e);
    }
  });
}
