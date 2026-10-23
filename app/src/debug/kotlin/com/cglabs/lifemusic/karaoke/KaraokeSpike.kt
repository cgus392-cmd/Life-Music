package com.cglabs.lifemusic.karaoke

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.arthenica.ffmpegkit.FFmpegKit
import com.cglabs.lifemusic.R
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
 *
 * «De oído» (idea 22-10, nombre de CG): si existe files/spike/karaoke/deoido, no
 * calza ninguna letra: escribe la cancion entera solo con lo que oye. Para
 * medirlo, cada carpeta lleva referencia.txt con la letra real (solo se usa para
 * contar aciertos, nunca se le pasa al reconocedor). Primero todas con la mezcla
 * y luego todas con la voz separada; lo que ya tiene resultado se salta, asi que
 * si Android cierra la app se puede volver a lanzar y sigue donde iba.
 */
object KaraokeSpike {
    private const val TAG = "KaraokeSpike"
    private const val HZ = 16_000
    private const val MARGEN_S = 0.3

    /** Por debajo de esto (~ −48 dB) el tramo no tiene voz y no se escucha: asi no inventa palabras. */
    private const val SIN_VOZ = 0.004

    /** Lo que dura un token mas alla de su comienzo, para estimar el final de la palabra. */
    private const val FIN_TOKEN_S = 0.12

