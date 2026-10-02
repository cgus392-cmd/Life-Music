/* Life Music Web.

   Busca en YouTube Music por medio de /api (funciones de Cloudflare Pages) y
   hace sonar cada canción en el reproductor oficial de YouTube incrustado,
   visible en el escenario. Cola, radio, «Me gusta» y la última sesión viven
   en este navegador (localStorage); no se manda nada a ningún otro lado. */

// ── Textos ──────────────────────────────────────────────────────────────────
const TEXTOS = {
  es: {
    toca: "Toca el video para empezar",
    no_embebible: "«%s» solo se puede escuchar en YouTube. Sigue la próxima.",
    no_suena: "Esta canción no se pudo cargar. Sigue la próxima.",
    demasiados_saltos: "Varias canciones seguidas no se pudieron reproducir. Prueba otra búsqueda.",
    sin_conexion: "No hay conexión con Life Music. Revisa el internet e intenta otra vez.",
    nada: "No encontré nada para «%s».",
    siguiente_en_cola: "Suena después de la actual",
    agregada: "Agregada al final de la cola",
    enlace_copiado: "Enlace copiado",
    menu_siguiente: "Reproducir a continuación",
    menu_cola: "Agregar a la cola",
    menu_radio: "Radio a partir de esta canción",
    menu_compartir: "Compartir",
    menu_quitar: "Quitar de la cola",
    menu_quitar_gusto: "Quitar de Me gusta",
    menu_gusta: "Guardar en Me gusta",
    mas_opciones: "Más opciones",
    me_gusta_si: "Guardada en Me gusta",
    me_gusta_no: "Quitada de Me gusta",
    explicita: "Explícita",
    pausar: "Pausar",
    reproducir: "Reproducir",
  },
  en: {
    instalar: "Install",
    toca: "Tap the video to start",
    me_gusta: "Liked",
    posicion: "Position",
    anterior: "Previous",
    reproducir: "Play",
    pausar: "Pause",
    siguiente: "Next",
    despierta: "Keep the screen on while music plays",
    buscar: "Search songs and artists",
    borrar: "Clear",
    resultados: "Results",
    cola: "Up next",
    hola: "Your music, now in the browser too.",
    hola_texto: "Search for a song and Life Music builds a radio so the music keeps going.",
    ios_titulo: "On iPhone or iPad?",
    ios_texto: 'Tap <span class="glifo">Share</span> and then <b>Add to Home Screen</b> to keep it as an app.',
    android_titulo: "On Android?",
    android_texto: "The full app plays with the screen off, downloads music and has an equalizer.",
    android_boton: "Get the app",
    limites_titulo: "What the web version can't do yet",
    limite_1: "On iPhone the music pauses when you lock the screen or leave the app. That's Apple's rule for web apps.",
    limite_2: "Some songs can only be played on YouTube. Life Music skips them for you.",
    limite_3: "YouTube ads may appear: music plays in YouTube's official player.",
    limite_4: "No downloads or equalizer.",
    mas: "Show more",
    cola_vacia: "Play a song and what comes next shows up here.",
    gustos_vacio: "Tap the heart while a song plays and it's saved here, on this device.",
    pie: "Made by",
    pie_yt: "Music plays in YouTube's official player. Life Music doesn't store or relay audio.",
    no_embebible: "“%s” can only be played on YouTube. Moving on.",
    no_suena: "This song couldn't load. Moving on.",
    demasiados_saltos: "Several songs in a row couldn't play. Try another search.",
    sin_conexion: "Can't reach Life Music. Check your internet and try again.",
    nada: "Nothing found for “%s”.",
    siguiente_en_cola: "Plays after the current song",
    agregada: "Added to the end of the queue",
    enlace_copiado: "Link copied",
    menu_siguiente: "Play next",
    menu_cola: "Add to queue",
    menu_radio: "Start radio from this song",
    menu_compartir: "Share",
    menu_quitar: "Remove from queue",
    menu_quitar_gusto: "Remove from Liked",
    menu_gusta: "Save to Liked",
    mas_opciones: "More options",
    me_gusta_si: "Saved to Liked",
    me_gusta_no: "Removed from Liked",
    explicita: "Explicit",
  },
};
const IDIOMA = (navigator.language || "es").toLowerCase().startsWith("es") ? "es" : "en";
const t = (k, ...args) => {
  let s = TEXTOS[IDIOMA][k] ?? TEXTOS.es[k] ?? k;
  for (const a of args) s = s.replace("%s", a);
  return s;
};
function traducir() {
  document.documentElement.lang = IDIOMA;
  if (IDIOMA === "es") return; // el HTML ya viene en español
  const en = TEXTOS.en;
  document.querySelectorAll("[data-t]").forEach((n) => en[n.dataset.t] && (n.textContent = en[n.dataset.t]));
  document.querySelectorAll("[data-t-html]").forEach((n) => en[n.dataset.tHtml] && (n.innerHTML = en[n.dataset.tHtml]));
  document.querySelectorAll("[data-t-aria]").forEach((n) => en[n.dataset.tAria] && n.setAttribute("aria-label", en[n.dataset.tAria]));
  document.querySelectorAll("[data-t-ph]").forEach((n) => en[n.dataset.tPh] && (n.placeholder = en[n.dataset.tPh]));
}

