package com.cglabs.lifemusic.clip

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Una pista de audio ya codificada en AAC, lista para el muxer. */
class AudioCodificado(
    val formato: MediaFormat,
    val muestras: List<Pair<ByteBuffer, MediaCodec.BufferInfo>>,
)

/**
 * Codifica el clip a MP4 (H.264) fotograma a fotograma, fuera de pantalla.
 *
 * El codificador de video recibe los fotogramas por una superficie EGL: cada
 * Bitmap (dibujado por LienzoDeClip, o capturado de la ventana en la
 * grabacion del modo ambiente) se sube como textura y se pinta en un cuadrado
 * a pantalla completa. Se usa EGL y no `lockCanvas` porque solo asi se puede
 * fijar el tiempo de presentacion de cada fotograma
 * (eglPresentationTimeANDROID): con el lienzo directo el tiempo seria el del
 * reloj, y como la tarjeta se dibuja mucho mas rapido que el tiempo real, el
 * video saldria acelerado.
 *
 * Dos formas de usarlo: [codificar] dibuja y codifica de un tiron (tarjeta), y
 * [abrir] / [entregar] / [cerrar] reciben los fotogramas de fuera segun llegan
 * (grabacion). El contexto EGL es del hilo que llama a [abrir]: los fotogramas
 * deben entregarse desde ese mismo hilo.
 *
 * El audio, si lo hay, llega ya codificado (ver AudioDelClip) y se escribe
 * entero antes del video; el muxer ordena por tiempo. En la grabacion el audio
 * se une despues (Remezclador), porque no se sabe hasta el final cuanto dura.
 */
