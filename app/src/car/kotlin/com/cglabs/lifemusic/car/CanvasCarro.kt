package com.cglabs.lifemusic.car

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.ui.player.buscarCanvas
import com.cglabs.lifemusic.utils.rememberEnumPreference
import com.music.innertube.YouTube
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

/** Cuando se carga el canvas. */
enum class ModoCanvas(val titulo: Int, val detalle: Int) {
    AUTOMATICO(R.string.carro_canvas_automatico, R.string.carro_canvas_automatico_texto),
    SIEMPRE(R.string.carro_canvas_siempre, R.string.carro_canvas_siempre_texto),
    NUNCA(R.string.carro_canvas_nunca, R.string.carro_canvas_nunca_texto),
}

val CarroCanvasKey = stringPreferencesKey("carroCanvas")

/** Lo que el audio tiene que ir por delante antes de que el canvas pida un solo byte. */
private const val AUDIO_ASEGURADO_MS = 15_000L

private const val ETIQUETA = "LifeMusicCarro"

/**
 * El canvas de la cancion (el video corto en bucle de Apple Music y compañia)
 * dentro del cuadro de la caratula de Sonando. Encima de la caratula, y solo
 * aparece con un fundido cuando tiene su primer cuadro: si no hay canvas o
 * falla, se queda la caratula.
 *
 * Cuando se carga, en [ModoCanvas.AUTOMATICO] (el de siempre):
 *  1. el perfil del radio es Completo (en Ligero no hay memoria para un video);
 *  2. la red esta Estable (el mismo medidor del indicador de Inicio);
 *  3. y la cancion ya esta asegurada: el audio va al menos [AUDIO_ASEGURADO_MS]
 *     por delante. El canvas nunca le quita red al arranque de la cancion.
 * Una vez arrancado se queda aunque la red empeore: el bucle ya esta en la
 * cache y no pide mas. [ModoCanvas.SIEMPRE] se salta el perfil y la red (sigue
 * esperando al audio); [ModoCanvas.NUNCA] lo apaga.
 */
@Composable
fun CanvasDeCaratula(conexion: PlayerConnection?, m: MediaMetadata?, sonando: Boolean, modifier: Modifier = Modifier) {
    val (modo, _) = rememberEnumPreference(CarroCanvasKey, defaultValue = ModoCanvas.AUTOMATICO)
    val perfil = LocalPerfil.current
    val monitor = LocalMonitorDeRed.current
    val red by (monitor?.estado ?: remember { MutableStateFlow(EstadoRed()) }).collectAsState()

    val porAjustes = when (modo) {
        ModoCanvas.NUNCA -> false
        ModoCanvas.SIEMPRE -> true
        ModoCanvas.AUTOMATICO -> perfil == Perfil.COMPLETO
    }
    val redAlcanza = when (modo) {
        ModoCanvas.AUTOMATICO -> red.calidad == CalidadRed.ESTABLE
        else -> red.tipo != TipoRed.NINGUNA && red.calidad != CalidadRed.SIN_INTERNET
    }

    val id = m?.id
    val alcance = rememberCoroutineScope()
    var audioAsegurado by remember(id) { mutableStateOf(false) }
    var url by remember(id) { mutableStateOf<String?>(null) }
    var buscado by remember(id) { mutableStateOf(false) }

    // 3. Esperar a que el audio vaya por delante (se mira una vez por segundo).
    LaunchedEffect(conexion, id, porAjustes) {
        val p = conexion?.player ?: return@LaunchedEffect
        if (!porAjustes || id == null) return@LaunchedEffect
        while (isActive && !audioAsegurado) {
            val duracion = p.duration
            audioAsegurado = p.playbackState == Player.STATE_READY &&
                (p.bufferedPosition - p.currentPosition >= AUDIO_ASEGURADO_MS || (duracion > 0 && p.bufferedPosition >= duracion - 1_000))
            if (!audioAsegurado) delay(1_000)
        }
    }

    // Con todo a favor se busca el canvas, una vez por cancion. La busqueda va aparte: si la red
    // cambia mientras tanto no se corta a medias (se tiraria lo ya pedido y se volveria a pedir).
    LaunchedEffect(id, porAjustes, redAlcanza, audioAsegurado) {
        if (buscado || m == null || !porAjustes || !redAlcanza || !audioAsegurado) return@LaunchedEffect
        buscado = true
        val cancion = m
        alcance.launch {
            url = buscarCanvas(cancion)?.preferredAnimationUrl?.takeIf { it.isNotBlank() }
            Log.d(ETIQUETA, "canvas de «${cancion.title}»: ${url?.let { Uri.parse(it).host } ?: "no tiene"}")
        }
    }

    val video = url
    if (porAjustes && video != null) {
        ReproductorDeCanvas(video, sonando, modifier)
    }
}

