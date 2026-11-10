package com.cglabs.lifemusic.ui.screens.mixed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.navigation.NavController
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.audio.MapaDeCancion
import com.cglabs.lifemusic.playback.audio.Motivo
import com.cglabs.lifemusic.playback.audio.PuntoDeMezcla
import com.cglabs.lifemusic.playback.audio.TipoPunto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Cuánto de cada canción muestra la mesa: el final de la que sale y el comienzo de la que entra. */
private const val VENTANA_MS = 60_000L

/**
 * 2.1.x · La mesa del Mixed Editor, primera versión (lámina 2 de la Mesa de mezcla). Arriba el final de la canción que
 * suena y abajo el comienzo de la siguiente, cada una con su onda sobre la rejilla de compases y los PUNTOS SUGERIDOS,
 * como los destacados de Instagram (pedido de CG, 09-11): dónde sale, dónde entra y lo más fuerte. Al tocar un punto,
 * Nova explica por qué lo propone y «Usar» lo aplica en la próxima mezcla. Arrastrar, «Escuchar · 10 s», el
 * ecualizador y guardar por lista vienen en la siguiente tanda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MesaScreen(navController: NavController) {
    val conexion = LocalPlayerConnection.current ?: return
    val servicio = conexion.service
    val contexto = LocalContext.current
    val metadatos by conexion.mediaMetadata.collectAsState()
    val mapaNuevo by servicio.mapaNuevo.collectAsState()
    val plan by conexion.automixDebugInfo.collectAsState()
    val p = conexion.player
    val actual = p.currentMediaItem
    val siguiente = p.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { p.getMediaItemAt(it) }
    val idA = actual?.mediaId
    val idB = siguiente?.mediaId
    var mapaA by remember(idA) { mutableStateOf<MapaDeCancion?>(null) }
    var mapaB by remember(idB) { mutableStateOf<MapaDeCancion?>(null) }
    var elegido by remember { mutableStateOf<Pair<Boolean, PuntoDeMezcla>?>(null) }
    LaunchedEffect(idA, idB, mapaNuevo) {
        val a = withContext(Dispatchers.IO) { idA?.let { servicio.mapaDe(it) } }
        val b = withContext(Dispatchers.IO) { idB?.let { servicio.mapaDe(it) } }
        mapaA = a
        mapaB = b
        // Si falta alguna, se escucha ahora: cuando llegue, la mesa la dibuja sola.
        if (a == null && idA != null) servicio.escucharParaLaMesa(idA)
        if (b == null && idB != null) servicio.escucharParaLaMesa(idB)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.mixed_mesa))
                        Text(
                            "${actual?.mediaMetadata?.title ?: "—"} › ${siguiente?.mediaMetadata?.title ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            // BPM y compatibilidad, como en la lámina.
            val bpmA = mapaA?.bpm ?: plan?.outBpm
            val bpmB = mapaB?.bpm ?: plan?.inBpm
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bpmA != null && bpmB != null) {
                    AssistChip(onClick = {}, label = { Text("${bpmA.roundToInt()} › ${bpmB.roundToInt()} BPM") })
                    val cerca = abs(bpmA / bpmB - 1f) <= 0.08f
                    AssistChip(onClick = {}, label = { Text(stringResource(if (cerca) R.string.mixed_compatibles else R.string.mixed_lejanas)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            val colorSale = MaterialTheme.colorScheme.primary
            val colorEntra = MaterialTheme.colorScheme.tertiary
            Pista(
                titulo = stringResource(R.string.mixed_sale) + " · " + (metadatos?.title ?: ""),
                mapa = mapaA,
                desdeMs = ((mapaA?.duracionMs ?: 0L) - VENTANA_MS).coerceAtLeast(0L),
                color = colorSale,
                marca = plan?.triggerTimeMs,
                puntos = mapaA?.puntos?.filter { it.tipo != TipoPunto.ENTRADA }.orEmpty(),
                elegido = elegido?.takeIf { it.first }?.second,
                onPunto = { elegido = true to it },
            )
            Spacer(Modifier.height(12.dp))
            Pista(
                titulo = stringResource(R.string.mixed_entra) + " · " + (siguiente?.mediaMetadata?.title ?: ""),
                mapa = mapaB,
                desdeMs = 0L,
                color = colorEntra,
                marca = plan?.incomingStartMs,
                puntos = mapaB?.puntos?.filter { it.tipo != TipoPunto.SALIDA }.orEmpty(),
                elegido = elegido?.takeIf { !it.first }?.second,
                onPunto = { elegido = false to it },
            )
            Spacer(Modifier.height(16.dp))
            // Nova, en una tarjeta: por qué propone ese punto.
            val e = elegido
            if (e != null) {
                val (esSalida, punto) = e
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.mixed_nova_sugiere), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                when (punto.tipo) {
                                    TipoPunto.SALIDA -> R.string.mixed_punto_salida
                                    TipoPunto.ENTRADA -> R.string.mixed_punto_entrada
                                    TipoPunto.DESTACADO -> R.string.mixed_punto_destacado
                                },
                                punto.compas + 1,
                                stringResource(textoDeMotivo(punto.motivo)),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(Modifier.padding(top = 4.dp)) {
                            TextButton(onClick = {
                                val id = if (esSalida) idA else idB
                                if (id != null) {
                                    if (esSalida) servicio.elegirSalida(id, punto.ms) else servicio.elegirEntrada(id, punto.ms)
                                    android.widget.Toast.makeText(contexto, contexto.getString(R.string.mixed_punto_usado), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(stringResource(R.string.mixed_usar)) }
                            TextButton(onClick = {
                                val lista = (if (esSalida) mapaA else mapaB)?.puntos
                                    ?.filter { if (esSalida) it.tipo != TipoPunto.ENTRADA else it.tipo != TipoPunto.SALIDA }.orEmpty()
                                if (lista.isNotEmpty()) {
                                    val i = lista.indexOf(punto)
                                    elegido = esSalida to lista[(i + 1) % lista.size]
                                }
                            }) { Text(stringResource(R.string.mixed_otra_idea)) }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.mixed_mesa_ayuda),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Una canción en la mesa: su onda sobre la rejilla de compases, con los puntos sugeridos encima. */
