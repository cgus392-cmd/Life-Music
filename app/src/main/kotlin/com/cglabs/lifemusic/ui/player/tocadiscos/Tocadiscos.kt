package com.cglabs.lifemusic.ui.player.tocadiscos

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.ui.utils.resize
import com.cglabs.lifemusic.utils.ModoLigero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * 2.1.0 · El tocadiscos del Mixed Editor (maqueta aprobada por CG el 09-11, Brand/maquetas/2026-11-09-tocadiscos).
 * La carátula es la funda: al acercarse el cambio el vinilo asoma, sale al plato con inercia hasta 33⅓ rpm y el brazo
 * baja la aguja; en el golpe entra la funda nueva, el viejo frena y se guarda, sale el nuevo y al final su funda lo
 * cubre y vuelve a ser la carátula. Se pinta con las piezas de Diseño (res/drawable-nodpi/tocadiscos_*), no con
 * figuras de código; la carátula de cada canción se pone encima en vivo.
 *
 * El reloj va atado a la música: antes del cambio se calcula con la posición de la canción y el punto de mezcla del
 * Automix; desde el cambio, con la hora en que empezó la transición (MusicService.transicionEnCurso).
 */

/** Una portada lista para pintar, con su pincel hecho una sola vez (no en cada cuadro). */
class Portada(val id: String, val imagen: ImageBitmap) {
    val pincel = ShaderBrush(ImageShader(imagen))
}

/** Las piezas de Diseño, leídas una vez al prender la mezcla y soltadas al apagarla. */
class KitTocadiscos private constructor(
    val vinilo: ImageBitmap,
    val brillo: ImageBitmap,
    val papel: ImageBitmap,
    val eje: ImageBitmap,
    val sombraVinilo: ImageBitmap,
    val fundaBorde: ImageBitmap,
    val fundaSombra: ImageBitmap,
    val brazo: ImageBitmap,
    val brazoBase: ImageBitmap,
    val brazoSombra: ImageBitmap,
) {
    val pincelBorde = ShaderBrush(ImageShader(fundaBorde))

    companion object {
        fun cargar(contexto: Context, ligero: Boolean): KitTocadiscos {
            val opciones = BitmapFactory.Options().apply { if (ligero) inSampleSize = 2 }
            // prepareToDraw: la pieza se sube a la GPU de una vez, y no en el primer cuadro del tocadiscos.
            fun leer(id: Int) = BitmapFactory.decodeResource(contexto.resources, id, opciones)
                .also { it.prepareToDraw() }
                .asImageBitmap()
            return KitTocadiscos(
                vinilo = leer(R.drawable.tocadiscos_vinilo),
                brillo = leer(R.drawable.tocadiscos_brillo),
                papel = leer(R.drawable.tocadiscos_etiqueta_papel),
                eje = leer(R.drawable.tocadiscos_eje),
                sombraVinilo = leer(R.drawable.tocadiscos_vinilo_sombra),
                fundaBorde = leer(R.drawable.tocadiscos_funda_borde),
                fundaSombra = leer(R.drawable.tocadiscos_funda_sombra),
                brazo = leer(R.drawable.tocadiscos_brazo),
                brazoBase = leer(R.drawable.tocadiscos_brazo_base),
                brazoSombra = leer(R.drawable.tocadiscos_brazo_sombra),
            )
        }
    }
}

/** El ensayo: una pasada completa a pedido (toque largo en la etiqueta verde «MIX»), sin cambiar la música. */
object Tocadiscos {
    class Ensayo(
        val golpeTransicionMs: Long,
        val golpeMs: Float,
        val deId: String,
        val deArte: String?,
        val aId: String,
        val aArte: String?,
    )

    val ensayo = MutableStateFlow<Ensayo?>(null)

    fun ensayar(conexion: PlayerConnection) {
        val p = conexion.player
        val actual = p.currentMediaItem ?: return
        val siguiente = p.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { p.getMediaItemAt(it) } ?: actual
        val bpm = conexion.automixDebugInfo.value?.outBpm?.takeIf { it > 0f } ?: 100f
        val golpeMs = 60_000f / bpm
        ensayo.value = Ensayo(
            // Empieza en el golpe ENTRA: faltan (9 − 0,5) golpes para la transición.
            golpeTransicionMs = SystemClock.elapsedRealtime() + ((Coreografia.TRANSICION - Coreografia.ENTRA) * golpeMs).toLong(),
            golpeMs = golpeMs,
            deId = actual.mediaId,
            deArte = arte(actual),
            aId = siguiente.mediaId,
            aArte = arte(siguiente),
        )
        Log.i("LifeMusicMixed", "ensayo del tocadiscos a ${bpm.roundToInt()} bpm")
    }

