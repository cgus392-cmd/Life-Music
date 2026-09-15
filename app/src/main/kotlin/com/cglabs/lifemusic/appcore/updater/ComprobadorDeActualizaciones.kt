package com.cglabs.lifemusic.appcore.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cglabs.lifemusic.appcore.UpdateNotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Comprueba en segundo plano si hay una version nueva y avisa por
 * notificacion, aunque la app lleve dias sin abrirse. Al tocar la
 * notificacion se abre el actualizador de la app, que descarga e instala.
 *
 * Existe porque hay usuarios que no saben actualizar desde fuera y las
 * versiones salen seguidas (correcciones de los informes que mandan).
 * WorkManager guarda el trabajo entre reinicios del telefono; solo hay que
 * programarlo al arrancar la app y quitarlo si el usuario apaga la
 * comprobacion automatica.
 */
class ComprobadorDeActualizaciones(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!getAutoUpdateCheckSetting(context)) return Result.success()
        var resultado: Result = Result.success()
        checkForUpdate(
            context = context,
            onSuccess = { version, hayNueva, _, _, _, _, _, _ ->
                saveUpdateAvailableState(context, hayNueva)
                if (hayNueva) UpdateNotificationHelper.avisarSiHaceFalta(context, version)
            },
            onError = { resultado = Result.retry() },
        )
        return resultado
    }

    companion object {
        private const val NOMBRE = "comprobar_actualizaciones"
        private const val CADA_HORAS = 12L

        /** Idempotente: si ya esta programado, lo actualiza sin reiniciar su reloj. */
        fun programar(context: Context) {
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val peticion = PeriodicWorkRequestBuilder<ComprobadorDeActualizaciones>(CADA_HORAS, TimeUnit.HOURS)
                .setConstraints(restricciones)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.UPDATE, peticion)
        }

        fun cancelar(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOMBRE)
        }
    }
}
