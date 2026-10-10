/*
 * Life Music — mesa de sonido del carro.
 * Copyright (C) 2026 CG LABS. GPL-3.0, como el resto del proyecto.
 */
package com.cglabs.lifemusic.eq.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lo que el usuario puso en la mesa. Todas las ganancias en dB, de -12 a +12.
 *
 * [graves] y [agudos] son estantes (shelves) en [MesaDeSonido.GRAVES_HZ] y
 * [MesaDeSonido.AGUDOS_HZ], como las perillas de bajo y brillo de un radio;
 * [bandas] son las 10 de octava de [MesaDeSonido.FRECUENCIAS]; [ganancia] es la
 * general. Nada de esto resta volumen: subir suena mas fuerte (ver el limitador
 * en MesaDeSonidoAudioProcessor).
 */
data class AjusteMesa(
    val activo: Boolean = false,
    val ganancia: Float = 0f,
    val graves: Float = 0f,
    val agudos: Float = 0f,
    val bandas: List<Float> = List(MesaDeSonido.BANDAS) { 0f },
)

/**
 * Lo que mide la mesa de un bloque de audio ya procesado. Se guardan en un
 * anillo con la hora en que se midieron, porque el audio procesado tarda unos
 * cientos de milisegundos en salir por el parlante: la pantalla pide la medida
 * de hace [MesaDeSonido.RETRASO_MEDIDOR_NS] para que el vumetro vaya con lo que
 * se oye y no por delante.
 */
class MedidaDeSalida {
    @Volatile var cuandoNs: Long = 0L
    /** Pico y RMS de cada canal, lineales (1 = el maximo digital). */
    @Volatile var picoIzq = 0f
    @Volatile var picoDer = 0f
    @Volatile var rmsIzq = 0f
    @Volatile var rmsDer = 0f
    /** Lo que quito el limitador en el bloque, en dB (0 o negativo). */
    @Volatile var limitadorDb = 0f
    /** Energia de cada banda del analizador, en dBFS. */
    val bandasDb = FloatArray(MesaDeSonido.BANDAS) { -90f }
}

/**
 * La mesa de sonido del carro: los ajustes vivos, la matematica de los filtros
 * (RBJ Audio EQ Cookbook) y lo que se mide. Kotlin puro, sin Android, para
 * poder probarla en la JVM.
 *
 * Una sola mesa por proceso: la pantalla escribe [ajuste] y lo leen todos los
 * procesadores (hay dos reproductores cuando hay transicion entre canciones).
 */
object MesaDeSonido {
    const val BANDAS = 10
    val FRECUENCIAS = doubleArrayOf(31.25, 62.5, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0)
    const val Q_BANDA = 1.41
    const val GRAVES_HZ = 90.0
    const val AGUDOS_HZ = 9_000.0
    const val MINIMO_DB = -12f
    const val MAXIMO_DB = 12f

    /** El audio procesado sale por el parlante unos 300 ms despues (bufer de la tarjeta). */
    const val RETRASO_MEDIDOR_NS = 300_000_000L

    @Volatile
    var ajuste = AjusteMesa()
        private set

    /** Cambia con cada ajuste nuevo; los procesadores recalculan solo cuando cambia. */
    @Volatile
    var version = 0
        private set

    /** La pantalla del ecualizador esta abierta: medir el vumetro y el analizador. */
    @Volatile
    var analizar = false

    @Synchronized
    fun aplicar(nuevo: AjusteMesa) {
        ajuste = nuevo.copy(
            ganancia = nuevo.ganancia.coerceIn(MINIMO_DB, MAXIMO_DB),
            graves = nuevo.graves.coerceIn(MINIMO_DB, MAXIMO_DB),
            agudos = nuevo.agudos.coerceIn(MINIMO_DB, MAXIMO_DB),
            bandas = List(BANDAS) { i -> (nuevo.bandas.getOrNull(i) ?: 0f).coerceIn(MINIMO_DB, MAXIMO_DB) },
        )
        version++
    }

    // --- Medidas: un anillo que escribe el hilo de audio y lee la pantalla ---

    private const val ANILLO = 64
    private val anillo = Array(ANILLO) { MedidaDeSalida() }

    @Volatile
    private var escrita = -1

    /** Da la siguiente casilla del anillo para llenarla (solo el hilo de audio). */
    internal fun casillaNueva(): MedidaDeSalida = anillo[(escrita + 1).mod(ANILLO)]

    /** Publica la casilla que se acaba de llenar. */
    internal fun publicar() {
        escrita = (escrita + 1).mod(ANILLO)
    }

    /**
     * La medida que corresponde a lo que suena ahora: la mas reciente hecha
     * antes de [ahoraNs] menos el retraso del bufer. Null si no hay ninguna
     * reciente (pausa, sin musica): la pantalla deja caer el vumetro.
     */
    fun medidaAhora(ahoraNs: Long): MedidaDeSalida? {
        val ultima = escrita
        if (ultima < 0) return null
        val objetivo = ahoraNs - RETRASO_MEDIDOR_NS
        for (paso in 0 until ANILLO) {
            val m = anillo[(ultima - paso).mod(ANILLO)]
            if (m.cuandoNs == 0L) return null
            if (m.cuandoNs <= objetivo) {
                // Mas de medio segundo sin audio nuevo: ya no esta sonando.
                return if (objetivo - m.cuandoNs > 500_000_000L) null else m
            }
        }
        return null
    }

