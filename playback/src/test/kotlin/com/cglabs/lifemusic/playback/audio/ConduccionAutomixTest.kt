package com.cglabs.lifemusic.playback.audio

import com.cglabs.lifemusic.eq.audio.TransitionFilterAudioProcessor.Companion.OFF_HZ
import com.cglabs.lifemusic.eq.audio.TransitionFilterAudioProcessor.Companion.OPEN_HZ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Las curvas son matematica pura; aqui se comprueba que hacen lo que su
 * documentacion promete en los puntos que importan: donde empiezan, donde
 * acaban y en que orden pasan las cosas.
 */
class ConduccionAutomixTest {

    @Test
    fun plano_deja_los_cuatro_cortes_abiertos() {
        for (p in listOf(0f, 0.3f, 0.7f, 1f)) {
            assertEquals(CortesDeFiltro.ABIERTOS, ConduccionAutomix.cortes(EstiloTransicion.PLANO, p))
        }
    }

    @Test
    fun deslizar_es_geometrico() {
        assertEquals(100.0, ConduccionAutomix.deslizar(100.0, 10_000.0, 0.0), 1e-9)
        assertEquals(10_000.0, ConduccionAutomix.deslizar(100.0, 10_000.0, 1.0), 1e-9)
        // El punto medio geometrico de 100 y 10 000 es 1000, no 5050.
        assertEquals(sqrt(100.0 * 10_000.0), ConduccionAutomix.deslizar(100.0, 10_000.0, 0.5), 1e-6)
    }

    @Test
    fun barrido_arranca_en_la_entrada_y_se_asienta_en_el_suelo() {
        val inicio = ConduccionAutomix.cortes(EstiloTransicion.FILTRO, 0f)
        val fin = ConduccionAutomix.cortes(EstiloTransicion.FILTRO, 1f)
        assertEquals(ConduccionAutomix.ENTRADA_FILTRO_HZ.toFloat(), inicio.salienteLowPassHz, 1f)
        assertEquals(ConduccionAutomix.SUELO_FILTRO_HZ.toFloat(), fin.salienteLowPassHz, 1f)
        // La saliente nunca pierde los graves en un barrido: el paso alto queda apagado.
        assertEquals(OFF_HZ, inicio.salienteHighPassHz, 0f)
        assertEquals(OFF_HZ, fin.salienteHighPassHz, 0f)
    }

    @Test
    fun barrido_adelanta_la_curva() {
        // FORMA_BARRIDO < 1: a un cuarto del fundido ya se ha recorrido mas de un
        // cuarto del camino (en escala logaritmica) hacia el suelo.
        val cuarto = ConduccionAutomix.cortes(EstiloTransicion.FILTRO, 0.25f).salienteLowPassHz
        val lineal = ConduccionAutomix.deslizar(ConduccionAutomix.ENTRADA_FILTRO_HZ, ConduccionAutomix.SUELO_FILTRO_HZ, 0.25)
        assertTrue("a 0,25 el corte ($cuarto) tiene que ir por delante del lineal ($lineal)", cuarto < lineal)
    }

    @Test
    fun barrido_la_entrante_entra_pasada_por_alto_y_esta_entera_a_tiempo() {
        val inicio = ConduccionAutomix.cortes(EstiloTransicion.FILTRO, 0f)
        val abierta = ConduccionAutomix.cortes(EstiloTransicion.FILTRO, ConduccionAutomix.ENTRADA_ABIERTA_EN.toFloat())
        assertEquals(ConduccionAutomix.ENTRADA_PASO_ALTO_HZ.toFloat(), inicio.entranteHighPassHz, 1f)
        assertEquals(OFF_HZ, abierta.entranteHighPassHz, 0.5f)
        assertEquals(OPEN_HZ, inicio.entranteLowPassHz, 0f)
    }

    @Test
    fun blend_los_graves_cambian_de_mano_una_vez_y_en_el_sitio() {
        val antes = ConduccionAutomix.cortes(EstiloTransicion.BLEND, 0.5f)
        val despues = ConduccionAutomix.cortes(EstiloTransicion.BLEND, 0.9f)
        // Antes del cambio: la saliente conserva los graves, la entrante los tiene levantados.
        assertEquals(OFF_HZ, antes.salienteHighPassHz, 0.5f)
        assertTrue("entrante sin graves antes del cambio", antes.entranteHighPassHz >= ConduccionAutomix.CAMBIO_GRAVES_HZ.toFloat() - 1f)
        // Despues: al reves.
        assertEquals(ConduccionAutomix.CAMBIO_GRAVES_HZ.toFloat(), despues.salienteHighPassHz, 1f)
        assertEquals(OFF_HZ, despues.entranteHighPassHz, 0.5f)
    }

