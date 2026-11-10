package com.cglabs.lifemusic.playback.audio

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * 2.1.x · Automix v2, bloque A: el mapa de la canción.
 *
 * Lo que la app sabe de una canción ENTERA, compás por compás: cuánta energía, cuánta batería, cuántos graves y cuánto
 * medio (donde viven las voces); dónde empiezan y terminan sus secciones (intro, estrofa, coro, outro); y los PUNTOS
 * DE MEZCLA, los lugares donde un DJ la haría entrar o salir: «arranca la batería», «empieza el coro», «se queda sin
 * batería», «empieza el outro». La mesa del Mixed Editor los dibuja sobre la onda como los destacados de Instagram, y
 * el Automix los usa para elegir dónde mezclar (el truco del AutoMix de Apple con Bad Bunny y Tego, 09-11).
 *
 * Se construye por pedazos, mientras llega el audio (no guarda la canción en memoria), y es Kotlin puro: se prueba en
 * la JVM con señales sintéticas.
 */

enum class TipoSeccion { INTRO, ESTROFA, CORO, OUTRO }

enum class TipoPunto { ENTRADA, SALIDA, DESTACADO }

/** Un compás de la canción: los valores van normalizados a la canción (1 ≈ lo fuerte de esa canción). */
data class Compas(
    val inicioMs: Long,
    val energia: Float,
    val bateria: Float,
    val graves: Float,
    val medios: Float,
)

data class Seccion(val desde: Int, val hasta: Int, val tipo: TipoSeccion)

/**
 * Un lugar donde se puede mezclar. [compas] es el índice del compás donde ocurre; [motivo] es la frase que la mesa
 * muestra y que Nova dice («arranca la batería»).
 */
data class PuntoDeMezcla(
    val compas: Int,
    val ms: Long,
    val tipo: TipoPunto,
    val puntaje: Float,
    val motivo: Motivo,
)

/** Por qué ese punto sirve. La interfaz lo traduce a palabras. */
enum class Motivo {
    ARRANCA_BATERIA,
    ARRANCA_BAJO,
    EMPIEZA_CORO,
    SE_VA_BATERIA,
    EMPIEZA_OUTRO,
    BAJA_ENERGIA,
    EL_CORO,
}

class MapaDeCancion(
    val duracionMs: Long,
    val bpm: Float,
    val primerGolpeMs: Long,
    /** La onda para dibujar: un valor 0..1 cada [PASO_ONDA_MS]. */
    val onda: FloatArray,
    val compases: List<Compas>,
    val secciones: List<Seccion>,
    val puntos: List<PuntoDeMezcla>,
) {
    val compasMs: Float get() = 4f * 60_000f / bpm

    fun guardar(archivo: File) {
        archivo.parentFile?.mkdirs()
        DataOutputStream(archivo.outputStream().buffered()).use { o ->
            o.writeInt(VERSION)
            o.writeLong(duracionMs); o.writeFloat(bpm); o.writeLong(primerGolpeMs)
            o.writeInt(onda.size); onda.forEach { o.writeFloat(it) }
            o.writeInt(compases.size)
            compases.forEach { o.writeLong(it.inicioMs); o.writeFloat(it.energia); o.writeFloat(it.bateria); o.writeFloat(it.graves); o.writeFloat(it.medios) }
            o.writeInt(secciones.size)
            secciones.forEach { o.writeInt(it.desde); o.writeInt(it.hasta); o.writeInt(it.tipo.ordinal) }
            o.writeInt(puntos.size)
            puntos.forEach { o.writeInt(it.compas); o.writeLong(it.ms); o.writeInt(it.tipo.ordinal); o.writeFloat(it.puntaje); o.writeInt(it.motivo.ordinal) }
        }
    }

    companion object {
        const val PASO_ONDA_MS = 100
        private const val VERSION = 1

        /** Null si no existe o es de otra versión (se vuelve a construir). */
        fun leer(archivo: File): MapaDeCancion? = runCatching {
            DataInputStream(archivo.inputStream().buffered()).use { i ->
                if (i.readInt() != VERSION) return null
                val dur = i.readLong(); val bpm = i.readFloat(); val golpe = i.readLong()
                val onda = FloatArray(i.readInt()) { i.readFloat() }
                val compases = List(i.readInt()) { Compas(i.readLong(), i.readFloat(), i.readFloat(), i.readFloat(), i.readFloat()) }
                val secciones = List(i.readInt()) { Seccion(i.readInt(), i.readInt(), TipoSeccion.entries[i.readInt()]) }
                val puntos = List(i.readInt()) {
                    PuntoDeMezcla(i.readInt(), i.readLong(), TipoPunto.entries[i.readInt()], i.readFloat(), Motivo.entries[i.readInt()])
                }
                MapaDeCancion(dur, bpm, golpe, onda, compases, secciones, puntos)
            }
        }.getOrNull()
    }
}

