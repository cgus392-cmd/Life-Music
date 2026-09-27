@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cglabs.lifemusic.car

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.models.toMediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.playback.queues.YouTubeQueue
import com.cglabs.lifemusic.ui.theme.DefaultThemeColor
import com.cglabs.lifemusic.ui.theme.LifeMusicTheme
import com.cglabs.lifemusic.ui.theme.extractThemeColor
import com.cglabs.lifemusic.ui.utils.resize
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La primera pantalla de Life Music for Car: a la izquierda lo que suena, a la
 * derecha buscar. Pensada para un radio de 1024×600 que se mira de reojo:
 * todo lo que se toca mide 64 dp o mas, el titulo se lee desde el asiento y el
 * color sale de la caratula (MaterialKolor; los radios corren Android 8, sin
 * color dinamico del sistema). Sin efectos pesados: ni desenfoques ni video.
 */
@Composable
fun PantallaCarro(conexion: PlayerConnection?) {
    val metadatos by (conexion?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf<MediaMetadata?>(null) })
    val semilla = colorDeCaratula(metadatos?.thumbnailUrl)
    val color by animateColorAsState(semilla, tween(900), label = "colorCaratula")

    LifeMusicTheme(darkTheme = true, themeColor = color) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Row(
                // Lejos de la barra de navegacion del sistema (y de la de estado si aparece).
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Sonando(conexion, metadatos, Modifier.weight(1.1f).fillMaxHeight())
                Buscar(conexion, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** El color principal de la caratula (pequena, para no gastar memoria), o el verde de Life Music. */
@Composable
private fun colorDeCaratula(url: String?): Color {
    val contexto = LocalContext.current
    var color by remember { mutableStateOf(DefaultThemeColor) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                contexto.imageLoader.execute(
                    ImageRequest.Builder(contexto).data(url.resize(96, 96)).size(96, 96).allowHardware(false).build()
                ).image?.toBitmap()
            }.getOrNull()
        }
        bitmap?.let { color = withContext(Dispatchers.Default) { it.extractThemeColor() } }
    }
    return color
}

@Composable
private fun Sonando(conexion: PlayerConnection?, m: MediaMetadata?, modifier: Modifier) {
    val sonando by (conexion?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) })
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // La caratula ocupa el alto disponible: es lo que se reconoce de reojo.
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxHeight(0.92f).aspectRatio(1f),
            ) {
                val caratula = m?.thumbnailUrl
                if (caratula != null) {
                    AsyncImage(
                        model = caratula.resize(400, 400),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(painterResource(R.drawable.music_note), contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = m?.title ?: stringResource(R.string.carro_nada_sonando),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = m?.artists?.joinToString { it.name } ?: stringResource(R.string.carro_busca_para_empezar),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Progreso(conexion, sonando)
        Spacer(Modifier.height(12.dp))
        Controles(conexion, sonando)
    }
}

/** La barra ondulada de Expressive: ondula mientras suena y se queda lisa en pausa. */
@Composable
private fun Progreso(conexion: PlayerConnection?, sonando: Boolean) {
    var posicion by remember { mutableLongStateOf(0L) }
    var duracion by remember { mutableLongStateOf(0L) }
    LaunchedEffect(conexion, sonando) {
        val p = conexion?.player ?: return@LaunchedEffect
        while (true) {
            posicion = p.currentPosition.coerceAtLeast(0L)
            duracion = p.duration.takeIf { it > 0 } ?: 0L
            if (!sonando) break
            delay(500)
        }
    }
    val fraccion = if (duracion > 0) (posicion.toFloat() / duracion).coerceIn(0f, 1f) else 0f
    LinearWavyProgressIndicator(
        progress = { fraccion },
        amplitude = { if (sonando) 1f else 0f },
        modifier = Modifier.fillMaxWidth().height(14.dp),
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(reloj(posicion), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(reloj(duracion), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun reloj(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Anterior, reproducir/pausa y siguiente, grandes. El boton central cambia de
 * forma como en Expressive: redondo en pausa, cuadrado suave sonando.
 */
@Composable
private fun Controles(conexion: PlayerConnection?, sonando: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BotonControl(R.drawable.skip_previous, R.string.carro_anterior, 76.dp, 38.dp, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
            conexion?.seekToPrevious()
        }
        val esquina by animateDpAsState(if (sonando) 28.dp else 48.dp, spring(dampingRatio = 0.6f, stiffness = 400f), label = "formaPlay")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 128.dp, height = 96.dp)
                .clip(RoundedCornerShape(esquina))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = conexion != null) { conexion?.togglePlayPause() },
        ) {
            Icon(
                painterResource(if (sonando) R.drawable.pause else R.drawable.play),
                contentDescription = stringResource(if (sonando) R.string.carro_pausar else R.string.carro_reproducir),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            )
        }
        BotonControl(R.drawable.skip_next, R.string.carro_siguiente, 76.dp, 38.dp, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
            conexion?.seekToNext()
        }
    }
}

@Composable
private fun BotonControl(icono: Int, descripcion: Int, lado: androidx.compose.ui.unit.Dp, iconoLado: androidx.compose.ui.unit.Dp, fondo: Color, tinta: Color, alTocar: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(lado).clip(RoundedCornerShape(lado / 2)).background(fondo).clickable(onClick = alTocar),
    ) {
        Icon(painterResource(icono), contentDescription = stringResource(descripcion), tint = tinta, modifier = Modifier.size(iconoLado))
    }
}

/** Buscar canciones: el campo, y cada resultado toca y suena (con su radio detras). */
@Composable
private fun Buscar(conexion: PlayerConnection?, modifier: Modifier) {
    var texto by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    val foco = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current

    fun buscar() {
        val q = texto.trim()
        if (q.isEmpty()) return
        teclado?.hide()
        foco.clearFocus()
        buscando = true
        alcance.launch {
            resultados = withContext(Dispatchers.IO) {
                YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
            }
            buscando = false
        }
    }

    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.carro_buscar_pista), style = MaterialTheme.typography.titleMedium) },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { buscar() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            when {
                buscando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(72.dp)) }
                resultados.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.carro_buscar_vacio),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(resultados, key = { it.id }) { cancion ->
                        FilaResultado(cancion) { conexion?.playQueue(YouTubeQueue.radio(cancion.toMediaMetadata())) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaResultado(cancion: SongItem, alTocar: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = alTocar)
            .padding(horizontal = 8.dp),
    ) {
        AsyncImage(
            model = cancion.thumbnail.resize(160, 160),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(cancion.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                cancion.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
