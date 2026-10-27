import type { ReactNode } from "react";
import { Enlace, useIr } from "./Transicion";
import { Icono } from "./Iconos";
import { TituloMarca } from "./Marca";

type Props = {
  /** Con marca: el título de marca a la izquierda (Inicio). Si no, el botón de atrás. */
  marca?: boolean;
  atras?: boolean;
  acciones?: ReactNode;
};

/** La barra de arriba de cada pantalla en el teléfono. Instalada, la app no tiene el gesto de atrás del navegador,
    así que cada pantalla interior trae su propio botón. */
export function Cabecera({ marca = false, atras = false, acciones }: Props) {
  const ir = useIr();
  return (
    <div className="cabecera">
      {marca ? (
        <TituloMarca alto={20} />
      ) : atras ? (
        <button type="button" className="boton-vidrio vidrio" onClick={ir.atras} aria-label="Atrás">
          <Icono nombre="atras" />
        </button>
      ) : (
        <span />
      )}
      <div className="cabecera-acciones">
        {acciones}
        {marca && (
          <Enlace to="/ajustes" className="boton-vidrio vidrio" etiqueta="Ajustes">
            <Icono nombre="ajustes" />
          </Enlace>
        )}
      </div>
    </div>
  );
}
