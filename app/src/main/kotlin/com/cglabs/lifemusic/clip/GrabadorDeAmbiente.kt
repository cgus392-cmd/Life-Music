package com.cglabs.lifemusic.clip

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.PixelCopy
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.playback.MusicService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Graba el modo ambiente tal cual se ve, como si fuera una grabacion interna:
 * cuenta atras de tres, y desde ahi se captura la ventana de la app fotograma
 * a fotograma (PixelCopy, sin permiso de grabar pantalla ni marca de agua del
 * sistema) y se codifica en un hilo aparte. La cuenta atras y el REC van en
 * otra ventana (un Popup) para que no salgan en el video.
 *
 * El audio no se graba del microfono ni del sistema: se saca de la propia
 * cancion (el tramo que sono, por posicion) y se une al final con
 * Remezclador. Si la cancion cambia, se pausa o se busca dentro de ella, la
 * grabacion termina ahi: el audio ya no cuadraria.
 *
 * Termina por tiempo, por «detener», o por esas interrupciones; el resultado
 * vuelve a la hoja del clip (ClipLanzador.abrirResultado) para compartir.
 */
object GrabadorDeAmbiente {
    sealed class Estado {
        data object Inactivo : Estado()
        /** Copiando el audio y preparando el codificador. */
        data object Preparando : Estado()
        data class CuentaAtras(val n: Int) : Estado()
        data class Grabando(val segundos: Int, val total: Int) : Estado()
        /** Audio y union; [fraccion] de 0 a 1. */
        data class Terminando(val fraccion: Float) : Estado()
    }

    const val FPS = 30
    private const val TAG = "LifeMusicClip"
    /** Menos que esto no es un clip: se descarta. */
    private const val MINIMO_MS = 2_000L
    /** Deriva tolerada entre lo que suena y lo esperado antes de cortar por «busqueda». */
    private const val DERIVA_MS = 1_500L

    val estado = MutableStateFlow<Estado>(Estado.Inactivo)
    val activo: Boolean get() = estado.value != Estado.Inactivo

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var trabajo: Job? = null
    @Volatile private var pedidoDetener = false

