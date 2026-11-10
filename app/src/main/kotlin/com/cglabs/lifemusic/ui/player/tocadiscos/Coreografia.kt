package com.cglabs.lifemusic.ui.player.tocadiscos

import kotlin.math.pow

/**
 * 2.1.0 · El tocadiscos del Mixed Editor: los tiempos y las curvas de la hoja de movimiento que CG aprobó el 09-11
 * (Brand/maquetas/2026-11-09-tocadiscos/HOJA-DE-MOVIMIENTO.md), pasados tal cual de la maqueta en movimiento
 * (Video/tocadiscos/tocadiscos.js).
 *
 * Todo va en GOLPES. El 9 es el de la transición: el punto donde entra la canción nueva. Las distancias van en
 * «dp de la maqueta», con la carátula de 360; quien dibuja las escala al tamaño real de la carátula.
 *
 * No usa Android, para poder probarlo en la JVM.
 */
object Coreografia {
    /** El golpe de la transición: aquí entra la canción nueva. */
    const val TRANSICION = 9f

    /** El último golpe: la funda nueva ya cubrió su disco y volvió a ser la carátula. */
    const val FIN = 16f

    /** Desde aquí la capa del tocadiscos reemplaza a la carátula, todavía quieta e idéntica a ella. */
    const val ENTRA = 0.5f

    fun lim(x: Float, a: Float = 0f, b: Float = 1f) = if (x < a) a else if (x > b) b else x
    fun tramo(b: Float, inicio: Float, duracion: Float) = lim((b - inicio) / duracion)

    /** «suave» de la hoja: 6p⁵ − 15p⁴ + 10p³. */
    fun suave(p: Float) = p * p * p * (p * (6f * p - 15f) + 10f)

    /** «sale» de la hoja: 1 − (1 − p)³. */
    fun sale(p: Float) = 1f - (1f - p).pow(3)

    /** «entra» de la hoja: p³. */
    fun entra(p: Float) = p * p * p

    fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p

    /** Fotogramas clave en pares planos (golpe, valor), con curva suave entre cada par. */
    fun claves(k: FloatArray, b: Float): Float {
        if (b <= k[0]) return k[1]
        var i = 0
        while (i + 3 < k.size) {
            if (b < k[i + 2]) return mix(k[i + 1], k[i + 3], suave((b - k[i]) / (k[i + 2] - k[i])))
            i += 2
        }
        return k[k.size - 1]
    }

    // ── En tarjeta: posición en x respecto al centro de la carátula ─────────────────────────────────────────
    /** A asoma (la funda se corre y el disco asoma), sale al plato, en el golpe vuelve a su funda y se van. */
    val FUNDA_A = floatArrayOf(0f, 0f, 2f, 0f, 3.4f, -70f, 3.6f, -70f, 5f, -320f, 9.4f, -320f, 10.6f, -150f, 11.4f, -150f, 12.4f, -600f)
    val VINILO_A = floatArrayOf(0f, 0f, 2f, 0f, 3.4f, 80f, 3.6f, 80f, 5f, 0f, 9.6f, 0f, 11f, -150f, 11.4f, -150f, 12.4f, -600f)

    /** B: la funda entra en el golpe, su disco sale al plato y al final la funda vuelve al centro y lo cubre. */
    val FUNDA_B = floatArrayOf(0f, 470f, 8.6f, 470f, 9.6f, 320f, 14.2f, 320f, 15.4f, 0f)
    val VINILO_B = floatArrayOf(0f, 470f, 8.6f, 470f, 9.6f, 320f, 10f, 320f, 11.4f, 0f, 16f, 0f)

    /** En el plato el vinilo va delante de su funda; al entrar o salir de ella, detrás. */
    fun enPlatoA(b: Float) = b >= 3.5f && b < 10.8f
    fun enPlatoB(b: Float) = b >= 10f && b < 14.4f

    /** La funda nueva aparece un poco antes del golpe. */
    fun hayFundaB(b: Float) = b > 8.4f

    /** Sombra de la funda y textura de cartón: nada en reposo, para que el relevo con la carátula no se note. */
    fun adornos(b: Float) = suave(tramo(b, 0.6f, 1.2f)) * (1f - suave(tramo(b, 15.2f, 0.7f)))

    class Brazo(val visible: Float, val angulo: Float, val alto: Float)

    /** El brazo en tarjeta: entra con el disco en el plato, baja la aguja, sube en el golpe y vuelve con el nuevo. */
    fun brazoEnTarjeta(b: Float): Brazo {
        val en = suave(tramo(b, 5f, 1f)) * (1f - suave(tramo(b, 9f, 0.8f))) +
            suave(tramo(b, 11.2f, 1f)) * (1f - suave(tramo(b, 13.6f, 0.9f)))
        val alto = 1f - (
            suave(tramo(b, 6f, 0.4f)) * (1f - suave(tramo(b, 8.7f, 0.3f))) +
                suave(tramo(b, 12.2f, 0.4f)) * (1f - suave(tramo(b, 13.3f, 0.3f)))
            )
        val visible = suave(tramo(b, 4.4f, 0.8f)) * (1f - suave(tramo(b, 14.6f, 0.8f)))
        return Brazo(visible, mix(-0.9f, 0.12f, en), alto)
    }

    // ── Apple Music (versión 2): la carátula a sangre se queda; el reproductor se recoge y el disco sale abajo ──
    /** Cuánto se recoge el reproductor: 0 = completo; 1 = solo nombre, barra y controles, más abajo. */
    fun recoge(b: Float) = suave(tramo(b, 1.4f, 1.2f)) * (1f - suave(tramo(b, 14.6f, 1.2f)))

