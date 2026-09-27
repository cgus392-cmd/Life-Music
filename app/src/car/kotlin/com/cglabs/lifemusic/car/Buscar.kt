@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cglabs.lifemusic.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.models.toMediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.playback.queues.YouTubeQueue
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lo que se busco y lo que salio. Vive fuera de la pantalla (en PantallaCarro)
 * para que ir a Sonando o a Cola y volver no borre los resultados.
 */
class EstadoBusqueda {
    var texto by mutableStateOf("")
    var resultados by mutableStateOf<List<SongItem>>(emptyList())
    var buscando by mutableStateOf(false)
}

/**
 * Buscar canciones: el campo arriba y los resultados en dos columnas (el
 * radio es ancho y bajo; una sola columna dejaria media pantalla vacia).
 * Cada resultado toca y suena, con su radio detras. Al entrar vacio, el campo
 * ya tiene el foco: en el carro, un toque menos es un toque menos.
 */
@Composable
fun Buscar(conexion: PlayerConnection?, m: MediaMetadata?, estado: EstadoBusqueda, modifier: Modifier = Modifier) {
    val alcance = rememberCoroutineScope()
    val teclado = LocalSoftwareKeyboardController.current
    val pedirFoco = remember { FocusRequester() }
    val sumidero = remember { FocusRequester() }
    val sonando = sonandoAhora(conexion)

    fun buscar() {
        val q = estado.texto.trim()
        if (q.isEmpty()) return
        // En Android 8 soltar el foco (clearFocus) no basta: el sistema se lo devuelve
        // a la app y Compose al campo, y el teclado vuelve a salir. Se le da a un
        // sumidero invisible: el campo lo pierde de verdad y el teclado se va.
        runCatching { sumidero.requestFocus() }
        teclado?.hide()
        estado.buscando = true
        alcance.launch {
            estado.resultados = withContext(Dispatchers.IO) {
                YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
            }
            estado.buscando = false
        }
    }

    LaunchedEffect(Unit) {
        if (estado.texto.isEmpty() && estado.resultados.isEmpty()) runCatching { pedirFoco.requestFocus() }
    }

    Column(modifier.fillMaxSize()) {
        TituloDePantalla(stringResource(R.string.carro_buscar)) {
            Box(Modifier.size(1.dp).focusRequester(sumidero).focusable())
        }
        OutlinedTextField(
            value = estado.texto,
            onValueChange = { estado.texto = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.carro_buscar_pista), style = MaterialTheme.typography.titleLarge) },
            leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(28.dp)) },
            textStyle = MaterialTheme.typography.titleLarge,
            shape = RoundedCornerShape(32.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { buscar() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
            ),
            modifier = Modifier.fillMaxWidth().height(68.dp).focusRequester(pedirFoco),
        )
        Spacer(Modifier.height(14.dp))
        when {
            estado.buscando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(80.dp)) }
            estado.resultados.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.carro_buscar_vacio), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(estado.resultados, key = { it.id }) { cancion ->
                    val activa = cancion.id == m?.id
                    FilaCancion(
                        titulo = cancion.title,
                        artistas = cancion.artists.joinToString { it.name },
                        caratula = cancion.thumbnail,
                        activa = activa,
                        sonando = sonando,
                        alTocar = {
                            if (activa) conexion?.togglePlayPause()
                            else conexion?.playQueue(YouTubeQueue.radio(cancion.toMediaMetadata()))
                        },
                    )
                }
            }
        }
    }
}
