package com.cglabs.lifemusic.clip

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import com.cglabs.lifemusic.lyrics.LyricsEntry
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Dibuja un fotograma del clip sobre un Bitmap, con la misma estetica del modo
 * ambiente: fondo negro con seis manchas de luz de los colores de la caratula
 * derivando (la misma formula que AmbientGlowBackground, a 20 s por vuelta),
 * la caratula con esquinas redondeadas, y la letra con la palabra viva
 * barriendo como Life Line. Arriba, titulo y artista; abajo, la barra de
 * progreso del clip y la firma «Life Music», sola: en un clip que va a redes
 * la marca personal sobra (lo dijo CG).
 *
 * Es puro android.graphics, sin Compose: se puede dibujar fuera de pantalla,
 * en cualquier hilo, mas rapido que el tiempo real.
 */
class LienzoDeClip(
    private val opciones: ClipOpciones,
    private val caratula: Bitmap?,
    colores: List<Int>,
    private val titulo: String,
    private val artista: String,
    private val letra: List<LyricsEntry>?,
    private val tipografia: Typeface?,
) {
    private val ancho = opciones.ancho
    private val alto = opciones.alto
    private val vertical = opciones.vertical
    /** Escala respecto al lado corto de 1080: todo se dibuja en «dp de clip». */
    private val e = min(ancho, alto) / 1080f

    private val colores: List<Int> = colores.ifEmpty { listOf(0xFF3A3A3A.toInt(), 0xFF2A2A2A.toInt()) }
    private val fondo = Paint().apply { color = 0xFF050505.toInt() }
    // Las manchas se dibujan a un cuarto de resolucion y se escalan: seis
    // degradados radiales a pantalla completa por fotograma eran 80 ms; asi son
    // unos 5, y al ser degradados suaves no se nota.
    private val fondoPequeno: Bitmap = Bitmap.createBitmap((ancho / 4).coerceAtLeast(1), (alto / 4).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    private val lienzoPequeno = Canvas(fondoPequeno)
    private val escaladoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destinoFondo = android.graphics.Rect(0, 0, ancho, alto)
    private val manchas = Array(6) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val recorte = Path()
    private val marco = RectF()
    private val caratulaPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val progresoFondo = Paint().apply { color = Color.argb(46, 255, 255, 255) }
    private val progresoFrente = Paint().apply { color = Color.argb(217, 255, 255, 255) }

    private val negrita = tipografia?.let { Typeface.create(it, Typeface.BOLD) } ?: Typeface.DEFAULT_BOLD
    private val normal = tipografia ?: Typeface.DEFAULT

    private val tituloPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = negrita; color = Color.WHITE; textSize = 44f * e; textAlign = Paint.Align.CENTER
    }
    private val artistaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = normal; color = Color.argb(153, 255, 255, 255); textSize = 34f * e; textAlign = Paint.Align.CENTER
    }
    private val firmaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = negrita; color = Color.argb(115, 255, 255, 255); textSize = 30f * e; letterSpacing = 0.12f
    }
    private val lineaVivaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = negrita; color = Color.WHITE; textSize = 60f * e
    }
    private val lineaTenuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = normal; color = Color.argb(89, 255, 255, 255); textSize = 40f * e
    }
    private val colorApagado = Color.argb(89, 255, 255, 255)

    /**
     * Pinta el fotograma. [tMs] es el tiempo dentro del clip; [posMs] la
     * posicion en la cancion; [energia] y [golpe] en 0..1 (cero si no hay audio).
     */
    fun dibujar(destino: Bitmap, tMs: Long, posMs: Long, energia: Float, golpe: Float) {
        val c = Canvas(destino)
        lienzoPequeno.drawRect(0f, 0f, fondoPequeno.width.toFloat(), fondoPequeno.height.toFloat(), fondo)
        dibujarManchas(lienzoPequeno, fondoPequeno.width.toFloat(), fondoPequeno.height.toFloat(), tMs, energia, if (opciones.fondoLate) golpe else 0f)
        c.drawBitmap(fondoPequeno, null, destinoFondo, escaladoPaint)

        if (vertical) dibujarVertical(c, tMs, posMs, golpe) else dibujarHorizontal(c, tMs, posMs, golpe)
    }

    // ── Fondo ─────────────────────────────────────────────────────────────────

    private fun oscilar(progreso: Float, min: Float, max: Float, fase: Float): Float {
        val v = sin(2f * PI.toFloat() * (progreso + fase))
        return min + (max - min) * ((v + 1f) * 0.5f)
    }

    private fun colorGirado(progreso: Float, indice: Int): Int {
        val n = colores.size
        val idx = indice + progreso * n
        val a = floor(idx).toInt() % n
        val b = (a + 1) % n
        val f = idx - floor(idx)
        return mezclar(colores[a], colores[b], f)
    }

    private fun mezclar(x: Int, y: Int, f: Float): Int {
        fun ch(a: Int, b: Int) = (a + (b - a) * f).toInt().coerceIn(0, 255)
        return Color.argb(255, ch(Color.red(x), Color.red(y)), ch(Color.green(x), Color.green(y)), ch(Color.blue(x), Color.blue(y)))
    }

    private fun dibujarManchas(c: Canvas, w: Float, h: Float, tMs: Long, energia: Float, golpe: Float) {
        val p = (tMs % 20_000L) / 20_000f
        val escalaRadio = 1f + golpe * 0.22f
        val escalaAlfa = 1f + energia * 0.3f
        // Posiciones y radios: los mismos de AmbientGlowBackground.
        val defs = arrayOf(
            floatArrayOf(0.0f, 1.0f, 0.00f, 0.0f, 0.5f, 0.07f, 0.8f, 1.6f, 0.12f, 0.85f, 0.5f),
            floatArrayOf(1.0f, 0.0f, 0.20f, 0.5f, 1.0f, 0.25f, 0.7f, 1.5f, 0.18f, 0.80f, 0.45f),
            floatArrayOf(0.2f, 0.8f, 0.33f, 0.8f, 0.2f, 0.36f, 0.6f, 1.4f, 0.29f, 0.75f, 0.40f),
            floatArrayOf(0.3f, 0.7f, 0.44f, 0.2f, 0.8f, 0.41f, 0.9f, 1.7f, 0.47f, 0.70f, 0.35f),
            floatArrayOf(0.4f, 0.6f, 0.55f, 0.0f, 1.0f, 0.51f, 0.7f, 1.5f, 0.58f, 0.65f, 0.30f),
            floatArrayOf(0.0f, 1.0f, 0.66f, 0.5f, 0.7f, 0.62f, 0.8f, 1.8f, 0.69f, 0.60f, 0.25f),
        )
        for (i in defs.indices) {
            val d = defs[i]
            val cx = w * oscilar(p, d[0], d[1], d[2])
            val cy = h * oscilar(p, d[3], d[4], d[5])
            val r = w * oscilar(p, d[6], d[7], d[8]) * escalaRadio
            val color = colorGirado(p, i)
            val a1 = (255 * (d[9] * escalaAlfa).coerceIn(0f, 1f)).toInt()
            val a2 = (255 * (d[10] * escalaAlfa).coerceIn(0f, 1f)).toInt()
            manchas[i].shader = RadialGradient(
                cx, cy, r.coerceAtLeast(1f),
                intArrayOf(conAlfa(color, a1), conAlfa(color, a2), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w, h, manchas[i])
        }
    }

    private fun conAlfa(color: Int, alfa: Int) = Color.argb(alfa, Color.red(color), Color.green(color), Color.blue(color))

    // ── Vertical (9:16) ───────────────────────────────────────────────────────

    private fun dibujarVertical(c: Canvas, tMs: Long, posMs: Long, golpe: Float) {
        val w = ancho.toFloat(); val h = alto.toFloat()
        val margen = 72f * e

        // Arriba: titulo y artista, en vez de una segunda firma.
        var y = 150f * e
        c.drawText(recortar(titulo, tituloPaint, w - 2 * margen), w / 2f, y, tituloPaint)
        y += 52f * e
        c.drawText(recortar(artista, artistaPaint, w - 2 * margen), w / 2f, y, artistaPaint)

        // Caratula: mas grande si no hay letra que mostrar.
        val hayLetra = opciones.conLetra && !letra.isNullOrEmpty()
        val lado = (if (hayLetra) 0.60f else 0.72f) * w
        val topCaratula = if (hayLetra) 250f * e else 380f * e
        dibujarCaratula(c, (w - lado) / 2f, topCaratula, lado, golpe)

        if (hayLetra) {
            val topLetra = topCaratula + lado + 90f * e
            dibujarLetra(c, posMs, margen, topLetra, w - 2 * margen, Layout.Alignment.ALIGN_CENTER, h - 220f * e)
        }

        // Abajo: progreso del clip y la firma.
        dibujarPie(c, tMs, margen, h - 120f * e, w - 2 * margen)
    }

    // ── Horizontal (16:9): caratula a la izquierda, letra a la derecha ────────

    private fun dibujarHorizontal(c: Canvas, tMs: Long, posMs: Long, golpe: Float) {
        val w = ancho.toFloat(); val h = alto.toFloat()
        val margen = 80f * e
        val hayLetra = opciones.conLetra && !letra.isNullOrEmpty()

        val lado = 0.62f * h
        val xCaratula = if (hayLetra) margen + 40f * e else (w - lado) / 2f
        val yCaratula = (h - lado) / 2f - 20f * e
        dibujarCaratula(c, xCaratula, yCaratula, lado, golpe)

        // Titulo y artista, arriba a la derecha (o centrados sin letra).
        tituloPaint.textAlign = if (hayLetra) Paint.Align.LEFT else Paint.Align.CENTER
        artistaPaint.textAlign = tituloPaint.textAlign
        val xTexto = if (hayLetra) xCaratula + lado + 70f * e else w / 2f
        c.drawText(recortar(titulo, tituloPaint, w - xTexto - margen), xTexto, 120f * e, tituloPaint)
        c.drawText(recortar(artista, artistaPaint, w - xTexto - margen), xTexto, 168f * e, artistaPaint)
        tituloPaint.textAlign = Paint.Align.CENTER
        artistaPaint.textAlign = Paint.Align.CENTER

        if (hayLetra) {
            val anchoLetra = w - xTexto - margen
            dibujarLetra(c, posMs, xTexto, 240f * e, anchoLetra, Layout.Alignment.ALIGN_NORMAL, h - 160f * e)
        }

        dibujarPie(c, tMs, margen, h - 90f * e, w - 2 * margen)
    }

    // ── Piezas ────────────────────────────────────────────────────────────────

    private fun dibujarCaratula(c: Canvas, x: Float, y: Float, lado: Float, golpe: Float) {
        // De serie quieta; si el usuario lo pide, late con los graves.
        val escala = if (opciones.caratulaLate) 1f + golpe * 0.06f else 1f
        val l = lado * escala
        val dx = x - (l - lado) / 2f
        val dy = y - (l - lado) / 2f
        marco.set(dx, dy, dx + l, dy + l)
        recorte.reset()
        recorte.addRoundRect(marco, 40f * e, 40f * e, Path.Direction.CW)
        c.save()
        c.clipPath(recorte)
        val bmp = caratula
        if (bmp != null) {
            c.drawBitmap(bmp, null, marco, caratulaPaint)
        } else {
            c.drawRect(marco, Paint().apply { color = 0xFF222222.toInt() })
        }
        c.restore()
    }

    private fun dibujarPie(c: Canvas, tMs: Long, x: Float, y: Float, w: Float) {
        val total = opciones.duracionSeg * 1000f
        val f = (tMs / total).coerceIn(0f, 1f)
        val grosor = 4f * e
        c.drawRoundRect(x, y, x + w, y + grosor, grosor, grosor, progresoFondo)
        c.drawRoundRect(x, y, x + w * f, y + grosor, grosor, grosor, progresoFrente)
        c.drawText("Life Music", x, y + 62f * e, firmaPaint)
    }

    /**
     * Tres lineas: la anterior y la siguiente tenues, la actual grande con las
     * palabras ya cantadas en blanco y las que vienen apagadas. Si la letra trae
     * tiempos por palabra se usan; si no, se reparte por longitud, como Life Line.
     */
    private fun dibujarLetra(
        c: Canvas, posMs: Long, x: Float, top: Float, w: Float,
        alineacion: Layout.Alignment, limiteInferior: Float,
    ) {
        val entradas = letra ?: return
        var i = entradas.indexOfLast { it.time <= posMs }
        if (i < 0) i = 0
        val actual = entradas[i]
        val siguiente = entradas.getOrNull(i + 1)
        val anterior = entradas.getOrNull(i - 1)

        val anchoInt = w.toInt().coerceAtLeast(10)
        var y = top

        anterior?.text?.takeIf { it.isNotBlank() }?.let {
            val l = capa(it, lineaTenuePaint, anchoInt, alineacion, 2)
            c.save(); c.translate(x, y); l.draw(c); c.restore()
            y += l.height + 26f * e
        }

        val texto = actual.text
        if (texto.isNotBlank()) {
            val fraccion = fraccionCantada(actual, siguiente, posMs)
            val corte = (texto.length * fraccion).toInt().coerceIn(0, texto.length)
            val span = SpannableString(texto)
            if (corte < texto.length) {
                span.setSpan(ForegroundColorSpan(colorApagado), corte, texto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val l = capa(span, lineaVivaPaint, anchoInt, alineacion, 3)
            if (y + l.height < limiteInferior) {
                c.save(); c.translate(x, y); l.draw(c); c.restore()
                y += l.height + 26f * e
            }
        }

        siguiente?.text?.takeIf { it.isNotBlank() }?.let {
            val l = capa(it, lineaTenuePaint, anchoInt, alineacion, 2)
            if (y + l.height < limiteInferior) {
                c.save(); c.translate(x, y); l.draw(c); c.restore()
            }
        }
    }

    /** Cuanto de la linea actual va cantado, 0..1. */
    private fun fraccionCantada(actual: LyricsEntry, siguiente: LyricsEntry?, posMs: Long): Float {
        val palabras = actual.words?.takeIf { it.isNotEmpty() }
        if (palabras != null) {
            val s = posMs / 1000.0
            var caracteres = 0
            var cantados = 0
            for (p in palabras) {
                val n = p.text.length + 1
                caracteres += n
                when {
                    s >= p.endTime -> cantados += n
                    s > p.startTime -> {
                        val f = ((s - p.startTime) / (p.endTime - p.startTime).coerceAtLeast(0.01)).coerceIn(0.0, 1.0)
                        cantados += (n * f).toInt()
                    }
                }
            }
            return if (caracteres == 0) 0f else cantados.toFloat() / caracteres
        }
        val inicio = actual.time
        val fin = siguiente?.time ?: (inicio + 4_000L)
        if (fin <= inicio) return 1f
        return ((posMs - inicio).toFloat() / (fin - inicio)).coerceIn(0f, 1f)
    }

    private fun capa(texto: CharSequence, paint: TextPaint, ancho: Int, alineacion: Layout.Alignment, maxLineas: Int): StaticLayout =
        StaticLayout.Builder.obtain(texto, 0, texto.length, paint, ancho)
            .setAlignment(alineacion)
            .setLineSpacing(0f, 1.08f)
            .setMaxLines(maxLineas)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

    private fun recortar(texto: String, paint: TextPaint, ancho: Float): String =
        android.text.TextUtils.ellipsize(texto, paint, ancho, android.text.TextUtils.TruncateAt.END).toString()
}
