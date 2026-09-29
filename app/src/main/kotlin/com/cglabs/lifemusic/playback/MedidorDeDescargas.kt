package com.cglabs.lifemusic.playback

import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * La velocidad real de la red, medida con lo que la app ya descarga (el audio),
 * sin bajar nada de mas.
 *
 * Solo cuenta el arranque de cada descarga. Al empezar una cancion (o tras
 * adelantar) el reproductor llena su bufer desde cero sin pausas, asi que
 * ahi la red es el cuello de botella: los primeros [MUESTRA_BYTES] dicen lo
 * que da. Despues el reproductor lee a su ritmo, con pausas cuando el bufer
 * esta lleno, y eso ya no mide la red (por eso no sirve el medidor de ExoPlayer:
 * con una descarga larga y pausada da casi el bitrate de la cancion).
 *
 * Lo usa el indicador de red del carro. En el telefono solo suma bytes.
 */
object MedidorDeDescargas : TransferListener {

    /** Lo bastante para salir del arranque lento de TCP; menos que el bufer a la calidad mas baja. */
    private const val MUESTRA_BYTES = 256 * 1024L

    /** Una medida: bits por segundo y cuando se tomo (reloj de arranque, ms). */
    data class Muestra(val bitsPorSegundo: Long, val cuandoMs: Long)

    private val _ultima = MutableStateFlow<Muestra?>(null)

    /** La ultima medida, o null si aun no se ha descargado nada en esta sesion. */
    val ultima: StateFlow<Muestra?> = _ultima

    private class EnCurso(val inicioNs: Long) {
        var bytes = 0L
        var medida = false
    }

    private val enCurso = ConcurrentHashMap<DataSource, EnCurso>()

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (isNetwork) enCurso[source] = EnCurso(SystemClock.elapsedRealtimeNanos())
    }

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (!isNetwork) return
        val t = enCurso[source] ?: return
        if (t.medida) return
        t.bytes += bytesTransferred
        if (t.bytes < MUESTRA_BYTES) return
        t.medida = true
        val ns = SystemClock.elapsedRealtimeNanos() - t.inicioNs
        if (ns > 0) _ultima.value = Muestra(t.bytes * 8 * 1_000_000_000L / ns, SystemClock.elapsedRealtime())
    }

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        enCurso.remove(source)
    }
}
