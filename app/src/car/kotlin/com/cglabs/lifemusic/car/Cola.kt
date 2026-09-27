package com.cglabs.lifemusic.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.extensions.metadata
import com.cglabs.lifemusic.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lo que viene: la cola entera, con la que suena resaltada y ya a la vista al
 * entrar (la lista abre una fila por encima de ella). Tocar una salta a esa.
 * Reordenar y quitar llegan en la tanda 2.
 */
@Composable
fun Cola(conexion: PlayerConnection?, modifier: Modifier = Modifier) {
    val ventanas by (conexion?.queueWindows ?: remember { MutableStateFlow(emptyList()) }).collectAsState()
    val actual by (conexion?.currentWindowIndex ?: remember { MutableStateFlow(-1) }).collectAsState()
    val titulo by (conexion?.queueTitle ?: remember { MutableStateFlow<String?>(null) }).collectAsState()
    val sonando = sonandoAhora(conexion)

    Column(modifier.fillMaxSize()) {
        TituloDePantalla(titulo ?: stringResource(R.string.carro_cola)) {
            if (ventanas.isNotEmpty()) {
                Text(
                    stringResource(R.string.carro_cola_cuantas, ventanas.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (ventanas.isEmpty()) {
            Proximamente(R.drawable.queue_music, stringResource(R.string.carro_cola_vacia), stringResource(R.string.carro_cola_vacia_texto))
            return@Column
        }
        val lista = rememberLazyListState(initialFirstVisibleItemIndex = (actual - 1).coerceAtLeast(0))
        LazyColumn(state = lista, contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(ventanas, key = { i, v -> "$i-${v.mediaItem.mediaId}" }) { i, ventana ->
                val md = ventana.mediaItem.metadata
                FilaCancion(
                    titulo = md?.title ?: ventana.mediaItem.mediaMetadata.title?.toString().orEmpty(),
                    artistas = md?.artists?.joinToString { it.name } ?: ventana.mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                    caratula = md?.thumbnailUrl,
                    activa = i == actual,
                    sonando = sonando,
                    alTocar = {
                        if (i == actual) {
                            conexion?.togglePlayPause()
                        } else {
                            conexion?.player?.run {
                                seekToDefaultPosition(ventana.firstPeriodIndex)
                                playWhenReady = true
                            }
                        }
                    },
                )
            }
        }
    }
}