@Composable
private fun Pista(
    titulo: String,
    mapa: MapaDeCancion?,
    desdeMs: Long,
    color: Color,
    marca: Long?,
    puntos: List<PuntoDeMezcla>,
    elegido: PuntoDeMezcla?,
    onPunto: (PuntoDeMezcla) -> Unit,
) {
    val colorRejilla = MaterialTheme.colorScheme.outlineVariant
    val colorDestacado = MaterialTheme.colorScheme.secondary
    val fondo = MaterialTheme.colorScheme.surfaceContainerHigh
    Text(titulo, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(6.dp))
    Card(colors = CardDefaults.cardColors(containerColor = fondo), shape = RoundedCornerShape(16.dp)) {
        if (mapa == null) {
            Row(Modifier.fillMaxWidth().height(120.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.mixed_mesa_escuchando), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Card
        }
        val hastaMs = (desdeMs + VENTANA_MS).coerceAtMost(mapa.duracionMs)
        val visibles = puntos.filter { it.ms in desdeMs..hastaMs }
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 12.dp, vertical = 10.dp)
                .pointerInput(visibles, desdeMs, hastaMs) {
                    detectTapGestures { toque ->
                        val ms = desdeMs + (toque.x / size.width * (hastaMs - desdeMs)).toLong()
                        val cerca = visibles.minByOrNull { abs(it.ms - ms) }
                        val margen = (hastaMs - desdeMs) * 0.06f
                        if (cerca != null && abs(cerca.ms - ms) <= margen) onPunto(cerca)
                    }
                },
        ) {
            fun x(ms: Long) = (ms - desdeMs).toFloat() / (hastaMs - desdeMs).coerceAtLeast(1L) * size.width
            val arriba = 22.dp.toPx()
            val centro = arriba + (size.height - arriba) / 2f
            val alto = (size.height - arriba) * 0.9f
            // Rejilla de compases: más marcada cada 4.
            mapa.compases.forEachIndexed { i, c ->
                if (c.inicioMs in desdeMs..hastaMs) {
                    val xc = x(c.inicioMs)
                    drawLine(colorRejilla.copy(alpha = if (i % 4 == 0) 0.9f else 0.35f), Offset(xc, arriba), Offset(xc, size.height), strokeWidth = 1.dp.toPx())
                }
            }
            // La onda.
            val paso = 3.dp.toPx()
            var xx = paso / 2f
            while (xx < size.width) {
                val ms = desdeMs + (xx / size.width * (hastaMs - desdeMs)).toLong()
                val i = (ms / MapaDeCancion.PASO_ONDA_MS).toInt()
                val a = if (i in mapa.onda.indices) mapa.onda[i] else 0f
                val h = (a * alto).coerceAtLeast(1.5.dp.toPx())
                drawLine(color.copy(alpha = 0.85f), Offset(xx, centro - h / 2f), Offset(xx, centro + h / 2f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                xx += paso
            }
            // Donde mezcla hoy el Automix.
            marca?.takeIf { it in desdeMs..hastaMs }?.let {
                drawLine(color, Offset(x(it), arriba), Offset(x(it), size.height), strokeWidth = 2.dp.toPx())
            }
            // Los puntos sugeridos, como los destacados de Instagram.
            visibles.forEach { pnt ->
                val xc = x(pnt.ms)
                val c = if (pnt.tipo == TipoPunto.DESTACADO) colorDestacado else color
                val r = if (pnt == elegido) 8.dp.toPx() else 6.dp.toPx()
                drawLine(c.copy(alpha = 0.6f), Offset(xc, arriba), Offset(xc, size.height), strokeWidth = 1.5.dp.toPx())
                drawCircle(c, radius = r, center = Offset(xc, arriba / 2f + 2.dp.toPx()))
                if (pnt == elegido) drawCircle(Color.White, radius = r * 0.4f, center = Offset(xc, arriba / 2f + 2.dp.toPx()))
            }
        }
    }
}

fun textoDeMotivo(m: Motivo) = when (m) {
    Motivo.ARRANCA_BATERIA -> R.string.mixed_motivo_arranca_bateria
    Motivo.ARRANCA_BAJO -> R.string.mixed_motivo_arranca_bajo
    Motivo.EMPIEZA_CORO -> R.string.mixed_motivo_empieza_coro
    Motivo.SE_VA_BATERIA -> R.string.mixed_motivo_se_va_bateria
    Motivo.EMPIEZA_OUTRO -> R.string.mixed_motivo_empieza_outro
    Motivo.BAJA_ENERGIA -> R.string.mixed_motivo_baja_energia
    Motivo.EL_CORO -> R.string.mixed_motivo_el_coro
}
