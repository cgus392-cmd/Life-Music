/* Transiciones entre pantallas, como en iOS: al entrar, la pantalla nueva llega desde la derecha y la vieja se corre
   un poco a la izquierda; al volver, al revés; entre pestañas, un fundido corto. Usa View Transitions (Safari 18+,
   Chrome); donde no hay, la pantalla cambia sin animación. Con «reducir movimiento», tampoco hay. */
import type { MouseEvent, ReactNode } from "react";
import { Link, NavLink, useNavigate, type To } from "react-router";

export type Direccion = "adelante" | "atras" | "lado";

const sinMovimiento = () => matchMedia("(prefers-reduced-motion: reduce)").matches;

/** Corre el cambio de pantalla dentro de una transición en la dirección dada. */
export function transicion(dir: Direccion, cambiar: () => void) {
  document.documentElement.dataset.dir = dir;
  if (!document.startViewTransition || sinMovimiento()) return cambiar();
  document.startViewTransition(
    () =>
      new Promise<void>((listo) => {
        // Al volver, la ruta cambia con el evento popstate; se espera a que React pinte la pantalla nueva.
        const pintar = () => requestAnimationFrame(() => requestAnimationFrame(() => listo()));
        if (dir === "atras") addEventListener("popstate", pintar, { once: true });
        cambiar();
        if (dir !== "atras") pintar();
        setTimeout(listo, 400);
      }),
  );
}

/** navigate con transición. */
export function useIr() {
  const ir = useNavigate();
  return {
    a: (to: To, dir: Direccion = "adelante") => transicion(dir, () => ir(to)),
    atras: () => transicion("atras", () => ir(-1)),
  };
}

const conTransicion = (ir: (to: To) => void, to: To, dir: Direccion) => (e: MouseEvent) => {
  if (e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0) return;
  e.preventDefault();
  transicion(dir, () => ir(to));
};

/** Un enlace a otra pantalla, que entra desde la derecha. */
export function Enlace({ to, className, children, dir = "adelante", etiqueta }: {
  to: To;
  className?: string;
  children: ReactNode;
  dir?: Direccion;
  /** El nombre para lectores de pantalla, cuando el enlace es solo un ícono. */
  etiqueta?: string;
}) {
  const ir = useNavigate();
  return (
    <Link to={to} className={className} aria-label={etiqueta} onClick={conTransicion(ir, to, dir)}>
      {children}
    </Link>
  );
}

/** Una pestaña: cambia con un fundido. Si ya estás en ella, vuelve arriba, como en iOS. */
export function Pestana({ to, end, className, children }: { to: string; end?: boolean; className?: string; children: ReactNode }) {
  const ir = useNavigate();
  return (
    <NavLink
      to={to}
      end={end}
      className={className}
      onClick={(e) => {
        if (location.pathname === to) {
          e.preventDefault();
          document.querySelector(".centro")?.scrollTo({ top: 0, behavior: "smooth" });
          scrollTo({ top: 0, behavior: "smooth" });
          return;
        }
        conTransicion(ir, to, "lado")(e);
      }}
    >
      {children}
    </NavLink>
  );
}
