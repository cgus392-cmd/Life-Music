package com.cglabs.lifemusic.concurso

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.MusicService
import com.cglabs.lifemusic.ui.screens.recordarSiAnimar

private val VERDE = Color(0xFF34D399)

/**
 * El trofeo de la barra de Inicio, con dos anillos: uno fijo que se llena con
 * lo que llevas hoy hasta el tope de 6 h, y otro que gira mientras suena algo,
 * para que se vea que los minutos estan corriendo. Sin sonar, el que gira
 * desaparece y queda solo el progreso.
 */
@Composable
fun TrofeoReto(progreso: MusicService.ProgresoReto?, onClick: () -> Unit) {
    val animar = recordarSiAnimar()
    val hoy = (progreso?.minutosHoy ?: 0) + (progreso?.segundosEnCurso ?: 0) / 60
    val objetivo = (hoy.toFloat() / Concurso.TOPE_MIN_DIA).coerceIn(0f, 1f)
    val fraccion by animateFloatAsState(
        targetValue = objetivo,
        animationSpec = if (animar) tween(900, easing = FastOutSlowInEasing) else tween(0),
        label = "progresoHoy",
    )
    val sonando = progreso?.reproduciendo == true

    val giro = if (animar && sonando) {
        val t = rememberInfiniteTransition(label = "trofeoGira")
        val g by t.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2_200, easing = LinearEasing)),
            label = "giro",
        )
        g
    } else 0f

    val pista = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val tinta = MaterialTheme.colorScheme.onSurface

    IconButton(onClick = onClick) {
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(38.dp)) {
                val grosor = 2.5.dp.toPx()
                val margen = grosor
                val diametro = size.minDimension - margen * 2
                val esquina = androidx.compose.ui.geometry.Offset(margen, margen)
                val tam = androidx.compose.ui.geometry.Size(diametro, diametro)
                // Pista.
                drawArc(pista, 0f, 360f, false, esquina, tam, style = Stroke(grosor))
                // Lo de hoy.
                if (fraccion > 0f) {
                    drawArc(VERDE, -90f, 360f * fraccion, false, esquina, tam, style = Stroke(grosor, cap = StrokeCap.Round))
                }
                // Sonando: un arco corto que da vueltas por encima.
                if (sonando) {
                    drawArc(
                        VERDE.copy(alpha = 0.9f), giro - 90f, 70f, false,
                        androidx.compose.ui.geometry.Offset(margen * 2.6f, margen * 2.6f),
                        androidx.compose.ui.geometry.Size(diametro - margen * 3.2f, diametro - margen * 3.2f),
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.trophy),
                contentDescription = stringResource(R.string.concurso_titulo),
                tint = tinta,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
