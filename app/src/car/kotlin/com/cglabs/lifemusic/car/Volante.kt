package com.cglabs.lifemusic.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.MediaLibrarySessionCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.DateFormat
import java.util.Date

/** Por donde llego un boton a la app. */
enum class ViaDeTecla(val texto: Int) {
    /** La ventana de la app, cuando esta al frente. */
    VENTANA(R.string.carro_tecla_via_ventana),

    /** La sesion de medios: llega aunque la app este detras (Waze, Maps). */
    SESION(R.string.carro_tecla_via_sesion),

    /** Un aviso propio del fabricante del radio, en vez de una tecla normal. */
    FABRICANTE(R.string.carro_tecla_via_fabricante),
}

/** Un boton recibido: su codigo de tecla, por donde llego y, si vino de un aviso, cual. */
data class Pulsacion(val codigo: Int, val via: ViaDeTecla, val cuando: Long, val detalle: String? = null)

/**
 * Los botones del volante y del radio.
 *
 * Android le manda las teclas de medios a la ventana que esta al frente y, si no
 * las usa, a la sesion de medios de la ultima app que sono. Algunos radios, en
 * cambio, mandan sus propios avisos. Las tres vias se escuchan:
 *  - VENTANA: CarActivity.dispatchKeyEvent. Con la app al frente la tecla se
 *    atiende ahi mismo y no sigue, asi no se hace dos veces.
 *  - SESION: MediaLibrarySessionCallback.alBotonDeMedios. Solo se escucha; lo
 *    que hace la tecla lo decide Media3, como siempre.
 *  - FABRICANTE: [FabricanteDeTeclas]. Por ahora solo se anota; que hace cada
 *    codigo se decide cuando se vea que manda el radio de CG.
 *
 * Con el probador abierto ([probando]) la ventana deja pasar las teclas: asi se
 * ve si tambien llegan por la sesion, que es lo que importa con la app detras.
 */
object TeclasDelVolante {
    private const val MAXIMO = 12
    private const val REPETIDA_MS = 300L

    private val _ultimas = MutableStateFlow<List<Pulsacion>>(emptyList())
    val ultimas: StateFlow<List<Pulsacion>> = _ultimas.asStateFlow()

    @Volatile
    var probando = false

    /** La ultima accion hecha (codigo y reloj), para no repetirla si llega por dos vias. */
    private var ultimaAccion: Pair<Int, Long>? = null

    private val DE_MEDIOS = setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_HEADSETHOOK,
    )

    fun esDeMedios(codigo: Int) = codigo in DE_MEDIOS

    fun recibir(context: Context, codigo: Int, via: ViaDeTecla, detalle: String? = null) {
        val pulsacion = Pulsacion(codigo, via, System.currentTimeMillis(), detalle)
        _ultimas.update { (listOf(pulsacion) + it).take(MAXIMO) }
        Bitacora.anotar(context, describir(Bitacora.enIdiomaDeLaApp(context), pulsacion))
    }

    /** Hace lo que pide la tecla. Devuelve false si no es de medios o si era repetida. */
    fun actuar(player: Player?, codigo: Int): Boolean {
        if (player == null || !esDeMedios(codigo)) return false
        val ahora = SystemClock.elapsedRealtime()
        val previa = ultimaAccion
        if (previa != null && previa.first == codigo && ahora - previa.second < REPETIDA_MS) return false
        ultimaAccion = codigo to ahora
        when (codigo) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK ->
                if (player.isPlaying) player.pause() else player.play()
            KeyEvent.KEYCODE_MEDIA_PLAY -> player.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> player.pause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> player.seekToNext()
            // Como en todo Android: pasados 3 s vuelve al inicio; antes, a la anterior.
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> player.seekToPrevious()
        }
        return true
    }

    /** El nombre de un boton para la persona, o el codigo de Android si no es conocido. */
    fun nombre(context: Context, codigo: Int): String = when (codigo) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> context.getString(R.string.carro_tecla_siguiente)
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> context.getString(R.string.carro_tecla_anterior)
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> context.getString(R.string.carro_tecla_play_pausa)
        KeyEvent.KEYCODE_MEDIA_PLAY -> context.getString(R.string.carro_tecla_play)
        KeyEvent.KEYCODE_MEDIA_PAUSE -> context.getString(R.string.carro_tecla_pausa)
        KeyEvent.KEYCODE_MEDIA_STOP -> context.getString(R.string.carro_tecla_detener)
        KeyEvent.KEYCODE_HEADSETHOOK -> context.getString(R.string.carro_tecla_auricular)
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> context.getString(R.string.carro_tecla_adelantar)
        KeyEvent.KEYCODE_MEDIA_REWIND -> context.getString(R.string.carro_tecla_retroceder)
        KeyEvent.KEYCODE_VOLUME_UP -> context.getString(R.string.carro_tecla_volumen_mas)
        KeyEvent.KEYCODE_VOLUME_DOWN -> context.getString(R.string.carro_tecla_volumen_menos)
        KeyEvent.KEYCODE_VOLUME_MUTE -> context.getString(R.string.carro_tecla_silencio)
        else -> KeyEvent.keyCodeToString(codigo).removePrefix("KEYCODE_")
    }

    fun icono(codigo: Int): Int = when (codigo) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> R.drawable.skip_next
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> R.drawable.skip_previous
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_HEADSETHOOK -> R.drawable.play
        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> R.drawable.pause
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> R.drawable.fast_forward
        KeyEvent.KEYCODE_VOLUME_UP -> R.drawable.volume_up
        KeyEvent.KEYCODE_VOLUME_DOWN -> R.drawable.volume_down
        KeyEvent.KEYCODE_VOLUME_MUTE -> R.drawable.volume_off
        else -> R.drawable.tune
    }

    private fun describir(context: Context, p: Pulsacion): String {
        val base = context.getString(R.string.carro_bitacora_tecla, nombre(context, p.codigo), p.codigo, context.getString(p.via.texto))
        return if (p.detalle != null) "$base · ${p.detalle}" else base
    }

    /**
     * Empieza a escuchar la sesion de medios y los avisos de fabricante. Se llama
     * una vez por proceso (InicioDelCarro), asi funciona con la app detras.
     */
    fun escuchar(context: Context) {
        val app = context.applicationContext
        MediaLibrarySessionCallback.alBotonDeMedios = { evento ->
            if (evento.action == KeyEvent.ACTION_DOWN && evento.repeatCount == 0) {
                recibir(app, evento.keyCode, ViaDeTecla.SESION)
            }
        }
        FabricanteDeTeclas.registrar(app)
    }
}