    internal fun arte(item: MediaItem): String? = item.mediaMetadata.artworkUri?.toString()?.resize(1200, 1200)
}

@Stable
class EstadoTocadiscos internal constructor(val quieto: Boolean) {
    /**
     * El golpe que se ve ahora; NaN cuando no hay tocadiscos (se ve la carátula de siempre). Cambia en cada cuadro:
     * se lee solo al pintar (drawBehind, graphicsLayer), nunca al componer.
     */
    var golpe by mutableFloatStateOf(Float.NaN)
        internal set
    var golpeMs by mutableFloatStateOf(600f)
        internal set

    /** Si hay una coreografía en marcha. Cambia pocas veces: se puede leer al componer. */
    var activo by mutableStateOf(false)
        internal set
    var kit by mutableStateOf<KitTocadiscos?>(null)
        internal set
    var portadaA by mutableStateOf<Portada?>(null)
        internal set
    var portadaB by mutableStateOf<Portada?>(null)
        internal set

    /** La que sale y la que entra, con la dirección de su carátula. */
    var idA by mutableStateOf<String?>(null)
        internal set
    var idB by mutableStateOf<String?>(null)
        internal set
    var arteA by mutableStateOf<String?>(null)
        internal set
    var arteB by mutableStateOf<String?>(null)
        internal set

    /** Si el tocadiscos tapa la carátula de siempre (la capa ya tiene todo para pintarse). */
    val tapaLaCaratula: Boolean
        get() = activo && kit != null && portadaA?.id == idA

    /** El golpe para pintar, 0 si no hay tocadiscos. */
    val golpeParaPintar: Float
        get() = golpe.let { if (it.isNaN()) 0f else it }

    internal fun reposo() {
        golpe = Float.NaN
        activo = false
        portadaA = null
        portadaB = null
        idA = null
        idB = null
        arteA = null
        arteB = null
    }
}

private class Calculo(
    val golpe: Float,
    val golpeMs: Float,
    val deId: String?,
    val deArte: String?,
    val aId: String?,
    val aArte: String?,
)

/** Dónde va la coreografía ahora mismo, o null si no toca tocadiscos. */
private fun calcular(conexion: PlayerConnection, mezclando: Boolean): Calculo? {
    val ahora = SystemClock.elapsedRealtime()
    Tocadiscos.ensayo.value?.let { e ->
        val g = Coreografia.TRANSICION + (ahora - e.golpeTransicionMs) / e.golpeMs
        if (g < Coreografia.FIN + 0.3f) return Calculo(min(g, Coreografia.FIN), e.golpeMs, e.deId, e.deArte, e.aId, e.aArte)
        Tocadiscos.ensayo.value = null
    }
    if (!mezclando) return null
    val servicio = conexion.service
    servicio.transicionEnCurso.value?.let { t ->
        // 2.1.0 · Si la mezcla ya termino y lo que suena no es la entrante, la persona se movio (volvio a la
        // anterior, eligio otra): el tocadiscos y las ondas se van a reposo de una. Antes se quedaban hasta 30 s en
        // el final, con la funda de la otra cancion y las ondas tapando los botones (CG, 12-11).
        // Ojo: al cerrar la mezcla puede haber un instante con la saliente aun como actual, pero cerca de su final;
        // eso no es moverse. Volver a la anterior la deja en el comienzo, antes de donde salio.
        val p = conexion.player
        val actualId = p.currentMediaItem?.mediaId
        val seMovio = !servicio.isCrossfading.value && actualId != t.aId &&
            (actualId != t.deId || p.currentPosition < t.salienteDesdeMs)
        if (seMovio) return@let
        val dt = (ahora - t.inicioMs).toFloat()
        // Sin plan no hubo previa: la entrada se hace rápida (1,2 s) y desde el golpe se sigue normal.
        val g = when {
            t.conPlan -> Coreografia.TRANSICION + dt / t.golpeMs
            dt < 1200f -> 2f + (Coreografia.TRANSICION - 2f) * dt / 1200f
            else -> Coreografia.TRANSICION + (dt - 1200f) / t.golpeMs
        }
        if (g < Coreografia.FIN) return Calculo(g, t.golpeMs, t.deId, t.deArte, t.aId, t.aArte)
        // La funda nueva se queda como carátula hasta que la carátula de verdad también sea la nueva.
        if (dt < 30_000f && conexion.player.currentMediaItem?.mediaId != t.aId) {
            return Calculo(Coreografia.FIN, t.golpeMs, t.deId, t.deArte, t.aId, t.aArte)
        }
    }
    if (servicio.isCrossfading.value) return null
    val plan = servicio.automixDebugInfo.value ?: return null
    val disparo = plan.triggerTimeMs ?: return null
    val bpm = plan.outBpm?.takeIf { it > 0f } ?: return null
    val golpeMs = 60_000f / bpm
    val p = conexion.player
    val g = Coreografia.TRANSICION - (disparo - p.currentPosition) / golpeMs
    if (g < Coreografia.ENTRA || g >= Coreografia.TRANSICION + 0.5f) return null
    val actual = p.currentMediaItem ?: return null
    val siguiente = p.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { p.getMediaItemAt(it) } ?: return null
    return Calculo(
        min(g, Coreografia.TRANSICION - 0.02f), golpeMs,
        actual.mediaId, Tocadiscos.arte(actual), siguiente.mediaId, Tocadiscos.arte(siguiente),
    )
}

