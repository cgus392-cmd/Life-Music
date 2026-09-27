package com.cglabs.lifemusic.cast

/**
 * El mando de un reproductor DLNA: AVTransport (cargar, play, pausa, parar,
 * saltar, estado y posicion) y RenderingControl (volumen). Todas las llamadas
 * son de red y bloquean: van siempre fuera del hilo principal.
 */
class SesionDlna(val r: DescubridorDlna.Renderizador) {
    private val av = r.control["AVTransport"] ?: error("${r.nombre} no tiene AVTransport")
    private val rc = r.control["RenderingControl"]

    class Estado(
        /** PLAYING, PAUSED_PLAYBACK, STOPPED, TRANSITIONING, NO_MEDIA_PRESENT. */
        val estado: String,
        val posicionMs: Long?,
        val duracionMs: Long?,
    )

    fun cargar(url: String, metadatos: String) {
        Upnp.llamar(av, Upnp.AV_TRANSPORT, "SetAVTransportURI", "InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to metadatos)
    }

    fun play() { Upnp.llamar(av, Upnp.AV_TRANSPORT, "Play", "InstanceID" to "0", "Speed" to "1") }
    fun pause() { Upnp.llamar(av, Upnp.AV_TRANSPORT, "Pause", "InstanceID" to "0") }
    fun stop() { Upnp.llamar(av, Upnp.AV_TRANSPORT, "Stop", "InstanceID" to "0") }
    fun seek(ms: Long) { Upnp.llamar(av, Upnp.AV_TRANSPORT, "Seek", "InstanceID" to "0", "Unit" to "REL_TIME", "Target" to reloj(ms)) }

    fun estado(): Estado {
        val t = Upnp.llamar(av, Upnp.AV_TRANSPORT, "GetTransportInfo", "InstanceID" to "0")
        val p = runCatching { Upnp.llamar(av, Upnp.AV_TRANSPORT, "GetPositionInfo", "InstanceID" to "0") }.getOrNull()
        return Estado(
            estado = Upnp.valor(t, "CurrentTransportState") ?: "STOPPED",
            posicionMs = p?.let { Upnp.valor(it, "RelTime") }?.let(::aMs),
            duracionMs = p?.let { Upnp.valor(it, "TrackDuration") }?.let(::aMs),
        )
    }

    /** Volumen de 0 a 1, o null si el aparato no lo dice. */
    fun volumen(): Float? {
        val ctl = rc ?: return null
        return runCatching {
            Upnp.valor(Upnp.llamar(ctl, Upnp.RENDERING_CONTROL, "GetVolume", "InstanceID" to "0", "Channel" to "Master"), "CurrentVolume")
                ?.toIntOrNull()?.let { it / 100f }
        }.getOrNull()
    }

    fun ponerVolumen(v: Float) {
        val ctl = rc ?: return
        Upnp.llamar(ctl, Upnp.RENDERING_CONTROL, "SetVolume", "InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to (v * 100).toInt().coerceIn(0, 100).toString())
    }

    /** Formatos de audio que acepta (del ConnectionManager), para el diagnostico. */
    fun formatosDeAudio(): List<String> {
        val cm = r.control["ConnectionManager"] ?: return emptyList()
        val sink = runCatching { Upnp.valor(Upnp.llamar(cm, Upnp.CONNECTION_MANAGER, "GetProtocolInfo"), "Sink") }.getOrNull().orEmpty()
        return sink.split(',').map { it.trim() }.filter { it.contains(":audio/") }.mapNotNull { it.split(':').getOrNull(2) }.distinct()
    }

    companion object {
        /** «0:03:25», «00:03:25.500»; NOT_IMPLEMENTED y demas, null. */
        fun aMs(t: String): Long? {
            val partes = t.trim().split(':')
            if (partes.size != 3) return null
            val h = partes[0].toLongOrNull() ?: return null
            val m = partes[1].toLongOrNull() ?: return null
            val s = partes[2].toDoubleOrNull() ?: return null
            return h * 3_600_000 + m * 60_000 + (s * 1000).toLong()
        }

        fun reloj(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        }
    }
}
