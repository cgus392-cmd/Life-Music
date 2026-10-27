import { usarReproductor } from "../reproductor/tienda";
import { BordeTiempo } from "./BordeTiempo";
import { CaratulaSuave } from "./Caratula";
import { Icono } from "./Iconos";

/** El mini reproductor del teléfono: flota sobre las pestañas, de vidrio, con su hilo y la cabeza de la nota. */
export function MiniReproductor({ alAbrir }: { alAbrir: () => void }) {
  const c = usarReproductor((s) => s.cola[s.indice]);
  const sonando = usarReproductor((s) => s.sonando);
  const aviso = usarReproductor((s) => s.aviso);
  const alternar = usarReproductor((s) => s.alternar);
  const siguiente = usarReproductor((s) => s.siguiente);
  if (!c) return null;

  return (
    <div className="mini vidrio">
      <button type="button" className="mini-abrir" onClick={alAbrir} aria-label={`Abrir «${c.titulo}»`}>
        <CaratulaSuave cancion={c} />
        {/* La clave hace que el título nuevo entre con un fundido al cambiar de canción. */}
        <span className="mini-textos" key={c.id}>
          <b>{c.titulo}</b>
          {aviso ? <span className="aviso">{aviso}</span> : <span>{c.artista}</span>}
        </span>
      </button>
      <button type="button" className="boton-icono" onClick={alternar} aria-label={sonando ? "Pausa" : "Reproducir"}>
        <Icono nombre={sonando ? "pausa" : "play"} />
      </button>
      <button type="button" className="boton-icono" onClick={siguiente} aria-label="Siguiente">
        <Icono nombre="siguiente" />
      </button>
      <BordeTiempo />
    </div>
  );
}
