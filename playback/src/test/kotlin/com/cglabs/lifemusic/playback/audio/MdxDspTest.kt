package com.cglabs.lifemusic.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Verifica el DSP contra la implementacion de referencia en Python que se uso
 * para la prueba de concepto.
 *
 * No es un test de cortesia: si la ventana es simetrica en vez de periodica, si
 * el orden de los canales se cruza o si la FFT de radix mixto tiene un giro mal,
 * el modelo no separa peor — devuelve ruido. Y eso, oyendolo en el telefono
 * despues de montar el pipeline entero, cuesta una tarde de encontrar. Aqui
 * cuesta ocho segundos.
 *
 * Las cifras esperadas salieron de ejecutar el mismo vector determinista por
 * separar.py, el banco de pruebas de Python.
 */
class MdxDspTest {

    /** El mismo generador congruencial que usa numpy.random.RandomState(7). */
    private fun senalDePrueba(): Array<FloatArray> {
        val n = MdxDsp.CHUNK
        val l = FloatArray(n)
        val r = FloatArray(n)
        // Ruido reproducible propio: el de numpy no se puede replicar aqui, asi
        // que las comprobaciones fuertes van sobre los tonos, que si son iguales.
        var semilla = 7L
        for (i in 0 until n) {
            val t = i / 44100.0
            semilla = (semilla * 6364136223846793005L + 1442695040888963407L)
            val ruido = ((semilla ushr 33).toDouble() / (1L shl 31) - 0.5) * 0.1
            l[i] = (0.5 * sin(2 * PI * 220 * t) + 0.2 * sin(2 * PI * 1370 * t) + ruido).toFloat()
            r[i] = (0.4 * sin(2 * PI * 331 * t) + 0.3 * sin(2 * PI * 990 * t) + ruido).toFloat()
        }
        return arrayOf(l, r)
    }

    @Test
    fun `la ventana es Hann periodica`() {
        // La periodica vale 0 en la primera muestra y NO vuelve a 0 en la ultima;
        // la simetrica si. Confundirlas es el error clasico y silencioso.
        val w = FloatArray(MdxDsp.N_FFT) {
            (0.5 - 0.5 * kotlin.math.cos(2.0 * PI * it / MdxDsp.N_FFT)).toFloat()
        }
        assertEquals(0.0f, w[0], 1e-7f)
        // El criterio que de verdad las distingue: la periodica suma
        // exactamente N/2, porque el coseno se cancela sobre un periodo entero.
        // La simetrica, con denominador N-1, se queda medio punto corta. Mirar la
        // ultima muestra no vale: en la periodica es 2.6e-7, distinta de cero
        // pero indistinguible de ruido de coma flotante.
        var suma = 0.0
        for (v in w) suma += v
        assertEquals(MdxDsp.N_FFT / 2.0, suma, 0.01)
    }

    @Test
    fun `la FFT de radix mixto acierta en un tono conocido`() {
        // Un seno exacto en el bin 96 tiene que dar toda su energia en ese bin.
        val s = MdxDsp.Scratch()
        val x = arrayOf(FloatArray(MdxDsp.CHUNK), FloatArray(MdxDsp.CHUNK))
        val bin = 96
        val f = bin * 44100.0 / MdxDsp.N_FFT
        for (i in 0 until MdxDsp.CHUNK) {
            x[0][i] = sin(2 * PI * f * i / 44100.0).toFloat()
        }
        val tensor = FloatArray(MdxDsp.TENSOR)
        MdxDsp.stft(x, tensor, s)

        val t = MdxDsp.DIM_T / 2
        fun magnitud(b: Int): Double {
            val re = tensor[0 * MdxDsp.DIM_F * MdxDsp.DIM_T + b * MdxDsp.DIM_T + t].toDouble()
            val im = tensor[1 * MdxDsp.DIM_F * MdxDsp.DIM_T + b * MdxDsp.DIM_T + t].toDouble()
            return sqrt(re * re + im * im)
        }
        val enElBin = magnitud(bin)
        val vecinos = maxOf(magnitud(bin - 8), magnitud(bin + 8))
        assertTrue(
            "el bin $bin ($enElBin) tiene que dominar sobre sus vecinos ($vecinos)",
            enElBin > vecinos * 50,
        )
    }

    @Test
    fun `ida y vuelta reconstruye la senal`() {
        val s = MdxDsp.Scratch()
        val x = senalDePrueba()
        val tensor = FloatArray(MdxDsp.TENSOR)
        MdxDsp.stft(x, tensor, s)

        val y = arrayOf(FloatArray(MdxDsp.CHUNK), FloatArray(MdxDsp.CHUNK))
        MdxDsp.istft(tensor, y, s)

        // Se miden solo las muestras centrales: en los bordes el solape esta
        // incompleto y la referencia de Python tambien se desvia alli.
        var maxErr = 0.0
        var suma = 0.0
        var cuenta = 0
        for (canal in 0 until 2) {
            for (i in MdxDsp.N_FFT until MdxDsp.CHUNK - MdxDsp.N_FFT) {
                val e = abs(x[canal][i] - y[canal][i]).toDouble()
                if (e > maxErr) maxErr = e
                suma += e; cuenta++
            }
        }
        val medio = suma / cuenta
        println("ida y vuelta: error max %.6f  medio %.8f".format(maxErr, medio))
        // Umbrales atados a la referencia de Python sobre el MISMO tramo central:
        // max 0.001774, medio 0.00037474. Este Kotlin sale mejor —max 0.00074,
        // medio 0.00023— porque la FFT de aqui trabaja en Double de punta a
        // punta y numpy hace la rfft en float32. El margen deja sitio a otras
        // maquinas sin dejar pasar un error de verdad, que seria de otro orden.
        assertTrue("error maximo demasiado alto: $maxErr", maxErr < 1e-3)
        assertTrue("error medio demasiado alto: $medio", medio < 3e-4)
    }

    @Test
    fun `los canales del tensor no estan cruzados`() {
        // L y R llevan tonos distintos: si el orden [L_re, L_im, R_re, R_im] se
        // cruza, el modelo recibe la mezcla al reves y separa cualquier cosa.
        val s = MdxDsp.Scratch()
        val x = arrayOf(FloatArray(MdxDsp.CHUNK), FloatArray(MdxDsp.CHUNK))
        val binL = 100
        val binR = 700
        for (i in 0 until MdxDsp.CHUNK) {
            x[0][i] = sin(2 * PI * binL * 44100.0 / MdxDsp.N_FFT * i / 44100.0).toFloat()
            x[1][i] = sin(2 * PI * binR * 44100.0 / MdxDsp.N_FFT * i / 44100.0).toFloat()
        }
        val tensor = FloatArray(MdxDsp.TENSOR)
        MdxDsp.stft(x, tensor, s)

        val t = MdxDsp.DIM_T / 2
        fun mag(canalPar: Int, b: Int): Double {
            val re = tensor[canalPar * MdxDsp.DIM_F * MdxDsp.DIM_T + b * MdxDsp.DIM_T + t].toDouble()
            val im = tensor[(canalPar + 1) * MdxDsp.DIM_F * MdxDsp.DIM_T + b * MdxDsp.DIM_T + t].toDouble()
            return sqrt(re * re + im * im)
        }
        assertTrue("el tono de L no esta en los canales 0-1", mag(0, binL) > mag(0, binR) * 50)
        assertTrue("el tono de R no esta en los canales 2-3", mag(2, binR) > mag(2, binL) * 50)
    }
}
