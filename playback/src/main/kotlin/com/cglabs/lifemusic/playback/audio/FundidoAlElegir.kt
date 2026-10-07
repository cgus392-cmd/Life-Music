package com.cglabs.lifemusic.playback.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fundido al elegir otra cancion (1.3.1): bajar, cambiar, subir. Cuando suena
 * algo y la persona elige una cancion nueva, la que suena baja, se cambia con
 * el volumen en cero y la nueva sube cuando de verdad empieza a sonar. Un solo
 * reproductor: el Automix y la transicion suave no intervienen.
 *
 * Aqui va solo la parte que no depende de Android (curvas, pasos y cuando
 * aplica); el motor vive en MusicService. Ficha: docs/versiones/1.3.1/fundido.md.
 */
object FundidoAlElegir {
    /** Bajar retrasa el cambio, asi que es corto. */
    const val BAJAR_MS = 400L

    /** Subir no retrasa nada: puede ser mas largo y mas suave. */
    const val SUBIR_MS = 600L

    /** Si la nueva no suena en este tiempo (red lenta), se devuelve el volumen y se acaba. */
    const val ESPERA_MAXIMA_MS = 20_000L

    /** Unos 15 ms por paso: con 100 ms el fundido suena a cremallera. */
    private const val MS_POR_PASO = 15L

    /** Ganancia al bajar: 1 al empezar, 0 al terminar (coseno, potencia constante). */
    fun bajada(progreso: Float): Float = cos(progreso.coerceIn(0f, 1f) * (PI / 2).toFloat()).coerceIn(0f, 1f)

    /** Ganancia al subir: 0 al empezar, 1 al terminar (seno). */
    fun subida(progreso: Float): Float = sin(progreso.coerceIn(0f, 1f) * (PI / 2).toFloat()).coerceIn(0f, 1f)

    /** Cuantos pasos tiene una rampa de [ms] milisegundos. Nunca menos de uno. */
    fun pasos(ms: Long): Int = (ms / MS_POR_PASO).toInt().coerceAtLeast(1)

    /**
     * Si un cambio de cancion lleva fundido. Solo cuando hay algo que bajar: si
     * no sonaba nada, la nueva arranca con su primer golpe entero.
     *
     * @param activo el interruptor de Ajustes.
     * @param sonando el reproductor esta sonando de verdad (no en pausa ni cargando).
     * @param transmitiendo suena el TV, no el telefono.
     * @param enTransicion hay un fundido de Automix o de transicion suave en marcha.
     * @param silenciado el silencio de la app esta puesto.
     * @param playWhenReady la cancion elegida va a sonar al cargar.
     */
    fun aplica(
        activo: Boolean,
        sonando: Boolean,
        transmitiendo: Boolean,
        enTransicion: Boolean,
        silenciado: Boolean,
        playWhenReady: Boolean,
    ): Boolean = activo && sonando && playWhenReady && !transmitiendo && !enTransicion && !silenciado
}
