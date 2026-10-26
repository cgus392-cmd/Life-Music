/* La marca «La nota es el pulso» (Brand/logo/2026-10-22-nota-pulso): el trazo «lm» y la cabeza de la nota.
   En «El trazo» la cabeza marca el tiempo y lo que suena, y toma el color de la canción (--cancion). */
import { useEffect, useState } from "react";

const TRAZO = "M28 31 Q28 22 21 18 Q13 14 12 8 V80 Q12 92 24 92 Q36 92 36 80 V58 Q36 42 49 42 Q62 42 62 58 V90 V58 Q62 42 75 42 Q88 42 88 58 V90";

/** La marca completa (trazo y cabeza). Señala lo que suena en una lista: nunca un punto suelto. */
export function Nota({ alto = 18, color = "currentColor", cabeza = "var(--cancion)" }: { alto?: number; color?: string; cabeza?: string }) {
  return (
    <svg viewBox="-12 4 104 94" style={{ height: alto, width: "auto", overflow: "visible" }} aria-hidden="true">
      <path d={TRAZO} fill="none" stroke={color} strokeWidth="10" strokeLinecap="round" strokeLinejoin="round" />
      <ellipse cx="2.5" cy="85" rx="11.5" ry="8.5" transform="rotate(-22 2.5 85)" fill={cabeza} />
    </svg>
  );
}

/** Solo la cabeza de la nota: el marcador del tiempo. */
export function Cabeza({ ancho = 18, color = "var(--cancion)" }: { ancho?: number; color?: string }) {
  return (
    <svg viewBox="-12 -9 24 18" style={{ width: ancho, height: ancho * 0.75, overflow: "visible", display: "block" }} aria-hidden="true">
      <ellipse rx="11.5" ry="8.5" transform="rotate(-22)" fill={color} />
    </svg>
  );
}

/** El logo horizontal: el trazo con «Life Music» en Geist 600. La cabeza siempre es coral. */
export function Logo({ alto = 22 }: { alto?: number }) {
  return (
    <svg viewBox="-14 0 532 100" style={{ height: alto, width: "auto", overflow: "visible" }} role="img" aria-label="Life Music">
      <path d={TRAZO} fill="none" stroke="currentColor" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round" />
      <ellipse cx="2.5" cy="85" rx="11.5" ry="8.5" transform="rotate(-22 2.5 85)" fill="#FF6B3D" />
      <text x="116" y="88" fontFamily="'Geist Variable', Geist, sans-serif" fontWeight="600" fontSize="86" letterSpacing="-4.3" fill="currentColor">
        Life Music
      </text>
    </svg>
  );
}

// Los mismos tiempos que BrandTitle en Android (ui/component/BrandTitle.kt).
const SALIDA_MS = 900;
const ENTRADA_MS = 1_100;
const ESPERA_NOMBRE_MS = 6_000;
const ESPERA_MARCA_MS = 3_000;

/** El título de marca: alterna «Life Music» y el logo de CG LABS, como en Android. Es intocable. */
export function TituloMarca({ alto = 20 }: { alto?: number }) {
  const [marca, setMarca] = useState(false);
  useEffect(() => {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const t = setTimeout(() => setMarca((m) => !m), SALIDA_MS + ENTRADA_MS + (marca ? ESPERA_MARCA_MS : ESPERA_NOMBRE_MS));
    return () => clearTimeout(t);
  }, [marca]);
  return (
    <span className="titulo-marca" style={{ height: alto }} aria-label="Life Music by CG LABS">
      <span className={`titulo-marca-capa${marca ? "" : " visible"}`}>
        <Logo alto={alto} />
      </span>
      <span className={`titulo-marca-capa${marca ? " visible" : ""}`}>
        <img className="solo-claro" src="/marca/cglabs_lockup_negro.webp" alt="" style={{ height: alto * 0.85 }} />
        <img className="solo-oscuro" src="/marca/cglabs_lockup_blanco.webp" alt="" style={{ height: alto * 0.85 }} />
      </span>
    </span>
  );
}
