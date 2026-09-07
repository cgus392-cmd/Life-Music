package com.cglabs.lifemusic.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.KaraokeVocalReductionKey
import com.cglabs.lifemusic.constants.KaraokeVocalStrengthKey
import com.cglabs.lifemusic.lyrics.LyricsEntry
import com.cglabs.lifemusic.lyrics.LyricsUtils
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay

/**
 * Modo karaoke: la letra a pantalla completa, palabra por palabra, con la voz
 * original bajada si el usuario quiere.
 *
 * Tres decisiones que lo separan de "la vista de letra pero mas grande":
 *
 * 1. Se ve la linea SIGUIENTE. Cantar es anticipar; si solo ves la que suena,
 *    llegas tarde a la siguiente siempre. Es la diferencia entre leer y cantar.
 * 2. Hay cuenta atras en los huecos. Antes de la primera linea y en cada pasaje
 *    instrumental largo, unos puntos van marcando lo que falta para volver a
 *    entrar. Sin eso el silencio da la sensacion de que la letra se colgo.
 * 3. La palabra que suena va en color de acento, no solo resaltada. Con letra
 *    grande y varias lineas en pantalla, saber cual de todas es la de ahora
 *    tiene que costar cero.
 *
 * El resaltado es por PALABRA y no un degradado continuo como en Life Line, y es
 * a proposito: aqui la letra ocupa varias lineas de pantalla, y un degradado
 * horizontal se calcula sobre el ancho del bloque entero, con lo que a partir de
 * la segunda linea cae donde no debe. Por palabras funciona con cualquier salto
 * de linea, y ademas para cantar se lee mejor un salto limpio que una transicion
 * suave.
 */

/** Un hueco mas largo que esto cuenta como pasaje instrumental y trae cuenta atras. */
private const val HUECO_MIN_MS = 4_000L

/** La cuenta atras aparece cuando faltan menos de esto para volver a entrar. */
private const val CUENTA_ATRAS_MS = 5_000L

@Composable
fun KaraokeScreen(navController: NavController) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val letraActual by playerConnection.currentLyrics.collectAsState(initial = null)
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val (reduccionActiva, onReduccionChange) =
        rememberPreference(KaraokeVocalReductionKey, defaultValue = false)
    val (fuerza, onFuerzaChange) =
        rememberPreference(KaraokeVocalStrengthKey, defaultValue = 85)

    // Pantalla encendida y barras fuera: nadie quiere que se apague a media
    // cancion ni pulsar sin querer la barra de navegacion mientras canta.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        var controller: WindowInsetsControllerCompat? = null
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { navController.popBackStack() }

    // La posicion se lee a ~20 Hz. Mas seria gastar bateria para nada: la
    // palabra mas corta de una cancion dura bastante mas de 50 ms.
    var posicion by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            posicion = playerConnection.player.currentPosition
            delay(50)
        }
    }

    val crudo = letraActual?.takeIf { it.id == mediaMetadata?.id }?.lyrics?.trim()
    val entradas = remember(crudo) {
        crudo?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LyricsUtils.parseLyrics(it) }.getOrNull() }
            ?.takeIf { lista -> lista.any { it.time > 0L } }
    }

    val esMono = playerConnection.service.vocalReducer?.inputIsMono == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            CabeceraKaraoke(
                titulo = mediaMetadata?.title.orEmpty(),
                artista = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                onSalir = { navController.popBackStack() },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (entradas == null) {
                    Text(
                        text = stringResource(R.string.karaoke_no_lyrics),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LetraKaraoke(entradas = entradas, positionMs = posicion)
                }
            }

            ControlesKaraoke(
                reduccionActiva = reduccionActiva,
                onReduccionChange = onReduccionChange,
                fuerza = fuerza,
                onFuerzaChange = onFuerzaChange,
                esMono = esMono,
                isPlaying = isPlaying,
                onPlayPause = { playerConnection.player.togglePlayPause() },
            )
        }
    }
}

