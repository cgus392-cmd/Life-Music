package com.cglabs.lifemusic.playback.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import timber.log.Timber
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Golpes reales de una cancion completa (22-10-2026, pedido de CG): los instantes de cada bombo o bajo fuerte,
 * para que el receptor del TV salte solo con ellos (tema Escenario) y no a un ritmo fijo. El TV no puede medir
 * el audio (viene de YouTube y el navegador no deja conectarlo a Web Audio), asi que se mide aqui.
 *
 * Como: se decodifica la cancion entera en pedazos, sin guardar el audio (seria unos 46 MB por cancion); los
 * graves pasan por un paso bajo de 150 Hz (el rango del bombo y del bajo) y se mide su energia cada 10 ms. Hay
 * golpe donde esa energia sube de golpe (9 dB en 40 ms) cerca del volumen fuerte de la cancion. Solo cuentan los
 * tramos con ritmo (5 golpes en 4 s): un golpe suelto en una balada no hace saltar nada.
 * Afinado el 22-10 en el navegador con «Tití Me Preguntó» (nada en la escena hablada del video ni en la parte
 * lenta), salsa, vallenato, rumba y regional: menos de 0,1 s de calculo por cancion en el PC.
 */
object DetectorDeGolpes {
    private const val TAG = "DetectorDeGolpes"
    private const val MARCO_MS = 10
    private const val CORTE_HZ = 150.0
    /** Cuanto tiene que subir la energia de los graves en 40 ms para ser un golpe. */
    private const val SUBIDA_DB = 9f
    /** Que tan cerca del volumen fuerte de la cancion (su percentil 95) tiene que estar el pico. */
    private const val BAJO_PICO_DB = 7f
    private const val SEPARACION_MS = 180L
    /** Solo en tramos con ritmo: al menos [MIN_EN_VENTANA] golpes en [VENTANA_MS] alrededor. */
    private const val VENTANA_MS = 4000L
    private const val MIN_EN_VENTANA = 5
    private const val MAX_GOLPES = 3000
    /** Unos 15 minutos de audio como mucho: si el decodificador no avisa el final, no se queda pegado. */
    private const val MAX_MARCOS = 90_000
    /** Lo que se baja para medir: una cancion entera en Opus o AAC de YouTube cabe de sobra. */
    private const val MAX_BYTES = 14L * 1024 * 1024

    /** Cada golpe (ms desde el inicio) y su fuerza, de 1 a 9, en el orden en que suenan. */
    class Golpes(val ms: LongArray, val fuerza: IntArray)

    fun deUri(context: Context, uri: Uri, cancelar: () -> Boolean = { false }): Golpes? =
        detectar(cancelar) { it.setDataSource(context, uri, null) }

