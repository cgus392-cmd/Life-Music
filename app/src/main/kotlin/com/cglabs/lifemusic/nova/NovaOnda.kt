package com.cglabs.lifemusic.nova

import kotlin.math.sqrt

/**
 * La onda de la voz de Nova, de verdad: la energia del audio que acaba de
 * generar el motor, en tramos de [TRAMO_MS]. Con esto late la estrella y se
 * mueven las barras de Life Line; nada es una animacion inventada.
 *
 * Sin Android, para poder probarlo: recibe los bytes del WAV.
 */
object NovaOnda {
    const val TRAMO_MS = 50

    /**
     * Energia de cada tramo de un WAV PCM de 16 bits, de 0 a 1 (1 = el tramo mas
     * fuerte de la frase). Vacio si el archivo no es un WAV que se pueda leer:
     * entonces los subtitulos salen sin onda, pero salen.
     */
    fun envolvente(bytes: ByteArray): FloatArray {
        if (bytes.size < 44 || ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return FloatArray(0)
        var pos = 12
        var canales = 1
        var tasa = 0
        var bits = 0
        var datos = -1
        var largo = 0
        while (pos + 8 <= bytes.size) {
            val id = ascii(bytes, pos)
            val tam = le32(bytes, pos + 4)
            if (id == "fmt " && pos + 24 <= bytes.size) {
                canales = le16(bytes, pos + 10).coerceAtLeast(1)
                tasa = le32(bytes, pos + 12)
                bits = le16(bytes, pos + 22)
            }
            if (id == "data") {
                datos = pos + 8
                val resto = bytes.size - datos
                // Hay motores que escriben el tamaño en 0 o en -1 (WAV «en vivo»): se lee hasta el final.
                largo = if (tam in 1..resto) tam else resto
                break
            }
            if (tam < 0) break
            pos += 8 + tam + (tam and 1)
        }
        if (datos < 0 || bits != 16 || tasa <= 0) return FloatArray(0)

        val porTramo = (tasa.toLong() * TRAMO_MS / 1000 * canales).toInt().coerceAtLeast(1)
        val muestras = largo / 2
        val tramos = muestras / porTramo
        if (tramos <= 0) return FloatArray(0)
        val salida = FloatArray(tramos)
        for (t in 0 until tramos) {
            var suma = 0.0
            for (i in 0 until porTramo) {
                val k = datos + (t * porTramo + i) * 2
                val valor = ((bytes[k].toInt() and 0xFF) or (bytes[k + 1].toInt() shl 8)).toShort() / 32768.0
                suma += valor * valor
            }
            salida[t] = sqrt(suma / porTramo).toFloat()
        }
        val maximo = salida.maxOrNull() ?: 0f
        if (maximo > 0f) for (i in salida.indices) salida[i] = (salida[i] / maximo).coerceIn(0f, 1f)
        return salida
    }

    /** El nivel en un momento de la frase, o 0 fuera de ella. */
    fun nivel(onda: FloatArray, transcurridoMs: Long): Float {
        if (onda.isEmpty() || transcurridoMs < 0) return 0f
        val i = (transcurridoMs / TRAMO_MS).toInt()
        return if (i in onda.indices) onda[i] else 0f
    }

    private fun ascii(b: ByteArray, desde: Int) = String(b, desde, 4, Charsets.US_ASCII)
    private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
}
