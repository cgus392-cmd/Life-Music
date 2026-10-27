/* El menú de una canción: una hoja que sube y flota a 8 pt de los bordes, con esquinas de 46 pt, como en iOS 26. */
import { useRef, useState, type MouseEvent, type PointerEvent } from "react";
import { create } from "zustand";
import type { Cancion } from "../datos/api";
import { usarReproductor } from "../reproductor/tienda";
import { esMeGusta, usarBiblioteca } from "../datos/biblioteca";
import { Caratula } from "./Caratula";
import { Hoja } from "./Hoja";
import { Icono, type NombreIcono } from "./Iconos";
import { useIr } from "./Transicion";

type Menu = { cancion: Cancion | null; abrir: (c: Cancion) => void; cerrar: () => void };

export const usarMenu = create<Menu>()((set) => ({
  cancion: null,
  abrir: (cancion) => set({ cancion }),
  cerrar: () => set({ cancion: null }),
}));

/** Cuánto hay que mantener presionado para abrir el menú (ms), como Haptic Touch. */
const MANTENER_MS = 450;

/** El dedo que abrió el menú se levanta encima de la hoja: ese clic no cuenta, ni cierra el menú ni reproduce. Van
    fuera del componente para que poner y quitar el bloqueo use siempre la misma función. */
const bloquear = (e: Event) => {
  e.preventDefault();
  e.stopPropagation();
};
const desbloquear = () => removeEventListener("click", bloquear, { capture: true });

/** Mantener presionada una canción abre su menú, como Haptic Touch en iOS (plan 0.4, punto 2.3); en el computador,
    el clic derecho. Mientras se mantiene, la fila o la tarjeta se hunde poco a poco (la clase «manteniendo»).
    Moverse más de 8 px es desplazar, no mantener; y el toque que abrió el menú no reproduce la canción. */
export function useMantener(cancion: Cancion) {
  const abrir = usarMenu((s) => s.abrir);
  const [manteniendo, setManteniendo] = useState(false);
  const toque = useRef<{ x: number; y: number; reloj: number; abrio: boolean } | null>(null);

  const soltar = () => {
    const t = toque.current;
    if (!t) return;
    clearTimeout(t.reloj);
    setManteniendo(false);
    // El clic llega justo después de levantar el dedo; pasado eso, todo vuelve a la normalidad.
    if (t.abrio) setTimeout(desbloquear, 400);
    toque.current = null;
  };

  return {
    manteniendo,
    props: {
      onPointerDown: (e: PointerEvent) => {
        // Con mouse está el clic derecho; la manija de la cola y el ··· tienen lo suyo.
        if (e.pointerType === "mouse" || (e.target as HTMLElement).closest(".manija, .fila-mas")) return;
        const reloj = window.setTimeout(() => {
          if (toque.current) toque.current.abrio = true;
          setManteniendo(false);
          addEventListener("click", bloquear, { capture: true, once: true });
          setTimeout(desbloquear, 3000);
          abrir(cancion);
        }, MANTENER_MS);
        toque.current = { x: e.clientX, y: e.clientY, reloj, abrio: false };
        setManteniendo(true);
      },
      onPointerMove: (e: PointerEvent) => {
        const t = toque.current;
        if (t && !t.abrio && Math.hypot(e.clientX - t.x, e.clientY - t.y) > 8) soltar();
      },
      onPointerUp: soltar,
      onPointerCancel: soltar,
      onContextMenu: (e: MouseEvent) => {
        e.preventDefault();
        // En Android, mantener presionado también dispara este evento: si el menú ya se abrió, no se repite.
        if (toque.current?.abrio) return;
        soltar();
        abrir(cancion);
      },
    },
  };
}

function Renglon({ icono, texto, alTocar, peligro = false }: { icono: NombreIcono; texto: string; alTocar: () => void; peligro?: boolean }) {
  return (
    <button type="button" className={`renglon${peligro ? " peligro" : ""}`} onClick={alTocar}>
      <Icono nombre={icono} />
      <span className="renglon-t">{texto}</span>
    </button>
  );
}

export function MenuCancion() {
  const { cancion, cerrar } = usarMenu();
  const ir = useIr();
  const r = usarReproductor();
  const { alternarMeGusta, listas, agregarALista, crearLista } = usarBiblioteca();
  const [eligiendoLista, setEligiendoLista] = useState(false);
  const [hecho, setHecho] = useState<string | null>(null);
  // Mientras la hoja baja, sigue mostrando la última canción (en la tienda ya es null). Cada vez que se abre,
  // aunque sea con la misma canción, el menú empieza limpio.
  const [c, setC] = useState(cancion);
  const [antes, setAntes] = useState(cancion);
  if (cancion !== antes) {
    setAntes(cancion);
    if (cancion) {
      setC(cancion);
      setEligiendoLista(false);
      setHecho(null);
    }
  }
  const megusta = esMeGusta(c?.id);

  if (!c) return null;

  // El aviso («Agregada a la cola») se queda mientras la hoja baja, para que no cambie de tamaño al irse.
  const listo = (mensaje: string) => {
    setHecho(mensaje);
    setTimeout(cerrar, 900);
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
    <Hoja abierta={!!cancion} alCerrar={cerrar} className="menu-cancion" role="dialog" aria-modal="true" aria-label={`Opciones de «${c.titulo}»`}>
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
            {c.albumId && <Renglon icono="disco" texto="Ir al álbum" alTocar={() => (cerrar(), ir.a(`/album/${c.albumId}`))} />}
            {c.artistaId && <Renglon icono="persona" texto="Ir al artista" alTocar={() => (cerrar(), ir.a(`/artista/${c.artistaId}`))} />}
          </div>
          <div className="grupo">
            <Renglon icono="compartir" texto="Compartir" alTocar={compartir} />
          </div>
        </>
      )}
    </Hoja>
  );
}
