/* Letras sincronizadas (plan 0.4, punto 4.1), con los proveedores de la app Android que dejan que una página les
   pregunte: YouLyPlus (palabra por palabra), LrcLib y Unison (por línea). Todo sale directo del navegador: no pasa
   por nuestro servidor ni gasta su cupo. Los demás de Android (SimpMusic, PaxSenix, Better Lyrics, KuGou) no se
   dejan usar desde una página; ver la tabla del plan. */
import type { Cancion } from "./api";

/** Una palabra (o sílaba) con su tiempo. «sintetica»: el proveedor solo dio la línea y el tiempo se repartió. */
export type Palabra = { inicio: number; fin: number; texto: string; sintetica?: boolean };
/** Quién canta la línea: la voz principal (izquierda), la segunda voz de un dueto (derecha) o el grupo (centro). */
export type Voz = "v1" | "v2" | "grupo";
export type Linea = { inicio: number; fin: number; texto: string; palabras: Palabra[]; coro?: Palabra[]; voz: Voz };
export type Letra = {
  lineas: Linea[];
  sincronizada: boolean;
  fuente: string;
  compositores?: string[];
  /** Lo que dice el proveedor de la versión que encontró: el título y su duración (s). */
  titulo?: string;
  duracion?: number;
  /** Por qué se muestra sin sincronizar, si es de otra versión. */
  nota?: string;
};

/** Los servidores de YouLyPlus, en orden. Con «Despacito», binimum trajo la de Luis Fonsi y prjktla la versión china
    de JJ Lin: por eso va primero binimum, y por eso se revisa que la letra sea creíble. */
const YOULYPLUS = ["https://lyricsplus.binimum.org", "https://lyricsplus.prjktla.my.id"];
/** Lo que se espera a cada proveedor antes de pasar al siguiente (ms). */
const ESPERA_MS = 6000;

const guardadas = new Map<string, Promise<Letra | null>>();

/** La letra de una canción, o null si ninguno la tiene. Se pide una sola vez por canción. */
export function buscarLetra(c: Cancion, duracion: number): Promise<Letra | null> {
  let letra = guardadas.get(c.id);
  if (!letra) {
    letra = buscar(c, duracion).catch(() => null);
    guardadas.set(c.id, letra);
  }
  return letra;
}

async function buscar(c: Cancion, duracion: number): Promise<Letra | null> {
  const titulo = limpiarTitulo(c.titulo);
  const artista = primerArtista(c.artista);
  // YouLyPlus elige la versión por la duración. La de un video oficial incluye su intro, y con ella puede elegir
  // otra versión («Despacito (Live)» con los 4:40 del video): por eso se pregunta también sin duración, que da la
  // del álbum.
  const [binimum, prjktla] = YOULYPLUS;
  const intentos = [
    () => deYouLyPlus(binimum, titulo, artista, c.album, duracion),
    () => deYouLyPlus(binimum, titulo, artista, c.album, 0),
    () => deYouLyPlus(prjktla, titulo, artista, c.album, duracion),
    () => deLrcLib(titulo, artista, duracion),
    () => deUnison(c.id, titulo, artista, duracion),
  ];
  // Gana la primera sincronizada que sea creíble y de la misma duración; las demás quedan de reserva para leer.
  let reserva: Letra | null = null;
  for (const intento of intentos) {
    const cruda = await intento().catch(() => null);
    const letra = cruda && sinCreditos(cruda, titulo, artista);
    if (!letra || !creible(letra, c, duracion)) continue;
    if (letra.sincronizada && mismaDuracion(letra, duracion)) return repartirPalabras(letra);
    reserva ??= paraLeer(letra);
  }
  return reserva;
}

/** Cuánto puede diferir la duración de la versión de la letra de la canción que suena (s). */
const MARGEN_DURACION_S = 8;
/** Sin la duración de la versión, cuánto puede durar como mucho la música después de la última línea (s). */
const FINAL_MAXIMO_S = 45;

/** Si el proveedor dice cuánto dura su versión, debe parecerse a la que suena. Si no lo dice (el servidor prjktla),
    la última línea debe terminar cerca del final: en un video oficial con intro, la letra del álbum acaba más de un
    minuto antes. En los dos casos, si no cuadra, la letra iría corrida. */
