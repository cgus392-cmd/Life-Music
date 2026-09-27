package com.cglabs.lifemusic.car

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cglabs.lifemusic.ui.theme.DefaultThemeColor
import com.cglabs.lifemusic.ui.theme.extractThemeColor
import com.cglabs.lifemusic.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** Lado de la caratula que se desenfoca. 32 px: a pantalla completa ya no se distingue la foto, solo sus colores. */
private const val LADO = 32
/** Radio de la caja del desenfoque, en esos 32 px. Tres pasadas de caja se parecen mucho a una gaussiana. */
private const val RADIO = 3
/** Cada cuanto se redibuja el fondo al moverse: 20 veces por segundo. Va tan lento que 60 no se notarian, y el radio descansa. */
private const val PASO_MS = 50L
/** El fondo cuando aun no hay caratula: casi negro, para que el cristal tenga algo sobre lo que brillar. */
private val FONDO_BASE = Color(0xFF09090C)

/**
 * El fondo ambiente del carro: los colores de la caratula, desenfocados y
 * moviendose despacio, como el modo ambiente del telefono.
 *
 * Android 8 no sabe desenfocar (RenderEffect es de Android 12), asi que se hace
 * a mano y barato: la caratula se pide de 32×32, se desenfoca en la CPU (mil
 * pixeles, nada) y se estira a pantalla completa con filtrado bilineal, que en
 * una imagen ya borrosa no deja escalones. Dos capas de esa misma imagen giran
 * en sentidos contrarios; donde se cruzan salen las manchas de color.
 *
 * [moverse] en falso deja las manchas quietas: cero trabajo por fotograma.
 */
@Composable
fun FondoAmbiente(url: String?, moverse: Boolean, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    var imagen by remember { mutableStateOf<ImageBitmap?>(null) }
    var luz by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val caratula = withContext(Dispatchers.IO) { caratulaPequena(contexto, url, LADO) } ?: return@LaunchedEffect
        val borrosa = withContext(Dispatchers.Default) { desenfocar(caratula) }
        luz = withContext(Dispatchers.Default) { luminancia(borrosa) }
        imagen = borrosa.asImageBitmap()
    }
    val luzSuave by animateFloatAsState(luz, tween(1400), label = "luzFondo")
    Box(modifier.fillMaxSize().background(FONDO_BASE)) {
        Crossfade(targetState = imagen, animationSpec = tween(1400), label = "fondoAmbiente") { img ->
            if (img != null) Manchas(img, moverse) else ManchaDeEspera()
        }
        // Velo: el fondo acompana, no compite. Mas oscuro abajo, donde van los controles,
        // y mas cuanto mas clara es la caratula: con una portada blanca o pastel el
        // texto gris dejaria de leerse.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.28f + 0.38f * luzSuave),
                        Color.Black.copy(alpha = 0.55f + 0.27f * luzSuave),
                    )
                )
            )
        )
    }
}

/** Luminancia media (0 negro, 1 blanco) de la caratula ya desenfocada: 1 024 pixeles, nada. */
private fun luminancia(b: Bitmap): Float {
    val pixeles = IntArray(b.width * b.height)
    b.getPixels(pixeles, 0, b.width, 0, 0, b.width, b.height)
    var suma = 0f
    for (px in pixeles) {
        suma += 0.2126f * (px shr 16 and 0xFF) + 0.7152f * (px shr 8 and 0xFF) + 0.0722f * (px and 0xFF)
    }
    return (suma / pixeles.size / 255f).coerceIn(0f, 1f)
}

