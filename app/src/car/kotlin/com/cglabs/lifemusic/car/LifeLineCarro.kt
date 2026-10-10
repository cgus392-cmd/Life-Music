package com.cglabs.lifemusic.car

import android.os.SystemClock
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.LifeLineEnabledKey
import com.cglabs.lifemusic.constants.LifeLineHighlight
import com.cglabs.lifemusic.db.entities.LyricsEntity
import com.cglabs.lifemusic.lyrics.LyricsUtils
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.ui.component.FraseLifeLine
import com.cglabs.lifemusic.ui.component.LifeLine
import com.cglabs.lifemusic.ui.component.LifeLineVoiceRow
import com.cglabs.lifemusic.ui.component.LifeLineVoz
import com.cglabs.lifemusic.ui.component.recordarVozLifeLine
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Las voces propias del carro. Como las del telefono, ninguna se inventa: cada
 * una sale de algo que de verdad esta pasando (se reanudo al encender, la
 * siguiente ya quedo guardada, suena sin internet).
 */
enum class VozDeCarretera(val frase: Int) {
    RETOMANDO(R.string.carro_voz_retomando),
    GUARDADA(R.string.carro_voz_guardada),
    DE_LO_GUARDADO(R.string.carro_voz_de_lo_guardado),
}

/**
 * Las voces pasajeras las anuncia quien las causa (reanudar, la precarga), con
 * lo que deben durar; Life Line las dice mientras tanto y luego vuelve a la
 * letra. Vive con el proceso: un aviso no depende de que la pantalla este viva.
 */
object VocesDeCarretera {
    /** La voz y hasta cuando vale (reloj de SystemClock.elapsedRealtime). */
    private val _pasajera = MutableStateFlow<Pair<VozDeCarretera, Long>?>(null)
    val pasajera: StateFlow<Pair<VozDeCarretera, Long>?> = _pasajera.asStateFlow()

    fun decir(voz: VozDeCarretera, duranteMs: Long = 8_000L) {
        _pasajera.value = voz to SystemClock.elapsedRealtime() + duranteMs
    }
}

/**
 * Life Line en el carro: la linea de letra viva del telefono, mas grande para
 * leerla de reojo, entre el artista y la barra. Sin letra habla la app.
 *
 * Quien manda, de mas a menos:
 *  1. un error o la red caida que corto la musica (el aviso del telefono, en rojo);
 *  2. una voz de carretera pasajera (retomando, guardada, la entrada en el tunel);
 *  3. el resto de interrupciones del telefono (cargando, transiciones);
 *  4. la letra sincronizada;
 *  5. sin internet pero sonando: «sonando de lo guardado»;
 *  6. la voz tranquila del telefono (pausa, despedida, sin letra).
 *
 * Con Life Line apagado, o con el panel de Letra abierto (seria la misma frase
 * dos veces), deja solo la [separacion] de siempre: el diseno de la 0.2.0.
 */
