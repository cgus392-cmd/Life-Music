@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cglabs.lifemusic.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.db.entities.PlaylistSong
import com.cglabs.lifemusic.db.entities.Song
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.models.toMediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.playback.queues.YouTubeQueue
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.PlaylistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** La pagina de un album, una lista o un artista. */
@Composable
fun PantallaDetalle(d: Detalle, conexion: PlayerConnection?, alVolver: () -> Unit, modifier: Modifier = Modifier) {
    when (d) {
        is Detalle.Album -> DetalleAlbum(d, conexion, alVolver, modifier)
        is Detalle.Lista -> if (d.local) DetalleListaLocal(d, conexion, alVolver, modifier) else DetalleListaYT(d, conexion, alVolver, modifier)
        is Detalle.Artista -> DetalleArtista(d, conexion, alVolver, modifier)
    }
}

@Composable
private fun DetalleAlbum(d: Detalle.Album, conexion: PlayerConnection?, alVolver: () -> Unit, modifier: Modifier) {
    var intento by remember { mutableIntStateOf(0) }
    val pagina by produceState<Result<AlbumPage>?>(null, d.id, intento) {
        value = null
        value = withContext(Dispatchers.IO) { YouTube.album(d.id) }
    }
    // Si YouTube no contesta pero el album esta guardado en el carro, se abre igual.
    val locales by remember(d.id) { conexion?.database?.albumSongs(d.id) ?: flowOf(emptyList<Song>()) }.collectAsState(initial = emptyList())
    val album = pagina?.getOrNull()
    val canciones = album?.songs?.map { it.toMediaMetadata() } ?: locales.map { it.toMediaMetadata() }
    val titulo = album?.album?.title ?: d.titulo
    val caratula = album?.album?.thumbnail ?: d.caratula

    MarcoDetalle(
        caratula = caratula,
        etiqueta = listOfNotNull(stringResource(R.string.carro_album), album?.album?.year?.toString()).joinToString(" · "),
        titulo = titulo,
        subtitulo = album?.album?.artists?.joinToString { it.name },
        cargando = pagina == null && locales.isEmpty(),
        fallo = pagina?.isFailure == true && locales.isEmpty(),
        alReintentar = { intento++ },
        alVolver = alVolver,
        alReproducir = siHay(canciones.isNotEmpty()) { conexion?.tocarLista(titulo, canciones) },
        alAleatorio = siHay(canciones.size > 1) { conexion?.tocarLista(titulo, canciones, aleatorio = true) },
        modifier = modifier,
    ) {
        listaDeCanciones(canciones, titulo, caratula, conexion)
    }
}

@Composable
private fun DetalleListaYT(d: Detalle.Lista, conexion: PlayerConnection?, alVolver: () -> Unit, modifier: Modifier) {
    var intento by remember { mutableIntStateOf(0) }
    val pagina by produceState<Result<PlaylistPage>?>(null, d.id, intento) {
        value = null
        value = withContext(Dispatchers.IO) { YouTube.playlist(d.id) }
    }
    val lista = pagina?.getOrNull()
    // Las listas largas llegan por partes: la siguiente se pide al llegar al final.
    var mas by remember(d.id, intento) { mutableStateOf(emptyList<SongItem>()) }
    var continuacion by remember(d.id, intento) { mutableStateOf<String?>(null) }
    LaunchedEffect(lista) { continuacion = lista?.songsContinuation }

    val canciones = remember(lista, mas) { ((lista?.songs ?: emptyList()) + mas).distinctBy { it.id }.map { it.toMediaMetadata() } }
    val titulo = lista?.playlist?.title ?: d.titulo
    val caratula = lista?.playlist?.thumbnail ?: d.caratula

    MarcoDetalle(
        caratula = caratula,
        etiqueta = listOfNotNull(stringResource(R.string.carro_lista), lista?.playlist?.songCountText).joinToString(" · "),
        titulo = titulo,
        subtitulo = lista?.playlist?.author?.name,
        cargando = pagina == null,
        fallo = pagina?.isFailure == true,
        alReintentar = { intento++ },
        alVolver = alVolver,
        // Si YouTube no deja abrirla (algunas mezclas), Reproducir usa su enlace directo.
        alReproducir = when {
            canciones.isNotEmpty() -> siHay(true) { conexion?.tocarLista(titulo, canciones) }
            d.reproducir != null -> siHay(true) { conexion?.playQueue(YouTubeQueue(d.reproducir)) }
            else -> null
        },
        alAleatorio = siHay(canciones.size > 1) { conexion?.tocarLista(titulo, canciones, aleatorio = true) },
        modifier = modifier,
    ) {
        listaDeCanciones(canciones, titulo, caratula, conexion)
        continuacion?.let { c ->
            item(key = "mas-$c") {
                LaunchedEffect(c) {
                    val siguiente = withContext(Dispatchers.IO) { YouTube.playlistContinuation(c).getOrNull() }
                    mas = mas + siguiente?.songs.orEmpty()
                    continuacion = siguiente?.continuation
                }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
            }
        }
    }
}