    @Test
    fun cierre_baja_hasta_el_suelo_de_cierre_y_deja_la_entrante_en_paz() {
        val inicio = ConduccionAutomix.cortes(EstiloTransicion.CIERRE, 0f)
        val fin = ConduccionAutomix.cortes(EstiloTransicion.CIERRE, 1f)
        assertEquals(ConduccionAutomix.ENTRADA_FILTRO_HZ.toFloat(), inicio.salienteLowPassHz, 1f)
        assertEquals(ConduccionAutomix.SUELO_CIERRE_HZ.toFloat(), fin.salienteLowPassHz, 1f)
        assertTrue("el cierre baja mas que el barrido", fin.salienteLowPassHz < ConduccionAutomix.SUELO_FILTRO_HZ)
        // La entrante espera en pausa: sus cortes quedan abiertos de principio a fin.
        for (p in listOf(0f, 0.5f, 1f)) {
            val c = ConduccionAutomix.cortes(EstiloTransicion.CIERRE, p)
            assertEquals(OPEN_HZ, c.entranteLowPassHz, 0f)
            assertEquals(OFF_HZ, c.entranteHighPassHz, 0f)
        }
    }

    @Test
    fun ganancia_de_cierre_entera_hasta_la_mitad_y_cero_al_final() {
        assertEquals(1f, ConduccionAutomix.gananciaDeCierre(0f), 1e-6f)
        assertEquals(1f, ConduccionAutomix.gananciaDeCierre(ConduccionAutomix.CIERRE_GANANCIA_DESDE.toFloat()), 1e-6f)
        assertEquals(0f, ConduccionAutomix.gananciaDeCierre(1f), 1e-6f)
        // Monotona: nunca vuelve a subir.
        var previa = 1f
        for (i in 0..100) {
            val g = ConduccionAutomix.gananciaDeCierre(i / 100f)
            assertTrue("la ganancia sube en $i", g <= previa + 1e-6f)
            previa = g
        }
    }

    @Test
    fun entrada_de_cierre_calla_hasta_su_punto_y_esta_entera_al_final() {
        assertEquals(0f, ConduccionAutomix.entradaDeCierre(0f, 0.5f), 1e-6f)
        assertEquals(0f, ConduccionAutomix.entradaDeCierre(0.49f, 0.5f), 1e-6f)
        assertEquals(1f, ConduccionAutomix.entradaDeCierre(1f, 0.5f), 1e-6f)
        // A tres cuartos del cierre, con entrada a la mitad, va por sin(45°).
        assertEquals(sqrt(0.5).toFloat(), ConduccionAutomix.entradaDeCierre(0.75f, 0.5f), 1e-5f)
        // Con 0 entra desde el principio; con 1 espera al final sin dividir por cero.
        assertTrue(ConduccionAutomix.entradaDeCierre(0.1f, 0f) > 0f)
        assertEquals(0f, ConduccionAutomix.entradaDeCierre(0.99f, 1f), 0f)
        assertEquals(1f, ConduccionAutomix.entradaDeCierre(1f, 1f), 0f)
    }

    @Test
    fun blend_el_paso_bajo_de_salida_es_suave_y_tardio() {
        val temprano = ConduccionAutomix.cortes(EstiloTransicion.BLEND, ConduccionAutomix.BLEND_SALIDA_DESDE.toFloat() - 0.05f)
        val fin = ConduccionAutomix.cortes(EstiloTransicion.BLEND, 1f)
        assertEquals("antes de BLEND_SALIDA_DESDE no se toca", OPEN_HZ, temprano.salienteLowPassHz, 1f)
        assertEquals(ConduccionAutomix.BLEND_SALIDA_PASO_BAJO_HZ.toFloat(), fin.salienteLowPassHz, 1f)
        assertTrue("nunca baja al suelo del barrido", fin.salienteLowPassHz > ConduccionAutomix.SUELO_FILTRO_HZ * 3)
    }
}
