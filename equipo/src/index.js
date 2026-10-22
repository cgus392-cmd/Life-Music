/* Cabina del equipo de Life Music: la API de lifemusic.app/equipo.
   Ficha: docs/versiones/1.3.3/equipo.md. Vive en la ruta lifemusic.app/equipo/api/*,
   el mismo dominio de la pagina: la sesion va en una cookie __Host- HttpOnly y no
   hace falta CORS (solo en desarrollo, para localhost).

   Seguridad:
   - Ningun codigo ni sesion se guarda en claro: solo su HMAC con la PIMIENTA (secreto
     del Worker). Si se filtra la base, no sirve para entrar.
   - 5 codigos equivocados desde una conexion la bloquean 15 minutos.
   - Las escrituras piden Origin de lifemusic.app y la cabecera x-equipo.
   - Fuera de la jornada, quien no es el dueño solo puede leer (lo hace cumplir aqui).
   - El dueño entra la primera vez con la clave ARRANQUE, que CG pone el mismo con
     `wrangler secret put ARRANQUE`; despues esa ruta queda cerrada.

   Plan gratis de Cloudflare: si se pasa de los limites, falla; nunca cobra. */

import {
  DIA_MS, ROLES, FASES, codigoAlAzar, normalizarCodigo, formatoPase, formatoAparato, enColombia,
  estadoJornada, diaDelCargo, puedeMoverIdea, puede, actaDe, firmaDibujadaSvg, firmaEscritaSvg,
  taparDocumento, aparatoDe, texto, fechaValida, aMinutos, semanaIso, msDeColombia,
  CAPAS, MARCAS, GRUPOS, resumenHoja, estadoToma, textoTelefono, androidValido,
} from "./logica.js";

const PRODUCCION = /^https:\/\/(www\.)?lifemusic\.app$/;
const DESARROLLO = /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/;
const COOKIE = "__Host-lm_equipo";
const VIDA_PASE = 48 * 3600000;
const VIDA_APARATO = 5 * 60000;
const VIDA_SESION = 30 * DIA_MS;
const VENTANA = 15 * 60000;
const MAX_FALLOS = 5;
const MAX_CUERPO = 8192;
const MAX_FIRMA = 160000;
const ID = /^[0-9a-f-]{36}$/;

// ── Respuestas ───────────────────────────────────────────────────────────────

function respuesta(ctx, cuerpo, estado = 200, extra = {}) {
  const h = new Headers({
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
    "x-robots-tag": "noindex, nofollow",
    ...extra,
  });
  if (ctx.env.DESARROLLO === "1" && ctx.origen && DESARROLLO.test(ctx.origen)) {
    h.set("access-control-allow-origin", ctx.origen);
    h.set("access-control-allow-credentials", "true");
    h.set("vary", "Origin");
  }
  for (const c of ctx.cookies) h.append("set-cookie", c);
  return new Response(cuerpo === null ? null : JSON.stringify(cuerpo), { status: estado, headers: h });
}
const error = (ctx, codigo, estado = 400, extra = {}) => respuesta(ctx, { ok: false, error: codigo, ...extra }, estado);

async function leerCuerpo(ctx, max = MAX_CUERPO) {
  const t = await ctx.req.text();
  if (t.length > max) return null;
  try {
    const o = JSON.parse(t || "{}");
    return o && typeof o === "object" && !Array.isArray(o) ? o : null;
  } catch {
    return null;
  }
}

// ── Huellas y azar ───────────────────────────────────────────────────────────

const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
const nuevoId = () => crypto.randomUUID();
async function sha256(t) {
  return hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(t)));
}
let llave = null, llaveDe = null;
async function huella(env, tipo, valor) {
  if (!llave || llaveDe !== env.PIMIENTA) {
    llave = await crypto.subtle.importKey("raw", new TextEncoder().encode(env.PIMIENTA), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
    llaveDe = env.PIMIENTA;
  }
  return hex(await crypto.subtle.sign("HMAC", llave, new TextEncoder().encode(`${tipo}:${valor}`)));
}
function tokenAlAzar() {
  return hex(crypto.getRandomValues(new Uint8Array(32)));
}
async function igualesSinFiltrar(env, a, b) {
  const [x, y] = await Promise.all([huella(env, "cmp", a), huella(env, "cmp", b)]);
  let d = 0;
  for (let i = 0; i < x.length; i++) d |= x.charCodeAt(i) ^ y.charCodeAt(i);
  return d === 0;
}

// ── Base ─────────────────────────────────────────────────────────────────────

async function config(env) {
  const { results } = await env.DB.prepare("select clave, valor from config").all();
  return Object.fromEntries(results.map((r) => [r.clave, r.valor]));
}
const festivosDe = (cfg) => new Set(String(cfg.festivos || "").split(",").filter(Boolean));

async function anotar(ctx, personaId, accion, detalle = null) {
  await ctx.env.DB.prepare("insert into bitacora (persona_id, accion, detalle, aparato, creada_en) values (?, ?, ?, ?, ?)")
    .bind(personaId, accion, detalle, ctx.aparato, ctx.ahora).run();
}

// ── Sesion ───────────────────────────────────────────────────────────────────

function leerCookie(req, nombre) {
  const c = req.headers.get("cookie") || "";
  for (const parte of c.split(/;\s*/)) {
    const i = parte.indexOf("=");
    if (i > 0 && parte.slice(0, i) === nombre) return parte.slice(i + 1);
  }
  return null;
}
function ponerCookie(ctx, token, maxAge) {
  ctx.cookies.push(`${COOKIE}=${token}; Path=/; Max-Age=${maxAge}; HttpOnly; Secure; SameSite=Strict`);
}

async function abrirSesion(ctx, personaId) {
  const token = tokenAlAzar();
  await ctx.env.DB.prepare("insert into sesiones (id, persona_id, huella, aparato, creada_en, vista_en, vence_en) values (?, ?, ?, ?, ?, ?, ?)")
    .bind(nuevoId(), personaId, await huella(ctx.env, "sesion", token), ctx.aparato, ctx.ahora, ctx.ahora, ctx.ahora + VIDA_SESION).run();
  ponerCookie(ctx, token, VIDA_SESION / 1000);
}

async function sesion(ctx) {
  const token = leerCookie(ctx.req, COOKIE);
  if (!token || !/^[0-9a-f]{64}$/.test(token)) return null;
  const h = await huella(ctx.env, "sesion", token);
  const f = await ctx.env.DB.prepare(
    "select s.id as sid, s.vista_en as s_vista, s.vence_en as s_vence, p.* from sesiones s join personas p on p.id = s.persona_id where s.huella = ? and s.cerrada_en is null",
  ).bind(h).first();
  if (!f || f.s_vence < ctx.ahora || f.estado === "terminada") return null;
  if (ctx.ahora - f.s_vista > 3600000) {
    await ctx.env.DB.prepare("update sesiones set vista_en = ?, vence_en = ? where id = ?").bind(ctx.ahora, ctx.ahora + VIDA_SESION, f.sid).run();
    ponerCookie(ctx, token, VIDA_SESION / 1000);
  }
  const { sid, s_vista, s_vence, ...persona } = f;
  return { sid, persona };
}

// ── Intentos fallidos ────────────────────────────────────────────────────────

async function revisarIntentos(ctx) {
  const ip = await huella(ctx.env, "ip", ctx.req.headers.get("cf-connecting-ip") || "local");
  const v = Math.floor(ctx.ahora / VENTANA);
  const f = await ctx.env.DB.prepare("select fallos from intentos where ip = ? and ventana = ?").bind(ip, v).first();
  return { ip, v, bloqueado: !!f && f.fallos >= MAX_FALLOS };
}
async function fallo(ctx, intento) {
  const db = ctx.env.DB;
  await db.prepare("insert into intentos (ip, ventana, fallos) values (?, ?, 1) on conflict (ip, ventana) do update set fallos = fallos + 1").bind(intento.ip, intento.v).run();
  await db.prepare("delete from intentos where ventana < ?").bind(intento.v - 96).run(); // 24 horas
  const f = await db.prepare("select fallos from intentos where ip = ? and ventana = ?").bind(intento.ip, intento.v).first();
  if (f && f.fallos === MAX_FALLOS) await anotar(ctx, null, "alerta", `${MAX_FALLOS} códigos equivocados desde una misma conexión. Quedó bloqueada 15 minutos.`);
}

// ── Vistas ───────────────────────────────────────────────────────────────────

function vistaPersona(p, ctx, festivos) {
  return {
    id: p.id, nombre: p.nombre, rol: p.rol, cargo: ROLES[p.rol], estado: p.estado, numero: p.numero,
    desde: p.desde, hasta: p.hasta, dias: p.dias, entrada: p.entrada, salida: p.salida,
    jornada: estadoJornada(p, festivos, ctx.ahora), diaCargo: diaDelCargo(p, ctx.ahora),
  };
}

async function actaDePersona(env, personaId) {
  return env.DB.prepare("select * from actas where persona_id = ?").bind(personaId).first();
}
function vistaActa(fila, completa) {
  return {
    id: fila.id, folio: fila.folio, doc: JSON.parse(fila.texto), nombre: fila.nombre,
    documento: completa ? fila.documento : taparDocumento(fila.documento),
    firma: fila.firma, firmaTipo: fila.firma_tipo, firmadaEn: fila.firmada_en, aparato: fila.aparato, huella: fila.huella,
    contrafirma: fila.contrafirma, contrafirmadaEn: fila.contrafirmada_en, huellaFinal: fila.huella_final,
  };
}

// ── Monitoreo y concurso (se consultan desde aqui: sin CORS ni claves) ────────

const SERVICIOS = [
  ["relevo", "Relevo TV", "lifemusic-enlace", "https://lifemusic-enlace.cho--usic.workers.dev/salud", "GET"],
  ["comunidad", "Comunidad", "prerregistro y calificaciones", "https://lifemusic-comunidad.cho--usic.workers.dev/salud", "GET"],
  ["descargas", "Descargas", "APK en GitHub", "https://github.com/cgus392-cmd/Life-Music/releases/latest", "HEAD"],
  ["web", "Web", "lifemusic.app", "https://lifemusic.app/", "HEAD"],
];
async function medir([id, nombre, detalle, url, metodo]) {
  const t0 = Date.now();
  try {
    const r = await fetch(url, { method: metodo, redirect: "manual", signal: AbortSignal.timeout(6000), cf: { cacheTtl: 0 } });
    const ok = r.status < 400;
    return { id, nombre, detalle, ok, ms: Date.now() - t0 };
  } catch {
    return { id, nombre, detalle, ok: false, ms: null };
  }
}

// ── Sala de control (2.0) ────────────────────────────────────────────────────

const RELEASES = "https://api.github.com/repos/cgus392-cmd/Life-Music/releases?per_page=40";
const VIDA_MEDIDORES = 600; // 10 minutos: unas 6 consultas por hora a GitHub (sin clave el limite es 60)

/** Descargas por version (GitHub), calificaciones y prerregistros. Guardado 10 minutos. */
async function medidores(ctx) {
  const llaveCache = new Request("https://lifemusic.app/equipo/api/_cache/medidores");
  const cache = typeof caches !== "undefined" ? caches.default : null;
  const guardado = cache && (await cache.match(llaveCache));
  if (guardado) return guardado.json();
  const leer = async (url, extra = {}) => {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(6000), ...extra });
      return r.ok ? await r.json() : null;
    } catch {
      return null;
    }
  };
  const [releases, calif, pre] = await Promise.all([
    leer(RELEASES, { headers: { "user-agent": "lifemusic-equipo", accept: "application/vnd.github+json" } }),
    leer("https://lifemusic-comunidad.cho--usic.workers.dev/calificaciones/resumen"),
    leer("https://lifemusic-comunidad.cho--usic.workers.dev/prerregistro/estado"),
  ]);
  const versiones = Array.isArray(releases)
    ? releases.map((r) => ({
        tag: r.tag_name, fecha: r.published_at, prueba: !!r.prerelease,
        descargas: (r.assets || []).reduce((s, a) => s + (a.download_count || 0), 0),
      }))
    : null;
  const datos = {
    descargas: versiones && { total: versiones.reduce((s, v) => s + v.descargas, 0), versiones },
    calificaciones: calif && { total: calif.total || 0, promedio: calif.visible ? calif.promedio : null },
    prerregistro: pre && { contador: pre.contador, meta: pre.meta, abierto: pre.abierto },
    hora: ctx.ahora,
  };
  if (cache && versiones) {
    await cache.put(llaveCache, new Response(JSON.stringify(datos), { headers: { "cache-control": `max-age=${VIDA_MEDIDORES}` } }));
  }
  return datos;
}

