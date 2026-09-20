package com.cglabs.lifemusic.playback

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.cast.CastCliente
import com.cglabs.lifemusic.cast.DescubridorCast
import com.cglabs.lifemusic.extensions.currentMetadata
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.ui.utils.resize
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
                        lanzado = c.lanzarReproductor()
                        if (lanzado) break
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "intento $intento fallo: ${e.javaClass.simpleName}")
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
                android.util.Log.w(TAG, "conectar fallo", e)
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
        android.util.Log.w(TAG, "conexion perdida", motivo)
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
            }
        }
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
    }
}
