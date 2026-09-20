package com.cglabs.lifemusic.clip

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * El tramo de audio de un clip: PCM estereo de 16 bits decodificado de un
 * fichero (el de la cache del reproductor, o la cancion local), las
 * envolventes por fotograma para que el fondo y la caratula latan aunque el
 * clip salga mudo, y, si se pide, la pista AAC lista para el muxer.
 */
class AudioDelClip private constructor(
    val pcm: ShortArray,
    val canales: Int,
    val sampleRate: Int,
) {
    /** Energia y graves por fotograma, 0..1, con la misma logica que LevelMeterAudioProcessor. */
    fun envolventes(fps: Int, totalFotogramas: Int): Pair<FloatArray, FloatArray> {
        val energia = FloatArray(totalFotogramas)
        val graves = FloatArray(totalFotogramas)
        val porFotograma = sampleRate / fps
        val lpCoef = 1f - exp(-2f * Math.PI.toFloat() * 120f / sampleRate)
        var lp = 0f
        var envN = 0f; var envG = 0f
        val caidaN = exp(-1f / (0.35f * fps)); val caidaG = exp(-1f / (0.12f * fps))
        for (f in 0 until totalFotogramas) {
            val desde = f * porFotograma
            var sc = 0f; var sg = 0f; var n = 0
            for (i in desde until minOf(desde + porFotograma, pcm.size / canales)) {
                var mono = 0f
                for (c in 0 until canales) mono += pcm[i * canales + c].toFloat()
                mono /= canales
                sc += mono * mono
                lp += lpCoef * (mono - lp)
                sg += lp * lp
                n++
            }
            if (n == 0) { energia[f] = envN; graves[f] = envG; continue }
            val rms = sqrt(sc / n) / 8_192f
            val rmsG = sqrt(sg / n) / 5_000f
            envN = if (rms > envN) rms else envN * caidaN
            envG = if (rmsG > envG) rmsG else envG * caidaG
            energia[f] = envN.coerceIn(0f, 1f)
            graves[f] = envG.coerceIn(0f, 1f)
        }
        return energia to graves
    }

    /** Codifica el tramo a AAC (128 kbit/s) en memoria. */
    fun codificarAac(): AudioCodificado {
        val formato = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, canales).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(formato, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muestras = ArrayList<Pair<ByteBuffer, MediaCodec.BufferInfo>>()
        var formatoSalida: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        val entrada = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.nativeOrder())
        for (s in pcm) entrada.putShort(s)
        entrada.flip()
        var terminadoEntrada = false
        var terminado = false
        val bytesPorMuestraFrame = 2 * canales
        while (!terminado) {
            if (!terminadoEntrada) {
                val idx = codec.dequeueInputBuffer(10_000L)
                if (idx >= 0) {
                    val buf = codec.getInputBuffer(idx)!!
                    buf.clear()
                    val n = minOf(buf.remaining(), entrada.remaining())
                    val consumidosAntes = entrada.position()
                    if (n > 0) {
                        val trozo = entrada.slice(); trozo.limit(n)
                        buf.put(trozo)
                        entrada.position(consumidosAntes + n)
                    }
                    val pts = consumidosAntes.toLong() / bytesPorMuestraFrame * 1_000_000L / sampleRate
                    if (!entrada.hasRemaining()) {
                        codec.queueInputBuffer(idx, 0, n, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        terminadoEntrada = true
                    } else {
                        codec.queueInputBuffer(idx, 0, n, pts, 0)
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, 10_000L)
            when {
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> formatoSalida = codec.outputFormat
                out >= 0 -> {
                    val buf = codec.getOutputBuffer(out)!!
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val copia = ByteBuffer.allocate(info.size)
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        copia.put(buf); copia.flip()
                        val bi = MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                        muestras += copia to bi
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) terminado = true
                }
            }
        }
        codec.stop(); codec.release()
        return AudioCodificado(formatoSalida ?: formato, muestras)
    }

    companion object {
        /**
         * Decodifica [duracionMs] a partir de [desdeMs] del primer canal de audio
         * de [fichero]. Devuelve null si no hay pista o no se pudo decodificar.
         */
        fun decodificar(fichero: File, desdeMs: Long, duracionMs: Long, cancelado: () -> Boolean): AudioDelClip? {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(fichero.absolutePath)
                var pista = -1
                var formato: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { pista = i; formato = f; break }
                }
                if (pista < 0 || formato == null) return null
                extractor.selectTrack(pista)
                extractor.seekTo(desdeMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val mime = formato.getString(MediaFormat.KEY_MIME)!!
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(formato, null, null, 0)
                codec.start()

                var sampleRate = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var canales = formato.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val salida = java.io.ByteArrayOutputStream()
                val info = MediaCodec.BufferInfo()
                val finUs = (desdeMs + duracionMs) * 1000L
                val inicioUs = desdeMs * 1000L
                var entradaAgotada = false
                var listo = false
                while (!listo) {
                    if (cancelado()) return null
                    if (!entradaAgotada) {
                        val idx = codec.dequeueInputBuffer(10_000L)
                        if (idx >= 0) {
                            val buf = codec.getInputBuffer(idx)!!
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0 || extractor.sampleTime > finUs) {
                                codec.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                entradaAgotada = true
                            } else {
                                codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val out = codec.dequeueOutputBuffer(info, 10_000L)
                    when {
                        out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            canales = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        out >= 0 -> {
                            val buf = codec.getOutputBuffer(out)!!
                            if (info.size > 0) {
                                // Solo lo que cae dentro del tramo: el seek fue al sync anterior.
                                val bytesPorFrame = 2 * canales
                                val framesBuf = info.size / bytesPorFrame
                                val ptsUs = info.presentationTimeUs
                                val saltar = if (ptsUs < inicioUs) ((inicioUs - ptsUs) * sampleRate / 1_000_000L).toInt().coerceIn(0, framesBuf) else 0
                                val desde = info.offset + saltar * bytesPorFrame
                                val hasta = info.offset + info.size
                                if (hasta > desde) {
                                    val arr = ByteArray(hasta - desde)
                                    buf.position(desde); buf.get(arr)
                                    salida.write(arr)
                                }
                            }
                            codec.releaseOutputBuffer(out, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) listo = true
                        }
                    }
                }
                val bytes = salida.toByteArray()
                val totalFrames = (duracionMs * sampleRate / 1000L).toInt()
                val limite = minOf(bytes.size / 2, totalFrames * canales)
                val pcm = ShortArray(limite)
                ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer().get(pcm, 0, limite)
                return AudioDelClip(pcm, canales, sampleRate)
            } catch (e: Exception) {
                android.util.Log.w("LifeMusicClip", "decodificar fallo", e)
                return null
            } finally {
                runCatching { codec?.stop() }; runCatching { codec?.release() }
                runCatching { extractor.release() }
            }
        }
    }
}
