/* La biblioteca de cada persona: Me gusta, Mis listas, Historial y búsquedas recientes.
   Vive solo en este navegador (localStorage). No hay cuentas, servidor ni telemetría. */
import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Cancion } from "./api";

/** Cuántas canciones guarda el historial. */
const TOPE_HISTORIAL = 200;
/** Cuántas búsquedas recientes se recuerdan. */
const TOPE_RECIENTES = 8;

export type Lista = { id: string; nombre: string; canciones: Cancion[]; creada: number };

export type Reciente =
  | { tipo: "busqueda"; texto: string }
  | { tipo: "cancion"; cancion: Cancion };

type Biblioteca = {
  megusta: Cancion[];
  listas: Lista[];
  historial: (Cancion & { cuando: number })[];
  recientes: Reciente[];

  alternarMeGusta: (c: Cancion) => void;
  anotarEscuchada: (c: Cancion) => void;
  anotarReciente: (r: Reciente) => void;
  borrarRecientes: () => void;
  crearLista: (nombre: string) => string;
  renombrarLista: (id: string, nombre: string) => void;
  borrarLista: (id: string) => void;
  agregarALista: (id: string, c: Cancion) => void;
  quitarDeLista: (id: string, cancionId: string) => void;
  borrarTodo: () => void;
};

const vacia = { megusta: [], listas: [], historial: [], recientes: [] };

export const usarBiblioteca = create<Biblioteca>()(
  persist(
    (set, get) => ({
      ...vacia,

      alternarMeGusta: (c) => {
        const ya = get().megusta.some((x) => x.id === c.id);
        set({ megusta: ya ? get().megusta.filter((x) => x.id !== c.id) : [c, ...get().megusta] });
      },

      anotarEscuchada: (c) =>
        set({
          historial: [{ ...c, cuando: Date.now() }, ...get().historial.filter((x) => x.id !== c.id)].slice(0, TOPE_HISTORIAL),
        }),

      anotarReciente: (r) => {
        const igual = (x: Reciente) =>
          x.tipo === r.tipo && (x.tipo === "busqueda" ? x.texto === (r as typeof x).texto : x.cancion.id === (r as typeof x).cancion.id);
        set({ recientes: [r, ...get().recientes.filter((x) => !igual(x))].slice(0, TOPE_RECIENTES) });
      },

      borrarRecientes: () => set({ recientes: [] }),

      crearLista: (nombre) => {
        const id = crypto.randomUUID();
        set({ listas: [{ id, nombre, canciones: [], creada: Date.now() }, ...get().listas] });
        return id;
      },

      renombrarLista: (id, nombre) => set({ listas: get().listas.map((l) => (l.id === id ? { ...l, nombre } : l)) }),

      borrarLista: (id) => set({ listas: get().listas.filter((l) => l.id !== id) }),

      agregarALista: (id, c) =>
        set({
          listas: get().listas.map((l) =>
            l.id === id && !l.canciones.some((x) => x.id === c.id) ? { ...l, canciones: [...l.canciones, c] } : l,
          ),
        }),

      quitarDeLista: (id, cancionId) =>
        set({
          listas: get().listas.map((l) => (l.id === id ? { ...l, canciones: l.canciones.filter((x) => x.id !== cancionId) } : l)),
        }),

      borrarTodo: () => set(vacia),
    }),
    { name: "lifemusic-biblioteca", version: 1 },
  ),
);

export const esMeGusta = (id: string | undefined) => usarBiblioteca((s) => !!id && s.megusta.some((x) => x.id === id));
