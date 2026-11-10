package com.cglabs.lifemusic.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * 2.1.0 · El detector de tempo con ritmos sintéticos. El caso que lo motivó: «No Me Quiero Casar» (dembow, 99,4 BPM)
 * salía en 131,7 porque la caja del dembow (3+3+2 semicorcheas) repite cada tres cuartos de golpe. Se prueba que el
 * dembow ya no engañe y que los demás ritmos sigan bien.
 */
class BeatAnalyzerTempoTest {
    private val frameRate = 44_100f / 512f

    /** Flujo de onsets de un patrón de 16 semicorcheas: (semicorchea, fuerza). */
    private fun flujo(bpm: Float, patron: List<Pair<Int, Float>>, segundos: Float = 18f): FloatArray {
        val n = (segundos * frameRate).toInt()
        val f = FloatArray(n)
        val semi = 60f / bpm / 4f
        var t = 0f
        while (t < segundos) {
            for ((k, peso) in patron) {
                val i = ((t + k * semi) * frameRate).toInt()
                if (i < n) f[i] += peso
            }
            t += 16 * semi
        }
        val nucleo = floatArrayOf(0.3f, 1f, 0.5f, 0.2f)
        val salida = FloatArray(n)
        for (i in 0 until n) for (j in nucleo.indices) {
            val k = i + j - 1
            if (k in 0 until n) salida[i] += f[k] * nucleo[j]
        }
        val azar = Random(3)
        for (i in 0 until n) salida[i] += (azar.nextFloat() * 0.03f)
        return salida
    }

    private fun tempo(flux: FloatArray): Float {
        val (periodo, _) = BeatAnalyzer.estimateTempoPeriod(flux, frameRate)!!
        var bpm = 60f * frameRate / periodo
        while (bpm < 70f) bpm *= 2f
        while (bpm >= 140f) bpm /= 2f
        return bpm
    }

    private val dembow = listOf(0, 4, 8, 12).map { it to 1f } + listOf(3, 6, 11, 14).map { it to 0.9f } +
        (0 until 16 step 2).map { it to 0.25f }
    private val vallenato = listOf(0 to 1f, 6 to 0.8f, 8 to 1f, 14 to 0.8f) + (0 until 16 step 2).map { it to 0.4f }
    private val house = listOf(0, 4, 8, 12).map { it to 1f } + listOf(2, 6, 10, 14).map { it to 0.5f }
    private val pop = listOf(0 to 1f, 8 to 1f, 4 to 0.9f, 12 to 0.9f) + (0 until 16 step 2).map { it to 0.3f }

    @Test
    fun el_dembow_ya_no_se_lee_a_cuatro_tercios() {
        assertEquals(99.4f, tempo(flujo(99.4f, dembow)), 1.5f)
        assertEquals(92f, tempo(flujo(92f, dembow)), 1.5f)
        assertEquals(104f, tempo(flujo(104f, dembow)), 1.5f)
    }

    @Test
    fun los_demas_ritmos_siguen_bien() {
        assertEquals(99.4f, tempo(flujo(99.4f, vallenato)), 1.5f)
        assertEquals(110f, tempo(flujo(110f, vallenato)), 1.5f)
        assertEquals(124f, tempo(flujo(124f, house)), 1.5f)
        assertEquals(90f, tempo(flujo(90f, pop)), 1.5f)
        assertEquals(135f, tempo(flujo(135f, pop)), 2f)
    }
}
