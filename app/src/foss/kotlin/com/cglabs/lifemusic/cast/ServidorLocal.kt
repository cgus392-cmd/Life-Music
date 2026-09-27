package com.cglabs.lifemusic.cast

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.cglabs.lifemusic.playback.MusicService
import com.cglabs.lifemusic.utils.isLocalMediaId
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Un servidor HTTP minimo dentro del telefono que le entrega el audio a un
 * reproductor DLNA de la red local. El TV no le pide nada a YouTube: el
 * telefono lee la cancion por la misma cadena que su reproductor (descargas y
 * cache primero, red si falta, via [MusicService.fuenteParaServir]) y se la
 * pasa. Asi suenan las descargadas, no importa que el TV tenga otra IP
 * publica (IPv6) y no hace falta HTTPS en el TV, que muchos no soportan.
 *
 * Solo sirve a la red local y solo rutas con un token aleatorio por sesion:
 * nadie de la red puede pedir otra cosa. Soporta Range (los TV saltan y
 * vuelven a pedir por trozos) y HEAD. Una conexion, un hilo.
 */
class ServidorLocal(private val context: Context, private val servicio: MusicService) {

    private var socket: ServerSocket? = null
    private val hilos = Executors.newCachedThreadPool()
    private val token = ByteArray(9).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    /** Tipo de contenido y largo total ya averiguados, por cancion. */
    private val conocidos = ConcurrentHashMap<String, Pair<String, Long>>()
    /** Caratula de cada cancion (URL https original), para servirla por http al TV. */
    private val caratulas = ConcurrentHashMap<String, String>()