private fun sinAnimaciones(contexto: Context) = runCatching {
    Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

private suspend fun cargarPortada(contexto: Context, url: String, lado: Int): ImageBitmap? {
    val pedido = ImageRequest.Builder(contexto).data(url).size(lado, lado).allowHardware(false).build()
    return runCatching {
        contexto.imageLoader.execute(pedido).image?.toBitmap()?.also { it.prepareToDraw() }?.asImageBitmap()
    }.getOrNull()
}

/**
 * El reloj del tocadiscos. Corre solo con la mezcla prendida (o un ensayo) y el reproductor abierto; lejos del
 * cambio mira cada 200 ms, y cerca va cuadro a cuadro. Las piezas se leen al prender la mezcla.
 */
@Composable
fun rememberTocadiscos(estaAbierto: () -> Boolean): EstadoTocadiscos? {
    val conexion = LocalPlayerConnection.current ?: return null
    val contexto = LocalContext.current
    val estado = remember { EstadoTocadiscos(quieto = ModoLigero.esGamaBaja(contexto) || sinAnimaciones(contexto)) }
    val mezclando by conexion.service.mixedEditor.collectAsState()
    val ensayo by Tocadiscos.ensayo.collectAsState()
    val abierto by rememberUpdatedState(estaAbierto)
    val enMarcha = mezclando || ensayo != null

    LaunchedEffect(enMarcha) {
        if (!enMarcha) {
            estado.reposo()
            estado.kit = null
            return@LaunchedEffect
        }
        if (estado.kit == null) {
            estado.kit = withContext(Dispatchers.IO) {
                runCatching { KitTocadiscos.cargar(contexto, estado.quieto) }
                    .onFailure { Log.w("LifeMusicMixed", "no se pudieron leer las piezas del tocadiscos", it) }
                    .getOrNull()
            }
        }
        val lado = if (estado.quieto) 600 else 1200
        val pedidas = HashSet<String>()
        fun pedir(id: String?, url: String?, esA: Boolean) {
            if (id == null || url == null) return
            val yaEsta = if (esA) estado.portadaA?.id == id else estado.portadaB?.id == id
            if (yaEsta || !pedidas.add(id)) return
            launch {
                val imagen = cargarPortada(contexto, url, lado)
                pedidas.remove(id)
                if (imagen == null) return@launch
                if (esA && estado.idA == id) estado.portadaA = Portada(id, imagen)
                if (!esA && estado.idB == id) estado.portadaB = Portada(id, imagen)
            }
        }
        var antes = false
        while (isActive) {
            val c = calcular(conexion, mezclando || ensayo != null)
            if (c == null || !abierto()) {
                if (antes) Log.i("LifeMusicMixed", "tocadiscos en reposo")
                antes = false
                estado.reposo()
                delay(200)
                continue
            }
            if (!antes) Log.i("LifeMusicMixed", "tocadiscos en marcha: golpe ${"%.1f".format(c.golpe)}, ${c.golpeMs.roundToInt()} ms por golpe")
            antes = true
            if (estado.idA != c.deId) estado.portadaA = null
            if (estado.idB != c.aId) estado.portadaB = null
            estado.idA = c.deId
            estado.idB = c.aId
            estado.arteA = c.deArte
            estado.arteB = c.aArte
            pedir(c.deId, c.deArte, esA = true)
            pedir(c.aId, c.aArte, esA = false)
            estado.golpeMs = c.golpeMs
            estado.golpe = c.golpe
            estado.activo = true
            withFrameNanos { }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            estado.reposo()
            estado.kit = null
        }
    }
    return estado
}

// ── Pintar ──────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * El tocadiscos sobre la carátula en tarjeta (Seguir tema, Gradiente, Desenfoque, Glow, Live Mesh, Liquid Glass y
 * Canvas). Ocupa el mismo lugar que la carátula y la reemplaza mientras dura; puede salirse de ella por los lados.
 */
