/* Bandas (Life Music 2.0): grupos de 2 a 8 amigos que escuchan juntos.
   Ficha: docs/versiones/2.0/bandas.md.

   Cada banda es un Durable Object (clase Banda) con nombre por su codigo de 6
   letras. Guarda solo el nombre, el color y los miembros: id al azar, nombre,
   inicial, color, desde cuando, y la huella SHA-256 de su secreto. Sin cuentas:
   el telefono inventa su id y su secreto y los manda en la cabecera
   Authorization («Banda <id>.<secreto>»), nunca en la direccion.

   En vivo vive en el mismo objeto: un WebSocket por persona que habla el
   protocolo JSON de Escuchar juntos (app/.../listentogether/Protocol.kt). Los de
   la banda entran directo, sin pedir permiso. Lo que suena y la cola se guardan
   solo mientras hay alguien y se borran cuando sale el ultimo. Nada de historial.

   Plan gratis: el ping exacto {"type":"ping"} se contesta sin despertar la sala. */

import { DurableObject } from "cloudflare:workers";

const CODIGO = /^[2-9A-HJKMNP-Z]{6}$/;
const ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
const AUTORIZACION = /^Banda ([0-9a-f]{32})\.([0-9a-f]{64})$/;
const COLORES = ["coral", "ambar", "rosa", "menta", "cielo", "lila", "arena", "lima"];
const REACCIONES = ["suena", "me_encanta", "otra_vez", "subele", "a_bailar", "escalofrio"];
const MAX_MIEMBROS = 8;
const MAX_PEDIDOS = 5;
const MAX_CONSULTA = 10; // bandas por consulta de estado (la tarjeta del Inicio)
const MAX_MENSAJE = 256 * 1024;
const MAX_COLA = 500;
// Un telefono lento no frena a todos: a los 8 s arranca la cancion igual.
const ESPERA_CARGA_MS = 8000;
// Quien se cae tiene 2 minutos para volver con su session_token.
const GRACIA_MS = 2 * 60 * 1000;
const PING = '{"type":"ping"}';
const PONG = '{"type":"pong"}';

const NO_EXISTE = { estado: 404, cuerpo: { error: "no-existe" } };
const SIN_PERMISO = { estado: 403, cuerpo: { error: "sin-permiso" } };
const DATOS = { estado: 400, cuerpo: { error: "datos" } };
const LLENA = { estado: 409, cuerpo: { error: "llena" } };
const ok = (cuerpo) => ({ estado: 200, cuerpo });