// ── Utilidades ──────────────────────────────────────────────────────────────
const $ = (id) => document.getElementById(id);
function el(etiqueta, attrs = {}, ...hijos) {
  const n = document.createElement(etiqueta);
  for (const [k, v] of Object.entries(attrs)) {
    if (v == null || v === false) continue;
    if (k === "class") n.className = v;
    else if (k.startsWith("on")) n.addEventListener(k.slice(2), v);
    else n.setAttribute(k, v === true ? "" : v);
  }
  for (const h of hijos.flat()) if (h != null && h !== false) n.append(h);
  return n;
}
const leer = (clave, defecto) => {
  try {
    const v = localStorage.getItem(clave);
    return v ? JSON.parse(v) : defecto;
  } catch {
    return defecto;
  }
};
const guardarEn = (clave, valor) => {
  try {
    localStorage.setItem(clave, JSON.stringify(valor));
  } catch {}
};
const mmss = (s) => {
  if (!isFinite(s) || s < 0) s = 0;
  s = Math.floor(s);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const ss = String(s % 60).padStart(2, "0");
  return h ? `${h}:${String(m).padStart(2, "0")}:${ss}` : `${m}:${ss}`;
};
/** La carátula pequeña para las listas (la grande pesa ~40 KB). */
const caratulaChica = (url) => (url || "").replace(/=w\d+-h\d+/, "=w120-h120");

let temporizadorAviso;
function aviso(texto) {
  const n = $("toast");
  n.textContent = texto;
  n.hidden = false;
  clearTimeout(temporizadorAviso);
  temporizadorAviso = setTimeout(() => (n.hidden = true), 3200);
}

async function api(ruta, senal) {
  const r = await fetch(ruta, { signal: senal });
  if (!r.ok) throw new Error((await r.json().catch(() => ({}))).error || r.status);
  return r.json();
}

// ── Estado ──────────────────────────────────────────────────────────────────
const sesion = leer("lm.sesion", null);
const estado = {
  cola: sesion?.cola || [],
  indice: sesion?.indice ?? -1,
  radioMas: sesion?.radioMas || null,
  sonando: false,
  arrancoAlgunaVez: false,
  saltosSeguidos: 0,
  gustos: leer("lm.gustos", []),
  resultados: [],
  resultadosMas: null,
  pidiendoRadio: null,
};
const actual = () => estado.cola[estado.indice] || null;
const guardarSesion = () =>
  guardarEn("lm.sesion", { cola: estado.cola.slice(0, 200), indice: estado.indice, radioMas: estado.radioMas });

// ── Reproductor de YouTube ──────────────────────────────────────────────────
let reproductor = null;
let reproductorListo = false;
let pendiente = null; // { id, sonar }
let apiYouTube = null;