/** Las reseñas de la web: las pendientes y las revisadas en la ultima semana (para poder deshacer). El correo solo lo ve el dueño. */
async function resenasDeLaSala(env) {
  if (!env.COMUNIDAD) return null;
  try {
    const lista = await env.COMUNIDAD.prepare(`select id, notas, nombre, correo, comentario, estado, creada_en from resenas
                                                where estado = 'pendiente' or revisada_en > datetime('now', '-7 day')
                                                order by estado = 'pendiente' desc, creada_en desc limit 40`).all();
    const cuenta = await env.COMUNIDAD.prepare("select estado, count(*) as n from resenas group by estado").all();
    return { lista: lista.results, cuenta: Object.fromEntries(cuenta.results.map((r) => [r.estado, r.n])) };
  } catch {
    return null; // la tabla todavia no existe en la base de la comunidad
  }
}

/** La toma al aire: la ultima sin cerrar. */
const tomaActual = (db) => db.prepare("select * from tomas where cerrada_en is null order by numero desc limit 1").first();
/** Sin toma al aire, la claqueta muestra la ultima publicada (decision de CG, 18-10). */
const ultimaPublicada = (db) => db.prepare("select * from tomas where publicada_en is not null order by publicada_en desc limit 1").first();
/** Lo que trae una toma: las ideas de sus puntos, con su capa para el color. */
const ideasDeToma = (db, tomaId) => db.prepare(`select distinct i.id, i.titulo, i.capa from pruebas p join ideas i on i.id = p.idea_id
                                                  where p.toma_id = ? order by i.numero`).bind(tomaId).all().then((r) => r.results);
const vistaToma = (t) => t && ({
  id: t.id, numero: t.numero, version: t.version, apk: t.apk, trae: t.trae, enlace: t.enlace,
  creadaEn: t.creada_en, cerradaEn: t.cerrada_en, publicadaEn: t.publicada_en,
});

// ── Rutas ────────────────────────────────────────────────────────────────────

