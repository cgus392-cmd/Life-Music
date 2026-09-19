package com.cglabs.lifemusic.concurso

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
import androidx.datastore.preferences.core.edit
import com.cglabs.lifemusic.MainActivity
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.ConcursoAnuncioKey
import com.cglabs.lifemusic.constants.ConcursoAnuncioNotificadoKey
import com.cglabs.lifemusic.constants.ConcursoGanadorKey
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.get

/**
 * El anuncio del ganador no vive en la app: CG lo escribe en el servidor
 * (columnas `ganador_apodo` y `anuncio` de la configuracion) cuando lo tiene
 * verificado, y la app lo lee al abrir la pantalla del reto o al arrancar tras
 * el fin. Asi se anuncia sin publicar una version y se puede corregir.
 *
 * Se avisa con notificacion una sola vez por ganador y solo a quien participo:
 * el resto lo ve en la pantalla si entra.
 */
object AnuncioDelReto {
    private const val CANAL = "reto"
    private const val ID = 1003

    /** Lo ultimo leido del servidor, tal como lo guarda el telefono. */
    data class Anuncio(val ganador: String, val texto: String) {
        val hay: Boolean get() = ganador.isNotBlank()
    }

    fun guardado(context: Context): Anuncio = Anuncio(
        ganador = context.dataStore.get(ConcursoGanadorKey, ""),
        texto = context.dataStore.get(ConcursoAnuncioKey, ""),
    )

    /**
     * Pregunta al servidor y guarda lo que diga. Solo tiene sentido con el
     * modulo visible; fuera de red devuelve lo guardado.
     */
    suspend fun comprobar(context: Context): Anuncio {
        if (!Concurso.visible()) return guardado(context)
        val estado = ConcursoApi.estado() ?: return guardado(context)
        val nuevo = Anuncio(estado.ganadorApodo, estado.anuncio)
        context.dataStore.edit {
            it[ConcursoGanadorKey] = nuevo.ganador
            it[ConcursoAnuncioKey] = nuevo.texto
        }
        if (nuevo.hay) {
            val yaAvisado = context.dataStore.get(ConcursoAnuncioNotificadoKey, "")
            val participa = ConcursoRepository.participacion(context)
            if (participa != null && !yaAvisado.equals(nuevo.ganador, ignoreCase = true)) {
                notificar(context, nuevo, esMio = participa.apodo.equals(nuevo.ganador, ignoreCase = true))
                context.dataStore.edit { it[ConcursoAnuncioNotificadoKey] = nuevo.ganador }
            }
        }
        return nuevo
    }

    private fun notificar(context: Context, anuncio: Anuncio, esMio: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, context.getString(R.string.concurso_titulo), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val titulo = context.getString(R.string.concurso_ganador_notif_titulo)
        val texto = when {
            esMio -> context.getString(R.string.concurso_ganador_eres_tu)
            anuncio.texto.isNotBlank() -> context.getString(R.string.concurso_ganador_notif_texto, anuncio.ganador) + " " + anuncio.texto
            else -> context.getString(R.string.concurso_ganador_notif_texto, anuncio.ganador)
        }
        val abrir = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_RETO
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendiente = PendingIntent.getActivity(
            context, ID, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.trophy)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(ID, notificacion)
        }
    }
}
