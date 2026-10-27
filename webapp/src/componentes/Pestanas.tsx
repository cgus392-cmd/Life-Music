import { Pestana } from "./Transicion";
import { Icono } from "./Iconos";

/** La barra de pestañas flotante del teléfono, de vidrio. */
export function Pestanas() {
  return (
    <nav className="pestanas vidrio" aria-label="Principal">
      <Pestana to="/" end>
        <Icono nombre="casa" />
        <span>Inicio</span>
      </Pestana>
      <Pestana to="/buscar">
        <Icono nombre="buscar" />
        <span>Buscar</span>
      </Pestana>
      <Pestana to="/biblioteca">
        <Icono nombre="biblioteca" />
        <span>Biblioteca</span>
      </Pestana>
    </nav>
  );
}
