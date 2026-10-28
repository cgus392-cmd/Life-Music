package com.cglabs.lifemusic.bandas

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import com.cglabs.lifemusic.constants.BandasActivaKey
import com.cglabs.lifemusic.constants.BandasCodigosKey
import com.cglabs.lifemusic.constants.BandasColorKey
import com.cglabs.lifemusic.constants.BandasIdKey
import com.cglabs.lifemusic.constants.BandasNombreKey
import com.cglabs.lifemusic.constants.BandasSecretoKey
import com.cglabs.lifemusic.listentogether.BandaConexion
import com.cglabs.lifemusic.listentogether.ListenTogetherManager
import com.cglabs.lifemusic.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Bandas (2.0, beta; ficha docs/versiones/2.0/bandas.md; maqueta «la banda es un
 * acorde» en Brand/maquetas/2026-10-28-bandas): grupos de 2 a 8 amigos que
 * escuchan juntos con En vivo. El servidor es el relevo propio
 * (`enlace/src/bandas.js`), gratis y sin guardar lo que suena.
 *
 * Sin cuentas: la primera vez el telefono inventa un id y un secreto. El secreto
 * solo viaja en la cabecera Authorization, nunca en la direccion.
 */
object Bandas {
    const val RELEVO = "https://lifemusic-enlace.cho--usic.workers.dev"
    private const val RELEVO_WS = "wss://lifemusic-enlace.cho--usic.workers.dev"
    private val CODIGO = Regex("^[2-9A-HJKMNP-Z]{6}$")
    const val MAX_MIEMBROS = 8

    /** Los colores Pulso de la maqueta: cada persona y cada banda elige uno. */
    val COLORES: Map<String, Color> = linkedMapOf(
        "coral" to Color(0xFFFF6B3D),
        "ambar" to Color(0xFFF2B35E),
        "rosa" to Color(0xFFFF7A9A),
        "menta" to Color(0xFF6FD3B0),
        "cielo" to Color(0xFF7EB6FF),
        "lila" to Color(0xFFB79BFF),
        "arena" to Color(0xFFD8C3A0),
        "lima" to Color(0xFFC6E06A),
    )

    fun color(nombre: String?): Color = COLORES[nombre] ?: COLORES.getValue("coral")

    /** Las 6 reacciones propias, en el orden de la maqueta. */
    val REACCIONES = listOf("suena", "me_encanta", "otra_vez", "subele", "a_bailar", "escalofrio")

    /** El codigo escrito a mano o copiado («k7m qzp», «K7M-QZP») queda limpio; null si no sirve. */
    fun limpiarCodigo(texto: String): String? =
        texto.uppercase().filter { it.isLetterOrDigit() }.takeIf { CODIGO.matches(it) }

    /** `lifemusic.app/banda/CODIGO`, el enlace del QR. */
    fun codigoDeEnlace(uri: Uri): String? {
        if (uri.host !in setOf("lifemusic.app", "www.lifemusic.app")) return null
        if (uri.pathSegments.firstOrNull() != "banda") return null
        return limpiarCodigo(uri.pathSegments.getOrNull(1) ?: return null)
    }

    fun enlace(codigo: String) = "https://lifemusic.app/banda/$codigo"

    // ---------- Quien soy ----------

    data class Yo(val id: String, val secreto: String, val nombre: String, val color: String) {
        val autorizacion get() = "Banda $id.$secreto"
        val conPerfil get() = nombre.isNotBlank()
    }

