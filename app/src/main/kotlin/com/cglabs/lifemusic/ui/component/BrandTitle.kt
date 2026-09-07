package com.cglabs.lifemusic.ui.component

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import kotlinx.coroutines.delay

/**
 * El titulo de la barra superior, que en la pantalla de inicio se releva con el
 * wordmark de CG LABS y vuelve.
 *
 * Idea de CG. La gracia es que la marca paraguas aparezca sin ocupar sitio
 * propio: no hay un logo permanente robando espacio a la barra, sino el mismo
 * hueco del titulo diciendo dos cosas por turnos.
 *
 * Solo se releva cuando el titulo ES el nombre de la app. En Buscar, Biblioteca
 * o Escuchar juntos el titulo dice donde estas, y sustituirlo por un logo seria
 * quitarle al usuario la unica referencia de en que pantalla se encuentra.
 */

/**
 * El relevo, en dos tiempos. Casi dos segundos enteros: la marca no irrumpe, se
 * disuelve. Con curva seno de entrada y salida, que arranca y termina despacio;
 * la de Material por defecto empieza de golpe y en un fundido se nota como un
 * corte.
 */
private const val SALIDA_MS = 900
private const val ENTRADA_MS = 1_100
private const val TRANSICION_MS = (SALIDA_MS + ENTRADA_MS).toLong()

/** Cuanto se queda el nombre de la app, ya del todo visible. Es el reposo. */
private const val ESPERA_NOMBRE_MS = 6_000L

/** Cuanto se queda el wordmark. Corto: es una firma, no un cartel. */
private const val ESPERA_MARCA_MS = 3_000L

@Composable
fun BrandTitle(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val nombreDeApp = stringResource(R.string.app_name)
    val contexto = LocalContext.current

    // Si el sistema tiene las animaciones desactivadas —ajuste de accesibilidad,
    // o modo de bateria en algunos equipos— no se releva nada. Una marca que
    // parpadea sin transicion no es minimalista, es un error visual.
    val animar = remember(contexto) {
        Settings.Global.getFloat(
            contexto.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    if (text != nombreDeApp || !animar) {
        Text(text = text, style = style, modifier = modifier)
        return
    }

    var mostrandoMarca by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            // Hay que sumar la transicion: el reloj cuenta desde que se ordena el
            // cambio, no desde que la transicion termina. Sin sumarla, el
            // wordmark se pasaba casi todo su turno entrando y saliendo, del todo
            // visible apenas medio segundo. Las esperas de arriba son tiempo
            // quieto de verdad.
            delay(TRANSICION_MS + if (mostrandoMarca) ESPERA_MARCA_MS else ESPERA_NOMBRE_MS)
            mostrandoMarca = !mostrandoMarca
        }
    }

    AnimatedContent(
        targetState = mostrandoMarca,
        // Encadenado, no cruzado: primero se apaga uno del todo y despues entra
        // el otro. Solapar texto y logo en el mismo sitio se ve sucio, y lo que
        // pidio CG fue que se atenuara y luego apareciera.
        transitionSpec = {
            fadeIn(tween(ENTRADA_MS, delayMillis = SALIDA_MS, easing = EaseInOutSine))
                .togetherWith(fadeOut(tween(SALIDA_MS, easing = EaseInOutSine)))
                // Sin recorte ni animacion de tamano: el hueco del titulo lo
                // marca "Life Music", que es lo mas ancho, y asi los iconos de
                // la derecha no se mueven en cada relevo.
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        label = "tituloDeMarca",
        modifier = modifier,
    ) { marca ->
        if (marca) {
            // El alto sale del tamano del titulo, no de un numero fijo: si el
            // usuario tiene la fuente grande, el wordmark crece con ella.
            val tam = style.fontSize
            val alto = with(LocalDensity.current) {
                (if (tam.type == TextUnitType.Sp) tam else 24.sp).toDp() * 0.92f
            }
            Image(
                painter = painterResource(R.drawable.cglabs_wordmark),
                contentDescription = "CG LABS",
                // Tenido con el color del propio titulo, y no confiando en
                // drawable-night: el tema de la app es independiente del tema del
                // sistema —Ajustes tiene claro/oscuro/automatico y negro puro—,
                // asi que -night puede servir el wordmark blanco sobre un fondo
                // claro y dejarlo invisible. Tenirlo lo ata al color que de
                // verdad se esta usando. El PNG es monocromo (croma maxima 12
                // sobre 255), asi que no se pierde nada al hacerlo.
                colorFilter = ColorFilter.tint(LocalContentColor.current),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(alto),
            )
        } else {
            Text(text = text, style = style)
        }
    }
}
