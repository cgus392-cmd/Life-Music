/* Entrar a la app: la bienvenida de la primera visita (con el aviso legal y el del bloqueo), la guía para
   instalarla desde Safari y el botón «Instalar la app» del computador. */
import { useEffect, useState, type ReactNode } from "react";
import { usarAjustes } from "../datos/ajustes";
import { Hoja } from "./Hoja";
import { Icono } from "./Iconos";

/** La app abierta desde su ícono (instalada), no desde el navegador. */
export const instalada = () =>
  matchMedia("(display-mode: standalone)").matches || (navigator as Navigator & { standalone?: boolean }).standalone === true;

export const esIphone = () => /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

const MARCA = (
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
);

/** Los pasos de instalar desde Safari, con dibujos propios (los usa la bienvenida y la guía). */
function PasosInstalar() {
  return (
    <ol className="guia-pasos">
      <li>
        <span className="dibujo dibujo-anillo">
          <Icono nombre="compartir" />
        </span>
        <p>
          En la barra de Safari toca <b>Compartir</b> (o <b>···</b> y luego <b>Compartir</b>).
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
        <p>Ábrela desde su ícono: abre a pantalla completa, como una app.</p>
      </li>
    </ol>
  );
}

/** El avance de la bienvenida: la línea del logo con la cabeza de la nota en el paso actual (nunca puntos sueltos). */
function Avance({ paso, total }: { paso: number; total: number }) {
  return (
    <div className="avance" role="progressbar" aria-valuemin={1} aria-valuemax={total} aria-valuenow={paso + 1} aria-label={`Paso ${paso + 1} de ${total}`}>
      <span className="avance-linea">
        <i style={{ width: `${(paso / Math.max(1, total - 1)) * 100}%` }} />
      </span>
      <span className="avance-cabeza" style={{ left: `${(paso / Math.max(1, total - 1)) * 100}%` }}>
        <svg viewBox="-12 -9 24 18" aria-hidden="true">
          <ellipse rx="11.5" ry="8.5" transform="rotate(-22)" fill="#FF6B3D" />
        </svg>
      </span>
    </div>
  );
}

/** La bienvenida de la primera visita, en pasos. En el iPhone sin instalar agrega cómo instalarla; a todos les dice
    que es una versión de prueba. */
export function Bienvenida() {
  const { bienvenidaVista, fijar } = usarAjustes();
  const [paso, setPaso] = useState(0);
  const [dir, setDir] = useState<"adelante" | "atras">("adelante");
  if (bienvenidaVista) return null;

  const iphone = esIphone();
  const paginas: { clave: string; cuerpo: ReactNode }[] = [
    {
      clave: "hola",
      cuerpo: (
        <>
          {MARCA}
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
          </ul>
        </>
      ),
    },
  ];
  if (iphone && !instalada()) {
    paginas.push({
      clave: "instalar",
      cuerpo: (
        <>
          <span className="bienvenida-ico">
            <img src="/icono-192.png" alt="" />
          </span>
          <h2>Instálala en tu iPhone</h2>
          <p className="bienvenida-intro">Así se abre a pantalla completa, con su ícono y sin las barras de Safari, como una app.</p>
          <PasosInstalar />
        </>
      ),
    });
  }
  paginas.push({
    clave: "pruebas",
    cuerpo: (
      <>
        <span className="bienvenida-sello">Beta · versión {__VERSION__}</span>
        <h2>Estás en la fase de pruebas</h2>
        <p className="bienvenida-intro">
          Life Music Web todavía se está construyendo. Puede fallar o cambiar de un día para otro. Si algo no anda, cuéntale a
          quien te pasó el enlace: así la mejoramos.
        </p>
        {iphone && (
          <ul className="pasos">
            <li>
              <Icono nombre="candado" />
              <span>
                <b>Si bloqueas el iPhone, la música se pausa</b>
                <span>Es una regla de Safari, no un error. Al volver, sigue donde ibas. En Ajustes puedes dejar la pantalla encendida.</span>
              </span>
            </li>
          </ul>
        )}
        <div className="legal">
          <p>Life Music no está afiliado a YouTube ni a Google. La música la reproduce YouTube.</p>
          <p lang="en">Life Music is not affiliated with YouTube or Google. Music is played by YouTube.</p>
        </div>
      </>
    ),
  });

  const ultimo = paso === paginas.length - 1;
  const ir = (n: number) => {
    setDir(n > paso ? "adelante" : "atras");
    setPaso(n);
  };

  return (
    <div className="bienvenida" role="dialog" aria-modal="true" aria-labelledby="titulo-bienvenida">
      <Avance paso={paso} total={paginas.length} />
      <div key={paginas[paso].clave} className={`bienvenida-cuerpo entra-${dir}`}>
        {paginas[paso].cuerpo}
      </div>
      <div className="bienvenida-botones">
        {paso > 0 && (
          <button type="button" className="bienvenida-atras" onClick={() => ir(paso - 1)}>
            Atrás
          </button>
        )}
        <button
          type="button"
          className="boton-grande"
          onClick={() =>
            ultimo
              ? // Si la bienvenida ya explicó cómo instalarla, la guía no vuelve a salir (queda en Ajustes).
                fijar({ bienvenidaVista: true, guiaCerrada: paginas.some((p) => p.clave === "instalar") })
              : ir(paso + 1)
          }
        >
          {ultimo ? "Empezar" : "Siguiente"}
        </button>
      </div>
    </div>
  );
}

/** Solo en Safari del iPhone, cuando la app no está instalada. */
export function GuiaInstalar() {
  const { bienvenidaVista, guiaCerrada, fijar } = usarAjustes();
  const toca = bienvenidaVista && !guiaCerrada && esIphone() && !instalada();
  return (
    <Hoja abierta={toca} alCerrar={() => fijar({ guiaCerrada: true })} capa={false} className="guia" role="dialog" aria-labelledby="titulo-guia">
      <div className="guia-cab">
        <div>
          <h3 id="titulo-guia">Instálala en tu iPhone</h3>
          <p>Así abre a pantalla completa, con su ícono, como una app.</p>
        </div>
        <button type="button" className="boton-vidrio vidrio" onClick={() => fijar({ guiaCerrada: true })} aria-label="Cerrar">
          <Icono nombre="cerrar" />
        </button>
      </div>
      <PasosInstalar />
    </Hoja>
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
