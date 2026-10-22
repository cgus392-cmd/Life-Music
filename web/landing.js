/* lifemusic.app · la landing viva, segunda version (22-10-2026).
   Nova («A · La mirada», Brand/maquetas/2026-10-15-nova-personaje-luz.html) es la anfitriona:
   - al abrir aparece pequena, como la cabeza de la nota del logo, y se expande;
   - sigue el puntero con la mirada, parpadea cada 4 a 7 s, piensa antes de hablar y sonrie al terminar;
   - al bajar se queda pequena, abajo, y cuenta cada parte (solo texto: decision de CG);
   - cuando habla y suena musica, la musica baja (como en la app).
   La pagina es una cancion de 3:58: el scroll es el tiempo. El sonido se genera en el navegador (Web Audio) y solo
   suena si la persona lo pide. Datos en vivo de GitHub (con cache) y del servidor del TV. Sin analitica. */
(() => {
  "use strict";

  const DISCOS = [
    { nombre: "Marea alta", bpm: 98, raiz: 55, menor: false, c1: "#FF6B3D", c2: "#FFB49A" },
    { nombre: "Luz de neón", bpm: 118, raiz: 41.2, menor: true, c1: "#3B6CF6", c2: "#FF4FA3" },
    { nombre: "Órbita", bpm: 84, raiz: 36.7, menor: true, c1: "#8B5CF6", c2: "#B4A2F6" },
    { nombre: "Selva", bpm: 104, raiz: 49, menor: false, c1: "#1F9D6B", c2: "#7FD8B2" },
    { nombre: "Cobre", bpm: 92, raiz: 65.4, menor: false, c1: "#D98A00", c2: "#FBD38D" },
  ];
  // Lo que dice Nova en cada parte de la cancion (al llegar y al tocarla).
  const PARTES = {
    Intro: { dice: "Hola, soy Nova. Te muestro Life Music en un minuto: baja cuando quieras.", toque: "Estamos al comienzo. Elige una carátula y la pongo." },
    Verso: { dice: "Esta es la app de verdad: la letra va con la canción y nada se corta.", toque: "Pasa por cada función y te la cuento." },
    Puente: { dice: "¿Tu TV no tiene Cast? Elige un tema y te lo muestro en la pantalla grande.", toque: "En el TV abres lifemusic.app/tv, escaneas el QR y listo." },
    Coro: { dice: "Un minuto y la tienes. Si el teléfono pregunta, dale permitir: es normal.", toque: "Bájala con el botón de la tarjeta. Es gratis y sin anuncios." },
    Final: { dice: "Lo nuevo y lo que siempre preguntan. Y sí: con la 2.0 llego yo a la app.", toque: "Si te quedó alguna duda, mira las preguntas." },
  };
  const TEMAS_TV = {
    vinilo: { img: "/img/tv-tema-vinilo.jpg", alt: "Life Music TV con el tema Vinilo: un tocadiscos con la carátula y la letra al lado", dice: "Vinilo: tu carátula girando en un tocadiscos, con la letra al lado." },
    ambiente: { img: "/img/tv-tema-ambiente.jpg", alt: "Life Music TV con el tema Ambiente: la carátula grande y la letra sobre los colores de la canción", dice: "Ambiente: la sala toma los colores de la carátula." },
    cristal: { img: "/img/tv-tema-cristal.jpg", alt: "Life Music TV con el tema Cristal líquido: la letra detrás de un panel de vidrio", dice: "Cristal líquido: la letra detrás de un vidrio que se mueve con la música." },
  };
  const DURACION = 238; // 3:58
  const RELEVO_TV = "https://lifemusic-enlace.cho--usic.workers.dev";
  const RELEASES = "https://api.github.com/repos/cgus392-cmd/Life-Music/releases?per_page=100";

  const $ = (id) => document.getElementById(id);
  const raiz = document.documentElement;
  const reducido = !!(window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches);
  const oscuro = window.matchMedia ? matchMedia("(prefers-color-scheme: dark)") : { matches: false, addEventListener() {} };
  const ahora = () => performance.now();
  const limitar = (v, a, b) => Math.max(a, Math.min(b, v));

  // ── Colores ────────────────────────────────────────────────────────────────
  const rgb = (hex) => { const n = parseInt(hex.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; };
  const aHex = (c) => "#" + c.map((v) => Math.round(limitar(v, 0, 255)).toString(16).padStart(2, "0")).join("");
  const mezclar = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
  const BLANCO = [255, 255, 255];
  let disco = 0;

  // ══ Nova ═══════════════════════════════════════════════════════════════════
  const NS = "http://www.w3.org/2000/svg";
  const TINTA = "#14161B";
  // Los ojos de cada estado, en la reticula de 200 x 200 de la maqueta.
  const OJOS = {
    escucha: { xi: 80.5, xd: 109.5, y: 88, w: 10, h: 23 },
    piensa: { xi: 86, xd: 114, y: 81, w: 9.5, h: 19 },
    habla: { xi: 80.5, xd: 109.5, y: 92, w: 10, h: 17 },
  };
  let cuentaNova = 0;
  function nodo(tag, attrs, padre) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    if (padre) padre.appendChild(e);
    return e;
  }
  function crearNova(contenedor) {
    const id = "nova" + ++cuentaNova;
    const svg = nodo("svg", { viewBox: "0 0 200 200", "aria-hidden": "true", focusable: "false" }, contenedor);
    const defs = nodo("defs", {}, svg);
    const halo = nodo("radialGradient", { id: id + "h" }, defs);
    const h0 = nodo("stop", { offset: "0" }, halo), h1 = nodo("stop", { offset: ".55" }, halo), h2 = nodo("stop", { offset: "1", "stop-opacity": "0" }, halo);
    const orbe = nodo("radialGradient", { id: id + "o", cx: ".46", cy: ".42", r: ".6" }, defs);
    nodo("stop", { offset: "0", "stop-color": "#FFFFFF" }, orbe);
    nodo("stop", { offset: ".42", "stop-color": "#FFF8EE" }, orbe);
    const o2 = nodo("stop", { offset: ".78" }, orbe), o3 = nodo("stop", { offset: "1" }, orbe);
    const filtro = nodo("filter", { id: id + "b", x: "-50%", y: "-50%", width: "200%", height: "200%" }, defs);
    nodo("feGaussianBlur", { stdDeviation: "7" }, filtro);
    for (const [eje, x2, y2] of [["H", "1", "0"], ["V", "0", "1"]]) {
      const g = nodo("linearGradient", { id: id + eje, x2, y2 }, defs);
      nodo("stop", { offset: "0", "stop-color": "#fff", "stop-opacity": "0" }, g);
      nodo("stop", { offset: ".5", "stop-color": "#fff" }, g);
      nodo("stop", { offset: "1", "stop-color": "#fff", "stop-opacity": "0" }, g);
    }
    nodo("circle", { cx: 100, cy: 100, r: 98, fill: `url(#${id}h)` }, svg);
    const anillo1 = nodo("circle", { cx: 100, cy: 100, r: 72, fill: "none", "stroke-width": 1.4 }, svg);
    const anillo2 = nodo("circle", { cx: 100, cy: 100, r: 84, fill: "none", "stroke-width": 1.2 }, svg);
    const chispa = nodo("g", {}, svg);
    nodo("path", { d: "M156 66 A66 66 0 0 0 128 40", fill: "none", stroke: "#fff", "stroke-width": 3, "stroke-linecap": "round", opacity: ".75" }, chispa);
    nodo("circle", { cx: 156, cy: 66, r: 4.5, fill: "#fff" }, chispa);
    const cuerpo = nodo("g", {}, svg);
    const brillo = nodo("circle", { cx: 100, cy: 100, r: 60, opacity: ".55", filter: `url(#${id}b)` }, cuerpo);
    nodo("circle", { cx: 100, cy: 100, r: 56, fill: `url(#${id}o)` }, cuerpo);
    const ojoI = nodo("rect", { fill: TINTA }, cuerpo), ojoD = nodo("rect", { fill: TINTA }, cuerpo);
    const sonrisa = nodo("path", { d: "M78.5 103 q7 -11 14 0 M107.5 103 q7 -11 14 0", fill: "none", stroke: TINTA, "stroke-width": 4.8, "stroke-linecap": "round" }, cuerpo);
    const dormida = nodo("path", { d: "M80 103 h13 M107 103 h13", stroke: TINTA, "stroke-width": 4.5, "stroke-linecap": "round", opacity: ".55" }, cuerpo);
    // El destello en cruz, el ADN de la estrella de Nova.
    const destello = nodo("g", { opacity: ".85" }, cuerpo);
    nodo("rect", { x: 59, y: 71.2, width: 34, height: 1.6, rx: .8, fill: `url(#${id}H)` }, destello);
    nodo("rect", { x: 75.2, y: 55, width: 1.6, height: 34, rx: .8, fill: `url(#${id}V)` }, destello);

    const nova = {
      contenedor, estado: "apagada", ojos: { ...OJOS.escucha }, mira: [0, 0], quiereMirar: [0, 0],
      parpadeo: 0, proxParpadeo: ahora() + 2500, energia: 0, c1: rgb(DISCOS[0].c1), c2: rgb(DISCOS[0].c2), pintado: "",
      dibujar(t, nivelMusica) {
        // Colores: se mueven hacia los de la caratula que suena.
        const d = DISCOS[disco], m1 = rgb(d.c1), m2 = rgb(d.c2);
        this.c1 = mezclar(this.c1, m1, 0.06); this.c2 = mezclar(this.c2, m2, 0.06);
        const firma = aHex(this.c1) + aHex(this.c2) + this.estado;
        if (firma !== this.pintado) {
          this.pintado = firma;
          const apagada = this.estado === "apagada";
          const c1 = apagada ? "#8A8F96" : aHex(this.c1), c2 = apagada ? "#5F6368" : aHex(this.c2);
          const opHalo = apagada ? 0 : oscuro.matches ? 0.6 : 0.45;
          h0.setAttribute("stop-color", c1); h0.setAttribute("stop-opacity", opHalo);
          h1.setAttribute("stop-color", c2); h1.setAttribute("stop-opacity", opHalo * 0.45);
          h2.setAttribute("stop-color", c2);
          o2.setAttribute("stop-color", apagada ? "#AEB3B9" : aHex(mezclar(this.c1, BLANCO, 0.45)));
          o3.setAttribute("stop-color", c1);
          brillo.setAttribute("fill", c1);
          brillo.setAttribute("opacity", apagada ? "0" : ".55");
          anillo1.setAttribute("stroke", c1); anillo2.setAttribute("stroke", c2);
          destello.setAttribute("opacity", apagada ? "0" : ".85");
        }
        // La mirada sigue al puntero, con calma.
        this.mira[0] += (this.quiereMirar[0] - this.mira[0]) * 0.12;
        this.mira[1] += (this.quiereMirar[1] - this.mira[1]) * 0.12;
        const objetivo = OJOS[this.estado] || OJOS.escucha;
        for (const k of ["xi", "xd", "y", "w", "h"]) this.ojos[k] += (objetivo[k] - this.ojos[k]) * 0.25;
        // Parpadeo cada 4 a 7 s (nunca dormida ni sonriendo).
        if (t > this.proxParpadeo && (this.estado === "escucha" || this.estado === "habla")) { this.parpadeo = t; this.proxParpadeo = t + 4000 + Math.random() * 3000; }
        const fase = (t - this.parpadeo) / 150;
        const cierre = fase >= 0 && fase < 2 ? 1 - Math.sin((fase / 2) * Math.PI) * 0.88 : 1;
        const o = this.ojos, alto = o.h * cierre, dy = (o.h - alto) / 2;
        const mx = this.mira[0], my = this.mira[1];
        const ver = this.estado === "escucha" || this.estado === "piensa" || this.estado === "habla";
        for (const [r, x] of [[ojoI, o.xi], [ojoD, o.xd]]) {
          r.setAttribute("x", (x + mx).toFixed(2)); r.setAttribute("y", (o.y + dy + my).toFixed(2));
          r.setAttribute("width", o.w.toFixed(2)); r.setAttribute("height", alto.toFixed(2)); r.setAttribute("rx", (o.w / 2).toFixed(2));
          r.setAttribute("opacity", ver ? "1" : "0");
        }
        sonrisa.setAttribute("opacity", this.estado === "termino" ? "1" : "0");
        sonrisa.setAttribute("transform", `translate(${(mx * 0.6).toFixed(2)} ${(my * 0.6).toFixed(2)})`);
        dormida.setAttribute("opacity", this.estado === "apagada" ? ".55" : "0");
        chispa.setAttribute("opacity", this.estado === "piensa" ? "1" : "0");
        // El cuerpo: respira al escuchar, se mueve con su voz al hablar.
        if (this.estado === "habla") {
          const s = t / 1000;
          this.energia = limitar(0.3 + 0.7 * Math.abs(Math.sin(s * 9.3)) * (0.6 + 0.4 * Math.sin(s * 2.3)), 0, 1);
        } else this.energia *= 0.85;
        const respira = reducido ? 0 : Math.sin(t / 700) * 0.012;
        const escala = 1 + (this.estado === "escucha" ? respira + nivelMusica * 0.07 : 0) + (reducido ? 0 : this.energia * 0.08);
        cuerpo.setAttribute("transform", `translate(100 100) scale(${escala.toFixed(4)}) translate(-100 -100)`);
        const e = this.energia;
        if (this.estado === "habla") {
          anillo1.setAttribute("r", (70 + 18 * e).toFixed(1)); anillo1.setAttribute("opacity", ".55");
          anillo2.setAttribute("r", (84 + 22 * e).toFixed(1)); anillo2.setAttribute("opacity", ".32");
        } else {
          anillo1.setAttribute("r", (72 + nivelMusica * 10).toFixed(1)); anillo1.setAttribute("opacity", this.estado === "escucha" ? (0.28 + nivelMusica * 0.3).toFixed(2) : "0");
          anillo2.setAttribute("opacity", "0");
        }
      },
    };
    return nova;
  }

  const novaGrande = crearNova($("nova-grande"));
  const novaChica = crearNova($("nova-chica"));
  const novas = [novaGrande, novaChica];

  // Que mira Nova: el puntero (o, en pantallas tactiles, hacia donde va el scroll).
  let puntero = null;
  addEventListener("pointermove", (e) => { if (e.pointerType !== "touch") puntero = [e.clientX, e.clientY]; }, { passive: true });
  document.addEventListener("pointerleave", () => { puntero = null; });
  function apuntarMiradas(velScroll) {
    for (const n of novas) {
      const r = n.contenedor.getBoundingClientRect();
      if (!r.width) continue;
      if (puntero) {
        const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
        const dx = (puntero[0] - cx) / Math.max(200, r.width), dy = (puntero[1] - cy) / Math.max(200, r.height);
        n.quiereMirar = [limitar(dx * 9, -6, 6), limitar(dy * 7, -5, 5)];
      } else {
        n.quiereMirar = [0, limitar(velScroll * 3, -4, 4)];
      }
    }
  }

  // ── Lo que dice Nova ──────────────────────────────────────────────────────
  let enEscena = true; // la grande se ve; si no, habla la pequena
  let tiempos = [], cola = [], yaHablo = false;
  const voz = $("voz"), dice = $("dice-grande"), globo = $("globo");
  function limpiar() { tiempos.forEach(clearTimeout); tiempos = []; }
  function luego(ms, fn) { tiempos.push(setTimeout(fn, ms)); }
  // decir() corta lo que venia diciendo (y lo que tenia en fila); despues() encadena frases sin cortarse a si misma.
  function decir(texto, opciones) { cola.forEach(clearTimeout); cola = []; hablar(texto, opciones); }
  function despues(ms, texto, opciones) { cola.push(setTimeout(() => hablar(texto, opciones), ms)); }
  function hablar(texto, { pensar = true } = {}) {
    limpiar();
    yaHablo = true;
    const n = enEscena ? novaGrande : novaChica;
    const otra = enEscena ? novaChica : novaGrande;
    if (otra.estado !== "apagada") otra.estado = "escucha";
    voz.textContent = texto; // para lectores de pantalla, una sola vez
    const mostrar = () => {
      n.estado = "habla";
      if (enEscena) { dice.textContent = texto; dice.classList.add("visible"); globo.classList.remove("visible"); }
      else { globo.textContent = texto; globo.classList.add("visible"); dice.classList.remove("visible"); }
      bajarMusica(true);
      const dur = limitar(texto.length * 52, 1300, 5200);
      luego(dur, () => {
        n.estado = "termino";
        bajarMusica(false);
        luego(1400, () => { n.estado = "escucha"; });
        if (!enEscena) luego(5000, () => globo.classList.remove("visible"));
      });
    };
    if (pensar && !reducido) { n.estado = "piensa"; luego(450, mostrar); } else mostrar();
  }

  // ── La musica: un ritmo generado en el navegador, sin canciones con derechos ─
  let ac = null, master = null, filtroMaestro = null, analizador = null, muestras = null, ruido = null;
  let decks = [], sonando = false, nivel = 0;
  const golpes = []; // la hora de cada bombo del deck que manda: el titular y las notas van a ese golpe
  function bajarMusica(baja) {
    if (!master || !ac || !sonando) return;
    const t = ac.currentTime;
    master.gain.cancelScheduledValues(t);
    master.gain.setValueAtTime(master.gain.value, t);
    master.gain.linearRampToValueAtTime(baja ? 0.35 : 0.75, t + (baja ? 0.25 : 0.6));
  }
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
    setInterval(() => {
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
      if (d === decks[decks.length - 1] && golpes.length < 16) golpes.push(t);
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
    $("escuchar").setAttribute("aria-pressed", String(sonando));
    $("texto-escuchar").textContent = sonando ? "Pausar la música" : "Ponle música";
    document.body.classList.toggle("sonando", sonando);
    if (!sonando) golpes.length = 0;
  }
  function alternar() {
    if (!ac && !iniciarAudio()) { decir("Tu navegador no me deja poner música aquí."); return; }
    const D = DISCOS[disco];
    if (sonando) {
      const t = ac.currentTime;
      master.gain.cancelScheduledValues(t); master.gain.setValueAtTime(master.gain.value, t); master.gain.linearRampToValueAtTime(0, t + 0.3);
      setTimeout(() => { if (!sonando) ac.suspend(); }, 350);
      sonando = false;
      ponerBoton();
      decir("En pausa. La mezcla te espera.");
      return;
    }
    ac.resume();
    if (!decks.length) decks.push(crearDeck(disco, 1));
    sonando = true;
    const t = ac.currentTime;
    master.gain.cancelScheduledValues(t); master.gain.setValueAtTime(master.gain.value, t); master.gain.linearRampToValueAtTime(0.75, t + 0.4);
    ponerBoton();
    arrancar();
    decir("Esta es «" + D.nombre + "», a " + D.bpm + " BPM. Elige otra carátula y la mezclo.");
  }
  function elegir(k) {
    if (k === disco) return;
    const D = DISCOS[k];
    disco = k;
    pintarPulso();
    arrancar();
    if (!sonando || !decks.length) { decir("«" + D.nombre + "», " + D.bpm + " BPM. Toca «Ponle música» y la pongo."); return; }
    // La mezcla de 3 s: el filtro del deck viejo se cierra hasta 220 Hz mientras entra el nuevo.
    const viejo = decks[decks.length - 1], t = ac.currentTime;
    const nuevo = crearDeck(k, 0.0001, viejo.siguiente, viejo.paso);
    nuevo.gain.gain.setValueAtTime(0.0001, t); nuevo.gain.gain.exponentialRampToValueAtTime(1, t + 3);
    viejo.filtro.frequency.setValueAtTime(viejo.filtro.frequency.value, t); viejo.filtro.frequency.exponentialRampToValueAtTime(220, t + 3);
    viejo.gain.gain.setValueAtTime(viejo.gain.gain.value, t); viejo.gain.gain.linearRampToValueAtTime(0, t + 3.2);
    decks.push(nuevo);
    setTimeout(() => { decks = decks.filter((x) => x !== viejo); viejo.gain.disconnect(); }, 3400);
    decir("Cerrando el filtro…", { pensar: false });
    despues(1300, "Fundiendo las notas…", { pensar: false });
    despues(3300, "Sonando «" + D.nombre + "».", { pensar: false });
  }
  $("escuchar").addEventListener("click", alternar);
  document.querySelectorAll("[data-disco]").forEach((b) => b.addEventListener("click", () => elegir(+b.dataset.disco)));

  // ── El pulso: el unico color de la pagina ─────────────────────────────────
  function pintarPulso() {
    const d = DISCOS[disco];
    const base = rgb(oscuro.matches ? "#0E0E0E" : "#F5F5F3");
    const fondo = aHex(mezclar(base, rgb(d.c1), 0.07)); // el fondo se tine un 7 % con la caratula
    raiz.style.setProperty("--pulso", d.c1);
    raiz.style.setProperty("--fondo-vivo", fondo);
    raiz.style.setProperty("--fondo-barra", fondo + "D9");
    document.querySelectorAll("[data-disco]").forEach((b) => b.setAttribute("aria-pressed", String(+b.dataset.disco === disco)));
  }
  oscuro.addEventListener("change", () => { pintarPulso(); novas.forEach((n) => (n.pintado = "")); });

  // ── La pagina es una cancion: el scroll es el tiempo ──────────────────────
  const partes = [...document.querySelectorAll("[data-parte]")];
  const paralaje = [...document.querySelectorAll(".marco-tel, .marco-tv")];
  const lineaT = $("linea-t");
  let marcas = partes.map(() => 0), parteActual = 0;
  const reloj = (s) => { const t = Math.round(limitar(s, 0, DURACION)); return Math.floor(t / 60) + ":" + String(t % 60).padStart(2, "0"); };
  function medir() {
    const alto = Math.max(1, raiz.scrollHeight - innerHeight);
    marcas = partes.map((el) => limitar((el.offsetTop - 80) / alto, 0, 1));
    lineaT.querySelectorAll(".marcador").forEach((b, i) => { b.style.left = (marcas[i] * 100).toFixed(2) + "%"; });
    document.querySelectorAll("[data-marca]").forEach((el) => { el.textContent = reloj(marcas[+el.dataset.marca] * DURACION); });
  }
  partes.forEach((el) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "marcador";
    b.setAttribute("aria-label", "Ir a " + el.dataset.parte);
    b.innerHTML = "<i></i><span></span>";
    b.querySelector("span").textContent = el.dataset.parte;
    b.addEventListener("click", () => scrollTo({ top: Math.max(0, el.offsetTop - 70), behavior: reducido ? "auto" : "smooth" }));
    lineaT.insertBefore(b, $("cabezal"));
  });
  let ultY = scrollY, ultT = ahora(), velScroll = 0, ultRapido = 0, quieto = null;
  function recorrer() {
    const vh = innerHeight, alto = Math.max(1, raiz.scrollHeight - vh), y = scrollY, t = ahora();
    // Las partes no se cortan: entran subiendo y aclarandose, como una cancion que entra con el Automix.
    if (!reducido) {
      partes.forEach((el, i) => {
        if (i === 0) return;
        const r = el.getBoundingClientRect();
        const entra = limitar((vh - r.top) / (vh * 0.35), 0, 1);
        el.style.opacity = String(0.2 + 0.8 * entra);
        el.style.transform = "translateY(" + ((1 - entra) * 28).toFixed(1) + "px)";
      });
      // El telefono y el TV flotan un poco mas lento que la pagina (parallax suave).
      paralaje.forEach((el) => {
        const r = el.parentElement.getBoundingClientRect();
        if (r.bottom < -200 || r.top > vh + 200) return;
        el.style.transform = "translateY(" + ((r.top + r.height / 2 - vh / 2) * -0.06).toFixed(1) + "px)";
      });
    }
    const prog = limitar(y / alto, 0, 1);
    $("tiempo").textContent = reloj(prog * DURACION);
    $("relleno").style.width = (prog * 100).toFixed(2) + "%";
    $("cabezal").style.left = (prog * 100).toFixed(2) + "%";
    let parte = 0;
    partes.forEach((el, i) => { if (el.getBoundingClientRect().top < vh * 0.45) parte = i; });
    if (parte !== parteActual) {
      parteActual = parte;
      lineaT.querySelectorAll(".marcador").forEach((b, i) => b.classList.toggle("activo", i === parte));
      if (parte > 0) decir(PARTES[partes[parte].dataset.parte].dice);
    }
    // La velocidad del scroll: Nova lo nota y el filtro se cierra, como un DJ.
    const dt = Math.max(1, t - ultT);
    velScroll = (y - ultY) / dt;
    ultY = y; ultT = t;
    if (Math.abs(velScroll) > 2.4 && t - ultRapido > 3000) { ultRapido = t; decir(velScroll > 0 ? "¡Adelantando!" : "Rebobinando…", { pensar: false }); }
    if (filtroMaestro && ac && ac.state === "running") {
      const objetivo = Math.abs(velScroll) > 1.2 ? 450 : 18000;
      filtroMaestro.frequency.setTargetAtTime(objetivo, ac.currentTime, objetivo < 1000 ? 0.05 : 0.4);
    }
    arrancar();
  }
  let pedido = false;
  addEventListener("scroll", () => {
    if (!pedido) { pedido = true; requestAnimationFrame(() => { pedido = false; recorrer(); }); }
    clearTimeout(quieto);
    quieto = setTimeout(() => {
      velScroll = 0; ultT = ahora(); ultY = scrollY;
      if (filtroMaestro && ac && ac.state === "running") filtroMaestro.frequency.setTargetAtTime(18000, ac.currentTime, 0.4);
    }, 180);
  }, { passive: true });
  addEventListener("resize", () => { medir(); recorrer(); });
  if (!reducido) document.body.classList.add("viva");

  // ── Nova en escena y Nova acompanando ─────────────────────────────────────
  const acompana = $("acompana"), botonChica = $("nova-chica");
  if ("IntersectionObserver" in window) {
    new IntersectionObserver((e) => {
      enEscena = e[0].isIntersecting;
      acompana.classList.toggle("visible", !enEscena);
      acompana.setAttribute("aria-hidden", String(enEscena));
      botonChica.tabIndex = enEscena ? -1 : 0;
      if (!enEscena && novaChica.estado === "apagada") novaChica.estado = "escucha";
      if (enEscena) globo.classList.remove("visible");
      arrancar();
    }, { threshold: 0.2 }).observe($("escenario"));
  }
  botonChica.addEventListener("click", () => decir(PARTES[partes[parteActual].dataset.parte].toque));
  $("escenario").addEventListener("click", () => {
    decir(sonando ? "Sonando «" + DISCOS[disco].nombre + "». Elige otra carátula y la mezclo." : "¿Ponemos música? Toca «Ponle música» y yo la mezclo.");
  });
  // Mira el boton de descarga (y lo dice una vez).
  let dijoDescarga = false;
  document.querySelectorAll(".descargar").forEach((b) => b.addEventListener("pointerenter", (e) => {
    if (dijoDescarga || e.pointerType === "touch") return;
    dijoDescarga = true;
    decir("Ese es. Gratis, sin anuncios y sin cuentas.");
  }));
  // Las funciones: al pasar (o tocar), Nova la cuenta.
  document.querySelectorAll(".funcion[data-nova]").forEach((f) => {
    let espera = null;
    f.addEventListener("pointerenter", (e) => { if (e.pointerType !== "touch") espera = setTimeout(() => decir(f.dataset.nova), 500); });
    f.addEventListener("pointerleave", () => clearTimeout(espera));
    f.addEventListener("click", () => decir(f.dataset.nova));
  });
  // Los temas del TV.
  document.querySelectorAll("[data-tema]").forEach((b) => b.addEventListener("click", () => {
    const tema = TEMAS_TV[b.dataset.tema], img = $("tv-img");
    document.querySelectorAll("[data-tema]").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    img.style.opacity = "0";
    setTimeout(() => { img.src = tema.img; img.alt = tema.alt; img.onload = () => { img.style.opacity = "1"; }; }, reducido ? 0 : 200);
    decir(tema.dice);
  }));
  Object.values(TEMAS_TV).forEach((t) => { const i = new Image(); i.src = t.img; });

  // ── Las notas que flotan: cabezas de nota del logo que salen de Nova ──────
  // Salen con cada golpe del bombo cuando suena musica, y mientras Nova habla. Nada de esto con movimiento reducido.
  const DPR = Math.min(2, window.devicePixelRatio || 1);
  function lienzoNotas(padre, nova, tamano) {
    const c = document.createElement("canvas");
    c.className = "lienzo-notas";
    c.setAttribute("aria-hidden", "true");
    padre.insertBefore(c, padre.firstChild);
    return { c, g: c.getContext("2d"), nova, tamano, notas: [], w: 0, h: 0 };
  }
  const lienzos = reducido ? [] : [lienzoNotas($("escenario"), novaGrande, 1), lienzoNotas(acompana, novaChica, 0.6)];
  function soltarNota(L, cuantas = 1) {
    if (!L || L.notas.length > 36) return;
    const cr = L.c.getBoundingClientRect(), nr = L.nova.contenedor.getBoundingClientRect();
    if (!cr.width || !nr.width) return;
    const cx = nr.left + nr.width / 2 - cr.left, cy = nr.top + nr.height / 2 - cr.top, r = nr.width * 0.28;
    for (let i = 0; i < cuantas; i++) {
      const a = -Math.PI / 2 + (Math.random() - 0.5) * Math.PI * 1.1;
      L.notas.push({
        x: cx + Math.cos(a) * r * 0.85, y: cy + Math.sin(a) * r * 0.85,
        vx: Math.cos(a) * 14, vy: -(26 + Math.random() * 40) * (0.6 + L.tamano * 0.4),
        s: (4 + Math.random() * 4.5) * L.tamano + 2, fase: Math.random() * 6.28, vida: 0, dura: 2.2 + Math.random() * 1.4,
      });
    }
  }
  function moverNotas(L, dt) {
    if (!L.notas.length && !L.sucio) return;
    const w = L.c.clientWidth, h = L.c.clientHeight;
    if (w !== L.w || h !== L.h) { L.w = w; L.h = h; L.c.width = Math.round(w * DPR); L.c.height = Math.round(h * DPR); }
    const g = L.g;
    g.setTransform(DPR, 0, 0, DPR, 0, 0);
    g.clearRect(0, 0, w, h);
    g.fillStyle = aHex(L.nova.c1);
    L.notas = L.notas.filter((n) => (n.vida += dt) < n.dura);
    for (const n of L.notas) {
      n.x += (n.vx + Math.sin(n.vida * 2.2 + n.fase) * 10) * dt;
      n.y += n.vy * dt;
      n.vx *= 0.985;
      const entra = Math.min(1, n.vida / 0.18), sale = 1 - Math.max(0, (n.vida - n.dura * 0.55) / (n.dura * 0.45));
      g.globalAlpha = Math.max(0, entra * sale) * 0.9;
      g.save();
      g.translate(n.x, n.y);
      g.rotate((-22 + Math.sin(n.vida * 3 + n.fase) * 10) * Math.PI / 180);
      g.beginPath();
      g.ellipse(0, 0, n.s, n.s * 0.74, 0, 0, Math.PI * 2);
      g.fill();
      g.restore();
    }
    g.globalAlpha = 1;
    L.sucio = L.notas.length > 0;
  }

  // ── El titular, cantado como una letra sincronizada ───────────────────────
  const titular = document.querySelector(".heroe h1");
  const palabras = [];
  {
    const texto = titular.textContent.trim().split(/\s+/);
    titular.textContent = "";
    texto.forEach((p, i) => {
      const s = document.createElement("span");
      s.className = "w";
      s.textContent = p;
      titular.appendChild(s);
      palabras.push(s);
      if (i < texto.length - 1) titular.appendChild(document.createTextNode(" "));
    });
  }
  function cantarTitular() {
    if (reducido) return;
    titular.classList.add("cantando");
    palabras.forEach((p, i) => setTimeout(() => p.classList.add("cantada"), 250 + i * 230));
    setTimeout(() => titular.classList.remove("cantando"), 400 + palabras.length * 230);
  }
  let palabraGolpe = -1;
  // Con musica: la palabra del golpe salta un poco y se subraya con el color del pulso, una por golpe,
  // y el titular descansa dos golpes al final, como una letra que vuelve a empezar.
  function marcarPalabra(i) {
    palabras.forEach((p, k) => p.classList.toggle("golpe", k === i));
  }
  function alGolpe() {
    palabraGolpe = (palabraGolpe + 1) % (palabras.length + 2);
    if (!reducido) marcarPalabra(palabraGolpe);
    const L = lienzos[enEscena ? 0 : 1];
    if (L && Math.random() < 0.75) soltarNota(L, Math.random() < 0.3 ? 2 : 1);
  }

  // ── La barra respira: la linea de tiempo se vuelve la onda de lo que suena ─
  const onda = $("onda"), gOnda = onda.getContext("2d");
  let colorTexto = "", colorTenue = "";
  function leerColores() {
    const cs = getComputedStyle(raiz);
    colorTexto = cs.getPropertyValue("--texto").trim();
    colorTenue = cs.getPropertyValue("--tenue").trim();
  }
  leerColores();
  oscuro.addEventListener("change", leerColores);
  function dibujarOnda() {
    const activa = sonando && nivel > 0.01 && onda.offsetWidth > 0;
    lineaT.classList.toggle("con-onda", activa);
    if (!activa) return;
    const w = onda.clientWidth, h = onda.clientHeight;
    if (onda.width !== Math.round(w * DPR)) { onda.width = Math.round(w * DPR); onda.height = Math.round(h * DPR); }
    gOnda.setTransform(DPR, 0, 0, DPR, 0, 0);
    gOnda.clearRect(0, 0, w, h);
    const corte = (parseFloat($("cabezal").style.left) || 0) / 100 * w;
    const pasos = Math.max(24, Math.floor(w / 3)), mitad = 15;
    let pico = 0.05;
    for (let i = 0; i < muestras.length; i += 4) pico = Math.max(pico, Math.abs(muestras[i]));
    const escala = (9 * Math.min(1, nivel * 4)) / pico; // la onda llena la barra aunque la musica este bajita
    for (const [color, desde, hasta] of [[colorTexto, 0, corte], [colorTenue, corte, w]]) {
      if (hasta - desde < 1) continue;
      gOnda.strokeStyle = color;
      gOnda.lineWidth = 2;
      gOnda.lineJoin = "round";
      gOnda.beginPath();
      let empezo = false;
      for (let i = 0; i <= pasos; i++) {
        const x = (i / pasos) * w;
        if (x < desde - 3 || x > hasta + 3) continue;
        const m = muestras[Math.floor((i / pasos) * (muestras.length - 1))] || 0;
        const borde = Math.sin((i / pasos) * Math.PI); // quieta en las puntas, viva en el centro
        const y = mitad + limitar(m * escala * borde, -12, 12);
        const xc = limitar(x, desde, hasta);
        if (!empezo) { gOnda.moveTo(xc, y); empezo = true; } else gOnda.lineTo(xc, y);
      }
      gOnda.stroke();
    }
  }

  // ── Las caratulas se inclinan hacia el puntero ────────────────────────────
  if (!reducido) {
    document.querySelectorAll(".disco").forEach((b) => {
      const caratula = b.querySelector(".caratula");
      b.addEventListener("pointermove", (e) => {
        if (e.pointerType === "touch") return;
        const r = b.querySelector(".funda").getBoundingClientRect();
        const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
        caratula.style.setProperty("--ry", (x * 16).toFixed(1) + "deg");
        caratula.style.setProperty("--rx", (-y * 12).toFixed(1) + "deg");
      });
      b.addEventListener("pointerleave", () => { caratula.style.removeProperty("--rx"); caratula.style.removeProperty("--ry"); });
    });
  }

  // ── Lo que entra al bajar, en orden ───────────────────────────────────────
  if (!reducido && "IntersectionObserver" in window) {
    const revelar = new IntersectionObserver((es) => es.forEach((e) => {
      if (e.isIntersecting) { e.target.classList.add("visto"); revelar.unobserve(e.target); }
    }), { rootMargin: "0px 0px -8% 0px" });
    document.querySelectorAll(".funcion, .paso, .novedades li, .preguntas details, .aviso, .carro-accion").forEach((el) => {
      el.classList.add("revela");
      el.style.setProperty("--i", [...el.parentElement.children].indexOf(el) % 6);
      revelar.observe(el);
    });
  }

  // Un solo bucle de animacion, que se detiene si no hay nada que mover.
  let raf = 0, quietoDesde = 0, tAnterior = 0, ultNotaHabla = 0;
  function cuadro(t) {
    raf = 0;
    const dt = Math.min(0.05, (t - (tAnterior || t)) / 1000);
    tAnterior = t;
    if (analizador && ac && ac.state === "running") {
      analizador.getFloatTimeDomainData(muestras);
      let suma = 0;
      for (let i = 0; i < muestras.length; i++) suma += muestras[i] * muestras[i];
      nivel = nivel * 0.8 + Math.min(1, Math.sqrt(suma / muestras.length) * 3.2) * 0.2;
      while (golpes.length && golpes[0] <= ac.currentTime) { golpes.shift(); if (sonando) alGolpe(); }
    } else nivel *= 0.9;
    if (!sonando && palabraGolpe !== -1) { palabraGolpe = -1; marcarPalabra(-1); }
    apuntarMiradas(velScroll);
    const visibles = [];
    if (enEscena || novaGrande.estado !== "escucha") visibles.push(novaGrande);
    if (!enEscena) visibles.push(novaChica);
    for (const n of visibles) n.dibujar(t, nivel);
    // Mientras habla, a Nova se le escapa una nota de vez en cuando.
    const habla = enEscena ? novaGrande : novaChica;
    if (habla.estado === "habla" && t - ultNotaHabla > 520) { ultNotaHabla = t; soltarNota(lienzos[enEscena ? 0 : 1]); }
    lienzos.forEach((L) => moverNotas(L, dt));
    dibujarOnda();
    // Con la pestana oculta, nada; si todo esta quieto y no hay musica, se duerme hasta el proximo evento.
    const movido = sonando || novas.some((n) => n.estado !== "escucha") || puntero || lienzos.some((L) => L.notas.length);
    if (movido) quietoDesde = t;
    if (!document.hidden && (t - quietoDesde < 8000 || !reducido)) raf = requestAnimationFrame(cuadro);
  }
  function arrancar() { if (!raf && !document.hidden) raf = requestAnimationFrame(cuadro); }
  document.addEventListener("visibilitychange", arrancar);
  addEventListener("pointermove", arrancar, { passive: true });

  // ── Datos en vivo ──────────────────────────────────────────────────────────
  const fmtNum = (n) => n.toLocaleString("es-CO");
  const megas = (b) => Math.round(b / 1048576) + " MB";
  function ponerHora() {
    try { $("hora").textContent = new Date().toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit", timeZone: "America/Bogota" }); }
    catch { $("hora").textContent = new Date().toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" }); }
  }
  ponerHora();
  setInterval(ponerHora, 20000);
  fetch(RELEVO_TV + "/salud", { cache: "no-store" })
    .then((r) => (r.ok ? r.json() : null))
    .then((j) => !!j && j.estado === "ok")
    .catch(() => false)
    .then((ok) => { $("estado-tv").textContent = ok ? "El servidor del TV está en línea ahora mismo." : "El servidor del TV no responde en este momento. Prueba en un rato."; });

  // ── El contador de descargas ──────────────────────────────────────────────
  // Decision de CG (22-10): la cuenta arranca en 7.000 y a eso se le suman las descargas reales de GitHub
  // (7.000 + 490 = 7.490, y de ahi sube con cada descarga). La pagina la vuelve a pedir cada 10 minutos.
  const BASE_DESCARGAS = 7000;
  let descargasReales = null;
  const odometros = [...document.querySelectorAll("[data-odometro]")];
  function odometro(el, n, animar) {
    const txt = fmtNum(n), forma = txt.replace(/\d/g, "0");
    if (el.dataset.forma !== forma) {
      el.textContent = "";
      for (const ch of txt) {
        if (/\d/.test(ch)) {
          const dig = document.createElement("span"), tira = document.createElement("span");
          dig.className = "dig"; tira.className = "tira";
          for (let k = 0; k < 10; k++) { const s = document.createElement("span"); s.textContent = k; tira.appendChild(s); }
          dig.appendChild(tira); el.appendChild(dig);
        } else { const s = document.createElement("span"); s.textContent = ch; el.appendChild(s); }
      }
      el.dataset.forma = forma;
    }
    el.classList.toggle("quieto", !animar);
    const tiras = el.querySelectorAll(".tira"), digitos = txt.replace(/\D/g, "");
    tiras.forEach((t, k) => {
      t.style.transform = "translateY(" + (-digitos[k] * 10) + "%)";
      t.style.transitionDelay = ((tiras.length - 1 - k) * 70) + "ms"; // como un contador: las unidades primero
    });
  }
  function ponerCuenta(animar) {
    const n = BASE_DESCARGAS + (descargasReales || 0);
    odometros.forEach((el) => odometro(el, n, animar && !reducido));
    document.querySelectorAll("[data-cuenta-lector]").forEach((el) => {
      el.textContent = descargasReales == null ? "Más de 7.000 descargas" : fmtNum(n) + " descargas";
    });
  }
  ponerCuenta(false);

  // Version, pesos, descargas y el carro, de la API publica de GitHub (60 consultas por hora sin clave): 10 min de cache.
  const CACHE = "lm-github-2", CACHE_MS = 600000;
  function leerCache(cualquiera) {
    try { const c = JSON.parse(localStorage.getItem(CACHE) || "null"); return c && (cualquiera || Date.now() - c.hora < CACHE_MS) ? c.datos : null; } catch { return null; }
  }
  function guardarCache(datos, hora) { try { localStorage.setItem(CACHE, JSON.stringify({ hora: hora || Date.now(), datos })); } catch {} }
  function ponerGitHub(d, animar) {
    if (!d) return;
    if (typeof d.total === "number" && d.total !== descargasReales) {
      const subio = descargasReales != null && d.total > descargasReales;
      descargasReales = d.total;
      ponerCuenta(animar);
      if (subio && sonando === false && enEscena === false) decir("Alguien acaba de bajar Life Music. Ya van " + fmtNum(BASE_DESCARGAS + d.total) + ".", { pensar: false });
    }
    if (d.version) {
      const peso = d.peso ? ", " + megas(d.peso) : "";
      $("hechos").textContent = "Gratis y sin anuncios. Versión " + d.version + " para Android 8 o superior" + peso + ".";
      $("titulo-version").textContent = "Life Music " + d.version;
      $("detalle-version").textContent = "Para Android 8 o superior" + peso + ". Gratis, sin anuncios.";
      if (d.pesoLigera) $("enlace-ligera").textContent = "¿Teléfono con poco espacio? Versión ligera para arm64, " + megas(d.pesoLigera);
    }
    ponerCarro(d.carro);
  }
  // El boton del carro se enciende solo cuando GitHub tenga una version estable o una beta (las «prueba» no cuentan).
  function ponerCarro(c) {
    const b = $("boton-carro");
    if (!c || !c.url) return;
    b.href = c.url;
    b.classList.remove("apagado");
    b.lastChild.textContent = "Descargar Life Music for Car " + c.version;
    $("estado-carro").textContent = /beta/i.test(c.version)
      ? "Es una beta: puede tener detalles. Para radios Android 8 o superior."
      : "Para radios Android 8 o superior.";
  }
  function pedirGitHub() {
    fetch(RELEASES, { headers: { Accept: "application/vnd.github+json" } })
      .then((r) => (r.ok ? r.json() : null))
      .then((lista) => {
        if (!Array.isArray(lista)) return;
        const total = lista.reduce((s, r) => s + (r.assets || []).reduce((a, x) => a + (x.download_count || 0), 0), 0);
        const masNueva = (a, b) => Date.parse(b.published_at) - Date.parse(a.published_at);
        // La oficial: la ultima publicada que no es prueba ni del carro.
        const oficial = lista.filter((r) => !r.draft && !r.prerelease && /^v\d/.test(r.tag_name)).sort(masNueva)[0];
        const asset = (nombre) => oficial && (oficial.assets || []).find((x) => x.name === nombre);
        const carro = lista.filter((r) => !r.draft && /^carro-v\d+\.\d+\.\d+(-beta(\.\d+)?)?$/i.test(r.tag_name)).sort(masNueva)[0];
        const apkCarro = carro && (carro.assets || []).find((x) => /\.apk$/i.test(x.name));
        const datos = {
          total,
          version: oficial ? oficial.tag_name.replace(/^v/, "") : null,
          peso: asset("lifemusic.apk") ? asset("lifemusic.apk").size : 0,
          pesoLigera: asset("lifemusic-arm64.apk") ? asset("lifemusic-arm64.apk").size : 0,
          carro: carro ? { version: carro.tag_name.replace(/^carro-v/i, ""), url: apkCarro ? apkCarro.browser_download_url : carro.html_url } : null,
        };
        guardarCache(datos);
        ponerGitHub(datos, true);
      })
      .catch(() => {});
  }
  // Primero lo guardado (aunque sea viejo, para no mostrar «7.000» pelado) y, si ya vencio, se pide de nuevo.
  const guardado = leerCache(true);
  if (guardado) setTimeout(() => ponerGitHub(guardado, true), reducido ? 0 : 700);
  if (!leerCache(false)) pedirGitHub();
  setInterval(() => { if (!document.hidden) pedirGitHub(); }, CACHE_MS);

  // Cada descarga desde esta pagina suma una de una vez (GitHub la cuenta igual al rato).
  function sumarDescarga() {
    if (descargasReales == null) return;
    descargasReales += 1;
    ponerCuenta(true);
    const c = leerCache(true);
    if (c) { c.total = descargasReales; guardarCache(c, Date.now() - CACHE_MS + 120000); }
    decir("¡Una más! Ya van " + fmtNum(BASE_DESCARGAS + descargasReales) + ". Si el teléfono pregunta, dale permitir.", { pensar: false });
  }
  // En Android el boton de arriba descarga de una; en el computador lleva a la tarjeta, con el QR para el telefono.
  const esAndroid = /Android/i.test(navigator.userAgent);
  if (esAndroid) document.querySelectorAll('a.descargar[href="#coro"]').forEach((a) => { a.href = "/apk"; });
  document.querySelectorAll("a.descargar, #enlace-ligera").forEach((a) => a.addEventListener("click", () => {
    if (/\/apk(-arm64)?$/.test(a.getAttribute("href"))) sumarDescarga();
  }));
  const esComputador = !esAndroid && !/iPhone|iPad|iPod/i.test(navigator.userAgent) && window.matchMedia && matchMedia("(hover: hover) and (pointer: fine)").matches;
  if (esComputador) {
    const s = document.createElement("script");
    s.src = "/cast/qrcode.js";
    s.onload = () => {
      const lienzo = $("qr-descarga");
      if (!window.qrcode) return;
      const qr = window.qrcode(0, "M");
      qr.addData("https://lifemusic.app/apk");
      qr.make();
      const n = qr.getModuleCount(), margen = 2, celda = Math.floor(lienzo.width / (n + margen * 2));
      const g = lienzo.getContext("2d"), ancho = celda * (n + margen * 2), desde = Math.floor((lienzo.width - ancho) / 2);
      g.fillStyle = "#FFFFFF"; g.fillRect(0, 0, lienzo.width, lienzo.height);
      g.fillStyle = "#111111";
      for (let f = 0; f < n; f++) for (let k = 0; k < n; k++) if (qr.isDark(f, k)) g.fillRect(desde + (k + margen) * celda, desde + (f + margen) * celda, celda, celda);
      $("al-telefono").hidden = false;
      medir();
    };
    document.head.appendChild(s);
  }

  // ── Calificar sin salir de la pagina (pedido de CG, 22-10) ────────────────
  // El mismo servidor y las mismas reglas que /calificar: sale en la web solo si CG la aprueba.
  {
    const local = /^(localhost|127\.0\.0\.1)$/.test(location.hostname);
    const pedida = new URLSearchParams(location.search).get("api");
    const COMUNIDAD = local && pedida && /^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(pedida) ? pedida : "https://lifemusic-comunidad.cho--usic.workers.dev";
    const DICE = ["Elige de una a cinco notas.", "No me gustó.", "Le falta.", "Está bien.", "Me gusta.", "¡Me encanta!"];
    const NOVA_DICE = [
      "",
      "Uy. Cuéntame qué falló y CG lo arregla.",
      "Le falta, entendido. ¿Qué le pondrías?",
      "Bien, pero puede ser mejor. ¿Qué le cambiarías?",
      "¡Qué bueno! ¿Qué es lo que más te gusta?",
      "¡Gracias! Eso me pone feliz.",
    ];
    const ERRORES = {
      nombre: "Escribe tu nombre (dos letras o más).", rapido: "Espera un momentico y vuelve a enviarla.",
      muchas: "Ya enviaste varias en la última hora. Prueba más tarde.", comentario: "El comentario es muy largo o tiene caracteres raros.",
    };
    const labels = [...document.querySelectorAll("#notas-rapido label")];
    const radios = [...document.querySelectorAll("#notas-rapido input")];
    const resultado = $("resultado-rapido"), dicho = $("dice-rapido");
    const abierta = Date.now();
    let notas = 0;
    const encender = (n) => { labels.forEach((l, i) => l.classList.toggle("encendida", i < n)); dicho.textContent = DICE[n] || DICE[0]; };
    radios.forEach((r, i) => {
      r.addEventListener("change", () => {
        notas = i + 1;
        encender(notas);
        if (!reducido) labels.slice(0, notas).forEach((l, k) => setTimeout(() => { l.classList.add("salta"); setTimeout(() => l.classList.remove("salta"), 500); }, k * 60));
        decir(NOVA_DICE[notas], { pensar: false });
        const L = lienzos[enEscena ? 0 : 1];
        if (notas >= 4 && L) soltarNota(L, notas === 5 ? 5 : 2);
      });
      labels[i].addEventListener("pointerenter", (e) => { if (e.pointerType !== "touch") encender(i + 1); });
    });
    $("notas-rapido").addEventListener("pointerleave", () => encender(notas));

    // El promedio, arriba y en el bloque. Decision de CG (22-10): nunca se muestra por debajo de 4,2.
    const PISO = 4.2;
    function pintarNota(r) {
      const prom = Math.max(PISO, (r && r.promedio) || 0), total = (r && r.total) || 0;
      const texto = prom.toFixed(1).replace(".", ",");
      const votos = total ? " · " + fmtNum(total) + (total === 1 ? " calificación" : " calificaciones") : "";
      let svgs = "";
      for (let k = 1; k <= 5; k++) svgs += '<svg viewBox="0 0 20 16"' + (k <= Math.round(prom) ? ' class="llena"' : "") + '><ellipse cx="10" cy="8" rx="7.5" ry="5.5" transform="rotate(-22 10 8)"/></svg>';
      for (const sufijo of ["arriba", "bloque"]) {
        $("promedio-" + sufijo).textContent = texto;
        $("votos-" + sufijo).textContent = votos;
        $("notas-" + sufijo).innerHTML = svgs; // svg fijo, sin datos de afuera
      }
    }
    pintarNota(null);
    fetch(COMUNIDAD + "/calificaciones/resumen").then((r) => (r.ok ? r.json() : null)).then(pintarNota).catch(() => {});

    // El enlace para compartir: lifemusic.app/califica (o #califica) abre la pagina directo en las notas.
    if (/^\/califica\/?$/.test(location.pathname) || location.hash === "#califica") {
      yaHablo = true; // sin el saludo de siempre: Nova invita a calificar
      if ("scrollRestoration" in history) history.scrollRestoration = "manual"; // que el navegador no la devuelva a donde iba
      addEventListener("load", () => setTimeout(() => {
        $("califica").scrollIntoView({ behavior: reducido ? "auto" : "smooth", block: "center" });
        setTimeout(() => decir("¡Hola! Te pidieron calificar Life Music: toca de una a cinco notas.", { pensar: false }), reducido ? 0 : 700);
      }, 120));
    }
    const avisar = (t, mal) => { resultado.textContent = t; resultado.classList.toggle("mal", !!mal); };
    $("form-rapido").addEventListener("submit", async (e) => {
      e.preventDefault();
      const nombre = $("nombre-rapido");
      nombre.removeAttribute("aria-invalid");
      if (!notas) { avisar("Primero elige de una a cinco notas.", true); radios[0].focus(); return; }
      if (nombre.value.trim().length < 2) { nombre.setAttribute("aria-invalid", "true"); avisar(ERRORES.nombre, true); nombre.focus(); return; }
      const boton = $("enviar-rapido");
      boton.disabled = true;
      avisar("Enviando…");
      try {
        const r = await fetch(COMUNIDAD + "/resenas", {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ notas, nombre: nombre.value.trim(), correo: "", comentario: $("comentario-rapido").value.trim(), espera: Date.now() - abierta, sitio: $("sitio-rapido").value }),
        });
        const j = await r.json().catch(() => ({}));
        if (!r.ok || !j.ok) {
          if (j.error === "nombre") nombre.setAttribute("aria-invalid", "true");
          avisar(ERRORES[j.error] || "No se pudo enviar. Prueba otra vez en un rato.", true);
          boton.disabled = false;
          return;
        }
        const quien = nombre.value.trim().split(/\s+/)[0];
        $("form-rapido").hidden = true;
        const gracias = document.createElement("div");
        gracias.className = "gracias";
        gracias.setAttribute("role", "status");
        gracias.tabIndex = -1;
        const h = document.createElement("h3"), p = document.createElement("p"), a = document.createElement("a");
        h.textContent = "¡Gracias, " + quien + "!";
        p.textContent = "Tu calificación ya llegó. CG la lee y, si la aprueba, sale en la página de calificaciones.";
        a.href = "/calificar/"; a.textContent = "Ver lo que dicen";
        gracias.append(h, p, a);
        $("form-rapido").after(gracias);
        gracias.focus();
        decir("¡Gracias, " + quien + "! Se la paso a CG ahora mismo.", { pensar: false });
        const L = lienzos[enEscena ? 0 : 1];
        if (L) soltarNota(L, 6);
      } catch {
        avisar("No se pudo enviar. Revisa tu conexión y prueba otra vez.", true);
        boton.disabled = false;
      }
    });
  }

  // ── Arranque: Nova aparece pequena y se expande ──────────────────────────
  pintarPulso();
  medir();
  recorrer();
  const grande = $("nova-grande");
  // El saludo solo si la persona no ha hecho nada todavia (si recargo a mitad de pagina, Nova ya conto esa parte).
  const saludar = (opciones) => { if (!yaHablo) decir(PARTES.Intro.dice, opciones); };
  if (reducido) {
    grande.classList.add("lista");
    novaGrande.estado = "escucha";
    setTimeout(() => saludar({ pensar: false }), 400);
  } else {
    requestAnimationFrame(() => {
      grande.classList.add("llega");
      requestAnimationFrame(() => grande.classList.add("lista"));
    });
    setTimeout(() => { if (novaGrande.estado === "apagada") novaGrande.estado = "escucha"; }, 350);
    setTimeout(() => saludar(), 1300);
    cantarTitular();
  }
  arrancar();
  addEventListener("load", () => { medir(); recorrer(); });
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { medir(); recorrer(); });
})();
