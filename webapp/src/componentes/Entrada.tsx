/* Entrar a la app: la bienvenida de la primera visita (con el aviso legal y el del bloqueo), la guía para
   instalarla desde Safari y el botón «Instalar la app» del computador. */
import { useEffect, useState } from "react";
import { usarAjustes } from "../datos/ajustes";
import { Icono } from "./Iconos";

/** La app abierta desde su ícono (instalada), no desde el navegador. */
export const instalada = () =>
  matchMedia("(display-mode: standalone)").matches || (navigator as Navigator & { standalone?: boolean }).standalone === true;

const esIphone = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

export function Bienvenida() {
  const { bienvenidaVista, fijar } = usarAjustes();
  if (bienvenidaVista) return null;
  return (
    <div className="bienvenida" role="dialog" aria-modal="true" aria-labelledby="titulo-bienvenida">
      <div className="bienvenida-cuerpo">
        <svg viewBox="-14 0 120 100" className="bienvenida-marca" aria-hidden="true">
          <path
            d="M28 31 Q28 22 21 18 Q13 14 12 8 V80 Q12 92 24 92 Q36 92 36 80 V58 Q36 42 49 42 Q62 42 62 58 V90 V58 Q62 42 75 42 Q88 42 88 58 V90"
            fill="none"
            stroke="currentColor"
            strokeWidth="9"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <ellipse cx="2.5" cy="85" rx="11.5" ry="8.5" transform="rotate(-22 2.5 85)" fill="#FF6B3D" />
        </svg>
        <h2 id="titulo-bienvenida">
          Hola. Esto es
          <br />
          Life Music.
        </h2>
        <p className="bienvenida-intro">Un reproductor hecho por CG LABS para aprender. Gratis y de código abierto.</p>
        <ul className="pasos">
          <li>
            <Icono nombre="buscar" />
            <span>
              <b>Busca lo que quieras oír</b>
              <span>Canciones, artistas y álbumes de YouTube Music.</span>
            </span>
          </li>
          <li>
            <Icono nombre="corazon" />
            <span>
              <b>Guarda lo que te gusta</b>
              <span>Tu biblioteca se queda en este teléfono. No hace falta cuenta.</span>
            </span>
          </li>
          {esIphone() && (
            <li>
              <Icono nombre="candado" />
              <span>
                <b>Si bloqueas el iPhone, la música se pausa</b>
                <span>Es una regla de Safari, no un error. Al volver, sigue donde ibas.</span>
              </span>
            </li>
          )}
        </ul>
        <div className="legal">
          <p>Life Music no está afiliado a YouTube ni a Google. La música la reproduce YouTube.</p>
          <p lang="en">Life Music is not affiliated with YouTube or Google. Music is played by YouTube.</p>
        </div>
      </div>
      <button type="button" className="boton-grande" onClick={() => fijar({ bienvenidaVista: true })}>
        Empezar
      </button>
    </div>
  );
}

/** Solo en Safari del iPhone, cuando la app no está instalada. */
export function GuiaInstalar() {
  const { bienvenidaVista, guiaCerrada, fijar } = usarAjustes();
  if (!bienvenidaVista || guiaCerrada || !esIphone() || instalada()) return null;
  return (
    <div className="hoja-flotante vidrio guia" role="dialog" aria-labelledby="titulo-guia">
      <div className="guia-cab">
        <div>
          <h3 id="titulo-guia">Instálala en tu iPhone</h3>
          <p>Así abre a pantalla completa, con su ícono, como una app.</p>
        </div>
        <button type="button" className="boton-vidrio vidrio" onClick={() => fijar({ guiaCerrada: true })} aria-label="Cerrar">
          <Icono nombre="cerrar" />
        </button>
      </div>
      <ol className="guia-pasos">
        <li>
          <span className="dibujo dibujo-anillo">
            <Icono nombre="puntos" />
          </span>
          <p>
            Toca <b>···</b> o <b>Compartir</b> en la barra de Safari.
          </p>
        </li>
        <li>
          <span className="dibujo">
            <Icono nombre="mas" />
          </span>
          <p>
            Elige <b>Agregar a inicio</b> y toca <b>Agregar</b>.
          </p>
        </li>
        <li>
          <span className="dibujo dibujo-icono">
            <img src="/icono-192.png" alt="" />
          </span>
          <p>Ábrela desde su ícono. Ya no la tienes que buscar en Safari.</p>
        </li>
      </ol>
    </div>
  );
}

type AvisoInstalar = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> };

/** «Instalar la app» en Chrome y Edge del computador (y Android). Safari no avisa, por eso allá va la guía. */
export function BotonInstalar() {
  const [aviso, setAviso] = useState<AvisoInstalar | null>(null);
  useEffect(() => {
    const guardar = (e: Event) => {
      e.preventDefault();
      setAviso(e as AvisoInstalar);
    };
    addEventListener("beforeinstallprompt", guardar);
    addEventListener("appinstalled", () => setAviso(null));
    return () => removeEventListener("beforeinstallprompt", guardar);
  }, []);
  if (!aviso) return null;
  return (
    <div className="instalar">
      <button
        type="button"
        className="instalar-boton"
        onClick={async () => {
          await aviso.prompt();
          await aviso.userChoice;
          setAviso(null);
        }}
      >
        <Icono nombre="instalar" tam={20} /> Instalar la app
      </button>
      <p>Se abre en su propia ventana, como un programa.</p>
    </div>
  );
}
