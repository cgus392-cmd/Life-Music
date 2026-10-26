import { useRef, useState, type PointerEvent } from "react";
import { usarReproductor } from "../reproductor/tienda";
import { reloj, type Cancion } from "../datos/api";
import { esMeGusta, usarBiblioteca } from "../datos/biblioteca";
import { usarAjustes } from "../datos/ajustes";
import { BarraTiempo } from "./BarraTiempo";
import { Caratula } from "./Caratula";
import { FilaCancion } from "./FilaCancion";
import { Icono } from "./Iconos";

/** Cuánto hay que arrastrar (px) para cerrar o cambiar de canción. */
const UMBRAL_CERRAR = 120;
const UMBRAL_CAMBIAR = 80;
/** Alto de una fila de la cola, para reordenar arrastrando. */
const ALTO_FILA = 58;

/** Los fondos de la app Android (PlayerBackgroundStyle): Apple Music por defecto y Live Mesh en Ajustes.
    Van con <img> y no con background-image para tener respaldo si la carátula no carga. */
function Fondo({ c }: { c: Cancion }) {
  const estilo = usarAjustes((s) => s.fondo);
  if (estilo === "mesh") {
    return (
      <div className="ahora-fondo fondo-mesh" aria-hidden="true">
        <Caratula cancion={c} lado={128} className="capa-mesh" />
        <Caratula cancion={c} lado={128} className="capa-mesh" />
        <Caratula cancion={c} lado={128} className="capa-mesh" />
        <div className="ahora-velo" />
      </div>
    );
  }
  return (
    <div className="ahora-fondo fondo-apple" aria-hidden="true">
      <Caratula cancion={c} lado={128} className="ahora-borroso" />
      <Caratula cancion={c} lado={1080} className="ahora-nitida" />
      <div className="ahora-velo" />
    </div>
  );
}

