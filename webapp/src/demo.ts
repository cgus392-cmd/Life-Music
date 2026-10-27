/* Modo de demostración, para capturas y videos públicos: canciones y carátulas de ejemplo (dibujadas, sin artistas
   reales), tema claro y el reproductor quieto. Solo se activa en local (localhost) con ?demo=…; en la web publicada
   no hace nada.

   ?demo=inicio    el Inicio con canciones de ejemplo y el mini reproductor
   ?demo=ahora     «Ahora suena» abierto
   ?demo=guia      la guía «Instálala en tu iPhone» (con el agente de usuario de un iPhone)
   ?demo=bienvenida la bienvenida
   ?demo=arranque  la imagen de arranque, quieta

   Las carátulas son las de la maqueta aprobada (Brand/maquetas/2026-10-24-web-app/maqueta/maqueta.js, PORTADAS). */
import type { Cancion, Elemento, Fila } from "./datos/api";

const parametro = new URLSearchParams(location.search).get("demo");
export const demo = ["localhost", "127.0.0.1"].includes(location.hostname) ? parametro : null;

const GRANO = (id: string) =>
  `<filter id="${id}g" x="0" y="0" width="100%" height="100%"><feTurbulence type="fractalNoise" baseFrequency=".85" numOctaves="2" stitchTiles="stitch"/><feColorMatrix type="saturate" values="0"/><feComponentTransfer><feFuncA type="linear" slope=".55"/></feComponentTransfer></filter>`;