/**
 * Un reproductor de video pensado para un radio: sin audio, en bucle, con el
 * video mas pequeño que cubre el cuadro (el telefono pide el mas alto: aqui un
 * 1080p para 540 px de pantalla es memoria y datos tirados), en H.264, que
 * todos los radios decodifican por hardware, y con poco bufer. Todo pasa por
 * una cache propia: la segunda vuelta del bucle no baja nada. Se pausa con la
 * musica y se libera al salir de Sonando.
 */
@OptIn(UnstableApi::class)
@Composable
private fun ReproductorDeCanvas(url: String, sonando: Boolean, modifier: Modifier) {
    val contexto = LocalContext.current
    var lado by remember { mutableFloatStateOf(0f) }
    var listo by remember(url) { mutableStateOf(false) }
    var proporcion by remember(url) { mutableFloatStateOf(1f) }

    val jugador = remember {
        ExoPlayer.Builder(contexto)
            .setMediaSourceFactory(DefaultMediaSourceFactory(CacheDeCanvas.fuente(contexto)))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(2_000, 8_000, 500, 1_000)
                    .setTargetBufferBytes(4 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build(),
            )
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setPreferredVideoMimeType(MimeTypes.VIDEO_H264)
                    .setMaxVideoSize(1280, 1280)
                    .build()
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
    }

    // El video mas pequeño que cubre el cuadro, cuando se sabe cuanto mide.
    LaunchedEffect(lado) {
        if (lado <= 0f) return@LaunchedEffect
        jugador.trackSelectionParameters = jugador.trackSelectionParameters.buildUpon()
            .setViewportSize(lado.toInt(), lado.toInt(), false)
            .build()
    }

    DisposableEffect(jugador) {
        val oyente = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                listo = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) proporcion = videoSize.width.toFloat() / videoSize.height
            }

            override fun onPlayerError(error: PlaybackException) {
                listo = false
                Log.w(ETIQUETA, "el canvas no se pudo reproducir: ${error.errorCodeName}", error)
            }
        }
        jugador.addListener(oyente)
        onDispose {
            jugador.removeListener(oyente)
            jugador.release()
        }
    }

    LaunchedEffect(url) {
        val limpia = url.trim()
        val ruta = limpia.lowercase(Locale.ROOT).substringBefore('?')
        val tipo = when {
            ruta.endsWith(".m3u8") || "apple.com" in limpia -> MimeTypes.APPLICATION_M3U8
            ruta.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            else -> MimeTypes.APPLICATION_M3U8
        }
        jugador.setMediaItem(MediaItem.Builder().setUri(limpia).setMimeType(tipo).build())
        jugador.prepare()
    }

    LaunchedEffect(sonando) { jugador.playWhenReady = sonando }

    val alfa by animateFloatAsState(if (listo) 1f else 0f, tween(600), label = "canvas")
    AndroidView(
        factory = { ctx ->
            AspectRatioFrameLayout(ctx).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                val textura = TextureView(ctx)
                addView(textura, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                jugador.setVideoTextureView(textura)
            }
        },
        update = { it.setAspectRatio(proporcion) },
        modifier = modifier
            .onSizeChanged { lado = maxOf(it.width, it.height).toFloat() }
            .alpha(alfa),
    )
}

/**
 * La cache del canvas: 40 MB, lo mas viejo se va primero. Una sola instancia
 * por carpeta (SimpleCache no admite dos), creada al primer canvas, sobre la
 * misma base de datos de las caches del reproductor (CarActivity la pasa en
 * [baseDeDatos]; sin ella, una propia).
 */
@OptIn(UnstableApi::class)
internal object CacheDeCanvas {
    @Volatile private var cache: SimpleCache? = null

    @Volatile var baseDeDatos: DatabaseProvider? = null

    private val cliente by lazy { OkHttpClient.Builder().proxy(YouTube.proxy).build() }

    fun fuente(contexto: Context): DataSource.Factory {
        val c = cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(contexto.applicationContext.cacheDir, "canvas-carro"),
                LeastRecentlyUsedCacheEvictor(40L * 1024 * 1024),
                baseDeDatos ?: StandaloneDatabaseProvider(contexto.applicationContext),
            ).also { cache = it }
        }
        return CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(cliente))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
