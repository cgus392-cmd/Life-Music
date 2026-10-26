import type { Cancion } from "../datos/api";

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
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={(e) => {
        if (e.currentTarget.src !== respaldo) e.currentTarget.src = respaldo;
      }}
    />
  );
}
