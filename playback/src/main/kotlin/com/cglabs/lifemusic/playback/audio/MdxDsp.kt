package com.cglabs.lifemusic.playback.audio

import kotlin.math.cos
import kotlin.math.sin

/**
 * El pre y post proceso que espera MDX-Net: espectrograma de ida y de vuelta.
 *
 * Cada constante de aqui va atada al modelo. Si una sola no coincide, la red no
 * devuelve una separacion peor: devuelve ruido. Estan verificadas contra la
 * implementacion de referencia en Python que se uso para la prueba de concepto.
 *
 *   n_fft 6144 · hop 1024 · 3072 bins de los 3073 · 256 tramas por bloque
 *   ventana Hann PERIODICA, no simetrica
 *   los 4 canales del tensor son [L_real, L_imag, R_real, R_imag]
 *
 * Sobre la FFT: **6144 no es potencia de dos** —es 2^11 * 3— asi que la radix-2
 * que ya tiene BeatAnalyzer no sirve. Aqui va Cooley-Tukey de radix mixto:
 * se parte la entrada en 3 subsecuencias por n mod 3, cada una de 2048 (eso si
 * es potencia de dos), y se recombinan con los giros de 6144. Es la diferencia
 * entre que esto funcione y que no exista.
 */
internal object MdxDsp {

    const val N_FFT = 6144
    const val HOP = 1024
    const val DIM_F = 3072
    const val DIM_T = 256
    const val BINS = N_FFT / 2 + 1          // 3073
    const val CHUNK = HOP * (DIM_T - 1)     // 261120 muestras = 5.92 s
    const val TENSOR = 4 * DIM_F * DIM_T

    private const val SUB = 2048            // 6144 = 3 * 2048
    private const val SUB_BITS = 11

    /** Hann periodica: 0.5 - 0.5*cos(2*pi*n/N). La simetrica daria otra cosa. */
    private val ventana = FloatArray(N_FFT) {
        (0.5 - 0.5 * cos(2.0 * Math.PI * it / N_FFT)).toFloat()
    }
    private val ventana2 = FloatArray(N_FFT) { ventana[it] * ventana[it] }

    // Giros de la recombinacion de radix 3, W^j con W = exp(-2i*pi/6144).
    private val giroRe = DoubleArray(N_FFT) { cos(-2.0 * Math.PI * it / N_FFT) }
    private val giroIm = DoubleArray(N_FFT) { sin(-2.0 * Math.PI * it / N_FFT) }

    // Giros de las tres FFT de 2048.
    private val subRe = DoubleArray(SUB / 2) { cos(-2.0 * Math.PI * it / SUB) }
    private val subIm = DoubleArray(SUB / 2) { sin(-2.0 * Math.PI * it / SUB) }

    private val inversion = IntArray(SUB).also { tabla ->
        for (i in 0 until SUB) {
            var v = i; var r = 0
            repeat(SUB_BITS) { r = (r shl 1) or (v and 1); v = v shr 1 }
            tabla[i] = r
        }
    }

    /**
     * Espacio de trabajo. Se crea una vez por hilo de separacion y se reutiliza:
     * un bloque son 512 transformadas, y reservar por transformada tiraria la
     * memoria y el recolector de basura al suelo.
     */
    class Scratch {
        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        val subR = Array(3) { DoubleArray(SUB) }
        val subI = Array(3) { DoubleArray(SUB) }
        val marco = FloatArray(N_FFT)
        val acumulado = FloatArray(2 * (CHUNK + N_FFT))
        val norma = FloatArray(CHUNK + N_FFT)
    }

