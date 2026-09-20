package com.cglabs.lifemusic.clip

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.ClipDuracionDefault
import com.cglabs.lifemusic.constants.ClipDuracionKey
import com.cglabs.lifemusic.constants.ClipDuracionMaxima
import com.cglabs.lifemusic.constants.ClipGrabacionAudioKey
import com.cglabs.lifemusic.ui.guia.Guia
import com.cglabs.lifemusic.ui.guia.objetivoDeGuia
import com.cglabs.lifemusic.utils.rememberPreference

/** Duraciones del mini menu, las mismas que los chips de la hoja. */
private val DURACIONES = listOf(10, 15, 30, 60)

/**
 * El boton de clip del modo ambiente. Toque corto: la hoja con todas las
 * opciones. Mantener pulsado: un mini menu cuelga del boton con las
 * duraciones; se desliza con el mismo dedo y al soltar encima de una se graba
 * el modo ambiente directamente, con audio (salvo que se haya apagado en la
 * hoja). Soltar fuera no hace nada. Lo pidio CG: el menor numero de pasos.
 */
@Composable
fun BotonDeClip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val conexion = LocalPlayerConnection.current ?: return
    val haptico = LocalHapticFeedback.current
    val densidad = androidx.compose.ui.platform.LocalDensity.current.density
    val cancion by conexion.mediaMetadata.collectAsState()
    var duracionGuardada by rememberPreference(ClipDuracionKey, defaultValue = ClipDuracionDefault)
    val conAudio by rememberPreference(ClipGrabacionAudioKey, defaultValue = true)

    var menu by remember { mutableStateOf(false) }
    var origen by remember { mutableStateOf(Offset.Zero) }
    var dedo by remember { mutableStateOf(Offset.Zero) }
    val cajas = remember { mutableStateOf(List<Rect?>(DURACIONES.size) { null }) }
    val bajoElDedo = if (menu) cajas.value.indexOfFirst { it?.contains(dedo) == true } else -1

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = context.getString(R.string.clip_titulo) }
                .objetivoDeGuia(Guia.CLIP)
                .onGloballyPositioned { origen = it.positionInRoot() }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { ClipLanzador.abrir(desdeAmbiente = true) })
                }
                .pointerInput(cancion?.id, conAudio) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                            dedo = origen + local
                            menu = true
                            Guia.hecho(Guia.EV_CLIP_MENU)
                        },
                        onDrag = { cambio, arrastre ->
                            cambio.consume()
                            dedo += arrastre
                        },
                        onDragEnd = {
                            val elegido = cajas.value.indexOfFirst { it?.contains(dedo) == true }
                            menu = false
                            if (elegido >= 0) {
                                val segundos = DURACIONES[elegido].coerceIn(5, ClipDuracionMaxima)
                                duracionGuardada = segundos
                                val actual = cancion
                                val activity = context.actividad()
                                if (actual != null && activity != null) {
                                    haptico.performHapticFeedback(HapticFeedbackType.Confirm)
                                    GrabadorDeAmbiente.iniciar(activity, conexion.service, actual, segundos, conAudio)
                                }
                            }
                        },
                        onDragCancel = { menu = false },
                    )
                },
        ) {
            Icon(
                painter = painterResource(R.drawable.slow_motion_video),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
            )
        }
        AnimatedVisibility(
            visible = menu,
            enter = fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)),
            exit = fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.clip_grabar),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
                DURACIONES.forEachIndexed { i, s ->
                    val activo = i == bajoElDedo
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .width(84.dp)
                            .clip(CircleShape)
                            .background(if (activo) Color.White else Color.White.copy(alpha = 0.12f))
                            .padding(vertical = 9.dp)
                            .onGloballyPositioned { c ->
                                // Con margen alrededor: el dedo tapa la pastilla y no hay que afinar.
                                val r = c.boundsInRoot()
                                val nuevo = cajas.value.toMutableList()
                                nuevo[i] = Rect(r.left - 24f * densidad, r.top - 4f * densidad, r.right + 24f * densidad, r.bottom + 4f * densidad)
                                cajas.value = nuevo
                            },
                    ) {
                        Text(
                            text = "$s s",
                            color = if (activo) Color.Black else Color.White,
                            fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** La Activity detras de un contexto de Compose (las hojas viven en ventanas propias). */
internal fun Context.actividad(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
