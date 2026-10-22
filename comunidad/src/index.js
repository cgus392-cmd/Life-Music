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
     GET  /calificaciones/resumen   promedio y total (solo desde MIN_VISIBLE)
     GET  /insignia.svg             la insignia del README
     POST /resenas                  {notas, nombre, correo, comentario, espera, sitio}
     GET  /resenas                  las reseñas que CG aprobo y el resumen

   Las escrituras solo llegan desde la app: un navegador siempre manda Origin en
   un POST de otro sitio, y aqui se rechaza. La app no lo manda. La unica excepcion
   es /resenas, que llega de lifemusic.app/calificar (Origin propio) con un freno
   de 3 por hora por conexion. Las reseñas salen en publico solo si CG las aprueba
   desde la Sala de control (el Worker del equipo tiene esta base enlazada). */

const ORIGENES = [/^https:\/\/([a-z0-9-]+\.)?lifemusic\.pages\.dev$/, /^https:\/\/(www\.)?lifemusic\.app$/, /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/];
const MAX_CUERPO = 4096;
const MIN_VISIBLE = 25; // menos calificaciones que esto no se muestran en publico
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
// Caracteres de control y marcas invisibles de direccion (sirven para disfrazar un nombre en la Sala).
const CONTROL = /[\u0000-\u001f\u007f\u200e\u200f\u202a-\u202e\u2066-\u2069]/;

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

async function calificar(request, env) {
  const c = await leerCuerpo(request);
  if (!c) return json({ ok: false, error: "cuerpo" }, 400);
  const id = typeof c.id === "string" ? c.id.toLowerCase() : "";
  if (!UUID.test(id)) return json({ ok: false, error: "id" }, 400);
  const estrellas = c.estrellas;
  if (!Number.isInteger(estrellas) || estrellas < 1 || estrellas > 5) return json({ ok: false, error: "estrellas" }, 400);
  const comentario = c.comentario == null || c.comentario === "" ? null : texto(c.comentario, 500);
  if (c.comentario && comentario === null) return json({ ok: false, error: "comentario" }, 400);

  await env.DB.prepare(
    `insert into calificaciones (id, estrellas, comentario, version_app, idioma)
     values (?1, ?2, ?3, ?4, ?5)
     on conflict(id) do update set estrellas = excluded.estrellas, comentario = excluded.comentario,
       version_app = excluded.version_app, idioma = excluded.idioma, cambiada_en = datetime('now')`,
  ).bind(id, estrellas, comentario, texto(c.version, 20), texto(c.idioma, 10)).run();
  return json({ ok: true });
}

// El promedio junta las de la app y las reseñas de la web que CG aprobo.
async function resumen(env) {
  const { results } = await env.DB.prepare(
    `select estrellas, count(*) as n from (
       select estrellas from calificaciones
       union all select notas as estrellas from resenas where estado = 'aprobada'
     ) group by estrellas`,
  ).all();
  const porEstrella = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
  let total = 0;
  let suma = 0;
  for (const r of results) {
    porEstrella[r.estrellas] = r.n;
    total += r.n;
    suma += r.estrellas * r.n;
  }
  // visible sigue igual para la app y la insignia (desde MIN_VISIBLE); la web muestra el promedio desde el primer voto.
  if (!total) return { total, visible: false };
  return { total, visible: total >= MIN_VISIBLE, promedio: Math.round((suma / total) * 10) / 10, porEstrella };
}

// ─── Reseñas de la web (22-10) ──────────────────────────────────────────────

const POR_HORA = 3;

// La sal cambia cada dia: la huella de una conexion de hoy no se puede cruzar con la de mañana.
async function salDelDia(env) {
  const hoy = new Date().toISOString().slice(0, 10);
  const fila = await env.DB.prepare("select valor from config where clave = 'sal_del_dia'").first();
  if (fila && fila.valor.startsWith(hoy + ":")) return fila.valor;
  const sal = hoy + ":" + [...crypto.getRandomValues(new Uint8Array(16))].map((b) => b.toString(16).padStart(2, "0")).join("");
  await env.DB.prepare("insert or replace into config (clave, valor) values ('sal_del_dia', ?1)").bind(sal).run();
  return sal;
}

