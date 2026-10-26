/* «Después suena»: dentro de la hoja del anuncio, la canción que sigue cuando YouTube termine su anuncio.
   Se monta con un portal dentro de #motor, junto al reproductor, sin moverlo. */
import { createPortal } from "react-dom";
import { usarReproductor } from "../reproductor/tienda";
import { Caratula } from "./Caratula";

export function AnuncioDespues() {
  const c = usarReproductor((s) => s.cola[s.indice]);
  const destino = document.getElementById("motor-despues");
  if (!c || !destino) return null;
  return createPortal(
    <div className="despues">
      <Caratula cancion={c} />
      <span>
        <small>Después suena</small>
        <b>{c.titulo}</b>
      </span>
    </div>,
    destino,
  );
}
