/* El artista: la foto arriba, como un álbum, Reproducir y Aleatorio, sus canciones populares y sus filas. */
import { useEffect, useState } from "react";
import { useParams } from "react-router";
import { useIr } from "../componentes/Transicion";
import { artista, type Artista as Datos } from "../datos/api";
import { usarReproductor } from "../reproductor/tienda";
import { Carrusel, ListaCanciones } from "../componentes/Elementos";
import { Cargando, SinConexion } from "../componentes/Estados";
import { Icono } from "../componentes/Iconos";

export function Artista() {
  const { id = "" } = useParams();
  const ir = useIr();
  const [datos, setDatos] = useState<Datos | null>(null);
  const [error, setError] = useState(false);
  const r = usarReproductor();

  const cargar = () => {
    setError(false);
    setDatos(null);
    artista(id).then(setDatos, () => setError(true));
  };
  useEffect(cargar, [id]);

  const reproducir = (aleatorio: boolean) => {
    if (!datos?.populares.length) return;
    if (aleatorio !== r.aleatorio) r.alternarAleatorio();
    // La radio del artista: arranca con su canción más escuchada y sigue sola.
    r.tocar(aleatorio ? datos.populares[Math.floor(Math.random() * datos.populares.length)] : datos.populares[0]);
  };

  return (
    <section className="artista" aria-labelledby="titulo-artista">
      <div className="artista-foto">
        {datos?.foto && <img src={datos.foto} alt="" referrerPolicy="no-referrer" />}
        <div className="artista-velo" />
        <div className="artista-barra">
          <button type="button" className="boton-vidrio vidrio" onClick={ir.atras} aria-label="Atrás">
            <Icono nombre="atras" />
          </button>
        </div>
        {datos && (
          <div className="artista-nombre">
            {datos.oyentes && <p>{datos.oyentes}</p>}
            <h1 id="titulo-artista">{datos.nombre}</h1>
          </div>
        )}
      </div>
      {error && <SinConexion alReintentar={cargar} />}
      {!datos && !error && <Cargando forma="lista" />}
      {datos && (
        <>
          <div className="pastillas artista-pastillas">
            <button type="button" className="pastilla" onClick={() => reproducir(false)}>
              <Icono nombre="play" /> Reproducir
            </button>
            <button type="button" className="pastilla" onClick={() => reproducir(true)}>
              <Icono nombre="aleatorio" /> Aleatorio
            </button>
          </div>
          {datos.populares.length > 0 && (
            <>
              <h2 className="seccion">Populares</h2>
              <ListaCanciones canciones={datos.populares} origen={`lo más escuchado de ${datos.nombre}`} numeros detalle={(c) => c.album ?? c.artista} />
            </>
          )}
          {datos.filas.map((f) => (
            <Carrusel key={f.titulo} titulo={f.titulo} elementos={f.elementos} />
          ))}
        </>
      )}
    </section>
  );
}
