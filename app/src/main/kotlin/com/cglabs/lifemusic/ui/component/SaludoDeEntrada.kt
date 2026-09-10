package com.cglabs.lifemusic.ui.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.GreetingEnabledKey
import com.cglabs.lifemusic.constants.LastColdStartAtKey
import com.cglabs.lifemusic.constants.RecentGreetingsKey
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay
import java.util.Calendar

/** Entrada, 380 ms. Lo justo para que no parezca que ya estaba ahi. */
private const val ENTRADA_MS = 380
/** Quieto, 800 ms. Se lee una frase corta dos veces; no da para aburrirse. */
private const val REPOSO_MS = 800L
/** Salida, 420 ms. Un pelin mas larga que la entrada: irse despacio se siente mejor. */
private const val SALIDA_MS = 420
/** Si tocas, se va en esto. Nunca se retiene a nadie. */
private const val SALIDA_TOQUE_MS = 200

/** A partir de aqui, «ha pasado un tiempo». Una semana sin abrir merece otra frase. */
private const val AUSENCIA_LARGA_DIAS = 7
/** Cuantas frases recientes se evitan. Tres: con once distintas por franja, sobra. */
private const val SIN_REPETIR = 3

/**
 * Bandera de proceso: el saludo sale UNA vez por arranque en frio y se acabo.
 * Girar la pantalla recrea la Activity pero no el proceso, asi que no vuelve a
 * salir; cerrar la app del todo y abrirla, si. Es la definicion mas honesta de
 * «arranque» que hay, y no necesita guardar nada.
 */
object SaludoEntrada {
    @Volatile
    private var reclamado = false

    /** Devuelve true solo la primera vez que se pregunta en este proceso. */
    fun reclamarArranqueEnFrio(): Boolean = synchronized(this) {
        if (reclamado) false else { reclamado = true; true }
    }
}

/**
 * Saludo de entrada: una frase a pantalla completa, antes de Inicio.
 *
 * Es la voz de Life Line saliendo del reproductor: la app habla tambien en la
 * puerta. Sabe la hora y cuanto hace que no abres, y nada mas —ni nombre ni
 * cuenta—. Se va sola en 1,6 s, o antes si tocas. Nunca bloquea la reproduccion,
 * que arranca detras.
 *
 * No se muestra si esta apagado en Apariencia, ni si el sistema tiene las
 * animaciones desactivadas: un adorno que ignora ese ajuste no es vistoso, es
 * maleducado. El que lo monta decide ademas no mostrarlo cuando va a salir la
 * bienvenida de primer arranque, para no encadenar dos intros.
 */
@Composable
fun SaludoDeEntrada(onTerminado: () -> Unit) {
    val contexto = LocalContext.current
    val activo by rememberPreference(GreetingEnabledKey, defaultValue = true)
    val animar = remember(contexto) {
        Settings.Global.getFloat(
            contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
    }

    if (!activo || !animar) {
        LaunchedEffect(Unit) { onTerminado() }
        return
    }

    // Se lee el arranque anterior y las frases recientes ANTES de sobrescribirlos.
    var ultimoArranque by rememberPreference(LastColdStartAtKey, defaultValue = 0L)
    var recientes by rememberPreference(RecentGreetingsKey, defaultValue = "")
    val ahora = remember { System.currentTimeMillis() }
    val diasSinAbrir = remember {
        val previo = ultimoArranque
        if (previo <= 0L) 0 else ((ahora - previo) / 86_400_000L).toInt()
    }
    val previas = remember { recientes.split(',').filter { it.isNotBlank() } }

    val (cabecera, frase, clave) = elegirFrase(diasSinAbrir, previas)
    LaunchedEffect(Unit) {
        ultimoArranque = ahora
        recientes = (listOf(clave) + previas).distinct().take(SIN_REPETIR).joinToString(",")
    }

    val alfa = remember { Animatable(0f) }
    var saltar by remember { mutableStateOf(false) }
    LaunchedEffect(saltar) {
        if (!saltar) {
            alfa.animateTo(1f, tween(ENTRADA_MS, easing = FastOutSlowInEasing))
            delay(REPOSO_MS)
            alfa.animateTo(0f, tween(SALIDA_MS))
        } else {
            alfa.animateTo(0f, tween(SALIDA_TOQUE_MS))
        }
        onTerminado()
    }

    val primario = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = alfa.value }
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { saltar = true },
        contentAlignment = Alignment.Center,
    ) {
        // Una sola mancha de luz, quieta. La bienvenida tiene dos y derivan; esto
        // dura segundo y medio y no necesita mas.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radio = size.width * 0.85f
            val centro = Offset(size.width * 0.5f, size.height * 0.42f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primario.copy(alpha = 0.16f), Color.Transparent),
                    center = centro,
                    radius = radio,
                ),
                radius = radio,
                center = centro,
            )
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .graphicsLayer { translationY = (1f - alfa.value) * 14.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = cabecera,
                style = MaterialTheme.typography.labelLarge,
                color = primario,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = frase,
                style = MaterialTheme.typography.displaySmall,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = 46.sp,
            )
        }
    }
}

/**
 * Cabecera por franja horaria y frase al azar entre las que tocan, evitando las
 * [SIN_REPETIR] ultimas: cada arranque en frio suena distinto. Devuelve tambien
 * la clave estable de la frase («t_1», «g_8») para recordarla.
 */
@Composable
private fun elegirFrase(diasSinAbrir: Int, recientes: List<String>): Triple<String, String, String> {
    val hora = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val vuelta = diasSinAbrir >= AUSENCIA_LARGA_DIAS

    val cabecera = stringResource(
        when {
            vuelta -> R.string.greeting_back
            hora in 5..11 -> R.string.greeting_morning
            hora in 12..18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    )

    val genericas = listOf(
        "g_1" to R.string.greeting_g_1, "g_2" to R.string.greeting_g_2,
        "g_3" to R.string.greeting_g_3, "g_4" to R.string.greeting_g_4,
        "g_5" to R.string.greeting_g_5, "g_6" to R.string.greeting_g_6,
        "g_7" to R.string.greeting_g_7, "g_8" to R.string.greeting_g_8,
    )
    val franja = when {
        vuelta -> listOf("v_1" to R.string.greeting_v_1, "v_2" to R.string.greeting_v_2, "v_3" to R.string.greeting_v_3)
        hora in 5..11 -> listOf("m_1" to R.string.greeting_m_1, "m_2" to R.string.greeting_m_2, "m_3" to R.string.greeting_m_3)
        hora in 12..18 -> listOf("t_1" to R.string.greeting_t_1, "t_2" to R.string.greeting_t_2, "t_3" to R.string.greeting_t_3)
        else -> listOf("n_1" to R.string.greeting_n_1, "n_2" to R.string.greeting_n_2, "n_3" to R.string.greeting_n_3)
    }
    // Tras una ausencia larga solo valen las de vuelta; el resto de dias, la
    // franja horaria pesa el doble que las genericas para que se note la hora.
    val bolsa = if (vuelta) franja else franja + franja + genericas

    // Fuera las recientes. Si eso vacia la bolsa (solo pasa con las tres de
    // vuelta), basta con no repetir la ultima; y si ni asi, la que sea.
    val elegida = remember {
        bolsa.filter { it.first !in recientes }
            .ifEmpty { bolsa.filter { it.first != recientes.firstOrNull() } }
            .ifEmpty { bolsa }
            .random()
    }

    return Triple(cabecera, stringResource(elegida.second), elegida.first)
}