@Composable
private fun DetalleListaLocal(d: Detalle.Lista, conexion: PlayerConnection?, alVolver: () -> Unit, modifier: Modifier) {
    val canciones by remember(d.id) {
        conexion?.database?.playlistSongs(d.id) ?: flowOf(emptyList<PlaylistSong>())
    }.collectAsState(initial = null)
    val lista = canciones?.map { it.song.toMediaMetadata() }.orEmpty()
    MarcoDetalle(
        caratula = d.caratula ?: lista.firstOrNull()?.thumbnailUrl,
        etiqueta = stringResource(R.string.carro_lista),
        titulo = d.titulo,
        subtitulo = if (canciones != null) stringResource(R.string.carro_cola_cuantas, lista.size) else null,
        cargando = canciones == null,
        fallo = false,
        alReintentar = {},
        alVolver = alVolver,
        alReproducir = siHay(lista.isNotEmpty()) { conexion?.tocarLista(d.titulo, lista) },
        alAleatorio = siHay(lista.size > 1) { conexion?.tocarLista(d.titulo, lista, aleatorio = true) },
        modifier = modifier,
    ) {
        listaDeCanciones(lista, d.titulo, d.caratula, conexion)
    }
}

@Composable
private fun DetalleArtista(d: Detalle.Artista, conexion: PlayerConnection?, alVolver: () -> Unit, modifier: Modifier) {
    var intento by remember { mutableIntStateOf(0) }
    val pagina by produceState<Result<ArtistPage>?>(null, d.id, intento) {
        value = null
        value = withContext(Dispatchers.IO) { YouTube.artist(d.id) }
    }
    val artista = pagina?.getOrNull()
    val radio: WatchEndpoint? = artista?.artist?.radioEndpoint ?: artista?.artist?.playEndpoint
    val mezcla: WatchEndpoint? = artista?.artist?.shuffleEndpoint

    MarcoDetalle(
        caratula = artista?.artist?.thumbnail ?: d.caratula,
        redonda = true,
        etiqueta = stringResource(R.string.carro_artista),
        titulo = artista?.artist?.title ?: d.titulo,
        subtitulo = artista?.monthlyListenerCount ?: artista?.subscriberCountText,
        cargando = pagina == null,
        fallo = pagina?.isFailure == true,
        alReintentar = { intento++ },
        alVolver = alVolver,
        alReproducir = siHay(radio != null) { radio?.let { conexion?.playQueue(YouTubeQueue(it)) } },
        alAleatorio = siHay(mezcla != null) { mezcla?.let { conexion?.playQueue(YouTubeQueue(it)) } },
        modifier = modifier,
    ) {
        artista?.sections.orEmpty().forEach { seccion ->
            val canciones = seccion.items.filterIsInstance<SongItem>()
            if (canciones.isNotEmpty() && canciones.size == seccion.items.size) {
                // Las canciones del artista, en filas: se tocan y suena la seccion desde ahi.
                item(key = "t-${seccion.title}") { TituloDeSeccion(seccion.title, Modifier.padding(top = 4.dp)) }
                listaDeCanciones(canciones.map { it.toMediaMetadata() }, seccion.title, null, conexion, clave = seccion.title)
                item(key = "e-${seccion.title}") { Spacer(Modifier.height(16.dp)) }
            } else if (seccion.items.isNotEmpty()) {
                item(key = "c-${seccion.title}") {
                    Carrusel(seccion.title, seccion.items, conexion, Modifier.padding(bottom = 18.dp))
                }
            }
        }
    }
}