    /** Baja la cancion por la cadena de reproduccion (cache primero) a un temporal y la mide. */
    @UnstableApi
    fun deStream(
        fabrica: DataSource.Factory,
        mediaId: String,
        carpeta: File,
        uri: Uri,
        cancelar: () -> Boolean = { false },
    ): Golpes? {
        val temporal = File(carpeta, "golpes_${mediaId.hashCode()}.tmp")
        try {
            var copiados = 0L
            val fuente = fabrica.createDataSource()
            try {
                fuente.open(DataSpec.Builder().setUri(uri).setKey(mediaId).build())
                java.io.FileOutputStream(temporal).use { salida ->
                    val buf = ByteArray(64 * 1024)
                    while (copiados < MAX_BYTES) {
                        if (cancelar()) return null
                        val leidos = fuente.read(buf, 0, buf.size)
                        if (leidos == C.RESULT_END_OF_INPUT) break
                        salida.write(buf, 0, leidos)
                        copiados += leidos
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("Descarga para los golpes de %s cortada en %d bytes (%s)", mediaId, copiados, e.message)
            } finally {
                try {
                    fuente.close()
                } catch (_: Exception) {
                }
            }
            if (copiados < 256 * 1024 || cancelar()) return null
            return detectar(cancelar) { it.setDataSource(temporal.absolutePath) }
        } finally {
            temporal.delete()
        }
    }

    private fun detectar(cancelar: () -> Boolean, fuente: (MediaExtractor) -> Unit): Golpes? {
        val extractor = MediaExtractor()
        try {
            fuente(extractor)
            var formato: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    formato = f
                    break
                }
            }
            val energia = energiaDeGraves(extractor, formato ?: return null, cancelar) ?: return null
            return picos(energia)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "No se pudieron detectar los golpes")
            return null
        } finally {
            extractor.release()
        }
    }

    /** La energia de los graves cada [MARCO_MS], de la cancion entera, sin guardar el audio. */
    private fun energiaDeGraves(extractor: MediaExtractor, formato: MediaFormat, cancelar: () -> Boolean): FloatArray? {
        val mime = formato.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mime)
        var energia = FloatArray(30_000)
        var marcos = 0
        var filtro: PasoBajo? = null
        var porMarco = 1
        var acumulado = 0.0
        var cuenta = 0
        try {
            codec.configure(formato, null, null, 0)
            codec.start()
            var entradaLista = false
            var salidaLista = false
            val info = MediaCodec.BufferInfo()
            while (!salidaLista && marcos < MAX_MARCOS) {
                if (cancelar()) return null
                if (!entradaLista) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val tam = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (tam < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            entradaLista = true
                        } else {
                            codec.queueInputBuffer(i, 0, tam, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) salidaLista = true
                    if (info.size > 0) {
                        val fmt = codec.outputFormat
                        val canales = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (filtro == null) {
                            val tasa = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            filtro = PasoBajo(CORTE_HZ, tasa)
                            porMarco = max(1, tasa * MARCO_MS / 1000)
                        }
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        val cortos = buf.asShortBuffer()
                        val muestras = cortos.remaining() / canales
                        for (m in 0 until muestras) {
                            var x = 0f
                            for (c in 0 until canales) x += cortos.get(m * canales + c) / 32768f
                            val y = filtro.paso((x / canales).toDouble())
                            acumulado += y * y
                            if (++cuenta == porMarco) {
                                if (marcos == energia.size) energia = energia.copyOf(energia.size * 2)
                                energia[marcos++] = (acumulado / porMarco).toFloat()
                                acumulado = 0.0
                                cuenta = 0
                            }
                        }
                    }
                    codec.releaseOutputBuffer(o, false)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Fallo al decodificar para los golpes")
            return null
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }
        return if (marcos > 100) energia.copyOf(marcos) else null
    }

    /** Un paso bajo de 2.o orden (Butterworth, Q = 0,707). */
    private class PasoBajo(corte: Double, tasa: Int) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            val w0 = 2 * PI * corte / tasa
            val c = cos(w0)
            val alfa = sin(w0) / (2 * sqrt(0.5))
            val a0 = 1 + alfa
            b0 = (1 - c) / 2 / a0
            b1 = (1 - c) / a0
            b2 = b0
            a1 = -2 * c / a0
            a2 = (1 - alfa) / a0
        }

        fun paso(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }

    /** Los golpes sobre la energia de los graves (un valor cada [MARCO_MS]). */
    internal fun picos(energia: FloatArray): Golpes {
        val n = energia.size
        val db = FloatArray(n) { (10.0 * log10(energia[it] + 1e-10)).toFloat() }
        val p95 = db.sortedArray()[min(n - 1, (0.95f * n).toInt())]
        // Candidatos: la energia sube SUBIDA_DB en 40 ms y queda cerca del volumen fuerte; el pico es el marco
        // mas alto de los 3 siguientes. La fuerza mezcla cuanto subio y que tan fuerte quedo.
        val tiempos = ArrayList<Long>()
        val fuerzas = ArrayList<Float>()
        for (i in 4 until n - 3) {
            val subida = db[i] - minOf(minOf(db[i - 1], db[i - 2]), minOf(db[i - 3], db[i - 4]))
            if (subida < SUBIDA_DB || db[i] < p95 - BAJO_PICO_DB) continue
            var k = i
            for (j in i + 1..i + 3) if (db[j] > db[k]) k = j
            val porSubida = ((subida - SUBIDA_DB) / 15f).coerceIn(0f, 1f)
            val porAltura = ((db[k] - (p95 - BAJO_PICO_DB)) / 10f).coerceIn(0f, 1f)
            val t = k.toLong() * MARCO_MS
            val f = 0.5f * porSubida + 0.5f * porAltura
            // Separacion minima: entre dos golpes muy juntos se queda el mas fuerte.
            if (tiempos.isNotEmpty() && t - tiempos.last() < SEPARACION_MS) {
                if (f > fuerzas.last()) {
                    tiempos[tiempos.size - 1] = t
                    fuerzas[fuerzas.size - 1] = f
                }
            } else {
                tiempos.add(t)
                fuerzas.add(f)
            }
        }
        // Solo los tramos con ritmo: cuantos golpes hay a menos de VENTANA_MS / 2 (dos punteros).
        val medio = VENTANA_MS / 2
        val ms = ArrayList<Long>()
        val fuerza = ArrayList<Int>()
        var desde = 0
        var hasta = 0
        for (i in tiempos.indices) {
            while (tiempos[desde] < tiempos[i] - medio) desde++
            while (hasta + 1 < tiempos.size && tiempos[hasta + 1] <= tiempos[i] + medio) hasta++
            if (hasta - desde + 1 >= MIN_EN_VENTANA) {
                ms.add(tiempos[i])
                fuerza.add(1 + (8 * fuerzas[i]).roundToInt())
                if (ms.size >= MAX_GOLPES) break
            }
        }
        return Golpes(ms.toLongArray(), fuerza.toIntArray())
    }
}
