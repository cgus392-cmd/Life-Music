import { useEffect, useState } from "react";
import { Cabecera } from "../componentes/Cabecera";
import { Carrusel, ListaCanciones } from "../componentes/Elementos";
import { Cargando, SinConexion } from "../componentes/Estados";
import { inicio, type Elemento, type Fila } from "../datos/api";
import { usarBiblioteca } from "../datos/biblioteca";

/** Cuántas canciones de una fila de lista se muestran en el Inicio. */
const CANCIONES_POR_FILA = 6;

export function Inicio() {
  const [filas, setFilas] = useState<Fila[] | null>(null);
  const [error, setError] = useState(false);
  const historial = usarBiblioteca((s) => s.historial);

  const cargar = () => {
    setError(false);
    inicio().then((r) => setFilas(r.filas), () => setError(true));
  };
  useEffect(cargar, []);

  const seguir: Elemento[] = historial.slice(0, 12).map((c) => ({
    tipo: "cancion",
    id: c.id,
    titulo: c.titulo,
    subtitulo: c.artista,
    caratula: c.caratula,
    cancion: c,
  }));

  return (
    <section className="inicio" aria-labelledby="titulo-inicio">
      <Cabecera marca />
      <h1 id="titulo-inicio" className="titulo-grande">
        Inicio
      </h1>
      {seguir.length > 0 && <Carrusel titulo="Seguir escuchando" elementos={seguir} origen="tu historial" />}
      {error && <SinConexion alReintentar={cargar} />}
      {!filas && !error && <Cargando forma="inicio" />}
      {filas?.map((f) =>
        f.forma === "lista" ? (
          <section key={f.titulo} className="fila-inicio">
            <h2 className="seccion">{f.titulo}</h2>
            <div className="lista-inicio">
              <ListaCanciones
                canciones={f.elementos.flatMap((e) => (e.cancion ? [e.cancion] : [])).slice(0, CANCIONES_POR_FILA)}
                origen={`«${f.titulo}»`}
                detalle={(c) => c.artista}
              />
            </div>
          </section>
        ) : (
          <Carrusel key={f.titulo} titulo={f.titulo} elementos={f.elementos} />
        ),
      )}
    </section>
  );
}
