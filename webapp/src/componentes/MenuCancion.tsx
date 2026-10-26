/* El menú de una canción: una hoja que sube y flota a 8 pt de los bordes, con esquinas de 46 pt, como en iOS 26. */
import { useState } from "react";
import { useNavigate } from "react-router";
import { create } from "zustand";
import type { Cancion } from "../datos/api";
import { usarReproductor } from "../reproductor/tienda";
import { esMeGusta, usarBiblioteca } from "../datos/biblioteca";
import { Caratula } from "./Caratula";
import { Icono, type NombreIcono } from "./Iconos";

type Menu = { cancion: Cancion | null; abrir: (c: Cancion) => void; cerrar: () => void };

export const usarMenu = create<Menu>()((set) => ({
  cancion: null,
  abrir: (cancion) => set({ cancion }),
  cerrar: () => set({ cancion: null }),
}));

function Renglon({ icono, texto, alTocar, peligro = false }: { icono: NombreIcono; texto: string; alTocar: () => void; peligro?: boolean }) {
  return (
    <button type="button" className={`renglon${peligro ? " peligro" : ""}`} onClick={alTocar}>
      <Icono nombre={icono} />
      <span className="renglon-t">{texto}</span>
    </button>
  );
}

export function MenuCancion() {
  const { cancion: c, cerrar } = usarMenu();
  const ir = useNavigate();
  const r = usarReproductor();
  const megusta = esMeGusta(c?.id);
  const { alternarMeGusta, listas, agregarALista, crearLista } = usarBiblioteca();
  const [eligiendoLista, setEligiendoLista] = useState(false);
  const [hecho, setHecho] = useState<string | null>(null);

  if (!c) return null;

  const listo = (mensaje: string) => {
    setHecho(mensaje);
    setTimeout(() => {
      setHecho(null);
      setEligiendoLista(false);
      cerrar();
    }, 900);
  };

  const compartir = async () => {
    const datos = { title: c.titulo, text: `${c.titulo} · ${c.artista} en Life Music`, url: location.origin };
    try {
      if (navigator.share) await navigator.share(datos);
      else await navigator.clipboard.writeText(`${datos.text} ${datos.url}`);
    } catch {
      // La persona canceló.
    }
    cerrar();
  };

  return (
    <div className="hoja-capa" onClick={cerrar}>
      <div className="hoja-flotante vidrio menu-cancion" role="dialog" aria-modal="true" aria-label={`Opciones de «${c.titulo}»`} onClick={(e) => e.stopPropagation()}>
        <div className="menu-cab">
          <Caratula cancion={c} />
          <span>
            <b>{c.titulo}</b>
            <span>{c.artista}</span>
          </span>
          <button type="button" className="boton-vidrio vidrio" onClick={cerrar} aria-label="Cerrar">
            <Icono nombre="cerrar" />
          </button>
        </div>

        {hecho ? (
          <p className="menu-hecho">{hecho}</p>
        ) : eligiendoLista ? (
          <div className="grupo">
            <Renglon
              icono="mas"
              texto="Nueva lista"
              alTocar={() => {
                const nombre = prompt("Nombre de la lista");
                if (!nombre?.trim()) return;
                agregarALista(crearLista(nombre.trim()), c);
                listo(`Agregada a «${nombre.trim()}»`);
              }}
            />
            {listas.map((l) => (
              <Renglon key={l.id} icono="listas" texto={l.nombre} alTocar={() => (agregarALista(l.id, c), listo(`Agregada a «${l.nombre}»`))} />
            ))}
          </div>
        ) : (
          <>
            <div className="grupo">
              <Renglon icono="aContinuacion" texto="Reproducir a continuación" alTocar={() => (r.reproducirDespues(c), listo("Suena después de esta"))} />
              <Renglon icono="cola" texto="Agregar a la cola" alTocar={() => (r.agregarACola(c), listo("Agregada a la cola"))} />
              <Renglon icono="mas" texto="Agregar a una lista" alTocar={() => setEligiendoLista(true)} />
            </div>
            <div className="grupo">
              <Renglon
                icono={megusta ? "corazonLleno" : "corazon"}
                texto={megusta ? "Quitar de Me gusta" : "Me gusta"}
                alTocar={() => (alternarMeGusta(c), listo(megusta ? "Quitada de Me gusta" : "Guardada en Me gusta"))}
              />
              {c.albumId && <Renglon icono="disco" texto="Ir al álbum" alTocar={() => (cerrar(), ir(`/album/${c.albumId}`))} />}
              {c.artistaId && <Renglon icono="persona" texto="Ir al artista" alTocar={() => (cerrar(), ir(`/artista/${c.artistaId}`))} />}
            </div>
            <div className="grupo">
              <Renglon icono="compartir" texto="Compartir" alTocar={compartir} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}
