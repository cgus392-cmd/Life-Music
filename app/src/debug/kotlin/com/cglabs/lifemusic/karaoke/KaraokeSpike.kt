package com.cglabs.lifemusic.karaoke

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.cglabs.lifemusic.lyrics.AlineadorDeLetras
import com.cglabs.lifemusic.lyrics.LyricsUtils
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spike del karaoke propio (ficha docs/versiones/2.0/karaoke.md, aprobada por CG
 * el 15-10): ¿se puede calzar palabra por palabra una letra sincronizada por
 * linea, escuchando la cancion en el telefono? Mide el tiempo, la cobertura y,
 * si hay una referencia palabra por palabra, la precision.
 *
 * No es la funcion. Sin el modelo no hace nada. Todo se empuja a mano por ADB a
 * la version de pruebas (com.cglabs.lifemusic.debug):
 *
 *   files/spike/karaoke/model.int8.onnx, tokens.txt   (NeMo FastConformer CTC es, CC-BY-4.0)
 *   files/spike/karaoke/separar.onnx                  (opcional: UVR MDX-Net KARA, separa la voz)
 *   files/spike/karaoke/canciones/<nombre>/audio.*     (mp3, m4a, flac…)
 *   files/spike/karaoke/canciones/<nombre>/letra.lrc   (sincronizada por linea)
 *   files/spike/karaoke/canciones/<nombre>/referencia.lrc  (opcional: palabra por palabra «oficial»)
 *
 * Compara variantes del audio: la mezcla tal cual y, con el separador, la voz
 * separada (y la mezcla menos la salida, por si el modelo da el acompañamiento).
 * Deja karaoke-<variante>.lrc y resultado-<variante>.txt en cada carpeta.
 */
object KaraokeSpike {
    private const val TAG = "KaraokeSpike"
    private const val HZ = 16_000
    private const val MARGEN_S = 0.3

    fun lanzarSiHayModelo(context: Context) {
        val base = File(context.filesDir, "spike/karaoke")
        if (!File(base, "model.int8.onnx").exists() || !File(base, "tokens.txt").exists()) return
        Thread({ runCatching { correr(base) }.onFailure { Log.e(TAG, "fallo el spike", it) } }, "karaoke-spike").start()
    }

