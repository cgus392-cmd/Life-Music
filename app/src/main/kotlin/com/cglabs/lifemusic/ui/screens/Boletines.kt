package com.cglabs.lifemusic.ui.screens

import androidx.compose.ui.graphics.Color
import com.cglabs.lifemusic.R

/**
 * Un apartado del boletin: azulejo de color con icono, titulo y texto. Los
 * textos son recursos para que salgan en el idioma del telefono.
 */
data class ApartadoBoletin(
    val icono: Int,
    val color: Color,
    val titulo: Int,
    val texto: Int,
)

/**
 * Lo que se cuenta de una version la primera vez que arranca tras actualizar.
 *
 * [rutaAjustes] es a donde lleva el boton secundario —normalmente la pantalla de
 * Ajustes donde vive lo nuevo, con su fila resaltada— o null si no hay a donde ir.
 */
data class BoletinDeVersion(
    val versionCode: Int,
    val versionName: String,
    val tituloLinea1: Int,
    val tituloLinea2: Int,
    val intro: Int,
    val apartados: List<ApartadoBoletin>,
    val ademas: List<Int> = emptyList(),
    val rutaAjustes: String? = null,
)

/**
 * Registro de boletines, uno por version que tenga algo que contar. Es lo que se
 * muestra encima de Inicio la primera vez que arranca una version nueva: Echo lo
 * hacia con un changelog remoto, esto va dentro de la app, en los dos idiomas,
 * y no depende de que haya red.
 *
 * Como se anade uno (parte del ritual de publicar, ver RELEASE_INFO.md):
 *  1. Cadenas `boletin_vXYZ_*` en values/life_strings.xml y values-es/.
 *  2. Una entrada aqui con el versionCode nuevo.
 * Una version sin entrada no muestra nada: las correcciones pequenas no
 * interrumpen a nadie.
 */
object Boletines {

    private val v115 = BoletinDeVersion(
        versionCode = 7,
        versionName = "1.1.5",
        tituloLinea1 = R.string.boletin_v115_titulo_1,
        tituloLinea2 = R.string.boletin_v115_titulo_2,
        intro = R.string.boletin_v115_intro,
        apartados = listOf(
            ApartadoBoletin(R.drawable.tune, Color(0xFF90CAF9),
                R.string.boletin_v115_modos_t, R.string.boletin_v115_modos),
            ApartadoBoletin(R.drawable.discover_tune, Color(0xFFCE93D8),
                R.string.boletin_v115_estilos_t, R.string.boletin_v115_estilos),
            ApartadoBoletin(R.drawable.music_note, Color(0xFF80CBC4),
                R.string.boletin_v115_musica_t, R.string.boletin_v115_musica),
            ApartadoBoletin(R.drawable.chat_msg, Color(0xFFFFAB91),
                R.string.boletin_v115_lifeline_t, R.string.boletin_v115_lifeline),
            ApartadoBoletin(R.drawable.settings, Color(0xFFA5D6A7),
                R.string.boletin_v115_donde_t, R.string.boletin_v115_donde),
        ),
        ademas = listOf(
            R.string.boletin_v115_ademas_1,
            R.string.boletin_v115_ademas_2,
            R.string.boletin_v115_ademas_3,
            R.string.boletin_v115_ademas_4,
        ),
        rutaAjustes = "settings/player?highlightKey=" + android.net.Uri.encode("Automix (Beta)"),
    )

    val todos: List<BoletinDeVersion> = listOf(v115)

    /** El boletin de una version, o null si esa version no tiene nada que contar. */
    fun para(versionCode: Int): BoletinDeVersion? = todos.firstOrNull { it.versionCode == versionCode }

    /** El mas reciente que no sea posterior a [versionCode]: para reabrirlo desde Ajustes. */
    fun ultimoHasta(versionCode: Int): BoletinDeVersion? =
        todos.filter { it.versionCode <= versionCode }.maxByOrNull { it.versionCode }
}
