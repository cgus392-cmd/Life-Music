package com.cglabs.lifemusic.car

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.AlbumSortType
import com.cglabs.lifemusic.constants.ArtistSortType
import com.cglabs.lifemusic.constants.PlaylistSortType
import com.cglabs.lifemusic.constants.SongSortType
import com.cglabs.lifemusic.db.entities.Album
import com.cglabs.lifemusic.db.entities.Artist
import com.cglabs.lifemusic.db.entities.Playlist
import com.cglabs.lifemusic.db.entities.Song
import com.cglabs.lifemusic.models.toMediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Tu musica guardada en el carro, por pestañas grandes: Me gusta, Listas,
 * Albumes, Artistas y Descargas. Hoy se llena con lo que marques aqui; con el
 * inicio de sesion (tanda 4) y el paso por QR (tanda 5) llegara la del telefono.
 */
@Composable
fun Biblioteca(conexion: PlayerConnection?, pestana: Pestana, alCambiar: (Pestana) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Pestanas(pestana, alCambiar)
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = pestana,
            transitionSpec = { fadeIn(tween(220, delayMillis = 40)) togetherWith fadeOut(tween(120)) },
            label = "pestanaBiblioteca",
            modifier = Modifier.weight(1f),
        ) { p ->
            val db = conexion?.database
            when (p) {
                Pestana.ME_GUSTA -> CancionesGuardadas(
                    conexion,
                    remember(db) { db?.likedSongs(SongSortType.CREATE_DATE, true) ?: flowOf(emptyList()) },
                    stringResource(R.string.carro_pestana_me_gusta),
                    R.drawable.favorite_border,
                    stringResource(R.string.carro_vacio_me_gusta),
                    stringResource(R.string.carro_vacio_me_gusta_texto),
                )
                Pestana.DESCARGAS -> CancionesGuardadas(
                    conexion,
                    remember(db) { db?.downloadedSongs(SongSortType.CREATE_DATE, true) ?: flowOf(emptyList()) },
                    stringResource(R.string.carro_pestana_descargas),
                    R.drawable.download,
                    stringResource(R.string.carro_vacio_descargas),
                    stringResource(R.string.carro_vacio_descargas_texto),
                )
                Pestana.LISTAS -> Cuadricula(
                    remember(db) { db?.playlists(PlaylistSortType.CREATE_DATE, true) ?: flowOf(emptyList<Playlist>()) },
                    R.drawable.queue_music,
                    stringResource(R.string.carro_vacio_listas),
                    stringResource(R.string.carro_vacio_cuenta_texto),
                ) { lista ->
                    val navegador = LocalNavegador.current
                    val portada = lista.thumbnailUrl ?: lista.songThumbnails.firstOrNull()
                    Tarjeta(
                        titulo = lista.title,
                        subtitulo = stringResource(R.string.carro_cola_cuantas, lista.songCount),
                        caratula = portada,
                        alTocar = { navegador.abrir(Detalle.Lista(lista.id, lista.title, portada, local = true)) },
                    )
                }
                Pestana.ALBUMES -> Cuadricula(
                    remember(db) { db?.albums(AlbumSortType.CREATE_DATE, true) ?: flowOf(emptyList<Album>()) },
                    R.drawable.album,
                    stringResource(R.string.carro_vacio_albumes),
                    stringResource(R.string.carro_vacio_cuenta_texto),
                ) { album ->
                    val navegador = LocalNavegador.current
                    Tarjeta(
                        titulo = album.title,
                        subtitulo = album.artists.joinToString { it.name },
                        caratula = album.thumbnailUrl,
                        alTocar = { navegador.abrir(Detalle.Album(album.id, album.title, album.thumbnailUrl)) },
                    )
                }
                Pestana.ARTISTAS -> Cuadricula(
                    remember(db) { db?.artists(ArtistSortType.CREATE_DATE, true) ?: flowOf(emptyList<Artist>()) },
                    R.drawable.artist,
                    stringResource(R.string.carro_vacio_artistas),
                    stringResource(R.string.carro_vacio_cuenta_texto),
                ) { artista ->
                    val navegador = LocalNavegador.current
                    Tarjeta(
                        titulo = artista.title,
                        subtitulo = null,
                        caratula = artista.thumbnailUrl,
                        redonda = true,
                        alTocar = { navegador.abrir(Detalle.Artista(artista.id, artista.title, artista.thumbnailUrl)) },
                    )
                }
            }
        }
    }
}

