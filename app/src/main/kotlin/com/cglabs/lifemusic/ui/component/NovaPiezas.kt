package com.cglabs.lifemusic.ui.component

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.nova.NovaSesion

/*
 * 2.0 · Las piezas de Nova (maqueta aprobada 2026-10-15-nova-dj, adaptada a
 * Material You). Superficies y botones con los roles del tema; Nova es un
 * personaje de luz (NovaMirada) con el color de lo que suena; la luz AL AIRE siempre naranja, porque es la señal
 * de la cabina. Ficha: docs/versiones/2.0/nova.md (seccion 5).
 *
 * Pedido de CG (15-10): nada fijo en el Inicio ni en el reproductor. Nova se
 * invoca con un solo boton pequeño, la estrella, y todo lo demas vive en su
 * cabina (NovaCabina).
 */

/** El nombre de Nova: Google Sans Flex a su ancho maximo, ligera, como la marca de un equipo de alta fidelidad. */
@OptIn(ExperimentalTextApi::class)
internal val LetraNova = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight(340),
        variationSettings = FontVariation.Settings(FontVariation.weight(340), FontVariation.width(151f)),
    ),
)

/** La luz de la cabina de CG LABS: naranja fijo, no cambia con el tema. */
private val NARANJA_AL_AIRE = Color(0xFFFF5B2E)

/**
 * La etiqueta BETA: Nova es una vista previa (pedido de CG, 15-10), igual que la
 * barra Aurora. Discreta, con el color terciario del tema.
 */
@Composable
fun EtiquetaBeta(modifier: Modifier = Modifier) {
    val esquema = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.nova_beta),
        color = esquema.tertiary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.16.em,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, esquema.tertiary.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** La luz AL AIRE. Apagada queda como un contorno tenue: la luz esta, pero no se prende. */
@Composable
fun LuzAlAire(modifier: Modifier = Modifier, encendida: Boolean = true) {
    val forma = RoundedCornerShape(5.dp)
    val fondo by animateColorAsState(
        if (encendida) NARANJA_AL_AIRE else NARANJA_AL_AIRE.copy(alpha = 0.10f), tween(500), label = "aire",
    )
    val tinta = if (encendida) Color(0xFFFFF6F2) else NARANJA_AL_AIRE.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(forma)
            .background(fondo)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (encendida) Color(0xFFFFE7DD) else NARANJA_AL_AIRE.copy(alpha = 0.45f)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.nova_al_aire),
            color = tinta,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.16.em,
            maxLines = 1,
        )
    }
}

/**
 * El unico boton de Nova fuera de su cabina: la estrella pequeña, en la barra de
 * arriba y en el reproductor. Al aire brilla y late; tocarla abre la cabina.
 */
@Composable
fun NovaBotonEstrella(modifier: Modifier = Modifier, fondo: Color = Color.Transparent, tamano: Dp = 40.dp) {
    val alAire by NovaSesion.alAire.collectAsState()
    val colores = rememberColoresAurora()
    val descripcion = stringResource(R.string.nova_abrir)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(fondo)
            .clickable(role = Role.Button) { NovaSesion.abrirCabina() }
            .semantics { contentDescription = descripcion },
    ) {
        // La forma pequeña es la misma figura; apagada se ve «escuchando» (maqueta elegida).
        val (estadoNova, energia) = rememberEstadoNova()
        NovaMirada(
            estado = if (estadoNova == EstadoNova.APAGADA) EstadoNova.ESCUCHA else estadoNova,
            energia = energia,
            colores = colores,
            modifier = Modifier.size(tamano * 0.86f),
        )
    }
}

/**
 * El boton de encender de la cabina: apagado es un circulo con el simbolo de
 * encender; al aire se transforma en un destello (Material 3 Expressive) que
 * gira despacio. Tocarlo enciende o apaga.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BotonEncender(alAire: Boolean, alTocar: () -> Unit, tamano: Dp = 72.dp) {
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
    val descripcion = stringResource(if (alAire) R.string.nova_apagar else R.string.nova_encender)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(tamano)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = alTocar)
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
            painter = painterResource(R.drawable.nova_encender),
            contentDescription = null,
            tint = tinta,
            modifier = Modifier.size(tamano * 0.4f),
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
