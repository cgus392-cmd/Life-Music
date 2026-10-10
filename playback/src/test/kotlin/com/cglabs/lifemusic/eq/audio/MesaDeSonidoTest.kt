package com.cglabs.lifemusic.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * La mesa de sonido del carro: que la curva diga la verdad, que en plano no
 * toque nada, y lo que pidio CG: subir suena mas fuerte (no se resta volumen)
 * y aun asi nunca satura.
 */
class MesaDeSonidoTest {

    private val formato = AudioProcessor.AudioFormat(SR, 2, C.ENCODING_PCM_16BIT)

    @After
    fun dejarPlana() {
        MesaDeSonido.aplicar(AjusteMesa())
    }

    @Test
    fun la_curva_marca_la_ganancia_de_cada_banda() {
        for (i in 0 until MesaDeSonido.BANDAS - 1) {
            val bandas = List(MesaDeSonido.BANDAS) { if (it == i) 6f else 0f }
            val ajuste = AjusteMesa(activo = true, bandas = bandas)
            val filtros = MesaDeSonido.filtros(ajuste, SR.toDouble())
            val db = MesaDeSonido.respuestaDb(ajuste, MesaDeSonido.FRECUENCIAS[i], filtros, SR.toDouble())
            assertEquals("banda $i", 6.0, db, 0.05)
        }
    }

    @Test
    fun los_estantes_suben_lo_que_dicen_lejos_del_corte() {
        val ajuste = AjusteMesa(activo = true, graves = 8f, agudos = -6f)
        val filtros = MesaDeSonido.filtros(ajuste, SR.toDouble())
        assertEquals(8.0, MesaDeSonido.respuestaDb(ajuste, 20.0, filtros, SR.toDouble()), 0.5)
        assertEquals(-6.0, MesaDeSonido.respuestaDb(ajuste, 20_000.0, filtros, SR.toDouble()), 0.5)
        assertEquals(0.0, MesaDeSonido.respuestaDb(ajuste, 1_000.0, filtros, SR.toDouble()), 0.5)
    }

    @Test
    fun en_plano_sale_lo_mismo_que_entra() {
        MesaDeSonido.aplicar(AjusteMesa(activo = true))
        val entrada = seno(1_000.0, 0.25, SR)
        val salida = procesar(entrada)
        for (i in entrada.indices) assertEquals(entrada[i].toFloat(), salida[i].toFloat(), 1f)
    }

    @Test
    fun apagada_pasa_tal_cual() {
        MesaDeSonido.aplicar(AjusteMesa(activo = false, graves = 12f))
        val entrada = seno(100.0, 0.5, SR)
        val salida = procesar(entrada)
        assertTrue(entrada.contentEquals(salida))
    }

    @Test
    fun subir_graves_suena_mas_fuerte() {
        MesaDeSonido.aplicar(AjusteMesa(activo = true, graves = 12f))
        val entrada = seno(60.0, 0.1, SR)
        val salida = procesar(entrada)
        // +12 dB de estante a 60 Hz: casi x4. Se pide al menos x3 (+9,5 dB).
        assertTrue(rmsFinal(salida) > rmsFinal(entrada) * 3.0)
    }

    @Test
    fun subir_mucho_nunca_satura_ni_baja_el_volumen() {
        MesaDeSonido.aplicar(AjusteMesa(activo = true, ganancia = 12f, graves = 12f))
        val entrada = seno(80.0, 0.9, SR)
        val salida = procesar(entrada)
        val mitad = salida.size / 2
        val pico = (mitad until salida.size).maxOf { abs(salida[it].toInt()) }
        // El limitador deja la senal a -0,5 dBFS: ni un recorte.
        assertTrue("pico $pico", pico <= (MesaDeSonidoAudioProcessor.TECHO * 32767).toInt() + 2)
        // Y lo que sale no suena mas bajito que lo que entro.
        assertTrue(rmsFinal(salida) >= rmsFinal(entrada))
    }

    @Test
    fun en_el_telefono_la_cadena_la_salta() {
        val mesa = MesaDeSonidoAudioProcessor(habilitado = false)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, mesa.configure(formato))
        assertFalse(mesa.isActive)
    }

    @Test
    fun el_recorte_suave_no_toca_lo_que_esta_bajo_el_techo() {
        assertEquals(0.5, MesaDeSonidoAudioProcessor.recorteSuave(0.5), 0.0)
        assertEquals(-0.94, MesaDeSonidoAudioProcessor.recorteSuave(-0.94), 0.0)
        assertTrue(MesaDeSonidoAudioProcessor.recorteSuave(5.0) <= 1.0)
        assertTrue(MesaDeSonidoAudioProcessor.recorteSuave(-5.0) >= -1.0)
    }

    // --- Ayudas ---

    /** Un segundo de seno estereo (izquierdo = derecho), intercalado. */
    private fun seno(hz: Double, amplitud: Double, sr: Int): ShortArray {
        val cuadros = sr
        return ShortArray(cuadros * 2) { i ->
            val n = i / 2
            (amplitud * 32767 * sin(2 * PI * hz * n / sr)).toInt().toShort()
        }
    }

    /** Pasa el audio por la mesa en bloques de 1024 cuadros, como hace ExoPlayer. */
    private fun procesar(entrada: ShortArray): ShortArray {
        val mesa = MesaDeSonidoAudioProcessor(habilitado = true)
        mesa.configure(formato)
        mesa.flush()
        val salida = ShortArray(entrada.size)
        var hecho = 0
        val bloque = 1024 * 2
        var escrito = 0
        while (hecho < entrada.size) {
            val n = minOf(bloque, entrada.size - hecho)
            val buf = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
            for (i in 0 until n) buf.putShort(entrada[hecho + i])
            buf.flip()
            mesa.queueInput(buf)
            val out = mesa.output
            while (out.hasRemaining()) salida[escrito++] = out.short
            hecho += n
        }
        return salida
    }

    /** RMS de la segunda mitad (ya pasado el fundido de entrada y el ataque). */
    private fun rmsFinal(x: ShortArray): Double {
        val desde = x.size / 2
        var suma = 0.0
        for (i in desde until x.size) suma += x[i].toDouble() * x[i]
        return sqrt(suma / (x.size - desde))
    }

    private companion object {
        const val SR = 48_000
    }
}
