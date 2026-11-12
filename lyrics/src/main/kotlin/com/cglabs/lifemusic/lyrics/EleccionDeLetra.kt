package com.cglabs.lifemusic.lyrics

import java.text.Normalizer

/**
 * 2.1.x · La mejor letra, no la primera (pedido de CG, 12-11).
 *
 * Antes se usaba la primera letra sincronizada que llegara, de cualquier proveedor. Muchas veces no traía palabra
 * por palabra, estaba en otro idioma o era de otra versión (en vivo, remix, acelerada), y la persona tenía que buscar
 * a mano entre todas. Aquí cada letra recibe un puntaje:
 * - **Sincronía:** palabra por palabra > por línea > sin sincronizar.
 * - **Escritura:** si el título y el artista están en letras latinas, una letra en chino, japonés, coreano,
 *   cirílico o árabe es de otra canción o una traducción; también al revés.
 * - **Duración:** si la última línea pasa del final de la canción, o la letra se queda en la mitad, es otra versión.
 *   Sus tiempos no sirven y queda por debajo de una letra sin sincronizar.
 * - **El título dentro de la letra** dice que es la canción original y no una traducción.
 * - **Pocas líneas:** un fragmento no es una letra.
 * - **El orden de proveedores** de la persona solo desempata.
 *
 * Es Kotlin puro, sin Android, para probarlo en el PC (EleccionDeLetraTest).
 */
object EleccionDeLetra {

    enum class Sincronia { NINGUNA, LINEA, PALABRA }

    /** Una letra recibida. [orden] es la posición de su proveedor en la lista de la persona (0 = el primero). */
    data class Candidata(val proveedor: String, val letra: String, val orden: Int)

    data class Evaluacion(
        val candidata: Candidata,
        val sincronia: Sincronia,
        val escrituraBien: Boolean,
        val duracionBien: Boolean,
        val tituloDentro: Boolean,
        val lineas: Int,
        val puntaje: Int,
    ) {
        /** Tan buena que no vale la pena esperar a las demás. */
        val perfecta: Boolean
            get() = sincronia == Sincronia.PALABRA && escrituraBien && duracionBien && lineas >= MINIMO_LINEAS
    }

    const val MINIMO_LINEAS = 4

    /** Holgura del final: las letras suelen cerrar unos segundos antes, o el video trae cola. */
    private const val HOLGURA_FINAL_S = 15

    // Ojo: en Android las expresiones van por ICU, mas estricto que Java en el PC: «]» y «}» siempre escapados, o la
    // app se cae al cargar esta clase (paso en la prueba.25, 12-11).
    private val TIEMPO_DE_LINEA = Regex("^\\s*\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})\\]")
    private val PALABRA_RICA = Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>")
    private val PALABRAS_APARTE = Regex("^<[^<>]+:\\d+(\\.\\d+)?:\\d+(\\.\\d+)?(\\|[^<>]+:\\d+(\\.\\d+)?:\\d+(\\.\\d+)?)*>$")
    private val METADATO = Regex("^\\s*\\[[a-zA-Z]+:.*\\]\\s*$")
    private val ETIQUETAS = Regex("\\[[^\\]]*\\]|<[^>]*>|\\{[^\\}]*\\}")
    private val ENTRE_PARENTESIS = Regex("\\([^)]*\\)|\\[[^\\]]*\\]")

    /** Palabras vacías: no dicen nada del título (es / en). */
    private val VACIAS = setOf(
        "the", "and", "you", "for", "with", "feat", "remix", "live", "version", "video", "oficial", "official",
        "audio", "lyric", "lyrics", "los", "las", "del", "con", "que", "por", "una", "para", "mix",
    )

