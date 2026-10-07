/* Relevo de Life Music TV.

   El receptor web (lifemusic.pages.dev/tv) y el telefono no pueden hablarse por
   la red de la casa: una pagina https no puede abrir ws:// hacia una IP local.
   Asi que los dos se conectan aqui y este Worker les pasa los mensajes.

   Cada «cuarto» es un Durable Object con dos lados, «tv» y «tel»:

   - c-XXXXXX  cuarto del codigo. El TV lo abre con un codigo que inventa el
               mismo; el telefono entra con ese codigo. En cuanto los dos estan,
               se crea un token y se les manda a ambos (_enlazado).
   - t-<hex32> cuarto del token. Ahi transcurre la sesion, y ahi vuelven los dos
               la proxima vez sin codigo (el TV y el telefono guardan el token).

   No se guarda nada: ni canciones ni tokens. Los mensajes se reenvian tal cual
   al otro lado y se olvidan. Los mensajes propios del relevo empiezan por «_».

   Con la API de hibernacion, un cuarto sin mensajes no consume tiempo, y el
   ping/pong de los clientes se contesta sin despertarlo. */

import { DurableObject } from "cloudflare:workers";

const CUARTO = /^(c-[2-9A-HJKMNP-Z]{6}|t-[0-9a-f]{32})$/;
const ORIGENES = [/^https:\/\/([a-z0-9-]+\.)?lifemusic\.pages\.dev$/, /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/];
const MAX_MENSAJE = 512 * 1024; // la letra con palabras cronometradas cabe de sobra

function respuesta(cuerpo, estado = 200) {
  return new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { "content-type": "application/json; charset=utf-8", "access-control-allow-origin": "*" },
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const partes = url.pathname.split("/").filter(Boolean);
    if (partes.length === 0) return respuesta({ servicio: "lifemusic-enlace", estado: "ok" });
    if (partes[0] !== "cuarto" || !CUARTO.test(partes[1] || "")) return respuesta({ error: "no-existe" }, 404);

    // Desde un navegador, solo nuestras paginas. La app no manda Origin.
    const origen = request.headers.get("origin");
    if (origen && !ORIGENES.some((r) => r.test(origen))) return respuesta({ error: "origen" }, 403);

    const rol = url.searchParams.get("rol");
    if (rol !== "tv" && rol !== "tel") return respuesta({ error: "rol" }, 400);
    if (request.headers.get("upgrade") !== "websocket") return respuesta({ error: "solo-websocket" }, 426);

    const cuarto = env.CUARTOS.get(env.CUARTOS.idFromName(partes[1]));
    return cuarto.fetch(request);
  },
};

export class Cuarto extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    // El ping de los clientes se contesta solo, sin despertar al cuarto.
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(request) {
    const url = new URL(request.url);
    const id = url.pathname.split("/")[2];
    const rol = url.searchParams.get("rol");
    const nombre = (url.searchParams.get("nombre") || "").slice(0, 60);
    const esCodigo = id.startsWith("c-");
    const tvs = this.ctx.getWebSockets("tv");

    if (esCodigo) {
      // El codigo lo inventa el TV: si ya hay otro TV con el, que pruebe otro.
      if (rol === "tv" && tvs.length > 0) return respuesta({ error: "codigo-ocupado" }, 409);
      if (rol === "tel" && tvs.length === 0) return respuesta({ error: "codigo-no-existe" }, 404);
    } else if (rol === "tv") {
      // El mismo TV que recarga la pagina: el lado viejo sobra.
      for (const viejo of tvs) this.cerrar(viejo, 4000, "reemplazado");
    }

    const [cliente, servidor] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(servidor, [rol]);
    servidor.serializeAttachment({ rol, nombre });

    const otros = this.ctx.getWebSockets(rol === "tv" ? "tel" : "tv");
    // Cada lado sabe al entrar si el otro ya esta.
    servidor.send(JSON.stringify({ tipo: "_par", conectado: otros.length > 0, nombres: otros.map((o) => o.deserializeAttachment()?.nombre || "") }));
    this.aLosDemas(servidor, rol, { tipo: "_par", conectado: true, rol, nombre });

    if (esCodigo && rol === "tel") {
      // Enlace hecho: el token es la llave del cuarto donde seguiran los dos.
      const token = "t-" + crypto.randomUUID().replace(/-/g, "");
      const aviso = JSON.stringify({ tipo: "_enlazado", token, nombre });
      for (const ws of this.ctx.getWebSockets()) {
        try { ws.send(aviso); } catch {}
      }
    }

    return new Response(null, { status: 101, webSocket: cliente });
  }

  async webSocketMessage(ws, mensaje) {
    const largo = typeof mensaje === "string" ? mensaje.length : mensaje.byteLength;
    if (largo > MAX_MENSAJE) {
      ws.send(JSON.stringify({ tipo: "_error", error: "mensaje-grande" }));
      return;
    }
    const { rol } = ws.deserializeAttachment() || {};
    const otros = this.ctx.getWebSockets(rol === "tv" ? "tel" : "tv");
    if (otros.length === 0) {
      ws.send(JSON.stringify({ tipo: "_par", conectado: false }));
      return;
    }
    for (const o of otros) {
      try { o.send(mensaje); } catch {}
    }
  }

  async webSocketClose(ws, codigo) {
    this.salio(ws);
    this.cerrar(ws, codigo === 1005 ? 1000 : codigo, "adios");
  }

  async webSocketError(ws) {
    this.salio(ws);
  }

  salio(ws) {
    const { rol, nombre } = ws.deserializeAttachment() || {};
    if (!rol) return;
    const quedan = this.ctx.getWebSockets(rol).filter((x) => x !== ws).length;
    if (quedan === 0) this.aLosDemas(ws, rol, { tipo: "_par", conectado: false, rol, nombre });
  }

  aLosDemas(ws, rol, datos) {
    const texto = JSON.stringify(datos);
    for (const o of this.ctx.getWebSockets(rol === "tv" ? "tel" : "tv")) {
      if (o !== ws) try { o.send(texto); } catch {}
    }
  }

  cerrar(ws, codigo, motivo) {
    try { ws.close(codigo, motivo); } catch {}
  }
}
