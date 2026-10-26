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
// Filtros de búsqueda por tipo (probados el 24-10-2026).
export const FILTROS = {
  canciones: FILTRO_CANCIONES,
  albumes: "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D",
  artistas: "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D",
  listas: "EgeKAQQoAEABagoQBBAKEAkQAxAF",
};

/** Guarda la respuesta en la caché de Cloudflare, por región, para no volver a preguntarle a YouTube.
    En local (sin caches) solo responde. */
export async function conCache(context, region, hacer) {
  const cache = globalThis.caches?.default;
  const url = context.request.url;
  const llave = new Request(`${url}${url.includes("?") ? "&" : "?"}_r=${region.gl}-${region.hl}`);
  const guardada = cache && (await cache.match(llave));
  if (guardada) return guardada;
  const respuesta = await hacer();
  if (cache && respuesta.status === 200) context.waitUntil(cache.put(llave, respuesta.clone()));
  return respuesta;
}

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

// ── Álbumes, artistas, listas y filas del Inicio ─────────────────────────────

const texto = (runs) => (runs || []).map((r) => r.text).join("");

const paginaDe = (nav) => nav?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;

const TIPOS = {
  MUSIC_PAGE_TYPE_ALBUM: "album",
  MUSIC_PAGE_TYPE_ARTIST: "artista",
  MUSIC_PAGE_TYPE_USER_CHANNEL: "artista",
  MUSIC_PAGE_TYPE_PLAYLIST: "lista",
};

/** Cualquier cosa que YouTube Music muestre en una fila o un carrusel: canción, álbum, artista o lista. */
export function elementoDe(item) {
  const dos = item?.musicTwoRowItemRenderer;
  if (dos) {
    const nav = dos.navigationEndpoint || {};
    const portada = caratula(dos.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails);
    const titulo = texto(dos.title?.runs);
    const subtitulo = texto(dos.subtitle?.runs);
    const videoId = nav.watchEndpoint?.videoId;
    if (videoId) {
      const cancion = {
        id: videoId,
        titulo,
        ...artistasDe(grupos(dos.subtitle?.runs)),
        album: null,
        albumId: null,
        duracion: null,
        caratula: portada,
        explicita: explicita(dos.subtitleBadges),
      };
      return { tipo: "cancion", id: videoId, titulo, subtitulo, caratula: portada, cancion };
    }
    const tipo = TIPOS[paginaDe(nav)];
    return tipo ? { tipo, id: nav.browseEndpoint.browseId, titulo, subtitulo, caratula: portada } : null;
  }
  const fila = item?.musicResponsiveListItemRenderer;
  if (!fila) return null;
  const cancion = cancionDeFila(fila);
  if (cancion) {
    const subtitulo = [cancion.artista, cancion.album].filter(Boolean).join(" · ");
    return { tipo: "cancion", id: cancion.id, titulo: cancion.titulo, subtitulo, caratula: cancion.caratula, cancion };
  }
  const tipo = TIPOS[paginaDe(fila.navigationEndpoint)];
  if (!tipo) return null;
  const columnas = (fila.flexColumns || []).map((c) => c.musicResponsiveListItemFlexColumnRenderer?.text?.runs || []);
  return {
    tipo,
    id: fila.navigationEndpoint.browseEndpoint.browseId,
    titulo: texto(columnas[0]),
    subtitulo: texto(columnas[1]),
    caratula: caratula(fila.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails),
  };
}

/** Las filas de una página (carruseles y estantes). Los videos y los botones de ánimo se saltan. */
export function filasDe(secciones) {
  return (secciones || [])
    .map((s) => {
      const c = s.musicCarouselShelfRenderer || s.musicShelfRenderer;
      if (!c) return null;
      const titulo = texto(c.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs) || texto(c.title?.runs);
      if (!titulo || /v[ií]deo/i.test(titulo)) return null;
      const elementos = (c.contents || []).map(elementoDe).filter(Boolean);
      if (!elementos.length) return null;
      const deCanciones = elementos.every((e) => e.tipo === "cancion") && !!c.contents[0].musicResponsiveListItemRenderer;
      return { titulo, forma: deCanciones ? "lista" : "carrusel", elementos };
    })
    .filter(Boolean);
}

/** Las secciones de una página de una sola columna (Inicio, Explorar, artista). */
export const seccionesDe = (r) => r?.contents?.singleColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];

/** Un álbum o una lista: cabecera (musicResponsiveHeaderRenderer) y sus canciones. */
export function coleccionDe(r, id) {
  const tc = r?.contents?.twoColumnBrowseResultsRenderer;
  const cab = tc?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents?.[0]?.musicResponsiveHeaderRenderer;
  const estante = tc?.secondaryContents?.sectionListRenderer?.contents?.[0];
  const items = (estante?.musicShelfRenderer || estante?.musicPlaylistShelfRenderer)?.contents || [];
  const titulo = texto(cab?.title?.runs);
  const portada = caratula(cab?.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails, 720);
  const autor = texto(cab?.straplineTextOne?.runs);
  const autorId = cab?.straplineTextOne?.runs?.[0]?.navigationEndpoint?.browseEndpoint?.browseId || null;
  const canciones = items
    .map((i) => i.musicResponsiveListItemRenderer && cancionDeFila(i.musicResponsiveListItemRenderer))
    .filter(Boolean)
    .map((c) => ({
      ...c,
      artista: c.artista || autor,
      artistaId: c.artistaId || autorId,
      album: c.album || (id.startsWith("MPRE") ? titulo : null),
      albumId: c.albumId || (id.startsWith("MPRE") ? id : null),
      caratula: c.caratula || portada,
    }));
  return {
    id,
    titulo,
    autor,
    autorId,
    subtitulo: texto(cab?.subtitle?.runs),
    detalle: texto(cab?.secondSubtitle?.runs),
    caratula: portada,
    canciones,
  };
}

/** El mejor resultado de una búsqueda sin filtro (musicCardShelfRenderer). */
export function mejorDe(r) {
  const secciones = r?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];
  const tarjeta = secciones.find((s) => s.musicCardShelfRenderer)?.musicCardShelfRenderer;
  if (!tarjeta) return null;
  const run = tarjeta.title?.runs?.[0];
  const nav = run?.navigationEndpoint || tarjeta.onTap || {};
  const portada = caratula(tarjeta.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails);
  const subtitulo = texto(tarjeta.subtitle?.runs);
  if (nav.watchEndpoint?.videoId) {
    const cancion = {
      id: nav.watchEndpoint.videoId,
      titulo: run.text,
      ...artistasDe(grupos(tarjeta.subtitle?.runs).slice(1)),
      album: null,
      albumId: null,
      duracion: duracionDe(grupos(tarjeta.subtitle?.runs)),
      caratula: portada,
      explicita: false,
    };
    return { tipo: "cancion", id: cancion.id, titulo: run.text, subtitulo, caratula: portada, cancion };
  }
  const tipo = TIPOS[paginaDe(nav)];
  return tipo ? { tipo, id: nav.browseEndpoint.browseId, titulo: run.text, subtitulo, caratula: portada } : null;
}

/** La primera lista de resultados de una búsqueda filtrada, con su continuación. */
export function estanteDe(r, continuacion = false) {
  if (continuacion) return r?.continuationContents?.musicShelfContinuation;
  const secciones = r?.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.sectionListRenderer?.contents || [];
  return secciones.find((s) => s.musicShelfRenderer)?.musicShelfRenderer;
}
