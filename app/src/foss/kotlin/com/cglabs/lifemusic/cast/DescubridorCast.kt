package com.cglabs.lifemusic.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Busca receptores de Google Cast en la red local por mDNS (`_googlecast._tcp`)
 * con el NsdManager de Android: sin librerias, sin permisos especiales. Cada
 * receptor anuncia en su registro TXT el nombre amigable (`fn`) y el modelo
 * (`md`); la direccion y el puerto salen al resolverlo.
 */
class DescubridorCast(context: Context) {

    class Aparato(
        val id: String,
        val nombre: String,
        val modelo: String?,
        val host: String,
        val puerto: Int,
    )

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val ejecutor = Executors.newSingleThreadExecutor()
    private val encontrados = LinkedHashMap<String, Aparato>()
    private val porResolver = ArrayDeque<NsdServiceInfo>()
    private var resolviendo = false
    private var oyente: NsdManager.DiscoveryListener? = null

    val aparatos = MutableStateFlow<List<Aparato>>(emptyList())
    val buscando = MutableStateFlow(false)

    fun iniciar() {
        if (oyente != null) return
        val o = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { buscando.value = true }
            override fun onDiscoveryStopped(serviceType: String) { buscando.value = false }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { com.cglabs.lifemusic.cast.DiagnosticoCast.log("descubrimiento fallo: $errorCode"); buscando.value = false; oyente = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { buscando.value = false }
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(porResolver) { porResolver.addLast(info) }
                resolverSiguiente()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                synchronized(encontrados) {
                    encontrados.remove(info.serviceName)
                    aparatos.value = encontrados.values.toList()
                }
            }
        }
        oyente = o
        runCatching { nsd.discoverServices(SERVICIO, NsdManager.PROTOCOL_DNS_SD, o) }
            .onFailure { oyente = null }
    }

    fun detener() {
        oyente?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        oyente = null
        buscando.value = false
    }

    /** NsdManager solo resuelve de a uno: cola y turno. */
    private fun resolverSiguiente() {
        val info = synchronized(porResolver) {
            if (resolviendo) return
            porResolver.removeFirstOrNull() ?: return
        }.also { resolviendo = true }
        if (Build.VERSION.SDK_INT >= 34) {
            nsd.registerServiceInfoCallback(info, ejecutor, object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.hostAddresses.firstOrNull { it.hostAddress?.contains(':') == false }?.hostAddress
                        ?: serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                    if (host != null) {
                        anotar(serviceInfo, host)
                        runCatching { nsd.unregisterServiceInfoCallback(this) }
                        siguiente()
                    }
                }
                override fun onServiceLost() { siguiente() }
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) { siguiente() }
                override fun onServiceInfoCallbackUnregistered() {}
            })
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { siguiente() }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    serviceInfo.host?.hostAddress?.let { anotar(serviceInfo, it) }
                    siguiente()
                }
            })
        }
    }

    private fun siguiente() {
        synchronized(porResolver) { resolviendo = false }
        resolverSiguiente()
    }

    private fun anotar(info: NsdServiceInfo, host: String) {
        val txt = info.attributes
        fun campo(clave: String) = txt[clave]?.let { String(it, Charsets.UTF_8) }?.takeIf { it.isNotBlank() }
        val aparato = Aparato(
            id = campo("id") ?: info.serviceName,
            nombre = campo("fn") ?: info.serviceName,
            modelo = campo("md"),
            host = host,
            puerto = if (info.port > 0) info.port else CastCliente.PUERTO,
        )
        com.cglabs.lifemusic.cast.DiagnosticoCast.log("aparato: ${aparato.nombre} (${aparato.modelo}) ${aparato.host}:${aparato.puerto}")
        synchronized(encontrados) {
            encontrados[info.serviceName] = aparato
            aparatos.value = encontrados.values.toList()
        }
    }

    companion object {
        private const val SERVICIO = "_googlecast._tcp."
    }
}
