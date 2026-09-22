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

/** La imagen de cabecera del boletin. */
enum class HeroBoletin { CRUCE, TROFEO, NINGUNO }

/**
 * Lo que se cuenta de una version la primera vez que arranca tras actualizar.
 *
 * [rutaAjustes] es a donde lleva el boton secundario —normalmente la pantalla de
 * Ajustes donde vive lo nuevo, con su fila resaltada— o null si no hay a donde ir.
 * [textoBoton] es su rotulo; [hero] la imagen de cabecera.
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
    val textoBoton: Int = R.string.boletin_ver_ajustes,
    val hero: HeroBoletin = HeroBoletin.NINGUNO,
    /** Imagen de cabecera (drawable); si esta, manda sobre [hero]. */
    val heroImagen: Int? = null,
    /** Actualizacion grande: la cabecera lo dice y lleva estrella (lo pidio CG para 1.1.10). */
    val grande: Boolean = false,
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
        hero = HeroBoletin.CRUCE,
    )

    private val v116 = BoletinDeVersion(
        versionCode = 8,
        versionName = "1.1.6",
        tituloLinea1 = R.string.boletin_v116_titulo_1,
        tituloLinea2 = R.string.boletin_v116_titulo_2,
        intro = R.string.boletin_v116_intro,
        apartados = listOf(
            ApartadoBoletin(R.drawable.trophy, Color(0xFFFFE082),
                R.string.boletin_v116_como_t, R.string.boletin_v116_como),
            ApartadoBoletin(R.drawable.timer, Color(0xFF90CAF9),
                R.string.boletin_v116_reglas_t, R.string.boletin_v116_reglas),
            ApartadoBoletin(R.drawable.share, Color(0xFFCE93D8),
                R.string.boletin_v116_ganador_t, R.string.boletin_v116_ganador),
            ApartadoBoletin(R.drawable.info, Color(0xFFA5D6A7),
                R.string.boletin_v116_privacidad_t, R.string.boletin_v116_privacidad),
        ),
        rutaAjustes = "concurso",
        textoBoton = R.string.concurso_participar,
        heroImagen = R.drawable.reto_airpods,
    )

    private val v117 = BoletinDeVersion(
        versionCode = 9,
        versionName = "1.1.7",
        tituloLinea1 = R.string.boletin_v117_titulo_1,
        tituloLinea2 = R.string.boletin_v117_titulo_2,
        intro = R.string.boletin_v117_intro,
        apartados = listOf(
            ApartadoBoletin(R.drawable.update, Color(0xFFA5D6A7),
                R.string.boletin_v117_camino_t, R.string.boletin_v117_camino),
            ApartadoBoletin(R.drawable.notification, Color(0xFF90CAF9),
                R.string.boletin_v117_aviso_t, R.string.boletin_v117_aviso),
            ApartadoBoletin(R.drawable.link, Color(0xFFCE93D8),
                R.string.boletin_v117_spotify_t, R.string.boletin_v117_spotify),
        ),
        ademas = listOf(R.string.boletin_v117_ademas_1),
        rutaAjustes = "settings/update",
    )

    private val v118 = BoletinDeVersion(
        versionCode = 10,
        versionName = "1.1.8",
        tituloLinea1 = R.string.boletin_v118_titulo_1,
        tituloLinea2 = R.string.boletin_v118_titulo_2,
        intro = R.string.boletin_v118_intro,
        apartados = listOf(
            ApartadoBoletin(R.drawable.trophy, Color(0xFFFFE082),
                R.string.boletin_v118_puesto_t, R.string.boletin_v118_puesto),
            ApartadoBoletin(R.drawable.notification, Color(0xFF90CAF9),
                R.string.boletin_v118_recordatorio_t, R.string.boletin_v118_recordatorio),
        ),
        rutaAjustes = "concurso",
        textoBoton = R.string.concurso_titulo,
    )

    private val v119 = BoletinDeVersion(
        versionCode = 11,
        versionName = "1.1.9",
        tituloLinea1 = R.string.boletin_v119_titulo_1,
        tituloLinea2 = R.string.boletin_v119_titulo_2,
        intro = R.string.boletin_v119_intro,
        apartados = listOf(
            ApartadoBoletin(R.drawable.volume_up, Color(0xFF90CAF9),
                R.string.boletin_v119_volumen_t, R.string.boletin_v119_volumen),
            ApartadoBoletin(R.drawable.ic_canvas, Color(0xFFCE93D8),
                R.string.boletin_v119_canvas_t, R.string.boletin_v119_canvas),
            ApartadoBoletin(R.drawable.play, Color(0xFF80CBC4),
                R.string.boletin_v119_controles_t, R.string.boletin_v119_controles),
            ApartadoBoletin(R.drawable.trophy, Color(0xFFFFE082),
                R.string.boletin_v119_reto_t, R.string.boletin_v119_reto),
        ),
        ademas = listOf(R.string.boletin_v119_ademas_1),
        rutaAjustes = "settings/appearance",
    )

    private val v1110 = BoletinDeVersion(
        versionCode = 12,
        versionName = "1.1.10",
        tituloLinea1 = R.string.boletin_v1110_titulo_1,
        tituloLinea2 = R.string.boletin_v1110_titulo_2,
        intro = R.string.boletin_v1110_intro,
        apartados = listOf(
            ApartadoBoletin(R.drawable.slow_motion_video, Color(0xFFF48FB1),
                R.string.boletin_v1110_clip_t, R.string.boletin_v1110_clip),
            ApartadoBoletin(R.drawable.ic_canvas, Color(0xFFCE93D8),
                R.string.boletin_v1110_grabar_t, R.string.boletin_v1110_grabar),
            ApartadoBoletin(R.drawable.envolvente, Color(0xFF80CBC4),
                R.string.boletin_v1110_envolvente_t, R.string.boletin_v1110_envolvente),
            ApartadoBoletin(R.drawable.settings, Color(0xFF90CAF9),
                R.string.boletin_v1110_telefono_t, R.string.boletin_v1110_telefono),
            ApartadoBoletin(R.drawable.trophy, Color(0xFFFFE082),
                R.string.boletin_v1110_ganador_t, R.string.boletin_v1110_ganador),
        ),
        ademas = listOf(R.string.boletin_v1110_ademas_1, R.string.boletin_v1110_ademas_2, R.string.boletin_v1110_ademas_3),
        rutaAjustes = "settings/player",
        grande = true,
    )

    private val v1120 = BoletinDeVersion(
        versionCode = 13,
        versionName = "1.2.0",
        tituloLinea1 = R.string.boletin_v1120_titulo_1,
        tituloLinea2 = R.string.boletin_v1120_titulo_2,
        intro = R.string.boletin_v1120_intro,
        apartados = listOf(
            ApartadoBoletin(R.drawable.cast, Color(0xFF80CBC4),
                R.string.boletin_v1120_transmitir_t, R.string.boletin_v1120_transmitir),
            ApartadoBoletin(R.drawable.cast_tv, Color(0xFFCE93D8),
                R.string.boletin_v1120_ambiente_t, R.string.boletin_v1120_ambiente),
            ApartadoBoletin(R.drawable.cast_connected, Color(0xFF90CAF9),
                R.string.boletin_v1120_pantalla_t, R.string.boletin_v1120_pantalla),
            ApartadoBoletin(R.drawable.stats, Color(0xFFA5D6A7),
                R.string.boletin_v1120_barra_t, R.string.boletin_v1120_barra),
        ),
        ademas = listOf(R.string.boletin_v1120_ademas_1, R.string.boletin_v1120_ademas_2),
        grande = true,
    )

    val todos: List<BoletinDeVersion> = listOf(v115, v116, v117, v118, v119, v1110, v1120)

    /** El boletin de una version, o null si esa version no tiene nada que contar. */
    fun para(versionCode: Int): BoletinDeVersion? = todos.firstOrNull { it.versionCode == versionCode }

    /** El mas reciente que no sea posterior a [versionCode]: para reabrirlo desde Ajustes. */
    fun ultimoHasta(versionCode: Int): BoletinDeVersion? =
        todos.filter { it.versionCode <= versionCode }.maxByOrNull { it.versionCode }
}
