import type { ReactNode } from "react";
import type { Cancion } from "../datos/api";
import { Caratula } from "./Caratula";
import { Nota } from "./Marca";

type Props = {
  cancion: Cancion;
  alTocar: () => void;
  actual?: boolean;
  /** Lo que va debajo del título; por defecto, artista · álbum. */
  detalle?: string;
  /** Lo que va al final de la fila (duración, botón de más). */
  fin?: ReactNode;
  /** El número de pista (álbum, populares del artista). */
  numero?: number;
  /** En un álbum las filas no llevan carátula: todas son la misma. */
  sinPortada?: boolean;
};

/** Una canción en una lista. Si es la que suena, lleva la marca completa de la nota, nunca un punto. */
export function FilaCancion({ cancion: c, alTocar, actual = false, detalle, fin, numero, sinPortada = false }: Props) {
  return (
    <div className={`fila${sinPortada ? " fila--pista" : ""}`} aria-current={actual ? "true" : undefined}>
      {numero !== undefined && <span className="fila-numero">{actual ? <Nota alto={15} /> : numero}</span>}
      {!sinPortada && (
        <button type="button" className="fila-abrir" onClick={alTocar} aria-label={`Reproducir «${c.titulo}»`}>
          <Caratula cancion={c} />
        </button>
      )}
      <span className="fila-textos">
        <button type="button" className="fila-cuerpo" onClick={alTocar}>
          <b>
            {actual && numero === undefined && <Nota alto={14} />}
            <span className="fila-titulo">{c.titulo}</span>
            {c.explicita && (
              <abbr className="fila-explicita" title="Explícita">
                E
              </abbr>
            )}
          </b>
          {!sinPortada && <span>{detalle ?? [c.artista, c.album].filter(Boolean).join(" · ")}</span>}
        </button>
        {fin ?? <span className="fila-fin">{c.duracion}</span>}
      </span>
    </div>
  );
}
