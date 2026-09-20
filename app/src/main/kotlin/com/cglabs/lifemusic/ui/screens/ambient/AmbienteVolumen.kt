package com.cglabs.lifemusic.ui.screens.ambient

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Teclas fisicas de volumen mientras el modo ambiente esta en pantalla.
 *
 * Si la actividad deja pasar la tecla, Android muestra su panel de volumen,
 * que tapa el indicador propio del modo ambiente (y en Samsung, media letra).
 * Como hacen las historias de Instagram o WhatsApp: la actividad se queda la
 * tecla, cambia el volumen SIN interfaz del sistema, y solo se ve el
 * indicador de la pantalla. Fuera del modo ambiente no toca nada.
 */
object AmbienteVolumen {
    /** true mientras el modo ambiente esta en pantalla; lo pone la propia pantalla. */
    @Volatile var interceptarTeclas = false

    /** Sube con cada pulsacion atendida; la zona de volumen lo observa para asomar. */
    val pulsaciones = MutableStateFlow(0)

    /** Atiende una tecla de volumen. Devuelve true si se la quedo (tambien el ACTION_UP). */
    fun atender(context: Context, event: KeyEvent): Boolean {
        if (!interceptarTeclas) return false
        val direccion = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return false
        }
        // ACTION_DOWN se repite solo al mantener la tecla: cada repeticion es un paso.
        if (event.action == KeyEvent.ACTION_DOWN) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // flags = 0: sin panel del sistema. El aviso de cambio de volumen se
            // emite igual, y ademas se avisa aqui por si el fabricante no lo manda.
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direccion, 0)
            pulsaciones.value = pulsaciones.value + 1
        }
        return true
    }
}
