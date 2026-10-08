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
   navegadores de TV pueden ser muy viejos. Ver docs/versiones/1.3.1/tv-por-codigo.md.

   1.3.2 (enlace seguro, docs/versiones/1.3.2/tv-enlace-seguro.md): el codigo
   vale 5 minutos con cuenta regresiva (y el relevo lo hace cumplir), «enlazar
   otro telefono» pide confirmacion y desconecta de verdad al anterior, latido
   con el telefono para saber si sigue ahi, estado del servidor en pantalla y
   el enlace recordado vence a los 30 dias sin uso. */
(function () {
  "use strict";

  var params = {};
  location.search.slice(1).split("&").forEach(function (p) {
    var kv = p.split("=");
    if (kv[0]) params[kv[0]] = decodeURIComponent(kv[1] || "");
  });

  var RELEVO = params.relevo || "wss://lifemusic-enlace.cho--usic.workers.dev";
  var ENLAZAR = (/^https:\/\/([a-z0-9-]+\.)?lifemusic\.pages\.dev$/.test(location.origin) ? location.origin : "https://lifemusic.pages.dev") + "/tv/enlazar?c=";
  var ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
  var RELEVO_HTTP = RELEVO.replace(/^ws/, "http");
  var VIDA_CODIGO = 5 * 60 * 1000;
  var CLAVE_TOKEN = "lm.tv.token", CLAVE_TELEFONO = "lm.tv.telefono", CLAVE_USADO = "lm.tv.usado";
  var DIAS_RECUERDO = 30;
  var LATIDO_MS = 10000, SILENCIO_MS = 30000, GRACIA_MS = 15000;
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
    alternarClase(document.body, "enlazando", si);
    if (!si) pararVigencia();
  }
  function ponerTexto(id, texto) { var e = $(id); if (e) e.textContent = texto; }
  function alternarClase(el, clase, si) {
    if (!el) return;
    var re = new RegExp("\\s*\\b" + clase + "\\b", "g");
    el.className = el.className.replace(re, "") + (si ? " " + clase : "");
  }

  /** El codigo en fichas: «K7P · 2XQ». */
  function ponerCodigo(c) {
    var caja = $("enlace-codigo");
    if (!caja) return;
    while (caja.firstChild) caja.removeChild(caja.firstChild);
    caja.setAttribute("aria-label", "Código " + c.slice(0, 3) + " " + c.slice(3));
    for (var i = 0; i < c.length; i++) {
      if (i === 3) { var s = document.createElement("span"); s.className = "enl-separa"; caja.appendChild(s); }
      var f = document.createElement("span");
      f.className = "enl-ficha";
      f.style.animationDelay = (i * 0.05) + "s";
      f.textContent = c.charAt(i);
      caja.appendChild(f);
    }
  }

  // Cuenta regresiva de la vigencia del codigo: barra, anillo del QR y «4:12».
  var vigenciaHasta = 0, relojVigencia = null, LARGO_ARO = 295.3;
  function pintarVigencia() {
    var quedan = Math.max(0, vigenciaHasta - Date.now());
    var s = Math.ceil(quedan / 1000), m = Math.floor(s / 60);
    var fraccion = quedan / VIDA_CODIGO;
    var barra = $("enl-barra"), aro = $("enl-aro"), vence = $("enl-vence");
    if (barra) barra.style.width = (fraccion * 100) + "%";
    if (aro) aro.style.strokeDashoffset = String(LARGO_ARO * (1 - fraccion));
    if (vence) {
      while (vence.firstChild) vence.removeChild(vence.firstChild);
      vence.appendChild(document.createTextNode("Vence en "));
      var b = document.createElement("b");
      b.textContent = m + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
      vence.appendChild(b);
      vence.appendChild(document.createTextNode(" · luego sale otro solo"));
    }
  }
  function iniciarVigencia() {
    vigenciaHasta = Date.now() + VIDA_CODIGO;
    alternarClase($("enl-vigencia"), "oculto", false);
    pintarVigencia();
    clearInterval(relojVigencia);
    relojVigencia = setInterval(pintarVigencia, 1000);
  }
  function pararVigencia() {
    clearInterval(relojVigencia);
    relojVigencia = null;
  }

  // Estado del servidor (1.3.2): el chip «● Servidor en línea» de la pantalla de enlace.
  function ponerServidor(bien) {
    var chip = $("enl-servidor");
    if (!chip) return;
    alternarClase(chip, "ok", bien);
    alternarClase(chip, "mal", !bien);
    ponerTexto("enl-servidor-texto", bien ? "Servidor en línea" : "Servidor sin respuesta · reintentando");
  }
  function revisarServidor() {
    try {
      var x = new XMLHttpRequest();
      x.open("GET", RELEVO_HTTP + "/salud", true);
      x.timeout = 8000;
      x.onload = function () {
        var bien = false;
        try { bien = x.status === 200 && JSON.parse(x.responseText).estado === "ok"; } catch (e) {}
        ponerServidor(bien);
      };
      x.onerror = x.ontimeout = function () { ponerServidor(false); };
      x.send();
    } catch (e) { ponerServidor(false); }
  }

  // Dialogos (1.3.2). El texto se arma con nodos: el nombre del telefono no se
  // mete nunca como HTML.
  var ICONOS = {
    telefono: '<svg viewBox="0 0 24 24"><rect x="5" y="2" width="10" height="20" rx="2.5"/><path d="M19 8v6M16 11h6"/></svg>',
    perdido: '<svg viewBox="0 0 24 24"><path d="M2 8.8a15 15 0 0 1 20 0M5 12.6a10 10 0 0 1 14 0M8.5 16.4a5 5 0 0 1 7 0M12 20h.01"/><path d="M3 3l18 18"/></svg>',
    pestana: '<svg viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="M2 9h20M7 4v5"/></svg>',
  };
  var dialogoActual = null;
  function mostrarDialogo(o) {
    var capa = $("tv-dialogo");
    if (!capa) return;
    dialogoActual = o.tipo;
    $("tv-dialogo-ico").innerHTML = ICONOS[o.icono] || "";
    capa.firstElementChild.className = "tv-dialogo" + (o.alerta ? " alerta" : "");
    ponerTexto("tv-dialogo-titulo", o.titulo);
    var p = $("tv-dialogo-texto");
    while (p.firstChild) p.removeChild(p.firstChild);
    for (var i = 0; i < o.texto.length; i++) {
      var t = o.texto[i];
      if (typeof t === "string") p.appendChild(document.createTextNode(t));
      else { var b = document.createElement("b"); b.textContent = t.b; p.appendChild(b); }
    }
    var si = $("tv-dialogo-si"), no = $("tv-dialogo-no");
    si.textContent = o.si;
    si.onclick = function () { cerrarDialogo(); if (o.alSi) o.alSi(); };
    if (o.no) { no.hidden = false; no.textContent = o.no; no.onclick = function () { cerrarDialogo(); if (o.alNo) o.alNo(); }; }
    else no.hidden = true;
    alternarClase(capa, "oculto", false);
    try { si.focus(); } catch (e) {}
  }
  function cerrarDialogo(tipo) {
    if (tipo && dialogoActual !== tipo) return;
    dialogoActual = null;
    alternarClase($("tv-dialogo"), "oculto", true);
  }

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
    // Sin sesgo: se descartan los bytes de 248 en adelante (248 = 31 x 8).
    var i = 0;
    while (s.length < 6) {
      if (azar && i >= azar.length) { try { (window.crypto || window.msCrypto).getRandomValues(azar); } catch (e) { azar = null; } i = 0; }
      var n = azar ? azar[i++] : Math.floor(Math.random() * 248);
      if (n < 248) s += ALFABETO.charAt(n % ALFABETO.length);
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
    ponerCodigo(codigo);
    ponerTexto("enlace-estado", "Esperando al teléfono…");
    dibujarQR(ENLAZAR + codigo);
    iniciarVigencia();
    wsCodigo = abrir("c-" + codigo,
      function () { R.diag("enlace: codigo listo"); ponerServidor(true); },
      function (m) {
        if (m.tipo === "_cerrado" && m.motivo === "codigo-vencido") { if (codigo === mio) pedirCodigo(); return; }
        if (m.tipo === "_enlazado" && m.token) {
          pararVigencia();
          guardar(CLAVE_USADO, String(Date.now()));
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
        revisarServidor();
        temporizadorCodigo = setTimeout(pedirCodigo, 2000);
      });
    // Un codigo dura 5 minutos (el relevo tampoco acepta uno mas viejo); despues, otro.
    temporizadorCodigo = setTimeout(function () { if (codigo === mio) pedirCodigo(); }, VIDA_CODIGO);
  }

  // Latido con el telefono (1.3.2): se le manda uno cada 10 s y se vigila que
  // el telefono siga hablando. Un telefono de la 1.3.1 no manda latidos: a ese
  // solo se le vigila por el relevo (cuando sale del cuarto).
  var ultimoDelTelefono = 0, telefonoConLatido = false, estuvoTelefono = false, despedido = false;
  var vigilancia = null, temporizadorAusencia = null, relevado = false;
  function arrancarLatido() {
    clearInterval(vigilancia);
    vigilancia = setInterval(function () {
      if (!telefonoPresente) return;
      if (telefonoConLatido) enviar({ tipo: "latido" });
      if (telefonoConLatido && Date.now() - ultimoDelTelefono > SILENCIO_MS && dialogoActual !== "perdido") mostrarPerdido();
    }, LATIDO_MS);
  }
  function pararLatido() {
    clearInterval(vigilancia);
    vigilancia = null;
    clearTimeout(temporizadorAusencia);
  }
  function mostrarPerdido() {
    mostrarDialogo({
      tipo: "perdido", icono: "perdido", alerta: true,
      titulo: "Se perdió la conexión con tu teléfono",
      texto: [{ b: leer(CLAVE_TELEFONO) || "Tu teléfono" }, " no responde. La canción que suena termina aquí y no sigue con otra. Si vuelve, seguimos solos."],
      si: "Enlazar otro teléfono", alSi: desenlazar,
      no: "Seguir esperando",
    });
  }

  /** «Enlazar otro telefono»: con un telefono enlazado, primero se confirma. */
  function pedirOtroTelefono() {
    var token = leer(CLAVE_TOKEN);
    if (!token) { mostrarEnlace(true); pedirCodigo(); return; }
    var nombre = leer(CLAVE_TELEFONO) || "tu teléfono";
    mostrarDialogo({
      tipo: "confirmar", icono: "telefono",
      titulo: "¿Enlazar otro teléfono?",
      texto: telefonoPresente
        ? ["Esto desconecta a ", { b: nombre }, " y para la música. Después sale un código nuevo."]
        : ["Esto olvida a ", { b: nombre }, ": para volver a usarlo tendrá que enlazarse con un código."],
      si: telefonoPresente ? "Sí, desconectar" : "Sí, olvidar", alSi: desenlazar,
      no: "Volver",
    });
  }

  /** Desconecta de verdad: avisa al telefono, para el audio, olvida el token y muestra un codigo. */
  function desenlazar() {
    enviar({ tipo: "desenlazado" });
    audio.pause();
    R.manejar({ tipo: "vaciar" });
    guardar(CLAVE_TOKEN, null);
    guardar(CLAVE_TELEFONO, null);
    guardar(CLAVE_USADO, null);
    pararLatido();
    clearTimeout(temporizadorReintento);
    var ws = wsToken;
    wsToken = null;
    // Se cierra un instante despues para que el aviso salga antes que el cierre.
    if (ws) setTimeout(function () { try { ws.onclose = null; ws.close(); } catch (e) {} }, 300);
    telefonoPresente = false;
    estuvoTelefono = false;
    cerrarDialogo();
    var bo = $("btn-codigo");
    if (bo) bo.style.display = "none";
    R.estadoSaludo("Esperando al teléfono…", false);
    mostrarEnlace(true);
    pedirCodigo();
  }

  function entrarAlToken(token) {
    if (wsToken) { try { wsToken.onclose = null; wsToken.close(); } catch (e) {} }
    relevado = false;
    var nombre = leer(CLAVE_TELEFONO) || "tu teléfono";
    R.estadoSaludo("Esperando a " + nombre + "…", false);
    arrancarLatido();
    wsToken = abrir(token,
      function () {
        reintento = 0;
        ponerServidor(true);
        clearInterval(latido);
        latido = setInterval(function () { try { wsToken && wsToken.send("ping"); } catch (e) {} }, 25000);
        R.ponerMandarDiag(function (texto) { enviar({ tipo: "diag", texto: texto }); });
      },
      function (m) {
        if (m.tipo === "_cerrado" && m.motivo === "reemplazado") {
          // La misma pagina se abrio en otra pestana o en otro navegador con este
          // enlace: esta se aparta, en vez de pelearse por el cuarto para siempre.
          relevado = true;
          audio.pause();
          mostrarDialogo({
            tipo: "pestana", icono: "pestana",
            titulo: "Life Music TV se abrió en otra pantalla",
            texto: ["Esta pantalla quedó en pausa. Si quieres seguir aquí, tráelo de vuelta."],
            si: "Usar esta pantalla", alSi: function () { entrarAlToken(token); },
          });
          return;
        }
        alMensaje(m);
      },
      function (e, ws) {
        if (ws !== wsToken) return;
        clearInterval(latido);
        if (relevado) return;
        revisarServidor();
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
      tipo: "listo", version: R.version, nombre: nombreDelTV(), perfil: capacidades.perfil, id: idActual,
      capacidades: { opus: capacidades.opus, aac: capacidades.aac, webgl: capacidades.webgl, video: capacidades.video, hls: capacidades.hls },
    });
    enviarEstado();
  }

  function alMensaje(m) {
    if (m.tipo && m.tipo.charAt(0) !== "_") { ultimoDelTelefono = Date.now(); if (dialogoActual === "perdido") cerrarDialogo("perdido"); }
    switch (m.tipo) {
      case "_par":
        if (m.rol === "tel" || m.nombres) {
          var antes = telefonoPresente;
          telefonoPresente = !!m.conectado;
          if (telefonoPresente) {
            if (m.nombre) guardar(CLAVE_TELEFONO, m.nombre);
            guardar(CLAVE_USADO, String(Date.now()));
            clearTimeout(temporizadorAusencia);
            var otro = $("btn-codigo");
            if (otro) otro.style.display = "";
            estuvoTelefono = true;
            despedido = false;
            ultimoDelTelefono = Date.now();
            cerrarDialogo("perdido");
            mostrarEnlace(false);
            R.estadoSaludo("Teléfono enlazado · elige una canción", true);
            if (!antes) avisarListo();
          } else {
            R.estadoSaludo("Esperando a " + (leer(CLAVE_TELEFONO) || "tu teléfono") + "…", false);
            // Si el telefono se fue sin despedirse, a los 15 s se dice (una recarga o un
            // cambio de red vuelven antes).
            clearTimeout(temporizadorAusencia);
            if (estuvoTelefono && !despedido) temporizadorAusencia = setTimeout(function () { if (!telefonoPresente) mostrarPerdido(); }, GRACIA_MS);
          }
        }
        break;
      case "_error": R.diag("relevo: " + m.error); break;
      case "_cerrado": break;
      case "latido": telefonoConLatido = true; ultimoDelTelefono = Date.now(); cerrarDialogo("perdido"); break;
      case "adios": despedido = true; ultimoDelTelefono = Date.now(); break;
      case "cargar": cargar(m); break;
      case "play": tocar(); break;
      case "pausa": audio.pause(); break;
      case "ir": if (typeof m.ms === "number") irA(m.ms); break;
      case "volumen": if (typeof m.v === "number") audio.volume = Math.max(0, Math.min(1, m.v)); break;
      case "olvidar":
        // El telefono olvido este TV: se borra el token y vuelve el codigo.
        pararLatido();
        cerrarDialogo();
        guardar(CLAVE_TOKEN, null);
        guardar(CLAVE_USADO, null);
        if (wsToken) { try { wsToken.onclose = null; wsToken.close(); } catch (e) {} wsToken = null; }
        audio.pause();
        R.manejar({ tipo: "vaciar" });
        mostrarEnlace(true);
        pedirCodigo();
        break;
      default:
        // cancion, letra, tempo, ajustes, saludo, vaciar: los mismos de Cast.
        // «vaciar» es la despedida de un telefono de la 1.3.1 al dejar de transmitir.
        if (m.tipo === "vaciar") despedido = true;
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
  // La barra de progreso usa la duracion real del audio (1.3.2), no solo la que
  // mando el telefono, que a veces no la sabe todavia.
  audio.addEventListener("durationchange", function () {
    if (isFinite(audio.duration) && audio.duration > 0 && R.ponerDuracion) R.ponerDuracion(idActual, Math.round(audio.duration * 1000));
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
    if (bo) bo.onclick = pedirOtroTelefono;
    revisarServidor();
    setInterval(function () { if (!/oculto/.test(elEnlace.className)) revisarServidor(); }, 60000);
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
      // El enlace recordado vence a los 30 dias sin uso (1.3.2).
      var usado = parseInt(leer(CLAVE_USADO) || "0", 10);
      if (token && usado && Date.now() - usado > DIAS_RECUERDO * 86400000) {
        R.diag("tv: el enlace recordado vencio (30 dias sin uso)");
        guardar(CLAVE_TOKEN, null); guardar(CLAVE_TELEFONO, null); guardar(CLAVE_USADO, null);
        token = null;
      }
      if (token && !usado) guardar(CLAVE_USADO, String(Date.now()));
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
