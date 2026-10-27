package com.cglabs.lifemusic.nova

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 2.0 · Que Nova se sienta a gusto en cualquier telefono (pedido de CG, 15-10):
 * no todos tienen el procesador de un S25. Dos pruebas:
 *
 * 1. **Antes de bajar** (instantanea, sin descargar nada): la clase de rendimiento
 *    que declara el fabricante (Android 12+), la memoria y los nucleos. Con eso
 *    cada voz queda disponible, «puede tardar» o no disponible, y lo que el
 *    telefono no puede mover ni se deja bajar: no se gastan datos en vano.
 * 2. **Despues de bajar** (2 a 3 s): la voz dice una frase de prueba y se mide
 *    cuanto tarda por cada segundo de voz. Eso decide de verdad: si no da la
 *    talla, la voz no se puede elegir.
 */
object NovaCapacidad {
    /** Que tan exigente es una voz. */
    enum class Exigencia { LIGERA, MEDIA, ALTA }

    /** Lo que se le ofrece a este telefono antes de bajar. */
    enum class Prevision { DISPONIBLE, PUEDE_TARDAR, NO_DISPONIBLE }

    /** El resultado de la prueba real con la voz ya bajada. */
    enum class Veredicto { FLUIDA, CON_ESPERA, NO_APTA }

    /** Hasta este tiempo por segundo de voz, habla al instante. */
    const val FLUIDA_HASTA = 0.5f

    /**
     * Hasta aqui sirve: Nova prepara la frase 20 s antes y la musica la espera.
     * Antes era 1,1 y bloqueo a Daniela en el S25 (midio 1,27 con la musica sonando):
     * solo se bloquea lo que de verdad no se puede usar (CG, 15-10).
     */
    const val CON_ESPERA_HASTA = 2.0f

    data class Telefono(val claseRendimiento: Int, val memoriaGb: Float, val nucleos: Int, val memoriaBaja: Boolean)

    fun telefono(contexto: Context): Telefono {
        val am = contexto.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val clase = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0
        return Telefono(
            claseRendimiento = clase,
            memoriaGb = info.totalMem / 1_073_741_824f,
            nucleos = Runtime.getRuntime().availableProcessors(),
            memoriaBaja = am.isLowRamDevice,
        )
    }

    /**
     * 2.0 · Estudio: hasta donde llega este telefono en el medidor de «la prueba
     * de sonido» (0 = nada, 1 = todo), con las mismas gamas que [prevision]. Las
     * funciones se ubican en esa misma escala: ligera ~0,4, media ~0,6, pesada ~0,88.
     */
    fun aguante(t: Telefono): Float {
        if (t.memoriaBaja) return 0.25f
        val gamaAlta = t.claseRendimiento >= Build.VERSION_CODES.S || (t.memoriaGb >= 7.5f && t.nucleos >= 8)
        val gamaMedia = t.claseRendimiento >= Build.VERSION_CODES.R || (t.memoriaGb >= 3.5f && t.nucleos >= 8)
        return when {
            gamaAlta && t.memoriaGb >= 5.5f -> 0.78f
            gamaAlta -> 0.66f
            gamaMedia -> 0.52f
            else -> 0.32f
        }
    }

