/*
 * Life Music — sonido envolvente.
 * Copyright (C) 2026 CG LABS. GPL-3.0, como el resto del proyecto.
 */
package com.cglabs.lifemusic.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Sonido envolvente de Life Music: tres etapas propias, cada una con su
 * control, dentro de la cadena de ExoPlayer para que ninguna capa de efectos
 * del fabricante la pise.
 *
 *  1. **Amplitud** (mid/side): ensancha la imagen estereo subiendo el «lado» y
 *     compensando la ganancia para que no recorte.
 *  2. **Crossfeed** de auriculares: cada oido recibe un poco del canal contrario,
 *     retrasado unos 0,35 ms (la diferencia interaural real) y con los agudos
 *     recortados, como llegaria desde un altavoz al oido lejano. Quita la
 *     sensacion de «cabeza partida» y la fatiga en sesiones largas.
 *  3. **Sala**: seis reflexiones tempranas cruzadas y una cola corta de
 *     reverberacion Schroeder (cuatro peines con amortiguacion + dos pasa-todo
 *     por canal), con tres tamanos. Es lo que de verdad «envuelve»; las dos
 *     etapas anteriores solo colocan.
 *
 * Todo O(1) por muestra: sin FFT ni convolucion. No es Dolby ni pretende
 * serlo: es ingenieria de manual, medida en SurroundTest con tonos e impulsos.
 *
 * Solo estereo de 16 bits. Otro formato responde [AudioProcessor.AudioFormat.NOT_SET]
 * en vez de lanzar, por la misma razon que el filtro de transicion: una
 * excepcion aqui mata el renderer entero y la cancion no suena.
 */
class SurroundAudioProcessor : BaseAudioProcessor() {

    /** Tamano de la sala: cambia los tiempos de las reflexiones y la cola. */
    enum class Sala(val rt60Segundos: Float, val escalaReflejos: Float) {
        PEQUENA(0.45f, 0.6f),
        MEDIA(0.9f, 1.0f),
        GRANDE(1.6f, 1.5f),
    }

    @Volatile private var targetWidth = 0f
    @Volatile private var targetCrossfeed = 0f
    @Volatile private var targetRoom = 0f
    @Volatile private var targetSala = Sala.MEDIA

    private var currentWidth = 0f
    private var currentCrossfeed = 0f
    private var currentRoom = 0f
    private var currentSala = Sala.MEDIA

    private var sampleRate = 0

    // Crossfeed: una linea de retardo corta por canal y un pasa-bajos de un polo.
    private var cfDelayL = FloatArray(0)
    private var cfDelayR = FloatArray(0)
    private var cfPos = 0
    private var cfLpL = 0f
    private var cfLpR = 0f
    private var cfLpCoef = 0f

    // Sala: linea de retardo comun para las reflexiones tempranas (por canal).
    private var erL = FloatArray(0)
    private var erR = FloatArray(0)
    private var erPos = 0
    private var erMask = 0
    private val erTapsL = IntArray(3)
    private val erTapsR = IntArray(3)

    // Cola: peines y pasa-todo por canal.
    private val combL = Array(COMBS) { FloatArray(0) }
    private val combR = Array(COMBS) { FloatArray(0) }
    private val combPos = IntArray(COMBS)
    private val combLen = IntArray(COMBS)
    private val combLenR = IntArray(COMBS)
    private val combPosR = IntArray(COMBS)
    private val combGain = FloatArray(COMBS)
    private val combLpL = FloatArray(COMBS)
    private val combLpR = FloatArray(COMBS)
    private val apL = Array(ALLPASSES) { FloatArray(0) }
    private val apR = Array(ALLPASSES) { FloatArray(0) }
    private val apPosL = IntArray(ALLPASSES)
    private val apPosR = IntArray(ALLPASSES)
    private val apLen = IntArray(ALLPASSES)

    /**
     * Ajusta las tres etapas. Todo en 0..1; cero apaga la etapa. Los cambios
     * entran deslizados por bloque, asi que mover un control mientras suena no
     * chasquea.
     */
    fun setParams(width: Float, crossfeed: Float, room: Float, sala: Sala) {
        targetWidth = width.coerceIn(0f, 1f)
        targetCrossfeed = crossfeed.coerceIn(0f, 1f)
        targetRoom = room.coerceIn(0f, 1f)
        targetSala = sala
    }

