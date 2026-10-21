/* lifemusic.app · la «landing viva» (identidad «03 · Pulso», aprobada por CG el 21-10-2026).
   Referencia exacta: Brand/maquetas/2026-10-21-landing-viva/landing-viva-escritorio.dc.html.
   - La pagina es una cancion de 3:58: el scroll es el tiempo y las partes se funden como el Automix.
   - La esfera de luz (Canvas 2D, 900 puntos en espiral de Fibonacci) sigue el puntero y el nivel del audio.
   - El sonido se genera en el navegador (Web Audio): sin archivos ni canciones con derechos, y solo al tocar «Escuchar».
   - Datos en vivo: descargas y version (API publica de GitHub, con cache), el servidor del TV y la hora de Barranquilla.
   Sin analitica. */
(() => {
  "use strict";

  const DISCOS = [
    { nombre: "Marea alta", bpm: 98, raiz: 55, menor: false, color: "#FF6B3D" },
    { nombre: "Luz de neón", bpm: 118, raiz: 41.2, menor: true, color: "#3B6CF6" },
    { nombre: "Órbita", bpm: 84, raiz: 36.7, menor: true, color: "#8B5CF6" },
    { nombre: "Selva", bpm: 104, raiz: 49, menor: false, color: "#1F9D6B" },
    { nombre: "Cobre", bpm: 92, raiz: 65.4, menor: false, color: "#D98A00" },
  ];
  const DURACION = 238; // 3:58
  const RELEVO_TV = "https://lifemusic-enlace.cho--usic.workers.dev";
  const RELEASES = "https://api.github.com/repos/cgus392-cmd/Life-Music/releases?per_page=100";

  const $ = (id) => document.getElementById(id);
  const raiz = document.documentElement;
  const reducido = !!(window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches);
  const oscuro = window.matchMedia ? matchMedia("(prefers-color-scheme: dark)") : { matches: false, addEventListener() {} };

  // ── El pulso: el unico color de la pagina ──────────────────────────────────
  const rgb = (hex) => { const n = parseInt(hex.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; };
  const mezcla = (a, b, t) => { const x = rgb(a), y = rgb(b); return "#" + x.map((v, i) => Math.round(v + (y[i] - v) * t).toString(16).padStart(2, "0")).join(""); };
  let disco = 0;
  function pintarPulso() {
    const d = DISCOS[disco];
    const base = oscuro.matches ? "#0E0E0E" : "#F5F5F3";
    const fondo = mezcla(base, d.color, 0.07); // el fondo se tine un 7 % con la caratula
    raiz.style.setProperty("--pulso", d.color);
    raiz.style.setProperty("--fondo-vivo", fondo);
    raiz.style.setProperty("--fondo-barra", fondo + "D9");
    document.querySelectorAll("[data-disco]").forEach((b) => b.setAttribute("aria-pressed", String(+b.dataset.disco === disco)));
    $("ahora").textContent = (sonando ? "Sonando · " : "Lista · ") + d.nombre + " · " + d.bpm + " BPM";
  }
  oscuro.addEventListener("change", pintarPulso);

  // ── Life Line: arriba en el heroe y fija abajo ─────────────────────────────
  const temporizadores = [];
  let ultNarra = 0;
  const narrar = (texto) => { ultNarra = performance.now(); $("narra").textContent = texto; };
  function decir(texto, ms = 0) {
    temporizadores.push(setTimeout(() => { $("linea").textContent = texto; narrar(texto); }, ms));
  }
  const horaBogota = () => {
    try { return +new Intl.DateTimeFormat("es-CO", { hour: "numeric", hourCycle: "h23", timeZone: "America/Bogota" }).format(new Date()); }
    catch { return new Date().getHours(); }
  };
  {
    const h = horaBogota();
    const saludo = (h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches") + ". ¿Listo para la sesión de hoy?";
    $("linea").textContent = saludo;
    $("narra").textContent = saludo;
  }

  // ── La pagina es una cancion: el scroll es el tiempo ──────────────────────
  const partes = [...document.querySelectorAll("[data-parte]")];
  const lineaT = $("linea-t");
  let marcas = partes.map(() => 0);
  let parteActual = -1;
  function reloj(s) { const t = Math.max(0, Math.min(DURACION, Math.round(s))); return Math.floor(t / 60) + ":" + String(t % 60).padStart(2, "0"); }
  function medir() {
    const alto = Math.max(1, raiz.scrollHeight - innerHeight);
    marcas = partes.map((el) => Math.min(1, Math.max(0, (el.offsetTop - 80) / alto)));
    lineaT.querySelectorAll(".marcador").forEach((b, i) => { b.style.left = (marcas[i] * 100).toFixed(2) + "%"; });
    document.querySelectorAll("[data-marca]").forEach((el) => { el.textContent = reloj(marcas[+el.dataset.marca] * DURACION); });
  }
  // Los marcadores Intro, Verso, Puente, Coro y Final: se tocan para saltar.
  partes.forEach((el, i) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "marcador";
    b.setAttribute("aria-label", "Ir a " + el.dataset.parte);
    b.innerHTML = "<i></i><span></span>";
    b.querySelector("span").textContent = el.dataset.parte;
    b.addEventListener("click", () => {
      narrar("Saltando al " + el.dataset.parte.toLowerCase() + "…");
      scrollTo({ top: Math.max(0, el.offsetTop - 70), behavior: reducido ? "auto" : "smooth" });
    });
    lineaT.insertBefore(b, $("cabeza"));
  });

  let ultY = scrollY, ultT = performance.now(), quieto = null;
  function recorrer() {
    const vh = innerHeight, alto = Math.max(1, raiz.scrollHeight - vh), y = scrollY, ahora = performance.now();
    // Las partes no se cortan: se funden (opacidad, subida y desenfoque), como el Automix.
    if (!reducido) {
      partes.forEach((el, i) => {
        if (i === 0) return;
        const r = el.getBoundingClientRect();
        const entra = Math.min(1, Math.max(0, (vh - r.top) / (vh * 0.35)));
        const sale = Math.min(1, Math.max(0, r.bottom / (vh * 0.3)));
        const f = Math.min(entra, sale);
        el.style.opacity = String(0.15 + 0.85 * f);
        el.style.transform = "translateY(" + ((1 - entra) * 34).toFixed(1) + "px)";
        el.style.filter = f < 0.98 ? "blur(" + ((1 - f) * 5).toFixed(1) + "px)" : "";
      });
    }
    const prog = Math.min(1, Math.max(0, y / alto));
    $("tiempo").textContent = reloj(prog * DURACION);
    $("relleno").style.width = (prog * 100).toFixed(2) + "%";
    $("cabeza").style.left = (prog * 100).toFixed(2) + "%";
    let parte = 0;
    partes.forEach((el, i) => { if (el.getBoundingClientRect().top < vh * 0.45) parte = i; });
    if (parte !== parteActual) {
      if (parteActual !== -1) narrar(partes[parte].dataset.dice);
      parteActual = parte;
      lineaT.querySelectorAll(".marcador").forEach((b, i) => b.classList.toggle("activo", i === parte));
    }
    // La velocidad del scroll mueve el filtro, como un DJ.
    const dt = Math.max(1, ahora - ultT), vel = (y - ultY) / dt;
    ultY = y; ultT = ahora;
    if (Math.abs(vel) > 2.2 && ahora - ultNarra > 1400) narrar(vel > 0 ? "Adelantando…" : "Rebobinando…");
    if (filtroMaestro && ac && ac.state === "running") {
      const objetivo = Math.abs(vel) > 1.2 ? 450 : 18000;
      filtroMaestro.frequency.setTargetAtTime(objetivo, ac.currentTime, objetivo < 1000 ? 0.05 : 0.4);
    }
  }
  let pedido = false;
  addEventListener("scroll", () => {
    if (!pedido) { pedido = true; requestAnimationFrame(() => { pedido = false; recorrer(); }); }
    // Al parar, el filtro vuelve a abrirse.
    clearTimeout(quieto);
    quieto = setTimeout(() => { ultT = performance.now(); ultY = scrollY; if (filtroMaestro && ac && ac.state === "running") filtroMaestro.frequency.setTargetAtTime(18000, ac.currentTime, 0.4); }, 180);
  }, { passive: true });
  addEventListener("resize", () => { medir(); recorrer(); });
  if (!reducido) document.body.classList.add("viva");

  // ── El sonido: un ritmo generado en el navegador, sin canciones con derechos ─
  let ac = null, master = null, filtroMaestro = null, analizador = null, muestras = null, ruido = null, relojAudio = null;
  let decks = [], sonando = false;
  function iniciarAudio() {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return false;
    ac = new AC();
    master = ac.createGain(); master.gain.value = 0;
    filtroMaestro = ac.createBiquadFilter(); filtroMaestro.type = "lowpass"; filtroMaestro.frequency.value = 18000;
    analizador = ac.createAnalyser(); analizador.fftSize = 512;
    muestras = new Float32Array(analizador.fftSize);
    master.connect(filtroMaestro); filtroMaestro.connect(analizador); analizador.connect(ac.destination);
    ruido = ac.createBuffer(1, ac.sampleRate, ac.sampleRate);
    const datos = ruido.getChannelData(0);
    for (let i = 0; i < datos.length; i++) datos[i] = Math.random() * 2 - 1;
    relojAudio = setInterval(() => {
      if (!ac || ac.state !== "running") return;
      const limite = ac.currentTime + 0.12;
      decks.forEach((d) => {
        while (d.siguiente < limite) { tocar(d, d.paso, d.siguiente); d.paso = (d.paso + 1) % 32; d.siguiente += 60 / d.bpm / 4; }
      });
    }, 25);
    return true;
  }
  function crearDeck(k, g0, inicio, paso) {
    const D = DISCOS[k];
    const gain = ac.createGain(); gain.gain.value = g0;
    const filtro = ac.createBiquadFilter(); filtro.type = "lowpass"; filtro.frequency.value = 18000;
    gain.connect(filtro); filtro.connect(master);
    return { gain, filtro, bpm: D.bpm, raiz: D.raiz, menor: D.menor, paso: paso || 0, siguiente: inicio || ac.currentTime + 0.06 };
  }
  function ruidoCorto(out, t, tipo, frec, vol, dur) {
    const s = ac.createBufferSource(), f = ac.createBiquadFilter(), g = ac.createGain();
    s.buffer = ruido; f.type = tipo; f.frequency.value = frec;
    g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
    s.connect(f).connect(g).connect(out); s.start(t, Math.random() * 0.5); s.stop(t + dur + 0.02);
  }
  function tocar(d, p, t) {
    const out = d.gain, paso = 60 / d.bpm / 4;
    if (p % 4 === 0) { // bombo
      const o = ac.createOscillator(), g = ac.createGain();
      o.frequency.setValueAtTime(150, t); o.frequency.exponentialRampToValueAtTime(42, t + 0.12);
      g.gain.setValueAtTime(0.9, t); g.gain.exponentialRampToValueAtTime(0.001, t + 0.35);
      o.connect(g).connect(out); o.start(t); o.stop(t + 0.4);
    }
    if (p % 4 === 2) ruidoCorto(out, t, "highpass", 7500, 0.1, 0.05); // hi-hat
    if (p % 8 === 4) ruidoCorto(out, t, "bandpass", 1600, 0.2, 0.14); // palmas
    const bajo = [0, null, null, 7, null, null, 0, null, 0, null, null, 7, null, 12, null, null][p % 16];
    if (bajo !== null) {
      const o = ac.createOscillator(), g = ac.createGain(), lp = ac.createBiquadFilter();
      o.type = "triangle"; o.frequency.value = d.raiz * 2 * Math.pow(2, bajo / 12);
      lp.type = "lowpass"; lp.frequency.value = 700;
      g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.3, t + 0.01); g.gain.exponentialRampToValueAtTime(0.001, t + 0.22);
      o.connect(lp).connect(g).connect(out); o.start(t); o.stop(t + 0.25);
    }
    if (p % 16 === 0) { // el acorde
      const dur = paso * 16;
      [0, d.menor ? 3 : 4, 7].forEach((n) => {
        const o = ac.createOscillator(), g = ac.createGain(), lp = ac.createBiquadFilter();
        o.type = "sawtooth"; o.frequency.value = d.raiz * 4 * Math.pow(2, n / 12);
        lp.type = "lowpass"; lp.frequency.value = 1100;
        g.gain.setValueAtTime(0.0001, t); g.gain.linearRampToValueAtTime(0.03, t + 0.5); g.gain.linearRampToValueAtTime(0.0001, t + dur);
        o.connect(lp).connect(g).connect(out); o.start(t); o.stop(t + dur + 0.05);
      });
    }
  }
  function ponerBoton() {
    const b = $("escuchar");
    b.setAttribute("aria-pressed", String(sonando));
    $("texto-escuchar").textContent = sonando ? "Pausar" : "Escuchar";
  }
  function alternar() {
    if (!ac && !iniciarAudio()) { decir("Tu navegador no puede generar el sonido."); return; }
    const D = DISCOS[disco];
    if (sonando) {
      const t = ac.currentTime;
      master.gain.cancelScheduledValues(t); master.gain.setValueAtTime(master.gain.value, t); master.gain.linearRampToValueAtTime(0, t + 0.3);
      temporizadores.push(setTimeout(() => { if (!sonando) ac.suspend(); }, 350));
      sonando = false;
      ponerBoton(); pintarPulso();
      decir("En pausa. La mezcla te espera.");
      return;
    }
    ac.resume();
    if (!decks.length) decks.push(crearDeck(disco, 1));
    const t = ac.currentTime;
    master.gain.cancelScheduledValues(t); master.gain.setValueAtTime(master.gain.value, t); master.gain.linearRampToValueAtTime(0.75, t + 0.4);
    sonando = true;
    ponerBoton(); pintarPulso(); arrancarEsfera();
    $("linea").textContent = "Sonando · " + D.nombre;
    narrar("Sonando · " + D.nombre + ". Baja: la página sigue el ritmo.");
  }
  function elegir(k) {
    if (k === disco) return;
    const D = DISCOS[k];
    disco = k;
    objetivo = rgb(D.color);
    pintarPulso(); arrancarEsfera();
    if (!sonando || !decks.length) { decir(D.nombre + " · " + D.bpm + " BPM. Toca «Escuchar»."); return; }
    // La mezcla de 3 s: el filtro del deck viejo se cierra hasta 220 Hz mientras entra el nuevo.
    const viejo = decks[decks.length - 1], t = ac.currentTime;
    const nuevo = crearDeck(k, 0.0001, viejo.siguiente, viejo.paso);
    nuevo.gain.gain.setValueAtTime(0.0001, t); nuevo.gain.gain.exponentialRampToValueAtTime(1, t + 3);
    viejo.filtro.frequency.setValueAtTime(viejo.filtro.frequency.value, t); viejo.filtro.frequency.exponentialRampToValueAtTime(220, t + 3);
    viejo.gain.gain.setValueAtTime(viejo.gain.gain.value, t); viejo.gain.gain.linearRampToValueAtTime(0, t + 3.2);
    decks.push(nuevo);
    temporizadores.push(setTimeout(() => { decks = decks.filter((x) => x !== viejo); viejo.gain.disconnect(); }, 3400));
    decir("Cerrando el filtro…");
    decir("Fundiendo las notas…", 1300);
    decir("Sonando · " + D.nombre, 3300);
  }
  $("escuchar").addEventListener("click", alternar);
  document.querySelectorAll("[data-disco]").forEach((b) => b.addEventListener("click", () => elegir(+b.dataset.disco)));

  // ── La esfera: el Pulso hecho luz ──────────────────────────────────────────
  const lienzo = $("esfera"), escena = $("escena");
  const puntos = [];
  {
    const N = 900, oro = Math.PI * (3 - Math.sqrt(5));
    for (let i = 0; i < N; i++) {
      const y = 1 - (i / (N - 1)) * 2, r = Math.sqrt(1 - y * y), a = oro * i;
      puntos.push({ x: Math.cos(a) * r, y, z: Math.sin(a) * r, fase: Math.random() * 6.28 });
    }
  }
  const t0 = performance.now();
  let color = rgb(DISCOS[0].color), objetivo = color.slice();
  let mx = 0, my = 0, px = -999, py = -999, nivel = 0;
  let visible = true, raf = 0;
  function dibujar(now) {
    raf = 0;
    const W = lienzo.clientWidth, H = lienzo.clientHeight;
    if (!W || !H) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    if (lienzo.width !== Math.round(W * dpr) || lienzo.height !== Math.round(H * dpr)) { lienzo.width = Math.round(W * dpr); lienzo.height = Math.round(H * dpr); }
    const g = lienzo.getContext("2d");
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, W, H);
    const t = (now - t0) / 1000;
    if (analizador && ac && ac.state === "running") {
      analizador.getFloatTimeDomainData(muestras);
      let suma = 0;
      for (let i = 0; i < muestras.length; i++) suma += muestras[i] * muestras[i];
      nivel = nivel * 0.8 + Math.min(1, Math.sqrt(suma / muestras.length) * 3.2) * 0.2;
    } else {
      nivel *= 0.94;
    }
    for (let i = 0; i < 3; i++) color[i] += (objetivo[i] - color[i]) * 0.05;
    const [r, gg, b] = color.map(Math.round);
    const cx = W / 2, cy = H / 2 - 10, base = Math.min(W, H) * 0.32;
    const R = base * (1 + (reducido ? 0 : Math.sin(t * 1.6) * 0.03) + nivel * 0.34);
    const halo = g.createRadialGradient(cx, cy, R * 0.1, cx, cy, R * 1.7);
    halo.addColorStop(0, "rgba(" + r + "," + gg + "," + b + "," + (0.16 + 0.3 * nivel) + ")");
    halo.addColorStop(1, "rgba(" + r + "," + gg + "," + b + ",0)");
    g.fillStyle = halo;
    g.fillRect(0, 0, W, H);
    const ry = (reducido ? 0 : t * 0.22) + mx * 0.7, rx = 0.35 + my * 0.45;
    const cY = Math.cos(ry), sY = Math.sin(ry), cX = Math.cos(rx), sX = Math.sin(rx);
    for (let i = 0; i < puntos.length; i++) {
      const p = puntos[i];
      const w = 1 + nivel * 0.22 * Math.sin(p.fase * 3 + t * 5);
      const x1 = p.x * cY + p.z * sY, z1 = -p.x * sY + p.z * cY;
      const y1 = p.y * cX - z1 * sX, z2 = p.y * sX + z1 * cX;
      const f = 2.8 / (2.8 - z2);
      let sx = cx + x1 * R * w * f, sy = cy + y1 * R * w * f;
      const dx = sx - px, dy = sy - py, d2 = dx * dx + dy * dy;
      if (d2 < 7000) { const k = (1 - d2 / 7000) * 20, d = Math.sqrt(d2) || 1; sx += (dx / d) * k; sy += (dy / d) * k; } // se aparta del puntero
      const prof = (z2 + 1) / 2, tam = 0.7 + 2.3 * prof;
      g.fillStyle = "rgba(" + r + "," + gg + "," + b + "," + (0.12 + 0.88 * prof) + ")";
      g.beginPath(); g.arc(sx, sy, tam, 0, 6.2832); g.fill();
    }
    // Con movimiento reducido, la esfera se queda quieta: un solo cuadro (o los que pida el color o el sonido).
    const asentado = Math.abs(objetivo[0] - color[0]) + Math.abs(objetivo[1] - color[1]) + Math.abs(objetivo[2] - color[2]) < 1.5;
    if (!reducido || !asentado || sonando || nivel > 0.01) arrancarEsfera();
  }
  function arrancarEsfera() {
    if (!raf && visible && !document.hidden) raf = requestAnimationFrame(dibujar);
  }
  // Fuera de pantalla o con la pestana oculta, la esfera no gasta bateria.
  if ("IntersectionObserver" in window) {
    new IntersectionObserver((e) => { visible = e[0].isIntersecting; if (visible) arrancarEsfera(); }).observe(escena);
  }
  document.addEventListener("visibilitychange", () => { if (!document.hidden) arrancarEsfera(); });
  escena.addEventListener("pointermove", (e) => {
    const rc = escena.getBoundingClientRect();
    mx = ((e.clientX - rc.left) / rc.width - 0.5) * 2; my = ((e.clientY - rc.top) / rc.height - 0.5) * 2;
    px = e.clientX - rc.left; py = e.clientY - rc.top;
    if (reducido) arrancarEsfera();
  });
  escena.addEventListener("pointerleave", () => { px = -999; py = -999; if (reducido) arrancarEsfera(); });

  // ── Datos en vivo ──────────────────────────────────────────────────────────
  const fmtNum = (n) => n.toLocaleString("es-CO");
  const megas = (b) => Math.round(b / 1048576) + " MB";
  function ponerHora() {
    try { $("hora").textContent = new Date().toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit", timeZone: "America/Bogota" }); }
    catch { $("hora").textContent = new Date().toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" }); }
  }
  ponerHora();
  setInterval(ponerHora, 20000);

  // El servidor del TV, como en la landing anterior.
  fetch(RELEVO_TV + "/salud", { cache: "no-store" })
    .then((r) => (r.ok ? r.json() : null))
    .then((j) => !!j && j.estado === "ok")
    .catch(() => false)
    .then((ok) => {
      const texto = ok ? "Servidor del TV en línea" : "Servidor del TV sin respuesta";
      $("vivo-tv").textContent = texto;
      $("estado-tv").textContent = texto;
      $("punto-tv").classList.toggle("apagado", !ok);
    });

  // Descargas y version, de la API publica de GitHub (60 consultas por hora sin clave): se guardan 30 minutos.
  function leerCache() {
    try { const c = JSON.parse(localStorage.getItem("lm-github") || "null"); return c && Date.now() - c.hora < 1800000 ? c.datos : null; } catch { return null; }
  }
  function guardarCache(datos) { try { localStorage.setItem("lm-github", JSON.stringify({ hora: Date.now(), datos })); } catch {} }
  function ponerGitHub(d) {
    if (!d) return;
    if (d.version) {
      $("vivo-version").textContent = "Versión " + d.version;
    }
    if (d.total) {
      $("vivo-descargas").textContent = fmtNum(d.total) + " descargas";
      $("vivo-descargas").hidden = false;
      $("num-descargas").textContent = fmtNum(d.total);
      $("detalle-descargas").textContent = (d.ultima ? fmtNum(d.ultima) + " de la " + d.version + ". " : "") + "Se cuenta solo, desde GitHub.";
    }
    if (d.peso) $("peso-apk").textContent = "APK " + megas(d.peso);
    if (d.pesoLigera) $("enlace-ligera").textContent = "versión ligera arm64, " + megas(d.pesoLigera);
  }
  const enCache = leerCache();
  if (enCache) ponerGitHub(enCache);
  else {
    fetch(RELEASES, { headers: { Accept: "application/vnd.github+json" } })
      .then((r) => (r.ok ? r.json() : null))
      .then((lista) => {
        if (!Array.isArray(lista)) return;
        const total = lista.reduce((s, r) => s + (r.assets || []).reduce((a, x) => a + (x.download_count || 0), 0), 0);
        // La «oficial» es la ultima publicada que no es prueba ni del carro.
        const oficial = lista.filter((r) => !r.draft && !r.prerelease && /^v\d/.test(r.tag_name))
          .sort((a, b) => Date.parse(b.published_at) - Date.parse(a.published_at))[0];
        const asset = (nombre) => oficial && (oficial.assets || []).find((x) => x.name === nombre);
        const datos = {
          total,
          version: oficial ? oficial.tag_name.replace(/^v/, "") : null,
          ultima: oficial ? (oficial.assets || []).reduce((a, x) => a + (x.download_count || 0), 0) : 0,
          peso: asset("lifemusic.apk") ? asset("lifemusic.apk").size : 0,
          pesoLigera: asset("lifemusic-arm64.apk") ? asset("lifemusic-arm64.apk").size : 0,
        };
        guardarCache(datos);
        ponerGitHub(datos);
      })
      .catch(() => {});
  }

  // ── Arranque ───────────────────────────────────────────────────────────────
  pintarPulso();
  medir();
  recorrer();
  arrancarEsfera();
  // Las letras y las imagenes cambian la altura de la pagina: se vuelve a medir cuando terminan de cargar.
  addEventListener("load", () => { medir(); recorrer(); });
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { medir(); recorrer(); });
})();
