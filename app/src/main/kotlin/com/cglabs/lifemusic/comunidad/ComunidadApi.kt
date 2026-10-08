package com.cglabs.lifemusic.comunidad

import com.cglabs.lifemusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Lo que el servidor cuenta del prerregistro. Nunca trae nombres ni correos: solo el numero. */
data class EstadoPrerregistro(
    val empezo: Boolean,
    val abierto: Boolean,
    /** Pasaron los dias y no se llego a la meta: sigue abierto hasta llegar. */
    val alargado: Boolean,
    /** Fin de los dias de prerregistro (ISO 8601), o null si no ha empezado. */
    val fin: String?,
    val meta: Int,
    val contador: Int,
    val metaAlcanzada: Boolean,
    val premio: String,
    /** El video de la pagina (lifemusic.pages.dev/concurso/video.mp4): la app lo pide de ahi. */
    val video: String,
)

/** Respuesta de prerregistrarse: ok, o el error con nombre que devuelve el servidor. */
data class RespuestaPrerregistro(
    val ok: Boolean,
    val error: String? = null,
    val yaEstabas: Boolean = false,
    val contador: Int? = null,
)

/** Promedio publico: solo visible desde 25 calificaciones. */
data class ResumenCalificaciones(val total: Int, val visible: Boolean, val promedio: Double?)

/**
 * Cliente del Worker lifemusic-comunidad (Cloudflare, plan gratis): el
 * prerregistro del concurso y la calificacion con estrellas (1.3.1). Fichas:
 * docs/versiones/1.3.1/prerregistro.md y calificacion.md.
 *
 * Nada de aqui sale solo: cada escritura es porque la persona toco un boton.
 */
object ComunidadApi {
    const val SERVIDOR = "https://lifemusic-comunidad.cho--usic.workers.dev"
    private const val TAG = "ComunidadApi"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val cliente by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** GET o POST; el cuerpo de un error (400, 409) tambien se lee: trae el motivo. null = sin red. */
    private suspend fun pedir(ruta: String, cuerpo: JSONObject? = null): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(SERVIDOR + ruta)
                .header("Accept", "application/json")
                .header("User-Agent", "LifeMusic/${BuildConfig.VERSION_NAME}")
                .apply { if (cuerpo != null) post(cuerpo.toString().toRequestBody(JSON)) }
                .build()
            cliente.newCall(req).execute().use { resp ->
                val texto = resp.body.string()
                if (!texto.trimStart().startsWith("{")) {
                    Timber.tag(TAG).w("%s -> %d sin JSON", ruta, resp.code)
                    return@withContext null
                }
                JSONObject(texto)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "%s fallo", ruta)
            null
        }
    }

    fun leerEstado(j: JSONObject): EstadoPrerregistro = EstadoPrerregistro(
        empezo = j.optBoolean("empezo", false),
        abierto = j.optBoolean("abierto", false),
        alargado = j.optBoolean("alargado", false),
        fin = j.optString("fin").takeIf { it.isNotEmpty() && it != "null" },
        meta = j.optInt("meta", 100),
        contador = j.optInt("contador", 0),
        metaAlcanzada = j.optBoolean("metaAlcanzada", false),
        premio = j.optString("premio"),
        video = j.optString("video"),
    )

    suspend fun estado(): EstadoPrerregistro? = pedir("/prerregistro/estado")?.takeIf { it.has("abierto") }?.let(::leerEstado)

    suspend fun prerregistrar(id: String, secreto: String, nombre: String, correo: String): RespuestaPrerregistro? =
        pedir(
            "/prerregistro",
            JSONObject()
                .put("id", id)
                .put("secreto", secreto)
                .put("nombre", nombre)
                .put("correo", correo)
                .put("mayor", true)
                .put("acepta", true)
                .put("version", BuildConfig.VERSION_NAME),
        )?.let { j ->
            RespuestaPrerregistro(
                ok = j.optBoolean("ok", false),
                error = j.optString("error").takeIf { it.isNotEmpty() },
                yaEstabas = j.optBoolean("yaEstabas", false),
                contador = if (j.has("contador")) j.optInt("contador") else null,
            )
        }

    suspend fun salir(id: String, secreto: String): Boolean =
        pedir("/prerregistro/salir", JSONObject().put("id", id).put("secreto", secreto))?.optBoolean("ok", false) == true

    suspend fun calificar(id: String, estrellas: Int, comentario: String?): Boolean =
        pedir(
            "/calificar",
            JSONObject()
                .put("id", id)
                .put("estrellas", estrellas)
                .put("comentario", comentario?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("version", BuildConfig.VERSION_NAME)
                .put("idioma", Locale.getDefault().language),
        )?.optBoolean("ok", false) == true

    suspend fun resumen(): ResumenCalificaciones? = pedir("/calificaciones/resumen")?.let { j ->
        ResumenCalificaciones(
            total = j.optInt("total", 0),
            visible = j.optBoolean("visible", false),
            promedio = if (j.has("promedio")) j.optDouble("promedio") else null,
        )
    }
}
