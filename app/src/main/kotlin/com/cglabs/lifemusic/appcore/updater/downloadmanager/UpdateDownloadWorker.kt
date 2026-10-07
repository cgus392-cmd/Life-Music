package com.cglabs.lifemusic.appcore.updater.downloadmanager

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cglabs.lifemusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Baja el APK de una version nueva (1.3.1, actualizador renovado; ver
 * docs/versiones/1.3.1/actualizador.md).
 *
 * - Publica el progreso de verdad: bytes bajados, total y velocidad (media
 *   movil), para que la pantalla y la notificacion digan «18,4 de 41 MB ·
 *   quedan 11 s».
 * - Si se corta la red, el archivo parcial se queda y el reintento sigue donde
 *   iba (Range); si el servidor no lo acepta, empieza de cero.
 * - Antes de darlo por bueno, lo verifica: tamano, huella (si se publico) y que
 *   sea Life Music y mas nueva que la instalada. Asi no se le ofrece al
 *   instalador un APK a medias («error al analizar el paquete»).
 *
 * Entrada: apk_url, version, file_size (texto para mostrar) y, si se saben,
 * bytes (tamano exacto) y sha256.
 */
class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
        val version = inputData.getString("version") ?: "?"
        val tamanoTexto = inputData.getString("file_size") ?: ""
        val esperados = inputData.getLong("bytes", -1L)
        val huella = inputData.getString("sha256")?.lowercase(Locale.ROOT)
        // Si la app no estaba abierta, el trabajo arranca sin que nadie haya preparado las notificaciones.
        DownloadNotificationManager.initialize(context)

        ponerEnPrimerPlano(version, tamanoTexto)

        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        // Restos del actualizador heredado de Echo, que guardaba en otra carpeta.
        File(base, "echo_updates").takeIf { it.exists() }?.deleteRecursively()
        val carpeta = File(base, "actualizaciones").apply { mkdirs() }
        val nombre = "lifemusic-" + version.removePrefix("v")
        // Solo se guarda la version que se esta bajando: lo demas sobra.
        carpeta.listFiles()?.filter { !it.name.startsWith(nombre) }?.forEach { it.delete() }
        val parcial = File(carpeta, "$nombre.apk.part")
        val listo = File(carpeta, "$nombre.apk")

        // Bajado antes y bueno: no se repite.
        if (listo.exists() && verificar(listo, esperados, huella) == null) {
            DownloadNotificationManager.showDownloadComplete(version, listo.absolutePath)
            return@withContext Result.success(workDataOf("file_path" to listo.absolutePath))
        }

        try {
            var desde = if (parcial.exists()) parcial.length() else 0L
            if (esperados > 0 && desde >= esperados) { parcial.delete(); desde = 0L }
            val conexion = abrir(apkUrl, desde)
            val codigo = conexion.responseCode
            val anexar = when {
                codigo == HttpURLConnection.HTTP_PARTIAL && desde > 0 -> true
                codigo == HttpURLConnection.HTTP_OK -> { desde = 0L; false } // no acepto seguir: desde cero
                codigo == 416 -> { conexion.disconnect(); parcial.delete(); return@withContext Result.retry() }
                else -> {
                    conexion.disconnect()
                    DownloadNotificationManager.showDownloadFailed(version, context.getString(R.string.server_error, codigo))
                    return@withContext if (codigo >= 500) Result.retry() else Result.failure()
                }
            }
            val largo = conexion.contentLengthLong
            val total = when {
                esperados > 0 -> esperados
                largo > 0 -> desde + largo
                else -> -1L
            }

            var bajados = desde
            var ultimoAviso = 0L
            var ultimaMedida = System.nanoTime()
            var bajadosMedida = bajados
            var velocidad = 0.0 // bytes por segundo, media movil
            FileOutputStream(parcial, anexar).use { salida ->
                conexion.inputStream.use { entrada ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) {
                            // El parcial se queda: el siguiente intento sigue desde aqui.
                            conexion.disconnect()
                            return@withContext Result.retry()
                        }
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        salida.write(buffer, 0, n)
                        bajados += n
                        val ahora = System.nanoTime()
                        val pasado = (ahora - ultimaMedida) / 1e9
                        if (pasado >= 0.5) {
                            val instantanea = (bajados - bajadosMedida) / pasado
                            velocidad = if (velocidad == 0.0) instantanea else velocidad * 0.7 + instantanea * 0.3
                            ultimaMedida = ahora
                            bajadosMedida = bajados
                            setProgress(
                                workDataOf(
                                    "progress" to (if (total > 0) bajados.toFloat() / total else 0f),
                                    "bajados" to bajados,
                                    "total" to total,
                                    "velocidad" to velocidad,
                                ),
                            )
                            if (ahora - ultimoAviso >= 1_000_000_000L) {
                                ultimoAviso = ahora
                                val porcentaje = if (total > 0) (bajados * 100 / total).toInt() else 0
                                DownloadNotificationManager.showDownloadProgress(
                                    porcentaje,
                                    version,
                                    ProgresoDeDescarga.detalle(context, bajados, total, velocidad),
                                )
                            }
                        }
                    }
                }
            }
            conexion.disconnect()

            listo.delete()
            if (!parcial.renameTo(listo)) throw IOException("no se pudo terminar el archivo")
            val problema = verificar(listo, esperados, huella)
            if (problema != null) {
                listo.delete()
                DownloadNotificationManager.showDownloadFailed(version, context.getString(R.string.update_descarga_danada))
                return@withContext Result.failure(workDataOf("error" to problema))
            }
            DownloadNotificationManager.showDownloadComplete(version, listo.absolutePath)
            Result.success(workDataOf("file_path" to listo.absolutePath))
        } catch (e: IOException) {
            // Red caida a mitad: el parcial se queda y WorkManager reintenta.
            DownloadNotificationManager.showDownloadFailed(version, e.message ?: context.getString(R.string.download_failed))
            Result.retry()
        } catch (e: Exception) {
            DownloadNotificationManager.showDownloadFailed(version, e.message ?: context.getString(R.string.download_failed))
            Result.failure()
        }
    }

    private suspend fun ponerEnPrimerPlano(version: String, tamanoTexto: String) {
        runCatching {
            val aviso = DownloadNotificationManager.getDownloadStartingNotification(version, tamanoTexto)
            setForeground(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ForegroundInfo(DownloadNotificationManager.NOTIFICATION_ID, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    ForegroundInfo(DownloadNotificationManager.NOTIFICATION_ID, aviso)
                },
            )
        }
    }

    /**
     * Abre la descarga desde el byte [desde]. Las redirecciones (GitHub manda a
     * su almacen) se siguen a mano para que el Range viaje en cada salto.
     */
    private fun abrir(direccion: String, desde: Long): HttpURLConnection {
        var url = URL(direccion)
        repeat(6) {
            val c = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "LifeMusic-actualizador")
                if (desde > 0) setRequestProperty("Range", "bytes=$desde-")
            }
            val codigo = c.responseCode
            if (codigo in 300..399) {
                val destino = c.getHeaderField("Location") ?: return c
                c.disconnect()
                url = URL(url, destino)
            } else {
                return c
            }
        }
        throw IOException("demasiadas redirecciones")
    }

    /** Null si el APK sirve; si no, por que (para el registro). */
    private fun verificar(apk: File, esperados: Long, huella: String?): String? {
        if (!apk.exists() || apk.length() == 0L) return "vacio"
        if (esperados > 0 && apk.length() != esperados) return "tamano ${apk.length()} de $esperados"
        if (huella != null && sha256(apk) != huella) return "huella"
        val pm = context.packageManager
        val info = runCatching { pm.getPackageArchiveInfo(apk.absolutePath, 0) }.getOrNull() ?: return "no es un APK"
        if (info.packageName != context.packageName) return "otro paquete: ${info.packageName}"
        val instalada = runCatching { PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(context.packageName, 0)) }.getOrDefault(0L)
        if (PackageInfoCompat.getLongVersionCode(info) <= instalada) return "no es mas nueva"
        return null
    }

    private fun sha256(archivo: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { entrada ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = entrada.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** El texto del progreso: «18,4 de 41 MB · quedan 11 s». */
object ProgresoDeDescarga {
    private fun mb(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))

    fun detalle(context: Context, bajados: Long, total: Long, velocidad: Double): String {
        if (total <= 0) return context.getString(R.string.update_progreso_sin_total, mb(bajados), mb(velocidad.toLong()))
        val restante = if (velocidad > 1) ((total - bajados) / velocidad).toLong().coerceAtLeast(0) else -1L
        val tiempo = when {
            restante < 0 -> "…"
            restante < 60 -> context.getString(R.string.update_segundos, restante)
            else -> context.getString(R.string.update_minutos, (restante + 30) / 60)
        }
        return context.getString(R.string.update_progreso_detalle, mb(bajados), mb(total), tiempo)
    }
}
