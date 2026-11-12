package com.cglabs.lifemusic.playback.audio

import com.cglabs.lifemusic.playback.audio.ReglaDeRadio.Perfil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El Auto como la radio: lo que prometen la investigación y CG (11-11), en casos concretos. */
class ReglaDeRadioTest {

    @Test
    fun la_mezcla_dura_lo_menor_entre_la_cola_y_la_intro() {
        assertEquals(6_000L, ReglaDeRadio.superposicion(colaMs = 6_000L, introMs = 9_000L, perfil = Perfil.RITMICO))
        assertEquals(5_000L, ReglaDeRadio.superposicion(colaMs = 20_000L, introMs = 5_000L, perfil = Perfil.RITMICO))
    }

    @Test
    fun termina_fuerte_o_canta_de_una_y_queda_el_minimo() {
        assertEquals(ReglaDeRadio.MINIMO_MS, ReglaDeRadio.superposicion(0L, 9_000L, Perfil.RITMICO))
        assertEquals(ReglaDeRadio.MINIMO_MS, ReglaDeRadio.superposicion(9_000L, 0L, Perfil.SUAVE))
    }

    @Test
    fun nunca_pasa_del_tope_y_el_suave_es_mas_corto() {
        assertEquals(ReglaDeRadio.TOPE_RITMICO_MS, ReglaDeRadio.superposicion(30_000L, 25_000L, Perfil.RITMICO))
        assertEquals(ReglaDeRadio.TOPE_SUAVE_MS, ReglaDeRadio.superposicion(30_000L, 25_000L, Perfil.SUAVE))
    }

    @Test
    fun sin_analisis_es_un_fundido_corto() {
        assertEquals(ReglaDeRadio.SIN_MARCAS_MS, ReglaDeRadio.superposicion(null, 9_000L, Perfil.RITMICO))
        assertEquals(ReglaDeRadio.SIN_MARCAS_MS, ReglaDeRadio.superposicion(9_000L, null, Perfil.SUAVE))
    }

    @Test
    fun cola_e_intro_salen_de_las_marcas() {
        assertEquals(12_000L, ReglaDeRadio.cola(finDeMusicaMs = 200_000L, empiezaElOutroMs = 188_000L))
        assertEquals(0L, ReglaDeRadio.cola(200_000L, null))
        assertEquals(0L, ReglaDeRadio.cola(200_000L, -1L))
        assertNull(ReglaDeRadio.cola(-1L, 188_000L))
        assertEquals(7_500L, ReglaDeRadio.intro(analizada = true, empiezaASonarMs = 500L, entraConFuerzaMs = 8_000L))
        assertEquals(0L, ReglaDeRadio.intro(true, 500L, -1L))
        assertNull(ReglaDeRadio.intro(false, 500L, 8_000L))
    }

    @Test
    fun la_mezcla_termina_donde_se_acaba_la_musica_ajustada_al_compas() {
        // 100 BPM: golpe de 600 ms, compás de 2,4 s, primer golpe en 0.
        val fin = 180_000L
        val t = ReglaDeRadio.arranque(fin, superposicionMs = 6_000L, primerGolpeMs = 0L, golpeMs = 600.0)
        assertEquals(0L, t % 2_400L)
        assertTrue("termina cerca del final de la música", kotlin.math.abs((fin - t) - 6_000L) <= 1_200L)
        // Sin rejilla, exacto.
        assertEquals(174_000L, ReglaDeRadio.arranque(fin, 6_000L, 0L, null))
    }

    @Test
    fun el_ajuste_al_compas_nunca_deja_menos_del_minimo() {
        // 60 BPM: compás de 4 s. Con 2,5 s el compás más cercano podría quedar a 0 s del final.
        // Con el final en 101 s, el compás más cercano a 98,5 s es el de 100 s: quedaría 1 s. Se toma el anterior.
        val fin = 101_000L
        val t = ReglaDeRadio.arranque(fin, ReglaDeRadio.MINIMO_MS, primerGolpeMs = 0L, golpeMs = 1_000.0)
        assertEquals(96_000L, t)
        assertTrue(fin - t >= ReglaDeRadio.MINIMO_MS)
    }

    @Test
    fun se_estira_solo_bajo_el_cinco_por_ciento_con_golpe_y_cuatro_compases() {
        val golpe = 600.0 // 100 BPM: 4 compases son 9,6 s
        assertTrue(ReglaDeRadio.alGolpe(0.04f, Perfil.RITMICO, 10_000L, golpe))
        assertFalse("mezcla corta", ReglaDeRadio.alGolpe(0.04f, Perfil.RITMICO, 5_000L, golpe))
        assertFalse("más del 5 %", ReglaDeRadio.alGolpe(0.07f, Perfil.RITMICO, 12_000L, golpe))
        assertFalse("sin golpe claro", ReglaDeRadio.alGolpe(0.01f, Perfil.SUAVE, 12_000L, golpe))
        assertTrue("casi el mismo tempo vale aunque sea corta", ReglaDeRadio.alGolpe(0.01f, Perfil.RITMICO, 3_000L, golpe))
    }

    @Test
    fun el_perfil_sale_de_las_confianzas_y_decide_el_estilo() {
        assertEquals(Perfil.RITMICO, ReglaDeRadio.perfil(0.95f, 0.92f))
        assertEquals(Perfil.SUAVE, ReglaDeRadio.perfil(0.95f, 0.55f))
        assertEquals(Perfil.SUAVE, ReglaDeRadio.perfil(0.95f, null))
        assertEquals(EstiloTransicion.BLEND, ReglaDeRadio.estilo(Perfil.RITMICO, alGolpe = true))
        assertEquals(EstiloTransicion.FILTRO, ReglaDeRadio.estilo(Perfil.RITMICO, alGolpe = false))
        assertEquals(EstiloTransicion.PLANO, ReglaDeRadio.estilo(Perfil.SUAVE, alGolpe = false))
    }
}
