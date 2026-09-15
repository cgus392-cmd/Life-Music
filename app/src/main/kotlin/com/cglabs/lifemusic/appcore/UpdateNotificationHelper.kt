package com.cglabs.lifemusic.appcore

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cglabs.lifemusic.MainActivity
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.appcore.updater.getNotifiedVersion
import com.cglabs.lifemusic.appcore.updater.getUpdateNotificationsSetting
import com.cglabs.lifemusic.appcore.updater.saveNotifiedVersion

object UpdateNotificationHelper {
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 1001

    /**
     * Avisa de una version nueva una sola vez por version, y solo si el
     * usuario tiene activados los avisos. Lo llaman la comprobacion del
     * arranque y la de segundo plano, que si no se pisarian: la misma
     * notificacion volviendo cada 12 horas o en cada apertura de la app.
     */
    fun avisarSiHaceFalta(context: Context, versionName: String) {
        if (!getUpdateNotificationsSetting(context)) return
        if (getNotifiedVersion(context) == versionName) return
        saveNotifiedVersion(context, versionName)
        showUpdateNotification(context, versionName)
    }

    fun showUpdateNotification(context: Context, versionName: String) {
        val nm = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_updates_title),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(channel)
        }

                // Al tocarla se abre el actualizador de la app, el mismo de Ajustes,
        // que descarga e instala. Antes abria la web de GitHub en el navegador.
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_UPDATE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_nobg)
            .setContentTitle(context.getString(R.string.update_available_title))
            .setContentText(versionName)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
        }
    }
}