function cargarApiYouTube() {
  if (apiYouTube) return apiYouTube;
  apiYouTube = new Promise((listo) => {
    if (window.YT?.Player) return listo();
    window.onYouTubeIframeAPIReady = listo;
    document.head.append(el("script", { src: "https://www.youtube.com/iframe_api", async: true }));
  });
  return apiYouTube;
}

async function asegurarReproductor() {
  if (reproductor) return;
  await cargarApiYouTube();
  if (reproductor) return;
  reproductor = new YT.Player("yt", {
    host: "https://www.youtube-nocookie.com",
    width: "100%",
    height: "100%",
    playerVars: {
      playsinline: 1,
      controls: 0,
      disablekb: 1,
      fs: 0,
      rel: 0,
      iv_load_policy: 3,
      origin: location.origin,
    },
    events: {
      onReady: () => {
        reproductorListo = true;
        if (pendiente) cargarEnReproductor(pendiente.id, pendiente.sonar);
      },
      onStateChange: alCambiarEstado,
      onError: alFallar,
    },
  });
}

let vigiaArranque;
function cargarEnReproductor(id, sonar) {
  if (!reproductorListo) {
    pendiente = { id, sonar };
    return;
  }
  pendiente = null;
  if (sonar) {
    reproductor.loadVideoById(id);
    // En iPhone el primer video no arranca solo: hay que tocarlo dentro del reproductor.
    clearTimeout(vigiaArranque);
    vigiaArranque = setTimeout(() => {
      const s = reproductor.getPlayerState?.();
      if (!estado.arrancoAlgunaVez && s !== YT.PlayerState.PLAYING && s !== YT.PlayerState.BUFFERING) $("toca").hidden = false;
    }, 2500);
  } else {
    reproductor.cueVideoById(id);
  }
}

function alCambiarEstado({ data }) {
  const E = YT.PlayerState;
  if (data === E.PLAYING) {
    estado.arrancoAlgunaVez = true;
    estado.saltosSeguidos = 0;
    $("toca").hidden = true;
    ponerSonando(true);
  } else if (data === E.PAUSED || data === E.CUED) {
    ponerSonando(false);
  } else if (data === E.ENDED) {
    ponerSonando(false);
    siguiente();
  }
}

function alFallar({ data }) {
  const c = actual();
  estado.saltosSeguidos++;
  if (c) c.saltada = true;
  pintarCola();
  if (estado.saltosSeguidos >= 6) {
    ponerSonando(false);
    aviso(t("demasiados_saltos"));
    return;
  }
  // 101 y 150: el dueño no deja reproducirla fuera de YouTube.
  aviso(data === 101 || data === 150 ? t("no_embebible", c?.titulo || "") : t("no_suena"));
  siguiente();
}

function ponerSonando(si) {
  estado.sonando = si;
  document.body.classList.toggle("sonando", si);
  document.body.classList.toggle("pausado", !si);
  const etiqueta = si ? t("pausar") : t("reproducir");
  $("btnReproducir").setAttribute("aria-label", etiqueta);
  $("miniReproducir").setAttribute("aria-label", etiqueta);
  if ("mediaSession" in navigator) navigator.mediaSession.playbackState = si ? "playing" : "paused";
  despertar();
}

// ── Cola y radio ────────────────────────────────────────────────────────────
function reproducirIndice(i, sonar = true) {
  if (i < 0 || i >= estado.cola.length) return;
  estado.indice = i;
  const c = actual();
  mostrarCancion(c);
  asegurarReproductor().then(() => cargarEnReproductor(c.id, sonar));
  guardarSesion();
  pintarCola();
  pintarListas();
  if (estado.cola.length - estado.indice <= 4) pedirMasRadio();
}

/** Toca una canción suelta: suena ya y detrás llega su radio. */
async function radioDe(c) {
  estado.cola = [{ ...c, saltada: false }];
  estado.radioMas = null;
  reproducirIndice(0);
  await pedirRadio(c.id);
}

