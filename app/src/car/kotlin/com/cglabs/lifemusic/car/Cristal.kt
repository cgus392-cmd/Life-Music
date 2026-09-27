package com.cglabs.lifemusic.car

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp

/**
 * El cristal del carro.
 *
 * El Liquid Glass del telefono refracta lo que tiene detras con RenderEffect,
 * que llega en Android 12; los radios corren Android 8. Aqui el truco es otro:
 * el fondo ambiente ya viene desenfocado de casa (FondoAmbiente), asi que un
 * panel translucido con un reflejo y un filo de luz se lee como cristal
 * esmerilado sin desenfocar nada. Son tres degradados: ni capas fuera de
 * pantalla ni sombreadores, lo aguanta cualquier GPU de radio.
 *
 * [tinte] tine el cuerpo (blanco para los paneles, el color de la caratula para
 * lo activo) y [fuerza] lo hace mas o menos presente.
 */
fun Modifier.cristal(forma: Shape, tinte: Color = Color.White, fuerza: Float = 1f): Modifier = this
    .drawBehind {
        val contorno = forma.createOutline(size, layoutDirection, this)
        // Cuerpo: algo mas de luz arriba, como si le diera el sol por el parabrisas.
        drawOutline(
            contorno,
            Brush.verticalGradient(listOf(tinte.copy(alpha = (0.14f * fuerza).coerceAtMost(0.6f)), tinte.copy(alpha = (0.05f * fuerza).coerceAtMost(0.4f)))),
        )
        // Reflejo: una franja de luz que entra por la esquina de arriba a la izquierda.
        drawOutline(
            contorno,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.42f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
    }
    // El filo: brilla donde da la luz y casi desaparece en el lado contrario.
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.18f))),
        forma,
    )
    .clip(forma)
