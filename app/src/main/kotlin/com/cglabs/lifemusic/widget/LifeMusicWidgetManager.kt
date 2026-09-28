package com.cglabs.lifemusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cglabs.lifemusic.ActividadPrincipal
import com.cglabs.lifemusic.MainActivity
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.LiquidGlassBlurRadiusKey
import com.cglabs.lifemusic.constants.LiquidGlassChromaticAberrationKey
import com.cglabs.lifemusic.constants.LiquidGlassDepthEffectKey
import com.cglabs.lifemusic.constants.LiquidGlassLensAmountKey
import com.cglabs.lifemusic.constants.LiquidGlassLensHeightKey
import com.cglabs.lifemusic.constants.LiquidGlassSurfaceOpacityKey
import com.cglabs.lifemusic.constants.LiquidGlassSurfaceTintColorKey
import com.cglabs.lifemusic.constants.LiquidGlassVibrancyKey
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Los widgets de Life Music: Ambiente (2x2, 4x1 y 4x2), Cristal liquido, Tocadiscos
 * y Tu musica. Sustituye a los heredados de Echo (morado generico).
 *
 * Como se actualizan, que era el problema de antes:
 *  - Solo cuando algo cambia (cancion, play/pausa, me gusta, tamaño), no 5 veces
 *    por segundo. La barra de progreso va aparte ([actualizarProgreso], cada 2 s,
 *    una actualizacion parcial de un solo numero).
 *  - Las imagenes (fondo, cristal, vinilo, caratula) se pintan una vez por cancion y
 *    tamaño, y solo viajan al escritorio la primera vez: si ya las tiene, se le
 *    manda una actualizacion parcial con textos, iconos y colores.
 */