@Composable
fun TocadiscosEnTarjeta(estado: EstadoTocadiscos, lado: Dp, esquina: Dp, modifier: Modifier = Modifier) {
    val pinturaSombra = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Spacer(
        modifier.drawBehind {
            val b = estado.golpe
            if (b.isNaN() || !estado.tapaLaCaratula) return@drawBehind
            val kit = estado.kit ?: return@drawBehind
            val a = estado.portadaA ?: return@drawBehind
            val escena = Escena(this, kit, pinturaSombra, lado.toPx(), esquina.toPx())
            if (estado.quieto) {
                escena.quieta(b, estado.golpeMs, a, estado.portadaB)
            } else {
                escena.enTarjeta(b, estado.golpeMs, a, estado.portadaB)
            }
        },
    )
}

/**
 * Apple Music (versión 2, pedida por CG): la carátula a sangre se queda y sigue siendo la del estilo, con su fondo
 * borroso y su desvanecido. El vinilo va DETRÁS de ella y sale por el borde de abajo, donde se desvanece; el cambio de
 * carátula es un enfoque de cámara (la vieja se desenfoca y se apaga, la nueva entra borrosa y se enfoca). Mientras
 * dura, el reproductor se recoge (eso lo hace Player.kt) y el desvanecido se acorta para que el disco se vea entero.
 *
 * Reemplaza al fondo de Apple Music solo mientras hay tocadiscos; en reposo pinta lo mismo que él, para que el relevo
 * no se note. [urlA] es la carátula que había al empezar; [urlB], la nueva.
 */
@Composable
fun FondoAppleConTocadiscos(
    estado: EstadoTocadiscos,
    urlA: String?,
    urlB: String?,
    alfaFondo: Float,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val pinturaSombra = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Box(modifier.fillMaxSize().alpha(alfaFondo)) {
        // 1. El fondo borroso del estilo, mezclado entre las dos carátulas al ritmo del enfoque.
        for ((url, esA) in listOf(urlA to true, urlB to false)) {
            if (url == null) continue
            AsyncImage(
                model = ImageRequest.Builder(contexto).data(url).size(128, 128).allowHardware(false).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val cambia = Coreografia.cambia(estado.golpeParaPintar)
                        alpha = if (esA) 1f - cambia else cambia
                    }
                    .blur(150.dp),
            )
        }
        // 2. Los vinilos, detrás de la carátula.
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val b = estado.golpe
                val kit = estado.kit
                val a = estado.portadaA
                if (b.isNaN() || kit == null || a == null) return@drawBehind
                Escena(this, kit, pinturaSombra, size.width * 360f / 440f, 0f).appleVinilos(b, estado.golpeMs, a, estado.portadaB)
            },
        )
        // 3. Las carátulas nítidas, con el desvanecido del estilo (más corto mientras el reproductor está recogido).
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val f = Coreografia.desvanecido(Coreografia.recoge(estado.golpeParaPintar))
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            f[0] to Color.Black,
                            f[1] to Color.Black.copy(alpha = 0.4f),
                            f[2] to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            for ((url, esA) in listOf(urlA to true, urlB to false)) {
                if (url == null) continue
                AsyncImage(
                    model = ImageRequest.Builder(contexto).data(url).size(coil3.size.Size.ORIGINAL).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val g = estado.golpeParaPintar
                        val cambia = Coreografia.cambia(g)
                        alpha = if (esA) Coreografia.alfaCaratulaA(g) else Coreografia.alfaCaratulaB(g)
                        val radio = 26.dp.toPx() * (if (esA) cambia else 1f - cambia)
                        renderEffect = if (radio > 0.5f) BlurEffect(radio, radio, TileMode.Clamp) else null
                    },
                )
            }
        }
        // 4. El velo del estilo.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.4f)))),
        )
        // 5. El brazo, delante de la carátula.
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val b = estado.golpe
                val kit = estado.kit
                if (b.isNaN() || kit == null) return@drawBehind
                Escena(this, kit, pinturaSombra, size.width * 360f / 440f, 0f).appleBrazo(b)
            },
        )
    }
}

