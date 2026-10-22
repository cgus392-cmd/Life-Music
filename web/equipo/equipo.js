/* Cabina del equipo de Life Music (lifemusic.app/equipo).
   Ficha: docs/versiones/1.3.3/equipo.md · Diseño: maqueta-equipo.html (aprobada por CG).
   Todo lo que llega del servidor o de una persona se escapa antes de pintarse (h``).
   Las firmas son SVG que arma el servidor con numeros; por eso entran tal cual. */
(() => {
  "use strict";

  const LOCAL = ["localhost", "127.0.0.1"].includes(location.hostname);
  // En local, ?api=8788 apunta a otro puerto del servidor de prueba (wrangler dev).
  const PUERTO_LOCAL = /^\d{4,5}$/.test(new URLSearchParams(location.search).get("api") || "") ? new URLSearchParams(location.search).get("api") : "8787";
  const API = LOCAL ? `http://${location.hostname}:${PUERTO_LOCAL}/equipo/api` : "/equipo/api";
  const ZONA = "America/Bogota";
  const app = document.getElementById("app");
  const FASES = ["", "Idea", "Planteamiento", "Luz verde", "Ejecución", "Hecha · falta probar", "Lanzada"];
  const COLORES = ["c-a", "c-b", "c-c", "c-d", "c-e"];
  const REVISION = [["Revisar el monitoreo", "LUN"], ["Leer las calificaciones nuevas", "MAR"], ["Ordenar los informes de los probadores", "MIÉ"], ["Responder los issues de GitHub", "VIE"]];
  const ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

  // ── HTML seguro ────────────────────────────────────────────────────────────
  class Crudo { constructor(s) { this.s = s; } toString() { return this.s; } }
  const crudo = (s) => new Crudo(s);
  const esc = (v) => (v instanceof Crudo ? v.s : Array.isArray(v) ? v.map(esc).join("") : v == null || v === false ? ""
    : String(v).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]));
  const h = (partes, ...valores) => crudo(partes.reduce((a, p, i) => a + p + (i < valores.length ? esc(valores[i]) : ""), ""));

  // ── Servidor ───────────────────────────────────────────────────────────────
  const ERRORES = {
    "pase-invalido": "Ese pase no sirve: ya se usó, venció o fue anulado. Pídele uno nuevo a Camilo.",
    "codigo-invalido": "Ese código no sirve o ya venció. Genera otro desde el aparato donde ya entraste.",
    bloqueado: "Demasiados códigos equivocados desde esta conexión. Espera 15 minutos y vuelve a intentar.",
    "fuera-de-jornada": "Fuera de tu jornada la cabina es de solo lectura.",
    "acta-pendiente": "Primero tienes que aceptar las condiciones de tu cargo.",
    "sin-sesion": "Tu sesión se cerró. Vuelve a entrar con un pase.",
    permiso: "Tu cargo no permite hacer eso.",
    clave: "Esa no es la clave de arranque.",
    "ya-abierta": "La cabina ya tiene dueño.",
    "sin-clave-de-arranque": "Falta poner la clave de arranque en el servidor (wrangler secret put ARRANQUE).",
    casillas: "Marca las tres casillas para poder firmar.",
    firma: "Falta tu firma.",
    nombre: "Escribe el nombre completo.",
    documento: "El documento solo puede tener números, letras, puntos y guiones.",
    vigencia: "Revisa las fechas de la vigencia (máximo un año).",
    dias: "Elige al menos un día, de lunes a viernes.",
    horario: "La hora de salida tiene que ser después de la de entrada.",
    titulo: "El título es muy corto.",
    campos: "Revisa los campos.",
    correo: "Revisa el correo.",
    ciudad: "Escribe la ciudad.",
    red: "No hay conexión con la cabina. Revisa tu internet.",
    nota: "Cuenta qué pasó: en qué paso y qué viste (mínimo 3 letras).",
    "sin-telefono": "Primero dinos con qué teléfono pruebas.",
    "toma-cerrada": "Esa toma ya se cerró. Ya está cargada la nueva.",
    "en-pausa": "Tu cargo está en pausa: puedes mirar tu hoja, pero no marcar.",
    estado: "Ese estado no existe.",
    capa: "Elige una de las cuatro capas.",
  };
  async function api(metodo, ruta, cuerpo) {
    let r;
    try {
      r = await fetch(API + ruta, {
        method: metodo, credentials: "include", cache: "no-store",
        headers: metodo === "POST" ? { "content-type": "application/json", "x-equipo": "1" } : {},
        body: metodo === "POST" ? JSON.stringify(cuerpo || {}) : undefined,
      });
    } catch {
      throw Object.assign(new Error("red"), { codigo: "red", estado: 0 });
    }
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw Object.assign(new Error(d.error || "red"), { codigo: d.error || "red", estado: r.status, datos: d });
    return d;
  }
  const mensajeDe = (e) => ERRORES[e && e.codigo] || "Algo falló. Intenta de nuevo.";

  // ── Fechas (siempre en hora de Colombia) ───────────────────────────────────
  const fmt = (o) => new Intl.DateTimeFormat("es-CO", { timeZone: ZONA, ...o });
  const hora = (ms) => fmt({ hour: "numeric", minute: "2-digit" }).format(ms);
  const fechaHora = (ms) => fmt({ day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }).format(ms);
  const diaClave = (ms) => fmt({ year: "numeric", month: "2-digit", day: "2-digit" }).format(ms); // dd/mm/aaaa
  const fechaIso = (f, o = { day: "numeric", month: "short", year: "numeric" }) => {
    if (!f) return "";
    const [a, m, d] = f.split("-").map(Number);
    return new Intl.DateTimeFormat("es-CO", { timeZone: "UTC", ...o }).format(Date.UTC(a, m - 1, d));
  };
  const hora12 = (hhmm) => {
    const [hh, mm] = (hhmm || "00:00").split(":").map(Number);
    return `${((hh + 11) % 12) + 1}:${String(mm).padStart(2, "0")} ${hh < 12 ? "a. m." : "p. m."}`;
  };
  function cuando(ms) {
    if (!ms) return "—";
    const s = Math.round((Date.now() - ms) / 1000);
    if (s < 60) return "ahora";
    if (s < 3600) return `hace ${Math.round(s / 60)} min`;
    if (diaClave(ms) === diaClave(Date.now())) return `hoy ${hora(ms)}`;
    return fechaHora(ms);
  }
  function cuandoAbre(ms) {
    if (!ms) return null;
    const hoy = diaClave(Date.now()), dia = diaClave(ms), manana = diaClave(Date.now() + 86400000);
    if (dia === hoy) return `hoy a las ${hora(ms)}`;
    if (dia === manana) return `mañana a las ${hora(ms)}`;
    return `el ${fmt({ weekday: "long", day: "numeric", month: "long" }).format(ms)} a las ${hora(ms)}`;
  }
  const hoyIso = () => { const [d, m, a] = diaClave(Date.now()).split("/"); return `${a}-${m}-${d}`; };
  const sumarDias = (f, n) => { const [a, m, d] = f.split("-").map(Number); return new Date(Date.UTC(a, m - 1, d) + n * 86400000).toISOString().slice(0, 10); };

  // ── Estado ─────────────────────────────────────────────────────────────────
  const S = { publico: null, yo: null, acta: null, cabina: null, prod: null, mon: null, concurso: null, vista: "cargando", antes: null, pestana: "sala", pase: null, ultimoPase: null,
    sala: null, med: undefined, hoja: null, fallaAbierta: null, cambiarTelefono: false };
  const yo = () => S.yo && S.yo.yo;
  const esDuenio = () => !!yo() && yo().rol === "duenio";
  const abierta = () => !!yo() && (esDuenio() || yo().jornada.abierta);
  const puede = (accion) => {
    const r = yo() && yo().rol;
    const P = { duenio: ["idea", "incidencia", "cerrar-incidencia", "revision", "nota", "concurso"], supervisor: ["idea", "incidencia"], operaciones: ["idea", "incidencia", "cerrar-incidencia", "revision", "concurso"] };
    return (P[r] || []).includes(accion);
  };
  function puedeMover(de, a) {
    if (a < 1 || a > 6 || a === de || !abierta()) return false;
    if (esDuenio()) return true;
    if (yo().rol !== "supervisor") return false;
    return ["1>2", "2>1", "3>4", "4>3", "4>5", "5>4"].includes(`${de}>${a}`);
  }
  const iniciales = (n) => (n || "?").split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
  const claseRol = (rol) => (rol === "duenio" ? "cg" : rol === "operaciones" ? "ap" : "");

  // ── Avisos y ventanas ──────────────────────────────────────────────────────
  let relojAviso = null;
  function avisar(texto, mal = false) {
    let a = document.querySelector(".aviso-flotante");
    if (!a) { a = document.createElement("div"); a.setAttribute("role", "status"); document.body.appendChild(a); }
    a.className = "aviso-flotante" + (mal ? " mal" : "");
    a.textContent = texto;
    clearTimeout(relojAviso);
    relojAviso = setTimeout(() => a.remove(), 4200);
  }
  function ventana({ titulo, cuerpo, pie = "", ancha = false, alCerrar }) {
    const d = document.createElement("dialog");
    d.className = "ventana" + (ancha ? " ancha" : "");
    d.innerHTML = esc(h`<div class="ventana-cab"><h2>${titulo}</h2><button type="button" data-cerrar aria-label="Cerrar">×</button></div>
      <div class="ventana-cuerpo">${cuerpo}</div>${pie ? h`<div class="ventana-pie">${pie}</div>` : ""}`);
    document.body.appendChild(d);
    d.addEventListener("click", (e) => { if (e.target === d || e.target.closest("[data-cerrar]")) d.close(); });
    d.addEventListener("close", () => { d.remove(); if (alCerrar) alCerrar(); });
    d.showModal();
    return d;
  }
  const abiertaUnaVentana = () => !!document.querySelector("dialog[open]");

  // ── Carga ──────────────────────────────────────────────────────────────────
  async function cargarYo() {
    try {
      S.yo = await api("GET", "/yo");
    } catch (e) {
      S.yo = null;
      if (e.estado !== 401) { pintarError(e); return; }
    }
    if (S.vista === "politica") { pintar(); return; }
    if (!S.yo) S.vista = location.hash === "#arranque" ? "arranque" : "pase";
    else if (esDuenio()) S.vista = "duenio";
    // 2.0: el supervisor trabaja con su hoja de pruebas. El acta solo se pide con «Equipo y contratación» encendido.
    else if (S.yo.requisito === "acta") S.vista = "acta";
    else S.vista = "hoja";
    if (esDuenio() && !S.yo.contratacion) S.pestana = "sala";
    await cargarDatos();
    pintar();
  }
  async function cargarDatos() {
    const tareas = [];
    if (S.vista === "acta") tareas.push(api("GET", "/acta").then((d) => (S.acta = d)));
    if (S.vista === "hoja" && !S.yo.requisito) tareas.push(api("GET", "/hoja").then((d) => (S.hoja = d)));
    if (S.vista === "duenio" && S.pestana === "sala") {
      tareas.push(api("GET", "/sala").then((d) => (S.sala = d)));
      // Los medidores consultan servicios de afuera (hasta 6 s): la sala no los espera, se pintan al llegar.
      const repintar = () => { if (S.vista === "duenio" && S.pestana === "sala" && S.sala) pintar(); };
      api("GET", "/medidores").then((d) => (S.med = d)).catch(() => (S.med = null)).then(repintar);
      api("GET", "/monitoreo").then((d) => (S.mon = d)).catch(() => (S.mon = null)).then(repintar);
    }
    if (S.vista === "cabina" || (S.vista === "duenio" && S.pestana !== "sala")) {
      tareas.push(api("GET", "/cabina").then((d) => (S.cabina = d)));
      tareas.push(api("GET", "/monitoreo").then((d) => (S.mon = d)).catch(() => (S.mon = null)));
      if (puede("concurso")) tareas.push(api("GET", "/concurso").then((d) => (S.concurso = d)).catch(() => (S.concurso = null)));
      if (esDuenio()) tareas.push(api("GET", "/produccion").then((d) => (S.prod = d)));
      if (S.yo && S.yo.acta.firmada && !esDuenio()) tareas.push(api("GET", "/acta").then((d) => (S.acta = d)).catch(() => {}));
    }
    try { await Promise.all(tareas); } catch (e) { if (e.estado === 401) { S.yo = null; S.vista = "pase"; } else avisar(mensajeDe(e), true); }
  }
  async function recargar() {
    await cargarYo();
  }

  // ── Pintar ─────────────────────────────────────────────────────────────────
  function pintar() {
    const v = S.vista;
    const html = v === "pase" ? vistaPase() : v === "arranque" ? vistaArranque() : v === "acta" ? vistaActa()
      : v === "politica" ? vistaPolitica() : v === "hoja" ? vistaHoja()
      : v === "duenio" ? (S.pestana === "sala" ? vistaSala() : S.pestana === "produccion" ? vistaProduccion() : vistaCabina()) : v === "cabina" ? vistaCabina() : h`<p class="cargando">SALA · CARGANDO…</p>`;
    app.innerHTML = esc(html);
    despuesDePintar();
  }
  function pintarError(e) {
    app.innerHTML = esc(h`<div class="en-blanco"><span class="hueco">!</span><b>No se pudo abrir la cabina</b><p>${mensajeDe(e)}</p><button class="boton" data-accion="recargar">Intentar de nuevo</button></div>`);
  }
  function despuesDePintar() {
    // Las ondas de los clips: barras con alturas al azar, pero siempre las mismas para cada idea.
    app.querySelectorAll("[data-onda]").forEach((o) => {
      let semilla = +o.dataset.onda * 7919 + 13;
      const n = 14 + +o.dataset.largo * 16;
      for (let i = 0; i < n; i++) {
        semilla = (semilla * 9301 + 49297) % 233280;
        const b = document.createElement("i");
        b.style.height = `${Math.round(20 + (semilla / 233280) * 80)}%`;
        o.appendChild(b);
      }
    });
    app.querySelectorAll("[data-ancho]").forEach((el) => { el.style.width = el.dataset.ancho; });
    // La CSP no deja estilos en linea: la posicion de cada fader se pone desde aqui.
    app.querySelectorAll("[data-top]").forEach((el) => { el.style.top = el.dataset.top; });
    if (S.fallaAbierta) { const n = document.getElementById(`nota-${S.fallaAbierta}`); if (n) n.focus(); }
    if (S.vista === "pase") prepararPase();
    if (S.vista === "acta") prepararActa();
    if (S.vista === "duenio" && S.pestana === "produccion" && S.ultimoPase) pintarQr();
  }

  // ── 1 · El pase ────────────────────────────────────────────────────────────
  function normalizar(t) {
    let x = (t || "").toUpperCase().replace(/[^0-9A-Z]/g, "");
    if (x.length === 10 && x.startsWith("LM")) x = x.slice(2);
    return [...x].every((c) => ALFABETO.includes(c)) ? x : "";
  }
  function vistaPase() {
    const p = S.pase;
    const primer = p ? p.nombre.split(" ")[0] : "";
    return h`<div class="pase-escena">
      <div class="pase-texto">
        <div class="rotulo"><span class="led rojo"></span><span>${p ? "Pase a tu nombre" : "Acceso restringido · equipo de producción"}</span></div>
        <h1>${p ? crudo(`Tu pase está listo, <em>${esc(primer)}.</em>`) : crudo("Muestra tu <em>pase.</em>")}</h1>
        <p>${p ? "Revisa que el pase esté a tu nombre y valídalo para seguir." : "Esta es la cabina de Life Music: aquí trabaja el equipo que hace la app. Escribe el código que te dio Camilo."}</p>
        <ol>
          <li><span>01</span><div>El código sirve <b>una sola vez</b> y vence en <b>48 horas</b>.</div></li>
          <li><span>02</span><div>Después aceptas las <b>condiciones de tu cargo</b> y entras a tu hoja de pruebas.</div></li>
          <li><span>03</span><div>Este aparato queda enlazado. Para entrar desde otro, pides un código de 5 minutos o escribes aquí el que te dé tu otro aparato.</div></li>
        </ol>
        ${S.publico && !S.publico.hayDuenio ? h`<p><button class="boton chico" data-accion="ir" data-a="arranque">Soy Camilo: abrir la cabina por primera vez</button></p>` : ""}
      </div>
      <div class="pase-mesa">
        <div class="pase" role="group" aria-label="Pase de equipo">
          <div class="holo" aria-hidden="true"></div>
          <div class="pase-cab"><div><b>LIFE MUSIC</b><small>EQUIPO · TEMPORADA ${new Date().getFullYear()}</small></div><span class="n">Nº ${p ? String(p.numero).padStart(4, "0") : "————"}</span></div>
          <div class="aaa">AAA<small>ACCESO TOTAL · CABINA</small></div>
          <div class="pase-campos">
            <div><label>NOMBRE</label><div class="vacio${p ? " lleno" : ""}">${p ? p.nombre : "Aparece al escribir el código"}</div></div>
            <div class="par"><div><label>CARGO</label><div class="vacio${p ? " lleno" : ""}">${p ? p.cargo : "—"}</div></div><div><label>VENCE</label><div class="vacio${p ? " lleno" : ""}">${p ? fechaHora(p.venceEn) : "—"}</div></div></div>
            <div><label for="codigo">CÓDIGO DE ENTRADA</label><input id="codigo" class="codigo-entrada" autocomplete="one-time-code" autocapitalize="characters" spellcheck="false" maxlength="14" placeholder="LM-XXXX-XXXX" value="${S.codigo || ""}"></div>
          </div>
          <div class="pase-pie"><div class="barras" aria-hidden="true"></div><small>CG LABS<br>lifemusic.app/equipo</small></div>
        </div>
        <button class="boton piloto" id="validar" data-accion="validar" disabled>${p ? `Soy ${primer} · validar pase` : "Validar pase"}</button>
        <p class="error-campo" id="error-pase" hidden></p>
        <p class="pase-nota">${p ? `Si no eres ${primer}, cierra esta página y avísale a Camilo.` : "¿No tienes código? Pídeselo a Camilo. Nadie del equipo te lo va a pedir por chat."}</p>
        <p class="aviso-legal">Sistema privado de CG LABS. Solo pueden entrar las personas invitadas. Entrar sin autorización es un delito en Colombia (Ley 1273 de 2009, art. 269A).</p>
      </div>
    </div>`;
  }
  let consultaPase = 0;
  function prepararPase() {
    const campo = document.getElementById("codigo"), boton = document.getElementById("validar");
    const revisar = async () => {
      const c = normalizar(campo.value);
      S.codigo = campo.value;
      document.getElementById("error-pase").hidden = true;
      if (c.length === 6) { boton.disabled = false; boton.textContent = "Enlazar este aparato"; return; }
      boton.disabled = c.length !== 8;
      if (c.length !== 8) { if (S.pase) { S.pase = null; pintar(); } return; }
      const n = ++consultaPase;
      try {
        const d = await api("POST", "/pase/ver", { codigo: c });
        if (n !== consultaPase) return;
        S.pase = d.pase;
        pintar();
        const otro = document.getElementById("codigo");
        otro.focus();
        otro.setSelectionRange(otro.value.length, otro.value.length);
        document.getElementById("validar").disabled = false;
      } catch (e) {
        if (n !== consultaPase) return;
        mostrarErrorPase(e);
        boton.disabled = true;
      }
    };
    campo.addEventListener("input", revisar);
    if (S.codigo && normalizar(S.codigo).length === 8 && !S.pase) revisar();
    else if (S.pase) boton.disabled = false;
    else if (normalizar(campo.value).length === 6) revisar();
    if (!S.pase) campo.focus();
  }
  function mostrarErrorPase(e) {
    const p = document.getElementById("error-pase");
    if (p) { p.textContent = mensajeDe(e); p.hidden = false; }
  }
  async function validar() {
    const c = normalizar(document.getElementById("codigo").value);
    const b = document.getElementById("validar");
    b.disabled = true;
    try {
      await api("POST", c.length === 6 ? "/aparato/enlazar" : "/pase/validar", { codigo: c });
      S.codigo = null; S.pase = null;
      await cargarYo();
    } catch (e) {
      mostrarErrorPase(e);
      b.disabled = false;
    }
  }

  // ── Arranque (solo la primera vez, el dueño) ───────────────────────────────
  function vistaArranque() {
    return h`<div class="pase-escena">
      <div class="pase-texto">
        <div class="rotulo"><span class="led rojo"></span><span>Primera vez · dueño</span></div>
        <h1>Abre la <em>cabina.</em></h1>
        <p>Escribe la clave de arranque que pusiste en el servidor con <code>wrangler secret put ARRANQUE</code>. Tu nombre, la ciudad y el correo salen en la política de datos como responsable.</p>
        <form class="arranque-forma" data-forma="arranque">
          <div class="campo"><label for="a-clave">Clave de arranque</label><input class="control" id="a-clave" type="password" autocomplete="off" required></div>
          <div class="campo"><label for="a-nombre">Tu nombre completo</label><input class="control" id="a-nombre" autocomplete="name" required minlength="3" maxlength="100"></div>
          <div class="fila-campos">
            <div class="campo"><label for="a-ciudad">Ciudad</label><input class="control" id="a-ciudad" required maxlength="60"></div>
            <div class="campo"><label for="a-correo">Correo para asuntos de datos</label><input class="control" id="a-correo" type="email" required maxlength="120"></div>
          </div>
          <p class="error-campo" id="error-arranque" hidden></p>
          <div><button class="boton piloto" type="submit">Abrir la cabina</button> <button class="boton" type="button" data-accion="ir" data-a="pase">Volver</button></div>
        </form>
      </div>
      <div class="pase-mesa"><p class="aviso-legal">Esto solo funciona una vez. Después, la cabina queda a tu nombre y nadie más puede abrirla así.</p></div>
    </div>`;
  }
  async function arrancar() {
    const v = (id) => document.getElementById(id).value;
    try {
      await api("POST", "/arranque", { clave: v("a-clave"), nombre: v("a-nombre"), ciudad: v("a-ciudad"), correo: v("a-correo") });
      history.replaceState(null, "", location.pathname);
      S.publico = await api("GET", "/estado").catch(() => S.publico);
      await cargarYo();
      avisar("La cabina quedó abierta a tu nombre");
    } catch (e) {
      const p = document.getElementById("error-arranque");
      p.textContent = mensajeDe(e); p.hidden = false;
    }
  }

  // ── 2 · El acta ────────────────────────────────────────────────────────────
  function documentoActa(doc) {
    return h`<header class="acta-cab">
        <div><div class="rot">Life Music · CG LABS</div><h1>${doc.titulo}</h1></div>
        <div class="folio">FOLIO<b>${doc.folio}</b>versión del acta ${doc.version}</div>
      </header>
      <dl class="terminos">${doc.terminos.map(([k, v]) => h`<div><dt>${k}</dt><dd>${v}</dd></div>`)}</dl>
      <ol class="clausulas">${doc.clausulas.map(([t, x]) => h`<li><span><b>${t}.</b> ${x}</span></li>`)}</ol>`;
  }
  function vistaActa() {
    const a = S.acta;
    if (!a || !a.doc) return h`<p class="cargando">CARGANDO EL ACTA…</p>`;
    const p = yo();
    const dias = p.diaCargo ? p.diaCargo.total : null;
    return h`<div class="marco">
      <div class="pasos"><span class="hecho">Pase validado</span><em></em><span class="ahora">Acta del cargo</span><em></em><span>Cabina</span></div>
      <div class="acta-mesa">
        <article class="acta" aria-label="Acta de aceptación del cargo temporal">
          ${documentoActa(a.doc)}
          <div class="datos-firma">
            <div class="campo"><label for="f-nombre">Nombre completo</label><input id="f-nombre" class="control-papel" value="${p.nombre}" placeholder="Como aparece en tu documento" maxlength="100"></div>
            <div class="campo"><label for="f-doc">Documento (opcional)</label><input id="f-doc" class="control-papel" placeholder="C.C. o C.E." maxlength="30"><small>Se guarda tapado; solo Camilo lo ve completo.</small></div>
          </div>
          <div class="firmas">
            <div class="firma-caja">
              <div class="campo"><label>Firma de quien acepta</label></div>
              <div class="lienzo"><span class="guia" id="guia">Firma aquí con el dedo o el mouse</span><span class="x">×</span><span class="linea-base"></span><canvas id="firma" aria-label="Espacio para firmar"></canvas></div>
              <div class="firma-pie"><span id="firma-estado">Sin firmar</span><span><button type="button" class="alterna" data-accion="firma-escrita">Firmar escribiendo mi nombre</button> · <button type="button" data-accion="firma-borrar">Borrar y repetir</button></span></div>
            </div>
            <div>
              <div class="campo"><label>Firma de Camilo (producción)</label></div>
              <div class="firma-cg"><div><b>Pendiente</b>Camilo firma desde su panel cuando tú firmes. Ahí el acta queda completa.</div></div>
            </div>
          </div>
          <div class="permisos">
            <label class="acepto"><input type="checkbox" id="c-acepto"><span>Leí el acta completa y acepto el cargo temporal en estas condiciones.</span></label>
            <label class="acepto"><input type="checkbox" id="c-autorizo"><span>Autorizo a CG LABS a tratar mis datos personales (nombre, documento, firma y actividad en la cabina) solo para gestionar este cargo, según la <button type="button" class="enlace" data-accion="ir" data-a="politica">Política de datos del equipo</button>.</span></label>
            <label class="acepto"><input type="checkbox" id="c-mayor"><span>Soy mayor de edad.</span></label>
          </div>
          <div class="acta-acciones"><button class="boton" id="firmar" data-accion="firmar-acta" disabled>Firmar y aceptar el cargo</button><small>Firma electrónica · Ley 527 de 1999 y Decreto 2364 de 2012</small></div>
          <p class="error-campo" id="error-acta" hidden></p>
        </article>
        <aside class="lateral">
          ${dias ? h`<div class="ficha-lat vigencia"><h3>Tu temporada</h3><div class="grande">${dias}<small>días</small></div>
            <div class="regla-tiempo"><span>${fechaIso(p.desde, { day: "numeric", month: "short" }).toUpperCase()}</span><span>${fechaIso(p.hasta, { day: "numeric", month: "short" }).toUpperCase()}</span></div><div></div></div>` : ""}
          <div class="ficha-lat"><h3>Cómo queda guardada</h3>
            <p>El acta y tu firma se guardan juntas con una huella <code>SHA-256</code>. Si alguien cambiara una sola coma del acta, la huella dejaría de coincidir.</p>
            <p>Cuando Camilo firme, puedes guardar una copia en PDF desde la cabina.</p></div>
          <div class="ficha-lat"><h3>Antes de firmar</h3>
            <p>Si algo del acta no te cuadra, no firmes todavía y habla con Camilo. Este aparato queda esperando.</p>
            <p><button class="boton chico" data-accion="salir">Salir por ahora</button></p></div>
        </aside>
      </div>
    </div>`;
  }

  // El lienzo de la firma: los trazos se guardan como puntos y se repintan al cambiar de tamaño.
  let firma = null;
  function lienzoDeFirma(canvas, alCambiar) {
    const g = canvas.getContext("2d");
    let caminos = [], actual = null, escrita = "";
    const tinta = "#2441B5";
    function pintarFirma() {
      const d = window.devicePixelRatio || 1;
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.clearRect(0, 0, canvas.width, canvas.height);
      g.setTransform(d, 0, 0, d, 0, 0);
      g.lineCap = "round"; g.lineJoin = "round"; g.lineWidth = 2.4; g.strokeStyle = tinta; g.fillStyle = tinta;
      if (escrita) { g.font = "italic 600 36px Georgia, 'Times New Roman', serif"; g.fillText(escrita, 34, canvas.getBoundingClientRect().height - 46); }
      for (const c of caminos) {
        g.beginPath(); g.moveTo(c[0][0], c[0][1]);
        for (let i = 1; i < c.length; i++) g.lineTo(c[i][0], c[i][1]);
        if (c.length === 1) g.lineTo(c[0][0] + 0.1, c[0][1]);
        g.stroke();
      }
    }
    function ajustar() {
      const r = canvas.getBoundingClientRect(), d = window.devicePixelRatio || 1;
      if (!r.width) return;
      canvas.width = Math.round(r.width * d); canvas.height = Math.round(r.height * d);
      pintarFirma();
    }
    const punto = (e) => { const r = canvas.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; };
    canvas.addEventListener("pointerdown", (e) => { canvas.setPointerCapture(e.pointerId); escrita = ""; actual = [punto(e)]; caminos.push(actual); pintarFirma(); });
    canvas.addEventListener("pointermove", (e) => { if (!actual) return; actual.push(punto(e)); pintarFirma(); });
    const soltar = () => { if (actual) { actual = null; alCambiar(); } };
    canvas.addEventListener("pointerup", soltar);
    canvas.addEventListener("pointercancel", soltar);
    new ResizeObserver(ajustar).observe(canvas);
    ajustar();
    return {
      vacia: () => !escrita && caminos.length === 0,
      escrita: () => !!escrita,
      trazos: () => caminos.length,
      borrar() { caminos = []; escrita = ""; pintarFirma(); alCambiar(); },
      escribir(n) { caminos = []; escrita = n; pintarFirma(); alCambiar(); },
      datos() {
        if (escrita) return { tipo: "escrita" };
        const r = canvas.getBoundingClientRect();
        return { tipo: "dibujada", ancho: Math.round(r.width), alto: Math.round(r.height), trazos: caminos.map((c) => c.flatMap(([x, y]) => [Math.round(x * 10) / 10, Math.round(y * 10) / 10])) };
      },
    };
  }
  function prepararActa() {
    const est = () => {
      document.getElementById("guia").hidden = !firma.vacia();
      document.getElementById("firma-estado").textContent = firma.escrita() ? "Firma escrita lista" : firma.vacia() ? "Sin firmar" : `Firma lista · ${firma.trazos()} ${firma.trazos() === 1 ? "trazo" : "trazos"}`;
      const marcadas = ["c-acepto", "c-autorizo", "c-mayor"].every((id) => document.getElementById(id).checked);
      document.getElementById("firmar").disabled = firma.vacia() || !marcadas || document.getElementById("f-nombre").value.trim().length < 3;
    };
    firma = lienzoDeFirma(document.getElementById("firma"), est);
    ["c-acepto", "c-autorizo", "c-mayor", "f-nombre"].forEach((id) => document.getElementById(id).addEventListener("input", est));
  }
  async function firmarActa() {
    const b = document.getElementById("firmar");
    b.disabled = true;
    try {
      const d = await api("POST", "/acta/firmar", {
        nombre: document.getElementById("f-nombre").value, documento: document.getElementById("f-doc").value,
        acepto: document.getElementById("c-acepto").checked, autorizo: document.getElementById("c-autorizo").checked, mayor: document.getElementById("c-mayor").checked,
        firma: firma.datos(),
      });
      avisar(`Acta ${d.folio} firmada. Bienvenida a la cabina.`);
      await cargarYo();
    } catch (e) {
      const p = document.getElementById("error-acta");
      p.textContent = mensajeDe(e); p.hidden = false;
      b.disabled = false;
    }
  }

  // ── Piezas comunes de la cabina ────────────────────────────────────────────
  function tope(subtitulo) {
    const p = yo();
    return h`<header class="tope">
      <div class="sello"><b>LIFE MUSIC</b><span>${subtitulo}</span></div>
      ${esDuenio() ? h`<nav class="pestanas" aria-label="Secciones">
        <button data-accion="pestana" data-a="sala">Sala de control</button>
        <button data-accion="pestana" data-a="produccion" ${S.pestana === "produccion" ? crudo('aria-current="page"') : ""}>Producción</button>
        <button data-accion="pestana" data-a="cabina" ${S.pestana === "cabina" ? crudo('aria-current="page"') : ""}>Sesión y operación</button></nav>` : ""}
      <div class="tope-der">
        ${esDuenio() && S.prod && S.prod.fallosHoy > 0 ? h`<span class="estado mal">${S.prod.fallosHoy} CÓDIGOS FALLIDOS HOY</span>` : ""}
        <div class="quien"><div class="nombre"><b>${p.nombre}</b><small>${p.cargo}</small></div><span class="iniciales ${claseRol(p.rol)}">${iniciales(p.nombre)}</span></div>
      </div>
    </header>`;
  }
  function llamado() {
    const p = yo();
    const ahora = Date.now();
    const dia = fmt({ day: "numeric" }).format(ahora);
    const semana = S.yo.semana ? S.yo.semana.split("-W")[1] : "";
    const fecha = h`<div class="fecha"><span class="d num">${dia}</span><div class="dm"><span>${fmt({ weekday: "long" }).format(ahora)}</span><span>${fmt({ month: "long" }).format(ahora)}</span><small>${fmt({ year: "numeric" }).format(ahora)} · semana ${semana}</small></div></div>`;
    if (esDuenio()) {
      const equipo = S.prod ? S.prod.personas.filter((x) => x.estado !== "terminada" && x.estado !== "invitada") : [];
      const alAire = equipo.filter((x) => x.jornada.abierta).length;
      return h`<div class="llamado">${fecha}
        <dl class="horas"><div><dt>Equipo</dt><dd>${equipo.length}<small>de ${S.prod ? S.prod.vacantes : 2} vacantes</small></dd></div>
          <div><dt>Al aire ahora</dt><dd>${alAire}<small>${alAire === 1 ? "persona" : "personas"}</small></dd></div>
          <div><dt>Versión</dt><dd>${S.cabina ? S.cabina.versionObjetivo : ""}</dd></div></dl>
        <div class="aire grande ${alAire ? "encendida" : ""}">${alAire ? "Al aire" : "Fuera del aire"}</div></div>`;
    }
    const j = p.jornada;
    const dc = p.diaCargo;
    return h`<div class="llamado">${fecha}
      <dl class="horas">
        <div><dt>Llamado</dt><dd>${hora12(p.entrada).replace(/ (a|p)\. m\./, "")}<small>${hora12(p.entrada).slice(-5)}</small></dd></div>
        <div><dt>Cierre</dt><dd>${hora12(p.salida).replace(/ (a|p)\. m\./, "")}<small>${hora12(p.salida).slice(-5)}</small></dd></div>
        ${dc ? h`<div><dt>Cargo temporal</dt><dd>Día ${dc.dia}<small>de ${dc.total}</small></dd><div class="temporada"><i data-ancho="${Math.round((dc.dia / dc.total) * 100)}%"></i></div></div>` : ""}
      </dl>
      <div class="aire grande ${j.abierta ? "encendida" : ""}">${j.abierta ? "Al aire" : "Fuera del aire"}</div></div>
      ${j.abierta ? "" : h`<div class="franja-fuera"><span class="led"></span><span>${crudo(textoFuera(j))}</span></div>`}`;
  }
  function textoFuera(j) {
    const sig = cuandoAbre(j.siguiente);
    // «…a las 8:00 a. m.» ya termina en punto: no se le pone otro.
    const hasta = sig ? ` hasta ${esc(sig)}`.replace(/\.$/, "") : "";
    const p = yo();
    switch (j.motivo) {
      case "pausa": return "<b>Tu cargo está en pausa.</b> Puedes mirar todo, pero no mover nada. Habla con Camilo.";
      case "antes-de-vigencia": return `<b>Tu cargo empieza el ${esc(fechaIso(p.desde, { day: "numeric", month: "long" }))}.</b> Mientras tanto la cabina es de solo lectura.`;
      case "vigencia-terminada": return `<b>Tu cargo terminó el ${esc(fechaIso(p.hasta, { day: "numeric", month: "long" }))}.</b> Puedes mirar y descargar tus datos.`;
      case "festivo": return `<b>Hoy es festivo.</b> La cabina queda en solo lectura${hasta}.`;
      case "dia-libre": return `<b>Hoy es día libre.</b> La cabina queda en solo lectura${hasta}.`;
      default: return `<b>Fuera del aire.</b> La cabina queda en solo lectura${hasta}. Puedes mirar todo, pero no mover nada.`;
    }
  }

  // La sesion de la version: cada idea es una pista y su clip avanza por las fases.
  function bloqueSesion() {
    const c = S.cabina;
    const ideas = c.ideas;
    const objetivo = c.versionObjetivo;
    const enObjetivo = c.funcionesObjetivo;
    const luces = [0, 1, 2, 3, 4].map((i) => h`<b class="${i < enObjetivo ? (i >= 3 ? "on pico" : "on") : ""}"></b>`);
    const pistas = ideas.map((i, n) => {
      const color = COLORES[(i.numero - 1) % COLORES.length];
      const atras = i.fase - 1, adelante = i.fase + 1;
      const botones = [];
      if (puedeMover(i.fase, atras)) botones.push(h`<button data-accion="mover" data-id="${i.id}" data-fase="${atras}" aria-label="Devolver a ${FASES[atras]}">←</button>`);
      if (puedeMover(i.fase, adelante)) {
        const clave = adelante === 3 || adelante === 6;
        botones.push(h`<button class="${clave ? "clave" : ""}" data-accion="mover" data-id="${i.id}" data-fase="${adelante}">${adelante === 3 ? "Dar luz verde" : adelante === 6 ? "Lanzar" : `${FASES[adelante]} →`}</button>`);
      }
      if (esDuenio() && i.fase < 6) botones.push(h`<button data-accion="descartar" data-id="${i.id}" aria-label="Descartar">✕</button>`);
      const celdas = [1, 2, 3, 4, 5, 6].map((k) => h`<span class="celda k${k}${k === 3 || k === 6 ? " puerta" : ""}"></span>`);
      const etiqueta = i.fase === 3 ? "Con luz verde" : i.fase === 6 ? "Lanzada" : FASES[i.fase];
      return h`<div class="pista ${color}">
        <div class="pista-cab"><span class="n">${String(n + 1).padStart(2, "0")}</span><span class="color"></span>
          <div><button class="titulo-idea" data-accion="ver-idea" data-id="${i.id}">${i.titulo}</button>
            <small><span>${iniciales(i.autor)}</span><span>${i.version || "sin versión"}</span></small>
            ${botones.length ? h`<div class="mover">${botones}</div>` : ""}</div></div>
        <div class="carril">${celdas}<div class="clip l${i.fase}${i.fase === 4 || i.fase === 5 ? " vivo" : ""}"><span class="titulo-clip">${etiqueta}${i.responsable ? ` · ${i.responsable}` : ""}</span><span class="onda" data-onda="${i.numero}" data-largo="${i.fase}"></span></div></div>
      </div>`;
    });
    return h`<section class="bloque" aria-label="Sesión de la versión ${objetivo}">
      <div class="bloque-cab"><h2>Sesión ${objetivo} <span>· ${ideas.length ? `${ideas.length} ${ideas.length === 1 ? "pista" : "pistas"}` : "sin pistas"}</span></h2>
        <div class="medidor"><i aria-hidden="true">${luces}</i>${enObjetivo} de 5 funciones para la versión</div></div>
      ${ideas.length ? h`<div class="sesion"><div class="sesion-in">
        <div class="regla"><div>Pista</div><div><span class="n">1</span>Idea</div><div><span class="n">2</span>Planteamiento</div>
          <div class="puerta"><span>Luz verde<small>solo CG</small></span></div><div><span class="n">3</span>Ejecución</div><div><span class="n">4</span>Pruebas</div>
          <div class="puerta"><span>Lanzar<small>solo CG</small></span></div></div>${pistas}</div></div>`
        : h`<div class="en-blanco"><span class="hueco">01</span><b>La sesión está en silencio</b><p>Todavía no hay ideas. La primera que se proponga entra como pista 01 y avanza por las fases desde aquí.</p></div>`}
      <div class="sesion-pie"><span>Las franjas rojas son las puertas que solo abre Camilo: dar luz verde y lanzar.</span>
        ${puede("idea") ? h`<button class="boton chico" data-accion="nueva-idea" ${abierta() ? "" : crudo("disabled")}>+ Proponer una idea</button>` : ""}</div>
    </section>`;
  }

  function bloqueNotas() {
    const notas = S.cabina.notas;
    return h`<section class="bloque" aria-label="Notas de producción">
      <div class="bloque-cab"><h2>Notas de producción</h2>${esDuenio() ? h`<button class="boton chico" data-accion="nueva-nota">+ Nota</button>` : ""}</div>
      ${notas.length ? notas.map((n) => h`<div class="nota"><div class="meta"><span class="${n.fijada ? "fija" : ""}">${n.fijada ? "FIJADA" : "NOTA"}</span><span>${iniciales(n.autor)} · ${cuando(n.creada_en).toUpperCase()}</span></div>
          <b>${n.titulo}</b>${n.texto ? h`<p>${n.texto}</p>` : ""}${esDuenio() ? h`<p><button class="boton chico" data-accion="quitar-nota" data-id="${n.id}">Quitar</button></p>` : ""}</div>`)
        : h`<div class="en-blanco"><p>${esDuenio() ? "Aquí van los avisos que fijes para el equipo." : "Sin notas de Camilo por ahora. Los avisos que él fije aparecen aquí."}</p></div>`}
    </section>`;
  }

  function bloqueConexiones() {
    const acc = S.cabina.accesos;
    const chip = (e) => (e === "concedido" ? h`<span class="estado ok">CONECTADO</span>` : h`<span class="estado esp">PEDIDO</span>`);
    return h`<section class="bloque" aria-label="Conexiones">
      <div class="bloque-cab"><h2>Conexiones</h2><span class="estado ok">SIN CLAVES AQUÍ</span></div>
      ${acc.length ? h`<ul class="conexiones">${acc.map((a) => h`<li><span class="jack ${a.estado === "concedido" ? "on" : "pedido"}"></span>
          <div><b>${a.plataforma}${esDuenio() ? ` · ${a.persona}` : ""}</b><small>${a.permiso || "Sin permiso indicado"}</small></div>
          <span>${chip(a.estado)}${esDuenio() ? h` <button class="boton chico" data-accion="acceso-estado" data-id="${a.id}" data-a="retirado">Retirar</button>` : ""}</span></li>`)}</ul>`
        : h`<div class="en-blanco"><p>${esDuenio() ? "Aquí registras qué plataforma tiene cada persona. Las claves nunca se guardan aquí." : "Todavía no tienes conexiones. Camilo te invita a cada plataforma con tu propia cuenta y aquí ves cuáles tienes."}</p></div>`}
      <div class="bloque-pie">${esDuenio()
        ? h`<button class="boton chico" data-accion="registrar-acceso">+ Registrar un acceso</button>`
        : h`<button class="boton chico" data-accion="pedir-acceso" ${abierta() ? "" : crudo("disabled")}>${acc.length ? "Pedir otra conexión" : "Pedir una conexión"}</button>`}</div>
    </section>`;
  }

  function bloquePase() {
    const p = yo(), aps = S.yo.aparatos, acta = S.yo.acta;
    return h`<section class="bloque" aria-label="Tu pase">
      <div class="mini-pase"><span class="iniciales ${claseRol(p.rol)}">${iniciales(p.nombre)}</span>
        <div><b>Pase Nº ${String(p.numero).padStart(4, "0")} · ${p.estado === "pausa" ? "en pausa" : "activo"}</b><small>${aps.length} ${aps.length === 1 ? "aparato enlazado" : "aparatos enlazados"}</small></div>
        <div class="firmado"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12l5 5 9-10"/></svg><span>${acta.contrafirmada ? `Acta ${acta.folio} firmada por ti y por Camilo` : "Acta firmada por ti · falta la firma de Camilo"}</span></div></div>
      <ul class="aparatos-lista">${aps.map((a) => h`<li><span>${a.aparato || "Aparato"} · ${a.esta ? "este" : `visto ${cuando(a.vista_en)}`}</span>${a.esta ? "" : h`<button class="boton chico" data-accion="cerrar-sesion" data-id="${a.id}">Cerrar</button>`}</li>`)}</ul>
      <div class="bloque-pie"><button class="boton chico" data-accion="codigo-aparato">Enlazar otro aparato</button><button class="boton chico" data-accion="ver-acta">Ver el acta</button><button class="boton chico" data-accion="salir">Salir de este aparato</button></div>
    </section>`;
  }

  function bloqueDatos() {
    return h`<section class="bloque tus-datos" aria-label="Tus datos">
      <div class="bloque-cab"><h2>Tus datos</h2><span class="estado ok">LEY 1581</span></div>
      <p>Puedes ver todo lo que la cabina guarda de ti, pedir que se corrija o que se borre. Camilo responde en máximo 10 días hábiles.</p>
      <div class="bloque-pie"><button class="boton chico" data-accion="mis-datos">Ver y descargar mis datos</button><button class="boton chico" data-accion="solicitud" data-a="corregir">Pedir una corrección</button><button class="boton chico" data-accion="solicitud" data-a="borrar">Pedir que se borren</button></div>
    </section>`;
  }

  function bloqueMonitoreo(clase) {
    const m = S.mon;
    if (!m) return h`<section class="bloque ${clase}"><div class="bloque-cab"><h2>Monitoreo</h2></div><div class="en-blanco"><p>No se pudo medir ahora. Se reintenta en un minuto.</p></div></section>`;
    const canales = m.servicios.map((s) => {
      // Como la señal de un teléfono: más barras, mejor respuesta. Lento (más de 1 s) en ámbar.
      const nivel = s.ok && s.ms != null ? Math.min(8, Math.max(2, 8 - Math.floor(s.ms / 250))) : 0;
      const lento = s.ok && s.ms > 1000;
      const barras = [0, 1, 2, 3, 4, 5, 6, 7].map((k) => h`<b class="${k < nivel ? (lento ? "on alto" : "on") : ""}"></b>`);
      return h`<div class="canal"><div class="canal-arriba"><div class="vu">${barras}</div><div class="ms">${s.ok ? s.ms : "—"}<small>${s.ok ? "ms" : "sin respuesta"}</small></div></div>
        <div><b class="nom">${s.nombre}</b><small class="det">${s.detalle}</small></div></div>`;
    });
    const todos = m.servicios.every((s) => s.ok);
    return h`<section class="bloque ${clase}" aria-label="Monitoreo de servicios">
      <div class="bloque-cab"><h2>Monitoreo</h2>${todos ? h`<span class="vivo-tag">EN VIVO · TODO EN ORDEN</span>` : h`<span class="estado mal">HAY UN SERVICIO CAÍDO</span>`}</div>
      <div class="canales">${canales}</div></section>`;
  }
  function bloqueConcurso(clase) {
    const c = S.concurso;
    if (!c) return h`<section class="bloque ${clase}"><div class="bloque-cab"><h2>Concurso</h2></div><div class="en-blanco"><p>No se pudo leer el contador ahora.</p></div></section>`;
    const n = String(Math.min(999, c.contador || 0)).padStart(3, "0");
    return h`<section class="bloque ${clase}" aria-label="Contador del concurso">
      <div class="bloque-cab"><h2>Concurso${c.premio ? ` · ${c.premio}` : ""}</h2><span class="vivo-tag">EN VIVO</span></div>
      <div class="contador"><div class="digitos num">${[...n].map((d) => h`<span>${d}</span>`)}</div><div class="de"><b>Prerregistros</b>meta ${c.meta}</div></div>
      <div class="cinta-prog"><i data-ancho="${Math.min(100, Math.round(((c.contador || 0) / (c.meta || 100)) * 100))}%"></i></div>
      <div class="bloque-pie">Aquí solo va el número. Los nombres y correos son privados y solo los ve Camilo.</div></section>`;
  }
  function bloqueIncidencias(clase) {
    const lista = S.cabina.incidencias;
    const abiertas = lista.filter((i) => i.abierta).length;
    return h`<section class="bloque ${clase}" aria-label="Bitácora de incidencias">
      <div class="bloque-cab"><h2>Bitácora <span>· ${abiertas ? `${abiertas} ${abiertas === 1 ? "abierta" : "abiertas"}` : "ninguna abierta"}</span></h2>
        ${puede("incidencia") ? h`<button class="boton chico piloto" data-accion="nueva-incidencia" ${abierta() ? "" : crudo("disabled")}>Nueva incidencia</button>` : ""}</div>
      ${lista.length ? h`<ul class="bitacora">${lista.map((i) => h`<li><span class="sev ${i.gravedad}"></span><span class="tc">${fmt({ day: "2-digit", month: "short" }).format(i.creada_en).toUpperCase()}<br>${hora(i.creada_en)}</span>
          <div><b>${i.titulo}</b><small>${i.detalle ? `${i.detalle} · ` : ""}${i.autor || ""}${i.abierta ? "" : ` · cerrada ${cuando(i.cerrada_en)}`}</small></div>
          <span>${i.abierta ? h`<span class="estado ${i.gravedad === "alta" ? "mal" : i.gravedad === "media" ? "esp" : "neutro"}">${i.gravedad.toUpperCase()}</span>` : h`<span class="estado neutro">CERRADA</span>`}
          ${i.abierta && puede("cerrar-incidencia") && abierta() ? h` <button class="boton chico" data-accion="cerrar-incidencia" data-id="${i.id}">Cerrar</button>` : ""}</span></li>`)}</ul>`
        : h`<div class="en-blanco"><span class="hueco">0</span><b>Sin incidencias</b><p>Cuando Camilo o un probador reporte algo, aparece aquí con su hora y su gravedad.</p></div>`}
    </section>`;
  }
  function bloqueRevision(clase) {
    const r = S.cabina.revision;
    if (!r) return "";
    const hechos = new Set(r.hechos.map((x) => x.item));
    return h`<section class="bloque ${clase}" aria-label="Revisión de la semana">
      <div class="bloque-cab"><h2>Revisión de la semana</h2><span class="estado esp">${hechos.size} DE 4</span></div>
      <ul class="chequeo">${REVISION.map(([t, d], k) => h`<li class="${hechos.has(k) ? "hecho" : ""}"><button data-accion="revision" data-item="${k}" ${abierta() ? "" : crudo("disabled")} aria-pressed="${hechos.has(k)}"><span class="c"></span><b>${t}</b><small>${d}</small></button></li>`)}</ul>
    </section>`;
  }

  function vistaCabina() {
    const p = yo();
    const ops = p.rol === "operaciones" || esDuenio();
    const operacion = h`<div class="ops">
      ${bloqueMonitoreo("s7")}${ops ? bloqueConcurso("s5") : bloqueIncidencias("s5")}
      ${ops ? bloqueIncidencias("s7") : ""}${ops ? bloqueRevision("s5") : ""}</div>`;
    const sesion = h`<div class="cabina">${bloqueSesion()}
      <div class="col-der">${bloqueNotas()}${bloqueConexiones()}${esDuenio() ? "" : bloquePase()}${esDuenio() ? "" : bloqueDatos()}</div></div>`;
    return h`${tope(esDuenio() ? "Producción" : "Cabina")}<div class="marco ${abierta() ? "" : "fuera-del-aire"}">${llamado()}
      ${p.rol === "operaciones" ? h`${operacion}<div class="espacio"></div>${sesion}` : h`${sesion}<div class="espacio"></div>${operacion}`}</div>`;
  }

  // ── Produccion: el panel de Camilo ─────────────────────────────────────────
  function filaPersona(p) {
    let ahora;
    if (p.estado === "invitada") ahora = h`<span class="estado neutro">INVITADA</span>`;
    else if (p.estado === "pausa") ahora = h`<span class="estado esp">EN PAUSA</span>`;
    else if (p.estado === "terminada") ahora = h`<span class="estado neutro">TERMINADA</span>`;
    else ahora = h`<span class="aire chico ${p.jornada.abierta ? "encendida" : ""}">${p.jornada.abierta ? "Al aire" : "Fuera"}</span>`;
    let acta;
    if (!p.acta) acta = p.invitacion ? h`<span class="estado neutro">PASE SIN USAR</span> <small>vence ${fechaHora(p.invitacion.vence_en)}</small> <button class="boton chico" data-accion="anular-pase" data-id="${p.invitacion.id}">Anular</button>` : h`<span class="estado neutro">SIN FIRMAR</span>`;
    else if (!p.acta.contrafirmada_en) acta = h`<span class="estado esp">FALTA TU FIRMA</span> <button class="boton chico piloto" data-accion="contrafirmar" data-id="${p.acta.id}">Firmar</button>`;
    else acta = h`<span class="estado ok">COMPLETA</span> <button class="boton chico" data-accion="ver-acta" data-id="${p.acta.id}">Ver</button>`;
    const acciones = p.estado === "terminada" ? "" : h`
      ${p.estado !== "invitada" ? h`<button class="boton chico" data-accion="persona-estado" data-id="${p.id}" data-a="${p.estado === "pausa" ? "activa" : "pausa"}">${p.estado === "pausa" ? "Reanudar" : "Pausar"}</button>` : ""}
      <button class="boton chico" data-accion="pase-de-nuevo" data-id="${p.id}">Pase nuevo</button>
      <button class="boton chico peligro" data-accion="cortar" data-id="${p.id}" data-nombre="${p.nombre}">Cortar acceso</button>`;
    return h`<tr><td class="n">${String(p.numero).padStart(4, "0")}</td>
      <td><div class="persona"><span class="iniciales ${claseRol(p.rol)}">${iniciales(p.nombre)}</span><div>${p.nombre}<small>${p.cargo}${p.diaCargo ? ` · día ${p.diaCargo.dia} de ${p.diaCargo.total}` : ""} · visto ${cuando(p.ultimaVez)}</small></div></div></td>
      <td>${ahora}</td><td><div class="contrato">${acta}</div></td><td><div class="contrato">${acciones}</div></td></tr>`;
  }
  function vistaProduccion() {
    const d = S.prod;
    if (!d) return h`${tope("Producción")}<p class="cargando">CARGANDO…</p>`;
    const vigentes = d.personas.filter((p) => p.estado !== "terminada");
    const hoy = hoyIso();
    const pase = S.ultimoPase;
    return h`${tope("Producción")}<div class="marco">${llamado()}
      <div class="prod">
        <div class="col-der">
          <section class="bloque" aria-label="Hoja de llamado del equipo">
            <div class="bloque-cab"><h2>Hoja de llamado <span>· ${vigentes.filter((p) => p.estado !== "invitada").length} de ${d.vacantes} vacantes</span></h2></div>
            ${d.personas.length ? h`<div class="tabla-wrap"><table><thead><tr><th>Nº</th><th>Persona</th><th>Ahora</th><th>Acta</th><th></th></tr></thead><tbody>${d.personas.map(filaPersona)}</tbody></table></div>`
              : h`<div class="en-blanco"><span class="hueco">0</span><b>Nadie en el equipo todavía</b><p>Imprime el primer pase en el panel de la derecha y mándale el enlace a la persona. Cuando lo valide y firme el acta, aparece aquí.</p></div>`}
          </section>
          <section class="bloque" aria-label="Esperan tu luz verde">
            <div class="bloque-cab"><h2>Esperan tu luz verde <span>· ${d.porAprobar.length + d.accesosPedidos.length}</span></h2></div>
            ${d.porAprobar.length || d.accesosPedidos.length ? h`<ul class="aprobar">
              ${d.porAprobar.map((i) => h`<li><div><b>${i.titulo}</b><small>${i.autor || ""} · ${i.fase === 2 ? "planteamiento listo" : "probada, lista para lanzar"}${i.version ? ` · para la ${i.version}` : ""}</small></div>
                <div class="acciones"><button class="boton chico" data-accion="ver-idea" data-id="${i.id}">Leer</button><button class="boton chico piloto" data-accion="mover" data-id="${i.id}" data-fase="${i.fase === 2 ? 3 : 6}">${i.fase === 2 ? "Luz verde" : "Lanzar"}</button></div></li>`)}
              ${d.accesosPedidos.map((a) => h`<li><div><b>${a.plataforma} para ${a.persona}</b><small>Pedido de conexión${a.permiso ? ` · ${a.permiso}` : ""}${a.motivo ? ` · ${a.motivo}` : ""}</small></div>
                <div class="acciones"><button class="boton chico" data-accion="acceso-estado" data-id="${a.id}" data-a="retirado">No</button><button class="boton chico piloto" data-accion="acceso-estado" data-id="${a.id}" data-a="concedido">Ya la invité</button></div></li>`)}</ul>`
              : h`<div class="en-blanco"><p>Nada espera tu luz verde.</p></div>`}
          </section>
          ${d.solicitudes.length ? h`<section class="bloque" aria-label="Solicitudes de datos"><div class="bloque-cab"><h2>Solicitudes de datos <span>· responder en 10 días hábiles</span></h2></div>
            <ul class="aprobar">${d.solicitudes.map((s) => h`<li><div><b>${s.persona} pide ${s.tipo === "borrar" ? "que se borren sus datos" : "corregir sus datos"}</b><small>${s.detalle || "Sin detalle"} · ${cuando(s.creada_en)}</small></div>
              <div class="acciones"><button class="boton chico piloto" data-accion="responder-solicitud" data-id="${s.id}">Ya respondí</button></div></li>`)}</ul></section>` : ""}
          <section class="bloque" aria-label="Bitácora de la cabina">
            <div class="bloque-cab"><h2>Bitácora</h2></div>
            ${d.bitacora.length ? h`<ul class="bitacora">${d.bitacora.map((b) => h`<li><span class="sev ${b.accion === "alerta" ? "alta" : b.accion.startsWith("acta") ? "v-d" : b.accion.startsWith("pase") ? "v-a" : "v-b"}"></span>
                <span class="tc">${diaClave(b.creada_en) === diaClave(Date.now()) ? "HOY" : fmt({ day: "2-digit", month: "short" }).format(b.creada_en).toUpperCase()}<br>${fmt({ hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }).format(b.creada_en)}</span>
                <div><b>${b.detalle || b.accion}</b><small>${b.aparato || ""}</small></div><span>${b.accion === "alerta" ? h`<span class="estado mal">ALERTA</span>` : ""}</span></li>`)}</ul>`
              : h`<div class="en-blanco"><p>La bitácora empieza con el primer pase que imprimas. Cada pase, firma y cambio queda aquí con su hora.</p></div>`}
          </section>
        </div>
        <div class="col-der">
          <section class="bloque" aria-label="Imprimir un pase nuevo">
            <div class="bloque-cab"><h2>Pase nuevo</h2></div>
            <form class="nuevo-pase" data-forma="pase">
              <div class="campo"><label for="n-nombre">Nombre</label><input class="control" id="n-nombre" required minlength="3" maxlength="100" placeholder="Nombre y apellido"></div>
              <div class="campo"><label for="n-rol">Vacante</label><select class="control" id="n-rol"><option value="supervisor">Supervisor de ejecución / Planificador</option><option value="operaciones">Operaciones</option></select></div>
              <div class="fila-campos"><div class="campo"><label for="n-desde">Desde</label><input class="control" id="n-desde" type="date" required value="${hoy}"></div>
                <div class="campo"><label for="n-hasta">Hasta</label><input class="control" id="n-hasta" type="date" required value="${sumarDias(hoy, 59)}"></div></div>
              <div class="campo"><span class="etq">Días · máximo de lunes a viernes</span><div class="dias-elegir">
                ${["L", "M", "X", "J", "V"].map((t, k) => h`<label><input type="checkbox" name="dia" value="${k + 1}" checked aria-label="${["lunes", "martes", "miércoles", "jueves", "viernes"][k]}"><span>${t}</span></label>`)}
                <label class="no"><input type="checkbox" disabled aria-label="sábado (no se trabaja)"><span>S</span></label><label class="no"><input type="checkbox" disabled aria-label="domingo (no se trabaja)"><span>D</span></label></div></div>
              <div class="fila-campos"><div class="campo"><label for="n-entrada">Entrada</label><input class="control" id="n-entrada" type="time" value="08:00" required></div>
                <div class="campo"><label for="n-salida">Salida</label><input class="control" id="n-salida" type="time" value="17:00" required></div></div>
              <p class="error-campo" id="error-pase-nuevo" hidden></p>
              <button class="boton piloto" type="submit">Imprimir pase</button>
              ${pase ? resultadoPase(pase) : h`<p class="cuidado">Al imprimir salen el código, el enlace, el QR y el mensaje listo para mandar.</p>`}
            </form>
          </section>
          <section class="bloque" aria-label="Responsable de los datos">
            <div class="bloque-cab"><h2>Responsable de los datos</h2><button class="boton chico" data-accion="editar-responsable">Editar</button></div>
            <div class="responsable"><b>${d.responsable.nombre}</b><span>${d.responsable.ciudad}, Colombia</span><span>${d.responsable.correo}</span><small>Sale en la política de datos del equipo.</small></div>
          </section>
        </div>
      </div></div>`;
  }
  function resultadoPase(p) {
    const primer = p.nombre.split(" ")[0];
    const vence = fmt({ weekday: "long", day: "numeric", hour: "numeric", minute: "2-digit" }).format(p.venceEn);
    const mensaje = `Hola ${primer}, este es tu pase para la cabina de Life Music: ${p.enlace} . Sirve una sola vez y vence el ${vence}. Ábrelo cuando vayas a empezar.`;
    return h`<div class="pase-impreso"><small>Pase Nº ${String(p.numero).padStart(4, "0")} · un solo uso</small><small>AAA</small><div class="cod">${p.codigo}</div><div class="vence">Vence el ${vence}.</div></div>
      <div class="enlace-pase">
        <div class="rotulo">Enlace para ${primer}</div>
        <div class="url" id="url-pase">${p.enlace}</div>
        <div class="acciones"><button type="button" class="boton chico piloto" data-accion="copiar" data-texto="${p.enlace}">Copiar enlace</button><button type="button" class="boton chico" data-accion="compartir" data-texto="${mensaje}">Compartir…</button><button type="button" class="boton chico" data-accion="ver-qr" aria-pressed="false">Mostrar QR</button></div>
        <div class="qr-caja" id="qr" hidden><canvas id="qr-lienzo" width="232" height="232" aria-label="Código QR del enlace"></canvas><small>Para que lo escanee con su teléfono si está contigo.</small></div>
        <div class="rotulo">Mensaje listo para mandar</div>
        <p class="mensaje">${mensaje}</p>
        <p class="cuidado">Mándalo por un chat privado, nunca en un grupo. El enlace sirve una sola vez; si se filtra, anúlalo en la hoja de llamado y sale otro.</p>
        <div class="acciones"><button type="button" class="boton chico" data-accion="copiar" data-texto="${mensaje}">Copiar el mensaje</button><button type="button" class="boton chico" data-accion="olvidar-pase">Listo, ya lo mandé</button></div>
      </div>`;
  }
  function pintarQr() {
    const c = document.getElementById("qr-lienzo");
    if (!c || !window.qrcode || !S.ultimoPase) return;
    const qr = window.qrcode(0, "M"); qr.addData(S.ultimoPase.enlace); qr.make();
    const g = c.getContext("2d"), n = qr.getModuleCount(), m = 2;
    const celda = Math.floor(c.width / (n + m * 2)), desde = Math.floor((c.width - celda * (n + m * 2)) / 2);
    g.fillStyle = "#fff"; g.fillRect(0, 0, c.width, c.height); g.fillStyle = "#17201B";
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (qr.isDark(y, x)) g.fillRect(desde + (x + m) * celda, desde + (y + m) * celda, celda, celda);
  }

  // ── Sala de control (2.0) ──────────────────────────────────────────────────
  // Maqueta aprobada por CG el 18-10: Brand/maquetas/2026-10-18-sala-de-control.html.
  // Ficha: docs/versiones/2.0/cabina-sala-de-control.md. Estilos en sala.css (todo bajo .sala).
  const CAPA_COLOR = ["c-d", "c-e", "c-a", "c-c", "c-b"]; // sin capa, funciones, desarrollo, optimizacion, mantenimiento
  const CAPAS = ["Sin capa", "Funciones", "Desarrollo", "Optimización", "Mantenimiento"];
  const FRENTE_COLOR = { app: "c-e", tv: "c-a", carro: "c-b", web: "c-c", diseno: "c-d" };
  const COLOR_GENTE = ["c-a", "c-b", "c-e", "c-d"];
  const ESTADO_FRENTE = { "en-marcha": "en marcha", "espera-cg": "espera a CG", "en-pausa": "en pausa", lista: "lista" };
  const REQUIERE = { tv: "Necesitas un TV con Chromecast o una pantalla para transmitir.", descargada: "Necesitas una canción descargada con letra por línea.", carro: "Necesitas el radio del carro." };
  const nombreToma = (t) => `prueba.${t.numero}`;
  const horaCorta = (ms) => fmt({ hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(ms);
  const diaMes = (ms) => fmt({ day: "2-digit", month: "short" }).format(ms).replace(".", "").toUpperCase();
  const fechaClaqueta = (ms) => { const [d, m, a] = diaClave(ms).split("/"); return `${d}·${m}·${a.slice(2)}`; };
  const hoyOdia = (ms) => (diaClave(ms) === diaClave(Date.now()) ? horaCorta(ms) : fmt({ day: "numeric", month: "short" }).format(ms).replace(".", ""));
  const nombrePropio = (n) => (n || "").split(/\s+/)[0].replace(/^./, (c) => c.toUpperCase());
  const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;
  const segs = (total, f, x, n) => h`<span class="segs" aria-hidden="true">${Array.from({ length: total }, (_, i) => h`<i class="${i < f ? "f" : i < f + x ? "x" : i < f + x + n ? "n" : ""}"></i>`)}</span>`;
  const barrasVu = (n, on, alto = 99) => h`<span class="vu" aria-hidden="true">${Array.from({ length: n }, (_, i) => h`<b class="${i < on ? (i >= alto ? "on alto" : "on") : ""}"></b>`)}</span>`;
  const escalaLed = (n, on, pico = 99) => h`<span class="esc" aria-hidden="true">${Array.from({ length: n }, (_, i) => h`<b class="${i < on ? (i >= pico ? "on pico" : "on") : ""}"></b>`)}</span>`;
  const telefonoCorto = (t) => (t || "").split(" · ")[0];

  function fechaGrande() {
    const ahora = Date.now();
    const semana = S.yo.semana ? S.yo.semana.split("-W")[1] : "";
    return h`<div class="fecha"><span class="d num">${fmt({ day: "numeric" }).format(ahora)}</span><div class="dm"><span>${fmt({ weekday: "long" }).format(ahora)}</span><span>${fmt({ month: "long" }).format(ahora)}</span><small>${fmt({ year: "numeric" }).format(ahora)} · semana ${semana}</small></div></div>`;
  }
  function topeSala() {
    const p = yo();
    const nav = esDuenio() && S.yo.contratacion
      ? h`<nav class="pestanas" aria-label="Secciones">${[["sala", "Sala de control"], ["produccion", "Equipo y contratación"], ["cabina", "Sesión y operación"]]
          .map(([k, t]) => h`<button data-accion="pestana" data-a="${k}" ${S.pestana === k ? crudo('aria-current="page"') : ""}>${t}</button>`)}</nav>` : "";
    return h`<header class="tope"><div class="sello"><b>LIFE MUSIC</b><span>Sala de control</span></div>${nav}
      <div class="tope-der"><div class="quien"><div class="nombre"><b>${esDuenio() ? "CG" : nombrePropio(p.nombre)}</b><small>${p.cargo}</small></div>
        <span class="iniciales ${claseRol(p.rol)}">${esDuenio() ? "CG" : iniciales(p.nombre)}</span></div></div></header>`;
  }

  /** La claqueta de la toma: siempre arriba, aunque no haya toma (decision de CG). */
  function claqueta({ toma, trae, ultima, version, modo, mio }) {
    const palos = h`<div class="palos arriba"></div><div class="palos"></div>`;
    if (!toma) {
      return h`<div class="claqueta">${palos}<div class="pizarra">
        <div class="pz-fila f2"><div><div class="pz-eti">Producción</div><div class="pz-val">Life Music ${version}</div></div><div><div class="pz-eti">Toma</div><div class="pz-val"><b>—</b></div></div></div>
        <div class="pz-vacio"><b>Sin toma al aire</b>${ultima ? `La última publicada fue ${nombreToma(ultima)}, el ${fmt({ day: "numeric", month: "long" }).format(ultima.publicadaEn)}. ` : "Todavía no se ha publicado ninguna toma. "}${modo === "sup" ? "Cuando Camilo suba la próxima, aquí aparece tu hoja de pruebas." : "Cuando haya un APK para probar, sale aquí con su hoja."}</div>
      </div></div>`;
    }
    const g = toma.grupos || {};
    const partes = [g.nuevo && plural(g.nuevo, "nuevo", "nuevos"), g.siempre && `${g.siempre} de la ronda de siempre`, g.volver && `${g.volver} para volver a probar`].filter(Boolean);
    const queProbar = partes.length > 1 ? `${partes.slice(0, -1).join(", ")} y ${partes[partes.length - 1]}` : partes.join("");
    const chips = trae && trae.length
      ? h`<div class="trae">${trae.map((i) => h`<span class="${CAPA_COLOR[i.capa || 0]}"><i>●</i>${i.titulo}</span>`)}</div>`
      : h`<div class="pz-val chico">${toma.trae || "—"}</div>`;
    // La toma privada se baja de la API de la sala (con la sesion); en local, del puerto de prueba.
    const privada = !!toma.enlace && toma.enlace.startsWith("/equipo/api/");
    const descarga = privada ? API + toma.enlace.slice("/equipo/api".length) : toma.enlace;
    let abajo;
    if (modo === "sup") {
      const r = mio || { total: toma.total || 0, funciona: 0, falla: 0, noPude: 0, faltan: 0 };
      const detalle = [`${r.funciona + r.falla + r.noPude} DE ${r.total}`, r.falla && plural(r.falla, "FALLA", "FALLAS"), r.noPude && `${r.noPude} NO PUDE`, "SE GUARDA SOLO"].filter(Boolean).join(" · ");
      abajo = h`<div class="bajar"><div><div class="pz-eti">Tu avance</div>${segs(r.total, r.funciona, r.falla, r.noPude)}<small>${detalle}</small></div>
        ${toma.enlace ? (privada ? h`<a class="boton piloto" href="${descarga}" download>↓ Bajar el APK de prueba</a>`
            : h`<a class="boton piloto" href="${descarga}" target="_blank" rel="noopener noreferrer">↓ Bajar el APK de prueba</a>`)
          : h`<span class="boton" aria-disabled="true">El APK de prueba todavía no está listo</span>`}</div>`;
    } else {
      const gente = toma.supervisores || [];
      const filas = gente.length ? gente.map((s, k) => {
        const hechos = s.funciona + s.falla + s.noPude;
        const nota = s.falla ? plural(s.falla, "FALLA", "FALLAS") : s.noPude ? `${s.noPude} NO PUDE` : s.faltan === 0 && s.total ? "COMPLETA" : "";
        return h`<div class="sup ${s.estado === "pausa" ? "pausa" : ""}"><span class="iniciales ${COLOR_GENTE[k % COLOR_GENTE.length]}">${iniciales(s.nombre)}</span>
          <div><b>${nombrePropio(s.nombre)}</b><small>${s.estado === "pausa" ? "En pausa" : s.telefono ? s.telefono.replace(/^\S+\s/, "") : "Sin teléfono todavía"}</small></div>
          ${segs(s.total, s.estado === "pausa" ? 0 : s.funciona, s.estado === "pausa" ? 0 : s.falla, s.estado === "pausa" ? 0 : s.noPude)}
          <div class="cuenta">${s.estado === "pausa" ? "—" : `${hechos} de ${s.total}`}<small>${nota || crudo("&nbsp;")}</small></div></div>`;
      }) : h`<div class="pz-vacio">Nadie está probando todavía. Cuando un supervisor marque su primer punto, su avance sale aquí.</div>`;
      abajo = h`<div class="sups">${filas}</div>
        <div class="pz-pie"><span>Cuando todo quede en verde, la toma pasa a «Lista para publicar». Publicar sigue siendo solo tuyo.</span><span class="aire lista ${toma.lista ? "encendida" : ""}">Lista para publicar</span></div>`;
    }
    return h`<div class="claqueta">${palos}<div class="pizarra">
      <div class="pz-fila f4"><div><div class="pz-eti">Producción</div><div class="pz-val">Life Music ${toma.version}</div></div>
        <div><div class="pz-eti">Toma</div><div class="pz-val"><b>${nombreToma(toma)}</b></div></div>
        <div class="solo-ancho"><div class="pz-eti">Fecha</div><div class="pz-val">${fechaClaqueta(toma.creadaEn)}</div></div>
        <div class="solo-ancho"><div class="pz-eti">Salió</div><div class="pz-val">${horaCorta(toma.creadaEn)}</div></div></div>
      <div class="pz-fila"><div><div class="pz-eti">Qué trae</div>${chips}</div></div>
      <div class="pz-fila f2"><div><div class="pz-eti">APK</div><div class="pz-val chico"><b>${toma.apk}</b> · ${!toma.enlace ? "todavía sin subir"
          : privada ? (modo === "sup" ? "solo para el equipo: no lo compartas" : h`<a href="${descarga}" download>versión de prueba privada · solo la sala</a>`)
          : modo === "sup" ? "versión de prueba en GitHub" : h`<a href="${descarga}" target="_blank" rel="noopener noreferrer">versión de prueba en GitHub</a>`}</div></div>
        <div class="solo-ancho"><div class="pz-eti">Qué probar</div><div class="pz-val chico"><b>${plural(toma.total || 0, "punto", "puntos")}${queProbar ? ":" : ""}</b> ${queProbar}</div></div></div>
      ${abajo}
    </div></div>`;
  }

  // La sala del dueño: cinco piezas del mismo objeto.
  function vistaSala() {
    const d = S.sala;
    if (!d) return h`<div class="sala">${topeSala()}<p class="cargando">SALA · CARGANDO…</p></div>`;
    const t = d.toma;
    const activos = t ? t.supervisores.filter((s) => s.estado !== "pausa") : [];
    const probados = activos.reduce((a, s) => a + s.funciona + s.falla + s.noPude, 0);
    const llamadoSala = h`<div class="llamado">${fechaGrande()}
      <dl class="lecturas">
        <div><dt>Toma al aire</dt><dd>${t ? nombreToma(t) : "—"}<small>${t ? hora(t.creadaEn) : "sin toma"}</small></dd></div>
        <div><dt>Versión</dt><dd>${d.versionObjetivo}<small>${t ? "en pruebas" : "en desarrollo"}</small></dd></div>
        <div><dt>Puntos probados</dt><dd>${t ? `${probados} / ${t.total * activos.length}` : "—"}<small>${plural(activos.length, "supervisor", "supervisores")}</small></dd></div>
        <div><dt>Incidencias</dt><dd>${d.incidencias.length}<small>${d.incidencias.length === 1 ? "abierta" : "abiertas"}</small></dd></div></dl>
      <div class="lamparas"><span class="aire grande ${t ? "encendida" : ""}">${t ? "Al aire" : "Fuera del aire"}</span></div></div>`;
    const estadoToma = t ? (t.lista ? h`<span class="estado ok">LISTA PARA PUBLICAR</span>`
      : h`<span class="estado esp">FALTA PROBAR${t.conFalla ? ` · ${plural(t.conFalla, "FALLA ABIERTA", "FALLAS ABIERTAS")}` : ""}</span>`) : h`<span class="estado gris">SIN TOMA</span>`;
    return h`<div class="sala">${topeSala()}<div class="marco">${llamadoSala}
      <div class="fila-2">
        <section class="bloque sin-marco" aria-label="Al aire: la toma para probar"><div class="bloque-cab"><h2><span class="pz">1</span>Al aire <small>· la toma para probar</small></h2>${estadoToma}</div>
          ${claqueta({ toma: t, trae: t && t.trae, ultima: d.ultimaPublicada, version: d.versionObjetivo, modo: "duenio" })}</section>
        ${bloqueHoy()}
      </div>
      <div class="fila-2 ancha">${bloqueConsola()}${bloqueMedidores()}</div>
      ${bloqueCinta()}
      <div class="fila-2">${bloqueBitacoraSala()}${bloqueContratacion()}</div>
      ${bloqueResenas()}
      <div class="pie-sala"><span>Sin analítica · una sola cookie de sesión · política de datos (Ley 1581)</span>
        <nav><button data-ir="politica">Política de datos</button><button data-accion="codigo-aparato">Enlazar otro aparato</button><button data-accion="salir">Salir</button></nav></div>
    </div></div>`;
  }

  function bloqueHoy() {
    const d = S.sala;
    const frentes = [...d.frentes].sort((a, b) => b.actualizado_en - a.actualizado_en);
    const hoy = (f) => diaClave(f.actualizado_en) === diaClave(Date.now());
    const grabando = frentes.filter(hoy).length;
    const filas = [];
    if (d.alAire) filas.push(h`<li><span class="led rojo"></span><div><b>Al aire · ${(d.alAire.sesion || "").replace(/^Life Music · /, "")}</b><p>${d.alAire.texto}</p></div><time>${d.alAire.en ? hoyOdia(d.alAire.en) : ""}</time></li>`);
    for (const f of frentes) {
      const led = hoy(f) ? "verde" : f.estado === "en-pausa" ? "" : f.estado === "espera-cg" ? "ambar" : "verde";
      filas.push(h`<li><span class="led ${led}"></span><div><b>${f.nombre}</b><p>${f.avance || ESTADO_FRENTE[f.estado]}</p></div><time>${hoyOdia(f.actualizado_en)}</time></li>`);
    }
    return h`<section class="bloque" aria-label="Hoy en la sala"><div class="bloque-cab"><h2>Hoy en la sala</h2>
      ${grabando ? h`<span class="estado ok">${grabando === 1 ? "1 CABINA GRABANDO" : `${grabando} CABINAS GRABANDO`}</span>` : h`<span class="estado gris">NADIE GRABA HOY</span>`}</div>
      ${filas.length ? h`<ul class="cabinas">${filas}</ul>` : h`<div class="en-blanco"><p>Cuando una sesión cierre su primera tanda, sale aquí.</p></div>`}</section>`;
  }

  function bloqueConsola() {
    const fs = S.sala.frentes;
    const cuenta = (e) => fs.filter((f) => f.estado === e).length;
    const nivelVu = (f) => {
      if (f.estado === "en-pausa") return 0;
      const dias = Math.floor((Date.now() - f.actualizado_en) / 86400000);
      return dias <= 0 ? 9 : dias === 1 ? 7 : dias <= 3 ? 5 : dias <= 7 ? 3 : 1;
    };
    const teclas = (f) => h`<div class="teclas-c" aria-label="Estado: ${ESTADO_FRENTE[f.estado]}"><span class="marcha ${f.estado === "en-marcha" || f.estado === "lista" ? "on" : ""}">MARCHA</span><span class="cue ${f.estado === "espera-cg" ? "on" : ""}">CUE</span><span class="mute ${f.estado === "en-pausa" ? "on" : ""}">MUTE</span></div>`;
    const led = (f) => (f.estado === "en-marcha" || f.estado === "lista" ? "verde" : f.estado === "espera-cg" ? "ambar" : "");
    const canales = fs.map((f, k) => h`<div class="canal ${f.estado === "en-pausa" ? "pausa" : ""}">
      <div class="ch"><span>CH ${k + 1}</span><span class="led ${led(f)}"></span></div>
      <div class="lcd">${f.version || "—"}<small>${ESTADO_FRENTE[f.estado]}</small></div>${teclas(f)}
      <div class="tira">${barrasVu(12, nivelVu(f), 9)}<div class="fader" role="img" aria-label="Avance ${f.progreso || 0} %"><span class="cap" data-top="${100 - (f.progreso || 0)}%"></span><span class="pct" data-top="${100 - (f.progreso || 0)}%">${f.progreso || 0}%</span></div></div>
      <div class="masking"><b>${f.nombre}</b><span class="ult">${f.avance || ""}${f.avance ? " · " : ""}${hoyOdia(f.actualizado_en)}</span>${f.espera ? h`<span class="esp">Espera: ${f.espera}</span>` : ""}</div></div>`);
    const filasM = fs.map((f) => h`<div class="canal-m"><div class="masking"><b>${f.nombre}</b></div><div class="der-m">
      <div class="lcd-led"><div class="lcd">${f.version || "—"}</div><span class="led ${led(f)}"></span></div>${teclas(f)}
      <p>${f.avance || ""}${f.avance ? " · " : ""}${hoyOdia(f.actualizado_en)} · ${f.progreso || 0}%</p>${f.espera ? h`<p class="esp">Espera: ${f.espera}</p>` : ""}</div></div>`);
    return h`<section class="bloque" aria-label="La consola: un canal por frente"><div class="bloque-cab"><h2><span class="pz">2</span>La consola <small>· un canal por frente</small></h2>
        <div class="medidor">${cuenta("en-marcha") + cuenta("lista")} en marcha · ${cuenta("espera-cg")} ${cuenta("espera-cg") === 1 ? "espera" : "esperan"} a CG · ${cuenta("en-pausa")} en pausa</div></div>
      ${fs.length ? h`<div class="consola">${canales}</div><div class="consola-m">${filasM}</div>
        <div class="consola-pie"><span><i class="v"></i>MARCHA · en marcha</span><span><i class="a"></i>CUE · espera a CG</span><span><i class="r"></i>MUTE · en pausa</span><span>Fader · cuánto lleva</span></div>`
        : h`<div class="en-blanco"><p>Los frentes aparecen cuando cada sesión cierra su primera tanda.</p></div>`}</section>`;
  }

  function bloqueMedidores() {
    const m = S.med, mon = S.mon;
    // undefined: todavia midiendo; null: no respondio.
    const sinDato = (que) => h`<p class="med-pie">${m === undefined ? "Midiendo…" : `No se pudo leer ${que} ahora.`}</p>`;
    let descargas = sinDato("GitHub");
    if (m && m.descargas) {
      // Las tres versiones publicadas mas recientes (sin las pruebas ni el carro).
      const estables = m.descargas.versiones.filter((v) => !v.prueba && /^v\d/.test(v.tag)).sort((a, b) => Date.parse(b.fecha) - Date.parse(a.fecha)).slice(0, 3);
      const max = Math.max(1, ...estables.map((v) => v.descargas));
      descargas = h`${estables.map((v) => h`<div class="ver"><span>${v.tag.replace(/^v/, "")}</span>${escalaLed(24, Math.max(1, Math.round((v.descargas / max) * 22)), 20)}<strong>${v.descargas}</strong></div>`)}
        <p class="med-pie">${m.descargas.total} en total, contando las pruebas del carro.</p>`;
    }
    const c = m && m.calificaciones;
    const calif = c ? (c.promedio != null
      ? h`<div class="grande-n">${String(c.promedio).replace(".", ",")}<small>de 5 · ${plural(c.total, "voto", "votos")}</small></div><div class="estrellas" aria-hidden="true">${"★".repeat(Math.round(c.promedio))}${"☆".repeat(5 - Math.round(c.promedio))}</div>`
      : h`<div class="grande-n">—<small>${plural(c.total, "voto", "votos")}</small></div><p class="med-pie">El promedio se muestra cuando haya más votos.</p>`)
      : sinDato("las calificaciones");
    const pre = m && m.prerregistro;
    const prer = pre ? h`<div class="digitos num">${[...String(Math.min(999, pre.contador || 0)).padStart(3, "0")].map((x) => h`<span>${x}</span>`)}</div>
      <p class="med-pie">${pre.abierto ? `Meta: ${pre.meta}` : "Concurso en pausa"}</p>` : sinDato("el contador");
    let serv = h`<p class="med-pie">${mon === undefined ? "Midiendo…" : "No se pudo medir ahora."}</p>`, servOk = true;
    if (mon) {
      servOk = mon.servicios.every((s) => s.ok);
      serv = h`<div class="serv">${mon.servicios.map((s) => {
        const nivel = s.ok && s.ms != null ? Math.min(9, Math.max(2, 9 - Math.floor(s.ms / 220))) : 0;
        return h`<div>${barrasVu(9, nivel, s.ms > 1000 ? 0 : 7)}<b>${s.ok ? s.ms : "—"}<span> ${s.ok ? "ms" : ""}</span></b><small>${s.nombre}</small></div>`;
      })}</div>`;
    }
    return h`<section class="bloque" aria-label="Medidores"><div class="bloque-cab"><h2><span class="pz">4</span>Medidores</h2><span class="estado gris">CADA 10 MIN</span></div>
      <div class="puente">
        <div class="med ancho"><div class="rotulo"><span>Descargas</span><span class="estado ok">GITHUB · REAL</span></div>${descargas}</div>
        <div class="med"><div class="rotulo"><span>Calificaciones</span></div>${calif}</div>
        <div class="med der"><div class="rotulo"><span>Prerregistros</span></div>${prer}</div>
        <div class="med ancho"><div class="rotulo"><span>Servicios</span>${mon ? (servOk ? h`<span class="estado ok">TODO EN ORDEN</span>` : h`<span class="estado mal">HAY UNO CAÍDO</span>`) : ""}</div>${serv}</div>
      </div></section>`;
  }

  function bloqueCinta() {
    const d = S.sala;
    const ideas = d.cinta;
    const enCinta = ideas.filter((i) => !i.descartada && i.capa);
    const descartadas = ideas.filter((i) => i.descartada);
    const pausadas = ideas.filter((i) => !i.descartada && !i.capa);
    const hechas = enCinta.filter((i) => i.fase >= 5).length, enEjec = enCinta.filter((i) => i.fase === 4).length;
    const dos = (n) => String(n).padStart(2, "0");
    const clip = (i) => h`<button class="clip ${i.fase === 4 ? "vivo" : ""}" data-accion="ver-clip" data-id="${i.id}" title="${i.titulo}"><b>${i.titulo}</b>
      ${i.fase === 5 ? h`<span class="marca-ok">BETA</span>` : i.fase === 6 ? h`<span class="marca-ok">✓</span>` : ""}<span class="onda" data-onda="${i.numero}" data-largo="0.6"></span></button>`;
    const regla = h`<div class="regla-c"><div>Pista</div><div><span class="n">1</span>Idea</div><div><span class="n">2</span>Planteamiento</div>
      <div class="puerta"><span>Luz verde<small>solo CG</small></span></div><div><span class="n">3</span>Ejecución</div><div><span class="n">4</span>Hecha · falta probar</div>
      <div class="puerta"><span>Lanzada<small>solo CG publica</small></span></div></div>`;
    const pistas = [1, 2, 3, 4].map((capa) => {
      const suyas = enCinta.filter((i) => i.capa === capa);
      return h`<div class="pista ${CAPA_COLOR[capa]}"><div class="pista-cab"><span class="n">${dos(capa)}</span><span class="color"></span>
          <div><b>${CAPAS[capa]}</b><small>capa ${capa} · ${plural(suyas.length, "idea", "ideas")}</small></div></div>
        ${[1, 2, 3, 4, 5, 6].map((f) => h`<div class="celda ${f === 3 || f === 6 ? "puerta" : ""}">${suyas.filter((i) => i.fase === f).map(clip)}</div>`)}</div>`;
    });
    return h`<section class="bloque" aria-label="La cinta de la ${d.versionObjetivo}"><div class="bloque-cab">
        <h2><span class="pz">3</span>La cinta de la ${d.versionObjetivo} <small>· ${plural(ideas.length, "idea", "ideas")} · 4 pistas</small></h2>
        <div class="transporte" aria-hidden="true"><span class="carrete"></span><span class="botones-t"><span>◀◀</span><span>■</span><span>▶</span><span class="rec">●</span></span>
          <span class="contador">${d.versionObjetivo} · ${dos(hechas)} HECHAS · ${dos(enEjec)} EN EJECUCIÓN</span><span class="carrete"></span></div></div>
      <div class="cinta-scroll"><div class="cinta">${regla}${pistas}</div></div>
      <div class="bandeja">${descartadas.length || pausadas.length ? h`<span>Fuera de la cinta:</span>` : ""}
        ${descartadas.map((i) => h`<button class="clip ${CAPA_COLOR[i.capa || 0]}" data-accion="ver-clip" data-id="${i.id}"><b>${i.titulo}</b></button>`)}${descartadas.length ? h`<span>${descartadas.length === 1 ? "descartada" : "descartadas"}${pausadas.length ? " ·" : ""}</span>` : ""}
        ${pausadas.map((i) => h`<button class="clip pausa ${CAPA_COLOR[0]}" data-accion="ver-clip" data-id="${i.id}"><b>${i.titulo}</b></button>`)}${pausadas.length ? h`<span>en pausa</span>` : ""}
        <span class="der"><span>Las franjas rojas son tus puertas: dar luz verde y lanzar.</span><button class="boton chico" data-accion="nueva-idea">+ Idea</button></span></div></section>`;
  }

  function bloqueBitacoraSala() {
    const d = S.sala;
    const terminado = d.avances.slice(0, 6);
    const inc = d.incidencias;
    return h`<section class="bloque" aria-label="Bitácora"><div class="bloque-cab"><h2><span class="pz">5</span>Bitácora</h2><button class="boton chico" data-accion="nueva-incidencia">+ Nueva incidencia</button></div>
      <div class="bit">
        <div><h4><span class="led verde"></span><span class="rotulo">Terminado</span></h4>
          ${terminado.length ? h`<ul>${terminado.map((a) => h`<li class="${FRENTE_COLOR[a.frente] || "c-d"}"><span class="sev"></span><time>${diaMes(a.hecho_en)}</time><div><b>${a.titulo}</b>${a.detalle ? h`<small>${a.detalle}</small>` : ""}</div><span class="estado ok">HECHO</span></li>`)}</ul>`
            : h`<p class="vacio">Lo que se termine en cada tanda aparece aquí.</p>`}</div>
        <div><h4><span class="led ${inc.length ? "rojo" : ""}"></span><span class="rotulo">Incidencias abiertas · ${inc.length}</span></h4>
          ${inc.length ? h`<ul>${inc.map((i) => h`<li class="toca c-${i.gravedad}" data-accion="ver-incidencia" data-id="${i.id}" tabindex="0" role="button"><span class="sev"></span><time>${diaMes(i.creada_en)}<br>${horaCorta(i.creada_en)}</time>
              <div><b>${i.punto ? i.titulo.replace(/^Toma \d+ · /, "") : i.titulo}</b><small>${i.punto ? `${nombrePropio(i.autor)} · ${telefonoCorto(i.telefono)} · punto ${i.punto} de prueba.${i.toma}` : i.autor || ""}</small></div>
              <span class="estado ${i.gravedad === "alta" ? "mal" : i.gravedad === "media" ? "esp" : "gris"}">${i.gravedad.toUpperCase()}</span></li>`)}</ul>`
            : h`<p class="vacio">Ninguna abierta. Las fallas que marquen los supervisores llegan aquí solas.</p>`}</div>
      </div></section>`;
  }

  // Las reseñas de lifemusic.app/calificar: salen en la web solo si Camilo las publica aquí.
  function bloqueResenas() {
    const r = S.sala.resenas;
    if (!r) return "";
    const pend = r.lista.filter((x) => x.estado === "pendiente");
    const ms = (t) => Date.parse(t.replace(" ", "T") + "Z");
    const notas = (n) => h`<span class="notas-r" role="img" aria-label="${n} de 5 notas">${[1, 2, 3, 4, 5].map((k) => h`<svg viewBox="0 0 20 16" class="${k <= n ? "llena" : ""}" aria-hidden="true"><ellipse cx="10" cy="8" rx="7.5" ry="5.5" transform="rotate(-22 10 8)"/></svg>`)}</span>`;
    const boton = (x, a, texto) => h`<button class="boton chico" data-accion="resena" data-id="${x.id}" data-a="${a}">${texto}</button>`;
    const fila = (x) => h`<li class="${x.estado}"><div class="r-cab"><b>${x.nombre}</b>${notas(x.notas)}<time>${diaMes(ms(x.creada_en))} · ${horaCorta(ms(x.creada_en))}</time></div>
      <p class="${x.comentario ? "" : "sin"}">${x.comentario || "Sin comentario, solo las notas."}</p>
      ${x.correo ? h`<small>${x.correo} · solo lo ves tú, nunca se publica</small>` : ""}
      <div class="r-acciones">${x.estado === "pendiente" ? h`${boton(x, "aprobada", "Publicar")}${boton(x, "oculta", "Ocultar")}`
        : h`<span class="estado ${x.estado === "aprobada" ? "ok" : "gris"}">${x.estado === "aprobada" ? "PUBLICADA" : "OCULTA"}</span>${boton(x, x.estado === "aprobada" ? "oculta" : "aprobada", x.estado === "aprobada" ? "Ocultar" : "Publicar")}`}</div></li>`;
    const c = r.cuenta || {};
    return h`<section class="bloque" aria-label="Reseñas de la web"><div class="bloque-cab"><h2><span class="pz">6</span>Reseñas de la web <small>· lifemusic.app/calificar</small></h2>
        ${pend.length ? h`<span class="estado esp">${plural(pend.length, "POR REVISAR", "POR REVISAR")}</span>` : h`<span class="estado gris">AL DÍA</span>`}</div>
      ${r.lista.length ? h`<ul class="resenas">${r.lista.map(fila)}</ul>` : h`<div class="en-blanco"><p>Cuando alguien califique Life Music en la web, llega aquí. Sale en público solo si tú la publicas.</p></div>`}
      <p class="med-pie resenas-pie">${plural(c.aprobada || 0, "publicada", "publicadas")} · ${plural(c.oculta || 0, "oculta", "ocultas")} · aquí se ven las pendientes y lo que revisaste en la última semana.</p></section>`;
  }

  function bloqueContratacion() {
    const on = !!S.sala.contratacion;
    return h`<section class="bloque" aria-label="Equipo y contratación"><div class="interruptor">
        <button class="switch" role="switch" aria-checked="${on}" aria-label="Equipo y contratación" data-accion="contratacion" data-a="${on ? "0" : "1"}"></button>
        <div><b>Equipo y contratación</b><small>Pases, acta con firma, jornada, accesos y revisión semanal. Guardados, sin borrar.</small></div>
        <span class="estado ${on ? "ok" : "gris"}">${on ? "ENCENDIDO" : "APAGADO"}</span></div>
      ${on ? h`<div class="interruptor-pie"><button class="boton chico" data-accion="pestana" data-a="produccion">Abrir Equipo y contratación</button></div>` : ""}</section>`;
  }

  // ── La hoja del supervisor ──
  function vistaHoja() {
    const p = yo();
    const d = S.hoja;
    const primera = S.yo.requisito === "politica" || !d || !d.telefono || S.cambiarTelefono;
    if (primera) return h`<div class="sala">${topeSala()}<div class="marco">${vistaPrimera(d && d.telefono)}</div></div>`;
    const t = d.toma;
    const pausa = p.estado === "pausa";
    const actual = t ? (d.pruebas.find((x) => !x.mio) || {}).id : null;
    const grupos = [["nuevo", "Lo nuevo de esta toma"], ["siempre", "La ronda de siempre"], ["volver", "Volver a probar"]].map(([g, titulo]) => {
      const suyos = d.pruebas.filter((x) => x.grupo === g);
      if (!suyos.length) return "";
      return h`<div class="grupo"><div class="grupo-cab"><span class="rotulo">${titulo}</span><span class="cuenta">${plural(suyos.length, "punto", "puntos")}</span></div>${suyos.map((x) => puntoHoja(x, x.id === actual, pausa))}</div>`;
    });
    const r = d.resumen;
    const tel = d.telefono;
    return h`<div class="sala">${topeSala()}<div class="marco">
      ${pausa ? h`<div class="franja-fuera"><span class="led"></span><span><b>Tu cargo está en pausa.</b> Puedes mirar tu hoja, pero no marcar. Habla con Camilo.</span></div>` : ""}
      <div class="hoja-cab"><div><div class="rotulo">${t ? `Toma ${nombreToma(t)} · ${fmt({ day: "numeric", month: "long" }).format(t.creadaEn)}` : "Sin toma al aire"}</div>
          <h1>Mi hoja de pruebas</h1><p>Prueba cada punto en tu teléfono y marca lo que pasó. Puedes hacerlo a cualquier hora: lo que marques se guarda solo.</p></div>
        <button class="mi-tel" data-accion="cambiar-telefono" aria-label="Cambiar mi teléfono"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M17 1H7c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-2-2-2zm0 18H7V5h10v14z"/></svg>
          <div><b>${tel.marca} ${tel.modelo}</b><small>ANDROID ${tel.android} · <u>CAMBIAR</u></small></div></button></div>
      ${claqueta({ toma: t && { ...t, total: d.pruebas.length, grupos: Object.fromEntries(["nuevo", "siempre", "volver"].map((g) => [g, d.pruebas.filter((x) => x.grupo === g).length])) }, trae: d.trae, ultima: d.ultimaPublicada, version: (t && t.version) || "2.0", modo: "sup", mio: r })}
      <div class="hoja"><div>${t ? grupos : ""}</div>
        <div class="col-der">
          ${r ? h`<section class="bloque avance" aria-label="Mi avance"><div class="rotulo">Mi avance</div><div class="grande-n">${r.funciona + r.falla + r.noPude}<small>de ${r.total}</small></div>${segs(r.total, r.funciona, r.falla, r.noPude)}
            <ul><li><span class="led verde"></span>${r.funciona === 1 ? "1 funciona" : `${r.funciona} funcionan`}</li><li><span class="led rojo"></span>${plural(r.falla, "falla", "fallas")}${r.falla ? " · ya están en la sala de CG" : ""}</li>
              <li><span class="led"></span>${r.noPude} no pude</li><li><span class="led hueco"></span>${r.faltan} por probar</li></ul></section>` : ""}
          <section class="bloque" aria-label="Mi historial"><div class="bloque-cab"><h2>Mi historial</h2></div><ul class="historial">
            ${d.historial.length ? d.historial.map((x) => {
              const hechos = x.funciona + x.falla + x.no_pude;
              return h`<li><div><b>prueba.${x.numero}</b><small>${fmt({ day: "numeric", month: "short" }).format(x.creada_en)} · ${tel.modelo}</small></div>
                <span class="estado ${x.falla ? "esp" : "ok"}">${hechos} / ${x.total}${x.falla ? ` · ${plural(x.falla, "FALLA", "FALLAS")}` : ""}</span></li>`;
            }) : h`<li><span class="vacio">Todavía no has cerrado ninguna toma.</span></li>`}</ul></section>
          <section class="bloque" aria-label="Mis incidencias"><div class="bloque-cab"><h2>Mis incidencias</h2><span class="estado gris">SOLO LEER</span></div><ul class="historial">
            ${d.incidencias.length ? d.incidencias.map((i) => h`<li><div><b class="texto">${i.titulo.replace(/^Toma \d+ · /, "")}</b><small>${i.punto ? `Punto ${i.punto} · prueba.${i.toma} · ` : ""}${hoyOdia(i.creada_en)}</small></div>
                <span class="estado ${i.abierta ? "esp" : "ok"}">${i.abierta ? "ABIERTA" : "ARREGLADA"}</span></li>`)
              : h`<li><span class="vacio">No has abierto ninguna. Cuando marques una falla, aquí ves si ya se arregló.</span></li>`}</ul></section>
          <div class="tus-datos-sala">Tus datos: tu nombre, tu teléfono y lo que marcas. Nada más. Política de datos (Ley 1581).
            <nav><button data-accion="mis-datos">Ver mis datos</button><button data-accion="solicitud" data-a="corregir">Pedir una corrección</button><button data-accion="solicitud" data-a="borrar">Pedir que se borren</button>
              ${S.yo.acta.firmada && !esDuenio() ? h`<button data-accion="ver-acta">Ver el acta</button>` : ""}<button data-ir="politica">Política</button><button data-accion="codigo-aparato">Enlazar otro aparato</button><button data-accion="salir">Salir</button></nav></div>
        </div></div>
    </div></div>`;
  }

  function puntoHoja(x, actual, pausa) {
    const e = x.mio && x.mio.estado;
    const pasos = (x.pasos || "").split(/(?<=[.!?»])\s+(?=[A-ZÁÉÍÓÚÑ¿¡«])/).map((s) => s.trim()).filter(Boolean);
    const etiqueta = x.titulo.includes(":") ? x.titulo.split(":")[0] : (x.idea || "").split(/\s+/)[0];
    const abierta = S.fallaAbierta === x.id;
    const boton = (estado, clase, texto) => h`<button class="${clase} ${e === estado ? "on" : ""}" data-accion="marcar" data-id="${x.id}" data-estado="${estado}" aria-pressed="${e === estado}" ${pausa ? crudo("disabled") : ""}>${texto}</button>`;
    return h`<div class="punto ${e === "funciona" || e === "no-pude" ? "hecho" : ""} ${e === "falla" ? "falla" : ""} ${actual ? "actual" : ""} ${pasos.length ? "" : "corto"}" id="punto-${x.id}">
      <span class="n">${String(x.orden).padStart(2, "0")}</span>
      <div><h5>${x.titulo}${x.idea && etiqueta ? h`<span class="idea ${CAPA_COLOR[x.capa || 0]}">${etiqueta}</span>` : ""}</h5>
        ${pasos.length ? h`<ol>${pasos.map((s) => h`<li>${s}</li>`)}</ol>` : ""}
        ${x.espera ? h`<div class="debe"><b>Debe pasar</b>${x.espera}</div>` : ""}
        ${x.requiere && REQUIERE[x.requiere] ? h`<span class="requiere">${REQUIERE[x.requiere]} Si no lo tienes, marca «No pude».</span>` : ""}</div>
      <div class="tri">${boton("funciona", "f", "✓ Funciona")}${boton("falla", "x", "✕ Falla")}${boton("no-pude", "n", "— No pude")}</div>
      ${abierta ? h`<div class="nota-falla"><label class="rotulo" for="nota-${x.id}">¿Qué pasó? En qué paso y qué viste</label>
          <textarea class="control" id="nota-${x.id}" maxlength="500" placeholder="Por ejemplo: en el paso 2 la letra no se ilumina, se queda quieta.">${(x.mio && x.mio.nota) || ""}</textarea>
          <div class="acciones"><button class="boton chico" data-accion="falla-cancelar">Cancelar</button><button class="boton chico piloto" data-accion="falla-guardar" data-id="${x.id}">Guardar la falla</button></div></div>`
        : e === "falla" ? h`<div class="notita">${x.mio.nota}<small>INCIDENCIA ABIERTA EN LA SALA · ${hoyOdia(x.mio.marcadoEn)}</small></div>` : ""}
    </div>`;
  }

  /** La primera vez (o al cambiar de teléfono): con qué teléfono pruebas y, si falta, la política. */
  function vistaPrimera(tel) {
    const politica = S.yo.requisito === "politica";
    const ver = tel ? tel.android : "";
    const marcas = ["Samsung", "Xiaomi", "Motorola", "Huawei", "Honor", "Oppo", "Realme", "Tecno", "Infinix", "Vivo", "Google", "OnePlus", "Nokia", "ZTE", "Alcatel", "Sony"];
    return h`<div class="primera"><div class="rotulo">${tel ? "Cambiar de teléfono" : "Primera vez"}</div><h1>¿Con qué teléfono pruebas?</h1>
      <p>Vale oro: Life Music tiene que funcionar en todos los teléfonos, no solo en uno. ${tel ? "Lo que marques desde ahora queda con el teléfono nuevo." : "Se pregunta una sola vez."}</p>
      <form data-forma="primera">
        <div class="campo"><label for="t-marca">Marca</label><input class="control" id="t-marca" list="t-marcas" required maxlength="40" autocomplete="off" value="${tel ? tel.marca : ""}"><datalist id="t-marcas">${marcas.map((m) => h`<option value="${m}"></option>`)}</datalist></div>
        <div class="campo"><label for="t-modelo">Modelo</label><input class="control" id="t-modelo" required maxlength="60" autocomplete="off" placeholder="Escríbelo como sale en Ajustes › Acerca del teléfono" value="${tel ? tel.modelo : ""}"></div>
        <div class="campo"><span class="etq">Android</span><div class="chips">${["9", "10", "11", "12", "13", "14", "15", "16", "17"].map((v) => h`<label><input type="radio" name="t-android" value="${v}" required ${v === ver ? crudo("checked") : ""} aria-label="Android ${v}"><span>${v}</span></label>`)}</div></div>
        ${politica ? h`<label class="acepto"><input type="checkbox" id="t-autorizo" required><span>Autorizo que se traten mis datos según la <button type="button" data-ir="politica">política de datos</button> (Ley 1581): mi nombre, mi teléfono y lo que marque.</span></label>
          <label class="acepto"><input type="checkbox" id="t-mayor" required><span>Soy mayor de edad.</span></label>` : ""}
        <p class="error-campo" id="error-primera" hidden></p>
        <button class="boton piloto" type="submit">Guardar y ver mi hoja</button>
        ${tel ? h`<button class="boton" type="button" data-accion="cancelar-telefono">Cancelar</button>` : ""}
      </form></div>`;
  }

  function verClip(id) {
    const i = S.sala && S.sala.cinta.find((x) => x.id === id);
    if (!i) return;
    const atras = i.fase - 1, adelante = i.fase + 1;
    const d = ventana({
      titulo: i.titulo,
      cuerpo: h`<p><b>Fase:</b> ${FASES[i.fase]}${i.descartada ? " · descartada" : ""} · <b>Capa:</b> ${CAPAS[i.capa || 0]}${i.version ? ` · para la ${i.version}` : ""}</p>
        ${i.para_que ? h`<p>${i.para_que}</p>` : ""}
        ${i.descartada ? "" : h`${campo("v-capa", "Capa (la pista de la cinta)", crudo(`<select class="control" id="v-capa">${CAPAS.map((c, k) => `<option value="${k}" ${k === (i.capa || 0) ? "selected" : ""}>${esc(c)}</option>`).join("")}</select>`))}`}`,
      pie: i.descartada ? h`<button class="boton" data-cerrar>Cerrar</button>` : h`<button class="boton peligro" id="clip-descartar">Descartar</button>
        ${atras >= 1 ? h`<button class="boton" id="clip-atras">← ${FASES[atras]}</button>` : ""}
        ${adelante <= 6 ? h`<button class="boton piloto" id="clip-adelante">${adelante === 3 ? "Dar luz verde" : adelante === 6 ? "Lanzar" : `${FASES[adelante]} →`}</button>` : ""}`,
    });
    const hechoY = async (fn) => { d.close(); await hacer(fn); };
    const capa = d.querySelector("#v-capa");
    if (capa) capa.addEventListener("change", () => hechoY(() => api("POST", `/ideas/${id}/capa`, { capa: +capa.value || null })));
    const q = (sel, fn) => { const b = d.querySelector(sel); if (b) b.addEventListener("click", () => hechoY(fn)); };
    q("#clip-atras", () => api("POST", `/ideas/${id}/fase`, { fase: atras }));
    q("#clip-adelante", () => api("POST", `/ideas/${id}/fase`, { fase: adelante }));
    q("#clip-descartar", () => api("POST", `/ideas/${id}/descartar`));
  }

  function verIncidencia(id) {
    const i = S.sala && S.sala.incidencias.find((x) => x.id === id);
    if (!i) return;
    const d = ventana({
      titulo: i.titulo,
      cuerpo: h`<p><b>Gravedad:</b> ${i.gravedad} · abierta ${cuando(i.creada_en)}${i.autor ? ` por ${i.autor}` : ""}</p>
        ${i.telefono ? h`<p><b>Teléfono:</b> ${i.telefono}${i.punto ? ` · punto ${i.punto} de prueba.${i.toma}` : ""}</p>` : ""}
        ${i.detalle ? h`<p>${i.detalle}</p>` : ""}
        <p class="cuidado">Al marcarla arreglada, el supervisor la ve como ARREGLADA en su hoja.</p>`,
      pie: h`<button class="boton" data-cerrar>Cerrar</button><button class="boton piloto" id="inc-arreglada">Marcar arreglada</button>`,
    });
    d.querySelector("#inc-arreglada").addEventListener("click", () => { d.close(); hacer(() => api("POST", `/incidencias/${id}/cerrar`)); });
  }

  async function marcar(id, estado, nota = "") {
    try {
      await api("POST", `/pruebas/${id}/marcar`, { estado, nota });
      if (estado === "falla") avisar("Se abrió una incidencia en la sala de CG.");
      S.fallaAbierta = null;
      await recargar();
    } catch (e) {
      avisar(mensajeDe(e), true);
      if (e.estado === 401 || e.codigo === "toma-cerrada" || e.codigo === "sin-telefono") recargar();
    }
  }

  // ── Politica de datos y terminos ───────────────────────────────────────────
  function vistaPolitica() {
    const p = S.publico || {};
    const r = p.responsable || {};
    const falta = (t) => h`<span class="falta">${t}</span>`;
    return h`<div class="marco"><p><button class="boton chico" data-accion="volver">← Volver</button></p>
      <article class="acta politica" aria-label="Política de datos y términos">
        <header class="acta-cab"><div><div class="rot">Life Music · CG LABS · equipo</div><h1>Política de datos y términos de la cabina</h1></div>
          <div class="folio">VERSIÓN<b>${p.politicaVersion || 1}</b>vigente desde el ${p.estreno ? fechaIso(p.estreno, { day: "numeric", month: "long", year: "numeric" }) : "día del estreno"}</div></header>
        <p class="ley">Ley 1581 de 2012 · Decreto 1377 de 2013 (Decreto 1074 de 2015) · Ley 527 de 1999 · Ley 1273 de 2009</p>
        <h2>1. Quién responde por tus datos</h2>
        <p>CG LABS, a cargo de ${r.nombre || falta("el responsable")}, en ${r.ciudad || falta("su ciudad")}, Colombia. Para cualquier asunto de datos: ${r.correo || falta("correo de contacto")}.</p>
        <h2>2. Qué datos se guardan</h2>
        <ul><li>Tu nombre y, si lo das, tu documento de identidad.</li><li>El cargo, la vigencia y el horario de tu pase.</li>
          <li>El acta firmada: tu firma, la fecha y la hora, y el tipo de aparato desde el que firmaste.</li><li>Tu actividad en la cabina: qué cambiaste y cuándo.</li><li>Si eres supervisor de ejecución: el teléfono con que pruebas (marca, modelo y versión de Android) y lo que marcas en tu hoja de pruebas.</li>
          <li>Una huella de tu conexión, solo para bloquear intentos de entrada fallidos. Se borra a las 24 horas.</li></ul>
        <h2>3. Para qué</h2>
        <p>Para gestionar tu cargo temporal, darte y quitarte accesos, dejar constancia del acta y mantener segura la cabina. Nunca para publicidad. No se venden ni se comparten con nadie para otros fines.</p>
        <h2>4. Dónde se guardan</h2>
        <p>En los servicios de Cloudflare (Workers y D1), que actúan como encargado. Sus servidores pueden estar fuera de Colombia; todo viaja cifrado.</p>
        <h2>5. Cuánto tiempo</h2>
        <p>Mientras dure tu cargo y ${p.retencionMeses === 12 || !p.retencionMeses ? "un año" : `${p.retencionMeses} meses`} después. Pasado ese plazo se borran, salvo que una ley obligue a conservarlos.</p>
        <h2>6. Tus derechos</h2>
        <ul><li>Conocer, actualizar y corregir tus datos.</li><li>Pedir prueba de la autorización que diste.</li><li>Saber para qué se usan.</li>
          <li>Revocar la autorización o pedir que se borren, cuando no haya un deber legal o contractual de guardarlos.</li><li>Acceder a ellos gratis.</li>
          <li>Quejarte ante la Superintendencia de Industria y Comercio (SIC).</li></ul>
        <h2>7. Cómo pedirlo</h2>
        <p>Desde «Tus datos» en la cabina o escribiendo a ${r.correo || falta("correo de contacto")}. Las consultas se responden en máximo 10 días hábiles y los reclamos en máximo 15.</p>
        <h2>8. Cookies</h2>
        <p>La cabina usa una sola cookie, la de la sesión, que hace falta para entrar. No hay analítica, ni publicidad, ni rastreo. Las fuentes se sirven desde lifemusic.app.</p>
        <h2>9. Términos de uso de la cabina</h2>
        <ul><li>La cabina es solo para el trabajo de tu cargo.</li><li>Tu pase y tu sesión son personales: no se prestan.</li>
          <li>Lo que ves aquí es confidencial, también después de terminar el cargo.</li><li>Camilo puede cerrar cualquier acceso en cualquier momento.</li>
          <li>Entrar sin autorización es un delito (Ley 1273 de 2009, art. 269A).</li><li>Rige la ley colombiana.</li></ul>
      </article></div>`;
  }

  // ── Ventanas de cada accion ────────────────────────────────────────────────
  function formulario({ titulo, campos, boton, enviar }) {
    const d = ventana({
      titulo,
      cuerpo: h`<form id="forma-ventana" class="ventana-forma">${campos}<p class="error-campo" id="error-ventana" hidden></p></form>`,
      pie: h`<button class="boton" data-cerrar>Cancelar</button><button class="boton piloto" type="submit" form="forma-ventana">${boton}</button>`,
    });
    d.querySelector("form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const b = d.querySelector('button[type="submit"]');
      b.disabled = true;
      try {
        await enviar((id) => d.querySelector("#" + id));
        d.close();
        await recargar();
      } catch (err) {
        const p = d.querySelector("#error-ventana");
        p.textContent = mensajeDe(err); p.hidden = false;
        b.disabled = false;
      }
    });
    const primero = d.querySelector("input, textarea, select");
    if (primero) primero.focus();
    return d;
  }
  const campo = (id, etiqueta, control) => h`<div class="campo"><label for="${id}">${etiqueta}</label>${control}</div>`;

  function verIdea(id) {
    const i = S.cabina ? S.cabina.ideas.find((x) => x.id === id) : null;
    const a = !i && S.prod ? S.prod.porAprobar.find((x) => x.id === id) : null;
    const idea = i || a;
    if (!idea) return;
    ventana({
      titulo: idea.titulo,
      cuerpo: h`<p><b>Fase:</b> ${FASES[idea.fase]}${idea.version ? ` · para la ${idea.version}` : ""}</p>
        ${idea.para_que ? h`<p><b>Para qué:</b> ${idea.para_que}</p>` : ""}
        <p><b>Propuesta por:</b> ${idea.autor || "—"}${idea.responsable ? ` · responsable: ${idea.responsable}` : ""}</p>
        ${idea.enlace ? h`<p><b>Ficha o PR:</b> <a href="${idea.enlace}" target="_blank" rel="noopener noreferrer">${idea.enlace}</a></p>` : ""}`,
      pie: h`<button class="boton" data-cerrar>Cerrar</button>`,
    });
  }

  async function verActa(id) {
    try {
      const d = id ? await api("GET", `/actas/${id}`) : await api("GET", "/acta");
      const a = d.acta;
      ventana({
        titulo: `Acta ${a.folio}`, ancha: true,
        cuerpo: h`<article class="acta acta-en-ventana">${documentoActa(a.doc)}
          <p><b>Nombre:</b> ${a.nombre}${a.documento ? ` · Documento: ${a.documento}` : ""}</p>
          <div class="firmas-hechas">
            <div><div class="firma-svg">${crudo(a.firma)}</div><small>Firmó ${a.nombre} · ${fechaHora(a.firmadaEn)} · ${a.aparato || ""}<br>Huella ${a.huella}</small></div>
            <div><div class="firma-svg">${a.contrafirma ? crudo(a.contrafirma) : h`<span class="cuidado">Falta la firma de Camilo</span>`}</div><small>${a.contrafirmadaEn ? `Firmó Camilo · ${fechaHora(a.contrafirmadaEn)}` : ""}${a.huellaFinal ? crudo(`<br>Huella final ${esc(a.huellaFinal)}`) : ""}</small></div>
          </div>
          <p class="ley">Firma electrónica · Ley 527 de 1999 y Decreto 2364 de 2012.</p></article>`,
        pie: h`<button class="boton" data-cerrar>Cerrar</button><button class="boton piloto" data-accion="imprimir">Guardar como PDF</button>`,
      });
    } catch (e) { avisar(mensajeDe(e), true); }
  }

  function contrafirmar(id) {
    const d = ventana({
      titulo: "Firmar el acta", ancha: true,
      cuerpo: h`<p>Tu firma completa el acta. Después ninguna de las dos firmas se puede cambiar.</p>
        <div class="acta acta-en-ventana"><div class="lienzo"><span class="guia" id="guia-cf">Firma aquí</span><span class="x">×</span><span class="linea-base"></span><canvas id="firma-cf" aria-label="Espacio para firmar"></canvas></div>
        <div class="firma-pie"><span id="estado-cf">Sin firmar</span><span><button type="button" class="alterna" id="cf-escrita">Firmar escribiendo mi nombre</button> · <button type="button" id="cf-borrar">Borrar</button></span></div></div>
        <p class="error-campo" id="error-cf" hidden></p>`,
      pie: h`<button class="boton" data-cerrar>Cancelar</button><button class="boton piloto" id="cf-firmar" disabled>Firmar</button>`,
    });
    const lien = lienzoDeFirma(d.querySelector("#firma-cf"), () => {
      d.querySelector("#guia-cf").hidden = !lien.vacia();
      d.querySelector("#estado-cf").textContent = lien.vacia() ? "Sin firmar" : "Firma lista";
      d.querySelector("#cf-firmar").disabled = lien.vacia();
    });
    d.querySelector("#cf-escrita").addEventListener("click", () => lien.escribir((S.prod && S.prod.responsable.nombre) || yo().nombre));
    d.querySelector("#cf-borrar").addEventListener("click", () => lien.borrar());
    d.querySelector("#cf-firmar").addEventListener("click", async () => {
      try {
        await api("POST", `/actas/${id}/contrafirmar`, { firma: lien.datos() });
        d.close();
        avisar("Acta completa");
        await recargar();
      } catch (e) { const p = d.querySelector("#error-cf"); p.textContent = mensajeDe(e); p.hidden = false; }
    });
  }

  function codigoAparato() {
    api("POST", "/aparato/codigo").then((r) => {
      const d = ventana({
        titulo: "Enlazar otro aparato",
        cuerpo: h`<p>En el otro aparato abre <b>lifemusic.app/equipo</b> y escribe este código donde dice «Código de entrada».</p>
          <div class="codigo-grande">${r.codigo}</div><div class="cuenta"><i id="cuenta"></i></div><p id="cuenta-texto"></p>`,
        pie: h`<button class="boton" data-cerrar>Listo</button>`,
      });
      const total = r.venceEn - Date.now();
      const tic = () => {
        const queda = r.venceEn - Date.now();
        const barra = d.querySelector("#cuenta");
        if (!barra) return clearInterval(reloj);
        barra.style.width = `${Math.max(0, (queda / total) * 100)}%`;
        const s = Math.max(0, Math.ceil(queda / 1000));
        d.querySelector("#cuenta-texto").textContent = queda > 0 ? `Vence en ${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}` : "Venció. Cierra y pide otro.";
      };
      const reloj = setInterval(tic, 1000);
      tic();
    }).catch((e) => avisar(mensajeDe(e), true));
  }

  async function descargarMisDatos() {
    try {
      const r = await fetch(API + "/mis-datos", { credentials: "include", cache: "no-store" });
      if (!r.ok) throw Object.assign(new Error(), { codigo: (await r.json().catch(() => ({}))).error });
      const blob = await r.blob();
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "mis-datos-lifemusic-equipo.json";
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    } catch (e) { avisar(mensajeDe(e), true); }
  }

  async function copiar(texto, boton) {
    try {
      await navigator.clipboard.writeText(texto);
      const antes = boton.textContent;
      boton.textContent = "Copiado"; boton.classList.add("hecho");
      setTimeout(() => { boton.textContent = antes; boton.classList.remove("hecho"); }, 1800);
    } catch {
      const r = document.createRange(); r.selectNodeContents(document.getElementById("url-pase"));
      const sel = getSelection(); sel.removeAllRanges(); sel.addRange(r);
      avisar("Selecciónalo y cópialo a mano");
    }
  }

  // ── Acciones (un solo escuchador para toda la pagina) ──────────────────────
  async function hacer(fn) {
    try { await fn(); await recargar(); } catch (e) { avisar(mensajeDe(e), true); if (e.estado === 401) recargar(); }
  }
  const ACCIONES = {
    recargar: () => recargar(),
    validar: () => validar(),
    ir: (b) => {
      if (b.dataset.a === "politica") { S.antes = S.vista; S.vista = "politica"; pintar(); scrollTo(0, 0); return; }
      S.vista = b.dataset.a; pintar();
    },
    volver: () => { S.vista = S.antes || "pase"; S.antes = null; recargar(); },
    pestana: (b) => { S.pestana = b.dataset.a; recargar(); scrollTo(0, 0); },
    contratacion: (b) => hacer(async () => {
      await api("POST", "/contratacion", { activa: b.dataset.a === "1" });
      avisar(b.dataset.a === "1" ? "«Equipo y contratación» encendido: vuelven los pases, el acta y la jornada." : "«Equipo y contratación» apagado. Nada se borró.");
    }),
    "ver-clip": (b) => verClip(b.dataset.id),
    "ver-incidencia": (b) => verIncidencia(b.dataset.id),
    marcar: (b) => {
      if (b.dataset.estado === "falla") { S.fallaAbierta = b.dataset.id; pintar(); return; }
      marcar(b.dataset.id, b.dataset.estado);
    },
    "falla-cancelar": () => { S.fallaAbierta = null; pintar(); },
    "falla-guardar": (b) => {
      const n = document.getElementById(`nota-${b.dataset.id}`);
      if (!n || n.value.trim().length < 3) { avisar(ERRORES.nota, true); if (n) n.focus(); return; }
      b.disabled = true;
      marcar(b.dataset.id, "falla", n.value);
    },
    "cambiar-telefono": () => { S.cambiarTelefono = true; pintar(); scrollTo(0, 0); },
    "cancelar-telefono": () => { S.cambiarTelefono = false; pintar(); },
    "firma-escrita": () => { const n = document.getElementById("f-nombre").value.trim(); if (n.length < 3) { document.getElementById("f-nombre").focus(); return; } firma.escribir(n); },
    "firma-borrar": () => firma.borrar(),
    "firmar-acta": () => firmarActa(),
    salir: () => hacer(() => api("POST", "/salir")),
    "cerrar-sesion": (b) => hacer(() => api("POST", `/sesiones/${b.dataset.id}/cerrar`)),
    "codigo-aparato": () => codigoAparato(),
    "ver-acta": (b) => verActa(b.dataset.id),
    imprimir: () => window.print(),
    "mis-datos": () => descargarMisDatos(),
    solicitud: (b) => formulario({
      titulo: b.dataset.a === "borrar" ? "Pedir que se borren tus datos" : "Pedir una corrección",
      campos: h`<p>${b.dataset.a === "borrar" ? "Camilo borra tus datos cuando no haya un deber legal o contractual de guardarlos, y te avisa." : "Cuéntale a Camilo qué dato está mal y cómo debe quedar."}</p>
        ${campo("v-detalle", "Detalle", crudo('<textarea class="control" id="v-detalle" maxlength="600"></textarea>'))}`,
      boton: "Enviar la solicitud",
      enviar: (q) => api("POST", "/solicitudes", { tipo: b.dataset.a, detalle: q("v-detalle").value }),
    }),
    "ver-idea": (b) => verIdea(b.dataset.id),
    "nueva-idea": () => formulario({
      titulo: "Proponer una idea",
      campos: h`${campo("v-titulo", "Título", crudo('<input class="control" id="v-titulo" required minlength="4" maxlength="120">'))}
        ${campo("v-para", "Para qué sirve", crudo('<textarea class="control" id="v-para" maxlength="600"></textarea>'))}
        <div class="fila-campos">${campo("v-version", "Versión", crudo(`<input class="control" id="v-version" maxlength="16" value="${esc((S.cabina || S.sala || {}).versionObjetivo || "2.0")}">`))}
        ${campo("v-enlace", "Ficha o PR (opcional)", crudo('<input class="control" id="v-enlace" type="url" maxlength="300" placeholder="https://github.com/…">'))}</div>`,
      boton: "Proponer",
      enviar: (q) => api("POST", "/ideas", { titulo: q("v-titulo").value, paraQue: q("v-para").value, version: q("v-version").value, enlace: q("v-enlace").value }),
    }),
    mover: (b) => hacer(() => api("POST", `/ideas/${b.dataset.id}/fase`, { fase: +b.dataset.fase })),
    descartar: (b) => {
      const d = ventana({ titulo: "Descartar la idea", cuerpo: h`<p>La idea sale de la sesión. Queda anotado en la bitácora.</p>`,
        pie: h`<button class="boton" data-cerrar>Cancelar</button><button class="boton peligro" id="si-descartar">Descartar</button>` });
      d.querySelector("#si-descartar").addEventListener("click", () => { d.close(); hacer(() => api("POST", `/ideas/${b.dataset.id}/descartar`)); });
    },
    "nueva-nota": () => formulario({
      titulo: "Nota de producción",
      campos: h`${campo("v-titulo", "Título", crudo('<input class="control" id="v-titulo" required maxlength="120">'))}
        ${campo("v-texto", "Texto", crudo('<textarea class="control" id="v-texto" maxlength="1200"></textarea>'))}
        <label class="acepto"><input type="checkbox" id="v-fijada"><span>Fijarla arriba</span></label>`,
      boton: "Publicar la nota",
      enviar: (q) => api("POST", "/notas", { titulo: q("v-titulo").value, texto: q("v-texto").value, fijada: q("v-fijada").checked }),
    }),
    "quitar-nota": (b) => hacer(() => api("POST", `/notas/${b.dataset.id}/quitar`)),
    "pedir-acceso": () => formulario({
      titulo: "Pedir una conexión",
      campos: h`<p>Aquí no se escriben claves. Camilo te invita a la plataforma con tu propia cuenta.</p>
        ${campo("v-plat", "Plataforma", crudo('<input class="control" id="v-plat" required maxlength="40" placeholder="GitHub, Cloudflare, Bitwarden…">'))}
        ${campo("v-perm", "Permiso que necesitas", crudo('<input class="control" id="v-perm" maxlength="80" placeholder="Colaborador, solo lectura…">'))}
        ${campo("v-motivo", "Para qué", crudo('<textarea class="control" id="v-motivo" maxlength="300"></textarea>'))}`,
      boton: "Pedir",
      enviar: (q) => api("POST", "/accesos", { plataforma: q("v-plat").value, permiso: q("v-perm").value, motivo: q("v-motivo").value }),
    }),
    "registrar-acceso": () => {
      const gente = S.prod ? S.prod.personas.filter((p) => p.estado !== "terminada") : [];
      if (!gente.length) { avisar("Primero tiene que haber alguien en el equipo", true); return; }
      formulario({
        titulo: "Registrar un acceso concedido",
        campos: h`<p>Solo el registro: a quién invitaste y dónde. Nunca la clave.</p>
          ${campo("v-persona", "Persona", crudo(`<select class="control" id="v-persona">${gente.map((p) => `<option value="${esc(p.id)}">${esc(p.nombre)}</option>`).join("")}</select>`))}
          ${campo("v-plat", "Plataforma", crudo('<input class="control" id="v-plat" required maxlength="40">'))}
          ${campo("v-perm", "Permiso", crudo('<input class="control" id="v-perm" maxlength="80">'))}`,
        boton: "Registrar",
        enviar: (q) => api("POST", "/accesos", { personaId: q("v-persona").value, plataforma: q("v-plat").value, permiso: q("v-perm").value }),
      });
    },
    "acceso-estado": (b) => hacer(() => api("POST", `/accesos/${b.dataset.id}/estado`, { estado: b.dataset.a })),
    "nueva-incidencia": () => formulario({
      titulo: "Nueva incidencia",
      campos: h`${campo("v-titulo", "Qué pasa", crudo('<input class="control" id="v-titulo" required minlength="4" maxlength="140">'))}
        ${campo("v-detalle", "Detalle", crudo('<textarea class="control" id="v-detalle" maxlength="1000"></textarea>'))}
        ${campo("v-grav", "Gravedad", crudo('<select class="control" id="v-grav"><option value="media">Media</option><option value="alta">Alta</option><option value="baja">Baja</option></select>'))}`,
      boton: "Abrir la incidencia",
      enviar: (q) => api("POST", "/incidencias", { titulo: q("v-titulo").value, detalle: q("v-detalle").value, gravedad: q("v-grav").value }),
    }),
    "cerrar-incidencia": (b) => hacer(() => api("POST", `/incidencias/${b.dataset.id}/cerrar`)),
    resena: (b) => hacer(() => api("POST", `/resenas/${b.dataset.id}/estado`, { estado: b.dataset.a })),
    revision: (b) => hacer(() => api("POST", `/revision/${b.dataset.item}`)),
    contrafirmar: (b) => contrafirmar(b.dataset.id),
    "anular-pase": (b) => hacer(() => api("POST", `/invitaciones/${b.dataset.id}/anular`)),
    "pase-de-nuevo": (b) => hacer(async () => { S.ultimoPase = await api("POST", `/personas/${b.dataset.id}/pase`); }),
    "persona-estado": (b) => hacer(() => api("POST", `/personas/${b.dataset.id}/estado`, { estado: b.dataset.a })),
    cortar: (b) => {
      const d = ventana({
        titulo: `Cortar el acceso de ${b.dataset.nombre}`,
        cuerpo: h`<p>Se cierran todas sus sesiones al instante y su pase deja de servir. No se puede deshacer: para volver, necesitaría un pase nuevo como persona nueva.</p>`,
        pie: h`<button class="boton" data-cerrar>Cancelar</button><button class="boton peligro" id="si-cortar">Cortar acceso</button>`,
      });
      d.querySelector("#si-cortar").addEventListener("click", async () => {
        d.close();
        try {
          const r = await api("POST", `/personas/${b.dataset.id}/cortar`);
          await recargar();
          ventana({
            titulo: "Acceso cortado",
            cuerpo: r.accesosPorRetirar.length
              ? h`<p>Ahora retira estos accesos en cada plataforma (aquí ya quedan como por retirar):</p><ul class="lista-simple salida-lista">${r.accesosPorRetirar.map((a) => h`<li><span class="jack on"></span><div><b>${a.plataforma}</b><small>${a.permiso || ""}</small></div></li>`)}</ul>`
              : h`<p>No tenía accesos registrados. Sus sesiones ya están cerradas.</p>`,
            pie: h`<button class="boton" data-cerrar>Listo</button>`,
          });
        } catch (e) { avisar(mensajeDe(e), true); }
      });
    },
    "responder-solicitud": (b) => hacer(() => api("POST", `/solicitudes/${b.dataset.id}/responder`)),
    "editar-responsable": () => {
      const r = S.prod.responsable;
      formulario({
        titulo: "Responsable de los datos",
        campos: h`${campo("v-nombre", "Nombre completo", crudo(`<input class="control" id="v-nombre" required maxlength="100" value="${esc(r.nombre)}">`))}
          <div class="fila-campos">${campo("v-ciudad", "Ciudad", crudo(`<input class="control" id="v-ciudad" required maxlength="60" value="${esc(r.ciudad)}">`))}
          ${campo("v-correo", "Correo para asuntos de datos", crudo(`<input class="control" id="v-correo" type="email" required maxlength="120" value="${esc(r.correo)}">`))}</div>`,
        boton: "Guardar",
        enviar: async (q) => { await api("POST", "/responsable", { nombre: q("v-nombre").value, ciudad: q("v-ciudad").value, correo: q("v-correo").value }); S.publico = await api("GET", "/estado").catch(() => S.publico); },
      });
    },
    copiar: (b) => copiar(b.dataset.texto, b),
    compartir: (b) => {
      if (navigator.share) navigator.share({ title: "Tu pase para la cabina de Life Music", text: b.dataset.texto }).catch(() => {});
      else copiar(b.dataset.texto, b);
    },
    "ver-qr": (b) => {
      const caja = document.getElementById("qr"), ver = caja.hidden;
      caja.hidden = !ver;
      b.textContent = ver ? "Ocultar QR" : "Mostrar QR";
      b.setAttribute("aria-pressed", String(ver));
    },
    "olvidar-pase": () => { S.ultimoPase = null; pintar(); },
  };
  document.addEventListener("click", (e) => {
    const b = e.target.closest("[data-accion], [data-ir]");
    if (!b || b.disabled) return;
    if (b.dataset.ir) { ACCIONES.ir({ dataset: { a: b.dataset.ir } }); return; }
    const f = ACCIONES[b.dataset.accion];
    if (f) { e.preventDefault(); f(b, e); }
  });
  // Las filas que se tocan (role="button") tambien responden a Enter y a la barra espaciadora.
  document.addEventListener("keydown", (e) => {
    if ((e.key === "Enter" || e.key === " ") && e.target.matches('[role="button"][data-accion]')) { e.preventDefault(); e.target.click(); }
  });
  document.addEventListener("submit", async (e) => {
    const forma = e.target.closest("[data-forma]");
    if (!forma) return;
    e.preventDefault();
    if (forma.dataset.forma === "arranque") return arrancar();
    if (forma.dataset.forma === "primera") {
      const v = (id) => document.getElementById(id);
      const android = forma.querySelector('input[name="t-android"]:checked');
      const err = document.getElementById("error-primera");
      const boton = forma.querySelector('button[type="submit"]');
      boton.disabled = true;
      try {
        if (S.yo.requisito === "politica") await api("POST", "/politica/aceptar", { autorizo: v("t-autorizo").checked, mayor: v("t-mayor").checked });
        await api("POST", "/telefono", { marca: v("t-marca").value, modelo: v("t-modelo").value, android: android ? android.value : "" });
        S.cambiarTelefono = false;
        await recargar();
        scrollTo(0, 0);
      } catch (x) { err.textContent = mensajeDe(x); err.hidden = false; boton.disabled = false; }
      return;
    }
    if (forma.dataset.forma === "pase") {
      const v = (id) => document.getElementById(id).value;
      const dias = [...forma.querySelectorAll('input[name="dia"]:checked')].map((x) => x.value).join(",");
      const err = document.getElementById("error-pase-nuevo");
      try {
        S.ultimoPase = await api("POST", "/invitaciones", { nombre: v("n-nombre"), rol: v("n-rol"), desde: v("n-desde"), hasta: v("n-hasta"), dias, entrada: v("n-entrada"), salida: v("n-salida") });
        await recargar();
        avisar("Pase impreso. Mándale el enlace por un chat privado.");
      } catch (x) { err.textContent = mensajeDe(x); err.hidden = false; }
    }
  });

  // Cada minuto se refresca la cabina (la luz AL AIRE, el monitoreo), salvo si alguien esta escribiendo.
  setInterval(() => {
    if (!["cabina", "duenio", "hoja"].includes(S.vista) || abiertaUnaVentana() || S.fallaAbierta || S.cambiarTelefono) return;
    const foco = document.activeElement;
    if (foco && /INPUT|TEXTAREA|SELECT/.test(foco.tagName)) return;
    if (S.ultimoPase) return;
    recargar();
  }, 60000);

  // ── Arranque ───────────────────────────────────────────────────────────────
  (async () => {
    // El codigo del enlace llega despues del # (nunca viaja al servidor). Se lee y se borra.
    const m = /^#pase=([A-Za-z0-9-]{6,14})$/.exec(location.hash);
    if (m) { S.codigo = m[1].toUpperCase(); history.replaceState(null, "", location.pathname); }
    if (location.hash === "#politica") S.vista = "politica";
    S.publico = await api("GET", "/estado").catch(() => null);
    await cargarYo();
  })();
})();
