@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cglabs.lifemusic.car

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.PauseSearchHistoryKey
import com.cglabs.lifemusic.db.entities.SearchHistory
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.models.toMediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.playback.queues.YouTubeQueue
import com.cglabs.lifemusic.utils.rememberPreference
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Que se busca: cada filtro es una pastilla sobre los resultados. */
enum class Filtro(val deYouTube: YouTube.SearchFilter, val nombre: Int) {
    CANCIONES(YouTube.SearchFilter.FILTER_SONG, R.string.carro_filtro_canciones),
    ARTISTAS(YouTube.SearchFilter.FILTER_ARTIST, R.string.carro_filtro_artistas),
    ALBUMES(YouTube.SearchFilter.FILTER_ALBUM, R.string.carro_filtro_albumes),
    LISTAS(YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST, R.string.carro_filtro_listas),
    VIDEOS(YouTube.SearchFilter.FILTER_VIDEO, R.string.carro_filtro_videos),
}

/**
 * Lo que se busco y lo que salio. Vive fuera de la pantalla (en PantallaCarro)
 * para que ir a Sonando o a Cola y volver no borre los resultados.
 */
class EstadoBusqueda {
    var campo by mutableStateOf(TextFieldValue(""))
    val texto get() = campo.text
    var filtro by mutableStateOf(Filtro.CANCIONES)
    var resultados by mutableStateOf<List<YTItem>>(emptyList())
    var buscando by mutableStateOf(false)

    /** Lo ultimo que se busco (con que filtro): para decir «nada para ...» y no repetir la misma busqueda. */
    var buscado by mutableStateOf<Pair<String, Filtro>?>(null)
    var sugerencias by mutableStateOf<List<String>>(emptyList())
    var directos by mutableStateOf<List<YTItem>>(emptyList())

    fun ponerTexto(t: String) {
        campo = TextFieldValue(t, TextRange(t.length))
    }
}

/**
 * Buscar: el campo arriba (con el microfono si el radio tiene dictado), las
 * pastillas de filtro debajo y, segun el momento:
 *
 *  - escribiendo (o al entrar vacio): lo que ya buscaste y lo que sugiere
 *    YouTube Music, en dos columnas, mas los aciertos directos (un artista, una
 *    cancion) que se tocan sin buscar;
 *  - despues de buscar: los resultados, canciones en filas y lo demas en
 *    tarjetas.
 *
 * En el carro cada toque cuenta: una sugerencia busca al tocarla, el filtro
 * vuelve a buscar solo, y al entrar vacio el campo ya tiene el foco.
 */
