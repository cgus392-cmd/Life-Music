/* Atajos de teclado. No actúan mientras se escribe en un campo. */
import { usarReproductor } from "./tienda";

export function activarAtajos() {
  window.addEventListener("keydown", (e) => {
    const destino = e.target as HTMLElement;
    if (destino.closest("input, textarea, select, [contenteditable]") || e.ctrlKey || e.metaKey || e.altKey) return;
    const r = usarReproductor.getState();
    if (e.key === " ") r.alternar();
    else if (e.key === "ArrowRight" && e.shiftKey) r.siguiente();
    else if (e.key === "ArrowLeft" && e.shiftKey) r.anterior();
    else if (e.key === "ArrowRight") r.buscarEn(Math.min(r.tiempo + 10, r.duracion));
    else if (e.key === "ArrowLeft") r.buscarEn(Math.max(r.tiempo - 10, 0));
    else if (e.key.toLowerCase() === "m") r.alternarSilencio();
    else return;
    e.preventDefault();
  });
}
