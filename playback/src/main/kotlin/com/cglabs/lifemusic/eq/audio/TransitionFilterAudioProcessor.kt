/*
 * Portado de BitChord (https://github.com/kushagrasinghx/BitChord),
 * app/src/main/java/com/music/bitchord/playback/TransitionFilterProcessor.kt.
 *
 * Copyright (C) 2026 Kushagra Singh (BitChord, implementacion original)
 * Copyright (C) 2026 CG LABS (adaptacion para Life Music)
 *
 * BitChord se distribuye bajo la GNU General Public License, version 3 o
 * posterior: la misma licencia que Life Music. Este fichero conserva el aviso
 * de copyright original, como exigen las secciones 4 y 5 de la GPL-3.0.
 *
 * Este programa se distribuye con la esperanza de que sea util, pero SIN
 * NINGUNA GARANTIA; ni siquiera la garantia implicita de COMERCIABILIDAD o
 * IDONEIDAD PARA UN PROPOSITO PARTICULAR. Vease la GNU General Public License.
 */

package com.cglabs.lifemusic.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan

/**
 * El filtro por el que pasa un plato durante una transicion de Automix: un paso
 * bajo que puede cerrarse sobre la pista saliente, y un paso alto que puede
 * levantar los graves de un lado de la mezcla.
 *
 * ## Por que existe
 *
 * Un fundido de ganancias, por bien igualado que este, no arregla las dos cosas
 * que hacen que una mezcla suene de aficionado:
 *
 *  - **Dos lineas de bajo a la vez.** Por debajo de unos 200 Hz hay poco sitio;
 *    dos bombos y dos bajos ocupandolo suenan a barro. Toda mesa de DJ trae un
 *    corte de graves para esto: los graves los tiene UNA pista a la vez, y cambian
 *    de mano una vez, en un beat que eligio el planificador.
 *  - **Dos tempos incompatibles a la vez.** Si no se pueden alinear los beats, los
 *    transitorios chocan. Cerrar un paso bajo sobre la saliente la mete detras de
 *    la entrante en vez de dejarlas pelear. Por eso el barrido de filtro es la
 *    transicion estandar para un cambio de tempo.
 *
 * Sustituye al low-shelf de -10 dB que traia el proyecto de origen: aquel
 * atenuaba, este corta a 24 dB por octava y ademas puede cerrar el paso bajo.
 *
 * ## El filtro
 *
 * Filtro de estado variable con integrador trapezoidal (TPT), dos secciones de
 * segundo orden en cascada = Butterworth de 24 dB/oct. Elegido sobre el Chamberlin
 * clasico porque la forma trapezoidal es estable en cualquier corte hasta Nyquist,
 * mientras que Chamberlin solo se porta bien por debajo de un sexto de la
 * frecuencia de muestreo: un paso bajo aparcado en 20 kHz esta fuera de eso.
 *
 * `tan` se evalua una vez por sub-bloque, no por muestra, y cuando los dos cortes
 * estan aparcados el procesador degenera en una copia de buffer: una transicion
 * que no pide filtro no cuesta nada.
 *
 * ## Deslizamiento
 *
 * Los cortes son objetivos, no valores. El bucle del fundido los reapunta cada
 * paso, y saltar un filtro a golpes se oye como ruido de cremallera, asi que el
 * corte real persigue al objetivo geometricamente en sub-bloques de
 * [GLIDE_FRAMES] muestras. Geometrico porque el corte se percibe en logaritmo: un
 * deslizamiento lineal desde 20 kHz se pasaria casi entero inaudible y luego se
 * precipitaria por la ultima octava.
 */
