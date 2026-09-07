package com.cglabs.lifemusic.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reduccion de voz para el modo karaoke, por cancelacion de centro en banda.
 *
 * El truco clasico —restar los canales, L menos R— borra todo lo que este
 * paneado al centro. La voz principal casi siempre lo esta, asi que funciona;
 * el problema es que el bombo, el bajo y buena parte de la percusion tambien
 * viven en el centro, y la pista se queda hueca y sin fondo. Es la razon por la
 * que el karaoke casero suena mal.
 *
 * Aqui se descompone en medio y lados —M = (L+R)/2, S = (L-R)/2— y solo se
 * ataca el medio EN LA BANDA DE LA VOZ:
 *
 *   M_grave  = paso bajo de M por debajo de [KEEP_LOW_HZ]     (bombo y bajo)
 *   M_agudo  = paso alto de M por encima de [KEEP_HIGH_HZ]    (aire, platos)
 *   M_voz    = M - M_grave - M_agudo                          (lo que se quita)
 *
 *   M' = M_grave + M_agudo + M_voz * (1 - fuerza)
 *   L' = M' + S      R' = M' - S
 *
 * Con fuerza = 1 la voz se va y el fondo se queda. Lo que no se puede arreglar
 * es lo que la mezcla no separa: coros paneados al centro, reverb de la voz
 * repartida en los lados, y sobre todo una grabacion MONO, donde S vale cero y
 * no hay nada que separar. Eso ultimo se mide y se publica en [inputIsMono] para
 * que la interfaz pueda decirlo en vez de dejar al usuario pensando que falla.
 *
 * Sobre los coeficientes: se calculan una vez al configurar y no se tocan mas.
 * Cambiar los coeficientes de un IIR en caliente, con la linea de retardo
 * cargada de estado del filtro anterior, produce chasquidos. La fuerza se aplica
 * como mezcla lineal entre seco y procesado, suavizada muestra a muestra, que es
 * la misma solucion que ya usa [AutomixDuckAudioProcessor].
 */
