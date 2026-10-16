package com.cglabs.lifemusic.lyrics

import com.cglabs.lifemusic.lyrics.AlineadorDeLetras.Reconocida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlineadorDeLetrasTest {

    @Test
    fun normaliza_sin_acentos_ni_signos() {
        assertEquals("corazon", AlineadorDeLetras.normalizar("¡Corazón!"))
        assertEquals("nino", AlineadorDeLetras.normalizar("Niño,"))
    }

    @Test
    fun junta_tokens_bpe_en_palabras_con_el_tiempo_del_primero() {
        val tokens = arrayOf("▁la", "▁ma", "re", "a", "▁al", "ta")
        val tiempos = floatArrayOf(0.2f, 0.5f, 0.6f, 0.7f, 1.1f, 1.3f)
        val palabras = AlineadorDeLetras.palabrasReconocidas(tokens, tiempos, desde = 10.0)
        assertEquals(listOf("la", "marea", "alta"), palabras.map { it.texto })
        assertEquals(10.2, palabras[0].inicio, 1e-6)
        assertEquals(10.5, palabras[1].inicio, 1e-6)
        assertEquals(11.1, palabras[2].inicio, 1e-6)
    }

    @Test
    fun alinea_igual_cuando_todo_se_reconocio() {
        val letra = listOf("Y", "me", "quedo", "en", "la", "marea")
        val oidas = listOf(
            Reconocida("y", 10.0), Reconocida("me", 10.3), Reconocida("quedo", 10.5),
            Reconocida("en", 11.0), Reconocida("la", 11.2), Reconocida("marea", 11.4),
        )
        val r = AlineadorDeLetras.alinear(letra, oidas, 9.9, 13.0)
        assertTrue(r.all { it.calzada })
        assertEquals(listOf(10.0, 10.3, 10.5, 11.0, 11.2, 11.4), r.map { it.inicio })
    }

    @Test
    fun tolera_errores_del_reconocedor_y_reparte_lo_que_falta() {
        // Oyo «quero» por «quedo» (se calza por parecido) y no oyo «en» ni «la».
        val letra = listOf("Y", "me", "quedo", "en", "la", "marea")
        val oidas = listOf(Reconocida("y", 10.0), Reconocida("me", 10.3), Reconocida("quero", 10.5), Reconocida("marea", 11.6))
        val r = AlineadorDeLetras.alinear(letra, oidas, 9.9, 13.0)
        assertTrue(r[2].calzada)
        assertFalse(r[3].calzada)
        assertFalse(r[4].calzada)
        // Las no calzadas quedan entre «quedo» y «marea», en orden.
        assertTrue(r[3].inicio > r[2].inicio && r[4].inicio > r[3].inicio && r[4].inicio < r[5].inicio)
        assertEquals(11.6, r[5].inicio, 1e-6)
    }

    @Test
    fun sin_nada_reconocido_reparte_toda_la_linea_por_silabas() {
        val letra = listOf("hola", "corazon")
        val r = AlineadorDeLetras.alinear(letra, emptyList(), 20.0, 24.0)
        assertEquals(20.0, r[0].inicio, 1e-6)
        // «hola» tiene 2 silabas de 5 en total: «corazon» empieza al 40 % de la linea.
        assertEquals(21.6, r[1].inicio, 1e-6)
        assertEquals(0.0, AlineadorDeLetras.cobertura(r), 1e-6)
    }

    @Test
    fun descarta_anclas_que_retroceden_en_el_tiempo() {
        val letra = listOf("uno", "dos", "tres")
        val oidas = listOf(Reconocida("uno", 5.0), Reconocida("dos", 4.0), Reconocida("tres", 6.0))
        val r = AlineadorDeLetras.alinear(letra, oidas, 4.5, 8.0)
        assertTrue(r[0].calzada)
        assertFalse(r[1].calzada)
        assertTrue(r.zipWithNext().all { (a, b) -> b.inicio > a.inicio })
    }

    @Test
    fun la_suelta_del_comienzo_queda_antes_de_la_primera_calzada_aunque_esta_adelante_la_linea() {
        // Caso real de «Hazme Olvidarla»: «llena» sono 0,3 s antes del tiempo del LRC y «Y» no se oyo.
        val letra = listOf("Y", "llena", "mis", "sentidos")
        val oidas = listOf(Reconocida("llena", 18.8), Reconocida("mis", 20.23), Reconocida("sentidos", 20.55))
        val r = AlineadorDeLetras.alinear(letra, oidas, 19.1, 24.7)
        assertFalse(r[0].calzada)
        assertTrue(r.zipWithNext().all { (a, b) -> b.inicio > a.inicio })
        assertEquals(18.68, r[0].inicio, 1e-6)
    }

    @Test
    fun escribe_lrc_mejorado() {
        val linea = listOf(AlineadorDeLetras.Alineada("Hola", 61.5, true), AlineadorDeLetras.Alineada("amor", 62.04, true))
        assertEquals("[01:01.50]<01:01.50>Hola <01:02.04>amor", AlineadorDeLetras.aLrcMejorado(61.5, linea))
    }
}
