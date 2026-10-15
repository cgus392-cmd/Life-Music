package com.cglabs.lifemusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.nova.NovaSesion
import java.time.LocalTime

/**
 * 2.0 · La capsula de Nova en el Inicio (maqueta aprobada 2026-10-15-nova-dj,
 * pantallas 1 y 4), adaptada a Material You: superficie y botones con los roles
 * del tema, la estrella con el color de lo que suena y la luz AL AIRE siempre
 * naranja, porque es la señal de la cabina. Solo aparece con Nova prendida en
 * Ajustes. Ficha: docs/versiones/2.0/nova.md (seccion 5).
 *
 * Apagada: toca el boton para encenderla. Al aire: tocar cambia de onda y
 * mantener apretado la apaga (la musica sigue).
 */
@Composable
fun NovaCapsula(modifier: Modifier = Modifier) {
    val conexion = LocalPlayerConnection.current ?: return
    val alAire by NovaSesion.alAire.collectAsState()
    val estado by NovaSesion.estado.collectAsState()
    val colores = rememberColoresAurora()
    val esquema = MaterialTheme.colorScheme

    val fondo by animateColorAsState(
        if (alAire) lerp(esquema.surfaceContainerHigh, colores.c1, 0.14f) else esquema.surfaceContainerHigh,
        tween(900), label = "fondo",
    )
    val borde by animateColorAsState(
        if (alAire) colores.c1.copy(alpha = 0.55f) else esquema.outlineVariant.copy(alpha = 0.6f),
        tween(900), label = "borde",
    )
    val forma = RoundedCornerShape(28.dp)

    val subtitulo = when {
        alAire && estado != null -> stringResource(
            R.string.nova_subtitulo_al_aire,
            stringResource(estado!!.bloque.nombre), estado!!.posicion, estado!!.total,
        )
        alAire -> stringResource(R.string.nova_sintonizando)
        else -> stringResource(
            when (LocalTime.now().hour) {
                in 5..11 -> R.string.nova_lista_manana
                in 12..18 -> R.string.nova_lista_tarde
                else -> R.string.nova_lista_noche
            },
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(fondo)
            .border(1.dp, borde, forma)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    ) {
        NovaEstrella(encendida = alAire, color = colores.c1, modifier = Modifier.size(52.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NOVA",
                    fontFamily = LetraNova,
                    fontWeight = FontWeight(340),
                    fontSize = 17.sp,
                    letterSpacing = 0.3.em,
                    color = esquema.onSurface,
                    maxLines = 1,
                )
                AnimatedVisibility(
                    visible = alAire,
                    enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.8f),
                ) {
                    Row {
                        Spacer(Modifier.width(10.dp))
                        LuzAlAire()
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = esquema.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        BotonNova(
            alAire = alAire,
            alTocar = { if (alAire) conexion.service.cambiarDeOnda() else conexion.service.encenderNova() },
            alMantener = { if (alAire) conexion.service.apagarNova() },
        )
    }
}

/** El nombre de Nova: Google Sans Flex a su ancho maximo, ligera, como la marca de un equipo de alta fidelidad. */
@OptIn(ExperimentalTextApi::class)
private val LetraNova = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight(340),
        variationSettings = FontVariation.Settings(FontVariation.weight(340), FontVariation.width(151f)),
    ),
)

/** La luz de la cabina de CG LABS: naranja fijo, no cambia con el tema. */
private val NARANJA_AL_AIRE = Color(0xFFFF5B2E)

@Composable
fun LuzAlAire(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(NARANJA_AL_AIRE)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFFFE7DD)))
        Spacer(Modifier.width(5.dp))
        Text(
            text = stringResource(R.string.nova_al_aire),
            color = Color(0xFFFFF6F2),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.14.em,
            maxLines = 1,
        )
    }
}

/**
 * La estrella de Nova: un nucleo blanco calido que toma en los bordes el color
 * de lo que suena. Apagada es gris y quieta; encendida brilla y late despacio.
 */
@Composable
fun NovaEstrella(encendida: Boolean, color: Color, modifier: Modifier = Modifier) {
    val luz by animateFloatAsState(if (encendida) 1f else 0f, tween(1200), label = "luz")
    val latido = rememberInfiniteTransition(label = "latido")
    val pulso by latido.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulso",
    )
    val apagada = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val escala = 1f + (pulso - 1f) * luz
        // El halo: el color de lo que suena.
        if (luz > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = 0.55f * luz),
                    0.45f to color.copy(alpha = 0.18f * luz),
                    1f to Color.Transparent,
                    center = center, radius = r * escala,
                ),
                radius = r * escala,
            )
            // Los destellos en cruz, finos, que la hacen estrella y no bolita.
            val largo = r * 0.92f * escala
            val rayo = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.55f * luz), 1f to Color.Transparent,
                center = center, radius = largo,
            )
            drawLine(rayo, Offset(center.x - largo, center.y), Offset(center.x + largo, center.y), strokeWidth = 1.2.dp.toPx())
            drawLine(rayo, Offset(center.x, center.y - largo), Offset(center.x, center.y + largo), strokeWidth = 1.2.dp.toPx())
        }
        // El nucleo: blanco calido encendida, gris apagada.
        val centro = lerp(apagada.copy(alpha = 0.55f), Color(0xFFFFF8EE), luz)
        val orilla = lerp(apagada.copy(alpha = 0.2f), color, luz)
        val nucleo = r * 0.40f * escala
        drawCircle(
            brush = Brush.radialGradient(
                0f to centro, 0.5f to lerp(centro, orilla, 0.5f), 1f to orilla.copy(alpha = 0f),
                center = center, radius = nucleo * 1.6f,
            ),
            radius = nucleo * 1.6f,
        )
        drawCircle(color = centro, radius = nucleo * 0.42f)
    }
}

/**
 * El boton: apagado es un circulo con el simbolo de encender; al aire se
 * transforma en un destello (Material 3 Expressive) que gira despacio, con el
 * icono de la radio para cambiar de onda.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun BotonNova(alAire: Boolean, alTocar: () -> Unit, alMantener: () -> Unit) {
    val progreso by animateFloatAsState(
        if (alAire) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = 220f),
        label = "forma",
    )
    val giro = rememberInfiniteTransition(label = "giro")
    val angulo by giro.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
        label = "angulo",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.SoftBurst) }
    val esquema = MaterialTheme.colorScheme
    val fondo by animateColorAsState(if (alAire) esquema.secondaryContainer else esquema.primary, tween(500), label = "fondoBoton")
    val tinta by animateColorAsState(if (alAire) esquema.onSecondaryContainer else esquema.onPrimary, tween(500), label = "tinta")
    val descripcion = stringResource(if (alAire) R.string.nova_cambiar_de_onda else R.string.nova_encender)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = alTocar, onLongClick = alMantener)
            .semantics { contentDescription = descripcion },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = angulo * progreso }
                .clip(FormaMorph(morph, progreso.coerceIn(0f, 1f)))
                .background(fondo),
        )
        Icon(
            painter = painterResource(if (alAire) R.drawable.radio else R.drawable.nova_encender),
            contentDescription = null,
            tint = tinta,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Una forma que va de un poligono de Material a otro segun [progreso]. */
private class FormaMorph(private val morph: Morph, private val progreso: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val camino = morph.toPath(progreso).asComposePath()
        camino.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(camino)
    }
}
