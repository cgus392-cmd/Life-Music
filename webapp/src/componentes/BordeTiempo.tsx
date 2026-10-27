/* El tiempo en el mini reproductor: una línea recta en la orilla de abajo de la cápsula, de donde empieza la parte
   recta hasta donde termina. No sube por las curvas de los lados (CG lo corrigió el 26-10). La cabeza de la nota va
   en la punta, del color de la canción. */
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { usarReproductor } from "../reproductor/tienda";
import { Cabeza } from "./Marca";

/** Grosor de la línea (px). */
const GROSOR = 2.5;
/** pathLength fijo para poder dibujar el avance como porcentaje del borde. */
const LARGO = 1000;

export function BordeTiempo() {
  const svg = useRef<SVGSVGElement>(null);
  const camino = useRef<SVGPathElement>(null);
  const [tam, setTam] = useState({ w: 0, h: 0 });
  const [punta, setPunta] = useState({ x: 0, y: 0 });
  const tiempo = usarReproductor((s) => s.tiempo);
  const duracion = usarReproductor((s) => s.duracion);
  const avance = duracion > 0 ? Math.min(1, tiempo / duracion) : 0;

  // El borde se mide de la cápsula que lo contiene.
  useEffect(() => {
    const capsula = svg.current?.parentElement;
    if (!capsula) return;
    const medir = () => setTam({ w: capsula.offsetWidth, h: capsula.offsetHeight });
    medir();
    const o = new ResizeObserver(medir);
    o.observe(capsula);
    return () => o.disconnect();
  }, []);

  const { w, h } = tam;
  const r = h / 2;
  const y = h - GROSOR / 2;
  // Solo la orilla de abajo: la parte recta, entre las dos curvas.
  const d = w && h ? `M ${r} ${y} L ${w - r} ${y}` : "";

  useLayoutEffect(() => {
    const p = camino.current;
    if (!p || !d) return;
    const punto = p.getPointAtLength(p.getTotalLength() * avance);
    setPunta({ x: punto.x, y: punto.y });
  }, [d, avance]);

  if (!d) return <svg ref={svg} className="borde-tiempo" aria-hidden="true" />;
  return (
    <>
      <svg ref={svg} className="borde-tiempo" width={w} height={h} viewBox={`0 0 ${w} ${h}`} aria-hidden="true">
        <path d={d} className="borde-pista" strokeWidth={GROSOR} pathLength={LARGO} />
        <path
          ref={camino}
          d={d}
          className="borde-avance"
          strokeWidth={GROSOR}
          pathLength={LARGO}
          strokeDasharray={LARGO}
          strokeDashoffset={LARGO * (1 - avance)}
        />
      </svg>
      {avance > 0 && (
        <span className="borde-cabeza" style={{ left: punta.x, top: punta.y }} aria-hidden="true">
          <Cabeza ancho={11} />
        </span>
      )}
    </>
  );
}
