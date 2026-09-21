package com.cglabs.lifemusic.cast

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Las ultimas lineas del registro de Cast, en memoria, para que un probador
 * las copie desde la hoja de «Reproducir en…» y las mande por WhatsApp. Es la
 * unica forma de ver que contesto SU televisor sin telemetria: lo copia el
 * usuario, cuando quiere, y solo esto (aparatos de su red, mensajes del
 * protocolo; nada de cuentas ni canciones mas alla del titulo cargado).
 */
object DiagnosticoCast {
    private const val TOPE = 200
    private val lineas = ArrayDeque<String>(TOPE)
    private val hora = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Al logcat (tag LifeMusicCast) y al buffer. */
    fun log(texto: String, e: Throwable? = null) {
        if (e != null) android.util.Log.w("LifeMusicCast", texto, e) else android.util.Log.i("LifeMusicCast", texto)
        apuntar(if (e != null) "$texto: ${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}" else texto)
    }

    fun apuntar(texto: String) {
        synchronized(lineas) {
            if (lineas.size >= TOPE) lineas.removeFirst()
            lineas.addLast("${hora.format(Date())} $texto")
        }
    }

    fun texto(): String = synchronized(lineas) {
        buildString {
            appendLine("Life Music · diagnostico de Cast")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            appendLine("app ${com.cglabs.lifemusic.BuildConfig.VERSION_NAME} (${com.cglabs.lifemusic.BuildConfig.VERSION_CODE})")
            appendLine("----")
            lineas.forEach { appendLine(it) }
        }
    }

    fun vaciar() = synchronized(lineas) { lineas.clear() }
}