    fun off() = setParams(0f, 0f, 0f, targetSala)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        currentWidth = targetWidth
        currentCrossfeed = targetCrossfeed
        currentRoom = targetRoom
        // Antes de reservar: allocate() calcula los coeficientes con la sala actual.
        currentSala = targetSala
        allocate()
        return inputAudioFormat
    }

    override fun onFlush() {
        clearState()
        currentWidth = targetWidth
        currentCrossfeed = targetCrossfeed
        currentRoom = targetRoom
        if (currentSala != targetSala) {
            currentSala = targetSala
            if (sampleRate > 0) updateRoomCoefficients()
        }
    }

    override fun onReset() {
        targetWidth = 0f; targetCrossfeed = 0f; targetRoom = 0f
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * BYTES_PER_FRAME)

        val tw = targetWidth; val tc = targetCrossfeed; val tr = targetRoom
        // Apagado y ya asentado: el buffer pasa tal cual, bit a bit. Es la rama
        // por la que sale quien no quiere el efecto: no cuesta nada.
        val parked = tw == 0f && tc == 0f && tr == 0f &&
            currentWidth < SETTLED && currentCrossfeed < SETTLED && currentRoom < SETTLED
        if (parked) {
            currentWidth = 0f; currentCrossfeed = 0f; currentRoom = 0f
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        if (targetSala != currentSala) {
            currentSala = targetSala
            updateRoomCoefficients()
        }

        var remaining = frameCount
        while (remaining > 0) {
            val block = min(remaining, GLIDE_FRAMES)
            currentWidth = glide(currentWidth, tw)
            currentCrossfeed = glide(currentCrossfeed, tc)
            currentRoom = glide(currentRoom, tr)

            val width = 1f + currentWidth * MAX_EXTRA_WIDTH
            val widthMakeup = 1f / (1f + currentWidth * WIDTH_MAKEUP)
            val cf = currentCrossfeed * MAX_CROSSFEED
            val cfMakeup = 1f / (1f + cf * 0.5f)
            val early = currentRoom * MAX_EARLY
            val tail = currentRoom * MAX_TAIL
            val dryMakeup = 1f - currentRoom * ROOM_HEADROOM

            repeat(block) {
                var l = inputBuffer.short.toFloat()
                var r = inputBuffer.short.toFloat()

                // 1. Amplitud.
                if (currentWidth > 0f) {
                    val m = (l + r) * 0.5f
                    val s = (l - r) * 0.5f * width
                    l = (m + s) * widthMakeup
                    r = (m - s) * widthMakeup
                }

                // 2. Crossfeed.
                if (currentCrossfeed > 0f) {
                    val fromR = cfDelayR[cfPos]
                    val fromL = cfDelayL[cfPos]
                    cfDelayL[cfPos] = l
                    cfDelayR[cfPos] = r
                    cfPos = (cfPos + 1) % cfDelayL.size
                    cfLpL += cfLpCoef * (fromR - cfLpL)
                    cfLpR += cfLpCoef * (fromL - cfLpR)
                    l = (l + cfLpL * cf) * cfMakeup
                    r = (r + cfLpR * cf) * cfMakeup
                }

                // 3. Sala.
                if (currentRoom > 0f) {
                    erL[erPos] = l
                    erR[erPos] = r
                    var reflL = 0f
                    var reflR = 0f
                    for (i in 0 until 3) {
                        // Cruzadas: lo que refleja la pared izquierda llega
                        // sobre todo al oido derecho, y al reves.
                        reflL += erR[(erPos - erTapsL[i]) and erMask] * ER_GAINS[i]
                        reflR += erL[(erPos - erTapsR[i]) and erMask] * ER_GAINS[i]
                    }
                    erPos = (erPos + 1) and erMask

                    val mono = (l + r) * 0.5f
                    val tailL = reverb(mono, combL, combPos, combLen, combLpL, apL, apPosL)
                    val tailR = reverb(mono, combR, combPosR, combLenR, combLpR, apR, apPosR)

                    l = l * dryMakeup + reflL * early + tailL * tail
                    r = r * dryMakeup + reflR * early + tailR * tail
                }

                outputBuffer.putShort(clampToShort(l))
                outputBuffer.putShort(clampToShort(r))
            }
            remaining -= block
        }
        outputBuffer.flip()
    }

    // ---- Sala ----------------------------------------------------------------

    /** Cuatro peines en paralelo con amortiguacion, luego dos pasa-todo en serie. */
    private fun reverb(
        input: Float,
        combs: Array<FloatArray>,
        pos: IntArray,
        len: IntArray,
        lp: FloatArray,
        allpasses: Array<FloatArray>,
        apPos: IntArray,
    ): Float {
        var acc = 0f
        for (i in 0 until COMBS) {
            val buf = combs[i]
            val out = buf[pos[i]]
            // Amortiguacion: los agudos mueren antes que los graves, como en
            // una sala de verdad.
            lp[i] += DAMPING * (out - lp[i])
            buf[pos[i]] = input + lp[i] * combGain[i]
            pos[i] = (pos[i] + 1) % len[i]
            acc += out
        }
        var x = acc * (1f / COMBS)
        for (i in 0 until ALLPASSES) {
            val buf = allpasses[i]
            val delayed = buf[apPos[i]]
            val y = -x * ALLPASS_GAIN + delayed
            buf[apPos[i]] = x + delayed * ALLPASS_GAIN
            apPos[i] = (apPos[i] + 1) % apLen[i]
            x = y
        }
        return x
    }

    private fun allocate() {
        val sr = sampleRate.toFloat()
        val cfLen = (CROSSFEED_DELAY_MS * sr / 1000f).toInt().coerceAtLeast(1)
        cfDelayL = FloatArray(cfLen)
        cfDelayR = FloatArray(cfLen)
        cfLpCoef = 1f - kotlin.math.exp(-2f * Math.PI.toFloat() * CROSSFEED_LP_HZ / sr)

        // Reflexiones: potencia de dos para poder enmascarar en vez de dividir.
        var erSize = 1
        val erMax = (ER_MAX_MS * Sala.GRANDE.escalaReflejos * sr / 1000f).toInt() + 1
        while (erSize < erMax) erSize = erSize shl 1
        erL = FloatArray(erSize)
        erR = FloatArray(erSize)
        erMask = erSize - 1

        for (i in 0 until COMBS) {
            val l = (COMB_MS[i] * sr / 1000f).toInt().coerceAtLeast(2)
            // El canal derecho un poco mas largo: descorrelaciona la cola y la
            // hace ancha en vez de un eco mono en el centro.
            val r = ((COMB_MS[i] + COMB_STEREO_OFFSET_MS) * sr / 1000f).toInt().coerceAtLeast(2)
            combLen[i] = l; combLenR[i] = r
            combL[i] = FloatArray(l); combR[i] = FloatArray(r)
        }
        for (i in 0 until ALLPASSES) {
            apLen[i] = (ALLPASS_MS[i] * sr / 1000f).toInt().coerceAtLeast(2)
            apL[i] = FloatArray(apLen[i]); apR[i] = FloatArray(apLen[i])
        }
        clearState()
        updateRoomCoefficients()
    }

    private fun updateRoomCoefficients() {
        val sr = sampleRate.toFloat()
        val sala = currentSala
        for (i in 0 until 3) {
            erTapsL[i] = (ER_MS_L[i] * sala.escalaReflejos * sr / 1000f).toInt().coerceIn(1, erMask)
            erTapsR[i] = (ER_MS_R[i] * sala.escalaReflejos * sr / 1000f).toInt().coerceIn(1, erMask)
        }
        // Ganancia de cada peine a partir del RT60 pedido: -60 dB tras rt60 s.
        for (i in 0 until COMBS) {
            val delaySeg = combLen[i] / sr
            combGain[i] = 10f.pow(-3f * delaySeg / sala.rt60Segundos)
        }
    }

    private fun clearState() {
        cfDelayL.fill(0f); cfDelayR.fill(0f); cfPos = 0; cfLpL = 0f; cfLpR = 0f
        erL.fill(0f); erR.fill(0f); erPos = 0
        for (i in 0 until COMBS) {
            combL[i].fill(0f); combR[i].fill(0f); combPos[i] = 0; combPosR[i] = 0
            combLpL[i] = 0f; combLpR[i] = 0f
        }
        for (i in 0 until ALLPASSES) { apL[i].fill(0f); apR[i].fill(0f); apPosL[i] = 0; apPosR[i] = 0 }
    }

    private fun glide(current: Float, target: Float): Float {
        val next = current + (target - current) * GLIDE_FACTOR
        return if (abs(next - target) < SETTLED) target else next
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    companion object {
        private const val BYTES_PER_FRAME = 4 // estereo, 16 bits

        /** Muestras por bloque de deslizamiento (~5 ms a 48 kHz). */
        private const val GLIDE_FRAMES = 256
        private const val GLIDE_FACTOR = 0.15f
        private const val SETTLED = 0.002f

        /** Amplitud al maximo: el lado se dobla. Mas suena a fase invertida. */
        private const val MAX_EXTRA_WIDTH = 1.0f
        private const val WIDTH_MAKEUP = 0.35f

        /** Diferencia interaural real, no un eco: 0,35 ms. */
        private const val CROSSFEED_DELAY_MS = 0.35f
        /** El oido lejano oye la cabeza como un pasa-bajos de ~700 Hz. */
        private const val CROSSFEED_LP_HZ = 700f
        private const val MAX_CROSSFEED = 0.5f

        /** Reflexiones tempranas (ms, sala media) y sus ganancias. */
        private val ER_MS_L = floatArrayOf(11f, 19f, 29f)
        private val ER_MS_R = floatArrayOf(13f, 23f, 31f)
        private val ER_GAINS = floatArrayOf(0.5f, 0.4f, 0.3f)
        private const val ER_MAX_MS = 31f
        private const val MAX_EARLY = 0.32f

        /** Schroeder: cuatro peines, dos pasa-todo. Retardos clasicos. */
        private const val COMBS = 4
        private val COMB_MS = floatArrayOf(29.7f, 37.1f, 41.1f, 43.7f)
        private const val COMB_STEREO_OFFSET_MS = 0.6f
        private const val DAMPING = 0.35f
        private const val ALLPASSES = 2
        private val ALLPASS_MS = floatArrayOf(5.0f, 1.7f)
        private const val ALLPASS_GAIN = 0.7f
        private const val MAX_TAIL = 0.28f
        /** Al meter sala, el directo baja un poco para que la suma no recorte. */
        private const val ROOM_HEADROOM = 0.15f
    }
}
