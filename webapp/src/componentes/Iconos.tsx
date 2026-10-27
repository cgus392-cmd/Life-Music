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
  // Los controles del reproductor, como los de iOS: triángulos llenos con las puntas redondas. Siguiente y anterior
  // son dos triángulos (adelantar/retroceder), como en Apple Music, no la barra de «saltar al final».
  anterior:
    '<path class="lleno redondo" d="M11.2 6.4v11.2c0 .8-.9 1.3-1.6.8L2.9 13a1.2 1.2 0 0 1 0-2l6.7-5.4c.7-.5 1.6 0 1.6.8zM21.4 6.4v11.2c0 .8-.9 1.3-1.6.8L13.1 13a1.2 1.2 0 0 1 0-2l6.7-5.4c.7-.5 1.6 0 1.6.8z"/>',
  siguiente:
    '<path class="lleno redondo" d="M12.8 6.4v11.2c0 .8.9 1.3 1.6.8l6.7-5.4a1.2 1.2 0 0 0 0-2l-6.7-5.4c-.7-.5-1.6 0-1.6.8zM2.6 6.4v11.2c0 .8.9 1.3 1.6.8l6.7-5.4a1.2 1.2 0 0 0 0-2L4.2 5.6c-.7-.5-1.6 0-1.6.8z"/>',
  pausa:
    '<rect class="lleno" x="5.6" y="4.2" width="4.6" height="15.6" rx="1.4"/><rect class="lleno" x="13.8" y="4.2" width="4.6" height="15.6" rx="1.4"/>',
  play: '<path class="lleno redondo" d="M7 5.3v13.4c0 1 1.1 1.6 1.9 1.1l10.4-6.7a1.3 1.3 0 0 0 0-2.2L8.9 4.2C8.1 3.7 7 4.3 7 5.3z"/>',
  atras: '<path d="m15 5-7 7 7 7"/>',
  derecha: '<path d="m9 5 7 7-7 7"/>',
  compartir: '<path d="M12 3.5v11M8 7.5l4-4 4 4M6 11v8a1.5 1.5 0 0 0 1.5 1.5h9A1.5 1.5 0 0 0 18 19v-8"/>',
  // Un globo de diálogo con comillas: la letra de la canción.
  letra:
    '<path d="M6 4.5h12A2.5 2.5 0 0 1 20.5 7v7.5A2.5 2.5 0 0 1 18 17h-6.5l-4.5 3.5V17H6a2.5 2.5 0 0 1-2.5-2.5V7A2.5 2.5 0 0 1 6 4.5z"/><path d="M9.5 9v2.2M9.5 9c0-.9.5-1.4 1.3-1.5M14 9v2.2M14 9c0-.9.5-1.4 1.3-1.5"/>',
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
  ola: '<path d="M3 12c2-2 4-2 6 0s4 2 6 0 4-2 6 0M3 17c2-2 4-2 6 0s4 2 6 0 4-2 6 0"/>',
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

/* Los íconos del reproductor estilo Apple Music de la app Android, copiados de sus vectores (res/drawable:
   apple_skip_previous, apple_skip_next, play_applemusic, pause_applemusic, apple_queue, volume_mute y
   volume_up), en su cuadrícula de 960. Van llenos; los de saltar llevan además el trazo grueso de redondeo. */
const apple = {
  anterior:
    '<path class="grueso" d="M872.74,732.51L503.18,480L872.74,227.49L872.74,732.51ZM456.82,732.51L87.26,480L456.82,227.49L456.82,732.51Z"/>',
  siguiente:
    '<path class="grueso" d="M85.93,732.51L85.93,227.49L455.49,480L85.93,732.51ZM505.18,732.51L505.18,227.49L874.74,480L505.18,732.51Z"/>',
  play: '<path d="M320,687L320,273Q320,256 332,244.5Q344,233 360,233Q365,233 370.5,234.5Q376,236 381,239L707,446Q716,452 720.5,461Q725,470 725,480Q725,490 720.5,499Q716,508 707,514L381,721Q376,724 370.5,725.5Q365,727 360,727Q344,727 332,715.5Q320,704 320,687Z"/>',
  pausa:
    '<path d="M595,720Q562,720 538.5,696.5Q515,673 515,640L515,320Q515,287 538.5,263.5Q562,240 595,240L595,240Q628,240 651.5,263.5Q675,287 675,320L675,640Q675,673 651.5,696.5Q628,720 595,720ZM365,720Q332,720 308.5,696.5Q285,673 285,640L285,320Q285,287 308.5,263.5Q332,240 365,240L365,240Q398,240 421.5,263.5Q445,287 445,320L445,640Q445,673 421.5,696.5Q398,720 365,720Z"/>',
  cola: '<path d="M360,760L360,680L840,680L840,760L360,760ZM360,520L360,440L840,440L840,520L360,520ZM360,280L360,200L840,200L840,280L360,280ZM200,800Q167,800 143.5,776.5Q120,753 120,720Q120,687 143.5,663.5Q167,640 200,640Q233,640 256.5,663.5Q280,687 280,720Q280,753 256.5,776.5Q233,800 200,800ZM200,560Q167,560 143.5,536.5Q120,513 120,480Q120,447 143.5,423.5Q167,400 200,400Q233,400 256.5,423.5Q280,447 280,480Q280,513 256.5,536.5Q233,560 200,560ZM143.5,296.5Q120,273 120,240Q120,207 143.5,183.5Q167,160 200,160Q233,160 256.5,183.5Q280,207 280,240Q280,273 256.5,296.5Q233,320 200,320Q167,320 143.5,296.5Z"/>',
  volumenBajo: '<path d="M280,600v-240h160l200,-200v640L440,600L280,600ZM560,354l-86,86L360,440v80h114l86,86v-252ZM460,480Z"/>',
  volumenAlto:
    '<path d="M560,829v-82q90,-26 145,-100t55,-168q0,-94 -55,-168T560,211v-82q124,28 202,125.5T840,479q0,127 -78,224.5T560,829ZM120,600v-240h160l200,-200v640L280,600L120,600ZM560,640v-322q47,22 73.5,66t26.5,96q0,51 -26.5,94.5T560,640ZM400,354l-86,86L200,440v80h114l86,86v-252ZM300,480Z"/>',
} as const;

export type NombreApple = keyof typeof apple;

export function IconoApple({ nombre, tam }: { nombre: NombreApple; tam: number }) {
  return (
    <svg
      className="ico ico-apple"
      viewBox="0 0 960 960"
      style={{ width: tam, height: tam }}
      aria-hidden="true"
      focusable="false"
      dangerouslySetInnerHTML={{ __html: apple[nombre] }}
    />
  );
}

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
