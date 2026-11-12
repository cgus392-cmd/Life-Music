package com.cglabs.lifemusic.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.cglabs.lifemusic.constants.LetrasElegidasAManoKey
import com.cglabs.lifemusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 2.1.x · Las letras que la persona eligió o editó a mano. La elección automática (EleccionDeLetra) nunca las
 * vuelve a tocar, ni siquiera si están sin sincronizar.
 */
object EleccionAMano {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun anotar(context: Context, id: String) {
        alcance.launch {
            context.dataStore.edit { it[LetrasElegidasAManoKey] = (it[LetrasElegidasAManoKey] ?: emptySet()) + id }
        }
    }

    suspend fun esAMano(context: Context, id: String): Boolean =
        context.dataStore.data.first()[LetrasElegidasAManoKey]?.contains(id) == true
}