/** Todo lo que se pinta, en las medidas de la maqueta (carátula de 360) escaladas a la carátula real. */
private class Escena(
    val d: DrawScope,
    val kit: KitTocadiscos,
    val pinturaSombra: android.graphics.Paint,
    val lado: Float,
    val esquina: Float,
) {
    /** Píxeles por «dp de la maqueta». */
    val u = lado / 360f
    val cx = d.size.width / 2f
    val cy = d.size.height / 2f
    val radio = 168f * u

    fun enTarjeta(b: Float, golpeMs: Float, a: Portada, pb: Portada?) {
        val fA = cx + Coreografia.claves(Coreografia.FUNDA_A, b) * u
        val vA = cx + Coreografia.claves(Coreografia.VINILO_A, b) * u
        val fB = cx + Coreografia.claves(Coreografia.FUNDA_B, b) * u
        val vB = cx + Coreografia.claves(Coreografia.VINILO_B, b) * u
        val adornos = Coreografia.adornos(b)
        val giroA = Coreografia.giroA(b, golpeMs)
        val giroB = Coreografia.giroB(b, golpeMs)

        if (!Coreografia.enPlatoA(b)) {
            vinilo(vA, cy, radio, giroA, a, sombra = 0.6f * adornos)
            sombraDeBoca(fA, cy, Coreografia.lim((vA - fA) / (60f * u)))
        }
        funda(fA, cy, a, abierta = Coreografia.lim((vA - fA) / (50f * u)), adornos = adornos)
        if (Coreografia.enPlatoA(b)) vinilo(vA, cy, radio, giroA, a, sombra = adornos)
        if (Coreografia.hayFundaB(b)) {
            if (!Coreografia.enPlatoB(b)) {
                vinilo(vB, cy, radio, giroB, pb, sombra = 0.6f * adornos)
                funda(fB, cy, pb, abierta = Coreografia.lim((vB - fB) / (50f * u)), adornos = adornos)
            } else {
                funda(fB, cy, pb, abierta = 0.8f, adornos = adornos)
                vinilo(vB, cy, radio, giroB, pb, sombra = adornos)
            }
        }
        val pose = Coreografia.brazoEnTarjeta(b)
        if (pose.visible > 0.003f) {
            brazo(cx + 175f * u, cy - 170f * u, pose.angulo, pose.alto, 0.85f * u, pose.visible)
        }
    }

    // ── Apple Music: medidas de la maqueta (teléfono de 440 de ancho) llevadas a la pantalla real ─────────────
    private val anchoMaqueta get() = d.size.width / 440f

    /** Dónde queda el centro del vinilo cuando ya salió: más abajo cuanto más recogido está el reproductor. */
    private fun yAfuera(recoge: Float) = Coreografia.mix(0.42f, 0.50f, recoge) * d.size.height

    /** Los vinilos de Apple Music: salen por el borde de abajo de la carátula, el viejo se va de lado. */
    fun appleVinilos(b: Float, golpeMs: Float, a: Portada, pb: Portada?) {
        val w = d.size.width
        val recoge = Coreografia.recoge(b)
        val r = Coreografia.mix(150f, 172f, recoge) * anchoMaqueta
        // Escondido: detrás de la parte opaca de la carátula.
        val yDentro = 0.20f * d.size.height
        val yFuera = yAfuera(recoge)
        if (Coreografia.hayViniloAEnApple(b)) {
            val x = w / 2f + Coreografia.ladoA(b) * w
            vinilo(x, Coreografia.mix(yDentro, yFuera, Coreografia.saleA(b)), r, Coreografia.giroA(b, golpeMs), a, sombra = 1f)
        }
        if (Coreografia.hayViniloBEnApple(b)) {
            vinilo(w / 2f, Coreografia.mix(yDentro, yFuera, Coreografia.saleB(b)), r, Coreografia.giroB(b, golpeMs), pb, sombra = 1f)
        }
    }

    /** El brazo de Apple Music, a la derecha del disco. El de la maqueta mide 175 a escala 0,72; la pieza, 230. */
    fun appleBrazo(b: Float) {
        val pose = Coreografia.brazoEnApple(b)
        if (pose.visible <= 0.003f) return
        val k = anchoMaqueta
        val yFuera = yAfuera(Coreografia.recoge(b))
        brazo(d.size.width / 2f + 174f * k, yFuera - 120f * k, pose.angulo, pose.alto, 0.72f * k * 175f / 230f, pose.visible)
    }

    /** Gama baja y «Quitar animaciones»: cuatro cuadros quietos, con fundidos de 400 ms entre ellos. */
    fun quieta(b: Float, golpeMs: Float, a: Portada, pb: Portada?) {
        val pesos = Coreografia.cuadrosQuietos(b, golpeMs)
        val cuadros = listOf<() -> Unit>(
            { funda(cx, cy, a, abierta = 0f, adornos = 0f) },
            { asomando(a) },
            { asomando(pb) },
            { funda(cx, cy, pb, abierta = 0f, adornos = 0f) },
        )
        // Fundido cruzado: el que sale se apaga mientras el que entra se enciende encima.
        for (i in cuadros.indices) {
            val peso = pesos[i]
            if (peso <= 0.003f) continue
            if (peso >= 0.997f) cuadros[i]() else capa(peso) { cuadros[i]() }
        }
    }

    private fun asomando(p: Portada?) {
        val f = cx - 70f * u
        val v = cx + 80f * u
        vinilo(v, cy, radio, 0.6f, p, sombra = 0.6f)
        sombraDeBoca(f, cy, 1f)
        funda(f, cy, p, abierta = 1f, adornos = 1f)
    }

    private fun capa(alfa: Float, dibujo: () -> Unit) {
        d.drawIntoCanvas { lienzo ->
            lienzo.saveLayer(Rect(0f, 0f, d.size.width, d.size.height), Paint().apply { this.alpha = alfa })
            dibujo()
            lienzo.restore()
        }
    }

    // ── Las piezas ─────────────────────────────────────────────────────────────────────────────────────

    /** El vinilo: sombra, surcos y etiqueta (giran), brillo de la luz (no gira) y eje (quieto). */
    fun vinilo(x: Float, y: Float, r: Float, giro: Float, portada: Portada?, sombra: Float) {
        val centro = Offset(x, y)
        if (sombra > 0.003f) imagen(kit.sombraVinilo, centro, r * 2.5f, alfa = sombra)
        d.rotate(Math.toDegrees(giro.toDouble()).toFloat(), centro) {
            imagen(kit.vinilo, centro, r * 2f)
            val etiqueta = r * 0.33f
            if (portada != null) {
                recortada(portada.pincel, portada.imagen, centro, etiqueta * 2f, esquina = 0f, circulo = true)
            } else {
                d.drawCircle(Color(0xFF1E1C20), etiqueta, centro)
            }
            imagen(kit.papel, centro, etiqueta * 2f)
        }
        imagen(kit.brillo, centro, r * 2f, modo = BlendMode.Screen)
        imagen(kit.eje, centro, r * 0.03f * 2f * 64f / 60f)
    }

    /** La funda: la carátula como objeto, con su sombra, la boca que se oscurece y el filo de cartón. */
    fun funda(x: Float, y: Float, portada: Portada?, abierta: Float, adornos: Float) {
        val izq = x - lado / 2f
        val arriba = y - lado / 2f
        if (adornos > 0.01f) {
            d.drawIntoCanvas { lienzo ->
                pinturaSombra.color = android.graphics.Color.argb(255, 34, 34, 34)
                pinturaSombra.setShadowLayer(10f * u, 0f, 5f * u, android.graphics.Color.argb((0.22f * adornos * 255).roundToInt(), 0, 0, 0))
                lienzo.nativeCanvas.drawRoundRect(izq, arriba, izq + lado, arriba + lado, esquina, esquina, pinturaSombra)
            }
        }
        if (portada != null) {
            recortada(portada.pincel, portada.imagen, Offset(x, y), lado, esquina, circulo = false)
        } else {
            d.drawRoundRect(Color(0xFF1E1C20), Offset(izq, arriba), Size(lado, lado), CornerRadius(esquina))
        }
        if (abierta > 0.01f) {
            d.drawRoundRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f * abierta),
                    startX = izq + lado * 0.93f,
                    endX = izq + lado,
                ),
                topLeft = Offset(izq, arriba),
                size = Size(lado, lado),
                cornerRadius = CornerRadius(esquina),
            )
        }
        if (adornos > 0.01f) {
            recortada(kit.pincelBorde, kit.fundaBorde, Offset(x, y), lado, esquina, circulo = false, alfa = adornos)
        }
    }

    /** La sombra que la funda tira sobre el vinilo que asoma por su boca. */
    fun sombraDeBoca(xFunda: Float, y: Float, fuerza: Float) {
        if (fuerza <= 0.01f) return
        imagen(kit.fundaSombra, Offset(xFunda + lado / 2f + 13f * u, y), 26f * u, lado, alfa = fuerza)
    }

    /** El brazo: sombra (más lejos y difusa cuanto más alto), base fija y la pieza que gira sobre su pivote. */
    fun brazo(px: Float, py: Float, angulo: Float, alto: Float, escala: Float, alfa: Float) {
        val grados = Math.toDegrees(angulo.toDouble()).toFloat()
        val dz = (6f + alto * 14f) * escala
        d.translate(px + dz * 0.6f, py + dz) {
            d.rotate(grados, Offset.Zero) {
                // brazo-sombra: 256 × 640, 1,6 px por unidad, pivote en (128, 128).
                val k = escala / 1.6f
                imagenEn(kit.brazoSombra, Offset(-128f * k, -128f * k), 256f * k, 640f * k, alfa * (0.22f - alto * 0.08f) / 0.22f)
            }
        }
        imagen(kit.brazoBase, Offset(px, py), 64f * escala, alfa = alfa)
        d.translate(px, py) {
            d.rotate(grados, Offset.Zero) {
                d.scale(1f + alto * 0.03f, Offset.Zero) {
                    // brazo: 512 × 1280, 3,2 px por unidad, pivote en (256, 256).
                    val k = escala / 3.2f
                    imagenEn(kit.brazo, Offset(-256f * k, -256f * k), 512f * k, 1280f * k, alfa)
                }
            }
        }
    }

    // ── Ayudas ─────────────────────────────────────────────────────────────────────────────────────────

    /** Una imagen cuadrada (o de [alto]) centrada en [centro], con posición en fracciones de píxel. */
    fun imagen(img: ImageBitmap, centro: Offset, ancho: Float, alto: Float = ancho, alfa: Float = 1f, modo: BlendMode = BlendMode.SrcOver) {
        imagenEn(img, Offset(centro.x - ancho / 2f, centro.y - alto / 2f), ancho, alto, alfa, modo)
    }

    fun imagenEn(img: ImageBitmap, arribaIzq: Offset, ancho: Float, alto: Float, alfa: Float = 1f, modo: BlendMode = BlendMode.SrcOver) {
        if (alfa <= 0.003f || ancho < 1f || alto < 1f) return
        d.translate(arribaIzq.x, arribaIzq.y) {
            d.drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(ancho.roundToInt(), alto.roundToInt()),
                alpha = Coreografia.lim(alfa),
                blendMode = modo,
                filterQuality = FilterQuality.Medium,
            )
        }
    }

    /** La imagen recortada al centro en un cuadrado de [ladoDestino] con esquinas redondas, o en un círculo. */
    fun recortada(pincel: ShaderBrush, img: ImageBitmap, centro: Offset, ladoDestino: Float, esquina: Float, circulo: Boolean, alfa: Float = 1f) {
        val w = img.width.toFloat()
        val h = img.height.toFloat()
        val m = min(w, h)
        val k = ladoDestino / m
        d.withTransform({
            translate(centro.x - ladoDestino / 2f, centro.y - ladoDestino / 2f)
            scale(k, k, Offset.Zero)
            translate(-(w - m) / 2f, -(h - m) / 2f)
        }) {
            if (circulo) {
                d.drawCircle(pincel, radius = m / 2f, center = Offset(w / 2f, h / 2f), alpha = alfa)
            } else {
                d.drawRoundRect(
                    brush = pincel,
                    topLeft = Offset((w - m) / 2f, (h - m) / 2f),
                    size = Size(m, m),
                    cornerRadius = CornerRadius(esquina / k),
                    alpha = alfa,
                )
            }
        }
    }
}
