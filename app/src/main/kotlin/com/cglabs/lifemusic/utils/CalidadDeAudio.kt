package com.cglabs.lifemusic.utils

import androidx.annotation.StringRes
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.AudioQuality

/** El nombre de cada calidad, igual en los ajustes del telefono y del carro. */
@get:StringRes
val AudioQuality.titulo: Int
    get() = when (this) {
        AudioQuality.AUTO -> R.string.calidad_auto
        AudioQuality.HIGH -> R.string.calidad_alta
        AudioQuality.NORMAL -> R.string.calidad_normal
        AudioQuality.LOW -> R.string.calidad_baja
    }

/** Una linea que dice que significa en la practica. */
@get:StringRes
val AudioQuality.detalle: Int
    get() = when (this) {
        AudioQuality.AUTO -> R.string.calidad_auto_detalle
        AudioQuality.HIGH -> R.string.calidad_alta_detalle
        AudioQuality.NORMAL -> R.string.calidad_normal_detalle
        AudioQuality.LOW -> R.string.calidad_baja_detalle
    }
