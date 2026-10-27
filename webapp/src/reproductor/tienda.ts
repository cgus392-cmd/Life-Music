/* El estado del reproductor: la cola, la canción actual y lo que hace el motor. */
import { create } from "zustand";
import { cambiarDeReproductor, iniciarMotor, motor, mostrarMotor, relevo } from "./youtube";
import { radio, radioMas, segundos, type Cancion } from "../datos/api";
import { colorDe, CORAL } from "../datos/color";
import { usarBiblioteca } from "../datos/biblioteca";
import { usarAjustes } from "../datos/ajustes";
import { puedeMezclar } from "../datos/dispositivo";
import { activarSesion, actualizarSesion } from "./sesion";
import { activarAtajos } from "./atajos";
import { caratulaA, respaldoDe } from "../componentes/Caratula";

// Estados del IFrame API de YouTube.
const TERMINO = 0;
const SONANDO = 1;
const PAUSADO = 2;
const CARGANDO = 3;

/** Si quedan estas canciones o menos en la cola, se pide más radio. */
const COLCHON = 3;
/** Diferencia de duración (s) a partir de la cual se asume que suena un anuncio. */
const TOLERANCIA_ANUNCIO = 3;
/** Cuánto se queda a la vista el aviso de una canción que falló (ms). */
const AVISO_MS = 7000;
/** Cuánto dura el Automix: los últimos segundos de una canción se funden con los primeros de la siguiente. */
const FUNDIDO_S = 6;
/** Las canciones más cortas que esto no se mezclan (intros, interludios). */
const MINIMO_MEZCLA_S = 45;

export type Repetir = "no" | "todas" | "una";

type Estado = {
  cola: Cancion[];
  indice: number;
  /** Nombre de lo que armó la cola («Radio de …», un álbum, una lista). */
  origen: string | null;
  sonando: boolean;
  cargando: boolean;
  tiempo: number;
  duracion: number;
  volumen: number;
  silencio: boolean;
  aleatorio: boolean;
  repetir: Repetir;
  /** El color de la canción que suena, para la cabeza de la nota. */
  color: string;
  anuncio: boolean;
  /** Si el Automix está fundiendo esta canción con la siguiente (se avisa en «Ahora suena»). */
  mezclando: boolean;
  visibleSiempre: boolean;
  aviso: string | null;
  radioSigue: string | null;
  pidiendoRadio: boolean;

  tocar: (c: Cancion, lista?: Cancion[], origen?: string) => void;
  irA: (i: number) => void;
  siguiente: () => void;
  anterior: () => void;
  alternar: () => void;
  buscarEn: (s: number) => void;
  fijarVolumen: (v: number) => void;
  alternarSilencio: () => void;
  alternarAleatorio: () => void;
  cambiarRepetir: () => void;
  reproducirDespues: (c: Cancion) => void;
  agregarACola: (c: Cancion) => void;
  quitar: (i: number) => void;
  mover: (de: number, a: number) => void;
};

/** El orden en que llegaron las canciones, para deshacer el aleatorio. */
let ordenOriginal: string[] = [];

function barajar<T>(lista: T[]): T[] {
  const l = [...lista];
  for (let i = l.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [l[i], l[j]] = [l[j], l[i]];
  }
  return l;
}

