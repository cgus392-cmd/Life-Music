package com.cglabs.lifemusic.ui.screens.ambient

import android.app.Activity
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.canvas.CanvasArtwork
import com.cglabs.lifemusic.constants.AmbienteCanvasKey
import com.cglabs.lifemusic.constants.AmbienteControlesEstilo
import com.cglabs.lifemusic.constants.AmbienteControlesEstiloKey
import com.cglabs.lifemusic.constants.AmbienteControlesKey
import com.cglabs.lifemusic.constants.AmbienteControlesSegundosDefault
import com.cglabs.lifemusic.constants.AmbienteControlesSegundosKey
import com.cglabs.lifemusic.constants.AmbienteZonaVolumen
import com.cglabs.lifemusic.constants.AmbienteZonaVolumenKey
import com.cglabs.lifemusic.constants.CanvasThumbnailAnimationKey
import com.cglabs.lifemusic.extensions.togglePlayPause
import com.cglabs.lifemusic.ui.player.CanvasArtworkPlayer
import com.cglabs.lifemusic.ui.player.InlineLyricsView
import com.cglabs.lifemusic.ui.player.buscarCanvas
import com.cglabs.lifemusic.utils.rememberEnumPreference
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Duracion de la entrada y salida del panel de controles. */
private const val CONTROLES_ANIM_MS = 220

@Composable
fun AmbientModeScreen(navController: NavController) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val zonaVolumen by rememberEnumPreference(AmbienteZonaVolumenKey, AmbienteZonaVolumen.DERECHA)
    val canvasEnAmbiente by rememberPreference(AmbienteCanvasKey, defaultValue = true)
    val controlesAlTocar by rememberPreference(AmbienteControlesKey, defaultValue = true)
    val controlesEstilo by rememberEnumPreference(AmbienteControlesEstiloKey, AmbienteControlesEstilo.CORTE)
    val controlesSegundos by rememberPreference(AmbienteControlesSegundosKey, defaultValue = AmbienteControlesSegundosDefault)
    // El interruptor general de canvas del reproductor manda sobre el nuestro:
    // si el usuario los apago ahi, aqui tampoco se descargan.
    val canvasGeneral by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val mostrarCanvas = canvasEnAmbiente && canvasGeneral

    // Sin animaciones del sistema (accesibilidad o ahorro de bateria) el panel
    // aparece y desaparece de golpe; una transicion a medias seria peor.
    val animar = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        var windowInsetsController: WindowInsetsControllerCompat? = null
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        navController.popBackStack()
    }

    // ── Canvas de la cancion ────────────────────────────────────────────────
    // Apagado, no se busca nada: ni red ni estado. Encendido, primero la cache
    // (si el reproductor ya lo trajo, aparece al instante) y si no, se pide.
    var canvas by remember(mediaMetadata?.id) { mutableStateOf<CanvasArtwork?>(null) }
    if (mostrarCanvas) {
        LaunchedEffect(mediaMetadata?.id) {
            val item = mediaMetadata ?: return@LaunchedEffect
            canvas = buscarCanvas(item)
        }
    }

    // ── Controles pasivos ───────────────────────────────────────────────────
    var controlesVisibles by remember { mutableStateOf(false) }
    // Cada toque en un control reinicia la cuenta atras.
    var ultimoToque by remember { mutableIntStateOf(0) }
    if (controlesAlTocar) {
        LaunchedEffect(controlesVisibles, ultimoToque, controlesSegundos) {
            if (!controlesVisibles) return@LaunchedEffect
            delay(controlesSegundos * 1_000L)
            controlesVisibles = false
        }
    }

    var swipeThresholdX by remember { mutableStateOf(0f) }
    var swipeThresholdY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Solo el barrido horizontal (anterior / siguiente). El
                // vertical ya no toca el volumen: eso vive en su franja.
                detectDragGestures(
                    onDragEnd = {
                        if (abs(swipeThresholdX) > 150f && abs(swipeThresholdX) > abs(swipeThresholdY)) {
                            if (swipeThresholdX > 0) {
                                playerConnection.player.seekToPreviousMediaItem()
                            } else {
                                playerConnection.player.seekToNext()
                            }
                        }
                        swipeThresholdX = 0f
                        swipeThresholdY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        swipeThresholdX += dragAmount.x
                        swipeThresholdY += dragAmount.y
                    }
                )
            }
    ) {
        AmbientGlowBackground(
            mediaMetadata = mediaMetadata,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Izquierda: la caratula (o su canvas), con los controles dentro.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // El recorte lo pone este Box, no la imagen: asi el panel de
                // controles queda cortado por la misma forma que la portada.
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(controlesAlTocar) {
                            detectTapGestures(
                                onTap = {
                                    if (controlesAlTocar) {
                                        controlesVisibles = !controlesVisibles
                                        ultimoToque++
                                    }
                                },
                                onDoubleTap = {
                                    playerConnection.togglePlayPause()
                                }
                            )
                        }
                ) {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // La imagen se queda debajo hasta que el video tiene su
                    // primer fotograma; el propio CanvasArtworkPlayer lo funde.
                    if (mostrarCanvas) {
                        canvas?.let { artwork ->
                            CanvasArtworkPlayer(
                                primaryUrl = artwork.animated,
                                fallbackUrl = artwork.videoUrl,
                                isPlaying = isPlaying,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (controlesAlTocar) {
                        ControlesDeCaratula(
                            visibles = controlesVisibles,
                            estilo = controlesEstilo,
                            animar = animar,
                            isPlaying = isPlaying,
                            onAnterior = {
                                playerConnection.player.seekToPreviousMediaItem()
                                ultimoToque++
                            },
                            onPausa = {
                                playerConnection.togglePlayPause()
                                ultimoToque++
                            },
                            onSiguiente = {
                                playerConnection.player.seekToNext()
                                ultimoToque++
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }

            // Derecha: la letra.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 32.dp, top = 32.dp, bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                InlineLyricsView(
                    mediaMetadata = mediaMetadata,
                    showLyrics = true,
                    positionProvider = { playerConnection.player.currentPosition }
                )
            }
        }

        if (zonaVolumen != AmbienteZonaVolumen.DESACTIVADA) {
            ZonaDeVolumen(
                animar = animar,
                modifier = Modifier
                    .align(if (zonaVolumen == AmbienteZonaVolumen.DERECHA) Alignment.CenterEnd else Alignment.CenterStart)
                    .safeDrawingPadding()
            )
        }

        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.ambient_back),
                tint = Color.White
            )
        }
    }
}

