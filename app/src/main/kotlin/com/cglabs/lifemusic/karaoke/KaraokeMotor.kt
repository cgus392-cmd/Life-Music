package com.cglabs.lifemusic.karaoke

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.cglabs.lifemusic.lyrics.AlineadorDeLetras
import com.cglabs.lifemusic.lyrics.LyricsEntry
import com.cglabs.lifemusic.nova.NovaCapacidad
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 2.x · El motor del karaoke propio (ficha docs/versiones/2.0/karaoke.md, spike
 * en el S25 el 15-10). Toma una cancion descargada y su letra sincronizada por
 * linea, escucha cada tramo con el reconocedor de voz y calza las palabras.
 *
 * Dos caminos, el rapido primero:
 * 1. La mezcla tal cual: ~10 s por cancion. Basta en pop y baladas con voz clara.
 * 2. Si quedo corto (< 60 % de palabras oidas) y el telefono puede: separar la
 *    voz con UVR MDX-Net KARA y volver a escuchar. En «Hazme Olvidarla» (salsa)
 *    subio de 30 % a 56 %, a cambio de ~4 min en el S25.
 * Gana el que oyo mas. Lo que no se oye se reparte por silabas: nunca queda
 * peor que la letra por linea.
 */
class KaraokeMotor(context: Context) {
    private val contexto = context.applicationContext

    data class Resultado(val lrc: String, val cobertura: Double, val camino: String, val milisegundos: Long)

    /** Que esta haciendo, para la notificacion: el texto y el avance (0 a 1) si se sabe. */
    fun interface Etapa {
        fun cambio(texto: Int, avance: Float?)
    }