async function enrutar(ctx) {
  const { req, env } = ctx;
  const db = env.DB;
  const partes = ctx.ruta.split("/").filter(Boolean);
  const m = req.method;
  const [r0, r1, r2] = partes;
  const cfg = await config(env);
  const festivos = festivosDe(cfg);

  // Publicas
  if (m === "GET" && r0 === "estado") {
    const duenio = await db.prepare("select count(*) as n from personas where rol = 'duenio'").first();
    return respuesta(ctx, {
      ok: true, hayDuenio: duenio.n > 0, politicaVersion: +cfg.politica_version || 1, retencionMeses: +cfg.retencion_meses || 12, estreno: cfg.estreno || null,
      responsable: { nombre: cfg.responsable_nombre || null, ciudad: cfg.responsable_ciudad || null, correo: cfg.responsable_correo || null },
    });
  }

  if (m === "POST" && r0 === "arranque") {
    const intento = await revisarIntentos(ctx);
    if (intento.bloqueado) return error(ctx, "bloqueado", 429);
    const duenio = await db.prepare("select count(*) as n from personas where rol = 'duenio'").first();
    if (duenio.n > 0) return error(ctx, "ya-abierta", 410);
    if (!env.ARRANQUE || env.ARRANQUE.length < 12) return error(ctx, "sin-clave-de-arranque", 503);
    const c = await leerCuerpo(ctx);
    if (!c || typeof c.clave !== "string") return error(ctx, "cuerpo");
    if (!(await igualesSinFiltrar(env, c.clave, env.ARRANQUE))) {
      await fallo(ctx, intento);
      return error(ctx, "clave", 403);
    }
    const nombre = texto(c.nombre, 100), ciudad = texto(c.ciudad, 60), correo = texto(c.correo, 120);
    if (!nombre || nombre.length < 3) return error(ctx, "nombre");
    if (!ciudad) return error(ctx, "ciudad");
    if (!correo || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(correo)) return error(ctx, "correo");
    const id = nuevoId();
    await db.batch([
      db.prepare("insert into personas (id, nombre, rol, estado, numero, creada_en) values (?, ?, 'duenio', 'activa', 1, ?)").bind(id, nombre, ctx.ahora),
      db.prepare("insert or replace into config (clave, valor) values ('responsable_nombre', ?), ('responsable_ciudad', ?), ('responsable_correo', ?), ('estreno', ?)")
        .bind(nombre, ciudad, correo, enColombia(ctx.ahora).fecha),
    ]);
    await abrirSesion(ctx, id);
    await anotar(ctx, id, "estreno", "Se abrió la cabina.");
    return respuesta(ctx, { ok: true });
  }

  // El pase: ver (no lo gasta) y validar (lo gasta)
  if (m === "POST" && r0 === "pase" && (r1 === "ver" || r1 === "validar")) {
    const intento = await revisarIntentos(ctx);
    if (intento.bloqueado) return error(ctx, "bloqueado", 429);
    const c = await leerCuerpo(ctx);
    const codigo = normalizarCodigo(c && c.codigo);
    let inv = null;
    if (codigo.length === 8) {
      inv = await db.prepare(
        "select i.id as iid, i.vence_en, i.vista_en, p.* from invitaciones i join personas p on p.id = i.persona_id where i.huella = ? and i.usada_en is null and i.anulada_en is null",
      ).bind(await huella(env, "pase", codigo)).first();
    }
    if (!inv || inv.vence_en < ctx.ahora || inv.estado === "terminada") {
      await fallo(ctx, intento);
      return error(ctx, "pase-invalido", 404);
    }
    if (r1 === "ver") {
      if (!inv.vista_en) {
        await db.prepare("update invitaciones set vista_en = ? where id = ?").bind(ctx.ahora, inv.iid).run();
        await anotar(ctx, inv.id, "pase-abierto", `${inv.nombre} abrió su pase Nº ${String(inv.numero).padStart(4, "0")}.`);
      }
      return respuesta(ctx, { ok: true, pase: { nombre: inv.nombre, cargo: ROLES[inv.rol], numero: inv.numero, venceEn: inv.vence_en, desde: inv.desde, hasta: inv.hasta } });
    }
    const usado = await db.prepare("update invitaciones set usada_en = ? where id = ? and usada_en is null").bind(ctx.ahora, inv.iid).run();
    if (!usado.meta.changes) return error(ctx, "pase-invalido", 404);
    await abrirSesion(ctx, inv.id);
    await anotar(ctx, inv.id, "pase-validado", `${inv.nombre} validó su pase.`);
    return respuesta(ctx, { ok: true });
  }

  // Enlazar otro aparato con el codigo de 5 minutos
  if (m === "POST" && r0 === "aparato" && r1 === "enlazar") {
    const intento = await revisarIntentos(ctx);
    if (intento.bloqueado) return error(ctx, "bloqueado", 429);
    const c = await leerCuerpo(ctx);
    const codigo = normalizarCodigo(c && c.codigo);
    let f = null;
    if (codigo.length === 6) {
      f = await db.prepare("select c.huella, c.vence_en, p.id, p.nombre, p.estado from codigos_aparato c join personas p on p.id = c.persona_id where c.huella = ? and c.usado_en is null")
        .bind(await huella(env, "aparato", codigo)).first();
    }
    if (!f || f.vence_en < ctx.ahora || f.estado === "terminada") {
      await fallo(ctx, intento);
      return error(ctx, "codigo-invalido", 404);
    }
    const usado = await db.prepare("update codigos_aparato set usado_en = ? where huella = ? and usado_en is null").bind(ctx.ahora, f.huella).run();
    if (!usado.meta.changes) return error(ctx, "codigo-invalido", 404);
    await abrirSesion(ctx, f.id);
    await anotar(ctx, f.id, "aparato-enlazado", `${f.nombre} enlazó ${ctx.aparato}.`);
    return respuesta(ctx, { ok: true });
  }

  // ── De aqui en adelante, con sesion ──
  const s = await sesion(ctx);
  if (!s) return error(ctx, "sin-sesion", 401);
  const yo = s.persona;
  const duenio = yo.rol === "duenio";
  const jornada = estadoJornada(yo, festivos, ctx.ahora);
  const miActa = duenio ? null : await actaDePersona(env, yo.id);
  // 2.0: sin contratacion, para entrar basta aceptar la politica de datos (Ley 1581);
  // con contratacion encendida, el acta firmada, como antes. Lo ya firmado sigue valiendo.
  const contratacion = cfg.equipo_contratacion === "1";
  const miAceptacion = duenio || miActa ? null
    : await db.prepare("select politica_version, aceptada_en from aceptaciones where persona_id = ?").bind(yo.id).first();
  const actaLista = duenio || !!miActa || (!contratacion && !!miAceptacion);
  const requisito = actaLista ? null : contratacion ? "acta" : "politica";
  const escribir = () => (duenio || jornada.abierta ? null : error(ctx, "fuera-de-jornada", 403, { jornada }));
  const conActa = () => (actaLista ? null : error(ctx, "acta-pendiente", 403));

  if (m === "GET" && r0 === "yo") {
    const { results: aparatos } = await db.prepare("select id, aparato, creada_en, vista_en from sesiones where persona_id = ? and cerrada_en is null and vence_en > ? order by vista_en desc")
      .bind(yo.id, ctx.ahora).all();
    return respuesta(ctx, {
      ok: true, yo: vistaPersona(yo, ctx, festivos), sesionId: s.sid,
      acta: miActa ? { firmada: true, contrafirmada: !!miActa.contrafirmada_en, folio: miActa.folio } : { firmada: duenio },
      requisito, contratacion, hoja: puede(yo.rol, "hoja"),
      aparatos: aparatos.map((a) => ({ ...a, esta: a.id === s.sid })),
      hoy: enColombia(ctx.ahora).fecha, semana: semanaIso(enColombia(ctx.ahora).fecha),
    });
  }

  if (m === "POST" && r0 === "salir") {
    await db.prepare("update sesiones set cerrada_en = ? where id = ?").bind(ctx.ahora, s.sid).run();
    ctx.cookies.push(`${COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict`);
    return respuesta(ctx, { ok: true });
  }

  if (m === "POST" && r0 === "sesiones" && ID.test(r1 || "") && r2 === "cerrar") {
    const f = await db.prepare("select persona_id from sesiones where id = ?").bind(r1).first();
    if (!f || (f.persona_id !== yo.id && !duenio)) return error(ctx, "no-existe", 404);
    await db.prepare("update sesiones set cerrada_en = ? where id = ? and cerrada_en is null").bind(ctx.ahora, r1).run();
    await anotar(ctx, yo.id, "sesion-cerrada", "Cerró la sesión de un aparato.");
    return respuesta(ctx, { ok: true });
  }

  if (m === "POST" && r0 === "aparato" && r1 === "codigo") {
    const codigo = codigoAlAzar(6);
    await db.prepare("delete from codigos_aparato where vence_en < ?").bind(ctx.ahora - DIA_MS).run();
    await db.prepare("insert into codigos_aparato (huella, persona_id, creada_en, vence_en) values (?, ?, ?, ?)")
      .bind(await huella(env, "aparato", codigo), yo.id, ctx.ahora, ctx.ahora + VIDA_APARATO).run();
    return respuesta(ctx, { ok: true, codigo: formatoAparato(codigo), venceEn: ctx.ahora + VIDA_APARATO });
  }

  // El acta
  if (m === "GET" && r0 === "acta") {
    if (miActa) return respuesta(ctx, { ok: true, firmada: true, acta: vistaActa(miActa, true) });
    if (duenio) return error(ctx, "no-aplica", 404);
    const anio = (yo.desde || enColombia(ctx.ahora).fecha).slice(0, 4);
    return respuesta(ctx, { ok: true, firmada: false, doc: actaDe(yo, +cfg.acta_version || 1, anio), politicaVersion: +cfg.politica_version || 1 });
  }
  if (m === "POST" && r0 === "acta" && r1 === "firmar") {
    if (duenio || miActa) return error(ctx, "ya-firmada", 409);
    const c = await leerCuerpo(ctx, MAX_FIRMA);
    if (!c) return error(ctx, "cuerpo");
    const nombre = texto(c.nombre, 100);
    if (!nombre || nombre.length < 3) return error(ctx, "nombre");
    const documento = texto(c.documento, 30, { vacio: true });
    if (documento === null || (documento && !/^[0-9A-Za-z .-]+$/.test(documento))) return error(ctx, "documento");
    if (c.acepto !== true || c.autorizo !== true || c.mayor !== true) return error(ctx, "casillas");
    const f = c.firma || {};
    const svg = f.tipo === "escrita" ? firmaEscritaSvg(nombre) : f.tipo === "dibujada" ? firmaDibujadaSvg(f.trazos, f.ancho, f.alto) : null;
    if (!svg) return error(ctx, "firma");
    const anio = (yo.desde || enColombia(ctx.ahora).fecha).slice(0, 4);
    const doc = actaDe(yo, +cfg.acta_version || 1, anio);
    const textoActa = JSON.stringify(doc);
    const h = await sha256([textoActa, nombre, documento || "", svg, ctx.ahora, ctx.aparato].join("\n"));
    await db.batch([
      db.prepare(`insert into actas (id, persona_id, folio, version, texto, nombre, documento, firma, firma_tipo, firmada_en, aparato, huella,
                  autoriza_datos_en, politica_version, mayor_edad) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)`)
        .bind(nuevoId(), yo.id, doc.folio, doc.version, textoActa, nombre, documento || null, svg, f.tipo, ctx.ahora, ctx.aparato, h, ctx.ahora, +cfg.politica_version || 1),
      db.prepare("update personas set estado = 'activa' where id = ? and estado = 'invitada'").bind(yo.id),
    ]);
    await anotar(ctx, yo.id, "acta-firmada", `${yo.nombre} firmó el acta ${doc.folio}. Huella ${h.slice(0, 4)}…${h.slice(-4)}.`);
    return respuesta(ctx, { ok: true, huella: h, folio: doc.folio });
  }

  // Los derechos sobre los datos (Ley 1581): se pueden ejercer siempre, aun sin acta y fuera de jornada.
  if (m === "GET" && r0 === "mis-datos") {
    const q = (sql) => db.prepare(sql).bind(yo.id).all().then((r) => r.results);
    const datos = {
      generado: new Date(ctx.ahora).toISOString(),
      persona: vistaPersona(yo, ctx, festivos),
      acta: miActa ? vistaActa(miActa, true) : null,
      aparatos: await q("select aparato, creada_en, vista_en, cerrada_en from sesiones where persona_id = ?"),
      accesos: await q("select plataforma, permiso, estado, motivo, creado_en, actualizado_en from accesos where persona_id = ?"),
      ideas: await q("select titulo, para_que, version, fase, creada_en from ideas where autor_id = ?"),
      incidencias: await q("select titulo, detalle, gravedad, creada_en from incidencias where autor_id = ?"),
      bitacora: await q("select accion, detalle, aparato, creada_en from bitacora where persona_id = ? order by creada_en"),
      solicitudes: await q("select tipo, detalle, creada_en, respondida_en from solicitudes where persona_id = ?"),
      aceptacion: miAceptacion,
      telefono: await db.prepare("select marca, modelo, android, actualizado_en from telefonos where persona_id = ?").bind(yo.id).first(),
      pruebas: await q(`select t.numero as toma, p.titulo, r.estado, r.nota, r.telefono, r.marcado_en from resultados r
                        join pruebas p on p.id = r.prueba_id join tomas t on t.id = p.toma_id where r.persona_id = ? order by r.marcado_en`),
    };
    await anotar(ctx, yo.id, "datos-descargados", `${yo.nombre} descargó sus datos.`);
    return respuesta(ctx, datos, 200, { "content-disposition": `attachment; filename="mis-datos-lifemusic-equipo.json"` });
  }
  if (m === "POST" && r0 === "solicitudes" && !r1) {
    const c = await leerCuerpo(ctx);
    if (!c || !["corregir", "borrar"].includes(c.tipo)) return error(ctx, "tipo");
    const detalle = texto(c.detalle, 600, { multilinea: true, vacio: true });
    if (detalle === null) return error(ctx, "detalle");
    await db.prepare("insert into solicitudes (id, persona_id, tipo, detalle, creada_en) values (?, ?, ?, ?, ?)").bind(nuevoId(), yo.id, c.tipo, detalle, ctx.ahora).run();
    await anotar(ctx, yo.id, "solicitud-datos", `${yo.nombre} pidió ${c.tipo === "borrar" ? "que se borren sus datos" : "corregir sus datos"}.`);
    return respuesta(ctx, { ok: true });
  }

  // Sin contratacion, la entrada es aceptar la politica de datos con una casilla.
  if (m === "POST" && r0 === "politica" && r1 === "aceptar") {
    if (actaLista) return error(ctx, "ya-firmada", 409);
    if (contratacion) return error(ctx, "acta-pendiente", 403);
    const c = await leerCuerpo(ctx);
    if (!c || c.autorizo !== true || c.mayor !== true) return error(ctx, "casillas");
    await db.batch([
      db.prepare("insert into aceptaciones (persona_id, politica_version, mayor_edad, aparato, aceptada_en) values (?, ?, 1, ?, ?)")
        .bind(yo.id, +cfg.politica_version || 1, ctx.aparato, ctx.ahora),
      db.prepare("update personas set estado = 'activa' where id = ? and estado = 'invitada'").bind(yo.id),
    ]);
    await anotar(ctx, yo.id, "politica-aceptada", `${yo.nombre} aceptó la política de datos.`);
    return respuesta(ctx, { ok: true });
  }

  const sinActa = conActa();
  if (sinActa) return sinActa;

  // El APK de una toma: solo con sesion de la sala (CG, 18-10: «prueba solo para los de la pagina»).
  // No va a GitHub porque alli el canal beta de la app se lo ofreceria a cualquiera. Vive en KV,
  // partido en pedazos de menos de 25 MB (el tope de un valor), y se entrega unido.
  if (m === "GET" && r0 === "tomas" && ID.test(r1 || "") && r2 === "apk") {
    if (!env.APKS) return error(ctx, "sin-configurar", 503);
    const toma = await db.prepare("select numero from tomas where id = ?").bind(r1).first();
    const meta = toma && (await env.APKS.get(`apk:${r1}:meta`, "json"));
    if (!meta) return error(ctx, "no-existe", 404);
    const { readable, writable } = typeof FixedLengthStream === "function" ? new FixedLengthStream(meta.tamano) : new TransformStream();
    const copiar = (async () => {
      try {
        for (let i = 0; i < meta.partes; i++) {
          const parte = await env.APKS.get(`apk:${r1}:${i}`, "stream");
          if (!parte) throw new Error(`falta la parte ${i}`);
          await parte.pipeTo(writable, { preventClose: true });
        }
        await writable.close();
      } catch (e) {
        console.error("equipo: apk", e);
        await writable.abort(e).catch(() => {});
      }
    })();
    if (ctx.ec) ctx.ec.waitUntil(copiar);
    await anotar(ctx, yo.id, "toma-bajada", `${yo.nombre} bajó el APK de la toma ${toma.numero}.`);
    return new Response(readable, {
      headers: {
        "content-type": "application/vnd.android.package-archive",
        "content-disposition": `attachment; filename="${String(meta.nombre).replace(/[^\w.-]/g, "_")}"`,
        "cache-control": "private, no-store", "x-content-type-options": "nosniff", "x-robots-tag": "noindex, nofollow",
      },
    });
  }

  // ── La hoja de pruebas (2.0). Se marca a cualquier hora: sin jornada (decision de CG, 18-10) ──
  if ((r0 === "hoja" || r0 === "telefono" || r0 === "pruebas") && !puede(yo.rol, "hoja")) return error(ctx, "permiso", 403);
  if (m === "POST" && (r0 === "telefono" || r0 === "pruebas") && yo.estado === "pausa") return error(ctx, "en-pausa", 403);
  if (m === "GET" && r0 === "hoja") {
    const all = (sql, ...b) => db.prepare(sql).bind(...b).all().then((r) => r.results);
    const t = await tomaActual(db);
    const telefono = await db.prepare("select marca, modelo, android from telefonos where persona_id = ?").bind(yo.id).first();
    let pruebas = [], resumen = null;
    if (t) {
      pruebas = await all(`select p.id, p.orden, p.grupo, p.titulo, p.pasos, p.espera, p.requiere, i.titulo as idea, i.capa as idea_capa,
                                  r.estado, r.nota, r.marcado_en
                             from pruebas p left join ideas i on i.id = p.idea_id
                             left join resultados r on r.prueba_id = p.id and r.persona_id = ?
                            where p.toma_id = ? order by p.orden`, yo.id, t.id);
      resumen = resumenHoja(pruebas, new Map(pruebas.filter((p) => p.estado).map((p) => [p.id, p.estado])));
    }
    const cuenta = (estado) => `(select count(*) from resultados r join pruebas p on p.id = r.prueba_id
                                  where p.toma_id = t.id and r.persona_id = ?1 and r.estado = '${estado}')`;
    const historial = await all(`select t.numero, t.version, t.apk, t.creada_en, t.cerrada_en, t.publicada_en,
                                        (select count(*) from pruebas p where p.toma_id = t.id) as total,
                                        ${cuenta("funciona")} as funciona, ${cuenta("falla")} as falla, ${cuenta("no-pude")} as no_pude
                                   from tomas t where t.cerrada_en is not null order by t.numero desc limit 12`, yo.id);
    // «Mis incidencias», solo para leer: las que abrio esta persona y si ya se arreglaron.
    const incidencias = await all(`select i.titulo, i.abierta, i.creada_en, i.cerrada_en, p.orden as punto, t.numero as toma
                                     from incidencias i left join resultados r on r.incidencia_id = i.id and r.persona_id = i.autor_id
                                     left join pruebas p on p.id = r.prueba_id left join tomas t on t.id = p.toma_id
                                    where i.autor_id = ? order by i.abierta desc, i.creada_en desc limit 20`, yo.id);
    return respuesta(ctx, {
      ok: true, toma: vistaToma(t), resumen, telefono, historial, grupos: GRUPOS, incidencias,
      trae: t ? await ideasDeToma(db, t.id) : [],
      ultimaPublicada: t ? null : vistaToma(await ultimaPublicada(db)),
      pruebas: pruebas.map((p) => ({
        id: p.id, orden: p.orden, grupo: p.grupo, titulo: p.titulo, pasos: p.pasos, espera: p.espera, requiere: p.requiere, idea: p.idea, capa: p.idea_capa,
        mio: p.estado ? { estado: p.estado, nota: p.nota, marcadoEn: p.marcado_en } : null,
      })),
    });
  }
  if (m === "POST" && r0 === "telefono" && !r1) {
    const c = await leerCuerpo(ctx);
    const marca = texto(c && c.marca, 40), modelo = texto(c && c.modelo, 60);
    if (!marca || !modelo || !androidValido(c.android)) return error(ctx, "campos");
    const android = c.android.trim();
    await db.prepare(`insert into telefonos (persona_id, marca, modelo, android, actualizado_en) values (?, ?, ?, ?, ?)
                      on conflict (persona_id) do update set marca = excluded.marca, modelo = excluded.modelo,
                      android = excluded.android, actualizado_en = excluded.actualizado_en`)
      .bind(yo.id, marca, modelo, android, ctx.ahora).run();
    await anotar(ctx, yo.id, "telefono", `${yo.nombre} prueba en ${marca} ${modelo} · Android ${android}.`);
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "pruebas" && ID.test(r1 || "") && r2 === "marcar") {
    const c = await leerCuerpo(ctx);
    const estado = c && c.estado;
    if (!MARCAS.includes(estado)) return error(ctx, "estado");
    const nota = texto(c.nota, 500, { multilinea: true, vacio: true });
    // Una falla sin nota no sirve para arreglarla.
    if (nota === null || (estado === "falla" && nota.length < 3)) return error(ctx, "nota");
    const p = await db.prepare("select p.id, p.titulo, t.numero, t.cerrada_en from pruebas p join tomas t on t.id = p.toma_id where p.id = ?").bind(r1).first();
    if (!p) return error(ctx, "no-existe", 404);
    if (p.cerrada_en) return error(ctx, "toma-cerrada", 409);
    const tel = await db.prepare("select marca, modelo, android from telefonos where persona_id = ?").bind(yo.id).first();
    if (!tel) return error(ctx, "sin-telefono", 409);
    const telefono = textoTelefono(tel);
    const antes = await db.prepare("select estado, incidencia_id from resultados where prueba_id = ? and persona_id = ?").bind(p.id, yo.id).first();
    let incidencia = (antes && antes.incidencia_id) || null;
    // Una falla abre sola su incidencia en la bitacora de la sala (o pone al dia la que sigue abierta).
    if (estado === "falla") {
      const detalle = `${nota} — ${yo.nombre}, en ${telefono}.`.slice(0, 1000);
      const sigue = incidencia && (await db.prepare("select 1 from incidencias where id = ? and abierta = 1").bind(incidencia).first());
      if (sigue) {
        await db.prepare("update incidencias set detalle = ? where id = ?").bind(detalle, incidencia).run();
      } else {
        incidencia = nuevoId();
        await db.prepare("insert into incidencias (id, titulo, detalle, gravedad, autor_id, creada_en) values (?, ?, ?, 'media', ?, ?)")
          .bind(incidencia, `Toma ${p.numero} · ${p.titulo}`.slice(0, 140), detalle, yo.id, ctx.ahora).run();
      }
    }
    await db.prepare(`insert into resultados (prueba_id, persona_id, estado, nota, telefono, incidencia_id, marcado_en) values (?, ?, ?, ?, ?, ?, ?)
                      on conflict (prueba_id, persona_id) do update set estado = excluded.estado, nota = excluded.nota,
                      telefono = excluded.telefono, incidencia_id = excluded.incidencia_id, marcado_en = excluded.marcado_en`)
      .bind(p.id, yo.id, estado, nota || null, telefono, incidencia, ctx.ahora).run();
    if (!antes || antes.estado !== estado) {
      const verbo = estado === "funciona" ? "vio funcionar" : estado === "falla" ? "encontró una falla en" : "no pudo probar";
      await anotar(ctx, yo.id, "prueba", `${yo.nombre} ${verbo} «${p.titulo}» (toma ${p.numero}).`);
    }
    return respuesta(ctx, { ok: true, incidencia: estado === "falla" ? incidencia : null });
  }

  // La cabina
  if (m === "GET" && r0 === "cabina") {
    const all = (sql, ...b) => db.prepare(sql).bind(...b).all().then((r) => r.results);
    const objetivo = cfg.version_objetivo || "1.3.3";
    const data = {
      ok: true,
      versionObjetivo: objetivo,
      fases: FASES,
      notas: await all("select n.id, n.titulo, n.texto, n.fijada, n.creada_en, p.nombre as autor from notas n left join personas p on p.id = n.autor_id order by n.fijada desc, n.creada_en desc limit 30"),
      ideas: await all(`select i.id, i.numero, i.titulo, i.para_que, i.version, i.fase, i.capa, i.enlace, i.creada_en, i.actualizada_en,
                        a.nombre as autor, r.nombre as responsable from ideas i left join personas a on a.id = i.autor_id left join personas r on r.id = i.responsable_id
                        where i.descartada = 0 order by i.numero`),
      equipo: await all("select nombre, rol, numero from personas where estado in ('prueba', 'activa', 'pausa') order by numero"),
      accesos: duenio
        ? await all("select a.id, a.plataforma, a.permiso, a.estado, a.motivo, a.creado_en, p.nombre as persona from accesos a join personas p on p.id = a.persona_id where a.estado != 'retirado' order by a.creado_en desc")
        : await all("select id, plataforma, permiso, estado, motivo, creado_en from accesos where persona_id = ? and estado != 'retirado' order by creado_en desc", yo.id),
      incidencias: await all("select i.id, i.titulo, i.detalle, i.gravedad, i.abierta, i.creada_en, i.cerrada_en, p.nombre as autor from incidencias i left join personas p on p.id = i.autor_id order by i.abierta desc, i.creada_en desc limit 40"),
      solicitudes: duenio ? [] : await all("select tipo, detalle, creada_en, respondida_en from solicitudes where persona_id = ? order by creada_en desc", yo.id),
    };
    data.funcionesObjetivo = data.ideas.filter((i) => i.version === objetivo).length;
    data.equipo = data.equipo.map((p) => ({ ...p, cargo: ROLES[p.rol] }));
    if (puede(yo.rol, "revision")) {
      const semana = semanaIso(enColombia(ctx.ahora).fecha);
      data.revision = { semana, hechos: await all("select item, hecho_en from revision where semana = ?", semana) };
    }
    return respuesta(ctx, data);
  }

  if (m === "GET" && r0 === "monitoreo") {
    return respuesta(ctx, { ok: true, servicios: await Promise.all(SERVICIOS.map(medir)), hora: ctx.ahora });
  }
  if (m === "GET" && r0 === "concurso") {
    if (!puede(yo.rol, "concurso")) return error(ctx, "permiso", 403);
    try {
      const r = await fetch("https://lifemusic-comunidad.cho--usic.workers.dev/prerregistro/estado", { signal: AbortSignal.timeout(6000) });
      const e = await r.json();
      return respuesta(ctx, { ok: true, contador: e.contador, meta: e.meta, abierto: e.abierto, inicio: e.inicio, fin: e.fin, alargado: e.alargado, premio: e.premio });
    } catch {
      return error(ctx, "sin-respuesta", 502);
    }
  }

  // Ideas
  if (m === "POST" && r0 === "ideas" && !r1) {
    if (!puede(yo.rol, "idea")) return error(ctx, "permiso", 403);
    const fuera = escribir();
    if (fuera) return fuera;
    const c = await leerCuerpo(ctx);
    const titulo = texto(c && c.titulo, 120), paraQue = texto(c && c.paraQue, 600, { multilinea: true, vacio: true }), version = texto(c && c.version, 16, { vacio: true });
    const enlace = texto(c && c.enlace, 300, { vacio: true });
    if (!titulo || titulo.length < 4) return error(ctx, "titulo");
    if (paraQue === null || version === null || enlace === null || (enlace && !/^https:\/\//.test(enlace))) return error(ctx, "campos");
    const n = await db.prepare("select coalesce(max(numero), 0) + 1 as n from ideas").first();
    await db.prepare("insert into ideas (id, numero, titulo, para_que, version, fase, autor_id, enlace, creada_en, actualizada_en) values (?, ?, ?, ?, ?, 1, ?, ?, ?, ?)")
      .bind(nuevoId(), n.n, titulo, paraQue || null, version || null, yo.id, enlace || null, ctx.ahora, ctx.ahora).run();
    await anotar(ctx, yo.id, "idea", `${yo.nombre} propuso «${titulo}».`);
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "ideas" && ID.test(r1 || "") && (r2 === "fase" || r2 === "descartar")) {
    const idea = await db.prepare("select * from ideas where id = ? and descartada = 0").bind(r1).first();
    if (!idea) return error(ctx, "no-existe", 404);
    const fuera = escribir();
    if (fuera) return fuera;
    if (r2 === "descartar") {
      if (!duenio) return error(ctx, "permiso", 403);
      await db.prepare("update ideas set descartada = 1, actualizada_en = ? where id = ?").bind(ctx.ahora, r1).run();
      await anotar(ctx, yo.id, "idea", `Camilo descartó «${idea.titulo}».`);
      return respuesta(ctx, { ok: true });
    }
    const c = await leerCuerpo(ctx);
    const a = c && c.fase;
    if (!puedeMoverIdea(yo.rol, idea.fase, a)) return error(ctx, "permiso", 403);
    const responsable = a === 4 && !idea.responsable_id && yo.rol === "supervisor" ? yo.id : idea.responsable_id;
    await db.prepare("update ideas set fase = ?, responsable_id = ?, actualizada_en = ? where id = ?").bind(a, responsable, ctx.ahora, r1).run();
    const verbo = a === 3 ? "dio luz verde a" : a === 6 ? "lanzó" : "movió";
    await anotar(ctx, yo.id, "idea", `${yo.nombre} ${verbo} «${idea.titulo}»${a === 3 || a === 6 ? "" : ` a ${FASES[a]}`}.`);
    return respuesta(ctx, { ok: true });
  }

  // Incidencias
  if (m === "POST" && r0 === "incidencias" && !r1) {
    if (!puede(yo.rol, "incidencia")) return error(ctx, "permiso", 403);
    const fuera = escribir();
    if (fuera) return fuera;
    const c = await leerCuerpo(ctx);
    const titulo = texto(c && c.titulo, 140), detalle = texto(c && c.detalle, 1000, { multilinea: true, vacio: true });
    if (!titulo || titulo.length < 4 || detalle === null || !["alta", "media", "baja"].includes(c.gravedad)) return error(ctx, "campos");
    await db.prepare("insert into incidencias (id, titulo, detalle, gravedad, autor_id, creada_en) values (?, ?, ?, ?, ?, ?)")
      .bind(nuevoId(), titulo, detalle || null, c.gravedad, yo.id, ctx.ahora).run();
    await anotar(ctx, yo.id, "incidencia", `${yo.nombre} abrió la incidencia «${titulo}».`);
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "incidencias" && ID.test(r1 || "") && r2 === "cerrar") {
    if (!puede(yo.rol, "cerrar-incidencia")) return error(ctx, "permiso", 403);
    const fuera = escribir();
    if (fuera) return fuera;
    const i = await db.prepare("select titulo from incidencias where id = ? and abierta = 1").bind(r1).first();
    if (!i) return error(ctx, "no-existe", 404);
    await db.prepare("update incidencias set abierta = 0, cerrada_en = ? where id = ?").bind(ctx.ahora, r1).run();
    await anotar(ctx, yo.id, "incidencia", `${yo.nombre} cerró «${i.titulo}».`);
    return respuesta(ctx, { ok: true });
  }

  // Revision semanal de Operaciones
  if (m === "POST" && r0 === "revision" && /^[0-3]$/.test(r1 || "")) {
    if (!puede(yo.rol, "revision")) return error(ctx, "permiso", 403);
    const fuera = escribir();
    if (fuera) return fuera;
    const semana = semanaIso(enColombia(ctx.ahora).fecha);
    const hecho = await db.prepare("select 1 from revision where semana = ? and item = ?").bind(semana, +r1).first();
    if (hecho) await db.prepare("delete from revision where semana = ? and item = ?").bind(semana, +r1).run();
    else await db.prepare("insert into revision (semana, item, persona_id, hecho_en) values (?, ?, ?, ?)").bind(semana, +r1, yo.id, ctx.ahora).run();
    return respuesta(ctx, { ok: true });
  }

  // Accesos (un registro: aqui no hay claves)
  if (m === "POST" && r0 === "accesos" && !r1) {
    const fuera = escribir();
    if (fuera) return fuera;
    const c = await leerCuerpo(ctx);
    const plataforma = texto(c && c.plataforma, 40), permiso = texto(c && c.permiso, 80, { vacio: true }), motivo = texto(c && c.motivo, 300, { multilinea: true, vacio: true });
    if (!plataforma || permiso === null || motivo === null) return error(ctx, "campos");
    let para = yo.id, estado = "pedido";
    if (duenio) {
      if (!ID.test(c.personaId || "")) return error(ctx, "persona");
      const p = await db.prepare("select id from personas where id = ? and rol != 'duenio' and estado != 'terminada'").bind(c.personaId).first();
      if (!p) return error(ctx, "persona");
      para = p.id;
      estado = "concedido";
    }
    await db.prepare("insert into accesos (id, persona_id, plataforma, permiso, estado, motivo, creado_en, actualizado_en) values (?, ?, ?, ?, ?, ?, ?, ?)")
      .bind(nuevoId(), para, plataforma, permiso || null, estado, motivo || null, ctx.ahora, ctx.ahora).run();
    await anotar(ctx, yo.id, "acceso", duenio ? `Camilo registró ${plataforma} como concedido.` : `${yo.nombre} pidió acceso a ${plataforma}.`);
    return respuesta(ctx, { ok: true });
  }

  // ── Solo el dueño ──
  if (!duenio) return error(ctx, "no-existe", 404);

  // ── La Sala de control (2.0): lo primero que ve el dueño ──
  if (m === "GET" && r0 === "sala") {
    const all = (sql, ...b) => db.prepare(sql).bind(...b).all().then((r) => r.results);
    const t = await tomaActual(db);
    let toma = null;
    if (t) {
      const pruebas = await all("select id, orden, grupo, titulo, requiere from pruebas where toma_id = ? order by orden", t.id);
      const resultados = await all("select r.prueba_id, r.persona_id, r.estado from resultados r join pruebas p on p.id = r.prueba_id where p.toma_id = ?", t.id);
      const gente = await all(`select p.id, p.nombre, p.rol, p.estado, tl.marca, tl.modelo, tl.android from personas p
                                 left join telefonos tl on tl.persona_id = p.id
                                where p.rol != 'duenio' and p.estado in ('prueba', 'activa', 'pausa') order by p.numero`);
      const supervisores = gente.map((g) => ({
        nombre: g.nombre, estado: g.estado, telefono: textoTelefono(g.marca ? g : null),
        ...resumenHoja(pruebas, new Map(resultados.filter((r) => r.persona_id === g.id).map((r) => [r.prueba_id, r.estado]))),
      }));
      const grupos = Object.fromEntries(GRUPOS.map((g) => [g, pruebas.filter((p) => p.grupo === g).length]));
      toma = { ...vistaToma(t), ...estadoToma(pruebas, resultados), supervisores, grupos, trae: await ideasDeToma(db, t.id) };
    }
    return respuesta(ctx, {
      ok: true,
      alAire: cfg.al_aire_texto ? { texto: cfg.al_aire_texto, sesion: cfg.al_aire_sesion || null, en: +cfg.al_aire_en || null } : null,
      toma,
      ultimaPublicada: t ? null : vistaToma(await ultimaPublicada(db)),
      frentes: await all("select id, nombre, orden, version, estado, avance, espera, sesion, progreso, actualizado_en from frentes order by orden"),
      cinta: await all("select id, numero, titulo, para_que, version, capa, fase, descartada, actualizada_en from ideas order by numero"),
      capas: CAPAS, fases: FASES, versionObjetivo: cfg.version_objetivo || "2.0",
      avances: await all("select id, frente, titulo, detalle, hecho_en from avances order by hecho_en desc limit 20"),
      // Las que abre un supervisor desde su hoja dicen quien, en que telefono y en que punto.
      resenas: await resenasDeLaSala(env),
      incidencias: await all(`select i.id, i.titulo, i.detalle, i.gravedad, i.creada_en, p.nombre as autor,
                                     r.telefono, pr.orden as punto, t.numero as toma
                                from incidencias i left join personas p on p.id = i.autor_id
                                left join resultados r on r.incidencia_id = i.id and r.persona_id = i.autor_id
                                left join pruebas pr on pr.id = r.prueba_id left join tomas t on t.id = pr.toma_id
                               where i.abierta = 1 order by i.creada_en desc`),
      contratacion,
    });
  }
  if (m === "GET" && r0 === "medidores") {
    return respuesta(ctx, { ok: true, ...(await medidores(ctx)) });
  }
  // Las reseñas de lifemusic.app/calificar: el dueño las publica, las oculta o las devuelve a pendiente.
  if (m === "POST" && r0 === "resenas" && ID.test(r1 || "") && r2 === "estado") {
    if (!env.COMUNIDAD) return error(ctx, "sin-comunidad", 503);
    const c = await leerCuerpo(ctx);
    if (!c || !["aprobada", "oculta", "pendiente"].includes(c.estado)) return error(ctx, "estado");
    const r = await env.COMUNIDAD.prepare("update resenas set estado = ?, revisada_en = datetime('now') where id = ?").bind(c.estado, r1).run();
    if (!r.meta.changes) return error(ctx, "no-existe", 404);
    const que = { aprobada: "publicó", oculta: "ocultó", pendiente: "devolvió a pendiente" }[c.estado];
    await anotar(ctx, yo.id, "resena", `Camilo ${que} una reseña de la web.`);
    return respuesta(ctx, { ok: true });
  }
  // El interruptor «Equipo y contratacion»: vuelve a mostrar pases, acta y jornada.
  if (m === "POST" && r0 === "contratacion") {
    const c = await leerCuerpo(ctx);
    if (!c || typeof c.activa !== "boolean") return error(ctx, "cuerpo");
    await db.prepare("insert or replace into config (clave, valor) values ('equipo_contratacion', ?)").bind(c.activa ? "1" : "0").run();
    await anotar(ctx, yo.id, "contratacion", `Camilo ${c.activa ? "encendió" : "apagó"} «Equipo y contratación».`);
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "ideas" && ID.test(r1 || "") && r2 === "capa") {
    const c = await leerCuerpo(ctx);
    const capa = c && c.capa;
    if (!(capa === null || (Number.isInteger(capa) && capa >= 1 && capa < CAPAS.length))) return error(ctx, "capa");
    const r = await db.prepare("update ideas set capa = ?, actualizada_en = ? where id = ?").bind(capa, ctx.ahora, r1).run();
    if (!r.meta.changes) return error(ctx, "no-existe", 404);
    return respuesta(ctx, { ok: true });
  }

  if (m === "GET" && r0 === "produccion") {
    const all = (sql, ...b) => db.prepare(sql).bind(...b).all().then((r) => r.results);
    const personas = await all("select * from personas where rol != 'duenio' order by numero");
    const lista = [];
    for (const p of personas) {
      const acta = await db.prepare("select id, folio, firmada_en, contrafirmada_en from actas where persona_id = ?").bind(p.id).first();
      const ses = await db.prepare("select count(*) as n, max(vista_en) as ultima from sesiones where persona_id = ? and cerrada_en is null and vence_en > ?").bind(p.id, ctx.ahora).first();
      const inv = await db.prepare("select id, vence_en, vista_en from invitaciones where persona_id = ? and usada_en is null and anulada_en is null and vence_en > ? order by creada_en desc").bind(p.id, ctx.ahora).first();
      lista.push({ ...vistaPersona(p, ctx, festivos), acta, aparatos: ses.n, ultimaVez: ses.ultima, invitacion: inv });
    }
    const inicioHoy = Math.floor(msDeColombia(enColombia(ctx.ahora).fecha, 0) / VENTANA);
    const fallos = await db.prepare("select coalesce(sum(fallos), 0) as n from intentos where ventana >= ?").bind(inicioHoy).first();
    return respuesta(ctx, {
      ok: true,
      personas: lista,
      vacantes: +cfg.vacantes || 2,
      porAprobar: await all("select i.id, i.titulo, i.fase, i.version, a.nombre as autor from ideas i left join personas a on a.id = i.autor_id where i.descartada = 0 and i.fase in (2, 5) order by i.actualizada_en"),
      accesosPedidos: await all("select a.id, a.plataforma, a.permiso, a.motivo, p.nombre as persona from accesos a join personas p on p.id = a.persona_id where a.estado = 'pedido' order by a.creado_en"),
      solicitudes: await all("select s.id, s.tipo, s.detalle, s.creada_en, p.nombre as persona from solicitudes s join personas p on p.id = s.persona_id where s.respondida_en is null order by s.creada_en"),
      bitacora: await all("select b.accion, b.detalle, b.aparato, b.creada_en, p.nombre as persona from bitacora b left join personas p on p.id = b.persona_id order by b.id desc limit 60"),
      fallosHoy: fallos.n,
      responsable: { nombre: cfg.responsable_nombre || "", ciudad: cfg.responsable_ciudad || "", correo: cfg.responsable_correo || "" },
    });
  }

  if (m === "POST" && r0 === "invitaciones" && !r1) {
    const c = await leerCuerpo(ctx);
    if (!c) return error(ctx, "cuerpo");
    const nombre = texto(c.nombre, 100);
    if (!nombre || nombre.length < 3) return error(ctx, "nombre");
    if (!["operaciones", "supervisor"].includes(c.rol)) return error(ctx, "rol");
    if (!fechaValida(c.desde) || !fechaValida(c.hasta) || c.hasta < c.desde) return error(ctx, "vigencia");
    if ((Date.parse(c.hasta) - Date.parse(c.desde)) / DIA_MS > 366) return error(ctx, "vigencia");
    const dias = String(c.dias || "").split(",").map(Number);
    if (!dias.length || !dias.every((d) => d >= 1 && d <= 5) || new Set(dias).size !== dias.length) return error(ctx, "dias");
    const ent = aMinutos(c.entrada), sal = aMinutos(c.salida);
    if (!(ent < sal)) return error(ctx, "horario");
    const n = await db.prepare("select coalesce(max(numero), 1) + 1 as n from personas").first();
    const id = nuevoId();
    await db.prepare("insert into personas (id, nombre, rol, estado, numero, desde, hasta, dias, entrada, salida, creada_en) values (?, ?, ?, 'invitada', ?, ?, ?, ?, ?, ?, ?)")
      .bind(id, nombre, c.rol, n.n, c.desde, c.hasta, dias.sort().join(","), c.entrada, c.salida, ctx.ahora).run();
    return respuesta(ctx, await imprimirPase(ctx, { id, nombre, numero: n.n }));
  }
  if (m === "POST" && r0 === "personas" && ID.test(r1 || "") && r2 === "pase") {
    const p = await db.prepare("select id, nombre, numero from personas where id = ? and rol != 'duenio' and estado != 'terminada'").bind(r1).first();
    if (!p) return error(ctx, "no-existe", 404);
    await db.prepare("update invitaciones set anulada_en = ? where persona_id = ? and usada_en is null and anulada_en is null").bind(ctx.ahora, p.id).run();
    return respuesta(ctx, await imprimirPase(ctx, p));
  }
  if (m === "POST" && r0 === "invitaciones" && ID.test(r1 || "") && r2 === "anular") {
    const r = await db.prepare("update invitaciones set anulada_en = ? where id = ? and usada_en is null and anulada_en is null").bind(ctx.ahora, r1).run();
    if (!r.meta.changes) return error(ctx, "no-existe", 404);
    await anotar(ctx, yo.id, "pase-anulado", "Camilo anuló un pase sin usar.");
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "personas" && ID.test(r1 || "") && r2 === "estado") {
    const c = await leerCuerpo(ctx);
    if (!c || !["prueba", "activa", "pausa"].includes(c.estado)) return error(ctx, "estado");
    const p = await db.prepare("select nombre, estado from personas where id = ? and rol != 'duenio'").bind(r1).first();
    if (!p || p.estado === "terminada" || p.estado === "invitada") return error(ctx, "no-existe", 404);
    await db.prepare("update personas set estado = ? where id = ?").bind(c.estado, r1).run();
    await anotar(ctx, yo.id, "persona", `Camilo puso a ${p.nombre} ${c.estado === "pausa" ? "en pausa" : c.estado === "prueba" ? "en prueba" : "activa"}.`);
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "personas" && ID.test(r1 || "") && r2 === "cortar") {
    const p = await db.prepare("select nombre from personas where id = ? and rol != 'duenio' and estado != 'terminada'").bind(r1).first();
    if (!p) return error(ctx, "no-existe", 404);
    await db.batch([
      db.prepare("update personas set estado = 'terminada', terminada_en = ? where id = ?").bind(ctx.ahora, r1),
      db.prepare("update sesiones set cerrada_en = ? where persona_id = ? and cerrada_en is null").bind(ctx.ahora, r1),
      db.prepare("update invitaciones set anulada_en = ? where persona_id = ? and usada_en is null and anulada_en is null").bind(ctx.ahora, r1),
    ]);
    const { results: accesos } = await db.prepare("select id, plataforma, permiso from accesos where persona_id = ? and estado = 'concedido'").bind(r1).all();
    await anotar(ctx, yo.id, "acceso-cortado", `Camilo cortó el acceso de ${p.nombre}. Se cerraron todas sus sesiones.`);
    return respuesta(ctx, { ok: true, accesosPorRetirar: accesos });
  }
  if (m === "GET" && r0 === "actas" && ID.test(r1 || "")) {
    const a = await db.prepare("select * from actas where id = ?").bind(r1).first();
    if (!a) return error(ctx, "no-existe", 404);
    return respuesta(ctx, { ok: true, acta: vistaActa(a, true) });
  }
  if (m === "POST" && r0 === "actas" && ID.test(r1 || "") && r2 === "contrafirmar") {
    const a = await db.prepare("select id, folio, huella, contrafirmada_en, nombre from actas where id = ?").bind(r1).first();
    if (!a) return error(ctx, "no-existe", 404);
    if (a.contrafirmada_en) return error(ctx, "ya-firmada", 409);
    const c = await leerCuerpo(ctx, MAX_FIRMA);
    const f = (c && c.firma) || {};
    const svg = f.tipo === "escrita" ? firmaEscritaSvg(cfg.responsable_nombre || yo.nombre) : f.tipo === "dibujada" ? firmaDibujadaSvg(f.trazos, f.ancho, f.alto) : null;
    if (!svg) return error(ctx, "firma");
    const final = await sha256([a.huella, svg, ctx.ahora].join("\n"));
    await db.prepare("update actas set contrafirma = ?, contrafirmada_en = ?, huella_final = ? where id = ?").bind(svg, ctx.ahora, final, a.id).run();
    await anotar(ctx, yo.id, "acta-completa", `Camilo firmó el acta ${a.folio} de ${a.nombre}. Quedó completa.`);
    return respuesta(ctx, { ok: true, huellaFinal: final });
  }
  if (m === "POST" && r0 === "accesos" && ID.test(r1 || "") && r2 === "estado") {
    const c = await leerCuerpo(ctx);
    if (!c || !["concedido", "retirado"].includes(c.estado)) return error(ctx, "estado");
    const a = await db.prepare("select a.plataforma, p.nombre from accesos a join personas p on p.id = a.persona_id where a.id = ?").bind(r1).first();
    if (!a) return error(ctx, "no-existe", 404);
    await db.prepare("update accesos set estado = ?, actualizado_en = ? where id = ?").bind(c.estado, ctx.ahora, r1).run();
    await anotar(ctx, yo.id, "acceso", `Camilo marcó ${a.plataforma} de ${a.nombre} como ${c.estado}.`);
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "notas" && !r1) {
    const c = await leerCuerpo(ctx);
    const titulo = texto(c && c.titulo, 120), cuerpo = texto(c && c.texto, 1200, { multilinea: true, vacio: true });
    if (!titulo || cuerpo === null) return error(ctx, "campos");
    await db.prepare("insert into notas (id, autor_id, titulo, texto, fijada, creada_en) values (?, ?, ?, ?, ?, ?)")
      .bind(nuevoId(), yo.id, titulo, cuerpo || null, c.fijada ? 1 : 0, ctx.ahora).run();
    await anotar(ctx, yo.id, "nota", `Camilo publicó la nota «${titulo}».`);
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "notas" && ID.test(r1 || "") && r2 === "quitar") {
    await db.prepare("delete from notas where id = ?").bind(r1).run();
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "solicitudes" && ID.test(r1 || "") && r2 === "responder") {
    await db.prepare("update solicitudes set respondida_en = ? where id = ? and respondida_en is null").bind(ctx.ahora, r1).run();
    await anotar(ctx, yo.id, "solicitud-datos", "Camilo marcó una solicitud de datos como respondida.");
    return respuesta(ctx, { ok: true });
  }
  if (m === "POST" && r0 === "responsable") {
    const c = await leerCuerpo(ctx);
    const nombre = texto(c && c.nombre, 100), ciudad = texto(c && c.ciudad, 60), correo = texto(c && c.correo, 120);
    if (!nombre || !ciudad || !correo || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(correo)) return error(ctx, "campos");
    await db.prepare("insert or replace into config (clave, valor) values ('responsable_nombre', ?), ('responsable_ciudad', ?), ('responsable_correo', ?)")
      .bind(nombre, ciudad, correo).run();
    return respuesta(ctx, { ok: true });
  }

  return error(ctx, "no-existe", 404);
}

async function imprimirPase(ctx, p) {
  const codigo = codigoAlAzar(8);
  const vence = ctx.ahora + VIDA_PASE;
  await ctx.env.DB.prepare("insert into invitaciones (id, persona_id, huella, creada_en, vence_en) values (?, ?, ?, ?, ?)")
    .bind(nuevoId(), p.id, await huella(ctx.env, "pase", codigo), ctx.ahora, vence).run();
  await anotar(ctx, null, "pase-impreso", `Camilo imprimió el pase Nº ${String(p.numero).padStart(4, "0")} para ${p.nombre}.`);
  const base = ctx.origen && (PRODUCCION.test(ctx.origen) || DESARROLLO.test(ctx.origen)) ? ctx.origen : "https://lifemusic.app";
  // El codigo va despues del #: el navegador no lo manda al servidor ni queda en registros.
  return { ok: true, codigo: formatoPase(codigo), enlace: `${base}/equipo/#pase=${formatoPase(codigo)}`, venceEn: vence, numero: p.numero, nombre: p.nombre };
}

export default {
  async fetch(req, env, ec) {
    const url = new URL(req.url);
    const ctx = { req, env, ec, url, origen: req.headers.get("origin"), cookies: [], ahora: Date.now(), aparato: aparatoDe(req.headers.get("user-agent")) };
    // Solo en desarrollo (DESARROLLO=1 en .dev.vars, nunca en produccion): fijar la hora para probar la jornada.
    if (env.DESARROLLO === "1" && /^\d{13}$/.test(req.headers.get("x-prueba-ahora") || "")) ctx.ahora = +req.headers.get("x-prueba-ahora");
    if (!url.pathname.startsWith("/equipo/api/")) return error(ctx, "no-existe", 404);
    ctx.ruta = url.pathname.slice("/equipo/api".length);

    if (req.method === "OPTIONS") {
      if (env.DESARROLLO !== "1" || !ctx.origen || !DESARROLLO.test(ctx.origen)) return new Response(null, { status: 403 });
      return respuesta(ctx, null, 204, { "access-control-allow-methods": "GET, POST", "access-control-allow-headers": "content-type, x-equipo", "access-control-max-age": "600" });
    }
    if (req.method !== "GET" && req.method !== "POST") return error(ctx, "metodo", 405);
    // Toda escritura viene de nuestra pagina: Origin propio y la cabecera x-equipo.
    if (req.method === "POST") {
      const propio = ctx.origen && (PRODUCCION.test(ctx.origen) || (env.DESARROLLO === "1" && DESARROLLO.test(ctx.origen)));
      if (!propio || req.headers.get("x-equipo") !== "1") return error(ctx, "origen", 403);
    }
    if (!env.PIMIENTA || env.PIMIENTA.length < 32) return error(ctx, "sin-configurar", 503);
    try {
      return await enrutar(ctx);
    } catch (e) {
      console.error("equipo:", e && e.stack ? e.stack : e);
      return error(ctx, "interno", 500);
    }
  },
};
