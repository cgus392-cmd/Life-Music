/* El motor: el reproductor oficial de YouTube, escondido fuera de la pantalla.
   Solo hace de parlante. Lo que la persona ve es Life Music (carátula y controles).

   Vive en #motor-yt, fuera de React (index.html): mover un iframe por la página lo
   recarga y corta la canción, así que nunca se mueve; solo cambia su CSS.

   Resguardos (plan web-1.0, 2.1):
   1. Si suena un anuncio, el reproductor se deja ver para poder saltarlo.
   2. Si ajustes-remotos.json trae "reproductorVisible": true, se ve siempre. */

type Oyentes = {
  alCambiar: (estado: number) => void;
  alFallar: (codigo: number) => void;
};

let player: YT.Player | null = null;
let listo: Promise<void> | null = null;

export function iniciarMotor(oyentes: Oyentes): Promise<void> {
  if (listo) return listo;
  listo = new Promise((resolver) => {
    window.onYouTubeIframeAPIReady = () => {
      player = new YT.Player("motor-yt", {
        width: 320,
        height: 180,
        playerVars: { playsinline: 1, rel: 0, iv_load_policy: 3, fs: 0, disablekb: 1, origin: location.origin },
        events: {
          onReady: () => resolver(),
          onStateChange: (e) => oyentes.alCambiar(e.data),
          onError: (e) => oyentes.alFallar(e.data),
        },
      });
    };
    const script = document.createElement("script");
    script.src = "https://www.youtube.com/iframe_api";
    document.head.append(script);
  });
  return listo;
}

export const motor = {
  cargar: (id: string) => player?.loadVideoById(id),
  reanudar: () => player?.playVideo(),
  pausar: () => player?.pauseVideo(),
  ir: (s: number) => player?.seekTo(s, true),
  volumen: (v: number) => player?.setVolume(v),
  tiempo: () => player?.getCurrentTime?.() ?? 0,
  duracion: () => player?.getDuration?.() ?? 0,
};

export function mostrarMotor(motivo: "anuncio" | "interruptor" | null) {
  const caja = document.getElementById("motor");
  if (!caja) return;
  caja.classList.toggle("motor--visible", motivo !== null);
  caja.classList.toggle("motor--anuncio", motivo === "anuncio");
  caja.setAttribute("aria-hidden", String(motivo === null));
  const aviso = caja.querySelector(".motor-aviso");
  if (aviso) aviso.textContent = motivo === "anuncio" ? "Anuncio de YouTube" : "Reproductor de YouTube";
}

declare global {
  interface Window {
    onYouTubeIframeAPIReady?: () => void;
  }
}
