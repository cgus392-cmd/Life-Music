/* El estado del reproductor: la cola, la canción actual y lo que hace el motor. */
import { create } from "zustand";
import { iniciarMotor, motor, mostrarMotor } from "./youtube";
import { radio, radioMas, segundos, type Cancion } from "../datos/api";
import { colorDe, CORAL } from "../datos/color";
import { usarBiblioteca } from "../datos/biblioteca";
import { activarSesion, actualizarSesion } from "./sesion";
import { activarAtajos } from "./atajos";

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
  visibleSiempre: false,
  aviso: null,
  radioSigue: null,
  pidiendoRadio: false,

  tocar: (c, lista, origen) => {
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
    const { tiempo, indice } = get();
    if (tiempo > 3 || indice === 0) get().buscarEn(0);
    else get().irA(indice - 1);
  },

  alternar: () => {
    const { sonando, indice } = get();
    if (indice < 0) return;
    if (sonando) motor.pausar();
    else motor.reanudar();
  },

  buscarEn: (s) => {
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
  actualizarSesion(c);
  usarBiblioteca.getState().anotarEscuchada(c);
  colorDe(c.id).then((color) => {
    if (actual()?.id !== c.id) return;
    fijar({ color });
    document.documentElement.style.setProperty("--cancion", color);
  });
  const { cola, indice } = estado();
  if (cola.length - 1 - indice <= COLCHON) pedirMasRadio();
}

/** Agrega canciones sin repetir las que ya están en la cola. */
function agregar(canciones: Cancion[], sigue: string | null) {
  const ya = new Set(estado().cola.map((c) => c.id));
  const nuevas = canciones.filter((c) => !ya.has(c.id));
  ordenOriginal.push(...nuevas.map((c) => c.id));
  fijar({ cola: [...estado().cola, ...(estado().aleatorio ? barajar(nuevas) : nuevas)], radioSigue: sigue });
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

/** Cada medio segundo: el tiempo, la duración y si lo que suena es un anuncio. */
function vigilar() {
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
      else if (e === PAUSADO) fijar({ sonando: false });
      else if (e === CARGANDO) fijar({ cargando: true });
      else if (e === TERMINO) {
        if (estado().repetir === "una") {
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
