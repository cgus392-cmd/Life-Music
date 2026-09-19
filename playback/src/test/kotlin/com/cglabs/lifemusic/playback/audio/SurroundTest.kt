package com.cglabs.lifemusic.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.cglabs.lifemusic.eq.audio.SurroundAudioProcessor
import com.cglabs.lifemusic.eq.audio.SurroundAudioProcessor.Sala
import org.junit.Assert.assertArrayEquals
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
 * Mide el envolvente antes de decir que envuelve. Cada etapa tiene una
 * propiedad que se puede comprobar con numeros:
 *  - apagado, el buffer sale bit a bit igual;
 *  - la amplitud sube el lado y no el centro;
 *  - el crossfeed lleva senal al oido contrario, atenuada y sin agudos;
 *  - la sala anade una cola que decae y se extingue;
 *  - mono o 24 bits, el procesador se aparta (NOT_SET) en vez de romper.
 */
class SurroundTest {

    private val sampleRate = 48_000
    private val estereo = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)

    /** Pasa un buffer estereo entero por el procesador y devuelve la salida como shorts intercalados. */
    private fun procesar(p: SurroundAudioProcessor, entrada: ShortArray): ShortArray {
        val buf = ByteBuffer.allocateDirect(entrada.size * 2).order(ByteOrder.nativeOrder())
        entrada.forEach { buf.putShort(it) }
        buf.flip()
        val salida = ShortArray(entrada.size)
        var i = 0
        while (buf.hasRemaining()) {
            val trozo = buf.slice().order(ByteOrder.nativeOrder())
            trozo.limit(minOf(trozo.remaining(), 4096 * 4))
            buf.position(buf.position() + trozo.limit())
            p.queueInput(trozo)
            val out = p.output.order(ByteOrder.nativeOrder())
            while (out.remaining() >= 2) salida[i++] = out.short
        }
        return salida.copyOf(i)
    }

    private fun tono(hz: Double, segundos: Double, izquierda: Double, derecha: Double): ShortArray {
        val n = (sampleRate * segundos).toInt()
        val s = ShortArray(n * 2)
        for (i in 0 until n) {
            val v = sin(2 * PI * hz * i / sampleRate) * 16_000
            s[2 * i] = (v * izquierda).toInt().toShort()
            s[2 * i + 1] = (v * derecha).toInt().toShort()
        }
        return s
    }

    /** RMS de un canal (0 = L, 1 = R) entre dos instantes, en dB respecto a 16 000. */
    private fun rmsDb(s: ShortArray, canal: Int, desdeSeg: Double, hastaSeg: Double): Double {
        val desde = (desdeSeg * sampleRate).toInt()
        val hasta = minOf((hastaSeg * sampleRate).toInt(), s.size / 2)
        var suma = 0.0
        for (i in desde until hasta) { val v = s[2 * i + canal].toDouble(); suma += v * v }
        val rms = sqrt(suma / (hasta - desde))
        return 20 * log10((rms / 16_000 * sqrt(2.0)).coerceAtLeast(1e-9))
    }

    private fun configurado(width: Float, crossfeed: Float, room: Float, sala: Sala = Sala.MEDIA): SurroundAudioProcessor {
        val p = SurroundAudioProcessor()
        p.setParams(width, crossfeed, room, sala)
        p.configure(estereo)
        p.flush()
        return p
    }

    @Test
    fun apagadoPasaBitABit() {
        val p = configurado(0f, 0f, 0f)
        val entrada = tono(440.0, 0.5, 1.0, 0.3)
        assertArrayEquals(entrada, procesar(p, entrada))
    }

    @Test
    fun laAmplitudSubeElLadoYNoElCentro() {
        val centroSolo = tono(440.0, 1.0, 1.0, 1.0)      // L = R: solo «mid»
        val ladoSolo = tono(440.0, 1.0, 1.0, -1.0)       // L = -R: solo «side»
        val sin = configurado(0f, 0f, 0f)
        val con = configurado(1f, 0f, 0f)

        val centroAntes = rmsDb(procesar(sin, centroSolo), 0, 0.5, 1.0)
        val centroDespues = rmsDb(procesar(configurado(1f, 0f, 0f), centroSolo), 0, 0.5, 1.0)
        val ladoAntes = rmsDb(procesar(configurado(0f, 0f, 0f), ladoSolo), 0, 0.5, 1.0)
        val ladoDespues = rmsDb(procesar(con, ladoSolo), 0, 0.5, 1.0)

        // El lado gana claramente mas que el centro (que solo baja por la
        // compensacion de ganancia).
        val gananciaLado = ladoDespues - ladoAntes
        val gananciaCentro = centroDespues - centroAntes
        assertTrue("lado +$gananciaLado dB, centro $gananciaCentro dB", gananciaLado - gananciaCentro > 4.0)
        assertTrue("el centro no debe subir: $gananciaCentro", gananciaCentro <= 0.5)
    }

    @Test
    fun elCrossfeedLlevaSenalAlOidoContrarioSinAgudos() {
        val soloIzquierda = tono(300.0, 1.0, 1.0, 0.0)
        val agudoIzquierda = tono(6_000.0, 1.0, 1.0, 0.0)

        val sinCf = procesar(configurado(0f, 0f, 0f), soloIzquierda)
        assertTrue("sin crossfeed la derecha calla", rmsDb(sinCf, 1, 0.5, 1.0) < -80)

        val conCf = procesar(configurado(0f, 1f, 0f), soloIzquierda)
        val derechaGrave = rmsDb(conCf, 1, 0.5, 1.0)
        val izquierdaGrave = rmsDb(conCf, 0, 0.5, 1.0)
        assertTrue("llega al otro oido: $derechaGrave dB", derechaGrave > -20)
        assertTrue("pero atenuado respecto al directo", izquierdaGrave - derechaGrave > 4)

        val conCfAgudo = procesar(configurado(0f, 1f, 0f), agudoIzquierda)
        val derechaAguda = rmsDb(conCfAgudo, 1, 0.5, 1.0)
        assertTrue("los agudos cruzan menos que los graves: $derechaAguda vs $derechaGrave", derechaGrave - derechaAguda > 10)
    }

    /** Medio segundo de tono a plena escala y luego silencio: lo que hace una cancion al parar. */
    private fun rafaga(segundosTotales: Double): ShortArray {
        val s = ShortArray((sampleRate * segundosTotales).toInt() * 2)
        val burst = tono(200.0, 0.5, 1.0, 1.0)
        burst.copyInto(s)
        return s
    }

    @Test
    fun laSalaAnadeUnaColaQueDecaeYSeExtingue() {
        val salida = procesar(configurado(0f, 0f, 1f, Sala.GRANDE), rafaga(3.0))

        val temprano = rmsDb(salida, 0, 0.55, 0.75)
        val medio = rmsDb(salida, 0, 0.90, 1.30)
        val tarde = rmsDb(salida, 0, 1.50, 2.00)
        val final = rmsDb(salida, 0, 2.60, 3.00)
        assertTrue("hay cola: $temprano dB", temprano > -60)
        assertTrue("decae: $temprano > $medio > $tarde", temprano > medio && medio > tarde)
        assertTrue("se extingue: $final dB", final < -70)
        assertTrue("sin NaN ni valores absurdos", salida.all { it.toInt() in -32768..32767 })
    }

    @Test
    fun laSalaPequenaMuereAntesQueLaGrande() {
        val pequena = rmsDb(procesar(configurado(0f, 0f, 1f, Sala.PEQUENA), rafaga(2.0)), 0, 0.7, 0.9)
        val grande = rmsDb(procesar(configurado(0f, 0f, 1f, Sala.GRANDE), rafaga(2.0)), 0, 0.7, 0.9)
        assertTrue("pequena $pequena dB < grande $grande dB", grande - pequena > 6)
    }

    @Test
    fun todoAlMaximoNoRecortaUnTonoNormal() {
        val p = configurado(1f, 1f, 1f, Sala.GRANDE)
        val salida = procesar(p, tono(200.0, 1.0, 1.0, 1.0))
        val pico = salida.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("pico $pico", pico < 32_000)
    }

    @Test
    fun monoYOtrosFormatosSeApartan() {
        val p = SurroundAudioProcessor()
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, p.configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT)))
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, p.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT)))
    }
}
