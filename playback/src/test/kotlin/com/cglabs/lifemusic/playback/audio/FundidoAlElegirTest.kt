package com.cglabs.lifemusic.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El fundido al elegir otra cancion (1.3.1): que las curvas empiecen y acaben
 * donde deben, que nunca vayan hacia atras, y que solo haya fundido cuando hay
 * algo sonando que bajar.
 */
class FundidoAlElegirTest {

    @Test
    fun bajar_va_de_entero_a_cero() {
        assertEquals(1f, FundidoAlElegir.bajada(0f), 1e-6f)
        assertEquals(0f, FundidoAlElegir.bajada(1f), 1e-6f)
    }

    @Test
    fun subir_va_de_cero_a_entero() {
        assertEquals(0f, FundidoAlElegir.subida(0f), 1e-6f)
        assertEquals(1f, FundidoAlElegir.subida(1f), 1e-6f)
    }

    @Test
    fun las_curvas_nunca_van_hacia_atras() {
        var antesBajada = 1f
        var antesSubida = 0f
        for (i in 0..100) {
            val p = i / 100f
            val b = FundidoAlElegir.bajada(p)
            val s = FundidoAlElegir.subida(p)
            assertTrue("bajar subio en $p", b <= antesBajada + 1e-6f)
            assertTrue("subir bajo en $p", s >= antesSubida - 1e-6f)
            antesBajada = b
            antesSubida = s
        }
    }

    @Test
    fun fuera_de_rango_se_queda_en_los_extremos() {
        assertEquals(1f, FundidoAlElegir.bajada(-0.5f), 1e-6f)
        assertEquals(0f, FundidoAlElegir.bajada(1.5f), 1e-6f)
        assertEquals(0f, FundidoAlElegir.subida(-0.5f), 1e-6f)
        assertEquals(1f, FundidoAlElegir.subida(1.5f), 1e-6f)
    }

    @Test
    fun los_pasos_son_finos_y_nunca_cero() {
        assertTrue(FundidoAlElegir.pasos(FundidoAlElegir.BAJAR_MS) >= 20)
        assertTrue(FundidoAlElegir.pasos(FundidoAlElegir.SUBIR_MS) >= 30)
        assertEquals(1, FundidoAlElegir.pasos(0))
    }

    @Test
    fun el_fundido_entero_dura_un_segundo() {
        assertEquals(1000L, FundidoAlElegir.BAJAR_MS + FundidoAlElegir.SUBIR_MS)
    }

    private fun aplica(
        activo: Boolean = true,
        sonando: Boolean = true,
        transmitiendo: Boolean = false,
        enTransicion: Boolean = false,
        silenciado: Boolean = false,
        playWhenReady: Boolean = true,
    ) = FundidoAlElegir.aplica(activo, sonando, transmitiendo, enTransicion, silenciado, playWhenReady)

    @Test
    fun funde_cuando_suena_algo_y_eliges_otra() {
        assertTrue(aplica())
    }

    @Test
    fun cada_caso_de_la_ficha_queda_sin_fundido() {
        assertFalse("interruptor apagado", aplica(activo = false))
        assertFalse("no sonaba nada", aplica(sonando = false))
        assertFalse("suena el TV", aplica(transmitiendo = true))
        assertFalse("Automix en marcha", aplica(enTransicion = true))
        assertFalse("silencio de la app", aplica(silenciado = true))
        assertFalse("la nueva no va a sonar", aplica(playWhenReady = false))
    }
}
