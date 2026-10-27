/* Un álbum o una lista: la carátula, el título, Reproducir y Aleatorio, y las canciones. Al bajar, la cabecera se
   encoge en una barra de vidrio con el título y el play. */
import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router";
import { Enlace, useIr } from "../componentes/Transicion";
import { album, lista, type Coleccion as Datos } from "../datos/api";
import { usarReproductor } from "../reproductor/tienda";
import { ListaCanciones } from "../componentes/Elementos";
import { Cargando, SinConexion } from "../componentes/Estados";
import { Icono } from "../componentes/Iconos";

export function Coleccion({ tipo }: { tipo: "album" | "lista" }) {
  const { id = "" } = useParams();
  const ir = useIr();
  const [datos, setDatos] = useState<Datos | null>(null);
  const [error, setError] = useState(false);
  const [encogida, setEncogida] = useState(false);
  const titulo = useRef<HTMLHeadingElement>(null);
  const r = usarReproductor();

  const cargar = () => {
    setError(false);
    setDatos(null);
    (tipo === "album" ? album(id) : lista(id)).then(setDatos, () => setError(true));
  };
  useEffect(cargar, [id, tipo]);

  // La cabecera se encoge cuando el título sale de la pantalla.
  useEffect(() => {
    const el = titulo.current;
    if (!el) return;
    const o = new IntersectionObserver(([e]) => setEncogida(!e.isIntersecting), { rootMargin: "-90px 0px 0px 0px" });
    o.observe(el);
    return () => o.disconnect();
  }, [datos]);

  const origen = datos ? `«${datos.titulo}»` : "";
  const reproducir = (aleatorio: boolean) => {
    if (!datos?.canciones.length) return;
    if (aleatorio !== r.aleatorio) r.alternarAleatorio();
    const primera = aleatorio ? datos.canciones[Math.floor(Math.random() * datos.canciones.length)] : datos.canciones[0];
    r.tocar(primera, datos.canciones, origen);
  };

  return (
    <section className="coleccion" aria-labelledby="titulo-coleccion">
      <div className={`barra-encogida vidrio${encogida ? " visible" : ""}`} aria-hidden={!encogida}>
        <button type="button" className="boton-vidrio" onClick={ir.atras} aria-label="Atrás">
          <Icono nombre="atras" />
        </button>
        <span className="titulo-bar">{datos?.titulo}</span>
        <button type="button" className="boton-vidrio" onClick={() => reproducir(false)} aria-label="Reproducir">
          <Icono nombre="play" />
        </button>
      </div>
      <div className="cabecera">
        <button type="button" className="boton-vidrio vidrio" onClick={ir.atras} aria-label="Atrás">
          <Icono nombre="atras" />
        </button>
      </div>

      {error && <SinConexion alReintentar={cargar} />}
      {!datos && !error && <Cargando forma="coleccion" />}
      {datos && (
        <>
          <div className="coleccion-cabeza">
            <img className="portada coleccion-portada" src={datos.caratula ?? undefined} alt="" referrerPolicy="no-referrer" />
            <div className="coleccion-info">
              <p className="coleccion-tipo">{datos.subtitulo.split(" • ")[0]}</p>
              <h1 id="titulo-coleccion" ref={titulo}>
                {datos.titulo}
              </h1>
              {datos.autor &&
                (datos.autorId ? (
                  <Enlace to={`/artista/${datos.autorId}`} className="coleccion-autor">
                    {datos.autor}
                  </Enlace>
                ) : (
                  <p className="coleccion-autor">{datos.autor}</p>
                ))}
              <p className="coleccion-meta">
                {[...datos.subtitulo.split(" • ").slice(1), ...datos.detalle.split(" • ")].filter(Boolean).join(" · ")}
              </p>
              <div className="pastillas">
                <button type="button" className="pastilla" onClick={() => reproducir(false)}>
                  <Icono nombre="play" /> Reproducir
                </button>
                <button type="button" className="pastilla" onClick={() => reproducir(true)}>
                  <Icono nombre="aleatorio" /> Aleatorio
                </button>
              </div>
            </div>
          </div>
          <ListaCanciones
            canciones={datos.canciones}
            origen={origen}
            numeros={tipo === "album"}
            sinPortada={tipo === "album"}
            detalle={(c) => c.artista}
          />
        </>
      )}
    </section>
  );
}
