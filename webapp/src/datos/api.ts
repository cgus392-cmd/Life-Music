/* Lo que la web le pregunta a nuestras funciones de Cloudflare (functions/api).
   Solo viajan textos: el audio nunca pasa por aquí. */
import { demo, INICIO } from "../demo";

export type Cancion = {
  id: string;
  titulo: string;
  artista: string;
  artistaId: string | null;
  album: string | null;
  albumId: string | null;
  duracion: string | null;
  caratula: string | null;
  explicita: boolean;
};

export type Pagina = { canciones: Cancion[]; mas: string | null };

export type TipoElemento = "cancion" | "album" | "artista" | "lista";

/** Lo que YouTube Music muestra en una fila o un carrusel. */
export type Elemento = {
  tipo: TipoElemento;
  id: string;
  titulo: string;
  subtitulo: string;
  caratula: string | null;
  cancion?: Cancion;
};

export type Fila = { titulo: string; forma: "lista" | "carrusel"; elementos: Elemento[] };

/** Un álbum o una lista. */
export type Coleccion = {
  id: string;
  titulo: string;
  autor: string;
  autorId: string | null;
  subtitulo: string;
  detalle: string;
  caratula: string | null;
  canciones: Cancion[];
};

export type Artista = {
  id: string;
  nombre: string;
  foto: string | null;
  oyentes: string | null;
  populares: Cancion[];
  filas: Fila[];
};

export type TipoBusqueda = "todo" | "canciones" | "artistas" | "albumes" | "listas";

export type ResultadoTodo = {
  mejor: Elemento | null;
  secciones: { tipo: "canciones" | "artistas" | "albumes"; elementos: Elemento[] }[];
};

async function pedir<T>(ruta: string): Promise<T> {
  // Modo de demostración (solo en local): el Inicio con canciones de ejemplo.
  if (demo && ruta.startsWith("/api/inicio")) return INICIO as T;
  const r = await fetch(ruta);
  const datos = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(datos.error || `El servidor respondió ${r.status}`);
  return datos as T;
}

const q = encodeURIComponent;

export const buscar = (texto: string) => pedir<Pagina>(`/api/buscar?q=${q(texto)}`);
export const buscarMas = (mas: string) => pedir<Pagina>(`/api/buscar?mas=${q(mas)}`);
export const radio = (id: string) => pedir<Pagina>(`/api/radio?v=${q(id)}`);
export const radioMas = (mas: string) => pedir<Pagina>(`/api/radio?mas=${q(mas)}`);
export const sugerencias = (texto: string) =>
  pedir<{ sugerencias: string[] }>(`/api/sugerencias?q=${q(texto)}`).then((d) => d.sugerencias);
export const inicio = () => pedir<{ filas: Fila[]; pais: string }>("/api/inicio");
export const album = (id: string) => pedir<Coleccion>(`/api/album?id=${q(id)}`);
export const lista = (id: string) => pedir<Coleccion>(`/api/lista?id=${q(id)}`);
export const artista = (id: string) => pedir<Artista>(`/api/artista?id=${q(id)}`);
export const buscarTodo = (texto: string) => pedir<ResultadoTodo>(`/api/buscar?q=${q(texto)}&tipo=todo`);
export const buscarTipo = (texto: string, tipo: Exclude<TipoBusqueda, "todo" | "canciones">) =>
  pedir<{ elementos: Elemento[]; mas: string | null }>(`/api/buscar?q=${q(texto)}&tipo=${tipo}`);

/** A dónde lleva un elemento que no es canción. */
export function rutaDe(e: Elemento): string {
  return e.tipo === "album" ? `/album/${e.id}` : e.tipo === "artista" ? `/artista/${e.id}` : `/lista/${e.id}`;
}

/** «3:58» → 238. */
export function segundos(duracion: string | null): number | null {
  if (!duracion) return null;
  const n = duracion.split(":").reduce((t, p) => t * 60 + Number(p), 0);
  return Number.isFinite(n) ? n : null;
}

/** 238 → «3:58». */
export function reloj(s: number): string {
  if (!Number.isFinite(s) || s < 0) s = 0;
  const m = Math.floor(s / 60);
  return `${m}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
}
