package com.cglabs.lifemusic.karaoke

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spike del karaoke: separa la voz principal con UVR MDX-Net «KARA» (entrada y
 * salida [1, 4, 2048, 256]: estereo en real/imaginario, 2048 frecuencias, 256
 * instantes; n_fft 5120, salto 1024, ventana Hann, como el STFT de UVR). Procesa
 * por bloques de ~5,9 s leyendo y escribiendo a disco, para no cargar la cancion
 * entera en memoria. Deja la voz en mono a 44,1 kHz (float little-endian).
 *
 * Memoria: sin arena ni patrones de ONNX Runtime. En el spike, con ellos, la app
 * llego a 1,7 GB y Android la cerro; aqui cada bloque suelta lo suyo.
 */
class KaraokeSeparador(modelo: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val sesion = env.createSession(
        modelo.absolutePath,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
        },
    )
    private val entrada = sesion.inputNames.first()

    private val nFft = 5120
    private val salto = 1024
    private val dimF = 2048
    private val dimT = 256
    private val bloque = salto * (dimT - 1)       // 261.120 muestras por bloque
    private val recorte = nFft / 2                // 2560: se descarta en cada borde del bloque
    private val util = bloque - 2 * recorte       // 256.000 muestras utiles por bloque
    private val ventana = FloatArray(nFft) { (0.5 - 0.5 * cos(2 * PI * it / nFft)).toFloat() }
    private val fft = Fft5120()

    /**
     * [estereo]: el audio a 44,1 kHz estereo intercalado (float LE). Escribe en
     * [voz] la voz principal en mono. [invertir]: si el modelo da el acompañamiento,
     * la voz es la mezcla menos su salida.
     */
    fun separar(estereo: File, voz: File, invertir: Boolean = false, avance: (Float) -> Unit = {}): Long {
        val inicio = System.currentTimeMillis()
        RandomAccessFile(estereo, "r").use { lector ->
            val total = (lector.length() / 8).toInt() // muestras por canal
            DataOutputStream(BufferedOutputStream(FileOutputStream(voz), 1 shl 16)).use { salida ->
                var i = 0
                var bloques = 0
                while (i < total) {
                    // El bloque arranca [recorte] antes de lo util, con ceros fuera de la cancion.
                    val (l, r) = leer(lector, total, i - recorte, bloque)
                    val (vl, vr) = procesarBloque(l, r)
                    val n = minOf(util, total - i)
                    val buf = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
                    for (k in 0 until n) {
                        val idx = recorte + k
                        var m = 0.5f * (vl[idx] + vr[idx])
                        if (invertir) m = 0.5f * (l[idx] + r[idx]) - m
                        buf.putFloat(m)
                    }
                    salida.write(buf.array())
                    i += util
                    bloques++
                    avance((i.toFloat() / total).coerceAtMost(1f))
                }
                Log.i("LifeMusicKaraoke", "separacion: $bloques bloques en ${System.currentTimeMillis() - inicio} ms")
            }
        }
        return System.currentTimeMillis() - inicio
    }

    private fun leer(lector: RandomAccessFile, total: Int, desde: Int, cuantos: Int): Pair<FloatArray, FloatArray> {
        val l = FloatArray(cuantos)
        val r = FloatArray(cuantos)
        val a = desde.coerceAtLeast(0)
        val b = (desde + cuantos).coerceAtMost(total)
        if (b > a) {
            val bytes = ByteArray((b - a) * 8)
            lector.seek(a.toLong() * 8)
            lector.readFully(bytes)
            val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            for (k in 0 until (b - a)) {
                l[a - desde + k] = fb.get()
                r[a - desde + k] = fb.get()
            }
        }
        return l to r
    }

    /** STFT de los dos canales, el modelo y la ISTFT de su salida. */
    private fun procesarBloque(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
        val datos = FloatArray(4 * dimF * dimT)
        stft(l, datos, 0)
        stft(r, datos, 2)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(datos), longArrayOf(1, 4, dimF.toLong(), dimT.toLong()))
        val salida = tensor.use { t ->
            sesion.run(mapOf(entrada to t)).use { res ->
                val fb = (res[0] as OnnxTensor).floatBuffer
                FloatArray(fb.remaining()).also { fb.get(it) }
            }
        }
        return istft(salida, 0) to istft(salida, 2)
    }

    /** Canal [s] al tensor en los planos [plano] (real) y [plano]+1 (imaginario), indice ((c*F)+f)*T+t. */
    private fun stft(s: FloatArray, datos: FloatArray, plano: Int) {
        val re = FloatArray(nFft)
        val im = FloatArray(nFft)
        for (t in 0 until dimT) {
            // center=True con reflejo, como torch.stft.
            for (k in 0 until nFft) {
                var idx = t * salto + k - nFft / 2
                if (idx < 0) idx = -idx
                if (idx >= s.size) idx = 2 * (s.size - 1) - idx
                re[k] = s[idx.coerceIn(0, s.size - 1)] * ventana[k]
                im[k] = 0f
            }
            fft.transformar(re, im)
            for (f in 0 until dimF) {
                datos[((plano * dimF) + f) * dimT + t] = re[f]
                datos[(((plano + 1) * dimF) + f) * dimT + t] = im[f]
            }
        }
    }

    private fun istft(datos: FloatArray, plano: Int): FloatArray {
        val largo = nFft + salto * (dimT - 1)
        val suma = FloatArray(largo)
        val pesos = FloatArray(largo)
        val re = FloatArray(nFft)
        val im = FloatArray(nFft)
        for (t in 0 until dimT) {
            java.util.Arrays.fill(re, 0f)
            java.util.Arrays.fill(im, 0f)
            for (f in 0 until dimF) {
                re[f] = datos[((plano * dimF) + f) * dimT + t]
                im[f] = datos[(((plano + 1) * dimF) + f) * dimT + t]
            }
            // Simetria hermitica: la parte negativa es la conjugada (las frecuencias altas quedan en cero).
            for (f in 1 until nFft / 2) {
                re[nFft - f] = re[f]
                im[nFft - f] = -im[f]
            }
            // IFFT = conj(FFT(conj(x))) / N
            for (k in 0 until nFft) im[k] = -im[k]
            fft.transformar(re, im)
            for (k in 0 until nFft) {
                val muestra = re[k] / nFft
                val pos = t * salto + k
                suma[pos] += muestra * ventana[k]
                pesos[pos] += ventana[k] * ventana[k]
            }
        }
        return FloatArray(bloque) { k ->
            val pos = k + nFft / 2
            if (pesos[pos] > 1e-8f) suma[pos] / pesos[pos] else 0f
        }
    }

    override fun close() {
        sesion.close()
    }
}

