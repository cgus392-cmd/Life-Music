/* Receptor de Life Music: el modo ambiente en el TV.
   Ver index.html. Sin modulos ni sintaxis reciente a proposito: los Chromecast
   viejos traen un Chrome antiguo. */
(function () {
  "use strict";

  var NS = "urn:x-cast:com.cglabs.lifemusic";
  var FPS_FONDO = 30;

  // ── Estado ────────────────────────────────────────────────────────────────
  var cancion = null;   // {id, titulo, artista, caratula, colores[], bpm, primerBeatMs, duracionMs}
  var lineas = [];      // [{t, texto, palabras:[{t, w}]}]
  var ajustes = { manchas: true, latido: true, canvas: true };
  var sonando = false;
  var posicion = function () { return 0; }; // ms; lo pone CAF o la demo

  var $ = function (id) { return document.getElementById(id); };
  // Estado del arranque, visible en la bienvenida: si algo falla en el TV, se lee ahi.
  function estado(texto) { var e = $("estado"); if (e) e.textContent = texto; }
  window.onerror = function (msg, src, linea) { estado("Error: " + msg + " (" + (src || "").split("/").pop() + ":" + linea + ")"); };
  var escena = $("escena");
  var elLetra = $("letra");
  var elTitulo = $("titulo");
  var elArtista = $("artista");
  var elCaratula = $("caratula");
  var elCanvas = $("canvas");
  var marco = $("caratula-marco");
  var elBarra = $("progreso-barra");

  // ── Cancion y letra ───────────────────────────────────────────────────────
  // El LOAD estandar y el mensaje propio «cancion» llegan por separado y en
  // cualquier orden: si son de la misma cancion se mezclan (solo los campos
  // que traen valor), si no, es cancion nueva y la letra vieja se va.
  var letraDeId = null;
  function ponerCancion(c) {
    var cambio = !cancion || cancion.id !== c.id;
    if (cambio) { cancion = c; } else {
      for (var k in c) if (c[k] !== null && c[k] !== undefined) cancion[k] = c[k];
    }
    c = cancion;
    escena.classList.remove("vacia");
    elTitulo.textContent = c.titulo || "";
    elArtista.textContent = c.artista || "";
    if (c.caratula && elCaratula.getAttribute("src") !== c.caratula) elCaratula.src = c.caratula;
    ponerCanvas(c.canvas);
    var nuevos = (c.colores && c.colores.length) ? c.colores.map(aRgb) : PALETA_LIFE.map(aRgb);
    if (cambio || nuevos.join() !== colores.join()) { colores = nuevos; reubicarManchas(); }
    if (letraDeId !== c.id) ponerLetra([], null);
  }

  // El canvas se ve solo si carga y mientras suena; si falla, queda la caratula.
  function ponerCanvas(url) {
    if (!ajustes.canvas || !url) {
      marco.classList.remove("con-canvas");
      if (elCanvas.getAttribute("src")) { elCanvas.pause(); elCanvas.removeAttribute("src"); elCanvas.load(); }
      return;
    }
    if (elCanvas.getAttribute("src") === url) return;
    marco.classList.remove("con-canvas");
    elCanvas.src = url;
    elCanvas.oncanplay = function () { marco.classList.add("con-canvas"); if (sonando) elCanvas.play(); };
    elCanvas.onerror = function () { marco.classList.remove("con-canvas"); };
    elCanvas.load();
  }
  function sincronizarCanvas() {
    if (!marco.classList.contains("con-canvas")) return;
    if (sonando && elCanvas.paused) { var p = elCanvas.play(); if (p && p.catch) p.catch(function () {}); }
    else if (!sonando && !elCanvas.paused) elCanvas.pause();
  }

  function ponerLetra(nuevas, id) {
    letraDeId = id === undefined ? (cancion ? cancion.id : null) : id;
    lineas = nuevas || [];
    elLetra.innerHTML = "";
    indiceVivo = -1;
    if (!lineas.length) { escena.classList.add("sin-letra"); return; }
    escena.classList.remove("sin-letra");
    for (var i = 0; i < lineas.length; i++) {
      var l = lineas[i];
      var div = document.createElement("div");
      div.className = "linea";
      if (l.palabras && l.palabras.length) {
        for (var j = 0; j < l.palabras.length; j++) {
          var span = document.createElement("span");
          span.className = "p";
          span.textContent = l.palabras[j].w + (j < l.palabras.length - 1 ? " " : "");
          div.appendChild(span);
        }
      } else {
        div.textContent = l.texto;
      }
      elLetra.appendChild(div);
    }
  }

  var indiceVivo = -1;
  var palabraViva = -1;
  function actualizarLetra(ms) {
    if (!lineas.length) return;
    var idx = -1;
    for (var i = 0; i < lineas.length; i++) { if (lineas[i].t <= ms) idx = i; else break; }
    if (idx !== indiceVivo) {
      var hijos = elLetra.children;
      for (var k = 0; k < hijos.length; k++) {
        hijos[k].className = "linea" + (k === idx ? " viva" : (k < idx ? " pasada" : ""));
      }
      indiceVivo = idx;
      palabraViva = -1;
      // La linea viva al centro del panel.
      var objetivo = idx >= 0 ? hijos[idx] : hijos[0];
      var y = objetivo ? (objetivo.offsetTop + objetivo.offsetHeight / 2) : 0;
      elLetra.style.transform = "translateY(" + (-y) + "px)";
    }
    if (idx >= 0 && lineas[idx].palabras && lineas[idx].palabras.length) {
      var ps = lineas[idx].palabras;
      var pv = -1;
      for (var m = 0; m < ps.length; m++) { if (ps[m].t <= ms) pv = m; else break; }
      if (pv !== palabraViva) {
        var spans = elLetra.children[idx].children;
        for (var n = 0; n < spans.length; n++) spans[n].className = "p" + (n <= pv ? " dicha" : "");
        palabraViva = pv;
      }
    }
  }

  // ── Fondo: manchas de luz que laten al tempo ──────────────────────────────
  var PALETA_LIFE = ["#34D399", "#0F5C43", "#1B7F5E", "#A7F3D0", "#0B2F24", "#2DD4BF"];
  var colores = PALETA_LIFE.map(aRgb);
  var lienzo = $("fondo");
  var ctx = lienzo.getContext("2d");
  var W = lienzo.width, H = lienzo.height;
  var manchas = [];

  function aRgb(hex) {
    var h = String(hex).replace("#", "");
    if (h.length === 8) h = h.slice(2); // AARRGGBB de Android
    var n = parseInt(h, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }

  function reubicarManchas() {
    manchas = [];
    for (var i = 0; i < 6; i++) {
      manchas.push({
        color: colores[i % colores.length],
        // Cada mancha recorre una curva de Lissajous distinta: nunca se repite igual.
        ax: 0.32 + 0.1 * Math.random(), ay: 0.28 + 0.1 * Math.random(),
        fx: 0.05 + 0.03 * Math.random(), fy: 0.04 + 0.03 * Math.random(),
        px: Math.random() * 6.28, py: Math.random() * 6.28,
        r: 0.42 + 0.18 * Math.random(),
      });
    }
  }
  reubicarManchas();

  function pulso(ms) {
    if (!ajustes.latido) return 0;
    if (cancion && cancion.bpm > 40 && sonando) {
      var periodo = 60000 / cancion.bpm;
      var fase = ((ms - (cancion.primerBeatMs || 0)) % periodo) / periodo;
      if (fase < 0) fase += 1;
      return Math.exp(-fase * 3.2); // golpe seco y caida
    }
    return 0.5 + 0.5 * Math.sin(ms / 1600); // sin tempo: respiracion lenta
  }

  var ultimoFotograma = 0;
  function dibujarFondo(ahora) {
    if (ahora - ultimoFotograma < 1000 / FPS_FONDO) return;
    ultimoFotograma = ahora;
    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = "#000";
    ctx.fillRect(0, 0, W, H);
    if (!ajustes.manchas) return;
    var t = ahora / 1000;
    var p = pulso(posicion());
    var escala = 1 + 0.16 * p;
    var alfa = (cancion ? 0.62 : 0.35) * (1 + 0.25 * p);
    ctx.globalCompositeOperation = "lighter";
    for (var i = 0; i < manchas.length; i++) {
      var m = manchas[i];
      var x = W * (0.5 + m.ax * Math.sin(t * m.fx * 6.28 + m.px));
      var y = H * (0.5 + m.ay * Math.sin(t * m.fy * 6.28 + m.py));
      var r = H * m.r * escala;
      var g = ctx.createRadialGradient(x, y, 0, x, y, r);
      var c = m.color;
      g.addColorStop(0, "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + alfa + ")");
      g.addColorStop(1, "rgba(" + c[0] + "," + c[1] + "," + c[2] + ",0)");
      ctx.fillStyle = g;
      ctx.beginPath(); ctx.arc(x, y, r, 0, 6.2832); ctx.fill();
    }
  }

  // ── Bucle ─────────────────────────────────────────────────────────────────
  function bucle(ahora) {
    dibujarFondo(ahora);
    if (cancion) {
      var ms = posicion();
      actualizarLetra(ms);
      if (cancion.duracionMs > 0) elBarra.style.width = Math.min(100, 100 * ms / cancion.duracionMs) + "%";
      sincronizarCanvas();
    }
    requestAnimationFrame(bucle);
  }
  requestAnimationFrame(bucle);

  // ── Mensajes del telefono (canal propio) ──────────────────────────────────
  function manejar(datos) {
    if (!datos) return;
    var d = typeof datos === "string" ? JSON.parse(datos) : datos;
    switch (d.tipo) {
      case "cancion": ponerCancion(d); break;
      case "letra":
        if (!cancion || !d.id || d.id === cancion.id) ponerLetra(d.lineas || [], d.id || (cancion ? cancion.id : null));
        break;
      case "ajustes":
        if (typeof d.manchas === "boolean") ajustes.manchas = d.manchas;
        if (typeof d.latido === "boolean") ajustes.latido = d.latido;
        if (typeof d.canvas === "boolean") { ajustes.canvas = d.canvas; ponerCanvas(cancion ? cancion.canvas : null); }
        break;
      case "vaciar":
        cancion = null; ponerLetra([], null); escena.classList.add("vacia"); break;
    }
  }

  // ── CAF: el reproductor del TV ────────────────────────────────────────────
  function iniciarCast() {
    estado("Iniciando Cast…");
    if (typeof cast === "undefined" || !cast.framework) { estado("No cargo el SDK de Cast (¿el TV tiene internet?)"); return; }
    var context = cast.framework.CastReceiverContext.getInstance();
    var pm = context.getPlayerManager();
    posicion = function () { return Math.max(0, pm.getCurrentTimeSec() * 1000); };

    context.addCustomMessageListener(NS, function (e) { manejar(e.data); });

    var T = cast.framework.events.EventType;
    // Lo que trae el LOAD estandar vale para pintar aunque el canal propio tarde.
    // La cancion se identifica por el id que manda el telefono (media.customData).
    // La URL del audio cambia entre estados sin que cambie la cancion; solo se
    // toma como cancion nueva si no hay id y la URL es otra distinta a la ultima.
    var ultimaUrl = null;
    pm.addEventListener(T.MEDIA_STATUS, function () {
      var info = pm.getMediaInformation();
      if (!info) return;
      var md = info.metadata || {};
      var img = (md.images && md.images.length) ? md.images[0].url : null;
      var idNuevo = info.customData && info.customData.mediaId;
      if (!idNuevo) {
        if (cancion && info.contentId === ultimaUrl) idNuevo = cancion.id;
        else idNuevo = info.contentId;
      }
      ultimaUrl = info.contentId;
      if (!cancion || cancion.id !== idNuevo) {
        ponerCancion({
          id: idNuevo, titulo: md.title, artista: md.artist, caratula: img,
          duracionMs: (info.duration || 0) * 1000,
        });
      } else if (info.duration) {
        cancion.duracionMs = info.duration * 1000;
      }
    });
    // Estado de reproduccion: CAF no tiene un evento «cambio de estado» unico; se
    // escuchan los del reproductor y se relee el estado en cada uno.
    function releerEstado() {
      var s = pm.getPlayerState();
      sonando = s === cast.framework.messages.PlayerState.PLAYING;
      escena.classList.toggle("pausa", s === cast.framework.messages.PlayerState.PAUSED);
    }
    [T.PLAYING, T.PAUSE, T.ENDED, T.MEDIA_FINISHED, T.PLAYER_LOAD_COMPLETE, T.BUFFERING, T.WAITING, T.SEEKED].forEach(function (tipo) {
      if (tipo) pm.addEventListener(tipo, releerEstado);
    });

    context.addEventListener(cast.framework.system.EventType.READY, function () { estado("Cast listo · esperando la cancion"); });
    context.addEventListener(cast.framework.system.EventType.ERROR, function (e) {
      var d = e && e.data;
      estado("Error de Cast: " + String(d && (d.reason || d.error || d.message || d.type) || d || e).slice(0, 200));
    });
    context.addEventListener(cast.framework.system.EventType.SENDER_CONNECTED, function () { estado("Telefono conectado"); });
    var opciones = new cast.framework.CastReceiverOptions();
    opciones.disableIdleTimeout = false;
    opciones.customNamespaces = {};
    opciones.customNamespaces[NS] = cast.framework.system.MessageType.JSON;
    context.start(opciones);
    estado("Cast iniciado · esperando al TV");
  }

  // ── Demo en el navegador (?demo=1) ────────────────────────────────────────
  function iniciarDemo() {
    var t0 = Date.now();
    var largo = 214000;
    sonando = true;
    posicion = function () { return (Date.now() - t0) % largo; };
    // Caratula de mentira: un degradado con las iniciales.
    var c = document.createElement("canvas"); c.width = c.height = 600;
    var g = c.getContext("2d");
    var grad = g.createLinearGradient(0, 0, 600, 600);
    grad.addColorStop(0, "#F59E0B"); grad.addColorStop(0.5, "#EF4444"); grad.addColorStop(1, "#7C3AED");
    g.fillStyle = grad; g.fillRect(0, 0, 600, 600);
    g.fillStyle = "rgba(0,0,0,0.25)"; g.beginPath(); g.arc(300, 300, 190, 0, 6.2832); g.fill();
    g.fillStyle = "#fff"; g.font = "700 150px sans-serif"; g.textAlign = "center"; g.textBaseline = "middle";
    g.fillText("LM", 300, 315);
    ponerCancion({
      id: "demo", titulo: "Noche de prueba", artista: "Life Music · Demo", caratula: c.toDataURL(), canvas: null,
      colores: ["#F59E0B", "#EF4444", "#7C3AED", "#FBBF24", "#DC2626", "#4C1D95"],
      bpm: 96, primerBeatMs: 400, duracionMs: largo,
    });
    // Letra de relleno, no es de ninguna cancion.
    var texto = [
      "Se apaga la sala y sube la luz",
      "las manchas de color siguen el compas",
      "la caratula grande, la letra al frente",
      "y el telefono en la mano, de mando",
      "esto es Life Music en tu televisor",
      "sin cables, sin cuentas, sin anuncios",
      "una cancion detras de otra, sola",
      "hasta que decidas parar",
    ];
    var ls = [];
    for (var i = 0; i < texto.length; i++) {
      var ws = texto[i].split(" "), palabras = [];
      var inicio = 2000 + i * 5200;
      for (var j = 0; j < ws.length; j++) palabras.push({ t: inicio + j * Math.round(4200 / ws.length), w: ws[j] });
      ls.push({ t: inicio, texto: texto[i], palabras: palabras });
    }
    ponerLetra(ls);
  }

  var demo = /[?&]demo=1/.test(location.search);
  if (demo) {
    iniciarDemo();
  } else {
    // En el TV nunca se cae a la demo: si Cast no arranca, se ve el error escrito.
    try { iniciarCast(); } catch (e) { estado("Error al iniciar Cast: " + (e && e.message ? e.message : e)); }
  }
})();