export const usarReproductor = create<Estado>()((set, get) => ({
  cola: [],
  indice: -1,
  origen: null,
  sonando: false,
  cargando: false,
  tiempo: 0,
  duracion: 0,
  volumen: 80,
  silencio: false,
  aleatorio: false,
  repetir: "no",
  color: CORAL,
  anuncio: false,
  mezclando: false,
  visibleSiempre: false,
  aviso: null,
  radioSigue: null,
  pidiendoRadio: false,

  tocar: (c, lista, origen) => {
    // Si alguien usa la página del modo demo para oír música de verdad, el reproductor deja de estar quieto.
    congelado = false;
    cancelarMezcla();
    if (lista?.length) {
      const indice = Math.max(0, lista.findIndex((x) => x.id === c.id));
      ordenOriginal = lista.map((x) => x.id);
      const cola = get().aleatorio ? [...lista.slice(0, indice + 1), ...barajar(lista.slice(indice + 1))] : lista;
      set({ cola, indice, radioSigue: null, origen: origen ?? null });
    } else {
      ordenOriginal = [c.id];
      set({ cola: [c], indice: 0, radioSigue: null, origen: origen ?? `Radio de «${c.titulo}»` });
      pedirRadio(c.id);
    }
    reproducirActual();
  },

  irA: (i) => {
    cancelarMezcla();
    if (i < 0 || i >= get().cola.length) return;
    set({ indice: i });
    reproducirActual();
  },

  siguiente: () => {
    const { indice, cola, repetir } = get();
    if (indice < cola.length - 1) get().irA(indice + 1);
    else if (repetir === "todas" && cola.length) get().irA(0);
    else {
      motor.pausar();
      set({ sonando: false });
    }
  },

  anterior: () => {
    cancelarMezcla();
    const { tiempo, indice } = get();
    if (tiempo > 3 || indice === 0) get().buscarEn(0);
    else get().irA(indice - 1);
  },

  alternar: () => {
    cancelarMezcla();
    const { sonando, indice } = get();
    if (indice < 0) return;
    if (sonando) motor.pausar();
    else motor.reanudar();
  },

  buscarEn: (s) => {
    cancelarMezcla();
    motor.ir(s);
    set({ tiempo: s });
  },

  fijarVolumen: (v) => {
    motor.volumen(v);
    set({ volumen: v, silencio: v === 0 });
  },

  alternarSilencio: () => {
    const { silencio, volumen } = get();
    motor.volumen(silencio ? volumen || 80 : 0);
    set({ silencio: !silencio });
  },

  alternarAleatorio: () => {
    const { aleatorio, cola, indice } = get();
    const antes = cola.slice(0, indice + 1);
    const despues = cola.slice(indice + 1);
    const posicion = (c: Cancion) => {
      const p = ordenOriginal.indexOf(c.id);
      return p < 0 ? Infinity : p;
    };
    set({
      aleatorio: !aleatorio,
      cola: [...antes, ...(aleatorio ? [...despues].sort((a, b) => posicion(a) - posicion(b)) : barajar(despues))],
    });
  },

  cambiarRepetir: () => {
    const siguiente: Record<Repetir, Repetir> = { no: "todas", todas: "una", una: "no" };
    set({ repetir: siguiente[get().repetir] });
  },

  reproducirDespues: (c) => {
    const { cola, indice } = get();
    const resto = cola.filter((x, i) => i <= indice || x.id !== c.id);
    resto.splice(Math.min(indice, resto.length - 1) + 1, 0, c);
    set({ cola: resto });
    if (indice < 0) get().tocar(c);
  },

  agregarACola: (c) => {
    const { cola, indice } = get();
    if (indice < 0) return get().tocar(c);
    set({ cola: [...cola.filter((x, i) => i <= indice || x.id !== c.id), c] });
  },

  quitar: (i) => {
    const { cola, indice } = get();
    if (i <= indice || i >= cola.length) return;
    set({ cola: cola.filter((_, j) => j !== i) });
  },

  mover: (de, a) => {
    const { cola, indice } = get();
    if (de <= indice || a <= indice || de >= cola.length || a >= cola.length) return;
    const nueva = [...cola];
    const [c] = nueva.splice(de, 1);
    nueva.splice(a, 0, c);
    set({ cola: nueva });
  },
}));

const estado = () => usarReproductor.getState();
const fijar = usarReproductor.setState;

function actual(): Cancion | undefined {
  const { cola, indice } = estado();
  return cola[indice];
}

function reproducirActual() {
  const c = actual();
  if (!c) return;
  fijar({ cargando: true, tiempo: 0, duracion: segundos(c.duracion) ?? 0, anuncio: false });
  motor.cargar(c.id);
  alEmpezar(c);
}

/** Lo que pasa cada vez que empieza una canción, sea por un toque o por el Automix. */
function alEmpezar(c: Cancion) {
  actualizarSesion(c);
  usarBiblioteca.getState().anotarEscuchada(c);
  colorDe(c.id).then((color) => {
    if (actual()?.id !== c.id) return;
    fijar({ color });
    document.documentElement.style.setProperty("--cancion", color);
  });
  const { cola, indice } = estado();
  if (cola.length - 1 - indice <= COLCHON) pedirMasRadio();
  if (!c.albumId || !c.artistaId) completarDatos(c);
  precargarSiguiente();
}

