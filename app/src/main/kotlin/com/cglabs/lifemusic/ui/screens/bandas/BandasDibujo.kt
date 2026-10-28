package com.cglabs.lifemusic.ui.screens.bandas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.bandas.Bandas
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/*
 * El dibujo de Bandas (maqueta «la banda es un acorde», bandas.js): cada persona
 * es una cabeza de nota del logo (elipse girada −22°) con su color y su inicial;
 * la banda junta es un acorde, esas cabezas apiladas en un palo. Las reacciones
 * van con el trazo y la cabeza coral.
 */

internal val CORAL_BANDAS = Color(0xFFFF6B3D)

private fun DrawScope.cabeza(centro: Offset, rx: Float, ry: Float, color: Color, punteada: Color? = null) {
    rotate(-22f, centro) {
        val arriba = Offset(centro.x - rx, centro.y - ry)
        if (punteada != null) {
            drawOval(
                punteada.copy(alpha = 0.55f), arriba, Size(rx * 2, ry * 2),
                style = Stroke(width = 1.6f * size.width / 44f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
            )
        } else {
            drawOval(color, arriba, Size(rx * 2, ry * 2))
        }
    }
}

/** Una persona: la cabeza de nota con su inicial adentro. */
@Composable
fun CabezaPersona(color: Color, inicial: String, ancho: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(ancho, ancho * 0.75f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ancho, ancho * 0.75f)) {
            val s = size.width / 48f
            cabeza(Offset(size.width / 2, size.height / 2), 23f * s, 13.5f * s, color)
        }
        Text(
            inicial, color = Color(0xFF111111), fontWeight = FontWeight.SemiBold,
            fontSize = (ancho.value * 17f / 48f).sp,
        )
    }
}

/**
 * El acorde de la banda: las cabezas apiladas en un palo; los lugares libres, punteados.
 * Al entrar, las cabezas caen al acorde una por una (70 ms entre cada una) con rebote;
 * la que llega despues (alguien aprobado) cae sola. Sin animaciones, aparecen con un fundido.
 */
@Composable
fun Acorde(colores: List<Color>, alto: Dp, libres: Int = 0, tinta: Color, modifier: Modifier = Modifier) {
    val n = (colores.size + libres).coerceAtLeast(1)
    val quieto = sinMovimiento()
    // 1 = arriba, fuera del acorde; 0 = en su lugar. Una por cabeza; las nuevas se suman.
    val caidas = remember { ArrayList<Animatable<Float, AnimationVector1D>>() }
    while (caidas.size < colores.size) caidas.add(Animatable(1f))
    LaunchedEffect(colores.size) {
        caidas.forEachIndexed { i, caida ->
            if (caida.targetValue != 0f) launch {
                delay(70L * i)
                caida.animateTo(0f, if (quieto) tween(150) else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    val paso = 18f
    val base = 26f + (n - 1) * paso
    val arriba = 6f
    val h = base + 14f
    val ancho = alto * (44f / h)
    Canvas(modifier.size(ancho, alto)) {
        val s = size.height / h
        // viewBox="-16 0 44 h": el palo queda en x=13.
        fun p(x: Float, y: Float) = Offset((x + 16f) * s, y * s)
        val palo = androidx.compose.ui.graphics.Path().apply {
            moveTo(p(13f, base - 2f).x, p(13f, base - 2f).y)
            lineTo(p(13f, arriba).x, p(13f, arriba).y)
            quadraticTo(p(13f, arriba - 4f).x, p(13f, arriba - 4f).y, p(18f, arriba + 2f).x, p(18f, arriba + 2f).y)
        }
        drawPath(palo, tinta, style = Stroke(width = 3.2f * s, cap = StrokeCap.Round))
        for (i in 0 until n) {
            val y = base - i * paso
            val x = if (i % 2 == 0) 0f else 3f
            if (i < colores.size) {
                val caida = caidas.getOrNull(i)?.value ?: 0f
                // Sin animaciones solo se funde; si no, cae y se aclara un poco mientras cae.
                val alfa = if (quieto) 1f - caida else 1f - 0.4f * caida
                val color = colores[i].copy(alpha = colores[i].alpha * alfa.coerceIn(0f, 1f))
                cabeza(p(x, y - if (quieto) 0f else caida * 40f), 11.5f * s, 8.5f * s, color)
            }
            else cabeza(p(x, y), 10.5f * s, 7.5f * s, tinta, punteada = tinta)
        }
    }
}

/** Las 6 reacciones de la maqueta: trazo (viewBox 40) y donde va la cabeza coral. */
private val TRAZOS = mapOf(
    "suena" to ("M21 26V8 M27 14a8 8 0 0 1 0 12M31.5 10a14 14 0 0 1 0 20" to Offset(14f, 27f)),
    "me_encanta" to ("M20 32C9 25 5 19.5 5 13.5 5 8.8 8.6 5.5 12.8 5.5c3 0 5.6 1.8 7.2 4.4 1.6-2.6 4.2-4.4 7.2-4.4 4.2 0 7.8 3.3 7.8 8 0 6-4 11.5-15 18.5z" to Offset(19.5f, 35f)),
    "otra_vez" to ("M27.5 10.5A11 11 0 1 0 31 20 M27.5 5v6h6" to Offset(31f, 22.5f)),
    "subele" to ("M7 33l7-6 6 3 7-11 5-8" to Offset(33.5f, 8f)),
    "a_bailar" to ("M9 21l6-8 6 13 6-13 6 8" to Offset(6f, 24f)),
    "escalofrio" to ("M12 6c-3 4 3 8 0 12s3 8 0 11M20 6c-3 4 3 8 0 12s3 8 0 11M28 6c-3 4 3 8 0 12s3 8 0 11" to Offset(20.5f, 34.5f)),
)

internal fun nombreDeReaccion(tipo: String) = when (tipo) {
    "suena" -> R.string.bandas_r_suena
    "me_encanta" -> R.string.bandas_r_me_encanta
    "otra_vez" -> R.string.bandas_r_otra_vez
    "subele" -> R.string.bandas_r_subele
    "a_bailar" -> R.string.bandas_r_a_bailar
    else -> R.string.bandas_r_escalofrio
}

@Composable
fun IconoReaccion(tipo: String, tinta: Color, tam: Dp = 30.dp, modifier: Modifier = Modifier) {
    val (d, cabezaEn) = TRAZOS[tipo] ?: TRAZOS.getValue("suena")
    val trazo = remember(tipo) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(tam)) {
        val s = size.width / 40f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(trazo, tinta, style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            cabeza(cabezaEn, 4.6f, 3.4f, CORAL_BANDAS)
        }
    }
}

/** El QR del enlace de la banda, negro sobre blanco, con su margen. */
@Composable
fun CodigoQr(texto: String, tam: Dp, modifier: Modifier = Modifier) {
    val matriz = remember(texto) {
        QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
    }
    Canvas(modifier.size(tam)) {
        drawRect(Color.White)
        val n = matriz.width
        val margen = 2
        val celda = size.width / (n + 2 * margen)
        for (y in 0 until n) for (x in 0 until n) {
            if (matriz[x, y]) drawRect(Color(0xFF111111), Offset((x + margen) * celda, (y + margen) * celda), Size(celda + 0.5f, celda + 0.5f))
        }
    }
}

/** Los colores de una banda para el acorde: los de sus miembros. */
internal fun coloresDe(nombres: List<String>) = nombres.map { Bandas.color(it) }