    fun iniciar(activity: Activity, service: MusicService, cancion: MediaMetadata, duracionSeg: Int, conAudio: Boolean) {
        if (activo) return
        pedidoDetener = false
        estado.value = Estado.Preparando
        trabajo = scope.launch {
            try {
                grabar(activity, service, cancion, duracionSeg, conAudio)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "grabacion fallo", e)
            } finally {
                estado.value = Estado.Inactivo
            }
        }
    }

    /** Termina la grabacion en curso y la guarda (boton «detener», salir del modo ambiente). */
    fun detener() { pedidoDetener = true }

    /** Aborta sin guardar nada. */
    fun cancelar() { trabajo?.cancel(); trabajo = null }

    private suspend fun grabar(activity: Activity, service: MusicService, cancion: MediaMetadata, duracionSeg: Int, conAudio: Boolean) {
        val context = activity.applicationContext
        val carpeta = File(context.cacheDir, "compartir").apply { mkdirs() }
        val videoTmp = File(context.cacheDir, "clip_video_tmp.mp4")
        val audioTmp = File(context.cacheDir, "clip_audio.tmp")
        val salida = File(carpeta, "clip-life-music.mp4")
        val player = service.player

        // 1. El audio se copia mientras corre la cuenta atras.
        val copia = if (conAudio) scope.async(Dispatchers.IO) { service.copiarAudioParaClip(cancion.id, audioTmp) } else null

        // 2. Codificador en su hilo (el contexto EGL es de ese hilo).
        val decor = activity.window.decorView
        val (ancho, alto) = tamanoDeSalida(decor.width, decor.height)
        val hilo = HandlerThread("clip-grabacion").apply { start() }
        val handler = Handler(hilo.looper)
        val codificador = CodificadorDeClip(ancho, alto, FPS, videoTmp)
        val abierto = CompletableDeferred<Boolean>()
        handler.post { abierto.complete(runCatching { codificador.abrir() }.onFailure { android.util.Log.w(TAG, "abrir codificador", it) }.isSuccess) }
        val libres = ConcurrentLinkedQueue<Bitmap>()
        val todos = List(3) { Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888) }
        libres.addAll(todos)
        val enVuelo = AtomicInteger(0)
        try {
            if (!abierto.await()) throw IllegalStateException("codificador")

            // 3. Cuenta atras. Si esta en pausa, arranca al llegar a cero.
            for (n in 3 downTo 1) {
                estado.value = Estado.CuentaAtras(n)
                delay(1_000L)
            }
            if (!player.playWhenReady) player.play()

            // 4. Grabacion: un fotograma cada 1/FPS mientras haya bitmap libre
            // (si el codificador va lento se salta el fotograma, no se acumula).
            val desdeMs = player.currentPosition.coerceAtLeast(0L)
            val idInicial = player.currentMediaItem?.mediaId
            val t0 = SystemClock.elapsedRealtimeNanos()
            val duracionNs = duracionSeg * 1_000_000_000L
            val fotogramas = AtomicInteger(0)
            var ultimoPtsNs = 0L
            while (true) {
                val ahora = SystemClock.elapsedRealtimeNanos() - t0
                if (ahora >= duracionNs || pedidoDetener) break
                if (player.currentMediaItem?.mediaId != idInicial || !player.playWhenReady) break
                val esperado = desdeMs + (ahora / 1_000_000L * player.playbackParameters.speed).toLong()
                if (abs(player.currentPosition - esperado) > DERIVA_MS) break
                estado.value = Estado.Grabando((ahora / 1_000_000_000L).toInt(), duracionSeg)
                val bmp = libres.poll()
                if (bmp != null) {
                    enVuelo.incrementAndGet()
                    ultimoPtsNs = ahora
                    PixelCopy.request(activity.window, bmp, { resultado ->
                        if (resultado == PixelCopy.SUCCESS) {
                            runCatching { codificador.entregar(bmp, ahora) }
                                .onSuccess { fotogramas.incrementAndGet() }
                                .onFailure { android.util.Log.w(TAG, "entregar fotograma", it) }
                        }
                        libres.add(bmp)
                        enVuelo.decrementAndGet()
                    }, handler)
                }
                delay(1_000L / FPS)
            }
            while (enVuelo.get() > 0) delay(5L)

            // 5. Cierre del video.
            estado.value = Estado.Terminando(0.15f)
            val cerrado = CompletableDeferred<Unit>()
            handler.post { runCatching { codificador.cerrar() }; cerrado.complete(Unit) }
            cerrado.await()
            val duracionRealMs = ultimoPtsNs / 1_000_000L
            android.util.Log.i(TAG, "grabacion ${fotogramas.get()} fotogramas, ${duracionRealMs} ms, ${videoTmp.length()} bytes, ${ancho}x${alto}")
            if (duracionRealMs < MINIMO_MS || fotogramas.get() < 10 || videoTmp.length() < 10_000) {
                videoTmp.delete()
                return
            }

            // 6. Audio del tramo que sono, y union.
            salida.delete()
            var unido = false
            if (copia != null && copia.await()) {
                estado.value = Estado.Terminando(0.4f)
                val tramo = withContext(Dispatchers.IO) { AudioDelClip.decodificar(audioTmp, desdeMs, duracionRealMs + 200L) { false } }
                val aac = tramo?.let { t -> withContext(Dispatchers.Default) { runCatching { t.codificarAac() }.onFailure { android.util.Log.w(TAG, "AAC fallo", it) }.getOrNull() } }
                estado.value = Estado.Terminando(0.8f)
                if (aac != null) unido = withContext(Dispatchers.IO) { Remezclador.unir(videoTmp, aac, salida) }
                android.util.Log.i(TAG, "audio grabacion: tramo=${tramo != null} aac=${aac?.muestras?.size ?: 0} unido=$unido")
            }
            if (!unido) {
                salida.delete()
                if (!videoTmp.renameTo(salida)) videoTmp.copyTo(salida, overwrite = true)
            }
            videoTmp.delete()
            estado.value = Estado.Terminando(1f)
            ClipLanzador.abrirResultado(salida)
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                copia?.cancel()
                // Nada de reciclar mientras PixelCopy o el codificador tengan un bitmap.
                var espera = 0
                while (enVuelo.get() > 0 && espera++ < 100) delay(5L)
                val cerrado = CompletableDeferred<Unit>()
                handler.post { runCatching { codificador.cerrar() }; cerrado.complete(Unit) }
                cerrado.await()
                hilo.quitSafely()
                todos.forEach { runCatching { it.recycle() } }
                audioTmp.delete()
                videoTmp.delete()
            }
        }
    }

    /** Ancho 1920 apaisado o 1080 vertical, con la misma proporcion que la ventana (pares). */
    private fun tamanoDeSalida(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1920 to 1080
        return if (w >= h) 1920 to ((1920L * h / w).toInt() / 2 * 2)
        else 1080 to ((1080L * h / w).toInt() / 2 * 2)
    }
}