async function pedirRadio(id) {
  const pedido = (estado.pidiendoRadio = Symbol());
  try {
    const r = await api(`/api/radio?v=${encodeURIComponent(id)}`);
    if (estado.pidiendoRadio !== pedido) return;
    agregarSinRepetir(r.canciones);
    estado.radioMas = r.mas;
  } catch {
    // Sin radio igual se puede seguir escuchando lo que hay.
  } finally {
    if (estado.pidiendoRadio === pedido) estado.pidiendoRadio = null;
  }
  guardarSesion();
  pintarCola();
}

async function pedirMasRadio() {
  if (estado.pidiendoRadio) return;
  if (!estado.radioMas) {
    const ultima = estado.cola[estado.cola.length - 1];
    if (ultima) await pedirRadio(ultima.id);
    return;
  }
  const pedido = (estado.pidiendoRadio = Symbol());
  try {
    const r = await api(`/api/radio?mas=${encodeURIComponent(estado.radioMas)}`);
    agregarSinRepetir(r.canciones);
    estado.radioMas = r.mas;
  } catch {
  } finally {
    if (estado.pidiendoRadio === pedido) estado.pidiendoRadio = null;
  }
  guardarSesion();
  pintarCola();
}

function agregarSinRepetir(canciones) {
  const ya = new Set(estado.cola.map((c) => c.id));
  for (const c of canciones || []) if (!ya.has(c.id)) (ya.add(c.id), estado.cola.push(c));
}

async function siguiente() {
  if (estado.indice < estado.cola.length - 1) return reproducirIndice(estado.indice + 1);
  await pedirMasRadio();
  if (estado.indice < estado.cola.length - 1) reproducirIndice(estado.indice + 1);
}

function anterior() {
  if (reproductorListo && reproductor.getCurrentTime() > 4) return reproductor.seekTo(0, true);
  if (estado.indice > 0) reproducirIndice(estado.indice - 1);
}

function alternar() {
  if (!reproductorListo) return;
  if (estado.sonando) reproductor.pauseVideo();
  else reproductor.playVideo();
}

function despuesDeLaActual(c) {
  const i = estado.cola.findIndex((x) => x.id === c.id);
  if (i >= 0 && i !== estado.indice) {
    estado.cola.splice(i, 1);
    if (i < estado.indice) estado.indice--;
  }
  if (estado.indice < 0) return radioDe(c);
  estado.cola.splice(estado.indice + 1, 0, { ...c, saltada: false });
  aviso(t("siguiente_en_cola"));
  guardarSesion();
  pintarCola();
}

function alFinal(c) {
  if (estado.indice < 0) return radioDe(c);
  if (!estado.cola.some((x) => x.id === c.id)) estado.cola.push({ ...c, saltada: false });
  aviso(t("agregada"));
  guardarSesion();
  pintarCola();
}

async function compartir(c) {
  const url = `${location.origin}/?v=${c.id}`;
  try {
    if (navigator.share) return await navigator.share({ title: c.titulo, text: `${c.titulo} · ${c.artista}`, url });
  } catch {
    return;
  }
  try {
    await navigator.clipboard.writeText(url);
    aviso(t("enlace_copiado"));
  } catch {}
}

// ── Me gusta ────────────────────────────────────────────────────────────────
const leGusta = (id) => estado.gustos.some((g) => g.id === id);
function alternarGusto(c = actual()) {
  if (!c) return;
  if (leGusta(c.id)) {
    estado.gustos = estado.gustos.filter((g) => g.id !== c.id);
    aviso(t("me_gusta_no"));
  } else {
    const { saltada, ...limpia } = c;
    estado.gustos.unshift(limpia);
    aviso(t("me_gusta_si"));
  }
  guardarEn("lm.gustos", estado.gustos);
  pintarCorazon();
  pintarGustos();
}
function pintarCorazon() {
  const c = actual();
  $("btnMeGusta").classList.toggle("activo", !!c && leGusta(c.id));
}

