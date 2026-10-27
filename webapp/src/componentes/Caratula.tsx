import { useState } from "react";
import type { Cancion } from "../datos/api";
import { demo } from "../demo";

/** La imagen del video en i.ytimg.com: siempre existe y sirve de respaldo. */
export const respaldoDe = (id: string) => `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;

/** La carátula a otro tamaño (las de YouTube Music aceptan el tamaño en la URL). */
export const caratulaA = (c: Cancion, lado: number) =>
  c.caratula?.replace(/=w\d+-h\d+/, `=w${lado}-h${lado}`) ?? respaldoDe(c.id);

/** La carátula de una canción. Si la de YouTube Music no carga (su servidor a veces frena con un 429), usa la
    imagen del video, recortada en cuadro. Sin Referer, que es lo que más lo dispara. */
export function Caratula({ cancion, className = "", lado }: { cancion: Cancion; className?: string; lado?: number }) {
  const respaldo = respaldoDe(cancion.id);
  return (
    <img
      className={`portada ${className}`}
      src={lado ? caratulaA(cancion, lado) : cancion.caratula ?? respaldo}
      alt=""
      loading={demo ? "eager" : "lazy"}
      referrerPolicy="no-referrer"
      onError={(e) => {
        if (e.currentTarget.src !== respaldo) e.currentTarget.src = respaldo;
      }}
    />
  );
}

/** La anterior se quita cuando la nueva terminó de aparecer. Por si no hay transición (menos movimiento, capturas),
    también se quita pasado este tiempo (ms); hasta entonces queda tapada, así que no se nota. */
const QUITAR_ANTERIOR_MS = 3000;

/** La carátula de lo que suena. Al cambiar de canción no salta: la nueva se funde sobre la anterior, como en Apple
    Music, y solo empieza cuando ya cargó, para no fundirse desde un hueco. La duración la pone cada lugar con
    --fundido (el fondo de «Ahora suena» tarda más). */
export function CaratulaSuave({ cancion, className = "", lado }: { cancion: Cancion; className?: string; lado?: number }) {
  const [capas, setCapas] = useState([{ c: cancion, lista: true }]);
  if (capas[capas.length - 1].c.id !== cancion.id) {
    // Debajo queda la última que ya se veía (si se salta rápido, no una que nunca terminó de cargar).
    const debajo = [...capas].reverse().find((x) => x.lista) ?? capas[capas.length - 1];
    setCapas([debajo, { c: cancion, lista: false }]);
  }

  // Deja solo la de esta canción, si sigue siendo la de arriba.
  const quitarDebajo = (id: string) => setCapas((cs) => (cs.length > 1 && cs[cs.length - 1].c.id === id ? cs.slice(-1) : cs));
  const lista = (id: string) => {
    setCapas((cs) => cs.map((x) => (x.c.id === id ? { ...x, lista: true } : x)));
    setTimeout(() => quitarDebajo(id), QUITAR_ANTERIOR_MS);
  };

  return (
    <span className={`portada portada-suave ${className}`}>
      {capas.map(({ c, lista: cargada }) => {
        const respaldo = respaldoDe(c.id);
        return (
          <img
            key={c.id}
            className={`portada-capa${cargada ? " lista" : ""}`}
            src={lado ? caratulaA(c, lado) : c.caratula ?? respaldo}
            alt=""
            referrerPolicy="no-referrer"
            onLoad={() => !cargada && lista(c.id)}
            onTransitionEnd={(e) => e.propertyName === "opacity" && cargada && quitarDebajo(c.id)}
            onError={(e) => {
              if (e.currentTarget.src !== respaldo) e.currentTarget.src = respaldo;
              else if (!cargada) lista(c.id);
            }}
          />
        );
      })}
    </span>
  );
}