/**
 * Construye un [MapaDeCancion] a partir del audio en mono, en pedazos. Cada [PASO_MS] mide: el volumen, los graves
 * (30–150 Hz), los medios (300–3400 Hz, donde está la voz) y el «golpe» de la batería (flujo espectral positivo en
 * 2–12 kHz, donde suenan platillos, cajas y hi-hats).
 */
class ConstructorDeMapa(private val frecuencia: Int) {
    private val n = 2048
    private val salto = frecuencia * PASO_MS / 1000
    private val ventana = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    private var pendiente = FloatArray(n * 4)
    private var lleno = 0
    private var anterior = FloatArray(n / 2)

    private val rms = ArrayList<Float>()
    private val graves = ArrayList<Float>()
    private val medios = ArrayList<Float>()
    private val golpe = ArrayList<Float>()

    private val hz = frecuencia.toFloat() / n
    private val graveDesde = (30f / hz).toInt().coerceAtLeast(1)
    private val graveHasta = (150f / hz).toInt()
    private val medioDesde = (300f / hz).toInt()
    private val medioHasta = (3400f / hz).toInt()
    private val golpeDesde = (2000f / hz).toInt()
    private val golpeHasta = min((12_000f / hz).toInt(), n / 2 - 1)

    fun alimentar(muestras: FloatArray, cuantas: Int = muestras.size) {
        if (lleno + cuantas > pendiente.size) pendiente = pendiente.copyOf(max(pendiente.size * 2, lleno + cuantas))
        System.arraycopy(muestras, 0, pendiente, lleno, cuantas)
        lleno += cuantas
        var inicio = 0
        // A 44,1 o 48 kHz el paso (50 ms) es más largo que la ventana: hay que tener los dos completos antes de avanzar.
        val necesario = max(n, salto)
        while (lleno - inicio >= necesario) {
            cuadro(inicio)
            inicio += salto
        }
        if (inicio > 0) {
            System.arraycopy(pendiente, inicio, pendiente, 0, lleno - inicio)
            lleno -= inicio
        }
    }

    private fun cuadro(desde: Int) {
        val re = FloatArray(n)
        val im = FloatArray(n)
        var suma = 0f
        for (k in 0 until n) {
            val v = pendiente[desde + k]
            if (k < salto) suma += v * v
            re[k] = v * ventana[k]
        }
        rms.add(sqrt(suma / salto))
        BeatAnalyzer.fft(re, im)
        var g = 0f
        var m = 0f
        var f = 0f
        for (k in 1 until n / 2) {
            val mag = sqrt(re[k] * re[k] + im[k] * im[k])
            if (k in graveDesde..graveHasta) g += mag * mag
            if (k in medioDesde..medioHasta) m += mag * mag
            if (k in golpeDesde..golpeHasta) {
                val l = ln(1f + mag)
                val d = l - anterior[k]
                if (d > 0f) f += d
                anterior[k] = l
            }
        }
        graves.add(g)
        medios.add(m)
        golpe.add(f)
    }