    /** El volumen baja y se apaga un poco después que la fila de abajo, y vuelve antes que ella. */
    fun escondeVolumen(b: Float) = suave(tramo(b, 1.6f, 1.0f)) * (1f - suave(tramo(b, 14.9f, 1.0f)))
    fun escondeFila(b: Float) = suave(tramo(b, 1.4f, 0.9f)) * (1f - suave(tramo(b, 15.1f, 0.9f)))

    /** Enfoque de cámara: 0 = A nítida, 1 = B nítida. El fondo borroso se mezcla igual. */
    fun cambia(b: Float) = suave(tramo(b, 8.8f, 1.8f))
    fun alfaCaratulaA(b: Float) = 1f - suave(tramo(b, 9.4f, 1.6f))
    fun alfaCaratulaB(b: Float) = suave(tramo(b, 9.0f, 1.4f))

    /**
     * El desvanecido de la carátula (fracciones de su alto: opaca hasta, 40 % en, transparente en). En reposo es el del
     * estilo (0,75 · 0,92 · 1); recogido se acorta, para que el disco que sale por abajo se vea entero.
     */
    fun desvanecido(recoge: Float) = floatArrayOf(mix(0.75f, 0.48f, recoge), mix(0.92f, 0.60f, recoge), mix(1f, 0.70f, recoge))

    /** Cuánto salió cada vinilo por el borde de abajo: 0 = escondido detrás de la carátula, 1 = afuera. */
    fun saleA(b: Float) = sale(tramo(b, 2f, 2.6f))
    fun saleB(b: Float) = sale(tramo(b, 10f, 2.2f)) - suave(tramo(b, 14f, 1.4f))

    /** El vinilo A se va de lado mientras frena, en fracción del ancho. */
    fun ladoA(b: Float) = mix(0f, -0.9f, entra(tramo(b, 9.4f, 1.4f)))
    fun hayViniloAEnApple(b: Float) = b < 11f
    fun hayViniloBEnApple(b: Float) = b > 9.8f

    /** El brazo en Apple Music: entra con cada disco y gira hacia él a la vez que aparece. */
    fun brazoEnApple(b: Float): Brazo {
        val enA = suave(tramo(b, 4.6f, 1f)) * (1f - suave(tramo(b, 9f, 0.7f)))
        val enB = suave(tramo(b, 11.6f, 1f)) * (1f - suave(tramo(b, 13.6f, 0.8f)))
        val visible = maxOf(enA, enB)
        val alto = 1f - (
            suave(tramo(b, 5.6f, 0.4f)) * (1f - suave(tramo(b, 8.6f, 0.3f))) +
                suave(tramo(b, 12.6f, 0.4f)) * (1f - suave(tramo(b, 13.4f, 0.3f)))
            )
        return Brazo(visible, mix(-0.95f, 0.42f, visible), alto)
    }

    // ── El giro: 33⅓ rpm reales, con arranque y frenado. El ángulo es la integral de la velocidad. ──────────
    /** 33⅓ rpm en radianes por segundo. */
    const val OMEGA = (100f / 3f) / 60f * 2f * Math.PI.toFloat()

    private const val PASOS = 240

    private fun tablaDeGiro(inicio: Float, arranque: Float, fin: Float, frenado: Float): FloatArray {
        val n = ((FIN + 1f) * PASOS).toInt()
        val tabla = FloatArray(n + 1)
        var acumulado = 0f
        for (s in 1..n) {
            val b = s.toFloat() / PASOS
            acumulado += suave(tramo(b, inicio, arranque)) * (1f - suave(tramo(b, fin, frenado))) / PASOS
            tabla[s] = acumulado
        }
        return tabla
    }

    private val GIRO_A = tablaDeGiro(3.6f, 1.6f, 9.2f, 1.4f)
    private val GIRO_B = tablaDeGiro(10f, 1.6f, 14.4f, 1.4f)

    private fun leer(tabla: FloatArray, b: Float): Float {
        val x = lim(b * PASOS, 0f, (tabla.size - 1).toFloat())
        val i = x.toInt().coerceAtMost(tabla.size - 2)
        return mix(tabla[i], tabla[i + 1], x - i)
    }

    /** Ángulo del vinilo A en radianes. [golpeMs] es lo que dura un golpe en la canción que suena. */
    fun giroA(b: Float, golpeMs: Float) = OMEGA * golpeMs / 1000f * leer(GIRO_A, b)

    /** Ángulo del vinilo B en radianes. */
    fun giroB(b: Float, golpeMs: Float) = OMEGA * golpeMs / 1000f * leer(GIRO_B, b)

    // ── Gama baja y «Quitar animaciones»: cuadros quietos con fundidos de 400 ms ──────────────────────────────
    /**
     * Cuánto se ve de cada cuadro quieto: la carátula A, A asomando, B asomando y la carátula B.
     * Cada cambio es un fundido cruzado de 400 ms; la suma siempre es 1.
     */
    fun cuadrosQuietos(b: Float, golpeMs: Float): FloatArray {
        val f = 400f / golpeMs
        val p1 = suave(tramo(b, 2f, f))
        val p2 = suave(tramo(b, TRANSICION, f))
        val p3 = suave(tramo(b, 15f, f))
        return floatArrayOf(1f - p1, p1 * (1f - p2), p2 * (1f - p3), p3)
    }
}
