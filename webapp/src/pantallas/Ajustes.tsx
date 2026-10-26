/* Ajustes y Acerca de, en grupos de iOS. */
import { usarAjustes, type FondoAhora, type Tema } from "../datos/ajustes";
import { usarBiblioteca } from "../datos/biblioteca";
import { Cabecera } from "../componentes/Cabecera";
import { Icono } from "../componentes/Iconos";
import { Logo, Nota } from "../componentes/Marca";

function Segmentos<T extends string>({ valor, opciones, alCambiar, etiqueta }: {
  valor: T;
  opciones: { valor: T; nombre: string }[];
  alCambiar: (v: T) => void;
  etiqueta: string;
}) {
  return (
    <div className="segmentos" role="radiogroup" aria-label={etiqueta}>
      {opciones.map((o) => (
        <button key={o.valor} type="button" role="radio" aria-checked={o.valor === valor} className={o.valor === valor ? "si" : ""} onClick={() => alCambiar(o.valor)}>
          {o.nombre}
        </button>
      ))}
    </div>
  );
}

export function Ajustes() {
  const a = usarAjustes();
  const borrarTodo = usarBiblioteca((s) => s.borrarTodo);

  return (
    <section aria-labelledby="titulo-ajustes" className="ajustes">
      <Cabecera atras />
      <h1 id="titulo-ajustes" className="titulo-grande">
        Ajustes
      </h1>

      <h2 className="grupo-titulo">Apariencia</h2>
      <div className="grupo">
        <div className="renglon">
          <Icono nombre="contraste" />
          <span className="renglon-t">Tema</span>
        </div>
        <Segmentos<Tema>
          etiqueta="Tema"
          valor={a.tema}
          alCambiar={(tema) => a.fijar({ tema })}
          opciones={[
            { valor: "auto", nombre: "Automático" },
            { valor: "claro", nombre: "Claro" },
            { valor: "oscuro", nombre: "Oscuro" },
          ]}
        />
        <div className="renglon">
          <Icono nombre="fondo" />
          <span className="renglon-t">Fondo de «Ahora suena»</span>
        </div>
        <Segmentos<FondoAhora>
          etiqueta="Fondo de Ahora suena"
          valor={a.fondo}
          alCambiar={(fondo) => a.fijar({ fondo })}
          opciones={[
            { valor: "apple", nombre: "Apple Music" },
            { valor: "mesh", nombre: "Live Mesh" },
          ]}
        />
        <div className="renglon">
          <Icono nombre="globo" />
          <span className="renglon-t">
            Idioma <em>Español</em>
          </span>
        </div>
      </div>

      <h2 className="grupo-titulo">Reproducción</h2>
      <div className="grupo">
        <label className="renglon">
          <Icono nombre="pantalla" />
          <span className="renglon-t">
            Pantalla siempre encendida
            <input
              type="checkbox"
              role="switch"
              className="interruptor"
              checked={a.pantallaEncendida}
              onChange={(e) => a.fijar({ pantallaEncendida: e.target.checked })}
            />
          </span>
        </label>
      </div>
      <p className="pie-grupo">Mientras suena, el iPhone no se bloquea y la música no se pausa.</p>

      <div className="grupo">
        <button
          type="button"
          className="renglon peligro"
          onClick={() => {
            if (confirm("¿Borrar tu biblioteca, tu historial y tus ajustes de este teléfono? No se puede deshacer.")) {
              borrarTodo();
              a.fijar({ tema: "auto", fondo: "apple", pantallaEncendida: false });
            }
          }}
        >
          <Icono nombre="papelera" />
          <span className="renglon-t">Borrar mis datos</span>
        </button>
      </div>
      <p className="pie-grupo">Borra tu biblioteca, tu historial y tus ajustes de este teléfono.</p>
    </section>
  );
}

const CADENA = [
  { nombre: "InnerTune", autor: "Zion Huang" },
  { nombre: "Metrolist y Vivi Music" },
  { nombre: "Echo Music" },
];

export function Acerca() {
  return (
    <section aria-labelledby="titulo-acerca" className="acerca">
      <Cabecera atras />
      <h1 id="titulo-acerca" className="oculto">
        Acerca de
      </h1>
      <div className="acerca-logo">
        <Logo alto={34} />
      </div>
      <p className="acerca-version">Life Music Web · versión {__VERSION__}</p>
      <p className="acerca-frase">Un reproductor para aprender, hecho por CG LABS. Gratis, sin cuenta y de código abierto.</p>

      <h2 className="origen-titulo">De dónde viene · todo bajo GPL-3.0</h2>
      <ol className="cadena">
        {CADENA.map((c) => (
          <li key={c.nombre}>
            {c.nombre} {c.autor && <span>{c.autor}</span>}
          </li>
        ))}
        <li className="cadena-fin">
          <Nota alto={18} />
          Life Music
        </li>
      </ol>

      <div className="grupo">
        <a className="renglon" href="https://github.com/cgus392-cmd/Life-Music" target="_blank" rel="noreferrer">
          <Icono nombre="codigo" />
          <span className="renglon-t">
            Código fuente <em>GitHub</em>
          </span>
        </a>
        <a className="renglon" href="https://www.gnu.org/licenses/gpl-3.0.html" target="_blank" rel="noreferrer">
          <Icono nombre="info" />
          <span className="renglon-t">Licencia GPL-3.0</span>
        </a>
      </div>

      <div className="firma">
        <img className="solo-claro" src="/marca/cglabs_lockup_negro.webp" alt="CG LABS" />
        <img className="solo-oscuro" src="/marca/cglabs_lockup_blanco.webp" alt="CG LABS" />
        <p>© 2026 CG LABS · No afiliado a YouTube ni a Google</p>
        <p lang="en">Not affiliated with YouTube or Google</p>
      </div>
    </section>
  );
}
