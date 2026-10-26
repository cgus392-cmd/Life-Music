import { usarReproductor } from "../reproductor/tienda";
import { reloj } from "../datos/api";
import { Cabeza } from "./Marca";

type Variante = "ahora" | "mini" | "barra";

/** El ancho de la cabeza de la nota en cada lugar. */
const CABEZA: Record<Variante, number> = { ahora: 22, mini: 12, barra: 16 };

/** La línea del tiempo: la línea del logo, con la cabeza de la nota marcando dónde va (en el color de la canción). */
export function BarraTiempo({ variante }: { variante: Variante }) {
  const tiempo = usarReproductor((s) => s.tiempo);
  const duracion = usarReproductor((s) => s.duracion);
  const buscarEn = usarReproductor((s) => s.buscarEn);
  const avance = duracion > 0 ? Math.min(100, (tiempo / duracion) * 100) : 0;

  return (
    <div className={`tiempo tiempo--${variante}`}>
      <div className="tiempo-pista">
        <i style={{ width: `${avance}%` }} />
      </div>
      <span className="tiempo-cabeza" style={{ left: `${avance}%` }}>
        <Cabeza ancho={CABEZA[variante]} />
      </span>
      {variante !== "mini" && (
        <input
          className="tiempo-control"
          type="range"
          min={0}
          max={Math.max(1, Math.round(duracion))}
          step={1}
          value={Math.min(Math.round(tiempo), Math.round(duracion))}
          onChange={(e) => buscarEn(Number(e.target.value))}
          aria-label="Posición en la canción"
          aria-valuetext={`${reloj(tiempo)} de ${reloj(duracion)}`}
        />
      )}
    </div>
  );
}