    private fun hex(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it) }
    }

    private val inventando = Mutex()

    /** Mi identidad; la inventa la primera vez (una sola, aunque la pidan dos pantallas a la vez). */
    suspend fun yo(context: Context): Yo = inventando.withLock {
        val datos = context.dataStore.data.first()
        var id = datos[BandasIdKey].orEmpty()
        var secreto = datos[BandasSecretoKey].orEmpty()
        if (id.length != 32 || secreto.length != 64) {
            id = hex(16)
            secreto = hex(32)
            context.dataStore.edit {
                it[BandasIdKey] = id
                it[BandasSecretoKey] = secreto
            }
        }
        Yo(id, secreto, datos[BandasNombreKey].orEmpty(), datos[BandasColorKey] ?: "coral")
    }

    suspend fun guardarPerfil(context: Context, nombre: String, color: String) {
        context.dataStore.edit {
            it[BandasNombreKey] = nombre.trim().take(20)
            it[BandasColorKey] = color
        }
    }

    suspend fun codigos(context: Context): Set<String> = context.dataStore.data.first()[BandasCodigosKey].orEmpty()

    private suspend fun recordar(context: Context, codigo: String) {
        context.dataStore.edit {
            it[BandasCodigosKey] = it[BandasCodigosKey].orEmpty() + codigo
            // Quien entra a una banda la ve en el menu aunque no haya prendido el interruptor.
            it[BandasActivaKey] = true
        }
    }

    suspend fun olvidar(context: Context, codigo: String) {
        context.dataStore.edit { it[BandasCodigosKey] = it[BandasCodigosKey].orEmpty() - codigo }
    }

    // ---------- Lo que manda el relevo ----------

    @Serializable
    data class Miembro(
        val id: String,
        val nombre: String,
        val inicial: String,
        val color: String,
        val desde: String? = null,
    )

    @Serializable
    data class CancionEnVivo(val titulo: String, val artista: String, val caratula: String? = null)

    @Serializable
    data class EnVivo(val anfitrion: String = "", val cancion: CancionEnVivo? = null, val escuchando: Int = 0)

    /** La banda vista por mi. Con estado «pendiente», [jefe] es el nombre de quien aprueba. */
    @Serializable
    data class Vista(
        val codigo: String,
        val estado: String,
        val nombre: String = "",
        val color: String = "coral",
        val jefe: String = "",
        val yo: String = "",
        val miembros: List<Miembro> = emptyList(),
        val pedidos: List<Miembro> = emptyList(),
        val envivo: EnVivo? = null,
        val colores: List<String> = emptyList(),
    ) {
        val soyJefe get() = estado == "miembro" && jefe == yo
    }

    @Serializable
    data class EstadoCorto(
        val codigo: String,
        val estado: String,
        val nombre: String = "",
        val color: String = "coral",
        val miembros: Int = 0,
        val pedidos: Int = 0,
        val envivo: EnVivo? = null,
    )

    /** El error del relevo («llena», «no-existe», «rechazado», «muchos-pedidos») o «red» sin conexion. */
    class ErrorDeBanda(val codigo: String) : Exception(codigo)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    private suspend fun llamar(yo: Yo, metodo: String, ruta: String, cuerpo: JsonObject? = null): String =
        withContext(Dispatchers.IO) {
            val tipo = "application/json".toMediaType()
            val body = when {
                cuerpo != null -> cuerpo.toString().toRequestBody(tipo)
                metodo == "POST" -> "{}".toRequestBody(tipo)
                else -> null
            }
            val pedido = Request.Builder().url(RELEVO + ruta).header("Authorization", yo.autorizacion).method(metodo, body).build()
            val respuesta = try {
                http.newCall(pedido).execute()
            } catch (e: Exception) {
                throw ErrorDeBanda("red")
            }
            respuesta.use { r ->
                val texto = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val codigo = runCatching { json.parseToJsonElement(texto).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                    // Un rechazado ve la banda con estado «rechazado» y codigo 403.
                    val estado = runCatching { json.parseToJsonElement(texto).jsonObject["estado"]?.jsonPrimitive?.content }.getOrNull()
                    throw ErrorDeBanda(codigo ?: estado ?: "red")
                }
                texto
            }
        }

    private fun persona(yo: Yo) = JsonObject(mapOf("nombre" to JsonPrimitive(yo.nombre), "color" to JsonPrimitive(yo.color)))

    suspend fun crear(context: Context, nombre: String, color: String): Result<Vista> = runCatching {
        val yo = yo(context)
        val cuerpo = JsonObject(mapOf("nombre" to JsonPrimitive(nombre.trim()), "color" to JsonPrimitive(color), "yo" to persona(yo)))
        json.decodeFromString(Vista.serializer(), llamar(yo, "POST", "/bandas", cuerpo)).also { recordar(context, it.codigo) }
    }

    suspend fun pedir(context: Context, codigo: String): Result<Vista> = runCatching {
        val yo = yo(context)
        json.decodeFromString(Vista.serializer(), llamar(yo, "POST", "/banda/$codigo/pedir", JsonObject(mapOf("yo" to persona(yo)))))
            .also { recordar(context, codigo) }
    }

    suspend fun ver(context: Context, codigo: String): Result<Vista> = runCatching {
        try {
            json.decodeFromString(Vista.serializer(), llamar(yo(context), "GET", "/banda/$codigo"))
        } catch (e: ErrorDeBanda) {
            // La banda se borro o me quitaron: deja de estar en mi lista.
            if (e.codigo == "no-existe") olvidar(context, codigo)
            throw e
        }
    }

    /** aprobar, rechazar o quitar a alguien (solo quien la creo). */
    suspend fun decidir(context: Context, codigo: String, accion: String, id: String): Result<Vista> = runCatching {
        json.decodeFromString(Vista.serializer(), llamar(yo(context), "POST", "/banda/$codigo/$accion", JsonObject(mapOf("id" to JsonPrimitive(id)))))
    }

    suspend fun salir(context: Context, codigo: String): Result<Unit> = runCatching {
        llamar(yo(context), "POST", "/banda/$codigo/salir")
        olvidar(context, codigo)
    }

    suspend fun borrar(context: Context, codigo: String): Result<Unit> = runCatching {
        llamar(yo(context), "DELETE", "/banda/$codigo")
        olvidar(context, codigo)
    }

    /** El estado corto de todas mis bandas: la lista y la tarjeta del Inicio. */
    suspend fun estado(context: Context): Result<List<EstadoCorto>> = runCatching {
        val mias = codigos(context)
        if (mias.isEmpty()) return@runCatching emptyList()
        val cuerpo = JsonObject(mapOf("codigos" to kotlinx.serialization.json.JsonArray(mias.map { JsonPrimitive(it) })))
        val texto = llamar(yo(context), "POST", "/bandas/estado", cuerpo)
        val lista = json.decodeFromJsonElement(ListSerializer(EstadoCorto.serializer()), json.parseToJsonElement(texto).jsonObject.getValue("bandas"))
        lista.filter { it.estado == "no-existe" }.forEach { olvidar(context, it.codigo) }
        lista.filter { it.estado != "no-existe" }
    }

    // ---------- En vivo ----------

    /** Entra a En vivo de la banda con el Escuchar juntos de siempre, pero contra el relevo propio. */
    fun entrarEnVivo(manager: ListenTogetherManager, codigo: String, yo: Yo) {
        if (manager.banda?.codigo == codigo && manager.isInRoom) return
        if (manager.isInRoom) manager.leaveRoom()
        manager.disconnect()
        manager.banda = BandaConexion(codigo, "$RELEVO_WS/banda/$codigo/envivo", yo.autorizacion)
        manager.joinRoom(codigo, yo.nombre)
    }

    fun salirDeEnVivo(manager: ListenTogetherManager) {
        if (manager.isInRoom) manager.leaveRoom()
        manager.disconnect()
        manager.banda = null
    }
}