/**
 * FFT de 5120 puntos (5 x 1024): cinco FFT radix-2 de 1024 sobre las muestras
 * intercaladas y una mariposa de radix 5 para combinarlas. En el sitio.
 */
class Fft5120 {
    private val n = 5120
    private val m = 1024
    private val cosN = FloatArray(n) { cos(-2 * PI * it / n).toFloat() }
    private val sinN = FloatArray(n) { sin(-2 * PI * it / n).toFloat() }
    private val cosM = FloatArray(m / 2) { cos(-2 * PI * it / m).toFloat() }
    private val sinM = FloatArray(m / 2) { sin(-2 * PI * it / m).toFloat() }
    private val inv = IntArray(m).also { tabla ->
        val bits = 10
        for (i in 0 until m) {
            var x = i
            var y = 0
            repeat(bits) { y = (y shl 1) or (x and 1); x = x shr 1 }
            tabla[i] = y
        }
    }
    private val subRe = Array(5) { FloatArray(m) }
    private val subIm = Array(5) { FloatArray(m) }

    fun transformar(re: FloatArray, im: FloatArray) {
        for (r in 0 until 5) {
            val sr = subRe[r]
            val si = subIm[r]
            for (k in 0 until m) {
                val origen = r + 5 * k
                sr[inv[k]] = re[origen]
                si[inv[k]] = im[origen]
            }
            radix2(sr, si)
        }
        for (k in 0 until n) {
            var accRe = 0f
            var accIm = 0f
            val km = k % m
            for (r in 0 until 5) {
                val w = (r * k) % n
                val a = subRe[r][km]
                val b = subIm[r][km]
                accRe += a * cosN[w] - b * sinN[w]
                accIm += a * sinN[w] + b * cosN[w]
            }
            re[k] = accRe
            im[k] = accIm
        }
    }

    private fun radix2(re: FloatArray, im: FloatArray) {
        var largo = 2
        while (largo <= m) {
            val mitad = largo / 2
            val paso = m / largo
            var i = 0
            while (i < m) {
                for (j in 0 until mitad) {
                    val wr = cosM[j * paso]
                    val wi = sinM[j * paso]
                    val a = i + j
                    val b = a + mitad
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                i += largo
            }
            largo *= 2
        }
    }
}
