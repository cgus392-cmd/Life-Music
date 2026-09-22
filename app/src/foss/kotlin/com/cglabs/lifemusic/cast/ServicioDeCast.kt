package com.cglabs.lifemusic.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cglabs.lifemusic.MainActivity
import com.cglabs.lifemusic.R

/**
 * Servicio en primer plano que vive solo mientras se transmite al TV.
 *
 * Por que existe: transmitiendo, el reproductor del telefono esta en pausa y
 * Media3 baja MusicService de primer plano. Sin ningun servicio en primer
 * plano, a los cinco segundos de apagar la pantalla Android cierra a la fuerza
 * las conexiones de la app (`InetDiagMessage: Destroyed live tcp sockets`) y
 * el TV se queda sin mando. Este servicio, del mismo tipo que la reproduccion
 * (mediaPlayback), mantiene la app viva para el sistema con una notificacion
 * «Sonando en X» que sirve de mando minimo: tocar abre la app, y hay un boton
 * para devolver la musica al telefono.
 */
class ServicioDeCast : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_DEVOLVER) {
            alDevolver?.invoke()
            return START_NOT_STICKY
        }
        val nombre = intent?.getStringExtra(EXTRA_NOMBRE).orEmpty()
        val notificacion = construir(nombre)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(ID, notificacion)
        }
        return START_NOT_STICKY
    }

    private fun construir(nombre: String): Notification {
        val gestor = getSystemService(NotificationManager::class.java)
        gestor.createNotificationChannel(
            NotificationChannel(CANAL, getString(R.string.cast_transmitir), NotificationManager.IMPORTANCE_LOW),
        )
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val devolver = PendingIntent.getService(
            this, 1, Intent(this, ServicioDeCast::class.java).setAction(ACCION_DEVOLVER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.cast_connected)
            .setContentTitle(getString(R.string.cast_conectado_a, nombre))
            .setContentText(getString(R.string.cast_notificacion_texto))
            .setContentIntent(abrir)
            .addAction(0, getString(R.string.cast_desconectar), devolver)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val ID = 7301
        private const val CANAL = "cast"
        private const val EXTRA_NOMBRE = "nombre"
        private const val ACCION_DEVOLVER = "com.cglabs.lifemusic.cast.DEVOLVER"

        /** Lo pone CastConnectionHandler mientras transmite: el boton de la notificacion llama aqui. */
        @Volatile var alDevolver: (() -> Unit)? = null

        fun iniciar(context: Context, nombreAparato: String) {
            val intent = Intent(context, ServicioDeCast::class.java).putExtra(EXTRA_NOMBRE, nombreAparato)
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, intent) }
                .onFailure { DiagnosticoCast.log("servicio de cast no arranco", it) }
        }

        fun parar(context: Context) {
            alDevolver = null
            runCatching { context.stopService(Intent(context, ServicioDeCast::class.java)) }
        }
    }
}
