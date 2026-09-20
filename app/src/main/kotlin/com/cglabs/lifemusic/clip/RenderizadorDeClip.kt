package com.cglabs.lifemusic.clip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.lyrics.LyricsEntry
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.playback.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Produce el clip: junta caratula y sus colores, letra, y el tramo de audio;
 * dibuja cada fotograma con LienzoDeClip y lo codifica con CodificadorDeClip.
 * Todo fuera de pantalla; 15 s de video salen en unos segundos.
 *
 * El fichero va a la cache de «compartir» (la misma que la tarjeta del reto),
 * servido por el FileProvider; el que reciba el intent lo copia.
 */
object RenderizadorDeClip {
    const val FPS = 30
    private const val TAG = "LifeMusicClip"

    class Etapa(val fraccion: Float, val texto: Int)

    suspend fun crear(
        context: Context,
        service: MusicService,
        cancion: MediaMetadata,
        letra: List<LyricsEntry>?,
        opciones: ClipOpciones,
        progreso: (Etapa) -> Unit,
        cancelado: () -> Boolean,
    ): File? = withContext(Dispatchers.Default) {
        val carpeta = File(context.cacheDir, "compartir").apply { mkdirs() }
        val salida = File(carpeta, "clip-life-music.mp4")
        val temporalAudio = File(context.cacheDir, "clip_audio.tmp")
        try {
            // 1. Caratula y colores.
            progreso(Etapa(0.02f, R.string.clip_etapa_caratula))
            val caratula = cargarCaratula(context, cancion.thumbnailUrl)
            val colores = caratula?.let { coloresDe(it) } ?: emptyList()
            if (cancelado()) return@withContext null

            // 2. Audio: envolventes siempre (para el latido), pista solo si se pidio.
            progreso(Etapa(0.10f, R.string.clip_etapa_audio))
            val totalFotogramas = opciones.duracionSeg * FPS
            var energia = FloatArray(totalFotogramas)
            var graves = FloatArray(totalFotogramas)
            var audioCodificado: AudioCodificado? = null
            val t0 = android.os.SystemClock.elapsedRealtime()
            val hayAudio = service.copiarAudioParaClip(cancion.id, temporalAudio)
            android.util.Log.i(TAG, "audio copiado=$hayAudio bytes=${temporalAudio.length()} en ${android.os.SystemClock.elapsedRealtime() - t0} ms")
            if (hayAudio && !cancelado()) {
                val tramo = AudioDelClip.decodificar(temporalAudio, opciones.desdeMs, opciones.duracionSeg * 1000L + 200L, cancelado)
                android.util.Log.i(TAG, "tramo decodificado=${tramo != null} muestras=${tramo?.pcm?.size ?: 0} sr=${tramo?.sampleRate} canales=${tramo?.canales}")
                if (tramo != null) {
                    val (e, g) = tramo.envolventes(FPS, totalFotogramas)
                    energia = e; graves = g
                    // Si la copia se corto y el tramo no llega ni al 90 %, mejor mudo que
                    // un clip cuyo audio se apaga a mitad.
                    val cobertura = tramo.pcm.size.toFloat() / (opciones.duracionSeg * tramo.sampleRate * tramo.canales)
                    if (opciones.conAudio && cobertura < 0.9f) {
                        android.util.Log.w(TAG, "tramo incompleto (${(cobertura * 100).toInt()} %), el clip sale sin audio")
                    } else if (opciones.conAudio) {
                        progreso(Etapa(0.22f, R.string.clip_etapa_audio))
                        audioCodificado = runCatching { tramo.codificarAac() }
                            .onFailure { android.util.Log.w(TAG, "AAC fallo", it) }
                            .getOrNull()
                        android.util.Log.i(TAG, "aac=${audioCodificado?.muestras?.size ?: 0} tramas")
                    }
                }
            }
            if (cancelado()) return@withContext null

            // 3. Fotogramas.
            val tipografia: Typeface? = runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull()
            val lienzo = LienzoDeClip(
                opciones = opciones,
                caratula = caratula,
                colores = colores,
                titulo = cancion.title,
                artista = cancion.artists.joinToString { it.name },
                letra = if (opciones.conLetra) letra else null,
                tipografia = tipografia,
            )
            val msPorFotograma = 1000f / FPS
            val t1 = android.os.SystemClock.elapsedRealtime()
            CodificadorDeClip(opciones.ancho, opciones.alto, FPS, salida).codificar(
                totalFotogramas = totalFotogramas,
                audio = audioCodificado,
                dibujar = { bitmap, i ->
                    val tMs = (i * msPorFotograma).toLong()
                    lienzo.dibujar(bitmap, tMs, opciones.desdeMs + tMs, energia[i], graves[i])
                },
                progreso = { f -> progreso(Etapa(0.3f + 0.7f * f, R.string.clip_etapa_video)) },
                cancelado = cancelado,
            )
            android.util.Log.i(TAG, "video ${totalFotogramas} fotogramas en ${android.os.SystemClock.elapsedRealtime() - t1} ms, ${salida.length()} bytes")
            if (cancelado() || !salida.exists() || salida.length() < 10_000) null else salida
        } catch (e: Exception) {
            android.util.Log.w(TAG, "no se pudo crear el clip", e)
            null
        } finally {
            temporalAudio.delete()
        }
    }

    /** Caratula y sus seis colores, para quien los necesite fuera del clip (el receptor de Cast). */
    suspend fun coloresDeCaratula(context: Context, url: String?): List<Int> =
        cargarCaratula(context, url)?.let { coloresDe(it) } ?: emptyList()

    private suspend fun cargarCaratula(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(1000, 1000)
            .allowHardware(false)
            .build()
        val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
        return result?.image?.toBitmap()
    }

    /** Los mismos seis colores que usa el fondo del modo ambiente. */
    private fun coloresDe(bitmap: Bitmap): List<Int> {
        val fallback = 0xFF444444.toInt()
        val palette = Palette.from(bitmap).maximumColorCount(8).resizeBitmapArea(100 * 100).generate()
        return listOf(
            palette.getVibrantColor(fallback),
            palette.getLightVibrantColor(fallback),
            palette.getDarkVibrantColor(fallback),
            palette.getMutedColor(fallback),
            palette.getLightMutedColor(fallback),
            palette.getDarkMutedColor(fallback),
        ).distinct()
    }
}
