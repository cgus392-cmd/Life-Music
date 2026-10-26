/* El color de la canción, para la cabeza de la nota («El trazo»).
   googleusercontent responde 429 a las lecturas con CORS desde el navegador; la miniatura de i.ytimg.com sí deja
   leer sus píxeles, así que el color sale de ahí. */

export const CORAL = "#FF6B3D";

const memoria = new Map<string, string>();

/** La luz mínima del color: la cabeza tiene que verse sobre fondos oscuros y sobre la carátula. */
const LUZ_MINIMA = 0.55;

function hsl(r: number, g: number, b: number) {
  const [R, G, B] = [r / 255, g / 255, b / 255];
  const max = Math.max(R, G, B);
  const min = Math.min(R, G, B);
  const l = (max + min) / 2;
  const s = max === min ? 0 : l > 0.5 ? (max - min) / (2 - max - min) : (max - min) / (max + min);
  let h = 0;
  if (max !== min) {
    const d = max - min;
    h = max === R ? ((G - B) / d + (G < B ? 6 : 0)) / 6 : max === G ? ((B - R) / d + 2) / 6 : ((R - G) / d + 4) / 6;
  }
  return { h, s, l };
}

/** El mismo tono y la misma saturación, con la luz que haga falta. */
function conLuz(r: number, g: number, b: number) {
  const { h, s, l } = hsl(r, g, b);
  const luz = Math.max(l, LUZ_MINIMA);
  return `hsl(${Math.round(h * 360)} ${Math.round(Math.max(s, 0.45) * 100)}% ${Math.round(luz * 100)}%)`;
}

/** El color más vivo de la carátula (saturado y ni muy oscuro ni muy claro). Si no se puede leer, el coral. */
export async function colorDe(id: string): Promise<string> {
  const ya = memoria.get(id);
  if (ya) return ya;
  try {
    const r = await fetch(`https://i.ytimg.com/vi/${id}/mqdefault.jpg`, { mode: "cors" });
    const imagen = await createImageBitmap(await r.blob());
    const lienzo = document.createElement("canvas");
    lienzo.width = lienzo.height = 24;
    const cx = lienzo.getContext("2d")!;
    cx.drawImage(imagen, 0, 0, 24, 24);
    const d = cx.getImageData(0, 0, 24, 24).data;
    const cubos = new Map<string, { r: number; g: number; b: number; n: number; puntaje: number }>();
    for (let i = 0; i < d.length; i += 4) {
      const [r, g, b] = [d[i], d[i + 1], d[i + 2]];
      const { s, l } = hsl(r, g, b);
      if (l < 0.18 || l > 0.88) continue;
      const clave = `${r >> 5}-${g >> 5}-${b >> 5}`;
      const c = cubos.get(clave) ?? { r: 0, g: 0, b: 0, n: 0, puntaje: 0 };
      c.r += r;
      c.g += g;
      c.b += b;
      c.n++;
      c.puntaje += s * s * (1 - Math.abs(l - 0.55));
      cubos.set(clave, c);
    }
    const mejor = [...cubos.values()].sort((a, b) => b.puntaje - a.puntaje)[0];
    const color = mejor && mejor.puntaje / mejor.n > 0.05 ? conLuz(mejor.r / mejor.n, mejor.g / mejor.n, mejor.b / mejor.n) : CORAL;
    memoria.set(id, color);
    return color;
  } catch {
    return CORAL;
  }
}