    /** Cierra el mapa con la rejilla de golpes que ya calculó el analizador. */
    fun terminar(bpm: Float, primerGolpeMs: Long, duracionMs: Long): MapaDeCancion? {
        if (rms.size < 20 || bpm <= 0f) return null
        val compasMs = 4f * 60_000f / bpm
        val porCuadro = PASO_MS.toFloat()
        // Los compases empiezan en un golpe de la rejilla; se arranca en el primero que cabe desde el inicio.
        var inicio = primerGolpeMs.toFloat()
        while (inicio - compasMs >= 0f) inicio -= compasMs
        val crudos = ArrayList<FloatArray>()
        val inicios = ArrayList<Long>()
        var t = inicio
        while (t + compasMs <= duracionMs.toFloat() + compasMs * 0.5f) {
            val a = (t / porCuadro).toInt().coerceIn(0, rms.size - 1)
            val b = ((t + compasMs) / porCuadro).toInt().coerceIn(a + 1, rms.size)
            crudos.add(floatArrayOf(media(rms, a, b), media(golpe, a, b), media(graves, a, b), media(medios, a, b)))
            inicios.add(t.toLong())
            t += compasMs
        }
        if (crudos.size < 8) return null
        // Normalizar a la canción: 1 ≈ el percentil 90 de esa medida.
        val refs = FloatArray(4) { j -> percentil(crudos.map { it[j] }, 0.9f).coerceAtLeast(1e-9f) }
        val compases = crudos.mapIndexed { i, c ->
            Compas(inicios[i], c[0] / refs[0], c[1] / refs[1], c[2] / refs[2], c[3] / refs[3])
        }
        val secciones = secciones(compases)
        val puntos = puntos(compases, secciones)
        val maxRms = rms.maxOrNull()?.coerceAtLeast(1e-9f) ?: 1f
        val cada = (MapaDeCancion.PASO_ONDA_MS / PASO_MS).coerceAtLeast(1)
        val onda = FloatArray(rms.size / cada) { i -> (media(rms, i * cada, i * cada + cada) / maxRms).coerceIn(0f, 1f) }
        return MapaDeCancion(duracionMs, bpm, primerGolpeMs, onda, compases, secciones, puntos)
    }

    // ── Secciones: donde cambia el «color» de la canción, en límite de frase ──────────────────────────────────

    private fun vector(c: Compas) = floatArrayOf(c.energia, c.bateria, c.graves, c.medios)

    private fun mediaDe(cs: List<Compas>, desde: Int, hasta: Int): FloatArray {
        val v = FloatArray(4)
        val a = desde.coerceAtLeast(0)
        val b = hasta.coerceAtMost(cs.size)
        if (b <= a) return v
        for (i in a until b) vector(cs[i]).forEachIndexed { j, x -> v[j] += x }
        for (j in v.indices) v[j] /= (b - a)
        return v
    }

    /** Cuánto cambia la canción en el compás [b]: los 4 compases de antes contra los 4 de después. */
    private fun novedad(cs: List<Compas>, b: Int): Float {
        val antes = mediaDe(cs, b - 4, b)
        val despues = mediaDe(cs, b, b + 4)
        var d = 0f
        for (j in antes.indices) d += abs(despues[j] - antes[j])
        return d
    }

    private fun secciones(cs: List<Compas>): List<Seccion> {
        val cortes = ArrayList<Int>()
        // Se buscan cambios solo en límite de frase (cada 4 compases), donde los hace la música.
        val candidatos = (4 until cs.size - 2 step 4).map { it to novedad(cs, it) }
        val umbral = max(UMBRAL_CORTE, percentil(candidatos.map { it.second }, 0.6f))
        for ((b, v) in candidatos) if (v >= umbral) cortes.add(b)
        val bordes = listOf(0) + cortes + listOf(cs.size)
        val crudas = bordes.zipWithNext().filter { (a, b) -> b > a }
        val energias = crudas.map { (a, b) -> mediaDe(cs, a, b)[0] }
        val mediana = percentil(energias, 0.5f)
        val alta = percentil(energias, 0.75f)
        return crudas.mapIndexed { i, (a, b) ->
            val v = mediaDe(cs, a, b)
            val tipo = when {
                i == 0 && crudas.size > 1 && (v[0] < mediana * 0.75f || v[1] < 0.35f) -> TipoSeccion.INTRO
                i == crudas.lastIndex && crudas.size > 1 && (v[0] < mediana * 0.75f || v[1] < 0.35f) -> TipoSeccion.OUTRO
                v[0] >= alta && b - a >= 4 -> TipoSeccion.CORO
                else -> TipoSeccion.ESTROFA
            }
            Seccion(a, b, tipo)
        }
    }

