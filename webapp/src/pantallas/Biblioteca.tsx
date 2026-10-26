/* La biblioteca: Me gusta, Mis listas, Historial, y la entrada a Ajustes y Acerca de. Todo vive en este teléfono. */
import { Link, useNavigate, useParams } from "react-router";
import { usarBiblioteca } from "../datos/biblioteca";
import { Cabecera } from "../componentes/Cabecera";
import { ListaCanciones } from "../componentes/Elementos";
import { Caratula } from "../componentes/Caratula";
import { Icono, type NombreIcono } from "../componentes/Iconos";

function Entrada({ a, icono, texto, color = false }: { a: string; icono: NombreIcono; texto: string; color?: boolean }) {
  return (
    <Link to={a} className="fila menu-fila">
      <Icono nombre={icono} className={color ? "color" : ""} />
      <span className="fila-textos">
        <span className="fila-cuerpo">
          <b>{texto}</b>
        </span>
        <Icono nombre="derecha" tam={18} className="fila-flecha" />
      </span>
    </Link>
  );
}

/** Biblioteca vacía: un corazón con la cabeza de la nota en la punta. Una invitación, no una disculpa. */
function Vacia() {
  return (
    <div className="vacio">
      <svg viewBox="0 0 100 100" className="vacio-dibujo" aria-hidden="true">
        <path d="M50 86 C30 72 12 58 12 38 A20 20 0 0 1 50 28 A20 20 0 0 1 88 38 C88 58 70 72 54 82" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
        <ellipse cx="50" cy="86" rx="11.5" ry="8.5" transform="rotate(-22 50 86)" fill="var(--coral)" />
      </svg>
      <h3>Empieza tu biblioteca</h3>
      <p>Toca el corazón de una canción y aparece aquí. Se guarda en este teléfono, sin cuenta.</p>
      <Link to="/buscar" className="accion-texto">
        Buscar música
      </Link>
    </div>
  );
}

export function Biblioteca() {
  const { megusta, historial, listas } = usarBiblioteca();
  const nada = !megusta.length && !historial.length && !listas.length;
  return (
    <section aria-labelledby="titulo-biblioteca">
      <div className="cabecera" />
      <h1 id="titulo-biblioteca" className="titulo-grande">
        Biblioteca
      </h1>
      <nav className="menu" aria-label="Biblioteca">
        <Entrada a="/biblioteca/megusta" icono="corazon" texto="Me gusta" color />
        <Entrada a="/biblioteca/listas" icono="listas" texto="Mis listas" color />
        <Entrada a="/biblioteca/historial" icono="reloj" texto="Historial" color />
        <div className="grupo-sep" />
        <Entrada a="/ajustes" icono="ajustes" texto="Ajustes" color />
        <Entrada a="/acerca" icono="info" texto="Acerca de" color />
      </nav>
      {nada && <Vacia />}
      {megusta.length > 0 && (
        <>
          <h2 className="seccion">Agregado hace poco</h2>
          <ListaCanciones canciones={megusta.slice(0, 6)} origen="tus Me gusta" />
        </>
      )}
      {!megusta.length && historial.length > 0 && (
        <>
          <h2 className="seccion">Escuchado hace poco</h2>
          <ListaCanciones canciones={historial.slice(0, 6)} origen="tu historial" />
        </>
      )}
    </section>
  );
}

/** Me gusta e Historial: una lista que se reproduce entera. */
export function ListaPropia({ cual }: { cual: "megusta" | "historial" }) {
  const canciones = usarBiblioteca((s) => (cual === "megusta" ? s.megusta : s.historial));
  const titulo = cual === "megusta" ? "Me gusta" : "Historial";
  return (
    <section aria-labelledby="titulo-propia">
      <Cabecera atras />
      <h1 id="titulo-propia" className="titulo-grande">
        {titulo}
      </h1>
      <p className="subtitulo-pantalla">{canciones.length} canciones · guardadas en este teléfono</p>
      {canciones.length ? <ListaCanciones canciones={canciones} origen={cual === "megusta" ? "tus Me gusta" : "tu historial"} /> : <Vacia />}
    </section>
  );
}

/** Mis listas: las que la persona creó en este teléfono. */
export function MisListas() {
  const { listas, crearLista } = usarBiblioteca();
  const ir = useNavigate();
  const nueva = () => {
    const nombre = prompt("Nombre de la lista");
    if (nombre?.trim()) ir(`/biblioteca/lista/${crearLista(nombre.trim())}`);
  };
  return (
    <section aria-labelledby="titulo-listas">
      <Cabecera atras />
      <h1 id="titulo-listas" className="titulo-grande">
        Mis listas
      </h1>
      <ul className="menu">
        <li>
          <button type="button" className="fila menu-fila" onClick={nueva}>
            <span className="ficha-ico">
              <Icono nombre="mas" />
            </span>
            <span className="fila-textos">
              <span className="fila-cuerpo">
                <b>Nueva lista</b>
              </span>
            </span>
          </button>
        </li>
        {listas.map((l) => (
          <li key={l.id}>
            <Link to={`/biblioteca/lista/${l.id}`} className="fila">
              {l.canciones[0] ? (
                <Caratula cancion={l.canciones[0]} />
              ) : (
                <span className="ficha-ico">
                  <Icono nombre="listas" />
                </span>
              )}
              <span className="fila-textos">
                <span className="fila-cuerpo">
                  <b>{l.nombre}</b>
                  <span>{l.canciones.length} canciones</span>
                </span>
                <Icono nombre="derecha" tam={18} className="fila-flecha" />
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Una lista propia: se reproduce, se renombra y se borra. */
export function ListaGuardada() {
  const { id = "" } = useParams();
  const ir = useNavigate();
  const { listas, renombrarLista, borrarLista } = usarBiblioteca();
  const l = listas.find((x) => x.id === id);
  if (!l) return <Vacia />;
  return (
    <section aria-labelledby="titulo-lista-propia">
      <Cabecera
        atras
        acciones={
          <>
            <button
              type="button"
              className="boton-vidrio vidrio"
              aria-label="Cambiar el nombre"
              onClick={() => {
                const nombre = prompt("Nuevo nombre", l.nombre);
                if (nombre?.trim()) renombrarLista(l.id, nombre.trim());
              }}
            >
              <Icono nombre="lista" />
            </button>
            <button
              type="button"
              className="boton-vidrio vidrio"
              aria-label="Borrar la lista"
              onClick={() => {
                if (confirm(`¿Borrar «${l.nombre}»? Las canciones no se borran de Me gusta.`)) {
                  borrarLista(l.id);
                  ir("/biblioteca/listas");
                }
              }}
            >
              <Icono nombre="papelera" />
            </button>
          </>
        }
      />
      <h1 id="titulo-lista-propia" className="titulo-grande">
        {l.nombre}
      </h1>
      <p className="subtitulo-pantalla">{l.canciones.length} canciones</p>
      {l.canciones.length ? (
        <ListaCanciones canciones={l.canciones} origen={`«${l.nombre}»`} />
      ) : (
        <p className="pantalla-aviso">Agrega canciones desde el menú ··· de cualquier canción.</p>
      )}
    </section>
  );
}
