/* Volver atrás deslizando desde el borde izquierdo, como en iOS (plan 0.4, punto 2.2).
   Solo en la app instalada en el iPhone: ahí no hay barras de Safari ni gesto del navegador. En Safari normal ya
   está el de Safari, y en Android el del sistema; si actuaran los dos, se retrocedería dos veces. */
import { useEffect } from "react";
import { useNavigate } from "react-router";
import { esIphone, instalada } from "./Entrada";
import { transicion } from "./Transicion";

/** Lo ancho del borde donde empieza el gesto (px), como el de iOS. */
const BORDE = 24;
/** Lo que hay que mover antes de decidir si el gesto va de lado o hacia abajo (px). */
const DECIDIR = 10;
/** Al soltar se vuelve si se pasó de este tanto del ancho, o si el dedo iba rápido hacia la derecha. */
const FRACCION = 0.35;
const RAPIDO = 0.5; // px por ms

/** Hay a dónde volver dentro de la app (React Router guarda la posición en el historial). Sin esto, abrir un enlace
    directo a un álbum y deslizar sacaría de la app. */
const hayAtras = () => ((history.state as { idx?: number } | null)?.idx ?? 0) > 0;

/** Algo encima de la página (Ahora suena, un menú, la bienvenida) se queda con el gesto. */
const algoEncima = () => !!document.querySelector(".ahora--abierta, .hoja-capa, .bienvenida");

export function GestoAtras() {
  const ir = useNavigate();

  useEffect(() => {
    const raiz = document.documentElement;
    let gesto: { x: number; y: number; lado: boolean | null; dx: number; t: number; v: number } | null = null;

    const mover = (dx: number) => raiz.style.setProperty("--atras-dx", `${dx}px`);
    const limpiar = () => {
      raiz.classList.remove("arrastrando-atras", "volviendo-atras");
      raiz.style.removeProperty("--atras-dx");
    };

    // Solo se escucha el movimiento cuando el toque empezó en el borde: un touchmove que puede frenar el
    // desplazamiento, puesto todo el tiempo, haría que la página se sintiera pesada al bajar.
    const empezar = (e: TouchEvent) => {
      const t = e.touches[0];
      if (e.touches.length !== 1 || t.clientX > BORDE || !esIphone() || !instalada() || !hayAtras() || algoEncima()) return;
      gesto = { x: t.clientX, y: t.clientY, lado: null, dx: 0, t: e.timeStamp, v: 0 };
      addEventListener("touchmove", seguir, { passive: false });
    };

    const seguir = (e: TouchEvent) => {
      if (!gesto) return;
      const t = e.touches[0];
      const dx = t.clientX - gesto.x;
      const dy = t.clientY - gesto.y;
      if (gesto.lado === null) {
        if (Math.hypot(dx, dy) < DECIDIR) return;
        gesto.lado = Math.abs(dx) > Math.abs(dy) && dx > 0;
        if (!gesto.lado) return void (gesto = null);
        raiz.classList.remove("volviendo-atras");
        raiz.classList.add("arrastrando-atras");
      }
      // La página sigue al dedo y la página no se desplaza mientras tanto.
      e.preventDefault();
      const ahora = Math.max(0, dx);
      gesto.v = (ahora - gesto.dx) / Math.max(1, e.timeStamp - gesto.t);
      gesto.dx = ahora;
      gesto.t = e.timeStamp;
      mover(ahora);
    };

    const soltar = (e: TouchEvent) => {
      removeEventListener("touchmove", seguir);
      const g = gesto;
      gesto = null;
      if (!g?.lado) return;
      // Si el dedo se quedó quieto antes de soltar, no es un empujón: cuenta solo hasta dónde llegó.
      const v = e.timeStamp - g.t > 100 ? 0 : g.v;
      if (g.dx > innerWidth * FRACCION || (v > RAPIDO && g.dx > 40)) {
        // La transición de volver toma la página tal como quedó corrida y la sigue llevando a la derecha.
        transicion("atras", () => {
          limpiar();
          ir(-1);
        });
      } else {
        // No alcanzó: la página regresa a su sitio.
        raiz.classList.add("volviendo-atras");
        mover(0);
        setTimeout(limpiar, 300);
      }
    };

    addEventListener("touchstart", empezar, { passive: true });
    addEventListener("touchend", soltar);
    addEventListener("touchcancel", soltar);
    return () => {
      removeEventListener("touchstart", empezar);
      removeEventListener("touchmove", seguir);
      removeEventListener("touchend", soltar);
      removeEventListener("touchcancel", soltar);
      limpiar();
    };
  }, [ir]);

  return null;
}
