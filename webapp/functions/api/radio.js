// GET /api/radio?v=videoId           → la cancion y su radio (unas 50)
// GET /api/radio?mas=continuacion    → mas canciones para la misma radio
import { yt, regionDe, json, fallo, cancionDeCola } from "../../lib/yt.js";

const ID = /^[\w-]{11}$/;

export async function onRequestGet({ request }) {
  const url = new URL(request.url);
  const v = url.searchParams.get("v");
  const mas = url.searchParams.get("mas");
  if (!mas && !ID.test(v || "")) return json({ error: "Falta la cancion" }, { estado: 400 });
  try {
    const region = regionDe(request);
    let panel;
    if (mas) {
      const r = await yt("next", { continuation: mas }, region);
      panel = r.continuationContents?.playlistPanelContinuation;
    } else {
      const r = await yt("next", { videoId: v, playlistId: `RDAMVM${v}`, isAudioOnly: true }, region);
      const pestanas =
        r.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs || [];
      panel = pestanas[0]?.tabRenderer?.content?.musicQueueRenderer?.content?.playlistPanelRenderer;
    }
    const canciones = (panel?.contents || [])
      .map((c) => cancionDeCola(c.playlistPanelVideoRenderer))
      .filter(Boolean);
    const sigue = panel?.continuations?.[0];
    return json(
      { canciones, mas: (sigue?.nextRadioContinuationData || sigue?.nextContinuationData)?.continuation || null },
      { cache: 120 },
    );
  } catch (e) {
    return fallo(e);
  }
}
