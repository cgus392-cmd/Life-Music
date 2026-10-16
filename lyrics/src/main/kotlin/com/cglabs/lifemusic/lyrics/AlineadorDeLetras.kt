package com.cglabs.lifemusic.lyrics

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * 2.x · Karaoke propio (ficha docs/versiones/2.0/karaoke.md): calza las palabras
 * de una linea de letra con lo que un reconocedor de voz oyo en ese tramo de la
 * cancion, para saber en que instante se canta cada palabra.
 *
 * Es alineacion forzada «por texto»: el reconocedor da palabras con su tiempo
 * (aunque se equivoque en algunas) y aqui se emparejan con las de la letra con
 * programacion dinamica (Needleman-Wunsch) usando parecido de letras. Las que no
 * se pudieron calzar se reparten por silabas entre las vecinas que si.
 *
 * Sin Android, para poder probarlo.
 */
object AlineadorDeLetras {
    /** Una palabra que oyo el reconocedor y cuando empezo, en segundos desde el inicio de la cancion. */
    data class Reconocida(val texto: String, val inicio: Double)

    /** Una palabra de la letra con su instante; [calzada] dice si salio del audio o se estimo. */
    data class Alineada(val texto: String, val inicio: Double, val calzada: Boolean)

    /** Parecido minimo (0 a 1) para dar por buena una pareja letra/reconocida. */
    const val PARECIDO_MINIMO = 0.5

    /** Lo minimo entre dos palabras estimadas cuando no hay mas espacio (segundos). */
    const val SEPARACION_MINIMA = 0.12

    /** Sin acentos, en minuscula y solo letras y numeros: «¡Corazón!» y «corazon» son la misma. */
    fun normalizar(palabra: String): String =
        Normalizer.normalize(palabra.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]"), "")

    /** Silabas aproximadas (grupos de vocales): sirven para repartir el tiempo. */
    fun silabas(palabra: String): Int =
        max(1, Regex("[aeiouyáéíóúü]+", RegexOption.IGNORE_CASE).findAll(palabra).count())

    /** Las palabras de una linea de letra, tal como se escriben. */
    fun palabrasDeLaLetra(linea: String): List<String> =
        linea.split(Regex("\\s+")).map { it.trim() }.filter { normalizar(it).isNotEmpty() }

    /**
     * Junta los tokens del reconocedor en palabras. En los modelos con BPE
     * (NeMo), «▁» marca el comienzo de una palabra; el tiempo de la palabra es el
     * de su primer token. [desde] es donde empezaba el tramo dentro de la cancion.
     */
    fun palabrasReconocidas(tokens: Array<String>, tiempos: FloatArray, desde: Double): List<Reconocida> {
        val salida = mutableListOf<Reconocida>()
        val actual = StringBuilder()
        var inicio = 0.0
        tokens.forEachIndexed { i, token ->
            val t = tiempos.getOrNull(i)?.toDouble() ?: return@forEachIndexed
            val empieza = token.startsWith("▁") || token.startsWith(" ") || actual.isEmpty()
            if (empieza && actual.isNotEmpty()) {
                salida += Reconocida(actual.toString(), inicio)
                actual.clear()
            }
            if (actual.isEmpty()) inicio = desde + t
            actual.append(token.removePrefix("▁").trim())
        }
        if (actual.isNotEmpty()) salida += Reconocida(actual.toString(), inicio)
        return salida.filter { normalizar(it.texto).isNotEmpty() }
    }

    /**
     * Como suena en español, para perdonar los errores tipicos del reconocedor con
     * musica encima: b/v, s/z/c (seseo), ll/y (yeismo), la h muda, qu/k, g/j ante
     * e/i, letras dobles y la s o d final que se comen en el Caribe («verda», «lo ojo»).
     * «vez» y «bes», «hacer» y «aser», «llorar» y «yorar» suenan igual.
     */
    fun sonido(palabra: String): String =
        normalizar(palabra)
            .replace("ch", "C") // la ch se guarda aparte para que no la borre la h muda
            .replace(Regex("qu(?=[ei])"), "k")
            .replace(Regex("gu(?=[ei])"), "G") // guerra: g dura
            .replace(Regex("g(?=[ei])"), "j")
            .replace(Regex("c(?=[ei])"), "s")
            .replace('c', 'k')
            .replace('q', 'k')
            .replace('z', 's')
            .replace("x", "ks")
            .replace('v', 'b')
            .replace('w', 'u')
            .replace("ll", "y")
            .replace("h", "")
            .replace(Regex("y(?![aeiou])"), "i")
            .replace(Regex("(.)\\1+"), "$1")
            .replace(Regex("(?<=.)[sd]$"), "")
            .lowercase()

    /**
     * 1 = iguales, 0 = nada que ver: distancia de edicion sobre como se escribe y
     * sobre como suena; vale la mejor de las dos.
     */
    fun parecido(a: String, b: String): Double =
        max(semejanza(normalizar(a), normalizar(b)), semejanza(sonido(a), sonido(b)))

