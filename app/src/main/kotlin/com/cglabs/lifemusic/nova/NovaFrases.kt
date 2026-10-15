package com.cglabs.lifemusic.nova

/**
 * Lo que dice Nova. Regla de oro, la misma de la voz de Life Line: solo cosas
 * ciertas. Cada hueco se llena con un dato real (la hora, la cancion que sono,
 * la que suena) y, si falta el dato, la frase se arma sin ese pedazo, nunca con
 * uno inventado.
 *
 * Aqui va solo el texto, sin Android, para poder probarlo. La voz y el momento
 * en que habla viven en [NovaLocutor]. Ficha: docs/versiones/2.0/nova.md.
 */
object NovaFrases {
    /** Lo minimo que Nova necesita saber de una cancion para nombrarla. */
    data class Pista(val id: String, val titulo: String, val artistas: List<String>)

    fun saludo(hora: Int): String = when (hora) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }

    /** El primer nombre de la cuenta: Nova saluda como una persona, no como un formulario. */
    fun primerNombre(cuenta: String?): String? =
        cuenta?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= 20 && it.none(Char::isDigit) }

    /**
     * El titulo para decirlo en voz alta: sin los parentesis de YouTube
     * («Official Video», «Remastered 2011», «feat. …»), que en la radio nadie lee.
     * Si quitar todo dejaria el titulo vacio, se deja como venia.
     */
    fun tituloHablado(titulo: String): String {
        val limpio = titulo
            .replace(Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]"), "")
            .replace(Regex("\\s+(feat\\.?|ft\\.?)\\s+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+-\\s+(official|video|audio|lyric|letra|visualizer|remaster).*$", RegexOption.IGNORE_CASE), "")
            .trim()
        return limpio.ifBlank { titulo.trim() }
    }

    /** Uno o dos artistas, como se dicen: «Bad Bunny» o «Karol G y Feid». */
    fun artistasHablados(artistas: List<String>): String? {
        val nombres = artistas.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return when (nombres.size) {
            0 -> null
            1 -> nombres[0]
            else -> "${nombres[0]} y ${nombres[1]}"
        }
    }

    /** Cuantas plantillas de puente hay: quien llama elige una sin repetir la anterior. */
    const val PUENTES = 3

    /**
     * La primera vez que sale al aire: saludo, quien es y el puente. Si no hay
     * nombre, saluda sin nombre; si no habia cancion antes, solo presenta la que suena.
     */
    fun presentacion(hora: Int, nombre: String?, anterior: Pista?, nueva: Pista, eleccion: Int): String {
        val saludo = if (nombre != null) "${saludo(hora)}, $nombre." else "${saludo(hora)}."
        return "$saludo Soy Nova, tu DJ. ${puente(anterior, nueva, eleccion)}"
    }

    /** El puente entre dos canciones: lo que acaba de sonar y lo que suena ahora. */
    fun puente(anterior: Pista?, nueva: Pista, eleccion: Int): String {
        val tituloNueva = tituloHablado(nueva.titulo)
        val deNueva = artistasHablados(nueva.artistas)?.let { ", de $it" }.orEmpty()
        if (anterior == null) return "Ahora suena $tituloNueva$deNueva."

        val tituloAnterior = tituloHablado(anterior.titulo)
        val artistaAnterior = artistasHablados(anterior.artistas)
        val deAnterior = artistaAnterior?.let { ", de $it" }.orEmpty()

        // Mismo artista seguido: decirlo dos veces suena a maquina.
        val mismoArtista = artistaAnterior != null && artistaAnterior == artistasHablados(nueva.artistas)
        if (mismoArtista) return "Seguimos con $artistaAnterior. Eso fue $tituloAnterior, y ahora, $tituloNueva."

        return when (Math.floorMod(eleccion, PUENTES)) {
            0 -> "Eso fue $tituloAnterior$deAnterior. Ahora suena $tituloNueva$deNueva."
            1 -> "Acabas de escuchar $tituloAnterior$deAnterior. Sigue $tituloNueva$deNueva."
            else -> if (artistaAnterior != null) {
                "Venías con $artistaAnterior. Ahora, $tituloNueva$deNueva."
            } else {
                "Eso fue $tituloAnterior. Ahora, $tituloNueva$deNueva."
            }
        }
    }
}
