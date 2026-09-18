package com.cglabs.lifemusic.ui.player

import com.cglabs.lifemusic.applecanvas.AppleMusicCanvasProvider
import com.cglabs.lifemusic.canvas.CanvasArtwork
import com.cglabs.lifemusic.canvas.TidalCanvasProvider
import com.cglabs.lifemusic.echomusiccanvas.echomusicCanvasProvider
import com.cglabs.lifemusic.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Busca el canvas (video corto) de una cancion: primero en la cache de la
 * sesion y, si no esta, en los proveedores por orden (Echo, Tidal, Apple).
 * Solo se acepta si el artista devuelto coincide con el pedido: sin ese
 * filtro un titulo comun («Hold On») trae el video de otro.
 *
 * Es la misma busqueda que hacia el reproductor en linea dentro de Player.kt;
 * vive aqui para que el modo ambiente la use sin copiarla (ya hay otra
 * variante, mas exhaustiva, en Thumbnail.kt; unificar las dos queda pendiente).
 */
suspend fun buscarCanvas(item: MediaMetadata): CanvasArtwork? {
    CanvasArtworkPlaybackCache.get(item.id)?.let { return it }

    val encontrado = withContext(Dispatchers.IO) {
        val storefront = Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "us"
        val artistaPedido = item.artists.joinToString { it.name }
        val album = item.album?.title ?: ""
        val s = normalizeCanvasSongTitle(item.title)
        val a = normalizeCanvasArtistName(artistaPedido)

        val traido = echomusicCanvasProvider.getBySongArtist(s, a)
            ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            ?: TidalCanvasProvider.getBySongArtist(s, a, album)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
            ?: AppleMusicCanvasProvider.getBySongArtist(s, a, album, storefront)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }

        traido?.takeIf { artwork ->
            val locales = splitAndNormalizeArtists(artistaPedido)
            val devueltos = splitAndNormalizeArtists(artwork.artist ?: "")
            locales.isNotEmpty() && devueltos.isNotEmpty() &&
                locales.any { local -> devueltos.any { it.equals(local, ignoreCase = true) } }
        }
    }

    if (encontrado != null) CanvasArtworkPlaybackCache.put(item.id, encontrado)
    return encontrado
}