function respuesta(cuerpo, estado = 200) {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

/** Texto de 1 a `max` letras, sin espacios de sobra; null si no sirve. */
function texto(valor, max) {
  if (typeof valor !== "string") return null;
  const limpio = valor.replace(/\s+/g, " ").trim();
  const largo = Array.from(limpio).length;
  return largo >= 1 && largo <= max ? limpio : null;
}

async function huellaDe(secreto) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secreto));
  return [...new Uint8Array(bytes)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function quienEs(request) {
  const m = AUTORIZACION.exec(request.headers.get("authorization") || "");
  return m ? { id: m[1], huella: await huellaDe(m[2]) } : null;
}

function codigoNuevo() {
  return Array.from(crypto.getRandomValues(new Uint8Array(6)), (b) => ALFABETO[b % ALFABETO.length]).join("");
}

/** La persona que manda el telefono, lista para guardar; null si no sirve. */
function personaDe(yo, persona) {
  const nombre = texto(yo && yo.nombre, 20);
  if (!nombre || !COLORES.includes(yo.color)) return null;
  return {
    id: persona.id,
    huella: persona.huella,
    nombre,
    inicial: Array.from(nombre)[0].toLocaleUpperCase("es"),
    color: yo.color,
    desde: new Date().toISOString(),
  };
}

const publico = (m) => ({ id: m.id, nombre: m.nombre, inicial: m.inicial, color: m.color, desde: m.desde });

/** Una cancion del protocolo (TrackInfo) con lo minimo para sonar. */
function pista(t) {
  return !!t && typeof t.id === "string" && t.id.length > 0 && t.id.length <= 64 &&
    typeof t.title === "string" && typeof t.artist === "string" && typeof t.duration === "number";
}

async function cuerpoDe(request) {
  try {
    const datos = await request.json();
    return datos && typeof datos === "object" ? datos : {};
  } catch {
    return {};
  }
}

/** Rutas de las bandas; null si la ruta no es de bandas.
    POST /bandas · POST /bandas/estado · GET y DELETE /banda/COD ·
    POST /banda/COD/pedir|aprobar|rechazar|quitar|salir · WS /banda/COD/envivo */
export async function rutaBandas(request, env, partes) {
  if (partes[0] !== "bandas" && partes[0] !== "banda") return null;
  const persona = await quienEs(request);
  if (!persona) return respuesta({ error: "sin-identidad" }, 401);
  const banda = (codigo) => env.BANDAS.get(env.BANDAS.idFromName(codigo));

  if (partes[0] === "bandas") {
    if (request.method !== "POST") return respuesta({ error: "metodo" }, 405);
    const datos = await cuerpoDe(request);
    if (partes.length === 1) {
      // Si el codigo ya es de otra banda, se prueba otro.
      for (let intento = 0; intento < 5; intento++) {
        const codigo = codigoNuevo();
        const r = await banda(codigo).crear(codigo, persona, datos);
        if (r.cuerpo.error !== "codigo-ocupado") return respuesta(r.cuerpo, r.estado);
      }
      return respuesta({ error: "intenta-de-nuevo" }, 503);
    }
    if (partes[1] === "estado" && partes.length === 2) {
      const codigos = Array.isArray(datos.codigos)
        ? [...new Set(datos.codigos)].filter((c) => typeof c === "string" && CODIGO.test(c)).slice(0, MAX_CONSULTA)
        : [];
      const bandas = await Promise.all(codigos.map((c) => banda(c).estadoCorto(c, persona)));
      return respuesta({ bandas });
    }
    return respuesta({ error: "no-existe" }, 404);
  }

  const codigo = (partes[1] || "").toUpperCase();
  if (!CODIGO.test(codigo) || partes.length > 3) return respuesta({ error: "no-existe" }, 404);
  const accion = partes[2] || "";
  const b = banda(codigo);

  if (accion === "envivo") {
    if (request.headers.get("upgrade") !== "websocket") return respuesta({ error: "solo-websocket" }, 426);
    // Al objeto le llega quien es ya verificado; el secreto no sale de aqui.
    const cabeceras = new Headers(request.headers);
    cabeceras.delete("authorization");
    cabeceras.set("x-persona", persona.id);
    cabeceras.set("x-huella", persona.huella);
    return b.fetch(new Request(request.url, { headers: cabeceras }));
  }

  let r;
  if (request.method === "GET" && !accion) r = await b.ver(codigo, persona);
  else if (request.method === "DELETE" && !accion) r = await b.borrar(persona);
  else if (request.method === "POST" && accion === "pedir") r = await b.pedir(persona, await cuerpoDe(request));
  else if (request.method === "POST" && ["aprobar", "rechazar", "quitar"].includes(accion)) {
    r = await b.decidir(accion, persona, (await cuerpoDe(request)).id);
  } else if (request.method === "POST" && accion === "salir") r = await b.salir(persona);
  else return respuesta({ error: "no-existe" }, 404);
  return respuesta(r.cuerpo, r.estado);
}

export class Banda extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair(PING, PONG));
  }

  // ---------- La banda ----------

  async datos() {
    return (await this.ctx.storage.get("banda")) || null;
  }

  async guardar(b) {
    await this.ctx.storage.put("banda", b);
  }

  miembro(b, persona) {
    return b.miembros.some((m) => m.id === persona.id && m.huella === persona.huella);
  }

  pendiente(b, persona) {
    return b.pedidos.some((p) => p.id === persona.id && p.huella === persona.huella);
  }

  nombreDe(b, id) {
    return (b && b.miembros.find((m) => m.id === id)?.nombre) || "";
  }

  async vista(b, id) {
    return {
      codigo: b.codigo,
      estado: "miembro",
      nombre: b.nombre,
      color: b.color,
      creada: b.creada,
      jefe: b.jefe,
      yo: id,
      miembros: b.miembros.map(publico),
      // Solo quien la creo ve quien pide entrar: es quien aprueba.
      pedidos: id === b.jefe ? b.pedidos.map(publico) : [],
      envivo: await this.enVivo(b),
    };
  }

  /** Lo que ve quien espera aprobacion: «Esperando a Camila». */
  vistaPendiente(b) {
    return {
      codigo: b.codigo,
      estado: "pendiente",
      nombre: b.nombre,
      color: b.color,
      jefe: this.nombreDe(b, b.jefe),
      colores: b.miembros.map((m) => m.color),
    };
  }

  async crear(codigo, persona, datos) {
    if (await this.datos()) return { estado: 409, cuerpo: { error: "codigo-ocupado" } };
    const nombre = texto(datos.nombre, 30);
    const yo = personaDe(datos.yo, persona);
    if (!nombre || !COLORES.includes(datos.color) || !yo) return DATOS;
    const b = {
      codigo,
      nombre,
      color: datos.color,
      creada: yo.desde,
      jefe: persona.id,
      miembros: [yo],
      pedidos: [],
      rechazados: [],
    };
    await this.guardar(b);
    return ok(await this.vista(b, persona.id));
  }

  async ver(codigo, persona) {
    const b = await this.datos();
    if (!b) return NO_EXISTE;
    if (this.miembro(b, persona)) return ok(await this.vista(b, persona.id));
    if (this.pendiente(b, persona)) return ok(this.vistaPendiente(b));
    if (b.rechazados.includes(persona.id)) return { estado: 403, cuerpo: { codigo, estado: "rechazado" } };
    return NO_EXISTE;
  }

  /** El estado corto de una banda para la tarjeta del Inicio («Tu banda esta en vivo»). */
  async estadoCorto(codigo, persona) {
    const b = await this.datos();
    if (!b) return { codigo, estado: "no-existe" };
    if (this.miembro(b, persona)) {
      return {
        codigo,
        estado: "miembro",
        nombre: b.nombre,
        color: b.color,
        miembros: b.miembros.length,
        pedidos: persona.id === b.jefe ? b.pedidos.length : 0,
        envivo: await this.enVivo(b),
      };
    }
    if (this.pendiente(b, persona)) return { codigo, estado: "pendiente", nombre: b.nombre };
    if (b.rechazados.includes(persona.id)) return { codigo, estado: "rechazado" };
    return { codigo, estado: "no-existe" };
  }

  async pedir(persona, datos) {
    const b = await this.datos();
    if (!b) return NO_EXISTE;
    if (this.miembro(b, persona)) return ok(await this.vista(b, persona.id));
    // El id es de otra persona de la banda pero el secreto no cuadra.
    if (b.miembros.some((m) => m.id === persona.id)) return SIN_PERMISO;
    if (b.rechazados.includes(persona.id)) return { estado: 403, cuerpo: { error: "rechazado" } };
    if (b.miembros.length >= MAX_MIEMBROS) return LLENA;
    const yo = personaDe(datos.yo, persona);
    if (!yo) return DATOS;
    const i = b.pedidos.findIndex((p) => p.id === persona.id);
    if (i >= 0) {
      if (b.pedidos[i].huella !== persona.huella) return SIN_PERMISO;
      b.pedidos[i] = yo;
    } else {
      if (b.pedidos.length >= MAX_PEDIDOS) return { estado: 429, cuerpo: { error: "muchos-pedidos" } };
      b.pedidos.push(yo);
    }
    await this.guardar(b);
    return ok(this.vistaPendiente(b));
  }

  /** aprobar, rechazar o quitar: solo quien la creo (o a quien le paso la banda). */
  async decidir(accion, persona, id) {
    const b = await this.datos();
    if (!b || !this.miembro(b, persona)) return NO_EXISTE;
    if (persona.id !== b.jefe) return SIN_PERMISO;
    if (typeof id !== "string") return DATOS;
    if (accion === "quitar") {
      if (id === b.jefe || !b.miembros.some((m) => m.id === id)) return DATOS;
      b.miembros = b.miembros.filter((m) => m.id !== id);
      await this.guardar(b);
      await this.sacarDeLaSala(b, id, "quitado");
      return ok(await this.vista(b, persona.id));
    }
    const pedido = b.pedidos.find((p) => p.id === id);
    if (!pedido) return { estado: 404, cuerpo: { error: "sin-pedido" } };
    if (accion === "aprobar") {
      if (b.miembros.length >= MAX_MIEMBROS) return LLENA;
      b.miembros.push({ ...pedido, desde: new Date().toISOString() });
    } else {
      b.rechazados = [...b.rechazados.filter((x) => x !== id), id].slice(-50);
    }
    b.pedidos = b.pedidos.filter((p) => p.id !== id);
    await this.guardar(b);
    return ok(await this.vista(b, persona.id));
  }

  async salir(persona) {
    const b = await this.datos();
    if (!b || !this.miembro(b, persona)) return NO_EXISTE;
    b.miembros = b.miembros.filter((m) => m.id !== persona.id);
    if (b.miembros.length === 0) {
      await this.sacarDeLaSala(b, persona.id, "salio");
      await this.ctx.storage.deleteAlarm();
      await this.ctx.storage.deleteAll();
      return ok({ estado: "fuera" });
    }
    // Si se va quien la creo, la banda pasa a quien lleva mas tiempo en ella.
    if (b.jefe === persona.id) b.jefe = b.miembros[0].id;
    await this.guardar(b);
    await this.sacarDeLaSala(b, persona.id, "salio");
    return ok({ estado: "fuera" });
  }

  async borrar(persona) {
    const b = await this.datos();
    if (!b || !this.miembro(b, persona)) return NO_EXISTE;
    if (persona.id !== b.jefe) return SIN_PERMISO;
    for (const ws of this.ctx.getWebSockets()) {
      this.enviar(ws, "kicked", { reason: "banda-borrada" });
      this.cerrar(ws, 4004, "banda-borrada");
    }
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
    return ok({ estado: "borrada" });
  }

  // ---------- En vivo ----------

  async fetch(request) {
    const persona = { id: request.headers.get("x-persona"), huella: request.headers.get("x-huella") };
    const b = await this.datos();
    if (!b || !this.miembro(b, persona)) return respuesta({ error: "no-existe" }, 404);
    // Una conexion por persona: el telefono que vuelve reemplaza a la vieja.
    for (const vieja of this.ctx.getWebSockets(persona.id)) {
      vieja.serializeAttachment({ ...(vieja.deserializeAttachment() || {}), enSala: false });
      this.cerrar(vieja, 4000, "reemplazado");
    }
    const [cliente, servidor] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(servidor, [persona.id]);
    servidor.serializeAttachment({ id: persona.id, enSala: false, ultimaReaccion: 0 });
    return new Response(null, { status: 101, webSocket: cliente });
  }

  salaVacia() {
    return {
      anfitrion: null,
      cancion: null,
      sonando: false,
      posicion: 0,
      actualizado: Date.now(),
      volumen: 1,
      cola: [],
      control: false,
      carga: null,
      sesiones: {},
      ausentes: {},
      sugerencias: {},
    };
  }

  async sala() {
    return (await this.ctx.storage.get("sala")) || this.salaVacia();
  }

  async guardarSala(sala) {
    if (this.enSala().length === 0 && Object.keys(sala.ausentes).length === 0) {
      // Se fue el ultimo: no queda nada de lo que sono.
      await this.ctx.storage.delete("sala");
      await this.ctx.storage.deleteAlarm();
      return;
    }
    await this.ctx.storage.put("sala", sala);
    const fechas = Object.values(sala.ausentes);
    if (sala.carga) fechas.push(sala.carga.hasta);
    if (fechas.length) await this.ctx.storage.setAlarm(Math.min(...fechas));
    else await this.ctx.storage.deleteAlarm();
  }

  /** Los ids que estan en la sala ahora, en el orden de la banda (el mas antiguo primero). */
  enSala(menos) {
    const ids = new Set();
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === menos) continue;
      const a = ws.deserializeAttachment();
      if (a && a.enSala) ids.add(a.id);
    }
    return [...ids];
  }

  ordenar(b, ids) {
    const orden = b.miembros.map((m) => m.id);
    return [...ids].sort((x, y) => orden.indexOf(x) - orden.indexOf(y));
  }

  posicionAhora(sala) {
    return sala.sonando ? sala.posicion + (Date.now() - sala.actualizado) : sala.posicion;
  }

  estadoSala(b, sala) {
    const enLinea = this.enSala();
    const ids = this.ordenar(b, new Set([...enLinea, ...Object.keys(sala.ausentes)]));
    return {
      room_code: b.codigo,
      host_id: sala.anfitrion || "",
      users: ids.map((id) => ({
        user_id: id,
        username: this.nombreDe(b, id),
        is_host: id === sala.anfitrion,
        is_connected: enLinea.includes(id),
      })),
      current_track: sala.cancion,
      is_playing: sala.sonando,
      position: this.posicionAhora(sala),
      last_update: Date.now(),
      volume: sala.volumen,
      queue: sala.cola,
      allow_participant_control: sala.control,
    };
  }

  sincronia(sala) {
    return {
      current_track: sala.cancion,
      is_playing: sala.sonando,
      position: this.posicionAhora(sala),
      last_update: Date.now(),
      queue: sala.cola,
      volume: sala.volumen,
    };
  }

  /** «En vivo ahora» para la banda y el Inicio; null si no hay nadie. */
  async enVivo(b) {
    const ids = this.enSala();
    if (ids.length === 0) return null;
    const sala = await this.sala();
    const c = sala.cancion;
    return {
      anfitrion: this.nombreDe(b, sala.anfitrion),
      cancion: c ? { titulo: c.title, artista: c.artist, caratula: c.thumbnail || null } : null,
      escuchando: ids.length,
    };
  }

  async webSocketMessage(ws, mensaje) {
    const crudo = typeof mensaje === "string" ? mensaje : new TextDecoder().decode(mensaje);
    if (crudo.length > MAX_MENSAJE) return this.enviar(ws, "error", { code: "mensaje_grande", message: "Mensaje muy grande" });
    let tipo;
    let datos;
    try {
      const m = JSON.parse(crudo);
      tipo = m.type;
      datos = m.payload && typeof m.payload === "object" ? m.payload : {};
    } catch {
      return;
    }
    if (tipo === "ping") return this.enviar(ws, "pong");

    const yo = ws.deserializeAttachment() || {};
    const b = await this.datos();
    if (!b || !b.miembros.some((m) => m.id === yo.id)) {
      this.enviar(ws, "kicked", { reason: "fuera-de-la-banda" });
      return this.cerrar(ws, 4003, "fuera-de-la-banda");
    }
    const sala = await this.sala();

    if (tipo === "create_room" || tipo === "join_room") return this.entrar(ws, yo, b, sala);
    if (tipo === "reconnect") return this.volver(ws, yo, b, sala, datos.session_token);
    if (!yo.enSala) return this.enviar(ws, "error", { code: "not_in_room", message: "Primero entra a la sala" });

    switch (tipo) {
      case "leave_room": return this.irse(ws, yo, b, sala);
      case "playback_action": return this.mover(ws, yo, b, sala, datos);
      case "buffer_ready": return this.cargado(yo, sala, datos.track_id);
      case "request_sync": return this.enviar(ws, "sync_state", this.sincronia(sala));
      case "transfer_host": return this.pasarMando(ws, yo, b, sala, datos.new_host_id);
      case "suggest_track": return this.sugerir(ws, yo, b, sala, datos.track_info);
      case "approve_suggestion":
      case "reject_suggestion": return this.decidirSugerencia(yo, sala, tipo, datos);
      case "update_room_settings": return this.ajustar(ws, yo, sala, datos);
      case "reaccion": return this.reaccionar(ws, yo, b, sala, datos.tipo);
      // chat, kick_user y la aprobacion de Metrolist no aplican en una banda.
      default: return;
    }
  }

  async entrar(ws, yo, b, sala) {
    const enLinea = this.enSala(ws);
    if (enLinea.length === 0 && Object.keys(sala.ausentes).length === 0) sala = this.salaVacia();
    const volvia = yo.id in sala.ausentes;
    delete sala.ausentes[yo.id];
    for (const [t, id] of Object.entries(sala.sesiones)) if (id === yo.id) delete sala.sesiones[t];
    const token = "s-" + crypto.randomUUID().replace(/-/g, "");
    sala.sesiones[token] = yo.id;
    ws.serializeAttachment({ ...yo, enSala: true });
    const nombre = this.nombreDe(b, yo.id);

    // El primero que entra pone la musica; tambien si quien la ponia no esta.
    const anfitrion = !sala.anfitrion || !enLinea.includes(sala.anfitrion);
    if (anfitrion) sala.anfitrion = yo.id;
    await this.guardarSala(sala);

    if (anfitrion) this.enviar(ws, "room_created", { room_code: b.codigo, user_id: yo.id, session_token: token });
    else this.enviar(ws, "join_approved", { room_code: b.codigo, user_id: yo.id, session_token: token, state: this.estadoSala(b, sala) });
    this.aTodos(volvia ? "user_reconnected" : "user_joined", { user_id: yo.id, username: nombre }, ws);
    if (anfitrion && enLinea.length) this.aTodos("host_changed", { new_host_id: yo.id, new_host_name: nombre }, ws);
  }

  async volver(ws, yo, b, sala, token) {
    if (typeof token !== "string" || sala.sesiones[token] !== yo.id) {
      return this.enviar(ws, "error", { code: "session_not_found", message: "La sesion ya no existe" });
    }
    const enLinea = this.enSala(ws);
    delete sala.ausentes[yo.id];
    ws.serializeAttachment({ ...yo, enSala: true });
    const nombre = this.nombreDe(b, yo.id);
    const toma = sala.anfitrion !== yo.id && (!sala.anfitrion || !enLinea.includes(sala.anfitrion));
    if (toma) sala.anfitrion = yo.id;
    await this.guardarSala(sala);
    this.enviar(ws, "reconnected", {
      room_code: b.codigo,
      user_id: yo.id,
      state: this.estadoSala(b, sala),
      is_host: sala.anfitrion === yo.id,
    });
    this.aTodos("user_reconnected", { user_id: yo.id, username: nombre }, ws);
    if (toma && enLinea.length) this.aTodos("host_changed", { new_host_id: yo.id, new_host_name: nombre }, ws);
  }

  async irse(ws, yo, b, sala) {
    ws.serializeAttachment({ ...yo, enSala: false });
    await this.quitarDeLaSala(b, sala, yo.id);
  }

  /** Saca a una persona de la sala (se fue, la quitaron o se le acabo la gracia). */
  async quitarDeLaSala(b, sala, id) {
    delete sala.ausentes[id];
    for (const [t, dueno] of Object.entries(sala.sesiones)) if (dueno === id) delete sala.sesiones[t];
    for (const [s, sug] of Object.entries(sala.sugerencias)) if (sug.de === id) delete sala.sugerencias[s];
    if (sala.carga) sala.carga.esperando = sala.carga.esperando.filter((x) => x !== id);
    this.aTodos("user_left", { user_id: id, username: this.nombreDe(b, id) });
    if (sala.anfitrion === id) this.pasarAlSiguiente(b, sala);
    await this.guardarSala(sala);
  }

  /** El mando pasa a quien lleva mas tiempo en la banda de los que estan en la sala. */
  pasarAlSiguiente(b, sala) {
    const siguiente = this.ordenar(b, this.enSala())[0] || null;
    sala.anfitrion = siguiente;
    if (siguiente) this.aTodos("host_changed", { new_host_id: siguiente, new_host_name: this.nombreDe(b, siguiente) });
  }

  async sacarDeLaSala(b, id, motivo) {
    const sockets = this.ctx.getWebSockets(id);
    for (const ws of sockets) {
      this.enviar(ws, "kicked", { reason: motivo });
      ws.serializeAttachment({ ...(ws.deserializeAttachment() || {}), enSala: false });
    }
    const sala = await this.ctx.storage.get("sala");
    if (sala && (sala.anfitrion === id || id in sala.ausentes || Object.values(sala.sesiones).includes(id))) {
      await this.quitarDeLaSala(b, sala, id);
    }
    for (const ws of sockets) this.cerrar(ws, 4003, motivo);
  }

  async mover(ws, yo, b, sala, datos) {
    if (yo.id !== sala.anfitrion && !sala.control) {
      return this.enviar(ws, "error", { code: "not_host", message: "Solo quien pone la musica" });
    }
    const ahora = Date.now();
    const numero = (v, otro) => (typeof v === "number" && Number.isFinite(v) && v >= 0 ? v : otro);
    let esperando = null;
    switch (datos.action) {
      case "play":
      case "pause":
        sala.posicion = numero(datos.position, this.posicionAhora(sala));
        sala.sonando = datos.action === "play";
        sala.actualizado = ahora;
        break;
      case "seek":
        sala.posicion = numero(datos.position, 0);
        sala.actualizado = ahora;
        break;
      case "change_track":
        if (!pista(datos.track_info)) return;
        sala.cancion = datos.track_info;
        sala.sonando = false;
        sala.posicion = 0;
        sala.actualizado = ahora;
        // Todos los demas cargan la cancion antes de arrancar.
        esperando = this.enSala(ws);
        sala.carga = esperando.length ? { cancion: datos.track_info.id, esperando, hasta: ahora + ESPERA_CARGA_MS } : null;
        break;
      case "queue_add":
        if (!pista(datos.track_info) || sala.cola.length >= MAX_COLA) return;
        sala.cola = datos.insert_next ? [datos.track_info, ...sala.cola] : [...sala.cola, datos.track_info];
        break;
      case "queue_remove":
        sala.cola = sala.cola.filter((t) => t.id !== datos.track_id);
        break;
      case "queue_clear":
        sala.cola = [];
        break;
      case "sync_queue":
        if (!Array.isArray(datos.queue)) return;
        sala.cola = datos.queue.filter(pista).slice(0, MAX_COLA);
        break;
      case "set_volume":
        sala.volumen = Math.min(1, Math.max(0, numero(datos.volume, sala.volumen)));
        break;
      case "skip_next":
      case "skip_prev":
        break;
      default:
        return;
    }
    await this.guardarSala(sala);
    // Quien mueve no recibe su propio eco: el anfitrion se correria a si mismo.
    this.aTodos("sync_playback", { ...datos, server_time: ahora }, ws);
    if (esperando && esperando.length) this.aTodos("buffer_wait", { track_id: datos.track_info.id, waiting_for: esperando });
  }

  async cargado(yo, sala, trackId) {
    if (!sala.carga || sala.carga.cancion !== trackId) return;
    sala.carga.esperando = sala.carga.esperando.filter((id) => id !== yo.id);
    if (sala.carga.esperando.length === 0) {
      this.aTodos("buffer_complete", { track_id: trackId });
      sala.carga = null;
    }
    await this.guardarSala(sala);
  }

  async pasarMando(ws, yo, b, sala, nuevo) {
    if (yo.id !== sala.anfitrion) return this.enviar(ws, "error", { code: "not_host", message: "Solo quien pone la musica" });
    if (nuevo === yo.id || !this.enSala().includes(nuevo)) return this.enviar(ws, "error", { code: "user_not_found", message: "No esta en la sala" });
    sala.anfitrion = nuevo;
    await this.guardarSala(sala);
    this.aTodos("host_changed", { new_host_id: nuevo, new_host_name: this.nombreDe(b, nuevo) });
  }

  async sugerir(ws, yo, b, sala, track) {
    if (yo.id === sala.anfitrion || !pista(track)) return;
    if (!sala.anfitrion || !this.enSala().includes(sala.anfitrion)) {
      return this.enviar(ws, "error", { code: "host_not_connected", message: "No hay quien ponga la musica" });
    }
    const nombre = this.nombreDe(b, yo.id);
    const id = crypto.randomUUID();
    // «La puso Andres» en la cola.
    const conNombre = { ...track, suggested_by: nombre };
    const viejas = Object.keys(sala.sugerencias);
    if (viejas.length >= 20) delete sala.sugerencias[viejas[0]];
    sala.sugerencias[id] = { de: yo.id, pista: conNombre };
    await this.guardarSala(sala);
    this.aUno(sala.anfitrion, "suggestion_received", {
      suggestion_id: id,
      from_user_id: yo.id,
      from_username: nombre,
      track_info: conNombre,
    });
  }

  async decidirSugerencia(yo, sala, tipo, datos) {
    if (yo.id !== sala.anfitrion) return;
    const sug = sala.sugerencias[datos.suggestion_id];
    if (!sug) return;
    delete sala.sugerencias[datos.suggestion_id];
    await this.guardarSala(sala);
    // La cola la actualiza el telefono del anfitrion cuando la suma a su reproductor.
    if (tipo === "approve_suggestion") {
      this.aUno(sug.de, "suggestion_approved", { suggestion_id: datos.suggestion_id, track_info: sug.pista });
    } else {
      this.aUno(sug.de, "suggestion_rejected", { suggestion_id: datos.suggestion_id, reason: datos.reason || null });
    }
  }

  async ajustar(ws, yo, sala, datos) {
    if (yo.id !== sala.anfitrion) return;
    sala.control = datos.allow_participant_control === true;
    await this.guardarSala(sala);
    this.aTodos("room_settings_changed", { allow_participant_control: sala.control }, ws);
  }

  /** Las 6 reacciones de la maqueta. No se guardan; una por segundo por persona. */
  reaccionar(ws, yo, b, sala, tipo) {
    if (!REACCIONES.includes(tipo)) return;
    const ahora = Date.now();
    if (ahora - (yo.ultimaReaccion || 0) < 1000) return;
    ws.serializeAttachment({ ...yo, ultimaReaccion: ahora });
    this.aTodos("reaccion", { user_id: yo.id, username: this.nombreDe(b, yo.id), tipo, track_id: sala.cancion?.id || null }, ws);
  }

  async webSocketClose(ws) {
    await this.caida(ws);
    this.cerrar(ws, 1000, "adios");
  }

  async webSocketError(ws) {
    await this.caida(ws);
  }

  /** Se cayo una conexion: la persona queda «ausente» 2 minutos por si vuelve. */
  async caida(ws) {
    const yo = ws.deserializeAttachment() || {};
    if (!yo.enSala) return;
    ws.serializeAttachment({ ...yo, enSala: false });
    // Si ya entro una conexion nueva de la misma persona, esta solo sobraba.
    if (this.ctx.getWebSockets(yo.id).some((x) => x !== ws)) return;
    const b = await this.datos();
    const sala = await this.ctx.storage.get("sala");
    if (!b || !sala) return;
    sala.ausentes[yo.id] = Date.now() + GRACIA_MS;
    this.aTodos("user_disconnected", { user_id: yo.id, username: this.nombreDe(b, yo.id) }, ws);
    // Si se cae quien pone la musica y hay alguien mas, el mando pasa de una.
    if (sala.anfitrion === yo.id && this.enSala(ws).length) this.pasarAlSiguiente(b, sala);
    await this.guardarSala(sala);
  }

  async alarm() {
    const sala = await this.ctx.storage.get("sala");
    if (!sala) return;
    const b = await this.datos();
    const ahora = Date.now();
    if (sala.carga && ahora >= sala.carga.hasta) {
      this.aTodos("buffer_complete", { track_id: sala.carga.cancion });
      sala.carga = null;
    }
    for (const [id, hasta] of Object.entries(sala.ausentes)) {
      if (ahora >= hasta) await this.quitarDeLaSala(b, sala, id);
    }
    await this.guardarSala(sala);
  }

  enviar(ws, tipo, datos) {
    try {
      ws.send(JSON.stringify(datos === undefined ? { type: tipo } : { type: tipo, payload: datos }));
    } catch {}
  }

  aTodos(tipo, datos, menos) {
    for (const ws of this.ctx.getWebSockets()) {
      if (ws !== menos && ws.deserializeAttachment()?.enSala) this.enviar(ws, tipo, datos);
    }
  }

  aUno(id, tipo, datos) {
    for (const ws of this.ctx.getWebSockets(id)) {
      if (ws.deserializeAttachment()?.enSala) this.enviar(ws, tipo, datos);
    }
  }

  cerrar(ws, codigo, motivo) {
    try {
      ws.close(codigo, motivo);
    } catch {}
  }
}
