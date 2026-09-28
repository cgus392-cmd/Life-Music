package com.cglabs.lifemusic.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.cglabs.lifemusic.playback.MusicService

/**
 * El widget Cristal liquido. Lo pinta LifeMusicWidgetManager (con el servicio en
 * marcha); los botones usan las mismas acciones que el widget Ambiente, que las
 * recibe MusicWidgetReceiver. Aqui solo se pide repintar al ponerlo o al cambiarle
 * el tamaño: la capsula de cristal se calcula para el tamaño exacto.
 */
class CristalWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        pedirRepintado(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        pedirRepintado(context)
    }

    companion object {
        /**
         * Solo si el servicio ya esta en marcha: arrancarlo desde un widget en segundo
         * plano no esta permitido en Android 12+ (BackgroundServiceStartNotAllowed).
         * Sin servicio se ve el diseño inicial hasta que se abre la app.
         */
        fun pedirRepintado(context: Context) {
            if (!MusicService.isRunning) return
            runCatching {
                context.startService(Intent(context, MusicService::class.java).setAction(MusicWidgetReceiver.ACTION_UPDATE_WIDGET))
            }
        }
    }
}
