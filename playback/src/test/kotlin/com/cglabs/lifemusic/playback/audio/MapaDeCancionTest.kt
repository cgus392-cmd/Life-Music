package com.cglabs.lifemusic.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 2.1.x · El mapa de la canción con una canción sintética de 32 compases a 100 BPM: 8 de intro (solo un colchón de
 * acordes), 16 con batería y bajo, y 8 de outro (otra vez solo el colchón). El mapa tiene que encontrar la intro y el
 * outro, una ENTRADA donde arranca la batería y una SALIDA donde se va.
 */
class MapaDeCancionTest {
    // 44,1 kHz como el audio real: a esa frecuencia el paso de 50 ms es más largo que la ventana de análisis.
    private val sr = 44_100
    private val bpm = 100f
    private val golpe = 60.0 / bpm
    private val compas = 4 * golpe

    private fun cancion(): FloatArray {
        val total = (32 * compas * sr).toInt()
        val s = FloatArray(total)
        val azar = Random(5)
        for (i in 0 until total) {
            val t = i.toDouble() / sr
            val bar = (t / compas).toInt()
            var v = 0.12 * (sin(2 * PI * 220 * t) + sin(2 * PI * 277.2 * t) + sin(2 * PI * 329.6 * t))
            if (bar in 8 until 24) {
                val enGolpe = t % golpe
                val enCorchea = t % (golpe / 2)
                v += 0.6 * sin(2 * PI * 55 * enGolpe) * exp(-enGolpe / 0.12) // bombo en cada golpe
                v += 0.25 * (azar.nextDouble() * 2 - 1) * exp(-enCorchea / 0.02) // hi-hat en cada corchea
                v += 0.3 * sin(2 * PI * 55 * t) // bajo
            }
            s[i] = v.toFloat()
        }
        return s
    }

    private fun mapa(): MapaDeCancion {
        val c = ConstructorDeMapa(sr)
        val audio = cancion()
        var i = 0
        while (i < audio.size) {
            val n = minOf(4_096, audio.size - i)
            c.alimentar(audio.copyOfRange(i, i + n))
            i += n
        }
        return c.terminar(bpm, 0L, (audio.size * 1000L) / sr)!!
    }

    @Test
    fun encuentra_la_intro_el_outro_y_los_puntos_de_mezcla() {
        val m = mapa()
        assertTrue("al menos 31 compases: ${m.compases.size}", m.compases.size >= 31)
        assertEquals(TipoSeccion.INTRO, m.secciones.first().tipo)
        assertEquals(TipoSeccion.OUTRO, m.secciones.last().tipo)
        assertTrue("la intro termina en el compás 8: ${m.secciones}", abs(m.secciones.first().hasta - 8) <= 1)
        assertTrue("el outro empieza en el compás 24: ${m.secciones}", abs(m.secciones.last().desde - 24) <= 1)

        val entrada = m.puntos.firstOrNull { it.tipo == TipoPunto.ENTRADA }
        assertNotNull("tiene que proponer dónde entrar: ${m.puntos}", entrada)
        assertTrue("entra donde arranca la batería: $entrada", abs(entrada!!.compas - 8) <= 1)
        val salida = m.puntos.firstOrNull { it.tipo == TipoPunto.SALIDA }
        assertNotNull("tiene que proponer dónde salir: ${m.puntos}", salida)
        assertTrue("sale donde se va la batería: $salida", abs(salida!!.compas - 24) <= 1)

        // La batería de la parte del medio pesa mucho más que la de la intro.
        assertTrue(m.compases[12].bateria > m.compases[3].bateria * 3)
        assertTrue(m.compases[12].graves > m.compases[3].graves * 3)
    }

    @Test
    fun se_guarda_y_se_lee_igual() {
        val m = mapa()
        val archivo = File.createTempFile("mapa", ".bin")
        try {
            m.guardar(archivo)
            val leido = MapaDeCancion.leer(archivo)!!
            assertEquals(m.compases.size, leido.compases.size)
            assertEquals(m.puntos, leido.puntos)
            assertEquals(m.secciones, leido.secciones)
            assertEquals(m.onda.size, leido.onda.size)
            assertEquals(m.bpm, leido.bpm, 0f)
        } finally {
            archivo.delete()
        }
    }
}
