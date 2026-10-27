import { usarReproductor } from "../reproductor/tienda";
import { esMeGusta, usarBiblioteca } from "../datos/biblioteca";
import { CaratulaSuave } from "./Caratula";
import { FilaCancion } from "./FilaCancion";
import { Icono } from "./Iconos";

/** Cuántas canciones de la cola se muestran. */
const VISIBLES = 40;

/** La columna derecha del computador: «Ahora suena» o la cola. */
export function PanelAhora({ modo }: { modo: "ahora" | "cola" }) {
  const r = usarReproductor();
  const c = r.cola[r.indice];
  const megusta = esMeGusta(c?.id);
  const alternarMeGusta = usarBiblioteca((s) => s.alternarMeGusta);
  const siguen = r.cola.slice(r.indice + 1, r.indice + 1 + VISIBLES);

  if (!c) {
    return (
      <aside className="ahora-panel" aria-label="Ahora suena">
        <p className="panel-titulo">Ahora suena</p>
        <p className="vacio-panel">Toca una canción y aparece aquí, con lo que sigue.</p>
      </aside>
    );
  }

  const lista = (
    <ol>
      {siguen.map((s, i) => (
        <li key={s.id}>
          <FilaCancion
            cancion={s}
            detalle={s.artista}
            alTocar={() => r.irA(r.indice + 1 + i)}
            fin={
              modo === "cola" ? (
                <button type="button" className="boton-icono" onClick={() => r.quitar(r.indice + 1 + i)} aria-label={`Quitar «${s.titulo}» de la cola`}>
                  <Icono nombre="cerrar" tam={18} />
                </button>
              ) : (
                <span />
              )
            }
          />
        </li>
      ))}
    </ol>
  );

  if (modo === "cola") {
    return (
      <aside className="ahora-panel" aria-label="Cola">
        <div className="panel-cabecera">
          <h2>Cola</h2>
          <div>
            <button
              type="button"
              className={`boton-vidrio vidrio${r.aleatorio ? " activo" : ""}`}
              onClick={r.alternarAleatorio}
              aria-label="Aleatorio"
              aria-pressed={r.aleatorio}
            >
              <Icono nombre="aleatorio" />
            </button>
            <button
              type="button"
              className={`boton-vidrio vidrio${r.repetir !== "no" ? " activo" : ""}`}
              onClick={r.cambiarRepetir}
              aria-label="Repetir"
            >
              <Icono nombre={r.repetir === "una" ? "repetirUna" : "repetir"} />
            </button>
          </div>
        </div>
        <div className="panel-sonando">
          <CaratulaSuave cancion={c} />
          <span className="barra-textos">
            <b>{c.titulo}</b>
            <span>Sonando · {c.artista}</span>
          </span>
        </div>
        <p className="panel-origen">
          {r.origen ? `De ${r.origen} · ` : ""}
          {siguen.length} canciones
        </p>
        {lista}
      </aside>
    );
  }

  return (
    <aside className="ahora-panel" aria-label="Ahora suena">
      <p className="panel-titulo">Ahora suena</p>
      <CaratulaSuave cancion={c} className="panel-portada" />
      <div className="panel-cancion">
        <div>
          <h2>{c.titulo}</h2>
          <p>{c.artista}</p>
          {r.aviso && <p className="aviso">{r.aviso}</p>}
        </div>
        <button
          type="button"
          className={`boton-icono${megusta ? " activo" : ""}`}
          onClick={() => alternarMeGusta(c)}
          aria-label={megusta ? "Quitar de Me gusta" : "Me gusta"}
          aria-pressed={megusta}
        >
          <Icono nombre={megusta ? "corazonLleno" : "corazon"} />
        </button>
      </div>
      <p className="panel-seccion">A continuación</p>
      {siguen.length === 0 ? <p className="vacio-panel">{r.pidiendoRadio ? "Armando la radio…" : "No hay más en la cola."}</p> : lista}
    </aside>
  );
}
