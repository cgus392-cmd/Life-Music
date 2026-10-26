import { NavLink } from "react-router";
import { Icono } from "./Iconos";

/** La barra de pestañas flotante del teléfono, de vidrio. */
export function Pestanas() {
  return (
    <nav className="pestanas vidrio" aria-label="Principal">
      <NavLink to="/" end>
        <Icono nombre="casa" />
        <span>Inicio</span>
      </NavLink>
      <NavLink to="/buscar">
        <Icono nombre="buscar" />
        <span>Buscar</span>
      </NavLink>
      <NavLink to="/biblioteca">
        <Icono nombre="biblioteca" />
        <span>Biblioteca</span>
      </NavLink>
    </nav>
  );
}
