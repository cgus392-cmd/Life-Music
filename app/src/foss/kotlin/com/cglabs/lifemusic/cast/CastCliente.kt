package com.cglabs.lifemusic.cast

import com.cglabs.lifemusic.cast.proto.CastChannel.CastMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Cliente del protocolo de Google Cast (CASTV2) sin el SDK de Google, para la
 * variante foss. Habla directamente con el receptor por TLS en el puerto 8009:
 * tramas de 4 bytes de longitud + CastMessage (protobuf), y dentro JSON por
 * espacio de nombres (conexion, latido, receptor, media). Es lo mismo que hace
 * el SDK por debajo, y lo que implementan receptores como Chromecast, Google TV
 * o AirScreen.
 *
 * Solo el reproductor por defecto (CC1AD845): cargar una URL con sus metadatos,
 * play, pausa, seek, volumen y estado. Sin cola en el receptor: la cola es la
 * del telefono, que carga la siguiente cancion cuando el receptor termina.
 *
 * El certificado del receptor es autofirmado (asi vienen de fabrica), asi que
 * no se verifica la cadena; la conexion es dentro de la red local.
 */
class CastCliente(private val scope: CoroutineScope) {

    /** Lo ultimo que dijo el receptor de la pista cargada. */
    class EstadoMedia(
        val mediaSessionId: Int,
        /** PLAYING, PAUSED, BUFFERING, IDLE. */
        val estado: String,
        val posicionSeg: Double,
        val duracionSeg: Double?,
        /** FINISHED, CANCELLED, ERROR, INTERRUPTED; solo en IDLE. */
        val razonIdle: String?,
        /** Reloj del telefono (elapsedRealtime) cuando llego, para extrapolar la posicion. */
        val medidoEn: Long,
    )

    val estadoMedia = MutableStateFlow<EstadoMedia?>(null)
    val volumenReceptor = MutableStateFlow(1f)
    val conectado = MutableStateFlow(false)
    /** Se llama una vez cuando la conexion se pierde o se cierra (null = cierre nuestro). */
    var alCerrarse: ((Throwable?) -> Unit)? = null