/** Las filas de una lista de canciones; tocar una suena la lista desde ella. */
private fun LazyListScope.listaDeCanciones(
    canciones: List<MediaMetadata>,
    titulo: String?,
    caratulaDeRespaldo: String?,
    conexion: PlayerConnection?,
    clave: String = "",
) {
    itemsIndexed(canciones, key = { i, m -> "$clave-$i-${m.id}" }) { i, m ->
        val actual = metadatosActuales(conexion)
        val activa = m.id == actual?.id
        FilaCancion(
            titulo = m.title,
            artistas = m.artists.joinToString { it.name },
            caratula = m.thumbnailUrl ?: caratulaDeRespaldo,
            activa = activa,
            sonando = sonandoAhora(conexion),
            alTocar = { if (activa) conexion?.togglePlayPause() else conexion?.tocarLista(titulo, canciones, desde = i) },
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/**
 * El esqueleto de toda pagina de detalle, a lo ancho del radio: a la izquierda
 * la caratula, el titulo y los botones (Volver arriba, Reproducir y Aleatorio
 * abajo, a mano); a la derecha la lista, que es lo que se recorre.
 */
@Composable
private fun MarcoDetalle(
    caratula: String?,
    etiqueta: String,
    titulo: String,
    subtitulo: String?,
    cargando: Boolean,
    fallo: Boolean,
    alReintentar: () -> Unit,
    alVolver: () -> Unit,
    alReproducir: (() -> Unit)?,
    alAleatorio: (() -> Unit)?,
    modifier: Modifier = Modifier,
    redonda: Boolean = false,
    contenido: LazyListScope.() -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = modifier.fillMaxSize()) {
        Column(Modifier.width(290.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonCristal(R.drawable.arrow_back, stringResource(R.string.carro_volver), alVolver, lado = 56.dp)
                Spacer(Modifier.width(14.dp))
                TituloDeSeccion(etiqueta, Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(12.dp))
            Caratula(caratula, if (redonda) CircleShape else RoundedCornerShape(26.dp), Modifier.size(172.dp), tamano = 172.dp, sombra = 8.dp)
            Spacer(Modifier.height(12.dp))
            Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitulo.isNullOrBlank()) {
                Text(subtitulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (alReproducir != null) BotonPildora(R.drawable.play, stringResource(R.string.carro_reproducir), alReproducir, lleno = true)
                if (alAleatorio != null) BotonPildora(R.drawable.shuffle, null, alAleatorio)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                cargando -> LoadingIndicator(Modifier.size(80.dp).align(Alignment.Center))
                fallo -> Aviso(
                    R.drawable.error,
                    stringResource(R.string.carro_no_abre),
                    stringResource(R.string.carro_no_abre_texto),
                    Modifier.align(Alignment.Center),
                ) {
                    BotonPildora(R.drawable.refresh, stringResource(R.string.carro_reintentar), alReintentar)
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), modifier = Modifier.fillMaxSize()) { contenido() }
            }
        }
    }
}

/** La accion [f] si [hay] (un boton que se muestra), o null (no se muestra). */
private fun siHay(hay: Boolean, f: () -> Unit): (() -> Unit)? = if (hay) f else null
