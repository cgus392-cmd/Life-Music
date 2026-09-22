/* Receptor de Life Music: el modo ambiente en el TV.
   Ver index.html. Sin modulos ni sintaxis reciente a proposito: los Chromecast
   viejos traen un Chrome antiguo. */
(function () {
  "use strict";

  var VERSION = "2026-09-21f";
  var NS = "urn:x-cast:com.cglabs.lifemusic";

  // ── Rendimiento ───────────────────────────────────────────────────────────
  // Los Android TV van desde un Chromecast viejo hasta un Shield: la pagina
  // mide cuanto tarda cada fotograma y, si el aparato no da (mas de 45 ms de
  // media sostenida, o sea menos de ~22 fps), pasa sola a modo ligero: fondo a
  // 15 fps y lienzo mas pequeno. No vuelve atras en la sesion (evita el vaiven).
  var FPS_FONDO = 24;
  var ligero = false;
  var mediaFrame = 16;   // ms por fotograma, media movil
  var ultimoTic = 0, fotogramas = 0, avisoRendimiento = false, lentoDesde = 0, cancionDesde = 0;
  function medirFotograma(ahora) {
    if (ultimoTic) {
      var d = Math.min(200, ahora - ultimoTic);
      mediaFrame += (d - mediaFrame) * 0.02; // media de ~1 s: un pico suelto no la mueve
      fotogramas++;
      // Solo cuenta lo sostenido: lento durante 4 s seguidos, y nunca en los
      // primeros 6 s de una cancion (entrar, cargar hls.js y arrancar el video
      // pesa un momento en cualquier TV y no dice nada de como ira despues).
      if (!ligero && cancion && ahora - cancionDesde > 6000) {
        if (mediaFrame > 45) { if (!lentoDesde) lentoDesde = ahora; else if (ahora - lentoDesde > 4000) activarLigero(); }
        else lentoDesde = 0;
      }
      if (!avisoRendimiento && cancion && ahora - cancionDesde > 20000) { avisoRendimiento = true; diag("rendimiento: " + mediaFrame.toFixed(1) + " ms/fotograma" + (ligero ? " (ligero)" : "")); }
    }
    ultimoTic = ahora;
  }
  function activarLigero() {
    ligero = true;
    FPS_FONDO = 15;
    redimensionarLienzo(288, 162);
    diag("modo ligero: " + mediaFrame.toFixed(1) + " ms/fotograma");
  }

  // ── Estado ────────────────────────────────────────────────────────────────
  var cancion = null;   // {id, titulo, artista, caratula, colores[], bpm, primerBeatMs, duracionMs, canvas}
  var lineas = [];      // [{t, texto, palabras:[{t, w}]}]
  var ajustes = { manchas: true, latido: true, canvas: true };
  var sonando = false;
  var posicion = function () { return 0; }; // ms; lo pone CAF o la demo

  var $ = function (id) { return document.getElementById(id); };
  var escena = $("escena");
  var elLetra = $("letra");
  var elTitulo = $("titulo");
  var elArtista = $("artista");
  var marco = $("caratula-marco");
  var elCanvas = $("canvas");
  var elBarra = $("progreso-barra");
  var elSaludo = $("saludo");
  var elEstadoSaludo = $("saludo-estado");

  // ── Diagnostico ───────────────────────────────────────────────────────────
  // Linea de estado en la bienvenida (se lee mirando el TV) y, si hay telefono,
  // el mismo texto viaja por el canal propio para que «Copiar diagnostico» lo
  // incluya: es la unica forma de saber que pasa dentro del receptor.
  var mandarDiag = null; // lo pone iniciarCast
  var diagPendiente = [];
  function estado(texto) { var e = $("estado"); if (e) e.textContent = texto; }
  function diag(texto) {
    estado(texto);
    if (mandarDiag) { try { mandarDiag(texto); } catch (e) { /* sin telefono aun */ } }
    else if (diagPendiente.length < 40) diagPendiente.push(texto);
  }
  window.onerror = function (msg, src, linea) { diag("Error: " + msg + " (" + (src || "").split("/").pop() + ":" + linea + ")"); };

  // ── Saludo ────────────────────────────────────────────────────────────────
  function saludoDelDia() {
    var h = new Date().getHours();
    if (h >= 5 && h < 12) return "Buenos dias";
    if (h >= 12 && h < 19) return "Buenas tardes";
    return "Buenas noches";
  }
  if (elSaludo) elSaludo.textContent = saludoDelDia();
  function estadoSaludo(texto, listo) {
    if (!elEstadoSaludo) return;
    elEstadoSaludo.textContent = texto;
    elEstadoSaludo.classList.toggle("listo", !!listo);
  }

  // ── Cancion y letra ───────────────────────────────────────────────────────
  // El LOAD estandar y el mensaje propio «cancion» llegan por separado y en
  // cualquier orden: si son de la misma cancion se mezclan (solo los campos
  // que traen valor), si no, es cancion nueva y la letra vieja se va.
  var letraDeId = null;
  function ponerCancion(c) {
    var cambio = !cancion || cancion.id !== c.id;
    var primera = !cancion;
    if (cambio) { cancion = c; } else {
      for (var k in c) if (c[k] !== null && c[k] !== undefined) cancion[k] = c[k];
    }
    c = cancion;
    if (cambio) cancionDesde = performance.now();
    if (primera) mostrarAmbiente();
    ponerTexto(elTitulo, c.titulo || "", cambio && !primera);
    ponerTexto(elArtista, c.artista || "", cambio && !primera);
    if (c.caratula) ponerCaratula(c.caratula, primera);
    ponerCanvas(c.canvas);
    var nuevos = (c.colores && c.colores.length) ? c.colores.map(aRgb) : null;
    if (nuevos && (cambio || nuevos.join() !== paletaNueva.join())) ponerPaleta(nuevos);
    if (cambio) elBarra.style.width = "0%";
    if (letraDeId !== c.id) ponerLetra([], null);
  }

  // El saludo de entrada de la app, en grande: llega del telefono al conectar
  // (mismas frases, misma voz). Si la cancion llega mientras se lee, espera a
  // que el saludo se vaya; asi no se pisan.
  var SALUDO_MS = 3400;
  var elSaludoPantalla = $("saludo-pantalla");
  var saludoActivo = false, saludoTemporizador = null, ambientePendiente = false;
  function mostrarSaludo(cabecera, frase) {
    if (!elSaludoPantalla) return;
    $("saludo-cabecera").textContent = cabecera || "";
    $("saludo-frase").textContent = frase || "";
    elSaludoPantalla.classList.add("visible");
    saludoActivo = true;
    clearTimeout(saludoTemporizador);
    saludoTemporizador = setTimeout(function () {
      elSaludoPantalla.classList.remove("visible");
      saludoActivo = false;
      if (ambientePendiente) { ambientePendiente = false; mostrarAmbiente(); }
    }, SALUDO_MS);
  }

  function mostrarAmbiente() {
    if (saludoActivo) { ambientePendiente = true; return; }
    escena.classList.remove("vacia");
    escena.classList.add("con-cancion");
  }
  function mostrarBienvenida() {
    escena.classList.add("vacia");
    escena.classList.remove("con-cancion");
    estadoSaludo("Elige una cancion en el telefono", true);
  }

  // Texto que cambia: sale hacia arriba, entra desde abajo.
  function ponerTexto(el, texto, animar) {
    if (el.textContent === texto) return;
    if (!animar) { el.textContent = texto; return; }
    el.classList.add("sale");
    setTimeout(function () {
      el.textContent = texto;
      el.classList.remove("sale");
      el.classList.add("entra");
      void el.offsetWidth; // que el navegador vea el estado inicial antes de animar
      el.classList.remove("entra");
    }, 240);
  }

  // Caratula: la nueva se funde encima de la vieja cuando ya cargo.
  var caratulaActual = null;
  function ponerCaratula(url, primera) {
    if (caratulaActual === url) return;
    caratulaActual = url;
    var img = document.createElement("img");
    img.className = "caratula entra";
    img.alt = "";
    img.onload = function () {
      if (caratulaActual !== url) return;
      marco.insertBefore(img, elCanvas);
      void img.offsetWidth;
      img.classList.remove("entra");
      var viejas = marco.querySelectorAll("img.caratula");
      setTimeout(function () {
        for (var i = 0; i < viejas.length; i++) if (viejas[i] !== img && viejas[i].parentNode) viejas[i].parentNode.removeChild(viejas[i]);
      }, primera ? 0 : 900);
    };
    img.onerror = function () { diag("caratula no cargo"); };
    img.src = url;
  }

  // El canvas se ve solo si carga y mientras suena; si falla, queda la caratula.
  // Los de Apple son HLS (.m3u8): un <video> normal no los entiende, asi que se
  // cargan con hls.js (solo cuando hace falta; son 600 KB) por Media Source.
  var canvasUrl = null;
  var hlsCanvas = null;
  function soltarHls() { if (hlsCanvas) { try { hlsCanvas.destroy(); } catch (e) { /* nada */ } hlsCanvas = null; } }
  function quitarCanvas() {
    soltarHls();
    canvasUrl = null;
    marco.classList.remove("con-canvas");
    if (elCanvas.getAttribute("src")) { elCanvas.pause(); elCanvas.removeAttribute("src"); elCanvas.load(); }
  }
  var hlsCargando = null;
  function conHls(cb) {
    if (window.Hls) { cb(window.Hls); return; }
    if (!hlsCargando) {
      hlsCargando = [];
      var s = document.createElement("script");
      s.src = "hls.min.js?v=1.7.3";
      s.onload = function () { var l = hlsCargando; hlsCargando = null; for (var i = 0; i < l.length; i++) l[i](window.Hls); };
      s.onerror = function () { hlsCargando = null; diag("canvas: no cargo hls.js"); };
      document.head.appendChild(s);
    }
    hlsCargando.push(cb);
  }
  function ponerCanvas(url) {
    if (!ajustes.canvas || !url) { if (canvasUrl) quitarCanvas(); return; }
    if (canvasUrl === url) return;
    quitarCanvas();
    canvasUrl = url;
    var host = (url.match(/^https?:\/\/([^\/]+)/) || [])[1] || "?";
    var esHls = /\.m3u8(\?|$)/.test(url);
    diag("canvas: cargando de " + host + (esHls ? " (HLS)" : ""));
    var avisado = false;
    elCanvas.oncanplay = function () {
      if (canvasUrl !== url) return;
      if (!avisado) { avisado = true; diag("canvas: listo " + elCanvas.videoWidth + "x" + elCanvas.videoHeight); }
      marco.classList.add("con-canvas");
      sincronizarCanvas();
    };
    elCanvas.onerror = function () {
      var e = elCanvas.error;
      diag("canvas: error " + (e ? e.code + " " + (e.message || "") : "?"));
      marco.classList.remove("con-canvas");
    };
    if (!esHls) {
      elCanvas.src = url;
      elCanvas.load();
      return;
    }
    // Siempre por hls.js si hay Media Source: el HLS «nativo» que anuncia el
    // WebView de Android (AirScreen) falla por debajo (PIPELINE_ERROR_EXTERNAL_RENDERER_FAILED).
    conHls(function (Hls) {
      if (canvasUrl !== url) return;
      if (!Hls || !Hls.isSupported()) {
        if (elCanvas.canPlayType("application/vnd.apple.mpegurl")) { diag("canvas HLS: sin Media Source, se prueba el nativo"); elCanvas.src = url; elCanvas.load(); }
        else diag("canvas HLS: este TV no tiene Media Source; queda la caratula");
        return;
      }
      // Bufer corto y calidad acotada al tamano del marco: el canvas es un adorno
      // de 8 s en bucle, no merece la memoria ni el decodificador de un estreno.
      hlsCanvas = new Hls({ capLevelToPlayerSize: true, maxBufferLength: 8, maxMaxBufferLength: 12, backBufferLength: 2, maxBufferSize: 12 * 1024 * 1024 });
      hlsCanvas.on(Hls.Events.ERROR, function (ev, d) {
        if (!d) return;
        diag("canvas HLS " + (d.fatal ? "fatal " : "") + d.type + "/" + d.details + (d.response && d.response.code ? " http " + d.response.code : ""));
        if (d.fatal) { marco.classList.remove("con-canvas"); soltarHls(); }
      });
      hlsCanvas.loadSource(url);
      hlsCanvas.attachMedia(elCanvas);
    });
  }
  function sincronizarCanvas() {
    if (!marco.classList.contains("con-canvas")) return;
    if (sonando && elCanvas.paused) {
      var p = elCanvas.play();
      if (p && p.catch) p.catch(function (e) { diag("canvas: play rechazado " + (e && e.name)); });
    } else if (!sonando && !elCanvas.paused) elCanvas.pause();
  }

  function ponerLetra(nuevas, id) {
    letraDeId = id === undefined ? (cancion ? cancion.id : null) : id;
    lineas = nuevas || [];
    indiceVivo = -1;
    elLetra.classList.add("oculta");
    var pintar = function () {
      elLetra.innerHTML = "";
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
      elLetra.style.transition = "none";
      actualizarLetra(posicion());
      void elLetra.offsetWidth;
      elLetra.style.transition = "";
      elLetra.classList.remove("oculta");
    };
    // Si habia letra, se le da tiempo a irse antes de pintar la nueva.
    if (elLetra.children.length) setTimeout(pintar, 320); else pintar();
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

  // ── Fondo: las luces del modo ambiente del telefono ───────────────────────
  // Es el mismo dibujo que AmbientGlowBackground en la app: seis luces grandes
  // y suaves con los colores de la caratula, que se pasean y cambian de color
  // en un ciclo de 20 s. En el telefono laten con el audio medido; aqui, con
  // el tempo que manda el telefono (el bombo ensancha las luces, la energia
  // las aviva). Al cambiar de cancion, la paleta se funde en 1,2 s.
  var PALETA_LIFE = ["#34D399", "#0F5C43", "#1B7F5E", "#A7F3D0", "#0B2F24", "#2DD4BF"];
  var CICLO_MS = 20000;
  var FUNDIDO_MS = 1200;
  var LUCES = [ // recorrido x, y, radio (en anchos), fases y alfas, tal cual la app
    { x: [0.0, 1.0], y: [0.0, 0.5], r: [0.8, 1.6], fx: 0.00, fy: 0.07, fr: 0.12, a: [0.85, 0.50] },
    { x: [1.0, 0.0], y: [0.5, 1.0], r: [0.7, 1.5], fx: 0.20, fy: 0.25, fr: 0.18, a: [0.80, 0.45] },
    { x: [0.2, 0.8], y: [0.8, 0.2], r: [0.6, 1.4], fx: 0.33, fy: 0.36, fr: 0.29, a: [0.75, 0.40] },
    { x: [0.3, 0.7], y: [0.2, 0.8], r: [0.9, 1.7], fx: 0.44, fy: 0.41, fr: 0.47, a: [0.70, 0.35] },
    { x: [0.4, 0.6], y: [0.0, 1.0], r: [0.7, 1.5], fx: 0.55, fy: 0.51, fr: 0.58, a: [0.65, 0.30] },
    { x: [0.0, 1.0], y: [0.5, 0.7], r: [0.8, 1.8], fx: 0.66, fy: 0.62, fr: 0.69, a: [0.60, 0.25] },
  ];
  var paletaVieja = PALETA_LIFE.map(aRgb);
  var paletaNueva = paletaVieja;
  var fundidoDesde = -1e9;
  var lienzo = $("fondo");
  var ctx = lienzo.getContext("2d");
  var W = 384, H = 216; // se escala a toda la pantalla; las luces son degradados y no se nota
  function redimensionarLienzo(w, h) { W = w; H = h; lienzo.width = w; lienzo.height = h; }
  redimensionarLienzo(W, H);

  function aRgb(hex) {
    var h = String(hex).replace("#", "");
    if (h.length === 8) h = h.slice(2); // AARRGGBB de Android
    var n = parseInt(h, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  function ponerPaleta(nueva) {
    paletaVieja = paletaActual(performance.now());
    paletaNueva = nueva;
    fundidoDesde = performance.now();
  }
  // La paleta tal como se ve ahora (a medio fundido), para encadenar cambios sin saltos.
  function paletaActual(ahora) {
    var m = Math.min(1, (ahora - fundidoDesde) / FUNDIDO_MS);
    var n = Math.max(paletaVieja.length, paletaNueva.length), r = [];
    for (var i = 0; i < n; i++) r.push(mezclar(paletaVieja[i % paletaVieja.length], paletaNueva[i % paletaNueva.length], m));
    return r;
  }
  function mezclar(a, b, t) { return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; }
  function colorRotado(paleta, indice, progreso) {
    var n = paleta.length;
    var idx = indice + progreso * n;
    var a = Math.floor(idx) % n, b = (a + 1) % n;
    return mezclar(paleta[a], paleta[b], idx - Math.floor(idx));
  }
  function oscilar(min, max, fase, progreso) {
    var v = Math.sin(6.2832 * (progreso + fase));
    return min + (max - min) * ((v + 1) * 0.5);
  }

  // Latido con el tempo: golpe seco en cada beat y caida rapida, como el bombo
  // medido en el telefono. Sin tempo, una respiracion lenta y discreta.
  function pulso(ms) {
    if (!ajustes.latido || !sonando) return 0;
    if (cancion && cancion.bpm > 40) {
      var periodo = 60000 / cancion.bpm;
      var fase = ((ms - (cancion.primerBeatMs || 0)) % periodo) / periodo;
      if (fase < 0) fase += 1;
      return Math.exp(-fase * 4);
    }
    return 0.12 + 0.12 * Math.sin(ms / 2200);
  }
  var latido = 0, viveza = 0;

  var ultimoFotograma = 0;
  function dibujarFondo(ahora) {
    // En pausa o en la bienvenida no hay latido que seguir: menos fotogramas.
    var fps = cancion ? (sonando ? FPS_FONDO : 12) : Math.min(FPS_FONDO, 15);
    if (ahora - ultimoFotograma < 1000 / fps) return;
    var dt = Math.min(100, ahora - ultimoFotograma);
    ultimoFotograma = ahora;
    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = "#050505";
    ctx.fillRect(0, 0, W, H);
    if (!ajustes.manchas) return;

    // Suavizado corto (70 ms el golpe, 160 ms la energia) para que el latido se
    // vea como latido y no como parpadeo.
    var p = pulso(posicion());
    latido += (p - latido) * Math.min(1, dt / 70);
    var energiaObjetivo = sonando ? 0.35 + 0.4 * p : 0;
    viveza += (energiaObjetivo - viveza) * Math.min(1, dt / 160);
    var escalaRadio = 1 + latido * 0.22;
    var escalaAlfa = 1 + viveza * 0.3;
    var atenuar = cancion ? 1 : 0.55; // en la bienvenida, mas tenue

    var progreso = (ahora % CICLO_MS) / CICLO_MS;
    var paleta = paletaActual(ahora);
    for (var i = 0; i < LUCES.length; i++) {
      var l = LUCES[i];
      var c = colorRotado(paleta, i, progreso);
      var x = W * oscilar(l.x[0], l.x[1], l.fx, progreso);
      var y = H * oscilar(l.y[0], l.y[1], l.fy, progreso);
      var r = W * oscilar(l.r[0], l.r[1], l.fr, progreso) * escalaRadio;
      var rgb = Math.round(c[0]) + "," + Math.round(c[1]) + "," + Math.round(c[2]);
      var g = ctx.createRadialGradient(x, y, 0, x, y, r);
      g.addColorStop(0, "rgba(" + rgb + "," + Math.min(1, l.a[0] * escalaAlfa * atenuar) + ")");
      g.addColorStop(0.5, "rgba(" + rgb + "," + Math.min(1, l.a[1] * escalaAlfa * atenuar) + ")");
      g.addColorStop(1, "rgba(" + rgb + ",0)");
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, W, H);
    }
  }

  // ── Bucle ─────────────────────────────────────────────────────────────────
  var ultimaBarra = 0;
  function bucle(ahora) {
    medirFotograma(ahora);
    dibujarFondo(ahora);
    if (cancion) {
      var ms = posicion();
      actualizarLetra(ms);
      // La barra avanza 4 veces por segundo (su transicion CSS la suaviza).
      if (cancion.duracionMs > 0 && ahora - ultimaBarra > 250) {
        ultimaBarra = ahora;
        elBarra.style.width = Math.min(100, 100 * ms / cancion.duracionMs) + "%";
      }
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
      case "tempo":
        // Llega despues, cuando el telefono termino de analizar la cancion.
        if (cancion && d.id === cancion.id && d.bpm > 40) { cancion.bpm = d.bpm; cancion.primerBeatMs = d.primerBeatMs || 0; }
        break;
      case "ajustes":
        if (typeof d.manchas === "boolean") ajustes.manchas = d.manchas;
        if (typeof d.latido === "boolean") ajustes.latido = d.latido;
        if (typeof d.canvas === "boolean") { ajustes.canvas = d.canvas; ponerCanvas(cancion ? cancion.canvas : null); }
        break;
      case "saludo": mostrarSaludo(d.cabecera, d.frase); diag("saludo mostrado"); break;
      case "vaciar":
        cancion = null; ponerLetra([], null); mostrarBienvenida(); break;
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
    pm.addEventListener(T.ERROR, function (e) {
      var d = e && e.detailedErrorCode;
      diag("error del reproductor " + (d !== undefined ? d : "") + " " + String(e && e.error && (e.error.reason || e.error.type) || "").slice(0, 80));
    });

    context.addEventListener(cast.framework.system.EventType.READY, function () {
      estado("Cast listo · esperando la cancion");
      estadoSaludo("Esperando al telefono…", false);
      mandarDiag = function (texto) { context.sendCustomMessage(NS, undefined, { tipo: "diag", texto: texto }); };
      diag("receptor " + VERSION + " listo");
      // Aviso formal de arranque: el telefono contesta con el saludo (lo que mande antes se pierde).
      context.sendCustomMessage(NS, undefined, { tipo: "listo", version: VERSION });
      for (var i = 0; i < diagPendiente.length; i++) mandarDiag(diagPendiente[i]);
      diagPendiente = [];
    });
    context.addEventListener(cast.framework.system.EventType.ERROR, function (e) {
      var d = e && e.data;
      var texto = d && (d.reason || d.error || d.message || d.type);
      if (!texto) { try { texto = JSON.stringify(d || e); } catch (x) { texto = String(d || e); } }
      diag("Error de Cast: " + String(texto).slice(0, 200));
    });
    context.addEventListener(cast.framework.system.EventType.SENDER_CONNECTED, function () {
      estado("Telefono conectado");
      estadoSaludo("Telefono conectado · elige una cancion", true);
    });
    context.addEventListener(cast.framework.system.EventType.SENDER_DISCONNECTED, function () {
      estadoSaludo("Esperando al telefono…", false);
    });
    var opciones = new cast.framework.CastReceiverOptions();
    opciones.disableIdleTimeout = false;
    opciones.customNamespaces = {};
    opciones.customNamespaces[NS] = cast.framework.system.MessageType.JSON;
    context.start(opciones);
    estado("Cast iniciado · esperando al TV");
  }

  // ── Demo en el navegador (?demo=1) ────────────────────────────────────────
  // ?demo=1&canvas=URL prueba un canvas real; ?demo=1&cambio=1 cambia de cancion
  // cada 12 s para ver las transiciones.
  function iniciarDemo() {
    var t0 = Date.now();
    var largo = 214000;
    sonando = true;
    posicion = function () { return (Date.now() - t0) % largo; };
    var params = {};
    location.search.slice(1).split("&").forEach(function (p) { var kv = p.split("="); if (kv[0]) params[kv[0]] = decodeURIComponent(kv[1] || ""); });
    estadoSaludo("Demo: la cancion llega en 3 s", true);
    if (params.saludo) setTimeout(function () { mostrarSaludo("Buenas noches", "Baja el volumen, sube el sentimiento."); }, 600);

    // Caratulas de mentira: un degradado con las iniciales.
    function caratulaFalsa(c1, c2, c3, letras) {
      var c = document.createElement("canvas"); c.width = c.height = 600;
      var g = c.getContext("2d");
      var grad = g.createLinearGradient(0, 0, 600, 600);
      grad.addColorStop(0, c1); grad.addColorStop(0.5, c2); grad.addColorStop(1, c3);
      g.fillStyle = grad; g.fillRect(0, 0, 600, 600);
      g.fillStyle = "rgba(0,0,0,0.25)"; g.beginPath(); g.arc(300, 300, 190, 0, 6.2832); g.fill();
      g.fillStyle = "#fff"; g.font = "700 150px sans-serif"; g.textAlign = "center"; g.textBaseline = "middle";
      g.fillText(letras, 300, 315);
      return c.toDataURL();
    }
    var canciones = [
      { id: "demo1", titulo: "Noche de prueba", artista: "Life Music · Demo", caratula: caratulaFalsa("#F59E0B", "#EF4444", "#7C3AED", "LM"),
        colores: ["#F59E0B", "#EF4444", "#7C3AED", "#FBBF24", "#DC2626", "#4C1D95"], bpm: 96, primerBeatMs: 400, duracionMs: largo, canvas: params.canvas || null },
      { id: "demo2", titulo: "Segunda cancion, titulo bastante largo", artista: "Otro Artista · Demo", caratula: caratulaFalsa("#0EA5E9", "#22C55E", "#0F172A", "CG"),
        colores: ["#0EA5E9", "#22C55E", "#0F172A", "#38BDF8", "#16A34A", "#1E3A8A"], bpm: 128, primerBeatMs: 0, duracionMs: largo, canvas: null },
    ];
    // Letra de relleno, no es de ninguna cancion.
    function letraFalsa(texto) {
      var ls = [];
      for (var i = 0; i < texto.length; i++) {
        var ws = texto[i].split(" "), palabras = [];
        var inicio = 2000 + i * 5200;
        for (var j = 0; j < ws.length; j++) palabras.push({ t: inicio + j * Math.round(4200 / ws.length), w: ws[j] });
        ls.push({ t: inicio, texto: texto[i], palabras: palabras });
      }
      return ls;
    }
    var letras = [
      letraFalsa(["Se apaga la sala y sube la luz", "las luces de color siguen el compas", "la caratula grande, la letra al frente",
        "y el telefono en la mano, de mando", "esto es Life Music en tu televisor", "sin cables, sin cuentas, sin anuncios",
        "una cancion detras de otra, sola", "hasta que decidas parar"]),
      letraFalsa(["Cambio de cancion y cambia todo", "la caratula se funde con la nueva", "los colores del fondo se van despacio",
        "y la letra vuelve a empezar", "asi se ve en el televisor", "cuando pasas de una a otra"]),
    ];
    var cual = 0;
    function poner(i) {
      t0 = Date.now();
      ponerCancion(canciones[i]);
      ponerLetra(letras[i], canciones[i].id);
    }
    setTimeout(function () {
      poner(0);
      if (params.cambio) setInterval(function () { cual = (cual + 1) % canciones.length; poner(cual); }, 12000);
    }, 3000);
  }

  var demo = /[?&]demo=1/.test(location.search);
  if (demo) {
    iniciarDemo();
  } else {
    // En el TV nunca se cae a la demo: si Cast no arranca, se ve el error escrito.
    try { iniciarCast(); } catch (e) { estado("Error al iniciar Cast: " + (e && e.message ? e.message : e)); }
  }
})();
