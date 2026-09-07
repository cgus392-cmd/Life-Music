package com.cglabs.lifemusic.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toArgb
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.LifeLineCustomColorKey
import com.cglabs.lifemusic.constants.LifeLineEnabledKey
import com.cglabs.lifemusic.constants.LifeLineHighlight
import com.cglabs.lifemusic.constants.LifeLineHighlightKey
import com.cglabs.lifemusic.constants.LifeLineTranslationKey
import com.cglabs.lifemusic.lyrics.LyricsEntry
import com.cglabs.lifemusic.lyrics.LyricsUtils
import com.cglabs.lifemusic.utils.rememberEnumPreference
import com.cglabs.lifemusic.utils.rememberPreference

/**
 * Life Line: la linea de letra viva en la vista principal del reproductor.
 *
 * Muestra UNA sola linea de la cancion bajo el artista, con la parte ya cantada
 * resaltada palabra por palabra, y un chevron para abrir la letra completa. No
 * sustituye a la caratula: convive con ella. Eso la distingue de InlineLyricsView,
 * que reemplaza la portada por el panel de letras entero.
 *
 * Sobre la sincronia: el reproductor actualiza la posicion cada pocas decenas de
 * milisegundos, no por fotograma. En vez de leerla a 60 Hz —que gastaria bateria
 * para nada— se calcula la fraccion objetivo y se suaviza con un tween corto, la
 * misma tecnica que ya usa LifeMusicLyrics. El barrido sale fluido y el trabajo
 * lo hace la animacion, no la recomposicion.
 */
private const val ANTICIPO_MS = 400L
private const val DURACION_POR_DEFECTO_MS = 4000L

/**
 * Envoltorio para el reproductor: lee las preferencias, saca la letra de la
 * cancion en curso y la deja parseada. Player.kt solo tiene que colocarlo.
 */
@Composable
fun LifeLineSection(
    positionMs: Long,
    baseColor: Color,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activa by rememberPreference(LifeLineEnabledKey, defaultValue = true)
    if (!activa) return

    val conexion = LocalPlayerConnection.current ?: return
    val resaltado by rememberEnumPreference(LifeLineHighlightKey, LifeLineHighlight.DYNAMIC)
    val colorPropio by rememberPreference(LifeLineCustomColorKey, Color.White.toArgb())
    val traducir by rememberPreference(LifeLineTranslationKey, defaultValue = false)

    val letraActual by conexion.currentLyrics.collectAsState(initial = null)
    val crudo = letraActual?.lyrics?.trim()

    // El parseo es caro y la letra solo cambia al cambiar de cancion.
    val entradas = remember(crudo) {
        crudo?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LyricsUtils.parseLyrics(it) }.getOrNull() }
            ?.takeIf { lista -> lista.any { it.time > 0L } } // sin sincronia no hay barrido que valga
    }

    LifeLine(
        entries = entradas,
        positionMs = positionMs,
        offsetMs = 0L,
        baseColor = baseColor,
        highlight = resaltado,
        customColor = Color(colorPropio),
        showTranslation = traducir,
        onOpenLyrics = onOpenLyrics,
        modifier = modifier,
    )
}

