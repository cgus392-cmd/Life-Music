package com.cglabs.lifemusic.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.cglabs.lifemusic.ui.theme.extractThemeColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Los colores de un widget, sacados de la caratula (como el tema de la app). */
class ColoresDeWidget(
    /** Botones principales (play), la barra y la linea del saludo: claro y vivo. */
    val acento: Int,
    /** Iconos sobre el acento: oscuro del mismo tono. */
    val sobreAcento: Int,
    /** El fondo cuando no hay caratula o mientras llega: muy oscuro del mismo tono. */
    val fondo: Int,
)

/**
 * El Liquid Glass del usuario (Apariencia → Liquid Glass), el mismo que usa el TV:
 * el widget de cristal lo respeta.
 */
class AjustesCristal(
    val opacidad: Float = 0.4f,
    val tinte: Int = 0,
    val vibrancia: Float = 1f,
    val lente: Float = 0.5f,
    val altura: Float = 0.5f,
    val aberracion: Boolean = true,
    val profundidad: Boolean = true,
    val desenfoqueDp: Float = 8f,
)

/**
 * Dibuja, pixel a pixel y en la CPU, lo que los widgets no pueden hacer solos: un
 * widget es un RemoteViews, sin sombreadores, sin desenfoque y sin acceso a lo que
 * tiene detras. Asi que el fondo ambiente, el cristal liquido y el vinilo se
 * pintan aqui como imagenes, una vez por cancion (no por fotograma).
 */
object PintorDeWidgets {
    private const val VERDE_LIFE = 0xFF34B37D.toInt()

    /** Igual que GlassEffect.LENS_MAX_DP: 0,5 en los ajustes son 24 dp, como el cristal de Apple. */
    private const val LENTE_MAX_DP = 48f

    fun colores(portada: Bitmap?): ColoresDeWidget {
        val semilla = portada?.let { runCatching { it.extractThemeColor().toArgb() }.getOrNull() } ?: VERDE_LIFE
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(semilla, hsl)
        val s = hsl[1]
        return ColoresDeWidget(
            acento = ColorUtils.HSLToColor(floatArrayOf(hsl[0], s.coerceIn(0.35f, 0.9f), 0.76f)),
            sobreAcento = ColorUtils.HSLToColor(floatArrayOf(hsl[0], s.coerceIn(0.3f, 0.8f), 0.13f)),
            fondo = ColorUtils.HSLToColor(floatArrayOf(hsl[0], (s * 0.6f).coerceIn(0.15f, 0.6f), 0.12f)),
        )
    }

