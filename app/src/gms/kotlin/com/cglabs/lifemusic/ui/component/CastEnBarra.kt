package com.cglabs.lifemusic.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * El boton «Transmitir» de la barra de Inicio (variante gms). El SDK de Google
 * ya decide si Cast esta disponible; [CastButton] no dibuja nada si no lo esta.
 */
@Composable
fun CastEnBarra() {
    CastButton(tintColor = MaterialTheme.colorScheme.onSurfaceVariant)
}