@Composable
fun LifeLineCarro(
    conexion: PlayerConnection?,
    sonando: Boolean,
    tamano: TextUnit,
    alto: Dp,
    separacion: Dp,
    alAbrirLetra: () -> Unit,
    modifier: Modifier = Modifier,
    oculta: Boolean = false,
) {
    val activa by rememberPreference(LifeLineEnabledKey, defaultValue = true)
    if (!activa || oculta || conexion == null) {
        Spacer(Modifier.height(separacion))
        return
    }
    val suave = LocalPerfil.current == Perfil.COMPLETO

    val cancion by conexion.mediaMetadata.collectAsState()
    val letra by conexion.currentLyrics.collectAsState(initial = null)
    // Solo vale la letra de ESTA cancion (al cambiar, Room tarda en emitir la nueva).
    val crudo = letra?.takeIf { it.id == cancion?.id }?.lyrics?.trim()
    val entradas = remember(crudo) {
        crudo?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LyricsUtils.parseLyrics(it) }.getOrNull() }
            ?.takeIf { lista -> lista.any { it.time > 0L } }
    }
    val haySincronia = !entradas.isNullOrEmpty()
    val hayLetraGuardada = !crudo.isNullOrEmpty() && crudo != LyricsEntity.LYRICS_NOT_FOUND

    // La posicion: cada 100 ms en Completo; cada 250 en Ligero, que el barrido
    // ya lo suaviza la animacion y los radios flojos lo agradecen.
    var posicion by remember { mutableLongStateOf(0L) }
    LaunchedEffect(conexion, sonando, suave) {
        val p = conexion.player
        while (true) {
            posicion = p.currentPosition
            if (!sonando) break
            delay(if (suave) 100L else 250L)
        }
    }
    val duracion = conexion.player.duration.takeIf { it > 0 } ?: ((cancion?.duration ?: 0).toLong() * 1000L)

    val vozTelefono = recordarVozLifeLine(
        positionMs = posicion,
        duracionMs = duracion,
        tieneLetraSincronizada = haySincronia,
        hayLetraSinSincronizar = hayLetraGuardada && !haySincronia,
    )

    // Sin internet y sonando: lo que suena sale de lo ya cargado. Al entrar en
    // ese estado se dice un momento aunque haya letra (es lo que uno quiere saber
    // al meterse a un tunel); despues, solo si no hay letra que mostrar.
    val monitor = LocalMonitorDeRed.current
    val flujoRed: StateFlow<EstadoRed?> = remember(monitor) { monitor?.estado ?: MutableStateFlow<EstadoRed?>(null) }
    val red by flujoRed.collectAsState()
    val deLoGuardado = sonando && red?.calidad == CalidadRed.SIN_INTERNET
    LaunchedEffect(deLoGuardado) {
        if (deLoGuardado) VocesDeCarretera.decir(VozDeCarretera.DE_LO_GUARDADO, 6_000L)
    }

    val anunciada by VocesDeCarretera.pasajera.collectAsState()
    var pasajera by remember { mutableStateOf<VozDeCarretera?>(null) }
    LaunchedEffect(anunciada) {
        val (voz, hasta) = anunciada ?: run { pasajera = null; return@LaunchedEffect }
        val resta = hasta - SystemClock.elapsedRealtime()
        if (resta > 0) {
            pasajera = voz
            delay(resta)
        }
        pasajera = null
    }

    val vozCarro = when {
        vozTelefono == LifeLineVoz.ERROR || vozTelefono == LifeLineVoz.SIN_RED -> null
        pasajera != null -> pasajera
        vozTelefono?.esInterrupcion == true -> null
        haySincronia -> null
        deLoGuardado -> VozDeCarretera.DE_LO_GUARDADO
        else -> null
    }

    val base = MaterialTheme.colorScheme.onSurface
    when {
        vozCarro != null -> FraseLifeLine(
            frase = stringResource(vozCarro.frase),
            esAviso = false,
            baseColor = base,
            canOpenLyrics = hayLetraGuardada,
            onOpenLyrics = alAbrirLetra,
            modifier = modifier,
            tamano = tamano,
            altoMinimo = alto,
        )
        vozTelefono != null -> LifeLineVoiceRow(
            voz = vozTelefono,
            seedId = cancion?.id.orEmpty(),
            positionMs = posicion,
            baseColor = base,
            canOpenLyrics = hayLetraGuardada,
            onOpenLyrics = alAbrirLetra,
            modifier = modifier,
            tamano = tamano,
            altoMinimo = alto,
        )
        haySincronia -> LifeLine(
            entries = entradas,
            positionMs = posicion,
            offsetMs = 0L,
            baseColor = base,
            highlight = LifeLineHighlight.DYNAMIC,
            customColor = base,
            showTranslation = false,
            onOpenLyrics = alAbrirLetra,
            modifier = modifier,
            tamano = tamano,
            altoMinimo = alto,
            suave = suave,
        )
        else -> Spacer(Modifier.height(separacion))
    }
}

/** Los tamanos de Life Line en el carro: Sonando y el panel de Inicio. */
object TamanoLifeLine {
    val SONANDO = 22.sp
    val SONANDO_ALTO = 44.dp
    val INICIO = 18.sp
    val INICIO_ALTO = 36.dp
}
