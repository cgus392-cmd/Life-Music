package com.cglabs.lifemusic.nova

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 2.0 · La voz HD de Nova: una voz Piper corriendo dentro del telefono con
 * sherpa-onnx. Sin internet, sin cuentas, sin enviar nada: el texto entra y el
 * audio sale aqui mismo. Escribe la frase a un WAV, igual que la voz del
 * telefono (NovaVoz), asi el resto de Nova no cambia: la musica baja, la onda
 * y la mirada salen del mismo audio.
 *
 * Cargar un modelo tarda un par de segundos y ocupa memoria, asi que se carga
 * una vez y se reusa mientras no se cambie de voz.
 */
class NovaVozHD(context: Context) {
    private val contexto = context.applicationContext
    private val cerrojo = Mutex()
    private var motor: OfflineTts? = null
    private var cargada: String? = null

    /** Deja cargada la voz [clave] para que la primera frase no tarde. */
    suspend fun precargar(clave: String) {
        withContext(Dispatchers.Default) { cerrojo.withLock { cargar(clave) } }
    }

    /**
     * Escribe [texto] como voz en [archivo]. Falso si la voz no esta en el
     * telefono o el motor fallo: entonces Nova usa la voz del telefono esa vez.
     */
    suspend fun sintetizar(texto: String, archivo: File, clave: String, hablante: Int): Boolean =
        withContext(Dispatchers.Default) {
            cerrojo.withLock {
                val m = cargar(clave) ?: return@withLock false
                val inicio = android.os.SystemClock.elapsedRealtime()
                val audio = m.generate(texto, hablante.coerceIn(0, (m.numSpeakers() - 1).coerceAtLeast(0)), 1.0f)
                if (audio.samples.isEmpty()) return@withLock false
                archivo.parentFile?.mkdirs()
                val ok = audio.save(archivo.absolutePath)
                android.util.Log.i(
                    "LifeMusicNova",
                    "voz HD: ${audio.samples.size * 1000L / audio.sampleRate} ms de audio en " +
                        "${android.os.SystemClock.elapsedRealtime() - inicio} ms",
                )
                ok && archivo.length() > 0
            }
        }

    private fun cargar(clave: String): OfflineTts? {
        if (cargada == clave) motor?.let { return it }
        soltarYa()
        val archivos = NovaDescargas.archivos(contexto, clave) ?: return null
        val inicio = android.os.SystemClock.elapsedRealtime()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = archivos.modelo,
                    tokens = archivos.tokens,
                    dataDir = archivos.datos,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 2,
        )
        return runCatching { OfflineTts(config = config) }
            .onSuccess {
                motor = it
                cargada = clave
                android.util.Log.i(
                    "LifeMusicNova",
                    "voz HD $clave cargada en ${android.os.SystemClock.elapsedRealtime() - inicio} ms " +
                        "(${it.numSpeakers()} voces, ${it.sampleRate()} Hz)",
                )
            }
            .onFailure { android.util.Log.w("LifeMusicNova", "no cargo la voz HD $clave", it) }
            .getOrNull()
    }

    /** Suelta el modelo de la memoria (al apagar Nova o cambiar a la voz del telefono). */
    suspend fun soltar() {
        withContext(Dispatchers.Default) { cerrojo.withLock { soltarYa() } }
    }

    private fun soltarYa() {
        runCatching { motor?.release() }
        motor = null
        cargada = null
    }
}
