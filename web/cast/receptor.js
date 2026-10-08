/* Receptor de Life Music: el modo ambiente en el TV.
   Ver index.html. Sin modulos ni sintaxis reciente a proposito: los Chromecast
   viejos traen un Chrome antiguo. */
(function () {
  "use strict";

  var VERSION = "2026-10-08a";
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
    document.body.classList.add("ligero");
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
    if (cambio) { elBarra.style.width = "0%"; elEscBarra.style.width = "0%"; }
    elEscTitulo.textContent = c.titulo || "";
    elEscArtista.textContent = c.artista || "";
    elGalTitulo.textContent = c.titulo || "";
    elGalArtista.textContent = c.artista || "";
    elAtdTitulo.textContent = c.titulo || "";
    elTdTitulo.textContent = c.titulo || "";
    elTdArtista.textContent = c.artista || "";
    elAtdArtista.textContent = c.artista || "";
    elNocCancion.textContent = (c.titulo || "") + (c.artista ? " · " + c.artista : "");
    if (cambio) { escIdx = -2; lineaSolaIdx = -2; tdIdx = -2; }
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
    document.body.classList.add("con-cancion");
  }
  function mostrarBienvenida() {
    escena.classList.add("vacia");
    escena.classList.remove("con-cancion");
    document.body.classList.remove("con-cancion");
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
    elEscCaratula.src = url;
    elEtiqueta.src = url;
    elGalCaratula.src = url;
    elAtdCaratula.src = url;
    caratulaTocadiscos(url);
    ponerGaleriaFondo(url);
    cargarArteGL(url);
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
    escIdx = -2;
    lineaSolaIdx = -2;
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
    ponerAcento(nueva);
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
    if (tema === "galeria" || tema === "nocturno" || tema === "atardecer" || tema === "tocadiscos") return false;
    // En pausa o en la bienvenida no hay latido que seguir: menos fotogramas.
    var fps = cancion ? (sonando ? FPS_FONDO : 12) : Math.min(FPS_FONDO, 15);
    if (ahora - ultimoFotograma < 1000 / fps) return false;
    var dt = Math.min(100, ahora - ultimoFotograma);
    ultimoFotograma = ahora;
    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = "#050505";
    ctx.fillRect(0, 0, W, H);
    if (!ajustes.manchas) return true;

    // Suavizado corto (70 ms el golpe, 160 ms la energia) para que el latido se
    // vea como latido y no como parpadeo.
    var p = pulso(posicion());
    latido += (p - latido) * Math.min(1, dt / 70);
    var energiaObjetivo = sonando ? 0.35 + 0.4 * p : 0;
    viveza += (energiaObjetivo - viveza) * Math.min(1, dt / 160);
    var fuerza = tema === "escenario" ? 1.9 : 1;
    var escalaRadio = 1 + latido * 0.22 * fuerza;
    var escalaAlfa = 1 + viveza * 0.3 * fuerza;
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
    return true;
  }

  // ── Temas ─────────────────────────────────────────────────────────────────
  // «ambiente» (el de siempre), «cristal» (paneles de cristal liquido, la
  // receta de la app) y «escenario» (una linea enorme al centro, para fiestas).
  // Llega del telefono en «ajustes»; la escena se funde a negro y vuelve.
  var TEMAS = ["ambiente", "cristal", "escenario", "vinilo", "galeria", "nocturno", "atardecer", "tocadiscos"];
  var tema = "ambiente";
  var cambioTema = null;
  function ponerTema(nuevo) {
    if (TEMAS.indexOf(nuevo) < 0) nuevo = "ambiente";
    if (nuevo === tema) return;
    var aplicar = function () {
      document.body.classList.remove("tema-" + tema);
      tema = nuevo;
      document.body.classList.add("tema-" + tema);
      if (tema === "cristal") prepararGL();
      prepararPaisaje();
      prepararTocadiscos();
      tdIdx = -2;
      escIdx = -2;
      lineaSolaIdx = -2;
      ultimoReloj = 0;
      ultimoCorrimiento = performance.now();
      ultimoGolpe = -1;
      if (elDestello) elDestello.style.opacity = "0";
      medirVidrios();
      diag("tema: " + tema);
    };
    clearTimeout(cambioTema);
    if (!cancion || escena.classList.contains("vacia")) { aplicar(); return; }
    escena.classList.add("cambiando-tema");
    cambioTema = setTimeout(function () {
      aplicar();
      // Un fotograma para que el diseno nuevo se asiente antes de volver a verse.
      requestAnimationFrame(function () { escena.classList.remove("cambiando-tema"); });
    }, 400);
  }

  // ── Cristal liquido ───────────────────────────────────────────────────────
  // La configuracion de cristal del usuario en la app (la manda el telefono):
  // mismo tinte, opacidad, viveza, lente, aberracion y profundidad. El fondo
  // que se ve a traves del panel lo pinta aqui cada panel en su <canvas.vidrio>,
  // leyendo el lienzo del fondo: el centro con un pelo de aumento (lente) y
  // las orillas trayendo lo que hay mas alla del borde (la refraccion del
  // canto), todo mas vivo que alrededor y con el tinte encima. El lienzo del
  // panel es pequeno y se reescala: eso es el desenfoque, gratis.
  var cristal = { opacidad: 0.4, tinte: null, vibrancia: 1, lente: 0.5, altura: 0.5, aberracion: true, profundidad: true, desenfoque: 8 };
  var tinteVidrio = "rgba(12,14,16,0.22)";
  function ponerCristal(c) {
    if (!c) return;
    for (var k in cristal) if (c[k] !== undefined && c[k] !== null) cristal[k] = c[k];
    if ("tinte" in c) cristal.tinte = c.tinte || null;
    var alfa = Math.max(0, Math.min(1, +cristal.opacidad || 0)) * 0.55;
    var rgb = cristal.tinte ? aRgb(cristal.tinte) : [12, 14, 16];
    tinteVidrio = "rgba(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + "," + alfa.toFixed(3) + ")";
    document.body.classList.toggle("aberracion", !!cristal.aberracion);
    document.body.classList.toggle("sin-profundidad", !cristal.profundidad);
    medirVidrios();
  }
  document.body.classList.add("aberracion");

  var vidrios = [];
  (function () {
    var cs = document.querySelectorAll(".vidrio");
    for (var i = 0; i < cs.length; i++) vidrios.push({ c: cs[i], g: cs[i].getContext("2d"), panel: cs[i].parentNode, r: null });
  })();
  var vidriosMedidosEn = 0;
  function medirVidrios() {
    vidriosMedidosEn = performance.now();
    if (tema !== "cristal") return;
    var escala = 5 + Math.max(0, Math.min(24, +cristal.desenfoque || 0)) / 2;
    for (var i = 0; i < vidrios.length; i++) {
      var v = vidrios[i];
      var r = v.panel.getBoundingClientRect();
      v.r = r;
      var w = Math.max(8, Math.round(r.width / escala)), h = Math.max(8, Math.round(r.height / escala));
      if (v.c.width !== w) v.c.width = w;
      if (v.c.height !== h) v.c.height = h;
    }
  }
  window.addEventListener("resize", medirVidrios);

  // Toma del fondo el rectangulo centrado en (cx, cy) de sw×sh por «zoom» y lo
  // pinta en el panel entero; recorta a lo que existe del fondo.
  function tomar(g, cx, cy, sw, sh, zoom, dw, dh) {
    var w = sw * zoom, h = sh * zoom, x = cx - w / 2, y = cy - h / 2;
    var x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(W, x + w), y1 = Math.min(H, y + h);
    if (x1 - x0 < 1 || y1 - y0 < 1) return;
    g.drawImage(lienzo, x0, y0, x1 - x0, y1 - y0, (x0 - x) / w * dw, (y0 - y) / h * dh, (x1 - x0) / w * dw, (y1 - y0) / h * dh);
  }
  function rectRedondo(g, x, y, w, h, rad) {
    rad = Math.max(0, Math.min(rad, w / 2, h / 2));
    g.moveTo(x + rad, y);
    g.arcTo(x + w, y, x + w, y + h, rad);
    g.arcTo(x + w, y + h, x, y + h, rad);
    g.arcTo(x, y + h, x, y, rad);
    g.arcTo(x, y, x + w, y, rad);
    g.closePath();
  }
  // Recorta a la franja entre el borde metido «de» y el metido «a» (a > de).
  function franja(g, cw, ch, rad, de, a) {
    g.beginPath();
    rectRedondo(g, de, de, cw - 2 * de, ch - 2 * de, rad - de);
    rectRedondo(g, a, a, cw - 2 * a, ch - 2 * a, Math.max(0, rad - a));
    g.clip("evenodd");
  }
  function dibujarVidrios(ahora) {
    if (tema !== "cristal" || !cancion) return;
    if (ahora - vidriosMedidosEn > 700) medirVidrios();
    var iw = window.innerWidth, ih = window.innerHeight;
    var sat = 1 + 0.5 * Math.max(0, Math.min(2, +cristal.vibrancia || 0));
    var lente = Math.max(0, Math.min(1, +cristal.lente || 0));
    var filtro = "saturate(" + sat.toFixed(2) + ") brightness(1.06)";
    for (var i = 0; i < vidrios.length; i++) {
      var v = vidrios[i], r = v.r;
      if (!r || r.width < 2 || v.c.offsetParent === null) continue; // panel oculto (cancion sin letra)
      var g = v.g, cw = v.c.width, ch = v.c.height;
      var sx = r.left / iw * W, sy = r.top / ih * H, sw = r.width / iw * W, sh = r.height / ih * H;
      var cx = sx + sw / 2, cy = sy + sh / 2;
      var rad = (4.2 * Math.min(iw, ih) / 100) * cw / r.width;
      g.clearRect(0, 0, cw, ch);
      g.save();
      if ("filter" in g) g.filter = filtro;
      tomar(g, cx, cy, sw, sh, 1 - 0.10 * lente, cw, ch);
      g.restore();
      if (!ligero && lente > 0) {
        var b = Math.min(cw, ch) * (0.07 + 0.08 * lente);
        g.save(); if ("filter" in g) g.filter = filtro; franja(g, cw, ch, rad, b, 2 * b); tomar(g, cx, cy, sw, sh, 1 + 0.16 * lente, cw, ch); g.restore();
        g.save(); if ("filter" in g) g.filter = filtro; franja(g, cw, ch, rad, 0, b); tomar(g, cx, cy, sw, sh, 1 + 0.38 * lente, cw, ch); g.restore();
      }
      g.fillStyle = tinteVidrio;
      g.fillRect(0, 0, cw, ch);
      // Reflejo difuso de la superficie: la luz entra por arriba a la izquierda.
      var brillo = g.createLinearGradient(0, 0, cw * 0.6, ch * 0.6);
      brillo.addColorStop(0, "rgba(255,255,255,0.13)");
      brillo.addColorStop(0.45, "rgba(255,255,255,0.03)");
      brillo.addColorStop(1, "rgba(255,255,255,0)");
      g.fillStyle = brillo;
      g.fillRect(0, 0, cw, ch);
    }
  }

  // ── Escenario ─────────────────────────────────────────────────────────────
  // La linea viva, enorme, sube y se va; la nueva entra desde abajo. Antes de
  // la primera linea (y en canciones sin letra) el titulo ocupa su lugar.
  var elEscLetra = $("esc-letra"), elEscSig = $("esc-siguiente"), elEscCaratula = $("esc-caratula");
  var elEscTitulo = $("esc-titulo"), elEscArtista = $("esc-artista"), elEscBarra = $("esc-barra"), elDestello = $("destello");
  var escIdx = -2, escPalabra = -1, escViva = null;
  function lineaEscenario(texto, palabras, clase) {
    var hijos = elEscLetra.children;
    for (var i = 0; i < hijos.length; i++) {
      var viejo = hijos[i];
      if (viejo.classList.contains("sale")) continue;
      viejo.classList.add("sale");
      (function (e) { setTimeout(function () { if (e.parentNode) e.parentNode.removeChild(e); }, 650); })(viejo);
    }
    var div = document.createElement("div");
    div.className = "esc-linea entra" + (clase ? " " + clase : "");
    if (palabras && palabras.length) {
      for (var j = 0; j < palabras.length; j++) {
        var s = document.createElement("span");
        s.className = "p";
        s.textContent = palabras[j].w + (j < palabras.length - 1 ? " " : "");
        div.appendChild(s);
      }
    } else {
      div.textContent = texto;
      if (!clase) div.classList.add("sin-palabras");
    }
    elEscLetra.appendChild(div);
    void div.offsetWidth;
    div.classList.remove("entra");
    return div;
  }
  function actualizarEscenario(ms) {
    if (!lineas.length) {
      if (escIdx !== -3) { escIdx = -3; escViva = lineaEscenario(cancion.titulo || "", null, "titulo"); elEscSig.textContent = cancion.artista || ""; }
      return;
    }
    var idx = -1;
    for (var i = 0; i < lineas.length; i++) { if (lineas[i].t <= ms) idx = i; else break; }
    if (idx !== escIdx) {
      escIdx = idx;
      escPalabra = -1;
      if (idx < 0) {
        escViva = lineaEscenario(cancion.titulo || "", null, "titulo");
      } else {
        var t = (lineas[idx].texto || "").trim();
        escViva = lineaEscenario(t || "♪", t ? lineas[idx].palabras : null, t ? null : "titulo");
      }
      var sig = lineas[idx + 1];
      elEscSig.textContent = sig ? sig.texto : "";
    }
    if (idx >= 0 && escViva && lineas[idx].palabras && lineas[idx].palabras.length) {
      var ps = lineas[idx].palabras, pv = -1;
      for (var m = 0; m < ps.length; m++) { if (ps[m].t <= ms) pv = m; else break; }
      if (pv !== escPalabra) {
        var spans = escViva.children;
        for (var n = 0; n < spans.length; n++) spans[n].className = "p" + (n <= pv ? " dicha" : "");
        escPalabra = pv;
      }
    }
  }
  // El golpe del escenario: la linea late un poco y la sala se ilumina en cada beat.
  var ultimoGolpe = -1;
  function golpeEscenario() {
    var v = (sonando && cancion && cancion.bpm > 40) ? Math.round(latido * 100) / 100 : 0;
    if (v === ultimoGolpe) return;
    ultimoGolpe = v;
    elEscLetra.style.transform = "scale(" + (1 + v * 0.035).toFixed(4) + ")";
    elDestello.style.opacity = (v * 0.16).toFixed(3);
  }
  // El color de acento del escenario (el brillo de la palabra dicha): el mas vivo de la paleta.
  function ponerAcento(paleta) {
    var mejor = paleta[0], puntos = -1;
    for (var i = 0; i < paleta.length; i++) {
      var c = paleta[i], mx = Math.max(c[0], c[1], c[2]), mn = Math.min(c[0], c[1], c[2]);
      var p = mx === 0 ? 0 : ((mx - mn) / mx) * (mx / 255);
      if (p > puntos) { puntos = p; mejor = c; }
    }
    if (mejor) document.body.style.setProperty("--acento", Math.round(mejor[0]) + ", " + Math.round(mejor[1]) + ", " + Math.round(mejor[2]));
  }

  // ── Cristal en la GPU ─────────────────────────────────────────────────────
  // El tema Cristal con refraccion de verdad (cristal.js, WebGL): se descarga
  // la primera vez que se elige el tema. Si el TV no tiene WebGL, o algo falla,
  // queda el vidrio dibujado en 2D (dibujarVidrios), que siempre funciona.
  var cristalGL = null, glIntentado = false, lienzoGL = $("fondo-cristal");
  var arteGLUrl = null;
  function prepararGL() {
    if (glIntentado) return;
    glIntentado = true;
    var s = document.createElement("script");
    s.src = "cristal.js?v=" + VERSION;
    s.onload = function () {
      try { cristalGL = window.crearCristalGL ? window.crearCristalGL(lienzoGL) : null; }
      catch (e) { cristalGL = null; diag("cristal GL: " + String(e && e.message || e).slice(0, 160)); }
      if (!cristalGL) { diag("cristal GL: sin WebGL, queda el vidrio 2D"); return; }
      document.body.classList.add("gl");
      diag("cristal GL listo");
      cargarArteGL(caratulaActual);
    };
    s.onerror = function () { diag("cristal GL: no cargo cristal.js"); };
    document.head.appendChild(s);
  }
  // La caratula para la GPU: otra <img> pedida con CORS (la que se ve no lo pide).
  function cargarArteGL(url) {
    if (!cristalGL || !url || url === arteGLUrl) return;
    arteGLUrl = url;
    var img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = function () {
      if (arteGLUrl !== url) return;
      try { cristalGL.ponerArte(img); } catch (e) { diag("cristal GL: la caratula no admite CORS"); cristalGL.ponerArte(null); }
    };
    img.onerror = function () { if (arteGLUrl === url) cristalGL.ponerArte(null); };
    img.src = url;
  }
  function dibujarCristalGL(ahora) {
    if (!cristalGL || tema !== "cristal" || !cancion) return;
    if (ahora - vidriosMedidosEn > 700) medirVidrios();
    var iw = window.innerWidth, ih = window.innerHeight;
    var ancho = Math.round(Math.min(iw * (window.devicePixelRatio || 1), ligero ? 640 : 1280));
    var k = ancho / iw, alto = Math.round(ih * k);
    var paneles = [];
    for (var i = 0; i < vidrios.length; i++) {
      var r = vidrios[i].r;
      if (!r || r.width < 2 || vidrios[i].panel.offsetParent === null) continue;
      paneles.push([r.left * k, r.top * k, r.width * k, r.height * k]);
    }
    var vmin = Math.min(iw, ih) / 100 * k;
    // La caratula cubre la pantalla y se pasea despacio (y respira con el golpe).
    var t = ahora / 1000, aspecto = iw / ih;
    var zoom = 0.84 + 0.05 * Math.sin(t * 0.045) - 0.015 * latido;
    var cx = 0.5 + 0.05 * Math.sin(t * 0.031), cy = 0.5 + 0.05 * Math.cos(t * 0.027);
    var ex = zoom, ey = zoom / aspecto;
    var rgb = cristal.tinte ? aRgb(cristal.tinte) : [14, 16, 18];
    cristalGL.subirLuces(lienzo);
    cristalGL.dibujar({
      ancho: ancho, alto: alto, paneles: paneles,
      radio: 4.2 * vmin,
      lente: (2 + 14 * Math.max(0, Math.min(1, +cristal.altura || 0))) * vmin,
      refr: (1 + 12 * Math.max(0, Math.min(1, +cristal.lente || 0))) * vmin,
      aberracion: !!cristal.aberracion && !ligero,
      desenfoque: 0.3 + Math.max(0, Math.min(24, +cristal.desenfoque || 0)) / 12,
      saturacion: 1 + 0.5 * Math.max(0, Math.min(2, +cristal.vibrancia || 0)),
      tinte: [rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, Math.max(0, Math.min(1, +cristal.opacidad || 0)) * 0.7],
      sombra: !!cristal.profundidad,
      latido: latido,
      arte: [ex, ey, cx - ex / 2, cy - ey / 2],
    });
  }

  // ── Vinilo ────────────────────────────────────────────────────────────────
  // 33⅓ rpm son 200 grados por segundo. Arranca en ~0,5 s y se frena en ~0,7 s,
  // como un plato: no se congela de golpe al pausar.
  var elDisco = $("disco"), elEtiqueta = $("etiqueta");
  var anguloDisco = 0, velDisco = 0, ultimoGiro = 0;
  function girarDisco(ahora) {
    var dt = ultimoGiro ? Math.min(100, ahora - ultimoGiro) : 16;
    ultimoGiro = ahora;
    var objetivo = sonando ? 1 : 0;
    velDisco += (objetivo - velDisco) * Math.min(1, dt / (objetivo ? 450 : 700));
    if (!objetivo && velDisco < 0.002) return;
    anguloDisco = (anguloDisco + velDisco * 0.2 * dt) % 360;
    elDisco.style.transform = "rotate(" + anguloDisco.toFixed(2) + "deg)";
  }

  // ── Galeria ───────────────────────────────────────────────────────────────
  // El fondo es la caratula reducida a 64×36 (un rectangulo de pantalla) y
  // ampliada con un desenfoque CSS: se pinta una vez por cancion, no por
  // fotograma. Dos capas para fundir la vieja con la nueva.
  var galCapas = document.querySelectorAll("#galeria-fondo .gf"), galActual = 0, galUrl = null;
  for (var gi = 0; gi < galCapas.length; gi++) { galCapas[gi].width = 64; galCapas[gi].height = 36; }
  var elGalCaratula = $("gal-caratula"), elGalTitulo = $("gal-titulo"), elGalArtista = $("gal-artista"), elGalLinea = $("gal-linea");
  function ponerGaleriaFondo(url) {
    if (!url || url === galUrl) return;
    galUrl = url;
    var img = new Image();
    img.onload = function () {
      if (galUrl !== url) return;
      var sig = 1 - galActual, cv = galCapas[sig], g = cv.getContext("2d");
      var iw = img.naturalWidth, ih = img.naturalHeight;
      var sw = iw, sh = iw * 36 / 64;
      if (sh > ih) { sh = ih; sw = ih * 64 / 36; }
      g.drawImage(img, (iw - sw) / 2, (ih - sh) / 2, sw, sh, 0, 0, 64, 36);
      galCapas[galActual].classList.remove("visible");
      cv.classList.add("visible");
      galActual = sig;
    };
    img.src = url;
  }

  // Una sola linea de letra (Galeria y Nocturno): se desvanece y vuelve con la nueva.
  var lineaSolaIdx = -2;
  function actualizarLineaSola(el, ms) {
    var idx = -1;
    for (var i = 0; i < lineas.length; i++) { if (lineas[i].t <= ms) idx = i; else break; }
    if (idx === lineaSolaIdx) return;
    lineaSolaIdx = idx;
    var texto = idx >= 0 ? (lineas[idx].texto || "").trim() : "";
    el.classList.add("cambia");
    setTimeout(function () { if (lineaSolaIdx === idx) el.textContent = texto; el.classList.remove("cambia"); }, 380);
  }

  // ── Nocturno ──────────────────────────────────────────────────────────────
  var elNocturno = $("nocturno"), elNocHora = $("noc-hora"), elNocFecha = $("noc-fecha");
  var elNocLinea = $("noc-linea"), elNocCancion = $("noc-cancion");
  var idioma = navigator.language || "es";
  var ultimoReloj = 0, ultimoCorrimiento = 0;
  function actualizarNocturno(ahora) {
    if (ahora - ultimoReloj < 1000) return;
    ultimoReloj = ahora;
    var d = new Date(), hora, fecha;
    try {
      hora = d.toLocaleTimeString(idioma, { hour: "numeric", minute: "2-digit" });
      fecha = d.toLocaleDateString(idioma, { weekday: "long", day: "numeric", month: "long" });
    } catch (e) {
      hora = d.getHours() + ":" + ("0" + d.getMinutes()).slice(-2);
      fecha = "";
    }
    // «7:45 p. m.»: los numeros grandes y el a. m./p. m. pequeno al lado.
    var m = /^(\d{1,2}[:.]\d{2})\s*(.*)$/.exec(hora);
    if (m) {
      elNocHora.textContent = m[1];
      if (m[2]) { var s = document.createElement("small"); s.textContent = m[2]; elNocHora.appendChild(s); }
    } else {
      elNocHora.textContent = hora;
    }
    elNocFecha.textContent = fecha;
    // Pantallas OLED: todo se corre un poco cada minuto para no marcar la imagen.
    if (ahora - ultimoCorrimiento > 60000) {
      ultimoCorrimiento = ahora;
      elNocturno.style.transform = "translate(" + ((Math.random() * 2 - 1) * 1.5).toFixed(2) + "vmin, " + ((Math.random() * 2 - 1) * 1.5).toFixed(2) + "vmin)";
    }
  }

  // ── Temas con paisaje real (pro) ──────────────────────────────────────────
  // El fondo es un video real en bucle (generado en Flow, en temas/), no un
  // dibujo: el realismo viene del material y lo vivo (la cancion) va encima.
  // El clip no empalma consigo mismo (el agua del final no es la del inicio),
  // asi que hay dos copias: antes de que una acabe, la otra arranca desde cero
  // y se funde por encima; la de abajo sigue a opacidad completa hasta que la
  // de arriba la tapa, para que el empalme no oscurezca la pantalla.
  // Solo se descarga si se elige el tema, y al salir se suelta el decodificador
  // (un TV flojo no puede tener dos videos abiertos sin motivo).
  var PAISAJES = {
    // Por hora (desde, hasta): la primera que encaje; si no hay ninguna, la ultima.
    // «zoom» amplia el clip lo justo para esconder defectos del borde (el
    // atardecer trae bordes de pelicula y un rayon arriba a la derecha).
    atardecer: [
      { desde: 5, hasta: 12, src: "temas/amanecer.mp4", zoom: 1.02 },
      { desde: 12, hasta: 19, src: "temas/atardecer.mp4", zoom: 1.16 },
      { desde: 19, hasta: 24, src: "temas/noche.mp4", zoom: 1.02 },
      { desde: 0, hasta: 5, src: "temas/noche.mp4", zoom: 1.02 },
    ],
  };
  var FUNDIDO_PAISAJE = 1.6; // s
  var elPaisaje = $("paisaje"), pjVideos = document.querySelectorAll("#paisaje .pj");
  var pjActual = 0, pjSrc = null, pjFundiendo = false, pjRevisado = 0, pjHoraRevisada = 0;
  var horaForzada = null; // la demo la pone con ?hora=
  var sinVideo = false;   // TV con navegador que no reproduce H.264: paisaje quieto

  function paisajeDeAhora(lista) {
    var h = horaForzada !== null ? horaForzada : new Date().getHours();
    for (var i = 0; i < lista.length; i++) if (h >= lista[i].desde && h < lista[i].hasta) return lista[i];
    return lista[lista.length - 1];
  }
  function prepararPaisaje() {
    var lista = PAISAJES[tema];
    if (!lista || sinVideo) { soltarPaisaje(); return; }
    var p = paisajeDeAhora(lista), src = p.src;
    if (src === pjSrc) return;
    soltarPaisaje();
    pjSrc = src;
    for (var i = 0; i < pjVideos.length; i++) {
      pjVideos[i].preload = "auto";
      pjVideos[i].style.transform = "scale(" + (p.zoom || 1) + ")";
      pjVideos[i].src = src;
    }
    var v = pjVideos[pjActual];
    v.style.zIndex = "1";
    v.onplaying = function () { v.classList.add("visible"); v.onplaying = null; };
    v.onerror = function () { diag("paisaje: no cargo " + src); };
    var pr = v.play();
    if (pr && pr.catch) pr.catch(function (e) { diag("paisaje: " + (e && e.name)); });
  }
  function soltarPaisaje() {
    if (!pjSrc) return;
    pjSrc = null;
    pjFundiendo = false;
    for (var i = 0; i < pjVideos.length; i++) {
      var v = pjVideos[i];
      v.pause();
      v.classList.remove("visible");
      v.removeAttribute("src");
      v.load();
    }
  }
  function cuidarPaisaje(ahora) {
    if (!pjSrc || pjFundiendo || ahora - pjRevisado < 100) return;
    // Cada minuto: si cambio la franja del dia (amanecer → atardecer), otro paisaje.
    if (ahora - pjHoraRevisada > 60000) { pjHoraRevisada = ahora; if (paisajeDeAhora(PAISAJES[tema]).src !== pjSrc) { prepararPaisaje(); return; } }
    pjRevisado = ahora;
    var v = pjVideos[pjActual];
    if (!v.duration || v.currentTime < v.duration - FUNDIDO_PAISAJE - 0.3) return; // margen: un TV lento no llega al final congelado
    // Empalme: la otra copia arranca desde cero y se funde encima de esta.
    pjFundiendo = true;
    var sig = 1 - pjActual, n = pjVideos[sig];
    n.currentTime = 0;
    n.style.zIndex = "2";
    v.style.zIndex = "1";
    var p = n.play();
    if (p && p.catch) p.catch(function () { /* sigue la vieja; se reintenta en el proximo empalme */ });
    n.classList.add("visible");
    setTimeout(function () {
      v.classList.remove("visible");
      v.pause();
      pjActual = sig;
      pjFundiendo = false;
    }, FUNDIDO_PAISAJE * 1000 + 50);
  }

  // ── Tocadiscos (pro) ──────────────────────────────────────────────────────
  // Una foto real (generada en Flow, en temas/) con la etiqueta del disco en
  // blanco. La etiqueta se midio en la foto (contorno ajustado a una elipse):
  // la caratula se dibuja como un circulo que gira y se aplana a esa elipse,
  // que es exactamente como se ve un disco girando desde arriba en diagonal.
  // Encima van, sacados de la misma foto: la luz y el papel de la etiqueta
  // (multiplicados sobre la caratula, para que la caratula tenga la luz de la
  // escena y no parezca pegada) y el eje plateado (siempre por delante).
  // Todo lo que esta en «escena» va en pixeles de la foto; se escala entero.
  var PLACAS_TD = [
    { src: "temas/tocadiscos-1.jpg", w: 2000, h: 1116,
      etiqueta: { x: 630.1, y: 540.5, a: 216.2, b: 174.9, ang: 49.3 },
      eje: { x: 618, y: 512, r: 36 },
      luz: { x: 1460, y: 330, r: 420 } },
    { src: "temas/tocadiscos-2.jpg", w: 2000, h: 1116,
      etiqueta: { x: 480.4, y: 559.8, a: 154.8, b: 90.0, ang: 0 },
      eje: { x: 483, y: 549, r: 30 },
      luz: { x: 1420, y: 300, r: 460 } },
  ];
  var RPM_TD = 33.333;
  var elTd = $("tocadiscos"), elTdMarco = $("td-marco"), elTdEscena = $("td-escena"), elTdFoto = $("td-foto");
  var elTdEtiqueta = $("td-etiqueta"), elTdCaratula = $("td-caratula"), elTdSombreado = $("td-sombreado"), elTdEje = $("td-eje");
  var elTdLuz = $("td-luz"), elTdPolvo = $("td-polvo");
  var elTdLinea = $("td-linea"), elTdSiguiente = $("td-siguiente"), elTdTitulo = $("td-titulo"), elTdArtista = $("td-artista"), elTdBarra = $("td-barra");
  var tdPlaca = null, tdListo = false, tdAngulo = 0, tdVel = 0, tdUltimo = 0, tdLatido = 0;
  var tdIdx = -2, tdPalabras = [], tdUltimaLetra = 0, tdCaratulaUrl = null;
  var placaForzada = null; // la demo la pone con ?placa=

  function prepararTocadiscos() {
    if (tema !== "tocadiscos") { soltarTocadiscos(); return; }
    var p = PLACAS_TD[(placaForzada || 1) - 1] || PLACAS_TD[0];
    if (tdPlaca === p) return;
    tdPlaca = p; tdListo = false;
    elTd.classList.remove("listo");
    elTdFoto.onload = function () {
      if (tdPlaca !== p) return;
      colocarTocadiscos();
      try { pintarSombreado(p); pintarEje(p); } catch (e) { diag("tocadiscos: " + e.message); }
      tdListo = true;
      elTd.classList.add("listo");
    };
    elTdFoto.onerror = function () { diag("tocadiscos: no cargo " + p.src); };
    elTdFoto.src = p.src;
    tdIdx = -2;
  }
  function soltarTocadiscos() {
    if (!tdPlaca) return;
    tdPlaca = null; tdListo = false;
    elTd.classList.remove("listo");
    elTdFoto.removeAttribute("src");
  }

  // La escena mide lo que la foto; el marco la escala para cubrir la pantalla.
  function colocarTocadiscos() {
    var p = tdPlaca; if (!p) return;
    elTdEscena.style.width = p.w + "px"; elTdEscena.style.height = p.h + "px";
    elTdMarco.style.width = p.w + "px"; elTdMarco.style.height = p.h + "px";
    elTdMarco.style.marginLeft = (-p.w / 2) + "px"; elTdMarco.style.marginTop = (-p.h / 2) + "px";
    var k = Math.max(innerWidth / p.w, innerHeight / p.h);
    elTdMarco.style.transform = "scale(" + k + ")";
    var e = p.etiqueta, R = 100;
    elTdEtiqueta.style.left = (e.x - R) + "px"; elTdEtiqueta.style.top = (e.y - R) + "px";
    elTdEtiqueta.style.transform = "rotate(" + e.ang + "deg) scale(" + (e.a / R) + "," + (e.b / R) + ")";
    elTdEscena.style.transformOrigin = e.x + "px " + e.y + "px";
    var l = p.luz;
    elTdLuz.style.left = (l.x - l.r) + "px"; elTdLuz.style.top = (l.y - l.r) + "px";
    elTdLuz.style.width = elTdLuz.style.height = (2 * l.r) + "px";
    elTdPolvo.width = Math.round(innerWidth / 2); elTdPolvo.height = Math.round(innerHeight / 2);
  }
  window.addEventListener("resize", function () { if (tdPlaca) colocarTocadiscos(); });

  // Punto (x, y) de la foto → coordenada normalizada en el circulo de la etiqueta (1 = el borde).
  function enEtiqueta(e, x, y) {
    var c = Math.cos(-e.ang * Math.PI / 180), s = Math.sin(-e.ang * Math.PI / 180);
    var dx = x - e.x, dy = y - e.y;
    var u = (dx * c - dy * s) / e.a, v = (dx * s + dy * c) / e.b;
    return Math.sqrt(u * u + v * v);
  }

  // El papel de la etiqueta tal como lo ilumina la foto, normalizado para que
  // el papel quede casi blanco: multiplicado sobre la caratula le pone la
  // misma luz, sombras y textura, y el tono calido de la sala.
  function pintarSombreado(p) {
    var e = p.etiqueta, m = Math.ceil(Math.max(e.a, e.b)) + 2;
    var x0 = Math.floor(e.x - m), y0 = Math.floor(e.y - m), lado = 2 * m;
    var cv = elTdSombreado; cv.width = lado; cv.height = lado;
    cv.style.left = x0 + "px"; cv.style.top = y0 + "px";
    var g = cv.getContext("2d");
    g.drawImage(elTdFoto, x0, y0, lado, lado, 0, 0, lado, lado);
    var img = g.getImageData(0, 0, lado, lado), d = img.data;
    // Referencia: el papel mas claro (percentil 92 de la luminancia dentro de la etiqueta).
    var lums = [];
    for (var j = 0; j < lado; j += 3) for (var i = 0; i < lado; i += 3) {
      if (enEtiqueta(e, x0 + i, y0 + j) < 0.9) { var q = (j * lado + i) * 4; lums.push((d[q] + d[q + 1] + d[q + 2]) / 3); }
    }
    lums.sort(function (a, b) { return a - b; });
    var ref = lums[Math.floor(lums.length * 0.92)] || 220;
    for (var y = 0; y < lado; y++) for (var x = 0; x < lado; x++) {
      var o = (y * lado + x) * 4, rr = enEtiqueta(e, x0 + x, y0 + y);
      if (rr > 1.01) { d[o + 3] = 0; continue; }
      var lum = (d[o] + d[o + 1] + d[o + 2]) / 3;
      for (var c = 0; c < 3; c++) {
        // 60 % del color del papel, 40 % gris: conserva lo calido sin teñir de crema la caratula.
        var val = (0.6 * d[o + c] + 0.4 * lum) * 255 / ref;
        d[o + c] = val > 255 ? 255 : val;
      }
      // Borde suave de 1,5 px para que no se vea un corte.
      d[o + 3] = rr < 0.99 ? 255 : Math.max(0, 255 * (1.01 - rr) / 0.02);
    }
    g.putImageData(img, 0, 0);
  }

  // El eje: solo los pixeles que no son papel (el metal es gris; el papel, crema).
  function pintarEje(p) {
    var j = p.eje, lado = 2 * j.r, x0 = j.x - j.r, y0 = j.y - j.r;
    var cv = elTdEje; cv.width = lado; cv.height = lado;
    cv.style.left = x0 + "px"; cv.style.top = y0 + "px";
    var g = cv.getContext("2d");
    g.drawImage(elTdFoto, x0, y0, lado, lado, 0, 0, lado, lado);
    var img = g.getImageData(0, 0, lado, lado), d = img.data, alfa = new Float32Array(lado * lado);
    for (var y = 0; y < lado; y++) for (var x = 0; x < lado; x++) {
      var o = (y * lado + x) * 4, r = d[o], gg = d[o + 1], b = d[o + 2];
      var mx = Math.max(r, gg, b), mn = Math.min(r, gg, b), sat = mx ? (mx - mn) / mx : 0;
      var dist = Math.sqrt((x - j.r) * (x - j.r) + (y - j.r) * (y - j.r)) / j.r;
      // Metal: poco color. El papel crema tiene saturacion ~0,2; la sombra del eje, oscura y calida, la pone el sombreado.
      var a = sat < 0.14 ? 1 : sat < 0.2 ? (0.2 - sat) / 0.06 : 0;
      alfa[y * lado + x] = dist > 1 ? 0 : a;
    }
    // Un desenfoque de 3×3 a la mascara para que el borde del metal no se vea recortado.
    for (var y2 = 0; y2 < lado; y2++) for (var x2 = 0; x2 < lado; x2++) {
      var s = 0, n = 0;
      for (var dy = -1; dy <= 1; dy++) for (var dx = -1; dx <= 1; dx++) {
        var xx = x2 + dx, yy = y2 + dy;
        if (xx >= 0 && yy >= 0 && xx < lado && yy < lado) { s += alfa[yy * lado + xx]; n++; }
      }
      d[(y2 * lado + x2) * 4 + 3] = 255 * s / n;
    }
    g.putImageData(img, 0, 0);
  }

  // Polvo en el aire: motas calidas que flotan despacio y titilan con la luz.
  var tdMotas = [];
  for (var mi = 0; mi < 46; mi++) {
    tdMotas.push({ x: Math.random(), y: Math.random(), vx: (Math.random() - 0.5) * 0.004, vy: -0.002 - Math.random() * 0.006,
      t: Math.random() * 6.28, f: 0.4 + Math.random() * 1.2, r: 0.6 + Math.random() * 1.8 });
  }
  var tdSprite = (function () {
    var c = document.createElement("canvas"); c.width = c.height = 32;
    var g = c.getContext("2d"), gr = g.createRadialGradient(16, 16, 0, 16, 16, 16);
    gr.addColorStop(0, "rgba(255,226,180,1)"); gr.addColorStop(0.35, "rgba(255,190,120,0.45)"); gr.addColorStop(1, "rgba(255,170,90,0)");
    g.fillStyle = gr; g.fillRect(0, 0, 32, 32); return c;
  })();
  var tdUltimoPolvo = 0;
  function pintarPolvo(ahora, dt) {
    if (ahora - tdUltimoPolvo < 33) return; // 30 fps bastan para algo tan lento
    tdUltimoPolvo = ahora;
    var cv = elTdPolvo, g = cv.getContext("2d"), W2 = cv.width, H2 = cv.height;
    g.clearRect(0, 0, W2, H2);
    g.globalCompositeOperation = "lighter";
    var seg = dt / 1000;
    for (var i = 0; i < tdMotas.length; i++) {
      var m = tdMotas[i];
      m.x += m.vx * seg; m.y += m.vy * seg; m.t += m.f * seg;
      if (m.y < -0.05) { m.y = 1.05; m.x = Math.random(); }
      if (m.x < -0.05) m.x = 1.05; else if (m.x > 1.05) m.x = -0.05;
      // Mas visibles donde hay luz (arriba a la derecha de la foto), casi nada en lo oscuro.
      var luz = 0.35 + 0.65 * Math.max(0, 1 - Math.hypot(m.x - 0.62, m.y - 0.3) / 0.7);
      var a = luz * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(m.t)));
      var r = m.r * (H2 / 540) * 3;
      g.globalAlpha = a;
      g.drawImage(tdSprite, m.x * W2 - r, m.y * H2 - r, 2 * r, 2 * r);
    }
    g.globalAlpha = 1;
  }

  // Cada fotograma: el giro (33⅓, con arranque y frenada de plato real), la
  // deriva lenta de camara, la luz del fondo que respira con el tempo y el polvo.
  function animarTocadiscos(ahora) {
    if (!tdListo) return;
    var dt = tdUltimo ? Math.min(100, ahora - tdUltimo) : 16;
    tdUltimo = ahora;
    var objetivo = (cancion && sonando) ? RPM_TD * 6 : 0; // grados por segundo
    // Arranca en ~0,6 s; frena en ~1,8 s, como un plato que se apaga.
    tdVel += (objetivo - tdVel) * Math.min(1, dt / (objetivo > tdVel ? 220 : 650));
    if (tdVel < 0.05 && objetivo === 0) tdVel = 0;
    tdAngulo = (tdAngulo + tdVel * dt / 1000) % 360;
    elTdCaratula.style.transform = "rotate(" + tdAngulo.toFixed(2) + "deg)";
    // Camara: acercamiento de 2,5 % en un ciclo de 48 s, centrado en la etiqueta.
    var s = 1 + 0.0125 * (1 - Math.cos(ahora / 48000 * 6.2832));
    elTdEscena.style.transform = "scale(" + s.toFixed(5) + ")";
    var p = cancion ? pulso(posicion()) : 0;
    tdLatido += ((sonando ? p : 0) - tdLatido) * Math.min(1, dt / 120);
    elTdLuz.style.opacity = (0.22 + 0.2 * tdLatido + 0.06 * Math.sin(ahora / 3100)).toFixed(3);
    pintarPolvo(ahora, dt);
  }

  // La letra: la linea que suena en grande, palabra a palabra; la siguiente, tenue.
  function actualizarTocadiscos(ms) {
    var idx = -1;
    for (var i = 0; i < lineas.length; i++) { if (lineas[i].t <= ms) idx = i; else break; }
    if (idx !== tdIdx) {
      tdIdx = idx;
      elTdLinea.classList.add("cambia");
      elTdSiguiente.classList.add("cambia");
      setTimeout(function () {
        if (tdIdx !== idx) return;
        elTdLinea.textContent = ""; tdPalabras = [];
        var l = idx >= 0 ? lineas[idx] : null;
        if (!lineas.length) {
          // Sin letra: el titulo ocupa el sitio de la letra.
          elTdLinea.textContent = cancion ? (cancion.titulo || "") : "";
        } else if (l) {
          var ps = (l.palabras && l.palabras.length) ? l.palabras : [{ t: l.t, w: l.texto || "" }];
          for (var k = 0; k < ps.length; k++) {
            var sp = document.createElement("span");
            sp.textContent = (k ? " " : "") + ps[k].w;
            elTdLinea.appendChild(sp);
            tdPalabras.push({ t: ps[k].t, el: sp });
          }
        }
        var sig = lineas[idx + 1];
        elTdSiguiente.textContent = sig ? (sig.texto || "").trim() : "";
        elTdLinea.classList.remove("cambia");
        elTdSiguiente.classList.remove("cambia");
      }, 360);
    }
    var ahora = performance.now();
    if (ahora - tdUltimaLetra > 80) {
      tdUltimaLetra = ahora;
      for (var w = 0; w < tdPalabras.length; w++) tdPalabras[w].el.classList.toggle("dicha", tdPalabras[w].t <= ms);
      var dur = cancion.duracionMs || 0;
      elTdBarra.style.width = dur > 0 ? Math.min(100, 100 * ms / dur) + "%" : "0%";
    }
  }
  function caratulaTocadiscos(url) {
    if (!url || url === tdCaratulaUrl) return;
    tdCaratulaUrl = url;
    elTdCaratula.classList.add("cambia");
    var img = new Image();
    img.onload = function () {
      if (tdCaratulaUrl !== url) return;
      setTimeout(function () { elTdCaratula.src = url; elTdCaratula.classList.remove("cambia"); }, 300);
    };
    img.src = url;
  }

  // ── Atardecer ─────────────────────────────────────────────────────────────
  var elAtdCaratula = $("atd-caratula"), elAtdTitulo = $("atd-titulo"), elAtdArtista = $("atd-artista");
  var elAtdLinea = $("atd-linea"), elAtdBarra = $("atd-barra"), elAtdTiempo = $("atd-tiempo"), elAtdDuracion = $("atd-duracion");
  var ultimoAtd = 0;
  function mmss(ms) {
    var s = Math.max(0, Math.floor(ms / 1000));
    return Math.floor(s / 60) + ":" + ("0" + (s % 60)).slice(-2);
  }
  function actualizarAtardecer(ms) {
    var ahora = performance.now();
    if (ahora - ultimoAtd < 250) return;
    ultimoAtd = ahora;
    var dur = cancion.duracionMs || 0;
    elAtdTiempo.textContent = mmss(ms);
    elAtdDuracion.textContent = dur > 0 ? mmss(dur) : "";
    elAtdBarra.style.width = dur > 0 ? Math.min(100, 100 * ms / dur) + "%" : "0%";
  }

  // ── Bucle ────────────────────────────────────────────────────────────────
  var ultimaBarra = 0;
  function bucle(ahora) {
    medirFotograma(ahora);
    cuidarPaisaje(ahora);
    animarTocadiscos(ahora);
    if (dibujarFondo(ahora)) {
      if (cristalGL && tema === "cristal") dibujarCristalGL(ahora); else dibujarVidrios(ahora);
    }
    if (cancion) {
      var ms = posicion();
      if (tema === "ambiente" || tema === "cristal" || tema === "vinilo") actualizarLetra(ms);
      // La barra avanza 4 veces por segundo (su transicion CSS la suaviza).
      if (cancion.duracionMs > 0 && ahora - ultimaBarra > 250) {
        ultimaBarra = ahora;
        var pct = Math.min(100, 100 * ms / cancion.duracionMs) + "%";
        if (tema === "escenario") elEscBarra.style.width = pct; else elBarra.style.width = pct;
      }
      if (tema === "escenario") { actualizarEscenario(ms); golpeEscenario(); }
      else if (tema === "vinilo") girarDisco(ahora);
      else if (tema === "galeria") actualizarLineaSola(elGalLinea, ms);
      else if (tema === "nocturno") { actualizarLineaSola(elNocLinea, ms); actualizarNocturno(ahora); }
      else if (tema === "atardecer") { actualizarLineaSola(elAtdLinea, ms); actualizarAtardecer(ms); }
      else if (tema === "tocadiscos") actualizarTocadiscos(ms);
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
        if (d.cristal) ponerCristal(d.cristal);
        if (typeof d.idioma === "string" && d.idioma) { idioma = d.idioma; ultimoReloj = 0; }
        if (d.placa === 1 || d.placa === 2) { placaForzada = d.placa; if (tema === "tocadiscos") prepararTocadiscos(); }
        if (typeof d.tema === "string") ponerTema(d.tema);
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
      document.body.classList.toggle("sonando", sonando);
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
    document.body.classList.add("sonando");
    posicion = function () { return (Date.now() - t0) % largo; };
    var params = {};
    location.search.slice(1).split("&").forEach(function (p) { var kv = p.split("="); if (kv[0]) params[kv[0]] = decodeURIComponent(kv[1] || ""); });
    if (params.hora) horaForzada = +params.hora;
    if (params.placa) placaForzada = +params.placa;
    if (params.tema) ponerTema(params.tema);
    // En el navegador, la tecla T pasa al tema siguiente.
    document.addEventListener("keydown", function (e) { if (e.key === "t" || e.key === "T") ponerTema(TEMAS[(TEMAS.indexOf(tema) + 1) % TEMAS.length]); });
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
      { id: "demo1", titulo: "Noche de prueba", artista: "Life Music · Demo", caratula: params.caratula || caratulaFalsa("#F59E0B", "#EF4444", "#7C3AED", "LM"),
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

  // ── TV con navegador (lifemusic.pages.dev/tv) ─────────────────────────────
  // tv.js enlaza con el telefono por el relevo y toca el audio; aqui solo se le
  // dan los ganchos para dibujar como en Cast.
  function iniciarTV() {
    if (!window.LifeMusicTV) { estado("No cargo tv.js (¿el TV tiene internet?)"); return; }
    window.LifeMusicTV.iniciar({
      version: VERSION,
      manejar: manejar,
      diag: diag,
      estado: estado,
      estadoSaludo: estadoSaludo,
      ponerPosicion: function (f) { posicion = f; },
      // 1.3.2: la duracion real del audio del TV, como hace CAF con la suya. Sin
      // esto, si el telefono no la sabia (cancion recien elegida), la barra de
      // progreso se quedaba en cero hasta la siguiente conexion.
      ponerDuracion: function (id, ms) {
        if (cancion && cancion.id === id && ms > 0 && Math.abs((cancion.duracionMs || 0) - ms) > 500) cancion.duracionMs = ms;
      },
      ponerSonando: function (si, pausado) {
        sonando = si;
        document.body.classList.toggle("sonando", si);
        escena.classList.toggle("pausa", !!pausado);
      },
      ligero: function () { if (!ligero) activarLigero(); },
      sinVideo: function () { sinVideo = true; document.body.classList.add("sin-video"); soltarPaisaje(); },
      ponerMandarDiag: function (f) {
        mandarDiag = f;
        for (var i = 0; i < diagPendiente.length; i++) f(diagPendiente[i]);
        diagPendiente = [];
      },
    });
  }

  var demo = /[?&]demo=1/.test(location.search);
  if (demo) {
    iniciarDemo();
  } else if (window.MODO_TV) {
    try { iniciarTV(); } catch (e) { estado("Error al iniciar el TV: " + (e && e.message ? e.message : e)); }
  } else {
    // En el TV nunca se cae a la demo: si Cast no arranca, se ve el error escrito.
    try { iniciarCast(); } catch (e) { estado("Error al iniciar Cast: " + (e && e.message ? e.message : e)); }
  }
})();
