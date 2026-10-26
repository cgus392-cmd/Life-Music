/* «Pantalla siempre encendida» (Screen Wake Lock): mientras suena, el teléfono no se bloquea y la música no se
   pausa. Safari lo suelta cuando la página se esconde, así que se vuelve a pedir al regresar. */
import { usarAjustes } from "../datos/ajustes";
import { usarReproductor } from "./tienda";

let candado: WakeLockSentinel | null = null;

async function revisar() {
  const quiere = usarAjustes.getState().pantallaEncendida && usarReproductor.getState().sonando && document.visibilityState === "visible";
  if (quiere && !candado && "wakeLock" in navigator) {
    try {
      candado = await navigator.wakeLock.request("screen");
      candado.addEventListener("release", () => (candado = null));
    } catch {
      candado = null;
    }
  } else if (!quiere && candado) {
    await candado.release().catch(() => {});
    candado = null;
  }
}

export function activarPantallaEncendida() {
  usarAjustes.subscribe(revisar);
  usarReproductor.subscribe((s, antes) => s.sonando !== antes.sonando && revisar());
  document.addEventListener("visibilitychange", revisar);
}
