package com.cglabs.lifemusic.concurso

import com.cglabs.lifemusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Una fila del ranking publico: solo apodo, minutos y puesto. */
data class PuestoRanking(val puesto: Int, val apodo: String, val minutos: Int)

/** Lo que el servidor sabe del concurso; puede corregir las fechas de la app. */
data class EstadoConcurso(
    val inicio: String,
    val fin: String,
    val topeMinDia: Int,
    val premio: String,
    val participantes: Int,
    /** Apodo del ganador y texto del anuncio; vacios hasta que CG los publique. */
    val ganadorApodo: String = "",
    val anuncio: String = "",
)

/** Respuesta de registrar/reportar: ok, o un error con nombre. */
data class RespuestaConcurso(
    val ok: Boolean,
    val error: String? = null,
    val minutos: Int? = null,
    val puesto: Int? = null,
    val participantes: Int? = null,
)

/**
 * Cliente minimo de Supabase para el concurso: cuatro funciones (RPC) y una
 * vista. La clave anon va en el APK a proposito: esta pensada para eso, y no
 * da acceso a nada que las funciones no permitan (RLS sin politicas).
 */
object ConcursoApi {
    private const val TAG = "ConcursoApi"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val cliente by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val base get() = BuildConfig.CONCURSO_URL.trimEnd('/')
    private val clave get() = BuildConfig.CONCURSO_ANON_KEY

    private fun peticion(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("apikey", clave)
            .header("Authorization", "Bearer $clave")
            .header("Accept", "application/json")

    private suspend fun rpc(nombre: String, cuerpo: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = peticion("$base/rest/v1/rpc/$nombre")
                .post(cuerpo.toString().toRequestBody(JSON))
                .build()
            cliente.newCall(req).execute().use { resp ->
                val texto = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Timber.tag(TAG).w("rpc %s -> %d %s", nombre, resp.code, texto.take(200))
                    return@withContext null
                }
                JSONObject(texto)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "rpc %s fallo", nombre)
            null
        }
    }

    private fun JSONObject.aRespuesta(): RespuestaConcurso = RespuestaConcurso(
        ok = optBoolean("ok", false),
        error = optString("error").takeIf { it.isNotEmpty() },
        minutos = if (has("minutos")) optInt("minutos") else null,
        puesto = if (has("puesto") && !isNull("puesto")) optInt("puesto") else null,
        participantes = if (has("participantes")) optInt("participantes") else null,
    )

    /** null = sin red o servidor caido; distinto de un error del concurso. */
    suspend fun registrar(id: String, secreto: String, apodo: String, nombre: String, correo: String): RespuestaConcurso? =
        rpc(
            "concurso_registrar",
            JSONObject()
                .put("p_id", id)
                .put("p_secreto", secreto)
                .put("p_apodo", apodo)
                .put("p_nombre", nombre)
                .put("p_correo", correo)
                .put("p_version", BuildConfig.VERSION_NAME),
        )?.aRespuesta()

    suspend fun reportar(id: String, secreto: String, porDia: Map<String, Int>): RespuestaConcurso? =
        rpc(
            "concurso_reportar",
            JSONObject()
                .put("p_id", id)
                .put("p_secreto", secreto)
                .put("p_por_dia", JSONObject(porDia)),
        )?.aRespuesta()

    suspend fun abandonar(id: String, secreto: String): Boolean =
        rpc("concurso_abandonar", JSONObject().put("p_id", id).put("p_secreto", secreto))
            ?.optBoolean("ok", false) ?: false

    suspend fun estado(): EstadoConcurso? =
        rpc("concurso_estado", JSONObject())?.let {
            EstadoConcurso(
                inicio = it.optString("inicio"),
                fin = it.optString("fin"),
                topeMinDia = it.optInt("tope_min_dia", Concurso.TOPE_MIN_DIA),
                premio = it.optString("premio", Concurso.PREMIO),
                participantes = it.optInt("participantes", 0),
                ganadorApodo = it.optString("ganador_apodo", "").takeUnless { s -> s == "null" }.orEmpty().trim(),
                anuncio = it.optString("anuncio", "").takeUnless { s -> s == "null" }.orEmpty().trim(),
            )
        }

    suspend fun ranking(limite: Int = 100): List<PuestoRanking>? = withContext(Dispatchers.IO) {
        try {
            val req = peticion("$base/rest/v1/concurso_ranking?select=puesto,apodo,minutos&order=puesto.asc&limit=$limite")
                .get()
                .build()
            cliente.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.tag(TAG).w("ranking -> %d", resp.code)
                    return@withContext null
                }
                val arr = JSONArray(resp.body?.string().orEmpty())
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    PuestoRanking(o.optInt("puesto"), o.optString("apodo"), o.optInt("minutos"))
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "ranking fallo")
            null
        }
    }
}
