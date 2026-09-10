package com.cglabs.lifemusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.cglabs.lifemusic.eq.audio.TransitionFilterAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mide el filtro de transicion con tonos, igual que se hizo con el reductor de
 * voz. Un Butterworth de cuarto orden cae 24 dB por octava: a dos octavas del
 * corte tiene que haber unos -48 dB, y en la banda de paso, cero. Si estos
 * numeros no salen, el filtro no es lo que dice ser.
 */
class TransitionFilterTest {

    private val sampleRate = 48_000
    private val formato = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)

    /** RMS en dB de un tono estereo tras atravesar el filtro, medido despues del calentamiento. */
    private fun nivelDb(filtro: TransitionFilterAudioProcessor, hz: Double): Double {
        // Dos segundos: el primero deja que el corte se deslice hasta su objetivo y
        // que la memoria del filtro se asiente; se mide el segundo.
        val total = sampleRate * 2
        val entrada = ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until total) {
            val v = (sin(2 * PI * hz * i / sampleRate) * 20_000).toInt().toShort()
            entrada.putShort(v); entrada.putShort(v)
        }
        entrada.flip()

        var suma = 0.0
        var n = 0
        var frame = 0
        while (entrada.hasRemaining()) {
            val trozo = entrada.slice().order(ByteOrder.nativeOrder())
            trozo.limit(minOf(trozo.remaining(), 4096 * 4))
            entrada.position(entrada.position() + trozo.limit())
            filtro.queueInput(trozo)
            val salida = filtro.output.order(ByteOrder.nativeOrder())
            while (salida.remaining() >= 4) {
                val l = salida.short.toDouble(); salida.short
                if (frame >= sampleRate) { suma += l * l; n++ }
                frame++
            }
        }
        val rms = sqrt(suma / n)
        return 20 * log10(rms / 20_000 * sqrt(2.0))
    }

    private fun filtroConfigurado(lp: Float, hp: Float): TransitionFilterAudioProcessor {
        val f = TransitionFilterAudioProcessor()
        f.setCutoffs(lp, hp)
        f.configure(formato)
        f.flush()
        return f
    }

    @Test
    fun aparcado_es_una_copia_exacta() {
        val f = filtroConfigurado(TransitionFilterAudioProcessor.OPEN_HZ, TransitionFilterAudioProcessor.OFF_HZ)
        val bytes = ByteArray(4096) { (it * 37 % 251 - 125).toByte() }
        val entrada = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        entrada.flip()
        f.queueInput(entrada)
        val salida = f.output
        val out = ByteArray(salida.remaining()).also { salida.get(it) }
        assertTrue("aparcado tiene que dejar el buffer intacto", bytes.contentEquals(out))
    }

    @Test
    fun paso_bajo_a_1kHz_cae_24dB_por_octava() {
        val f = filtroConfigurado(1_000f, TransitionFilterAudioProcessor.OFF_HZ)
        val banda = nivelDb(f, 100.0)
        val dosOctavas = nivelDb(f, 4_000.0)
        assertEquals("100 Hz en la banda de paso", 0.0, banda, 0.5)
        // Cuarto orden: 24 dB/oct -> ~-48 dB a dos octavas. Se admite +-4 dB por
        // el redondeo a 16 bits y la transicion del Butterworth cerca del codo.
        assertTrue("4 kHz tiene que caer unos -48 dB, cayo $dosOctavas", dosOctavas < -44.0 && dosOctavas > -52.0)
    }

    @Test
    fun paso_alto_a_200Hz_corta_los_graves_y_deja_el_resto() {
        val f = filtroConfigurado(TransitionFilterAudioProcessor.OPEN_HZ, 200f)
        val grave = nivelDb(f, 50.0)
        val medio = nivelDb(f, 2_000.0)
        assertEquals("2 kHz en la banda de paso", 0.0, medio, 0.5)
        assertTrue("50 Hz tiene que caer unos -48 dB, cayo $grave", grave < -44.0 && grave > -52.0)
    }

    @Test
    fun paso_bajo_y_paso_alto_juntos_dejan_el_medio() {
        val f = filtroConfigurado(4_000f, 200f)
        assertEquals("1 kHz pasa entre los dos", 0.0, nivelDb(f, 1_000.0), 0.6)
        assertTrue("50 Hz cae", nivelDb(f, 50.0) < -40.0)
        assertTrue("16 kHz cae", nivelDb(f, 16_000.0) < -40.0)
    }
}
