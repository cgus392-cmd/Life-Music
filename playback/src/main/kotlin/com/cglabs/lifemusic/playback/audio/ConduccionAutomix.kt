/*
 * Las curvas de conduccion de filtro estan adaptadas de BitChord
 * (https://github.com/kushagrasinghx/BitChord), CrossfadeController.kt:
 * rideFilterSweep, rideBassSwap, entryHighPass, blendExitLowPass y sus
 * constantes.
 *
 * Copyright (C) 2026 Kushagra Singh (BitChord, implementacion original)
 * Copyright (C) 2026 CG LABS (adaptacion para Life Music)
 *
 * GNU General Public License, version 3 o posterior. Se conserva el aviso de
 * copyright original como exigen las secciones 4 y 5 de la GPL-3.0.
 */

package com.cglabs.lifemusic.playback.audio

import com.cglabs.lifemusic.eq.audio.TransitionFilterAudioProcessor.Companion.OFF_HZ
import com.cglabs.lifemusic.eq.audio.TransitionFilterAudioProcessor.Companion.OPEN_HZ
import kotlin.math.pow

/**
 * Cuanto se atreve Automix con un par de pistas, decidido a partir del analisis
 * guardado y de sus confianzas. Antes solo habia dos niveles —al beat, o fundido
 * plano— y todo lo que no era perfecto caia al segundo. El ASISTIDO es lo nuevo:
 * ajusta a frase y filtra, pero no estira el tempo.
 */
enum class NivelTransicion {
    /** Las dos rejillas de beat son fiables y los tempos caben en el margen de estiramiento. */
    AL_BEAT,

    /** Se permite anclar a frase y conducir filtros; no se permite estirar el tempo. */
    ASISTIDO,

    /** La evidencia no da para nada mas que un fundido equal-power. */
    PLANO,
}

/** Como se ejecuta la transicion. Lo elige el material, o el usuario desde Ajustes. */
enum class EstiloTransicion {
    /** Fundido equal-power sin filtrar. Lo unico que permite el nivel PLANO. */
    PLANO,

    /** Mezcla alineada al beat con cambio de mano de los graves. Para tempos iguales o casi. */
    BLEND,

    /** Barrido de filtro. Para tempos demasiado lejanos para mezclar en plano, o cuando falta analisis de una pista. */
    FILTRO,

    /**
     * Sin superposicion: la saliente se cierra con el filtro hasta callarse y
     * solo entonces arranca la entrante, por su principio y entera. Lo pidio
     * CG para tropical: ahi la intro de la siguiente es cancion, no relleno
     * sobre el que mezclar. Nunca lo elige el material; siempre el usuario.
     */
    CIERRE,
}

/** Los cuatro cortes que el bucle del fundido reapunta en cada paso. */
data class CortesDeFiltro(
    val salienteLowPassHz: Float,
    val salienteHighPassHz: Float,
    val entranteLowPassHz: Float,
    val entranteHighPassHz: Float,
) {
    companion object {
        val ABIERTOS = CortesDeFiltro(OPEN_HZ, OFF_HZ, OPEN_HZ, OFF_HZ)
    }
}

/**
 * Calcula los cortes de filtro de los dos platos para un estilo y un progreso
 * del fundido (0..1). No toca PCM: devuelve numeros, y el servicio los apunta.
 *
 * La curva de ganancia es la misma pareja equal-power para todos los estilos;
 * esto es lo que hace que suenen distintos entre si.
 */
object ConduccionAutomix {

    fun cortes(estilo: EstiloTransicion, progreso: Float): CortesDeFiltro = when (estilo) {
        EstiloTransicion.PLANO -> CortesDeFiltro.ABIERTOS
        EstiloTransicion.FILTRO -> barridoDeFiltro(progreso)
        EstiloTransicion.BLEND -> cambioDeGraves(progreso)
        EstiloTransicion.CIERRE -> cierre(progreso)
    }

    /**
     * El cierre: mismo barrido que [barridoDeFiltro] pero sin suelo que lo pare
     * —baja hasta [SUELO_CIERRE_HZ], donde ya solo queda el bombo— porque aqui
     * la saliente no tiene que seguir sonando debajo de nadie: tiene que irse.
     * La entrante no se toca: esta en pausa esperando su turno.
     */
    private fun cierre(progreso: Float): CortesDeFiltro {
        val corte = deslizar(ENTRADA_FILTRO_HZ, SUELO_CIERRE_HZ, progreso.toDouble().pow(FORMA_BARRIDO))
        return CortesDeFiltro(
            salienteLowPassHz = corte.toFloat(),
            salienteHighPassHz = OFF_HZ,
            entranteLowPassHz = OPEN_HZ,
            entranteHighPassHz = OFF_HZ,
        )
    }