    /**
     * Con el modelo puesto, arranca [KaraokeSpikeService] cuando se abre la primera
     * pantalla: un servicio en primer plano solo se puede pedir con la app delante.
     */
    fun lanzarSiHayModelo(context: Context) {
        val base = File(context.filesDir, "spike/karaoke")
        if (!File(base, "model.int8.onnx").exists() || !File(base, "tokens.txt").exists()) return
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                app.unregisterActivityLifecycleCallbacks(this)
                ContextCompat.startForegroundService(app, Intent(app, KaraokeSpikeService::class.java))
            }
            override fun onActivityCreated(activity: Activity, estado: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, estado: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Lo corre [KaraokeSpikeService] en su hilo. */
    fun correrAhora(context: Context) {
        runCatching { correr(File(context.filesDir, "spike/karaoke")) }.onFailure { Log.e(TAG, "fallo el spike", it) }
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
            if (File(base, "deoido").exists()) {
                // Primero las que no tienen letra en ningun proveedor: son el caso real de «De oído».
                val carpetas = File(base, "canciones").listFiles()?.filter { it.isDirectory }
                    ?.sortedWith(compareBy({ File(it, "referencia.txt").exists() }, { it.name })).orEmpty()
                carpetas.forEach { carpeta ->
                    runCatching { deOido(carpeta, reconocedor, null) }.onFailure { Log.e(TAG, "fallo de oido ${carpeta.name}", it) }
                }
                if (separador != null) {
                    carpetas.forEach { carpeta ->
                        runCatching { deOido(carpeta, reconocedor, separador) }.onFailure { Log.e(TAG, "fallo de oido ${carpeta.name}", it) }
                    }
                }
                Log.i(TAG, "fin de oido")
                return
            }
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

    /** Una palabra oida con su comienzo y su final aproximado (ultimo token + un poco), en segundos. */
    private data class Oida(val texto: String, val inicio: Double, val fin: Double)

    /**
     * «De oído»: escribe la cancion entera con el reconocedor, sin letra. Con
     * [separador] escucha la voz separada; sin el, la mezcla tal cual.
     * Deja deoido-<variante>.lrc (palabra por palabra) y resultado-deoido-<variante>.txt.
     */
    private fun deOido(carpeta: File, reconocedor: OfflineRecognizer, separador: KaraokeSeparador?) {
        val variante = if (separador == null) "mezcla" else "voz-separada"
        val resultado = File(carpeta, "resultado-deoido-$variante.txt")
        if (resultado.exists()) return
        val audio = carpeta.listFiles()?.firstOrNull { it.name.startsWith("audio.") } ?: return
        val t0 = SystemClock.elapsedRealtime()
        val voz16 = File(carpeta, "deoido16k.f32")
        var separarMs = 0L
        if (separador == null) {
            if (!ffmpeg("-y -i \"${audio.absolutePath}\" -ac 1 -ar $HZ -f f32le \"${voz16.absolutePath}\"")) return
        } else {
            val estereo = File(carpeta, "estereo44k.f32")
            val voz44 = File(carpeta, "voz44k.f32")
            try {
                if (!ffmpeg("-y -i \"${audio.absolutePath}\" -ac 2 -ar 44100 -f f32le \"${estereo.absolutePath}\"")) return
                separarMs = separador.separar(estereo, voz44)
                estereo.delete()
                if (!ffmpeg("-y -f f32le -ar 44100 -ac 1 -i \"${voz44.absolutePath}\" -ar $HZ -f f32le \"${voz16.absolutePath}\"")) return
            } finally {
                estereo.delete()
                voz44.delete()
            }
        }
        val muestras = leerFloats(voz16)
        voz16.delete()
        val prepararMs = SystemClock.elapsedRealtime() - t0

        val t1 = SystemClock.elapsedRealtime()
        val palabras = mutableListOf<Oida>()
        var callados = 0
        val cortes = tramos(muestras)
        cortes.forEach { tramo ->
            val pedazo = muestras.copyOfRange(tramo.first, tramo.last + 1)
            if (rms(pedazo) < SIN_VOZ) {
                callados++
                return@forEach
            }
            val stream = reconocedor.createStream()
            stream.acceptWaveform(pedazo, HZ)
            reconocedor.decode(stream)
            val r = reconocedor.getResult(stream)
            stream.release()
            palabras += palabrasConFin(r.tokens, r.timestamps, tramo.first.toDouble() / HZ)
        }
        val reconocerMs = SystemClock.elapsedRealtime() - t1

        val lineas = enLineas(palabras)
        File(carpeta, "deoido-$variante.lrc").writeText(
            lineas.joinToString("\n") { linea ->
                AlineadorDeLetras.aLrcMejorado(linea.first().inicio, linea.map { AlineadorDeLetras.Alineada(it.texto, it.inicio, true) })
            } + "\n",
        )
        val duracion = muestras.size.toDouble() / HZ
        val totalMs = SystemClock.elapsedRealtime() - t0
        val informe = StringBuilder()
        informe.appendLine("DE OIDO · cancion: ${carpeta.name} · variante: $variante")
        informe.appendLine("duracion: %.1f s · tramos: %d (%d callados) · palabras escritas: %d · lineas: %d"
            .format(duracion, cortes.size, callados, palabras.size, lineas.size))
        informe.appendLine("preparar audio: $prepararMs ms (separar: $separarMs ms) · reconocer: $reconocerMs ms · total: $totalMs ms (%.2fx tiempo real)"
            .format(totalMs / 1000.0 / duracion))
        File(carpeta, "referencia.txt").takeIf { it.exists() }?.readText()?.let { informe.append(evaluar(palabras.map { it.texto }, it)) }
        resultado.writeText(informe.toString())
        informe.lines().forEach { if (it.isNotBlank()) Log.i(TAG, it) }
    }

    /**
     * Corta en tramos de unos 20 s, por el punto mas callado (200 ms) entre los 16
     * y los 24 s, para no partir palabras por la mitad.
     */
    private fun tramos(muestras: FloatArray): List<IntRange> {
        val marco = HZ / 20 // 50 ms
        val n = muestras.size / marco
        val energia = FloatArray(n) { m ->
            var s = 0f
            for (k in m * marco until (m + 1) * marco) s += muestras[k] * muestras[k]
            s
        }
        val suave = FloatArray(n) { m -> (m until minOf(n, m + 4)).sumOf { energia[it].toDouble() }.toFloat() }
        val salida = mutableListOf<IntRange>()
        var desde = 0
        while (desde < n) {
            if (n - desde <= 24 * 20) {
                salida += desde * marco until muestras.size
                break
            }
            val corte = (desde + 16 * 20 until desde + 24 * 20).minBy { suave[it] } + 2
            salida += desde * marco until corte * marco
            desde = corte
        }
        return salida
    }

    private fun rms(x: FloatArray): Double {
        var s = 0.0
        for (v in x) s += v * v
        return if (x.isEmpty()) 0.0 else kotlin.math.sqrt(s / x.size)
    }

    /** Como [AlineadorDeLetras.palabrasReconocidas], pero guarda tambien cuando termina cada palabra. */
    private fun palabrasConFin(tokens: Array<String>, tiempos: FloatArray, desde: Double): List<Oida> {
        val salida = mutableListOf<Oida>()
        val actual = StringBuilder()
        var inicio = 0.0
        var ultimo = 0.0
        tokens.forEachIndexed { i, token ->
            val t = desde + (tiempos.getOrNull(i)?.toDouble() ?: return@forEachIndexed)
            if ((token.startsWith("▁") || token.startsWith(" ")) && actual.isNotEmpty()) {
                salida += Oida(actual.toString(), inicio, ultimo + FIN_TOKEN_S)
                actual.clear()
            }
            if (actual.isEmpty()) inicio = t
            val pedazo = token.removePrefix("▁").trim()
            actual.append(pedazo)
            // Un punto suelto puede caer mucho despues de la palabra: no la alarga.
            if (AlineadorDeLetras.normalizar(pedazo).isNotEmpty()) ultimo = t
        }
        if (actual.isNotEmpty()) salida += Oida(actual.toString(), inicio, ultimo + FIN_TOKEN_S)
        return salida.filter { AlineadorDeLetras.normalizar(it.texto).isNotEmpty() }
    }

    /**
     * Parte en lineas por las pausas (una larga siempre, una corta si la linea ya
     * va larga) y por los puntos que pone el modelo, que luego se quitan.
     */
    private fun enLineas(palabras: List<Oida>): List<List<Oida>> {
        val lineas = mutableListOf<MutableList<Oida>>()
        palabras.forEach { p ->
            val linea = lineas.lastOrNull()
            val anterior = linea?.last()
            val pausa = anterior?.let { p.inicio - it.fin } ?: Double.MAX_VALUE
            val punto = anterior?.texto?.endsWith(".") == true || anterior?.texto?.endsWith("?") == true
            if (linea == null || punto || pausa >= 0.8 || (linea.size >= 8 && pausa >= 0.35) || linea.size >= 12) {
                lineas += mutableListOf(p)
            } else {
                linea += p
            }
        }
        return lineas.map { l -> l.map { it.copy(texto = it.texto.trimEnd('.')) } }
    }

    /**
     * Cuanto se parece a la letra real, palabra por palabra (distancia de edicion,
     * la misma idea del WER). Dos lecturas: por como se escribe y por como suena
     * («vez» y «bes» cuentan bien en la segunda).
     */
    private fun evaluar(escritas: List<String>, referencia: String): String {
        val ref = referencia.lines().filterNot { it.trim().startsWith("[") }.flatMap { AlineadorDeLetras.palabrasDeLaLetra(it) }
        if (ref.isEmpty()) return "referencia: vacia\n"
        val salida = StringBuilder()
        listOf("escritura" to AlineadorDeLetras::normalizar, "sonido" to AlineadorDeLetras::sonido).forEach { (nombre, clave) ->
            val (bien, cambiadas, faltan, sobran) = distancia(ref.map(clave), escritas.map(clave))
            salida.appendLine(
                "ACIERTO por $nombre: %d de %d palabras de la letra (%d %%) · cambiadas %d · faltaron %d · sobraron %d · WER %d %%"
                    .format(bien, ref.size, bien * 100 / ref.size, cambiadas, faltan, sobran, (cambiadas + faltan + sobran) * 100 / ref.size),
            )
        }
        return salida.toString()
    }

    /** [bien, cambiadas, faltan, sobran] entre la letra real y lo escrito. */
    private fun distancia(ref: List<String>, hip: List<String>): IntArray {
        val n = ref.size
        val m = hip.size
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val c = if (ref[i - 1] == hip[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j - 1] + c, d[i - 1][j] + 1, d[i][j - 1] + 1)
        }
        var i = n
        var j = m
        val cuenta = IntArray(4)
        while (i > 0 || j > 0) {
            val igual = i > 0 && j > 0 && ref[i - 1] == hip[j - 1]
            when {
                i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + (if (igual) 0 else 1) -> {
                    cuenta[if (igual) 0 else 1]++
                    i--
                    j--
                }
                i > 0 && d[i][j] == d[i - 1][j] + 1 -> { cuenta[2]++; i-- }
                else -> { cuenta[3]++; j-- }
            }
        }
        return cuenta
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

/**
 * El spike en primer plano, con su aviso. Sin esto, al quedar la app atras (otra
 * app o la pantalla apagada) Android la congela y el separador se queda quieto a
 * la mitad: paso el 23-10 con «Escarcha».
 */
class KaraokeSpikeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(CANAL, "Prueba del karaoke", NotificationManager.IMPORTANCE_LOW),
        )
        val aviso = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.mic)
            .setContentTitle("Prueba «De oído»")
            .setContentText("Escuchando las canciones de prueba…")
            .setOngoing(true)
            .setSilent(true)
            .build()
        ServiceCompat.startForeground(this, AVISO, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!corriendo) {
            corriendo = true
            Thread({
                KaraokeSpike.correrAhora(this)
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }, "karaoke-spike").start()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CANAL = "karaoke_spike"
        private const val AVISO = 7390

        @Volatile
        private var corriendo = false
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