@Composable
fun LifeLine(
    entries: List<LyricsEntry>?,
    positionMs: Long,
    offsetMs: Long,
    baseColor: Color,
    highlight: LifeLineHighlight,
    customColor: Color,
    showTranslation: Boolean,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sin letra todavia, o cancion sin letra: la fila desaparece entera. Nunca se
    // deja un hueco reservado, para que el reproductor no de un salto al llegar.
    if (entries.isNullOrEmpty()) return

    val efectiva = positionMs + offsetMs + ANTICIPO_MS
    // Antes de la primera linea —las intros suelen durar diez segundos o mas— se
    // muestra esa primera linea entera en color de pendiente, en vez de no pintar
    // nada. Asi se ve lo que viene y, sobre todo, la fila no aparece de golpe
    // empujando el slider y los controles hacia abajo a mitad de cancion.
    val indice = entries.indexOfLast { it.time <= efectiva }.coerceAtLeast(0)
    val entrada = entries[indice]

    val finLinea = entries.getOrNull(indice + 1)?.time ?: (entrada.time + DURACION_POR_DEFECTO_MS)
    val duracion = (finLinea - entrada.time).coerceAtLeast(1L)

    // Las palabras no traen espacios: se unen igual que en LifeMusicLyrics, para
    // que los indices de caracter cuadren con el texto que se pinta.
    val palabras = entrada.words?.takeIf { it.isNotEmpty() }
    val texto = remember(entrada) {
        palabras?.joinToString(" ") { it.text } ?: entrada.text
    }

    val relativa = (positionMs + offsetMs - entrada.time).coerceAtLeast(0L)
    val objetivo = remember(texto, relativa / 50, duracion) {
        fraccionCantada(texto, palabras, entrada.time, relativa, duracion)
    }
    val avance by animateFloatAsState(
        targetValue = objetivo,
        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
        label = "lifeLineSweep",
    )

    val cantado = when (highlight) {
        LifeLineHighlight.DYNAMIC -> MaterialTheme.colorScheme.primary
        LifeLineHighlight.WHITE, LifeLineHighlight.ACCENT_EDGE -> baseColor
        LifeLineHighlight.CUSTOM -> customColor
    }
    val pendiente = baseColor.copy(alpha = 0.42f)
    val filo = if (highlight == LifeLineHighlight.ACCENT_EDGE) {
        MaterialTheme.colorScheme.primary
    } else {
        cantado
    }

    Column(
        // 48dp de alto minimo dan la zona tactil que pide accesibilidad, y el
        // contenido se centra dentro. Nada de padding vertical encima: sumaria
        // 12dp mas de aire para una linea de 14sp que no los necesita.
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenLyrics)
            .defaultMinSize(minHeight = 48.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = entrada,
            transitionSpec = {
                (slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(260)))
                    .togetherWith(slideOutVertically(tween(200)) { -it / 3 } + fadeOut(tween(160)))
            },
            label = "lifeLineLinea",
        ) { actual ->
            val textoActual = actual.words?.takeIf { it.isNotEmpty() }
                ?.joinToString(" ") { it.text } ?: actual.text

            if (textoActual.isBlank()) {
                // Pasaje instrumental: mejor una nota discreta que un hueco raro.
                Text(
                    text = "♪",
                    style = MaterialTheme.typography.titleMedium,
                    color = pendiente,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineaBarrida(
                        texto = textoActual,
                        avance = avance,
                        cantado = cantado,
                        pendiente = pendiente,
                        filo = filo,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        tint = baseColor.copy(alpha = 0.45f),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(14.dp),
                    )
                }
            }
        }

        if (showTranslation) {
            val traduccion by entrada.translatedTextFlow.collectAsState()
            traduccion?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = baseColor.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/**
 * Pinta la linea con el degradado movil y la desplaza para que la palabra que
 * suena siga a la vista. El desplazamiento es una traslacion de capa, no un
 * scroll: no toca el arbol de composicion en cada fotograma.
 */
@Composable
private fun LineaBarrida(
    texto: String,
    avance: Float,
    cantado: Color,
    pendiente: Color,
    filo: Color,
    modifier: Modifier = Modifier,
) {
    var anchoCaja by remember { mutableStateOf(0) }
    var disposicion by remember(texto) { mutableStateOf<TextLayoutResult?>(null) }

    val anchoTexto = disposicion?.size?.width?.toFloat() ?: 0f
    val desborda = anchoTexto > anchoCaja && anchoCaja > 0
    val destino = if (!desborda) {
        0f
    } else {
        // La palabra viva se mantiene al 40% del ancho visible: deja contexto
        // detras y anticipa lo que viene.
        (avance * anchoTexto - anchoCaja * 0.4f).coerceIn(0f, anchoTexto - anchoCaja)
    }
    val desplazamiento by animateFloatAsState(
        targetValue = destino,
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "lifeLineScroll",
    )

    val brocha = remember(avance, cantado, pendiente, filo) {
        val corte = avance.coerceIn(0f, 1f)
        if (filo != cantado) {
            Brush.horizontalGradient(
                0f to cantado,
                (corte - 0.04f).coerceAtLeast(0f) to cantado,
                corte to filo,
                (corte + 0.04f).coerceAtMost(1f) to pendiente,
                1f to pendiente,
            )
        } else {
            Brush.horizontalGradient(
                0f to cantado,
                (corte - 0.02f).coerceAtLeast(0f) to cantado,
                (corte + 0.02f).coerceAtMost(1f) to pendiente,
                1f to pendiente,
            )
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { anchoCaja = it.width },
    ) {
        Text(
            text = texto,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            onTextLayout = { disposicion = it },
            style = TextStyle(
                brush = brocha,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            // Sin acotar: el texto mide lo que necesita y la caja lo recorta.
            // Asi hay algo que desplazar cuando la linea no cabe.
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -desplazamiento },
        )
    }
}

/**
 * Que fraccion de la linea lleva cantada, en espacio de CARACTERES —no de
 * palabras— para que el degradado caiga donde esta el texto y no donde estaria
 * si todas las palabras midieran igual.
 *
 * Con tiempos por palabra usa los reales. Sin ellos reparte la duracion de la
 * linea proporcionalmente a la longitud de cada palabra, que es la misma
 * aproximacion que ya hace LifeMusicLyrics y se nota bastante mejor que un
 * reparto lineal a ciegas.
 */
private fun fraccionCantada(
    texto: String,
    palabras: List<com.cglabs.lifemusic.lyrics.WordTimestamp>?,
    inicioLinea: Long,
    relativa: Long,
    duracion: Long,
): Float {
    if (texto.isEmpty()) return 0f
    val total = texto.length.toFloat()

    if (palabras.isNullOrEmpty()) {
        return (relativa.toFloat() / duracion).coerceIn(0f, 1f)
    }

    var caracteres = 0
    palabras.forEachIndexed { i, palabra ->
        val desde = ((palabra.startTime * 1000).toLong() - inicioLinea).coerceAtLeast(0L)
        val hasta = ((palabra.endTime * 1000).toLong() - inicioLinea).coerceAtLeast(desde + 1L)
        val inicioChar = caracteres + if (i > 0) 1 else 0 // el espacio que la precede
        val finChar = inicioChar + palabra.text.length

        when {
            relativa >= hasta -> caracteres = finChar
            relativa < desde -> return (inicioChar / total).coerceIn(0f, 1f)
            else -> {
                val dentro = (relativa - desde).toFloat() / (hasta - desde)
                val pos = inicioChar + palabra.text.length * dentro
                return (pos / total).coerceIn(0f, 1f)
            }
        }
    }
    return 1f
}