/** Las pestañas: pastillas grandes; la elegida se llena del color de la caratula. */
@Composable
private fun Pestanas(actual: Pestana, alCambiar: (Pestana) -> Unit) {
    val nombres = mapOf(
        Pestana.ME_GUSTA to stringResource(R.string.carro_pestana_me_gusta),
        Pestana.LISTAS to stringResource(R.string.carro_pestana_listas),
        Pestana.ALBUMES to stringResource(R.string.carro_pestana_albumes),
        Pestana.ARTISTAS to stringResource(R.string.carro_pestana_artistas),
        Pestana.DESCARGAS to stringResource(R.string.carro_pestana_descargas),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Pestana.entries.forEach { p ->
            val activa = p == actual
            val fondo by animateColorAsState(if (activa) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f), tween(220), label = "pestana")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(fondo)
                    .clickable { alCambiar(p) }
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    nombres.getValue(p),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (activa) FontWeight.Bold else FontWeight.Medium,
                    color = if (activa) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Me gusta o Descargas: cuantas hay, Reproducir y Aleatorio, y las canciones en dos columnas. */
@Composable
private fun CancionesGuardadas(
    conexion: PlayerConnection?,
    flujo: Flow<List<Song>>,
    titulo: String,
    iconoVacio: Int,
    vacio: String,
    vacioTexto: String,
) {
    val canciones by flujo.collectAsState(initial = null)
    val lista = canciones ?: return
    if (lista.isEmpty()) {
        Aviso(iconoVacio, vacio, vacioTexto, Modifier.fillMaxSize())
        return
    }
    val metadatos = remember(lista) { lista.map { it.toMediaMetadata() } }
    val actual = metadatosActuales(conexion)
    val sonando = sonandoAhora(conexion)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    stringResource(R.string.carro_cola_cuantas, lista.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                BotonPildora(R.drawable.play, stringResource(R.string.carro_reproducir), { conexion?.tocarLista(titulo, metadatos) }, lleno = true)
                Spacer(Modifier.width(10.dp))
                BotonPildora(R.drawable.shuffle, null, { conexion?.tocarLista(titulo, metadatos, aleatorio = true) })
            }
        }
        itemsIndexed(metadatos, key = { _, m -> m.id }) { i, m ->
            val activa = m.id == actual?.id
            FilaCancion(
                titulo = m.title,
                artistas = m.artists.joinToString { it.name },
                caratula = m.thumbnailUrl,
                activa = activa,
                sonando = sonando,
                alTocar = { if (activa) conexion?.togglePlayPause() else conexion?.tocarLista(titulo, metadatos, desde = i) },
            )
        }
    }
}

/** Listas, albumes o artistas en cuadricula de tarjetas. */
@Composable
private fun <T> Cuadricula(
    flujo: Flow<List<T>>,
    iconoVacio: Int,
    vacio: String,
    vacioTexto: String,
    tarjeta: @Composable (T) -> Unit,
) {
    val elementos by flujo.collectAsState(initial = null)
    val lista = elementos ?: return
    if (lista.isEmpty()) {
        Aviso(iconoVacio, vacio, vacioTexto, Modifier.fillMaxSize())
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(164.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(lista) { e -> Box(contentAlignment = Alignment.TopCenter) { tarjeta(e) } }
    }
}