    /**
     * Ganancia de la saliente durante un cierre. Entera hasta
     * [CIERRE_GANANCIA_DESDE]: la primera mitad la hace el filtro solo, que es lo
     * que se oye como «se cierra». Despues cae en coseno hasta cero, para que el
     * bombo que sobrevive al filtro no acabe cortado a tajo sino apagandose.
     */
    fun gananciaDeCierre(progreso: Float): Float {
        val t = ((progreso - CIERRE_GANANCIA_DESDE) / (1.0 - CIERRE_GANANCIA_DESDE)).coerceIn(0.0, 1.0)
        return kotlin.math.cos(t * Math.PI / 2.0).toFloat()
    }

    /**
     * Lo que hace un DJ cuando dos tempos no casan: entregar el medio en vez de
     * doblarlo. La saliente empieza a perder el agudo en cuanto arranca la mezcla
     * y la entrante entra pasada por alto —platos y presencia, sin cuerpo de voz—
     * abriendose mientras la saliente se oscurece. Ocupan bandas complementarias
     * por el medio del fundido y nunca compiten por el rango donde vive una voz.
     *
     * [FORMA_BARRIDO] adelanta la curva: el agudo de la saliente se ha ido en la
     * primera decima del fundido, no pasado el medio. Lo que evita que eso vacie
     * la pista que se va es [SUELO_FILTRO_HZ]: el barrido se asienta en una cama
     * de 300 Hz y se queda ahi.
     */
    private fun barridoDeFiltro(progreso: Float): CortesDeFiltro {
        val corte = deslizar(ENTRADA_FILTRO_HZ, SUELO_FILTRO_HZ, progreso.toDouble().pow(FORMA_BARRIDO))
        return CortesDeFiltro(
            salienteLowPassHz = corte.toFloat(),
            salienteHighPassHz = OFF_HZ,
            entranteLowPassHz = OPEN_HZ,
            entranteHighPassHz = pasoAltoDeEntrada(progreso, ENTRADA_PASO_ALTO_HZ, ENTRADA_ABIERTA_EN),
        )
    }

    /**
     * Entrega los graves de una pista a la otra, una vez, en el beat que eligio el
     * planificador. Por debajo de [CAMBIO_GRAVES_HZ] hay exactamente una pista
     * presente en cada instante: la entrante llega con los graves levantados y los
     * toma mientras a la saliente se le levantan a su vez. Rampa de
     * [ANCHO_CAMBIO_GRAVES] del fundido y no un interruptor, porque un filtro de
     * 24 dB/oct apareciendo en un buffer es un transitorio por si mismo.
     *
     * El medio se trata mucho mas suave que en el barrido, pero ya no se deja
     * solo: la entrante entra con el cuerpo levantado desde una esquina mas baja y
     * por menos tiempo, y la saliente pierde el agudo en la segunda mitad, donde ya
     * suena bajo y el cambio se lee como que se aleja, no como un efecto.
     */
    private fun cambioDeGraves(progreso: Float): CortesDeFiltro {
        val entrega = ((progreso - FRACCION_CAMBIO_GRAVES) / ANCHO_CAMBIO_GRAVES * 0.5 + 0.5).coerceIn(0.0, 1.0)
        val entradaHP = maxOf(
            corteDeGraves(1.0 - entrega),
            pasoAltoDeEntrada(progreso, BLEND_ENTRADA_PASO_ALTO_HZ, BLEND_ENTRADA_ABIERTA_EN),
        )
        return CortesDeFiltro(
            salienteLowPassHz = salidaDeBlend(progreso),
            salienteHighPassHz = corteDeGraves(entrega),
            entranteLowPassHz = OPEN_HZ,
            entranteHighPassHz = entradaHP,
        )
    }

