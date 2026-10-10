/*
 * Life Music — procesador de la mesa de sonido del carro.
 * Copyright (C) 2026 CG LABS. GPL-3.0, como el resto del proyecto.
 */
package com.cglabs.lifemusic.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * La mesa de sonido del carro (ficha docs/versiones/carro-0.3.0/ecualizador.md):
 * ganancia, graves, 10 bandas y agudos, y al final un limitador.
 *
 * La regla que pidio CG: subir NO baja el volumen. No se resta nada para hacer
 * sitio, como hace el ecualizador de Android; lo que pasaria del maximo digital
 * lo frena el limitador, que solo toca los picos (ataque al instante, suelta en
 * 120 ms), y detras un recorte suave por si algo se escapa. Asi un refuerzo de
 * graves suena a mas graves y no a una cancion mas bajita.
 *
 * Hecho para radios de 1 GB: doble precision sin crear objetos por muestra,
 * los filtros conservan su estado al cambiar (nada de chasquidos al arrastrar),
 * una banda en 0 dB no se calcula, y el analizador solo corre con la pantalla
 * abierta. Prender o apagar la mesa es un fundido de 20 ms.
 *
 * [habilitado] = false (el telefono): devuelve NOT_SET y la cadena la salta
 * entera, sin costo.
 */
class MesaDeSonidoAudioProcessor(private val habilitado: Boolean) : BaseAudioProcessor() {

    private var sr = 0
    private var canales = 0

    // Filtros de la mesa: graves, 10 bandas, agudos. Coeficientes [b0 b1 b2 a1 a2]
    // y estado por canal [x1 x2 y1 y2] (izquierdo y derecho seguidos).
    private val total = MesaDeSonido.BANDAS + 2
    private val coef = DoubleArray(total * 5)
    private val usar = BooleanArray(total)
    private val estado = DoubleArray(total * 8)
    private var versionVista = -1

    // Ganancia general y cuanto de la mesa se oye (0 apagada, 1 prendida), suavizadas.
    private var gananciaObjetivo = 1.0
    private var ganancia = 1.0
    private var mezclaObjetivo = 0.0
    private var mezcla = 0.0
    private var suave = 0.0

    // Limitador.
    private var gLim = 1.0
    private var soltar = 0.0

