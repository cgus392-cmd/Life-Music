package com.cglabs.lifemusic.karaoke

import android.content.Context
import android.net.ConnectivityManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

/**
 * 2.x · Los modelos del karaoke propio. No van en el APK: se bajan una vez, la
 * primera vez que se prepara un karaoke, de las publicaciones de sherpa-onnx en
 * GitHub (sin servidor nuestro, sin costo). Ficha: docs/versiones/2.0/karaoke.md.
 *
 * - Reconocedor: NVIDIA NeMo FastConformer CTC español, int8 (103 MB comprimido),
 *   CC-BY-4.0 (stt_es_fastconformer_hybrid_large_pc).
 * - Separador: UVR MDX-Net KARA (29 MB): saca la voz principal; solo se baja si
 *   hace falta y el telefono puede moverlo.
 */
object KaraokeModelos {
    private const val RECONOCEDOR_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-fast-conformer-ctc-es-1424-int8.tar.bz2"
    private const val SEPARADOR_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/source-separation-models/UVR_MDXNET_KARA.onnx"

    const val MEGAS_RECONOCEDOR = 103
    const val MEGAS_SEPARADOR = 29

    private val cliente by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    private fun carpeta(contexto: Context) = File(contexto.filesDir, "karaoke-modelos")

    /** El modelo y los tokens del reconocedor, si ya estan. */
    fun reconocedor(contexto: Context): Pair<File, File>? {
        val dir = File(carpeta(contexto), "reconocedor")
        val todos = dir.takeIf { it.isDirectory }?.walkTopDown()?.toList() ?: return null
        val modelo = todos.firstOrNull { it.isFile && it.name.endsWith(".onnx") } ?: return null
        val tokens = todos.firstOrNull { it.isFile && it.name == "tokens.txt" } ?: return null
        return modelo to tokens
    }

    fun separador(contexto: Context): File? = File(carpeta(contexto), "separar.onnx").takeIf { it.isFile && it.length() > 0 }

    /** Datos moviles (o red medida): los modelos solo se bajan con Wi-Fi. */
    fun redMedida(contexto: Context): Boolean =
        contexto.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true

    /** Baja y abre el reconocedor si falta. [avance] de 0 a 1. */
    fun asegurarReconocedor(contexto: Context, avance: (Float) -> Unit): Boolean {
        if (reconocedor(contexto) != null) return true
        val paquete = File(contexto.cacheDir, "karaoke-reconocedor.tar.bz2.part")
        val abriendo = File(carpeta(contexto), "reconocedor.abriendo")
        try {
            bajar(RECONOCEDOR_URL, paquete, MEGAS_RECONOCEDOR, avance)
            abriendo.deleteRecursively()
            abriendo.mkdirs()
            val base = abriendo.canonicalPath + File.separator
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(paquete), 256 * 1024))).use { tar ->
                var entrada = tar.nextEntry
                while (entrada != null) {
                    val destino = File(abriendo, entrada.name)
                    if (!destino.canonicalPath.startsWith(base)) error("ruta rara en el paquete")
                    if (entrada.isDirectory) destino.mkdirs() else {
                        destino.parentFile?.mkdirs()
                        destino.outputStream().use { tar.copyTo(it, 64 * 1024) }
                    }
                    entrada = tar.nextEntry
                }
            }
            val final = File(carpeta(contexto), "reconocedor")
            final.deleteRecursively()
            if (!abriendo.renameTo(final)) error("no se pudo guardar")
            return reconocedor(contexto) != null
        } finally {
            paquete.delete()
            abriendo.deleteRecursively()
        }
    }

    /** Baja el separador si falta. */
    fun asegurarSeparador(contexto: Context, avance: (Float) -> Unit): Boolean {
        if (separador(contexto) != null) return true
        val parcial = File(carpeta(contexto), "separar.onnx.part")
        parcial.parentFile?.mkdirs()
        try {
            bajar(SEPARADOR_URL, parcial, MEGAS_SEPARADOR, avance)
            return parcial.renameTo(File(carpeta(contexto), "separar.onnx"))
        } finally {
            parcial.delete()
        }
    }

    private fun bajar(url: String, destino: File, megas: Int, avance: (Float) -> Unit) {
        cliente.newCall(Request.Builder().url(url).build()).execute().use { respuesta ->
            if (!respuesta.isSuccessful) error("HTTP ${respuesta.code}")
            val cuerpo = respuesta.body ?: error("sin contenido")
            val largo = cuerpo.contentLength().takeIf { it > 0 } ?: (megas * 1_048_576L)
            cuerpo.byteStream().use { entrada ->
                destino.outputStream().use { salida ->
                    val buffer = ByteArray(64 * 1024)
                    var llevado = 0L
                    var ultimo = 0L
                    while (true) {
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        salida.write(buffer, 0, n)
                        llevado += n
                        if (llevado - ultimo > 512 * 1024) {
                            ultimo = llevado
                            avance((llevado.toFloat() / largo).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
    }
}
