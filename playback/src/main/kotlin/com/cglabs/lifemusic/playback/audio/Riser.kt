package com.cglabs.lifemusic.playback.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 2.1.0 · El riser del estilo «Subida» del Mixed Editor: ruido que sube de tono y de volumen hasta el golpe en que la
 * canción nueva toma el mando, como el «uplifter» de un DJ. Se genera al vuelo (nada de archivos de sonido de nadie):
 * ruido blanco por un pasabanda que sube de [DESDE_HZ] a [HASTA_HZ], más un tono suave que sube una octava, con una
 * envolvente que crece y se suelta en los últimos [SOLTAR_MS] ms.
 *
 * Suena por su propio [AudioTrack], encima de los dos reproductores; respeta el volumen del sistema.
 */
class Riser(private val duracionMs: Long, volumen: Float) {
    private val pista: AudioTrack?

    init {
        val muestras = (FRECUENCIA.toLong() * duracionMs / 1000L).toInt().coerceAtLeast(FRECUENCIA / 10)
        val pcm = generar(muestras)
        pista = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(FRECUENCIA)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .also {
                    it.write(pcm, 0, pcm.size)
                    it.setVolume(volumen.coerceIn(0f, 1f))
                }
        }.getOrNull()
    }

    fun sonar() {
        runCatching { pista?.play() }
    }

    fun pausar() {
        runCatching { pista?.pause() }
    }

    fun soltar() {
        runCatching {
            pista?.stop()
            pista?.release()
        }
    }

    private fun generar(n: Int): ShortArray {
        val salida = FloatArray(n)
        val azar = java.util.Random(7)
        // Pasabanda de estado variable (Chamberlin). Estable hasta ~1/6 de la frecuencia de muestreo: por eso el tope.
        var bajo = 0.0
        var banda = 0.0
        var fase = 0.0
        val q = 0.45
        val soltar = (FRECUENCIA * SOLTAR_MS / 1000).coerceAtMost(n / 3)
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val centro = DESDE_HZ * (HASTA_HZ / DESDE_HZ).pow(t.pow(1.6))
            val f = 2.0 * sin(PI * centro / FRECUENCIA)
            val ruido = azar.nextGaussian() * 0.5
            val alto = ruido - bajo - q * banda
            banda += f * alto
            bajo += f * banda
            // El tono que sube una octava, por debajo del ruido.
            val tono = TONO_HZ * 2.0.pow(t.pow(2.0))
            fase += 2.0 * PI * tono / FRECUENCIA
            val envolvente = t.pow(2.2) * if (i > n - soltar) cos((i - (n - soltar)).toDouble() / soltar * PI / 2.0) else 1.0
            salida[i] = ((banda * 0.85 + sin(fase) * 0.18) * envolvente).toFloat()
        }
        val pico = salida.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        val escala = 0.5f / pico * Short.MAX_VALUE
        return ShortArray(n) { (salida[it] * escala).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }

    companion object {
        private const val FRECUENCIA = 44_100
        private const val DESDE_HZ = 350.0
        private const val HASTA_HZ = 7_000.0
        private const val TONO_HZ = 220.0
        private const val SOLTAR_MS = 140
    }
}