    // --- Filtros ---

    /** Coeficientes normalizados [b0, b1, b2, a1, a2] de un filtro de pico. */
    fun pico(sr: Double, hz: Double, db: Double, q: Double = Q_BANDA): DoubleArray {
        val a = 10.0.pow(db / 40.0)
        val w = 2.0 * PI * hz / sr
        val alfa = sin(w) / (2.0 * q)
        val c = cos(w)
        return normalizar(1.0 + alfa * a, -2.0 * c, 1.0 - alfa * a, 1.0 + alfa / a, -2.0 * c, 1.0 - alfa / a)
    }

    /** Estante de graves (pendiente S = 1). */
    fun estanteGraves(sr: Double, hz: Double, db: Double): DoubleArray {
        val a = 10.0.pow(db / 40.0)
        val w = 2.0 * PI * hz / sr
        val c = cos(w)
        val alfa = sin(w) / 2.0 * sqrt(2.0)
        val r = 2.0 * sqrt(a) * alfa
        return normalizar(
            a * ((a + 1) - (a - 1) * c + r),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - r),
            (a + 1) + (a - 1) * c + r,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - r,
        )
    }

    /** Estante de agudos (pendiente S = 1). */
    fun estanteAgudos(sr: Double, hz: Double, db: Double): DoubleArray {
        val a = 10.0.pow(db / 40.0)
        val w = 2.0 * PI * hz / sr
        val c = cos(w)
        val alfa = sin(w) / 2.0 * sqrt(2.0)
        val r = 2.0 * sqrt(a) * alfa
        return normalizar(
            a * ((a + 1) + (a - 1) * c + r),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - r),
            (a + 1) - (a - 1) * c + r,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - r,
        )
    }

    /** Pasabanda de ganancia 0 dB en el centro, para el analizador. */
    fun pasabanda(sr: Double, hz: Double, q: Double = Q_BANDA): DoubleArray {
        val w = 2.0 * PI * hz / sr
        val alfa = sin(w) / (2.0 * q)
        val c = cos(w)
        return normalizar(alfa, 0.0, -alfa, 1.0 + alfa, -2.0 * c, 1.0 - alfa)
    }

    private fun normalizar(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    /** Cuanto sube o baja un filtro a [hz], en dB. */
    fun magnitudDb(c: DoubleArray, sr: Double, hz: Double): Double {
        val w = 2.0 * PI * hz / sr
        val cw = cos(w)
        val sw = sin(w)
        val c2w = cos(2 * w)
        val s2w = sin(2 * w)
        val numRe = c[0] + c[1] * cw + c[2] * c2w
        val numIm = -(c[1] * sw + c[2] * s2w)
        val denRe = 1.0 + c[3] * cw + c[4] * c2w
        val denIm = -(c[3] * sw + c[4] * s2w)
        val num = numRe * numRe + numIm * numIm
        val den = denRe * denRe + denIm * denIm
        return 10.0 * log10(num / den)
    }

    /**
     * Los filtros de un ajuste, en el orden en que se aplican: graves, las 10
     * bandas y agudos. Null donde la ganancia es 0 (no se calcula).
     */
    fun filtros(a: AjusteMesa, sr: Double): Array<DoubleArray?> {
        val lista = arrayOfNulls<DoubleArray>(BANDAS + 2)
        val limite = sr / 2.0 * 0.95
        if (abs(a.graves) >= UMBRAL_DB) lista[0] = estanteGraves(sr, GRAVES_HZ, a.graves.toDouble())
        for (i in 0 until BANDAS) {
            val db = a.bandas.getOrNull(i) ?: 0f
            if (abs(db) >= UMBRAL_DB && FRECUENCIAS[i] < limite) lista[i + 1] = pico(sr, FRECUENCIAS[i], db.toDouble())
        }
        if (abs(a.agudos) >= UMBRAL_DB) lista[BANDAS + 1] = estanteAgudos(sr, AGUDOS_HZ, a.agudos.toDouble())
        return lista
    }

    /** La curva total de un ajuste a [hz] (ganancia incluida), en dB. Para dibujarla. */
    fun respuestaDb(a: AjusteMesa, hz: Double, filtros: Array<DoubleArray?>, sr: Double = 48_000.0): Double {
        var total = a.ganancia.toDouble()
        for (c in filtros) if (c != null) total += magnitudDb(c, sr, hz)
        return total
    }

    /** Por debajo de esto una banda cuenta como 0 dB y no se calcula. */
    const val UMBRAL_DB = 0.05f

    /** Lineal a dBFS, con suelo en -90. */
    fun aDb(lineal: Float): Float = if (lineal <= 0.0000316f) -90f else (20.0 * log10(lineal.toDouble())).toFloat()
}
