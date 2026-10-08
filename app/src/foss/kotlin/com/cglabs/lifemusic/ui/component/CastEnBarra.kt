package com.cglabs.lifemusic.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import com.cglabs.lifemusic.LocalPlayerConnection

/**
 * El boton «Transmitir» de la barra de Inicio (variante foss). Hibrido (1.3.2):
 * se ve siempre si hay algun TV con navegador recordado, porque ese no se
 * descubre en la red; si no, solo cuando hay un receptor de Cast o DLNA en la
 * red o ya se esta transmitiendo. El resto del tiempo no ocupa sitio. Para saberlo, la busqueda mDNS queda
 * encendida mientras la app esta en primer plano (se apaga al irse a segundo
 * plano: la radio no se gasta con la pantalla apagada).
 */
@Composable
fun CastEnBarra() {
    val conexion = LocalPlayerConnection.current ?: return
    val handler = remember(conexion) { runCatching { conexion.service.castConnectionHandler }.getOrNull() } ?: return
    LifecycleStartEffect(handler) {
        handler.buscar()
        onStopOrDispose { handler.dejarDeBuscar() }
    }
    val aparatos by handler.aparatos.collectAsState()
    val renderizadores by handler.renderizadores.collectAsState()
    val transmitiendo by handler.isCasting.collectAsState()
    val tvsWeb by handler.tvsWeb.collectAsState()
    AnimatedVisibility(
        visible = transmitiendo || aparatos.isNotEmpty() || renderizadores.isNotEmpty() || tvsWeb.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
    ) {
        CastButton(tintColor = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
