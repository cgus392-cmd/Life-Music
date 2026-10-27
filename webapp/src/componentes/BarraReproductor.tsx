import { usarReproductor } from "../reproductor/tienda";
import { reloj } from "../datos/api";
import { esMeGusta, usarBiblioteca } from "../datos/biblioteca";
import { BarraTiempo } from "./BarraTiempo";
import { CaratulaSuave } from "./Caratula";
import { Icono } from "./Iconos";

type Props = { alAbrir: () => void; alCola: () => void; colaAbierta: boolean };

/** La cápsula del reproductor en el computador: el mismo vidrio del teléfono, más ancha y con volumen. */
export function BarraReproductor({ alAbrir, alCola, colaAbierta }: Props) {
  const r = usarReproductor();
  const c = r.cola[r.indice];
  const megusta = esMeGusta(c?.id);
  const alternarMeGusta = usarBiblioteca((s) => s.alternarMeGusta);
  if (!c) return null;

  return (
    <footer className="barra-escritorio vidrio" aria-label="Reproductor">
      <div className="barra-cancion">
        <button type="button" className="barra-abrir" onClick={alAbrir} aria-label={`Abrir «${c.titulo}»`}>
          <CaratulaSuave cancion={c} />
          <span className="barra-textos">
            <b>{c.titulo}</b>
            {r.aviso ? <span className="aviso">{r.aviso}</span> : <span>{c.artista}</span>}
          </span>
        </button>
        <button
          type="button"
          className={`boton-icono${megusta ? " activo" : ""}`}
          onClick={() => alternarMeGusta(c)}
          aria-label={megusta ? "Quitar de Me gusta" : "Me gusta"}
          aria-pressed={megusta}
        >
          <Icono nombre={megusta ? "corazonLleno" : "corazon"} tam={20} />
        </button>
      </div>

      <div className="barra-centro">
        <div className="barra-controles">
          <button
            type="button"
            className={`boton-icono${r.aleatorio ? " activo" : ""}`}
            onClick={r.alternarAleatorio}
            aria-label="Aleatorio"
            aria-pressed={r.aleatorio}
          >
            <Icono nombre="aleatorio" />
          </button>
          <button type="button" className="boton-icono" onClick={r.anterior} aria-label="Anterior">
            <Icono nombre="anterior" />
          </button>
          <button
            type="button"
            className="boton-icono principal"
            onClick={r.alternar}
            aria-label={r.sonando ? "Pausa" : "Reproducir"}
          >
            <Icono nombre={r.sonando ? "pausa" : "play"} />
          </button>
          <button type="button" className="boton-icono" onClick={r.siguiente} aria-label="Siguiente">
            <Icono nombre="siguiente" />
          </button>
          <button
            type="button"
            className={`boton-icono${r.repetir !== "no" ? " activo" : ""}`}
            onClick={r.cambiarRepetir}
            aria-label={r.repetir === "una" ? "Repetir esta canción" : r.repetir === "todas" ? "Repetir todo" : "Repetir"}
          >
            <Icono nombre={r.repetir === "una" ? "repetirUna" : "repetir"} />
          </button>
        </div>
        {r.anuncio ? (
          <p className="barra-anuncio">Suena un anuncio de YouTube.</p>
        ) : (
          <div className="barra-tiempo">
            <span>{reloj(r.tiempo)}</span>
            <BarraTiempo variante="barra" />
            <span>{reloj(r.duracion)}</span>
          </div>
        )}
      </div>

      <div className="barra-extra">
        <button
          type="button"
          className={`boton-icono${colaAbierta ? " activo" : ""}`}
          onClick={alCola}
          aria-label="Cola"
          aria-pressed={colaAbierta}
        >
          <Icono nombre="cola" />
        </button>
        <button
          type="button"
          className="boton-icono boton-volumen"
          onClick={r.alternarSilencio}
          aria-label={r.silencio ? "Activar sonido" : "Silenciar"}
        >
          <Icono nombre={r.silencio ? "silencio" : "volumen"} />
        </button>
        <input
          className="volumen"
          type="range"
          min={0}
          max={100}
          value={r.silencio ? 0 : r.volumen}
          onChange={(e) => r.fijarVolumen(Number(e.target.value))}
          aria-label="Volumen"
        />
        <button type="button" className="boton-icono" onClick={alAbrir} aria-label="Pantalla completa">
          <Icono nombre="maximizar" />
        </button>
      </div>
    </footer>
  );
}
