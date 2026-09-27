package com.cglabs.lifemusic.car

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.GreetingEnabledKey
import com.cglabs.lifemusic.constants.RecentGreetingsKey
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/** Cuanto se queda el saludo ya leido. Algo mas que en el telefono: en el carro se lee de reojo. */
private const val REPOSO_MS = 1_400L
/** Salida del conjunto. Despacio: debajo ya esta la app y se descubre, no aparece. */
private const val SALIDA_MS = 520
/** Frases recientes que no se repiten. */
private const val SIN_REPETIR = 3
/** El verde de la hoja del icono, aclarado para leerse sobre negro. */
private val VERDE_LIFE = Color(0xFF6FD3A0)

/**
 * La entrada de Life Music for Car: la hoja de Life Music cae con un rebote,
 * sale el nombre y, debajo, el saludo del carro («Buenas noches · ¿De vuelta a
 * casa?»). Unos dos segundos y medio, o menos si se toca. La musica no espera:
 * el servicio arranca detras.
 *
 * Con el saludo apagado en Ajustes queda solo el logo, mas corto. Con las
 * animaciones del sistema apagadas no sale nada (lo decide quien lo monta).
 * Como en el telefono, sin nombre ni cuenta: sabe la hora y nada mas.
 */
@Composable
fun Arranque(alTerminar: () -> Unit) {
    val contexto = LocalContext.current
    val conSaludo by rememberPreference(GreetingEnabledKey, defaultValue = true)
    var recientes by rememberPreference(RecentGreetingsKey, defaultValue = "")
    val previas = remember { recientes.split(',').filter { it.isNotBlank() } }
    val saludo = remember { fraseDeCarro(contexto, previas) }
    LaunchedEffect(Unit) {
        if (conSaludo) recientes = (listOf(saludo.clave) + previas).distinct().take(SIN_REPETIR).joinToString(",")
    }

    val logo = remember { Animatable(0f) }
    val texto = remember { Animatable(0f) }
    val todo = remember { Animatable(1f) }
    var saltar by remember { mutableStateOf(false) }
    LaunchedEffect(saltar) {
        if (saltar) {
            todo.animateTo(0f, tween(200))
        } else {
            // La hoja cae con rebote (Expressive: muelle, no curva) y, sin esperar a
            // que se asiente, entra el texto.
            launch { logo.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 170f)) }
            delay(420)
            if (conSaludo) {
                texto.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                delay(REPOSO_MS)
            } else {
                delay(500)
            }
            todo.animateTo(0f, tween(SALIDA_MS))
        }
        alTerminar()
    }

    // La entrada es de la marca: verde Life Music siempre, aunque la caratula ya haya tenido la app de otro color.
    val primario = VERDE_LIFE
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = todo.value }
            .background(Color(0xFF09090C))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { saltar = true },
    ) {
        // Una luz verde detras de la hoja, que crece con ella.
        Canvas(Modifier.fillMaxSize()) {
            val centro = Offset(size.width / 2f, size.height * 0.40f)
            val radio = size.height * 0.95f
            drawCircle(
                Brush.radialGradient(listOf(primario.copy(alpha = 0.26f * logo.value.coerceIn(0f, 1f)), Color.Transparent), centro, radio),
                radio,
                centro,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 48.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        val v = logo.value
                        alpha = v.coerceIn(0f, 1f)
                        scaleX = 0.55f + 0.45f * v
                        scaleY = 0.55f + 0.45f * v
                        rotationZ = (1f - v) * -14f
                        translationY = (1f - v) * -40.dp.toPx()
                    },
            )
            // El PNG del icono trae aire alrededor de la hoja; el nombre sube a recogerlo.
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.offset(y = (-28).dp).graphicsLayer { alpha = logo.value.coerceIn(0f, 1f) },
            ) {
                Text(stringResource(R.string.carro_marca), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    " " + stringResource(R.string.carro_for_car),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                    color = primario,
                )
            }
            if (conSaludo) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = texto.value
                        translationY = (1f - texto.value) * 14.dp.toPx()
                    },
                ) {
                    Text(saludo.cabecera.uppercase(), style = MaterialTheme.typography.labelLarge, color = primario, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        saludo.frase,
                        style = MaterialTheme.typography.headlineMedium,
                        fontStyle = FontStyle.Italic,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Cabecera por la hora, frase del carro y su clave estable («cn_1») para no repetirla. */
class FraseDeCarro(val cabecera: String, val frase: String, val clave: String)

/**
 * Las frases del carro. Hermanas de las del telefono (fraseDeSaludo), pero de
 * camino: la manana va al trabajo, la noche vuelve a casa. La franja pesa el
 * doble que las genericas, y las [SIN_REPETIR] ultimas no salen.
 */
fun fraseDeCarro(contexto: Context, recientes: List<String>): FraseDeCarro {
    val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val franja = when (hora) {
        in 5..11 -> listOf("cm_1" to R.string.carro_saludo_m_1, "cm_2" to R.string.carro_saludo_m_2, "cm_3" to R.string.carro_saludo_m_3)
        in 12..18 -> listOf("ct_1" to R.string.carro_saludo_t_1, "ct_2" to R.string.carro_saludo_t_2, "ct_3" to R.string.carro_saludo_t_3)
        else -> listOf("cn_1" to R.string.carro_saludo_n_1, "cn_2" to R.string.carro_saludo_n_2, "cn_3" to R.string.carro_saludo_n_3)
    }
    val genericas = listOf(
        "cg_1" to R.string.carro_saludo_g_1, "cg_2" to R.string.carro_saludo_g_2,
        "cg_3" to R.string.carro_saludo_g_3, "cg_4" to R.string.carro_saludo_g_4,
    )
    val bolsa = franja + franja + genericas
    val elegida = bolsa.filter { it.first !in recientes }.ifEmpty { bolsa }.random()
    return FraseDeCarro(cabeceraDeLaHora(contexto), contexto.getString(elegida.second), elegida.first)
}