    /**
     * El fondo ambiente: la caratula ampliada, desenfocada y saturada, con un velo
     * oscuro para que el texto blanco se lea. Se pinta pequeño (220 px de ancho): el
     * widget lo estira y, siendo ya borroso, no se nota; asi la imagen que viaja al
     * escritorio pesa ~70 KB y no 2 MB. Las esquinas van ya redondeadas.
     */
    fun fondoAmbiente(portada: Bitmap?, colores: ColoresDeWidget, anchoPx: Int, altoPx: Int, radioPx: Float): Bitmap {
        val w = 220
        val h = max(1, (w.toFloat() * altoPx / max(1, anchoPx)).roundToInt())
        var base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(base)
        c.drawColor(colores.fondo)
        if (portada != null) {
            val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = saturacion(1.5f) }
            dibujarRecortada(c, portada, RectF(-w * 0.2f, -h * 0.2f, w * 1.2f, h * 1.2f), p)
            base = desenfocar(base, radio = 9, pasadas = 3)
        } else {
            manchasDeEspera(Canvas(base), colores, w, h)
        }
        Canvas(base).drawRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x55000000, 0x99000000.toInt(), Shader.TileMode.CLAMP) },
        )
        return redondear(base, radioPx * w / max(1, anchoPx))
    }

    /** La caratula cuadrada con las esquinas redondeadas (o una hoja de color si no hay). */
    fun caratulaRedondeada(portada: Bitmap?, colores: ColoresDeWidget, ladoPx: Int, radioPx: Float): Bitmap {
        val b = Bitmap.createBitmap(ladoPx, ladoPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (portada != null) {
            dibujarRecortada(c, portada, RectF(0f, 0f, ladoPx.toFloat(), ladoPx.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawColor(colores.acento)
        }
        return redondear(b, radioPx)
    }

    /**
     * El cristal liquido del mini reproductor, portado a la CPU desde su sombreador
     * (backdrop/internal/Shaders.kt, RoundedRectRefraction): detras, la caratula a
     * sangre; encima, una capsula que desenfoca y satura lo que tiene debajo,
     * lo refracta en el borde (perfil circular, hacia dentro, con profundidad),
     * separa los colores como un prisma (aberracion), lo tiñe como el cristal de la
     * app y le pone el filo de luz y la sombra. Con los mismos ajustes del usuario.
     */
    fun cristal(
        portada: Bitmap?,
        colores: ColoresDeWidget,
        anchoPx: Int,
        altoPx: Int,
        capsula: RectF,
        radioCapsulaPx: Float,
        radioWidgetPx: Float,
        ajustes: AjustesCristal,
        densidad: Float,
    ): Bitmap {
        // La escena: lo que hay «detras» del cristal.
        val escena = Bitmap.createBitmap(anchoPx, altoPx, Bitmap.Config.ARGB_8888)
        Canvas(escena).apply {
            drawColor(colores.fondo)
            if (portada != null) {
                dibujarRecortada(this, portada, RectF(0f, 0f, anchoPx.toFloat(), altoPx.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                manchasDeEspera(this, colores, anchoPx, altoPx)
            }
        }

        // Lo que se ve a traves: desenfocado y saturado (vibrancia), a un cuarto de
        // resolucion; se lee con interpolacion bilineal.
        val factor = 4
        val bw = max(1, anchoPx / factor)
        val bh = max(1, altoPx / factor)
        val saturada = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        Canvas(saturada).drawBitmap(
            escena,
            Rect(0, 0, anchoPx, altoPx),
            RectF(0f, 0f, bw.toFloat(), bh.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = saturacion(1f + 0.5f * ajustes.vibrancia.coerceIn(0f, 2f)) },
        )
        val radioBlur = max(1, (ajustes.desenfoqueDp.coerceAtLeast(2f) * densidad / factor).roundToInt())
        val borrosa = desenfocar(saturada, radioBlur, 3)
        val bpx = IntArray(bw * bh)
        borrosa.getPixels(bpx, 0, bw, 0, 0, bw, bh)

        val px = IntArray(anchoPx * altoPx)
        escena.getPixels(px, 0, anchoPx, 0, 0, anchoPx, altoPx)

        val cx = capsula.centerX()
        val cy = capsula.centerY()
        val hx = capsula.width() / 2f
        val hy = capsula.height() / 2f
        val r = min(radioCapsulaPx, min(hx, hy))
        val gradR = min(r * 1.5f, min(hx, hy))
        val alturaLente = max(1f, ajustes.altura * LENTE_MAX_DP * densidad)
        val cantidadLente = ajustes.lente * LENTE_MAX_DP * densidad
        val tinte = if (ajustes.tinte != 0) ajustes.tinte else 0xFF121212.toInt()
        val tr = (tinte shr 16) and 0xFF
        val tg = (tinte shr 8) and 0xFF
        val tb = tinte and 0xFF
        val op = ajustes.opacidad.coerceIn(0f, 1f)
        val filo = 1.4f * densidad
        val sombra = 12f * densidad
        val sombraDy = 3f * densidad
        val escalaB = 1f / factor

        for (y in 0 until altoPx) {
            for (x in 0 until anchoPx) {
                val qx = x + 0.5f - cx
                val qy = y + 0.5f - cy
                val i = y * anchoPx + x
                val sd = sdRectRedondeado(qx, qy, hx, hy, r)
                if (sd > 0f) {
                    // Fuera: solo la sombra que el cristal proyecta un poco mas abajo.
                    val sds = sdRectRedondeado(qx, qy - sombraDy, hx, hy, r)
                    if (sds < sombra) {
                        val a = 1f - max(0f, sds) / sombra
                        px[i] = oscurecer(px[i], a * a * 0.38f)
                    }
                    continue
                }
                val prof = -sd
                // Normal hacia fuera (gradiente del SDF con el radio suavizado, mas la
                // profundidad, que la inclina hacia el centro como una lente gruesa).
                val ax = abs(qx) - (hx - gradR)
                val ay = abs(qy) - (hy - gradR)
                var gx: Float
                var gy: Float
                if (ax >= 0f || ay >= 0f) {
                    val mx = max(ax, 0f)
                    val my = max(ay, 0f)
                    val l = sqrt(mx * mx + my * my)
                    gx = if (l > 0f) mx / l else 0f
                    gy = if (l > 0f) my / l else 0f
                } else {
                    val paso = if (ax >= ay) 1f else 0f
                    gx = paso
                    gy = 1f - paso
                }
                gx *= if (qx < 0f) -1f else 1f
                gy *= if (qy < 0f) -1f else 1f
                if (ajustes.profundidad) {
                    val lq = sqrt(qx * qx + qy * qy)
                    if (lq > 0f) {
                        gx += qx / lq
                        gy += qy / lq
                    }
                }
                val lg = sqrt(gx * gx + gy * gy)
                if (lg > 0f) {
                    gx /= lg
                    gy /= lg
                }

                var rr: Float
                var gg: Float
                var bb: Float
                if (prof < alturaLente && cantidadLente > 0f) {
                    // circleMap del sombreador: el borde dobla la luz como un canto redondo.
                    val t = 1f - prof / alturaLente
                    val d = (1f - sqrt(max(0f, 1f - t * t))) * cantidadLente
                    val sx = (x - d * gx) * escalaB
                    val sy = (y - d * gy) * escalaB
                    if (ajustes.aberracion) {
                        val intensidad = (qx * qy) / (hx * hy)
                        val dx = -d * gx * intensidad * escalaB
                        val dy = -d * gy * intensidad * escalaB
                        rr = bilineal(bpx, bw, bh, sx + dx, sy + dy, 16)
                        gg = bilineal(bpx, bw, bh, sx, sy, 8)
                        bb = bilineal(bpx, bw, bh, sx - dx, sy - dy, 0)
                    } else {
                        rr = bilineal(bpx, bw, bh, sx, sy, 16)
                        gg = bilineal(bpx, bw, bh, sx, sy, 8)
                        bb = bilineal(bpx, bw, bh, sx, sy, 0)
                    }
                } else {
                    val sx = x * escalaB
                    val sy = y * escalaB
                    rr = bilineal(bpx, bw, bh, sx, sy, 16)
                    gg = bilineal(bpx, bw, bh, sx, sy, 8)
                    bb = bilineal(bpx, bw, bh, sx, sy, 0)
                }
                // El tinte de la superficie (oscuro por defecto, como en la app).
                rr = rr * (1f - op) + tr * op
                gg = gg * (1f - op) + tg * op
                bb = bb * (1f - op) + tb * op
                // Un velo de luz en la mitad de arriba: el cristal recoge la luz.
                val arriba = 1f - ((qy + hy) / (2f * hy)).coerceIn(0f, 1f)
                val luzSup = 0.06f * arriba * arriba
                rr += (255f - rr) * luzSup
                gg += (255f - gg) * luzSup
                bb += (255f - bb) * luzSup
                // El filo: brilla donde da la luz (arriba a la izquierda) y un poco en
                // el lado contrario, como el Highlight del mini reproductor.
                if (prof < filo) {
                    val cara = -(gx * 0.55f + gy * 0.83f)
                    val luz = if (cara > 0f) cara * cara else 0f
                    val contra = if (cara < 0f) -cara * 0.3f else 0f
                    val f = (1f - prof / filo) * (0.22f + 0.7f * luz + contra)
                    rr += (255f - rr) * f
                    gg += (255f - gg) * f
                    bb += (255f - bb) * f
                }
                px[i] = (0xFF shl 24) or (rr.toInt().coerceIn(0, 255) shl 16) or (gg.toInt().coerceIn(0, 255) shl 8) or bb.toInt().coerceIn(0, 255)
            }
        }
        escena.setPixels(px, 0, anchoPx, 0, 0, anchoPx, altoPx)
        return redondear(escena, radioWidgetPx)
    }

    /**
     * El tocadiscos: un vinilo con sus surcos, dos reflejos de luz y la caratula de
     * etiqueta en el centro, con el agujero del eje. Como el tema Vinilo del TV.
     */
    fun vinilo(portada: Bitmap?, colores: ColoresDeWidget, ladoPx: Int): Bitmap {
        val b = Bitmap.createBitmap(ladoPx, ladoPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val cx = ladoPx / 2f
        val cy = ladoPx / 2f
        val radio = ladoPx / 2f * 0.98f
        val disco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, radio, intArrayOf(0xFF1C1C1E.toInt(), 0xFF0B0B0C.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, radio, disco)
        val surco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, ladoPx / 360f)
        }
        var rs = radio * 0.40f
        var k = 0
        val paso = max(2f, ladoPx / 150f)
        while (rs < radio * 0.95f) {
            surco.color = if (k % 3 == 0) 0x1EFFFFFF else 0x0DFFFFFF
            c.drawCircle(cx, cy, rs, surco)
            rs += paso
            k++
        }
        c.drawCircle(
            cx, cy, radio * 0.97f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = SweepGradient(
                    cx, cy,
                    intArrayOf(0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
                    floatArrayOf(0f, 0.10f, 0.22f, 0.5f, 0.6f, 0.72f, 1f),
                )
            },
        )
        c.drawCircle(
            cx, cy, radio - max(1f, ladoPx / 240f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1f, ladoPx / 200f)
                color = 0x33FFFFFF
            },
        )
        // La etiqueta: la caratula, con un aro del color de la cancion.
        val rEtiqueta = radio * 0.36f
        c.drawCircle(cx, cy, rEtiqueta + max(2f, ladoPx / 110f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colores.acento })
        c.save()
        c.clipPath(Path().apply { addCircle(cx, cy, rEtiqueta, Path.Direction.CW) })
        if (portada != null) {
            dibujarRecortada(c, portada, RectF(cx - rEtiqueta, cy - rEtiqueta, cx + rEtiqueta, cy + rEtiqueta), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawColor(colores.acento)
        }
        c.restore()
        c.drawCircle(cx, cy, radio * 0.035f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B0B0C.toInt() })
        return b
    }

    // ── Utilidades ──────────────────────────────────────────────────────────────

    private fun sdRectRedondeado(qx: Float, qy: Float, hx: Float, hy: Float, r: Float): Float {
        val ax = abs(qx) - (hx - r)
        val ay = abs(qy) - (hy - r)
        val mx = max(ax, 0f)
        val my = max(ay, 0f)
        return sqrt(mx * mx + my * my) - r + min(max(ax, ay), 0f)
    }

    /** Un canal (desplazamiento 16, 8 o 0) de [p] en (x, y) con interpolacion bilineal. */
    private fun bilineal(p: IntArray, w: Int, h: Int, x: Float, y: Float, canal: Int): Float {
        val fx = (x - 0.5f).coerceIn(0f, (w - 1).toFloat())
        val fy = (y - 0.5f).coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val tx = fx - x0
        val ty = fy - y0
        val a = (p[y0 * w + x0] shr canal) and 0xFF
        val b = (p[y0 * w + x1] shr canal) and 0xFF
        val c = (p[y1 * w + x0] shr canal) and 0xFF
        val d = (p[y1 * w + x1] shr canal) and 0xFF
        return (a + (b - a) * tx) * (1f - ty) + (c + (d - c) * tx) * ty
    }

    private fun oscurecer(color: Int, cuanto: Float): Int {
        val f = 1f - cuanto
        val r = (((color shr 16) and 0xFF) * f).toInt()
        val g = (((color shr 8) and 0xFF) * f).toInt()
        val b = ((color and 0xFF) * f).toInt()
        return (color and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    private fun saturacion(s: Float) = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(s) })

    /** Dibuja [bmp] llenando [destino] sin deformarlo (recorte centrado). */
    private fun dibujarRecortada(canvas: Canvas, bmp: Bitmap, destino: RectF, pintura: Paint) {
        val escala = max(destino.width() / bmp.width, destino.height() / bmp.height)
        val w = destino.width() / escala
        val h = destino.height() / escala
        val origen = Rect(
            ((bmp.width - w) / 2f).toInt(), ((bmp.height - h) / 2f).toInt(),
            ((bmp.width + w) / 2f).toInt(), ((bmp.height + h) / 2f).toInt(),
        )
        canvas.drawBitmap(bmp, origen, destino, pintura)
    }

    /** Sin caratula: dos luces del color de la app sobre su fondo. */
    private fun manchasDeEspera(c: Canvas, colores: ColoresDeWidget, w: Int, h: Int) {
        val luz = ColorUtils.setAlphaComponent(colores.acento, 90)
        c.drawCircle(w * 0.2f, h * 0.1f, w * 0.7f, Paint().apply {
            shader = RadialGradient(w * 0.2f, h * 0.1f, w * 0.7f, luz, 0, Shader.TileMode.CLAMP)
        })
        c.drawCircle(w * 0.9f, h * 1.0f, w * 0.5f, Paint().apply {
            shader = RadialGradient(w * 0.9f, h * 1.0f, w * 0.5f, ColorUtils.setAlphaComponent(colores.acento, 50), 0, Shader.TileMode.CLAMP)
        })
    }

    /** [bmp] con las esquinas redondeadas (lo de fuera, transparente). */
    private fun redondear(bmp: Bitmap, radio: Float): Bitmap {
        if (radio <= 0f) return bmp
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawRoundRect(
            RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), radio, radio,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) },
        )
        return out
    }

    /** Desenfoque de caja, horizontal y vertical, [pasadas] veces (tres ≈ gaussiano). */
    private fun desenfocar(bmp: Bitmap, radio: Int, pasadas: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val a = IntArray(w * h)
        bmp.getPixels(a, 0, w, 0, 0, w, h)
        val t = IntArray(w * h)
        repeat(pasadas) {
            caja(a, t, w, h, radio, horizontal = true)
            caja(t, a, w, h, radio, horizontal = false)
        }
        // Mutable: createBitmap(IntArray, ...) la devuelve inmutable, y el fondo
        // ambiente pinta el velo encima con un Canvas (IllegalStateException: el
        // widget Ambiente se quedaba sin actualizar).
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(a, 0, w, 0, 0, w, h) }
    }

    /** Una pasada de caja con ventana deslizante: O(pixeles), sin importar el radio. */
    private fun caja(de: IntArray, a: IntArray, w: Int, h: Int, radio: Int, horizontal: Boolean) {
        val largo = if (horizontal) w else h
        val lineas = if (horizontal) h else w
        val ventana = 2 * radio + 1
        for (linea in 0 until lineas) {
            fun idx(i: Int): Int {
                val j = i.coerceIn(0, largo - 1)
                return if (horizontal) linea * w + j else j * w + linea
            }
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -radio..radio) {
                val p = de[idx(k)]
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until largo) {
                a[if (horizontal) linea * w + i else i * w + linea] =
                    (0xFF shl 24) or ((sr / ventana) shl 16) or ((sg / ventana) shl 8) or (sb / ventana)
                val sale = de[idx(i - radio)]
                val entra = de[idx(i + radio + 1)]
                sr += ((entra shr 16) and 0xFF) - ((sale shr 16) and 0xFF)
                sg += ((entra shr 8) and 0xFF) - ((sale shr 8) and 0xFF)
                sb += (entra and 0xFF) - (sale and 0xFF)
            }
        }
    }
}
