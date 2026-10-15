package com.cglabs.lifemusic.ui.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.cglabs.lifemusic.nova.NovaOnda
import com.cglabs.lifemusic.nova.NovaSesion
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Lo que esta haciendo Nova, para su mirada. */
enum class EstadoNova { APAGADA, ESCUCHA, PIENSA, HABLA, TERMINO }

/**
 * El estado de Nova ahora mismo y la energia de su voz (0 a 1, real, del audio
 * que esta diciendo). Lee la sesion; mientras habla, se actualiza cada fotograma.
 */
@Composable
fun rememberEstadoNova(): Pair<EstadoNova, Float> {
    val alAire by NovaSesion.alAire.collectAsState()
    val habla by NovaSesion.hablando.collectAsState()
    val pensando by NovaSesion.pensando.collectAsState()

    var ahora by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val hablando = habla?.takeIf { !it.terminada }
    LaunchedEffect(hablando) {
        if (hablando == null) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            ahora = SystemClock.elapsedRealtime()
        }
    }
    val estado = when {
        hablando != null -> EstadoNova.HABLA
        habla?.terminada == true -> EstadoNova.TERMINO
        pensando -> EstadoNova.PIENSA
        alAire -> EstadoNova.ESCUCHA
        else -> EstadoNova.APAGADA
    }
    val energia = hablando?.let { NovaOnda.nivel(it.onda, ahora - it.empezoMs) } ?: 0f
    return estado to energia
}

/**
 * 2.0 · Nova como personaje de luz: «A · La mirada», la opcion que eligio CG
 * (Diseño y marca, 15-10). La misma estrella de siempre —nucleo blanco calido,
 * borde y halo con los colores de lo que suena— con dos ojos de luz en negativo.
 * Sin boca ni nariz: presencia sin volverse caricatura.
 *
 * - Apagada: gris y quieta, los ojos cerrados (dos lineas).
 * - Escucha: al aire en silencio; ojos abiertos, un anillo tenue.
 * - Piensa: mientras prepara la frase; mira arriba a la derecha y una chispa orbita.
 * - Habla: entrecierra los ojos, crece con la energia real de su voz y salen dos anillos.
 * - Termino: ojos felices (dos arcos), 4 s; tocarla repite.
 *
 * Parpadea cada 4 a 7 s, y nada mas: si los ojos se mueven mucho, se vuelve
 * caricatura. Dibujada sobre la caja de 200 de la maqueta (personaje.html).
 */
