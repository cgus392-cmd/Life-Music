package com.cglabs.lifemusic.appcore.updater

import android.content.Context
import com.cglabs.lifemusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 2.1.0 · Las novedades de cada version, en linea. Antes el historial y «Novedades de esta version»
 * salian solo de los boletines escritos en el codigo (Boletines.todos): la 2.0.1 y la 2.0.2 nunca
 * se escribieron y la app se quedo mostrando la 2.0 (lo vio CG en un A04s el 07-11). Ahora salen de
 * las mismas fichas que ya se publican con cada version (lifemusic.pages.dev/novedades/<v>.json) y
 * de su indice (indice.json). Se guardan en el telefono para verlas sin internet. Publicar la ficha
 * y sumarla al indice basta: no hay que tocar codigo.
 */
object NovedadesEnLinea {
    private const val INDICE = "https://lifemusic.pages.dev/novedades/indice.json"

    private fun carpeta(context: Context) = File(context.filesDir, "novedades").apply { mkdirs() }

    private fun bajar(url: String): String {
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "LifeMusic/" + BuildConfig.VERSION_NAME)
        }
        return try {
            if (conexion.responseCode !in 200..299) error("HTTP ${conexion.responseCode}")
            conexion.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexion.disconnect()
        }
    }

    /** Lo de la red si se puede (y se guarda); si no, lo guardado la ultima vez. */
    private fun conCopia(archivo: File, url: String): String? =
        runCatching { bajar(url).also { archivo.writeText(it) } }.getOrNull()
            ?: archivo.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    /** Las versiones publicadas con ficha, de la mas nueva a la mas vieja. */
    suspend fun indice(context: Context): List<String> = withContext(Dispatchers.IO) {
        val texto = conCopia(File(carpeta(context), "indice.json"), INDICE) ?: return@withContext emptyList()
        runCatching {
            val a = JSONArray(texto)
            (0 until a.length()).mapNotNull { a.optString(it).trim().removePrefix("v").ifEmpty { null } }
        }.getOrDefault(emptyList())
    }

    /** La ficha de una version, en el idioma del telefono. */
    suspend fun ficha(context: Context, version: String): NovedadesDeVersion? = withContext(Dispatchers.IO) {
        val v = version.removePrefix("v")
        val texto = conCopia(File(carpeta(context), "$v.json"), Novedades.url(v)) ?: return@withContext null
        Novedades.leer(texto, Locale.getDefault().language)
    }

    /** Ordena nombres de version como numeros: 2.0.10 va despues de 2.0.9. */
    val porVersion: Comparator<String> = Comparator { a, b ->
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        (0 until maxOf(x.size, y.size)).firstNotNullOfOrNull { i -> (x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }).takeIf { it != 0 } } ?: 0
    }
}
