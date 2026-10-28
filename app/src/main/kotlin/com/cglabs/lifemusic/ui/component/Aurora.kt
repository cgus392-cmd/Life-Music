package com.cglabs.lifemusic.ui.component

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.cglabs.lifemusic.LocalDatabase
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.ui.component.backdrop.drawBackdrop
import com.cglabs.lifemusic.ui.component.backdrop.effects.blur
import com.cglabs.lifemusic.ui.component.backdrop.effects.colorControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.util.Calendar

/*
 * Aurora (Life Music 2.0): el degradado de arriba del Inicio y la barra que se
 * vuelve vidrio al bajar. Maqueta aprobada por CG el 15-10-2026:
 * Life Music/Brand/maquetas/2026-10-15-aurora-inicio.html.
 *
 * - Tres manchas radiales con los colores de la carátula de lo que suena (o de lo
 *   último que sonó): vibrante, apagado y dominante. Sin historial, los de marca.
 * - Unos 420 dp de alto, fundidas con el fondo por una máscara que va de opaco
 *   (hasta el 30 %) a transparente.
 * - Intensidad por tema: oscuro 82 %, claro 55 %, negro puro 40 %.
 * - Al bajar se apaga y la barra pasa a vidrio: desenfoque real en Android 12+,
 *   casi opaca (95 %) y sin desenfoque en Android 11 o menos.
 */

@Immutable
data class ColoresAurora(val c1: Color, val c2: Color, val c3: Color)

object Aurora {
    /** Menta, lila y azul de Life Music: sin historial ni música. */
    val MARCA = ColoresAurora(Color(0xFF34D399), Color(0xFFB4A2F6), Color(0xFF7DB4F7))

    /** Alto del degradado. */
    val ALTO: Dp = 420.dp

    /** Lo que hay que bajar para que la Aurora se apague del todo y la barra quede en vidrio. */
    val UMBRAL: Dp = 160.dp

    fun intensidad(oscuro: Boolean, negroPuro: Boolean): Float = when {
        oscuro && negroPuro -> 0.40f
        oscuro -> 0.82f
        else -> 0.55f
    }

    /**
     * Cuánto se ha recogido el Inicio, de 0 (arriba del todo) a 1 (barra en vidrio).
     * Lo escribe el Inicio con su lista y lo lee la barra de arriba.
     */
    var fraccion by mutableFloatStateOf(0f)

    /** Los colores vigentes de la Aurora (los pone el Inicio), para el menú de la foto. */
    var colores by mutableStateOf(MARCA)
}

/** Los colores de la Aurora: de la canción que suena; si no, de la última que sonó; si no, los de marca. */
@Composable
fun rememberColoresAurora(): ColoresAurora {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val flujo = remember(playerConnection) { playerConnection?.mediaMetadata ?: MutableStateFlow<MediaMetadata?>(null) }
    val sonando by flujo.collectAsState()

    var objetivo by remember { mutableStateOf(Aurora.MARCA) }
    val caratula = sonando?.thumbnailUrl
    LaunchedEffect(caratula) {
        val url = caratula ?: withContext(Dispatchers.IO) {
            database.escuchadasHacePoco(1).firstOrNull()?.firstOrNull()?.song?.thumbnailUrl
        }
        objetivo = if (url == null) Aurora.MARCA else coloresDe(context, url) ?: Aurora.MARCA
    }

    // Al cambiar de canción, los colores pasan de uno a otro sin salto.
    val paso = tween<Color>(durationMillis = 1200)
    val c1 by animateColorAsState(objetivo.c1, paso, label = "aurora-c1")
    val c2 by animateColorAsState(objetivo.c2, paso, label = "aurora-c2")
    val c3 by animateColorAsState(objetivo.c3, paso, label = "aurora-c3")
    val resultado = ColoresAurora(c1, c2, c3)
    SideEffect { Aurora.colores = resultado }
    return resultado
}

private suspend fun coloresDe(context: android.content.Context, url: String): ColoresAurora? = runCatching {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(128, 128)
        .allowHardware(false)
        .build()
    val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return null
    withContext(Dispatchers.Default) {
        val paleta = Palette.from(bitmap).maximumColorCount(12).resizeBitmapArea(128 * 128).generate()
        val dominante = paleta.dominantSwatch?.rgb ?: return@withContext null
        val vibrante = paleta.vibrantSwatch?.rgb ?: paleta.lightVibrantSwatch?.rgb ?: dominante
        val apagado = paleta.mutedSwatch?.rgb ?: paleta.darkVibrantSwatch?.rgb ?: paleta.lightMutedSwatch?.rgb ?: dominante
        ColoresAurora(Color(vibrante), Color(apagado), Color(dominante))
    }
}.getOrNull()

