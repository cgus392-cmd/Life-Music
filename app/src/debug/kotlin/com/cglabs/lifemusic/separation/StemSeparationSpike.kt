package com.cglabs.lifemusic.separation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.nio.FloatBuffer

/**
 * Spike: cuanto tarda este telefono en separar voz e instrumental con MDX-Net.
 *
 * No es la funcion. Es el numero que decide si la funcion existe. Mide UNA cosa
 * —el tiempo de inferencia por bloque— y no monta pipeline, ni interfaz, ni
 * descarga: el modelo se empuja a mano por ADB.
 *
 *   adb push UVR-MDX-NET-Inst_full_292.onnx /data/local/tmp/mdx.onnx
 *   adb shell run-as com.cglabs.lifemusic.debug \
 *       sh -c 'mkdir -p files/spike && cp /data/local/tmp/mdx.onnx files/spike/'
 *
 * Sin ese fichero no hace absolutamente nada, ni siquiera carga la libreria. Por
 * eso puede vivir en el arranque sin molestar a nadie.
 *
 * El contrato del modelo, leido del propio fichero en el PC:
 *   entrada = salida = [1, 4, 3072, 256] float32
 *   con n_fft 6144 y hop 1024, cada bloque cubre 255 * 1024 / 44100 = 5.92 s
 *
 * Referencia del PC (12 nucleos, ONNX Runtime CPU): 2.30x tiempo real en
 * inferencia pura, 1.38x con el solapamiento del pipeline real.
 */
object StemSeparationSpike {

    private const val TAG = "StemSpike"

    private const val CARPETA = "spike"
    private const val MODELO = "mdx.onnx"

    private const val DIM_F = 3072
    private const val DIM_T = 256
    private const val CANALES = 4

    /** Muestras de audio que cubre un bloque: hop * (dim_t - 1). */
    private const val MUESTRAS_POR_BLOQUE = 1024 * (DIM_T - 1)
    private const val SR = 44100f
    private const val SEGUNDOS_POR_BLOQUE = MUESTRAS_POR_BLOQUE / SR

    private const val REPETICIONES = 5

    fun lanzarSiHayModelo(context: Context) {
        val modelo = File(File(context.filesDir, CARPETA), MODELO)
        if (!modelo.exists()) return
        // Hilo propio y no el scope de App: este spike lo arranca un
        // ContentProvider, que corre ANTES de Application.onCreate. Ni el scope
        // ni Timber existen todavia a esas alturas.
        Thread({ medir(modelo) }, "stem-spike").apply { isDaemon = true }.start()
    }

    private fun medir(modelo: File) {
        Log.i(TAG, "modelo %.1f MB".format(modelo.length() / 1024.0 / 1024.0))

        val entorno = OrtEnvironment.getEnvironment()
        val nucleos = Runtime.getRuntime().availableProcessors()

        // Se prueban varios repartos de hilos porque en el PC el escalado se
        // agotaba pronto: de 4 a 8 solo ganaba un 17%. Si aqui pasa igual,
        // conviene saberlo antes de dimensionar el trabajo en segundo plano.
        for (hilos in intArrayOf(1, 4, nucleos)) {
            val opciones = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(hilos)
                // XNNPACK acelera convoluciones en ARM. Si no esta disponible en
                // este build de ONNX Runtime, se sigue con la CPU normal.
                runCatching { addXnnpack(mapOf("intra_op_num_threads" to hilos.toString())) }
                    .onFailure { Log.w(TAG, "sin XNNPACK: " + it.message) }
            }

            var sesion: OrtSession? = null
            try {
                val abierta = SystemClock.elapsedRealtime()
                sesion = entorno.createSession(modelo.absolutePath, opciones)
                Log.i(
                    TAG,
                    "sesion abierta con %d hilos en %d ms"
                        .format(hilos, SystemClock.elapsedRealtime() - abierta),
                )

                val n = CANALES.toLong() * DIM_F * DIM_T
                val datos = FloatBuffer.allocate(n.toInt())
                var semilla = 12345
                repeat(n.toInt()) {
                    // Ruido barato y reproducible; el contenido da igual, lo que
                    // se mide es el coste de la red, que no depende de los datos.
                    semilla = semilla * 1103515245 + 12345
                    datos.put(((semilla shr 16) and 0x7FFF) / 32768f * 0.1f)
                }
                datos.rewind()

                val forma = longArrayOf(1, CANALES.toLong(), DIM_F.toLong(), DIM_T.toLong())
                OnnxTensor.createTensor(entorno, datos, forma).use { tensor ->
                    val entrada = mapOf(sesion.inputNames.first() to tensor)

                    sesion.run(entrada).close() // calentar: la primera siempre miente

                    val inicio = SystemClock.elapsedRealtime()
                    repeat(REPETICIONES) { sesion.run(entrada).close() }
                    val porBloque = (SystemClock.elapsedRealtime() - inicio) / REPETICIONES.toDouble()

                    val factor = SEGUNDOS_POR_BLOQUE / (porBloque / 1000.0)
                    Log.i(
                        TAG,
                        "%d hilos: %.0f ms/bloque -> %.2fx tiempo real | cancion de 4 min en %.0f s"
                            .format(hilos, porBloque, factor, 240.0 / factor),
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "fallo con $hilos hilos", e)
            } finally {
                runCatching { sesion?.close() }
                runCatching { opciones.close() }
            }
        }
        Log.i(TAG, "fin de la medicion")
    }
}

/**
 * Arranca el spike sin que el codigo de produccion sepa que existe.
 *
 * Un ContentProvider declarado solo en el manifiesto de depuracion: Android lo
 * instancia al abrir la app, y asi App.kt no tiene que referenciar una clase que
 * en release no esta compilada. Es el mismo truco que usa LeakCanary.
 */
class StemSpikeInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { StemSeparationSpike.lanzarSiHayModelo(it) }
        return true
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
