/* Logica pura de la cabina del equipo: codigos, jornada, permisos, el acta y
   la firma. Sin nada de Cloudflare para poder probarla con `node --test`.
   Ficha: docs/versiones/1.3.3/equipo.md. */

export const ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"; // sin 0/O, 1/I/L: no se confunden al dictarlos
export const COLOMBIA_MS = -5 * 3600000; // Colombia no tiene horario de verano
export const DIA_MS = 86400000;

export const ROLES = {
  duenio: "Dueño",
  operaciones: "Operaciones",
  supervisor: "Supervisor de ejecución / Planificador",
};
export const FASES = ["", "Idea", "Planteamiento", "Luz verde", "Ejecución", "Pruebas", "Lanzada"];
export const ESTADOS = ["invitada", "prueba", "activa", "pausa", "terminada"];

// ── Codigos ──────────────────────────────────────────────────────────────────

/** n caracteres del alfabeto, sin el sesgo del modulo (se descartan los bytes >= 248). */
export function codigoAlAzar(n, azar = (b) => crypto.getRandomValues(b)) {
  const limite = 256 - (256 % ALFABETO.length);
  let r = "";
  while (r.length < n) {
    const bytes = azar(new Uint8Array(n * 2));
    for (const b of bytes) {
      if (b < limite && r.length < n) r += ALFABETO[b % ALFABETO.length];
    }
  }
  return r;
}

/** «lm-7kq2 xp9m», «LM7KQ2XP9M» o «7KQ2XP9M» → «7KQ2XP9M». Lo que no sea del alfabeto se ignora. */
export function normalizarCodigo(texto) {
  if (typeof texto !== "string") return "";
  let t = texto.toUpperCase().replace(/[^0-9A-Z]/g, "");
  if (t.length === 10 && t.startsWith("LM")) t = t.slice(2);
  return [...t].every((c) => ALFABETO.includes(c)) ? t : "";
}

export const formatoPase = (c) => `LM-${c.slice(0, 4)}-${c.slice(4, 8)}`;
export const formatoAparato = (c) => `${c.slice(0, 3)}-${c.slice(3, 6)}`;

// ── Fechas en hora de Colombia ───────────────────────────────────────────────

export function enColombia(ms) {
  const d = new Date(ms + COLOMBIA_MS);
  return { fecha: d.toISOString().slice(0, 10), dia: d.getUTCDay(), minuto: d.getUTCHours() * 60 + d.getUTCMinutes() };
}

export function aMinutos(hhmm) {
  const m = /^(\d{2}):(\d{2})$/.exec(hhmm || "");
  if (!m) return NaN;
  const h = +m[1], mi = +m[2];
  return h < 24 && mi < 60 ? h * 60 + mi : NaN;
}

/** El instante (ms UTC) de una fecha y minuto de Colombia. */
export function msDeColombia(fecha, minuto) {
  const [a, m, d] = fecha.split("-").map(Number);
  return Date.UTC(a, m - 1, d, 0, minuto) - COLOMBIA_MS;
}

export function sumarDias(fecha, n) {
  const [a, m, d] = fecha.split("-").map(Number);
  return new Date(Date.UTC(a, m - 1, d) + n * DIA_MS).toISOString().slice(0, 10);
}

export function diaDeSemana(fecha) {
  const [a, m, d] = fecha.split("-").map(Number);
  return new Date(Date.UTC(a, m - 1, d)).getUTCDay();
}

export function fechaValida(f) {
  return typeof f === "string" && /^\d{4}-\d{2}-\d{2}$/.test(f) && sumarDias(f, 0) === f;
}

/** Semana ISO, «2026-W42», para la revision semanal de Operaciones. */
export function semanaIso(fecha) {
  const [a, m, d] = fecha.split("-").map(Number);
  const t = new Date(Date.UTC(a, m - 1, d));
  const dia = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - dia);
  const inicio = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  const semana = Math.ceil(((t - inicio) / DIA_MS + 1) / 7);
  return `${t.getUTCFullYear()}-W${String(semana).padStart(2, "0")}`;
}

// ── Jornada ──────────────────────────────────────────────────────────────────

export function diasDe(p) {
  return String(p.dias || "")
    .split(",")
    .map(Number)
    .filter((d) => d >= 1 && d <= 5);
}

