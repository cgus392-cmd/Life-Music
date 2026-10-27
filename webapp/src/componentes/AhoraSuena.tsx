import { useEffect, useRef, useState, type CSSProperties, type PointerEvent } from "react";
import { usarReproductor } from "../reproductor/tienda";
import { reloj, type Cancion } from "../datos/api";
import { esMeGusta, usarBiblioteca } from "../datos/biblioteca";
import { usarAjustes } from "../datos/ajustes";
import { puedeMezclar } from "../datos/dispositivo";
import { BarraTiempo } from "./BarraTiempo";
import { CaratulaSuave } from "./Caratula";
import { Deslizador, Desliza } from "./Deslizador";
import { FilaCancion } from "./FilaCancion";
import { Letras } from "./Letras";
import { Icono, IconoApple } from "./Iconos";
import { usarMenu } from "./MenuCancion";
import { useIr } from "./Transicion";

/** Cuánto hay que arrastrar (px) para cerrar o cambiar de canción. */
const UMBRAL_CERRAR = 120;
const UMBRAL_CAMBIAR = 80;
/** Alto de una fila de la cola, para reordenar arrastrando. */
const ALTO_FILA = 58;
/** Cuánto hay que bajar la hoja de la cola (px) para cerrarla. */
const UMBRAL_HOJA = 100;

/** Los fondos de la app Android (PlayerBackgroundStyle): Apple Music por defecto y Live Mesh en Ajustes.
    Van con <img> y no con background-image para tener respaldo si la carátula no carga. */
function Fondo({ c }: { c: Cancion }) {
  const estilo = usarAjustes((s) => s.fondo);
  if (estilo === "mesh") {
    return (
      <div className="ahora-fondo fondo-mesh" aria-hidden="true">
        <CaratulaSuave cancion={c} lado={128} className="capa-mesh" />
        <CaratulaSuave cancion={c} lado={128} className="capa-mesh" />
        <CaratulaSuave cancion={c} lado={128} className="capa-mesh" />
        <div className="ahora-velo" />
      </div>
    );
  }
  // Al cambiar de canción, el fondo y la carátula grande se funden en 1,2 s, como en Android (plan 0.4, punto 1.5).
  return (
    <div className="ahora-fondo fondo-apple" aria-hidden="true">
      <CaratulaSuave cancion={c} lado={128} className="ahora-borroso" />
      <CaratulaSuave cancion={c} lado={1080} className="ahora-nitida" />
      <div className="ahora-velo" />
    </div>
  );
}

/** «A continuación»: sube como hoja de iOS y la carátula grande se recoge en la miniatura de arriba (ahora.css).
    Se cierra tocando la canción de arriba o bajando la hoja con el dedo. Se reordena arrastrando la manija de cada
    fila. Siempre está montada, para poder animar la entrada y la salida. */
