package com.cglabs.lifemusic.cast

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Enlace con un TV que abrio lifemusic.pages.dev/tv en su navegador (1.3.1).
 *
 * El TV y el telefono no pueden hablarse por la red de la casa (una pagina https
 * no abre ws:// hacia una IP local), asi que los dos se conectan al relevo
 * (`enlace/`, un Worker de Cloudflare) y este les pasa los mensajes. Primero se
 * entra al cuarto del codigo que muestra el TV ([enlazar]); el relevo les da a
 * los dos un token y la sesion sigue en el cuarto del token ([conectar]), al
 * que se vuelve la proxima vez sin codigo.
 *
 * Por aqui no pasa audio: solo textos pequenos (la cancion, la letra, el
 * estado, los comandos). El TV pide el audio directo a YouTube.
 * Ver docs/versiones/1.3.1/tv-por-codigo.md.
 */
class EnlaceWeb(
    private val scope: CoroutineScope,
    private val nombreTelefono: String,
) {
    /** Un TV recordado: el token del cuarto y el nombre que mostro. */
    data class Tv(val token: String, val nombre: String)

    /** Cada mensaje del TV, ya leido (los del relevo, «_par», van por [alTvPresente]). */
    var alMensaje: ((JSONObject) -> Unit)? = null
    /** El TV entro o salio del cuarto (recargo la pagina, se apago...). */
    var alTvPresente: ((Boolean) -> Unit)? = null
    /** No hubo forma de volver al relevo tras varios intentos. */
    var alPerder: (() -> Unit)? = null

    @Volatile private var ws: WebSocket? = null
    @Volatile private var cerrado = false
    private var latido: Job? = null
    private var reintentos: Job? = null

    /** Entra al cuarto del token y se queda; si se cae, vuelve a entrar solo. */
    fun conectar(token: String) {
        cerrado = false
        abrir(token, intento = 0)
    }

    private fun abrir(token: String, intento: Int) {
        val pedido = Request.Builder().url(direccion(token, nombreTelefono)).build()
        ws = cliente.newWebSocket(pedido, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== ws) return
                DiagnosticoCast.log("TV web: conectado al relevo")
                latido?.cancel()
                latido = scope.launch(Dispatchers.IO) {
                    // El relevo contesta «pong» sin despertarse; mantiene vivo el socket.
                    while (isActive && webSocket === ws) { delay(25_000); webSocket.send("ping") }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== ws || text == "pong") return
                val m = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (m.optString("tipo") == "_par") {
                    // Al entrar llega «nombres» (quien ya estaba); despues, quien entra o sale.
                    val delTv = m.optString("rol") == "tv" || m.has("nombres")
                    if (delTv) alTvPresente?.invoke(m.optBoolean("conectado"))
                    return
                }
                alMensaje?.invoke(m)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = caido(webSocket, token, intento, null)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = caido(webSocket, token, intento, t)
        })
    }

    private fun caido(webSocket: WebSocket, token: String, intento: Int, motivo: Throwable?) {
        if (webSocket !== ws || cerrado) return
        latido?.cancel()
        DiagnosticoCast.log("TV web: se cayo el relevo (intento $intento)", motivo)
        if (intento >= ESPERAS.size) { alPerder?.invoke(); return }
        reintentos?.cancel()
        reintentos = scope.launch(Dispatchers.IO) {
            delay(ESPERAS[intento])
            if (!cerrado) abrir(token, intento + 1)
        }
    }

    /** Manda un mensaje al TV. Falso si no hay conexion con el relevo. */
    fun enviar(m: JSONObject): Boolean = ws?.send(m.toString()) ?: false

    fun cerrar() {
        cerrado = true
        latido?.cancel()
        reintentos?.cancel()
        ws?.close(1000, "adios")
        ws = null
    }

    companion object {
        const val RELEVO = "wss://lifemusic-enlace.cho--usic.workers.dev"
        /** El alfabeto del codigo del TV: sin 0/O ni 1/I/L, para que no se confundan. */
        private val CODIGO = Regex("^[2-9A-HJKMNP-Z]{6}$")
        /** Esperas entre reintentos: ~1 min en total antes de rendirse. */
        private val ESPERAS = longArrayOf(1_000, 2_000, 5_000, 10_000, 15_000, 20_000)

        private val cliente: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // un WebSocket puede pasar minutos callado
                .build()
        }

        private fun direccion(cuarto: String, nombre: String) =
            "$RELEVO/cuarto/$cuarto?rol=tel&nombre=" + URLEncoder.encode(nombre.take(60), "UTF-8")

        /** El codigo tal como lo escribio la persona (minusculas, espacios, guion) o null si no es valido. */
        fun normalizarCodigo(texto: String): String? =
            texto.uppercase().filter { it.isLetterOrDigit() }.takeIf { CODIGO.matches(it) }

        /**
         * Entra al cuarto del [codigo] que muestra el TV y espera el token. Devuelve
         * el TV para recordarlo, o el [Fallo]. Cierra ese socket al terminar: la
         * sesion sigue con [conectar] en el cuarto del token.
         */
        suspend fun enlazar(codigo: String, nombreTelefono: String): Result<Tv> {
            val resultado = CompletableDeferred<Result<Tv>>()
            var nombreTv = ""
            val pedido = Request.Builder().url(direccion("c-$codigo", nombreTelefono)).build()
            val socket = cliente.newWebSocket(pedido, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val m = runCatching { JSONObject(text) }.getOrNull() ?: return
                    when (m.optString("tipo")) {
                        "_par" -> m.optJSONArray("nombres")?.optString(0)?.takeIf { it.isNotBlank() }?.let { nombreTv = it }
                        "_enlazado" -> {
                            val token = m.optString("token")
                            if (token.startsWith("t-")) resultado.complete(Result.success(Tv(token, nombreTv.ifBlank { "TV" })))
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val fallo = if (response?.code == 404) Fallo.CODIGO_NO_EXISTE else Fallo.SIN_CONEXION
                    resultado.complete(Result.failure(EnlaceFallido(fallo)))
                }
            })
            val r = withTimeoutOrNull(12_000) { resultado.await() } ?: Result.failure(EnlaceFallido(Fallo.SIN_CONEXION))
            socket.close(1000, "enlazado")
            return r
        }
    }

    /** Por que no se pudo enlazar. */
    enum class Fallo { CODIGO_NO_EXISTE, SIN_CONEXION }

    class EnlaceFallido(val fallo: Fallo) : Exception(fallo.name)
}
