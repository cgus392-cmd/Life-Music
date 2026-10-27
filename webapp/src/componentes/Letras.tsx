/* La letra sincronizada en «Ahora suena», como en Apple Music (plan 0.4, puntos 4.1 y 4.1b). Las ideas y los valores
   salen de la letra de nuestra app Android y de AMLL («Apple Music-like Lyrics», AGPL-3.0: se tomaron las ideas, no
   el código):
   - la línea que suena va a un 35 % de la altura; las demás, un poco más chicas y desenfocadas según la distancia;
   - al cambiar de línea, todas suben en ola, cada una un instante después de la anterior;
   - cada palabra se barre de izquierda a derecha con una orilla suave; las largas y sostenidas brillan y suben;
   - los coros van chicos debajo; la segunda voz de un dueto, a la derecha;
   - en las pausas largas, tres puntos que respiran; al final, quién la escribió y de dónde viene.
   Al desplazar con el dedo todo se ve nítido para leer; a los 3 s vuelve a seguir la canción. */
import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react";
import type { Cancion } from "../datos/api";
import { buscarLetra, type Letra, type Linea, type Palabra } from "../datos/letras";
import { usarReproductor } from "../reproductor/tienda";
import { CaratulaSuave } from "./Caratula";

/** Tras desplazar la letra con el dedo, cuánto se espera antes de volver a seguir la canción (ms). */
const PAUSA_SEGUIR_MS = 3000;
/** Un pelín de adelanto: la línea se enciende justo cuando empieza a cantarse, no un instante después. */
const ADELANTO_S = 0.15;
/** Un silencio de esto o más (s) muestra los tres puntos. */
const PAUSA_MINIMA_S = 4;
/** Dónde queda la línea que suena, como fracción de la altura (Apple, según AMLL: 0,35). */
const ALTURA_LINEA = 0.35;
/** La ola: cada línea arranca este tanto después de la anterior (ms). */
const RETRASO_OLA_MS = 40;
/** Hasta cuánto se desenfoca una línea (px), como en AMLL. */
const BORROSO_MAXIMO = 5;

type Elemento = { tipo: "linea"; linea: Linea; inicio: number } | { tipo: "pausa"; inicio: number; fin: number };

/** Las líneas y, entre ellas, las pausas instrumentales largas (también antes de la primera línea). */
function elementosDe(letra: Letra): Elemento[] {
  const lista: Elemento[] = [];
  let antes = 0;
  for (const linea of letra.lineas) {
    if (letra.sincronizada && linea.inicio - antes >= PAUSA_MINIMA_S) lista.push({ tipo: "pausa", inicio: antes, fin: linea.inicio });
    lista.push({ tipo: "linea", linea, inicio: linea.inicio });
    antes = Math.max(antes, linea.fin);
  }
  return lista;
}

/** La regla de Apple para el brillo (según AMLL): una palabra de 1 s o más y de 2 a 7 letras. Solo con tiempos
    reales, no repartidos. */
const conEnfasis = (p: Palabra) => !p.sintetica && p.fin - p.inicio >= 1 && p.texto.trim().length >= 2 && p.texto.trim().length <= 7;

/** Las sílabas en palabras que no se parten al final del renglón; cada sílaba lleva sus tiempos para el barrido. */
function palabras(silabas: Palabra[]): ReactNode[] {
  const nodos: ReactNode[] = [];
  let grupo: ReactNode[] = [];
  silabas.forEach((s, i) => {
    grupo.push(
      <span key={i} className={`silaba${conEnfasis(s) ? " enfasis" : ""}`} data-a={s.inicio} data-b={s.fin}>
        {s.texto.trimEnd()}
      </span>,
    );
    if (/\s$/.test(s.texto) || i === silabas.length - 1) {
      nodos.push(
        <span key={`g${i}`} className="palabra">
          {grupo}
        </span>,
        " ",
      );
      grupo = [];
    }
  });
  return nodos;
}

/** «A, B y C». */
const enLista = (nombres: string[]) => (nombres.length < 2 ? nombres.join("") : `${nombres.slice(0, -1).join(", ")} y ${nombres[nombres.length - 1]}`);

const sinMovimiento = () => matchMedia("(prefers-reduced-motion: reduce)").matches;