@Singleton
class LifeMusicWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val imageLoader by lazy { ImageLoader.Builder(context).build() }
    private val densidad get() = context.resources.displayMetrics.density

    private var portadaUri: String? = null
    private var portada: Bitmap? = null
    private var colores: ColoresDeWidget = PintorDeWidgets.colores(null)

    /** Imagenes ya pintadas para la caratula actual (clave: tipo y tamaño). */
    private val pintadas = HashMap<String, Bitmap>()

    /** Que imagenes tiene ya cada widget en el escritorio (id → clave). */
    private val enviadas = HashMap<Int, String>()
    private val cerrojo = Mutex()

    private class Estado(
        val titulo: String,
        val artista: String,
        val sonando: Boolean,
        val meGusta: Boolean,
        val duracion: Long,
        val posicion: Long,
    )

    private enum class Forma { FILA, CUADRO, COMPLETO }

    suspend fun updateWidgets(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
    ) = cerrojo.withLock {
        val awm = AppWidgetManager.getInstance(context)
        val ambiente = ids(awm, MusicWidgetReceiver::class.java)
        val cristal = ids(awm, CristalWidgetReceiver::class.java)
        val vinilo = ids(awm, TurntableWidgetReceiver::class.java)
        val tuMusica = ids(awm, PlaylistWidgetReceiver::class.java)
        // Sin widgets en el escritorio no se carga ni la caratula.
        if (ambiente.isEmpty() && cristal.isEmpty() && vinilo.isEmpty() && tuMusica.isEmpty()) return@withLock

        if (artworkUri != portadaUri || (artworkUri != null && portada == null)) {
            portadaUri = artworkUri
            portada = artworkUri?.let { cargarPortada(it) }
            colores = withContext(Dispatchers.Default) { PintorDeWidgets.colores(portada) }
            pintadas.clear()
            enviadas.clear()
        }
        val e = Estado(title, artist, isPlaying, isLiked, duration, currentPosition)

        for (id in ambiente) {
            val op = awm.getAppWidgetOptions(id)
            val forma = formaAmbiente(op)
            val (w, h) = tamanoDp(op, 320, 160)
            enviar(awm, id, "amb|$forma|$w|$h|$portadaUri") { conImagenes -> vistaAmbiente(forma, w, h, e, conImagenes) }
        }
        for (id in cristal) {
            val (w, h) = tamanoDp(awm.getAppWidgetOptions(id), 320, 170)
            enviar(awm, id, "cri|$w|$h|$portadaUri") { conImagenes -> vistaCristal(w, h, e, conImagenes) }
        }
        for (id in vinilo) {
            val (w, h) = tamanoDp(awm.getAppWidgetOptions(id), 150, 150)
            enviar(awm, id, "vin|$w|$h|$portadaUri") { conImagenes -> vistaVinilo(w, h, e, conImagenes) }
        }
        for (id in tuMusica) {
            val (w, h) = tamanoDp(awm.getAppWidgetOptions(id), 320, 160)
            enviar(awm, id, "tum|$w|$h|$portadaUri") { conImagenes ->
                val fondo = if (conImagenes && portada != null) {
                    pintada("amb|$w|$h") { PintorDeWidgets.fondoAmbiente(portada, colores, px(w), px(h), px(24).toFloat()) }
                } else {
                    null
                }
                vistaTuMusica(context, fondo, colores.acento)
            }
        }
    }

    /**
     * Solo la barra: una actualizacion parcial de un numero, sin imagenes. La llama
     * MusicService cada 2 s mientras suena.
     */
    fun actualizarProgreso(posicion: Long, duracion: Long) {
        if (duracion <= 0) return
        val awm = AppWidgetManager.getInstance(context)
        val ambiente = ids(awm, MusicWidgetReceiver::class.java)
        if (ambiente.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.widget_ambiente)
        v.setProgressBar(R.id.widget_progreso, 1000, nivel(posicion, duracion), false)
        runCatching { awm.partiallyUpdateAppWidget(ambiente, v) }
    }

    // ── Vistas ──────────────────────────────────────────────────────────────────

    private suspend fun vistaAmbiente(forma: Forma, w: Int, h: Int, e: Estado, conImagenes: Boolean): RemoteViews {
        val layout = when (forma) {
            Forma.FILA -> R.layout.widget_ambiente_fila
            Forma.CUADRO -> R.layout.widget_ambiente_cuadro
            Forma.COMPLETO -> R.layout.widget_ambiente
        }
        val v = RemoteViews(context.packageName, layout)
        if (conImagenes) {
            v.setImageViewBitmap(R.id.widget_fondo, pintada("amb|$w|$h") { PintorDeWidgets.fondoAmbiente(portada, colores, px(w), px(h), px(24).toFloat()) })
            val lado = when (forma) {
                Forma.FILA -> 46
                Forma.CUADRO -> (min(w, h) - 20).coerceAtLeast(60)
                Forma.COMPLETO -> 72
            }
            ponerCaratula(v, lado, if (forma == Forma.CUADRO) 18 else 14)
        }
        ponerTextos(v, e)
        ponerPlay(v, e.sonando)
        ponerMeGusta(v, e.meGusta)
        v.setProgressBar(R.id.widget_progreso, 1000, nivel(e.posicion, e.duracion), false)
        ponerControles(v)
        return v
    }

    private suspend fun vistaCristal(w: Int, h: Int, e: Estado, conImagenes: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_cristal)
        if (conImagenes) {
            // Hasta 820 px de ancho: de ahi en adelante no se nota y la imagen pesa el doble.
            val anchoReal = px(w)
            val escala = min(1f, 820f / anchoReal)
            val ancho = (anchoReal * escala).roundToInt()
            val alto = (px(h) * escala).roundToInt()
            val d = densidad * escala
            val capsula = RectF(10 * d, alto - 10 * d - 76 * d, ancho - 10 * d, alto - 10 * d)
            val ajustes = ajustesCristal()
            v.setImageViewBitmap(
                R.id.widget_fondo,
                pintada("cri|$w|$h") { PintorDeWidgets.cristal(portada, colores, ancho, alto, capsula, 38 * d, 24 * d, ajustes, d) },
            )
            ponerCaratula(v, 54, 14)
        }
        ponerTextos(v, e)
        ponerPlay(v, e.sonando)
        ponerMeGusta(v, e.meGusta)
        ponerControles(v)
        return v
    }

    private suspend fun vistaVinilo(w: Int, h: Int, e: Estado, conImagenes: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_vinilo)
        if (conImagenes) {
            val lado = min(px(min(w, h).coerceAtLeast(100)), 520)
            v.setImageViewBitmap(R.id.widget_vinilo, pintada("vin|$lado") { PintorDeWidgets.vinilo(portada, colores, lado) })
        }
        ponerPlay(v, e.sonando)
        v.setOnClickPendingIntent(R.id.widget_vinilo, abrirApp(context))
        ponerControles(v)
        return v
    }

    private fun ponerCaratula(v: RemoteViews, ladoDp: Int, radioDp: Int) {
        val p = portada
        if (p == null) {
            v.setImageViewResource(R.id.widget_caratula, R.drawable.widget_caratula_inicial)
        } else {
            v.setImageViewBitmap(R.id.widget_caratula, pintadaSincrona("car|$ladoDp") { PintorDeWidgets.caratulaRedondeada(p, colores, px(ladoDp), px(radioDp).toFloat()) })
        }
    }

    private fun ponerTextos(v: RemoteViews, e: Estado) {
        v.setTextViewText(R.id.widget_titulo, e.titulo)
        v.setTextViewText(R.id.widget_artista, e.artista)
        val estado = context.getString(if (e.sonando) R.string.widget_sonando else R.string.widget_en_pausa)
        v.setTextViewText(R.id.widget_saludo, "${cabeceraDeLaHora(context)} · $estado")
        v.setTextColor(R.id.widget_saludo, colores.acento)
    }

    /** El play de Expressive: cuadrado suave mientras suena, redondo en pausa, del color de la caratula. */
    private fun ponerPlay(v: RemoteViews, sonando: Boolean) {
        v.setImageViewResource(R.id.widget_fondo_play, if (sonando) R.drawable.widget_play_cuadrado else R.drawable.widget_play_redondo)
        v.setInt(R.id.widget_fondo_play, "setColorFilter", colores.acento)
        v.setImageViewResource(R.id.widget_icono_play, if (sonando) R.drawable.pause else R.drawable.play)
        v.setInt(R.id.widget_icono_play, "setColorFilter", colores.sobreAcento)
    }

    private fun ponerMeGusta(v: RemoteViews, meGusta: Boolean) {
        v.setImageViewResource(R.id.widget_icono_me_gusta, if (meGusta) R.drawable.favorite else R.drawable.favorite_border)
        v.setInt(R.id.widget_icono_me_gusta, "setColorFilter", if (meGusta) colores.acento else Color.WHITE)
    }

    /** Los botones: si el layout no tiene alguno, el escritorio lo ignora. */
    private fun ponerControles(v: RemoteViews) {
        v.setOnClickPendingIntent(R.id.widget_raiz, abrirApp(context))
        v.setOnClickPendingIntent(R.id.widget_caratula, abrirApp(context))
        v.setOnClickPendingIntent(R.id.widget_boton_play, difusion(MusicWidgetReceiver.ACTION_PLAY_PAUSE, 11))
        v.setOnClickPendingIntent(R.id.widget_boton_me_gusta, difusion(MusicWidgetReceiver.ACTION_LIKE, 12))
        v.setOnClickPendingIntent(R.id.widget_boton_anterior, difusion(MusicWidgetReceiver.ACTION_PREVIOUS, 13))
        v.setOnClickPendingIntent(R.id.widget_boton_siguiente, difusion(MusicWidgetReceiver.ACTION_NEXT, 14))
    }

    // ── Utilidades ──────────────────────────────────────────────────────────────

    private suspend fun enviar(awm: AppWidgetManager, id: Int, clave: String, construir: suspend (Boolean) -> RemoteViews) {
        runCatching {
            if (enviadas[id] == clave) {
                awm.partiallyUpdateAppWidget(id, construir(false))
            } else {
                awm.updateAppWidget(id, construir(true))
                enviadas[id] = clave
            }
        }.onFailure {
            // A la vista en el registro (tambien en la version publicada): un fallo aqui
            // deja el widget sin pintar y, callado, no hay forma de saber por que.
            android.util.Log.w(ETIQUETA, "No se pudo actualizar el widget $id ($clave)", it)
        }
    }

    private suspend fun pintada(clave: String, pintar: () -> Bitmap): Bitmap =
        pintadas[clave] ?: withContext(Dispatchers.Default) { pintar() }.also { pintadas[clave] = it }

    private fun pintadaSincrona(clave: String, pintar: () -> Bitmap): Bitmap =
        pintadas[clave] ?: pintar().also { pintadas[clave] = it }

    private suspend fun cargarPortada(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            imageLoader.execute(
                ImageRequest.Builder(context).data(uri).size(512, 512).allowHardware(false).build()
            ).image?.toBitmap()
        }.getOrNull()
    }

    private fun ajustesCristal(): AjustesCristal {
        val ds = context.dataStore
        return runCatching {
            AjustesCristal(
                opacidad = ds.get(LiquidGlassSurfaceOpacityKey, 0.4f),
                tinte = ds.get(LiquidGlassSurfaceTintColorKey, 0),
                vibrancia = ds.get(LiquidGlassVibrancyKey, 1f),
                lente = ds.get(LiquidGlassLensAmountKey, 0.5f),
                altura = ds.get(LiquidGlassLensHeightKey, 0.5f),
                aberracion = ds.get(LiquidGlassChromaticAberrationKey, true),
                profundidad = ds.get(LiquidGlassDepthEffectKey, true),
                desenfoqueDp = ds.get(LiquidGlassBlurRadiusKey, 8f),
            )
        }.getOrDefault(AjustesCristal())
    }

    private fun px(dp: Int): Int = (dp * densidad).roundToInt().coerceAtLeast(1)

    private fun <T> ids(awm: AppWidgetManager, clase: Class<T>): IntArray =
        runCatching { awm.getAppWidgetIds(ComponentName(context, clase)) }.getOrDefault(IntArray(0))

    private fun difusion(accion: String, codigo: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        codigo,
        Intent(context, MusicWidgetReceiver::class.java).setAction(accion),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Ancho (retrato) y alto (retrato) del widget en dp, segun el escritorio; si no lo
     * dice, los de su tamaño tipico.
     */
    private fun tamanoDp(op: Bundle, anchoPorDefecto: Int, altoPorDefecto: Int): Pair<Int, Int> {
        val w = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: anchoPorDefecto
        val h = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: altoPorDefecto
        return w to h
    }

    /** Una fila (4x1), un cuadro (2x2) o el completo (4x2 y mas), segun el tamaño real. */
    private fun formaAmbiente(op: Bundle): Forma {
        val w = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return when {
            h in 1 until 110 -> Forma.FILA
            w in 1 until 220 -> Forma.CUADRO
            else -> Forma.COMPLETO
        }
    }

    private fun nivel(posicion: Long, duracion: Long): Int =
        if (duracion > 0) (posicion * 1000 / duracion).toInt().coerceIn(0, 1000) else 0

    companion object {
        private const val ETIQUETA = "LifeMusicWidgets"

        /** «Buenos dias», «Buenas tardes» o «Buenas noches», con las franjas del saludo de la app. */
        fun cabeceraDeLaHora(context: Context): String {
            val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return context.getString(
                when (hora) {
                    in 5..11 -> R.string.greeting_morning
                    in 12..18 -> R.string.greeting_afternoon
                    else -> R.string.greeting_evening
                }
            )
        }

        fun abrirApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, ActividadPrincipal.clase),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * El widget Tu musica: el saludo y cuatro accesos. Se puede montar sin el
         * servicio (lo hace el receptor al ponerlo en el escritorio); con musica
         * sonando, el servicio le pone el fondo de la caratula.
         */
        fun vistaTuMusica(context: Context, fondo: Bitmap?, acento: Int): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_tu_musica)
            if (fondo != null) v.setImageViewBitmap(R.id.widget_fondo, fondo)
            v.setTextViewText(R.id.widget_saludo, cabeceraDeLaHora(context))
            v.setTextColor(R.id.widget_saludo, acento)
            listOf(R.id.widget_icono_mezcla, R.id.widget_icono_me_gusta, R.id.widget_icono_descargas, R.id.widget_icono_buscar)
                .forEach { v.setInt(it, "setColorFilter", acento) }
            v.setOnClickPendingIntent(R.id.widget_raiz, abrirApp(context))
            v.setOnClickPendingIntent(
                R.id.widget_tesela_mezcla,
                PendingIntent.getBroadcast(
                    context, 20,
                    Intent(context, PlaylistWidgetReceiver::class.java).setAction(PlaylistWidgetReceiver.ACTION_MEZCLAR),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            v.setOnClickPendingIntent(R.id.widget_tesela_me_gusta, abrirEn(context, MainActivity.ACTION_ME_GUSTA, 21))
            v.setOnClickPendingIntent(R.id.widget_tesela_descargas, abrirEn(context, MainActivity.ACTION_DESCARGAS, 22))
            v.setOnClickPendingIntent(R.id.widget_tesela_buscar, abrirEn(context, MainActivity.ACTION_SEARCH, 23))
            return v
        }

        private fun abrirEn(context: Context, accion: String, codigo: Int): PendingIntent = PendingIntent.getActivity(
            context,
            codigo,
            Intent(context, ActividadPrincipal.clase).setAction(accion),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
