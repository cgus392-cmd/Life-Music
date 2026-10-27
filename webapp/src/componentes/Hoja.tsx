/* Las hojas que flotan, como en iOS 26 (plan 0.4, punto 1.6): el menú ··· y la guía para instalar. */
import { useEffect, useRef, useState, type HTMLAttributes, type PointerEvent, type ReactNode } from "react";

/** Cuánto hay que bajar una hoja (px) para cerrarla. */
const UMBRAL = 90;
/** Lo que tarda en bajar al cerrarse (ms); es la transición de salida de vistas.css. */
const SALIDA_MS = 280;

/** Una hoja que sube desde abajo con la curva de iOS mientras el fondo se oscurece, y baja al cerrarse. También se
    cierra bajándola con el dedo; si se suelta antes del umbral, vuelve a su sitio. Sigue montada mientras baja. */
export function Hoja({
  abierta,
  alCerrar,
  capa = true,
  className = "",
  children,
  ...resto
}: {
  abierta: boolean;
  alCerrar: () => void;
  /** Con la capa que oscurece el fondo y cierra al tocarla (el menú). La guía va sin capa. */
  capa?: boolean;
  className?: string;
  children: ReactNode;
} & Omit<HTMLAttributes<HTMLDivElement>, "className" | "children">) {
  const [montada, setMontada] = useState(abierta);
  // Ya subió: la clase que dispara la transición de entrada (o la quita, para la salida).
  const [arriba, setArriba] = useState(false);
  const tiron = useRef<{ y: number; arrastrando: boolean } | null>(null);
  const [bajada, setBajada] = useState(0);

  useEffect(() => {
    if (abierta) {
      setMontada(true);
      // Dos cuadros: en el primero se pinta abajo; en el segundo sube con la transición.
      let segundo = 0;
      const primero = requestAnimationFrame(() => (segundo = requestAnimationFrame(() => setArriba(true))));
      return () => {
        cancelAnimationFrame(primero);
        cancelAnimationFrame(segundo);
      };
    }
    setArriba(false);
    const t = setTimeout(() => setMontada(false), SALIDA_MS);
    return () => clearTimeout(t);
  }, [abierta]);

  if (!montada) return null;

  // Un toque es un toque; solo pasados 8 px hacia abajo se vuelve arrastre (y entonces no se activa ningún botón).
  const empezar = (e: PointerEvent) => {
    tiron.current = { y: e.clientY, arrastrando: false };
  };
  const mover = (e: PointerEvent) => {
    const t = tiron.current;
    if (!t) return;
    const dy = e.clientY - t.y;
    if (!t.arrastrando && dy > 8) {
      t.arrastrando = true;
      e.currentTarget.setPointerCapture(e.pointerId);
    }
    if (t.arrastrando) setBajada(Math.max(0, dy));
  };
  const soltar = () => {
    const t = tiron.current;
    tiron.current = null;
    if (t?.arrastrando && bajada > UMBRAL) alCerrar();
    setBajada(0);
  };

  const hoja = (
    <div
      {...resto}
      className={`hoja-flotante vidrio ${className}${arriba ? " arriba" : ""}`}
      style={bajada ? { transform: `translateY(${bajada}px)`, transition: "none" } : undefined}
      onClick={(e) => e.stopPropagation()}
      onPointerDown={empezar}
      onPointerMove={mover}
      onPointerUp={soltar}
      onPointerCancel={soltar}
    >
      {children}
    </div>
  );
  return capa ? (
    <div className={`hoja-capa${arriba ? " arriba" : ""}`} onClick={alCerrar}>
      {hoja}
    </div>
  ) : (
    hoja
  );
}