    @Synchronized
    fun iniciar() {
        if (socket?.isClosed == false) return
        val s = ServerSocket(0, 16)
        socket = s
        Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: Exception) { break }
                hilos.execute { atender(c) }
            }
        }, "LifeMusic-servidor").apply { isDaemon = true }.start()
        DiagnosticoCast.log("servidor local en el puerto ${s.localPort}")
    }

    @Synchronized
    fun detener() {
        runCatching { socket?.close() }
        socket = null
    }

    /** La IP del telefono en la interfaz por la que se llega a [host] (el TV). */
    private fun ipHacia(host: String): String =
        DatagramSocket().use { it.connect(InetAddress.getByName(host), 9); it.localAddress.hostAddress ?: "127.0.0.1" }

    fun urlCancion(mediaId: String, host: String): String =
        "http://${ipHacia(host)}:${socket?.localPort}/$token/c/${URLEncoder.encode(mediaId, "UTF-8")}"

    fun urlCaratula(mediaId: String, original: String, host: String): String {
        caratulas[mediaId] = original
        return "http://${ipHacia(host)}:${socket?.localPort}/$token/a/${URLEncoder.encode(mediaId, "UTF-8")}.jpg"
    }

    /** Tipo de contenido de la cancion (se mira en los primeros bytes) y su largo total, si se sabe. */
    fun tipoDe(mediaId: String): String = averiguar(mediaId)?.first ?: "audio/mp4"

    fun averiguar(mediaId: String): Pair<String, Long>? {
        conocidos[mediaId]?.let { return it }
        val abierta = abrir(mediaId, 0L, C.LENGTH_UNSET.toLong()) ?: return null
        return try {
            val cabeza = ByteArray(12)
            var leidos = 0
            while (leidos < cabeza.size) {
                val n = abierta.leer(cabeza, leidos, cabeza.size - leidos)
                if (n <= 0) break
                leidos += n
            }
            val tipo = olfatear(cabeza, leidos)
            (tipo to abierta.largo).also { conocidos[mediaId] = it }
        } finally {
            abierta.cerrar()
        }
    }

    private fun olfatear(b: ByteArray, n: Int): String {
        fun u(i: Int) = b[i].toInt() and 0xFF
        return when {
            n >= 4 && u(0) == 0x1A && u(1) == 0x45 && u(2) == 0xDF && u(3) == 0xA3 -> "audio/webm"
            n >= 8 && String(b, 4, 4, Charsets.US_ASCII) == "ftyp" -> "audio/mp4"
            n >= 4 && String(b, 0, 4, Charsets.US_ASCII) == "fLaC" -> "audio/flac"
            n >= 4 && String(b, 0, 4, Charsets.US_ASCII) == "OggS" -> "audio/ogg"
            n >= 3 && String(b, 0, 3, Charsets.US_ASCII) == "ID3" -> "audio/mpeg"
            n >= 2 && u(0) == 0xFF && (u(1) and 0xE0) == 0xE0 -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    private class Abierta(val largo: Long, val leer: (ByteArray, Int, Int) -> Int, val cerrar: () -> Unit)

    /**
     * El aparato no acepta Opus/WebM (lo dice su GetProtocolInfo): se le sirve la
     * version AAC de YouTube, pedida aparte, en vez de lo que tiene el reproductor.
     */
    @Volatile
    var soloAac = false
        set(v) { if (field != v) conocidos.clear(); field = v }
    /** URL AAC, su tamaño y cuando se pidio (caducan a las ~6 h; se renuevan a las 3). */
    private val urlsAac = ConcurrentHashMap<String, Triple<String, Long, Long>>()

    private fun aac(mediaId: String): Pair<String, Long>? {
        urlsAac[mediaId]?.takeIf { System.currentTimeMillis() - it.third < 3 * 3_600_000L }?.let { return it.first to it.second }
        val r = kotlinx.coroutines.runBlocking { servicio.urlAacParaDlna(mediaId) } ?: return null
        urlsAac[mediaId] = Triple(r.first, r.second, System.currentTimeMillis())
        return r
    }

    private fun abrirHttp(url: String, largoTotal: Long, desde: Long, largo: Long): Abierta? {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 8_000
        con.readTimeout = 20_000
        con.setRequestProperty("Range", if (largo > 0) "bytes=$desde-${desde + largo - 1}" else "bytes=$desde-")
        val codigo = con.responseCode
        if (codigo !in 200..299) {
            DiagnosticoCast.log("servidor: YouTube respondio $codigo al AAC")
            con.disconnect()
            return null
        }
        val total = con.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
            ?: largoTotal.takeIf { it > 0 }
            ?: if (codigo == 200) con.contentLengthLong else -1L
        val entrada = con.inputStream
        return Abierta(total, { b, o, l -> entrada.read(b, o, l) }, { runCatching { entrada.close() }; con.disconnect() })
    }

    /** Abre la cancion desde [desde]; [largo] LENGTH_UNSET = hasta el final. El largo devuelto es el total si se sabe (o -1). */
    private fun abrir(mediaId: String, desde: Long, largo: Long): Abierta? {
        if (soloAac && !mediaId.isLocalMediaId()) {
            val a = aac(mediaId)
            if (a != null) return abrirHttp(a.first, a.second, desde, largo)
            DiagnosticoCast.log("servidor: sin AAC para ${mediaId.take(11)}; se manda el formato del reproductor")
        }
        val fuente = servicio.fuenteParaServir(mediaId)
        if (fuente == null) {
            // Archivo del telefono: por el ContentResolver.
            val uri = Uri.parse(mediaId)
            val afd = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r") }.getOrNull() ?: return null
            val total = afd.length
            val entrada = afd.createInputStream()
            if (desde > 0) entrada.skip(desde)
            return Abierta(total, { b, o, l -> entrada.read(b, o, l) }, { runCatching { entrada.close(); afd.close() } })
        }
        val (ds, uri) = fuente
        val restante = ds.open(DataSpec.Builder().setUri(uri).setKey(mediaId).setPosition(desde).setLength(largo).build())
        val total = if (restante != C.LENGTH_UNSET.toLong() && largo == C.LENGTH_UNSET.toLong()) desde + restante
            else conocidos[mediaId]?.second ?: -1L
        return Abierta(total, { b, o, l -> ds.read(b, o, l).let { if (it == C.RESULT_END_OF_INPUT) -1 else it } }, { runCatching { ds.close() } })
    }

    private fun atender(c: Socket) {
        c.use { s ->
            s.soTimeout = 30_000
            val entrada = BufferedInputStream(s.getInputStream())
            val salida = s.getOutputStream()
            val linea = leerLinea(entrada) ?: return
            val cabeceras = HashMap<String, String>()
            while (true) {
                val h = leerLinea(entrada) ?: break
                if (h.isEmpty()) break
                val i = h.indexOf(':')
                if (i > 0) cabeceras[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
            }
            val partes = linea.split(' ')
            val metodo = partes.getOrNull(0) ?: return
            val ruta = partes.getOrNull(1) ?: return
            val trozos = ruta.trimStart('/').split('/')
            if (trozos.size < 3 || trozos[0] != token || (metodo != "GET" && metodo != "HEAD")) {
                responder(salida, "404 Not Found", mapOf("Content-Length" to "0")); return
            }
            val id = URLDecoder.decode(trozos[2].removeSuffix(".jpg"), "UTF-8")
            try {
                when (trozos[1]) {
                    "c" -> servirCancion(id, metodo, cabeceras["range"], salida, s.inetAddress.hostAddress)
                    "a" -> servirCaratula(id, metodo, salida)
                    else -> responder(salida, "404 Not Found", mapOf("Content-Length" to "0"))
                }
            } catch (e: Exception) {
                // El TV corta la conexion cuando salta o cambia de cancion: es normal.
                if (e !is java.net.SocketException) DiagnosticoCast.log("servidor: ${trozos[1]} fallo", e)
            }
        }
    }

    private fun servirCancion(id: String, metodo: String, rango: String?, salida: OutputStream, quien: String?) {
        val (tipo, total) = averiguar(id) ?: run { responder(salida, "404 Not Found", mapOf("Content-Length" to "0")); return }
        var desde = 0L
        var hasta = if (total > 0) total - 1 else -1L
        val parcial = rango != null && rango.startsWith("bytes=")
        if (parcial) {
            val r = rango!!.removePrefix("bytes=").substringBefore(',').split('-')
            r.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()?.let { desde = it }
            r.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()?.let { hasta = it }
            if (total > 0 && desde >= total) {
                responder(salida, "416 Range Not Satisfiable", mapOf("Content-Range" to "bytes */$total", "Content-Length" to "0")); return
            }
        }
        val largo = if (hasta >= desde) hasta - desde + 1 else -1L
        val cab = linkedMapOf(
            "Content-Type" to tipo,
            "Accept-Ranges" to "bytes",
            "transferMode.dlna.org" to "Streaming",
            "contentFeatures.dlna.org" to FEATURES,
            "Connection" to "close",
        )
        if (largo > 0) cab["Content-Length"] = largo.toString()
        if (parcial && total > 0) cab["Content-Range"] = "bytes $desde-${desde + largo - 1}/$total"
        responder(salida, if (parcial && total > 0) "206 Partial Content" else "200 OK", cab)
        if (metodo == "HEAD") return
        DiagnosticoCast.log("servidor: $quien pide ${id.take(11)} desde $desde ($tipo)")
        val abierta = abrir(id, desde, if (largo > 0) largo else C.LENGTH_UNSET.toLong()) ?: return
        try {
            val buf = ByteArray(64 * 1024)
            var pendiente = if (largo > 0) largo else Long.MAX_VALUE
            while (pendiente > 0) {
                val n = abierta.leer(buf, 0, minOf(buf.size.toLong(), pendiente).toInt())
                if (n <= 0) break
                salida.write(buf, 0, n)
                pendiente -= n
            }
            salida.flush()
        } finally {
            abierta.cerrar()
        }
    }

    private fun servirCaratula(id: String, metodo: String, salida: OutputStream) {
        val original = caratulas[id] ?: run { responder(salida, "404 Not Found", mapOf("Content-Length" to "0")); return }
        val con = URL(original).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 5_000
            con.readTimeout = 8_000
            val datos = con.inputStream.use { it.readBytes() }
            responder(salida, "200 OK", mapOf("Content-Type" to (con.contentType ?: "image/jpeg"), "Content-Length" to datos.size.toString(), "Connection" to "close"))
            if (metodo != "HEAD") salida.write(datos)
            salida.flush()
        } finally {
            con.disconnect()
        }
    }

    private fun responder(salida: OutputStream, estado: String, cabeceras: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 ").append(estado).append("\r\n")
        sb.append("Server: Android UPnP/1.0 LifeMusic/1.2\r\n")
        for ((k, v) in cabeceras) sb.append(k).append(": ").append(v).append("\r\n")
        sb.append("\r\n")
        salida.write(sb.toString().toByteArray(Charsets.US_ASCII))
    }

    private fun leerLinea(e: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = e.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8192) return null
            sb.append(b.toChar())
        }
    }

    companion object {
        /** Se puede saltar por bytes (OP=01), sin conversion, en streaming. */
        const val FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
    }
}
