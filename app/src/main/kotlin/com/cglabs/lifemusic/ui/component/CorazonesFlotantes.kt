package com.cglabs.lifemusic.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Corazones que suben flotando desde un punto y se desvanecen: la respuesta a
 * mantener pulsado el corazon del reproductor (favorito + descarga). Se
 * dibujan en una capa encima de todo (MainActivity), no dentro del boton: el
 * estallido de HeartBurstIcon quedaba recortado por el circulo del boton y no
 * se veia (lo noto CG).
 */
object Corazones {
    class Lanzamiento(val id: Long, val origen: Offset)

    val lanzamientos = MutableStateFlow<List<Lanzamiento>>(emptyList())

    /** [origen] en coordenadas de la raiz (boundsInRoot().center del boton). */
    fun lanzar(origen: Offset) {
        lanzamientos.value = lanzamientos.value + Lanzamiento(System.nanoTime(), origen)
    }

    internal fun terminado(id: Long) {
        lanzamientos.value = lanzamientos.value.filterNot { it.id == id }
    }
}

@Composable
fun CorazonesFlotantesOverlay() {
    val lista by Corazones.lanzamientos.collectAsState()
    if (lista.isEmpty()) return
    Box(Modifier.fillMaxSize()) {
        lista.forEach { l -> key(l.id) { Rafaga(l) } }
    }
}

private class Particula(
    val retraso: Float,
    val alturaDp: Float,
    val derivaDp: Float,
    val tamanoDp: Float,
    val fase: Float,
    val color: Color,
)

private val COLORES = listOf(Color(0xFFFF4081), Color(0xFFFF80AB), Color(0xFFF06292), Color(0xFFFFC1E3), Color.White)

@Composable
private fun Rafaga(l: Corazones.Lanzamiento) {
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(l.id) {
        progreso.animateTo(1f, tween(1_500, easing = LinearEasing))
        Corazones.terminado(l.id)
    }
    val particulas = remember(l.id) {
        val r = Random(l.id)
        List(10) { i ->
            Particula(
                retraso = i * 0.05f,
                alturaDp = 130f + r.nextFloat() * 110f,
                derivaDp = -48f + r.nextFloat() * 96f,
                tamanoDp = 14f + r.nextFloat() * 14f,
                fase = r.nextFloat() * 6.28f,
                color = COLORES[r.nextInt(COLORES.size)],
            )
        }
    }
    val pintor = painterResource(R.drawable.favorite)
    Canvas(Modifier.fillMaxSize()) {
        val p = progreso.value
        particulas.forEach { q ->
            val t = ((p - q.retraso) / (1f - q.retraso)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val suave = 1f - (1f - t) * (1f - t)
            val y = l.origen.y - suave * q.alturaDp.dp.toPx()
            val x = l.origen.x + q.derivaDp.dp.toPx() * suave + sin(t * 7f + q.fase) * 9.dp.toPx()
            val alfa = when {
                t < 0.12f -> t / 0.12f
                t > 0.55f -> (1f - t) / 0.45f
                else -> 1f
            }
            val escala = 0.55f + 0.65f * sin(t * PI).toFloat()
            val lado = q.tamanoDp.dp.toPx() * escala
            translate(x - lado / 2f, y - lado / 2f) {
                with(pintor) {
                    draw(size = Size(lado, lado), alpha = alfa.coerceIn(0f, 1f), colorFilter = ColorFilter.tint(q.color))
                }
            }
        }
    }
}
