package com.cglabs.lifemusic.ui.component

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.nova.NovaOnda
import com.cglabs.lifemusic.nova.NovaSesion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Lo que esta haciendo Nova, para su mirada. */
enum class EstadoNova { APAGADA, ESCUCHA, ESPERA, PIENSA, HABLA, TERMINO }

/**
 * El estado de Nova ahora mismo y la energia de su voz (0 a 1, real, del audio
 * que esta diciendo). Lee la sesion y el reproductor; mientras habla, se
 * actualiza cada fotograma.
 */
@Composable
fun rememberEstadoNova(): Pair<EstadoNova, Float> {
    val alAire by NovaSesion.alAire.collectAsState()
    val habla by NovaSesion.hablando.collectAsState()
    val pensando by NovaSesion.pensando.collectAsState()
    val conexion = LocalPlayerConnection.current
    val sonando by (conexion?.isPlaying ?: remember { MutableStateFlow(false) }).collectAsState()

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
        alAire && !sonando -> EstadoNova.ESPERA
        alAire -> EstadoNova.ESCUCHA
        else -> EstadoNova.APAGADA
    }
    val energia = hablando?.let { NovaOnda.nivel(it.onda, ahora - it.empezoMs) } ?: 0f
    return estado to energia
}

/**
 * 2.0 · Nova como personaje de luz: «A · La mirada», la opcion que eligio CG
 * (Brand/maquetas/2026-10-15-nova-personaje-luz). La misma estrella de siempre
 * —nucleo blanco calido, borde y halo con los colores de lo que suena— con dos
 * ojos de luz en negativo. Sin boca ni nariz: presencia sin volverse caricatura.
 *
 * Estados (de la maqueta) y vida (pedido de CG, 15-10: «mas animaciones de estado»):
 * - Apagada: gris, ojos cerrados. Al apagarse cierra los ojos y se encoge un poco.
 * - Despertar: al salir del aire se enciende con un rebote y un destello del halo.
 * - Escucha: ojos abiertos, respira despacio y de vez en cuando mira de reojo.
 * - Espera: al aire con la musica en pausa; entrecierra los ojos, como esperandote.
 * - Piensa: mira arriba a la derecha y una chispa orbita (al menos medio segundo).
 * - Habla: entrecierra, los ojos laten con la voz, crece con su energia real y
 *   salen anillos y ondas que se abren.
 * - Termino: ojos felices con un saltito y un destello, 4 s; tocarla repite.
 * - Gestos: NIEGA (estacion vacia) mueve la cabeza; ALEGRE («me gusta» con ella
 *   al aire) sonrie y salta.
 *
 * Todo con pocos movimientos y lentos: cada gesto se nota porque son pocos.
 * Dibujada sobre la caja de 200 de la maqueta.
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

    // ALEGRE pisa por un momento lo que este haciendo: sonrie con los ojos.
    var alegreHasta by remember { mutableLongStateOf(0L) }
    var reloj by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val alegre = reloj < alegreHasta
    val mostrado = if (alegre && estado != EstadoNova.APAGADA) EstadoNova.TERMINO else estado
    val apagada = mostrado == EstadoNova.APAGADA
    val ojosAbiertos = mostrado == EstadoNova.ESCUCHA || mostrado == EstadoNova.PIENSA ||
        mostrado == EstadoNova.HABLA || mostrado == EstadoNova.ESPERA

    // La energia de la voz, suavizada un pelo para que no tiemble.
    val e by animateFloatAsState(if (mostrado == EstadoNova.HABLA) energia else 0f, tween(90), label = "energia")
    val luz by animateFloatAsState(if (apagada) 0f else 1f, tween(700), label = "luzMirada")

    // Los ojos: barras, arcos felices o lineas cerradas, que se cruzan suave.
    val alfaBarras by animateFloatAsState(if (ojosAbiertos) 1f else 0f, tween(220), label = "barras")
    val alfaArcos by animateFloatAsState(if (mostrado == EstadoNova.TERMINO) 1f else 0f, tween(260), label = "arcos")
    val alfaLineas by animateFloatAsState(if (apagada) 1f else 0f, tween(420), label = "lineas")
    val resorte = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)
    val ojoX by animateFloatAsState(if (mostrado == EstadoNova.PIENSA) 5.5f else 0f, resorte, label = "ojoX")
    val ojoY by animateFloatAsState(
        when (mostrado) {
            EstadoNova.PIENSA -> 81f
            EstadoNova.HABLA -> 92f
            EstadoNova.ESPERA -> 96f
            else -> 88f
        },
        resorte, label = "ojoY",
    )
    val ojoAlto by animateFloatAsState(
        when (mostrado) {
            EstadoNova.PIENSA -> 19f
            EstadoNova.HABLA -> 17f
            EstadoNova.ESPERA -> 9f
            else -> 23f
        },
        resorte, label = "ojoAlto",
    )

    // Parpadeo: de vez en cuando, nunca seguido.
    val parpadeo = remember { Animatable(1f) }
    LaunchedEffect(apagada) {
        if (apagada) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(4_000, 7_000))
            parpadeo.animateTo(0.12f, tween(70))
            parpadeo.animateTo(1f, tween(110))
        }
    }

    // Mirar de reojo al escuchar: cada 9 a 16 s, un instante a un lado.
    val reojo = remember { Animatable(0f) }
    LaunchedEffect(mostrado == EstadoNova.ESCUCHA) {
        if (mostrado != EstadoNova.ESCUCHA) {
            reojo.animateTo(0f, tween(200))
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(9_000, 16_000))
            reojo.animateTo(if (Random.nextBoolean()) 3.5f else -3.5f, spring(dampingRatio = 0.8f, stiffness = 200f))
            delay(900)
            reojo.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 200f))
        }
    }

    // Rebote del cuerpo, destello del halo y del brillo en cruz.
    val cuerpo = remember { Animatable(if (apagada) 0.96f else 1f) }
    val flash = remember { Animatable(0f) }
    val brillo = remember { Animatable(1f) }
    var antes by remember { mutableStateOf(mostrado) }
    LaunchedEffect(mostrado) {
        val previo = antes
        antes = mostrado
        when {
            // Despertar: abre los ojos con un rebote y el halo se enciende de golpe.
            previo == EstadoNova.APAGADA && mostrado != EstadoNova.APAGADA -> {
                launch { flash.snapTo(1f); flash.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
                cuerpo.snapTo(0.86f)
                cuerpo.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 240f))
            }
            // Dormirse: se encoge un poco mientras cierra los ojos.
            mostrado == EstadoNova.APAGADA -> cuerpo.animateTo(0.96f, tween(600))
            // Termino (o alegre): saltito y destello.
            mostrado == EstadoNova.TERMINO -> {
                launch { brillo.snapTo(1.4f); brillo.animateTo(1f, tween(700)) }
                cuerpo.snapTo(1.07f)
                cuerpo.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
            }
            else -> if (cuerpo.value != 1f) cuerpo.animateTo(1f, tween(300))
        }
    }

    // Gestos sueltos: decir que no, o alegrarse con tu «me gusta».
    val sacudida = remember { Animatable(0f) }
    val salto = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        NovaSesion.gestos.collect { gesto ->
            when (gesto) {
                NovaSesion.Gesto.NIEGA -> launch {
                    sacudida.animateTo(0f, keyframes {
                        durationMillis = 520
                        -6f at 80
                        6f at 200
                        -4f at 310
                        3f at 410
                        0f at 520
                    })
                }
                NovaSesion.Gesto.ALEGRE -> {
                    alegreHasta = SystemClock.elapsedRealtime() + 1_500L
                    reloj = SystemClock.elapsedRealtime()
                    launch {
                        salto.animateTo(-8f, tween(160, easing = FastOutSlowInEasing))
                        salto.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 320f))
                    }
                    launch {
                        delay(1_520)
                        reloj = SystemClock.elapsedRealtime()
                    }
                }
            }
        }
    }

    // Lo que se repite: la respiracion, la chispa y las ondas de la voz.
    val ciclo = rememberInfiniteTransition(label = "cicloNova")
    val respiro by ciclo.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "respiro",
    )
    val angulo by ciclo.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "anguloChispa",
    )
    val fase by ciclo.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing)),
        label = "faseOndas",
    )

    val tinta = when {
        claro -> Color(0xFF1B1B1F)
        negroPuro -> Color(0xFF0A0B0D)
        else -> Color(0xFF14161B)
    }
    val gris = Color(0xFF8A8F96)
    val borde = lerp(gris, colores.c1, luz)
    val borde2 = lerp(Color(0xFF5F6368), colores.c2, luz)
    val baseHalo = when {
        negroPuro -> 0.35f
        claro -> 0.45f
        else -> 0.6f
    }
    val intensidadHalo = (baseHalo * luz + 0.35f * flash.value).coerceAtMost(1f)
    // Respira: un poco al escuchar, apenas al esperar; nada mientras habla (manda la voz).
    val respiracion = when (mostrado) {
        EstadoNova.ESCUCHA -> 0.985f + 0.03f * respiro
        EstadoNova.ESPERA -> 0.99f + 0.02f * respiro
        else -> 1f
    }

    Canvas(modifier) {
        val k = size.minDimension / 200f
        val c = Offset(size.width / 2f, size.height / 2f)
        fun p(x: Float, y: Float) = Offset(c.x + (x - 100f) * k, c.y + (y - 100f) * k)

        translate(left = sacudida.value * k, top = salto.value * k) {
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
            // Anillos y ondas: uno tenue al escuchar; al hablar, dos que se abren con la
            // voz y ondas que salen hacia afuera mientras dice algo.
            when (mostrado) {
                EstadoNova.HABLA -> {
                    drawCircle(borde.copy(alpha = 0.55f), (70f + 18f * e) * k, c, style = Stroke(1.6f * k))
                    drawCircle(borde2.copy(alpha = 0.32f), (84f + 22f * e) * k, c, style = Stroke(1.2f * k))
                    listOf(fase, (fase + 0.5f) % 1f).forEach { f ->
                        val alfa = (1f - f) * 0.4f * (0.25f + 0.75f * e)
                        if (alfa > 0.01f) {
                            drawCircle(borde.copy(alpha = alfa), (62f + 36f * f) * k, c, style = Stroke((2.2f - 1.4f * f) * k))
                        }
                    }
                }
                EstadoNova.ESCUCHA -> drawCircle(borde.copy(alpha = 0.22f + 0.1f * respiro), 72f * k, c, style = Stroke(1.2f * k))
                else -> Unit
            }
            // La chispa que orbita mientras piensa, con su estela.
            if (mostrado == EstadoNova.PIENSA) {
                drawArc(
                    color = Color.White.copy(alpha = 0.6f),
                    startAngle = angulo - 38f, sweepAngle = 38f, useCenter = false,
                    topLeft = p(34f, 34f), size = Size(132f * k, 132f * k),
                    style = Stroke(3f * k, cap = StrokeCap.Round),
                )
                val rad = Math.toRadians(angulo.toDouble())
                drawCircle(
                    Color.White, 4.5f * k,
                    Offset(c.x + (66f * k * cos(rad)).toFloat(), c.y + (66f * k * sin(rad)).toFloat()),
                )
            }

            // El cuerpo: rebota, respira y crece un 8 % con la energia de la voz.
            scale(cuerpo.value * respiracion * (1f + 0.08f * e), pivot = c) {
                if (luz > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to borde.copy(alpha = (0.55f * luz + 0.25f * flash.value).coerceAtMost(1f)),
                            1f to borde.copy(alpha = 0f),
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

                // Los ojos. Al hablar laten un poco con la voz; al escuchar, a veces miran de reojo.
                if (alfaBarras > 0.01f) {
                    val latido = if (mostrado == EstadoNova.HABLA) 1f - 0.22f * e else 1f
                    val alto = ojoAlto * parpadeo.value * latido
                    val arriba = ojoY + (ojoAlto - alto) / 2f
                    listOf(80.5f, 109.5f).forEach { x ->
                        drawRoundRect(
                            color = tinta.copy(alpha = alfaBarras),
                            topLeft = p(x + ojoX + reojo.value, arriba),
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

                // El destello en cruz, el ADN de la estrella. Crece un instante al terminar o alegrarse.
                if (luz > 0.01f) {
                    val largo = 34f * k * brillo.value
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
}
