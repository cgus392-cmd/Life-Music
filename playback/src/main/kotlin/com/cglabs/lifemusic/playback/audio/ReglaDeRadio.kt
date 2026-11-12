package com.cglabs.lifemusic.playback.audio

import kotlin.math.roundToLong

/**
 * 2.1.x · El Auto como la radio (prueba.21, aprobado por CG el 11-11).
 *
 * Sale de la investigación de docs/versiones/2.1.0/automix-v2/investigacion.md.
 * - **Radio profesional y Mixxx («Full Intro + Outro»):** se superpone solo lo que sobra. Eso es la cola de la
 *   saliente, que ya se apaga, y la intro de la entrante, que todavía no canta. La mezcla dura **lo menor de las
 *   dos** y termina justo donde se acaba la música de la saliente. La entrante arranca desde su comienzo: **nunca se
 *   salta la intro**.
 * - **Kim et al., ISMIR 2020:** el 86 % de los DJ estira el tempo menos del 5 %. Se estira solo por debajo de ese
 *   tope, con las dos rejillas seguras y con una mezcla de al menos 4 compases para que se note el beat. Por debajo
 *   del 1,5 % el cambio no se oye ni en una mezcla corta.
 * - **Perfiles por el material:** la app no tiene el género de la canción, porque YouTube no lo da. Por eso se mira
 *   cómo suena. Con golpe claro (reguetón, electrónica, pop bailable) se puede mezclar al golpe. Sin golpe claro
 *   (baladas y el vallenato o la salsa de tempo suelto) va un fundido limpio y corto, como la radio.
 *
 * Las mezclas largas por frases (4, 8 o 16 compases) quedan solo en el Mixed Editor, cuando la persona las elige.
 */
object ReglaDeRadio {

    /** Sin marcas de las dos (falta el análisis): un fundido corto. La radio usa un segundo; uno musical pide algo más. */
    const val SIN_MARCAS_MS = 4_000L

    /** Lo mínimo que dura la mezcla, aunque la saliente termine fuerte y la entrante cante desde el primer segundo. */
    const val MINIMO_MS = 2_500L

    /** Tope con golpe claro: lo que dura una frase de 4 compases a 80 BPM. */
    const val TOPE_RITMICO_MS = 12_000L

    /** Tope sin golpe claro: dos voces largas encima se ensucian. */
    const val TOPE_SUAVE_MS = 8_000L

    /** Las confianzas del detector rondan 0,90–0,97 en canciones con golpe y bajan de 0,6 sin él (registros del 09-11). */
    const val CONFIANZA_RITMICA = 0.8f

    /** Tope de estiramiento (Kim et al.: el 86 % de los ajustes de tempo de los DJ es menor al 5 %). */
    const val ESTIRAR_HASTA = 0.05f

    /** Por debajo de esto el estiramiento no se oye, así que vale aunque la mezcla sea corta. */
    const val ESTIRAR_SIEMPRE_HASTA = 0.015f

    /** Golpes mínimos de mezcla para que valga la pena estirar: 4 compases. */
    const val GOLPES_PARA_ESTIRAR = 16

    enum class Perfil {
        /** Golpe claro en las dos: se puede mezclar al golpe. */
        RITMICO,

        /** Una de las dos sin golpe claro, o sin analizar: fundido limpio. */
        SUAVE,
    }

    fun perfil(confianzaSaliente: Float, confianzaEntrante: Float?): Perfil =
        if (confianzaSaliente >= CONFIANZA_RITMICA && (confianzaEntrante ?: 0f) >= CONFIANZA_RITMICA) Perfil.RITMICO
        else Perfil.SUAVE

    /**
     * La cola de la saliente: desde que deja de sonar con fuerza hasta que se acaba la música.
     * - Da 0 si termina fuerte.
     * - Da null si no se conoce dónde termina la música (falta el análisis de la cola).
     */
    fun cola(finDeMusicaMs: Long?, empiezaElOutroMs: Long?): Long? {
        val fin = finDeMusicaMs?.takeIf { it > 0 } ?: return null
        val outro = empiezaElOutroMs?.takeIf { it in 1 until fin } ?: return 0L
        return fin - outro
    }

    /**
     * La intro de la entrante: desde que se oye hasta que entra con fuerza.
     * - Da 0 si arranca fuerte.
     * - Da null si la entrante no está analizada.
     */
    fun intro(analizada: Boolean, empiezaASonarMs: Long?, entraConFuerzaMs: Long?): Long? {
        if (!analizada) return null
        val desde = empiezaASonarMs?.takeIf { it > 0 } ?: 0L
        val fuerte = entraConFuerzaMs?.takeIf { it > desde } ?: return 0L
        return fuerte - desde
    }

    /** Cuánto se superponen: lo menor entre la cola y la intro, con su mínimo y su tope. */
    fun superposicion(colaMs: Long?, introMs: Long?, perfil: Perfil): Long {
        if (colaMs == null || introMs == null) return SIN_MARCAS_MS
        val tope = if (perfil == Perfil.RITMICO) TOPE_RITMICO_MS else TOPE_SUAVE_MS
        return minOf(colaMs, introMs).coerceIn(MINIMO_MS, tope)
    }

    /**
     * Dónde arranca la mezcla para que termine donde se acaba la música de la saliente.
     *
     * Con rejilla se ajusta al compás más cercano. Si el ajuste deja menos del mínimo, se toma el compás anterior.
     */
    fun arranque(finDeMusicaMs: Long, superposicionMs: Long, primerGolpeMs: Long, golpeMs: Double?): Long {
        val crudo = finDeMusicaMs - superposicionMs
        if (golpeMs == null || golpeMs <= 0.0) return crudo
        val compas = golpeMs * 4
        val k = ((crudo - primerGolpeMs) / compas).roundToLong()
        var t = (primerGolpeMs + k * compas).roundToLong()
        if (finDeMusicaMs - t < MINIMO_MS) t = (t - compas).roundToLong()
        return t
    }

    /** Si se estira la entrante al tempo de la saliente (y se mezcla al golpe). */
    fun alGolpe(desvio: Float, perfil: Perfil, superposicionMs: Long, golpeMs: Double): Boolean {
        if (perfil != Perfil.RITMICO || desvio > ESTIRAR_HASTA) return false
        if (desvio <= ESTIRAR_SIEMPRE_HASTA) return true
        // Un 5 % de holgura: el ajuste al compás puede recortar unos milisegundos.
        return superposicionMs >= GOLPES_PARA_ESTIRAR * golpeMs * 0.95
    }

    /** El estilo que elige el Auto. */
    fun estilo(perfil: Perfil, alGolpe: Boolean): EstiloTransicion = when {
        alGolpe -> EstiloTransicion.BLEND
        // Con golpe y sin estirar: los graves cambian de mano con el filtro, que tapa el choque de los golpes.
        perfil == Perfil.RITMICO -> EstiloTransicion.FILTRO
        else -> EstiloTransicion.PLANO
    }
}