// ── Pintar ──────────────────────────────────────────────────────────────────
let ambiente;
function mostrarCancion(c) {
  $("escenario").hidden = false;
  $("tTitulo").textContent = c.titulo;
  $("tArtista").textContent = [c.artista, c.album].filter(Boolean).join(" · ");
  $("miniTitulo").textContent = c.titulo;
  $("miniArtista").textContent = c.artista || "";
  $("miniCaratula").src = caratulaChica(c.caratula);
  document.title = `${c.titulo} · Life Music`;
  if (!ambiente) document.body.prepend((ambiente = el("div", { class: "ambiente", "aria-hidden": "true" })));
  if (c.caratula) {
    ambiente.style.backgroundImage = `url("${c.caratula}")`;
    ambiente.classList.add("vivo");
  }
  pintarCorazon();
  pintarProgreso(0, 0);
  if ("mediaSession" in navigator) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: c.titulo,
      artist: c.artista || "",
      album: c.album || "",
      artwork: c.caratula ? [{ src: c.caratula, sizes: "544x544", type: "image/jpeg" }] : [],
    });
  }
}

function filaDe(c, { esActual = false, alTocar, opciones }) {
  const datos = el(
    "div",
    { class: "datos" },
    el("b", {}, c.titulo),
    el("span", {}, c.explicita ? el("i", { class: "e", title: t("explicita") }, "E") : null, [c.artista, c.album].filter(Boolean).join(" · ")),
  );
  const menu = el(
    "button",
    {
      class: "icono menu",
      "aria-label": t("mas_opciones"),
      onclick: (ev) => {
        ev.stopPropagation();
        abrirMenu(ev.currentTarget, opciones);
      },
    },
  );
  menu.innerHTML = '<svg viewBox="0 0 24 24"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>';
  return el(
    "li",
    { class: `fila${esActual ? " actual" : ""}${c.saltada ? " saltada" : ""}`, onclick: alTocar },
    el("img", { src: caratulaChica(c.caratula), alt: "", loading: "lazy", width: 52, height: 52, referrerpolicy: "no-referrer" }),
    el("div", { class: "ecualizador", "aria-hidden": "true" }, el("i"), el("i"), el("i")),
    datos,
    c.duracion ? el("span", { class: "duracion" }, c.duracion) : null,
    menu,
  );
}

function opcionesComunes(c) {
  return [
    [t("menu_siguiente"), () => despuesDeLaActual(c)],
    [t("menu_cola"), () => alFinal(c)],
    [t("menu_radio"), () => radioDe(c)],
    [leGusta(c.id) ? t("menu_quitar_gusto") : t("menu_gusta"), () => alternarGusto(c)],
    [t("menu_compartir"), () => compartir(c)],
  ];
}

function pintarResultados() {
  const ul = $("resultados");
  const idActual = actual()?.id;
  ul.replaceChildren(
    ...estado.resultados.map((c) =>
      filaDe(c, { esActual: c.id === idActual, alTocar: () => radioDe(c), opciones: opcionesComunes(c) }),
    ),
  );
  $("btnMas").hidden = !estado.resultadosMas;
}

function pintarCola() {
  const desde = Math.max(0, estado.indice);
  const visibles = estado.cola.slice(desde);
  $("cola").replaceChildren(
    ...visibles.map((c, k) => {
      const i = desde + k;
      return filaDe(c, {
        esActual: i === estado.indice,
        alTocar: () => reproducirIndice(i),
        opciones: [
          ...opcionesComunes(c).slice(0, 1),
          [
            t("menu_quitar"),
            () => {
              const j = estado.cola.indexOf(c);
              if (j < 0 || j === estado.indice) return;
              estado.cola.splice(j, 1);
              if (j < estado.indice) estado.indice--;
              guardarSesion();
              pintarCola();
            },
          ],
          ...opcionesComunes(c).slice(2),
        ],
      });
    }),
  );
  const quedan = estado.cola.length - desde - 1;
  $("cuantas").textContent = quedan > 0 ? quedan : "";
  $("colaVacia").hidden = estado.cola.length > 0;
}