/** ¿Esta persona puede cambiar cosas ahora? Fuera de jornada la cabina es de solo lectura. */
export function estadoJornada(p, festivos, ahora) {
  if (p.rol === "duenio") return { abierta: true, motivo: "duenio" };
  if (p.estado === "terminada") return { abierta: false, motivo: "terminada", siguiente: null };
  const hoy = enColombia(ahora);
  const dias = diasDe(p);
  const ent = aMinutos(p.entrada), sal = aMinutos(p.salida);
  const vigente = (f) => (!p.desde || f >= p.desde) && (!p.hasta || f <= p.hasta);
  const laborable = (f) => dias.includes(diaDeSemana(f)) && !festivos.has(f);

  if (p.estado !== "pausa" && vigente(hoy.fecha) && laborable(hoy.fecha) && hoy.minuto >= ent && hoy.minuto < sal) {
    return { abierta: true, motivo: "jornada", hasta: msDeColombia(hoy.fecha, sal) };
  }
  let siguiente = null;
  if (p.estado !== "pausa") {
    for (let i = 0; i <= 60; i++) {
      const f = sumarDias(hoy.fecha, i);
      if (p.hasta && f > p.hasta) break;
      if (!vigente(f) || !laborable(f)) continue;
      if (i === 0 && hoy.minuto >= ent) continue;
      siguiente = msDeColombia(f, ent);
      break;
    }
  }
  let motivo = "fuera-de-horario";
  if (p.estado === "pausa") motivo = "pausa";
  else if (p.desde && hoy.fecha < p.desde) motivo = "antes-de-vigencia";
  else if (p.hasta && hoy.fecha > p.hasta) motivo = "vigencia-terminada";
  else if (festivos.has(hoy.fecha)) motivo = "festivo";
  else if (!dias.includes(hoy.dia)) motivo = "dia-libre";
  return { abierta: false, motivo, siguiente };
}

/** «Día 2 de 60» del cargo temporal. */
export function diaDelCargo(p, ahora) {
  if (!p.desde || !p.hasta) return null;
  const hoy = enColombia(ahora).fecha;
  const dias = (a, b) => Math.round((Date.parse(b) - Date.parse(a)) / DIA_MS);
  const total = dias(p.desde, p.hasta) + 1;
  const dia = Math.min(total, Math.max(0, dias(p.desde, hoy) + 1));
  return { dia, total };
}

// ── Permisos ─────────────────────────────────────────────────────────────────

/** Quien mueve una idea de que fase a cual. Luz verde (3) y lanzar (6) solo las da el dueño. */
export function puedeMoverIdea(rol, de, a) {
  if (!Number.isInteger(a) || a < 1 || a > 6 || a === de) return false;
  if (rol === "duenio") return true;
  if (rol === "supervisor") {
    const pasos = { "1>2": 1, "2>1": 1, "3>4": 1, "4>3": 1, "4>5": 1, "5>4": 1 };
    return !!pasos[`${de}>${a}`];
  }
  return false;
}

const PERMISOS = {
  duenio: ["idea", "incidencia", "cerrar-incidencia", "revision", "nota", "conceder", "invitar", "contrafirmar", "monitoreo", "concurso"],
  supervisor: ["idea", "incidencia", "monitoreo"],
  operaciones: ["idea", "incidencia", "cerrar-incidencia", "revision", "monitoreo", "concurso"],
};
export const puede = (rol, accion) => (PERMISOS[rol] || []).includes(accion);

// ── El acta del cargo temporal ───────────────────────────────────────────────

const MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];
export function fechaLarga(f) {
  if (!f) return "";
  const [a, m, d] = f.split("-").map(Number);
  return `${d} de ${MESES[m - 1]} de ${a}`;
}
export function hora12(hhmm) {
  const min = aMinutos(hhmm);
  const h = Math.floor(min / 60), m = min % 60;
  const sufijo = h < 12 ? "a. m." : "p. m.";
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, "0")} ${sufijo}`;
}
const NOMBRES_DIAS = ["", "lunes", "martes", "miércoles", "jueves", "viernes"];
export function textoDias(p) {
  const d = diasDe(p);
  if (d.join(",") === "1,2,3,4,5") return "Lunes a viernes";
  const t = d.map((x) => NOMBRES_DIAS[x]).join(", ");
  return t.charAt(0).toUpperCase() + t.slice(1);
}

export const folioDe = (p, anio) => `LM-EQ-${anio}-${String(p.numero).padStart(3, "0")}`;

/** El acta, armada con los datos del pase. Lo que se firma es este objeto en JSON, tal cual. */
export function actaDe(p, version, anio) {
  const total = p.desde && p.hasta ? Math.round((Date.parse(p.hasta) - Date.parse(p.desde)) / DIA_MS) + 1 : null;
  return {
    folio: folioDe(p, anio),
    version,
    titulo: "Acta de aceptación de cargo temporal",
    terminos: [
      ["Cargo", ROLES[p.rol]],
      ["Vigencia", p.desde && p.hasta ? `Del ${fechaLarga(p.desde)} al ${fechaLarga(p.hasta)} (${total} días)` : "Por definir"],
      ["Jornada", `${textoDias(p)}, de ${hora12(p.entrada)} a ${hora12(p.salida)}`],
      ["Modalidad", "Remota, en hora de Colombia"],
    ],
    clausulas: [
      ["Funciones", p.rol === "operaciones"
        ? "Mantener en orden la operación de Life Music: revisar el estado de los servicios, ordenar los informes de los probadores, llevar la bitácora de incidencias y proponer ideas."
        : "Planear las ideas y ejecutar las que Camilo apruebe, siguiendo el ciclo de cuatro fases: idea, planteamiento técnico, ejecución y pruebas."],
      ["Publicación", "Nada se publica sin la orden de Camilo: ni la app, ni la web, ni los servidores. El trabajo se entrega en una rama con su PR."],
      ["Confidencialidad", "Lo que se ve en la cabina y en los sistemas es confidencial durante el cargo y después de terminarlo."],
      ["Datos de usuarios", "No se accede ni se copia el prerregistro del concurso ni ningún dato personal de quienes usan la app."],
      ["Accesos", "Cada plataforma con cuenta propia. Las claves nunca van por chat. Al terminar el cargo se retiran todos los accesos."],
      ["Licencia", "Lo que se aporte al código se publica bajo la licencia GPL-3.0 del proyecto, como el resto de Life Music."],
      ["Fin del cargo", "El cargo termina en la fecha de vigencia o antes, si cualquiera de las partes lo pide. Camilo puede cerrar el acceso en cualquier momento."],
      ["Contrato aparte", "Esta acta deja constancia de que se acepta el cargo y sus reglas. No reemplaza el contrato de trabajo o de prestación de servicios, que se firma por separado."],
    ],
  };
}

// ── La firma ─────────────────────────────────────────────────────────────────

const TINTA = "#2441B5";
export function escaparXml(t) {
  return String(t).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" })[c]);
}

/** Los trazos llegan como listas de numeros [x, y, x, y...]. El SVG lo arma el servidor: nada del cliente entra tal cual. */
export function firmaDibujadaSvg(trazos, ancho, alto) {
  if (!Array.isArray(trazos) || trazos.length === 0 || trazos.length > 80) return null;
  const W = Math.round(ancho), H = Math.round(alto);
  if (!(W >= 100 && W <= 2000 && H >= 60 && H <= 800)) return null;
  let puntos = 0;
  const partes = [];
  for (const t of trazos) {
    if (!Array.isArray(t) || t.length < 2 || t.length % 2) return null;
    puntos += t.length / 2;
    if (puntos > 6000) return null;
    const p = [];
    for (let i = 0; i < t.length; i += 2) {
      const x = t[i], y = t[i + 1];
      if (typeof x !== "number" || typeof y !== "number" || !Number.isFinite(x) || !Number.isFinite(y)) return null;
      p.push(`${(Math.round(Math.min(W, Math.max(0, x)) * 10) / 10)} ${(Math.round(Math.min(H, Math.max(0, y)) * 10) / 10)}`);
    }
    partes.push(p.length === 1 ? `M${p[0]}l0.1 0` : `M${p[0]}L${p.slice(1).join("L")}`);
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}"><path fill="none" stroke="${TINTA}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" d="${partes.join("")}"/></svg>`;
}

export function firmaEscritaSvg(nombre) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 120"><text x="18" y="78" font-family="Georgia, 'Times New Roman', serif" font-style="italic" font-weight="600" font-size="40" fill="${TINTA}">${escaparXml(nombre)}</text></svg>`;
}

/** «1020456821» → «10•••••821». Lo ve completo solo el dueño. */
export function taparDocumento(doc) {
  if (!doc) return null;
  const t = String(doc);
  return t.length <= 5 ? "•".repeat(t.length) : t.slice(0, 2) + "•".repeat(t.length - 5) + t.slice(-3);
}

// ── Aparatos ─────────────────────────────────────────────────────────────────

export function aparatoDe(ua) {
  ua = ua || "";
  const so = /Android/i.test(ua) ? "Android" : /iPhone|iPad/i.test(ua) ? "iPhone" : /Windows/i.test(ua) ? "Windows"
    : /Mac OS X|Macintosh/i.test(ua) ? "Mac" : /Linux/i.test(ua) ? "Linux" : "otro sistema";
  const nav = /Edg\//.test(ua) ? "Edge" : /OPR\//.test(ua) ? "Opera" : /Firefox\//.test(ua) ? "Firefox"
    : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari" : "Navegador";
  return `${nav} en ${so}`;
}

// ── Textos que llegan del navegador ──────────────────────────────────────────

const CONTROL = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/;
export function texto(v, max, { multilinea = false, vacio = false } = {}) {
  if (v === undefined || v === null || v === "") return vacio ? "" : null;
  if (typeof v !== "string") return null;
  const t = v.trim();
  if (!t) return vacio ? "" : null;
  if (t.length > max || CONTROL.test(t)) return null;
  if (!multilinea && /[\r\n]/.test(t)) return null;
  return t;
}