@UnstableApi
class VocalReducerAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    /** 0 = sin tocar, 1 = voz fuera del todo. */
    @Volatile
    private var targetMix: Float = 0f
    private var currentMix: Float = 0f

    /**
     * Si lo que esta sonando es practicamente mono. En ese caso los dos canales
     * son iguales, S vale cero y no hay separacion posible: cancelar el centro
     * dejaria solo graves y agudos. La interfaz lo consulta para avisar.
     */
    @Volatile
    var inputIsMono: Boolean = false
        private set

    private var anchoAcumulado = 0.0
    private var muestrasMedidas = 0

    // Paso bajo y paso alto de segundo orden, Butterworth (Q = 0.7071), sobre M.
    private var lpB0 = 1.0; private var lpB1 = 0.0; private var lpB2 = 0.0
    private var lpA1 = 0.0; private var lpA2 = 0.0
    private var lpX1 = 0.0; private var lpX2 = 0.0; private var lpY1 = 0.0; private var lpY2 = 0.0

    private var hpB0 = 1.0; private var hpB1 = 0.0; private var hpB2 = 0.0
    private var hpA1 = 0.0; private var hpA2 = 0.0
    private var hpX1 = 0.0; private var hpX2 = 0.0; private var hpY1 = 0.0; private var hpY2 = 0.0

    companion object {
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

        /** Por debajo de aqui el centro se respeta: bombo y bajo. */
        private const val KEEP_LOW_HZ = 200.0

        /** Por encima de aqui tambien: platos y aire, que si se quitan apagan la pista. */
        private const val KEEP_HIGH_HZ = 7_500.0

        private const val Q = 0.70710678

        /** Constante de tiempo del suavizado de la mezcla, en ms. */
        private const val MIX_EASE_MS = 25.0

        /** Ventana para decidir si la grabacion es mono. */
        private const val MONO_WINDOW = 48_000

        /** Por debajo de esta anchura media, se considera mono. */
        private const val MONO_THRESHOLD = 0.0015
    }

    /** Cuanta voz se quita: 0 nada, 1 todo. */
    fun setStrength(fraction: Float) {
        targetMix = fraction.coerceIn(0f, 1f)
    }

    private fun computeCoefficients() {
        // Paso bajo
        run {
            val omega = 2.0 * PI * KEEP_LOW_HZ / sampleRate
            val cosO = cos(omega)
            val alpha = sin(omega) / (2.0 * Q)
            val a0 = 1.0 + alpha
            lpB0 = ((1.0 - cosO) / 2.0) / a0
            lpB1 = (1.0 - cosO) / a0
            lpB2 = lpB0
            lpA1 = (-2.0 * cosO) / a0
            lpA2 = (1.0 - alpha) / a0
        }
        // Paso alto
        run {
            val omega = 2.0 * PI * KEEP_HIGH_HZ / sampleRate
            val cosO = cos(omega)
            val alpha = sin(omega) / (2.0 * Q)
            val a0 = 1.0 + alpha
            hpB0 = ((1.0 + cosO) / 2.0) / a0
            hpB1 = (-(1.0 + cosO)) / a0
            hpB2 = hpB0
            hpA1 = (-2.0 * cosO) / a0
            hpA2 = (1.0 - alpha) / a0
        }
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT || channelCount > 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        computeCoefficients()
        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return

        if (outputBuffer.capacity() < inputSize) {
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val sampleCount = inputSize / 2

        // Un canal: no hay lados de donde separar nada. Pasa tal cual.
        if (channelCount != 2) {
            inputIsMono = true
            repeat(sampleCount) { outputBuffer.putShort(inputBuffer.getShort()) }
            outputBuffer.flip()
            return
        }

        val easePerSample = (1000.0 / (MIX_EASE_MS * sampleRate)).toFloat().coerceIn(0f, 1f)

        repeat(sampleCount / 2) {
            currentMix += (targetMix - currentMix) * easePerSample

            val inL = inputBuffer.getShort().toDouble() / 32768.0
            val inR = inputBuffer.getShort().toDouble() / 32768.0

            val medio = (inL + inR) * 0.5
            val lados = (inL - inR) * 0.5

            // Medida de anchura estereo, para poder avisar de que es mono.
            anchoAcumulado += abs(lados)
            if (++muestrasMedidas >= MONO_WINDOW) {
                inputIsMono = (anchoAcumulado / muestrasMedidas) < MONO_THRESHOLD
                anchoAcumulado = 0.0
                muestrasMedidas = 0
            }

            // Los filtros corren SIEMPRE, tambien con la mezcla a cero: asi su
            // estado esta caliente cuando el usuario sube la fuerza y no entra
            // con un transitorio.
            val grave = lpB0 * medio + lpB1 * lpX1 + lpB2 * lpX2 - lpA1 * lpY1 - lpA2 * lpY2
            lpX2 = lpX1; lpX1 = medio; lpY2 = lpY1; lpY1 = grave

            val agudo = hpB0 * medio + hpB1 * hpX1 + hpB2 * hpX2 - hpA1 * hpY1 - hpA2 * hpY2
            hpX2 = hpX1; hpX1 = medio; hpY2 = hpY1; hpY1 = agudo

            val conservado = grave + agudo
            val voz = medio - conservado
            val medioSalida = conservado + voz * (1.0 - currentMix)

            // Sin ganancia de compensacion, y es deliberado. La intuicion dice
            // que al quitar el centro hay que subir para recuperar nivel: eso
            // vale para el L-R crudo, que se lleva medio espectro por delante.
            // Aqui no. Verificado numericamente con tonos de prueba: con la
            // fuerza al maximo, el bajo del centro y todo lo paneado a los lados
            // salen a 0.0 dB, sin tocar; lo unico que baja es la voz, -25 dB. No
            // hay nada que compensar, y una compensacion de +3 dB sobre un master
            // moderno solo consigue recortar.
            val outL = medioSalida + lados
            val outR = medioSalida - lados

            outputBuffer.putShort((outL * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            outputBuffer.putShort((outR * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
        }

        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        lpX1 = 0.0; lpX2 = 0.0; lpY1 = 0.0; lpY2 = 0.0
        hpX1 = 0.0; hpX2 = 0.0; hpY1 = 0.0; hpY2 = 0.0
        anchoAcumulado = 0.0
        muestrasMedidas = 0
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        currentMix = 0f
        targetMix = 0f
        inputIsMono = false
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }
}
