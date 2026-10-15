package com.cglabs.lifemusic.nova

import com.cglabs.lifemusic.R

/**
 * Las cuatro estaciones del dial de Nova. El orden de la rotacion es el de las
 * entradas: lo conocido primero, despues algo nuevo, lo de esta hora y un viaje
 * al pasado. [estacion] es el rotulo corto del dial. Ficha: docs/versiones/2.0/nova.md (4.1).
 */
enum class NovaBloque(val nombre: Int, val estacion: Int) {
    DE_SIEMPRE(R.string.nova_bloque_de_siempre, R.string.nova_estacion_de_siempre),
    ALGO_NUEVO(R.string.nova_bloque_algo_nuevo, R.string.nova_estacion_algo_nuevo),
    ESTE_MOMENTO(R.string.nova_bloque_este_momento, R.string.nova_estacion_este_momento),
    AL_PASADO(R.string.nova_bloque_al_pasado, R.string.nova_estacion_al_pasado),
    ;

    companion object {
        /** Como van en el dial, de izquierda a derecha (maqueta aprobada). */
        val DIAL = listOf(DE_SIEMPRE, AL_PASADO, ALGO_NUEVO, ESTE_MOMENTO)
    }
}
