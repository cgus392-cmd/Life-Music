package com.cglabs.lifemusic.ui.screens.bandas

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Las cabezas con vida (pedido de CG del 28-10, «brincos, interactividad»; movimientos
 * de Diseño con springs de amortiguacion ~0,5). Nada se mueve solo: todo responde a
 * algo que paso (tocar, reaccionar, recibir el mando, la musica sonando, esperar).
 * Con «quitar animaciones» del telefono todo pasa a fundidos cortos.
 */

private val REBOTE = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)

/** Si el telefono pide quitar animaciones (escala de animacion en 0). */
@Composable
internal fun sinMovimiento(): Boolean {
    val contexto = LocalContext.current
    return remember { Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * Una persona con vida: al tocarla brinca 8 dp con aplaste y muestra su nombre 1,5 s.
 * Cada vez que sube [brincos] brinca (reaccion); cada vez que sube [doblesBrincos]
 * brinca dos veces (recibio el mando). Con [latiendo] late de 1 a 1,06 cada 0,5 s,
 * con [desfase] para que no lata toda la banda al mismo tiempo.
 */
@Composable
fun CabezaViva(
    color: Color,
    inicial: String,
    nombre: String,
    ancho: Dp,
    modifier: Modifier = Modifier,
    brincos: Int = 0,
    doblesBrincos: Int = 0,
    latiendo: Boolean = false,
    desfase: Int = 0,
) {
    val quieto = sinMovimiento()
    val escena = rememberCoroutineScope()
    val salto = remember { Animatable(0f) }
    val aplaste = remember { Animatable(1f) }
    val brillo = remember { Animatable(1f) }
    var globo by remember { mutableStateOf(false) }

    suspend fun brincar() {
        if (quieto) {
            brillo.snapTo(0.5f)
            brillo.animateTo(1f, tween(160))
            return
        }
        salto.animateTo(-8f, tween(130, easing = FastOutSlowInEasing))
        escena.launch {
            aplaste.snapTo(0.84f)
            aplaste.animateTo(1f, REBOTE)
        }
        salto.animateTo(0f, REBOTE)
    }

    LaunchedEffect(brincos) { if (brincos > 0) brincar() }
    LaunchedEffect(doblesBrincos) {
        if (doblesBrincos > 0) {
            brincar()
            brincar()
        }
    }
    LaunchedEffect(globo) {
        if (globo) {
            delay(1500)
            globo = false
        }
    }

    val latido = if (latiendo && !quieto) {
        rememberInfiniteTransition(label = "latido").animateFloat(
            initialValue = 1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 500
                    1.06f at 90
                    1f at 500
                },
                RepeatMode.Restart,
                initialStartOffset = StartOffset(desfase * 70),
            ),
            label = "latido",
        ).value
    } else {
        1f
    }
    val alfaGlobo by animateFloatAsState(if (globo) 1f else 0f, tween(if (quieto) 120 else 180), label = "globo")
    val densidad = LocalDensity.current

    Box(modifier, contentAlignment = Alignment.Center) {
        CabezaPersona(
            color, inicial, ancho,
            Modifier
                .graphicsLayer {
                    translationY = salto.value * density
                    scaleY = aplaste.value * latido
                    scaleX = (2f - aplaste.value) * latido
                    alpha = brillo.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    globo = true
                    escena.launch { brincar() }
                },
        )
        if (alfaGlobo > 0f) {
            val arriba = with(densidad) { (ancho * 0.75f + 30.dp).roundToPx() }
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -arriba)) {
                Text(
                    nombre, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .graphicsLayer { alpha = alfaGlobo }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE6222528))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** La reaccion que acaban de mandar: sube 40 dp y se desvanece en 0,9 s. [clave] cambia con cada reaccion. */
@Composable
fun ReaccionQueSube(tipo: String, clave: Long, tinta: Color, tam: Dp = 26.dp, modifier: Modifier = Modifier) {
    val quieto = sinMovimiento()
    val avance = remember(clave) { Animatable(0f) }
    LaunchedEffect(clave) { avance.animateTo(1f, tween(if (quieto) 400 else 900, easing = LinearEasing)) }
    if (avance.value >= 1f) return
    IconoReaccion(
        tipo, tinta, tam,
        modifier.graphicsLayer {
            translationY = if (quieto) 0f else -40.dp.toPx() * avance.value
            alpha = 1f - avance.value
        },
    )
}

/** Quien espera aprobacion flota ±4 dp en un ciclo de 3 s; si lo rechazan, cae. */
@Composable
fun CabezaQueEspera(color: Color, ancho: Dp, cayendo: Boolean, modifier: Modifier = Modifier) {
    val quieto = sinMovimiento()
    val flote = if (!quieto && !cayendo) {
        rememberInfiniteTransition(label = "flote").animateFloat(
            initialValue = -4f, targetValue = 4f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "flote",
        ).value
    } else {
        0f
    }
    val caida by animateFloatAsState(if (cayendo) 1f else 0f, tween(if (quieto) 200 else 600, easing = FastOutSlowInEasing), label = "caida")
    CabezaPersona(
        color, "", ancho,
        modifier.graphicsLayer {
            translationY = (flote + if (quieto) 0f else 60f * caida) * density
            rotationZ = if (quieto) 0f else 40f * caida
            alpha = 1f - caida
        },
    )
}
