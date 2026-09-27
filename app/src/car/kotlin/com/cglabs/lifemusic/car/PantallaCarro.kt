package com.cglabs.lifemusic.car

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.ui.component.SaludoEntrada
import com.cglabs.lifemusic.ui.theme.LifeMusicTheme
import com.cglabs.lifemusic.utils.rememberPreference

/**
 * Life Music for Car, entera. Pensada para un radio de 1024×600 (o 1280×720)
 * que se mira de reojo:
 *
 *  - detras de todo, el fondo ambiente con los colores de la caratula;
 *  - a la izquierda, el riel en cristal con la hora y las pantallas;
 *  - a la derecha, la pantalla elegida y, al pie, el mini reproductor;
 *  - encima, cuando se abre, Sonando a pantalla completa;
 *  - y al arrancar en frio, la entrada con el saludo.
 *
 * Todo lo que se toca mide 64 dp o mas, el texto se lee desde el asiento y el
 * color sale de la caratula (MaterialKolor; Android 8 no tiene color dinamico).
 * Los efectos estan hechos para un radio de 1 GB: ni desenfoques del sistema
 * (no existen en Android 8) ni capas fuera de pantalla.
 */
@Composable
fun PantallaCarro(conexion: PlayerConnection?) {
    val contexto = LocalContext.current
    val metadatos by (conexion?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf<MediaMetadata?>(null) })
    val semilla = colorDeCaratula(metadatos?.thumbnailUrl)
    val color by animateColorAsState(semilla, tween(900), label = "colorCaratula")

    val animar = remember { animacionesDelSistema(contexto) }
    val fondoEnMovimiento by rememberPreference(CarroFondoEnMovimientoKey, defaultValue = true)
    var destino by rememberSaveable { mutableStateOf(Destino.INICIO) }
    var sonandoAbierto by rememberSaveable { mutableStateOf(false) }
    // Una vez por arranque en frio, como el saludo del telefono (girar o volver de otra app no lo repite).
    var arrancando by rememberSaveable { mutableStateOf(SaludoEntrada.reclamarArranqueEnFrio() && animar) }
    val busqueda = remember { EstadoBusqueda() }

    // Atras cierra Sonando; si no esta abierto, vuelve a Inicio; en Inicio, sale.
    BackHandler(enabled = sonandoAbierto || destino != Destino.INICIO) {
        if (sonandoAbierto) sonandoAbierto = false else destino = Destino.INICIO
    }

    LifeMusicTheme(darkTheme = true, themeColor = color) {
        Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                FondoAmbiente(metadatos?.thumbnailUrl, moverse = fondoEnMovimiento && animar)

                AnimatedVisibility(
                    visible = !sonandoAbierto,
                    enter = fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(360), initialScale = 0.96f),
                    exit = fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.96f),
                ) {
                    Marco(conexion, metadatos, destino, { destino = it }, busqueda, alAbrirSonando = { sonandoAbierto = true })
                }

                AnimatedVisibility(
                    visible = sonandoAbierto,
                    enter = fadeIn(tween(320)) + slideInVertically(tween(420)) { it / 5 },
                    exit = fadeOut(tween(220)) + slideOutVertically(tween(260)) { it / 5 },
                ) {
                    SonandoCompleto(conexion, metadatos, alCerrar = { sonandoAbierto = false })
                }

                if (arrancando) Arranque(alTerminar = { arrancando = false })
            }
        }
    }
}

/** El riel y la pantalla elegida, con el mini reproductor al pie (salvo en Inicio, que ya lo lleva). */
@Composable
private fun Marco(
    conexion: PlayerConnection?,
    m: MediaMetadata?,
    destino: Destino,
    alElegir: (Destino) -> Unit,
    busqueda: EstadoBusqueda,
    alAbrirSonando: () -> Unit,
) {
    // El teclado solo encoge la columna de la derecha: el riel sigue entero.
    val tecladoAbierto = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))) {
        Riel(destino, alElegir, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .imePadding()
                .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp),
        ) {
            AnimatedContent(
                targetState = destino,
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320)) { it / 24 }) togetherWith fadeOut(tween(120))
                },
                label = "destino",
                modifier = Modifier.weight(1f),
            ) { d ->
                when (d) {
                    Destino.INICIO -> Inicio(conexion, m, alBuscar = { alElegir(Destino.BUSCAR) }, alAbrirSonando = alAbrirSonando)
                    Destino.BUSCAR -> Buscar(conexion, m, busqueda)
                    Destino.BIBLIOTECA -> Proximamente(
                        R.drawable.library_music_outlined,
                        stringResource(R.string.carro_biblioteca_pronto),
                        stringResource(R.string.carro_biblioteca_pronto_texto),
                    )
                    Destino.COLA -> Cola(conexion)
                    Destino.AJUSTES -> Ajustes()
                }
            }
            AnimatedVisibility(
                visible = destino != Destino.INICIO && m != null && !tecladoAbierto,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MiniReproductor(conexion, m, alAbrirSonando, Modifier.padding(top = 12.dp))
            }
        }
    }
}