/** Las carátulas de la siguiente canción se piden de antemano (en los tamaños de «Ahora suena» y del mini). Así,
    al pasar, la nueva aparece de una y se funde junto con el título, sin quedarse la vieja esperando a que cargue. */
function precargarSiguiente() {
  const { cola, indice } = estado();
  const sig = cola[indice + 1];
  if (!sig) return;
  for (const url of [caratulaA(sig, 1080), caratulaA(sig, 128), sig.caratula ?? respaldoDe(sig.id)]) {
    const img = new Image();
    img.referrerPolicy = "no-referrer";
    img.src = url;
  }
}

/** Las de la cola que llegaron sin álbum o sin artista, completadas con lo que trae la radio de YouTube. */
function completarCon(cola: Cancion[], canciones: Cancion[]) {
  const porId = new Map(canciones.map((c) => [c.id, c]));
  return cola.map((c) => {
    const otra = porId.get(c.id);
    if (!otra || (c.albumId && c.artistaId)) return c;
    return { ...c, album: c.album ?? otra.album, albumId: c.albumId ?? otra.albumId, artistaId: c.artistaId ?? otra.artistaId };
  });
}

/** Las canciones de «Selecciones rápidas» del Inicio llegan sin álbum, y sin él el título de «Ahora suena» no
    lleva a ninguna parte. La radio de la canción sí lo trae (y el navegador la guarda dos minutos, así que si la
    cola también la pide no se pregunta dos veces). */
async function completarDatos(c: Cancion) {
  try {
    const r = await radio(c.id);
    fijar({ cola: completarCon(estado().cola, r.canciones) });
  } catch {
    // Sin el dato, el título y el artista quedan como texto.
  }
}

/** Agrega canciones sin repetir las que ya están en la cola (y de paso completa las que ya estaban). */
function agregar(canciones: Cancion[], sigue: string | null) {
  const cola = completarCon(estado().cola, canciones);
  const ya = new Set(cola.map((c) => c.id));
  const nuevas = canciones.filter((c) => !ya.has(c.id));
  ordenOriginal.push(...nuevas.map((c) => c.id));
  fijar({ cola: [...cola, ...(estado().aleatorio ? barajar(nuevas) : nuevas)], radioSigue: sigue });
}

async function pedirRadio(id: string) {
  fijar({ pidiendoRadio: true });
  try {
    const r = await radio(id);
    if (estado().cola.some((c) => c.id === id)) agregar(r.canciones, r.mas);
  } catch {
    fijar({ aviso: "No pude armar la radio de esta canción." });
  } finally {
    fijar({ pidiendoRadio: false });
  }
}

async function pedirMasRadio() {
  const { radioSigue, pidiendoRadio, cola } = estado();
  if (pidiendoRadio) return;
  if (!radioSigue) {
    const ultima = cola[cola.length - 1];
    if (ultima) await pedirRadio(ultima.id);
    return;
  }
  fijar({ pidiendoRadio: true });
  try {
    const r = await radioMas(radioSigue);
    agregar(r.canciones, r.mas);
  } catch {
    // Sin más radio: la cola termina donde va.
  } finally {
    fijar({ pidiendoRadio: false });
  }
}

// ── Automix: el final de una canción se funde con el principio de la siguiente, como un DJ ──────────────────────
// La siguiente arranca en el segundo reproductor, en silencio, y los volúmenes se cruzan con una curva de potencia
// constante (seno y coseno), para que no haya un bache en la mitad. En iOS no se puede: Safari no deja que una
// página cambie el volumen.

let mezcla: { siguiente: number; reloj: number; volumen: number } | null = null;

/** La canción que sigue, o -1 si no hay. */
function siguienteIndice() {
  const { indice, cola, repetir } = estado();
  if (indice + 1 < cola.length) return indice + 1;
  return repetir === "todas" && cola.length > 1 ? 0 : -1;
}

function revisarMezcla() {
  const s = estado();
  if (mezcla || !puedeMezclar() || !usarAjustes.getState().automix) return;
  if (!s.sonando || s.anuncio || s.repetir === "una" || s.duracion < MINIMO_MEZCLA_S) return;
  const quedan = s.duracion - s.tiempo;
  const sig = siguienteIndice();
  if (sig < 0 || quedan <= 0.5 || quedan > FUNDIDO_S) return;
  empezarMezcla(sig);
}

