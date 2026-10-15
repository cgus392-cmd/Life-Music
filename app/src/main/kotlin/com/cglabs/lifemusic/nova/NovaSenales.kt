package com.cglabs.lifemusic.nova

import com.cglabs.lifemusic.db.entities.SenalNova

/**
 * Cuando una cancion termina de sonar, si cuenta como señal para Nova y de que
 * tipo. Sin Android, para poder probarlo. Ficha: docs/versiones/2.0/nova.md (4.2).
 *
 * No hace falta saber COMO termino (salto, eleccion de otra, final natural o
 * mezcla del Automix): una cancion solo se queda corta si la persona la dejo.
 */
object NovaSenales {
    /** Menos que esto es un toque sin querer o ir pasando rapido: no dice nada. */
    const val MINIMO_MS = 1_000L

    /** Devuelve el tipo de señal, o null si no se guarda. */
    fun tipo(escuchadoMs: Long, duracionMs: Long): Int? {
        if (duracionMs <= 0 || escuchadoMs < MINIMO_MS) return null
        return if (escuchadoMs * 2 < duracionMs) SenalNova.SALTO else SenalNova.ESCUCHADA
    }
}