    fun sincronia(letra: String): Sincronia {
        val lineas = letra.lines()
        val conTiempo = lineas.count { TIEMPO_DE_LINEA.containsMatchIn(it) }
        val conPalabras = lineas.count { PALABRA_RICA.findAll(it).count() >= 2 || PALABRAS_APARTE.matches(it.trim()) }
        return when {
            conPalabras >= 3 && conPalabras * 2 >= conTiempo -> Sincronia.PALABRA
            conTiempo >= 3 -> Sincronia.LINEA
            else -> Sincronia.NINGUNA
        }
    }

    /** El último tiempo de línea, en segundos; null si no hay. */
    fun ultimoTiempo(letra: String): Int? = letra.lines().mapNotNull { linea ->
        TIEMPO_DE_LINEA.find(linea)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
    }.maxOrNull()

    /** El texto que se canta, sin tiempos, etiquetas ni metadatos. */
    fun texto(letra: String): List<String> = letra.lines()
        .filterNot { METADATO.matches(it) || PALABRAS_APARTE.matches(it.trim()) }
        .map { ETIQUETAS.replace(it, " ").replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }

    private enum class Escritura { LATINA, OTRA, NINGUNA }

    private fun escritura(texto: String): Escritura {
        var latinas = 0
        var otras = 0
        for (c in texto) {
            if (!Character.isLetter(c)) continue
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.LATIN -> latinas++
                Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL, Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.ARABIC,
                Character.UnicodeScript.HEBREW, Character.UnicodeScript.THAI, Character.UnicodeScript.DEVANAGARI,
                Character.UnicodeScript.GREEK -> otras++
                else -> {}
            }
        }
        return when {
            latinas + otras == 0 -> Escritura.NINGUNA
            latinas >= otras -> Escritura.LATINA
            else -> Escritura.OTRA
        }
    }

    private fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun palabrasDelTitulo(titulo: String): Set<String> =
        normalizar(ENTRE_PARENTESIS.replace(titulo, " "))
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in VACIAS }
            .toSet()

    fun evaluar(c: Candidata, titulo: String, artistas: String, duracionS: Int): Evaluacion {
        val sincronia = sincronia(c.letra)
        val lineasDeTexto = texto(c.letra)
        val todoElTexto = lineasDeTexto.joinToString(" ")

        val esperada = escritura("$titulo $artistas")
        val dada = escritura(todoElTexto)
        val escrituraBien = esperada == Escritura.NINGUNA || dada == Escritura.NINGUNA || esperada == dada

        val ultimo = if (sincronia != Sincronia.NINGUNA) ultimoTiempo(c.letra) else null
        val duracionBien = when {
            ultimo == null || duracionS <= 0 -> true
            ultimo > duracionS + HOLGURA_FINAL_S -> false
            duracionS > 60 && ultimo < duracionS * 0.4 -> false
            else -> true
        }

        val delTitulo = palabrasDelTitulo(titulo)
        val palabrasDeLetra = normalizar(todoElTexto).split(Regex("[^\\p{L}\\p{N}]+")).toSet()
        val tituloDentro = delTitulo.isNotEmpty() && delTitulo.any { it in palabrasDeLetra }

        var puntaje = when (sincronia) {
            Sincronia.PALABRA -> 300
            Sincronia.LINEA -> 200
            Sincronia.NINGUNA -> 0
        }
        if (!escrituraBien) puntaje -= 400
        // Tiempos de otra versión: peor que no tener tiempos.
        if (!duracionBien) puntaje -= 320
        if (tituloDentro) puntaje += 40
        if (lineasDeTexto.size < MINIMO_LINEAS) puntaje -= 100
        puntaje -= c.orden * 2

        return Evaluacion(c, sincronia, escrituraBien, duracionBien, tituloDentro, lineasDeTexto.size, puntaje)
    }

    /** La mejor de las recibidas; null si no hay ninguna. */
    fun mejor(candidatas: List<Candidata>, titulo: String, artistas: String, duracionS: Int): Evaluacion? =
        candidatas.map { evaluar(it, titulo, artistas, duracionS) }.maxByOrNull { it.puntaje }
}
