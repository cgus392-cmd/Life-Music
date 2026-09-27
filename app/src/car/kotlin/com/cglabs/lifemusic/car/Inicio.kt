package com.cglabs.lifemusic.car

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.db.entities.Song
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.models.toMediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.playback.queues.YouTubeQueue
import kotlinx.coroutines.flow.flowOf

/** Cuantas canciones recientes se muestran en Inicio. Las que caben sin que la lista se vuelva tarea. */
private const val RECIENTES = 12

/**
 * Inicio del carro: el saludo de la hora arriba y, debajo, dos paneles de
 * cristal. A la izquierda lo que suena (o lo que quedo sonando), con
 * Reproducir a un toque; a la derecha lo escuchado hace poco, para volver a
 * ello sin teclear. En Inicio no va el mini reproductor: este panel lo hace.
 */
@Composable
fun Inicio(conexion: PlayerConnection?, m: MediaMetadata?, alBuscar: () -> Unit, alAbrirSonando: () -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val ahora = rememberAhora()
    Column(modifier.fillMaxSize()) {
        Text(cabeceraDeLaHora(contexto, ahora), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(fechaLarga(ahora), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.weight(1f)) {
            PanelSonando(conexion, m, alBuscar, alAbrirSonando, Modifier.weight(1.3f).fillMaxHeight())
            PanelRecientes(conexion, m, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun PanelSonando(conexion: PlayerConnection?, m: MediaMetadata?, alBuscar: () -> Unit, alAbrir: () -> Unit, modifier: Modifier) {
    val sonando = sonandoAhora(conexion)
    Column(
        modifier
            .cristal(RoundedCornerShape(32.dp))
            .clickable(enabled = m != null, onClick = alAbrir)
            .padding(20.dp),
    ) {
        Text(
            stringResource(if (sonando) R.string.carro_sonando else R.string.carro_continuar).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.8.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (m == null) {
            // Primera vez (o cola vacia): una invitacion clara a buscar.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                Icon(painterResource(R.drawable.music_note), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.carro_nada_sonando), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(64.dp)
                        .cristal(RoundedCornerShape(32.dp), MaterialTheme.colorScheme.primary, fuerza = 2.6f)
                        .clickable(onClick = alBuscar)
                        .padding(horizontal = 28.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.carro_empezar_buscar), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Caratula(m.thumbnailUrl, RoundedCornerShape(24.dp), Modifier.fillMaxHeight(0.84f).aspectRatio(1f), pixeles = 320, sombra = 8.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    m.artists.joinToString { it.name },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        BarraFina(conexion, sonando, m, Modifier.fillMaxWidth().height(4.dp))
        Spacer(Modifier.height(16.dp))
        Controles(conexion, sonando, Modifier.fillMaxWidth(), escala = 0.8f)
    }
}

@Composable
private fun PanelRecientes(conexion: PlayerConnection?, m: MediaMetadata?, modifier: Modifier) {
    val sonando = sonandoAhora(conexion)
    val canciones by remember(conexion) {
        conexion?.database?.escuchadasHacePoco(RECIENTES) ?: flowOf(emptyList<Song>())
    }.collectAsState(initial = emptyList())
    Column(modifier.cristal(RoundedCornerShape(32.dp)).padding(top = 20.dp, start = 12.dp, end = 12.dp)) {
        Text(
            stringResource(R.string.carro_escuchadas).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.8.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        if (canciones.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(stringResource(R.string.carro_sin_historial), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(canciones, key = { it.id }) { c ->
                val activa = c.id == m?.id
                FilaCancion(
                    titulo = c.title,
                    artistas = c.artists.joinToString { it.name },
                    caratula = c.thumbnailUrl,
                    activa = activa,
                    sonando = sonando,
                    alTocar = {
                        if (activa) conexion?.togglePlayPause()
                        else conexion?.playQueue(YouTubeQueue.radio(c.toMediaMetadata()))
                    },
                )
            }
        }
    }
}
