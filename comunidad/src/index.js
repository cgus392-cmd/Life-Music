/* Comunidad de Life Music (1.3.1): la calificacion con estrellas y el
   prerregistro del nuevo concurso. Fichas: docs/versiones/1.3.1/calificacion.md
   y prerregistro.md.

   En publico solo salen contadores y promedios. Nombres, correos y comentarios
   se quedan en la base D1 y los consulta CG desde su cuenta; no hay ninguna
   ruta que los devuelva.

   Rutas:
     GET  /prerregistro/estado      abierto, fechas, meta, contador, premio y video
     POST /prerregistro             {id, secreto, nombre, correo, mayor, acepta, version}
     POST /prerregistro/salir       {id, secreto}: borra sus datos
     POST /calificar                {id, estrellas, comentario, version, idioma}
     POST /calificar/web            lo mismo desde lifemusic.pages.dev/calificar
     GET  /calificaciones/resumen   promedio y total de la app (desde MIN_VISIBLE)
     GET  /calificaciones/web       cuantos han votado desde la pagina
     GET  /insignia.svg             la insignia del README

   Las escrituras llegan desde la app: un navegador siempre manda Origin en un
   POST de otro sitio, y aqui se rechaza. La app no lo manda.

   La excepcion es /calificar/web (1.3.3), que existe para repartir un enlace y
   que califiquen sin instalar nada. Esos votos se guardan con origen 'web' y
   NO entran en el promedio publico ni en la insignia: los pone cualquiera que
   reciba el enlace, sin haber usado Life Music, y mezclarlos convertiria «lo
   que opinan quienes la usan» en «lo que opina quien abrio una pagina». Van
   aparte, con un freno por IP, y CG los lee en su panel. */

const ORIGENES = [/^https:\/\/([a-z0-9-]+\.)?lifemusic\.pages\.dev$/, /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/];
const MAX_CUERPO = 4096;
const MIN_VISIBLE = 25; // menos calificaciones que esto no se muestran en publico
const MAX_WEB_IP_DIA = 5; // votos nuevos por IP y dia desde la pagina; cambiar uno ya puesto no cuenta
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const CONTROL = /[\u0000-\u001f\u007f]/;

function json(cuerpo, estado = 200, extra = {}) {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "cache-control": "no-store",
      ...extra,
    },
  });
}

