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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cglabs.lifemusic.MainActivity
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.ConcursoRecordatorioKey
import com.cglabs.lifemusic.db.MusicDatabase
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.get
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Recordatorio diario del Reto de la semana: a las 8 de la noche, cuanto llevas
 * hoy, cuanto te queda hasta el tope y tu puesto. Local del todo: lo calcula el
 * telefono con lo que ya cuenta para el reto; no sale nada nuevo de el.
 *
 * Solo existe mientras dura el reto y para quien esta inscrito y no lo ha
 * apagado (interruptor en la pantalla del reto). Cada disparo programa el
 * siguiente, con la hora exacta del dia siguiente, asi no se va corriendo como
 * pasaria con un periodo fijo de 24 h. El ultimo es el del 19; el 20 ya no hay
 * nada que recordar. Si ya se llego al tope del dia, calla: no hay nada que hacer.
 */
class RecordatorioReto(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencias {
        fun database(): MusicDatabase
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!procede(context)) return Result.success()
        val participacion = ConcursoRepository.participacion(context) ?: return Result.success()

        val database = EntryPointAccessors.fromApplication(context, Dependencias::class.java).database()
        val hoy = Concurso.minutosHoy(Concurso.minutosPorDia(database))
        if (hoy < Concurso.TOPE_MIN_DIA) notificar(context, hoy, participacion.puesto)

        programar(context)
        return Result.success()
    }

    private fun notificar(context: Context, minutosHoy: Int, puesto: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, context.getString(R.string.concurso_titulo), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val quedan = Concurso.TOPE_MIN_DIA - minutosHoy
        val texto = when {
            minutosHoy == 0 && puesto > 0 -> context.getString(R.string.concurso_recordatorio_cero, puesto)
            minutosHoy == 0 -> context.getString(R.string.concurso_recordatorio_cero_sin_puesto)
            puesto > 0 -> context.getString(R.string.concurso_recordatorio_texto, minutosHoy, quedan, puesto)
            else -> context.getString(R.string.concurso_recordatorio_texto_sin_puesto, minutosHoy, quedan)
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
            .setContentTitle(context.getString(R.string.concurso_titulo))
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

    companion object {
        private const val NOMBRE = "recordatorio_reto"
        private const val CANAL = "reto"
        private const val ID = 1002
        private const val HORA = 20

        /** Inscrito, con el recordatorio encendido y el reto aun en marcha. */
        private fun procede(context: Context): Boolean =
            Concurso.visible() && !Concurso.hoy().isAfter(Concurso.FIN) &&
                ConcursoRepository.registrado(context) &&
                context.dataStore.get(ConcursoRecordatorioKey, true)

        /** Idempotente: deja programado el proximo de las 8 de la noche, o nada si ya no toca. */
        fun programar(context: Context) {
            if (!procede(context)) { cancelar(context); return }
            val ahora = ZonedDateTime.now(Concurso.ZONA)
            var proximo = ahora.withHour(HORA).withMinute(0).withSecond(0).withNano(0)
            if (!proximo.isAfter(ahora)) proximo = proximo.plusDays(1)
            if (proximo.toLocalDate().isAfter(Concurso.FIN)) { cancelar(context); return }
            val espera = Duration.between(ahora, proximo)
            val peticion = OneTimeWorkRequestBuilder<RecordatorioReto>()
                .setInitialDelay(espera.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOMBRE, ExistingWorkPolicy.REPLACE, peticion)
        }

        fun cancelar(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOMBRE)
        }
    }
}
