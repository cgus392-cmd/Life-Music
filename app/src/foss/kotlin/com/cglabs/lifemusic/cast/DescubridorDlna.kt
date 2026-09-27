package com.cglabs.lifemusic.cast

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Busca reproductores DLNA/UPnP (Digital Media Renderer: la mayoria de los TV
 * Samsung, LG y Sony, AirScreen, Kodi, muchos parlantes) con SSDP: un
 * M-SEARCH a la direccion multicast 239.255.255.250:1900, y cada aparato
 * contesta con la URL de su descripcion. Se repite cada 10 s mientras alguien
 * quiera buscar (la barra de Inicio o la hoja de aparatos), y se olvida a
 * quien lleva 45 s sin contestar.
 */
class DescubridorDlna(private val context: Context, private val scope: CoroutineScope) {

    class Renderizador(
        val id: String,
        val nombre: String,
        val modelo: String?,
        val fabricante: String?,
        val host: String,
        /** controlURL absoluta por servicio: «AVTransport», «RenderingControl», «ConnectionManager». */
        val control: Map<String, String>,
    )

    val renderizadores = MutableStateFlow<List<Renderizador>>(emptyList())

    private var usuarios = 0
    private var trabajo: Job? = null
    private var candado: WifiManager.MulticastLock? = null
    /** Por URL de descripcion: el aparato y cuando contesto por ultima vez. */
    private val vistos = LinkedHashMap<String, Pair<Renderizador, Long>>()
    /** Descripciones que no se pudieron leer, para no reintentar en cada ronda. */
    private val fallidas = HashSet<String>()

    @Synchronized
    fun iniciar() {
        usuarios++
        if (trabajo?.isActive == true) return
        candado = runCatching {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("lifemusic-dlna").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        trabajo = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { ronda() }.onFailure { DiagnosticoCast.log("DLNA: busqueda fallo", it) }
                val limite = System.currentTimeMillis() - 45_000
                synchronized(vistos) {
                    if (vistos.values.removeAll { it.second < limite }) publicar()
                }
                delay(10_000)
            }
        }
    }

    @Synchronized
    fun detener(forzar: Boolean = false) {
        usuarios = if (forzar) 0 else (usuarios - 1).coerceAtLeast(0)
        if (usuarios > 0) return
        trabajo?.cancel()
        trabajo = null
        runCatching { candado?.release() }
        candado = null
        synchronized(vistos) { vistos.clear(); publicar() }
    }

    /** Quita a [id] de la lista (no contesto): si sigue en la red, la proxima ronda lo trae de nuevo. */
    fun olvidar(id: String) {
        synchronized(vistos) { if (vistos.values.removeAll { it.first.id == id }) publicar() }
    }

    private fun publicar() {
        renderizadores.value = vistos.values.map { it.first }.distinctBy { it.id }.sortedBy { it.nombre.lowercase() }
    }

    private suspend fun ronda() {
        val ubicaciones = LinkedHashMap<String, String>() // ubicacion -> host
        DatagramSocket().use { s ->
            s.soTimeout = 700
            val grupo = InetAddress.getByName("239.255.255.250")
            for (st in listOf("urn:schemas-upnp-org:device:MediaRenderer:1", "urn:schemas-upnp-org:service:AVTransport:1")) {
                val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\nUSER-AGENT: Android/1 UPnP/1.1 LifeMusic/1.2\r\n\r\n"
                val datos = msg.toByteArray(Charsets.US_ASCII)
                s.send(DatagramPacket(datos, datos.size, grupo, 1900))
            }
            val buf = ByteArray(4096)
            val hasta = System.currentTimeMillis() + 3_000
            while (System.currentTimeMillis() < hasta) {
                val paquete = DatagramPacket(buf, buf.size)
                try { s.receive(paquete) } catch (e: SocketTimeoutException) { continue }
                val texto = String(paquete.data, 0, paquete.length, Charsets.UTF_8)
                val ubicacion = Regex("(?im)^LOCATION:\\s*(\\S+)").find(texto)?.groupValues?.get(1) ?: continue
                ubicaciones[ubicacion] = paquete.address.hostAddress ?: continue
            }
        }
        val ahora = System.currentTimeMillis()
        for ((ubicacion, host) in ubicaciones) {
            val conocido = synchronized(vistos) { vistos[ubicacion] }
            if (conocido != null) {
                synchronized(vistos) { vistos[ubicacion] = conocido.first to ahora }
                continue
            }
            if (ubicacion in fallidas) continue
            val d = Upnp.describir(ubicacion)
            if (d == null) { fallidas.add(ubicacion); continue }
            val r = Renderizador(
                id = d.udn,
                nombre = d.nombre,
                modelo = d.modelo,
                fabricante = d.fabricante,
                host = runCatching { URL(ubicacion).host }.getOrDefault(host),
                control = d.control,
            )
            DiagnosticoCast.log("DLNA: ${r.nombre} (${r.fabricante ?: "?"} ${r.modelo ?: ""}) ${r.host} servicios=${r.control.keys}")
            synchronized(vistos) { vistos[ubicacion] = r to ahora; publicar() }
        }
    }
}
