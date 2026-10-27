import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { Enlace } from "../componentes/Transicion";
import {
  buscar,
  buscarMas,
  buscarTipo,
  buscarTodo,
  rutaDe,
  sugerencias,
  type Cancion,
  type Elemento,
  type ResultadoTodo,
  type TipoBusqueda,
} from "../datos/api";
import { usarReproductor } from "../reproductor/tienda";
import { usarBiblioteca } from "../datos/biblioteca";
import { Carrusel, FilaElemento, ListaCanciones } from "../componentes/Elementos";
import { Cargando, SinConexion, SinResultados } from "../componentes/Estados";
import { Caratula } from "../componentes/Caratula";
import { Icono, type NombreIcono } from "../componentes/Iconos";

/** Espera entre teclas antes de pedir sugerencias (ms). */
const PAUSA_SUGERENCIAS = 200;

const ALCANCES: { tipo: TipoBusqueda; nombre: string }[] = [
  { tipo: "todo", nombre: "Todo" },
  { tipo: "canciones", nombre: "Canciones" },
  { tipo: "artistas", nombre: "Artistas" },
  { tipo: "albumes", nombre: "Álbumes" },
  { tipo: "listas", nombre: "Listas" },
];

const MOMENTOS: { icono: NombreIcono; nombre: string; busqueda: string }[] = [
  { icono: "luna", nombre: "Para la noche", busqueda: "música para la noche" },
  { icono: "rayo", nombre: "Para entrenar", busqueda: "música para entrenar" },
  { icono: "hoja", nombre: "Para concentrarse", busqueda: "música para concentrarse" },
];

type Resultado =
  | { tipo: "todo"; datos: ResultadoTodo }
  | { tipo: "canciones"; canciones: Cancion[]; mas: string | null }
  | { tipo: "otros"; elementos: Elemento[] };

const vacio = (r: Resultado) =>
  r.tipo === "todo" ? !r.datos.mejor && r.datos.secciones.every((s) => !s.elementos.length) : r.tipo === "canciones" ? !r.canciones.length : !r.elementos.length;

function Mejor({ e }: { e: Elemento }) {
  const tocar = usarReproductor((s) => s.tocar);
  const cuerpo = (
    <>
      {e.cancion ? <Caratula cancion={e.cancion} /> : <img className={`portada${e.tipo === "artista" ? " redonda" : ""}`} src={e.caratula ?? undefined} alt="" referrerPolicy="no-referrer" />}
      <span className="mejor-t">
        <b>{e.titulo}</b>
        <span>{e.subtitulo}</span>
      </span>
    </>
  );
  return (
    <div className="mejor">
      {e.cancion ? (
        <button type="button" className="mejor-abrir" onClick={() => tocar(e.cancion!)}>
          {cuerpo}
        </button>
      ) : (
        <Enlace to={rutaDe(e)} className="mejor-abrir">
          {cuerpo}
        </Enlace>
      )}
      {e.cancion && (
        <button type="button" className="boton-vidrio vidrio mejor-play" onClick={() => tocar(e.cancion!)} aria-label={`Reproducir «${e.titulo}»`}>
          <Icono nombre="play" />
        </button>
      )}
    </div>
  );
}