    /** FFT compleja de 2048 en sitio, radix 2 iterativa. */
    private fun fft2048(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until SUB) {
            val j = inversion[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var largo = 2
        while (largo <= SUB) {
            val paso = SUB / largo
            var i = 0
            while (i < SUB) {
                var k = 0
                for (j in i until i + largo / 2) {
                    val wr = subRe[k]; val wi = subIm[k]
                    val j2 = j + largo / 2
                    val tr = wr * re[j2] - wi * im[j2]
                    val ti = wr * im[j2] + wi * re[j2]
                    re[j2] = re[j] - tr; im[j2] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += paso
                }
                i += largo
            }
            largo = largo shl 1
        }
    }

    /** FFT compleja de 6144 en sitio, por descomposicion 3 x 2048. */
    private fun fft6144(re: DoubleArray, im: DoubleArray, s: Scratch) {
        for (r in 0 until 3) {
            val dr = s.subR[r]; val di = s.subI[r]
            for (k in 0 until SUB) { dr[k] = re[3 * k + r]; di[k] = im[3 * k + r] }
            fft2048(dr, di)
        }
        for (j in 0 until N_FFT) {
            val m = j % SUB
            val g1r = giroRe[j]; val g1i = giroIm[j]
            val j2 = (2 * j) % N_FFT
            val g2r = giroRe[j2]; val g2i = giroIm[j2]
            val a1r = s.subR[1][m]; val a1i = s.subI[1][m]
            val a2r = s.subR[2][m]; val a2i = s.subI[2][m]
            re[j] = s.subR[0][m] + (g1r * a1r - g1i * a1i) + (g2r * a2r - g2i * a2i)
            im[j] = s.subI[0][m] + (g1r * a1i + g1i * a1r) + (g2r * a2i + g2i * a2r)
        }
    }

    /**
     * [estereo] de 2 x [CHUNK] -> tensor [4][DIM_F][DIM_T] aplanado.
     * Replica torch.stft(center=True): relleno reflejado de N_FFT/2 a cada lado.
     */
    fun stft(estereo: Array<FloatArray>, salida: FloatArray, s: Scratch) {
        val pad = N_FFT / 2
        for (canal in 0 until 2) {
            val x = estereo[canal]
            for (t in 0 until DIM_T) {
                val inicio = t * HOP - pad
                // Reflejo en los bordes, igual que numpy mode="reflect".
                for (i in 0 until N_FFT) {
                    var p = inicio + i
                    if (p < 0) p = -p
                    if (p >= CHUNK) p = 2 * CHUNK - 2 - p
                    s.marco[i] = if (p in 0 until CHUNK) x[p] else 0f
                }
                for (i in 0 until N_FFT) {
                    s.re[i] = (s.marco[i] * ventana[i]).toDouble()
                    s.im[i] = 0.0
                }
                fft6144(s.re, s.im, s)
                val baseRe = (canal * 2) * DIM_F * DIM_T
                val baseIm = (canal * 2 + 1) * DIM_F * DIM_T
                for (f in 0 until DIM_F) {
                    salida[baseRe + f * DIM_T + t] = s.re[f].toFloat()
                    salida[baseIm + f * DIM_T + t] = s.im[f].toFloat()
                }
            }
        }
    }

    /** Tensor [4][DIM_F][DIM_T] -> 2 x [CHUNK] por solape y suma. */
    fun istft(tensor: FloatArray, salida: Array<FloatArray>, s: Scratch) {
        val pad = N_FFT / 2
        val largoAcum = CHUNK + N_FFT
        java.util.Arrays.fill(s.acumulado, 0f)
        java.util.Arrays.fill(s.norma, 0f)

        for (canal in 0 until 2) {
            val baseRe = (canal * 2) * DIM_F * DIM_T
            val baseIm = (canal * 2 + 1) * DIM_F * DIM_T
            val desplace = canal * largoAcum
            for (t in 0 until DIM_T) {
                // Espectro completo por simetria hermitica; el bin 3072 va a cero,
                // que es justo lo que el modelo recorta y no devuelve.
                for (f in 0 until N_FFT) { s.re[f] = 0.0; s.im[f] = 0.0 }
                for (f in 0 until DIM_F) {
                    val vr = tensor[baseRe + f * DIM_T + t].toDouble()
                    val vi = tensor[baseIm + f * DIM_T + t].toDouble()
                    s.re[f] = vr; s.im[f] = vi
                    if (f in 1 until BINS - 1) {
                        s.re[N_FFT - f] = vr; s.im[N_FFT - f] = -vi
                    }
                }
                // ifft(X) = conj(fft(conj(X))) / N
                for (f in 0 until N_FFT) s.im[f] = -s.im[f]
                fft6144(s.re, s.im, s)
                val inicio = t * HOP
                for (i in 0 until N_FFT) {
                    val v = (s.re[i] / N_FFT).toFloat() * ventana[i]
                    s.acumulado[desplace + inicio + i] += v
                    if (canal == 0) s.norma[inicio + i] += ventana2[i]
                }
            }
        }
        for (i in 0 until largoAcum) if (s.norma[i] < 1e-8f) s.norma[i] = 1e-8f
        for (canal in 0 until 2) {
            val desplace = canal * largoAcum
            val dst = salida[canal]
            for (i in 0 until CHUNK) dst[i] = s.acumulado[desplace + pad + i] / s.norma[pad + i]
        }
    }
}
