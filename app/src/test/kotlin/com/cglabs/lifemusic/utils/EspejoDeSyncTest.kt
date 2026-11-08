package com.cglabs.lifemusic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 2.0.2 · Simulaciones de la sincronizacion con YouTube: que no se pierdan canciones de la app
 * (lo que reportaron los usuarios) y que lo quitado en YouTube si se quite.
 */
class EspejoDeSyncTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun sincronizar(remotos: Set<String>) = EspejoDeSync(carpeta.root, "me_gusta", remotos)

    private fun ids(rango: IntRange) = rango.map { "c$it" }.toSet()

    @Test
    fun laPrimeraSincronizacionNoQuitaNada() {
        val espejo = sincronizar(setOf("a", "b"))
        assertFalse(espejo.sePuedeQuitar("solo_en_la_app"))
        assertTrue(espejo.nuncaSubio("solo_en_la_app"))
        espejo.guardar()
    }

    @Test
    fun loQueSeQuitoEnYouTubeSeQuitaEnLaApp() {
        sincronizar(setOf("a", "b", "c")).guardar()
        val segunda = sincronizar(setOf("a", "b"))
        assertTrue(segunda.sePuedeQuitar("c"))
        assertFalse(segunda.nuncaSubio("c"))
    }

    @Test
    fun loQueNuncaLlegoAYouTubeSeQueda() {
        sincronizar(setOf("a", "b")).guardar()
        val segunda = sincronizar(setOf("a", "b"))
        assertFalse(segunda.sePuedeQuitar("nueva_en_la_app"))
        assertTrue(segunda.nuncaSubio("nueva_en_la_app"))
    }

    @Test
    fun loQueSigueEnYouTubeNoSeQuita() {
        sincronizar(setOf("a", "b")).guardar()
        assertFalse(sincronizar(setOf("a", "b")).sePuedeQuitar("a"))
    }

    @Test
    fun unaRespuestaIncompletaNoQuitaNadaNiSeGuarda() {
        sincronizar(ids(1..100)).guardar()
        // YouTube respondio a medias: 30 de 100.
        val cortada = sincronizar(ids(1..30))
        assertFalse(cortada.sePuedeQuitar("c50"))
        assertFalse(cortada.sePuedeQuitar("c99"))
        cortada.guardar()
        // La siguiente, completa, se compara con las 100 de antes y no con las 30.
        val completa = sincronizar(ids(1..99))
        assertTrue(completa.sePuedeQuitar("c100"))
        assertFalse(completa.sePuedeQuitar("c50"))
    }

    @Test
    fun quitarMenosDeLaMitadSiSeAcepta() {
        sincronizar(ids(1..100)).guardar()
        val espejo = sincronizar(ids(1..60))
        assertTrue(espejo.sePuedeQuitar("c61"))
    }

    @Test
    fun conPocasCancionesSeRespetaLoQueDigaYouTube() {
        sincronizar(ids(1..5)).guardar()
        assertTrue(sincronizar(ids(1..1)).sePuedeQuitar("c5"))
    }

    @Test
    fun unIdVacioNuncaSeQuita() {
        sincronizar(setOf("a")).guardar()
        assertFalse(sincronizar(emptySet()).sePuedeQuitar(null))
    }

    @Test
    fun cadaTipoTieneSuPropioRecuerdo() {
        EspejoDeSync(carpeta.root, "biblioteca", setOf("x")).guardar()
        val meGusta = sincronizar(emptySet())
        assertFalse(meGusta.sePuedeQuitar("x"))
    }

    @Test
    fun listasSospechosas() {
        assertTrue(listaRemotaSospechosa(remotas = 0, locales = 3))
        assertTrue(listaRemotaSospechosa(remotas = 4, locales = 20))
        assertFalse(listaRemotaSospechosa(remotas = 15, locales = 20))
        assertFalse(listaRemotaSospechosa(remotas = 2, locales = 5))
        assertFalse(listaRemotaSospechosa(remotas = 0, locales = 0))
        assertEquals(false, listaRemotaSospechosa(remotas = 12, locales = 12))
    }
}