/** La cola: sube desde abajo. Se reordena arrastrando la manija de cada fila. */
function Cola({ alCerrar }: { alCerrar: () => void }) {
  const r = usarReproductor();
  const c = r.cola[r.indice];
  const siguen = r.cola.slice(r.indice + 1);
  const arrastre = useRef<{ de: number; y: number } | null>(null);
  const [desplazo, setDesplazo] = useState<{ i: number; dy: number } | null>(null);

  const empezar = (i: number) => (e: PointerEvent) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    arrastre.current = { de: i, y: e.clientY };
    setDesplazo({ i, dy: 0 });
  };
  const mover = (e: PointerEvent) => {
    if (arrastre.current) setDesplazo({ i: arrastre.current.de, dy: e.clientY - arrastre.current.y });
  };
  const soltar = (e: PointerEvent) => {
    const a = arrastre.current;
    arrastre.current = null;
    setDesplazo(null);
    if (!a) return;
    const pasos = Math.round((e.clientY - a.y) / ALTO_FILA);
    if (pasos) r.mover(r.indice + 1 + a.de, Math.min(r.cola.length - 1, Math.max(r.indice + 1, r.indice + 1 + a.de + pasos)));
  };

  if (!c) return null;
  return (
    <div className="cola-capa">
      <button type="button" className="cola-compacto" onClick={alCerrar} aria-label="Volver a Ahora suena">
        <Caratula cancion={c} />
        <span>
          <b>{c.titulo}</b>
          <span>{c.artista}</span>
          <BarraTiempo variante="mini" />
        </span>
      </button>
      <section className="cola-hoja" aria-label="A continuación">
        <span className="agarre" />
        <div className="cola-cab">
          <h3>A continuación</h3>
          <div>
            <button
              type="button"
              className={`boton-vidrio vidrio${r.aleatorio ? " activo" : ""}`}
              onClick={r.alternarAleatorio}
              aria-label="Aleatorio"
              aria-pressed={r.aleatorio}
            >
              <Icono nombre="aleatorio" />
            </button>
            <button
              type="button"
              className={`boton-vidrio vidrio${r.repetir !== "no" ? " activo" : ""}`}
              onClick={r.cambiarRepetir}
              aria-label="Repetir"
            >
              <Icono nombre={r.repetir === "una" ? "repetirUna" : "repetir"} />
            </button>
          </div>
        </div>
        <p className="cola-origen">
          {r.origen ? `De ${r.origen} · ` : ""}
          {siguen.length} canciones
        </p>
        <ol>
          {siguen.map((s, i) => (
            <li
              key={s.id}
              style={desplazo?.i === i ? { transform: `translateY(${desplazo.dy}px)`, position: "relative", zIndex: 2 } : undefined}
            >
              <FilaCancion
                cancion={s}
                detalle={s.artista}
                alTocar={() => r.irA(r.indice + 1 + i)}
                fin={
                  <span
                    className="manija"
                    onPointerDown={empezar(i)}
                    onPointerMove={mover}
                    onPointerUp={soltar}
                    onPointerCancel={soltar}
                    role="img"
                    aria-label="Arrastrar para reordenar"
                  >
                    <Icono nombre="arrastrar" tam={20} />
                  </span>
                }
              />
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}

/** «Ahora suena» a pantalla completa. Sube desde el mini reproductor y se cierra deslizando hacia abajo;
    deslizar la carátula de lado cambia de canción. */
export function AhoraSuena({ abierta, alCerrar }: { abierta: boolean; alCerrar: () => void }) {
  const r = usarReproductor();
  const c = r.cola[r.indice];
  const megusta = esMeGusta(c?.id);
  const alternarMeGusta = usarBiblioteca((s) => s.alternarMeGusta);
  const [cola, setCola] = useState(false);
  const gesto = useRef<{ x: number; y: number } | null>(null);
  const [arrastre, setArrastre] = useState(0);

  if (!c) return null;

  const empezar = (e: PointerEvent) => {
    if ((e.target as HTMLElement).closest("button, input, .cola-hoja")) return;
    gesto.current = { x: e.clientX, y: e.clientY };
  };
  const mover = (e: PointerEvent) => {
    if (!gesto.current) return;
    const dy = e.clientY - gesto.current.y;
    if (dy > 0 && Math.abs(dy) > Math.abs(e.clientX - gesto.current.x)) setArrastre(dy);
  };
  const soltar = (e: PointerEvent) => {
    const g = gesto.current;
    gesto.current = null;
    setArrastre(0);
    if (!g) return;
    const dx = e.clientX - g.x;
    const dy = e.clientY - g.y;
    if (dy > UMBRAL_CERRAR && dy > Math.abs(dx)) alCerrar();
    else if (Math.abs(dx) > UMBRAL_CAMBIAR && Math.abs(dx) > Math.abs(dy)) (dx < 0 ? r.siguiente : r.anterior)();
  };

  const compartir = async () => {
    const datos = { title: c.titulo, text: `${c.titulo} · ${c.artista} en Life Music`, url: location.origin };
    try {
      if (navigator.share) await navigator.share(datos);
      else await navigator.clipboard.writeText(`${datos.text} ${datos.url}`);
    } catch {
      // La persona canceló.
    }
  };

  return (
    <div
      className={`ahora sobre-imagen${abierta ? " ahora--abierta" : ""}`}
      style={arrastre ? { transform: `translateY(${arrastre}px)`, transition: "none" } : undefined}
      role="dialog"
      aria-modal="true"
      aria-label="Ahora suena"
      aria-hidden={!abierta}
      onPointerDown={empezar}
      onPointerMove={mover}
      onPointerUp={soltar}
      onPointerCancel={() => ((gesto.current = null), setArrastre(0))}
    >
      <Fondo c={c} />
      <button type="button" className="agarre-boton" onClick={alCerrar} aria-label="Cerrar">
        <span className="agarre" />
      </button>
      <button type="button" className="boton-vidrio vidrio ahora-compartir" onClick={compartir} aria-label="Compartir">
        <Icono nombre="compartir" />
      </button>

      {cola ? (
        <Cola alCerrar={() => setCola(false)} />
      ) : (
        <div className="ahora-info">
          <div className="ahora-titulo">
            <div>
              <h2>{c.titulo}</h2>
              <p>{c.artista}</p>
              {r.aviso && <p className="aviso">{r.aviso}</p>}
            </div>
            <button
              type="button"
              className="boton-icono"
              onClick={() => alternarMeGusta(c)}
              aria-label={megusta ? "Quitar de Me gusta" : "Me gusta"}
              aria-pressed={megusta}
            >
              <Icono nombre={megusta ? "corazonLleno" : "corazon"} tam={28} />
            </button>
          </div>
          {r.anuncio ? (
            <p className="ahora-anuncio">Suena un anuncio de YouTube.</p>
          ) : (
            <>
              <BarraTiempo variante="ahora" />
              <div className="tiempos">
                <span>{reloj(r.tiempo)}</span>
                <span>-{reloj(Math.max(0, r.duracion - r.tiempo))}</span>
              </div>
            </>
          )}
          <div className="ahora-controles">
            <button type="button" className="boton-icono" onClick={r.anterior} aria-label="Anterior">
              <Icono nombre="anterior" tam={42} />
            </button>
            <button type="button" className="boton-icono grande" onClick={r.alternar} aria-label={r.sonando ? "Pausa" : "Reproducir"}>
              <Icono nombre={r.sonando ? "pausa" : "play"} tam={60} />
            </button>
            <button type="button" className="boton-icono" onClick={r.siguiente} aria-label="Siguiente">
              <Icono nombre="siguiente" tam={42} />
            </button>
          </div>
          <div className="ahora-extras vidrio">
            <button
              type="button"
              className={`boton-icono${r.aleatorio ? " activo" : ""}`}
              onClick={r.alternarAleatorio}
              aria-label="Aleatorio"
              aria-pressed={r.aleatorio}
            >
              <Icono nombre="aleatorio" />
            </button>
            <button type="button" className="boton-icono" onClick={() => setCola(true)} aria-label="Cola">
              <Icono nombre="cola" />
            </button>
            <button
              type="button"
              className={`boton-icono${r.repetir !== "no" ? " activo" : ""}`}
              onClick={r.cambiarRepetir}
              aria-label="Repetir"
            >
              <Icono nombre={r.repetir === "una" ? "repetirUna" : "repetir"} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
