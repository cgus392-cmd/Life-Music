/* Prepara el modo de demostración (demo.ts): tema claro, biblioteca y reproductor con canciones de ejemplo. */
import { CANCIONES } from "./demo";
import { usarAjustes } from "./datos/ajustes";
import { usarBiblioteca } from "./datos/biblioteca";
import { congelar, usarReproductor } from "./reproductor/tienda";

/** El color de «Luz de medianoche», la canción de ejemplo que suena. */
const COLOR = "#FF8A3D";

export function prepararDemo(modo: string) {
  // Las zonas seguras de un iPhone (la isla arriba, la raya abajo), que el navegador de captura no tiene.
  // Para la captura no hay animaciones: el navegador de captura no las corre y todo quedaría a medio entrar.
  document.documentElement.classList.add("demo");
  const raiz = document.documentElement.style;
  raiz.setProperty("--arriba", "54px");
  raiz.setProperty("--pestanas-abajo", "24px");
  raiz.setProperty("--mini-abajo", "98px");
  // Lo del demo se guarda aparte: antes pisaba los ajustes y la biblioteca de verdad de quien abriera la página
  // (dejaba el tema claro, el historial de ejemplo y borraba los Me gusta).
  usarAjustes.persist.setOptions({ name: "lifemusic-ajustes-demo" });
  usarBiblioteca.persist.setOptions({ name: "lifemusic-biblioteca-demo" });
  usarAjustes.setState({ tema: "claro", fondo: "apple", bienvenidaVista: modo !== "bienvenida", guiaCerrada: modo !== "guia" });
  usarBiblioteca.setState({ historial: CANCIONES.slice(1, 6).map((c) => ({ ...c, cuando: Date.now() })), megusta: [] });
  if (modo === "arranque" || modo === "bienvenida") return;
  congelar();
  usarReproductor.setState({ cola: CANCIONES, indice: 0, sonando: true, cargando: false, tiempo: 81, duracion: 215, color: COLOR });
  document.documentElement.style.setProperty("--cancion", COLOR);
}
