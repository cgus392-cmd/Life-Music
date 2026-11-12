package com.cglabs.lifemusic.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * 2.1.x · Mixed Editor: la luz que se le prende a la barra de progreso mientras mezcla.
 *
 * Sale del video de AutoMix de Apple (medido cuadro a cuadro el 11-11): al aparecer «Mixing», la parte llena de la
 * barra se ilumina en unos 0,3 s con un halo blanco suave. La luz se queda quieta, sin titilar, toda la mezcla. Se
 * apaga despacio, en unos 4 s, cuando la nueva ya suena sola.
 *
 * El halo son capas de trazos redondos, cada una más ancha y más tenue, que juntas imitan un desenfoque. No usa
 * filtros de desenfoque, así que sirve en cualquier Android y cuesta cinco líneas por cuadro. Solo se dibuja mientras
 * la intensidad es mayor que cero. Lee [intensidad] y [progreso] al pintar, de modo que la animación no recompone el
 * reproductor.
 */
fun Modifier.brilloDeMezcla(intensidad: () -> Float, progreso: () -> Float, color: Color): Modifier = drawBehind {
    val k = intensidad()
    if (k <= 0.01f) return@drawBehind
    val fin = size.width * progreso().coerceIn(0f, 1f)
    if (fin <= 0f) return@drawBehind
    val y = size.height / 2f
    for ((ancho, alfa) in CAPAS) {
        drawLine(
            color = color.copy(alpha = alfa * k),
            start = Offset(0f, y),
            end = Offset(fin, y),
            strokeWidth = ancho.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Ancho en dp y opacidad de cada capa, de la más ancha y tenue a la más angosta y fuerte. */
private val CAPAS = listOf(26f to 0.035f, 20f to 0.05f, 15f to 0.08f, 11f to 0.12f, 8f to 0.18f)
