package com.cglabs.lifemusic.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Donde acaba la musica de verdad, medido sobre envolventes sinteticas que
 * imitan colas de distintos generos. Bloques de 500 ms; la ventana de cola
 * empieza en [inicio] y la pista dura [duracion].
 *
 * El fallo que esto cubre: un mambo de salsa o la coda de un vallenato son
 * 30 segundos mas flojos que el cuerpo, seguidos de fade y silencio. Medidos
 * contra si mismos parecian «fuertes hasta el final», el plan caia a los
 * ultimos 16 beats del fichero, y el filtro actuaba sobre nada.
 */
class ColaDePistaTest {

    private val bloque = 500L
    private val cuerpo = 1.0f

    /** Envolvente de N bloques a partir de tramos (bloques, nivel relativo al cuerpo). */
    private fun envolvente(vararg tramos: Pair<Int, Float>): FloatArray =
        tramos.flatMap { (n, nivel) -> List(n) { nivel * cuerpo } }.toFloatArray()

    @Test
    fun salsa_mambo_largo_fade_y_silencio() {
        // 60 s de ventana: 20 s de cuerpo, 25 s de mambo al 35 %, 10 s de fade, 5 s de silencio.
        val env = envolvente(40 to 1.0f, 50 to 0.35f, 20 to 0.06f, 10 to 0.0f)
        val inicio = 180_000L
        val duracion = inicio + env.size * bloque          // 240 s

        val fin = BeatAnalyzer.detectContentEnd(env, inicio, duracion, cuerpo)
        val salida = BeatAnalyzer.detectMixOut(env, inicio, duracion, cuerpo)

        assertNotNull("tiene que encontrar el fin de contenido", fin)
        // El fade al 6 % ya no es audible (umbral 10 %): la musica acaba al terminar el mambo.
        assertEquals(inicio + 90 * bloque, fin!!)
        // El mambo al 35 % es outro (menos de medio cuerpo): la salida es donde acaba el cuerpo,
        // sujeta al tope de 45 s antes del final.
        assertNotNull(salida)
        assertEquals(maxOf(inicio + 40 * bloque, duracion - 45_000L), salida!!)
        assertTrue("la transicion tiene que caber antes del fin de contenido", salida < fin)
    }

    @Test
    fun pop_fuerte_hasta_el_final_con_dos_segundos_de_silencio_digital() {
        val env = envolvente(116 to 1.0f, 4 to 0.0f)
        val inicio = 150_000L
        val duracion = inicio + env.size * bloque

        val fin = BeatAnalyzer.detectContentEnd(env, inicio, duracion, cuerpo)
        val salida = BeatAnalyzer.detectMixOut(env, inicio, duracion, cuerpo)

        assertEquals("acaba dos segundos antes del fichero", duracion - 2_000L, fin!!)
        // Fuerte casi hasta el final: no hay outro que cortar. El planificador usara
        // contentEnd - solape, que es lo correcto.
        assertNull(salida)
    }

    @Test
    fun outro_mas_largo_que_la_ventana_entra_al_principio_de_ella() {
        // Toda la ventana es outro flojo: la salida es el inicio de la ventana, con tope de 45 s.
        val env = envolvente(100 to 0.3f, 20 to 0.0f)
        val inicio = 200_000L
        val duracion = inicio + env.size * bloque

        val salida = BeatAnalyzer.detectMixOut(env, inicio, duracion, cuerpo)
        assertEquals(maxOf(inicio, duracion - 45_000L), salida!!)
    }

    @Test
    fun la_referencia_es_el_cuerpo_y_no_la_cola() {
        // El fallo original: una cola floja medida contra su propio percentil parece fuerte.
        // Con la referencia del cuerpo, un outro al 30 % es outro aunque sea todo lo que hay.
        val env = envolvente(120 to 0.3f)
        val inicio = 200_000L
        val duracion = inicio + env.size * bloque
        val salida = BeatAnalyzer.detectMixOut(env, inicio, duracion, cuerpo)
        assertNotNull("un outro al 30 % del cuerpo tiene que detectarse como outro", salida)
    }
}
