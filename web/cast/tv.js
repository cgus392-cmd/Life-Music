/* Life Music TV en el navegador (lifemusic.pages.dev/tv).

   Para televisores sin Cast ni DLNA pero con navegador. La pagina es la misma
   del receptor de Cast (mismo dibujo, mismos temas); cambia como llegan las
   cosas. En vez del SDK de Cast:

   1. Se evalua el navegador (audio, WebGL, video, potencia...) y se elige un
      perfil: completo, medio o basico. Nada queda en blanco: si algo falta, se
      usa lo sencillo.
   2. Se enlaza con el telefono por el relevo (enlace/, un Worker de
      Cloudflare): el TV muestra un codigo y un QR; el telefono entra con el
      codigo y los dos reciben un token, que el TV guarda para la proxima vez.
   3. El audio lo toca un <audio> de esta pagina con la URL que manda el
      telefono (como en Cast: misma casa, misma IP publica). Lo demas (letra,
      colores, tema, saludo) son los mismos mensajes del canal propio de Cast.

   Escrito sin sintaxis reciente (ni let, ni flechas, ni plantillas): los
   navegadores de TV pueden ser muy viejos. Ver docs/versiones/1.3.1/tv-por-codigo.md. */
(function () {
  "use strict";

  var params = {};
  location.search.slice(1).split("&").forEach(function (p) {
    var kv = p.split("=");
    if (kv[0]) params[kv[0]] = decodeURIComponent(kv[1] || "");
  });

  var RELEVO = params.relevo || "wss://lifemusic-enlace.cho--usic.workers.dev";
  var ENLAZAR = "https://lifemusic.pages.dev/tv/enlazar?c=";
  var ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
  var VIDA_CODIGO = 10 * 60 * 1000;
  var CLAVE_TOKEN = "lm.tv.token", CLAVE_TELEFONO = "lm.tv.telefono";
  var FORZAR_BASICO = params.forzar === "basico";

  var $ = function (id) { return document.getElementById(id); };
  var R = null; // los ganchos del receptor (receptor.js)

  function leer(clave) { try { return window.localStorage.getItem(clave); } catch (e) { return null; } }
  function guardar(clave, valor) {
    try {
      if (valor === null) window.localStorage.removeItem(clave);
      else window.localStorage.setItem(clave, valor);
      return true;
    } catch (e) { return false; }
  }

  // ── 1. Evaluacion del navegador ───────────────────────────────────────────
  function puedeAudio(tipo) {
    try { var a = document.createElement("audio"); return !!(a.canPlayType && a.canPlayType(tipo).replace(/no/, "")); }
    catch (e) { return false; }
  }
  function puedeVideo(tipo) {
    try { var v = document.createElement("video"); return !!(v.canPlayType && v.canPlayType(tipo).replace(/no/, "")); }
    catch (e) { return false; }
  }
  function tieneWebGL() {
    try {
      var c = document.createElement("canvas");
      return !!(window.WebGLRenderingContext && (c.getContext("webgl") || c.getContext("experimental-webgl")));
    } catch (e) { return false; }
  }
  function soporta(prop, valor) {
    try { return !!(window.CSS && CSS.supports && CSS.supports(prop, valor)); } catch (e) { return false; }
  }
  function puedeRecordar() {
    try { window.localStorage.setItem("lm.prueba", "1"); window.localStorage.removeItem("lm.prueba"); return true; }
    catch (e) { return false; }
  }
  function puedePantallaCompleta() {
    var d = document.documentElement;
    return !!(d.requestFullscreen || d.webkitRequestFullscreen || d.webkitRequestFullScreen || d.mozRequestFullScreen || d.msRequestFullscreen);
  }

  /** Mide cuantos cuadros por segundo da el navegador durante ~1 s. */
  function medirCuadros(listo) {
    var raf = window.requestAnimationFrame || window.webkitRequestAnimationFrame;
    if (!raf) { listo(20); return; }
    var inicio = 0, cuadros = 0;
    function tic(t) {
      if (!inicio) inicio = t;
      cuadros++;
      if (t - inicio < 1000) raf(tic);
      else listo(Math.round(cuadros * 1000 / (t - inicio)));
    }
    raf(tic);
  }

  function evaluar(listo) {
    var c = {
      opus: puedeAudio('audio/webm; codecs="opus"'),
      aac: puedeAudio('audio/mp4; codecs="mp4a.40.2"'),
      websocket: "WebSocket" in window,
      webgl: tieneWebGL(),
      video: puedeVideo('video/mp4; codecs="avc1.42E01E"'),
      hls: !!(window.MediaSource || window.WebKitMediaSource) || puedeVideo("application/vnd.apple.mpegurl"),
      variablesCss: soporta("--x", "0"),
      cristalCss: soporta("backdrop-filter", "blur(2px)") || soporta("-webkit-backdrop-filter", "blur(2px)"),
      pantallaCompleta: puedePantallaCompleta(),
      despierta: !!(navigator.wakeLock && navigator.wakeLock.request),
      recordar: puedeRecordar(),
      nucleos: navigator.hardwareConcurrency || 0,
      memoriaGb: navigator.deviceMemory || 0,
      fps: 0,
    };
    if (FORZAR_BASICO) { c.opus = false; c.webgl = false; c.video = false; c.hls = false; c.cristalCss = false; }
    medirCuadros(function (fps) {
      c.fps = FORZAR_BASICO ? 18 : fps;
      var flojo = (c.nucleos && c.nucleos <= 2) || (c.memoriaGb && c.memoriaGb <= 1);
      if (!c.variablesCss || c.fps < 24 || flojo || FORZAR_BASICO) c.perfil = "basico";
      else if (!c.webgl || c.fps < 45 || (c.nucleos && c.nucleos <= 4)) c.perfil = "medio";
      else c.perfil = "completo";
      listo(c);
    });
  }

  function lineaDeCompatibilidad(c) {
    var partes = [];
    partes.push(c.opus ? "audio Opus" : c.aac ? "audio AAC" : "sin audio compatible");
    partes.push(c.webgl ? "cristal GPU" : "cristal 2D");
    if (!c.video) partes.push("paisajes quietos");
    if (!c.hls) partes.push("sin canvas");
    partes.push("perfil " + c.perfil);
    return "Este TV: " + partes.join(" · ");
  }

  /** Un nombre para que el telefono recuerde este TV. */
  function nombreDelTV() {
    var ua = navigator.userAgent || "";
    if (/Web0S|webOS/i.test(ua)) return "TV LG";
    if (/Tizen/i.test(ua)) return "TV Samsung";
    if (/Android/i.test(ua)) return "TV Android";
    if (/CrKey/i.test(ua)) return "Chromecast";
    if (/Windows|Macintosh|Linux/i.test(ua)) return "Navegador del computador";
    return "TV con navegador";
  }

  // ── 2. Pantalla del enlace ────────────────────────────────────────────────
  var elEnlace = $("enlace");
  function mostrarEnlace(si) {
    if (!elEnlace) return;
    if (si) elEnlace.className = elEnlace.className.replace(/\s*oculto/g, "");
    else if (!/oculto/.test(elEnlace.className)) elEnlace.className += " oculto";
  }
  function ponerTexto(id, texto) { var e = $(id); if (e) e.textContent = texto; }

  function dibujarQR(texto) {
    var lienzo = $("enlace-qr");
    if (!lienzo || !window.qrcode) return;
    var qr = window.qrcode(0, "M");
    qr.addData(texto);
    qr.make();
    var n = qr.getModuleCount(), margen = 2, celda = Math.floor(lienzo.width / (n + margen * 2));
    var g = lienzo.getContext("2d");
    var ancho = celda * (n + margen * 2);
    var desde = Math.floor((lienzo.width - ancho) / 2);
    g.fillStyle = "#FFFFFF";
    g.fillRect(0, 0, lienzo.width, lienzo.height);
    g.fillStyle = "#0B0F0D";
    for (var f = 0; f < n; f++) {
      for (var k = 0; k < n; k++) {
        if (qr.isDark(f, k)) g.fillRect(desde + (k + margen) * celda, desde + (f + margen) * celda, celda, celda);
      }
    }
  }

  // ── 3. Conexion con el relevo ─────────────────────────────────────────────
  var capacidades = null;
  var wsCodigo = null, wsToken = null;
  var codigo = null, temporizadorCodigo = null;
  var telefonoPresente = false;
  var reintento = 0, temporizadorReintento = null, latido = null;

  function nuevoCodigo() {
    var s = "";
    var azar = null;
    try { azar = new Uint8Array(6); (window.crypto || window.msCrypto).getRandomValues(azar); } catch (e) { azar = null; }
    for (var i = 0; i < 6; i++) {
      var n = azar ? azar[i] % ALFABETO.length : Math.floor(Math.random() * ALFABETO.length);
      s += ALFABETO.charAt(n);
    }
    return s;
  }

  function abrir(cuarto, alAbrir, alMensaje, alCerrar) {
    var ws;
    try { ws = new WebSocket(RELEVO + "/cuarto/" + cuarto + "?rol=tv&nombre=" + encodeURIComponent(nombreDelTV())); }
    catch (e) { alCerrar(e); return null; }
    ws.onopen = alAbrir;
    ws.onmessage = function (e) {
      if (e.data === "pong") return;
      var m;
      try { m = JSON.parse(e.data); } catch (x) { return; }
      alMensaje(m, ws);
    };
    ws.onclose = function (e) { alCerrar(e, ws); };
    ws.onerror = function () { /* llega tambien onclose */ };
    return ws;
  }

  /** Muestra un codigo nuevo y espera al telefono en su cuarto. */
  function pedirCodigo() {
    if (wsCodigo) { try { wsCodigo.onclose = null; wsCodigo.close(); } catch (e) {} wsCodigo = null; }
    clearTimeout(temporizadorCodigo);
    codigo = nuevoCodigo();
    var mio = codigo;
    ponerTexto("enlace-codigo", codigo.slice(0, 3) + " " + codigo.slice(3));
    ponerTexto("enlace-estado", "Esperando al teléfono…");
    dibujarQR(ENLAZAR + codigo);
    wsCodigo = abrir("c-" + codigo,
      function () { R.diag("enlace: codigo listo"); },
      function (m) {
        if (m.tipo === "_enlazado" && m.token) {
          guardar(CLAVE_TOKEN, m.token);
          if (m.nombre) guardar(CLAVE_TELEFONO, m.nombre);
          ponerTexto("enlace-estado", "¡Enlazado con " + (m.nombre || "tu teléfono") + "!");
          try { wsCodigo.onclose = null; wsCodigo.close(); } catch (e) {}
          wsCodigo = null;
          clearTimeout(temporizadorCodigo);
          entrarAlToken(m.token);
        }
      },
      function (e) {
        if (codigo !== mio) return;
        // 409: otro TV ya usa ese codigo. Cualquier otro cierre: se reintenta con otro.
        ponerTexto("enlace-estado", "Reconectando…");
        temporizadorCodigo = setTimeout(pedirCodigo, 2000);
      });
    // Un codigo dura 10 minutos; despues, otro.
    temporizadorCodigo = setTimeout(function () { if (codigo === mio) pedirCodigo(); }, VIDA_CODIGO);
  }

  function entrarAlToken(token) {
    if (wsToken) { try { wsToken.onclose = null; wsToken.close(); } catch (e) {} }
    var nombre = leer(CLAVE_TELEFONO) || "tu teléfono";
    R.estadoSaludo("Esperando a " + nombre + "…", false);
    wsToken = abrir(token,
      function () {
        reintento = 0;
        clearInterval(latido);
        latido = setInterval(function () { try { wsToken && wsToken.send("ping"); } catch (e) {} }, 25000);
        R.ponerMandarDiag(function (texto) { enviar({ tipo: "diag", texto: texto }); });
      },
      function (m) { alMensaje(m); },
      function (e, ws) {
        if (ws !== wsToken) return;
        clearInterval(latido);
        telefonoPresente = false;
        R.estadoSaludo("Sin conexión · reintentando…", false);
        var espera = [1000, 2000, 5000, 10000][Math.min(reintento, 3)];
        reintento++;
        clearTimeout(temporizadorReintento);
        temporizadorReintento = setTimeout(function () { entrarAlToken(token); }, espera);
      });
  }

  function enviar(m) {
    if (!wsToken || wsToken.readyState !== 1) return;
    try { wsToken.send(JSON.stringify(m)); } catch (e) {}
  }

  function avisarListo() {
    enviar({
      tipo: "listo", version: R.version, nombre: nombreDelTV(), perfil: capacidades.perfil,
      capacidades: { opus: capacidades.opus, aac: capacidades.aac, webgl: capacidades.webgl, video: capacidades.video, hls: capacidades.hls },
    });
    enviarEstado();
  }

  function alMensaje(m) {
    switch (m.tipo) {
      case "_par":
        if (m.rol === "tel" || m.nombres) {
          var antes = telefonoPresente;
          telefonoPresente = !!m.conectado;
          if (telefonoPresente) {
            if (m.nombre) guardar(CLAVE_TELEFONO, m.nombre);
            mostrarEnlace(false);
            R.estadoSaludo("Teléfono enlazado · elige una canción", true);
            if (!antes) avisarListo();
          } else {
            R.estadoSaludo("Esperando a " + (leer(CLAVE_TELEFONO) || "tu teléfono") + "…", false);
          }
        }
        break;
      case "_error": R.diag("relevo: " + m.error); break;
      case "cargar": cargar(m); break;
      case "play": tocar(); break;
      case "pausa": audio.pause(); break;
      case "ir": if (typeof m.ms === "number") irA(m.ms); break;
      case "volumen": if (typeof m.v === "number") audio.volume = Math.max(0, Math.min(1, m.v)); break;
      case "olvidar":
        // El telefono olvido este TV: se borra el token y vuelve el codigo.
        guardar(CLAVE_TOKEN, null);
        if (wsToken) { try { wsToken.onclose = null; wsToken.close(); } catch (e) {} wsToken = null; }
        audio.pause();
        R.manejar({ tipo: "vaciar" });
        mostrarEnlace(true);
        pedirCodigo();
        break;
      default:
        // cancion, letra, tempo, ajustes, saludo, vaciar: los mismos de Cast.
        R.manejar(m);
    }
  }

  // ── 4. El audio ───────────────────────────────────────────────────────────
  var audio = document.createElement("audio");
  audio.preload = "auto";
  var idActual = null, desdeMs = 0, cargando = false, ultimoEstado = 0;

  function cargar(m) {
    if (!m.url) return;
    idActual = m.id;
    desdeMs = m.desdeMs || 0;
    cargando = true;
    R.manejar({ tipo: "cancion", id: m.id, titulo: m.titulo, artista: m.artista, caratula: m.caratula, duracionMs: m.duracionMs || 0 });
    audio.src = m.url;
    try { audio.load(); } catch (e) {}
    if (m.reproducir !== false) tocar(); else enviarEstado();
  }

  function irA(ms) {
    try { audio.currentTime = ms / 1000; } catch (e) { desdeMs = ms; }
  }

  function tocar() {
    var p;
    try { p = audio.play(); } catch (e) { pedirToque(); return; }
    if (p && p.then) p.then(null, function (e) { if (e && e.name === "NotAllowedError") pedirToque(); });
  }

  /** El navegador no deja sonar sin un toque: «Presiona OK para activar el sonido». */
  function pedirToque() {
    var b = $("activar");
    if (!b) return;
    b.className = b.className.replace(/\s*oculto/g, "");
    var boton = $("activar-boton");
    if (boton) { try { boton.focus(); } catch (e) {} }
    R.diag("audio: el navegador pide un toque");
  }
  function activar() {
    var b = $("activar");
    if (b && !/oculto/.test(b.className)) b.className += " oculto";
    tocar();
  }

  function enviarEstado() {
    ultimoEstado = Date.now();
    enviar({
      tipo: "estado", id: idActual,
      posMs: Math.round((audio.currentTime || 0) * 1000),
      durMs: isFinite(audio.duration) ? Math.round(audio.duration * 1000) : 0,
      sonando: !audio.paused && !cargando,
      cargando: cargando,
      volumen: audio.volume,
    });
  }

  function reflejar() {
    R.ponerSonando(!audio.paused && !cargando, audio.paused);
    enviarEstado();
  }

  audio.addEventListener("loadedmetadata", function () {
    if (desdeMs > 0) { try { audio.currentTime = desdeMs / 1000; } catch (e) {} desdeMs = 0; }
  });
  audio.addEventListener("playing", function () { cargando = false; reflejar(); });
  audio.addEventListener("pause", reflejar);
  audio.addEventListener("waiting", function () { cargando = true; reflejar(); });
  audio.addEventListener("seeked", reflejar);
  audio.addEventListener("volumechange", enviarEstado);
  audio.addEventListener("timeupdate", function () { if (Date.now() - ultimoEstado > 1000) enviarEstado(); });
  audio.addEventListener("ended", function () {
    R.ponerSonando(false, false);
    enviar({ tipo: "fin", id: idActual });
  });
  audio.addEventListener("error", function () {
    cargando = false;
    var codigoError = audio.error ? audio.error.code : 0;
    // 2 = red (403 de YouTube suele llegar asi), 4 = formato o fuente no admitida.
    R.diag("audio: error " + codigoError);
    enviar({ tipo: "error", id: idActual, codigo: codigoError });
    reflejar();
  });

  // ── 5. Pantalla completa ──────────────────────────────────────────────────
  function alternarPantallaCompleta() {
    var d = document.documentElement;
    var dentro = document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement;
    try {
      if (dentro) (document.exitFullscreen || document.webkitExitFullscreen || document.mozCancelFullScreen || document.msExitFullscreen).call(document);
      else (d.requestFullscreen || d.webkitRequestFullscreen || d.webkitRequestFullScreen || d.mozRequestFullScreen || d.msRequestFullscreen).call(d);
    } catch (e) { R.diag("pantalla completa: " + (e && e.message)); }
  }

  // ── 6. Pantalla encendida ─────────────────────────────────────────────────
  var candado = null;
  function despertar() {
    if (!navigator.wakeLock || document.visibilityState !== "visible" || candado) return;
    navigator.wakeLock.request("screen").then(function (c) {
      candado = c;
      c.addEventListener("release", function () { candado = null; });
    }, function () { candado = null; });
  }

  // ── Arranque ──────────────────────────────────────────────────────────────
  function iniciar(ganchos) {
    R = ganchos;
    R.ponerPosicion(function () { return Math.max(0, (audio.currentTime || 0) * 1000); });
    document.body.className += " modo-tv";
    mostrarEnlace(true);
    ponerTexto("enlace-estado", "Preparando tu TV…");
    R.estado("");

    var bp = $("btn-pantalla"), bo = $("btn-codigo"), ba = $("activar-boton");
    if (bp) bp.onclick = alternarPantallaCompleta;
    if (bo) bo.onclick = function () { mostrarEnlace(true); pedirCodigo(); };
    if (ba) ba.onclick = activar;
    document.addEventListener("visibilitychange", despertar);

    // Los mandos (pantalla completa, otro telefono) salen al tocar el control y
    // se esconden solos mientras suena la musica.
    var temporizadorMandos = null;
    function mostrarMandos() {
      if (!/\bmandos\b/.test(document.body.className)) document.body.className += " mandos";
      clearTimeout(temporizadorMandos);
      temporizadorMandos = setTimeout(function () { document.body.className = document.body.className.replace(/\s*\bmandos\b/g, ""); }, 6000);
    }
    document.addEventListener("keydown", mostrarMandos);
    document.addEventListener("mousemove", mostrarMandos);

    evaluar(function (c) {
      capacidades = c;
      ponerTexto("enlace-compat", lineaDeCompatibilidad(c));
      R.diag("tv: " + lineaDeCompatibilidad(c) + " · " + c.fps + " fps");
      if (bp && !c.pantallaCompleta) bp.style.display = "none";
      if (c.perfil !== "completo") R.ligero();
      if (!c.video) R.sinVideo();
      if (!c.opus && !c.aac) {
        ponerTexto("enlace-titulo", "Este navegador no puede reproducir la música");
        ponerTexto("enlace-estado", "Prueba con otro navegador del TV o usa Cast si tu TV lo tiene.");
        return;
      }
      if (!c.websocket) {
        ponerTexto("enlace-titulo", "Este navegador es demasiado antiguo para enlazar");
        ponerTexto("enlace-estado", "Prueba con otro navegador del TV.");
        return;
      }
      if (!c.despierta) ponerTexto("enlace-nota", "Si el TV se apaga solo, desactiva su protector de pantalla.");
      despertar();
      var token = leer(CLAVE_TOKEN);
      if (token && /^t-[0-9a-f]{32}$/.test(token)) {
        // Ya enlazado antes: se espera al telefono sin codigo.
        mostrarEnlace(false);
        if (bo) bo.style.display = "";
        entrarAlToken(token);
      } else {
        pedirCodigo();
      }
      if (bp && c.pantallaCompleta) { try { bp.focus(); } catch (e) {} }
    });
  }

  window.LifeMusicTV = { iniciar: iniciar };
})();
