package com.cglabs.lifemusic.ui.screens.mixed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.db.entities.BeatInfoEntity
import com.cglabs.lifemusic.playback.queues.ListQueue
import kotlin.math.roundToInt

/**
 * 2.1.0 · Mixed Editor v1 (maqueta aprobada por CG el 09-11, Brand/maquetas/2026-11-07-mesa-de-mezcla).
 * «Mezclar» al lado de Reproducir: antes de sonar, Nova escucha las primeras canciones (golpes,
 * BPM y tonalidad, lo mismo que usa el Automix) y la sesion queda en modo mezcla: cada cambio va al
 * compas, con el estilo que pida la pareja. El set completo y el editor de cada transicion son de
 * la 2.1.1.
 */
object PedidoDeMezcla {
    var titulo: String? = null
    var canciones: List<MediaItem> = emptyList()

    /** Cuantas se escuchan antes de arrancar; las demas, el Automix las analiza al acercarse. */
    const val ANTES_DE_SONAR = 4

    fun abrir(navController: NavController, titulo: String?, canciones: List<MediaItem>) {
        if (canciones.isEmpty()) return
        this.titulo = titulo
        this.canciones = canciones
        navController.navigate("mixed/analizando")
    }
}

/** El boton «Mezclar» de las listas: tonal, con las dos ondas que se cruzan. */
@Composable
fun BotonMezclar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alto: Dp = 48.dp,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(alto),
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(painterResource(R.drawable.mezclar), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.mixed_mezclar),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «Do mayor», «La menor»: la tonalidad que calcula el analizador, en espanol. */
private val NOTAS = listOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")

@Composable
private fun detalle(info: BeatInfoEntity): String {
    // 2.1.0 · El tempo con que se mezcla, no el de la mitad: el del comienzo (por donde entra) y el del final (por
    // donde sale). Si son el mismo, uno solo.
    val entra = (info.bpmEntrada?.takeIf { it > 0f } ?: info.bpm).roundToInt()
    val sale = (info.bpmSalida?.takeIf { it > 0f } ?: info.bpm).roundToInt()
    val bpm = if (entra == sale) stringResource(R.string.mixed_bpm, sale)
    else stringResource(R.string.mixed_bpm_tramos, entra, sale)
    val tono = info.keyPitchClass?.let { NOTAS.getOrNull(it) }?.let { nota ->
        nota + " " + stringResource(if (info.keyIsMinor == true) R.string.mixed_menor else R.string.mixed_mayor)
    }
    return if (tono != null) "$bpm · $tono" else bpm
}

private enum class Estado { ESPERA, ANALIZANDO, LISTA, SIN_GOLPES, AL_SONAR }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MixedAnalizandoScreen(navController: NavController) {
    val conexion = LocalPlayerConnection.current ?: return
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val canciones = remember { PedidoDeMezcla.canciones }
    val titulo = remember { PedidoDeMezcla.titulo }
    val porAnalizar = remember { canciones.take(PedidoDeMezcla.ANTES_DE_SONAR) }
    val datos = remember { mutableStateMapOf<String, BeatInfoEntity?>() }
    var actual by remember { mutableStateOf<String?>(null) }
    var arrancada by remember { mutableStateOf(false) }
    var terminado by remember { mutableStateOf(false) }

    val mezclar: () -> Unit = {
        if (!arrancada) {
            arrancada = true
            conexion.service.mixedEditor.value = true
            android.widget.Toast.makeText(contexto, contexto.getString(R.string.mixed_activado), android.widget.Toast.LENGTH_LONG).show()
            conexion.playQueue(ListQueue(title = titulo, items = canciones))
            navController.navigateUp()
        }
    }

    LaunchedEffect(Unit) {
        conexion.service.analizarParaMezcla(porAnalizar.map { it.mediaId }) { id, info ->
            datos[id] = info
            actual = porAnalizar.getOrNull(porAnalizar.indexOfFirst { it.mediaId == id } + 1)?.mediaId
        }
        // Prueba de CG (09-11): si ya estaban analizadas, esto terminaba al instante y la pantalla se
        // cerraba sola. Ahora espera a que la persona toque «Empezar la mezcla».
        terminado = true
    }
    LaunchedEffect(Unit) { actual = porAnalizar.firstOrNull()?.mediaId }

    val hechas = datos.size
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mixed_analizando)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { relleno ->
        // Prueba de CG (09-11): con musica sonando, el mini reproductor tapaba la ultima fila y el
        // boton «Empezar la mezcla» no se veia. Se deja el espacio del mini reproductor, como las demas.
        Column(
            Modifier.fillMaxSize().padding(top = relleno.calculateTopPadding())
                .windowInsetsPadding(
                    com.cglabs.lifemusic.LocalPlayerAwareWindowInsets.current.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom),
                ),
        ) {
            LinearWavyProgressIndicator(
                progress = { if (porAnalizar.isEmpty()) 1f else hechas.toFloat() / porAnalizar.size },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Text(
                stringResource(R.string.mixed_analizando_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(canciones, key = { i, c -> "$i-${c.mediaId}" }) { i, c ->
                    val estado = when {
                        i >= porAnalizar.size -> Estado.AL_SONAR
                        datos.containsKey(c.mediaId) -> if ((datos[c.mediaId]?.bpm ?: 0f) > 0f) Estado.LISTA else Estado.SIN_GOLPES
                        c.mediaId == actual -> Estado.ANALIZANDO
                        else -> Estado.ESPERA
                    }
                    FilaDeCancion(c, estado, datos[c.mediaId])
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(R.string.mixed_progreso, hechas, porAnalizar.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (terminado) {
                    androidx.compose.material3.Button(onClick = mezclar) {
                        Icon(painterResource(R.drawable.mezclar), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.mixed_empezar))
                    }
                } else {
                    TextButton(onClick = mezclar) { Text(stringResource(R.string.mixed_mezclar_ya)) }
                }
            }
        }
    }
}

@Composable
private fun FilaDeCancion(c: MediaItem, estado: Estado, info: BeatInfoEntity?) {
    val apagada = estado == Estado.ESPERA || estado == Estado.AL_SONAR
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).alpha(if (apagada) 0.55f else 1f),
    ) {
        AsyncImage(
            model = c.mediaMetadata.artworkUri,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(c.mediaMetadata.title?.toString().orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (estado) {
                    Estado.LISTA -> info?.let { detalle(it) }.orEmpty()
                    Estado.SIN_GOLPES -> stringResource(R.string.mixed_sin_golpes)
                    Estado.ANALIZANDO -> stringResource(R.string.mixed_buscando_golpes)
                    Estado.ESPERA -> stringResource(R.string.mixed_en_espera)
                    Estado.AL_SONAR -> c.mediaMetadata.artist?.toString().orEmpty()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (estado) {
                Estado.LISTA -> Icon(painterResource(R.drawable.check), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Estado.ANALIZANDO -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else -> {}
            }
        }
    }
}
