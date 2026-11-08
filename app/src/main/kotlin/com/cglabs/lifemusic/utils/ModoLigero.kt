package com.cglabs.lifemusic.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cglabs.lifemusic.constants.AmbienteCanvasKey
import com.cglabs.lifemusic.constants.AmbienteFondoReactivoKey
import com.cglabs.lifemusic.constants.AppleMusicLyricsBlurKey
import com.cglabs.lifemusic.constants.CanvasThumbnailAnimationKey
import com.cglabs.lifemusic.nova.NovaCapacidad
import kotlinx.coroutines.flow.first

/**
 * 2.0.2 · En un telefono de gama baja (la misma medida del medidor de Estudio), la primera vez que
 * abre la app se apagan los efectos que mas cuestan: el Canvas animado, el desenfoque de la letra y el
 * fondo reactivo del modo ambiente. Solo los que la persona nunca toco, y una sola vez: si los vuelve
 * a prender, se quedan prendidos. Nada se quita, solo se dosifica segun el equipo.
 */
object ModoLigero {
    // 2.1.0: clave nueva, para volver a evaluar una vez con la medida de gama corregida.
    private val RevisadoKey = booleanPreferencesKey("modoLigeroRevisado2")
    private val PESADOS = listOf(
        CanvasThumbnailAnimationKey,
        AppleMusicLyricsBlurKey,
        AmbienteCanvasKey,
        AmbienteFondoReactivoKey,
    )

    @Volatile private var gamaBaja: Boolean? = null

    /** Se mide una vez por proceso: es barato, pero se pregunta en cada cuadro de algunos efectos. */
    fun esGamaBaja(context: Context): Boolean =
        gamaBaja ?: (NovaCapacidad.aguante(NovaCapacidad.telefono(context)) <= 0.32f).also { gamaBaja = it }

    suspend fun aplicarSiHaceFalta(context: Context) {
        if (context.dataStore.data.first()[RevisadoKey] == true) return
        val gamaBaja = esGamaBaja(context)
        context.dataStore.edit { p ->
            if (gamaBaja) PESADOS.forEach { k -> if (p[k] == null) p[k] = false }
            p[RevisadoKey] = true
        }
    }
}