function mismaDuracion(l: Letra, duracion: number) {
  if (!duracion) return true;
  if (l.duracion) return Math.abs(l.duracion - duracion) <= MARGEN_DURACION_S;
  const ultima = l.lineas[l.lineas.length - 1];
  return duracion - ultima.fin <= FINAL_MAXIMO_S;
}

/** Algunos proveedores (los que sacan la letra de QQ Music) meten al principio una línea «Título - Artista (…)» y
    créditos en chino («作词：…»). No son letra: se quitan. */
const CREDITO_CHINO = /^\s*(作词|作曲|编曲|词|曲|制作人?|监制|混音|和声|录音|吉他|出品)\s*[:：]/;

function sinCreditos(l: Letra, titulo: string, artista: string): Letra {
  const t = titulo.toLowerCase();
  const a = artista.toLowerCase();
  const lineas = l.lineas.filter((x, i) => {
    const texto = x.texto.toLowerCase();
    if (CREDITO_CHINO.test(x.texto)) return false;
    return !(i < 3 && texto.includes(t) && (texto.includes(" - ") || texto.includes(a)));
  });
  return lineas.length === l.lineas.length ? l : { ...l, lineas };
}

/** Una letra de otra versión, para leer sin tiempos: sincronizada iría adelantada o atrasada. */
function paraLeer(l: Letra): Letra {
  if (!l.sincronizada) return l;
  return {
    ...l,
    sincronizada: false,
    nota: "Esta letra es de otra versión de la canción y no va al ritmo de esta.",
    lineas: l.lineas.map((x) => ({ ...x, inicio: 0, fin: 0, palabras: [], coro: undefined })),
  };
}

/** Cuánto puede tardar el barrido de una línea que vino sin palabras (s): si después viene un silencio largo, la
    última palabra no se queda arrastrándose. */
const BARRIDO_MAXIMO_S = 8;

/** Como el estilo Apple V2 de Android: si la letra vino solo por línea, el tiempo de cada línea se reparte entre
    sus palabras según cuántas letras tienen (en el 95 % del tiempo), para que también se barra palabra por palabra. */
function repartirPalabras(letra: Letra): Letra {
  for (const l of letra.lineas) {
    if (l.palabras.length) continue;
    const trozos = l.texto.split(/(?<=\s)/).filter((p) => p.trim());
    const total = trozos.reduce((n, p) => n + p.length, 0) || 1;
    const dura = Math.min(BARRIDO_MAXIMO_S, Math.max(0.3, (l.fin - l.inicio) * 0.95));
    let t = l.inicio;
    l.palabras = trozos.map((texto) => {
      const inicio = t;
      t += (dura * texto.length) / total;
      return { inicio, fin: t, texto, sintetica: true };
    });
  }
  return letra;
}

// ── Cada proveedor ────────────────────────────────────────────────────────────────────────────────────────────

const pedir = (url: string) => fetch(url, { signal: AbortSignal.timeout(ESPERA_MS) });

type SilabaYLP = { time: number; duration: number; text: string; isBackground?: boolean };
type LineaYLP = { time: number; duration: number; text: string; syllabus?: SilabaYLP[]; element?: { singer?: string } };

const silaba = (s: SilabaYLP): Palabra => ({ inicio: s.time / 1000, fin: (s.time + s.duration) / 1000, texto: s.text });

/** v1 es la voz principal y v2 la segunda de un dueto; v1000 es el grupo (en Android, al centro). */
const vozDe = (singer?: string): Voz => (singer === "v2" ? "v2" : singer === "v1000" ? "grupo" : "v1");

