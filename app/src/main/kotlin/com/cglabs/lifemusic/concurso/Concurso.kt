package com.cglabs.lifemusic.concurso

import com.cglabs.lifemusic.BuildConfig
import com.cglabs.lifemusic.db.MusicDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Reto de la semana: quien mas minutos escuche en Life Music entre [INICIO] y
 * [FIN] gana. Las fechas y el tope viven aqui como defecto y el servidor puede
 * corregirlos (ver [ConcursoRepository.estado]); asi CG puede alargar o cerrar
 * el concurso sin publicar una version.
 *
 * Los minutos salen de la tabla `event`, que la app ya escribe con el tiempo
 * REAL de reproduccion de cada cancion (sin pausas ni cargas, y solo si sono
 * mas de 30 s). No hay contador nuevo ni telemetria: es la misma cifra de la
 * pantalla de Estadisticas, y por eso cuenta desde el inicio del concurso
 * aunque el usuario actualice tarde.
 */
object Concurso {
    val ZONA: ZoneId = ZoneId.of("America/Bogota")
    val INICIO: LocalDate = LocalDate.of(2026, 9, 13)
    val FIN: LocalDate = LocalDate.of(2026, 9, 19)
    const val TOPE_MIN_DIA = 360
    const val PREMIO = "AirPods Pro"

    /** Cuantos dias despues del fin sigue visible, para ver el resultado. */
    private const val DIAS_DE_GRACIA = 7L

    /** Sin URL y clave en local.properties el modulo no existe: ni icono ni llamadas. */
    val configurado: Boolean
        get() = BuildConfig.CONCURSO_URL.isNotBlank() && BuildConfig.CONCURSO_ANON_KEY.isNotBlank()

    fun hoy(): LocalDate = LocalDate.now(ZONA)

    fun enVentana(hoy: LocalDate = hoy()): Boolean = !hoy.isBefore(INICIO) && !hoy.isAfter(FIN)

    fun terminado(hoy: LocalDate = hoy()): Boolean = hoy.isAfter(FIN)

    fun porEmpezar(hoy: LocalDate = hoy()): Boolean = hoy.isBefore(INICIO)

    /** Visible en la app: desde que existe la version hasta unos dias tras el fin. */
    fun visible(hoy: LocalDate = hoy()): Boolean = configurado && !hoy.isAfter(FIN.plusDays(DIAS_DE_GRACIA))

    /** Dias que quedan contando hoy; 0 si ya termino. */
    fun diasRestantes(hoy: LocalDate = hoy()): Int =
        if (terminado(hoy)) 0 else (ChronoUnit.DAYS.between(hoy, FIN) + 1).toInt().coerceAtLeast(0)

    /**
     * Minutos escuchados por dia del concurso, segun los eventos del telefono,
     * SIN tope: el tope lo aplica el servidor, y la pantalla lo muestra aparte.
     * Los dias se cortan en la medianoche local del telefono, que es como la
     * app guarda los eventos (hora de pared tratada como UTC, ver Converters).
     */
    suspend fun minutosPorDia(database: MusicDatabase): Map<LocalDate, Int> {
        val hoyLocal = LocalDate.now()
        val ultimo = if (hoyLocal.isBefore(FIN)) hoyLocal else FIN
        if (ultimo.isBefore(INICIO)) return emptyMap()
        val resultado = LinkedHashMap<LocalDate, Int>()
        var dia = INICIO
        while (!dia.isAfter(ultimo)) {
            val desde = dia.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val hasta = dia.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1
            val ms = database.getTotalPlayTimeInRange(desde, hasta).first() ?: 0L
            resultado[dia] = (ms / 60_000L).toInt()
            dia = dia.plusDays(1)
        }
        return resultado
    }

    /** Suma con tope diario, tal como la calcula el servidor. */
    fun totalContado(porDia: Map<LocalDate, Int>): Int = porDia.values.sumOf { minOf(it, TOPE_MIN_DIA) }

    fun minutosHoy(porDia: Map<LocalDate, Int>): Int = porDia[LocalDate.now()] ?: 0

    /** «3 h 12 min», «45 min». */
    fun formatear(minutos: Int): String {
        val h = minutos / 60
        val m = minutos % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }
}
