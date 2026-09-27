package com.cglabs.lifemusic.cast

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lo minimo de UPnP para hablar con un reproductor DLNA (Digital Media
 * Renderer): leer su descripcion (quien es y donde estan sus servicios) y
 * llamar acciones SOAP de AVTransport, RenderingControl y ConnectionManager.
 * Sin librerias: HTTP de la plataforma y el XmlPullParser de Android.
 */
object Upnp {
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val CONNECTION_MANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1"

    class Descripcion(
        val udn: String,
        val nombre: String,
        val modelo: String?,
        val fabricante: String?,
        /** controlURL absoluta por tipo de servicio (sin la version: «AVTransport»). */
        val control: Map<String, String>,
    )

    /** Baja y lee la descripcion del aparato en [ubicacion] (la URL del LOCATION de SSDP). */
    fun describir(ubicacion: String): Descripcion? {
        val xml = get(ubicacion) ?: return null
        class Disp { var tipo = ""; var nombre = ""; var modelo: String? = null; var fabricante: String? = null; var udn = ""; val control = HashMap<String, String>() }
        val base = Regex("<(?:\\w+:)?URLBase>\\s*([^<\\s]+)\\s*</").find(xml)?.groupValues?.get(1) ?: ubicacion
        val pila = ArrayList<Disp>()
        val todos = ArrayList<Disp>()
        var servicioTipo: String? = null
        var servicioControl: String? = null
        var etiqueta = ""
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    etiqueta = p.name.substringAfter(':')
                    when (etiqueta) {
                        "device" -> pila.add(Disp())
                        "service" -> { servicioTipo = null; servicioControl = null }
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = p.text.trim()
                    val d = pila.lastOrNull()
                    if (t.isNotEmpty() && d != null) when (etiqueta) {
                        "deviceType" -> d.tipo = t
                        "friendlyName" -> d.nombre = t
                        "modelName" -> d.modelo = t
                        "manufacturer" -> d.fabricante = t
                        "UDN" -> d.udn = t
                        "serviceType" -> servicioTipo = t
                        "controlURL" -> servicioControl = t
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (p.name.substringAfter(':')) {
                        "service" -> {
                            val tipo = servicioTipo; val ctl = servicioControl
                            if (tipo != null && ctl != null) {
                                val clave = tipo.substringAfter(":service:").substringBefore(':')
                                pila.lastOrNull()?.control?.put(clave, runCatching { URL(URL(base), ctl).toString() }.getOrDefault(ctl))
                            }
                        }
                        "device" -> pila.removeLastOrNull()?.let { todos.add(it) }
                    }
                    etiqueta = ""
                }
            }
            ev = p.next()
        }
        // El que reproduce es el que tiene AVTransport (en un TV suele ser un dispositivo embebido).
        val r = todos.firstOrNull { "AVTransport" in it.control } ?: return null
        return Descripcion(
            udn = r.udn.ifEmpty { ubicacion },
            nombre = r.nombre.ifEmpty { r.modelo ?: "DLNA" },
            modelo = r.modelo,
            fabricante = r.fabricante,
            control = r.control,
        )
    }

    /** Llama a [accion] del [servicio] en [control]; devuelve el XML de la respuesta o lanza con el error. */
    fun llamar(control: String, servicio: String, accion: String, vararg args: Pair<String, String>): String {
        val cuerpo = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
            append("<u:").append(accion).append(" xmlns:u=\"").append(servicio).append("\">")
            for ((k, v) in args) append('<').append(k).append('>').append(escapar(v)).append("</").append(k).append('>')
            append("</u:").append(accion).append("></s:Body></s:Envelope>")
        }.toByteArray(Charsets.UTF_8)
        val c = URL(control).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 4_000
            c.readTimeout = 6_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            c.setRequestProperty("SOAPAction", "\"$servicio#$accion\"")
            c.setRequestProperty("User-Agent", "Android UPnP/1.0 LifeMusic/1.2")
            c.setFixedLengthStreamingMode(cuerpo.size)
            c.outputStream.use { it.write(cuerpo) }
            val codigo = c.responseCode
            val texto = (if (codigo in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (codigo !in 200..299) {
                val error = valor(texto, "errorDescription") ?: valor(texto, "faultstring") ?: "HTTP $codigo"
                throw IllegalStateException("$accion: $error (${valor(texto, "errorCode") ?: codigo})")
            }
            return texto
        } finally {
            c.disconnect()
        }
    }

    /** El texto de la primera <etiqueta> (con o sin prefijo de espacio de nombres). */
    fun valor(xml: String, etiqueta: String): String? =
        Regex("<(?:\\w+:)?$etiqueta(?:\\s[^>]*)?>([\\s\\S]*?)</(?:\\w+:)?$etiqueta>").find(xml)?.groupValues?.get(1)?.let { desescapar(it.trim()) }

    fun escapar(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun desescapar(s: String): String = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 3_000
            c.readTimeout = 4_000
            c.setRequestProperty("User-Agent", "Android UPnP/1.0 LifeMusic/1.2")
            if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
