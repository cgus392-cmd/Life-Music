package com.cglabs.lifemusic.car

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cglabs.lifemusic.R

/**
 * Como se porta la app segun lo que aguanta el radio.
 *
 *  - [LIGERO]: sin canvas ni latido y con el fondo quieto. Es para los radios de
 *    1 GB (o 1,5), donde Android con el mapa abierto ya va justo: si la musica
 *    pide mas memoria, el sistema cierra a alguien, y puede ser el mapa.
 *  - [COMPLETO]: todo encendido. Radios de 2 GB o mas.
 */
enum class Perfil { LIGERO, COMPLETO }

/** Lo que se elige en Ajustes: el perfil que toca segun la RAM, o uno a mano. */
enum class ModoRendimiento(val titulo: Int) {
    AUTOMATICO(R.string.carro_rendimiento_automatico),
    LIGERO(R.string.carro_rendimiento_ligero),
    COMPLETO(R.string.carro_rendimiento_completo),
}

val CarroRendimientoKey = stringPreferencesKey("carroRendimiento")

/**
 * Lo que se sabe del radio, medido una vez al abrir la app.
 *
 * La RAM se da en su tamaño de fabrica: Android reporta siempre algo menos (el
 * nucleo y la GPU se quedan su parte; un radio «de 2 GB» dice 1,8 y uno «de
 * 1 GB» 0,9), asi que se redondea al tamaño comercial siguiente. Asi Ajustes
 * dice lo mismo que la caja del radio.
 */
data class Equipo(
    val ramBytes: Long,
    val pocaMemoria: Boolean,
    val nucleos: Int,
    val bits64: Boolean,
) {
    /** El tamaño comercial, en GB: 1, 1,5, 2, 3, 4... */
    val ramDeFabrica: Double = TAMANOS_GB.firstOrNull { ramBytes <= it * GIB } ?: (ramBytes / GIB)

    /** Hasta 1,5 GB, o si el propio Android se declara de poca memoria (Android Go): Ligero. */
    val perfilSugerido: Perfil = if (pocaMemoria || ramDeFabrica <= 1.5) Perfil.LIGERO else Perfil.COMPLETO

    /** «2 GB», «1,5 GB»: sin decimales si no hacen falta. */
    val ramTexto: String
        get() = if (ramDeFabrica % 1.0 == 0.0) "${ramDeFabrica.toInt()} GB" else "%.1f GB".format(ramDeFabrica)

    companion object {
        private const val GIB = 1024.0 * 1024 * 1024
        private val TAMANOS_GB = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0)

        fun medir(contexto: Context): Equipo {
            val am = contexto.getSystemService(ActivityManager::class.java)
            val info = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
            return Equipo(
                ramBytes = info.totalMem,
                pocaMemoria = am?.isLowRamDevice == true,
                nucleos = Runtime.getRuntime().availableProcessors(),
                bits64 = Process.is64Bit(),
            )
        }
    }
}

/** El perfil que manda: el elegido a mano o, en automatico, el que toca segun la RAM. */
fun perfilDe(modo: ModoRendimiento, equipo: Equipo): Perfil = when (modo) {
    ModoRendimiento.AUTOMATICO -> equipo.perfilSugerido
    ModoRendimiento.LIGERO -> Perfil.LIGERO
    ModoRendimiento.COMPLETO -> Perfil.COMPLETO
}

/** El radio, medido una vez en CarActivity. */
val LocalEquipo = staticCompositionLocalOf { Equipo(0, pocaMemoria = false, nucleos = 1, bits64 = false) }

/** El perfil en vigor, calculado en PantallaCarro. */
val LocalPerfil = staticCompositionLocalOf { Perfil.COMPLETO }
