package com.cglabs.lifemusic.clip

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cglabs.lifemusic.R

/**
 * Lo que se ve mientras se graba el modo ambiente: la cuenta atras en el
 * centro y luego una pastilla arriba con el punto rojo, el tiempo y «detener».
 * Va en un Popup, que es otra ventana: PixelCopy captura solo la de la app,
 * asi que nada de esto sale en el video. Vive en MainActivity.
 */
@Composable
fun GrabacionOverlay() {
    val estado by GrabadorDeAmbiente.estado.collectAsState()
    if (estado is GrabadorDeAmbiente.Estado.Inactivo) return
    val propiedades = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    when (val e = estado) {
        is GrabadorDeAmbiente.Estado.CuentaAtras -> Popup(alignment = Alignment.Center, properties = propiedades) {
            Text(
                text = e.n.toString(),
                style = TextStyle(
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 4f), 24f),
                ),
            )
        }
        is GrabadorDeAmbiente.Estado.Grabando -> Popup(alignment = Alignment.TopCenter, properties = propiedades) {
            Pastilla {
                val parpadeo = rememberInfiniteTransition(label = "rec")
                val alfa by parpadeo.animateFloat(
                    initialValue = 1f, targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
                    label = "recAlfa",
                )
                Box(
                    Modifier
                        .size(10.dp)
                        .alpha(alfa)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${tiempo(e.segundos)} / ${tiempo(e.total)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { GrabadorDeAmbiente.detener() }) {
                    Text(stringResource(R.string.clip_detener), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        else -> Popup(alignment = Alignment.TopCenter, properties = propiedades) {
            Pastilla {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        if (e is GrabadorDeAmbiente.Estado.Preparando) R.string.clip_grabacion_preparando
                        else R.string.clip_grabacion_guardando
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Pastilla(contenido: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        content = contenido,
    )
}

private fun tiempo(s: Int): String = "%d:%02d".format(s / 60, s % 60)
