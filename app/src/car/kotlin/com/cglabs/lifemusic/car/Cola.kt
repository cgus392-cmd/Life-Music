package com.cglabs.lifemusic.car

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.extensions.metadata
import com.cglabs.lifemusic.extensions.move
import com.cglabs.lifemusic.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Lo que viene: la cola entera, con la que suena resaltada y ya a la vista al
 * entrar (la lista abre una fila por encima de ella). Tocar una salta a esa.
 *
 * Se edita como en el telefono, pero con dedos de carro: cada fila lleva a la
 * derecha una X de 60 dp para quitarla (la que suena no se quita) y el asa para
 * arrastrarla a otro lugar. «Vaciar lo que viene» deja solo la que suena.
 * Mientras se arrastra, la lista se mueve en pantalla; el reproductor recibe
 * el cambio al soltar, de una vez (como el telefono, tambien con aleatorio).
 */
@Composable
fun Cola(conexion: PlayerConnection?, modifier: Modifier = Modifier) {
    val ventanas by (conexion?.queueWindows ?: remember { MutableStateFlow(emptyList()) }).collectAsState()
    val actual by (conexion?.currentWindowIndex ?: remember { MutableStateFlow(-1) }).collectAsState()
    val titulo by (conexion?.queueTitle ?: remember { MutableStateFlow<String?>(null) }).collectAsState()
    val sonando = sonandoAhora(conexion)

    // Copia de la cola que se reordena en pantalla mientras el dedo arrastra.
    val filas = remember { mutableStateListOf<Timeline.Window>() }
    LaunchedEffect(ventanas) {
        filas.clear()
        filas.addAll(ventanas)
    }
    val uidActual = ventanas.getOrNull(actual)?.uid

    val lista = rememberLazyListState(initialFirstVisibleItemIndex = (actual - 1).coerceAtLeast(0))
    // Primera y ultima posicion del arrastre en curso.
    var arrastre by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reordenable = rememberReorderableLazyListState(lista) { desde, hasta ->
        arrastre = (arrastre?.first ?: desde.index) to hasta.index
        filas.move(desde.index.coerceIn(0, filas.lastIndex), hasta.index.coerceIn(0, filas.lastIndex))
    }
    LaunchedEffect(reordenable.isAnyItemDragging) {
        if (reordenable.isAnyItemDragging) return@LaunchedEffect
        val (desde, hasta) = arrastre ?: return@LaunchedEffect
        arrastre = null
        val p = conexion?.player ?: return@LaunchedEffect
        if (desde == hasta || ventanas.isEmpty()) return@LaunchedEffect
        val de = desde.coerceIn(0, ventanas.lastIndex)
        val a = hasta.coerceIn(0, ventanas.lastIndex)
        if (!p.shuffleModeEnabled) {
            p.moveMediaItem(de, a)
        } else {
            // Con aleatorio, la cola que se ve es el orden barajado: se cambia ese orden.
            p.setShuffleOrder(
                DefaultShuffleOrder(
                    ventanas.map { it.firstPeriodIndex }.toMutableList().move(de, a).toIntArray(),
                    System.currentTimeMillis(),
                ),
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        TituloDePantalla(titulo ?: stringResource(R.string.carro_cola)) {
            if (ventanas.isNotEmpty()) {
                Text(
                    stringResource(R.string.carro_cola_cuantas, ventanas.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actual in ventanas.indices && actual < ventanas.lastIndex) {
                Spacer(Modifier.width(16.dp))
                BotonPildora(R.drawable.clear_all, stringResource(R.string.carro_cola_vaciar), alTocar = {
                    conexion?.player?.let { p ->
                        // Quita todo lo que viene despues de la que suena, en el orden que se ve.
                        ventanas.drop(actual + 1).map { it.firstPeriodIndex }.sortedDescending().forEach { p.removeMediaItem(it) }
                    }
                })
            }
        }
        if (ventanas.isEmpty()) {
            Proximamente(R.drawable.queue_music, stringResource(R.string.carro_cola_vacia), stringResource(R.string.carro_cola_vacia_texto))
            return@Column
        }
        LazyColumn(state = lista, contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(filas, key = { _, v -> v.uid.toString() }) { _, ventana ->
                ReorderableItem(reordenable, key = ventana.uid.toString()) { arrastrando ->
                    val md = ventana.mediaItem.metadata
                    val esActual = ventana.uid == uidActual
                    FilaCancion(
                        titulo = md?.title ?: ventana.mediaItem.mediaMetadata.title?.toString().orEmpty(),
                        artistas = md?.artists?.joinToString { it.name } ?: ventana.mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                        caratula = md?.thumbnailUrl,
                        activa = esActual,
                        sonando = sonando,
                        modifier = if (arrastrando) Modifier.scale(1.02f).cristal(RoundedCornerShape(20.dp), fuerza = 1.6f) else Modifier,
                        alTocar = {
                            if (esActual) {
                                conexion?.togglePlayPause()
                            } else {
                                conexion?.player?.run {
                                    seekToDefaultPosition(ventana.firstPeriodIndex)
                                    playWhenReady = true
                                }
                            }
                        },
                    ) {
                        if (!esActual) {
                            BotonFila(R.drawable.close, stringResource(R.string.carro_cola_quitar)) {
                                conexion?.player?.removeMediaItem(ventana.firstPeriodIndex)
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(60.dp).draggableHandle(),
                        ) {
                            Icon(
                                painterResource(R.drawable.drag_handle),
                                contentDescription = stringResource(R.string.carro_cola_mover),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Un boton de 60 dp al final de una fila (quitar...). */
@Composable
private fun BotonFila(icono: Int, descripcion: String, alTocar: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp).clip(CircleShape).clickable(onClick = alTocar)) {
        Icon(painterResource(icono), contentDescription = descripcion, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}
