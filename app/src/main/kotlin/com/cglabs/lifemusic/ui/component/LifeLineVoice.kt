package com.cglabs.lifemusic.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.audio.EstiloTransicion

/**
 * La voz de Life Line: lo que dice la app cuando no hay letra que cantar, o
 * cuando pasa algo por dentro que merece contarse.
 *
 * La idea es de CG, a partir de lo que hace BitChord. El objetivo no es rellenar
 * un hueco con frases bonitas —eso se nota y cansa— sino que la fila diga
 * siempre algo CIERTO sobre lo que el reproductor esta haciendo justo ahora. Por
 * eso ningun estado se inventa: todos salen de senales que PlayerConnection ya
 * publicaba y que nadie estaba mirando.
 *
 *   ERROR          error                        fallo de reproduccion
 *   SIN_RED        waitingForNetworkConnection  esperando conexion
 *   CARGANDO       playbackState                STATE_BUFFERING
 *   MEZCLANDO      automixEstilo == BLEND       mezcla al beat, graves cambiando de mano
 *   FILTRANDO      automixEstilo == FILTRO      barrido de filtro entre pistas
 *   CAMBIANDO      isCrossfading, sin plan      fundido plano entre pistas
 *   AUTOMIX        isAutomixing                 la app encadena la siguiente
 *   PAUSADO        isPlaying
 *   DESPIDIENDO    posicion contra duracion     ultimos segundos
 *   SIN_SINCRONIA  hay letra, pero sin tiempos
 *   SIN_LETRA      no hay letra en ningun proveedor
 *
 * Las cuatro primeras son [esInterrupcion]: pisan la letra aunque la haya,
 * porque describen algo que le esta pasando a la reproduccion y el usuario
 * agradece enterarse. Las demas solo hablan cuando no hay linea que mostrar,
 * para no robarle el sitio a la cancion.
 */
enum class LifeLineVoz(val esInterrupcion: Boolean) {
    ERROR(true),
    SIN_RED(true),
    CARGANDO(true),
    MEZCLANDO(true),
    FILTRANDO(true),
    CAMBIANDO(true),
    AUTOMIX(false),
    PAUSADO(false),
    DESPIDIENDO(false),
    SIN_SINCRONIA(false),
    SIN_LETRA(false),
}

/** Cuanto antes del final empieza a despedirse. */
private const val DESPEDIDA_MS = 12_000L

/**
 * Cada cuanto cambia de frase en los estados que duran. Doce segundos: lo
 * bastante espaciado para no distraer, lo bastante seguido para que una cancion
 * entera sin letra no repita siempre lo mismo.
 */
private const val ROTACION_MS = 12_000L

/**
 * Escucha al reproductor y decide que toca decir, o null si lo que toca es
 * callarse y dejar cantar a la letra.
 */
@Composable
fun recordarVozLifeLine(
    positionMs: Long,
    duracionMs: Long,
    tieneLetraSincronizada: Boolean,
    hayLetraSinSincronizar: Boolean,
): LifeLineVoz? {
    val conexion = LocalPlayerConnection.current ?: return null

    val error by conexion.error.collectAsState()
    val sinRed by conexion.waitingForNetworkConnection.collectAsState()
    val estado by conexion.playbackState.collectAsState()
    val mezclando by conexion.isCrossfading.collectAsState()
    val automix by conexion.isAutomixing.collectAsState()
    val estilo by conexion.automixEstilo.collectAsState()
    val sonando by conexion.isPlaying.collectAsState()

    return when {
        error != null -> LifeLineVoz.ERROR
        sinRed -> LifeLineVoz.SIN_RED
        estado == Player.STATE_BUFFERING -> LifeLineVoz.CARGANDO
        // «Mezclando» solo cuando de verdad hay plan al beat. Si Automix cayo a un
        // fundido plano —analisis pendiente, confianza baja— decirlo con otra frase:
        // una voz que promete una mezcla que no esta pasando es peor que ninguna.
        // El estilo lo decide el planificador por el material, y la voz lo cuenta
        // tal cual: una mezcla al beat, un barrido de filtro o un fundido plano son
        // tres cosas distintas y merecen tres frases distintas.
        mezclando && estilo == EstiloTransicion.BLEND -> LifeLineVoz.MEZCLANDO
        mezclando && (estilo == EstiloTransicion.FILTRO || estilo == EstiloTransicion.CIERRE) -> LifeLineVoz.FILTRANDO
        mezclando -> LifeLineVoz.CAMBIANDO

        // De aqui en adelante, la cancion manda: si hay letra sincronizada se
        // calla y la deja pasar.
        tieneLetraSincronizada -> null

        !sonando -> LifeLineVoz.PAUSADO
        automix -> LifeLineVoz.AUTOMIX
        duracionMs > 0L && positionMs >= duracionMs - DESPEDIDA_MS -> LifeLineVoz.DESPIDIENDO
        hayLetraSinSincronizar -> LifeLineVoz.SIN_SINCRONIA
        else -> LifeLineVoz.SIN_LETRA
    }
}

