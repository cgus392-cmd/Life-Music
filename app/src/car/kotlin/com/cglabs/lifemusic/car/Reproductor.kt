package com.cglabs.lifemusic.car

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * (con Atras, o bajandola con el dedo). El fondo es el mismo ambiente de
 * siempre, que sigue debajo.
 */
@Composable
fun SonandoCompleto(conexion: PlayerConnection?, m: MediaMetadata?, alCerrar: () -> Unit, modifier: Modifier = Modifier) {
    val sonando = sonandoAhora(conexion)
    val navegador = LocalNavegador.current
    var verLetra by rememberSaveable { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    val umbral = with(LocalDensity.current) { 96.dp.toPx() }
    // Bajar para cerrar: toda la pantalla sigue al dedo hacia abajo y se desvanece.
    val bajada = remember { Animatable(0f) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = bajada.value
                alpha = 1f - (bajada.value / size.height * 1.6f).coerceIn(0f, 0.7f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { cambio, dy ->
                        cambio.consume()
                        alcance.launch { bajada.snapTo((bajada.value + dy).coerceAtLeast(0f)) }
                    },
                    onDragEnd = {
                        if (bajada.value > umbral) alCerrar() else alcance.launch { bajada.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                    onDragCancel = { alcance.launch { bajada.animateTo(0f) } },
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
            .padding(28.dp),
    ) {
        // La caratula o, con el boton Letra, la letra en su lugar.
        Crossfade(targetState = verLetra, animationSpec = tween(350), label = "caratulaOLetra", modifier = Modifier.fillMaxHeight().aspectRatio(1f)) { letra ->
            if (letra) {
                Letra(conexion, sonando, Modifier.fillMaxSize())
            } else {
                CaratulaConGestos(conexion, m, umbral, Modifier.fillMaxSize())
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
 * La caratula grande de Sonando, que tambien se maneja con el dedo:
 *  - deslizarla a la izquierda pasa a la siguiente; a la derecha, la anterior
 *    (sigue al dedo, se va por el lado y la nueva entra desde el otro);
 *  - doble toque: me gusta, con un corazon que late encima. Si ya gustaba no la
 *    quita (un doble toque por un bache no deberia borrar nada).
 */
@Composable
private fun CaratulaConGestos(conexion: PlayerConnection?, m: MediaMetadata?, umbral: Float, modifier: Modifier = Modifier) {
    val alcance = rememberCoroutineScope()
    val lado = remember { Animatable(0f) }
    val latido = remember { Animatable(0f) }
    val cancion by remember(conexion) { conexion?.currentSong ?: flowOf<Song?>(null) }.collectAsState(initial = null)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                translationX = lado.value
                rotationZ = lado.value / size.width * 8f
                alpha = 1f - (kotlin.math.abs(lado.value) / size.width).coerceIn(0f, 0.8f)
            }
            .pointerInput(conexion) {
                detectTapGestures(onDoubleTap = {
                    if (cancion?.song?.liked != true) conexion?.toggleLike()
                    alcance.launch {
                        latido.snapTo(0.01f)
                        latido.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                        delay(450)
                        latido.animateTo(0f, tween(220))
                    }
                })
            }
            .pointerInput(conexion) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { cambio, dx ->
                        cambio.consume()
                        alcance.launch { lado.snapTo(lado.value + dx) }
                    },
                    onDragEnd = {
                        val hacia = when {
                            lado.value < -umbral -> -1f
                            lado.value > umbral -> 1f
                            else -> 0f
                        }
                        alcance.launch {
                            if (hacia == 0f) {
                                lado.animateTo(0f, spring(dampingRatio = 0.7f))
                                return@launch
                            }
                            lado.animateTo(hacia * size.width, tween(160))
                            if (hacia < 0) conexion?.seekToNext() else conexion?.seekToPrevious()
                            lado.snapTo(-hacia * size.width * 0.5f)
                            lado.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 300f))
                        }
                    },
                    onDragCancel = { alcance.launch { lado.animateTo(0f) } },
                )
            },
    ) {
        Caratula(m?.thumbnailUrl, RoundedCornerShape(36.dp), Modifier.fillMaxSize(), tamano = 480.dp, sombra = 18.dp)
        // El canvas, si toca y existe, encima de la caratula; los gestos siguen siendo del cuadro.
        CanvasDeCaratula(conexion, m, sonandoAhora(conexion), Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)))
        if (latido.value > 0f) {
            Icon(
                painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = latido.value
                        scaleY = latido.value
                        alpha = latido.value.coerceIn(0f, 1f)
                    },
            )
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
