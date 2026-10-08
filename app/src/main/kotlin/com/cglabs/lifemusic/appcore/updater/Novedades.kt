package com.cglabs.lifemusic.appcore.updater

import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lo que cuenta una version nueva antes de instalarla (1.3.1, actualizador
 * renovado; ver docs/versiones/1.3.1/actualizador.md).
 *
 * La version que aun no esta en el telefono no puede venir dentro de la app:
 * se publica en lifemusic.pages.dev/novedades/<version>.json junto a su imagen.
 * Si ese archivo no esta, el actualizador sigue con el texto de la publicacion
 * de GitHub, como antes. Las versiones ya instaladas salen de Boletines.
 */
data class FuncionNueva(val icono: String, val titulo: String, val texto: String)

data class ApkPublicado(val bytes: Long, val sha256: String?)

data class NovedadesDeVersion(
    val version: String,
    val imagen: String?,
    val resumen: String?,
    val funciones: List<FuncionNueva>,
    val arreglos: List<String>,
    /** Por nombre del archivo publicado: tamano y huella, para verificar la descarga. */
    val apks: Map<String, ApkPublicado>,
)

/** Un APK adjunto a una publicacion de GitHub. */
data class ApkAdjunto(val nombre: String, val url: String, val bytes: Long)

object Novedades {
    private const val BASE = "https://lifemusic.pages.dev/novedades/"

    fun url(version: String): String = BASE + version.removePrefix("v") + ".json"

    private val _disponibles = MutableStateFlow<NovedadesDeVersion?>(null)
    /** Las de la ultima version nueva que encontro la comprobacion; null si no hay o no se publicaron. */
    val disponibles: StateFlow<NovedadesDeVersion?> = _disponibles.asStateFlow()

    internal fun recordar(n: NovedadesDeVersion?) { _disponibles.value = n }

    /**
     * Lee el JSON en [idioma] ("es", "en"...). Si ese idioma no esta, usa el
     * ingles y despues el espanol. Null si el JSON no sirve o no trae nada que
     * contar.
     */
    fun leer(json: String, idioma: String): NovedadesDeVersion? = runCatching {
        val raiz = JSONObject(json)
        val textos = raiz.optJSONObject(idioma.take(2).lowercase())
            ?: raiz.optJSONObject("en")
            ?: raiz.optJSONObject("es")
            ?: return null
        val funciones = textos.optJSONArray("funciones").objetos().mapNotNull { f ->
            val titulo = f.optString("titulo").trim()
            if (titulo.isEmpty()) null else FuncionNueva(f.optString("icono", ""), titulo, f.optString("texto").trim())
        }
        val arreglos = textos.optJSONArray("arreglos").textos()
        val resumen = textos.optString("resumen").trim().ifEmpty { null }
        if (funciones.isEmpty() && arreglos.isEmpty() && resumen == null) return null
        val apks = mutableMapOf<String, ApkPublicado>()
        raiz.optJSONObject("apk")?.let { a ->
            for (nombre in a.keys()) {
                val o = a.optJSONObject(nombre) ?: continue
                apks[nombre] = ApkPublicado(o.optLong("bytes", -1L), o.optString("sha256").trim().lowercase().ifEmpty { null })
            }
        }
        NovedadesDeVersion(
            version = raiz.optString("version").removePrefix("v"),
            imagen = raiz.optString("imagen").trim().ifEmpty { null },
            resumen = resumen,
            funciones = funciones,
            arreglos = arreglos,
            apks = apks,
        )
    }.getOrNull()

    /** El icono de la app para un nombre del JSON; uno generico si no se conoce. */
    fun icono(nombre: String): Int = when (nombre) {
        "tv" -> R.drawable.cast_tv
        "cast" -> R.drawable.cast
        "palette" -> R.drawable.palette
        "update" -> R.drawable.update
        "music_note" -> R.drawable.music_note
        "tune" -> R.drawable.tune
        "equalizer" -> R.drawable.equalizer
        "graphic_eq" -> R.drawable.graphic_eq
        "lyrics" -> R.drawable.lyrics
        "download" -> R.drawable.download
        "speed" -> R.drawable.speed
        "link" -> R.drawable.link
        "radio" -> R.drawable.radio
        "queue_music" -> R.drawable.queue_music
        "album" -> R.drawable.album
        "shuffle" -> R.drawable.shuffle
        "replay" -> R.drawable.replay
        "timer" -> R.drawable.timer
        "star" -> R.drawable.estrella_llena
        "trophy" -> R.drawable.trophy
        else -> R.drawable.star
    }

    /**
     * El APK que conviene a este telefono ([abis] = Build.SUPPORTED_ABIS, el
     * preferido primero): el de arm64 si su primer ABI es arm64-v8a y la
     * publicacion lo trae (pesa ~40 % menos que el universal); si no, el
     * universal ([Repo.APK_ASSET]); si tampoco, cualquier .apk que no sea de
     * depuracion. Los dos salen de la misma variante con la misma firma y el
     * mismo versionCode, asi que uno instala encima del otro.
     */
    fun elegirApk(adjuntos: List<ApkAdjunto>, abis: List<String>): ApkAdjunto? {
        val apks = adjuntos.filter { it.nombre.endsWith(".apk", ignoreCase = true) && !it.nombre.contains("debug", ignoreCase = true) }
        if (abis.firstOrNull() == "arm64-v8a") {
            apks.firstOrNull { it.nombre.equals(Repo.APK_ARM64_ASSET, ignoreCase = true) }?.let { return it }
        }
        return apks.firstOrNull { it.nombre.equals(Repo.APK_ASSET, ignoreCase = true) }
            ?: apks.firstOrNull { !it.nombre.equals(Repo.APK_ARM64_ASSET, ignoreCase = true) }
            ?: apks.firstOrNull().takeIf { abis.firstOrNull() == "arm64-v8a" }
    }

    /** Los adjuntos de una publicacion de GitHub (el arreglo «assets» de su API). */
    fun adjuntos(assets: JSONArray?): List<ApkAdjunto> = assets.objetos().mapNotNull { a ->
        val nombre = a.optString("name")
        val url = a.optString("browser_download_url")
        if (nombre.isEmpty() || url.isEmpty()) null else ApkAdjunto(nombre, url, a.optLong("size", -1L))
    }

    private fun JSONArray?.objetos(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.textos(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().ifEmpty { null } }
}
