/* Los ajustes de cada persona, guardados en este navegador. */
import { create } from "zustand";
import { persist } from "zustand/middleware";

export type Tema = "auto" | "claro" | "oscuro";
export type FondoAhora = "apple" | "mesh";

type Ajustes = {
  tema: Tema;
  fondo: FondoAhora;
  pantallaEncendida: boolean;
  bienvenidaVista: boolean;
  guiaCerrada: boolean;
  fijar: (cambios: Partial<Omit<Ajustes, "fijar">>) => void;
};

export const usarAjustes = create<Ajustes>()(
  persist(
    (set) => ({
      tema: "auto",
      fondo: "apple",
      pantallaEncendida: false,
      bienvenidaVista: false,
      guiaCerrada: false,
      fijar: (cambios) => set(cambios),
    }),
    { name: "lifemusic-ajustes", version: 1 },
  ),
);

/** Aplica el tema a la página (el modo automático sigue al teléfono). */
export function aplicarTema(tema: Tema) {
  if (tema === "auto") delete document.documentElement.dataset.tema;
  else document.documentElement.dataset.tema = tema;
}
