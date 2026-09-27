package com.cglabs.lifemusic.car

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.playback.PlayerConnection
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

/**
 * Una tarjeta de carrusel o cuadricula: caratula grande (redonda si es un
 * artista, como en YouTube Music) y dos lineas de texto. Toda la tarjeta es el
 * boton.
 */
@Composable
fun Tarjeta(
    titulo: String,
    subtitulo: String?,
    caratula: String?,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    redonda: Boolean = false,
    lado: Dp = 148.dp,
) {
    Column(
        horizontalAlignment = if (redonda) Alignment.CenterHorizontally else Alignment.Start,
        modifier = modifier.width(lado).clip(RoundedCornerShape(22.dp)).clickable(onClick = alTocar).padding(bottom = 6.dp),
    ) {
        Caratula(caratula, if (redonda) CircleShape else RoundedCornerShape(22.dp), Modifier.size(lado), tamano = lado)
        Spacer(Modifier.height(8.dp))
        Text(
            titulo,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (redonda) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!subtitulo.isNullOrBlank()) {
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** La tarjeta de cualquier cosa de YouTube Music, con su accion al tocar. */
@Composable
fun TarjetaDe(item: YTItem, conexion: PlayerConnection?, modifier: Modifier = Modifier, lado: Dp = 148.dp) {
    val navegador = LocalNavegador.current
    val subtitulo = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> listOfNotNull(item.artists?.joinToString { it.name }, item.year?.toString()).joinToString(" · ")
        is PlaylistItem -> item.author?.name ?: item.songCountText
        is ArtistItem -> null
        else -> null
    }
    Tarjeta(
        titulo = item.title,
        subtitulo = subtitulo,
        caratula = item.thumbnail,
        redonda = item is ArtistItem,
        lado = lado,
        modifier = modifier,
        alTocar = { tocarElemento(item, conexion, navegador) },
    )
}

/** El titulo de una seccion: en mayusculas espaciadas, del color de la caratula. */
@Composable
fun TituloDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.8.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** Una fila de tarjetas que se desliza de lado, con su titulo. */
@Composable
fun Carrusel(titulo: String, items: List<YTItem>, conexion: PlayerConnection?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        TituloDeSeccion(titulo)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 8.dp)) {
            items(items, key = { it.id }) { item -> TarjetaDe(item, conexion) }
        }
    }
}

/**
 * Un boton de pastilla con icono y texto: Reproducir, Aleatorio... [lleno] le
 * da el color de la caratula (la accion principal); si no, va en cristal.
 */
@Composable
fun BotonPildora(icono: Int, texto: String?, alTocar: () -> Unit, modifier: Modifier = Modifier, lleno: Boolean = false) {
    val forma = RoundedCornerShape(30.dp)
    val tinta = if (lleno) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(60.dp)
            .then(if (lleno) Modifier.clip(forma).background(MaterialTheme.colorScheme.primary) else Modifier.cristal(forma, fuerza = 1.3f))
            .clickable(onClick = alTocar)
            .padding(horizontal = if (texto == null) 18.dp else 24.dp),
    ) {
        Icon(painterResource(icono), contentDescription = texto, tint = tinta, modifier = Modifier.size(26.dp))
        if (texto != null) {
            Spacer(Modifier.width(10.dp))
            Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tinta, maxLines = 1)
        }
    }
}

/** Un acceso grande de Inicio (Me gusta, Descargas...): icono, titulo y una linea. */
@Composable
fun Acceso(icono: Int, titulo: String, detalle: String?, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(88.dp).cristal(RoundedCornerShape(26.dp)).clickable(onClick = alTocar).padding(horizontal = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        ) {
            Icon(painterResource(icono), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detalle.isNullOrBlank()) {
                Text(detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Un aviso de pantalla vacia o de error, sobrio. */
@Composable
fun Aviso(icono: Int, titulo: String, texto: String?, modifier: Modifier = Modifier, accion: (@Composable () -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = modifier.padding(24.dp)) {
        Icon(painterResource(icono), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (!texto.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (accion != null) {
            Spacer(Modifier.height(16.dp))
            accion()
        }
    }
}

