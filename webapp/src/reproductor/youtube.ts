/* El motor: el reproductor oficial de YouTube, escondido fuera de la pantalla.
   Solo hace de parlante. Lo que la persona ve es Life Music (carátula y controles).

   Hay dos reproductores: el que suena (activo) y el relevo, que el Automix usa para que la siguiente canción entre
   mientras la anterior se apaga. Al terminar la mezcla cambian de papel.

   Viven en #motor, fuera de React (index.html): mover un iframe por la página lo recarga y corta la canción, así
   que nunca se mueven; solo cambia su CSS.

   Resguardos (plan web-1.0, 2.1):
   1. Si suena un anuncio, el reproductor se deja ver para poder saltarlo.
   2. Si ajustes-remotos.json trae "reproductorVisible": true, se ve siempre. */

type Oyentes = {
  alCambiar: (estado: number) => void;
  alFallar: (codigo: number) => void;
};

const jugadores: (YT.Player | null)[] = [null, null];
let activo = 0;
let listo: Promise<void> | null = null;
/** Los reproductores ya respondieron. Antes de eso existen, pero sin sus funciones: tocar una canción en el primer
    segundo de la app fallaba («loadVideoById is not a function»). Lo que se pida antes queda en espera. */
let preparados = false;
let enEspera: string | null = null;

const p = () => jugadores[activo];
const relevoJugador = () => jugadores[1 - activo];

/** Solo el reproductor activo puede verse (en la hoja del anuncio); el relevo siempre queda fuera de la pantalla. */
function marcarActivo() {
  [0, 1].forEach((n) => document.getElementById(`motor-yt-${n}`)?.classList.toggle("relevo", n !== activo));
}

export function iniciarMotor(oyentes: Oyentes): Promise<void> {
  if (listo) return listo;
  listo = new Promise((resolver) => {
    window.onYouTubeIframeAPIReady = () => {
      let listos = 0;
      [0, 1].forEach((n) => {
        jugadores[n] = new YT.Player(`motor-yt-${n}`, {
          width: 320,
          height: 180,
          playerVars: { playsinline: 1, rel: 0, iv_load_policy: 3, fs: 0, disablekb: 1, origin: location.origin },
          events: {
            onReady: () => {
              if (++listos < 2) return;
              marcarActivo();
              preparados = true;
              if (enEspera) p()?.loadVideoById(enEspera);
              enEspera = null;
              resolver();
            },
            // Lo que haga el relevo solo le importa al Automix.
            onStateChange: (e) => n === activo && oyentes.alCambiar(e.data),
            onError: (e) => n === activo && oyentes.alFallar(e.data),
          },
        });
      });
    };
    const script = document.createElement("script");
    script.src = "https://www.youtube.com/iframe_api";
    document.head.append(script);
  });
  return listo;
}

export const motor = {
  cargar: (id: string) => {
    if (preparados) p()?.loadVideoById(id);
    else enEspera = id;
  },
  reanudar: () => p()?.playVideo?.(),
  pausar: () => p()?.pauseVideo?.(),
  ir: (s: number) => p()?.seekTo?.(s, true),
  volumen: (v: number) => p()?.setVolume?.(v),
  tiempo: () => p()?.getCurrentTime?.() ?? 0,
  duracion: () => p()?.getDuration?.() ?? 0,
};

/** El segundo reproductor, para el Automix: arranca la siguiente canción en silencio y sube su volumen. */
export const relevo = {
  cargar: (id: string) => {
    relevoJugador()?.setVolume?.(0);
    relevoJugador()?.loadVideoById?.(id);
  },
  volumen: (v: number) => relevoJugador()?.setVolume?.(v),
  detener: () => relevoJugador()?.stopVideo?.(),
};

/** Al terminar la mezcla, el relevo pasa a ser el que suena. */
export function cambiarDeReproductor() {
  activo = 1 - activo;
  marcarActivo();
}

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
