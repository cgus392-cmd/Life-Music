// GET /api/sugerencias?q=texto → hasta 7 busquedas sugeridas
import { yt, regionDe, json, fallo } from "../../lib/yt.js";

export async function onRequestGet({ request }) {
  const q = (new URL(request.url).searchParams.get("q") || "").trim().slice(0, 100);
  if (!q) return json({ sugerencias: [] });
  try {
    const r = await yt("music/get_search_suggestions", { input: q }, regionDe(request));
    const sugerencias = (r.contents?.[0]?.searchSuggestionsSectionRenderer?.contents || [])
      .map((c) => (c.searchSuggestionRenderer?.suggestion?.runs || []).map((x) => x.text).join(""))
      .filter(Boolean)
      .slice(0, 7);
    return json({ sugerencias }, { cache: 3600 });
  } catch (e) {
    return fallo(e);
  }
}
