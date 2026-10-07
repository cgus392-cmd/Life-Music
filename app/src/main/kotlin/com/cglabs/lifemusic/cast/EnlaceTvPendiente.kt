package com.cglabs.lifemusic.cast

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * El codigo de Life Music TV que llego por el QR del TV (1.3.1).
 *
 * El TV con navegador muestra un QR con lifemusic.pages.dev/tv/enlazar?c=XXXXXX.
 * Con el App Link (assetlinks.json en la landing), Android abre la app directo;
 * MainActivity deja aqui el codigo y la variante foss muestra el dialogo
 * «¿Enlazar con el TV que muestra XXX XXX?» (EnlaceTvDesdeQr). Si el App Link no
 * se verifico, la pagina del QR tiene un boton que abre la app por el esquema
 * propio lifemusic://tv/enlazar?c=XXXXXX.
 */
object EnlaceTvPendiente {
    /** null cuando no hay ninguno esperando. */
    val codigo = MutableStateFlow<String?>(null)

    private val HOSTS = setOf("lifemusic.pages.dev", "prueba.lifemusic.pages.dev")
    private val CODIGO = Regex("^[2-9A-HJKMNP-Z]{6}$")

    /** El codigo si [uri] es un enlace del QR del TV; null si es otra cosa. */
    fun desdeUri(uri: Uri): String? {
        val esWeb = (uri.scheme == "https" || uri.scheme == "http") && uri.host in HOSTS && (uri.path ?: "").startsWith("/tv/enlazar")
        val esPropio = uri.scheme == "lifemusic" && uri.host == "tv" && (uri.path ?: "").startsWith("/enlazar")
        if (!esWeb && !esPropio) return null
        return uri.getQueryParameter("c")?.uppercase()?.filter { it.isLetterOrDigit() }?.takeIf { CODIGO.matches(it) }
    }
}
