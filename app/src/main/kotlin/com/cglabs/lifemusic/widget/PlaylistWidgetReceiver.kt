/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.cglabs.lifemusic.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.cglabs.lifemusic.playback.MusicService
import timber.log.Timber

/**
 * El widget «Tu musica» (antes «Listas»): el saludo y cuatro accesos en cristal —
 * mezclar Me gusta, Me gusta, Descargas y Buscar. Se conserva el nombre de la clase
 * para que los widgets ya puestos en el escritorio pasen solos al diseño nuevo.
 *
 * El de Listas mandaba al servicio una accion (PLAY_TARGET) que el servicio nunca
 * atendia: tocar una lista no hacia nada. Este se monta sin el servicio y solo lo
 * despierta para mezclar, desde un toque (permitido en segundo plano).
 */
class PlaylistWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val vista = LifeMusicWidgetManager.vistaTuMusica(context, null, VERDE_LIFE)
        appWidgetIds.forEach { runCatching { appWidgetManager.updateAppWidget(it, vista) } }
        // Si ya suena algo, que el servicio le ponga el fondo de la caratula.
        CristalWidgetReceiver.pedirRepintado(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MEZCLAR) {
            try {
                context.startService(Intent(context, MusicService::class.java).setAction(ACTION_MEZCLAR))
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "No se pudo mezclar Me gusta desde el widget")
                runCatching { LifeMusicWidgetManager.abrirApp(context).send() }
            }
        }
    }

    companion object {
        private const val TAG = "PlaylistWidgetReceiver"

        /** Mezclar las canciones que te gustan (lo atiende MusicService). */
        const val ACTION_MEZCLAR = "com.cglabs.lifemusic.widget.tumusica.MEZCLAR"

        private const val VERDE_LIFE = 0xFF7FD6A6.toInt()
    }
}
