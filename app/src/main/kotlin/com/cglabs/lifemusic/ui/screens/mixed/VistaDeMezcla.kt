package com.cglabs.lifemusic.ui.screens.mixed

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.audio.MapaDeCancion
import com.cglabs.lifemusic.ui.player.tocadiscos.EstadoTocadiscos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 2.1.x · Pedido de CG (09-11): mientras se acerca y dura la mezcla, en el lugar de los controles (atrás, pausa,
 * adelante) se ven las dos ondas: arriba la que sale y abajo la que entra, sobre el mismo tiempo, con la zona de la
 * mezcla marcada y un cabezal que avanza. Un toque devuelve los controles unos segundos.
 */
@Composable
fun VistaDeMezcla(
    estado: EstadoTocadiscos,
    colorSale: Color,
    colorEntra: Color,
    colorTexto: Color,
    onTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val conexion = LocalPlayerConnection.current ?: return
    val servicio = conexion.service
    val idA = estado.idA
    val idB = estado.idB
    val mapaNuevo by servicio.mapaNuevo.collectAsState()
    val plan by conexion.automixDebugInfo.collectAsState()
    val transicion by servicio.transicionEnCurso.collectAsState()
    var mapaA by remember(idA) { mutableStateOf<MapaDeCancion?>(null) }
    var mapaB by remember(idB) { mutableStateOf<MapaDeCancion?>(null) }
    LaunchedEffect(idA, idB, mapaNuevo) {
        val a = withContext(Dispatchers.IO) { idA?.let { servicio.mapaDe(it) } }
        val b = withContext(Dispatchers.IO) { idB?.let { servicio.mapaDe(it) } }
        mapaA = a
        mapaB = b
    }
    // El aviso del estilo y los compases, como en la etiqueta de arriba.
    val estilo = nombreDeEstiloPlaneado(plan?.estilo)
    val compases = plan?.compases ?: 0

    Column(modifier.pointerInput(Unit) { detectTapGestures { onTocar() } }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(stringResource(R.string.mixed_sale), color = colorSale, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.mixed_entra), color = colorEntra, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                if (compases > 0) "$estilo · $compases" else estilo,
                color = colorTexto.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                estado.golpe // vuelve a pintar en cada cuadro del reloj del tocadiscos
                val ahora = SystemClock.elapsedRealtime()
                val t = transicion?.takeIf { it.deId == idA && ahora - it.inicioMs < 120_000L }
                val disparo: Long
                val entraDesde: Long
                val largo: Long
                val posicion: Long
                if (t != null) {
                    disparo = t.salienteDesdeMs
                    entraDesde = t.entranteDesdeMs
                    largo = t.duracionMs.coerceAtLeast(1_000L)
                    posicion = disparo + (ahora - t.inicioMs)
                } else {
                    val p = plan ?: return@Canvas
                    disparo = p.triggerTimeMs ?: return@Canvas
                    entraDesde = p.incomingStartMs ?: 0L
                    largo = (p.overlapMs ?: 8_000L).coerceAtLeast(1_000L)
                    posicion = conexion.player.currentPosition
                }
                val desde = disparo - 12_000L
                val hasta = disparo + largo + 4_000L
                fun x(ms: Long) = (ms - desde).toFloat() / (hasta - desde) * size.width
                // La zona de la mezcla.
                drawRoundRect(
                    color = colorTexto.copy(alpha = 0.08f),
                    topLeft = Offset(x(disparo), 0f),
                    size = Size(x(disparo + largo) - x(disparo), size.height),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
                val mitad = size.height / 2f
                onda(mapaA, desde, hasta, 0L, mitad * 0.5f, mitad * 0.85f, colorSale, posicion)
                // La que entra se dibuja en el tiempo de la que sale: su entrada cae en el disparo.
                onda(mapaB, desde, hasta, disparo - entraDesde, mitad * 1.5f, mitad * 0.85f, colorEntra, posicion)
                val xc = x(posicion).coerceIn(0f, size.width)
                drawLine(colorTexto, Offset(xc, 0f), Offset(xc, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * Una onda en barras: cada columna toma el volumen del mapa en ese instante. [desplazamiento] lleva del tiempo de la
 * vista al de la canción. Lo que ya sonó va entero; lo que viene, más tenue. Sin mapa, una línea punteada.
 */
private fun DrawScope.onda(
    mapa: MapaDeCancion?,
    desde: Long,
    hasta: Long,
    desplazamiento: Long,
    centroY: Float,
    alto: Float,
    color: Color,
    posicion: Long,
) {
    val paso = 4.dp.toPx()
    val grosor = 2.5.dp.toPx()
    var x = paso / 2f
    while (x < size.width) {
        val ms = desde + ((x / size.width) * (hasta - desde)).toLong()
        val enCancion = ms - desplazamiento
        val a = if (mapa == null) {
            -1f
        } else {
            val i = (enCancion / MapaDeCancion.PASO_ONDA_MS).toInt()
            if (enCancion < 0 || i !in mapa.onda.indices) 0f else mapa.onda[i]
        }
        val alfa = if (ms <= posicion) 1f else 0.45f
        if (a < 0f) {
            drawCircle(color.copy(alpha = 0.3f), radius = 1.dp.toPx(), center = Offset(x, centroY))
        } else if (a > 0.01f) {
            val h = (a * alto).coerceAtLeast(grosor)
            drawLine(color.copy(alpha = alfa), Offset(x, centroY - h / 2f), Offset(x, centroY + h / 2f), strokeWidth = grosor, cap = StrokeCap.Round)
        }
        x += paso
    }
}
