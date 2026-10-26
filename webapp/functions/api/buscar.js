// GET /api/buscar?q=texto                             → canciones (como siempre)
// GET /api/buscar?q=texto&tipo=todo                   → mejor resultado y las primeras canciones, artistas y álbumes
// GET /api/buscar?q=texto&tipo=albumes|artistas|listas → esa lista
// GET /api/buscar?mas=continuacion[&tipo=…]           → la página siguiente
import { yt, regionDe, json, fallo, conCache, cancionDeFila, elementoDe, mejorDe, estanteDe, FILTROS } from "../../lib/yt.js";

async function estante(q, mas, tipo, region) {
  const r = mas
    ? await yt("search", {}, region, { continuation: mas, ctoken: mas, type: "next" })
    : await yt("search", { query: q, params: decodeURIComponent(FILTROS[tipo]) }, region);
  const e = estanteDe(r, !!mas);
  return { items: e?.contents || [], mas: e?.continuations?.[0]?.nextContinuationData?.continuation || null };
}

const elementos = (e) => e.items.map(elementoDe).filter(Boolean);

export async function onRequestGet(context) {
  const url = new URL(context.request.url);
  const q = (url.searchParams.get("q") || "").trim().slice(0, 150);
  const mas = url.searchParams.get("mas");
  const tipo = url.searchParams.get("tipo") || "canciones";
  if (!q && !mas) return json({ canciones: [], elementos: [], mas: null });
  if (tipo !== "todo" && !FILTROS[tipo]) return json({ error: "Tipo desconocido" }, { estado: 400 });
  const region = regionDe(context.request);
  return conCache(context, region, async () => {
    try {
      if (tipo === "todo") {
        const [mejor, canciones, artistas, albumes] = await Promise.all([
          yt("search", { query: q }, region).then(mejorDe, () => null),
          estante(q, null, "canciones", region),
          estante(q, null, "artistas", region),
          estante(q, null, "albumes", region),
        ]);
        return json(
          {
            mejor,
            secciones: [
              { tipo: "canciones", elementos: elementos(canciones).slice(0, 4) },
              { tipo: "artistas", elementos: elementos(artistas).slice(0, 8) },
              { tipo: "albumes", elementos: elementos(albumes).slice(0, 8) },
            ],
          },
          { cache: 600 },
        );
      }
      const e = await estante(q, mas, tipo, region);
      if (tipo === "canciones") {
        const canciones = e.items
          .map((c) => c.musicResponsiveListItemRenderer && cancionDeFila(c.musicResponsiveListItemRenderer))
          .filter(Boolean);
        return json({ canciones, mas: e.mas }, { cache: 600 });
      }
      return json({ elementos: elementos(e), mas: e.mas }, { cache: 600 });
    } catch (e) {
      return fallo(e);
    }
  });
}