const conGrano = (id: string, cuerpo: { defs?: string; dibujo: string }, fuerza = 0.22) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 300" width="600" height="600" preserveAspectRatio="xMidYMid slice"><defs>${GRANO(id)}${cuerpo.defs || ""}</defs>${cuerpo.dibujo}<rect width="300" height="300" filter="url(#${id}g)" opacity="${fuerza}" style="mix-blend-mode:overlay"/></svg>`;

const PORTADAS: Record<string, (id: string) => string> = {
  medianoche: (id) =>
    conGrano(id, {
      defs: `<linearGradient id="${id}c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#24185A"/><stop offset=".55" stop-color="#8E3F9E"/><stop offset=".74" stop-color="#FF5E7E"/></linearGradient><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFE07A"/><stop offset="1" stop-color="#FF8A3D"/></linearGradient>`,
      dibujo: `<rect width="300" height="300" fill="url(#${id}c)"/><circle cx="150" cy="168" r="80" fill="url(#${id}s)"/><g fill="#B04A8E"><rect y="178" width="300" height="4"/><rect y="191" width="300" height="5"/><rect y="203" width="300" height="6"/></g><rect y="212" width="300" height="88" fill="#3A1748"/><g fill="#FF9A5A" opacity=".85"><rect x="90" y="224" width="120" height="3" rx="1.5"/><rect x="104" y="238" width="92" height="3" rx="1.5"/><rect x="116" y="252" width="68" height="3" rx="1.5"/><rect x="128" y="266" width="44" height="3" rx="1.5"/></g>`,
    }),
  marea: (id) =>
    conGrano(id, {
      dibujo: `<rect width="300" height="300" fill="#0F4C5C"/><circle cx="222" cy="78" r="34" fill="#FFE2D6"/><path d="M0 170 Q75 120 150 170 T300 170 V300 H0Z" fill="#FF6B3D"/><path d="M0 205 Q75 160 150 205 T300 205 V300 H0Z" fill="#FFB49A"/><path d="M0 242 Q75 205 150 242 T300 242 V300 H0Z" fill="#F5F5F3"/>`,
    }),
  niebla: (id) =>
    conGrano(
      id,
      {
        defs: `<linearGradient id="${id}a" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#C9D6DF"/><stop offset="1" stop-color="#F3E6D8"/></linearGradient>`,
        dibujo: `<rect width="300" height="300" fill="url(#${id}a)"/><circle cx="196" cy="96" r="24" fill="#FFF4E6"/><path d="M0 170 L60 120 L110 160 L170 100 L240 150 L300 118 V300 H0Z" fill="#9FB3C2"/><path d="M0 206 L50 170 L120 205 L190 160 L260 200 L300 180 V300 H0Z" fill="#6F8798"/><path d="M0 246 L70 214 L140 244 L220 208 L300 238 V300 H0Z" fill="#435A6B"/><rect y="150" width="300" height="40" fill="#F3E6D8" opacity=".35"/>`,
      },
      0.3,
    ),
  ciudad: (id) => {
    let v = "";
    const az = (n: number) => (Math.sin(n * 91.7) + 1) / 2;
    for (let i = 0; i < 70; i++) {
      const x = 18 + (i % 10) * 27;
      const y = 130 + Math.floor(i / 10) * 22;
      if (az(i) > 0.45) v += `<rect x="${x}" y="${y}" width="10" height="12" fill="${az(i * 3) > 0.7 ? "#FFD38A" : "#F2B35E"}" opacity="${0.55 + az(i * 7) * 0.45}"/>`;
    }
    return conGrano(
      id,
      {
        defs: `<linearGradient id="${id}n" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0B1026"/><stop offset="1" stop-color="#2B2350"/></linearGradient>`,
        dibujo: `<rect width="300" height="300" fill="url(#${id}n)"/><rect x="8" y="118" width="284" height="182" fill="#141833"/>${v}<rect y="276" width="300" height="24" fill="#0A0D1F"/>`,
      },
      0.3,
    );
  },
  flor: (id) =>
    conGrano(
      id,
      {
        defs: `<radialGradient id="${id}r" cx=".5" cy=".55" r=".6"><stop offset="0" stop-color="#FFD9C2"/><stop offset="1" stop-color="#E86A7A"/></radialGradient>`,
        dibujo: `<rect width="300" height="300" fill="#3D1F2B"/><g transform="translate(150 160)">${[0, 60, 120, 180, 240, 300]
          .map((a) => `<ellipse rx="54" ry="96" transform="rotate(${a}) translate(0 -70)" fill="url(#${id}r)" opacity=".92"/>`)
          .join("")}<circle r="30" fill="#FFB347"/></g>`,
      },
      0.28,
    ),
  orbita: (id) =>
    conGrano(id, {
      dibujo: `<rect width="300" height="300" fill="#1E1446"/><circle cx="150" cy="150" r="118" fill="none" stroke="#3E2C86" stroke-width="2"/><circle cx="150" cy="150" r="84" fill="none" stroke="#5B44B5" stroke-width="2"/><circle cx="150" cy="150" r="48" fill="#8B5CF6"/><circle cx="234" cy="150" r="13" fill="#B4A2F6"/>`,
    }),
  selva: (id) =>
    conGrano(id, {
      dibujo: `<rect width="300" height="300" fill="#0E3B2C"/><path d="M-20 300 C40 180 90 160 150 300Z" fill="#1F9D6B"/><path d="M90 300 C140 150 210 130 260 300Z" fill="#7FD8B2"/><path d="M190 300 C230 200 280 190 330 300Z" fill="#2FB57F"/><circle cx="96" cy="86" r="30" fill="#E9F7EE"/>`,
    }),
  cobre: (id) =>
    conGrano(id, {
      dibujo: `<rect width="300" height="300" fill="#2B1A08"/><rect x="40" y="40" width="220" height="220" fill="#D98A00"/><rect x="80" y="80" width="140" height="140" fill="#FBD38D"/><rect x="120" y="120" width="60" height="60" fill="#2B1A08"/>`,
    }),
};

const imagen = (nombre: string) => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(PORTADAS[nombre](nombre))}`;

const ARTISTA = "Life Music Sessions";
const cancion = (n: number, titulo: string, portada: string, album: string, duracion: string): Cancion => ({
  id: `demo-${n}`,
  titulo,
  artista: ARTISTA,
  artistaId: null,
  album,
  albumId: null,
  duracion,
  caratula: imagen(portada),
  explicita: false,
});

export const CANCIONES: Cancion[] = [
  cancion(1, "Luz de medianoche", "medianoche", "Para la noche", "3:35"),
  cancion(2, "Marea alta", "marea", "Marea alta", "3:41"),
  cancion(3, "Madrugada", "niebla", "Madrugada", "3:05"),
  cancion(4, "Pétalos", "flor", "Pétalos", "2:47"),
  cancion(5, "Órbita", "orbita", "Para la noche", "4:12"),
  cancion(6, "Ciudad dormida", "ciudad", "Para la noche", "3:28"),
  cancion(7, "Selva", "selva", "Selva", "3:33"),
  cancion(8, "Cobre", "cobre", "Cobre", "4:01"),
];

const elemento = (c: Cancion): Elemento => ({ tipo: "cancion", id: c.id, titulo: c.titulo, subtitulo: c.artista, caratula: c.caratula, cancion: c });

/** El Inicio de la maqueta: «Para la noche» y «Lo más sonado en Colombia», con canciones de ejemplo. */
export const INICIO: { filas: Fila[]; pais: string } = {
  pais: "CO",
  filas: [
    { titulo: "Para la noche", forma: "carrusel", elementos: [CANCIONES[5], CANCIONES[0], CANCIONES[4], CANCIONES[3]].map(elemento) },
    { titulo: "Lo más sonado en Colombia", forma: "lista", elementos: [CANCIONES[7], CANCIONES[6], CANCIONES[2], CANCIONES[3], CANCIONES[4]].map(elemento) },
  ],
};
