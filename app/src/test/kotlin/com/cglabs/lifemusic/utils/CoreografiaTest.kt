package com.cglabs.lifemusic.utils

import com.cglabs.lifemusic.ui.player.tocadiscos.Coreografia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.1.0 · El tocadiscos: que la coreografía empiece y termine en la carátula de siempre, y que el giro tenga inercia. */
class CoreografiaTest {
    private val golpeMs = 600f // 100 bpm, como la maqueta

    @Test
    fun enReposoTodoEstaEnLaCaratula() {
        for (b in listOf(0f, Coreografia.ENTRA, 1.9f)) {
            assertEquals(0f, Coreografia.claves(Coreografia.FUNDA_A, b), 0.001f)
            assertEquals(0f, Coreografia.claves(Coreografia.VINILO_A, b), 0.001f)
            assertEquals(0f, Coreografia.adornos(0f), 0.001f)
        }
        // Al final la funda nueva cubre su disco justo donde estaba la carátula.
        assertEquals(0f, Coreografia.claves(Coreografia.FUNDA_B, Coreografia.FIN), 0.001f)
        assertEquals(0f, Coreografia.claves(Coreografia.VINILO_B, Coreografia.FIN), 0.001f)
        assertEquals(0f, Coreografia.adornos(Coreografia.FIN), 0.001f)
    }

    @Test
    fun losFotogramasClaveSeRespetan() {
        assertEquals(-70f, Coreografia.claves(Coreografia.FUNDA_A, 3.4f), 0.001f)
        assertEquals(-320f, Coreografia.claves(Coreografia.FUNDA_A, 7f), 0.001f)
        assertEquals(320f, Coreografia.claves(Coreografia.FUNDA_B, 12f), 0.001f)
        // Entre dos claves la curva es suave: a mitad de camino va a mitad.
        assertEquals(-35f, Coreografia.claves(Coreografia.FUNDA_A, 2.7f), 0.01f)
    }

    @Test
    fun elGiroArrancaYFrenaConInercia() {
        // Quieto antes de arrancar y al final; nunca gira hacia atrás.
        assertEquals(0f, Coreografia.giroA(3.5f, golpeMs), 0.0001f)
        var antes = 0f
        var b = 0f
        while (b <= Coreografia.FIN) {
            val ahora = Coreografia.giroA(b, golpeMs)
            assertTrue("el disco A giró hacia atrás en el golpe $b", ahora >= antes - 1e-4f)
            antes = ahora
            b += 0.05f
        }
        assertEquals(Coreografia.giroA(11f, golpeMs), Coreografia.giroA(15f, golpeMs), 0.0001f)
        // En pleno giro va a 33⅓ rpm: 200 grados por segundo.
        val porGolpe = Coreografia.giroA(7.1f, golpeMs) - Coreografia.giroA(7f, golpeMs)
        val gradosPorSegundo = Math.toDegrees(porGolpe.toDouble()) / (0.1 * golpeMs / 1000.0)
        assertEquals(200.0, gradosPorSegundo, 1.0)
    }

    @Test
    fun losCuadrosQuietosSiempreSumanUno() {
        var b = 0f
        while (b <= Coreografia.FIN) {
            assertEquals(1f, Coreografia.cuadrosQuietos(b, golpeMs).sum(), 0.001f)
            b += 0.1f
        }
        assertEquals(1f, Coreografia.cuadrosQuietos(0f, golpeMs)[0], 0.001f)
        assertEquals(1f, Coreografia.cuadrosQuietos(Coreografia.FIN, golpeMs)[3], 0.001f)
    }
}