@Composable
private fun CabeceraKaraoke(
    titulo: String,
    artista: String,
    onSalir: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = artista,
                fontSize = 13.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onSalir),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.karaoke_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LetraKaraoke(
    entradas: List<LyricsEntry>,
    positionMs: Long,
) {
    val indice = entradas.indexOfLast { it.time <= positionMs }
    val actual = entradas.getOrNull(indice)
    val siguiente = entradas.getOrNull(indice + 1)

    // Donde acaba de verdad la linea que suena: con tiempos por palabra, el final
    // de la ultima; sin ellos, el comienzo de la siguiente.
    val finActual = actual?.words?.lastOrNull()?.let { (it.endTime * 1000).toLong() }
        ?: siguiente?.time
        ?: Long.MAX_VALUE

    val faltanParaEntrar = when {
        actual == null -> entradas.first().time - positionMs
        positionMs >= finActual && siguiente != null -> siguiente.time - positionMs
        else -> -1L
    }
    val huecoLargo = when {
        actual == null -> entradas.first().time > HUECO_MIN_MS
        else -> siguiente != null && siguiente.time - finActual >= HUECO_MIN_MS
    }
    val enCuentaAtras = huecoLargo && faltanParaEntrar in 0..CUENTA_ATRAS_MS

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (enCuentaAtras || actual == null) {
            CuentaAtras(faltanMs = faltanParaEntrar)
            Spacer(Modifier.height(28.dp))
        }

        AnimatedContent(
            targetState = actual,
            transitionSpec = {
                (slideInVertically(tween(320)) { it / 4 } + fadeIn(tween(320)))
                    .togetherWith(slideOutVertically(tween(240)) { -it / 4 } + fadeOut(tween(200)))
            },
            label = "karaokeLinea",
        ) { linea ->
            if (linea == null) {
                Text(
                    text = entradas.first().let { primera ->
                        primera.words?.joinToString(" ") { it.text } ?: primera.text
                    },
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                )
            } else {
                LineaPalabraAPalabra(entrada = linea, positionMs = positionMs)
            }
        }

        // La linea que viene. Sin esto se canta a ciegas.
        siguiente?.let { proxima ->
            val texto = proxima.words?.takeIf { it.isNotEmpty() }
                ?.joinToString(" ") { it.text } ?: proxima.text
            if (texto.isNotBlank()) {
                Spacer(Modifier.height(26.dp))
                Text(
                    text = texto,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/**
 * La linea que suena, con la palabra actual en color de acento, las ya cantadas
 * atenuadas y las que faltan a media luz.
 */
@Composable
private fun LineaPalabraAPalabra(
    entrada: LyricsEntry,
    positionMs: Long,
) {
    val palabras = entrada.words?.takeIf { it.isNotEmpty() }
    val cantada = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val ahora = MaterialTheme.colorScheme.primary
    val pendiente = MaterialTheme.colorScheme.onSurface

    if (palabras == null) {
        // Sin tiempos por palabra no se puede senalar cual suena; se muestra la
        // linea entera encendida, que sigue sirviendo para cantar.
        Text(
            text = entrada.text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = pendiente,
        )
        return
    }

    val indiceActual = palabras.indexOfLast { (it.startTime * 1000.0) <= positionMs }

    val texto = buildAnnotatedString {
        palabras.forEachIndexed { i, palabra ->
            val color = when {
                i < indiceActual -> cantada
                i == indiceActual -> ahora
                else -> pendiente
            }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(palabra.text)
            }
            if (i != palabras.lastIndex) append(" ")
        }
    }

    Text(
        text = texto,
        fontSize = 30.sp,
        lineHeight = 40.sp,
        textAlign = TextAlign.Center,
    )
}

/** Puntos que se apagan uno a uno hasta que toca volver a cantar. */
@Composable
private fun CuentaAtras(faltanMs: Long) {
    val restantes = ((faltanMs + 999L) / 1000L).coerceIn(0L, 3L).toInt()
    val pulso = rememberInfinitePulse()

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i ->
            val encendido = i < restantes
            Box(
                modifier = Modifier
                    .size(if (encendido) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (encendido) pulso else 0.18f
                        )
                    )
            )
        }
    }
}

@Composable
private fun rememberInfinitePulse(): Float {
    val transicion = androidx.compose.animation.core.rememberInfiniteTransition(label = "karaokePulso")
    val valor by transicion.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "karaokePulsoAlfa",
    )
    return valor
}

@Composable
private fun ControlesKaraoke(
    reduccionActiva: Boolean,
    onReduccionChange: (Boolean) -> Unit,
    fuerza: Int,
    onFuerzaChange: (Int) -> Unit,
    esMono: Boolean,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.karaoke_vocal_reduction),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (esMono) {
                        stringResource(R.string.karaoke_mono_warning)
                    } else {
                        stringResource(R.string.karaoke_vocal_reduction_desc)
                    },
                    fontSize = 12.sp,
                    color = if (esMono) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(checked = reduccionActiva, onCheckedChange = onReduccionChange)
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        if (reduccionActiva) {
            Slider(
                value = fuerza.toFloat(),
                onValueChange = { onFuerzaChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Media3 no trae togglePlayPause en el Player publico. */
private fun androidx.media3.common.Player.togglePlayPause() {
    if (isPlaying) pause() else play()
}