function pintarGustos() {
  const idActual = actual()?.id;
  $("gustos").replaceChildren(
    ...estado.gustos.map((c, i) =>
      filaDe(c, {
        esActual: c.id === idActual,
        alTocar: () => {
          estado.cola = estado.gustos.map((g) => ({ ...g }));
          estado.radioMas = null;
          reproducirIndice(i);
        },
        opciones: opcionesComunes(c),
      }),
    ),
  );
  $("gustosVacio").hidden = estado.gustos.length > 0;
}

function pintarListas() {
  pintarResultados();
  pintarGustos();
}

let arrastrando = false;
function pintarProgreso(ahora, total) {
  const p = total > 0 ? Math.min(1, ahora / total) : 0;
  if (!arrastrando) {
    $("barra").value = Math.round(p * 1000);
    $("barra").style.setProperty("--p", `${p * 100}%`);
    $("tAhora").textContent = mmss(ahora);
  }
  $("tTotal").textContent = mmss(total);
  $("miniBarra").style.width = `${p * 100}%`;
}

setInterval(() => {
  if (!reproductorListo || !reproductor.getDuration) return;
  const total = reproductor.getDuration() || 0;
  const ahora = reproductor.getCurrentTime() || 0;
  pintarProgreso(ahora, total);
  if ("mediaSession" in navigator && total > 0 && navigator.mediaSession.setPositionState) {
    try {
      navigator.mediaSession.setPositionState({ duration: total, position: Math.min(ahora, total), playbackRate: 1 });
    } catch {}
  }
}, 500);

// ── Menú de opciones de una fila ────────────────────────────────────────────
let menuAbierto = null;
function cerrarMenu() {
  menuAbierto?.remove();
  menuAbierto = null;
}
function abrirMenu(boton, opciones) {
  cerrarMenu();
  const m = el(
    "div",
    { class: "menu-flotante", role: "menu" },
    opciones.map(([texto, hacer]) =>
      el("button", { role: "menuitem", onclick: () => (cerrarMenu(), hacer()) }, texto),
    ),
  );
  document.body.append(m);
  const r = boton.getBoundingClientRect();
  const alto = m.offsetHeight;
  const ancho = m.offsetWidth;
  const arriba = r.bottom + 6 + alto > innerHeight - 12 ? r.top - alto - 6 : r.bottom + 6;
  m.style.top = `${Math.max(12, arriba)}px`;
  m.style.left = `${Math.max(12, Math.min(r.right - ancho, innerWidth - ancho - 12))}px`;
  menuAbierto = m;
}
document.addEventListener("click", (e) => menuAbierto && !menuAbierto.contains(e.target) && cerrarMenu(), true);
addEventListener("scroll", cerrarMenu, { passive: true });

// ── Búsqueda ────────────────────────────────────────────────────────────────
let busquedaEnCurso;
async function buscar(q) {
  q = q.trim();
  if (!q) return;
  ocultarSugerencias();
  $("q").blur();
  elegirPestana("Resultados");
  $("bienvenida").hidden = true;
  $("vacio").hidden = true;
  $("resultados").replaceChildren();
  $("btnMas").hidden = true;
  $("cargando").hidden = false;
  busquedaEnCurso?.abort();
  const control = (busquedaEnCurso = new AbortController());
  try {
    const r = await api(`/api/buscar?q=${encodeURIComponent(q)}`, control.signal);
    estado.resultados = r.canciones;
    estado.resultadosMas = r.mas;
    pintarResultados();
    if (!r.canciones.length) {
      $("vacio").textContent = t("nada", q);
      $("vacio").hidden = false;
    }
    history.replaceState(null, "", `/?q=${encodeURIComponent(q)}`);
  } catch (e) {
    if (e.name === "AbortError") return;
    $("vacio").textContent = t("sin_conexion");
    $("vacio").hidden = false;
  } finally {
    if (busquedaEnCurso === control) $("cargando").hidden = true;
  }
}

async function verMas() {
  if (!estado.resultadosMas) return;
  $("btnMas").hidden = true;
  $("cargando").hidden = false;
  try {
    const r = await api(`/api/buscar?mas=${encodeURIComponent(estado.resultadosMas)}`);
    const ya = new Set(estado.resultados.map((c) => c.id));
    estado.resultados.push(...r.canciones.filter((c) => !ya.has(c.id)));
    estado.resultadosMas = r.mas;
  } catch {
    aviso(t("sin_conexion"));
  }
  $("cargando").hidden = true;
  pintarResultados();
}