/**
 * El degradado: tres manchas elípticas (como los radial-gradient de la maqueta)
 * y una máscara vertical que lo funde con el fondo.
 */
@Composable
fun FondoAurora(colores: ColoresAurora, opacidad: Float, modifier: Modifier = Modifier) {
    if (opacidad <= 0.001f) return
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Aurora.ALTO)
            .graphicsLayer {
                alpha = opacidad
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        // radial-gradient(62% 72% at 8% -6%, c1, transparente 70%)
        mancha(colores.c1, cx = 0.08f, cy = -0.06f, rx = 0.62f, ry = 0.72f, fin = 0.70f)
        // radial-gradient(60% 66% at 94% 4%, c2, transparente 70%)
        mancha(colores.c2, cx = 0.94f, cy = 0.04f, rx = 0.60f, ry = 0.66f, fin = 0.70f)
        // radial-gradient(84% 58% at 50% 40%, c3, transparente 72%)
        mancha(colores.c3, cx = 0.50f, cy = 0.40f, rx = 0.84f, ry = 0.58f, fin = 0.72f)
        // Máscara: opaco hasta el 30 % y luego se funde con el fondo.
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Black, 0.30f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** Una mancha elíptica: un degradado radial circular estirado al alto y ancho pedidos. */
private fun DrawScope.mancha(color: Color, cx: Float, cy: Float, rx: Float, ry: Float, fin: Float) {
    val centro = Offset(size.width * cx, size.height * cy)
    val radioX = size.width * rx
    val radioY = size.height * ry
    scale(scaleX = 1f, scaleY = radioY / radioX, pivot = centro) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                fin to color.copy(alpha = 0f),
                center = centro,
                radius = radioX,
            ),
            radius = radioX / fin * 1.02f,
            center = centro,
        )
    }
}

/**
 * El fondo de la barra cuando el Inicio se recoge: vidrio esmerilado sobre el
 * contenido (Android 12+) o el mismo color casi opaco (Android 11 o menos).
 * [fraccion] va de 0 (invisible, se ve la Aurora) a 1 (vidrio completo).
 */
@Composable
fun VidrioDeBarra(fraccion: Float, oscuro: Boolean, negroPuro: Boolean, modifier: Modifier = Modifier) {
    if (fraccion <= 0.001f) return
    val base = when {
        oscuro && negroPuro -> Color(0xFF000000)
        oscuro -> Color(0xFF111315)
        else -> Color(0xFFF5F6F5)
    }
    val linea = if (oscuro) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.09f)
    val densidad = LocalDensity.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val desenfoque = with(densidad) { 22.dp.toPx() }
        val opacidadVidrio = if (oscuro && negroPuro) 0.56f else if (oscuro) 0.58f else 0.60f
        Box(
            modifier
                .drawBackdrop(
                    backdrop = LocalAppBackdrop.current,
                    shape = { RectangleShape },
                    effects = {
                        colorControls(saturation = 1.7f)
                        blur(desenfoque)
                    },
                    highlight = null,
                    shadow = null,
                    layerBlock = { alpha = fraccion },
                    onDrawSurface = {
                        drawRect(base.copy(alpha = opacidadVidrio))
                        drawRect(linea, topLeft = Offset(0f, size.height - 1f), size = size.copy(height = 1f))
                    },
                ),
        )
    } else {
        Box(
            modifier
                .graphicsLayer { alpha = fraccion }
                .background(base.copy(alpha = 0.95f)),
        )
    }
}