/**
 * Los avisos con que algunos radios mandan los botones del volante en vez de
 * teclas normales. Desde Android 8 estos avisos no llegan a un receptor del
 * manifiesto, asi que se registran en tiempo de ejecucion. La lista crece con lo
 * que se vea en los radios de verdad (la del de CG, en la tanda 8).
 */
private object FabricanteDeTeclas : BroadcastReceiver() {
    /** Accion del aviso y el extra que trae el codigo de la tecla. */
    private val AVISOS = mapOf(
        // Microntek (MTCD/MTCE): los PX5/PX30 de hace unos anos.
        "com.microntek.irkeyDown" to "keyCode",
    )

    private var registrado = false

    fun registrar(context: Context) {
        if (registrado) return
        val filtro = IntentFilter().apply { AVISOS.keys.forEach(::addAction) }
        ContextCompat.registerReceiver(context, this, filtro, ContextCompat.RECEIVER_EXPORTED)
        registrado = true
    }

    override fun onReceive(context: Context, intent: Intent) {
        val accion = intent.action ?: return
        val extra = AVISOS[accion] ?: return
        val codigo = intent.getIntExtra(extra, -1)
        TeclasDelVolante.recibir(context, codigo, ViaDeTecla.FABRICANTE, "$accion $extra=$codigo")
    }
}

/**
 * El probador de botones: se pulsa un boton del volante y aparece en grande cual
 * llego y por donde. Sirve para saber que manda el radio sin cables ni adb.
 */
@Composable
fun ProbadorDeBotones(alCerrar: () -> Unit, modifier: Modifier = Modifier) {
    val abiertoEn = remember { System.currentTimeMillis() }
    DisposableEffect(Unit) {
        TeclasDelVolante.probando = true
        onDispose { TeclasDelVolante.probando = false }
    }
    BackHandler(onBack = alCerrar)
    val todas by TeclasDelVolante.ultimas.collectAsState()
    val pulsaciones = todas.filter { it.cuando >= abiertoEn }
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val hora = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }

    Column(modifier.fillMaxSize()) {
        TituloDePantalla(stringResource(R.string.carro_probador_titulo)) {
            BotonPildora(R.drawable.check, stringResource(R.string.carro_probador_listo), alCerrar, lleno = true)
        }
        Text(
            stringResource(R.string.carro_probador_texto),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // La ultima, en grande.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).fillMaxSize().cristal(RoundedCornerShape(32.dp)).padding(24.dp),
            ) {
                AnimatedContent(
                    targetState = pulsaciones.firstOrNull(),
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.85f)).togetherWith(fadeOut()) },
                    label = "ultimaTecla",
                ) { ultima ->
                    if (ultima == null) {
                        Text(
                            stringResource(R.string.carro_probador_esperando),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painterResource(TeclasDelVolante.icono(ultima.codigo)),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                TeclasDelVolante.nombre(contexto, ultima.codigo),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.carro_probador_detalle, ultima.codigo, stringResource(ultima.via.texto)),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // Las anteriores, con la hora: dos lineas seguidas del mismo boton por
            // vias distintas dicen que el radio lo manda por las dos.
            LazyColumn(
                modifier = Modifier.width(320.dp).fillMaxSize().cristal(RoundedCornerShape(32.dp)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(pulsaciones) { p ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Icon(painterResource(TeclasDelVolante.icono(p.codigo)), contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(TeclasDelVolante.nombre(contexto, p.codigo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                hora.format(Date(p.cuando)) + " · " + stringResource(p.via.texto),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