function Cola({ abierta, alCerrar }: { abierta: boolean; alCerrar: () => void }) {
  const r = usarReproductor();
  const c = r.cola[r.indice];
  const siguen = r.cola.slice(r.indice + 1);
  const arrastre = useRef<{ de: number; y: number } | null>(null);
  const [desplazo, setDesplazo] = useState<{ i: number; dy: number } | null>(null);
  // Bajar la hoja desde su cabecera: sigue al dedo y, pasado el umbral, se cierra.
  const tiron = useRef<number | null>(null);
  const [bajada, setBajada] = useState(0);

  const tirar = (e: PointerEvent) => {
    if ((e.target as HTMLElement).closest("button")) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    tiron.current = e.clientY;
  };
  const arrastrarHoja = (e: PointerEvent) => {
    if (tiron.current !== null) setBajada(Math.max(0, e.clientY - tiron.current));
  };
  const soltarHoja = () => {
    if (tiron.current === null) return;
    tiron.current = null;
    if (bajada > UMBRAL_HOJA) alCerrar();
    setBajada(0);
  };

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
    <div className={`cola-capa${abierta ? " abierta" : ""}`} inert={!abierta}>
      <button type="button" className="cola-compacto" onClick={alCerrar} aria-label="Volver a Ahora suena">
        <CaratulaSuave cancion={c} />
        <span>
          <b>{c.titulo}</b>
          <span>{c.artista}</span>
          <BarraTiempo variante="mini" />
        </span>
      </button>
      <section
        className="cola-hoja"
        aria-label="A continuación"
        style={bajada ? { transform: `translateY(${bajada}px)`, transition: "none" } : undefined}
      >
        <div className="cola-tirador" onPointerDown={tirar} onPointerMove={arrastrarHoja} onPointerUp={soltarHoja} onPointerCancel={soltarHoja}>
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
        </div>
        {/* --i escalona la entrada de las primeras filas al abrir la hoja. */}
        <ol>
          {siguen.map((s, i) => (
            <li
              key={s.id}
              style={
                {
                  "--i": Math.min(i, 12),
                  ...(desplazo?.i === i ? { transform: `translateY(${desplazo.dy}px)`, position: "relative", zIndex: 2 } : {}),
                } as CSSProperties
              }
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

/** «Ahora suena» a pantalla completa, armado como el reproductor estilo Apple Music de la app Android (Player.kt y
    Queue.kt): título con ··· y Me gusta, la barra fina, los controles sin fondo, el volumen y abajo la cola, el
    grupo de botones y compartir. Sube desde el mini reproductor y se cierra deslizando hacia abajo; deslizar de lado
    cambia de canción. */
export function AhoraSuena({ abierta, alCerrar }: { abierta: boolean; alCerrar: () => void }) {
  const r = usarReproductor();
  const c = r.cola[r.indice];
  const megusta = esMeGusta(c?.id);
  const alternarMeGusta = usarBiblioteca((s) => s.alternarMeGusta);
  const abrirMenu = usarMenu((s) => s.abrir);
  const ir = useIr();
  const [cola, setCola] = useState(false);
  // La letra sincronizada (plan 0.4, punto 4.1): los controles siguen abajo y la carátula se recoge arriba.
  const [letra, setLetra] = useState(false);
  const gesto = useRef<{ x: number; y: number } | null>(null);
  const [arrastre, setArrastre] = useState(0);
  // Mientras se arrastra la barra del tiempo, los números muestran a dónde va; salta al soltar.
  const [buscando, setBuscando] = useState<number | null>(null);

  // Al cerrar «Ahora suena», la próxima vez abre en la carátula y no en la cola.
  useEffect(() => {
    if (!abierta) {
      setCola(false);
      setLetra(false);
    }
  }, [abierta]);

  if (!c) return null;
  const tiempo = buscando ?? r.tiempo;
  // El recuadro del medio de Android (calidad, «Cargando», Automix). Aquí no hay calidad que mostrar.
  const estado = r.mezclando ? "Automix" : r.cargando ? "Cargando" : null;
  // Solo la pausa de verdad recoge la carátula: mientras carga no, para que no se encoja a cada rato.
  const pausa = !r.sonando && !r.cargando;

  const empezar = (e: PointerEvent) => {
    if ((e.target as HTMLElement).closest("button, input, .deslizador, .cola-hoja, .letra-lineas")) return;
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

  // «Ahora suena» baja y la página se abre detrás, con su transición.
  const irA = (ruta: string) => {
    alCerrar();
    ir.a(ruta);
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
      className={`ahora sobre-imagen${abierta ? " ahora--abierta" : ""}${pausa ? " ahora--pausa" : ""}${cola ? " ahora--cola" : ""}${letra ? " ahora--letra" : ""}`}
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

      {/* La información no se desmonta al abrir la cola: se aparta con una transición y la cola sube encima. */}
      <div className="ahora-info" inert={cola}>
        <div className="ahora-titulo">
          {/* La clave hace que el título nuevo entre con un fundido al cambiar de canción. */}
          <div className="ahora-textos" key={c.id}>
            {/* Como en Android: el título lleva al álbum y el artista a su página (plan 0.4, punto 1.4). Si YouTube
                no dice cuál es, queda como texto. */}
            <h2>
              {c.albumId ? (
                <button type="button" className="ahora-enlace" onClick={() => irA(`/album/${c.albumId}`)} aria-label={`${c.titulo}, ir al álbum`}>
                  <Desliza>{c.titulo}</Desliza>
                </button>
              ) : (
                <Desliza>{c.titulo}</Desliza>
              )}
            </h2>
            <p>
              {c.explicita && (
                <abbr className="fila-explicita" title="Explícita">
                  E
                </abbr>
              )}
              {c.artistaId ? (
                <button type="button" className="ahora-enlace" onClick={() => irA(`/artista/${c.artistaId}`)} aria-label={`Ir a ${c.artista}`}>
                  <Desliza>{c.artista}</Desliza>
                </button>
              ) : (
                <Desliza>{c.artista}</Desliza>
              )}
            </p>
            {r.aviso && <p className="aviso">{r.aviso}</p>}
          </div>
          <button type="button" className="ahora-redondo" onClick={() => abrirMenu(c)} aria-label="Más opciones">
            <Icono nombre="puntos" />
          </button>
          <button
            type="button"
            className="ahora-redondo"
            onClick={() => alternarMeGusta(c)}
            aria-label={megusta ? "Quitar de Me gusta" : "Me gusta"}
            aria-pressed={megusta}
          >
            <Icono nombre={megusta ? "corazonLleno" : "corazon"} />
          </button>
        </div>

        {r.anuncio ? (
          <p className="ahora-anuncio">Suena un anuncio de YouTube.</p>
        ) : (
          <div className="ahora-tiempo">
            <Deslizador
              valor={tiempo}
              max={r.duracion}
              alMover={setBuscando}
              alSoltar={(v) => {
                r.buscarEn(v);
                setBuscando(null);
              }}
              etiqueta="Posición en la canción"
              texto={`${reloj(tiempo)} de ${reloj(r.duracion)}`}
            />
            <div className="tiempos">
              <span>{reloj(tiempo)}</span>
              {estado && <span className="ahora-estado">{estado}</span>}
              {/* Como Apple Music: a la derecha lo que falta, con el signo menos (mientras no se sabe, nada). */}
              <span>{r.duracion > 0 ? `−${reloj(Math.max(0, r.duracion - tiempo))}` : ""}</span>
            </div>
          </div>
        )}

        <div className="ahora-controles">
          <button type="button" onClick={r.anterior} aria-label="Anterior">
            <IconoApple nombre="anterior" tam={42} />
          </button>
          <button type="button" className="principal" onClick={r.alternar} aria-label={r.sonando ? "Pausa" : "Reproducir"}>
            <IconoApple nombre={r.sonando ? "pausa" : "play"} tam={80} />
          </button>
          <button type="button" onClick={r.siguiente} aria-label="Siguiente">
            <IconoApple nombre="siguiente" tam={42} />
          </button>
        </div>

        {/* En el iPhone una página no puede cambiar el volumen: ahí se usan los botones del teléfono. */}
        {puedeMezclar() && (
          <div className="ahora-volumen">
            <IconoApple nombre="volumenBajo" tam={16} />
            <Deslizador valor={r.silencio ? 0 : r.volumen} max={100} alMover={r.fijarVolumen} etiqueta="Volumen" />
            <IconoApple nombre="volumenAlto" tam={16} />
          </div>
        )}

        {/* Como Apple Music: íconos chicos y sin fondo; la letra a la izquierda, Compartir al centro y la cola a la
            derecha (plan 0.4, puntos 1.1 y 4.1). Aleatorio y repetir viven en «A continuación». */}
        <div className="ahora-abajo">
          <button
            type="button"
            className={`ahora-abajo-icono${letra ? " activo" : ""}`}
            onClick={() => setLetra(!letra)}
            aria-label={letra ? "Ocultar la letra" : "Letra"}
            aria-pressed={letra}
          >
            <Icono nombre="letra" />
          </button>
          <button type="button" className="ahora-abajo-icono" onClick={compartir} aria-label="Compartir">
            <Icono nombre="compartir" />
          </button>
          <button
            type="button"
            className="ahora-abajo-icono"
            onClick={() => {
              setLetra(false);
              setCola(true);
            }}
            aria-label="A continuación"
          >
            <IconoApple nombre="cola" tam={26} />
          </button>
        </div>
      </div>

      <Letras cancion={c} abierta={letra && !cola} alCerrar={() => setLetra(false)} />
      <Cola abierta={cola} alCerrar={() => setCola(false)} />
    </div>
  );
}