    private fun correr(base: File) {
        probarFft()
        val carga = SystemClock.elapsedRealtime()
        val reconocedor = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = HZ, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = File(base, "model.int8.onnx").absolutePath),
                    tokens = File(base, "tokens.txt").absolutePath,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                ),
            ),
        )
        Log.i(TAG, "modelo cargado en ${SystemClock.elapsedRealtime() - carga} ms")
        val separador = File(base, "separar.onnx").takeIf { it.exists() }
            ?.let { runCatching { KaraokeSeparador(it) }.onFailure { e -> Log.e(TAG, "no cargo el separador", e) }.getOrNull() }
        try {
            File(base, "canciones").listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { carpeta ->
                runCatching { procesar(carpeta, reconocedor, separador) }.onFailure { Log.e(TAG, "fallo ${carpeta.name}", it) }
            }
        } finally {
            separador?.close()
            reconocedor.release()
        }
        Log.i(TAG, "fin del spike")
    }

    private fun procesar(carpeta: File, reconocedor: OfflineRecognizer, separador: KaraokeSeparador?) {
        val audio = carpeta.listFiles()?.firstOrNull { it.name.startsWith("audio.") } ?: return
        val letra = File(carpeta, "letra.lrc").takeIf { it.exists() }?.readText() ?: return
        val referencia = File(carpeta, "referencia.lrc").takeIf { it.exists() }?.readText()

        // Variante 1: la mezcla tal cual, a 16 kHz mono.
        val mezcla = File(carpeta, "mezcla16k.f32")
        val t0 = SystemClock.elapsedRealtime()
        if (!ffmpeg("-y -i \"${audio.absolutePath}\" -ac 1 -ar $HZ -f f32le \"${mezcla.absolutePath}\"")) return
        reconocerYAlinear(carpeta, reconocedor, "mezcla", leerFloats(mezcla), letra, referencia, SystemClock.elapsedRealtime() - t0)
        mezcla.delete()

        if (separador == null) return
        // Variantes 2 y 3: separar la voz (estereo a 44,1 kHz) y reconocer sobre ella.
        val estereo = File(carpeta, "estereo44k.f32")
        if (!ffmpeg("-y -i \"${audio.absolutePath}\" -ac 2 -ar 44100 -f f32le \"${estereo.absolutePath}\"")) return
        listOf(false to "voz-separada", true to "mezcla-menos-salida").forEach { (invertir, nombre) ->
            val voz44 = File(carpeta, "voz44k.f32")
            val voz16 = File(carpeta, "voz16k.f32")
            val t1 = SystemClock.elapsedRealtime()
            val separar = separador.separar(estereo, voz44, invertir)
            if (!ffmpeg("-y -f f32le -ar 44100 -ac 1 -i \"${voz44.absolutePath}\" -ar $HZ -f f32le \"${voz16.absolutePath}\"")) return
            Log.i(TAG, "$nombre: separar $separar ms")
            reconocerYAlinear(carpeta, reconocedor, nombre, leerFloats(voz16), letra, referencia, SystemClock.elapsedRealtime() - t1)
            voz44.delete()
            voz16.delete()
        }
        estereo.delete()
    }

    private fun reconocerYAlinear(
        carpeta: File, reconocedor: OfflineRecognizer, variante: String, muestras: FloatArray,
        letra: String, referencia: String?, prepararMs: Long,
    ) {
        val duracion = muestras.size.toDouble() / HZ
        val lineas = LyricsUtils.parseLyrics(letra).filter { it.text.isNotBlank() && it.time >= 0 }
        val salida = StringBuilder()
        val todas = mutableListOf<AlineadorDeLetras.Alineada>()
        var reconocer = 0L
        lineas.forEachIndexed { i, linea ->
            val inicio = linea.time / 1000.0
            val fin = (lineas.getOrNull(i + 1)?.time?.div(1000.0) ?: (inicio + 6.0)).coerceAtMost(duracion)
            val palabras = AlineadorDeLetras.palabrasDeLaLetra(linea.text)
            if (palabras.isEmpty() || fin <= inicio) return@forEachIndexed
            val desde = (inicio - MARGEN_S).coerceAtLeast(0.0)
            val hasta = (fin + MARGEN_S).coerceAtMost(duracion)
            val tramo = muestras.copyOfRange((desde * HZ).toInt(), (hasta * HZ).toInt().coerceAtMost(muestras.size))
            val t1 = SystemClock.elapsedRealtime()
            val stream = reconocedor.createStream()
            stream.acceptWaveform(tramo, HZ)
            reconocedor.decode(stream)
            val resultado = reconocedor.getResult(stream)
            stream.release()
            reconocer += SystemClock.elapsedRealtime() - t1
            val oidas = AlineadorDeLetras.palabrasReconocidas(resultado.tokens, resultado.timestamps, desde)
            val alineadas = AlineadorDeLetras.alinear(palabras, oidas, inicio, fin)
            todas += alineadas
            salida.appendLine(AlineadorDeLetras.aLrcMejorado(inicio, alineadas))
        }
        File(carpeta, "karaoke-$variante.lrc").writeText(salida.toString())

        val cobertura = if (todas.isEmpty()) 0.0 else todas.count { it.calzada }.toDouble() / todas.size
        val informe = StringBuilder()
        informe.appendLine("cancion: ${carpeta.name} · variante: $variante")
        informe.appendLine("duracion: %.1f s · lineas: %d · palabras: %d".format(duracion, lineas.size, todas.size))
        informe.appendLine("preparar audio: $prepararMs ms · reconocer: $reconocer ms")
        informe.appendLine("cobertura (palabras calzadas por el audio): %.0f %%".format(cobertura * 100))
        referencia?.let { informe.append(comparar(todas, it)) }
        File(carpeta, "resultado-$variante.txt").writeText(informe.toString())
        informe.lines().forEach { if (it.isNotBlank()) Log.i(TAG, it) }
    }

    /**
     * Compara con una referencia palabra por palabra (LRC mejorado «<mm:ss.xx>palabra»):
     * empareja en orden por la forma normalizada y mide cuanto se aleja cada palabra.
     */
    private fun comparar(nuestras: List<AlineadorDeLetras.Alineada>, referencia: String): String {
        val patron = Regex("<(\\d+):(\\d+(?:\\.\\d+)?)>\\s*([^<\\n]+)")
        val ref = patron.findAll(referencia).map {
            val t = it.groupValues[1].toInt() * 60 + it.groupValues[2].toDouble()
            AlineadorDeLetras.normalizar(it.groupValues[3]) to t
        }.filter { it.first.isNotEmpty() }.toList()
        if (ref.isEmpty()) return "referencia: sin palabras con tiempo\n"
        val errores = mutableListOf<Double>()
        var j = 0
        for (p in nuestras) {
            val clave = AlineadorDeLetras.normalizar(p.texto)
            val k = (j until minOf(ref.size, j + 6)).firstOrNull { ref[it].first == clave } ?: continue
            errores += abs(p.inicio - ref[k].second)
            j = k + 1
        }
        if (errores.isEmpty()) return "referencia: no se pudo emparejar\n"
        errores.sort()
        val dentro150 = errores.count { it <= 0.15 } * 100 / errores.size
        val dentro300 = errores.count { it <= 0.30 } * 100 / errores.size
        return "PRECISION vs referencia: %d palabras · mediana %.0f ms · %d%% a menos de 150 ms · %d%% a menos de 300 ms\n"
            .format(errores.size, errores[errores.size / 2] * 1000, dentro150, dentro300)
    }

    /** La FFT de 5120 contra la DFT directa en algunas frecuencias: si falla, el separador miente. */
    private fun probarFft() {
        val n = 5120
        val x = FloatArray(n) { (sin(2 * PI * 37 * it / n) + 0.5 * cos(2 * PI * 801 * it / n) + 0.1 * ((it * 7919) % 13 - 6)).toFloat() }
        val re = x.copyOf()
        val im = FloatArray(n)
        Fft5120().transformar(re, im)
        var error = 0.0
        for (k in listOf(0, 1, 37, 801, 1024, 2047, 2560, 4319)) {
            var dr = 0.0
            var di = 0.0
            for (t in 0 until n) {
                val a = -2 * PI * k * t / n
                dr += x[t] * cos(a)
                di += x[t] * sin(a)
            }
            error = maxOf(error, abs(dr - re[k]), abs(di - im[k]))
        }
        Log.i(TAG, "FFT 5120 vs DFT: error maximo %.4f (pico de la senal ~2560)".format(error))
    }

    private fun ffmpeg(argumentos: String): Boolean {
        val sesion = FFmpegKit.execute(argumentos)
        if (!ReturnCode.isSuccess(sesion.returnCode)) {
            Log.e(TAG, "ffmpeg fallo: ${sesion.failStackTrace ?: sesion.output?.takeLast(300)}")
            return false
        }
        return true
    }

    private fun leerFloats(archivo: File): FloatArray {
        val bytes = archivo.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }
}

/** Arranca el spike sin que la app de produccion sepa que existe (igual que StemSpikeInitializer). */
class KaraokeSpikeInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { KaraokeSpike.lanzarSiHayModelo(it) }
        return true
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
