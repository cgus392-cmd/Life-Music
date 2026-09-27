package com.cglabs.lifemusic.car

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.ExoDownloadService
import com.cglabs.lifemusic.db.entities.Song
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import kotlinx.coroutines.flow.flowOf

/**
 * Sonando a pantalla completa: la caratula todo lo alta que da la pantalla a la
 * izquierda y, a la derecha, la hora en grande, el titulo, la barra ondulada y
 * los controles. Se abre tocando el mini reproductor y se cierra con la flecha
 * (o con Atras). El fondo es el mismo ambiente de siempre, que sigue debajo.
 */
@Composable
fun SonandoCompleto(conexion: PlayerConnection?, m: MediaMetadata?, alCerrar: () -> Unit, modifier: Modifier = Modifier) {
    val sonando = sonandoAhora(conexion)
    val navegador = LocalNavegador.current
    var verLetra by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
            .padding(28.dp),
    ) {
        // La caratula o, con el boton Letra, la letra en su lugar.
        Crossfade(targetState = verLetra, animationSpec = tween(350), label = "caratulaOLetra", modifier = Modifier.fillMaxHeight().aspectRatio(1f)) { letra ->
            if (letra) {
                Letra(conexion, sonando, Modifier.fillMaxSize())
            } else {
                Caratula(m?.thumbnailUrl, RoundedCornerShape(36.dp), Modifier.fillMaxSize(), tamano = 480.dp, sombra = 18.dp)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ahora = rememberAhora()
                Column(Modifier.weight(1f)) {
                    Text(horaCorta(ahora), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(fechaLarga(ahora), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BotonCristal(
                    R.drawable.lyrics,
                    stringResource(R.string.carro_letra),
                    { verLetra = !verLetra },
                    tinta = if (verLetra) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                BotonCristal(R.drawable.expand_more, stringResource(R.string.carro_cerrar), alCerrar)
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.carro_sonando).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(6.dp))
            // Al cambiar de cancion el titulo sube y el nuevo entra desde abajo.
            AnimatedContent(
                targetState = m,
                contentKey = { it?.id },
                transitionSpec = {
                    (fadeIn(tween(320, delayMillis = 80)) + slideInVertically(tween(380)) { it / 3 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 4 })
                },
                label = "tituloSonando",
            ) { actual ->
                Column {
                    Text(
                        actual?.title ?: stringResource(R.string.carro_nada_sonando),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Tocar el artista abre su pagina (y cierra Sonando).
                    val artista = actual?.artists?.firstOrNull { it.id != null }
                    val idArtista = artista?.id
                    Text(
                        actual?.artists?.joinToString { it.name } ?: stringResource(R.string.carro_busca_para_empezar),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (artista != null && idArtista != null) {
                            Modifier.clickable { navegador.abrir(Detalle.Artista(idArtista, artista.name, null)) }
                        } else {
                            Modifier
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            BarraOndulada(conexion, sonando, m, Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Controles(conexion, sonando, Modifier.weight(1f), escala = 0.92f)
                MeGusta(conexion, m)
            }
        }
    }
}

/**
 * El corazon. Tocarlo: me gusta / ya no. Mantenerlo: el menu de la cancion
 * (descargar o quitar la descarga, me gusta, radio). Cuando la cancion ya esta
 * descargada lleva una marca abajo; mientras baja, el menu dice el porcentaje.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MeGusta(conexion: PlayerConnection?, m: MediaMetadata?) {
    val contexto = LocalContext.current
    val descargas = LocalDescargas.current
    val cancion by remember(conexion) { conexion?.currentSong ?: flowOf<Song?>(null) }.collectAsState(initial = null)
    val gusta = cancion?.song?.liked == true
    val descarga by remember(descargas, m?.id) {
        m?.id?.let { descargas?.getDownload(it) } ?: flowOf<Download?>(null)
    }.collectAsState(initial = null)
    val descargada = descarga?.state == Download.STATE_COMPLETED
    val bajando = descarga?.state == Download.STATE_DOWNLOADING || descarga?.state == Download.STATE_QUEUED
    var menu by remember { mutableStateOf(false) }

    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .cristal(CircleShape)
                .combinedClickable(onClick = { conexion?.toggleLike() }, onLongClick = { if (m != null) menu = true }),
        ) {
            Icon(
                painterResource(if (gusta) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = stringResource(R.string.carro_me_gusta),
                tint = if (gusta) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
            if (descargada || bajando) {
                Icon(
                    painterResource(R.drawable.download),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(14.dp),
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val texto = MaterialTheme.typography.titleMedium
            when {
                descargada -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.carro_quitar_descarga), style = texto) },
                    leadingIcon = { Icon(painterResource(R.drawable.delete), null) },
                    onClick = {
                        m?.let { DownloadService.sendRemoveDownload(contexto, ExoDownloadService::class.java, it.id, false) }
                        menu = false
                    },
                    modifier = Modifier.height(64.dp),
                )
                bajando -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.carro_descargando, (descarga?.percentDownloaded ?: 0f).toInt().coerceAtLeast(0)), style = texto) },
                    leadingIcon = { Icon(painterResource(R.drawable.close), null) },
                    onClick = {
                        m?.let { DownloadService.sendRemoveDownload(contexto, ExoDownloadService::class.java, it.id, false) }
                        menu = false
                    },
                    modifier = Modifier.height(64.dp),
                )
                else -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.carro_descargar), style = texto) },
                    leadingIcon = { Icon(painterResource(R.drawable.download), null) },
                    onClick = {
                        m?.let { cancionActual ->
                            conexion?.database?.transaction { insert(cancionActual) }
                            val pedido = DownloadRequest.Builder(cancionActual.id, cancionActual.id.toUri())
                                .setCustomCacheKey(cancionActual.id)
                                .setData(cancionActual.title.toByteArray())
                                .build()
                            DownloadService.sendAddDownload(contexto, ExoDownloadService::class.java, pedido, false)
                            Toast.makeText(contexto, contexto.getString(R.string.carro_descarga_empezo, cancionActual.title), Toast.LENGTH_SHORT).show()
                        }
                        menu = false
                    },
                    modifier = Modifier.height(64.dp),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (gusta) R.string.carro_ya_no_me_gusta else R.string.carro_me_gusta), style = texto) },
                leadingIcon = { Icon(painterResource(if (gusta) R.drawable.favorite else R.drawable.favorite_border), null) },
                onClick = {
                    conexion?.toggleLike()
                    menu = false
                },
                modifier = Modifier.height(64.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.carro_radio_cancion), style = texto) },
                leadingIcon = { Icon(painterResource(R.drawable.radio), null) },
                onClick = {
                    conexion?.startRadioSeamlessly()
                    menu = false
                },
                modifier = Modifier.height(64.dp),
            )
        }
    }
}

/**
 * El mini reproductor: en cristal al pie de Buscar, Biblioteca, Cola y Ajustes.
 * Tocarlo abre Sonando; los botones funcionan sin abrirlo. Una linea fina
 * abajo dice por donde va la cancion.
 */
@Composable
fun MiniReproductor(conexion: PlayerConnection?, m: MediaMetadata?, alAbrir: () -> Unit, modifier: Modifier = Modifier) {
    val sonando = sonandoAhora(conexion)
    val forma = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().height(88.dp).cristal(forma).clickable(onClick = alAbrir)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp)) {
            Caratula(m?.thumbnailUrl, RoundedCornerShape(16.dp), Modifier.size(64.dp), tamano = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m?.title ?: stringResource(R.string.carro_nada_sonando),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    m?.artists?.joinToString { it.name }.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Controles(conexion, sonando, escala = 0.66f)
        }
        BarraFina(
            conexion,
            sonando,
            m,
            Modifier.align(Alignment.BottomCenter).padding(start = 90.dp, end = 28.dp, bottom = 6.dp).fillMaxWidth().height(3.dp),
        )
    }
}
