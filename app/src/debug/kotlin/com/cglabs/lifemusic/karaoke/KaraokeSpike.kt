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
import kotlin.math.abs

/**
 * Spike del karaoke propio (ficha docs/versiones/2.0/karaoke.md, aprobada por CG
 * el 15-10): ¿se puede calzar palabra por palabra una letra sincronizada por
 * linea, escuchando la cancion en el telefono? Mide el tiempo y, si hay una
 * referencia palabra por palabra, la precision.
 *
 * No es la funcion. Sin el modelo no hace nada. Todo se empuja a mano por ADB
 * a la version de pruebas (com.cglabs.lifemusic.debug):
 *
 *   files/spike/karaoke/model.int8.onnx, tokens.txt   (NeMo FastConformer CTC es, CC-BY-4.0)
 *   files/spike/karaoke/canciones/<nombre>/audio.*     (mp3, m4a, flac…)
 *   files/spike/karaoke/canciones/<nombre>/letra.lrc   (sincronizada por linea)
 *   files/spike/karaoke/canciones/<nombre>/referencia.lrc  (opcional: palabra por palabra «oficial»)
 *
 * Deja en cada carpeta karaoke.lrc (el resultado) y resultado.txt (los numeros).
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
        try {
            File(base, "canciones").listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { carpeta ->
                runCatching { procesar(carpeta, reconocedor) }.onFailure { Log.e(TAG, "fallo ${carpeta.name}", it) }
            }
        } finally {
            reconocedor.release()
        }
        Log.i(TAG, "fin del spike")
    }

    private fun procesar(carpeta: File, reconocedor: OfflineRecognizer) {
        val audio = carpeta.listFiles()?.firstOrNull { it.name.startsWith("audio.") } ?: return
        val letra = File(carpeta, "letra.lrc").takeIf { it.exists() } ?: return
        Log.i(TAG, "== ${carpeta.name}")

        // 1) Decodificar a 16 kHz mono float con el FFmpeg que ya trae la app.
        val crudo = File(carpeta, "audio16k.f32")
        val t0 = SystemClock.elapsedRealtime()
        val sesion = FFmpegKit.execute("-y -i \"${audio.absolutePath}\" -ac 1 -ar $HZ -f f32le \"${crudo.absolutePath}\"")
        if (!ReturnCode.isSuccess(sesion.returnCode)) {
            Log.e(TAG, "no se pudo decodificar: ${sesion.failStackTrace ?: sesion.output?.takeLast(300)}")
            return
        }
        val muestras = leerFloats(crudo)
        val decodificar = SystemClock.elapsedRealtime() - t0
        val duracion = muestras.size.toDouble() / HZ

        // 2) Las lineas del LRC, con su ventana (hasta donde empieza la siguiente).
        val lineas = LyricsUtils.parseLyrics(letra.readText()).filter { it.text.isNotBlank() && it.time >= 0 }
        val salida = StringBuilder()
        val todas = mutableListOf<AlineadorDeLetras.Alineada>()
        var reconocer = 0L
        lineas.forEachIndexed { i, linea ->
            val inicio = linea.time / 1000.0
            val fin = (lineas.getOrNull(i + 1)?.time?.div(1000.0) ?: (inicio + 6.0)).coerceAtMost(duracion)
            val palabras = AlineadorDeLetras.palabrasDeLaLetra(linea.text)
            if (palabras.isEmpty() || fin <= inicio) return@forEachIndexed

            // 3) Reconocer solo ese tramo (con un margen), y 4) calzar las palabras.
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
            Log.i(
                TAG,
                "[%.1f s] %d%% · oyo «%s»".format(inicio, (AlineadorDeLetras.cobertura(alineadas) * 100).toInt(), resultado.text.take(80)),
            )
        }
        File(carpeta, "karaoke.lrc").writeText(salida.toString())

        // 5) Los numeros: tiempo, cobertura y, si hay referencia, precision.
        val cobertura = if (todas.isEmpty()) 0.0 else todas.count { it.calzada }.toDouble() / todas.size
        val informe = StringBuilder()
        informe.appendLine("cancion: ${carpeta.name}")
        informe.appendLine("duracion: %.1f s · lineas: %d · palabras: %d".format(duracion, lineas.size, todas.size))
        informe.appendLine("decodificar: $decodificar ms · reconocer: $reconocer ms · %.2f s por minuto de cancion".format(reconocer / 1000.0 / (duracion / 60.0)))
        informe.appendLine("cobertura (palabras calzadas por el audio): %.0f %%".format(cobertura * 100))
        File(carpeta, "referencia.lrc").takeIf { it.exists() }?.let { informe.append(comparar(todas, it.readText())) }
        File(carpeta, "resultado.txt").writeText(informe.toString())
        informe.lines().forEach { if (it.isNotBlank()) Log.i(TAG, it) }
        crudo.delete()
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
            // La siguiente aparicion de esa palabra en la referencia, cerca del punto actual.
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