async function sha256(texto) {
  const datos = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  return [...new Uint8Array(datos)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function leerCuerpo(request) {
  const texto = await request.text();
  if (texto.length > MAX_CUERPO) return null;
  try {
    const o = JSON.parse(texto);
    return o && typeof o === "object" ? o : null;
  } catch {
    return null;
  }
}

function texto(v, max) {
  if (typeof v !== "string") return null;
  const t = v.trim();
  return t.length > max || CONTROL.test(t) ? null : t;
}

// ─── Prerregistro ───────────────────────────────────────────────────────────

async function config(env) {
  const { results } = await env.DB.prepare("select clave, valor from config").all();
  return Object.fromEntries(results.map((r) => [r.clave, r.valor]));
}

async function contador(env) {
  const fila = await env.DB.prepare("select count(*) as n from prerregistro").first();
  return fila ? fila.n : 0;
}

/** Abierto: ya empezo y (faltan dias o no se ha llegado a la meta). Se alarga solo. */
export function estadoDelPrerregistro(cfg, n, ahora = Date.now()) {
  const meta = parseInt(cfg.prerregistro_meta || "100", 10);
  const dias = parseInt(cfg.prerregistro_dias || "6", 10);
  const inicio = cfg.prerregistro_inicio ? Date.parse(cfg.prerregistro_inicio) : NaN;
  const empezo = Number.isFinite(inicio) && ahora >= inicio;
  const fin = Number.isFinite(inicio) ? inicio + dias * 86400000 : NaN;
  const abierto = empezo && (ahora < fin || n < meta);
  return {
    empezo,
    abierto,
    alargado: empezo && ahora >= fin && n < meta,
    inicio: Number.isFinite(inicio) ? new Date(inicio).toISOString() : null,
    fin: Number.isFinite(fin) ? new Date(fin).toISOString() : null,
    meta,
    contador: n,
    metaAlcanzada: n >= meta,
    premio: cfg.prerregistro_premio || "",
    video: cfg.prerregistro_video || "",
  };
}

async function estado(env) {
  return estadoDelPrerregistro(await config(env), await contador(env));
}

async function prerregistrar(request, env) {
  const c = await leerCuerpo(request);
  if (!c) return json({ ok: false, error: "cuerpo" }, 400);
  const id = typeof c.id === "string" ? c.id.toLowerCase() : "";
  const secreto = typeof c.secreto === "string" ? c.secreto : "";
  const nombre = texto(c.nombre, 60);
  const correo = texto(c.correo, 120)?.toLowerCase();
  if (!UUID.test(id) || secreto.length < 32 || secreto.length > 128) return json({ ok: false, error: "id" }, 400);
  if (!nombre || nombre.length < 2) return json({ ok: false, error: "nombre" }, 400);
  if (!correo || !CORREO.test(correo)) return json({ ok: false, error: "correo" }, 400);
  if (c.acepta !== true) return json({ ok: false, error: "bases" }, 400);
  if (c.mayor !== true) return json({ ok: false, error: "edad" }, 400);

  const e = await estado(env);
  if (!e.abierto) return json({ ok: false, error: "cerrado", estado: e }, 409);

  const res = await env.DB.prepare(
    `insert into prerregistro (id, secreto_hash, nombre, correo, mayor_de_edad, version_app)
     values (?1, ?2, ?3, ?4, 1, ?5)
     on conflict do nothing`,
  ).bind(id, await sha256(secreto), nombre, correo, texto(c.version, 20)).run();

  // Si el telefono o el correo ya estaban, no se dice de quien es: solo «ya estabas».
  const nuevo = res.meta && res.meta.changes > 0;
  return json({ ok: true, yaEstabas: !nuevo, contador: await contador(env) });
}

async function salir(request, env) {
  const c = await leerCuerpo(request);
  if (!c || typeof c.id !== "string" || typeof c.secreto !== "string") return json({ ok: false, error: "cuerpo" }, 400);
  await env.DB.prepare("delete from prerregistro where id = ?1 and secreto_hash = ?2")
    .bind(c.id.toLowerCase(), await sha256(c.secreto)).run();
  return json({ ok: true, contador: await contador(env) });
}

// ─── Calificacion ───────────────────────────────────────────────────────────

/* Lo que comparten la calificacion de la app y la de la pagina: validar el
   cuerpo. Devuelve {error} o los campos ya limpios. */
async function leerVoto(request) {
  const c = await leerCuerpo(request);
  if (!c) return { error: "cuerpo" };
  const id = typeof c.id === "string" ? c.id.toLowerCase() : "";
  if (!UUID.test(id)) return { error: "id" };
  const estrellas = c.estrellas;
  if (!Number.isInteger(estrellas) || estrellas < 1 || estrellas > 5) return { error: "estrellas" };
  const comentario = c.comentario == null || c.comentario === "" ? null : texto(c.comentario, 500);
  if (c.comentario && comentario === null) return { error: "comentario" };
  return { id, estrellas, comentario, version: texto(c.version, 20), idioma: texto(c.idioma, 10) };
}

/* Guarda el voto. Un id ya visto actualiza el suyo: un voto por telefono (o por
   navegador), que se puede cambiar. El origen no se toca al actualizar, para
   que un voto de la app no pueda pasarse a 'web' ni al reves. */
async function guardarVoto(env, v, origen) {
  await env.DB.prepare(
    `insert into calificaciones (id, estrellas, comentario, version_app, idioma, origen)
     values (?1, ?2, ?3, ?4, ?5, ?6)
     on conflict(id) do update set estrellas = excluded.estrellas, comentario = excluded.comentario,
       version_app = excluded.version_app, idioma = excluded.idioma, cambiada_en = datetime('now')`,
  ).bind(v.id, v.estrellas, v.comentario, v.version, v.idioma, origen).run();
}

async function calificar(request, env) {
  const v = await leerVoto(request);
  if (v.error) return json({ ok: false, error: v.error }, 400);
  await guardarVoto(env, v, "app");
  return json({ ok: true });
}

/* Calificar desde lifemusic.pages.dev/calificar (1.3.3).

   Dos frenos, los que se decidieron: el navegador se guarda su id y reenviarlo
   cambia el voto en vez de sumar uno nuevo, y aqui se limita cuantos votos
   NUEVOS acepta una misma IP al dia. Cambiar un voto ya puesto no gasta cupo,
   porque no mueve el total. Nada de esto frena a alguien decidido con una VPN;
   para eso estaba la opcion de Turnstile, y se dejo fuera a proposito. */
async function calificarWeb(request, env) {
  const v = await leerVoto(request);
  if (v.error) return json({ ok: false, error: v.error }, 400);

  const yaEsta = await env.DB.prepare("select origen from calificaciones where id = ?1").bind(v.id).first();
  if (yaEsta && yaEsta.origen !== "web") return json({ ok: false, error: "id" }, 400);

  if (!yaEsta) {
    const ip = request.headers.get("cf-connecting-ip") || "";
    const dia = new Date().toISOString().slice(0, 10);
    const huella = await sha256(dia + "|" + ip);
    // Las huellas de dias pasados ya no sirven para nada y se van solas.
    await env.DB.prepare("delete from limite_web where dia <> ?1").bind(dia).run();
    const fila = await env.DB.prepare(
      `insert into limite_web (huella, n, dia) values (?1, 1, ?2)
       on conflict(huella) do update set n = n + 1
       returning n`,
    ).bind(huella, dia).first();
    if (fila && fila.n > MAX_WEB_IP_DIA) return json({ ok: false, error: "demasiados" }, 429);
  }

  await guardarVoto(env, v, "web");
  return json({ ok: true });
}

/* El promedio publico y la insignia: SOLO los votos de la app. Los de la pagina
   quedan fuera a proposito (ver la cabecera). */
async function resumen(env) {
  const { results } = await env.DB.prepare(
    "select estrellas, count(*) as n from calificaciones where origen = 'app' group by estrellas",
  ).all();
  const porEstrella = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  let total = 0;
  let suma = 0;
  for (const r of results) {
    porEstrella[r.estrellas] = r.n;
    total += r.n;
    suma += r.estrellas * r.n;
  }
  if (total < MIN_VISIBLE) return { total, visible: false };
  return { total, visible: true, promedio: Math.round((suma / total) * 10) / 10, porEstrella };
}

// La insignia, al estilo de shields.io. Verdana 11 px: unos 7 px por letra.
export function insignia(r) {
  const izq = "Life Music";
  const der = r.visible
    ? `★ ${String(r.promedio).replace(".", ",")} · ${r.total.toLocaleString("es-CO")}`
    : "califícala en la app";
  const ancho = (t) => Math.round(t.length * 7 + 14);
  const a = ancho(izq);
  const b = ancho(der);
  const esc = (t) => t.replace(/&/g, "&amp;").replace(/</g, "&lt;");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${a + b}" height="20" role="img" aria-label="${esc(izq)}: ${esc(der)}">
<title>${esc(izq)}: ${esc(der)}</title>
<linearGradient id="s" x2="0" y2="100%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient>
<clipPath id="r"><rect width="${a + b}" height="20" rx="3" fill="#fff"/></clipPath>
<g clip-path="url(#r)"><rect width="${a}" height="20" fill="#0B0F0D"/><rect x="${a}" width="${b}" height="20" fill="#0F5C43"/><rect width="${a + b}" height="20" fill="url(#s)"/></g>
<g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
<text x="${a / 2}" y="14">${esc(izq)}</text><text x="${a + b / 2}" y="14" fill="#34D399">${esc(der)}</text>
</g></svg>`;
}

/* Lo que ensena la pagina de calificar sobre si misma. Aqui no hay minimo que
   esperar: es su propio contador, no el numero que se publica en el README. */
async function resumenWeb(env) {
  const { results } = await env.DB.prepare(
    "select estrellas, count(*) as n from calificaciones where origen = 'web' group by estrellas",
  ).all();
  let total = 0;
  let suma = 0;
  for (const r of results) {
    total += r.n;
    suma += r.estrellas * r.n;
  }
  return total ? { total, promedio: Math.round((suma / total) * 10) / 10 } : { total: 0 };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const ruta = url.pathname.replace(/\/+$/, "") || "/";
    const origen = request.headers.get("origin");
    if (origen && !ORIGENES.some((r) => r.test(origen))) return json({ error: "origen" }, 403);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "access-control-allow-origin": "*",
          // POST por /calificar/web; su cuerpo es JSON, y eso pide el preflight.
          "access-control-allow-methods": "GET, POST",
          "access-control-allow-headers": "content-type",
          "access-control-max-age": "86400",
        },
      });
    }

    if (request.method === "GET") {
      if (ruta === "/") return json({ servicio: "lifemusic-comunidad", estado: "ok" });
      // Estado del sistema (1.3.2): el Worker responde y su base tambien.
      if (ruta === "/salud") {
        try {
          await env.DB.prepare("select 1").first();
          return json({ servicio: "lifemusic-comunidad", estado: "ok", base: "ok", hora: new Date().toISOString() });
        } catch {
          return json({ servicio: "lifemusic-comunidad", estado: "falla", base: "sin-respuesta" }, 503);
        }
      }
      if (ruta === "/prerregistro/estado") return json(await estado(env), 200, { "cache-control": "public, max-age=30" });
      if (ruta === "/calificaciones/resumen") return json(await resumen(env), 200, { "cache-control": "public, max-age=300" });
      if (ruta === "/calificaciones/web") return json(await resumenWeb(env), 200, { "cache-control": "public, max-age=60" });
      if (ruta === "/insignia.svg") {
        return new Response(insignia(await resumen(env)), {
          headers: { "content-type": "image/svg+xml; charset=utf-8", "cache-control": "public, max-age=3600" },
        });
      }
      return json({ error: "no-existe" }, 404);
    }

    if (request.method === "POST") {
      // La unica escritura que acepta un navegador, y solo desde los origenes
      // de arriba, que ya se comprobaron. Va antes del cierre de la linea
      // siguiente, que es el que vale para todo lo demas.
      if (ruta === "/calificar/web") {
        if (!origen) return json({ ok: false, error: "solo-web" }, 403);
        return calificarWeb(request, env);
      }
      // El resto de escrituras, solo desde la app (sin Origin).
      if (origen) return json({ error: "solo-app" }, 403);
      if (ruta === "/prerregistro") return prerregistrar(request, env);
      if (ruta === "/prerregistro/salir") return salir(request, env);
      if (ruta === "/calificar") return calificar(request, env);
      return json({ error: "no-existe" }, 404);
    }

    return json({ error: "metodo" }, 405);
  },
};
