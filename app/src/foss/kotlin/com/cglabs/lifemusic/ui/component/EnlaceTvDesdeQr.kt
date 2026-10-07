package com.cglabs.lifemusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.cast.EnlaceTvPendiente

/**
 * Cuando la app se abre por el QR de Life Music TV, pregunta «¿Enlazar con el TV
 * que muestra XXX XXX?» con el codigo ya puesto. Espera a que el servicio de
 * musica este conectado (al abrir en frio tarda un momento).
 */
@Composable
fun EnlaceTvDesdeQr() {
    val codigo by EnlaceTvPendiente.codigo.collectAsState()
    val c = codigo ?: return
    val conexion = LocalPlayerConnection.current ?: return
    val handler = remember(conexion) { runCatching { conexion.service.castConnectionHandler }.getOrNull() } ?: return
    key(c) {
        DialogoCodigoTv(
            handler = handler,
            codigoInicial = c,
            onCerrar = { EnlaceTvPendiente.codigo.value = null },
            onEnlazado = {},
        )
    }
}