class CodificadorDeClip(
    private val ancho: Int,
    private val alto: Int,
    private val fps: Int,
    private val salida: File,
) {
    private lateinit var encoder: MediaCodec
    private lateinit var superficie: Surface
    private lateinit var muxer: MediaMuxer
    private var audio: AudioCodificado? = null
    private var pistaVideo = -1
    private var pistaAudio = -1
    private var muxerEnMarcha = false
    private var abierto = false
    private val info = MediaCodec.BufferInfo()

    // EGL
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var contexto: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var programa = 0
    private var textura = 0
    private var posAttr = 0
    private var texAttr = 0
    private lateinit var vertices: java.nio.FloatBuffer

    /**
     * Dibuja y codifica [totalFotogramas]. [dibujar] recibe el bitmap de
     * trabajo y el indice del fotograma. [progreso] va de 0 a 1.
     */
    fun codificar(
        totalFotogramas: Int,
        audio: AudioCodificado?,
        dibujar: (Bitmap, Int) -> Unit,
        progreso: (Float) -> Unit,
        cancelado: () -> Boolean,
    ) {
        abrir(audio)
        val bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        try {
            val nsPorFotograma = 1_000_000_000L / fps
            for (i in 0 until totalFotogramas) {
                if (cancelado()) return
                dibujar(bitmap, i)
                entregar(bitmap, i * nsPorFotograma)
                progreso(i.toFloat() / totalFotogramas)
            }
            progreso(1f)
        } finally {
            bitmap.recycle()
            cerrar()
        }
    }

    /** Prepara codificador, EGL y muxer. Con [audio], su pista se escribe entera al arrancar el muxer. */
    fun abrir(audio: AudioCodificado? = null) {
        this.audio = audio
        prepararEncoder()
        prepararEgl()
        muxer = MediaMuxer(salida.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        abierto = true
    }

    /** Un fotograma mas, con su tiempo de presentacion en nanosegundos. Mismo hilo que [abrir]. */
    fun entregar(bitmap: Bitmap, ptsNs: Long) {
        subirYPintar(bitmap)
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, ptsNs)
        EGL14.eglSwapBuffers(display, eglSurface)
        drenar(false)
    }

    /** Fin de la entrada, vacia lo pendiente y libera todo. Sin fotogramas no hay fichero valido. */
    fun cerrar() {
        if (!abierto) return
        abierto = false
        runCatching {
            encoder.signalEndOfInputStream()
            drenar(true)
        }
        liberar()
    }

    // ── Video ─────────────────────────────────────────────────────────────────

    private fun prepararEncoder() {
        val formato = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, ancho, alto).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            // 1080p a 30 fps: 8 Mbit/s es lo que Instagram acepta sin recomprimir de mas.
            setInteger(MediaFormat.KEY_BIT_RATE, if (ancho >= 1920 || alto >= 1920) 8_000_000 else 5_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(formato, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        superficie = encoder.createInputSurface()
        encoder.start()
    }

    /** Saca lo que el codificador tenga listo y lo escribe; con [hastaElFinal] espera el EOS. */
    private fun drenar(hastaElFinal: Boolean) {
        val audio = this.audio
        while (true) {
            val indice = encoder.dequeueOutputBuffer(info, if (hastaElFinal) 10_000L else 0L)
            when {
                indice == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!hastaElFinal) return
                indice == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerEnMarcha) { "el formato cambio con el muxer ya en marcha" }
                    pistaVideo = muxer.addTrack(encoder.outputFormat)
                    if (audio != null) pistaAudio = muxer.addTrack(audio.formato)
                    muxer.start()
                    muxerEnMarcha = true
                    // El audio entero primero: el muxer ordena por tiempo.
                    audio?.muestras?.forEach { (buf, bi) ->
                        buf.position(bi.offset); buf.limit(bi.offset + bi.size)
                        muxer.writeSampleData(pistaAudio, buf, bi)
                    }
                }
                indice >= 0 -> {
                    val buf = encoder.getOutputBuffer(indice) ?: continue
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerEnMarcha) {
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        muxer.writeSampleData(pistaVideo, buf, info)
                    }
                    encoder.releaseOutputBuffer(indice, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    // ── EGL: la superficie del codificador como destino de dibujo ─────────────

    private fun prepararEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "sin display EGL" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL no inicializa" }
        val atributos = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        check(EGL14.eglChooseConfig(display, atributos, 0, configs, 0, 1, n, 0) && n[0] > 0) { "sin config EGL grabable" }
        val config = configs[0]!!
        contexto = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(contexto != EGL14.EGL_NO_CONTEXT) { "sin contexto EGL" }
        eglSurface = EGL14.eglCreateWindowSurface(display, config, superficie, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "sin superficie EGL" }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, contexto)) { "eglMakeCurrent fallo" }
        prepararGl()
    }

    private fun prepararGl() {
        val vs = """
            attribute vec4 aPos; attribute vec2 aTex; varying vec2 vTex;
            void main() { gl_Position = aPos; vTex = aTex; }
        """.trimIndent()
        val fs = """
            precision mediump float; varying vec2 vTex; uniform sampler2D uTex;
            void main() { gl_FragColor = texture2D(uTex, vTex); }
        """.trimIndent()
        programa = GLES20.glCreateProgram()
        GLES20.glAttachShader(programa, compilar(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(programa, compilar(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(programa)
        posAttr = GLES20.glGetAttribLocation(programa, "aPos")
        texAttr = GLES20.glGetAttribLocation(programa, "aTex")

        // Cuadrado a pantalla completa. El Bitmap se sube con la primera fila en
        // v = 0, que en GL es abajo: se invierte la coordenada de textura para
        // que el clip no salga cabeza abajo.
        val datos = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f,
        )
        vertices = ByteBuffer.allocateDirect(datos.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertices.put(datos).position(0)

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textura = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glViewport(0, 0, ancho, alto)
    }

    private fun compilar(tipo: Int, fuente: String): Int {
        val s = GLES20.glCreateShader(tipo)
        GLES20.glShaderSource(s, fuente)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "shader: " + GLES20.glGetShaderInfoLog(s) }
        return s
    }

    private fun subirYPintar(bitmap: Bitmap) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(programa)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(posAttr, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(posAttr)
        vertices.position(2)
        GLES20.glVertexAttribPointer(texAttr, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(texAttr)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun liberar() {
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching { if (muxerEnMarcha) muxer.stop() }
        runCatching { muxer.release() }
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (contexto != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, contexto)
            EGL14.eglTerminate(display)
        }
        runCatching { superficie.release() }
    }

    companion object {
        /** EGL_RECORDABLE_ANDROID: la config que puede alimentar a MediaCodec. */
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
