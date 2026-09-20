package com.cglabs.lifemusic.clip

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Une un MP4 que solo trae video (la grabacion del modo ambiente) con una
 * pista AAC ya codificada, sin recodificar nada: copia las muestras de video
 * tal cual y escribe el audio detras. Un minuto de 1080p se une en menos de un
 * segundo. Se usa porque en una grabacion no se sabe cuanto va a durar hasta
 * que termina, y el muxer necesita todas las pistas antes de arrancar.
 */
object Remezclador {
    fun unir(video: File, audio: AudioCodificado, salida: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(video.absolutePath)
            var pista = -1
            var formato: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { pista = i; formato = f; break }
            }
            if (pista < 0 || formato == null) return false
            extractor.selectTrack(pista)

            val mx = MediaMuxer(salida.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mx
            val pistaVideo = mx.addTrack(formato)
            val pistaAudio = mx.addTrack(audio.formato)
            mx.start()

            val tamano = if (formato.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) formato.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 2 * 1024 * 1024
            val buf = ByteBuffer.allocate(maxOf(tamano, 256 * 1024))
            val info = MediaCodec.BufferInfo()
            var ultimoVideoUs = 0L
            while (true) {
                val n = extractor.readSampleData(buf, 0)
                if (n < 0) break
                info.set(0, n, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mx.writeSampleData(pistaVideo, buf, info)
                ultimoVideoUs = maxOf(ultimoVideoUs, extractor.sampleTime)
                extractor.advance()
            }
            // El audio solo hasta donde llega el video: si la grabacion se corto
            // antes, la cola de audio sobrante no entra.
            audio.muestras.forEach { (b, bi) ->
                if (bi.presentationTimeUs > ultimoVideoUs + 40_000L) return@forEach
                b.position(bi.offset); b.limit(bi.offset + bi.size)
                mx.writeSampleData(pistaAudio, b, bi)
            }
            mx.stop()
            return salida.length() > 10_000
        } catch (e: Exception) {
            android.util.Log.w("LifeMusicClip", "remezclar fallo", e)
            return false
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