export function Letras({ cancion: c, abierta, alCerrar }: { cancion: Cancion; abierta: boolean; alCerrar: () => void }) {
  const duracion = usarReproductor((s) => s.duracion);
  const tiempo = usarReproductor((s) => s.tiempo);
  const sonando = usarReproductor((s) => s.sonando);
  const buscarEn = usarReproductor((s) => s.buscarEn);
  // undefined: buscando; null: ninguno la tiene.
  const [letra, setLetra] = useState<Letra | null | undefined>(undefined);
  const elementos = useMemo(() => (letra ? elementosDe(letra) : []), [letra]);
  const [actual, setActual] = useState(-1);
  // Para el reloj del modo lectura, que vuelve a la línea que suene en ese momento y no a la de cuando se tocó.
  const actualAhora = useRef(actual);
  actualAhora.current = actual;
  const [leyendo, setLeyendo] = useState(false);
  const capa = useRef<HTMLDivElement>(null);
  const lista = useRef<HTMLDivElement>(null);
  const relojLeyendo = useRef(0);
  // El tiempo llega cada medio segundo; entre medio se cuenta solo, para que las palabras vayan a su ritmo.
  const base = useRef({ t: tiempo, a: performance.now() });
  base.current = base.current.t === tiempo ? base.current : { t: tiempo, a: performance.now() };

  // La letra se busca solo cuando se abre, una vez por canción, y cuando ya se sabe cuánto dura: con la duración se
  // elige la versión (la del video oficial no es la del álbum).
  const conDuracion = duracion > 0;
  useEffect(() => {
    if (!abierta) return;
    setLetra(undefined);
    setActual(-1);
    if (!conDuracion) return;
    let vigente = true;
    buscarLetra(c, duracion).then((l) => vigente && setLetra(l));
    return () => {
      vigente = false;
    };
    // Con la primera duración que haya basta.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierta, c.id, conDuracion]);

  // Cada cuadro: qué elemento suena (React solo repinta cuando cambia) y el barrido de las sílabas de esa línea,
  // directo en el DOM con las variables --p (avance) y --brillo (énfasis).
  useEffect(() => {
    if (!abierta || !letra?.sincronizada) return;
    let cuadro = 0;
    let vivo: { i: number; silabas: { el: HTMLElement; a: number; b: number; enfasis: boolean }[]; pausa?: HTMLElement } | null = null;
    const seguir = () => {
      const { t: t0, a } = base.current;
      const t = t0 + (sonando ? (performance.now() - a) / 1000 : 0) + ADELANTO_S;
      let i = -1;
      for (let k = 0; k < elementos.length && elementos[k].inicio <= t; k++) i = k;
      setActual((v) => (v === i ? v : i));
      if (!vivo || vivo.i !== i) {
        const el = lista.current?.querySelector<HTMLElement>(`[data-i="${i}"]`);
        vivo = {
          i,
          silabas: [...(el?.querySelectorAll<HTMLElement>(".silaba") ?? [])].map((s) => ({
            el: s,
            a: Number(s.dataset.a),
            b: Number(s.dataset.b),
            enfasis: s.classList.contains("enfasis"),
          })),
          pausa: el?.classList.contains("letra-pausa") ? el : undefined,
        };
      }
      for (const s of vivo.silabas) {
        const p = Math.min(1, Math.max(0, (t - s.a) / Math.max(0.05, s.b - s.a)));
        s.el.style.setProperty("--p", p.toFixed(3));
        // El brillo sube mientras se canta y se apaga en 0,6 s después.
        if (s.enfasis) s.el.style.setProperty("--brillo", (p < 1 ? p : Math.max(0, 1 - (t - s.b) / 0.6)).toFixed(3));
      }
      const e = elementos[i];
      if (vivo.pausa && e?.tipo === "pausa") vivo.pausa.style.setProperty("--p", Math.min(1, (t - e.inicio) / (e.fin - e.inicio)).toFixed(3));
      cuadro = requestAnimationFrame(seguir);
    };
    cuadro = requestAnimationFrame(seguir);
    return () => cancelAnimationFrame(cuadro);
  }, [abierta, letra, elementos, sonando]);

  /** Lleva el elemento i a un 35 % de la altura. Con ola: la lista salta de una y cada línea se anima desde donde
      estaba, con un retraso que crece hacia abajo (la técnica FLIP, que el iPhone hace con la tarjeta gráfica). */
  const llevarA = (i: number, ola: boolean) => {
    const caja = lista.current;
    const fila = caja?.querySelector<HTMLElement>(`[data-i="${Math.max(0, i)}"]`);
    if (!caja || !fila) return;
    const objetivo = Math.max(0, fila.offsetTop - caja.clientHeight * ALTURA_LINEA);
    const delta = objetivo - caja.scrollTop;
    if (Math.abs(delta) < 1) return;
    const filas = [...caja.querySelectorAll<HTMLElement>("[data-i]")];
    const animar = ola && !sinMovimiento() && Math.abs(delta) < caja.clientHeight;
    if (animar)
      for (const f of filas) {
        f.style.transition = "none";
        f.style.setProperty("--ola", `${delta}px`);
      }
    caja.scrollTop = objetivo;
    if (!animar) return;
    void caja.offsetHeight;
    for (const f of filas) {
      const k = Number(f.dataset.i) - i;
      f.style.transition = "";
      f.style.setProperty("--retraso", `${Math.max(0, Math.min(10, k + 1)) * RETRASO_OLA_MS}ms`);
      f.style.setProperty("--ola", "0px");
    }
  };

  // Cuando cambia la línea, la letra la sigue en ola, salvo que la persona esté leyendo por su cuenta.
  useLayoutEffect(() => {
    if (!leyendo && letra?.sincronizada) llevarA(actual, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [actual, letra]);

  // Tocar o desplazar la letra: modo lectura (todo nítido y quieto); a los 3 s sin tocar, vuelve a seguir.
  const leer = () => {
    setLeyendo(true);
    clearTimeout(relojLeyendo.current);
    relojLeyendo.current = window.setTimeout(() => {
      setLeyendo(false);
      llevarA(actualAhora.current, true);
    }, PAUSA_SEGUIR_MS);
  };
  useEffect(() => () => clearTimeout(relojLeyendo.current), []);

  // La letra ocupa el espacio entre la cabecera y los controles de abajo, que cambian de alto (el volumen no está
  // en el iPhone): se mide lo que ocupan y se deja la letra encima.
  useEffect(() => {
    const info = document.querySelector<HTMLElement>(".ahora .ahora-info");
    const el = capa.current;
    if (!abierta || !info || !el) return;
    const medir = () => el.style.setProperty("--alto-info", `${innerHeight - info.getBoundingClientRect().top}px`);
    medir();
    const vigia = new ResizeObserver(medir);
    vigia.observe(info);
    return () => vigia.disconnect();
  }, [abierta]);

  /** El desenfoque y la escala de cada elemento según su distancia a la línea que suena; las ya cantadas, un punto
      más borrosas (AMLL). */
  const estiloDe = (k: number): CSSProperties | undefined => {
    if (!letra?.sincronizada) return undefined;
    const d = k - Math.max(0, actual);
    const borroso = d === 0 ? 0 : Math.min(BORROSO_MAXIMO, Math.abs(d) + (d < 0 ? 1 : 0));
    return { "--borroso": `${borroso}px`, "--escala": d === 0 ? 1 : 0.97 } as CSSProperties;
  };

  return (
    <div ref={capa} className={`letra-capa${abierta ? " abierta" : ""}`} inert={!abierta}>
      <button type="button" className="cola-compacto" onClick={alCerrar} aria-label="Ocultar la letra">
        <CaratulaSuave cancion={c} />
        <span>
          <b>{c.titulo}</b>
          <span>{c.artista}</span>
        </span>
      </button>

      {letra === undefined ? (
        <p className="letra-aviso">{abierta ? "Buscando la letra…" : ""}</p>
      ) : letra === null ? (
        <p className="letra-aviso">No encontramos la letra de esta canción.</p>
      ) : (
        <div
          ref={lista}
          className={`letra-lineas${letra.sincronizada ? "" : " sin-tiempo"}${leyendo ? " leyendo" : ""}`}
          onPointerDown={leer}
          onWheel={leer}
        >
          <div className="letra-espacio" />
          {elementos.map((e, k) =>
            e.tipo === "pausa" ? (
              <div key={`p${k}`} data-i={k} className={`letra-elemento letra-pausa${k === actual ? " actual" : ""}`} style={estiloDe(k)} aria-hidden="true">
                <span className="letra-puntos">
                  <i />
                  <i />
                  <i />
                </span>
              </div>
            ) : (
              <button
                type="button"
                key={k}
                data-i={k}
                className={`letra-elemento letra-linea voz-${e.linea.voz}${k === actual ? " actual" : ""}`}
                style={estiloDe(k)}
                onClick={() => {
                  if (!letra.sincronizada) return;
                  clearTimeout(relojLeyendo.current);
                  setLeyendo(false);
                  buscarEn(e.linea.inicio);
                }}
                disabled={!letra.sincronizada}
              >
                <span className="letra-principal">{e.linea.palabras.length ? palabras(e.linea.palabras) : e.linea.texto}</span>
                {e.linea.coro && <span className="letra-coro">{palabras(e.linea.coro)}</span>}
              </button>
            ),
          )}
          <div className="letra-creditos">
            {letra.compositores?.length ? <p>Escrita por {enLista(letra.compositores)}</p> : null}
            {!letra.sincronizada && <p>{letra.nota ?? "Esta letra no viene sincronizada."}</p>}
            <p>Letra: {letra.fuente}</p>
          </div>
          <div className="letra-espacio letra-espacio--fin" />
        </div>
      )}
    </div>
  );
}
