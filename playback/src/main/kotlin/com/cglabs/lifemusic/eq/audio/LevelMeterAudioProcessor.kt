/*
 * Life Music — medidor de nivel para el modo ambiente.
 * Copyright (C) 2026 CG LABS. GPL-3.0, como el resto del proyecto.
 */
package com.cglabs.lifemusic.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Mide lo que suena, sin tocarlo: el buffer sale bit a bit igual que entra.
 * Cada bloque calcula dos cifras en 0..1, ya con envolvente (ataque
 * instantaneo, caida suave) para que la interfaz no tenga que filtrar:
 *
 *  - [nivel]: energia de toda la banda. Es «cuanto suena».
 *  - [graves]: energia por debajo de ~120 Hz, con caida rapida. Es el bombo:
 *    sube de golpe y baja antes del siguiente. Con esto el fondo del modo
 *    ambiente late con la musica en vez de girar a reloj.
 *
 * Apagado ([activo] = false) no lee ni una muestra: la rama de quien no esta
 * en el modo ambiente no cuesta nada. Solo PCM de 16 bits; otro formato,
 * NOT_SET y la cadena lo rodea (misma razon que en los demas procesadores).
 */
class LevelMeterAudioProcessor : BaseAudioProcessor() {

    /** Ultimas medidas, 0..1. Las lee la interfaz a su ritmo. */
    @Volatile var nivel: Float = 0f
        private set
    @Volatile var graves: Float = 0f
        private set

    /** Solo mide mientras alguien mira. */
    @Volatile var activo: Boolean = false
        set(value) {
            field = value
            if (!value) { nivel = 0f; graves = 0f; envNivel = 0f; envGraves = 0f; lp = 0f }
        }

    /** Avisa tras cada bloque medido, desde el hilo de audio. Debe ser barato. */
    @Volatile var alMedir: ((nivel: Float, graves: Float) -> Unit)? = null

    private var channelCount = 0
    private var sampleRate = 0
    private var lpCoef = 0f
    private var lp = 0f
    private var envNivel = 0f
    private var envGraves = 0f
    private var caidaNivel = 0f
    private var caidaGraves = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount < 1) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        val sr = sampleRate.toFloat()
        lpCoef = 1f - exp(-2f * Math.PI.toFloat() * GRAVES_HZ / sr)
        // Caida por bloque, calculada para que el tiempo sea el mismo a cualquier
        // frecuencia de muestreo.
        val bloquesPorSegundo = sr / BLOQUE
        caidaNivel = exp(-1f / (CAIDA_NIVEL_S * bloquesPorSegundo))
        caidaGraves = exp(-1f / (CAIDA_GRAVES_S * bloquesPorSegundo))
        return inputAudioFormat
    }

    override fun onFlush() { lp = 0f }

    override fun onReset() { lp = 0f; envNivel = 0f; envGraves = 0f; nivel = 0f; graves = 0f }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = 2 * channelCount
        val frameCount = if (bytesPerFrame == 0) 0 else inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        if (activo) {
            // Se mide sobre una vista: el buffer real se copia entero mas abajo.
            val vista = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
            var restantes = frameCount
            while (restantes > 0) {
                val bloque = minOf(restantes, BLOQUE)
                var sumaCuadrados = 0f
                var sumaGraves = 0f
                repeat(bloque) {
                    var mono = 0f
                    for (c in 0 until channelCount) mono += vista.short.toFloat()
                    mono /= channelCount
                    sumaCuadrados += mono * mono
                    lp += lpCoef * (mono - lp)
                    sumaGraves += lp * lp
                }
                val rms = sqrt(sumaCuadrados / bloque) / ESCALA
                val rmsGraves = sqrt(sumaGraves / bloque) / ESCALA_GRAVES
                // Envolvente: sube de golpe, baja despacio.
                envNivel = if (rms > envNivel) rms else envNivel * caidaNivel
                envGraves = if (rmsGraves > envGraves) rmsGraves else envGraves * caidaGraves
                nivel = envNivel.coerceIn(0f, 1f)
                graves = envGraves.coerceIn(0f, 1f)
                alMedir?.invoke(nivel, graves)
                restantes -= bloque
            }
        }

        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    companion object {
        /** Muestras por medida: ~43 ms a 48 kHz, unas 23 medidas por segundo. */
        private const val BLOQUE = 2_048
        private const val GRAVES_HZ = 120f
        /** Un RMS de un cuarto de escala ya cuenta como «a tope»: la musica comercial vive ahi. */
        private const val ESCALA = 8_192f
        /** Los graves filtrados tienen menos energia; se normalizan aparte. */
        private const val ESCALA_GRAVES = 5_000f
        private const val CAIDA_NIVEL_S = 0.35f
        private const val CAIDA_GRAVES_S = 0.12f
    }
}
