@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cglabs.lifemusic.car

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.SongSortType
import com.cglabs.lifemusic.db.entities.Song
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.models.toMediaMetadata
import com.cglabs.lifemusic.playback.PlayerConnection
import com.cglabs.lifemusic.playback.queues.YouTubeQueue
import android.util.Log
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.pages.HomePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cuantas canciones recientes se muestran en Inicio. Las que caben sin que la lista se vuelva tarea. */
private const val RECIENTES = 12

/** Cuantas canciones entran en «Aleatorio» cuando aun no hay favoritas: las ultimas escuchadas. */
private const val POZO_ALEATORIO = 100

/**
 * El inicio de YouTube Music, pedido una vez y guardado mientras la app vive:
 * ir a Buscar y volver no lo pide otra vez (en un radio con datos del telefono,
 * cada peticion cuenta).
 */
class EstadoInicio {
    var secciones by mutableStateOf<List<HomePage.Section>>(emptyList())
    var cargado by mutableStateOf(false)
    var cargando by mutableStateOf(false)
    var fallo by mutableStateOf(false)
    private var continuacion: String? = null

    /**
     * Sin cuenta, YouTube suele mandar la primera pagina del inicio casi vacia y el
     * contenido en las siguientes: se siguen hasta juntar unas cuantas secciones.
     * Al final va «Novedades» de Explorar, que siempre trae algo.
     */
    suspend fun cargar() {
        if (cargando) return
        cargando = true
        fallo = false
        val r = withContext(Dispatchers.IO) { YouTube.home() }
        val pagina = r.getOrNull()
        if (pagina == null) {
            fallo = true
            cargando = false
            Log.w(ETIQUETA, "Inicio de YouTube Music no cargo", r.exceptionOrNull())
            return
        }
        var juntas = pagina.sections.filter { it.items.isNotEmpty() }
        continuacion = pagina.continuation
        var vueltas = 0
        while (juntas.size < MIN_SECCIONES && continuacion != null && vueltas < MAX_VUELTAS) {
            val siguiente = withContext(Dispatchers.IO) { YouTube.home(continuacion).getOrNull() } ?: break
            juntas = juntas + siguiente.sections.filter { it.items.isNotEmpty() }
            continuacion = siguiente.continuation
            vueltas++
        }
        novedades = withContext(Dispatchers.IO) { YouTube.explore().getOrNull()?.newReleaseAlbums }.orEmpty()
        Log.d(ETIQUETA, "Inicio: ${juntas.size} secciones en ${vueltas + 1} paginas, ${novedades.size} novedades")
        secciones = juntas.distinctBy { it.title }
        cargado = true
        cargando = false
    }

    /** Mas secciones al llegar al final de Inicio, si YouTube tiene. */
    suspend fun cargarMas() {
        val c = continuacion ?: return
        if (cargando) return
        cargando = true
        val siguiente = withContext(Dispatchers.IO) { YouTube.home(c).getOrNull() }
        continuacion = siguiente?.continuation
        secciones = (secciones + siguiente?.sections.orEmpty().filter { it.items.isNotEmpty() }).distinctBy { it.title }
        cargando = false
    }

    /** Los albumes nuevos de Explorar: la ultima fila de Inicio. */
    var novedades by mutableStateOf<List<AlbumItem>>(emptyList())

    val hayMas: Boolean get() = continuacion != null

    private companion object {
        const val ETIQUETA = "LifeMusicCarro"
        const val MIN_SECCIONES = 6
        const val MAX_VUELTAS = 4
    }
}

/**
 * Inicio del carro, de arriba abajo:
 *  - el saludo de la hora y la fecha;
 *  - lo que suena (o lo que quedo sonando) y lo escuchado hace poco, en cristal;
 *  - cuatro accesos grandes: Me gusta, Descargas, Aleatorio y Radio;
 *  - Seleccion rapida, a partir de lo que escuchas en el carro;
 *  - y las secciones del inicio de YouTube Music, cada una deslizandose de lado.
 * En Inicio no va el mini reproductor: el primer panel lo hace.
 */
