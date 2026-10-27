/* Piezas del reproductor estilo Apple Music de la app Android (Player.kt), para «Ahora suena». */
import { useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent, type ReactNode } from "react";

/** La barra fina de Android (PlayerSliderTrack, estilo SLIM): sin perilla, 10 px de alto y 16 px mientras se toca.
    Se toca en cualquier punto y se arrastra (el <input type="range"> del iPhone solo se deja mover desde la perilla).
    Sirve para el tiempo y para el volumen. */
export function Deslizador({
  valor,
  max,
  alMover,
  alSoltar,
  etiqueta,
  texto,
}: {
  valor: number;
  max: number;
  /** Mientras se arrastra (el volumen cambia en vivo; el tiempo solo muestra a dónde va). */
  alMover?: (v: number) => void;
  /** Al soltar (el tiempo salta ahí, como onValueChangeFinished en Android). */
  alSoltar?: (v: number) => void;
  etiqueta: string;
  texto?: string;
}) {
  const [tocando, setTocando] = useState(false);
  const caja = useRef<HTMLDivElement>(null);
  const ultimo = useRef<number | null>(null);
  const avance = max > 0 ? Math.min(100, Math.max(0, (valor / max) * 100)) : 0;

  const valorEn = (x: number) => {
    const r = caja.current!.getBoundingClientRect();
    return Math.round(Math.min(1, Math.max(0, (x - r.left) / r.width)) * max);
  };
  const mover = (v: number) => {
    ultimo.current = v;
    alMover?.(v);
  };
  const empezar = (e: PointerEvent) => {
    if (max <= 0) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    setTocando(true);
    mover(valorEn(e.clientX));
  };
  const soltar = () => {
    setTocando(false);
    if (ultimo.current !== null) alSoltar?.(ultimo.current);
    ultimo.current = null;
  };
  const tecla = (e: KeyboardEvent) => {
    const paso = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1 }[e.key];
    if (!paso || max <= 0) return;
    e.preventDefault();
    const v = Math.min(max, Math.max(0, Math.round(valor) + paso * Math.max(1, Math.round(max / 40))));
    alMover?.(v);
    alSoltar?.(v);
  };

  return (
    <div
      ref={caja}
      className={`deslizador${tocando ? " tocando" : ""}`}
      role="slider"
      tabIndex={0}
      aria-label={etiqueta}
      aria-valuemin={0}
      aria-valuemax={Math.round(max)}
      aria-valuenow={Math.round(valor)}
      aria-valuetext={texto}
      onPointerDown={empezar}
      onPointerMove={(e) => tocando && mover(valorEn(e.clientX))}
      onPointerUp={soltar}
      onPointerCancel={soltar}
      onKeyDown={tecla}
    >
      <div className="deslizador-pista">
        <i style={{ width: `${avance}%` }} />
      </div>
    </div>
  );
}

/** Un texto de una línea que, si no cabe, se desliza una vez a los 3 s y vuelve (basicMarquee en Android). */
export function Desliza({ children }: { children: ReactNode }) {
  const caja = useRef<HTMLSpanElement>(null);
  const [sobra, setSobra] = useState(0);

  useLayoutEffect(() => {
    const el = caja.current;
    if (!el) return;
    const medir = () => setSobra(Math.max(0, el.scrollWidth - el.clientWidth));
    medir();
    const vigia = new ResizeObserver(medir);
    vigia.observe(el);
    return () => vigia.disconnect();
  }, [children]);

  // A 30 px por segundo, como Android, ida y vuelta.
  const estilo = { "--sobra": `${sobra}px`, "--dura": `${Math.max(3, (sobra * 2) / 30 + 2)}s` } as CSSProperties;
  return (
    <span ref={caja} className="desliza">
      <span key={String(children)} className={`desliza-texto${sobra > 2 ? " moviendo" : ""}`} style={estilo}>
        {children}
      </span>
    </span>
  );
}
