package com.cglabs.lifemusic.nova

import android.content.Context
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 2.0 · La voz HD de Nova: una voz neuronal corriendo dentro del telefono con
 * sherpa-onnx, Piper (VITS) o Kokoro. Sin internet, sin cuentas, sin enviar
 * nada: el texto entra y el audio sale aqui mismo. Escribe la frase a un WAV,
 * igual que la voz del telefono (NovaVoz), asi el resto de Nova no cambia: la
 * musica baja, la onda y la mirada salen del mismo audio.
 *
 * Cargar un modelo tarda un par de segundos y ocupa memoria, asi que se carga
 * una vez y se reusa mientras no se cambie de voz.
 */
class NovaVozHD(context: Context) {
    private val contexto = context.applicationContext
    private val cerrojo = Mutex()
    private var motor: OfflineTts? = null
    private var cargada: String? = null

    /** Deja cargada la voz para que la primera frase no tarde. */
    suspend fun precargar(voz: VozHD, calidad: CalidadVoz) {
        withContext(Dispatchers.Default) { cerrojo.withLock { cargar(voz, calidad) } }
    }

    /**
     * Escribe [texto] como voz en [archivo]. Falso si la voz no esta en el
     * telefono o el motor fallo: entonces Nova usa la voz del telefono esa vez.
     */
    suspend fun sintetizar(texto: String, archivo: File, voz: VozHD, calidad: CalidadVoz): Boolean =
        withContext(Dispatchers.Default) {
            cerrojo.withLock {
                val m = cargar(voz, calidad) ?: return@withLock false
                val inicio = android.os.SystemClock.elapsedRealtime()
                val hablante = voz.hablante.coerceIn(0, (m.numSpeakers() - 1).coerceAtLeast(0))
                val audio = m.generate(texto, hablante, 1.0f)
                if (audio.samples.isEmpty()) return@withLock false
                archivo.parentFile?.mkdirs()
                // Las voces neuronales salen bajas: se nivelan al volumen de una voz de radio.
                val ok = GeneratedAudio(NovaOnda.nivelar(audio.samples), audio.sampleRate).save(archivo.absolutePath)
                android.util.Log.i(
                    "LifeMusicNova",
                    "voz HD: ${audio.samples.size * 1000L / audio.sampleRate} ms de audio en " +
                        "${android.os.SystemClock.elapsedRealtime() - inicio} ms",
                )
                ok && archivo.length() > 0
            }
        }

    private fun cargar(voz: VozHD, calidad: CalidadVoz): OfflineTts? {
        val clave = voz.clave(calidad)
        if (cargada == clave) motor?.let { return it }
        soltarYa()
        val archivos = NovaDescargas.archivos(contexto, clave) ?: return null
        val inicio = android.os.SystemClock.elapsedRealtime()
        return runCatching { OfflineTts(config = configuracion(voz, archivos)) }
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

    companion object {
        /** 4 nucleos: las voces exigentes (Daniela, Kokoro) los aprovechan; los telefonos de hoy tienen 8. */
        private fun nucleos() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

        /** La configuracion del motor para una voz, Piper o Kokoro. La usan la voz y la prueba. */
        fun configuracion(voz: VozHD, archivos: ArchivosDeVoz): OfflineTtsConfig = when (voz.motor) {
            MotorVoz.PIPER -> OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = archivos.modelo,
                        tokens = archivos.tokens,
                        dataDir = archivos.datos,
                    ),
                    numThreads = nucleos(),
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 2,
            )
            // Kokoro en español: sin lexicon propio, la pronunciacion sale de espeak-ng
            // con lang = «es» (el paquete solo trae diccionarios de ingles y chino).
            MotorVoz.KOKORO -> OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(
                        model = archivos.modelo,
                        voices = archivos.voces.orEmpty(),
                        tokens = archivos.tokens,
                        dataDir = archivos.datos,
                        lang = "es",
                    ),
                    numThreads = nucleos(),
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 2,
            )
        }
    }
}