function empezarMezcla(siguiente: number) {
  const s = estado();
  const volumen = s.silencio ? 0 : s.volumen;
  relevo.cargar(s.cola[siguiente].id);
  const inicio = performance.now();
  const reloj = window.setInterval(() => {
    const k = Math.min(1, (performance.now() - inicio) / (FUNDIDO_S * 1000));
    motor.volumen(Math.round(volumen * Math.cos((k * Math.PI) / 2)));
    relevo.volumen(Math.round(volumen * Math.sin((k * Math.PI) / 2)));
    if (k >= 1) terminarMezcla();
  }, 100);
  mezcla = { siguiente, reloj, volumen };
  fijar({ mezclando: true });
}

/** La mezcla termina (o la canción vieja se acabó antes): la nueva queda como la que suena. */
function terminarMezcla() {
  if (!mezcla) return;
  const { siguiente, reloj, volumen } = mezcla;
  clearInterval(reloj);
  mezcla = null;
  motor.pausar();
  cambiarDeReproductor();
  motor.volumen(volumen);
  const c = estado().cola[siguiente];
  fijar({
    indice: siguiente,
    sonando: true,
    cargando: false,
    anuncio: false,
    mezclando: false,
    tiempo: motor.tiempo(),
    duracion: motor.duracion() || segundos(c.duracion) || 0,
  });
  alEmpezar(c);
}

/** Si la persona hace algo (pausa, salta, busca) en plena mezcla, se deshace: sigue la canción que sonaba. */
function cancelarMezcla() {
  if (!mezcla) return;
  clearInterval(mezcla.reloj);
  relevo.detener();
  motor.volumen(mezcla.volumen);
  mezcla = null;
  fijar({ mezclando: false });
}

/** En el modo de demostración el reproductor queda quieto, con lo que se le ponga, hasta que se toque una canción. */
let congelado = false;
export const congelar = () => (congelado = true);

/** Cada medio segundo: el tiempo, la duración y si lo que suena es un anuncio. */
function vigilar() {
  if (congelado) return;
  const { sonando, visibleSiempre } = estado();
  const c = actual();
  let anuncio = false;
  if (sonando && c) {
    const dur = motor.duracion();
    const esperada = segundos(c.duracion);
    anuncio = dur > 0 && esperada !== null && Math.abs(dur - esperada) > TOLERANCIA_ANUNCIO;
    fijar(anuncio ? { anuncio } : { anuncio, tiempo: motor.tiempo(), duracion: dur || esperada || 0 });
  } else if (estado().anuncio) {
    fijar({ anuncio: false });
  }
  mostrarMotor(anuncio ? "anuncio" : visibleSiempre ? "interruptor" : null);
  revisarMezcla();
}

async function leerAjustesRemotos() {
  try {
    const r = await fetch("/ajustes-remotos.json", { cache: "no-store" });
    const a = await r.json();
    fijar({ visibleSiempre: a.reproductorVisible === true });
  } catch {
    // Sin el archivo, el reproductor sigue escondido.
  }
}

/** Se llama una vez al abrir la web. */
export function arrancar() {
  iniciarMotor({
    alCambiar: (e) => {
      if (e === SONANDO) fijar({ sonando: true, cargando: false });
      else if (e === PAUSADO && !mezcla) fijar({ sonando: false });
      else if (e === CARGANDO) fijar({ cargando: true });
      else if (e === TERMINO) {
        if (mezcla) terminarMezcla();
        else if (estado().repetir === "una") {
          motor.ir(0);
          motor.reanudar();
        } else estado().siguiente();
      }
    },
    alFallar: () => {
      const c = actual();
      // Se dice con palabras, debajo del artista y en el color de la canción (LEEME, regla 6).
      const aviso = c ? `«${c.titulo}» no se deja escuchar aquí. Pasé a la siguiente.` : null;
      fijar({ aviso });
      setTimeout(() => estado().aviso === aviso && fijar({ aviso: null }), AVISO_MS);
      estado().siguiente();
    },
  }).then(() => motor.volumen(estado().volumen));
  leerAjustesRemotos();
  setInterval(vigilar, 500);
  activarSesion();
  activarAtajos();
}