/**
 * La fila cuando habla la app. Comparte contenedor con la letra —mismo alto,
 * misma zona tactil— para que no baile nada al pasar de una a otra.
 *
 * Se distingue por como se comporta, no por como se ve: la letra barre de
 * izquierda a derecha siguiendo la voz, y esto respira despacio. Basta para leer
 * de un vistazo que ahora habla la app y no la cancion.
 */
@Composable
fun LifeLineVoiceRow(
    voz: LifeLineVoz,
    seedId: String,
    positionMs: Long,
    baseColor: Color,
    canOpenLyrics: Boolean,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frases = frasesDe(voz)
    if (frases.isEmpty()) return

    // Los estados pasajeros no rotan: duran menos que un turno.
    val turno = if (voz.esInterrupcion) 0 else (positionMs / ROTACION_MS).toInt()
    val elegida = remember(voz, seedId, turno) {
        // La semilla lleva el id de la cancion para que dos pistas seguidas sin
        // letra no saluden con la misma frase.
        val bruto = seedId.hashCode() * 31 + voz.ordinal * 17 + turno
        frases[Math.floorMod(bruto, frases.size)]
    }
    val frase = stringResource(elegida)

    val respiracion = rememberInfiniteTransition(label = "lifeLineVozRespira")
    val alfa by respiracion.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.66f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lifeLineVozAlfa",
    )

    // Un aviso —se cayo la red, esto no se pudo reproducir— tiene que leerse a
    // la primera, asi que no respira ni se apaga.
    val esAviso = voz == LifeLineVoz.ERROR || voz == LifeLineVoz.SIN_RED
    val color = if (esAviso) {
        MaterialTheme.colorScheme.error
    } else {
        baseColor.copy(alpha = alfa)
    }

    FilaLifeLine(onClick = onOpenLyrics, modifier = modifier) {
        AnimatedContent(
            targetState = frase,
            transitionSpec = { fadeIn(tween(420)).togetherWith(fadeOut(tween(320))) },
            label = "lifeLineVozFrase",
        ) { texto ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = texto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // El chevron solo aparece si tocar lleva a algun sitio. Sin letra
                // en ningun proveedor abriria un panel vacio, y una flecha que no
                // promete nada es peor que ninguna.
                if (canOpenLyrics) {
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
    }
}

/**
 * Las frases de cada estado, como cadenas sueltas y no como <string-array>.
 *
 * No es capricho: este proyecto no habia compilado nunca un string-array, y el
 * mezclador de recursos de AGP 9.0.0 revienta con un NullPointerException al
 * procesar sus <item>, que no llevan atributo name. Sueltas ademas se traducen
 * y se buscan una por una, que para un texto que el usuario lee viene bien.
 */
private fun frasesDe(voz: LifeLineVoz): IntArray = when (voz) {
    LifeLineVoz.ERROR -> intArrayOf(
        R.string.life_line_voice_error_1,
        R.string.life_line_voice_error_2,
    )
    LifeLineVoz.SIN_RED -> intArrayOf(
        R.string.life_line_voice_offline_1,
        R.string.life_line_voice_offline_2,
    )
    LifeLineVoz.CARGANDO -> intArrayOf(
        R.string.life_line_voice_buffering_1,
        R.string.life_line_voice_buffering_2,
    )
    LifeLineVoz.MEZCLANDO -> intArrayOf(
        R.string.life_line_voice_crossfade_1,
        R.string.life_line_voice_crossfade_2,
        R.string.life_line_voice_crossfade_3,
        R.string.life_line_voice_crossfade_4,
    )
    LifeLineVoz.FILTRANDO -> intArrayOf(
        R.string.life_line_voice_filter_1,
        R.string.life_line_voice_filter_2,
        R.string.life_line_voice_filter_3,
    )
    LifeLineVoz.CAMBIANDO -> intArrayOf(
        R.string.life_line_voice_switching_1,
        R.string.life_line_voice_switching_2,
        R.string.life_line_voice_switching_3,
    )
    LifeLineVoz.AUTOMIX -> intArrayOf(
        R.string.life_line_voice_automix_1,
        R.string.life_line_voice_automix_2,
        R.string.life_line_voice_automix_3,
    )
    LifeLineVoz.PAUSADO -> intArrayOf(
        R.string.life_line_voice_paused_1,
        R.string.life_line_voice_paused_2,
        R.string.life_line_voice_paused_3,
    )
    LifeLineVoz.DESPIDIENDO -> intArrayOf(
        R.string.life_line_voice_ending_1,
        R.string.life_line_voice_ending_2,
        R.string.life_line_voice_ending_3,
    )
    LifeLineVoz.SIN_SINCRONIA -> intArrayOf(
        R.string.life_line_voice_unsynced_1,
        R.string.life_line_voice_unsynced_2,
        R.string.life_line_voice_unsynced_3,
    )
    LifeLineVoz.SIN_LETRA -> intArrayOf(
        R.string.life_line_voice_no_lyrics_1,
        R.string.life_line_voice_no_lyrics_2,
        R.string.life_line_voice_no_lyrics_3,
        R.string.life_line_voice_no_lyrics_4,
        R.string.life_line_voice_no_lyrics_5,
        R.string.life_line_voice_no_lyrics_6,
        R.string.life_line_voice_no_lyrics_7,
        R.string.life_line_voice_no_lyrics_8,
    )
}
