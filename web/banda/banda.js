// Bandas (2.0): lee el codigo de lifemusic.app/banda/CODIGO y arma el boton para abrir la app.
// En Android, el enlace intent:// abre Life Music si esta instalada; si no, va a la descarga.
(function () {
  var m = location.pathname.match(/\/banda\/([A-Za-z0-9]{6})/);
  if (!m) return;
  var codigo = m[1].toUpperCase();
  document.getElementById("codigo").textContent = codigo.slice(0, 3) + " " + codigo.slice(3);
  if (/Android/i.test(navigator.userAgent)) {
    document.getElementById("abrir").href = "intent://lifemusic.app/banda/" + codigo +
      "#Intent;scheme=https;package=com.cglabs.lifemusic;S.browser_fallback_url=" +
      encodeURIComponent("https://lifemusic.app/descargar") + ";end";
  }
})();
