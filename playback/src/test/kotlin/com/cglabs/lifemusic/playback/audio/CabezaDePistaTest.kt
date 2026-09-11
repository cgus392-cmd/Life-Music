package com.cglabs.lifemusic.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Donde empieza la musica de verdad, en la cabeza de la pista. Bloques de 500 ms.
 *
 * Lo que esto protege: en modo «cancion completa» la entrante arranca en el
 * primer bloque audible, no en el primer bloque FUERTE. Una intro de acordeon al
 * 30 % del cuerpo es la cancion; el silencio digital de la subida, no.
 */
class CabezaDePistaTest {

    private val bloque = 500L
    private val cuerpo = 1.0f

    private fun envolvente(vararg tramos: Pair<Int, Float>): FloatArray =
        tramos.flatMap { (n, nivel) -> List(n) { nivel * cuerpo } }.toFloatArray()

    @Test
    fun vallenato_silencio_digital_e_intro_floja() {
        // 1,5 s de silencio, 12 s de acordeon al 30 %, y entra el cuerpo.
        val env = envolvente(3 to 0.0f, 24 to 0.3f, 5 to 1.0f)
        val inicio = BeatAnalyzer.detectContentStart(env, cuerpo)
        // Se salta el silencio y NADA mas: la intro se queda.
        assertEquals(3 * bloque, inicio)
    }

    @Test
    fun empieza_fuerte_arranca_en_cero() {
        val env = envolvente(32 to 1.0f)
        assertEquals(0L, BeatAnalyzer.detectContentStart(env, cuerpo))
    }

    @Test
    fun fade_in_por_debajo_del_umbral_no_cuenta_como_musica() {
        // 2 s al 5 % (inaudible contra el cuerpo), luego al 20 %.
        val env = envolvente(4 to 0.05f, 28 to 0.2f)
        assertEquals(4 * bloque, BeatAnalyzer.detectContentStart(env, cuerpo))
    }

    @Test
    fun cabeza_muda_no_da_arranque() {
        assertNull(BeatAnalyzer.detectContentStart(envolvente(32 to 0.0f), cuerpo))
        assertNull(BeatAnalyzer.detectContentStart(FloatArray(0), cuerpo))
    }
}