@UnstableApi
class TransitionFilterAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetLowPassHz: Float = OPEN_HZ

    @Volatile
    private var targetHighPassHz: Float = OFF_HZ

    private var channelCount = 0
    private var sampleRate = 0

    private var currentLowPassHz = OPEN_HZ
    private var currentHighPassHz = OFF_HZ

    /** Dos estados de integrador por seccion, por canal. */
    private var lowState = FloatArray(0)
    private var highState = FloatArray(0)

    private val lowA1 = FloatArray(STAGES)
    private val lowA2 = FloatArray(STAGES)
    private val lowA3 = FloatArray(STAGES)
    private val highA1 = FloatArray(STAGES)
    private val highA2 = FloatArray(STAGES)
    private val highA3 = FloatArray(STAGES)
    private val highK = FloatArray(STAGES)

    /**
     * Apunta el filtro. [lowPassHz] en o por encima de [OPEN_HZ] y [highPassHz] en o
     * por debajo de [OFF_HZ] significan «sin filtrar», que es el estado al que
     * vuelve entre transiciones.
     */
    fun setCutoffs(lowPassHz: Float, highPassHz: Float) {
        targetLowPassHz = lowPassHz.coerceIn(MIN_HZ, OPEN_HZ)
        targetHighPassHz = highPassHz.coerceIn(OFF_HZ, MAX_HIGH_PASS_HZ)
    }

    /** Aparca los dos filtros. Deslizado, no de golpe: ver la documentacion de la clase. */
    fun open() = setCutoffs(OPEN_HZ, OFF_HZ)

    /**
     * Solo PCM de 16 bits, como el resto de la cadena de Life Music. Si llega otro
     * formato responde [AudioProcessor.AudioFormat.NOT_SET] en vez de lanzar:
     * `DefaultAudioSink` configura todos los procesadores de la cadena tanto si el
     * efecto esta activo como si no, y una excepcion en cualquiera de ellos mata el
     * renderer entero. NOT_SET significa «inactivo para este formato» y la cadena
     * lo rodea.
     */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        lowState = FloatArray(channelCount * STAGES * 2)
        highState = FloatArray(channelCount * STAGES * 2)
        currentLowPassHz = targetLowPassHz
        currentHighPassHz = targetHighPassHz
        return inputAudioFormat
    }

    override fun onFlush() {
        lowState.fill(0f)
        highState.fill(0f)
        // De golpe, no deslizado: un flush es un seek o una fuente nueva, y no hay
        // senal continua con la que un deslizamiento pueda ser continuo.
        currentLowPassHz = targetLowPassHz
        currentHighPassHz = targetHighPassHz
    }

    override fun onReset() {
        targetLowPassHz = OPEN_HZ
        targetHighPassHz = OFF_HZ
        lowState = FloatArray(0)
        highState = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = BYTES_PER_SAMPLE * channelCount
        if (bytesPerFrame == 0) return
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        val targetLow = targetLowPassHz
        val targetHigh = targetHighPassHz
        // Aparcado en los dos extremos Y ya asentado ahi: no hay nada que hacer salvo
        // pasar el buffer tal cual. La mitad de «ya asentado» importa: una transicion
        // que acaba de terminar sigue deslizandose hacia abierto, y quitarle el
        // filtro de debajo en mitad del deslizamiento es el clic que este existe
        // para evitar.
        val parked = targetLow >= OPEN_HZ && targetHigh <= OFF_HZ &&
            currentLowPassHz >= OPEN_HZ - SETTLED_HZ && currentHighPassHz <= OFF_HZ + SETTLED_HZ
        if (parked) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            currentLowPassHz = glide(currentLowPassHz, targetLow)
            currentHighPassHz = glide(currentHighPassHz, targetHigh)
            val lowOn = currentLowPassHz < OPEN_HZ - SETTLED_HZ
            val highOn = currentHighPassHz > OFF_HZ + SETTLED_HZ
            if (lowOn) updateLowCoefficients()
            if (highOn) updateHighCoefficients()

            repeat(block) {
                for (channel in 0 until channelCount) {
                    var sample = inputBuffer.short.toFloat()
                    if (lowOn) sample = lowPass(channel, sample)
                    if (highOn) sample = highPass(channel, sample)
                    outputBuffer.putShort(clampToShort(sample))
                }
            }
            remaining -= block
        }
        outputBuffer.flip()
    }

    // ---- Filtro ------------------------------------------------------------

    private fun glide(current: Float, target: Float): Float {
        val from = ln(current.coerceAtLeast(MIN_HZ))
        val to = ln(target.coerceAtLeast(MIN_HZ))
        return exp(from + (to - from) * GLIDE_RATE)
    }

    /** El corte mas alto que la transformada bilineal representa sin irse a infinito. */
    private fun usableCutoff(hz: Float): Float =
        hz.coerceIn(MIN_HZ, sampleRate * MAX_CUTOFF_FRACTION)

    private fun updateLowCoefficients() {
        val g = tan(Math.PI * usableCutoff(currentLowPassHz) / sampleRate).toFloat()
        for (stage in 0 until STAGES) {
            val k = 1f / BUTTERWORTH_Q[stage]
            val a1 = 1f / (1f + g * (g + k))
            lowA1[stage] = a1
            lowA2[stage] = g * a1
            lowA3[stage] = g * (g * a1)
        }
    }

    private fun updateHighCoefficients() {
        val g = tan(Math.PI * usableCutoff(currentHighPassHz) / sampleRate).toFloat()
        for (stage in 0 until STAGES) {
            val k = 1f / BUTTERWORTH_Q[stage]
            val a1 = 1f / (1f + g * (g + k))
            highA1[stage] = a1
            highA2[stage] = g * a1
            highA3[stage] = g * (g * a1)
            highK[stage] = k
        }
    }

    private fun lowPass(channel: Int, input: Float): Float {
        var value = input
        for (stage in 0 until STAGES) {
            val i = (channel * STAGES + stage) * 2
            val ic1 = lowState[i]
            val ic2 = lowState[i + 1]
            val v3 = value - ic2
            val v1 = lowA1[stage] * ic1 + lowA2[stage] * v3
            val v2 = ic2 + lowA2[stage] * ic1 + lowA3[stage] * v3
            lowState[i] = 2f * v1 - ic1
            lowState[i + 1] = 2f * v2 - ic2
            value = v2
        }
        return value
    }

    private fun highPass(channel: Int, input: Float): Float {
        var value = input
        for (stage in 0 until STAGES) {
            val i = (channel * STAGES + stage) * 2
            val ic1 = highState[i]
            val ic2 = highState[i + 1]
            val v3 = value - ic2
            val v1 = highA1[stage] * ic1 + highA2[stage] * v3
            val v2 = ic2 + highA2[stage] * ic1 + highA3[stage] * v3
            highState[i] = 2f * v1 - ic1
            highState[i + 1] = 2f * v2 - ic2
            value -= highK[stage] * v1 + v2
        }
        return value
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    companion object {
        /** Un paso bajo en o por encima de esto no hace nada audible: cuenta como apagado. */
        const val OPEN_HZ = 20_000f

        /** Un paso alto en o por debajo de esto no hace nada audible: cuenta como apagado. */
        const val OFF_HZ = 20f

        /** Nada musical quiere los graves levantados por encima de esto, y una errata no deberia poder. */
        const val MAX_HIGH_PASS_HZ = 2_000f

        private const val MIN_HZ = 10f
        private const val BYTES_PER_SAMPLE = 2

        /** Dos secciones de segundo orden en cascada: 24 dB/octava, la pendiente habitual de un filtro de DJ. */
        private const val STAGES = 2

        /** Q de cada seccion para una respuesta de cuarto orden maximamente plana (Butterworth). */
        private val BUTTERWORTH_Q = floatArrayOf(0.54120f, 1.30656f)

        /** Muestras entre actualizaciones de coeficientes. ~1,5 ms a 44,1 kHz. */
        private const val GLIDE_FRAMES = 64

        /** Fraccion de deslizamiento por sub-bloque. Constante de tiempo de ~30 ms. */
        private const val GLIDE_RATE = 0.05f

        /** A que distancia de un valor aparcado cuenta como aparcado, para que un deslizamiento termine. */
        private const val SETTLED_HZ = 1f

        /** Mantiene `tan` lejos de su polo en Nyquist. */
        private const val MAX_CUTOFF_FRACTION = 0.45f
    }
}
