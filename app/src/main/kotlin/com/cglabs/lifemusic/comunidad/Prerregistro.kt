package com.cglabs.lifemusic.comunidad

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.cglabs.lifemusic.constants.PrerregistroCorreoKey
import com.cglabs.lifemusic.constants.PrerregistroDentroKey
import com.cglabs.lifemusic.constants.PrerregistroIdKey
import com.cglabs.lifemusic.constants.PrerregistroNombreKey
import com.cglabs.lifemusic.constants.PrerregistroSecretoKey
import com.cglabs.lifemusic.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.UUID

/**
 * Prerregistro del nuevo concurso (1.3.1). No es el concurso: mide cuantas
 * personas quieren participar. Solo nombre y correo; nunca hay lista publica,
 * solo el contador. Dura 6 dias y se alarga solo si no se llega a 100 (lo
 * decide el servidor). Ficha: docs/versiones/1.3.1/prerregistro.md.
 *
 * El id y el secreto son de este telefono y SOLO para el prerregistro (la
 * calificacion usa otro): con el secreto, solo este telefono puede salirse.
 */
object Prerregistro {
    private val _estado = MutableStateFlow<EstadoPrerregistro?>(null)
    val estado: StateFlow<EstadoPrerregistro?> = _estado

    /** Lo que falta para registrarse; null si esta bien. Lo mismo que valida el servidor. */
    enum class Falta { NOMBRE, CORREO, EDAD, BASES }

    private val CORREO = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun validar(nombre: String, correo: String, mayor: Boolean, acepta: Boolean): Falta? = when {
        nombre.trim().length !in 2..60 -> Falta.NOMBRE
        correo.trim().length > 120 || !CORREO.matches(correo.trim()) -> Falta.CORREO
        !mayor -> Falta.EDAD
        !acepta -> Falta.BASES
        else -> null
    }

    /** La copa de Inicio: desde que el servidor abre el prerregistro (y despues, mientras empieza el concurso). */
    fun copaVisible(e: EstadoPrerregistro?): Boolean = e?.empezo == true

    suspend fun actualizar(): EstadoPrerregistro? {
        val e = ComunidadApi.estado() ?: return _estado.value
        _estado.value = e
        return e
    }

    fun dentro(context: Context): Flow<Boolean> = context.dataStore.data.map { it[PrerregistroDentroKey] == true }

    data class Datos(val nombre: String, val correo: String)

    fun datos(context: Context): Flow<Datos?> = context.dataStore.data.map { p ->
        if (p[PrerregistroDentroKey] == true) Datos(p[PrerregistroNombreKey].orEmpty(), p[PrerregistroCorreoKey].orEmpty()) else null
    }

    private suspend fun credenciales(context: Context): Pair<String, String> {
        val p = context.dataStore.data.first()
        val id = p[PrerregistroIdKey]
        val secreto = p[PrerregistroSecretoKey]
        if (id != null && secreto != null) return id to secreto
        val nuevoId = UUID.randomUUID().toString()
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nuevoSecreto = bytes.joinToString("") { "%02x".format(it) }
        context.dataStore.edit {
            it[PrerregistroIdKey] = nuevoId
            it[PrerregistroSecretoKey] = nuevoSecreto
        }
        return nuevoId to nuevoSecreto
    }

    /** null = sin red. Si sale bien (o ya estaba), queda «dentro» en este telefono. */
    suspend fun registrar(context: Context, nombre: String, correo: String): RespuestaPrerregistro? {
        val (id, secreto) = credenciales(context)
        val r = ComunidadApi.prerregistrar(id, secreto, nombre.trim(), correo.trim()) ?: return null
        if (r.ok) {
            context.dataStore.edit {
                it[PrerregistroDentroKey] = true
                it[PrerregistroNombreKey] = nombre.trim()
                it[PrerregistroCorreoKey] = correo.trim().lowercase()
            }
            r.contador?.let { n -> _estado.value = _estado.value?.copy(contador = n) }
        }
        return r
    }

    /** Borra los datos del servidor y de este telefono. false = sin red (no se borra nada aqui). */
    suspend fun salir(context: Context): Boolean {
        val p = context.dataStore.data.first()
        val id = p[PrerregistroIdKey] ?: return true
        val secreto = p[PrerregistroSecretoKey] ?: return true
        if (!ComunidadApi.salir(id, secreto)) return false
        context.dataStore.edit {
            it.remove(PrerregistroDentroKey)
            it.remove(PrerregistroNombreKey)
            it.remove(PrerregistroCorreoKey)
        }
        actualizar()
        return true
    }
}
