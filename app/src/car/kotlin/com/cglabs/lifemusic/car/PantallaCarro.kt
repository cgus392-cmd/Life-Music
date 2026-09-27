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
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    var pestana by rememberSaveable { mutableStateOf(Pestana.ME_GUSTA) }
    // Las paginas de detalle abiertas (album, lista, artista), la ultima encima.
    val pila = remember { mutableStateListOf<Detalle>() }
    val busqueda = remember { EstadoBusqueda() }
    val inicio = remember { EstadoInicio() }
    val navegador = remember {
        Navegador(
            abrir = { d ->
                pila.add(d)
                sonandoAbierto = false
            },
            irABiblioteca = { p ->
                pila.clear()
                pestana = p
                destino = Destino.BIBLIOTECA
            },
        )
    }

    // Atras cierra Sonando; luego las paginas de detalle, una a una; luego vuelve a Inicio; en Inicio, sale.
    BackHandler(enabled = sonandoAbierto || pila.isNotEmpty() || destino != Destino.INICIO) {
        when {
            sonandoAbierto -> sonandoAbierto = false
            pila.isNotEmpty() -> pila.removeAt(pila.lastIndex)
            else -> destino = Destino.INICIO
        }
    }

    LifeMusicTheme(darkTheme = true, themeColor = color) {
        Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalNavegador provides navegador) {
                Box(Modifier.fillMaxSize()) {
                    FondoAmbiente(metadatos?.thumbnailUrl, moverse = fondoEnMovimiento && animar)

                    AnimatedVisibility(
                        visible = !sonandoAbierto,
                        enter = fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(360), initialScale = 0.96f),
                        exit = fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.96f),
                    ) {
                        Marco(
                            conexion = conexion,
                            m = metadatos,
                            destino = destino,
                            detalle = pila.lastOrNull(),
                            alElegir = { d ->
                                pila.clear()
                                destino = d
                            },
                            alCerrarDetalle = { if (pila.isNotEmpty()) pila.removeAt(pila.lastIndex) },
                            pestana = pestana,
                            alCambiarPestana = { pestana = it },
                            busqueda = busqueda,
                            inicio = inicio,
                            alAbrirSonando = { sonandoAbierto = true },
                        )
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
}

/**
 * El riel y la pantalla elegida (o la pagina de detalle abierta encima), con el
 * mini reproductor al pie (salvo en Inicio, que ya lo lleva).
 */
@Composable
private fun Marco(
    conexion: PlayerConnection?,
    m: MediaMetadata?,
    destino: Destino,
    detalle: Detalle?,
    alElegir: (Destino) -> Unit,
    alCerrarDetalle: () -> Unit,
    pestana: Pestana,
    alCambiarPestana: (Pestana) -> Unit,
    busqueda: EstadoBusqueda,
    inicio: EstadoInicio,
    alAbrirSonando: () -> Unit,
) {
    // Lo que se ve: la pagina de detalle de arriba de la pila, o la pantalla del riel.
    val vista: Any = detalle ?: destino
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
                targetState = vista,
                transitionSpec = {
                    if (targetState is Detalle) {
                        // Una pagina de detalle entra desde la derecha, como en el telefono.
                        (fadeIn(tween(260, delayMillis = 40)) + slideInHorizontally(tween(340)) { it / 10 }) togetherWith fadeOut(tween(120))
                    } else {
                        (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320)) { it / 24 }) togetherWith fadeOut(tween(120))
                    }
                },
                label = "vista",
                modifier = Modifier.weight(1f),
            ) { v ->
                when (v) {
                    is Detalle -> PantallaDetalle(v, conexion, alVolver = alCerrarDetalle)
                    Destino.INICIO -> Inicio(conexion, m, inicio, alBuscar = { alElegir(Destino.BUSCAR) }, alAbrirSonando = alAbrirSonando)
                    Destino.BUSCAR -> Buscar(conexion, m, busqueda)
                    Destino.BIBLIOTECA -> Biblioteca(conexion, pestana, alCambiarPestana)
                    Destino.COLA -> Cola(conexion)
                    Destino.AJUSTES -> Ajustes()
                }
            }
            AnimatedVisibility(
                visible = vista != Destino.INICIO && m != null && !tecladoAbierto,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MiniReproductor(conexion, m, alAbrirSonando, Modifier.padding(top = 12.dp))
            }
        }
    }
}
