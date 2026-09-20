package com.cglabs.lifemusic.clip

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Lo que el usuario elige para un clip de tarjeta. Cada cifra se recuerda en
 * preferencias (ver ClipSheet); esto es solo el paquete que recibe el renderizador.
 */
data class ClipOpciones(
    /** Segundos de video. */
    val duracionSeg: Int = 15,
    /** 9:16 para historias; si no, 16:9. */
    val vertical: Boolean = true,
    /** Letra sincronizada en el clip (solo si la cancion la tiene). */
    val conLetra: Boolean = true,
    /** Audio de la cancion dentro del video. Apagado de serie (ver PreferenceKeys). */
    val conAudio: Boolean = false,
    /** La caratula late con los graves. Quieta de serie: lo pidio CG. */
    val caratulaLate: Boolean = false,
    /** Las manchas del fondo laten con los graves. */
    val fondoLate: Boolean = true,
    /** Posicion de la cancion (ms) donde empieza el clip. */
    val desdeMs: Long = 0L,
) {
    val ancho: Int get() = if (vertical) 1080 else 1920
    val alto: Int get() = if (vertical) 1920 else 1080
}

/**
 * Interruptor global para abrir la hoja del clip desde cualquier sitio (menu
 * del reproductor, modo ambiente). La hoja vive en MainActivity y lo observa.
 */
object ClipLanzador {
    val abierto = MutableStateFlow(false)
    /** Tramo elegido en la letra (desde, hasta) en ms; null si se abrio sin seleccion. */
    val rango = MutableStateFlow<Pair<Long, Long>?>(null)
    /** Abierta desde el modo ambiente: es el unico sitio donde se ofrece grabarlo. */
    val desdeAmbiente = MutableStateFlow(false)
    /** Clip ya hecho (una grabacion que acaba de terminar): la hoja abre en «listo». */
    val resultado = MutableStateFlow<File?>(null)

    fun abrir(desdeMs: Long? = null, hastaMs: Long? = null, desdeAmbiente: Boolean = false) {
        rango.value = if (desdeMs != null && hastaMs != null && hastaMs > desdeMs) desdeMs to hastaMs else null
        this.desdeAmbiente.value = desdeAmbiente
        resultado.value = null
        abierto.value = true
    }

    /** Vuelve a abrir la hoja con un clip terminado, para compartirlo o guardarlo. */
    fun abrirResultado(fichero: File) {
        rango.value = null
        desdeAmbiente.value = true
        resultado.value = fichero
        abierto.value = true
    }

    fun cerrar() {
        abierto.value = false
        rango.value = null
        desdeAmbiente.value = false
        resultado.value = null
    }
}