export function Buscar() {
  const [params, setParams] = useSearchParams();
  const consulta = params.get("q") ?? "";
  const tipo = (params.get("tipo") as TipoBusqueda) || "todo";
  const [texto, setTexto] = useState(consulta);
  const [escribiendo, setEscribiendo] = useState(false);
  const [ideas, setIdeas] = useState<string[]>([]);
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const [error, setError] = useState(false);
  const [cargandoMas, setCargandoMas] = useState(false);
  const tocar = usarReproductor((s) => s.tocar);
  const { recientes, anotarReciente, borrarRecientes } = usarBiblioteca();
  const campo = useRef<HTMLInputElement>(null);

  // Sugerencias mientras se escribe.
  useEffect(() => {
    if (!escribiendo || !texto.trim()) return setIdeas([]);
    const t = setTimeout(() => sugerencias(texto).then(setIdeas, () => setIdeas([])), PAUSA_SUGERENCIAS);
    return () => clearTimeout(t);
  }, [texto, escribiendo]);

  // Resultados de la búsqueda que está en la dirección (?q=&tipo=).
  const cargar = () => {
    setTexto(consulta);
    setEscribiendo(false);
    setResultado(null);
    setError(false);
    if (!consulta) return;
    let vigente = true;
    const pedido: Promise<Resultado> =
      tipo === "todo"
        ? buscarTodo(consulta).then((datos) => ({ tipo: "todo", datos }))
        : tipo === "canciones"
          ? buscar(consulta).then((r) => ({ tipo: "canciones", canciones: r.canciones, mas: r.mas }))
          : buscarTipo(consulta, tipo).then((r) => ({ tipo: "otros", elementos: r.elementos }));
    pedido.then((r) => vigente && setResultado(r), () => vigente && setError(true));
    return () => {
      vigente = false;
    };
  };
  useEffect(cargar, [consulta, tipo]);

  const enviar = (q: string, t: TipoBusqueda = "todo") => {
    const limpio = q.trim();
    if (!limpio) return;
    anotarReciente({ tipo: "busqueda", texto: limpio });
    setParams({ q: limpio, tipo: t });
    campo.current?.blur();
  };

  const cargarMas = async () => {
    if (resultado?.tipo !== "canciones" || !resultado.mas) return;
    setCargandoMas(true);
    try {
      const r = await buscarMas(resultado.mas);
      setResultado({ ...resultado, canciones: [...resultado.canciones, ...r.canciones], mas: r.mas });
    } finally {
      setCargandoMas(false);
    }
  };

  return (
    <section className="buscar" aria-labelledby="titulo-buscar">
      <div className="cabecera" />
      <h1 id="titulo-buscar" className="titulo-grande">
        Buscar
      </h1>
      <form
        className="buscador"
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          enviar(texto, tipo);
        }}
      >
        <label>
          <Icono nombre="buscar" />
          <input
            ref={campo}
            type="search"
            value={texto}
            onChange={(e) => (setTexto(e.target.value), setEscribiendo(true))}
            onFocus={() => setEscribiendo(true)}
            placeholder="Canciones, artistas o álbumes"
            aria-label="Qué quieres escuchar"
            autoComplete="off"
            enterKeyHint="search"
          />
          {texto && (
            <button type="button" className="limpiar" onClick={() => (setTexto(""), campo.current?.focus())} aria-label="Borrar lo escrito">
              <Icono nombre="cerrar" />
            </button>
          )}
        </label>
        {escribiendo && (
          <button type="button" className="cancelar" onClick={() => (setTexto(consulta), setEscribiendo(false), campo.current?.blur())}>
            Cancelar
          </button>
        )}
      </form>

      {/* Mientras se escribe: sugerencias. */}
      {escribiendo && ideas.length > 0 && (
        <ul className="sugerencias" aria-label="Sugerencias">
          {ideas.map((idea) => (
            <li key={idea}>
              <button type="button" className="sugerencia" onMouseDown={(e) => e.preventDefault()} onClick={() => enviar(idea)}>
                <Icono nombre="buscar" />
                <span>{idea}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {/* Sin búsqueda: recientes y momentos. */}
      {!consulta && !(escribiendo && ideas.length) && (
        <>
          {recientes.length > 0 && (
            <>
              <h2 className="seccion">
                Recientes
                <button type="button" className="boton-texto" onClick={borrarRecientes}>
                  Borrar
                </button>
              </h2>
              <ul>
                {recientes.map((r) =>
                  r.tipo === "busqueda" ? (
                    <li key={`b-${r.texto}`}>
                      <button type="button" className="fila" onClick={() => enviar(r.texto)}>
                        <span className="ficha-ico">
                          <Icono nombre="reloj" />
                        </span>
                        <span className="fila-textos">
                          <span className="fila-cuerpo">
                            <b>{r.texto}</b>
                            <span>Búsqueda</span>
                          </span>
                        </span>
                      </button>
                    </li>
                  ) : (
                    <li key={`c-${r.cancion.id}`}>
                      <button type="button" className="fila" onClick={() => tocar(r.cancion)}>
                        <Caratula cancion={r.cancion} />
                        <span className="fila-textos">
                          <span className="fila-cuerpo">
                            <b>{r.cancion.titulo}</b>
                            <span>Canción · {r.cancion.artista}</span>
                          </span>
                        </span>
                      </button>
                    </li>
                  ),
                )}
              </ul>
            </>
          )}
          <h2 className="seccion">Explorar por momento</h2>
          <ul>
            {MOMENTOS.map((m) => (
              <li key={m.nombre}>
                <button type="button" className="fila momento" onClick={() => enviar(m.busqueda, "listas")}>
                  <span className="ficha-ico">
                    <Icono nombre={m.icono} />
                  </span>
                  <span className="fila-textos">
                    <span className="fila-cuerpo">
                      <b>{m.nombre}</b>
                    </span>
                    <Icono nombre="derecha" tam={18} className="fila-flecha" />
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </>
      )}

      {/* Con búsqueda: los alcances y los resultados. */}
      {consulta && !(escribiendo && ideas.length) && (
        <>
          <div className="alcances" role="tablist" aria-label="Tipo de resultado">
            {ALCANCES.map((a) => (
              <button
                key={a.tipo}
                type="button"
                role="tab"
                aria-selected={a.tipo === tipo}
                className={a.tipo === tipo ? "si" : ""}
                onClick={() => setParams({ q: consulta, tipo: a.tipo })}
              >
                {a.nombre}
              </button>
            ))}
          </div>
          {error && <SinConexion alReintentar={cargar} />}
          {!resultado && !error && <Cargando forma="lista" />}
          {resultado && vacio(resultado) && <SinResultados consulta={consulta} alProbar={(q) => enviar(q, tipo)} />}
          {resultado?.tipo === "todo" && !vacio(resultado) && (
            <>
              {resultado.datos.mejor && (
                <>
                  <h2 className="seccion">Mejor resultado</h2>
                  <Mejor e={resultado.datos.mejor} />
                </>
              )}
              {resultado.datos.secciones.map((s) =>
                !s.elementos.length ? null : s.tipo === "canciones" ? (
                  <section key={s.tipo}>
                    <h2 className="seccion">Canciones</h2>
                    <ListaCanciones canciones={s.elementos.flatMap((e) => (e.cancion ? [e.cancion] : []))} origen={`la búsqueda «${consulta}»`} />
                  </section>
                ) : (
                  <Carrusel key={s.tipo} titulo={s.tipo === "artistas" ? "Artistas" : "Álbumes"} elementos={s.elementos} />
                ),
              )}
            </>
          )}
          {resultado?.tipo === "canciones" && !vacio(resultado) && (
            <>
              <ListaCanciones canciones={resultado.canciones} origen={`la búsqueda «${consulta}»`} />
              {resultado.mas && (
                <button type="button" className="mas-resultados" onClick={cargarMas} disabled={cargandoMas}>
                  {cargandoMas ? "Buscando…" : "Más resultados"}
                </button>
              )}
            </>
          )}
          {resultado?.tipo === "otros" && !vacio(resultado) && (
            <ul>
              {resultado.elementos.map((e) => (
                <li key={e.id}>
                  <FilaElemento e={e} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
