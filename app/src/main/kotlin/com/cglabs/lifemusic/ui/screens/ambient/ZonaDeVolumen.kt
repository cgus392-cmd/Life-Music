package com.cglabs.lifemusic.ui.screens.ambient

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.delay

/** Ancho de la franja tactil. Generoso: se busca a ciegas, con la pantalla lejos. */
private val ANCHO_ZONA = 56.dp

/** Cuanto hay que arrastrar para mover el volumen un paso. */
private val PASO_ARRASTRE = 22.dp

/** Tras soltar, el indicador sigue encendido este tiempo y luego se atenua. */
private const val REPOSO_MS = 900L

/**
 * Franja al borde de la pantalla donde el arrastre vertical cambia el volumen
 * de la musica. Fuera de ella el arrastre vertical no hace nada: antes, bajar
 * la barra de notificaciones o rozar la pantalla bajaba el volumen.
 *
 * Dibuja su propio indicador (una linea con el nivel) en vez de pedir el panel
 * de volumen del sistema, que en pantalla completa tapa media letra.
 */
@Composable
fun ZonaDeVolumen(
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
    var destacado by remember { mutableStateOf(false) }
    var acumulado by remember { mutableFloatStateOf(0f) }

    // Al soltar, un respiro antes de volver al reposo.
    LaunchedEffect(arrastrando) {
        if (arrastrando) {
            destacado = true
        } else {
            delay(REPOSO_MS)
            destacado = false
        }
    }

    val duracion = if (animar) 200 else 0
    val alfaPista by animateFloatAsState(
        targetValue = if (destacado) 0.35f else 0.10f,
        animationSpec = tween(duracion),
        label = "alfaPista"
    )
    val alfaNivel by animateFloatAsState(
        targetValue = if (destacado) 0.95f else 0.28f,
        animationSpec = tween(duracion),
        label = "alfaNivel"
    )
    val alfaIcono by animateFloatAsState(
        targetValue = if (destacado) 1f else 0f,
        animationSpec = tween(duracion),
        label = "alfaIcono"
    )
    val fraccion by animateFloatAsState(
        targetValue = if (maximo > 0) nivel.toFloat() / maximo else 0f,
        animationSpec = tween(if (animar) 120 else 0),
        label = "fraccionVolumen"
    )

    val pasoPx = with(LocalDensity.current) { PASO_ARRASTRE.toPx() }

    Box(
        modifier = modifier
            .width(ANCHO_ZONA)
            .fillMaxHeight()
            // Deja libre la esquina superior (flecha atras) y el borde inferior.
            .padding(top = 72.dp, bottom = 40.dp)
            .pointerInput(maximo) {
                detectVerticalDragGestures(
                    onDragStart = {
                        // El volumen pudo cambiar por las teclas fisicas.
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
                        while (acumulado <= -pasoPx) {
                            acumulado += pasoPx
                            if (nivel < maximo) nivel++
                        }
                        while (acumulado >= pasoPx) {
                            acumulado -= pasoPx
                            if (nivel > 0) nivel--
                        }
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nivel, 0)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight()
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
                modifier = Modifier
                    .size(20.dp)
                    .alpha(alfaIcono)
            )
            Spacer(Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .width(4.dp)
                    .weight(1f)
            ) {
                val radio = CornerRadius(size.width / 2f)
                drawRoundRect(
                    color = Color.White.copy(alpha = alfaPista),
                    cornerRadius = radio
                )
                val altoNivel = size.height * fraccion
                if (altoNivel > 0f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = alfaNivel),
                        topLeft = Offset(0f, size.height - altoNivel),
                        size = Size(size.width, altoNivel),
                        cornerRadius = radio
                    )
                }
            }
        }
    }
}
