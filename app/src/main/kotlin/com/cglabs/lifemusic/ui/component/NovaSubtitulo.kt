package com.cglabs.lifemusic.ui.component

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.nova.NovaFrases
import com.cglabs.lifemusic.nova.NovaOnda
import com.cglabs.lifemusic.nova.NovaSesion

/**
 * 2.0 · Lo que dice Nova, en la fila de Life Line (maqueta aprobada, pieza D).
 *
 * La letra de la cancion barre; la voz de la app respira; Nova lleva su estrella
 * y su onda, que salen de su voz de verdad. Asi se sabe quien habla. La frase va
 * por trozos que caben en una linea, y dentro de cada trozo las palabras se
 * encienden al ritmo en que las dice. Al terminar se queda quieta unos segundos:
 * tocarla la repite. Ficha: docs/versiones/2.0/nova.md (4.7).
 */
@Composable
fun NovaSubtitulo(
    habla: NovaSesion.Habla,
    baseColor: Color,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    tamano: TextUnit = 14.sp,
    altoMinimo: Dp = 32.dp,
) {
    val trozos = remember(habla.texto) { NovaFrases.trozos(habla.texto).ifEmpty { listOf(habla.texto) } }
    val pesos = remember(trozos) { trozos.map { it.length.coerceAtLeast(1) } }
    val total = remember(pesos) { pesos.sum().toFloat() }

    // El reloj de la frase: cada fotograma mientras habla; quieto cuando termino.
    var ahora by remember(habla) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(habla) {
        if (habla.terminada) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            ahora = SystemClock.elapsedRealtime()
        }
    }
    val transcurrido = (ahora - habla.empezoMs).coerceAtLeast(0L)
    val progreso = if (habla.terminada) 1f else (transcurrido.toFloat() / habla.duracionMs).coerceIn(0f, 1f)
    val nivel = if (habla.terminada) 0f else NovaOnda.nivel(habla.onda, transcurrido)

    // Que trozo va y cuanto de el se dijo, repartiendo el tiempo por largo.
    var acumulado = 0f
    var indice = trozos.lastIndex
    var dentro = 1f
    for (i in trozos.indices) {
        val hasta = acumulado + pesos[i] / total
        if (progreso < hasta) {
            indice = i
            dentro = ((progreso - acumulado) / (pesos[i] / total)).coerceIn(0f, 1f)
            break
        }
        acumulado = hasta
    }

    val colores = rememberColoresAurora()
    FilaLifeLine(onClick = alTocar, modifier = modifier, altoMinimo = altoMinimo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NovaMirada(
                estado = if (habla.terminada) EstadoNova.TERMINO else EstadoNova.HABLA,
                energia = nivel,
                colores = colores,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(4.dp))
            OndaDeNova(habla = habla, transcurridoMs = transcurrido, color = baseColor)
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = indice,
                transitionSpec = { fadeIn(tween(260)).togetherWith(fadeOut(tween(200))) },
                label = "novaTrozo",
            ) { i ->
                val trozo = trozos[i]
                val palabras = trozo.split(' ')
                // En el trozo que suena, las dichas brillan y las que faltan esperan.
                val dichas = when {
                    i < indice -> palabras.size
                    i > indice -> 0
                    else -> (palabras.size * dentro).toInt().coerceIn(1, palabras.size)
                }
                Text(
                    text = buildAnnotatedString {
                        palabras.forEachIndexed { p, palabra ->
                            if (p > 0) append(' ')
                            withStyle(SpanStyle(color = baseColor.copy(alpha = if (p < dichas) 0.95f else 0.42f))) {
                                append(palabra)
                            }
                        }
                    },
                    fontSize = tamano,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Siete barras con la energia de los ultimos tramos de la voz: la onda real de Nova. */
@Composable
private fun OndaDeNova(habla: NovaSesion.Habla, transcurridoMs: Long, color: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 14.dp)) {
        val barras = 7
        val ancho = 2.dp.toPx()
        val hueco = (size.width - ancho * barras) / (barras - 1)
        for (b in 0 until barras) {
            // La barra del centro es el ahora; las de los lados, un poco antes.
            val atras = kotlin.math.abs(b - barras / 2) * NovaOnda.TRAMO_MS
            val nivel = if (habla.terminada) 0f else NovaOnda.nivel(habla.onda, transcurridoMs - atras)
            val alto = (size.height * (0.18f + 0.82f * nivel)).coerceAtMost(size.height)
            drawRoundRect(
                color = color.copy(alpha = 0.8f),
                topLeft = Offset(b * (ancho + hueco), (size.height - alto) / 2f),
                size = Size(ancho, alto),
                cornerRadius = CornerRadius(ancho / 2f, ancho / 2f),
            )
        }
    }
}