@Composable
private fun Manchas(img: ImageBitmap, moverse: Boolean) {
    val tiempo = remember { mutableFloatStateOf(0f) }
    if (moverse) {
        LaunchedEffect(Unit) {
            val inicio = SystemClock.uptimeMillis() - (tiempo.floatValue * 1000f).toLong()
            while (true) {
                tiempo.floatValue = (SystemClock.uptimeMillis() - inicio) / 1000f
                delay(PASO_MS)
            }
        }
    }
    // Un poco mas de saturacion: desenfocar apaga los colores, y el ambiente vive de ellos.
    val saturar = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) }) }
    // El tiempo se lee solo al dibujar: moverse redibuja esta capa y nada mas, sin recomponer.
    Canvas(Modifier.fillMaxSize()) {
        val t = tiempo.floatValue
        // Un cuadrado que cubre la pantalla aunque gire: su lado supera la diagonal.
        val lado = maxOf(size.width, size.height) * 1.35f
        val destino = IntSize(lado.toInt(), lado.toInt())
        val esquina = IntOffset(((size.width - lado) / 2f).toInt(), ((size.height - lado) / 2f).toInt())
        // Capa de fondo: la caratula entera, una vuelta cada tres minutos.
        rotate(t * 2f) {
            drawImage(img, dstOffset = esquina, dstSize = destino, colorFilter = saturar, filterQuality = FilterQuality.Low)
        }
        // Segunda capa, espejada, a contramano y paseando un poco, a media opacidad.
        withTransform({
            translate(sin(t * 0.11f) * size.width * 0.12f, cos(t * 0.08f) * size.height * 0.10f)
            rotate(180f - t * 3f)
            scale(-1f, 1f)
        }) {
            drawImage(img, dstOffset = esquina, dstSize = destino, alpha = 0.55f, colorFilter = saturar, filterQuality = FilterQuality.Low)
        }
    }
}

/** Sin caratula todavia: una sola luz del color de la app, quieta. */
@Composable
private fun ManchaDeEspera() {
    val primario = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val centro = Offset(size.width * 0.3f, size.height * 0.35f)
        val radio = size.width * 0.8f
        drawCircle(Brush.radialGradient(listOf(primario.copy(alpha = 0.30f), Color.Transparent), centro, radio), radio, centro)
    }
}

/** La caratula en pequeno y en software (para poder leer sus pixeles), o null si no llega. */
internal suspend fun caratulaPequena(contexto: Context, url: String, lado: Int): Bitmap? = runCatching {
    contexto.imageLoader.execute(
        ImageRequest.Builder(contexto).data(url.resize(96, 96)).size(lado, lado).allowHardware(false).build()
    ).image?.toBitmap()
}.getOrNull()

/** Tres pasadas de desenfoque de caja, horizontal y vertical, sobre la caratula de [LADO]×[LADO]. */
private fun desenfocar(origen: Bitmap): Bitmap {
    val b = if (origen.width == LADO && origen.height == LADO) origen else Bitmap.createScaledBitmap(origen, LADO, LADO, true)
    val pixeles = IntArray(LADO * LADO)
    b.getPixels(pixeles, 0, LADO, 0, 0, LADO, LADO)
    val apoyo = IntArray(LADO * LADO)
    repeat(3) {
        caja(pixeles, apoyo, horizontal = true)
        caja(apoyo, pixeles, horizontal = false)
    }
    return Bitmap.createBitmap(pixeles, LADO, LADO, Bitmap.Config.ARGB_8888)
}

private fun caja(de: IntArray, a: IntArray, horizontal: Boolean) {
    val ancho = 2 * RADIO + 1
    for (linea in 0 until LADO) for (i in 0 until LADO) {
        var r = 0
        var g = 0
        var b = 0
        for (k in -RADIO..RADIO) {
            val j = (i + k).coerceIn(0, LADO - 1)
            val px = if (horizontal) de[linea * LADO + j] else de[j * LADO + linea]
            r += px shr 16 and 0xFF
            g += px shr 8 and 0xFF
            b += px and 0xFF
        }
        val destino = if (horizontal) linea * LADO + i else i * LADO + linea
        a[destino] = (0xFF shl 24) or ((r / ancho) shl 16) or ((g / ancho) shl 8) or (b / ancho)
    }
}

/** El color principal de la caratula (MaterialKolor la convierte en tema), o el verde de Life Music. */
@Composable
fun colorDeCaratula(url: String?): Color {
    val contexto = LocalContext.current
    var color by remember { mutableStateOf(DefaultThemeColor) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) { caratulaPequena(contexto, url, 96) }
        bitmap?.let { color = withContext(Dispatchers.Default) { it.extractThemeColor() } }
    }
    return color
}

/**
 * Si el sistema tiene las animaciones apagadas (accesibilidad, o un radio que
 * las quita para ir mas suelto), aqui tampoco se anima nada de adorno.
 */
fun animacionesDelSistema(contexto: Context): Boolean =
    Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
