package com.cglabs.lifemusic.ui.guia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.cglabs.lifemusic.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Guia interactiva de novedades: la primera vez que el usuario llega a una
 * pantalla con algo nuevo, se le senala el control (foco recortado sobre un
 * velo) y se le dice que hacer. El paso se da por hecho cuando lo prueba (el
 * control avisa con [hecho]) o cuando toca «Siguiente»; «Saltar» cierra la
 * guia entera. Cada guia se ve una sola vez (GuiasVistasKey).
 *
 * Los controles se apuntan con [objetivoDeGuia] (sus limites en la raiz) y
 * avisan de lo que el usuario hizo con [hecho]. La capa que dibuja es
 * GuiaOverlay, en MainActivity, por encima de todo.
 */
object Guia {
    /** Limites en la raiz de cada control apuntado, mientras esta en pantalla. */
    val objetivos = mutableStateMapOf<String, Rect>()

    private val _hechos = MutableStateFlow<Set<String>>(emptySet())
    /** Acciones que el usuario ya hizo en esta sesion (para dar pasos por probados). */
    val hechos = _hechos.asStateFlow()

    /** Hay una guia en pantalla (el modo ambiente no entra en reposo mientras tanto). */
    val activa = MutableStateFlow(false)

    fun hecho(evento: String) {
        if (evento !in _hechos.value) _hechos.value = _hechos.value + evento
    }

    // Nombres de objetivos y eventos, para no repartir cadenas por el codigo.
    const val CLIP = "clip"
    const val CARATULA = "caratula"
    const val VOLUMEN = "volumen"
    const val LETRA = "letra"
    const val CORAZON = "corazon"
    const val EV_CLIP_MENU = "clip_menu"
    const val EV_CONTROLES = "ambiente_controles"
    const val EV_VOLUMEN = "volumen_cambiado"
    const val EV_LETRA_SELECCION = "letra_seleccion"
    const val EV_CORAZON_LARGO = "corazon_largo"
}

class PasoDeGuia(val objetivo: String, val titulo: Int, val texto: Int, val evento: String?)

class DefinicionDeGuia(val nombre: String, val pasos: List<PasoDeGuia>)

/** Las guias de esta version. El sufijo es la version en que nacio: si algo cambia, guia nueva. */
val GUIAS = listOf(
    DefinicionDeGuia(
        "ambiente_1110",
        listOf(
            PasoDeGuia(Guia.CLIP, R.string.guia_clip_titulo, R.string.guia_clip_texto, Guia.EV_CLIP_MENU),
            PasoDeGuia(Guia.CARATULA, R.string.guia_caratula_titulo, R.string.guia_caratula_texto, Guia.EV_CONTROLES),
            PasoDeGuia(Guia.VOLUMEN, R.string.guia_volumen_titulo, R.string.guia_volumen_texto, Guia.EV_VOLUMEN),
        ),
    ),
    DefinicionDeGuia(
        "letra_1110",
        listOf(PasoDeGuia(Guia.LETRA, R.string.guia_letra_titulo, R.string.guia_letra_texto, Guia.EV_LETRA_SELECCION)),
    ),
    DefinicionDeGuia(
        "corazon_1110",
        listOf(PasoDeGuia(Guia.CORAZON, R.string.guia_corazon_titulo, R.string.guia_corazon_texto, Guia.EV_CORAZON_LARGO)),
    ),
)

/** Apunta este control para la guia mientras este en pantalla. */
@Composable
fun Modifier.objetivoDeGuia(nombre: String): Modifier {
    DisposableEffect(nombre) {
        onDispose { Guia.objetivos.remove(nombre) }
    }
    return this.onGloballyPositioned { Guia.objetivos[nombre] = it.boundsInRoot() }
}
