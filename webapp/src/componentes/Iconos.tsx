/* Íconos de la maqueta «El trazo» (Brand/maquetas/2026-10-24-web-app/maqueta/maqueta.js): trazo de 2, puntas y
   uniones redondas, cuadrícula de 24. Son de la familia del logo. Cada uno representa su acción. */

const trazos = {
  casa: '<path d="M3.5 10.5 12 3.5l8.5 7V20a.8.8 0 0 1-.8.8H15v-6H9v6H4.3a.8.8 0 0 1-.8-.8z"/>',
  buscar: '<circle cx="11" cy="11" r="7"/><path d="m16.5 16.5 4 4"/>',
  biblioteca: '<path d="M5 4v16M10 4v16M14.5 4.5l4.5 15.5"/>',
  corazon: '<path d="M12 20s-7.5-4.6-7.5-10.2A4.2 4.2 0 0 1 12 7.3a4.2 4.2 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z"/>',
  corazonLleno:
    '<path class="lleno" d="M12 20s-7.5-4.6-7.5-10.2A4.2 4.2 0 0 1 12 7.3a4.2 4.2 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z"/>',
  aleatorio: '<path d="M3 7h3.5L17 17h4M3 17h3.5l3-3M14 10l3-3h4M18 4l3 3-3 3M18 14l3 3-3 3"/>',
  repetir: '<path d="M4 11V9.5A3.5 3.5 0 0 1 7.5 6H20l-3-3M20 13v1.5a3.5 3.5 0 0 1-3.5 3.5H4l3 3"/>',
  repetirUna:
    '<path d="M4 11V9.5A3.5 3.5 0 0 1 7.5 6H20l-3-3M20 13v1.5a3.5 3.5 0 0 1-3.5 3.5H4l3 3"/><path d="M11 10.5l1.5-1v5"/>',
  cola: '<path d="M4 6h13M4 11h13M4 16h8M18 13v8M14 17h8"/>',
  anterior: '<path class="lleno" d="M6 5.5h2.2v13H6zM20 5.5v13l-10.5-6.5z"/>',
  siguiente: '<path class="lleno" d="M15.8 5.5H18v13h-2.2zM4 5.5v13l10.5-6.5z"/>',
  pausa:
    '<rect class="lleno" x="6" y="4.5" width="4.2" height="15" rx="1.2"/><rect class="lleno" x="13.8" y="4.5" width="4.2" height="15" rx="1.2"/>',
  play: '<path class="lleno" d="M7.5 4.8v14.4a.9.9 0 0 0 1.4.8l11-7.2a.9.9 0 0 0 0-1.6l-11-7.2a.9.9 0 0 0-1.4.8z"/>',
  atras: '<path d="m15 5-7 7 7 7"/>',
  derecha: '<path d="m9 5 7 7-7 7"/>',
  compartir: '<path d="M12 3.5v11M8 7.5l4-4 4 4M6 11v8a1.5 1.5 0 0 0 1.5 1.5h9A1.5 1.5 0 0 0 18 19v-8"/>',
  ajustes:
    '<path d="M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 0 0 0-6.8z"/><path d="M19.2 13.4a7.6 7.6 0 0 0 0-2.8l2-1.5-2-3.4-2.4 1a7.4 7.4 0 0 0-2.4-1.4L14 2.8h-4l-.4 2.5a7.4 7.4 0 0 0-2.4 1.4l-2.4-1-2 3.4 2 1.5a7.6 7.6 0 0 0 0 2.8l-2 1.5 2 3.4 2.4-1a7.4 7.4 0 0 0 2.4 1.4l.4 2.5h4l.4-2.5a7.4 7.4 0 0 0 2.4-1.4l2.4 1 2-3.4z"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5.5M12 7.6v.4"/>',
  reloj: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  listas: '<path d="M4 6.5h16M4 12h16M4 17.5h9"/>',
  arrastrar: '<path d="M5 9.5h14M5 14.5h14"/>',
  cerrar: '<path d="m7 7 10 10M17 7 7 17"/>',
  luna: '<path d="M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z"/>',
  rayo: '<path d="M13 3 5 13.5h6L10 21l8-10.5h-6z"/>',
  hoja: '<path d="M5 19c0-8 6-14 15-14 0 9-6 15-14 15M5 19l7-7"/>',
  aContinuacion: '<path d="M4 6.5h11M4 11.5h11M4 16.5h6M15 14v6.5l5-3.25z"/>',
  mas: '<rect x="3.5" y="3.5" width="17" height="17" rx="4.5"/><path d="M12 8v8M8 12h8"/>',
  disco: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2.6"/>',
  persona: '<circle cx="12" cy="8.5" r="4"/><path d="M4.5 20.5c1-4.2 4-6.3 7.5-6.3s6.5 2.1 7.5 6.3"/>',
  globo:
    '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.4 2.5 3.5 5.4 3.5 8.5s-1.1 6-3.5 8.5c-2.4-2.5-3.5-5.4-3.5-8.5s1.1-6 3.5-8.5z"/>',
  contraste: '<circle cx="12" cy="12" r="8.5"/><path class="lleno" d="M12 3.5a8.5 8.5 0 0 1 0 17z"/>',
  pantalla: '<rect x="7" y="2.8" width="10" height="18.4" rx="2.6"/><path d="M11 17.8h2M3.5 9.5v5M20.5 9.5v5"/>',
  fondo: '<rect x="3.5" y="3.5" width="17" height="17" rx="4"/><path d="M3.5 15c3-2.6 5.6-2.6 8.5 0s5.5 2.6 8.5 0"/>',
  papelera:
    '<path d="M4.5 6.5h15M9.5 6.5v-2h5v2M6.5 6.5l1 13a1.5 1.5 0 0 0 1.5 1.4h6a1.5 1.5 0 0 0 1.5-1.4l1-13M10 10.5v6M14 10.5v6"/>',
  codigo: '<path d="m8.5 7.5-5 4.5 5 4.5M15.5 7.5l5 4.5-5 4.5"/>',
  sinRed:
    '<path d="M2.5 8.8a14 14 0 0 1 19 0M5.8 12.3a9 9 0 0 1 12.4 0M9.2 15.8a4 4 0 0 1 5.6 0M12 19.4v.1M3.5 3.5l17 17"/>',
  reintentar: '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4v4.5H15"/>',
  puntos:
    '<circle class="lleno" cx="5.5" cy="12" r="1.8"/><circle class="lleno" cx="12" cy="12" r="1.8"/><circle class="lleno" cx="18.5" cy="12" r="1.8"/>',
  abajo: '<path d="m5 9 7 7 7-7"/>',
  instalar: '<path d="M12 4v11M7.5 10.5 12 15l4.5-4.5M5 19.5h14"/>',
  volumen: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18.2 6.4a7.6 7.6 0 0 1 0 11.2"/>',
  silencio: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="m16 9.5 5 5M21 9.5l-5 5"/>',
  candado: '<rect x="5" y="10.5" width="14" height="10" rx="2.6"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  lista: '<rect x="3.5" y="3.5" width="17" height="17" rx="4"/><path d="M8 9h8M8 12.5h8M8 16h5"/>',
  maximizar: '<path d="M14 4h6v6M10 20H4v-6M20 4l-6.5 6.5M4 20l6.5-6.5"/>',
} as const;

export type NombreIcono = keyof typeof trazos;

export function Icono({ nombre, tam, className = "" }: { nombre: NombreIcono; tam?: number; className?: string }) {
  return (
    <svg
      className={`ico ${className}`}
      viewBox="0 0 24 24"
      width={tam}
      height={tam}
      aria-hidden="true"
      focusable="false"
      dangerouslySetInnerHTML={{ __html: trazos[nombre] }}
    />
  );
}
