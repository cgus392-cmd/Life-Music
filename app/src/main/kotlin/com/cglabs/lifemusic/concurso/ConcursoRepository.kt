package com.cglabs.lifemusic.concurso

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.cglabs.lifemusic.constants.ConcursoApodoKey
import com.cglabs.lifemusic.constants.ConcursoIdKey
import com.cglabs.lifemusic.constants.ConcursoMinutosKey
import com.cglabs.lifemusic.constants.ConcursoParticipantesKey
import com.cglabs.lifemusic.constants.ConcursoPuestoKey
import com.cglabs.lifemusic.constants.ConcursoRechazadoKey
import com.cglabs.lifemusic.constants.ConcursoSecretoKey
import com.cglabs.lifemusic.constants.ConcursoUltimoEnvioKey
import com.cglabs.lifemusic.db.MusicDatabase
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.security.SecureRandom
import java.util.UUID

/** Lo que la app sabe de su propia participacion. */
data class Participacion(
    val id: String,
    val secreto: String,
    val apodo: String,
    val minutosServidor: Int,
    val puesto: Int,
    val participantes: Int,
)

sealed class ResultadoRegistro {
    object Ok : ResultadoRegistro()
    object SinRed : ResultadoRegistro()
    object ApodoEnUso : ResultadoRegistro()
    object Cerrado : ResultadoRegistro()
    object Datos : ResultadoRegistro()
}

/**
 * Participacion en el reto: registro, envio de minutos y abandono. Todo pasa
 * por aqui para que la regla sea una sola: **sin registro no hay ninguna
 * llamada de red**. El id y el secreto nacen en el telefono; el servidor
 * guarda solo el hash del secreto, y con el hash no se puede suplantar a nadie.
 */
object ConcursoRepository {
    private const val TAG = "Concurso"

    /** Entre envios automaticos. Un envio es una llamada; no hace falta mas. */
    private const val INTERVALO_ENVIO_MS = 10 * 60_000L

    private val candado = Mutex()

    fun participacion(context: Context): Participacion? {
        val ds = context.dataStore
        val id = ds.get(ConcursoIdKey) ?: return null
        val secreto = ds.get(ConcursoSecretoKey) ?: return null
        return Participacion(
            id = id,
            secreto = secreto,
            apodo = ds.get(ConcursoApodoKey) ?: "",
            minutosServidor = ds.get(ConcursoMinutosKey, -1),
            puesto = ds.get(ConcursoPuestoKey, -1),
            participantes = ds.get(ConcursoParticipantesKey, -1),
        )
    }

    fun registrado(context: Context): Boolean = participacion(context) != null

    fun rechazado(context: Context): Boolean = context.dataStore.get(ConcursoRechazadoKey, false)

    suspend fun marcarRechazado(context: Context, rechazado: Boolean) {
        context.dataStore.edit { it[ConcursoRechazadoKey] = rechazado }
    }

    /**
     * Registra (o corrige) la participacion de este telefono. Si es la primera
     * vez genera id y secreto; si ya existen, los reutiliza para que corregir
     * el apodo no cree un participante nuevo.
     */
    suspend fun registrar(context: Context, database: MusicDatabase, apodo: String, nombre: String, correo: String): ResultadoRegistro {
        val ds = context.dataStore
        val id = ds.get(ConcursoIdKey) ?: UUID.randomUUID().toString()
        val secreto = ds.get(ConcursoSecretoKey) ?: nuevoSecreto()

        val respuesta = ConcursoApi.registrar(id, secreto, apodo.trim(), nombre.trim(), correo.trim())
            ?: return ResultadoRegistro.SinRed
        if (!respuesta.ok) {
            return when (respuesta.error) {
                "apodo_en_uso" -> ResultadoRegistro.ApodoEnUso
                "cerrado" -> ResultadoRegistro.Cerrado
                else -> ResultadoRegistro.Datos
            }
        }
        ds.edit {
            it[ConcursoIdKey] = id
            it[ConcursoSecretoKey] = secreto
            it[ConcursoApodoKey] = apodo.trim()
            it[ConcursoRechazadoKey] = false
        }
        // Los minutos que ya lleva desde el inicio del concurso salen ahora mismo.
        sincronizar(context, database, forzar = true)
        return ResultadoRegistro.Ok
    }

    /**
     * Manda al servidor los minutos por dia que cuenta el telefono. Devuelve
     * true si el servidor los acepto. Con [forzar] a false respeta el intervalo
     * entre envios; con true (abrir la pantalla, registrarse) va de inmediato.
     */
    suspend fun sincronizar(context: Context, database: MusicDatabase, forzar: Boolean = false): Boolean = candado.withLock {
        if (!Concurso.configurado) return false
        val p = participacion(context) ?: return false
        val ds = context.dataStore
        val ahora = System.currentTimeMillis()
        if (!forzar && ahora - ds.get(ConcursoUltimoEnvioKey, 0L) < INTERVALO_ENVIO_MS) return false
        // Tras el fin (y su gracia) no hay nada que mandar.
        if (Concurso.hoy().isAfter(Concurso.FIN.plusDays(1))) return false

        val porDia = Concurso.minutosPorDia(database)
        val cuerpo = porDia.entries.associate { (dia, min) -> dia.toString() to min }
        val respuesta = ConcursoApi.reportar(p.id, p.secreto, cuerpo) ?: return false
        ds.edit { it[ConcursoUltimoEnvioKey] = ahora }
        if (!respuesta.ok) {
            Timber.tag(TAG).w("Envio rechazado: %s", respuesta.error)
            if (respuesta.error == "no_registrado") {
                // La fila ya no existe (borrada desde el panel): se olvida el registro.
                olvidar(context)
            }
            return false
        }
        ds.edit {
            respuesta.minutos?.let { m -> it[ConcursoMinutosKey] = m }
            respuesta.puesto?.let { n -> it[ConcursoPuestoKey] = n }
            respuesta.participantes?.let { n -> it[ConcursoParticipantesKey] = n }
        }
        return true
    }

    /** Borra la fila en el servidor —nombre y correo incluidos— y el registro local. */
    suspend fun abandonar(context: Context): Boolean {
        val p = participacion(context) ?: return true
        val ok = ConcursoApi.abandonar(p.id, p.secreto)
        if (ok) olvidar(context)
        return ok
    }

    private suspend fun olvidar(context: Context) {
        context.dataStore.edit {
            it.remove(ConcursoIdKey)
            it.remove(ConcursoSecretoKey)
            it.remove(ConcursoApodoKey)
            it.remove(ConcursoMinutosKey)
            it.remove(ConcursoPuestoKey)
            it.remove(ConcursoParticipantesKey)
            it.remove(ConcursoUltimoEnvioKey)
        }
    }

    private fun nuevoSecreto(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
