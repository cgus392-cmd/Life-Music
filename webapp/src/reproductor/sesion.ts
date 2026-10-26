/* Media Session: la canción en las teclas multimedia del computador y en los controles del sistema. */
import type { Cancion } from "../datos/api";
import { usarReproductor } from "./tienda";

const ms = typeof navigator !== "undefined" ? navigator.mediaSession : undefined;

export function actualizarSesion(c: Cancion) {
  if (!ms) return;
  ms.metadata = new MediaMetadata({
    title: c.titulo,
    artist: c.artista,
    album: c.album ?? "",
    artwork: c.caratula ? [{ src: c.caratula, sizes: "544x544", type: "image/jpeg" }] : [],
  });
}

export function activarSesion() {
  if (!ms) return;
  const r = () => usarReproductor.getState();
  const accion = (nombre: MediaSessionAction, hacer: MediaSessionActionHandler) => {
    try {
      ms.setActionHandler(nombre, hacer);
    } catch {
      // El navegador no la soporta.
    }
  };
  accion("play", () => !r().sonando && r().alternar());
  accion("pause", () => r().sonando && r().alternar());
  accion("nexttrack", () => r().siguiente());
  accion("previoustrack", () => r().anterior());
  accion("seekto", (d) => d.seekTime != null && r().buscarEn(d.seekTime));
  usarReproductor.subscribe((s) => {
    ms.playbackState = s.indice < 0 ? "none" : s.sonando ? "playing" : "paused";
  });
}
