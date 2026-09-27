@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cglabs.lifemusic.car

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.ui.component.PlayingIndicatorBox
import com.cglabs.lifemusic.ui.utils.resize
import kotlinx.coroutines.delay

/** Si suena algo ahora mismo (false sin conexion con el servicio). */
@Composable
fun sonandoAhora(conexion: PlayerConnection?): Boolean {
    val sonando by (conexion?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) })
    return sonando
}

/** La caratula, o una nota de Life Music si no hay. Cambia de cancion con un fundido. */
@Composable
fun Caratula(url: String?, forma: Shape, modifier: Modifier = Modifier, pixeles: Int = 400, sombra: Dp = 0.dp) {
    Surface(shape = forma, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = sombra, modifier = modifier) {
        Crossfade(targetState = url, animationSpec = tween(450), label = "caratula") { u ->
            if (u != null) {
                AsyncImage(model = u.resize(pixeles, pixeles), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(painterResource(R.drawable.music_note), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize(0.4f))
                }
            }
        }
    }
}

/** El titulo de cada pantalla: grande, para leerlo desde el asiento. */
@Composable
fun TituloDePantalla(texto: String, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(texto, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        extra()
    }
}

/**
 * Una cancion en una lista (Buscar, Inicio, Cola). 72 dp de alto: se acierta
 * con el dedo sin mirar dos veces. La que suena va sobre cristal del color de
 * la caratula y con las barritas de Life Music encima.
 */
@Composable
fun FilaCancion(
    titulo: String,
    artistas: String,
    caratula: String?,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    activa: Boolean = false,
    sonando: Boolean = false,
) {
    val forma = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(if (activa) Modifier.cristal(forma, MaterialTheme.colorScheme.primary, fuerza = 2.4f) else Modifier.clip(forma))
            .clickable(onClick = alTocar)
            .padding(horizontal = 8.dp),
    ) {
        Box(Modifier.size(56.dp)) {
            Caratula(caratula, RoundedCornerShape(14.dp), Modifier.fillMaxSize(), pixeles = 160)
            if (activa) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.45f)))
                PlayingIndicatorBox(isActive = true, playWhenReady = sonando, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (activa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(artistas, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Un boton redondo de cristal (cerrar, me gusta...). */
@Composable
fun BotonCristal(icono: Int, descripcion: String, alTocar: () -> Unit, modifier: Modifier = Modifier, lado: Dp = 64.dp, tinta: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(lado).cristal(CircleShape).clickable(onClick = alTocar),
    ) {
        Icon(painterResource(icono), contentDescription = descripcion, tint = tinta, modifier = Modifier.size(lado * 0.46f))
    }
}

/** Una pantalla que llega en la siguiente tanda: lo dice con claridad, sin parecer un error. */
@Composable
fun Proximamente(icono: Int, titulo: String, texto: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp).cristal(RoundedCornerShape(36.dp))) {
            Icon(painterResource(icono), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(titulo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Posicion y duracion de lo que suena, en ms. Se consulta cada medio segundo
 * mientras suena; en pausa, una vez. Si el reproductor aun no preparo la
 * cancion (la cola recuperada al abrir, en pausa) no sabe la duracion: se
 * toma la de la cancion, para no mostrar 0:00 / 0:00.
 */
@Composable
fun progresoDe(conexion: PlayerConnection?, sonando: Boolean, m: MediaMetadata?): Pair<Long, Long> {
    var posicion by remember { mutableLongStateOf(0L) }
    var duracion by remember { mutableLongStateOf(0L) }
    LaunchedEffect(conexion, sonando, m?.id) {
        val p = conexion?.player ?: return@LaunchedEffect
        while (true) {
            posicion = p.currentPosition.coerceAtLeast(0L)
            duracion = p.duration.takeIf { it > 0 } ?: ((m?.duration ?: 0).coerceAtLeast(0) * 1000L)
            if (!sonando) break
            delay(500)
        }
    }
    return posicion to duracion
}

fun reloj(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** La barra ondulada de Expressive, con los tiempos: ondula mientras suena y se queda lisa en pausa. */
@Composable
fun BarraOndulada(conexion: PlayerConnection?, sonando: Boolean, m: MediaMetadata?, modifier: Modifier = Modifier) {
    val (posicion, duracion) = progresoDe(conexion, sonando, m)
    val fraccion = if (duracion > 0) (posicion.toFloat() / duracion).coerceIn(0f, 1f) else 0f
    Column(modifier) {
        LinearWavyProgressIndicator(
            progress = { fraccion },
            amplitude = { if (sonando) 1f else 0f },
            modifier = Modifier.fillMaxWidth().height(16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(reloj(posicion), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(reloj(duracion), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Una linea fina de progreso, para el mini reproductor. */
@Composable
fun BarraFina(conexion: PlayerConnection?, sonando: Boolean, m: MediaMetadata?, modifier: Modifier = Modifier) {
    val (posicion, duracion) = progresoDe(conexion, sonando, m)
    val fraccion = if (duracion > 0) (posicion.toFloat() / duracion).coerceIn(0f, 1f) else 0f
    val pista = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val lleno = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(pista, cornerRadius = r)
        drawRoundRect(lleno, size = Size(size.width * fraccion, size.height), cornerRadius = r)
    }
}

/**
 * Anterior, reproducir/pausa y siguiente. El boton central cambia de forma como
 * en Expressive: redondo en pausa, cuadrado suave sonando. [escala] 1 es el
 * tamano de Sonando; el mini reproductor usa menos.
 */
@Composable
fun Controles(conexion: PlayerConnection?, sonando: Boolean, modifier: Modifier = Modifier, escala: Float = 1f) {
    val lateral = 80.dp * escala
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp * escala, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        BotonControl(R.drawable.skip_previous, stringResource(R.string.carro_anterior), lateral) { conexion?.seekToPrevious() }
        val esquina by animateDpAsState(if (sonando) 28.dp * escala else 50.dp * escala, spring(dampingRatio = 0.6f, stiffness = 400f), label = "formaPlay")
        val fondo by animateColorAsState(MaterialTheme.colorScheme.primary, tween(900), label = "colorPlay")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 128.dp * escala, height = 100.dp * escala)
                .clip(RoundedCornerShape(esquina))
                .background(fondo)
                .clickable(enabled = conexion != null) { conexion?.togglePlayPause() },
        ) {
            Icon(
                painterResource(if (sonando) R.drawable.pause else R.drawable.play),
                contentDescription = stringResource(if (sonando) R.string.carro_pausar else R.string.carro_reproducir),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(50.dp * escala),
            )
        }
        BotonControl(R.drawable.skip_next, stringResource(R.string.carro_siguiente), lateral) { conexion?.seekToNext() }
    }
}

@Composable
private fun BotonControl(icono: Int, descripcion: String, lado: Dp, alTocar: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(lado).cristal(CircleShape, fuerza = 1.3f).clickable(onClick = alTocar),
    ) {
        Icon(painterResource(icono), contentDescription = descripcion, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(lado * 0.48f))
    }
}