@Composable
fun Buscar(conexion: PlayerConnection?, m: MediaMetadata?, estado: EstadoBusqueda, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val teclado = LocalSoftwareKeyboardController.current
    val pedirFoco = remember { FocusRequester() }
    val sumidero = remember { FocusRequester() }
    val sonando = sonandoAhora(conexion)
    val navegador = LocalNavegador.current
    val db = conexion?.database
    val pausaHistorial by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    var enfocado by remember { mutableStateOf(false) }
    val tecladoAbierto = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun buscar(consulta: String = estado.texto, filtro: Filtro = estado.filtro) {
        val q = consulta.trim()
        if (q.isEmpty()) return
        // En Android 8 soltar el foco (clearFocus) no basta: el sistema se lo devuelve
        // a la app y Compose al campo, y el teclado vuelve a salir. Se le da a un
        // sumidero invisible: el campo lo pierde de verdad y el teclado se va.
        runCatching { sumidero.requestFocus() }
        teclado?.hide()
        // El texto va despues de soltar el foco: el teclado, al irse, confirma lo
        // que tenia a medio escribir («bad») y pisaria la sugerencia tocada
        // («bad bunny»). Por si llega tarde, se vuelve a poner tras un instante.
        estado.ponerTexto(q)
        estado.filtro = filtro
        alcance.launch {
            delay(120)
            if (estado.texto != q) estado.ponerTexto(q)
        }
        if (estado.buscado == q to filtro && estado.resultados.isNotEmpty()) return
        estado.buscando = true
        alcance.launch {
            if (!pausaHistorial) withContext(Dispatchers.IO) { db?.query { insert(SearchHistory(query = q)) } }
            val items = withContext(Dispatchers.IO) {
                YouTube.search(q, filtro.deYouTube).getOrNull()?.items.orEmpty()
            }
            estado.resultados = when (filtro) {
                Filtro.CANCIONES, Filtro.VIDEOS -> items.filterIsInstance<SongItem>()
                else -> items
            }
            estado.buscado = q to filtro
            estado.buscando = false
        }
    }

    // El dictado del sistema (Google, o el que traiga el radio). Si no hay ninguno, no hay microfono.
    val dictado = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    val hayDictado = remember { runCatching { contexto.packageManager.queryIntentActivities(dictado, 0).isNotEmpty() }.getOrDefault(false) }
    val lanzarDictado = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { buscar(it) }
    }

    // Lo que ya se busco, filtrado por lo escrito.
    val historial by remember(db, estado.texto) {
        db?.searchHistory(estado.texto.trim()) ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Las sugerencias de YouTube Music, con una pausa corta para no pedir una por letra.
    LaunchedEffect(estado.texto) {
        val q = estado.texto.trim()
        if (q.isEmpty()) {
            estado.sugerencias = emptyList()
            estado.directos = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        val s = withContext(Dispatchers.IO) { YouTube.searchSuggestions(q).getOrNull() }
        estado.sugerencias = s?.queries.orEmpty()
        estado.directos = s?.recommendedItems.orEmpty()
    }

    LaunchedEffect(Unit) {
        if (estado.texto.isEmpty() && estado.resultados.isEmpty()) runCatching { pedirFoco.requestFocus() }
    }

    Column(modifier.fillMaxSize()) {
        // Con el teclado abierto queda poca altura: el titulo cede su sitio.
        AnimatedVisibility(visible = !tecladoAbierto) {
            TituloDePantalla(stringResource(R.string.carro_buscar))
        }
        Box(Modifier.size(1.dp).focusRequester(sumidero).focusable())
        OutlinedTextField(
            value = estado.campo,
            onValueChange = { estado.campo = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.carro_buscar_pista), style = MaterialTheme.typography.titleLarge) },
            leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(28.dp)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                    if (estado.texto.isNotEmpty()) {
                        BotonDelCampo(R.drawable.close, stringResource(R.string.carro_borrar_texto)) {
                            estado.ponerTexto("")
                            runCatching { pedirFoco.requestFocus() }
                            teclado?.show()
                        }
                    }
                    if (hayDictado) {
                        BotonDelCampo(R.drawable.mic, stringResource(R.string.carro_dictar), primario = true) {
                            runCatching { lanzarDictado.launch(dictado) }
                        }
                    }
                }
            },
            textStyle = MaterialTheme.typography.titleLarge,
            shape = RoundedCornerShape(32.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { buscar() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .focusRequester(pedirFoco)
                .onFocusChanged { enfocado = it.isFocused },
        )
        AnimatedVisibility(visible = !tecladoAbierto) {
            Filtros(estado.filtro, Modifier.padding(top = 12.dp)) { f ->
                if (estado.texto.isBlank()) estado.filtro = f else buscar(filtro = f)
            }
        }
        Spacer(Modifier.height(12.dp))

        val q = estado.texto.trim()
        val hayResultados = estado.buscado != null && estado.buscado?.first == q && estado.buscado?.second == estado.filtro
        when {
            estado.buscando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(80.dp)) }
            !enfocado && hayResultados && estado.resultados.isEmpty() -> Centrado(stringResource(R.string.carro_buscar_nada, q))
            !enfocado && hayResultados -> Resultados(estado, conexion, m, sonando)
            else -> Sugerencias(
                historial = historial.take(if (q.isEmpty()) 12 else 4),
                sugerencias = estado.sugerencias.filter { s -> historial.none { it.query.equals(s, ignoreCase = true) } }.take(8),
                directos = if (q.isEmpty()) emptyList() else estado.directos.take(4),
                alBuscar = { buscar(it) },
                alCompletar = { estado.ponerTexto("$it ") },
                alBorrar = { h -> alcance.launch(Dispatchers.IO) { db?.query { delete(h) } } },
                alBorrarTodo = { alcance.launch(Dispatchers.IO) { db?.query { clearSearchHistory() } } },
                alTocarDirecto = { item ->
                    runCatching { sumidero.requestFocus() }
                    teclado?.hide()
                    tocarElemento(item, conexion, navegador)
                },
            )
        }
    }
}

