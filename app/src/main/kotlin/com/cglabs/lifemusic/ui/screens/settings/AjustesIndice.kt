package com.cglabs.lifemusic.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.ui.component.Material3SettingsGroup
import com.cglabs.lifemusic.ui.component.Material3SettingsItem

/**
 * 2.0 · Ajustes por subcategorias (plan del 27-10, arbol aprobado por CG): al
 * entrar a Apariencia o a Reproductor y audio se ven sus subcategorias; cada una
 * abre la misma pantalla filtrada con `?seccion=`. Con la busqueda se ve todo.
 */
data class SeccionDeAjustes(val clave: String, val titulo: Int, val detalle: Int, val icono: Int)

val SECCIONES_APARIENCIA = listOf(
    SeccionDeAjustes("tema", R.string.ajustes_tema, R.string.ajustes_tema_desc, R.drawable.palette),
    SeccionDeAjustes("inicio", R.string.ajustes_inicio, R.string.ajustes_inicio_desc, R.drawable.music_note),
    SeccionDeAjustes("reproductor", R.string.ajustes_reproductor, R.string.ajustes_reproductor_desc, R.drawable.play),
    SeccionDeAjustes("mini", R.string.ajustes_mini, R.string.ajustes_mini_desc, R.drawable.slow_motion_video),
    SeccionDeAjustes("letras", R.string.ajustes_letras, R.string.ajustes_letras_desc, R.drawable.lyrics),
    SeccionDeAjustes("ambiente", R.string.ajustes_ambiente, R.string.ajustes_ambiente_desc, R.drawable.gradient),
    SeccionDeAjustes("listas", R.string.ajustes_listas, R.string.ajustes_listas_desc, R.drawable.playlist_play),
)

val SECCIONES_REPRODUCTOR = listOf(
    SeccionDeAjustes("calidad", R.string.ajustes_calidad, R.string.ajustes_calidad_desc, R.drawable.tune),
    SeccionDeAjustes("transiciones", R.string.ajustes_transiciones, R.string.ajustes_transiciones_desc, R.drawable.sync),
    SeccionDeAjustes("sonido", R.string.ajustes_sonido, R.string.ajustes_sonido_desc, R.drawable.equalizer),
    SeccionDeAjustes("cola", R.string.ajustes_cola, R.string.ajustes_cola_desc, R.drawable.queue_music),
    SeccionDeAjustes("otros", R.string.ajustes_otros, R.string.ajustes_otros_desc, R.drawable.cast),
)

@Composable
fun IndiceDeAjustes(scrollState: ScrollState, secciones: List<SeccionDeAjustes>, alElegir: (String) -> Unit) {
    Material3SettingsGroup(
        scrollState = scrollState,
        items = secciones.map { s ->
            Material3SettingsItem(
                icon = painterResource(s.icono),
                title = { Text(stringResource(s.titulo)) },
                description = { Text(stringResource(s.detalle)) },
                trailingContent = { Icon(painterResource(R.drawable.navigate_next), contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = { alElegir(s.clave) },
            )
        },
    )
}
