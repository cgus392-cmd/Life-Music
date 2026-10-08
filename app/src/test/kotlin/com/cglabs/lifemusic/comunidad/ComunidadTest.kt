package com.cglabs.lifemusic.comunidad

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comunidad (1.3.1): cuando se pregunta la calificacion, que valida el
 * formulario del prerregistro y como se lee el estado que manda el servidor.
 */
class ComunidadTest {
    private val dia = 86_400_000L
    private val ahora = 1_800_000_000_000L

    private fun pregunta(
        diasInstalada: Int = 30,
        canciones: Int = 100,
        versionActual: Int = 15,
        versionPreguntada: Int = 14,
        diasDesdeAhoraNo: Int? = null,
        yaCalifico: Boolean = false,
    ) = Calificacion.debePreguntar(
        ahora = ahora,
        instaladaEn = ahora - diasInstalada * dia,
        canciones = canciones,
        versionActual = versionActual,
        versionPreguntada = versionPreguntada,
        ahoraNoEn = diasDesdeAhoraNo?.let { ahora - it * dia } ?: 0L,
        yaCalifico = yaCalifico,
    )

    @Test
    fun pregunta_a_quien_ya_conoce_la_app() {
        assertTrue(pregunta())
    }

    @Test
    fun no_pregunta_antes_de_7_dias_ni_de_20_canciones() {
        assertFalse(pregunta(diasInstalada = 6))
        assertTrue(pregunta(diasInstalada = 7))
        assertFalse(pregunta(canciones = 19))
        assertTrue(pregunta(canciones = 20))
    }

    @Test
    fun una_vez_por_version() {
        assertFalse(pregunta(versionPreguntada = 15))
    }

    @Test
    fun ahora_no_aparta_60_dias() {
        assertFalse(pregunta(diasDesdeAhoraNo = 10))
        assertFalse(pregunta(diasDesdeAhoraNo = 59))
        assertTrue(pregunta(diasDesdeAhoraNo = 60))
    }

    @Test
    fun quien_ya_califico_no_vuelve_a_ver_la_pregunta() {
        assertFalse(pregunta(yaCalifico = true))
    }

    @Test
    fun el_formulario_pide_lo_mismo_que_el_servidor() {
        assertNull(Prerregistro.validar("Camilo", "camilo@ejemplo.com", mayor = true, acepta = true))
        assertEquals(Prerregistro.Falta.NOMBRE, Prerregistro.validar("C", "a@b.co", true, true))
        assertEquals(Prerregistro.Falta.NOMBRE, Prerregistro.validar("x".repeat(61), "a@b.co", true, true))
        assertEquals(Prerregistro.Falta.CORREO, Prerregistro.validar("Camilo", "sin-arroba", true, true))
        assertEquals(Prerregistro.Falta.CORREO, Prerregistro.validar("Camilo", "a@b", true, true))
        assertEquals(Prerregistro.Falta.EDAD, Prerregistro.validar("Camilo", "a@b.co", false, true))
        assertEquals(Prerregistro.Falta.BASES, Prerregistro.validar("Camilo", "a@b.co", true, false))
    }

    @Test
    fun lee_el_estado_del_servidor_sin_nombres_ni_correos() {
        val j = JSONObject(
            """{"empezo":true,"abierto":true,"alargado":false,"inicio":"2026-10-12T05:00:00.000Z",
               "fin":"2026-10-18T05:00:00.000Z","meta":100,"contador":37,"metaAlcanzada":false,
               "premio":"JBL PartyBox 330","video":"https://lifemusic.pages.dev/concurso/video.mp4"}""",
        )
        val e = ComunidadApi.leerEstado(j)
        assertTrue(e.empezo && e.abierto && !e.alargado)
        assertEquals(37, e.contador)
        assertEquals(100, e.meta)
        assertEquals("JBL PartyBox 330", e.premio)
        assertEquals("https://lifemusic.pages.dev/concurso/video.mp4", e.video)
        assertEquals("2026-10-18T05:00:00.000Z", e.fin)
    }

    @Test
    fun sin_fecha_no_hay_copa() {
        val e = ComunidadApi.leerEstado(JSONObject("""{"empezo":false,"abierto":false,"fin":null,"meta":100,"contador":0}"""))
        assertNull(e.fin)
        assertFalse(Prerregistro.copaVisible(e))
        assertFalse(Prerregistro.copaVisible(null))
        assertTrue(Prerregistro.copaVisible(e.copy(empezo = true, abierto = true)))
    }
}
