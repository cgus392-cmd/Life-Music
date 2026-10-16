package com.cglabs.lifemusic.karaoke

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import com.cglabs.lifemusic.MainActivity
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.db.MusicDatabase
import com.cglabs.lifemusic.db.entities.LyricsEntity
import com.cglabs.lifemusic.di.DownloadCache
import com.cglabs.lifemusic.lyrics.LyricsUtils
import com.cglabs.lifemusic.utils.isLocalMediaId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * 2.x · Karaoke propio en segundo plano. Se pide desde el menu de una cancion
 * descargada o local; corre en primer plano con su notificacion («Preparando
 * karaoke…») para que Android no lo cierre aunque la persona salga de la app o
 * apague la pantalla, y avisa al final («Karaoke listo»).
 *
 * Guarda la letra original en filesDir/karaoke/originales antes de reemplazarla
 * por la palabra por palabra, para poder volver atras.
 */
@AndroidEntryPoint
class KaraokeService : Service() {
    @Inject lateinit var database: MusicDatabase

    @Inject @DownloadCache lateinit var downloadCache: SimpleCache

    // Un solo hilo, en prioridad de fondo: los hilos de ONNX Runtime nacen de el y
    // heredan la prioridad, asi la musica que suena no se entrecorta.
    private val hilo = Executors.newSingleThreadExecutor { r ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "karaoke")
    }
    private val scope = CoroutineScope(SupervisorJob() + hilo.asCoroutineDispatcher())
    private val fila = ArrayDeque<Pedido>()
    private var trabajando = false
    private var avisoMs = 0L

    private data class Pedido(val id: String, val titulo: String)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        val titulo = intent?.getStringExtra(EXTRA_TITULO).orEmpty()
        crearCanal()
        // Hay que entrar en primer plano enseguida, incluso si el pedido viene vacio.
        ServiceCompat.startForeground(
            this, AVISO_EN_CURSO, aviso(titulo, getString(R.string.karaoke_etapa_esperando), null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (id == null) {
            if (!trabajando) terminar()
            return START_NOT_STICKY
        }
        synchronized(fila) {
            if (fila.none { it.id == id }) fila.addLast(Pedido(id, titulo))
            if (trabajando) return START_NOT_STICKY
            trabajando = true
        }
        scope.launch { atenderFila() }
        return START_NOT_STICKY
    }

    private suspend fun atenderFila() {
        val energia = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LifeMusic:karaoke")
            ?.apply { acquire(30 * 60 * 1000L) }
        try {
            while (true) {
                val pedido = synchronized(fila) {
                    fila.removeFirstOrNull() ?: run { trabajando = false; null }
                } ?: break
                preparar(pedido)
            }
        } finally {
            energia?.takeIf { it.isHeld }?.release()
            terminar()
        }
    }

    private suspend fun preparar(pedido: Pedido) {
        val titulo = pedido.titulo.ifBlank { getString(R.string.karaoke_esta_cancion) }
        Log.i(TAG, "preparando ${pedido.id} «$titulo»")
        val guardada = database.lyrics(pedido.id).first()
        // Si ya se preparo antes, se rehace desde la letra original que se guardo.
        val respaldo = leerOriginal(this, pedido.id)
        val texto = respaldo?.second ?: guardada?.lyrics?.takeIf { it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND }
        if (guardada == null || texto == null) return fallo(titulo, R.string.karaoke_falta_letra)
        val lineas = LyricsUtils.parseLyrics(texto)
        if (lineas.any { !it.words.isNullOrEmpty() }) return fallo(titulo, R.string.karaoke_ya_tiene)
        val conTiempo = lineas.filter { it.time > 0 && it.text.isNotBlank() }
        if (conTiempo.size < 3) return fallo(titulo, R.string.karaoke_sin_sincronia)
        if (KaraokeModelos.reconocedor(this) == null && KaraokeModelos.redMedida(this)) {
            return fallo(titulo, R.string.karaoke_necesita_wifi)
        }

        val audio = File(cacheDir, "karaoke-audio-${SystemClock.elapsedRealtime()}")
        try {
            avisar(titulo, getString(R.string.karaoke_etapa_leyendo), null)
            if (!copiarAudio(pedido.id, audio)) return fallo(titulo, R.string.karaoke_sin_audio)

            val resultado = try {
                KaraokeMotor(this).preparar(audio, lineas) { etapa, avance ->
                    avisar(titulo, getString(etapa), avance)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "el motor fallo", e)
                null
            } ?: return fallo(titulo, R.string.karaoke_error)

            // Comprobar que la app lo lee palabra por palabra antes de guardarlo.
            val leida = LyricsUtils.parseLyrics(resultado.lrc)
            if (leida.count { !it.words.isNullOrEmpty() } < conTiempo.size / 2) {
                Log.e(TAG, "el resultado no se lee bien: ${leida.size} lineas")
                return fallo(titulo, R.string.karaoke_error)
            }
            guardarOriginal(pedido.id, guardada)
            database.query {
                upsert(guardada.copy(lyrics = resultado.lrc, provider = marcaProveedor(respaldo?.first ?: guardada.provider)))
            }
            val porciento = (resultado.cobertura * 100).toInt()
            Log.i(TAG, "listo «$titulo»: $porciento % por ${resultado.camino}, ${resultado.milisegundos / 1000} s")
            final(getString(R.string.karaoke_listo, titulo), getString(R.string.karaoke_listo_desc, porciento))
        } finally {
            audio.delete()
        }
    }

    /** El audio completo: del archivo local, o de la cache de descargas (sin red). */
    private fun copiarAudio(id: String, destino: File): Boolean = try {
        if (id.isLocalMediaId()) {
            contentResolver.openInputStream(Uri.parse(id))?.use { entrada ->
                destino.outputStream().use { entrada.copyTo(it) }
            } != null
        } else {
            // Sin fuente de red: si algo no esta descargado, falla en vez de bajarlo.
            val fuente = CacheDataSource(downloadCache, null)
            try {
                fuente.open(DataSpec.Builder().setUri(Uri.parse(id)).setKey(id).build())
                destino.outputStream().use { salida ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val n = fuente.read(buffer, 0, buffer.size)
                        if (n == C.RESULT_END_OF_INPUT) break
                        salida.write(buffer, 0, n)
                    }
                }
            } finally {
                fuente.close()
            }
            destino.length() > 0
        }
    } catch (e: Exception) {
        Log.e(TAG, "no se pudo leer el audio de $id", e)
        false
    }

    private fun guardarOriginal(id: String, letra: LyricsEntity) {
        val archivo = original(this, id)
        if (archivo.exists()) return // la primera original es la que vale
        archivo.parentFile?.mkdirs()
        archivo.writeText(letra.provider + "\n" + letra.lyrics)
    }

    private fun fallo(titulo: String, motivo: Int) {
        Log.w(TAG, "no se preparo «$titulo»: ${getString(motivo)}")
        final(getString(R.string.karaoke_no_se_pudo, titulo), getString(motivo))
    }

    private fun avisar(titulo: String, etapa: String, avance: Float?) {
        // La notificacion no aguanta cientos de cambios por segundo.
        val ahora = SystemClock.elapsedRealtime()
        if (avance != null && ahora - avisoMs < 700) return
        avisoMs = ahora
        getSystemService<NotificationManager>()?.notify(AVISO_EN_CURSO, aviso(titulo, etapa, avance))
    }

    private fun aviso(titulo: String, etapa: String, avance: Float?) =
        NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.mic)
            .setContentTitle(getString(R.string.karaoke_preparando))
            .setContentText(if (titulo.isBlank()) etapa else "$titulo · $etapa")
            .setProgress(100, ((avance ?: 0f) * 100).toInt(), avance == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(abrirApp())
            .build()

    private fun final(titulo: String, texto: String) {
        val aviso = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.mic)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .setContentIntent(abrirApp())
            .build()
        getSystemService<NotificationManager>()?.notify(AVISO_FINAL + (titulo.hashCode() and 0xfff), aviso)
    }

    private fun abrirApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun crearCanal() {
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CANAL, getString(R.string.karaoke_canal), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun terminar() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        hilo.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LifeMusicKaraoke"
        private const val CANAL = "karaoke_propio"
        private const val AVISO_EN_CURSO = 7301
        private const val AVISO_FINAL = 7400
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITULO = "titulo"
        private const val MARCA = "Karaoke Life Music (beta)"

        fun preparar(context: Context, id: String, titulo: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, KaraokeService::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_TITULO, titulo),
            )
        }

        fun marcaProveedor(proveedor: String) = if (proveedor.contains(MARCA)) proveedor else "$proveedor · $MARCA"

        private fun original(context: Context, id: String) =
            File(context.filesDir, "karaoke/originales/${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.lrc")

        fun tieneOriginal(context: Context, id: String) = original(context, id).isFile

        /** El proveedor y la letra por linea que habia antes del karaoke, si se guardaron. */
        private fun leerOriginal(context: Context, id: String): Pair<String, String>? {
            val guardado = original(context, id).takeIf { it.isFile }?.readText() ?: return null
            return guardado.substringBefore('\n') to guardado.substringAfter('\n')
        }

        /** Devuelve la letra por linea que habia antes del karaoke (fuera del hilo principal). */
        suspend fun volverAlOriginal(context: Context, database: MusicDatabase, id: String): Boolean {
            val archivo = original(context, id).takeIf { it.isFile } ?: return false
            val (proveedor, letra) = leerOriginal(context, id) ?: return false
            val actual = database.lyrics(id).first()
            database.query {
                upsert(actual?.copy(lyrics = letra, provider = proveedor) ?: LyricsEntity(id = id, lyrics = letra, provider = proveedor))
            }
            archivo.delete()
            return true
        }
    }
}
