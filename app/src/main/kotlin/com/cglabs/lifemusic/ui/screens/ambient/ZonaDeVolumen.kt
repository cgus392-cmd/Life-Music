package com.cglabs.lifemusic.ui.screens.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.AmbienteIndicadorVolumen
import com.cglabs.lifemusic.constants.AmbienteZonaVolumen
import kotlinx.coroutines.delay

/** Ancho de la franja tactil. Generoso: se busca a ciegas, con la pantalla lejos. */
private val ANCHO_ZONA = 56.dp

/** Alto del indicador. Corto y centrado, como el de iOS; no una linea de borde a borde. */
private val ALTO_INDICADOR = 140.dp

/** Cuanto hay que arrastrar para mover el volumen un paso. */
private val PASO_ARRASTRE = 22.dp

/** Tras soltar (o tras una tecla), el indicador se queda este tiempo y se retira. */
private const val REPOSO_MS = 1_100L

/** Aviso del sistema al cambiar un volumen. No es API publica, pero es el que usan todos. */
private const val ACCION_VOLUMEN = "android.media.VOLUME_CHANGED_ACTION"
private const val EXTRA_TIPO_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"

/**
 * Franja al borde de la pantalla donde el arrastre vertical cambia el volumen
 * de la musica. Fuera de ella el arrastre vertical no hace nada: antes, bajar
 * la barra de notificaciones o rozar la pantalla bajaba el volumen.
 *
 * El indicador (icono + linea con el nivel) no es el panel del sistema, que
 * en pantalla completa tapa media letra. Segun [indicador]: DESLIZANTE asoma
 * desde el borde al usarlo y se retira solo; FIJO queda siempre tenue; OCULTO
 * no dibuja nada. Las teclas fisicas tambien lo despiertan: la pantalla
 * escucha el aviso de cambio de volumen del sistema.
 */
@Composable
fun ZonaDeVolumen(
    lado: AmbienteZonaVolumen,
    indicador: AmbienteIndicadorVolumen,
    animar: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maximo = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var nivel by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var arrastrando by remember { mutableStateOf(false) }
    // Sube con cada cambio (arrastre o tecla) para reiniciar la cuenta del retiro.
    var cambios by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    var acumulado by remember { mutableFloatStateOf(0f) }

    // Teclas fisicas (y cualquier otra cosa que toque el volumen): el nivel se
    // relee y el indicador asoma, igual que con el arrastre.
    DisposableEffect(context) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.getIntExtra(EXTRA_TIPO_STREAM, -1) != AudioManager.STREAM_MUSIC) return
                val nuevo = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (nuevo != nivel) {
                    nivel = nuevo
                    cambios++
                }
            }
        }
        context.registerReceiver(receptor, IntentFilter(ACCION_VOLUMEN))
        onDispose { runCatching { context.unregisterReceiver(receptor) } }
    }

    // Teclas atendidas por la actividad (sin panel del sistema): el nivel se
    // relee y el indicador asoma aunque ya estuviera al tope.
    val pulsaciones by AmbienteVolumen.pulsaciones.collectAsState()
    LaunchedEffect(pulsaciones) {
        if (pulsaciones == 0) return@LaunchedEffect
        nivel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        cambios++
    }

    // Visible mientras se arrastra y un respiro despues del ultimo cambio.
    LaunchedEffect(arrastrando, cambios) {
        visible = true
        if (arrastrando) return@LaunchedEffect
        delay(REPOSO_MS)
        visible = false
    }

    val duracion = if (animar) 220 else 0
    val mostrar = indicador != AmbienteIndicadorVolumen.OCULTO
    val fijo = indicador == AmbienteIndicadorVolumen.FIJO
    // FIJO: siempre en su sitio, tenue en reposo. DESLIZANTE: fuera de pantalla
    // y transparente en reposo; entra por el borde al usarlo.
    val alfa by animateFloatAsState(
        targetValue = when {
            !mostrar -> 0f
            visible -> 1f
            fijo -> 0.3f
            else -> 0f
        },
        animationSpec = tween(duracion),
        label = "alfaIndicador"
    )
    val desplazamiento by animateFloatAsState(
        targetValue = if (fijo || visible) 0f else 1f,
        animationSpec = tween(duracion),
        label = "desplazamientoIndicador"
    )
    val fraccion by animateFloatAsState(
        targetValue = if (maximo > 0) nivel.toFloat() / maximo else 0f,
        animationSpec = tween(if (animar) 120 else 0),
        label = "fraccionVolumen"
    )

    val pasoPx = with(LocalDensity.current) { PASO_ARRASTRE.toPx() }
    // Hacia donde se esconde: fuera de su propio borde.
    val sentido = if (lado == AmbienteZonaVolumen.IZQUIERDA) -1f else 1f

    Box(
        modifier = modifier
            .width(ANCHO_ZONA)
            .fillMaxHeight()
            // Deja libre la esquina superior (flecha atras) y el borde inferior.
            .padding(top = 72.dp, bottom = 40.dp)
            .pointerInput(maximo) {
                detectVerticalDragGestures(
                    onDragStart = {
                        nivel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        acumulado = 0f
                        arrastrando = true
                    },
                    onDragEnd = { arrastrando = false },
                    onDragCancel = { arrastrando = false },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        acumulado += dy
                        // Arriba sube (dy negativo), abajo baja; un paso por tramo.
                        val antes = nivel
                        while (acumulado <= -pasoPx) {
                            acumulado += pasoPx
                            if (nivel < maximo) nivel++
                        }
                        while (acumulado >= pasoPx) {
                            acumulado -= pasoPx
                            if (nivel > 0) nivel--
                        }
                        if (nivel != antes) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nivel, 0)
                            cambios++
                            com.cglabs.lifemusic.ui.guia.Guia.hecho(com.cglabs.lifemusic.ui.guia.Guia.EV_VOLUMEN)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (mostrar) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(x = ANCHO_ZONA * desplazamiento * sentido)
                    .alpha(alfa)
            ) {
                Icon(
                    painter = painterResource(
                        when {
                            nivel == 0 -> R.drawable.volume_off
                            fraccion < 0.5f -> R.drawable.volume_down
                            else -> R.drawable.volume_up
                        }
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.height(8.dp))
                Canvas(
                    modifier = Modifier
                        .width(5.dp)
                        .height(ALTO_INDICADOR)
                ) {
                    val radio = CornerRadius(size.width / 2f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.3f),
                        cornerRadius = radio
                    )
                    val altoNivel = size.height * fraccion
                    if (altoNivel > 0f) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(0f, size.height - altoNivel),
                            size = Size(size.width, altoNivel),
                            cornerRadius = radio
                        )
                    }
                }
            }
        }
    }
}