async function deYouLyPlus(servidor: string, titulo: string, artista: string, album: string | null, duracion: number) {
  const q = new URLSearchParams({ title: titulo, artist: artista });
  if (duracion > 0) q.set("duration", String(Math.round(duracion)));
  if (album) q.set("album", album);
  const r = await pedir(`${servidor}/v2/lyrics/get?${q}`);
  if (!r.ok) return null;
  const d = (await r.json()) as {
    type?: string;
    lyrics?: LineaYLP[];
    metadata?: { songWriters?: string[]; title?: string; totalDuration?: string };
  };
  const porPalabra = d.type === "Word";
  const lineas: Linea[] = (d.lyrics ?? [])
    .filter((l) => l.text?.trim())
    .map((l) => {
      const silabas = porPalabra ? (l.syllabus ?? []) : [];
      // Los coros vienen dentro de la línea, marcados sílaba por sílaba: van aparte, más chicos y debajo.
      const principal = espaciosDe(silabas.filter((s) => !s.isBackground).map(silaba), l.text);
      const coro = espaciosDe(silabas.filter((s) => s.isBackground).map(silaba), l.text);
      return {
        inicio: l.time / 1000,
        fin: (l.time + l.duration) / 1000,
        texto: principal.length ? principal.map((p) => p.texto).join("").trim() : l.text.trim(),
        palabras: principal,
        coro: coro.length ? coro : undefined,
        voz: vozDe(l.element?.singer),
      };
    });
  return {
    lineas,
    sincronizada: lineas.some((l) => l.inicio > 0),
    fuente: "YouLyPlus",
    compositores: d.metadata?.songWriters?.filter(Boolean),
    titulo: d.metadata?.title,
    duracion: segundosDe(d.metadata?.totalDuration),
  };
}

/** Algunas versiones parten las palabras con tilde en pedazos y ponen espacios donde no van («mir» «á » «ndote»).
    Los espacios se toman de la línea completa, que sí viene bien escrita. */
function espaciosDe(silabas: Palabra[], linea: string): Palabra[] {
  let desde = 0;
  return silabas.map((s) => {
    const limpia = s.texto.trim();
    const i = linea.indexOf(limpia, desde);
    if (!limpia || i < 0) return s;
    desde = i + limpia.length;
    const sigue = linea[desde];
    return { ...s, texto: limpia + (sigue === undefined || /\s/.test(sigue) ? " " : "") };
  });
}

/** «3:49.387» o «00:04:32.230» en segundos. */
function segundosDe(texto?: string) {
  if (!texto) return undefined;
  const s = texto.split(":").reduce((n, parte) => n * 60 + Number(parte), 0);
  return Number.isFinite(s) && s > 0 ? s : undefined;
}

type ResultadoLrcLib = { duration: number; syncedLyrics: string | null; plainLyrics: string | null };

async function deLrcLib(titulo: string, artista: string, duracion: number) {
  const r = await pedir(`https://lrclib.net/api/search?${new URLSearchParams({ track_name: titulo, artist_name: artista })}`);
  if (!r.ok) return null;
  const lista = (await r.json()) as ResultadoLrcLib[];
  // Primero las sincronizadas, y entre ellas la de duración más parecida. Si ninguna dura lo mismo, la más parecida
  // sirve para leer (buscar() la deja sin tiempos).
  const mejor = lista
    .filter((x) => x.syncedLyrics || x.plainLyrics)
    .sort((a, b) => Number(!!b.syncedLyrics) - Number(!!a.syncedLyrics) || Math.abs(a.duration - duracion) - Math.abs(b.duration - duracion))
    .sort((a, b) => Number(mismaDuracionLrc(b, duracion)) - Number(mismaDuracionLrc(a, duracion)))[0];
  if (!mejor) return null;
  return mejor.syncedLyrics
    ? { lineas: deLrc(mejor.syncedLyrics, duracion), sincronizada: true, fuente: "LrcLib", duracion: mejor.duration }
    : { lineas: plana(mejor.plainLyrics ?? ""), sincronizada: false, fuente: "LrcLib", duracion: mejor.duration };
}

const mismaDuracionLrc = (x: ResultadoLrcLib, duracion: number) => !duracion || Math.abs(x.duration - duracion) <= MARGEN_DURACION_S;

