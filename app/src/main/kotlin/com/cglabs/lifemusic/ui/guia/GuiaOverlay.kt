package com.cglabs.lifemusic.ui.guia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.GuiasVistasKey
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * La capa de la guia: velo oscuro con un recorte sobre el control del paso,
 * y una tarjeta con lo que hay que hacer. No intercepta toques fuera de la
 * tarjeta: la gracia es que el usuario pruebe el gesto sobre el control real.
 * Vive en MainActivity, por encima del reproductor y de las pantallas.
 */
@Composable
fun GuiaOverlay() {
    var vistas by rememberPreference(GuiasVistasKey, defaultValue = emptySet())
    val objetivos = Guia.objetivos
    val hechos by Guia.hechos.collectAsState()

    val densidad = LocalDensity.current
    val configuracion = LocalConfiguration.current
    val altoPantallaPx = with(densidad) { configuracion.screenHeightDp.dp.toPx() }
    val anchoPantallaPx = with(densidad) { configuracion.screenWidthDp.dp.toPx() }
    // Solo cuentan los controles que de verdad se ven: el reproductor recogido
    // sigue compuesto fuera de pantalla y sus limites quedan por debajo del borde.
    fun visible(nombre: String): Rect? = objetivos[nombre]?.takeIf {
        it.width > 0f && it.height > 0f && it.bottom > 0f && it.right > 0f && it.top < altoPantallaPx && it.left < anchoPantallaPx
    }
    // La primera guia pendiente cuyo primer control este en pantalla.
    val guia = GUIAS.firstOrNull { it.nombre !in vistas && visible(it.pasos.first().objetivo) != null } ?: return
    var paso by remember(guia.nombre) { mutableIntStateOf(0) }
    val hechosAlEmpezar by remember(guia.nombre, paso) { mutableStateOf(hechos) }
    var probado by remember(guia.nombre, paso) { mutableStateOf(false) }

    fun terminar() { vistas = vistas + guia.nombre }
    fun siguiente() { if (paso + 1 < guia.pasos.size) paso++ else terminar() }

    val actual = guia.pasos[paso]
    val caja = visible(actual.objetivo)
    // Si el control de este paso no esta (una opcion apagada), se salta.
    LaunchedEffect(caja == null, paso) { if (caja == null) { delay(400); if (visible(actual.objetivo) == null) siguiente() } }
    // Probado: el control aviso de un evento nuevo desde que empezo el paso.
    LaunchedEffect(hechos, paso) {
        val evento = actual.evento ?: return@LaunchedEffect
        if (evento in hechos && evento !in hechosAlEmpezar && !probado) {
            probado = true
            delay(1_400)
            siguiente()
        }
    }
    if (caja == null) return
    androidx.compose.runtime.DisposableEffect(Unit) {
        Guia.activa.value = true
        onDispose { Guia.activa.value = false }
    }

    val margen = with(densidad) { 8.dp.toPx() }
    val recorte by animateRectAsState(
        targetValue = Rect(caja.left - margen, caja.top - margen, caja.right + margen, caja.bottom + margen),
        label = "recorteGuia",
    )
    val tarjetaAbajo = recorte.center.y < altoPantallaPx / 2f

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.62f))
            val esquina = CornerRadius(20.dp.toPx())
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(recorte.left, recorte.top),
                size = Size(recorte.width, recorte.height),
                cornerRadius = esquina,
                blendMode = BlendMode.Clear,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(recorte.left, recorte.top),
                size = Size(recorte.width, recorte.height),
                cornerRadius = esquina,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        var altoTarjeta by remember { mutableIntStateOf(0) }
        val separacion = with(densidad) { 16.dp.toPx() }
        val y = if (tarjetaAbajo) recorte.bottom + separacion else recorte.top - separacion - altoTarjeta
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, y.roundToInt().coerceAtLeast(0)) }
                .padding(horizontal = 24.dp)
                .widthIn(max = 380.dp)
                .onGloballyPositioned { altoTarjeta = it.size.height },
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.guia_novedad),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (guia.pasos.size > 1) {
                        Text(
                            text = "${paso + 1}/${guia.pasos.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(actual.titulo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(actual.texto), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = probado, enter = fadeIn(), exit = fadeOut()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.guia_probado),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { terminar() }) { Text(stringResource(R.string.guia_saltar)) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { siguiente() }) {
                        Text(stringResource(if (paso + 1 < guia.pasos.size) R.string.guia_siguiente else R.string.guia_listo))
                    }
                }
            }
        }
    }
}
