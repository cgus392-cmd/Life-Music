package com.cglabs.lifemusic.car

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.db.entities.Song
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import kotlinx.coroutines.flow.flowOf

/**
 * Sonando a pantalla completa: la caratula todo lo alta que da la pantalla a la
 * izquierda y, a la derecha, la hora en grande, el titulo, la barra ondulada y
 * los controles. Se abre tocando el mini reproductor y se cierra con la flecha
 * (o con Atras). El fondo es el mismo ambiente de siempre, que sigue debajo.
 */
@Composable
fun SonandoCompleto(conexion: PlayerConnection?, m: MediaMetadata?, alCerrar: () -> Unit, modifier: Modifier = Modifier) {
    val sonando = sonandoAhora(conexion)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
            .padding(28.dp),
    ) {
        Caratula(m?.thumbnailUrl, RoundedCornerShape(36.dp), Modifier.fillMaxHeight().aspectRatio(1f), pixeles = 544, sombra = 18.dp)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ahora = rememberAhora()
                Column(Modifier.weight(1f)) {
                    Text(horaCorta(ahora), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text(fechaLarga(ahora), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BotonCristal(R.drawable.expand_more, stringResource(R.string.carro_cerrar), alCerrar)
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.carro_sonando).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(6.dp))
            // Al cambiar de cancion el titulo sube y el nuevo entra desde abajo.
            AnimatedContent(
                targetState = m,
                contentKey = { it?.id },
                transitionSpec = {
                    (fadeIn(tween(320, delayMillis = 80)) + slideInVertically(tween(380)) { it / 3 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 4 })
                },
                label = "tituloSonando",
            ) { actual ->
                Column {
                    Text(
                        actual?.title ?: stringResource(R.string.carro_nada_sonando),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        actual?.artists?.joinToString { it.name } ?: stringResource(R.string.carro_busca_para_empezar),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            BarraOndulada(conexion, sonando, m, Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Controles(conexion, sonando, Modifier.weight(1f), escala = 0.92f)
                MeGusta(conexion)
            }
        }
    }
}

/** El corazon: se rellena del color de la caratula cuando la cancion ya te gusta. */
@Composable
private fun MeGusta(conexion: PlayerConnection?) {
    val cancion by remember(conexion) { conexion?.currentSong ?: flowOf<Song?>(null) }.collectAsState(initial = null)
    val gusta = cancion?.song?.liked == true
    BotonCristal(
        icono = if (gusta) R.drawable.favorite else R.drawable.favorite_border,
        descripcion = stringResource(R.string.carro_me_gusta),
        alTocar = { conexion?.toggleLike() },
        tinta = if (gusta) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * El mini reproductor: en cristal al pie de Buscar, Biblioteca, Cola y Ajustes.
 * Tocarlo abre Sonando; los botones funcionan sin abrirlo. Una linea fina
 * abajo dice por donde va la cancion.
 */
@Composable
fun MiniReproductor(conexion: PlayerConnection?, m: MediaMetadata?, alAbrir: () -> Unit, modifier: Modifier = Modifier) {
    val sonando = sonandoAhora(conexion)
    val forma = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().height(88.dp).cristal(forma).clickable(onClick = alAbrir)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp)) {
            Caratula(m?.thumbnailUrl, RoundedCornerShape(16.dp), Modifier.size(64.dp), pixeles = 192)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m?.title ?: stringResource(R.string.carro_nada_sonando),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    m?.artists?.joinToString { it.name }.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Controles(conexion, sonando, escala = 0.66f)
        }
        BarraFina(
            conexion,
            sonando,
            m,
            Modifier.align(Alignment.BottomCenter).padding(start = 90.dp, end = 28.dp, bottom = 6.dp).fillMaxWidth().height(3.dp),
        )
    }
}
