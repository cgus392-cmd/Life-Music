/* En qué aparato está la web. */

/** iPhone o iPad (los iPad nuevos se presentan como Mac con pantalla táctil). */
export const esIOS = () =>
  /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

/** En iOS una página no puede cambiar el volumen (Safari lo ignora), así que no se puede mezclar dos canciones. */
export const puedeMezclar = () => !esIOS();
