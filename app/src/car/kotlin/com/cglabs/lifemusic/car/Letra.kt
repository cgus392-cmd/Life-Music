package com.cglabs.lifemusic.car

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.db.entities.LyricsEntity
import com.cglabs.lifemusic.lyrics.LyricsEntry
import com.cglabs.lifemusic.lyrics.LyricsUtils
import com.cglabs.lifemusic.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/**
 * La letra en Sonando, en lugar de la caratula. La busca el servicio solo, al
 * empezar cada cancion (la misma que usa Life Line en el telefono); aqui solo
 * se pinta. Si viene sincronizada, la linea que suena va en grande y blanca, y
 * la lista la sigue; tocar una linea salta a ella. Si no, se lee entera.
 */
@Composable
fun Letra(conexion: PlayerConnection?, sonando: Boolean, modifier: Modifier = Modifier) {
    val entidad by remember(conexion) { conexion?.currentLyrics ?: flowOf<LyricsEntity?>(null) }.collectAsState(initial = null)
    val texto = entidad?.lyrics
    val lineas: List<LyricsEntry> = remember(texto) {
        when {
            texto.isNullOrBlank() || texto == LyricsEntity.LYRICS_NOT_FOUND -> emptyList()
            texto.trimStart().startsWith("[") -> runCatching { LyricsUtils.parseLyrics(texto) }.getOrDefault(emptyList())
            else -> texto.lines().map { LyricsEntry(0L, it) }
        }
    }
    val sincronizada = lineas.any { it.time > 0 }

    var posicion by remember { mutableLongStateOf(0L) }
    LaunchedEffect(conexion, sonando, sincronizada) {
        val p = conexion?.player ?: return@LaunchedEffect
        if (!sincronizada) return@LaunchedEffect
        while (true) {
            posicion = p.currentPosition
            if (!sonando) break
            delay(200)
        }
    }
    val actual = if (sincronizada) lineas.indexOfLast { it.time <= posicion } else -1

    val lista = rememberLazyListState()
    LaunchedEffect(actual) {
        // La linea que suena, en el tercio de arriba: se ve lo que viene.
        if (actual >= 0) lista.animateScrollToItem((actual - 1).coerceAtLeast(0))
    }

    Box(modifier.cristal(RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
        when {
            entidad == null && texto == null -> Text(
                stringResource(R.string.carro_letra_buscando),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            lineas.isEmpty() -> Text(
                stringResource(R.string.carro_letra_no_hay),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                state = lista,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 40.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(lineas) { i, linea ->
                    val esta = i == actual
                    val color by animateColorAsState(
                        when {
                            !sincronizada || esta -> MaterialTheme.colorScheme.onSurface
                            i < actual -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        },
                        tween(300),
                        label = "linea",
                    )
                    Text(
                        linea.text.ifBlank { "♪" },
                        style = if (sincronizada) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        fontWeight = if (esta) FontWeight.Bold else FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (sincronizada) Modifier.clickable { conexion?.seekTo(linea.time) } else Modifier)
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
