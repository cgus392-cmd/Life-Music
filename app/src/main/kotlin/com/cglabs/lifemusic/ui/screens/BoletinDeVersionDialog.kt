package com.cglabs.lifemusic.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cglabs.lifemusic.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Boletin de version: lo que hay de nuevo, a pantalla completa, la primera vez
 * que arranca una version tras actualizar. Misma familia visual que la
 * bienvenida —fondo vivo, titulo en dos lineas, azulejos— para que se lea como
 * la misma voz, pero con un solo paso: se lee de arriba abajo y se cierra.
 *
 * Dos salidas: «Entendido», y «Ver ajustes» si el boletin sabe a donde llevar.
 * Las dos lo dan por visto; volver a verlo es cosa de Ajustes > Actualizaciones.
 */
@Composable
fun BoletinDeVersionDialog(
    boletin: BoletinDeVersion,
    onCerrar: () -> Unit,
    onAbrirAjustes: ((String) -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        )
    ) {
        VentanaAPantallaCompleta()
        ContenidoBoletin(boletin, onCerrar, onAbrirAjustes)
    }
}

@Composable
private fun ContenidoBoletin(
    boletin: BoletinDeVersion,
    onCerrar: () -> Unit,
    onAbrirAjustes: ((String) -> Unit)?,
) {
    val animar = recordarSiAnimar()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FondoVivo(animar = animar)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(28.dp))

                    Escalonado(0, animar) {
                        Chip(
                            icono = R.drawable.info,
                            texto = stringResource(R.string.boletin_eyebrow, boletin.versionName),
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Escalonado(1, animar) {
                        TituloDisplay(
                            primeraLinea = stringResource(boletin.tituloLinea1),
                            segundaLinea = stringResource(boletin.tituloLinea2),
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    Escalonado(2, animar) { HeroCruce(animar) }

                    Spacer(Modifier.height(20.dp))

                    Escalonado(3, animar) {
                        Text(
                            text = stringResource(boletin.intro),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 24.sp,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    boletin.apartados.forEachIndexed { i, a ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Escalonado(4 + i, animar) {
                            TarjetaAzulejo(
                                icono = a.icono,
                                colorAzulejo = a.color,
                                titulo = stringResource(a.titulo),
                                descripcion = stringResource(a.texto),
                            )
                        }
                    }

                    if (boletin.ademas.isNotEmpty()) {
                        Spacer(Modifier.height(28.dp))
                        Escalonado(4 + boletin.apartados.size, animar) {
                            Column {
                                Text(
                                    text = stringResource(R.string.boletin_ademas),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.2.sp,
                                )
                                Spacer(Modifier.height(12.dp))
                                boletin.ademas.forEach { texto ->
                                    Vineta(stringResource(texto))
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                BarraBoletin(
                    conAjustes = boletin.rutaAjustes != null && onAbrirAjustes != null,
                    onAjustes = { boletin.rutaAjustes?.let { onAbrirAjustes?.invoke(it) } },
                    onCerrar = onCerrar,
                )
            }
        }
    }
}

/**
 * La imagen del Automix es la propia transicion: dos curvas equal-power —la que
 * se va, la que llega— y un cursor que las recorre sin parar. No es un adorno:
 * es literalmente lo que hace el motor durante un fundido.
 */
@Composable
private fun HeroCruce(animar: Boolean) {
    val saliente = MaterialTheme.colorScheme.primary
    val entrante = MaterialTheme.colorScheme.tertiary
    val rejilla = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    val progreso = if (animar) {
        val transicion = rememberInfiniteTransition(label = "cruce")
        val v by transicion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4_200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "cursor",
        )
        v
    } else {
        0.5f
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            val ancho = size.width
            val alto = size.height
            val grosor = 3.dp.toPx()

            // Suelo y techo, apenas insinuados.
            drawLine(rejilla, Offset(0f, alto), Offset(ancho, alto), strokeWidth = 1.dp.toPx())
            drawLine(rejilla, Offset(0f, 0f), Offset(ancho, 0f), strokeWidth = 1.dp.toPx())

            fun curva(ganancia: (Float) -> Float): Path {
                val ruta = Path()
                val muestras = 96
                for (i in 0..muestras) {
                    val t = i / muestras.toFloat()
                    val x = t * ancho
                    val y = alto - ganancia(t) * alto
                    if (i == 0) ruta.moveTo(x, y) else ruta.lineTo(x, y)
                }
                return ruta
            }
            val fuera: (Float) -> Float = { t -> cos(t * PI.toFloat() / 2f) }
            val dentro: (Float) -> Float = { t -> sin(t * PI.toFloat() / 2f) }

            drawPath(curva(fuera), saliente, style = Stroke(width = grosor, cap = StrokeCap.Round))
            drawPath(curva(dentro), entrante, style = Stroke(width = grosor, cap = StrokeCap.Round))

            // El cursor: donde va el fundido ahora mismo, con un punto en cada curva.
            val x = progreso * ancho
            drawLine(
                brush = Brush.verticalGradient(listOf(Color.Transparent, rejilla, Color.Transparent)),
                start = Offset(x, -8.dp.toPx()),
                end = Offset(x, alto + 8.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawCircle(saliente, radius = 5.dp.toPx(), center = Offset(x, alto - fuera(progreso) * alto))
            drawCircle(entrante, radius = 5.dp.toPx(), center = Offset(x, alto - dentro(progreso) * alto))
        }
    }
}

@Composable
private fun Vineta(texto: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(7.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {}
        }
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BarraBoletin(
    conAjustes: Boolean,
    onAjustes: () -> Unit,
    onCerrar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (conAjustes) {
            OutlinedButton(
                onClick = onAjustes,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.boletin_ver_ajustes), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }
        Button(
            onClick = onCerrar,
            modifier = Modifier
                .weight(if (conAjustes) 1.15f else 1f)
                .height(64.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(stringResource(R.string.boletin_entendido), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
