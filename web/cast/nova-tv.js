/* Nova en el receptor del TV (22-10-2026, maqueta aprobada por CG en Brand/maquetas/2026-10-22-receptor-tv).
   «A · La mirada»: la misma geometria de web/landing.js (reticula de 200 x 200), en el coral del pulso.
   Para TV flojos: sin feGaussianBlur (el brillo es un degradado radial), sin bucle de animacion; respira con CSS
   y parpadea con un temporizador cada 4 a 7 s. ES5 a proposito: corre en navegadores de TV viejos.
   Uso: var n = NovaTV.crear(elemento, { estado: "escucha", mira: [5, 4] }); n.estado("habla"); n.mira(0, 0); */
(function () {
  "use strict";

  // Los ojos de cada estado: x izquierdo, x derecho, y, ancho, alto (los de landing.js).
  var OJOS = { escucha: [80.5, 109.5, 88, 10, 23], habla: [80.5, 109.5, 92, 10, 17] };
  var TINTA = "#14161B";
  var cuenta = 0;

  function crear(el, opciones) {
    opciones = opciones || {};
    var id = "nt" + (++cuenta);
    el.innerHTML =
      '<svg viewBox="0 0 200 200" aria-hidden="true" focusable="false"><defs>' +
      '<radialGradient id="' + id + 'h"><stop offset="0" stop-color="#FF6B3D" stop-opacity=".6"/><stop offset=".55" stop-color="#FFB49A" stop-opacity=".27"/><stop offset="1" stop-color="#FFB49A" stop-opacity="0"/></radialGradient>' +
      '<radialGradient id="' + id + 'b"><stop offset=".55" stop-color="#FF6B3D" stop-opacity=".55"/><stop offset="1" stop-color="#FF6B3D" stop-opacity="0"/></radialGradient>' +
      '<radialGradient id="' + id + 'o" cx=".46" cy=".42" r=".6"><stop offset="0" stop-color="#FFFFFF"/><stop offset=".42" stop-color="#FFF8EE"/><stop offset=".78" stop-color="#FFAE94"/><stop offset="1" stop-color="#FF6B3D"/></radialGradient>' +
      '<linearGradient id="' + id + 'H" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".5" stop-color="#fff"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>' +
      '<linearGradient id="' + id + 'V" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".5" stop-color="#fff"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>' +
      '</defs>' +
      '<circle cx="100" cy="100" r="98" fill="url(#' + id + 'h)"/>' +
      '<circle class="nova-aro" cx="100" cy="100" r="74" fill="none" stroke="#FF6B3D" stroke-width="1.4" opacity=".32"/>' +
      '<g class="nova-anillos"><circle cx="100" cy="100" r="82" fill="none" stroke="#FF6B3D" stroke-width="1.4" opacity=".55"/><circle cx="100" cy="100" r="98" fill="none" stroke="#FFB49A" stroke-width="1.2" opacity=".32"/></g>' +
      '<g class="nova-cuerpo">' +
      '<circle cx="100" cy="100" r="70" fill="url(#' + id + 'b)"/>' +
      '<circle cx="100" cy="100" r="56" fill="url(#' + id + 'o)"/>' +
      '<rect class="nova-ojo" rx="5" fill="' + TINTA + '"/><rect class="nova-ojo" rx="5" fill="' + TINTA + '"/>' +
      '<path class="nova-sonrisa" d="M78.5 103 q7 -11 14 0 M107.5 103 q7 -11 14 0" fill="none" stroke="' + TINTA + '" stroke-width="4.8" stroke-linecap="round"/>' +
      '<g opacity=".85"><rect x="59" y="71.2" width="34" height="1.6" rx=".8" fill="url(#' + id + 'H)"/><rect x="75.2" y="55" width="1.6" height="34" rx=".8" fill="url(#' + id + 'V)"/></g>' +
      '</g></svg>';

    var svg = el.firstChild;
    var ojos = el.getElementsByClassName("nova-ojo");
    var sonrisa = el.getElementsByClassName("nova-sonrisa")[0];
    var estado = opciones.estado || "escucha";
    var mira = opciones.mira || [0, 0];
    var cierre = 1; // 1 abiertos, ~0,12 en el parpadeo

    function pintar() {
      var o = OJOS[estado] || OJOS.escucha;
      var alto = o[4] * cierre, dy = (o[4] - alto) / 2;
      var xs = [o[0], o[1]];
      for (var i = 0; i < 2; i++) {
        ojos[i].setAttribute("x", xs[i] + mira[0]);
        ojos[i].setAttribute("y", o[2] + dy + mira[1]);
        ojos[i].setAttribute("width", o[3]);
        ojos[i].setAttribute("height", alto);
        ojos[i].setAttribute("opacity", estado === "termino" ? "0" : "1");
      }
      sonrisa.setAttribute("opacity", estado === "termino" ? "1" : "0");
      sonrisa.setAttribute("transform", "translate(" + mira[0] * 0.6 + " " + mira[1] * 0.6 + ")");
      svg.setAttribute("class", "nova-" + estado);
    }

    // Parpadeo cada 4 a 7 s, solo con los ojos abiertos y si se ve.
    function parpadear() {
      setTimeout(function () {
        if (estado !== "termino" && el.offsetWidth) {
          cierre = 0.12; pintar();
          setTimeout(function () { cierre = 1; pintar(); }, 150);
        }
        parpadear();
      }, 4000 + Math.random() * 3000);
    }

    pintar();
    parpadear();
    return {
      estado: function (e) { estado = e; pintar(); },
      mira: function (x, y) { mira = [x, y]; pintar(); },
    };
  }

  window.NovaTV = { crear: crear };
})();