    /**
     * La prevision para una voz segun el telefono. Generosa a proposito: la que
     * decide es la prueba real despues de bajar; esto solo evita bajar algo que
     * seguro no va a andar.
     */
    fun prevision(t: Telefono, exigencia: Exigencia): Prevision {
        if (t.memoriaBaja) return if (exigencia == Exigencia.LIGERA) Prevision.PUEDE_TARDAR else Prevision.NO_DISPONIBLE
        val gamaAlta = t.claseRendimiento >= Build.VERSION_CODES.S || (t.memoriaGb >= 7.5f && t.nucleos >= 8)
        val gamaMedia = t.claseRendimiento >= Build.VERSION_CODES.R || (t.memoriaGb >= 3.5f && t.nucleos >= 8)
        return when (exigencia) {
            Exigencia.LIGERA -> if (t.memoriaGb >= 1.8f) Prevision.DISPONIBLE else Prevision.PUEDE_TARDAR
            Exigencia.MEDIA -> when {
                gamaAlta -> Prevision.DISPONIBLE
                gamaMedia -> Prevision.PUEDE_TARDAR
                else -> Prevision.NO_DISPONIBLE
            }
            Exigencia.ALTA -> when {
                gamaAlta && t.memoriaGb >= 5.5f -> Prevision.DISPONIBLE
                gamaMedia && t.memoriaGb >= 5.5f -> Prevision.PUEDE_TARDAR
                else -> Prevision.NO_DISPONIBLE
            }
        }
    }

    fun veredicto(segundosPorSegundo: Float): Veredicto = when {
        segundosPorSegundo <= FLUIDA_HASTA -> Veredicto.FLUIDA
        segundosPorSegundo <= CON_ESPERA_HASTA -> Veredicto.CON_ESPERA
        else -> Veredicto.NO_APTA
    }

    // ---- La prueba real, guardada por voz (clave del paquete) ----

    private fun preferencias(contexto: Context) =
        contexto.applicationContext.getSharedPreferences("nova_pruebas", Context.MODE_PRIVATE)

    /** Cuanto tardo por segundo de voz en la prueba, o null si no se ha probado. */
    fun medido(contexto: Context, clave: String): Float? =
        preferencias(contexto).getFloat(clave, -1f).takeIf { it >= 0f }

    fun veredictoDe(contexto: Context, clave: String): Veredicto? = medido(contexto, clave)?.let(::veredicto)

    fun olvidar(contexto: Context, clave: String) {
        preferencias(contexto).edit().remove(clave).apply()
    }

    /**
     * La prueba real: carga la voz aparte, la calienta con una palabra y mide una
     * frase tipica de Nova. Guarda y devuelve el tiempo por segundo de voz.
     */
    suspend fun probar(contexto: Context, voz: VozHD, calidad: CalidadVoz): Float? = withContext(Dispatchers.Default) {
        val clave = voz.clave(calidad)
        val archivos = NovaDescargas.archivos(contexto, clave) ?: return@withContext null
        val motor = runCatching { OfflineTts(config = NovaVozHD.configuracion(voz, archivos)) }.getOrNull()
            ?: return@withContext null
        try {
            motor.generate("Hola.", voz.hablante, 1.0f)
            // Dos medidas y la mejor: un mal momento (la musica sonando, otra app) no castiga a la voz.
            val porSegundo = (1..2).mapNotNull {
                val inicio = SystemClock.elapsedRealtime()
                val audio = motor.generate(
                    "Buenas tardes. Soy Nova, tu DJ. Lo de siempre: Marea alta, de Luz de Neón.",
                    voz.hablante, 1.0f,
                )
                val tardo = (SystemClock.elapsedRealtime() - inicio) / 1000f
                val duro = audio.samples.size.toFloat() / audio.sampleRate.coerceAtLeast(1)
                if (duro > 0f) tardo / duro else null
            }.minOrNull() ?: return@withContext null
            preferencias(contexto).edit().putFloat(clave, porSegundo).apply()
            android.util.Log.i(
                "LifeMusicNova",
                "prueba de voz $clave: ${"%.2f".format(porSegundo)} s por segundo de voz -> ${veredicto(porSegundo)}",
            )
            porSegundo
        } finally {
            runCatching { motor.release() }
        }
    }

    /** Para el registro: como se ve este telefono. */
    fun describir(contexto: Context): String = telefono(contexto).let {
        "clase=${it.claseRendimiento} memoria=${"%.1f".format(it.memoriaGb)} GB nucleos=${it.nucleos} baja=${it.memoriaBaja}"
    }
}