    private var socket: SSLSocket? = null
    private var entrada: DataInputStream? = null
    private var salida: DataOutputStream? = null
    private var lector: Job? = null
    private var latido: Job? = null
    private val peticion = AtomicInteger(1)
    private val pendientes = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    // Todas las escrituras van por este hilo, en orden: enviar() se llama desde
    // el hilo principal (pausa, seek, estado) y Android prohibe red ahi.
    private val escritor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "cast-escritor") }
    private var cerrado = false

    /** transportId de la app del receptor una vez lanzada; destino de los mensajes de media. */
    private var transporte: String? = null
    private var sesionApp: String? = null
    /** appId que se pidio lanzar; con el propio de Life Music el TV dibuja el modo ambiente. */
    var appActiva: String? = null
        private set

    suspend fun conectar(host: String, puerto: Int = 8009) = withContext(Dispatchers.IO) {
        val contexto = SSLContext.getInstance("TLS")
        contexto.init(null, arrayOf(ConfiadoEnLaRedLocal), SecureRandom())
        val s = contexto.socketFactory.createSocket() as SSLSocket
        s.soTimeout = 0
        s.connect(InetSocketAddress(host, puerto), 6_000)
        s.startHandshake()
        socket = s
        entrada = DataInputStream(s.inputStream.buffered())
        salida = DataOutputStream(s.outputStream.buffered())
        cerrado = false
        lector = scope.launch(Dispatchers.IO) { leer() }
        com.cglabs.lifemusic.cast.DiagnosticoCast.log("conectado a $host:$puerto (${s.session.protocol} ${s.session.cipherSuite})")
        enviar(NS_CONEXION, RECEPTOR, conectarJson())
        latido = scope.launch(Dispatchers.IO) {
            while (isActive && !cerrado) {
                runCatching { enviar(NS_LATIDO, RECEPTOR, JSONObject().put("type", "PING")) }
                delay(5_000)
            }
        }
        conectado.value = true
    }

    /**
     * Lanza el reproductor por defecto (o se engancha si ya corre) y abre la
     * conexion virtual con el. Devuelve false si el receptor no respondio.
     */
    suspend fun lanzarReproductor(appId: String = APP_REPRODUCTOR): Boolean {
        appActiva = appId
        transporte = null
        val id = peticion.getAndIncrement()
        val espera = CompletableDeferred<JSONObject>()
        pendientes[id] = espera
        enviar(NS_RECEPTOR, RECEPTOR, JSONObject().put("type", "LAUNCH").put("appId", appId).put("requestId", id))
        // Vale la respuesta numerada o cualquier estado que ya traiga la app corriendo
        // (AirScreen carga la pagina y no siempre contesta con el requestId). Hasta
        // 20 s: un receptor que es un telefono tarda en cargar la pagina.
        val limite = android.os.SystemClock.elapsedRealtime() + 30_000
        while (android.os.SystemClock.elapsedRealtime() < limite) {
            if (transporte != null) { pendientes.remove(id); com.cglabs.lifemusic.cast.DiagnosticoCast.log("LAUNCH $appId: app corriendo (transporte $transporte)"); return true }
            val respuesta = withTimeoutOrNull(250) { espera.await() }
            if (respuesta != null) {
                com.cglabs.lifemusic.cast.DiagnosticoCast.log("LAUNCH $appId → ${respuesta.optString("type")} ${respuesta.toString().take(300)}")
                if (respuesta.optString("type") == "LAUNCH_ERROR") return false
                if (engancharApp(respuesta)) return true
            }
            if (cerrado) return false
        }
        pendientes.remove(id)
        com.cglabs.lifemusic.cast.DiagnosticoCast.log("LAUNCH $appId sin respuesta en 30 s")
        return transporte != null
    }

    /** El CONNECT como lo manda el SDK oficial: algunos receptores lo miran. */
    private fun conectarJson(): JSONObject = JSONObject()
        .put("type", "CONNECT")
        .put("origin", JSONObject())
        .put("userAgent", "Life Music")
        .put("senderInfo", JSONObject().put("sdkType", 2).put("version", "1.2.0").put("browserVersion", "").put("platform", 4).put("systemVersion", "Android").put("connectionType", 1))

    private fun engancharApp(estado: JSONObject): Boolean {
        val apps = estado.optJSONObject("status")?.optJSONArray("applications") ?: return false
        for (i in 0 until apps.length()) {
            val app = apps.getJSONObject(i)
            if (app.optString("appId") == (appActiva ?: APP_REPRODUCTOR)) {
                val t = app.optString("transportId")
                if (t.isNotEmpty() && t != transporte) {
                    transporte = t
                    sesionApp = app.optString("sessionId")
                    enviar(NS_CONEXION, t, conectarJson())
                }
                return true
            }
        }
        return false
    }

    /** Carga una pista en el receptor. [desdeSeg] arranca ahi. Devuelve true si el receptor la acepto. */
    suspend fun cargar(
        mediaId: String,
        url: String,
        tipo: String,
        titulo: String,
        artista: String,
        album: String?,
        imagen: String?,
        desdeSeg: Double,
        reproducir: Boolean,
    ): Boolean {
        val destino = transporte ?: return false
        val metadatos = JSONObject()
            .put("metadataType", 3) // MusicTrackMediaMetadata
            .put("title", titulo)
            .put("artist", artista)
        if (!album.isNullOrBlank()) metadatos.put("albumName", album)
        if (!imagen.isNullOrBlank()) metadatos.put("images", JSONArray().put(JSONObject().put("url", imagen)))
        val media = JSONObject()
            .put("contentId", url)
            .put("contentUrl", url)
            .put("contentType", tipo)
            .put("streamType", "BUFFERED")
            .put("metadata", metadatos)
        val id = peticion.getAndIncrement()
        val espera = CompletableDeferred<JSONObject>()
        pendientes[id] = espera
        enviar(
            NS_MEDIA, destino,
            JSONObject()
                .put("type", "LOAD")
                .put("requestId", id)
                .put("sessionId", sesionApp ?: "")
                .put("media", media)
                .put("autoplay", reproducir)
                .put("currentTime", desdeSeg)
                .put("customData", JSONObject().put("mediaId", mediaId)),
        )
        val respuesta = withTimeoutOrNull(15_000) { espera.await() } ?: run { pendientes.remove(id); com.cglabs.lifemusic.cast.DiagnosticoCast.log("LOAD sin respuesta"); return false }
        com.cglabs.lifemusic.cast.DiagnosticoCast.log("LOAD → ${respuesta.optString("type")} ${respuesta.toString().take(300)}")
        return respuesta.optString("type") == "MEDIA_STATUS"
    }

    /** Mensaje por el canal propio de Life Music (letra, colores, tempo); solo con nuestra app en el TV. */
    fun enviarPropio(carga: JSONObject) {
        val destino = transporte ?: return
        enviar(NS_LIFE, destino, carga)
    }

    fun play() = ordenMedia("PLAY")
    fun pause() = ordenMedia("PAUSE")
    fun detenerMedia() = ordenMedia("STOP")

    fun seek(seg: Double) {
        val destino = transporte ?: return
        val sesion = estadoMedia.value?.mediaSessionId ?: return
        enviar(
            NS_MEDIA, destino,
            JSONObject().put("type", "SEEK").put("requestId", peticion.getAndIncrement())
                .put("mediaSessionId", sesion).put("currentTime", seg).put("resumeState", "PLAYBACK_START"),
        )
    }

    /** Volumen del aparato, 0..1 (el del receptor, no el de la pista). */
    fun setVolumen(nivel: Float) {
        enviar(
            NS_RECEPTOR, RECEPTOR,
            JSONObject().put("type", "SET_VOLUME").put("requestId", peticion.getAndIncrement())
                .put("volume", JSONObject().put("level", nivel.coerceIn(0f, 1f).toDouble())),
        )
    }

    fun pedirEstado() {
        val destino = transporte ?: return
        enviar(NS_MEDIA, destino, JSONObject().put("type", "GET_STATUS").put("requestId", peticion.getAndIncrement()))
    }

    private fun ordenMedia(tipo: String) {
        val destino = transporte ?: return
        val sesion = estadoMedia.value?.mediaSessionId ?: return
        enviar(
            NS_MEDIA, destino,
            JSONObject().put("type", tipo).put("requestId", peticion.getAndIncrement()).put("mediaSessionId", sesion),
        )
    }

    /** Para la app del receptor (el TV vuelve a su reposo) y cierra el socket. */
    fun cerrar(pararApp: Boolean = true) {
        if (cerrado) return
        runCatching {
            if (pararApp && sesionApp != null) {
                enviar(NS_RECEPTOR, RECEPTOR, JSONObject().put("type", "STOP").put("sessionId", sesionApp).put("requestId", peticion.getAndIncrement()))
            }
            transporte?.let { enviar(NS_CONEXION, it, JSONObject().put("type", "CLOSE")) }
            enviar(NS_CONEXION, RECEPTOR, JSONObject().put("type", "CLOSE"))
        }
        terminar(null)
    }

    private fun terminar(motivo: Throwable?) {
        if (cerrado) return
        cerrado = true
        com.cglabs.lifemusic.cast.DiagnosticoCast.log("conexion terminada: " + (motivo?.let { it.javaClass.simpleName + (it.message?.let { m -> ": $m" } ?: "") } ?: "cierre propio"))
        conectado.value = false
        latido?.cancel()
        lector?.cancel()
        // Que salgan los CLOSE/STOP encolados antes de cortar; luego el socket.
        runCatching { escritor.execute { runCatching { socket?.close() } }; escritor.shutdown() }
        pendientes.values.forEach { it.cancel() }
        pendientes.clear()
        transporte = null
        sesionApp = null
        alCerrarse?.invoke(motivo)
    }

    // ── Cable ────────────────────────────────────────────────────────────────

    private fun enviar(espacio: String, destino: String, carga: JSONObject) {
        val mensaje = CastMessage.newBuilder()
            .setProtocolVersion(CastMessage.ProtocolVersion.CASTV2_1_0)
            .setSourceId(EMISOR)
            .setDestinationId(destino)
            .setNamespace(espacio)
            .setPayloadType(CastMessage.PayloadType.STRING)
            .setPayloadUtf8(carga.toString())
            .build()
        val bytes = mensaje.toByteArray()
        if (cerrado) return
        runCatching {
            escritor.execute {
                try {
                    val out = salida ?: return@execute
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                } catch (e: Exception) {
                    if (!cerrado) terminar(e)
                }
            }
        }
    }

    private fun leer() {
        val ent = entrada ?: return
        try {
            while (!cerrado) {
                val largo = ent.readInt()
                if (largo <= 0 || largo > 1 shl 20) throw IllegalStateException("trama de $largo bytes")
                val buf = ByteArray(largo)
                ent.readFully(buf)
                val mensaje = CastMessage.parseFrom(buf)
                if (mensaje.payloadType != CastMessage.PayloadType.STRING) continue
                procesar(mensaje.namespace, mensaje.sourceId, JSONObject(mensaje.payloadUtf8))
            }
        } catch (e: Exception) {
            if (!cerrado) terminar(e)
        }
    }

    private fun procesar(espacio: String, origen: String, carga: JSONObject) {
        val tipo = carga.optString("type")
        val requestId = carga.optInt("requestId", 0)
        when (espacio) {
            NS_LATIDO -> if (tipo == "PING") enviar(NS_LATIDO, origen, JSONObject().put("type", "PONG"))
            NS_CONEXION -> if (tipo == "CLOSE" && origen == transporte) {
                // La app del receptor se cerro (otro emisor, o el usuario desde el TV).
                terminar(IllegalStateException("el receptor cerro la sesion"))
            }
            NS_RECEPTOR -> {
                if (transporte == null) com.cglabs.lifemusic.cast.DiagnosticoCast.log("receptor → $tipo ${carga.toString().take(400)}")
                if (tipo == "RECEIVER_STATUS") {
                    carga.optJSONObject("status")?.optJSONObject("volume")?.let { v ->
                        val nivel = v.optDouble("level", Double.NaN)
                        if (!nivel.isNaN()) volumenReceptor.value = if (v.optBoolean("muted", false)) 0f else nivel.toFloat()
                    }
                    if (transporte == null) engancharApp(carga)
                    else if (!appSigueViva(carga)) terminar(IllegalStateException("el reproductor del receptor se detuvo"))
                }
                pendientes.remove(requestId)?.complete(carga)
            }
            NS_MEDIA -> {
                if (tipo == "MEDIA_STATUS") {
                    val lista = carga.optJSONArray("status")
                    if (lista != null && lista.length() > 0) {
                        val s = lista.getJSONObject(0)
                        estadoMedia.value = EstadoMedia(
                            mediaSessionId = s.optInt("mediaSessionId", 0),
                            estado = s.optString("playerState", "IDLE"),
                            posicionSeg = s.optDouble("currentTime", 0.0),
                            duracionSeg = s.optJSONObject("media")?.optDouble("duration", Double.NaN)?.takeIf { !it.isNaN() },
                            razonIdle = s.optString("idleReason").takeIf { it.isNotEmpty() },
                            medidoEn = android.os.SystemClock.elapsedRealtime(),
                        )
                    }
                }
                pendientes.remove(requestId)?.complete(carga)
            }
        }
    }

    private fun appSigueViva(estado: JSONObject): Boolean {
        // Sin lista de apps no se puede afirmar nada: se da por viva.
        val status = estado.optJSONObject("status") ?: return true
        if (!status.has("applications")) return true
        val apps = status.optJSONArray("applications") ?: return false
        for (i in 0 until apps.length()) if (apps.getJSONObject(i).optString("transportId") == transporte) return true
        return false
    }

    private object ConfiadoEnLaRedLocal : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    companion object {
        private const val TAG = "LifeMusicCast"
        const val PUERTO = 8009
        private const val EMISOR = "sender-0"
        private const val RECEPTOR = "receiver-0"
        /** El reproductor multimedia por defecto de Google, presente en todo receptor. */
        private const val APP_REPRODUCTOR = "CC1AD845"
        private const val NS_CONEXION = "urn:x-cast:com.google.cast.tp.connection"
        private const val NS_LATIDO = "urn:x-cast:com.google.cast.tp.heartbeat"
        private const val NS_RECEPTOR = "urn:x-cast:com.google.cast.receiver"
        private const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
        /** El canal propio; el receptor (web/cast/receptor.js) lo declara con el mismo nombre. */
        const val NS_LIFE = "urn:x-cast:com.cglabs.lifemusic"
    }
}
