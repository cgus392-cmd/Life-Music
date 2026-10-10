package com.cglabs.lifemusic.car

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.eq.audio.AjusteMesa
import com.cglabs.lifemusic.eq.audio.MedidaDeSalida
import com.cglabs.lifemusic.eq.audio.MesaDeSonido
import com.cglabs.lifemusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Abre la mesa de sonido a pantalla completa (lo da PantallaCarro). */
val LocalAbrirEcualizador = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Presets pensados para parlantes de carro: graves y agudos (estantes) y las 10
 * bandas, en dB. Los de puerta no dan 31 Hz: los refuerzos de graves cargan
 * 62–125 Hz, que es donde un carro de verdad suena. La ganancia no es del preset.
 */
enum class PresetDeSonido(val titulo: Int, val graves: Float, val agudos: Float, val bandas: List<Float>) {
    PLANO(R.string.carro_eq_plano, 0f, 0f, List(10) { 0f }),
    GRAVES(R.string.carro_eq_preset_graves, 5f, 0f, listOf(1f, 3f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    URBANO(R.string.carro_eq_urbano, 4f, 2f, listOf(1f, 3f, 2f, -1f, -1f, 0f, 1f, 1f, 2f, 2f)),
    TROPICAL(R.string.carro_eq_tropical, 2f, 2f, listOf(1f, 1f, 0f, 0f, 1f, 2f, 2f, 2f, 1f, 1f)),
    ROCK(R.string.carro_eq_rock, 3f, 2f, listOf(1f, 1f, 0f, -1f, -1f, 0f, 2f, 3f, 3f, 2f)),
    POP(R.string.carro_eq_pop, 2f, 1f, listOf(1f, 1f, 0f, 1f, 2f, 2f, 1f, 1f, 1f, 1f)),
    VOCES(R.string.carro_eq_voces, -2f, 0f, listOf(-2f, -1f, 0f, 2f, 3f, 3f, 2f, 1f, 0f, -1f)),
    NOCHE(R.string.carro_eq_noche, 2f, -2f, listOf(1f, 1f, 0f, 0f, 0f, -1f, -2f, -2f, -1f, -1f)),
    VOLUMEN_BAJO(R.string.carro_eq_volumen_bajo, 5f, 4f, listOf(1f, 2f, 1f, 0f, 0f, 0f, 0f, 1f, 2f, 2f)),
    ;

    fun sobre(a: AjusteMesa) = a.copy(graves = graves, agudos = agudos, bandas = bandas)
}

private val CarroEqActivoKey = booleanPreferencesKey("carroEqActivo")
private val CarroEqGananciaKey = floatPreferencesKey("carroEqGanancia")
private val CarroEqGravesKey = floatPreferencesKey("carroEqGraves")
private val CarroEqAgudosKey = floatPreferencesKey("carroEqAgudos")
private val CarroEqBandasKey = stringPreferencesKey("carroEqBandas")
private val CarroEqPresetKey = stringPreferencesKey("carroEqPreset")

/**
 * Guardar y cargar la mesa. Se carga al arrancar el proceso (InicioDelCarro),
 * asi suena con tu ecualizacion aunque no abras la pantalla.
 */
object SonidoDelCarro {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** El preset elegido, o null si se toco a mano («Personalizado»). */
    @Volatile
    var preset: PresetDeSonido? = PresetDeSonido.PLANO
        private set

    fun cargarAlArrancar(context: Context) {
        val app = context.applicationContext
        alcance.launch {
            val p = runCatching { app.dataStore.data.first() }.getOrNull() ?: return@launch
            val bandas = p[CarroEqBandasKey]?.split(';')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == MesaDeSonido.BANDAS }
            MesaDeSonido.aplicar(
                AjusteMesa(
                    activo = p[CarroEqActivoKey] ?: false,
                    ganancia = p[CarroEqGananciaKey] ?: 0f,
                    graves = p[CarroEqGravesKey] ?: 0f,
                    agudos = p[CarroEqAgudosKey] ?: 0f,
                    bandas = bandas ?: List(MesaDeSonido.BANDAS) { 0f },
                ),
            )
            preset = p[CarroEqPresetKey]?.let { nombre -> PresetDeSonido.entries.firstOrNull { it.name == nombre } }
                ?: if (p[CarroEqPresetKey] == null) PresetDeSonido.PLANO else null
        }
    }

    fun guardar(context: Context, a: AjusteMesa, elegido: PresetDeSonido?) {
        preset = elegido
        val app = context.applicationContext
        alcance.launch {
            runCatching {
                app.dataStore.edit {
                    it[CarroEqActivoKey] = a.activo
                    it[CarroEqGananciaKey] = a.ganancia
                    it[CarroEqGravesKey] = a.graves
                    it[CarroEqAgudosKey] = a.agudos
                    it[CarroEqBandasKey] = a.bandas.joinToString(";")
                    it[CarroEqPresetKey] = elegido?.name ?: ""
                }
            }
        }
    }
}

private val VERDE_VU = Color(0xFF7BE0A8)
private val AMBAR_VU = Color(0xFFF4C56A)
private val ROJO_VU = Color(0xFFFF8A7A)
private const val PISO_VU_DB = -42f
private const val PUNTOS_CURVA = 160
private val ETIQUETAS_HZ = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

/** 0,5 dB de paso, que es lo que distingue el oido y lo que se puede acertar con el dedo. */
private fun redondear(db: Float) = ((db * 2f).roundToInt() / 2f).coerceIn(MesaDeSonido.MINIMO_DB, MesaDeSonido.MAXIMO_DB)

private fun textoDb(db: Float): String =
    if (abs(db) < 0.05f) "0 dB" else String.format(Locale.getDefault(), "%+.1f dB", db)

/**
 * La mesa de sonido del carro, a pantalla completa (ficha
 * docs/versiones/carro-0.3.0/ecualizador.md): arriba los presets y el
 * interruptor; al centro la curva, el analizador y las 10 bandas; abajo el
 * rack con graves, agudos, ganancia y el vumetro de salida.
 *
 * Tocar cualquier control prende la mesa: mover algo y no oirlo confunde.
 */
@Composable
fun PantallaEcualizador(alCerrar: () -> Unit) {
    BackHandler(onBack = alCerrar)
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val ligero = LocalPerfil.current == Perfil.LIGERO
    var ajuste by remember { mutableStateOf(MesaDeSonido.ajuste) }
    var preset by remember { mutableStateOf(SonidoDelCarro.preset) }
    var guardado by remember { mutableStateOf<Job?>(null) }

    fun cambiar(nuevo: AjusteMesa, nuevoPreset: PresetDeSonido?) {
        ajuste = nuevo
        preset = nuevoPreset
        MesaDeSonido.aplicar(nuevo)
        // Se guarda cuando el dedo para un momento, no en cada milimetro.
        guardado?.cancel()
        guardado = alcance.launch {
            delay(400)
            SonidoDelCarro.guardar(contexto, nuevo, nuevoPreset)
        }
    }

    val lectura = remember { LecturaDeSalida() }
    DisposableEffect(Unit) {
        MesaDeSonido.analizar = true
        onDispose {
            MesaDeSonido.analizar = false
            SonidoDelCarro.guardar(contexto, MesaDeSonido.ajuste, preset)
        }
    }
    LaunchedEffect(ligero) {
        var antes = System.nanoTime()
        while (true) {
            delay(if (ligero) 66L else 33L)
            val ahora = System.nanoTime()
            lectura.avanzar(MesaDeSonido.medidaAhora(ahora), (ahora - antes) / 1e9f)
            antes = ahora
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // Cabecera: cerrar, titulo, presets e interruptor.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(60.dp)) {
            BotonCristal(R.drawable.close, stringResource(R.string.carro_cerrar), alCerrar, lado = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.carro_eq_titulo), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(preset?.titulo ?: R.string.carro_eq_personalizado),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(20.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(PresetDeSonido.entries) { p ->
                    Pildora(stringResource(p.titulo), p == preset) {
                        cambiar(p.sobre(ajuste).copy(activo = true), p)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            BotonCristal(R.drawable.refresh, stringResource(R.string.carro_eq_restablecer), {
                cambiar(PresetDeSonido.PLANO.sobre(ajuste).copy(ganancia = 0f), PresetDeSonido.PLANO)
            }, lado = 56.dp)
            Spacer(Modifier.width(12.dp))
            BotonPildora(
                R.drawable.graphic_eq,
                stringResource(if (ajuste.activo) R.string.carro_eq_encendido else R.string.carro_eq_apagado),
                { cambiar(ajuste.copy(activo = !ajuste.activo), preset) },
                lleno = ajuste.activo,
            )
        }
        Spacer(Modifier.height(12.dp))

        val apagada by animateFloatAsState(if (ajuste.activo) 1f else 0.55f, tween(250), label = "mesaApagada")

        // Las 10 bandas, con la curva y el analizador detras.
        TarjetaDeBandas(
            ajuste = ajuste,
            lectura = lectura,
            brillo = !ligero,
            alCambiar = { i, db ->
                val bandas = ajuste.bandas.toMutableList().also { it[i] = db }
                cambiar(ajuste.copy(activo = true, bandas = bandas), null)
            },
            modifier = Modifier.weight(1f).fillMaxWidth().alpha(apagada),
        )
        Spacer(Modifier.height(12.dp))

        // El rack: tres perillas y el vumetro de salida.
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(420.dp).fillMaxHeight().cristal(RoundedCornerShape(28.dp)).alpha(apagada),
            ) {
                Perilla(stringResource(R.string.carro_eq_graves), ajuste.graves) { cambiar(ajuste.copy(activo = true, graves = it), null) }
                Perilla(stringResource(R.string.carro_eq_agudos), ajuste.agudos) { cambiar(ajuste.copy(activo = true, agudos = it), null) }
                // La ganancia no deshace el preset: es el volumen de la mesa, no su color.
                Perilla(stringResource(R.string.carro_eq_ganancia), ajuste.ganancia) { cambiar(ajuste.copy(activo = true, ganancia = it), preset) }
            }
            Vumetro(lectura, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Una pastilla de preset. */
@Composable
private fun Pildora(texto: String, elegida: Boolean, alTocar: () -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(48.dp)
            .cristal(forma, if (elegida) MaterialTheme.colorScheme.primary else Color.White, fuerza = if (elegida) 2.4f else 1f)
            .clickable(onClick = alTocar)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (elegida) FontWeight.Bold else FontWeight.Medium,
            color = if (elegida) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Lo que se ve del medidor, ya con balistica: el VU sube en 50 ms y baja en
 * 300; el pico se sostiene 1,5 s y luego cae 20 dB/s; el analizador sube
 * rapido y cae en 350 ms. Se escribe desde un bucle y se lee al dibujar, asi
 * que mover el vumetro no recompone la pantalla.
 */
private class LecturaDeSalida {
    var vuIzq by mutableFloatStateOf(PISO_VU_DB)
    var vuDer by mutableFloatStateOf(PISO_VU_DB)
    var picoIzq by mutableFloatStateOf(PISO_VU_DB)
    var picoDer by mutableFloatStateOf(PISO_VU_DB)
    var limitador by mutableFloatStateOf(0f)
    var ultimoPico by mutableFloatStateOf(PISO_VU_DB)
    val bandas = Array(MesaDeSonido.BANDAS) { mutableFloatStateOf(-90f) }
    private var sostenIzq = 0f
    private var sostenDer = 0f

    fun avanzar(m: MedidaDeSalida?, dt: Float) {
        val rmsI = m?.let { MesaDeSonido.aDb(it.rmsIzq) } ?: -90f
        val rmsD = m?.let { MesaDeSonido.aDb(it.rmsDer) } ?: -90f
        vuIzq = balistica(vuIzq, rmsI.coerceAtLeast(PISO_VU_DB), dt, 0.05f, 0.3f)
        vuDer = balistica(vuDer, rmsD.coerceAtLeast(PISO_VU_DB), dt, 0.05f, 0.3f)

        val pI = m?.let { MesaDeSonido.aDb(it.picoIzq) } ?: -90f
        val pD = m?.let { MesaDeSonido.aDb(it.picoDer) } ?: -90f
        if (pI >= picoIzq) { picoIzq = pI.coerceAtLeast(PISO_VU_DB); sostenIzq = 1.5f } else if (sostenIzq > 0f) sostenIzq -= dt else picoIzq = (picoIzq - 20f * dt).coerceAtLeast(PISO_VU_DB)
        if (pD >= picoDer) { picoDer = pD.coerceAtLeast(PISO_VU_DB); sostenDer = 1.5f } else if (sostenDer > 0f) sostenDer -= dt else picoDer = (picoDer - 20f * dt).coerceAtLeast(PISO_VU_DB)
        ultimoPico = maxOf(picoIzq, picoDer)

        val lim = m?.limitadorDb ?: 0f
        limitador = if (lim < limitador) lim else balistica(limitador, lim, dt, 0.01f, 0.4f)

        for (b in 0 until MesaDeSonido.BANDAS) {
            val objetivo = m?.bandasDb?.get(b) ?: -90f
            bandas[b].floatValue = balistica(bandas[b].floatValue, objetivo, dt, 0.04f, 0.35f)
        }
    }

    private fun balistica(actual: Float, objetivo: Float, dt: Float, subir: Float, bajar: Float): Float {
        val tau = if (objetivo > actual) subir else bajar
        return actual + (objetivo - actual) * (1f - exp(-dt / tau))
    }
}

/**
 * La tarjeta de las bandas: escala en dB a la izquierda, y en el resto la curva
 * de lo que de verdad le pasa al sonido (ganancia, estantes y bandas, la misma
 * matematica que el procesador), el analizador detras de cada banda y los 10
 * deslizadores. Las bandas son de octava, asi que en escala logaritmica caen
 * cada una en el centro de su columna y la curva pasa por encima de su mando.
 */
@Composable
private fun TarjetaDeBandas(
    ajuste: AjusteMesa,
    lectura: LecturaDeSalida,
    brillo: Boolean,
    alCambiar: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val medidor = rememberTextMeasurer()
    val primario = MaterialTheme.colorScheme.primary
    val tenue = MaterialTheme.colorScheme.onSurfaceVariant
    val estiloEscala = TextStyle(fontSize = 12.sp, color = tenue)
    // La curva solo cambia al mover algo: se calcula entonces, no en cada cuadro
    // del analizador (en un radio flojo serian miles de senos por segundo).
    val curvaDb = remember(ajuste) {
        val filtros = MesaDeSonido.filtros(ajuste, 48_000.0)
        FloatArray(PUNTOS_CURVA + 1) { k ->
            val octava = k.toFloat() / PUNTOS_CURVA * MesaDeSonido.BANDAS - 0.5f
            MesaDeSonido.respuestaDb(ajuste, 31.25 * 2.0.pow(octava.toDouble()), filtros, 48_000.0).toFloat()
        }
    }

    Column(modifier.cristal(RoundedCornerShape(28.dp)).padding(start = 8.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // Escala en dB.
            Canvas(Modifier.width(40.dp).fillMaxHeight()) {
                val alto = size.height
                for (db in listOf(12, 6, 0, -6, -12)) {
                    val y = yDeDb(db.toFloat(), alto)
                    val texto = (if (db > 0) "+" else "") + db
                    val medida = medidor.measure(texto, estiloEscala)
                    drawText(medida, topLeft = Offset(size.width - medida.size.width - 6f, y - medida.size.height / 2f))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Rejilla, analizador y curva, en un solo lienzo.
                Canvas(Modifier.fillMaxSize()) {
                    val ancho = size.width
                    val alto = size.height
                    val columna = ancho / MesaDeSonido.BANDAS
                    for (db in listOf(12, 6, 0, -6, -12)) {
                        val y = yDeDb(db.toFloat(), alto)
                        drawLine(Color.White.copy(alpha = if (db == 0) 0.22f else 0.07f), Offset(0f, y), Offset(ancho, y), strokeWidth = if (db == 0) 2f else 1f)
                    }
                    // El analizador: lo que suena en cada banda, de -60 a 0 dBFS.
                    for (b in 0 until MesaDeSonido.BANDAS) {
                        val nivel = ((lectura.bandas[b].floatValue + 60f) / 60f).coerceIn(0f, 1f)
                        if (nivel <= 0f) continue
                        val alturaBarra = alto * nivel
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(primario.copy(alpha = 0.32f), primario.copy(alpha = 0.06f)),
                                startY = alto - alturaBarra,
                                endY = alto,
                            ),
                            topLeft = Offset(b * columna + columna * 0.18f, alto - alturaBarra),
                            size = Size(columna * 0.64f, alturaBarra),
                            cornerRadius = CornerRadius(10f, 10f),
                        )
                    }
                    // La curva.
                    val curva = Path()
                    for (k in 0..PUNTOS_CURVA) {
                        val x = ancho * k / PUNTOS_CURVA
                        val y = yDeDb(curvaDb[k].coerceIn(-15f, 15f), alto).coerceIn(0f, alto)
                        if (k == 0) curva.moveTo(x, y) else curva.lineTo(x, y)
                    }
                    val relleno = Path().apply {
                        addPath(curva)
                        lineTo(ancho, yDeDb(0f, alto))
                        lineTo(0f, yDeDb(0f, alto))
                        close()
                    }
                    drawPath(relleno, Brush.verticalGradient(listOf(primario.copy(alpha = 0.22f), primario.copy(alpha = 0.02f))))
                    if (brillo) drawPath(curva, primario.copy(alpha = 0.25f), style = Stroke(width = 12f, cap = StrokeCap.Round))
                    drawPath(curva, primario, style = Stroke(width = 4f, cap = StrokeCap.Round))
                }
                Row(Modifier.fillMaxSize()) {
                    for (i in 0 until MesaDeSonido.BANDAS) {
                        DeslizadorDeBanda(
                            valor = ajuste.bandas.getOrElse(i) { 0f },
                            alCambiar = { alCambiar(i, it) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
        // Las frecuencias, bajo cada columna.
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp)) {
            ETIQUETAS_HZ.forEach { f ->
                Text(
                    f,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** Donde cae un valor en dB (de +12 arriba a -12 abajo, con un margen para el mando). */
private fun yDeDb(db: Float, alto: Float): Float {
    val margen = 18f
    val util = alto - margen * 2
    return margen + (MesaDeSonido.MAXIMO_DB - db) / (MesaDeSonido.MAXIMO_DB - MesaDeSonido.MINIMO_DB) * util
}

private fun dbDeY(y: Float, alto: Float): Float {
    val margen = 18f
    val util = alto - margen * 2
    return MesaDeSonido.MAXIMO_DB - (y - margen) / util * (MesaDeSonido.MAXIMO_DB - MesaDeSonido.MINIMO_DB)
}

/**
 * Un deslizador vertical: toda la columna es el mando (se toca o se arrastra
 * donde sea), el valor sale encima del boton y el doble toque lo deja en 0.
 */
@Composable
private fun DeslizadorDeBanda(valor: Float, alCambiar: (Float) -> Unit, modifier: Modifier = Modifier) {
    val primario = MaterialTheme.colorScheme.primary
    val texto = MaterialTheme.colorScheme.onSurface
    val medidor = rememberTextMeasurer()
    var arrastrando by remember { mutableStateOf(false) }
    // Los gestos viven mas que una composicion: que llamen siempre a la ultima.
    val cambiar by rememberUpdatedState(alCambiar)
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { cambiar(0f) },
                    onTap = { p -> cambiar(redondear(dbDeY(p.y, size.height.toFloat()))) },
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { p ->
                        arrastrando = true
                        cambiar(redondear(dbDeY(p.y, size.height.toFloat())))
                    },
                    onDragEnd = { arrastrando = false },
                    onDragCancel = { arrastrando = false },
                    onVerticalDrag = { cambio, _ ->
                        cambio.consume()
                        cambiar(redondear(dbDeY(cambio.position.y, size.height.toFloat())))
                    },
                )
            },
    ) {
        val x = size.width / 2f
        val cero = yDeDb(0f, size.height)
        val y = yDeDb(valor, size.height)
        // Riel y el tramo desde 0 hasta el valor.
        drawLine(Color.White.copy(alpha = 0.16f), Offset(x, yDeDb(12f, size.height)), Offset(x, yDeDb(-12f, size.height)), strokeWidth = 6f, cap = StrokeCap.Round)
        drawLine(primario, Offset(x, cero), Offset(x, y), strokeWidth = 6f, cap = StrokeCap.Round)
        // El mando.
        val anchoMando = minOf(size.width * 0.62f, 96f)
        val altoMando = 30f
        drawRoundRect(
            color = if (arrastrando) Color.White else primario,
            topLeft = Offset(x - anchoMando / 2f, y - altoMando / 2f),
            size = Size(anchoMando, altoMando),
            cornerRadius = CornerRadius(altoMando / 2f, altoMando / 2f),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(x - anchoMando * 0.22f, y - 2f),
            size = Size(anchoMando * 0.44f, 4f),
            cornerRadius = CornerRadius(2f, 2f),
        )
        // El valor, encima del mando mientras se arrastra (o si no es 0).
        if (arrastrando || abs(valor) >= 0.05f) {
            val etiqueta = medidor.measure(
                String.format(Locale.getDefault(), "%+.1f", valor),
                TextStyle(fontSize = if (arrastrando) 15.sp else 12.sp, fontWeight = FontWeight.Bold, color = texto.copy(alpha = if (arrastrando) 1f else 0.75f)),
            )
            val arriba = (y - altoMando / 2f - etiqueta.size.height - 4f).let { if (it < 0f) y + altoMando / 2f + 4f else it }
            drawText(etiqueta, topLeft = Offset(x - etiqueta.size.width / 2f, arriba))
        }
    }
}

/**
 * Una perilla como las de un radio: arco de 270 grados, el valor en el centro y
 * el nombre debajo. Se gira arrastrando (arriba o a la derecha sube) y el doble
 * toque la deja en 0.
 */
@Composable
private fun Perilla(titulo: String, valor: Float, alCambiar: (Float) -> Unit) {
    val primario = MaterialTheme.colorScheme.primary
    var crudo by remember { mutableFloatStateOf(valor) }
    val valorActual by rememberUpdatedState(valor)
    val cambiar by rememberUpdatedState(alCambiar)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { crudo = 0f; cambiar(0f) })
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { crudo = valorActual }) { cambio, arrastre ->
                        cambio.consume()
                        // 7 dp por dB: de punta a punta en un gesto comodo.
                        crudo = (crudo + (arrastre.x - arrastre.y) / (7.dp.toPx())).coerceIn(MesaDeSonido.MINIMO_DB, MesaDeSonido.MAXIMO_DB)
                        cambiar(redondear(crudo))
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val grosor = 9.dp.toPx()
                val radio = size.minDimension / 2f - grosor
                val centro = Offset(size.width / 2f, size.height / 2f)
                val esquina = Offset(centro.x - radio, centro.y - radio)
                val caja = Size(radio * 2, radio * 2)
                drawArc(Color.White.copy(alpha = 0.12f), 135f, 270f, false, esquina, caja, style = Stroke(grosor, cap = StrokeCap.Round))
                // Desde arriba (0 dB) hacia donde este el valor.
                val barrido = valor / MesaDeSonido.MAXIMO_DB * 135f
                drawArc(primario, 270f, barrido, false, esquina, caja, style = Stroke(grosor, cap = StrokeCap.Round))
                // La marca del mando.
                val angulo = Math.toRadians((270f + barrido).toDouble())
                val punta = Offset(centro.x + cos(angulo).toFloat() * (radio - grosor * 1.6f), centro.y + sin(angulo).toFloat() * (radio - grosor * 1.6f))
                drawCircle(Color.White.copy(alpha = 0.06f), radio - grosor * 0.9f, centro)
                drawLine(Color.White, centro + (punta - centro) * 0.45f, punta, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
            Text(
                textoDb(valor),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 46.dp),
            )
        }
        Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * El vumetro de salida: dos filas de LED (izquierdo y derecho) de -42 a 0 dBFS,
 * verde, ambar desde -12 y rojo desde -4, con el pico sostenido; y a la
 * derecha la luz del limitador con lo que esta quitando.
 */
@Composable
private fun Vumetro(lectura: LecturaDeSalida, modifier: Modifier = Modifier) {
    val medidor = rememberTextMeasurer()
    val tenue = MaterialTheme.colorScheme.onSurfaceVariant
    val tinta = MaterialTheme.colorScheme.onSurface
    val salida = stringResource(R.string.carro_eq_salida)
    val limitador = stringResource(R.string.carro_eq_limitador)
    Canvas(modifier.cristal(RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 16.dp)) {
        val estiloChico = TextStyle(fontSize = 12.sp, color = tenue)
        val anchoLuz = 120.dp.toPx()
        val anchoBarras = size.width - anchoLuz - 34f
        val segmentos = 30
        val hueco = 3f
        val anchoSeg = (anchoBarras - hueco * (segmentos - 1)) / segmentos
        val altoFila = 18.dp.toPx()

        drawText(medidor.measure(salida.uppercase(), TextStyle(fontSize = 12.sp, color = tenue, letterSpacing = 1.5.sp)), topLeft = Offset(0f, 0f))
        val filas = listOf("I" to (lectura.vuIzq to lectura.picoIzq), "D" to (lectura.vuDer to lectura.picoDer))
        filas.forEachIndexed { f, (letra, valores) ->
            val (vu, pico) = valores
            val y = 26.dp.toPx() + f * (altoFila + 10.dp.toPx())
            drawText(medidor.measure(letra, estiloChico), topLeft = Offset(0f, y))
            for (s in 0 until segmentos) {
                val db = PISO_VU_DB + (s + 1) * (-PISO_VU_DB / segmentos)
                val color = when {
                    db > -4f -> ROJO_VU
                    db > -12f -> AMBAR_VU
                    else -> VERDE_VU
                }
                val encendido = db <= vu
                val esPico = !encendido && db >= pico && db - (-PISO_VU_DB / segmentos) < pico
                drawRoundRect(
                    color = if (encendido || esPico) color else color.copy(alpha = 0.12f),
                    topLeft = Offset(22f + s * (anchoSeg + hueco), y),
                    size = Size(anchoSeg, altoFila),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        // Escala bajo las barras.
        val yEscala = 26.dp.toPx() + 2 * (altoFila + 10.dp.toPx())
        for (db in listOf(-40, -30, -20, -12, -6, -3, 0)) {
            val x = 22f + (db - PISO_VU_DB) / -PISO_VU_DB * anchoBarras
            val m = medidor.measure(db.toString(), estiloChico)
            drawText(m, topLeft = Offset((x - m.size.width / 2f).coerceIn(0f, 22f + anchoBarras - m.size.width), yEscala))
        }

        // La luz del limitador y el pico.
        val xLuz = size.width - anchoLuz
        val trabajando = lectura.limitador < -1f
        val colorLuz = when {
            lectura.limitador < -4f -> ROJO_VU
            trabajando -> AMBAR_VU
            else -> Color.White.copy(alpha = 0.12f)
        }
        drawCircle(colorLuz, 9.dp.toPx(), Offset(xLuz + 9.dp.toPx(), 34.dp.toPx()))
        if (trabajando) drawCircle(colorLuz.copy(alpha = 0.25f), 16.dp.toPx(), Offset(xLuz + 9.dp.toPx(), 34.dp.toPx()))
        drawText(medidor.measure(limitador, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tinta)), topLeft = Offset(xLuz + 26.dp.toPx(), 24.dp.toPx()))
        drawText(
            medidor.measure(
                if (trabajando) String.format(Locale.getDefault(), "%.1f dB", lectura.limitador) else "—",
                TextStyle(fontSize = 13.sp, color = tenue),
            ),
            topLeft = Offset(xLuz + 26.dp.toPx(), 42.dp.toPx()),
        )
        val pico = if (lectura.ultimoPico <= PISO_VU_DB) "—" else String.format(Locale.getDefault(), "%.1f dB", lectura.ultimoPico)
        drawText(medidor.measure(pico, TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tinta)), topLeft = Offset(xLuz, 66.dp.toPx()))
        drawText(medidor.measure("PICO", TextStyle(fontSize = 11.sp, color = tenue, letterSpacing = 1.5.sp)), topLeft = Offset(xLuz, 92.dp.toPx()))
    }
}
