package com.cglabs.lifemusic

import android.app.Activity

/**
 * La pantalla principal de esta app: [MainActivity] en Life Music (telefono) y
 * CarActivity en Life Music for Car (variante «car», src/car). La usa el codigo
 * comun que tiene que abrir la app, como la notificacion de reproduccion, sin
 * saber en cual de las dos corre.
 */
object ActividadPrincipal {
    val clase: Class<out Activity> by lazy {
        if (BuildConfig.ES_CARRO) {
            Class.forName("com.cglabs.lifemusic.car.CarActivity").asSubclass(Activity::class.java)
        } else {
            MainActivity::class.java
        }
    }
}