    fun preparar(audio: File, lineas: List<LyricsEntry>, etapa: Etapa): Resultado? {
        val inicio = SystemClock.elapsedRealtime()
        etapa.cambio(com.cglabs.lifemusic.R.string.karaoke_etapa_modelo, 0f)
        if (!KaraokeModelos.asegurarReconocedor(contexto) { etapa.cambio(com.cglabs.lifemusic.R.string.karaoke_etapa_modelo, it) }) {
            return null
        }
        val (modelo, tokens) = KaraokeModelos.reconocedor(contexto) ?: return null
        val reconocedor = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = HZ, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = modelo.absolutePath),
                    tokens = tokens.absolutePath,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                ),
            ),
        )
        val trabajo = File(contexto.cacheDir, "karaoke-trabajo").apply { mkdirs() }
        try {
            // 1) El camino rapido: la mezcla tal cual.
            etapa.cambio(com.cglabs.lifemusic.R.string.karaoke_etapa_escuchando, null)
            val mezcla = File(trabajo, "mezcla16k.f32")
            if (!ffmpeg("-y -i \"${audio.absolutePath}\" -ac 1 -ar $HZ -f f32le \"${mezcla.absolutePath}\"")) return null
            var mejor = alinear(reconocedor, leerFloats(mezcla), lineas, "mezcla")
            mezcla.delete()
            Log.i(TAG, "camino rapido: ${(mejor.cobertura * 100).toInt()} %")

            // 2) Si quedo corto y el telefono puede, separar la voz y volver a escuchar.
            val telefono = NovaCapacidad.telefono(contexto)
            val puedeSeparar = !telefono.memoriaBaja && telefono.memoriaGb >= 5.5f &&
                (KaraokeModelos.separador(contexto) != null || !KaraokeModelos.redMedida(contexto))
            if (mejor.cobertura < SUFICIENTE && puedeSeparar) {
                etapa.cambio(com.cglabs.lifemusic.R.string.karaoke_etapa_modelo, 0f)
                if (KaraokeModelos.asegurarSeparador(contexto) { etapa.cambio(com.cglabs.lifemusic.R.string.karaoke_etapa_modelo, it) }) {
                    val estereo = File(trabajo, "estereo44k.f32")
                    val voz44 = File(trabajo, "voz44k.f32")
                    val voz16 = File(trabajo, "voz16k.f32")
                    try {
                        if (ffmpeg("-y -i \"${audio.absolutePath}\" -ac 2 -ar 44100 -f f32le \"${estereo.absolutePath}\"")) {
                            KaraokeSeparador(KaraokeModelos.separador(contexto)!!).use { separador ->
                                separador.separar(estereo, voz44) { etapa.cambio(com.cglabs.lifemusic.R.string.karaoke_etapa_separando, it) }
                            }
                            estereo.delete()
                            if (ffmpeg("-y -f f32le -ar 44100 -ac 1 -i \"${voz44.absolutePath}\" -ar $HZ -f f32le \"${voz16.absolutePath}\"")) {
                                etapa.cambio(com.cglabs.lifemusic.R.string.karaoke_etapa_escuchando, null)
                                val separada = alinear(reconocedor, leerFloats(voz16), lineas, "voz separada")
                                Log.i(TAG, "con la voz separada: ${(separada.cobertura * 100).toInt()} %")
                                if (separada.cobertura > mejor.cobertura) mejor = separada
                            }
                        }
                    } finally {
                        estereo.delete()
                        voz44.delete()
                        voz16.delete()
                    }
                }
            }
            return mejor.copy(milisegundos = SystemClock.elapsedRealtime() - inicio)
        } finally {
            reconocedor.release()
            trabajo.deleteRecursively()
        }
    }

    /** Escucha cada linea (con un margen) y calza sus palabras. */
    private fun alinear(reconocedor: OfflineRecognizer, muestras: FloatArray, lineas: List<LyricsEntry>, camino: String): Resultado {
        val duracion = muestras.size.toDouble() / HZ
        val salida = StringBuilder()
        var palabras = 0
        var calzadas = 0
        lineas.forEachIndexed { i, linea ->
            val inicio = linea.time / 1000.0
            val fin = (lineas.getOrNull(i + 1)?.time?.div(1000.0) ?: (inicio + 6.0)).coerceAtMost(duracion)
            val texto = AlineadorDeLetras.palabrasDeLaLetra(linea.text)
            if (texto.isEmpty() || fin <= inicio) {
                // Una linea sin palabras o fuera del audio queda como estaba.
                salida.appendLine("[${marca(inicio)}]${linea.text}")
                return@forEachIndexed
            }
            val desde = (inicio - MARGEN_S).coerceAtLeast(0.0)
            val hasta = (fin + MARGEN_S).coerceAtMost(duracion)
            val tramo = muestras.copyOfRange((desde * HZ).toInt(), (hasta * HZ).toInt().coerceAtMost(muestras.size))
            val stream = reconocedor.createStream()
            stream.acceptWaveform(tramo, HZ)
            reconocedor.decode(stream)
            val resultado = reconocedor.getResult(stream)
            stream.release()
            val oidas = AlineadorDeLetras.palabrasReconocidas(resultado.tokens, resultado.timestamps, desde)
            val alineadas = AlineadorDeLetras.alinear(texto, oidas, inicio, fin)
            palabras += alineadas.size
            calzadas += alineadas.count { it.calzada }
            salida.appendLine(AlineadorDeLetras.aLrcMejorado(inicio, alineadas))
        }
        return Resultado(salida.toString(), if (palabras == 0) 0.0 else calzadas.toDouble() / palabras, camino, 0)
    }

    private fun marca(segundos: Double): String {
        val c = (segundos.coerceAtLeast(0.0) * 100).toLong()
        return "%02d:%02d.%02d".format(c / 6000, (c / 100) % 60, c % 100)
    }

    private fun ffmpeg(argumentos: String): Boolean {
        val sesion = FFmpegKit.execute(argumentos)
        if (!ReturnCode.isSuccess(sesion.returnCode)) {
            Log.e(TAG, "ffmpeg fallo: ${sesion.output?.takeLast(300)}")
            return false
        }
        return true
    }

    private fun leerFloats(archivo: File): FloatArray {
        val bytes = archivo.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    companion object {
        private const val TAG = "LifeMusicKaraoke"
        private const val HZ = 16_000
        private const val MARGEN_S = 0.3

        /** Con esto el camino rapido alcanza; por debajo se intenta separar la voz. */
        const val SUFICIENTE = 0.6
    }
}
