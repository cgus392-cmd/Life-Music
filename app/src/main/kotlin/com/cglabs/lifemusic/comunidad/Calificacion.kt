package com.cglabs.lifemusic.comunidad

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.cglabs.lifemusic.constants.CalificacionAhoraNoKey
import com.cglabs.lifemusic.constants.CalificacionEstrellasKey
import com.cglabs.lifemusic.constants.CalificacionIdKey
import com.cglabs.lifemusic.constants.CalificacionVersionPreguntadaKey
import com.cglabs.lifemusic.utils.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Calificacion con estrellas (1.3.1): «¿Te gusta Life Music?», sin registrarse
 * en nada. Ficha: docs/versiones/1.3.1/calificacion.md.
 *
 * Se pregunta cuando ya conoce la app y como mucho una vez por version; «Ahora
 * no» la aparta 60 dias, y quien ya califico no vuelve a ver la pregunta (puede
 * cambiar su voto desde Acerca de). El id es otro numero al azar, distinto del
 * del prerregistro.
 */
object Calificacion {
    const val DIAS_MINIMOS = 7
    const val CANCIONES_MINIMAS = 20
    const val DIAS_TRAS_AHORA_NO = 60
    const val MAX_COMENTARIO = 500
    private const val DIA_MS = 86_400_000L

    /** La regla de cuando preguntar, sin Android: se prueba en CalificacionTest. */
    fun debePreguntar(
        ahora: Long,
        instaladaEn: Long,
        canciones: Int,
        versionActual: Int,
        versionPreguntada: Int,
        ahoraNoEn: Long,
        yaCalifico: Boolean,
    ): Boolean = !yaCalifico &&
        ahora - instaladaEn >= DIAS_MINIMOS * DIA_MS &&
        canciones >= CANCIONES_MINIMAS &&
        versionPreguntada < versionActual &&
        (ahoraNoEn <= 0L || ahora - ahoraNoEn >= DIAS_TRAS_AHORA_NO * DIA_MS)

    /** Las estrellas que ya dio este telefono (0 = nunca califico). */
    fun estrellas(context: Context): Flow<Int> = context.dataStore.data.map { it[CalificacionEstrellasKey] ?: 0 }

    suspend fun debePreguntar(context: Context, canciones: Int, versionActual: Int): Boolean {
        val p = context.dataStore.data.first()
        val instaladaEn = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(System.currentTimeMillis())
        return debePreguntar(
            ahora = System.currentTimeMillis(),
            instaladaEn = instaladaEn,
            canciones = canciones,
            versionActual = versionActual,
            versionPreguntada = p[CalificacionVersionPreguntadaKey] ?: 0,
            ahoraNoEn = p[CalificacionAhoraNoKey] ?: 0L,
            yaCalifico = (p[CalificacionEstrellasKey] ?: 0) > 0,
        )
    }

    /** Se anota al mostrar la hoja: una vez por version, conteste o no. */
    suspend fun marcarPreguntada(context: Context, versionActual: Int) {
        context.dataStore.edit { it[CalificacionVersionPreguntadaKey] = versionActual }
    }

    suspend fun ahoraNo(context: Context) {
        context.dataStore.edit { it[CalificacionAhoraNoKey] = System.currentTimeMillis() }
    }

    /** Manda (o cambia) el voto. false = sin red: no se guarda nada y se puede reintentar. */
    suspend fun enviar(context: Context, estrellas: Int, comentario: String?): Boolean {
        val id = context.dataStore.data.first()[CalificacionIdKey] ?: UUID.randomUUID().toString().also { nuevo ->
            context.dataStore.edit { it[CalificacionIdKey] = nuevo }
        }
        val ok = ComunidadApi.calificar(id, estrellas.coerceIn(1, 5), comentario?.trim()?.take(MAX_COMENTARIO))
        if (ok) context.dataStore.edit { it[CalificacionEstrellasKey] = estrellas.coerceIn(1, 5) }
        return ok
    }
}
