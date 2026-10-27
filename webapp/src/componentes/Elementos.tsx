/* Piezas para mostrar lo que trae YouTube Music: tarjetas de carrusel, filas de álbum/artista/lista y la lista de
   canciones de una fila. */
import type { ReactNode } from "react";
import { Enlace } from "./Transicion";
import { rutaDe, type Cancion, type Elemento } from "../datos/api";
import { usarReproductor } from "../reproductor/tienda";
import { useMantener, usarMenu } from "./MenuCancion";
import { FilaCancion } from "./FilaCancion";
import { Icono } from "./Iconos";

function Portada({ e, className = "" }: { e: Elemento; className?: string }) {
  const respaldo = e.cancion ? `https://i.ytimg.com/vi/${e.id}/hqdefault.jpg` : undefined;
  return (
    <img
      className={`portada ${className}${e.tipo === "artista" ? " redonda" : ""}`}
      src={e.caratula ?? respaldo}
      alt=""
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={(ev) => respaldo && ev.currentTarget.src !== respaldo && (ev.currentTarget.src = respaldo)}
    />
  );
}

/** Una tarjeta de carrusel de canción: suena al tocarla y, mantenida (o con clic derecho), abre su menú. */
function TarjetaCancion({ c, cuerpo, lista, origen }: { c: Cancion; cuerpo: ReactNode; lista?: Cancion[]; origen?: string }) {
  const tocar = usarReproductor((s) => s.tocar);
  const mantener = useMantener(c);
  return (
    <button
      type="button"
      className={`tarjeta${mantener.manteniendo ? " manteniendo" : ""}`}
      onClick={() => tocar(c, lista, origen)}
      {...mantener.props}
    >
      {cuerpo}
    </button>
  );
}

/** Una tarjeta de carrusel. Las canciones suenan al tocarlas; lo demás abre su página. */
export function Tarjeta({ e, lista, origen }: { e: Elemento; lista?: Cancion[]; origen?: string }) {
  const cuerpo = (
    <>
      <Portada e={e} />
      <span className="tarjeta-titulo">{e.titulo}</span>
      <span className="tarjeta-sub">{e.subtitulo}</span>
    </>
  );
  if (e.tipo === "cancion" && e.cancion) return <TarjetaCancion c={e.cancion} cuerpo={cuerpo} lista={lista} origen={origen} />;
  return (
    <Enlace to={rutaDe(e)} className={`tarjeta${e.tipo === "artista" ? " tarjeta--artista" : ""}`}>
      {cuerpo}
    </Enlace>
  );
}

/** Un carrusel que se desliza de lado. */
export function Carrusel({ titulo, elementos, origen }: { titulo: string; elementos: Elemento[]; origen?: string }) {
  const canciones = elementos.flatMap((e) => (e.cancion ? [e.cancion] : []));
  return (
    <section className="fila-inicio">
      <h2 className="seccion">{titulo}</h2>
      <div className="carrusel">
        {elementos.map((e) => (
          <Tarjeta key={`${e.tipo}-${e.id}`} e={e} lista={canciones.length > 1 ? canciones : undefined} origen={origen ?? `«${titulo}»`} />
        ))}
      </div>
    </section>
  );
}

/** Una fila de álbum, artista o lista que abre su página. */
export function FilaElemento({ e }: { e: Elemento }) {
  return (
    <Enlace to={rutaDe(e)} className="fila">
      <Portada e={e} />
      <span className="fila-textos">
        <span className="fila-cuerpo">
          <b>{e.titulo}</b>
          <span>{e.subtitulo}</span>
        </span>
        <Icono nombre="derecha" tam={18} className="fila-flecha" />
      </span>
    </Enlace>
  );
}

/** Canciones en lista; tocar una arma la cola con todas. Cada fila trae su menú. */
export function ListaCanciones({ canciones, origen, numeros = false, sinPortada = false, detalle }: {
  canciones: Cancion[];
  origen: string;
  numeros?: boolean;
  sinPortada?: boolean;
  detalle?: (c: Cancion) => string;
}) {
  const tocar = usarReproductor((s) => s.tocar);
  const idActual = usarReproductor((s) => s.cola[s.indice]?.id);
  const abrirMenu = usarMenu((s) => s.abrir);
  return (
    <ol className={`lista${numeros ? " lista--numerada" : ""}`}>
      {canciones.map((c, i) => (
        <li key={`${c.id}-${i}`}>
          <FilaCancion
            cancion={c}
            actual={c.id === idActual}
            numero={numeros ? i + 1 : undefined}
            sinPortada={sinPortada}
            detalle={detalle?.(c)}
            alTocar={() => tocar(c, canciones, origen)}
            fin={
              <span className="fila-fin-grupo">
                {c.duracion && <span className="fila-fin">{c.duracion}</span>}
                <button type="button" className="boton-icono fila-mas" onClick={() => abrirMenu(c)} aria-label={`Más opciones de «${c.titulo}»`}>
                  <Icono nombre="puntos" tam={20} />
                </button>
              </span>
            }
          />
        </li>
      ))}
    </ol>
  );
}