@Composable
fun Inicio(
    conexion: PlayerConnection?,
    m: MediaMetadata?,
    estado: EstadoInicio,
    alBuscar: () -> Unit,
    alAbrirSonando: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val navegador = LocalNavegador.current
    val ahora = rememberAhora()
    val db = conexion?.database
    val gustan by remember(db) { db?.likedSongs(SongSortType.CREATE_DATE, true) ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val descargas by remember(db) { db?.downloadedSongs(SongSortType.CREATE_DATE, true) ?: flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val escuchadas by remember(db) { db?.escuchadasHacePoco(POZO_ALEATORIO) ?: flowOf(emptyList<Song>()) }.collectAsState(initial = emptyList())
    val seleccion by remember(db) { db?.quickPicks() ?: flowOf(emptyList<Song>()) }.collectAsState(initial = emptyList())
    LaunchedEffect(Unit) { if (!estado.cargado) estado.cargar() }
    val alcance = rememberCoroutineScope()

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = modifier.fillMaxSize()) {
        item(key = "saludo") {
            Column {
                Text(cabeceraDeLaHora(contexto, ahora), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(fechaLarga(ahora), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
            }
        }
        item(key = "sonando") {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.height(360.dp)) {
                PanelSonando(conexion, m, alBuscar, alAbrirSonando, Modifier.weight(1.3f).fillMaxHeight())
                PanelRecientes(conexion, m, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item(key = "accesos") {
            // Aleatorio baraja tus favoritas y descargas; si aun no hay, lo ultimo escuchado.
            val pozo = (gustan + descargas).distinctBy { it.id }.ifEmpty { escuchadas }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp)) {
                Acceso(
                    R.drawable.favorite,
                    stringResource(R.string.carro_pestana_me_gusta),
                    stringResource(R.string.carro_cola_cuantas, gustan.size),
                    { navegador.irABiblioteca(Pestana.ME_GUSTA) },
                    Modifier.weight(1f),
                )
                Acceso(
                    R.drawable.download,
                    stringResource(R.string.carro_pestana_descargas),
                    stringResource(R.string.carro_cola_cuantas, descargas.size),
                    { navegador.irABiblioteca(Pestana.DESCARGAS) },
                    Modifier.weight(1f),
                )
                Acceso(
                    R.drawable.shuffle,
                    stringResource(R.string.carro_aleatorio),
                    stringResource(R.string.carro_aleatorio_texto),
                    { conexion?.tocarLista(contexto.getString(R.string.carro_aleatorio), pozo.map { it.toMediaMetadata() }, aleatorio = true) },
                    Modifier.weight(1f),
                )
                Acceso(
                    R.drawable.radio,
                    stringResource(R.string.carro_radio),
                    stringResource(R.string.carro_radio_texto),
                    { if (m != null) conexion?.startRadioSeamlessly() else alBuscar() },
                    Modifier.weight(1f),
                )
            }
        }
        if (seleccion.isNotEmpty()) {
            item(key = "seleccion") {
                Column(Modifier.padding(bottom = 22.dp)) {
                    TituloDeSeccion(stringResource(R.string.carro_seleccion_rapida))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(seleccion.take(20), key = { it.id }) { c ->
                            Tarjeta(
                                titulo = c.title,
                                subtitulo = c.artists.joinToString { it.name },
                                caratula = c.thumbnailUrl,
                                alTocar = { conexion?.playQueue(YouTubeQueue.radio(c.toMediaMetadata())) },
                            )
                        }
                    }
                }
            }
        }
        items(estado.secciones, key = { "yt-" + it.title }) { s ->
            Carrusel(s.title, s.items, conexion, Modifier.padding(bottom = 22.dp))
        }
        if (estado.hayMas) {
            // Al asomar el final se piden mas secciones.
            item(key = "mas") {
                LaunchedEffect(estado.secciones.size) { estado.cargarMas() }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(72.dp)) { LoadingIndicator(Modifier.size(48.dp)) }
            }
        }
        if (estado.novedades.isNotEmpty()) {
            item(key = "novedades") {
                Carrusel(stringResource(R.string.carro_novedades), estado.novedades, conexion, Modifier.padding(bottom = 22.dp))
            }
        }
        if (estado.secciones.isEmpty() && estado.novedades.isEmpty()) {
            item(key = "cargando") {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    when {
                        estado.cargando -> LoadingIndicator(Modifier.size(64.dp))
                        estado.fallo -> Aviso(R.drawable.error, stringResource(R.string.carro_no_abre), stringResource(R.string.carro_no_abre_texto)) {
                            BotonPildora(R.drawable.refresh, stringResource(R.string.carro_reintentar), { alcance.launch { estado.cargar() } })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelSonando(conexion: PlayerConnection?, m: MediaMetadata?, alBuscar: () -> Unit, alAbrir: () -> Unit, modifier: Modifier) {
    val sonando = sonandoAhora(conexion)
    Column(
        modifier
            .cristal(RoundedCornerShape(32.dp))
            .clickable(enabled = m != null, onClick = alAbrir)
            .padding(20.dp),
    ) {
        Text(
            stringResource(if (sonando) R.string.carro_sonando else R.string.carro_continuar).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.8.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (m == null) {
            // Primera vez (o cola vacia): una invitacion clara a buscar.
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                Icon(painterResource(R.drawable.music_note), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.carro_nada_sonando), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(64.dp)
                        .cristal(RoundedCornerShape(32.dp), MaterialTheme.colorScheme.primary, fuerza = 2.6f)
                        .clickable(onClick = alBuscar)
                        .padding(horizontal = 28.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.carro_empezar_buscar), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Caratula(m.thumbnailUrl, RoundedCornerShape(24.dp), Modifier.fillMaxHeight(0.84f).aspectRatio(1f), tamano = 240.dp, sombra = 8.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    m.artists.joinToString { it.name },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        BarraFina(conexion, sonando, m, Modifier.fillMaxWidth().height(4.dp))
        Spacer(Modifier.height(16.dp))
        Controles(conexion, sonando, Modifier.fillMaxWidth(), escala = 0.8f)
    }
}

@Composable
private fun PanelRecientes(conexion: PlayerConnection?, m: MediaMetadata?, modifier: Modifier) {
    val sonando = sonandoAhora(conexion)
    val canciones by remember(conexion) {
        conexion?.database?.escuchadasHacePoco(RECIENTES) ?: flowOf(emptyList<Song>())
    }.collectAsState(initial = emptyList())
    Column(modifier.cristal(RoundedCornerShape(32.dp)).padding(top = 20.dp, start = 12.dp, end = 12.dp)) {
        Text(
            stringResource(R.string.carro_escuchadas).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.8.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        if (canciones.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(stringResource(R.string.carro_sin_historial), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(canciones, key = { it.id }) { c ->
                val activa = c.id == m?.id
                FilaCancion(
                    titulo = c.title,
                    artistas = c.artists.joinToString { it.name },
                    caratula = c.thumbnailUrl,
                    activa = activa,
                    sonando = sonando,
                    alTocar = {
                        if (activa) conexion?.togglePlayPause()
                        else conexion?.playQueue(YouTubeQueue.radio(c.toMediaMetadata()))
                    },
                )
            }
        }
    }
}