let sugerenciasPedidas;
let temporizadorSugerencias;
let marcada = -1;
function pedirSugerencias(q) {
  clearTimeout(temporizadorSugerencias);
  if (!q.trim()) return ocultarSugerencias();
  temporizadorSugerencias = setTimeout(async () => {
    sugerenciasPedidas?.abort();
    const control = (sugerenciasPedidas = new AbortController());
    try {
      const r = await api(`/api/sugerencias?q=${encodeURIComponent(q)}`, control.signal);
      if (document.activeElement !== $("q")) return;
      pintarSugerencias(q, r.sugerencias);
    } catch {}
  }, 180);
}
function pintarSugerencias(q, lista) {
  const ul = $("sugerencias");
  marcada = -1;
  if (!lista.length) return ocultarSugerencias();
  const base = q.trim().toLowerCase();
  ul.replaceChildren(
    ...lista.map((s) => {
      const empieza = s.toLowerCase().startsWith(base);
      const texto = empieza ? [s.slice(0, base.length), el("b", {}, s.slice(base.length))] : [s];
      const b = el("button", { type: "button", onmousedown: (e) => e.preventDefault(), onclick: () => elegirSugerencia(s) }, ...texto);
      return el("li", {}, b);
    }),
  );
  ul.hidden = false;
}
function ocultarSugerencias() {
  $("sugerencias").hidden = true;
  marcada = -1;
}
function elegirSugerencia(s) {
  $("q").value = s;
  $("btnBorrar").hidden = false;
  buscar(s);
}

// ── Pestañas ────────────────────────────────────────────────────────────────
function elegirPestana(nombre) {
  for (const n of ["Resultados", "Cola", "Gustos"]) {
    $(`tab${n}`).setAttribute("aria-selected", String(n === nombre));
    $(`panel${n}`).hidden = n !== nombre;
  }
}

// ── Pantalla encendida (Wake Lock) ──────────────────────────────────────────
let candado = null;
async function despertar() {
  const quiere = $("chkDespierta").checked && estado.sonando && document.visibilityState === "visible";
  try {
    if (quiere && !candado) {
      candado = await navigator.wakeLock.request("screen");
      candado.addEventListener("release", () => (candado = null));
    } else if (!quiere && candado) {
      await candado.release();
      candado = null;
    }
  } catch {
    candado = null;
  }
}