    // ── Puntos de mezcla ──────────────────────────────────────────────────────────────────────────────────────

    private fun puntos(cs: List<Compas>, secciones: List<Seccion>): List<PuntoDeMezcla> {
        val salida = ArrayList<PuntoDeMezcla>()
        fun agregar(b: Int, tipo: TipoPunto, base: Float, motivo: Motivo) {
            val frase = when {
                b % 8 == 0 -> 0.2f
                b % 4 == 0 -> 0.1f
                else -> 0f
            }
            salida.add(PuntoDeMezcla(b, cs[b.coerceIn(0, cs.lastIndex)].inicioMs, tipo, base + frase, motivo))
        }
        val mitad = cs.size / 2
        for (s in secciones.drop(1)) {
            val b = s.desde
            val antes = mediaDe(cs, b - 4, b)
            val despues = mediaDe(cs, b, b + 4)
            val dBateria = despues[1] - antes[1]
            val dGraves = despues[2] - antes[2]
            val dEnergia = despues[0] - antes[0]
            if (b <= mitad) {
                when {
                    s.tipo == TipoSeccion.CORO -> agregar(b, TipoPunto.ENTRADA, 0.6f + dEnergia.coerceAtLeast(0f), Motivo.EMPIEZA_CORO)
                    dBateria > CAMBIO_FUERTE -> agregar(b, TipoPunto.ENTRADA, 0.5f + dBateria, Motivo.ARRANCA_BATERIA)
                    dGraves > CAMBIO_FUERTE -> agregar(b, TipoPunto.ENTRADA, 0.45f + dGraves, Motivo.ARRANCA_BAJO)
                }
            } else {
                when {
                    s.tipo == TipoSeccion.OUTRO -> agregar(b, TipoPunto.SALIDA, 0.6f - dEnergia.coerceAtMost(0f), Motivo.EMPIEZA_OUTRO)
                    dBateria < -CAMBIO_FUERTE -> agregar(b, TipoPunto.SALIDA, 0.5f - dBateria, Motivo.SE_VA_BATERIA)
                    dEnergia < -CAMBIO_FUERTE -> agregar(b, TipoPunto.SALIDA, 0.4f - dEnergia, Motivo.BAJA_ENERGIA)
                }
            }
        }
        // El destacado, como en Instagram: el coro más fuerte.
        secciones.filter { it.tipo == TipoSeccion.CORO }
            .maxByOrNull { mediaDe(cs, it.desde, it.hasta)[0] }
            ?.let { agregar(it.desde, TipoPunto.DESTACADO, 1f, Motivo.EL_CORO) }
        return salida
            .groupBy { it.tipo }
            .flatMap { (_, lista) -> lista.sortedByDescending { it.puntaje }.take(3) }
            .sortedBy { it.compas }
    }

    private fun media(l: List<Float>, a: Int, b: Int): Float {
        if (b <= a) return 0f
        var s = 0f
        for (i in a until min(b, l.size)) s += l[i]
        return s / (min(b, l.size) - a).coerceAtLeast(1)
    }

    private fun percentil(l: List<Float>, p: Float): Float {
        if (l.isEmpty()) return 0f
        val o = l.sorted()
        return o[((o.size - 1) * p).toInt().coerceIn(0, o.lastIndex)]
    }

    companion object {
        /** Cada cuánto se mide: 50 ms dan detalle de sobra para compases de 2 segundos. */
        const val PASO_MS = 50

        /** Cuánto tiene que cambiar algo (en «canciones», 1 ≈ lo fuerte) para que cuente como que arranca o se va. */
        const val CAMBIO_FUERTE = 0.3f

        /** Novedad mínima para cortar una sección. */
        const val UMBRAL_CORTE = 0.35f
    }
}