    // Analizador: 10 pasabandas sobre la mezcla mono.
    private val coefAn = DoubleArray(MesaDeSonido.BANDAS * 5)
    private val usarAn = BooleanArray(MesaDeSonido.BANDAS)
    private val estadoAn = DoubleArray(MesaDeSonido.BANDAS * 4)
    private val energia = DoubleArray(MesaDeSonido.BANDAS)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!habilitado || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sr = inputAudioFormat.sampleRate
        canales = inputAudioFormat.channelCount
        val fs = sr.toDouble()
        suave = 1.0 - exp(-1.0 / (SUAVIZADO_S * fs))
        soltar = 1.0 - exp(-1.0 / (SOLTAR_S * fs))
        val limite = fs / 2.0 * 0.95
        for (i in 0 until MesaDeSonido.BANDAS) {
            usarAn[i] = MesaDeSonido.FRECUENCIAS[i] < limite
            if (usarAn[i]) MesaDeSonido.pasabanda(fs, MesaDeSonido.FRECUENCIAS[i]).copyInto(coefAn, i * 5)
        }
        versionVista = -1
        limpiar()
        return inputAudioFormat
    }

    override fun onFlush() = limpiar()

    override fun onReset() {
        limpiar()
        sr = 0
        canales = 0
    }

    private fun limpiar() {
        estado.fill(0.0)
        estadoAn.fill(0.0)
        gLim = 1.0
    }

    /** Si cambio el ajuste, recalcula los filtros (conservando su estado) y los objetivos. */
    private fun actualizar() {
        val v = MesaDeSonido.version
        if (v == versionVista) return
        versionVista = v
        val a = MesaDeSonido.ajuste
        gananciaObjetivo = Math.pow(10.0, a.ganancia / 20.0)
        mezclaObjetivo = if (a.activo) 1.0 else 0.0
        val filtros = MesaDeSonido.filtros(a, sr.toDouble())
        for (f in 0 until total) {
            val c = filtros[f]
            if (c == null) {
                usar[f] = false
            } else {
                // Un filtro que vuelve despues de estar en 0 empieza limpio.
                if (!usar[f]) estado.fill(0.0, f * 8, f * 8 + 8)
                c.copyInto(coef, f * 5)
                usar[f] = true
            }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPorCuadro = 2 * canales
        val cuadros = if (bytesPorCuadro == 0) 0 else inputBuffer.remaining() / bytesPorCuadro
        if (cuadros == 0) return
        val salida = replaceOutputBuffer(cuadros * bytesPorCuadro)
        inputBuffer.order(ByteOrder.nativeOrder())
        actualizar()
        val medir = MesaDeSonido.analizar

        // Apagada del todo y sin nadie mirando: pasa tal cual.
        if (mezcla == 0.0 && mezclaObjetivo == 0.0 && !medir) {
            salida.put(inputBuffer)
            salida.flip()
            return
        }

        var picoI = 0.0
        var picoD = 0.0
        var cuadI = 0.0
        var cuadD = 0.0
        var minLim = 1.0
        if (medir) energia.fill(0.0)

        repeat(cuadros) {
            val secoI = inputBuffer.getShort() / 32768.0
            val secoD = if (canales == 2) inputBuffer.getShort() / 32768.0 else secoI

            var yI: Double
            var yD: Double
            if (mezcla == 0.0 && mezclaObjetivo == 0.0) {
                yI = secoI
                yD = secoD
            } else {
                ganancia += (gananciaObjetivo - ganancia) * suave
                mezcla += (mezclaObjetivo - mezcla) * suave
                if (abs(mezcla - mezclaObjetivo) < 1e-4) mezcla = mezclaObjetivo

                var i = secoI * ganancia
                var d = secoD * ganancia
                for (f in 0 until total) {
                    if (!usar[f]) continue
                    val k = f * 5
                    val s = f * 8
                    val b0 = coef[k]
                    val b1 = coef[k + 1]
                    val b2 = coef[k + 2]
                    val a1 = coef[k + 3]
                    val a2 = coef[k + 4]
                    val oi = b0 * i + b1 * estado[s] + b2 * estado[s + 1] - a1 * estado[s + 2] - a2 * estado[s + 3]
                    estado[s + 1] = estado[s]; estado[s] = i
                    estado[s + 3] = estado[s + 2]; estado[s + 2] = oi
                    val od = b0 * d + b1 * estado[s + 4] + b2 * estado[s + 5] - a1 * estado[s + 6] - a2 * estado[s + 7]
                    estado[s + 5] = estado[s + 4]; estado[s + 4] = d
                    estado[s + 7] = estado[s + 6]; estado[s + 6] = od
                    i = oi
                    d = od
                }

                // Limitador: lo que pasaria del techo se frena al instante y se suelta despacio.
                val pico = max(abs(i), abs(d))
                val necesario = if (pico > TECHO) TECHO / pico else 1.0
                gLim = if (necesario < gLim) necesario else gLim + (necesario - gLim) * soltar
                if (gLim < minLim) minLim = gLim
                i = recorteSuave(i * gLim)
                d = recorteSuave(d * gLim)

                // Prender o apagar se funde: seco y procesado mezclados.
                yI = secoI + (i - secoI) * mezcla
                yD = secoD + (d - secoD) * mezcla
            }

            salida.putShort(aMuestra(yI))
            if (canales == 2) salida.putShort(aMuestra(yD))

            if (medir) {
                val ai = abs(yI)
                val ad = abs(yD)
                if (ai > picoI) picoI = ai
                if (ad > picoD) picoD = ad
                cuadI += yI * yI
                cuadD += yD * yD
                val mono = (yI + yD) * 0.5
                for (b in 0 until MesaDeSonido.BANDAS) {
                    if (!usarAn[b]) continue
                    val k = b * 5
                    val s = b * 4
                    val o = coefAn[k] * mono + coefAn[k + 1] * estadoAn[s] + coefAn[k + 2] * estadoAn[s + 1] -
                        coefAn[k + 3] * estadoAn[s + 2] - coefAn[k + 4] * estadoAn[s + 3]
                    estadoAn[s + 1] = estadoAn[s]; estadoAn[s] = mono
                    estadoAn[s + 3] = estadoAn[s + 2]; estadoAn[s + 2] = o
                    energia[b] += o * o
                }
            }
        }
        salida.flip()

        if (medir) {
            val m = MesaDeSonido.casillaNueva()
            m.picoIzq = picoI.toFloat()
            m.picoDer = picoD.toFloat()
            m.rmsIzq = sqrt(cuadI / cuadros).toFloat()
            m.rmsDer = sqrt(cuadD / cuadros).toFloat()
            m.limitadorDb = MesaDeSonido.aDb(minLim.toFloat())
            for (b in 0 until MesaDeSonido.BANDAS) {
                // Raiz de 2: un seno a tope en la banda marca 0 dBFS.
                m.bandasDb[b] = MesaDeSonido.aDb((sqrt(2.0 * energia[b] / cuadros)).toFloat())
            }
            m.cuandoNs = System.nanoTime()
            MesaDeSonido.publicar()
        }
    }

    private fun aMuestra(x: Double): Short = (x * 32767.0).toInt().coerceIn(-32768, 32767).toShort()

    companion object {
        /** El techo del limitador: -0,5 dBFS. */
        internal const val TECHO = 0.944
        private const val SUAVIZADO_S = 0.02
        private const val SOLTAR_S = 0.12
        private const val RODILLA = 0.95

        /**
         * Por si algo se escapa del limitador: lineal hasta la rodilla (por encima
         * del techo, asi lo limitado pasa intacto) y de ahi se dobla suave hacia 1
         * en vez de cortar en seco.
         */
        internal fun recorteSuave(x: Double): Double {
            val a = abs(x)
            if (a <= RODILLA) return x
            val doblado = min(RODILLA + (1.0 - RODILLA) * tanh((a - RODILLA) / (1.0 - RODILLA)), 1.0)
            return if (x < 0) -doblado else doblado
        }
    }
}
