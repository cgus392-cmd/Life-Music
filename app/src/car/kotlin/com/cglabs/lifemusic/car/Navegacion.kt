package com.cglabs.lifemusic.car

import androidx.compose.runtime.staticCompositionLocalOf
import com.cglabs.lifemusic.extensions.toMediaItem
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.models.toMediaMetadata
import com.cglabs.lifemusic.playback.DownloadUtil
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.playback.queues.ListQueue
import com.cglabs.lifemusic.playback.queues.YouTubeQueue
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem

/**
 * Una pagina que se abre encima de la pantalla del riel: un album, una lista o
 * un artista. Se apilan (de un artista a su album, y Atras vuelve al artista);
 * tocar el riel las cierra todas.
 */
sealed interface Detalle {
    val titulo: String
    val caratula: String?

    data class Album(val id: String, override val titulo: String, override val caratula: String?) : Detalle

    /**
     * Una lista. [local] si vive en la base del carro (tus listas); si no, se pide a
     * YouTube Music. [reproducir] sirve de respaldo para las que YouTube no deja
     * abrir (algunas mezclas): al menos suenan.
     */
    data class Lista(
        val id: String,
        override val titulo: String,
        override val caratula: String?,
        val local: Boolean = false,
        val reproducir: WatchEndpoint? = null,
    ) : Detalle

    data class Artista(val id: String, override val titulo: String, override val caratula: String?) : Detalle
}

/** Las pestañas de Biblioteca. */
enum class Pestana { ME_GUSTA, LISTAS, ALBUMES, ARTISTAS, DESCARGAS }

/**
 * Lo que las pantallas necesitan para moverse sin conocer el marco: abrir una
 * pagina de detalle o saltar a una pestaña de Biblioteca.
 */
class Navegador(val abrir: (Detalle) -> Unit, val irABiblioteca: (Pestana) -> Unit)

val LocalNavegador = staticCompositionLocalOf { Navegador({}, {}) }

/** El detalle que abre un elemento de YouTube Music, o null si es una cancion (esa suena). */
fun YTItem.comoDetalle(): Detalle? = when (this) {
    is AlbumItem -> Detalle.Album(browseId, title, thumbnail)
    is PlaylistItem -> Detalle.Lista(id, title, thumbnail, reproducir = playEndpoint)
    is ArtistItem -> Detalle.Artista(id, title, thumbnail)
    else -> null
}

/** Tocar un elemento: una cancion suena con su radio detras; lo demas abre su pagina. */
fun tocarElemento(item: YTItem, conexion: PlayerConnection?, navegador: Navegador) {
    if (item is SongItem) {
        conexion?.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
    } else {
        item.comoDetalle()?.let(navegador.abrir)
    }
}

/**
 * Pone una lista a sonar tal cual (album, lista, me gusta...) desde [desde], o
 * barajada entera si [aleatorio].
 */
fun PlayerConnection.tocarLista(titulo: String?, canciones: List<MediaMetadata>, desde: Int = 0, aleatorio: Boolean = false) {
    if (canciones.isEmpty()) return
    val orden = if (aleatorio) canciones.shuffled() else canciones
    playQueue(ListQueue(titulo, orden.map { it.toMediaItem() }, if (aleatorio) 0 else desde.coerceIn(0, orden.lastIndex)))
}

/** Las descargas del servicio (el mismo DownloadUtil del telefono), para el menu del corazon. */
val LocalDescargas = staticCompositionLocalOf<DownloadUtil?> { null }