@Composable
fun NovaMirada(
    estado: EstadoNova,
    energia: Float,
    colores: ColoresAurora,
    modifier: Modifier = Modifier,
) {
    val esquema = MaterialTheme.colorScheme
    val superficie = esquema.surface
    val claro = superficie.luminance() > 0.5f
    val negroPuro = superficie == Color.Black
    val apagada = estado == EstadoNova.APAGADA

    // La energia de la voz, suavizada un pelo para que no tiemble.
    val e by animateFloatAsState(if (estado == EstadoNova.HABLA) energia else 0f, tween(90), label = "energia")
    val luz by animateFloatAsState(if (apagada) 0f else 1f, tween(700), label = "luzMirada")

    // Los ojos: tres formas (barras, arcos felices, lineas cerradas) que se cruzan suave.
    val alfaBarras by animateFloatAsState(
        if (estado == EstadoNova.ESCUCHA || estado == EstadoNova.PIENSA || estado == EstadoNova.HABLA) 1f else 0f,
        tween(220), label = "barras",
    )
    val alfaArcos by animateFloatAsState(if (estado == EstadoNova.TERMINO) 1f else 0f, tween(260), label = "arcos")
    val alfaLineas by animateFloatAsState(if (apagada) 1f else 0f, tween(260), label = "lineas")
    val resorte = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)
    val ojoX by animateFloatAsState(if (estado == EstadoNova.PIENSA) 5.5f else 0f, resorte, label = "ojoX")
    val ojoY by animateFloatAsState(
        when (estado) {
            EstadoNova.PIENSA -> 81f
            EstadoNova.HABLA -> 92f
            else -> 88f
        },
        resorte, label = "ojoY",
    )
    val ojoAlto by animateFloatAsState(
        when (estado) {
            EstadoNova.PIENSA -> 19f
            EstadoNova.HABLA -> 17f
            else -> 23f
        },
        resorte, label = "ojoAlto",
    )

    // El parpadeo: de vez en cuando, nunca seguido.
    val parpadeo = remember { Animatable(1f) }
    LaunchedEffect(apagada) {
        if (apagada) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(4_000, 7_000))
            parpadeo.animateTo(0.12f, tween(70))
            parpadeo.animateTo(1f, tween(110))
        }
    }

    // La chispa que orbita mientras piensa.
    val orbita = rememberInfiniteTransition(label = "orbita")
    val angulo by orbita.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "anguloChispa",
    )

    val tinta = when {
        claro -> Color(0xFF1B1B1F)
        negroPuro -> Color(0xFF0A0B0D)
        else -> Color(0xFF14161B)
    }
    val gris = Color(0xFF8A8F96)
    val borde = lerp(gris, colores.c1, luz)
    val borde2 = lerp(Color(0xFF5F6368), colores.c2, luz)
    val intensidadHalo = when {
        negroPuro -> 0.35f
        claro -> 0.45f
        else -> 0.6f
    } * luz

    Canvas(modifier) {
        val k = size.minDimension / 200f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun p(x: Float, y: Float) = Offset(c.x + (x - 100f) * k, c.y + (y - 100f) * k)

        // El halo.
        if (intensidadHalo > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to borde.copy(alpha = intensidadHalo),
                    0.55f to borde2.copy(alpha = intensidadHalo * 0.45f),
                    1f to borde2.copy(alpha = 0f),
                    center = c, radius = 98f * k,
                ),
                radius = 98f * k, center = c,
            )
        }
        // Los anillos: uno tenue al escuchar, dos que se abren con la voz al hablar.
        when (estado) {
            EstadoNova.HABLA -> {
                drawCircle(borde.copy(alpha = 0.55f), (70f + 18f * e) * k, c, style = Stroke(1.6f * k))
                drawCircle(borde2.copy(alpha = 0.32f), (84f + 22f * e) * k, c, style = Stroke(1.2f * k))
            }
            EstadoNova.ESCUCHA -> drawCircle(borde.copy(alpha = 0.28f), 72f * k, c, style = Stroke(1.2f * k))
            else -> Unit
        }
        // La chispa: un trazo corto que orbita y su punto de luz.
        if (estado == EstadoNova.PIENSA) {
            drawArc(
                color = Color.White.copy(alpha = 0.6f),
                startAngle = angulo - 38f, sweepAngle = 38f, useCenter = false,
                topLeft = p(34f, 34f), size = Size(132f * k, 132f * k),
                style = Stroke(3f * k, cap = StrokeCap.Round),
            )
            val rad = Math.toRadians(angulo.toDouble())
            drawCircle(
                Color.White, 4.5f * k,
                Offset(c.x + (66f * k * kotlin.math.cos(rad)).toFloat(), c.y + (66f * k * kotlin.math.sin(rad)).toFloat()),
            )
        }

        // El cuerpo: crece un 8 % con la energia de la voz.
        scale(1f + 0.08f * e, pivot = c) {
            if (luz > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to borde.copy(alpha = 0.55f * luz), 1f to borde.copy(alpha = 0f),
                        center = c, radius = 74f * k,
                    ),
                    radius = 74f * k, center = c,
                )
            }
            val centroBrillo = p(100f - 56f + 112f * 0.46f, 100f - 56f + 112f * 0.42f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to lerp(Color(0xFFC9CDD2), Color.White, luz),
                    0.42f to lerp(Color(0xFFAEB3B9), Color(0xFFFFF8EE), luz),
                    0.78f to lerp(Color(0xFF9AA0A6), lerp(colores.c1, Color.White, 0.45f), luz),
                    1f to borde,
                    center = centroBrillo, radius = 112f * 0.6f * k,
                ),
                radius = 56f * k, center = c,
            )
            if (claro && luz > 0.01f) {
                drawCircle(lerp(colores.c1, Color.Black, 0.1f).copy(alpha = 0.35f * luz), 56f * k, c, style = Stroke(1f * k))
            }

            // Los ojos.
            if (alfaBarras > 0.01f) {
                val alto = ojoAlto * parpadeo.value
                val arriba = ojoY + (ojoAlto - alto) / 2f
                listOf(80.5f, 109.5f).forEach { x ->
                    drawRoundRect(
                        color = tinta.copy(alpha = alfaBarras),
                        topLeft = p(x + ojoX, arriba),
                        size = Size(10f * k, alto * k),
                        cornerRadius = CornerRadius(5f * k, 5f * k),
                    )
                }
            }
            if (alfaArcos > 0.01f) {
                listOf(78.5f, 107.5f).forEach { x ->
                    val arco = Path().apply {
                        moveTo(p(x, 103f).x, p(x, 103f).y)
                        quadraticTo(p(x + 7f, 92f).x, p(x + 7f, 92f).y, p(x + 14f, 103f).x, p(x + 14f, 103f).y)
                    }
                    drawPath(arco, tinta.copy(alpha = alfaArcos), style = Stroke(4.8f * k, cap = StrokeCap.Round))
                }
            }
            if (alfaLineas > 0.01f) {
                drawLine(tinta.copy(alpha = 0.55f * alfaLineas), p(80f, 103f), p(93f, 103f), 4.5f * k, StrokeCap.Round)
                drawLine(tinta.copy(alpha = 0.55f * alfaLineas), p(107f, 103f), p(120f, 103f), 4.5f * k, StrokeCap.Round)
            }

            // El destello en cruz, el ADN de la estrella.
            if (luz > 0.01f) {
                val largo = 34f * k
                val grosor = 1.6f * k
                val centro = p(76f, 72f)
                drawLine(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * luz), Color.Transparent),
                        startX = centro.x - largo / 2f, endX = centro.x + largo / 2f,
                    ),
                    Offset(centro.x - largo / 2f, centro.y), Offset(centro.x + largo / 2f, centro.y),
                    grosor, StrokeCap.Round,
                )
                drawLine(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * luz), Color.Transparent),
                        startY = centro.y - largo / 2f, endY = centro.y + largo / 2f,
                    ),
                    Offset(centro.x, centro.y - largo / 2f), Offset(centro.x, centro.y + largo / 2f),
                    grosor, StrokeCap.Round,
                )
            }
        }
    }
}
