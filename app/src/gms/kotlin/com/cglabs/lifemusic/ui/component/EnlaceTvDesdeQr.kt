package com.cglabs.lifemusic.ui.component

import androidx.compose.runtime.Composable

/**
 * La variante gms transmite con el SDK de Google y no tiene el TV con navegador
 * (es de la foss, la que se publica). Existe para que MainActivity compile igual
 * en las dos.
 */
@Composable
fun EnlaceTvDesdeQr() = Unit
