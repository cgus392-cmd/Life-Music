// Bandas (2.0): lee el codigo de lifemusic.app/banda/CODIGO y arma el boton para abrir la app.
// El codigo son 6 caracteres del alfabeto del TV ([2-9A-HJKMNP-Z]); se aceptan minusculas, espacios y
// guiones («k7m qzp», «K7M-QZP»), como cuando alguien lo copia de la maqueta o lo escribe a mano.
// En Android, el enlace intent:// abre Life Music si esta instalada; si no, va a la descarga.
(function () {
  var crudo = location.pathname.replace(/^\/banda\/?/, "");
  try { crudo = decodeURIComponent(crudo); } catch (e) {}
  var codigo = crudo.replace(/[\s\-\/]/g, "").toUpperCase();
  if (!/^[2-9A-HJKMNP-Z]{6}$/.test(codigo)) return;
  document.getElementById("codigo").textContent = codigo.slice(0, 3) + " " + codigo.slice(3);
  if (/Android/i.test(navigator.userAgent)) {
    document.getElementById("abrir").href = "intent://lifemusic.app/banda/" + codigo +
      "#Intent;scheme=https;package=com.cglabs.lifemusic;S.browser_fallback_url=" +
      encodeURIComponent("https://lifemusic.app/descargar") + ";end";
  }
})();