// La politica promete que la huella se borra a las 24 horas: se limpia al escribir y tambien al leer, para que
// no se quede si nadie vuelve a calificar.
function borrarHuellasViejas(env) {
  return env.DB.prepare("delete from limites where creada_en < datetime('now', '-1 day')").run();
}

async function resenar(request, env) {
  const c = await leerCuerpo(request);
  if (!c) return json({ ok: false, error: "cuerpo" }, 400);
  // La trampa para robots: un campo que una persona no ve. Si viene lleno, se responde que si y no se guarda.
  if (c.sitio) return json({ ok: true });
  if (!Number.isInteger(c.espera) || c.espera < 2500) return json({ ok: false, error: "rapido" }, 400);
  const notas = c.notas;
  if (!Number.isInteger(notas) || notas < 1 || notas > 5) return json({ ok: false, error: "notas" }, 400);
  const nombre = texto(c.nombre, 40);
  if (!nombre || nombre.length < 2) return json({ ok: false, error: "nombre" }, 400);
  let correo = null;
  if (c.correo) {
    correo = texto(c.correo, 120)?.toLowerCase();
    if (!correo || !CORREO.test(correo)) return json({ ok: false, error: "correo" }, 400);
  }
  const plano = typeof c.comentario === "string" ? c.comentario.replace(/\s*\r?\n\s*/g, " ") : c.comentario;
  const comentario = plano == null || plano === "" ? null : texto(plano, 500);
  if (plano && comentario === null) return json({ ok: false, error: "comentario" }, 400);

  const huella = await sha256((request.headers.get("cf-connecting-ip") || "") + "|" + (await salDelDia(env)));
  await borrarHuellasViejas(env);
  const n = await env.DB.prepare("select count(*) as n from limites where huella = ?1 and creada_en > datetime('now', '-1 hour')").bind(huella).first();
  if (n && n.n >= POR_HORA) return json({ ok: false, error: "muchas" }, 429);
  await env.DB.batch([
    env.DB.prepare("insert into limites (huella) values (?1)").bind(huella),
    env.DB.prepare("insert into resenas (id, notas, nombre, correo, comentario) values (?1, ?2, ?3, ?4, ?5)")
      .bind(crypto.randomUUID(), notas, nombre, correo, comentario),
  ]);
  return json({ ok: true });
}

// Lo publico: nombre, notas, comentario y fecha. Nunca el correo.
async function resenasPublicas(env) {
  await borrarHuellasViejas(env).catch(() => {});
  const { results } = await env.DB.prepare(
    "select nombre, notas, comentario, creada_en from resenas where estado = 'aprobada' order by creada_en desc limit 30",
  ).all();
  return {
    resumen: await resumen(env),
    resenas: results.map((r) => ({ nombre: r.nombre, notas: r.notas, comentario: r.comentario, fecha: r.creada_en.slice(0, 10) })),
  };
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
          "access-control-allow-methods": ruta === "/resenas" ? "GET, POST" : "GET",
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
      if (ruta === "/resenas") return json(await resenasPublicas(env), 200, { "cache-control": "public, max-age=120" });
      if (ruta === "/insignia.svg") {
        return new Response(insignia(await resumen(env)), {
          headers: { "content-type": "image/svg+xml; charset=utf-8", "cache-control": "public, max-age=3600" },
        });
      }
      return json({ error: "no-existe" }, 404);
    }

    if (request.method === "POST") {
      // Las reseñas llegan desde lifemusic.app (el Origin ya se reviso arriba).
      if (ruta === "/resenas") return resenar(request, env);
      // Las demas escrituras, solo desde la app (sin Origin).
      if (origen) return json({ error: "solo-app" }, 403);
      if (ruta === "/prerregistro") return prerregistrar(request, env);
      if (ruta === "/prerregistro/salir") return salir(request, env);
      if (ruta === "/calificar") return calificar(request, env);
      return json({ error: "no-existe" }, 404);
    }

    return json({ error: "metodo" }, 405);
  },
};
