import { NavLink } from "react-router";
import { Icono } from "./Iconos";
import { TituloMarca } from "./Marca";
import { usarBiblioteca } from "../datos/biblioteca";
import { BotonInstalar } from "./Entrada";

/** La columna izquierda del computador: la marca, la navegación y tus listas. */
export function Lateral() {
  const listas = usarBiblioteca((s) => s.listas);
  return (
    <nav className="lateral" aria-label="Principal">
      <div className="lateral-marca">
        <TituloMarca alto={24} />
      </div>
      <NavLink to="/" end className="lateral-enlace">
        <Icono nombre="casa" />
        <span>Inicio</span>
      </NavLink>
      <NavLink to="/buscar" className="lateral-enlace">
        <Icono nombre="buscar" />
        <span>Buscar</span>
      </NavLink>
      <NavLink to="/biblioteca" className="lateral-enlace">
        <Icono nombre="biblioteca" />
        <span>Biblioteca</span>
      </NavLink>

      <p className="lateral-titulo">Tus listas</p>
      <NavLink to="/biblioteca/megusta" className="lateral-lista">
        <span className="ficha">
          <Icono nombre="corazon" />
        </span>
        <span>Me gusta</span>
      </NavLink>
      {listas.map((l) => (
        <NavLink key={l.id} to={`/biblioteca/lista/${l.id}`} className="lateral-lista">
          <span className="ficha">
            {l.canciones[0]?.caratula ? <img className="portada" src={l.canciones[0].caratula} alt="" /> : <Icono nombre="listas" />}
          </span>
          <span>{l.nombre}</span>
        </NavLink>
      ))}

      <BotonInstalar />
      <p className="lateral-pie">Life Music by CG · CG LABS</p>
    </nav>
  );
}
