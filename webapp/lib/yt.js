/* Puente con YouTube Music para Life Music Web.

   El navegador no puede hablar directo con music.youtube.com (CORS), asi que
   estas funciones de Cloudflare Pages hacen la pregunta y devuelven solo lo
   que la web necesita: canciones con su titulo, artista, album, duracion y
   caratula. Es el mismo cliente WEB_REMIX que usa la app de Android para los
   metadatos (innertube/.../YouTubeClient.kt).

   El audio NO pasa por aqui: suena en el reproductor oficial de YouTube
   incrustado en la pagina. */

const VERSION = "1.20260213.01.00";
const NAVEGADOR =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

export const FILTRO_CANCIONES = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D";

/** Idioma y pais de quien pregunta, para que los textos de YouTube salgan en su idioma. */
export function regionDe(request) {
  const idioma = (request.headers.get("accept-language") || "es").split(/[,;-]/)[0].trim().toLowerCase();
  const pais = request.cf?.country || "CO";
  return { hl: /^[a-z]{2}$/.test(idioma) ? idioma : "es", gl: /^[A-Z]{2}$/.test(pais) ? pais : "CO" };
}

export async function yt(punto, cuerpo, { hl, gl }, consulta = {}) {
  const url = new URL(`https://music.youtube.com/youtubei/v1/${punto}`);
  url.searchParams.set("prettyPrint", "false");
  for (const [k, v] of Object.entries(consulta)) url.searchParams.set(k, v);
  const r = await fetch(url, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      origin: "https://music.youtube.com",
      referer: "https://music.youtube.com/",
      "user-agent": NAVEGADOR,
      "x-youtube-client-name": "67",
      "x-youtube-client-version": VERSION,
    },
    body: JSON.stringify({ context: { client: { clientName: "WEB_REMIX", clientVersion: VERSION, hl, gl } }, ...cuerpo }),
  });
  if (!r.ok) throw new Error(`YouTube respondio ${r.status}`);
  return r.json();
}

export function json(datos, { estado = 200, cache = 300 } = {}) {
  return new Response(JSON.stringify(datos), {
    status: estado,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": estado === 200 ? `public, max-age=${cache}` : "no-store",
    },
  });
}

export function fallo(e) {
  return json({ error: String(e?.message || e) }, { estado: 502 });
}

// ── Lectura de las respuestas ────────────────────────────────────────────────

const SEPARADOR = " • ";
const DURACION = /^\d{1,2}(:\d{2}){1,2}$/;

const tipoDePagina = (run) =>
  run?.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;

/** Parte los textos de una linea por « • »: [artistas] • [album] • [duracion]. */
function grupos(runs) {
  const salida = [[]];
  for (const r of runs || []) {
    if (r.text === SEPARADOR) salida.push([]);
    else salida[salida.length - 1].push(r);
  }
  return salida.filter((g) => g.length);
}

/** La caratula mas grande que haya, pedida al tamano justo. */
export function caratula(miniaturas, lado = 544) {
  const url = miniaturas?.[miniaturas.length - 1]?.url;
  if (!url) return null;
  if (/googleusercontent|ggpht/.test(url)) return url.replace(/=w\d+-h\d+[^/]*$/, `=w${lado}-h${lado}-l90-rj`);
  return url;
}

function artistasDe(gs) {
  const g = gs.find((x) => x.some((r) => tipoDePagina(r) === "MUSIC_PAGE_TYPE_ARTIST")) || gs[0] || [];
  const conPagina = g.find((r) => tipoDePagina(r) === "MUSIC_PAGE_TYPE_ARTIST");
  return {
    artista: g.map((r) => r.text).join("").trim(),
    artistaId: conPagina?.navigationEndpoint.browseEndpoint.browseId || null,
  };
}

function albumDe(gs) {
  for (const g of gs) {
    const r = g.find((x) => tipoDePagina(x) === "MUSIC_PAGE_TYPE_ALBUM");
    if (r) return { album: r.text, albumId: r.navigationEndpoint.browseEndpoint.browseId };
  }
  return { album: null, albumId: null };
}

function duracionDe(gs) {
  for (let i = gs.length - 1; i >= 0; i--) {
    const t = gs[i].map((r) => r.text).join("").trim();
    if (DURACION.test(t)) return t;
  }
  return null;
}

const explicita = (badges) =>
  (badges || []).some((b) => b.musicInlineBadgeRenderer?.icon?.iconType === "MUSIC_EXPLICIT_BADGE");

/** Una fila de resultados (musicResponsiveListItemRenderer) convertida en cancion. */
export function cancionDeFila(fila) {
  const columnas = (fila.flexColumns || []).map((c) => c.musicResponsiveListItemFlexColumnRenderer?.text?.runs || []);
  const primera = columnas[0]?.[0];
  const videoId = fila.playlistItemData?.videoId || primera?.navigationEndpoint?.watchEndpoint?.videoId;
  if (!videoId) return null;
  const gs = grupos(columnas[1]);
  const fijas = (fila.fixedColumns || []).flatMap((c) => c.musicResponsiveListItemFixedColumnRenderer?.text?.runs || []);
  return {
    id: videoId,
    titulo: (columnas[0] || []).map((r) => r.text).join(""),
    ...artistasDe(gs),
    ...albumDe(gs),
    duracion: duracionDe(gs) || duracionDe(grupos(fijas)),
    caratula: caratula(fila.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails),
    explicita: explicita(fila.badges),
  };
}

/** Un renglon de la cola de YouTube (playlistPanelVideoRenderer) convertido en cancion. */
export function cancionDeCola(v) {
  if (!v?.videoId) return null;
  const gs = grupos(v.longBylineText?.runs);
  return {
    id: v.videoId,
    titulo: (v.title?.runs || []).map((r) => r.text).join(""),
    ...artistasDe(gs),
    ...albumDe(gs),
    duracion: v.lengthText?.runs?.[0]?.text || null,
    caratula: caratula(v.thumbnail?.thumbnails),
    explicita: explicita(v.badges),
  };
}