async function deUnison(id: string, titulo: string, artista: string, duracion: number) {
  const q = new URLSearchParams({ song: titulo, artist: artista });
  if (duracion > 0) q.set("duration", String(Math.round(duracion)));
  for (const url of [`https://unison.boidu.dev/lyrics?v=${id}`, `https://unison.boidu.dev/lyrics?${q}`]) {
    const r = await pedir(url);
    if (!r.ok) continue;
    const d = (await r.json()) as { data?: { lyrics?: string } };
    const texto = d.data?.lyrics?.trim();
    // Solo LRC o texto plano; otros formatos (TTML) se saltan.
    if (!texto || texto.startsWith("<")) continue;
    return /\[\d+:\d+/.test(texto)
      ? { lineas: deLrc(texto, duracion), sincronizada: true, fuente: "Unison" }
      : { lineas: plana(texto), sincronizada: false, fuente: "Unison" };
  }
  return null;
}

// ── Ayudas ────────────────────────────────────────────────────────────────────────────────────────────────────

/** Una letra en formato LRC ([mm:ss.xx] texto) convertida en líneas con su inicio y su fin. */
function deLrc(lrc: string, duracion: number): Linea[] {
  const todas: Linea[] = [];
  for (const fila of lrc.split(/\r?\n/)) {
    const marcas = [...fila.matchAll(/\[(\d+):(\d+(?:\.\d+)?)\]/g)];
    const texto = fila.replace(/\[[^\]]*\]/g, "").trim();
    for (const m of marcas) todas.push({ inicio: Number(m[1]) * 60 + Number(m[2]), fin: 0, texto, palabras: [], voz: "v1" });
  }
  todas.sort((a, b) => a.inicio - b.inicio);
  todas.forEach((l, i) => (l.fin = todas[i + 1]?.inicio ?? (duracion > l.inicio ? duracion : l.inicio + 5)));
  // Las líneas vacías marcan silencios: ya dieron su fin a la anterior y no se muestran.
  return todas.filter((l) => l.texto);
}

const plana = (texto: string): Linea[] =>
  texto
    .split(/\r?\n/)
    .map((t) => t.trim())
    .filter(Boolean)
    .map((t): Linea => ({ inicio: 0, fin: 0, texto: t, palabras: [], voz: "v1" }));

/** Sin «(Official Video)», «(Visualizer)», «feat. …» y compañía, que confunden a los proveedores. */
const limpiarTitulo = (t: string) =>
  t
    .replace(/\s*[([][^)\]]*(?:official|oficial|video|v[ií]deo|visuali[sz]er|lyric|letra|audio|en vivo|live|remaster|feat\.?|ft\.?)[^)\]]*[)\]]/gi, "")
    .replace(/\s+(?:feat\.?|ft\.?)\s.*$/i, "")
    .trim();

/** El primer artista de «Køddy G, THE FAVORITES y Fharid On the Beat». */
const primerArtista = (a: string) => a.split(/\s*(?:,|&|\s+y\s+|\s+x\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*/i)[0].trim();

const ESCRITURA_ASIATICA = /[぀-ヿ㐀-鿿가-힯]/;

/** Las marcas de otra versión. Si el proveedor encontró «Despacito (Live)» y suena «Despacito», es otra letra. */
const VERSIONES = /\b(live|en vivo|remix|acoustic|ac[uú]stic[oa]|unplugged|versi[oó]n|version|edit|instrumental|karaoke|sped up|slowed|cover|demo)\b/i;

const otraVersion = (deLaLetra: string | undefined, deLaCancion: string) =>
  !!deLaLetra && VERSIONES.test(deLaLetra) && !VERSIONES.test(deLaCancion);

/** Descarta lo que claramente es de otra canción: casi toda en otra escritura (la «Despacito» de JJ Lin) o con
    líneas que empiezan mucho después de que la canción terminó. */
function creible(l: Letra, c: Cancion, duracion: number) {
  if (!l.lineas.length) return false;
  if (otraVersion(l.titulo, c.titulo)) return false;
  const texto = l.lineas.map((x) => x.texto).join("");
  const asiaticas = [...texto].filter((ch) => ESCRITURA_ASIATICA.test(ch)).length;
  if (!ESCRITURA_ASIATICA.test(c.titulo + c.artista) && asiaticas > texto.length * 0.2) return false;
  if (l.sincronizada && duracion > 0 && l.lineas[l.lineas.length - 1].inicio > duracion + 15) return false;
  return true;
}
