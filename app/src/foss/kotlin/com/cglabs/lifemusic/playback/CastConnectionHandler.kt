package com.cglabs.lifemusic.playback

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.cast.CastCliente
import com.cglabs.lifemusic.cast.DescubridorCast
import com.cglabs.lifemusic.clip.RenderizadorDeClip
import com.cglabs.lifemusic.lyrics.LyricsUtils
import kotlinx.coroutines.flow.first
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

    fun buscar() = descubridor.iniciar()
    fun dejarDeBuscar() = descubridor.detener()

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
                c.alCerrarse = { motivo -> scope.launch { perdida(motivo) } }
                cliente = c
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

    fun disconnect() {
        val c = cliente ?: return
        cliente = null
        val posicion = _castPosition.value
        val sonaba = _castIsPlaying.value
        seguimiento?.cancel(); seguimiento = null
        cargando?.cancel(); cargando = null
        scope.launch(Dispatchers.IO) { runCatching { c.cerrar(pararApp = true) } }
        _isCasting.value = false
        _castIsPlaying.value = false
        _castIsBuffering.value = false
        _castDeviceName.value = null
        idCargado = null
        // La musica vuelve al telefono donde iba.
        scope.launch(Dispatchers.Main) {
            runCatching {
                musicService.player.seekTo(posicion)
                if (sonaba) musicService.player.play()
            }
        }
    }

    private fun perdida(motivo: Throwable?) {
        if (cliente == null) return
        com.cglabs.lifemusic.cast.DiagnosticoCast.log("conexion perdida", motivo)
        aviso(context.getString(R.string.cast_conexion_perdida))
        disconnect()
    }

    // ── Cargar canciones ─────────────────────────────────────────────────────

    fun loadCurrentMedia() {
        val actual = musicService.player.currentMetadata ?: return
        idCargado = null
        loadMedia(actual, desdeMs = _castPosition.value)
    }

    fun loadMedia(metadata: MediaMetadata) = loadMedia(metadata, desdeMs = 0L)

    private fun loadMedia(metadata: MediaMetadata, desdeMs: Long) {
        val c = cliente ?: return
        if (idCargado == metadata.id) return
        idCargado = metadata.id
        cargando?.cancel()
        cargando = scope.launch(Dispatchers.IO) {
            _castIsBuffering.value = true
            _castPosition.value = desdeMs
            val url = musicService.getStreamUrl(metadata.id)
            if (url == null) {
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
                _castIsBuffering.value = false
                if (idCargado == metadata.id) idCargado = null
                aviso(context.getString(R.string.cast_error_cancion))
            } else if (c.appActiva == APP_LIFE_MUSIC) {
                enviarAmbiente(c, metadata)
            }
        }
    }

    /**
     * Lo que el TV necesita para dibujar el modo ambiente y que no viaja en el
     * LOAD: los colores de la caratula, el tempo (del analisis de Automix, si
     * lo hay) y la letra sincronizada (de la base; si no esta, se pide como
     * hace el reproductor y se guarda).
     */
    private suspend fun enviarAmbiente(c: CastCliente, metadata: MediaMetadata) {
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
            if (beat != null && beat.bpm > 40f && beat.confidence >= 0.4f) {
                cancion.put("bpm", beat.bpm.toDouble()).put("primerBeatMs", beat.firstBeatOffsetMs)
            }
            c.enviarPropio(cancion)

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
            c.enviarPropio(JSONObject().put("tipo", "letra").put("id", metadata.id).put("lineas", json))
            com.cglabs.lifemusic.cast.DiagnosticoCast.log("ambiente enviado: colores=${colores.size} bpm=${beat?.bpm} canvas=${canvas != null} lineas=${lineas.size}")
        }.onFailure { com.cglabs.lifemusic.cast.DiagnosticoCast.log("enviarAmbiente fallo", it) }
    }

    // ── Mando ────────────────────────────────────────────────────────────────

    fun play() { quieroSonar = true; cliente?.play() }
    fun pause() { quieroSonar = false; cliente?.pause() }
    fun seekTo(position: Long) {
        _castPosition.value = position
        cliente?.seek(position / 1000.0)
    }
    fun setVolume(volume: Float) {
        _castVolume.value = volume.coerceIn(0f, 1f)
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
        disconnect()
        descubridor.detener()
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
                if (e != null) {
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
    }
}