@Composable
private fun Centrado(texto: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Borrar o dictar, dentro del campo: 52 dp, redondos. */
@Composable
private fun BotonDelCampo(icono: Int, descripcion: String, primario: Boolean = false, alTocar: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .then(if (primario) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)) else Modifier)
            .clickable(onClick = alTocar),
    ) {
        Icon(
            painterResource(icono),
            contentDescription = descripcion,
            tint = if (primario) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Las pastillas de filtro, como las pestañas de Biblioteca. */
@Composable
private fun Filtros(actual: Filtro, modifier: Modifier = Modifier, alElegir: (Filtro) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Filtro.entries.forEach { f ->
            val activo = f == actual
            val fondo by animateColorAsState(if (activo) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f), tween(220), label = "filtro")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(fondo)
                    .clickable { alElegir(f) }
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    stringResource(f.nombre),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
                    color = if (activo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Mientras se escribe: el historial (reloj, con una X para olvidar cada una) y
 * las sugerencias (lupa, con una flecha que las pasa al campo para seguir
 * escribiendo), en dos columnas; y debajo, a todo el ancho, los aciertos
 * directos. Con el campo vacio, solo el historial y un «Borrar todo».
 */
@Composable
private fun Sugerencias(
    historial: List<SearchHistory>,
    sugerencias: List<String>,
    directos: List<YTItem>,
    alBuscar: (String) -> Unit,
    alCompletar: (String) -> Unit,
    alBorrar: (SearchHistory) -> Unit,
    alBorrarTodo: () -> Unit,
    alTocarDirecto: (YTItem) -> Unit,
) {
    if (historial.isEmpty() && sugerencias.isEmpty() && directos.isEmpty()) {
        Centrado(stringResource(R.string.carro_buscar_vacio))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        if (historial.isNotEmpty() && sugerencias.isEmpty() && directos.isEmpty()) {
            item(key = "cabecera", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TituloDeSeccion(stringResource(R.string.carro_busquedas_recientes), Modifier.weight(1f))
                    Text(
                        stringResource(R.string.carro_borrar_todo),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = alBorrarTodo).padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
        items(historial, key = { "h" + it.id }) { h ->
            FilaSugerencia(R.drawable.history, h.query, { alBuscar(h.query) }, R.drawable.close, stringResource(R.string.carro_olvidar)) { alBorrar(h) }
        }
        items(sugerencias, key = { "s$it" }) { s ->
            FilaSugerencia(R.drawable.search, s, { alBuscar(s) }, R.drawable.arrow_top_left, stringResource(R.string.carro_completar)) { alCompletar(s) }
        }
        items(directos, key = { "d" + it.id }) { item ->
            FilaCancion(
                titulo = item.title,
                artistas = subtituloDe(item),
                caratula = item.thumbnail,
                alTocar = { alTocarDirecto(item) },
            )
        }
    }
}

/** El tipo y los artistas de un acierto directo: «Artista», «Álbum · Bad Bunny»... */
@Composable
private fun subtituloDe(item: YTItem): String {
    val tipo = when (item) {
        is ArtistItem -> stringResource(R.string.carro_tipo_artista)
        is AlbumItem -> stringResource(R.string.carro_tipo_album)
        is PlaylistItem -> stringResource(R.string.carro_tipo_lista)
        else -> stringResource(R.string.carro_tipo_cancion)
    }
    val quien = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> item.artists?.joinToString { it.name }
        is PlaylistItem -> item.author?.name
        else -> null
    }
    return listOfNotNull(tipo, quien?.takeIf { it.isNotBlank() }).joinToString(" · ")
}

/** Una sugerencia: 60 dp, el texto toca y busca; el boton de la derecha hace lo otro. */
@Composable
private fun FilaSugerencia(icono: Int, texto: String, alTocar: () -> Unit, iconoAccion: Int, descripcionAccion: String, alAccion: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = alTocar)
            .padding(start = 16.dp),
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp).clip(CircleShape).clickable(onClick = alAccion)) {
            Icon(painterResource(iconoAccion), contentDescription = descripcionAccion, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

/** Los resultados: canciones y videos en filas de dos columnas; artistas, albumes y listas en tarjetas. */
@Composable
private fun Resultados(estado: EstadoBusqueda, conexion: PlayerConnection?, m: MediaMetadata?, sonando: Boolean) {
    if (estado.filtro == Filtro.CANCIONES || estado.filtro == Filtro.VIDEOS) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(estado.resultados.filterIsInstance<SongItem>(), key = { it.id }) { cancion ->
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
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(estado.resultados, key = { it.id }) { item -> TarjetaDe(item, conexion, lado = 148.dp) }
        }
    }
}