/** Alto de la banda de controles. */
private val ALTO_BANDA = 72.dp

/**
 * Panel con anterior / pausa / siguiente que sube desde el borde inferior de la
 * caratula. La portada no se mueve: el panel la «corta» por abajo y el Box que
 * lo contiene lo recorta con la misma esquina redondeada.
 *
 * Tres estilos: CORTE (banda solida con un filo arriba, la que pidio CG),
 * SUAVE (degradado que se funde con la portada) y LIMPIO (solo los iconos, con
 * una sombra para que se lean sobre cualquier caratula).
 */
@Composable
private fun ControlesDeCaratula(
    visibles: Boolean,
    estilo: AmbienteControlesEstilo,
    animar: Boolean,
    isPlaying: Boolean,
    onAnterior: () -> Unit,
    onPausa: () -> Unit,
    onSiguiente: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duracion = if (animar) CONTROLES_ANIM_MS else 0
    AnimatedVisibility(
        visible = visibles,
        enter = slideInVertically(tween(duracion)) { it } + fadeIn(tween(duracion)),
        exit = slideOutVertically(tween(duracion)) { it } + fadeOut(tween(duracion)),
        modifier = modifier
    ) {
        val fondo: Modifier = when (estilo) {
            AmbienteControlesEstilo.CORTE -> Modifier
                .background(Color.Black.copy(alpha = 0.62f))
                // El filo: una linea de un pixel fisico arriba, como un corte.
                .drawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.White.copy(alpha = 0.28f),
                        size = Size(size.width, 1.dp.toPx().coerceAtLeast(1f))
                    )
                }
            AmbienteControlesEstilo.SUAVE -> Modifier.background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.7f)
                )
            )
            AmbienteControlesEstilo.LIMPIO -> Modifier
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTO_BANDA)
                .then(fondo)
        ) {
            BotonDeBanda(R.drawable.skip_previous, stringResource(R.string.ambient_previous), 28.dp, estilo, onAnterior)
            BotonDeBanda(
                if (isPlaying) R.drawable.pause else R.drawable.play,
                stringResource(if (isPlaying) R.string.pause else R.string.play),
                36.dp, estilo, onPausa
            )
            BotonDeBanda(R.drawable.skip_next, stringResource(R.string.ambient_next), 28.dp, estilo, onSiguiente)
        }
    }
}

@Composable
private fun BotonDeBanda(
    icono: Int,
    descripcion: String,
    tamano: Dp,
    estilo: AmbienteControlesEstilo,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            // Sin banda debajo, una sombra suave para que el icono se lea sobre
            // una caratula clara. Con banda, la banda ya hace ese trabajo.
            if (estilo == AmbienteControlesEstilo.LIMPIO) {
                Icon(
                    painter = painterResource(icono),
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .size(tamano)
                        .offset(x = 1.dp, y = 1.dp)
                        .blur(3.dp)
                )
            }
            Icon(
                painter = painterResource(icono),
                contentDescription = descripcion,
                tint = Color.White,
                modifier = Modifier.size(tamano)
            )
        }
    }
}
