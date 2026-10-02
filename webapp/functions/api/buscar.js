// GET /api/buscar?q=texto            → primeras canciones
// GET /api/buscar?mas=continuacion   → la pagina siguiente
import { yt, regionDe, json, fallo, cancionDeFila, FILTRO_CANCIONES } from "../../lib/yt.js";

export async function onRequestGet({ request }) {
  const url = new URL(request.url);
  const q = (url.searchParams.get("q") || "").trim().slice(0, 150);
  const mas = url.searchParams.get("mas");
  if (!q && !mas) return json({ canciones: [], mas: null });
  try {
    const region = regionDe(request);
    let estante;
    if (mas) {
      const r = await yt("search", {}, region, { continuation: mas, ctoken: mas, type: "next" });
      estante = r.continuationContents?.musicShelfContinuation;
    } else {
      const r = await yt("search", { query: q, params: decodeURIComponent(FILTRO_CANCIONES) }, region);
      const secciones =
        r.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];
      estante = secciones.find((s) => s.musicShelfRenderer)?.musicShelfRenderer;
    }
    const canciones = (estante?.contents || [])
      .map((c) => c.musicResponsiveListItemRenderer && cancionDeFila(c.musicResponsiveListItemRenderer))
      .filter(Boolean);
    return json({ canciones, mas: estante?.continuations?.[0]?.nextContinuationData?.continuation || null });
  } catch (e) {
    return fallo(e);
  }
}
