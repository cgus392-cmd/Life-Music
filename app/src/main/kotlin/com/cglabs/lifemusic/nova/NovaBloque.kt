package com.cglabs.lifemusic.nova

import com.cglabs.lifemusic.R

/**
 * Las cuatro estaciones del dial de Nova. El orden de la rotacion es el de las
 * entradas: lo conocido primero, despues algo nuevo, lo de esta hora y un viaje
 * al pasado. Ficha: docs/versiones/2.0/nova.md (4.1).
 */
enum class NovaBloque(val nombre: Int) {
    DE_SIEMPRE(R.string.nova_bloque_de_siempre),
    ALGO_NUEVO(R.string.nova_bloque_algo_nuevo),
    ESTE_MOMENTO(R.string.nova_bloque_este_momento),
    AL_PASADO(R.string.nova_bloque_al_pasado),
}