/** «Buenos días», «Buenas tardes» o «Buenas noches», con las mismas franjas del saludo de entrada. */
@Composable
fun saludoDelMomento(): String {
    val hora = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return stringResource(
        when (hora) {
            in 5..11 -> R.string.greeting_morning
            in 12..18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )
}

/**
 * El menú de la foto (2.0): lo que antes eran íconos sueltos en la barra. Tu
 * actividad (se despliega en Historial y Estadísticas), Escuchar juntos (si está
 * en la barra de arriba) y Ajustes. Cada ícono toma un color de la Aurora.
 */
@Composable
fun MenuDeLaFoto(
    abierto: Boolean,
    alCerrar: () -> Unit,
    oscuro: Boolean,
    mostrarHistorial: Boolean,
    mostrarJuntos: Boolean,
    alHistorial: () -> Unit,
    alEstadisticas: () -> Unit,
    alJuntos: () -> Unit,
    alAjustes: () -> Unit,
    mostrarEstudio: Boolean = false,
    alEstudio: () -> Unit = {},
    mostrarBandas: Boolean = false,
    alBandas: () -> Unit = {},
) {
    val colores = Aurora.colores
    var actividadAbierta by remember(abierto) { mutableStateOf(false) }
    DropdownMenu(
        expanded = abierto,
        onDismissRequest = alCerrar,
        shape = RoundedCornerShape(26.dp),
        containerColor = if (oscuro) Color(0xF5222528) else Color(0xF7FFFFFF),
        modifier = Modifier.width(280.dp),
    ) {
        OpcionDelMenu(
            icono = R.drawable.stats, color = colores.c1,
            titulo = stringResource(R.string.menu_foto_actividad),
            detalle = stringResource(if (mostrarHistorial) R.string.menu_foto_actividad_desc else R.string.stats),
            giro = if (actividadAbierta) 90f else 0f,
        ) {
            if (mostrarHistorial) actividadAbierta = !actividadAbierta else { alCerrar(); alEstadisticas() }
        }
        if (actividadAbierta) {
            SubopcionDelMenu(R.drawable.music_history, stringResource(R.string.history)) { alCerrar(); alHistorial() }
            SubopcionDelMenu(R.drawable.stats, stringResource(R.string.stats)) { alCerrar(); alEstadisticas() }
        }
        // 2.0 · Bandas (maqueta del 28-10): toma el lugar de Escuchar juntos, con su «Nuevo».
        if (mostrarBandas) {
            OpcionDelMenu(
                icono = R.drawable.bandas_acorde, color = Color(0xFFFF6B3D),
                titulo = stringResource(R.string.bandas_titulo),
                detalle = stringResource(R.string.bandas_menu_desc),
                etiqueta = stringResource(R.string.estudio_nuevo),
            ) { alCerrar(); alBandas() }
        } else if (mostrarJuntos) {
            OpcionDelMenu(
                icono = R.drawable.group_outlined, color = colores.c2,
                titulo = stringResource(R.string.menu_foto_juntos),
                detalle = stringResource(R.string.menu_foto_juntos_desc),
            ) { alCerrar(); alJuntos() }
        }
        // 2.0 · Estudio (maqueta del 27-10): entre Escuchar juntos y Ajustes, con su «Nuevo».
        if (mostrarEstudio) {
            OpcionDelMenu(
                icono = R.drawable.estudio_deslizadores, color = Color(0xFFFFB45E),
                titulo = stringResource(R.string.estudio_titulo),
                detalle = stringResource(R.string.estudio_menu_desc),
                etiqueta = stringResource(R.string.estudio_nuevo),
            ) { alCerrar(); alEstudio() }
        }
        OpcionDelMenu(
            icono = R.drawable.settings, color = colores.c3,
            titulo = stringResource(R.string.settings),
            detalle = null,
        ) { alCerrar(); alAjustes() }
    }
}

@Composable
private fun SubopcionDelMenu(icono: Int, titulo: String, alTocar: () -> Unit) {
    DropdownMenuItem(
        text = { Text(titulo, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = {
            Icon(
                painterResource(icono),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = alTocar,
        modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp)),
        contentPadding = PaddingValues(start = 62.dp, end = 10.dp),
    )
}

@Composable
private fun OpcionDelMenu(
    icono: Int,
    color: Color,
    titulo: String,
    detalle: String?,
    giro: Float = 0f,
    etiqueta: String? = null,
    alTocar: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(start = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titulo, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    if (etiqueta != null) {
                        Text(
                            etiqueta,
                            color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF8A5C) else Color(0xFFC8410F),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                if (detalle != null) {
                    Text(
                        detalle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingIcon = {
            Box(
                Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.26f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icono),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
            }
        },
        trailingIcon = {
            Icon(
                painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(giro),
            )
        },
        onClick = alTocar,
        modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp)),
        contentPadding = PaddingValues(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    )
}