    private fun semejanza(x: String, y: String): Double {
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val d = Array(x.length + 1) { IntArray(y.length + 1) }
        for (i in 0..x.length) d[i][0] = i
        for (j in 0..y.length) d[0][j] = j
        for (i in 1..x.length) for (j in 1..y.length) {
            val costo = if (x[i - 1] == y[j - 1]) 0 else 1
            d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + costo)
        }
        return 1.0 - d[x.length][y.length].toDouble() / max(x.length, y.length)
    }

    /**
     * Alinea una linea. [inicioLinea] y [finLinea] son los tiempos del LRC (el fin
     * es donde empieza la linea siguiente); las palabras no calzadas se reparten
     * por silabas entre las calzadas vecinas, o entre los bordes de la linea.
     */
    fun alinear(letra: List<String>, oidas: List<Reconocida>, inicioLinea: Double, finLinea: Double): List<Alineada> {
        if (letra.isEmpty()) return emptyList()
        val n = letra.size
        val m = oidas.size
        val anclas = arrayOfNulls<Double>(n)

        if (m > 0) {
            // El parecido de cada pareja se calcula una vez (lo usan la tabla y el regreso).
            val parecidos = Array(n) { i -> DoubleArray(m) { j -> parecido(letra[i], oidas[j].texto) } }
            // Needleman-Wunsch: emparejar suma segun el parecido; saltarse una palabra resta poco.
            val hueco = -0.4
            val puntaje = Array(n + 1) { DoubleArray(m + 1) }
            val desde = Array(n + 1) { IntArray(m + 1) } // 0 diagonal, 1 arriba (salta letra), 2 izquierda (salta oida)
            for (i in 1..n) { puntaje[i][0] = i * hueco; desde[i][0] = 1 }
            for (j in 1..m) { puntaje[0][j] = j * hueco; desde[0][j] = 2 }
            for (i in 1..n) for (j in 1..m) {
                val p = parecidos[i - 1][j - 1]
                val diagonal = puntaje[i - 1][j - 1] + if (p >= PARECIDO_MINIMO) 2.0 * p else -1.0
                val arriba = puntaje[i - 1][j] + hueco
                val izquierda = puntaje[i][j - 1] + hueco
                when {
                    diagonal >= arriba && diagonal >= izquierda -> { puntaje[i][j] = diagonal; desde[i][j] = 0 }
                    arriba >= izquierda -> { puntaje[i][j] = arriba; desde[i][j] = 1 }
                    else -> { puntaje[i][j] = izquierda; desde[i][j] = 2 }
                }
            }
            var i = n
            var j = m
            while (i > 0 && j > 0) {
                when (desde[i][j]) {
                    0 -> {
                        if (parecidos[i - 1][j - 1] >= PARECIDO_MINIMO) {
                            anclas[i - 1] = oidas[j - 1].inicio.coerceIn(inicioLinea - 0.5, finLinea)
                        }
                        i--; j--
                    }
                    1 -> i--
                    else -> j--
                }
            }
            // Las anclas tienen que ir en orden: una que retrocede se descarta.
            var ultima = Double.NEGATIVE_INFINITY
            for (k in 0 until n) {
                val t = anclas[k] ?: continue
                if (t <= ultima) anclas[k] = null else ultima = t
            }
        }

        // Repartir por silabas las que no se calzaron, entre anclas vecinas.
        val tiempos = DoubleArray(n)
        var k = 0
        while (k < n) {
            val t = anclas[k]
            if (t != null) {
                tiempos[k] = t
                k++
                continue
            }
            val previa = (k - 1 downTo 0).firstOrNull { anclas[it] != null }
            val siguiente = (k + 1 until n).firstOrNull { anclas[it] != null }
            val hastaT = siguiente?.let { anclas[it]!! } ?: finLinea
            // Si la primera calzada empieza antes que la linea (el LRC puede ir un pelo
            // tarde), las sueltas de delante se corren antes que ella: nunca al reves.
            val desdeT = previa?.let { anclas[it]!! }
                ?: minOf(inicioLinea, hastaT - SEPARACION_MINIMA * ((siguiente ?: n) - k))
            // El tramo reparte entre la previa (si hay) y las sueltas hasta la siguiente.
            val primero = previa ?: k
            val ultimoSuelto = (siguiente ?: n) - 1
            val pesos = (primero..ultimoSuelto).map { silabas(letra[it]).toDouble() }
            val total = pesos.sum().coerceAtLeast(1.0)
            var acumulado = 0.0
            for ((idx, palabra) in (primero..ultimoSuelto).withIndex()) {
                if (palabra >= k && anclas[palabra] == null) {
                    tiempos[palabra] = desdeT + (hastaT - desdeT) * (acumulado / total)
                }
                acumulado += pesos[idx]
            }
            k = ultimoSuelto + 1
        }
        return letra.mapIndexed { idx, palabra -> Alineada(palabra, tiempos[idx], anclas[idx] != null) }
    }

    /** Cuantas palabras de la linea salieron del audio (0 a 1). */
    fun cobertura(linea: List<Alineada>): Double =
        if (linea.isEmpty()) 0.0 else linea.count { it.calzada }.toDouble() / linea.size

    /** Una linea en LRC «mejorado»: [mm:ss.xx]<mm:ss.xx>palabra <mm:ss.xx>palabra … */
    fun aLrcMejorado(inicioLinea: Double, palabras: List<Alineada>): String =
        "[${marca(inicioLinea)}]" + palabras.joinToString(" ") { "<${marca(it.inicio)}>${it.texto}" }

    private fun marca(segundos: Double): String {
        val centesimas = (segundos.coerceAtLeast(0.0) * 100).toLong()
        return "%02d:%02d.%02d".format(centesimas / 6000, (centesimas / 100) % 60, centesimas % 100)
    }
}