// ── Arranque ────────────────────────────────────────────────────────────────
function enlazar() {
  $("formBuscar").addEventListener("submit", (e) => {
    e.preventDefault();
    if (marcada >= 0) return $("sugerencias").querySelectorAll("button")[marcada]?.click();
    buscar($("q").value);
  });
  $("q").addEventListener("input", (e) => {
    $("btnBorrar").hidden = !e.target.value;
    pedirSugerencias(e.target.value);
  });
  $("q").addEventListener("keydown", (e) => {
    const botones = [...$("sugerencias").querySelectorAll("button")];
    if ($("sugerencias").hidden || !botones.length) return;
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      // -1 es «ninguna»: se recorre ninguna → 0 → … → última → ninguna.
      const n = botones.length + 1;
      marcada = ((marcada + 1 + (e.key === "ArrowDown" ? 1 : -1) + n) % n) - 1;
      botones.forEach((b, i) => b.classList.toggle("marcada", i === marcada));
    } else if (e.key === "Escape") ocultarSugerencias();
  });
  $("q").addEventListener("blur", () => setTimeout(ocultarSugerencias, 120));
  $("btnBorrar").addEventListener("click", () => {
    $("q").value = "";
    $("btnBorrar").hidden = true;
    ocultarSugerencias();
    $("q").focus();
  });
  $("btnMas").addEventListener("click", verMas);
  $("tabResultados").addEventListener("click", () => elegirPestana("Resultados"));
  $("tabCola").addEventListener("click", () => elegirPestana("Cola"));
  $("tabGustos").addEventListener("click", () => elegirPestana("Gustos"));

  $("btnReproducir").addEventListener("click", alternar);
  $("miniReproducir").addEventListener("click", alternar);
  $("btnSiguiente").addEventListener("click", siguiente);
  $("miniSiguiente").addEventListener("click", siguiente);
  $("btnAnterior").addEventListener("click", anterior);
  $("btnMeGusta").addEventListener("click", () => alternarGusto());
  $("miniIr").addEventListener("click", () => $("escenario").scrollIntoView({ behavior: "smooth", block: "center" }));

  const barra = $("barra");
  barra.addEventListener("input", () => {
    arrastrando = true;
    const total = reproductorListo ? reproductor.getDuration() : 0;
    barra.style.setProperty("--p", `${barra.value / 10}%`);
    $("tAhora").textContent = mmss((barra.value / 1000) * total);
  });
  barra.addEventListener("change", () => {
    arrastrando = false;
    if (reproductorListo) reproductor.seekTo((barra.value / 1000) * reproductor.getDuration(), true);
  });

  if ("wakeLock" in navigator) {
    $("chkDespierta").checked = leer("lm.despierta", false);
    $("chkDespierta").addEventListener("change", () => (guardarEn("lm.despierta", $("chkDespierta").checked), despertar()));
    document.addEventListener("visibilitychange", despertar);
  } else {
    $("chkDespierta").closest("label").hidden = true;
  }

  if ("mediaSession" in navigator) {
    const ms = navigator.mediaSession;
    const poner = (accion, hacer) => {
      try {
        ms.setActionHandler(accion, hacer);
      } catch {}
    };
    poner("play", () => reproductorListo && reproductor.playVideo());
    poner("pause", () => reproductorListo && reproductor.pauseVideo());
    poner("nexttrack", siguiente);
    poner("previoustrack", anterior);
    poner("seekto", (d) => reproductorListo && reproductor.seekTo(d.seekTime, true));
  }

  // El mini reproductor aparece cuando el escenario se sale de la pantalla.
  new IntersectionObserver(
    ([e]) => {
      $("mini").hidden = e.isIntersecting || !actual();
    },
    { threshold: 0.15 },
  ).observe($("escenario"));

  const ua = navigator.userAgent;
  const esIos = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const instalada = navigator.standalone || matchMedia("(display-mode: standalone)").matches;
  $("avisoIos").hidden = !esIos || instalada;
  $("avisoAndroid").hidden = !/Android/i.test(ua);

  let pedidoInstalar;
  addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    pedidoInstalar = e;
    $("btnInstalar").hidden = false;
  });
  $("btnInstalar").addEventListener("click", async () => {
    if (!pedidoInstalar) return;
    pedidoInstalar.prompt();
    await pedidoInstalar.userChoice.catch(() => {});
    pedidoInstalar = null;
    $("btnInstalar").hidden = true;
  });
}

async function arrancar() {
  traducir();
  enlazar();
  pintarGustos();
  pintarCola();
  document.body.classList.add("pausado");

  const params = new URLSearchParams(location.search);
  const v = params.get("v");
  const q = params.get("q");
  if (v && /^[\w-]{11}$/.test(v)) {
    // Enlace compartido: la canción y su radio, lista para sonar al tocar.
    try {
      const r = await api(`/api/radio?v=${v}`);
      if (r.canciones.length) {
        estado.cola = r.canciones;
        estado.radioMas = r.mas;
        reproducirIndice(0, false);
      }
    } catch {}
  } else if (actual()) {
    // Donde lo dejaste: la última canción queda cargada, sin sonar.
    reproducirIndice(estado.indice, false);
  }
  if (q) {
    $("q").value = q;
    $("btnBorrar").hidden = false;
    buscar(q);
  }
  cargarApiYouTube();

  if ("serviceWorker" in navigator) navigator.serviceWorker.register("/sw.js").catch(() => {});
}

arrancar();
