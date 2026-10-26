/* Los estados: cargando (con la forma de lo que viene, sin ruedas), sin conexión y sin resultados. Los dibujos
   salen del mismo trazo del logo (LEEME, regla 3). */
import { Icono } from "./Iconos";

/** Cargando: la silueta de la pantalla que viene. Con «reducir movimiento» queda quieta. */
export function Cargando({ forma }: { forma: "inicio" | "lista" | "coleccion" }) {
  return (
    <div className={`cargando cargando--${forma}`} aria-busy="true" aria-label="Cargando">
      {forma === "coleccion" && <div className="sombra sombra-portada" />}
      {forma === "inicio" && (
        <>
          <div className="sombra sombra-titulo" />
          <div className="sombra-carrusel">
            <div className="sombra sombra-tarjeta" />
            <div className="sombra sombra-tarjeta" />
            <div className="sombra sombra-tarjeta" />
          </div>
        </>
      )}
      {[0, 1, 2, 3, 4].map((i) => (
        <div key={i} className="sombra-fila">
          <div className="sombra sombra-mini" />
          <div className="sombra-textos">
            <div className="sombra sombra-linea" />
            <div className="sombra sombra-linea corta" />
          </div>
        </div>
      ))}
    </div>
  );
}

/** Sin conexión: la marca con el palo cortado. */
export function SinConexion({ alReintentar }: { alReintentar: () => void }) {
  return (
    <div className="vacio">
      <svg viewBox="-14 0 120 100" className="vacio-dibujo" aria-hidden="true">
        <path d="M28 31 Q28 22 21 18 Q13 14 12 8 V40" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" />
        <path d="M12 56 V80" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" />
        <ellipse cx="2.5" cy="85" rx="11.5" ry="8.5" transform="rotate(-22 2.5 85)" fill="var(--coral)" />
      </svg>
      <h3>Sin conexión</h3>
      <p>Life Music necesita internet para traer la música. Revisa tu conexión.</p>
      <button type="button" className="accion-texto" onClick={alReintentar}>
        <Icono nombre="reintentar" tam={18} /> Reintentar
      </button>
    </div>
  );
}

/** Sin resultados: la línea del logo hace la pregunta y la cabeza es el punto. */
export function SinResultados({ consulta, alProbar }: { consulta: string; alProbar?: (q: string) => void }) {
  const palabra = consulta.split(/\s+/).sort((a, b) => b.length - a.length)[0];
  return (
    <div className="vacio">
      <svg viewBox="0 0 60 100" className="vacio-dibujo" aria-hidden="true">
        <path d="M14 30 Q14 10 32 10 Q50 10 50 28 Q50 42 34 50 Q30 52 30 60 V64" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
        <ellipse cx="30" cy="84" rx="11.5" ry="8.5" transform="rotate(-22 30 84)" fill="var(--coral)" />
      </svg>
      <h3>No encontramos «{consulta}»</h3>
      <p>Revisa cómo se escribe o prueba solo con el nombre del artista.</p>
      {alProbar && palabra && palabra !== consulta && (
        <button type="button" className="accion-texto" onClick={() => alProbar(palabra)}>
          Buscar «{palabra}»
        </button>
      )}
    </div>
  );
}
