package com.cglabs.lifemusic.nova

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * La voz del telefono para Nova: el motor de texto a voz que ya trae Android
 * (Google, Samsung…). Pesa 0 MB y funciona sin internet si el telefono tiene
 * la voz en espanol. La voz HD (Piper) llega en una tanda posterior.
 *
 * No habla directo por el parlante: escribe la frase a un archivo y la app la
 * reproduce ella misma. Asi la musica baja justo mientras suena la voz, y mas
 * adelante la onda y el latido de la estrella salen de este mismo audio.
 */
class NovaVoz(context: Context) {
    private val contexto = context.applicationContext
    private var tts: TextToSpeech? = null
    private var lista = CompletableDeferred<Boolean>()
    private val pendientes = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /** El idioma que quedo puesto, para el registro. */
    var idioma: String? = null
        private set

    /** Prende el motor. Se llama desde el hilo principal; prenderlo dos veces no hace nada. */
    fun prender() {
        if (tts != null) return
        val avisar = CompletableDeferred<Boolean>().also { lista = it }
        tts = TextToSpeech(contexto) { estado ->
            val ok = estado == TextToSpeech.SUCCESS && elegirIdioma()
            android.util.Log.i(ETIQUETA, "motor listo=$ok idioma=$idioma")
            avisar.complete(ok)
        }.also { motor ->
            motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { pendientes.remove(it)?.complete(true) }
                }
                @Deprecated("Android lo marca obsoleto, pero hay motores que solo llaman a este")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { pendientes.remove(it)?.complete(false) }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    android.util.Log.w(ETIQUETA, "error del motor codigo=$errorCode")
                    utteranceId?.let { pendientes.remove(it)?.complete(false) }
                }
            })
        }
    }

    /** «latam» o «espana», elegido en Personalizar. Cambiarlo con [cambiarRegion]. */
    private var region = "latam"

    /** Cambia el acento de la voz del telefono, si el motor lo tiene. */
    fun cambiarRegion(nueva: String) {
        if (nueva == region) return
        region = nueva
        if (tts != null && lista.isCompleted) {
            elegirIdioma()
            android.util.Log.i(ETIQUETA, "region=$region idioma=$idioma")
        }
    }

    /**
     * Espanol, en este orden. Latinoamerica: el del telefono si ya esta en
     * espanol (es-CO en el de CG) y despues los de America. España: es-ES
     * primero. Si el motor no tiene el pedido, el siguiente que tenga.
     */
    private fun elegirIdioma(): Boolean {
        val motor = tts ?: return false
        val delTelefono = Locale.getDefault().takeIf { it.language == "es" }
        val america = listOfNotNull(delTelefono?.takeIf { it.country != "ES" }) +
            listOf("es-US", "es-MX", "es-419").map(Locale::forLanguageTag)
        val espana = listOf(Locale.forLanguageTag("es-ES"))
        val candidatos = (if (region == "espana") espana + america else america + espana) +
            Locale.forLanguageTag("es")
        for (local in candidatos) {
            val disponible = runCatching { motor.isLanguageAvailable(local) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (disponible >= TextToSpeech.LANG_AVAILABLE) {
                val puesto = runCatching { motor.setLanguage(local) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (puesto >= TextToSpeech.LANG_AVAILABLE) {
                    idioma = local.toLanguageTag()
                    return true
                }
            }
        }
        return false
    }

    /**
     * Escribe [texto] como voz en [archivo] (WAV). Devuelve falso si el motor no
     * esta, no tiene espanol o tarda demasiado: en ese caso Nova no habla esa vez.
     */
    suspend fun sintetizar(texto: String, archivo: File, velocidad: Float = 1.0f): Boolean {
        val motor = tts ?: return false
        if (withTimeoutOrNull(ESPERA_MOTOR_MS) { lista.await() } != true) return false
        motor.setSpeechRate(velocidad)
        val id = "nova-${System.nanoTime()}"
        val hecho = CompletableDeferred<Boolean>()
        pendientes[id] = hecho
        archivo.parentFile?.mkdirs()
        val pedido = runCatching { motor.synthesizeToFile(texto, Bundle(), archivo, id) }
            .getOrDefault(TextToSpeech.ERROR)
        if (pedido != TextToSpeech.SUCCESS) {
            pendientes.remove(id)
            return false
        }
        val ok = withTimeoutOrNull(ESPERA_FRASE_MS) { hecho.await() } ?: false
        pendientes.remove(id)
        return ok && archivo.length() > 0
    }

    /** Apaga el motor y suelta lo que estuviera esperando. */
    fun apagar() {
        pendientes.values.forEach { it.complete(false) }
        pendientes.clear()
        runCatching { tts?.shutdown() }
        tts = null
        idioma = null
    }

    private companion object {
        const val ETIQUETA = "LifeMusicNova"
        const val ESPERA_MOTOR_MS = 5_000L
        const val ESPERA_FRASE_MS = 8_000L
    }
}
