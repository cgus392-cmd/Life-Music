package com.cglabs.lifemusic.playback

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.cast.CastCliente
import com.cglabs.lifemusic.cast.DescubridorCast
import com.cglabs.lifemusic.cast.EnlaceWeb
import com.cglabs.lifemusic.clip.RenderizadorDeClip
import com.cglabs.lifemusic.lyrics.LyricsUtils
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import org.json.JSONArray
import org.json.JSONObject
import com.cglabs.lifemusic.extensions.currentMetadata
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.ui.utils.resize
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transmision a un receptor de Google Cast en la variante foss, sin el SDK de
 * Google: [CastCliente] habla el protocolo y [DescubridorCast] encuentra los
 * aparatos. Misma interfaz que la variante gms, asi que MusicService,
 * PlayerConnection y el reproductor no cambian: mientras se transmite, el
 * servicio mantiene el reproductor local en pausa, manda play/pausa/seek por
 * aqui y llama a [loadMedia] en cada cambio de cancion.
 *
 * La cola vive en el telefono. Al receptor se le carga una cancion a la vez;
 * cuando termina (IDLE/FINISHED) se avanza la cola local, y el cambio de
 * cancion carga la siguiente. Por eso las operaciones de cola remota son no-op.
 */
class CastConnectionHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val musicService: MusicService,
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private val _deviceType = MutableStateFlow(CastDeviceKind.UNKNOWN)
    val deviceType: StateFlow<CastDeviceKind> = _deviceType.asStateFlow()

    private val _castPosition = MutableStateFlow(0L)
    val castPosition: StateFlow<Long> = _castPosition.asStateFlow()

    private val _castDuration = MutableStateFlow(0L)
    val castDuration: StateFlow<Long> = _castDuration.asStateFlow()

    private val _castIsPlaying = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = _castIsPlaying.asStateFlow()

    private val _castIsBuffering = MutableStateFlow(false)
    val castIsBuffering: StateFlow<Boolean> = _castIsBuffering.asStateFlow()

    private val _castVolume = MutableStateFlow(1.0f)
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()

    private val _autoReconnecting = MutableStateFlow(false)
    val autoReconnecting: StateFlow<Boolean> = _autoReconnecting.asStateFlow()

    /** Mientras el servicio no debe reaccionar a un cambio que provocamos nosotros. */
    @Volatile
    var isSyncingFromCast: Boolean = false
        private set

    val descubridor = DescubridorCast(context)
    val aparatos: StateFlow<List<DescubridorCast.Aparato>> get() = descubridor.aparatos

    private var cliente: CastCliente? = null
    /** Con quien se transmite, para volver a engancharse si el socket se cae. */
    private var aparatoActual: DescubridorCast.Aparato? = null
    /** Aparatos que no supieron lanzar la app propia en esta sesion (AirScreen): directo al reproductor por defecto. */
    private val sinAppPropia = mutableMapOf<String, Long>()
    private fun sinAppPropiaReciente(id: String) = (sinAppPropia[id] ?: 0L) > android.os.SystemClock.elapsedRealtime() - 5 * 60_000L
    private var seguimiento: Job? = null
    private var cargando: Job? = null
    /** Cancion cargada en el receptor, para no recargarla si el servicio repite el aviso. */
    private var idCargado: String? = null
    private var quieroSonar = true

    fun initialize(): Boolean = true
    fun isCastAvailable(): Boolean = true

    // ── Buscar y conectar ────────────────────────────────────────────────────

    /** Reproductores DLNA/UPnP de la red (TV Samsung, LG, Sony, AirScreen, Kodi...). */
    val dlna = com.cglabs.lifemusic.cast.DescubridorDlna(context, scope)
    val renderizadores: StateFlow<List<com.cglabs.lifemusic.cast.DescubridorDlna.Renderizador>> get() = dlna.renderizadores

    fun buscar() { descubridor.iniciar(); dlna.iniciar() }
    fun dejarDeBuscar() { descubridor.detener(); dlna.detener() }

    // ── DLNA ─────────────────────────────────────────────────────────────────
    // Un reproductor DLNA no carga paginas ni habla el protocolo de Cast: se le
    // da una URL de audio y se le manda play/pausa/saltar por SOAP. La URL es la
    // del servidor local del telefono (ServidorLocal), que le pasa la cancion
    // por la misma cadena que el reproductor, o en AAC si el aparato no acepta
    // Opus/WebM. El estado se pregunta cada segundo; cuando una pista termina,
    // se avanza la cola del telefono y el cambio de cancion carga la siguiente.

    private var sesionDlna: com.cglabs.lifemusic.cast.SesionDlna? = null
    private var servidor: com.cglabs.lifemusic.cast.ServidorLocal? = null
    /** Momento de la ultima carga: un STOPPED justo despues es del cambio, no un final. */
    @Volatile private var cargaDlnaEn = 0L

    fun conectarDlna(r: com.cglabs.lifemusic.cast.DescubridorDlna.Renderizador) {
        if (_isConnecting.value) return
        _isConnecting.value = true
        scope.launch(Dispatchers.IO) {
            try {
                val s = com.cglabs.lifemusic.cast.SesionDlna(r)
                s.estado() // prueba de vida: si no contesta, ni se empieza
                val formatos = s.formatosDeAudio()
                val aceptaWebm = formatos.isEmpty() || formatos.any { it.contains("webm") || it.contains("opus") }
                com.cglabs.lifemusic.cast.DiagnosticoCast.log("DLNA ${r.nombre}: ${if (aceptaWebm) "acepta WebM" else "sin WebM, se manda AAC"} (${formatos.size} formatos de audio)")
                // Si ya se transmitia a otro aparato, se suelta primero.
                if (cliente != null || sesionDlna != null) withContext(Dispatchers.Main) { disconnect(reanudar = false) }
                val srv = servidor ?: com.cglabs.lifemusic.cast.ServidorLocal(context, musicService).also { servidor = it }
                srv.soloAac = !aceptaWebm
                srv.iniciar()
                sesionDlna = s
                _receptorPropio.value = false
                _castDeviceName.value = r.nombre
                _deviceType.value = CastDeviceKind.TV
                s.volumen()?.let { _castVolume.value = it }
                withContext(Dispatchers.Main) {
                    quieroSonar = musicService.player.isPlaying || musicService.player.playWhenReady
                    _castPosition.value = musicService.player.currentPosition.coerceAtLeast(0L)
                    _isCasting.value = true
                    conSincronia { musicService.player.pause() }
                    seguirDlna(s)
                    loadCurrentMedia()
                }
                com.cglabs.lifemusic.cast.ServicioDeCast.alDevolver = { disconnect() }
                com.cglabs.lifemusic.cast.ServicioDeCast.iniciar(context, r.nombre)
                aviso(context.getString(R.string.cast_conectado_a, r.nombre))
            } catch (e: Exception) {
                com.cglabs.lifemusic.cast.DiagnosticoCast.log("DLNA ${r.nombre}: no se pudo conectar", e)
                // Quiza cambio de puerto (AirScreen al reiniciarse): la proxima busqueda lo trae de nuevo.
                dlna.olvidar(r.id)
                aviso(context.getString(R.string.cast_error_conectar, r.nombre))
            } finally {
                _isConnecting.value = false
            }
        }
    }

    private suspend fun cargarDlna(s: com.cglabs.lifemusic.cast.SesionDlna, m: MediaMetadata, desdeMs: Long) {
        val srv = servidor ?: return
        _castIsBuffering.value = true
        _castPosition.value = desdeMs
        cargaDlnaEn = SystemClock.elapsedRealtime()
        try {
            val url = srv.urlCancion(m.id, s.r.host)
            val tipo = srv.tipoDe(m.id) // abre la fuente: la primera vez tarda lo que tarde YouTube
            val caratula = m.thumbnailUrl?.resize(544, 544)?.let { srv.urlCaratula(m.id, it, s.r.host) }
            // Parar antes de cambiar de URL: algunos TV rechazan cargar mientras suenan.
            runCatching { s.stop() }
            s.cargar(url, didl(m, url, tipo, caratula))
            cargaDlnaEn = SystemClock.elapsedRealtime()
            if (quieroSonar) s.play()
            com.cglabs.lifemusic.cast.DiagnosticoCast.log("DLNA: cargada ${m.id} ($tipo)")
            if (desdeMs > 3_000) {
                // Saltar solo cuando ya suena: antes, muchos aparatos lo rechazan.
                for (i in 0 until 16) {
                    delay(500)
                    val e = runCatching { s.estado() }.getOrNull() ?: continue
                    if (e.estado == "PLAYING" || e.estado == "PAUSED_PLAYBACK") { runCatching { s.seek(desdeMs) }; break }
                }
            }
        } catch (e: Exception) {
            com.cglabs.lifemusic.cast.DiagnosticoCast.log("DLNA: no se pudo cargar ${m.id}", e)
            if (idCargado == m.id) idCargado = null
            aviso(context.getString(R.string.cast_error_cancion))
        } finally {
            _castIsBuffering.value = false
        }
    }

    /** La ficha de la cancion (DIDL-Lite) que el TV muestra: titulo, artista, album y caratula. */
    private fun didl(m: MediaMetadata, url: String, tipo: String, caratula: String?): String {
        val e = com.cglabs.lifemusic.cast.Upnp::escapar
        val artista = m.artists.joinToString { it.name }
        val dur = m.duration.toLong().coerceAtLeast(0)
        return buildString {
            append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
            append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" xmlns:dlna=\"urn:schemas-dlna-org:metadata-1-0/\">")
            append("<item id=\"").append(e(m.id)).append("\" parentID=\"0\" restricted=\"1\">")
            append("<dc:title>").append(e(m.title)).append("</dc:title>")
            append("<dc:creator>").append(e(artista)).append("</dc:creator>")
            append("<upnp:artist>").append(e(artista)).append("</upnp:artist>")
            m.album?.title?.let { append("<upnp:album>").append(e(it)).append("</upnp:album>") }
            caratula?.let { append("<upnp:albumArtURI dlna:profileID=\"JPEG_TN\">").append(e(it)).append("</upnp:albumArtURI>") }
            append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
            append("<res protocolInfo=\"http-get:*:").append(tipo).append(':').append(com.cglabs.lifemusic.cast.ServidorLocal.FEATURES).append("\"")
            if (dur > 0) append(" duration=\"").append(com.cglabs.lifemusic.cast.SesionDlna.reloj(dur * 1000)).append(".000\"")
            append('>').append(e(url)).append("</res>")
            append("</item></DIDL-Lite>")
        }
    }

    /** Pregunta el estado cada segundo; al terminar una pista, la cola del telefono avanza. */
    private fun seguirDlna(s: com.cglabs.lifemusic.cast.SesionDlna) {
        seguimiento?.cancel()
        seguimiento = scope.launch(Dispatchers.IO) {
            var anterior = ""
            var fallos = 0
            var ultimaPos = 0L
            var ultimaDur = 0L
            var tic = 0
            while (isActive && sesionDlna === s) {
                val e = runCatching { s.estado() }.getOrNull()
                if (e == null) {
                    if (++fallos >= 8) {
                        com.cglabs.lifemusic.cast.DiagnosticoCast.log("DLNA ${s.r.nombre}: dejo de contestar")
                        aviso(context.getString(R.string.cast_conexion_perdida))
                        withContext(Dispatchers.Main) { if (sesionDlna === s) disconnect(porUsuario = false) }
                        break
                    }
                    delay(1_000)
                    continue
                }
                fallos = 0
                val recienCargada = SystemClock.elapsedRealtime() - cargaDlnaEn < 5_000
                _castIsPlaying.value = e.estado == "PLAYING" || e.estado == "TRANSITIONING"
                if (!_castIsBuffering.value && !recienCargada) {
                    e.posicionMs?.let { _castPosition.value = it; ultimaPos = it }
                }
                e.duracionMs?.takeIf { it > 0 }?.let { _castDuration.value = it; ultimaDur = it }
                // Fin de pista: sonaba, se paro solo y estaba cerca del final.
                val termino = !recienCargada && (anterior == "PLAYING" || anterior == "TRANSITIONING") &&
                    (e.estado == "STOPPED" || e.estado == "NO_MEDIA_PRESENT") &&
                    ultimaDur > 0 && ultimaDur - ultimaPos < 5_000
                if (termino) {
                    withContext(Dispatchers.Main) {
                        val p = musicService.player
                        if (p.hasNextMediaItem()) p.seekToNext() else { _castIsPlaying.value = false; quieroSonar = false }
                    }
                }
                anterior = e.estado
                if (++tic % 5 == 0) s.volumen()?.let { _castVolume.value = it }
                delay(1_000)
            }
        }
    }

    private fun soltarDlna() {
        val s = sesionDlna ?: return
        sesionDlna = null
        scope.launch(Dispatchers.IO) { runCatching { s.stop() } }
        servidor?.detener()
    }

    // ── TV con navegador (lifemusic.pages.dev/tv) ────────────────────────────
    // Un TV sin Cast ni DLNA abre la pagina del receptor en su navegador y se
    // enlaza con un codigo ([EnlaceWeb], por el relevo). Se maneja como Cast: la
    // cola vive en el telefono, al TV se le carga una cancion a la vez con la URL
    // de YouTube (misma casa, misma IP publica) y avisa «fin» para avanzar. En
    // «listo» dice que formatos reproduce: sin Opus, se le manda AAC.

    private var sesionWeb: EnlaceWeb? = null
    private var tvWebActual: EnlaceWeb.Tv? = null
    @Volatile private var tvWebOpus = true
    /** Canciones que el TV no pudo tocar y ya se reintentaron en AAC con URL nueva. */
    private val reintentadasWeb = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _tvsWeb = MutableStateFlow(leerTvsWeb())
    /** TV con navegador ya enlazados: se tocan en la hoja y entran sin codigo. */
    val tvsWeb: StateFlow<List<EnlaceWeb.Tv>> = _tvsWeb.asStateFlow()

    private fun nombreDelTelefono(): String =
        runCatching { android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL

    private fun leerTvsWeb(): List<EnlaceWeb.Tv> = runCatching {
        val a = JSONArray(context.dataStore.get(com.cglabs.lifemusic.constants.CastTvsWebKey, "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            o.optString("token").takeIf { it.startsWith("t-") }?.let { EnlaceWeb.Tv(it, o.optString("nombre", "TV")) }
        }
    }.getOrDefault(emptyList())

    private fun guardarTvsWeb(lista: List<EnlaceWeb.Tv>) {
        _tvsWeb.value = lista
        val a = JSONArray(lista.map { JSONObject().put("token", it.token).put("nombre", it.nombre) })
        scope.launch(Dispatchers.IO) {
            runCatching { context.dataStore.edit { it[com.cglabs.lifemusic.constants.CastTvsWebKey] = a.toString() } }
        }
    }

    /**
     * Enlaza con el TV que muestra [codigo] y transmite a el. [alTerminar] recibe
     * null si salio bien, o el texto del error para el dialogo.
     */
    fun enlazarTvWeb(codigo: String, alTerminar: (String?) -> Unit) {
        val limpio = EnlaceWeb.normalizarCodigo(codigo)
        if (limpio == null) { alTerminar(context.getString(R.string.cast_web_codigo_invalido)); return }
        scope.launch(Dispatchers.IO) {
            EnlaceWeb.enlazar(limpio, nombreDelTelefono())
                .onSuccess { tv ->
                    com.cglabs.lifemusic.cast.DiagnosticoCast.log("TV web: enlazado con ${tv.nombre}")
                    guardarTvsWeb(listOf(tv) + _tvsWeb.value.filter { it.token != tv.token }.take(4))
                    withContext(Dispatchers.Main) { alTerminar(null); conectarTvWeb(tv) }
                }
                .onFailure { e ->
                    val fallo = (e as? EnlaceWeb.EnlaceFallido)?.fallo
                    com.cglabs.lifemusic.cast.DiagnosticoCast.log("TV web: no se pudo enlazar ($fallo)")
                    val texto = context.getString(
                        if (fallo == EnlaceWeb.Fallo.CODIGO_NO_EXISTE) R.string.cast_web_codigo_no_existe else R.string.cast_web_sin_conexion,
                    )
                    withContext(Dispatchers.Main) { alTerminar(texto) }
                }
        }
    }

    /** Olvida un TV recordado; si se transmite a el, el TV vuelve a mostrar su codigo. */
    fun olvidarTvWeb(tv: EnlaceWeb.Tv) {
        if (tvWebActual?.token == tv.token) {
            sesionWeb?.enviar(JSONObject().put("tipo", "olvidar"))
            disconnect()
        }
        guardarTvsWeb(_tvsWeb.value.filter { it.token != tv.token })
    }

    /** Transmite al TV recordado [tv]: entra a su cuarto y le pasa la cancion actual. */
    fun conectarTvWeb(tv: EnlaceWeb.Tv) {
        if (_isConnecting.value) return
        if (cliente != null || sesionDlna != null || sesionWeb != null) disconnect(reanudar = false)
        val s = EnlaceWeb(scope, nombreDelTelefono())
        s.alMensaje = { m -> alMensajeWeb(s, m) }
        s.alTvPresente = { presente ->
            com.cglabs.lifemusic.cast.DiagnosticoCast.log(if (presente) "TV web: el TV esta en linea" else "TV web: el TV salio")
        }
        s.alPerder = {
            scope.launch(Dispatchers.Main) {
                if (sesionWeb === s) { aviso(context.getString(R.string.cast_conexion_perdida)); disconnect(porUsuario = false) }
            }
        }
        sesionWeb = s
        tvWebActual = tv
        tvWebOpus = true
        reintentadasWeb.clear()
        _receptorPropio.value = true
        _castDeviceName.value = tv.nombre
        _deviceType.value = CastDeviceKind.TV
        s.conectar(tv.token)
        // El telefono deja de sonar y pasa a ser el mando, como con Cast.
        quieroSonar = musicService.player.isPlaying || musicService.player.playWhenReady
        _castPosition.value = musicService.player.currentPosition.coerceAtLeast(0L)
        _isCasting.value = true
        conSincronia { musicService.player.pause() }
        loadCurrentMedia()
        com.cglabs.lifemusic.cast.ServicioDeCast.alDevolver = { disconnect() }
        com.cglabs.lifemusic.cast.ServicioDeCast.iniciar(context, tv.nombre)
        aviso(context.getString(R.string.cast_conectado_a, tv.nombre))
    }

    private fun alMensajeWeb(s: EnlaceWeb, m: JSONObject) {
        if (sesionWeb !== s) return
        when (m.optString("tipo")) {
            "listo" -> {
                m.optJSONObject("capacidades")?.let { tvWebOpus = it.optBoolean("opus", true) }
                com.cglabs.lifemusic.cast.DiagnosticoCast.log("TV web listo: ${m.optString("version")} perfil=${m.optString("perfil")} opus=$tvWebOpus")
                ajustesJson()?.let { s.enviar(it) }
                saludoJson()?.let { s.enviar(it) }
                // El TV recien abierto (o que recargo la pagina) no tiene la cancion: se le repite.
                val enElTv = m.optString("id")
                if (idCargado != null && enElTv != idCargado) scope.launch(Dispatchers.Main) { idCargado = null; loadCurrentMedia() }
            }
            "estado" -> {
                if (m.optString("id") != idCargado) return
                val cargandoTv = m.optBoolean("cargando")
                _castIsBuffering.value = cargandoTv
                _castIsPlaying.value = m.optBoolean("sonando") || (cargandoTv && quieroSonar)
                if (!cargandoTv) _castPosition.value = m.optLong("posMs", _castPosition.value)
                m.optLong("durMs").takeIf { it > 0 }?.let { _castDuration.value = it }
                if (m.has("volumen")) _castVolume.value = m.optDouble("volumen", 1.0).toFloat()
            }
            "fin" -> if (m.optString("id") == idCargado) scope.launch(Dispatchers.Main) {
                val p = musicService.player
                if (p.hasNextMediaItem()) p.seekToNext() else { _castIsPlaying.value = false; quieroSonar = false }
            }
            "error" -> {
                val id = m.optString("id")
                if (id != idCargado) return
                com.cglabs.lifemusic.cast.DiagnosticoCast.log("TV web: no pudo tocar $id (codigo ${m.optInt("codigo")})")
                if (reintentadasWeb.add(id)) {
                    // Segundo intento con una URL nueva en AAC, lo mas compatible.
                    scope.launch(Dispatchers.Main) {
                        val meta = musicService.player.currentMetadata
                        if (meta?.id == id && sesionWeb === s) {
                            cargando?.cancel()
                            cargando = scope.launch(Dispatchers.IO) { cargarWeb(s, meta, _castPosition.value, forzarAac = true) }
                        }
                    }
                } else {
                    _castIsBuffering.value = false
                    aviso(context.getString(R.string.cast_web_no_suena))
                }
            }
            "diag" -> com.cglabs.lifemusic.cast.DiagnosticoCast.log("TV web: " + m.optString("texto"))
        }
    }

    private suspend fun cargarWeb(s: EnlaceWeb, m: MediaMetadata, desdeMs: Long, forzarAac: Boolean = false) {
        _castIsBuffering.value = true
        _castPosition.value = desdeMs
        var url: String? = null
        var tipo = "audio/mp4"
        if (tvWebOpus && !forzarAac) {
            url = musicService.getStreamUrl(m.id)
            if (url != null && (url.contains("mime=audio%2Fwebm") || url.contains("mime=audio/webm"))) tipo = "audio/webm"
        }
        if (url == null) url = musicService.urlAacParaDlna(m.id)?.first
        if (url == null) {
            _castIsBuffering.value = false
            if (idCargado == m.id) idCargado = null
            aviso(context.getString(R.string.cast_error_cancion))
            return
        }
        s.enviar(
            JSONObject()
                .put("tipo", "cargar")
                .put("id", m.id)
                .put("url", url)
                .put("tipoAudio", tipo)
                .put("desdeMs", desdeMs)
                .put("reproducir", quieroSonar)
                .put("titulo", m.title)
                .put("artista", m.artists.joinToString { it.name })
                .put("caratula", m.thumbnailUrl?.resize(1080, 1080))
                .put("duracionMs", m.duration * 1000L),
        )
        com.cglabs.lifemusic.cast.DiagnosticoCast.log("TV web: cargada ${m.id} ($tipo${if (forzarAac) ", reintento" else ""})")
        enviarAmbiente({ s.enviar(it) }, { sesionWeb === s && idCargado == m.id }, m)
    }

    private fun soltarWeb() {
        val s = sesionWeb ?: return
        sesionWeb = null
        tvWebActual = null
        // El TV se queda en su bienvenida, listo para la proxima vez.
        s.enviar(JSONObject().put("tipo", "pausa"))
        s.enviar(JSONObject().put("tipo", "vaciar"))
        s.cerrar()
    }

    /** Conecta con [aparato], lanza el reproductor del receptor y le pasa la cancion actual. */
    fun conectar(aparato: DescubridorCast.Aparato) {
        if (_isConnecting.value) return
        _isConnecting.value = true
        scope.launch(Dispatchers.IO) {
            var c = CastCliente(scope)
            try {
                // Un reintento: un receptor que aun tiene la sesion anterior a medio
                // morir (la app se cerro sin despedirse) corta el primer socket a los
                // dos segundos y acepta el siguiente.
                var lanzado = false
                for (intento in 1..2) {
                    try {
                        c.conectar(aparato.host, aparato.puerto)
                        // Primero nuestra app (el modo ambiente en el TV). Si el receptor no la
                        // conoce o no contesta (AirScreen abre la pagina pero no habla el
                        // protocolo con ella), el reproductor por defecto, y se recuerda para
                        // no volver a esperar con ese aparato.
                        val probarPropia = APP_LIFE_MUSIC != null && !sinAppPropiaReciente(aparato.id)
                        lanzado = probarPropia && c.lanzarReproductor(APP_LIFE_MUSIC!!)
                        if (!lanzado) {
                            if (probarPropia) { sinAppPropia[aparato.id] = android.os.SystemClock.elapsedRealtime(); com.cglabs.lifemusic.cast.DiagnosticoCast.log("${aparato.nombre} no lanza la app propia; reproductor por defecto") }
                            if (!c.conectado.value) { runCatching { c.cerrar(pararApp = false) }; c = CastCliente(scope); c.conectar(aparato.host, aparato.puerto) }
                            lanzado = c.lanzarReproductor()
                        }
                        if (lanzado) break
                    } catch (e: Exception) {
                        com.cglabs.lifemusic.cast.DiagnosticoCast.log("intento $intento fallo: ${e.javaClass.simpleName}")
                    }
                    runCatching { c.cerrar(pararApp = false) }
                    if (intento == 1) { delay(2_000); c = CastCliente(scope) }
                }
                if (!lanzado) throw IllegalStateException("el receptor no lanzo el reproductor")
                // Si se transmitia por DLNA, se suelta: el Chromecast toma el relevo.
                soltarDlna()
                c.alCerrarse = { motivo -> scope.launch { perdida(motivo) } }
                // El receptor avisa «listo» cuando su pagina arranco del todo; lo que se
                // le mande antes se pierde. Ahi va el saludo (y si la pagina se recarga
                // a mitad de sesion, vuelve a decir listo y se le repite la cancion).
                c.alMensajePropio = { m ->
                    // Por «cliente» y no por «c»: tras un reenganche el socket es otro.
                    if (m.optString("tipo") == "listo") {
                        cliente?.let { enviarAjustes(it); enviarSaludo(it) }
                        val actual = idCargado
                        if (actual != null) scope.launch(Dispatchers.IO) {
                            val meta = withContext(Dispatchers.Main) { musicService.player.currentMetadata }
                            val vivo = cliente
                            if (meta != null && meta.id == actual && vivo != null) enviarAmbiente({ vivo.enviarPropio(it) }, { cliente === vivo && idCargado == meta.id }, meta)
                        }
                    }
                }
                cliente = c
                aparatoActual = aparato
                _receptorPropio.value = c.appActiva == APP_LIFE_MUSIC
                _castDeviceName.value = aparato.nombre
                _deviceType.value = CastDeviceKind.fromName(aparato.nombre, aparato.modelo)
                withContext(Dispatchers.Main) {
                    // El telefono deja de sonar y pasa a ser el mando.
                    quieroSonar = musicService.player.isPlaying || musicService.player.playWhenReady
                    _castPosition.value = musicService.player.currentPosition.coerceAtLeast(0L)
                    _isCasting.value = true
                    conSincronia { musicService.player.pause() }
                    seguir(c)
                    loadCurrentMedia()
                }
                // Sin un servicio en primer plano, Android corta la red de la app a los
                // 5 s de apagar la pantalla (ver ServicioDeCast).
                com.cglabs.lifemusic.cast.ServicioDeCast.alDevolver = { disconnect() }
                com.cglabs.lifemusic.cast.ServicioDeCast.iniciar(context, aparato.nombre)
                aviso(context.getString(R.string.cast_conectado_a, aparato.nombre))
            } catch (e: Exception) {
                com.cglabs.lifemusic.cast.DiagnosticoCast.log("conectar fallo", e)
                runCatching { c.cerrar(pararApp = false) }
                if (cliente === c) cliente = null
                aviso(context.getString(R.string.cast_error_conectar, aparato.nombre))
            } finally {
                _isConnecting.value = false
            }
        }
    }

    /**
     * La misma voz que recibe al abrir la app (SaludoDeEntrada), ahora en el TV:
     * cabecera por hora y una frase que no repite las ultimas vistas en el
     * telefono. Solo con nuestro receptor, y solo si el saludo esta encendido.
     */
    // ── Tema del TV ──────────────────────────────────────────────────────────

    private val _tema = MutableStateFlow(context.dataStore.get(com.cglabs.lifemusic.constants.CastTemaKey, TEMA_AMBIENTE))
    /** Tema del receptor: [TEMA_AMBIENTE], [TEMA_CRISTAL], [TEMA_ESCENARIO]… (ver TEMA_*). */
    val tema: StateFlow<String> = _tema.asStateFlow()

    private val _versionTocadiscos = MutableStateFlow(context.dataStore.get(com.cglabs.lifemusic.constants.CastTocadiscosVersionKey, 1))
    /** Foto del tema [TEMA_TOCADISCOS]: 1 (de cerca) o 2 (plano abierto). */
    val versionTocadiscos: StateFlow<Int> = _versionTocadiscos.asStateFlow()

    private val _receptorPropio = MutableStateFlow(false)
    /** Se transmite a nuestro receptor (y no al reproductor por defecto): solo entonces hay temas. */
    val receptorPropio: StateFlow<Boolean> = _receptorPropio.asStateFlow()

    /** Elige el tema del TV: se guarda y, si se transmite, el TV cambia al momento. */
    fun ponerTema(nuevo: String) {
        _tema.value = nuevo
        scope.launch(Dispatchers.IO) {
            runCatching { context.dataStore.edit { it[com.cglabs.lifemusic.constants.CastTemaKey] = nuevo } }
        }
        cliente?.let { enviarAjustes(it) }
        sesionWeb?.let { s -> ajustesJson()?.let { s.enviar(it) } }
    }

    /** Elige la foto del Tocadiscos (V1/V2): se guarda y, si se transmite, el TV cambia al momento. */
    fun ponerVersionTocadiscos(version: Int) {
        _versionTocadiscos.value = version
        scope.launch(Dispatchers.IO) {
            runCatching { context.dataStore.edit { it[com.cglabs.lifemusic.constants.CastTocadiscosVersionKey] = version } }
        }
        cliente?.let { enviarAjustes(it) }
        sesionWeb?.let { s -> ajustesJson()?.let { s.enviar(it) } }
    }

    /**
     * El tema y, para el de cristal, la configuracion de Liquid Glass del usuario
     * en la app (tinte, opacidad, viveza, lente, aberracion, profundidad y
     * desenfoque): el TV hereda el cristal que el usuario ya eligio.
     */
    private fun enviarAjustes(c: CastCliente) {
        if (c.appActiva != APP_LIFE_MUSIC) return
        ajustesJson()?.let {
            c.enviarPropio(it)
            com.cglabs.lifemusic.cast.DiagnosticoCast.log("tema enviado: ${_tema.value}")
        }
    }

    private fun ajustesJson(): JSONObject? =
        runCatching {
            val ds = context.dataStore
            val tinte = ds.get(com.cglabs.lifemusic.constants.LiquidGlassSurfaceTintColorKey, 0)
            val cristal = JSONObject()
                .put("opacidad", ds.get(com.cglabs.lifemusic.constants.LiquidGlassSurfaceOpacityKey, 0.4f).toDouble())
                .put("tinte", if (tinte == 0) JSONObject.NULL else String.format("#%06X", tinte and 0xFFFFFF))
                .put("vibrancia", ds.get(com.cglabs.lifemusic.constants.LiquidGlassVibrancyKey, 1f).toDouble())
                .put("lente", ds.get(com.cglabs.lifemusic.constants.LiquidGlassLensAmountKey, 0.5f).toDouble())
                .put("altura", ds.get(com.cglabs.lifemusic.constants.LiquidGlassLensHeightKey, 0.5f).toDouble())
                .put("aberracion", ds.get(com.cglabs.lifemusic.constants.LiquidGlassChromaticAberrationKey, true))
                .put("profundidad", ds.get(com.cglabs.lifemusic.constants.LiquidGlassDepthEffectKey, true))
                .put("desenfoque", ds.get(com.cglabs.lifemusic.constants.LiquidGlassBlurRadiusKey, 8f).toDouble())
            JSONObject().put("tipo", "ajustes").put("tema", _tema.value).put("placa", _versionTocadiscos.value).put("cristal", cristal).put("idioma", java.util.Locale.getDefault().toLanguageTag())
        }.getOrNull()

    private fun enviarSaludo(c: CastCliente) {
        if (c.appActiva != APP_LIFE_MUSIC) return
        saludoJson()?.let { c.enviarPropio(it) }
    }

    private fun saludoJson(): JSONObject? {
        if (!context.dataStore.get(com.cglabs.lifemusic.constants.GreetingEnabledKey, true)) return null
        return runCatching {
            val recientes = context.dataStore.get(com.cglabs.lifemusic.constants.RecentGreetingsKey, "").split(',').filter { it.isNotBlank() }
            val f = com.cglabs.lifemusic.ui.component.fraseDeSaludo(context, diasSinAbrir = 0, recientes = recientes)
            JSONObject().put("tipo", "saludo").put("cabecera", f.cabecera).put("frase", f.frase)
        }.getOrNull()
    }

    /**
     * Deja de transmitir y la musica vuelve al telefono. [porUsuario]: lo pidio el
     * usuario (la hoja o la notificacion), no una caida de red. [reanudar] = false
     * cuando se cambia de aparato: el telefono no debe sonar entre medias.
     */
    fun disconnect(porUsuario: Boolean = true, reanudar: Boolean = true) {
        val c = cliente
        if (c == null && sesionDlna == null && sesionWeb == null) return
        cliente = null
        soltarDlna()
        soltarWeb()
        aparatoActual = null
        _receptorPropio.value = false
        com.cglabs.lifemusic.cast.ServicioDeCast.parar(context)
        val posicion = _castPosition.value
        val sonaba = _castIsPlaying.value
        seguimiento?.cancel(); seguimiento = null
        cargando?.cancel(); cargando = null
        sesionSuperada = -1
        if (c != null) scope.launch(Dispatchers.IO) { runCatching { c.cerrar(pararApp = true) } }
        _isCasting.value = false
        _castIsPlaying.value = false
        _castIsBuffering.value = false
        _castDeviceName.value = null
        idCargado = null
        // La musica vuelve al telefono donde iba. Sonando solo si la app se ve (o
        // lo pidio el usuario desde la notificacion): arrancar la reproduccion
        // con la app en segundo plano obliga a MusicService a pasar a primer
        // plano, y Android lo prohibe y cierra la app
        // (ForegroundServiceStartNotAllowedException, visto el 21-09 al perderse
        // la conexion con la pantalla apagada). En ese caso queda en pausa.
        val puedeSonar = porUsuario || androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        scope.launch(Dispatchers.Main) {
            runCatching {
                musicService.player.seekTo(posicion)
                if (sonaba && puedeSonar && reanudar) musicService.player.play()
            }
        }
    }

    /**
     * Se cayo el socket. El TV sigue sonando solo, asi que antes de rendirse se
     * intenta volver a entrar en la misma sesion (tres intentos en ~6 s) sin
     * recargar la cancion; el usuario no lo nota. Si el TV ya cerro la app o
     * no hay red, la musica vuelve al telefono.
     */
    private fun perdida(motivo: Throwable?) {
        val c = cliente ?: return
        val aparato = aparatoActual
        com.cglabs.lifemusic.cast.DiagnosticoCast.log("conexion perdida", motivo)
        if (aparato == null) { aviso(context.getString(R.string.cast_conexion_perdida)); disconnect(porUsuario = false); return }
        scope.launch(Dispatchers.IO) {
            _autoReconnecting.value = true
            var nuevo: CastCliente? = null
            for (intento in 1..3) {
                delay(if (intento == 1) 600L else 2_500L)
                if (cliente !== c) break // el usuario desconecto mientras tanto
                val n = CastCliente(scope)
                val ok = runCatching {
                    n.conectar(aparato.host, aparato.puerto)
                    n.engancharse(c.appActiva ?: "CC1AD845")
                }.getOrDefault(false)
                if (ok) { nuevo = n; break }
                runCatching { n.cerrar(pararApp = false) }
                com.cglabs.lifemusic.cast.DiagnosticoCast.log("reenganche $intento fallo")
            }
            _autoReconnecting.value = false
            if (nuevo == null || cliente !== c) {
                runCatching { nuevo?.cerrar(pararApp = false) }
                if (cliente === c) { aviso(context.getString(R.string.cast_conexion_perdida)); disconnect(porUsuario = false) }
                return@launch
            }
            nuevo.alCerrarse = { m -> scope.launch { perdida(m) } }
            nuevo.alMensajePropio = c.alMensajePropio
            cliente = nuevo
            sesionSuperada = -1
            withContext(Dispatchers.Main) { seguir(nuevo) }
            nuevo.pedirEstado()
            com.cglabs.lifemusic.cast.DiagnosticoCast.log("reenganchado a ${aparato.nombre}")
        }
    }

    // ── Cargar canciones ─────────────────────────────────────────────────────

    /**
     * La cancion actual del telefono, desde donde va: al conectar (el TV sigue
     * donde iba el telefono). El servicio tambien la llama al resincronizar tras
     * cargar una cola; si esa cancion ya esta cargada o cargandose por el cambio
     * de pista, no se repite: la segunda carga llegaba con la posicion de la
     * cancion anterior, que aun sonaba en el TV, y la nueva arrancaba a mitad.
     */
    fun loadCurrentMedia() {
        val actual = musicService.player.currentMetadata ?: return
        if (idCargado == actual.id) return
        loadMedia(actual, desdeMs = _castPosition.value)
    }

    fun loadMedia(metadata: MediaMetadata) = loadMedia(metadata, desdeMs = 0L)

    /**
     * Sesion de media del TV que estaba sonando cuando se pidio la cancion nueva.
     * Hasta que el TV conteste con una sesion mas nueva, el seguimiento no toma
     * la posicion de esa (la cancion anterior sigue sonando alla unos segundos).
     */
    @Volatile private var sesionSuperada = -1

    private fun loadMedia(metadata: MediaMetadata, desdeMs: Long) {
        sesionWeb?.let { s ->
            if (idCargado == metadata.id) return
            idCargado = metadata.id
            cargando?.cancel()
            cargando = scope.launch(Dispatchers.IO) { cargarWeb(s, metadata, desdeMs) }
            return
        }
        sesionDlna?.let { s ->
            if (idCargado == metadata.id) return
            idCargado = metadata.id
            cargando?.cancel()
            cargando = scope.launch(Dispatchers.IO) { cargarDlna(s, metadata, desdeMs) }
            return
        }
        val c = cliente ?: return
        if (idCargado == metadata.id) return
        idCargado = metadata.id
        cargando?.cancel()
        cargando = scope.launch(Dispatchers.IO) {
            sesionSuperada = c.estadoMedia.value?.mediaSessionId ?: -1
            _castIsBuffering.value = true
            _castPosition.value = desdeMs
            val url = musicService.getStreamUrl(metadata.id)
            if (url == null) {
                sesionSuperada = -1
                _castIsBuffering.value = false
                aviso(context.getString(R.string.cast_error_cancion))
                return@launch
            }
            val tipo = if (url.contains("mime=audio%2Fwebm") || url.contains("mime=audio/webm")) "audio/webm" else "audio/mp4"
            val ok = c.cargar(
                mediaId = metadata.id,
                url = url,
                tipo = tipo,
                titulo = metadata.title,
                artista = metadata.artists.joinToString { it.name },
                album = metadata.album?.title,
                imagen = metadata.thumbnailUrl?.resize(1080, 1080),
                desdeSeg = desdeMs / 1000.0,
                reproducir = quieroSonar,
            )
            if (!ok) {
                sesionSuperada = -1
                _castIsBuffering.value = false
                if (idCargado == metadata.id) idCargado = null
                aviso(context.getString(R.string.cast_error_cancion))
            } else if (c.appActiva == APP_LIFE_MUSIC) {
                enviarAmbiente({ c.enviarPropio(it) }, { cliente === c && idCargado == metadata.id }, metadata)
            }
        }
    }

    /**
     * Lo que el TV necesita para dibujar el modo ambiente y que no viaja en el
     * LOAD: los colores de la caratula, el tempo (del analisis de Automix, si
     * lo hay) y la letra sincronizada (de la base; si no esta, se pide como
     * hace el reproductor y se guarda).
     */
    private suspend fun enviarAmbiente(enviar: (JSONObject) -> Unit, vigente: () -> Boolean, metadata: MediaMetadata) {
        runCatching {
            val colores = RenderizadorDeClip.coloresDeCaratula(context, metadata.thumbnailUrl)
            val beat = runCatching { musicService.database.beatInfo(metadata.id) }.getOrNull()
            // El canvas, si la cancion lo tiene y el usuario no apago los canvas: el TV lo
            // reproduce en silencio dentro del marco de la caratula, como el telefono.
            val canvas = if (context.dataStore.get(com.cglabs.lifemusic.constants.CanvasThumbnailAnimationKey, true)) {
                runCatching { com.cglabs.lifemusic.ui.player.buscarCanvas(metadata)?.preferredAnimationUrl }.getOrNull()
            } else null
            val cancion = JSONObject()
                .put("tipo", "cancion")
                .put("id", metadata.id)
                .put("titulo", metadata.title)
                .put("artista", metadata.artists.joinToString { it.name })
                .put("album", metadata.album?.title)
                .put("caratula", metadata.thumbnailUrl?.resize(1080, 1080))
                .put("colores", JSONArray(colores.map { String.format("#%06X", it and 0xFFFFFF) }))
                .put("duracionMs", metadata.duration * 1000L)
                .put("canvas", canvas)
            val conTempo = beat != null && beat.bpm > 40f && beat.confidence >= 0.4f
            if (conTempo) {
                cancion.put("bpm", beat!!.bpm.toDouble()).put("primerBeatMs", beat.firstBeatOffsetMs)
            }
            enviar(cancion)
            if (!conTempo) {
                // Sin analisis previo (solo Automix lo hace), el TV respiraria a un
                // ritmo fijo. Se analiza ahora y se le manda el tempo en cuanto este,
                // sin retener la letra ni los colores.
                scope.launch(Dispatchers.IO) {
                    val t = runCatching { musicService.tempoDe(metadata.id) }.getOrNull()
                    if (t != null && vigente()) {
                        enviar(JSONObject().put("tipo", "tempo").put("id", metadata.id).put("bpm", t.bpm.toDouble()).put("primerBeatMs", t.firstBeatOffsetMs))
                        com.cglabs.lifemusic.cast.DiagnosticoCast.log("tempo analizado y enviado: ${t.bpm}")
                    } else com.cglabs.lifemusic.cast.DiagnosticoCast.log("tempo: sin resultado para ${metadata.id}")
                }
            }

            var texto = musicService.database.lyrics(metadata.id).first()?.lyrics
            if (texto == null) {
                val traida = runCatching { musicService.lyricsHelper.getLyrics(metadata) }.getOrNull()
                texto = traida?.lyrics
                if (traida != null) runCatching {
                    musicService.database.query {
                        upsert(com.cglabs.lifemusic.db.entities.LyricsEntity(id = metadata.id, lyrics = traida.lyrics ?: "", provider = traida.providerName))
                    }
                }
            }
            val lineas = texto?.trim()?.takeIf { it.isNotEmpty() && it.startsWith("[") }
                ?.let { runCatching { LyricsUtils.parseLyrics(it) }.getOrNull() }
                ?.filter { it.time >= 0L }
                .orEmpty()
            val json = JSONArray()
            for (l in lineas) {
                val linea = JSONObject().put("t", l.time).put("texto", l.text)
                l.words?.takeIf { it.isNotEmpty() }?.let { ws ->
                    linea.put("palabras", JSONArray(ws.map { w -> JSONObject().put("t", (w.startTime * 1000).toLong()).put("w", w.text) }))
                }
                json.put(linea)
            }
            enviar(JSONObject().put("tipo", "letra").put("id", metadata.id).put("lineas", json))
            com.cglabs.lifemusic.cast.DiagnosticoCast.log("ambiente enviado: colores=${colores.size} bpm=${beat?.bpm} canvas=${canvas?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }} lineas=${lineas.size}")
        }.onFailure { com.cglabs.lifemusic.cast.DiagnosticoCast.log("enviarAmbiente fallo", it) }
    }

    // ── Mando ────────────────────────────────────────────────────────────────

    fun play() {
        quieroSonar = true
        sesionWeb?.let { it.enviar(JSONObject().put("tipo", "play")); return }
        sesionDlna?.let { s -> scope.launch(Dispatchers.IO) { runCatching { s.play() } }; return }
        cliente?.play()
    }
    fun pause() {
        quieroSonar = false
        sesionWeb?.let { it.enviar(JSONObject().put("tipo", "pausa")); return }
        sesionDlna?.let { s -> scope.launch(Dispatchers.IO) { runCatching { s.pause() } }; return }
        cliente?.pause()
    }
    fun seekTo(position: Long) {
        _castPosition.value = position
        sesionWeb?.let { it.enviar(JSONObject().put("tipo", "ir").put("ms", position)); return }
        sesionDlna?.let { s -> scope.launch(Dispatchers.IO) { runCatching { s.seek(position) } }; return }
        cliente?.seek(position / 1000.0)
    }
    fun setVolume(volume: Float) {
        _castVolume.value = volume.coerceIn(0f, 1f)
        sesionWeb?.let { it.enviar(JSONObject().put("tipo", "volumen").put("v", volume.coerceIn(0f, 1f).toDouble())); return }
        sesionDlna?.let { s -> scope.launch(Dispatchers.IO) { runCatching { s.ponerVolumen(volume) } }; return }
        cliente?.setVolumen(volume)
    }

    fun skipToNext() {
        scope.launch(Dispatchers.Main) {
            val p = musicService.player
            if (p.hasNextMediaItem()) p.seekToNext()
        }
    }

    fun skipToPrevious() {
        scope.launch(Dispatchers.Main) {
            val p = musicService.player
            if (_castPosition.value > 3_000L || !p.hasPreviousMediaItem()) seekTo(0L)
            else p.seekToPreviousMediaItem()
        }
    }

    // La cola es la del telefono: nada que espejar en el receptor.
    fun navigateToMediaIfInQueue(mediaId: String): Boolean = false
    fun removeItemFromQueue(itemId: Int) {}
    fun moveItemInQueue(itemId: Int, newIndex: Int) {}
    fun clearQueue() {}
    suspend fun insertItemsAfterCurrent(items: List<androidx.media3.common.MediaItem>) = Unit
    suspend fun appendItemsToCastQueue(items: List<androidx.media3.common.MediaItem>) = Unit

    fun release() {
        disconnect(porUsuario = false, reanudar = false)
        descubridor.detener(forzar = true)
        dlna.detener(forzar = true)
    }

    // ── Seguimiento del receptor ─────────────────────────────────────────────

    /** Lee el estado del receptor, extrapola la posicion y avanza la cola al terminar una pista. */
    private fun seguir(c: CastCliente) {
        seguimiento?.cancel()
        seguimiento = scope.launch(Dispatchers.Main) {
            var ultimoTerminado = -1
            var tic = 0
            while (isActive && cliente === c) {
                val e = c.estadoMedia.value
                // Estado de la sesion que se esta reemplazando: se ignora hasta que llegue la nueva.
                if (e != null && e.mediaSessionId > sesionSuperada) {
                    val reproduciendo = e.estado == "PLAYING"
                    _castIsPlaying.value = reproduciendo || e.estado == "BUFFERING"
                    _castIsBuffering.value = e.estado == "BUFFERING"
                    val avance = if (reproduciendo) (SystemClock.elapsedRealtime() - e.medidoEn) else 0L
                    _castPosition.value = (e.posicionSeg * 1000).toLong() + avance
                    e.duracionSeg?.let { _castDuration.value = (it * 1000).toLong() }
                    if (e.estado == "IDLE" && e.razonIdle == "FINISHED" && e.mediaSessionId != ultimoTerminado) {
                        ultimoTerminado = e.mediaSessionId
                        val p = musicService.player
                        if (p.hasNextMediaItem()) p.seekToNext() else { _castIsPlaying.value = false; quieroSonar = false }
                    }
                }
                _castVolume.value = c.volumenReceptor.value
                if (++tic % 10 == 0) c.pedirEstado()
                delay(500)
            }
        }
    }

    private inline fun conSincronia(bloque: () -> Unit) {
        isSyncingFromCast = true
        try { bloque() } finally { isSyncingFromCast = false }
    }

    private fun aviso(texto: String) {
        scope.launch(Dispatchers.Main) { Toast.makeText(context, texto, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "LifeMusicCast"
        /**
         * App ID del receptor propio (web/cast/), registrado por CG en la consola de
         * Cast de Google el 2026-09-20 (es publico, no un secreto). Mientras la app
         * este sin publicar en la consola, solo la lanzan los Chromecast registrados
         * ahi por numero de serie; los demas receptores caen al reproductor por
         * defecto (audio).
         */
        val APP_LIFE_MUSIC: String? = "1D9B6EDB"

        const val TEMA_AMBIENTE = "ambiente"
        const val TEMA_CRISTAL = "cristal"
        const val TEMA_ESCENARIO = "escenario"
        const val TEMA_VINILO = "vinilo"
        const val TEMA_GALERIA = "galeria"
        const val TEMA_NOCTURNO = "nocturno"
        // Temas pro: fondo real (video o foto en web/cast/temas/), sin limites de uso por ahora.
        const val TEMA_ATARDECER = "atardecer"
        const val TEMA_TOCADISCOS = "tocadiscos"
    }
}