    /**
     * Donde esta el paso alto de la entrante en [progreso]: baja de [topeHz] a
     * nada en [abiertaEn] del fundido, asi la pista esta entera mucho antes de
     * quedarse sola. [FORMA_ENTRADA] es por lo que el descenso no es lineal: la
     * mitad inferior del rango es subgrave donde nadie oye un filtro, asi que la
     * curva gasta el recorrido donde de verdad esta una voz.
     */
    private fun pasoAltoDeEntrada(progreso: Float, topeHz: Double, abiertaEn: Double): Float {
        val restante = (1.0 - progreso / abiertaEn).coerceIn(0.0, 1.0)
        return deslizar(OFF_HZ.toDouble(), topeHz, restante.pow(FORMA_ENTRADA)).toFloat()
    }

    /**
     * El paso bajo de la saliente durante un blend: abierto hasta
     * [BLEND_SALIDA_DESDE], luego cerrandose hasta [BLEND_SALIDA_PASO_BAJO_HZ] al
     * final. Deliberadamente poco profundo: lo justo para quitarle el aire y la
     * sibilancia a una voz que se va, sin acercarse al suelo del barrido, que
     * contradiria la razon por la que se eligio este estilo.
     */
    private fun salidaDeBlend(progreso: Float): Float {
        val cantidad = ((progreso - BLEND_SALIDA_DESDE) / (1.0 - BLEND_SALIDA_DESDE)).coerceIn(0.0, 1.0)
        return deslizar(OPEN_HZ.toDouble(), BLEND_SALIDA_PASO_BAJO_HZ, cantidad).toFloat()
    }

    /** [cantidad] 0 deja los graves en paz; 1 los levanta del todo. */
    private fun corteDeGraves(cantidad: Double): Float =
        deslizar(OFF_HZ.toDouble(), CAMBIO_GRAVES_HZ, cantidad).toFloat()

    /**
     * Interpolacion geometrica entre dos cortes: [cantidad] 0 da [desde], 1 da
     * [hasta]. Geometrica y no lineal porque el tono es logaritmico: un corte que
     * se mueve en pasos iguales de Hz suena a que se precipita por el fondo del
     * rango y repta por arriba.
     */
    fun deslizar(desde: Double, hasta: Double, cantidad: Double): Double =
        desde * (hasta / desde).pow(cantidad.coerceIn(0.0, 1.0))

    // ---- Barrido de filtro ------------------------------------------------

    /** Donde arranca el paso bajo de la saliente: ya por debajo de los platos, para que se note desde el primer paso. */
    const val ENTRADA_FILTRO_HZ = 7_000.0

    /** Donde se asienta: una cama de graves y cuerpo, no silencio. Mas abajo vacia la pista. */
    const val SUELO_FILTRO_HZ = 300.0

    /** Exponente del progreso. Menor que 1 adelanta el barrido: el agudo se va en la primera decima. */
    const val FORMA_BARRIDO = 0.75

    // ---- Cierre y arranque ------------------------------------------------

    /** Hasta donde baja el cierre: por debajo queda el bombo y poco mas. */
    const val SUELO_CIERRE_HZ = 80.0

    /** Fraccion del cierre a partir de la cual la ganancia acompana al filtro hasta cero. */
    const val CIERRE_GANANCIA_DESDE = 0.5

    /** Paso alto de entrada en el barrido: platos y presencia, sin cuerpo. */
    const val ENTRADA_PASO_ALTO_HZ = 1_200.0

    /** Fraccion del fundido en la que la entrante ya esta entera. */
    const val ENTRADA_ABIERTA_EN = 0.6

    /** Exponente del descenso del paso alto. Gasta el recorrido donde hay voz, no en el subgrave. */
    const val FORMA_ENTRADA = 0.35

    // ---- Cambio de graves (blend) -----------------------------------------

    /** Por debajo de esto, los graves los tiene una sola pista. */
    const val CAMBIO_GRAVES_HZ = 200.0

    /** Fraccion del fundido en que cambian de mano. 0,7: la entrante ya esta bien presente. */
    const val FRACCION_CAMBIO_GRAVES = 0.7

    /** Ancho de la rampa del cambio, en fraccion del fundido. */
    const val ANCHO_CAMBIO_GRAVES = 0.10

    /** Paso alto de entrada en un blend: mas bajo y mas breve que en el barrido, porque las pistas SI deben sonar juntas. */
    const val BLEND_ENTRADA_PASO_ALTO_HZ = 520.0
    const val BLEND_ENTRADA_ABIERTA_EN = 0.45

    /** Desde cuando la saliente pierde el agudo en un blend, y hasta donde. */
    const val BLEND_SALIDA_DESDE = 0.3
    const val BLEND_SALIDA_PASO_BAJO_HZ = 2_200.0
}
