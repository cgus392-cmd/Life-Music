package com.cglabs.lifemusic.comunidad

import android.content.Intent
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.cglabs.lifemusic.LocalPlayerAwareWindowInsets
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.ui.component.IconButton
import com.cglabs.lifemusic.ui.utils.backToMain
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private const val PAGINA = "https://lifemusic.pages.dev/concurso"
private const val BASES = "https://lifemusic.pages.dev/concurso/bases"

/**
 * El prerregistro del nuevo concurso (1.3.1), al que se llega por la copa de
 * Inicio. Arriba el video de CG, que se pide a la pagina (lifemusic.pages.dev),
 * no va dentro del APK; el servidor dice cual es, asi que se puede cambiar sin
 * sacar version. Debajo el premio, el contador (nunca una lista) y el formulario
 * de nombre y correo. Ficha: docs/versiones/1.3.1/prerregistro.md.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrerregistroScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val estado by Prerregistro.estado.collectAsState()
    val datos by Prerregistro.datos(context).collectAsState(initial = null)
    var cargando by remember { mutableStateOf(estado == null) }
    var confirmarSalida by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Prerregistro.actualizar()
        cargando = false
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            title = { Text(stringResource(R.string.prerregistro_salir)) },
            text = { Text(stringResource(R.string.prerregistro_salir_confirmar)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    scope.launch {
                        if (!Prerregistro.salir(context)) {
                            android.widget.Toast.makeText(context, R.string.prerregistro_sin_red, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(stringResource(R.string.prerregistro_salir_si)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSalida = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prerregistro_titulo)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(
                    top = relleno.calculateTopPadding() + 8.dp,
                    bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val e = estado
            val video = e?.video?.takeIf { it.startsWith("https://") } ?: "https://lifemusic.pages.dev/concurso/video.mp4"
            VideoDelConcurso(video)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.prerregistro_eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.prerregistro_encabezado), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.prerregistro_explicacion, e?.meta ?: 100),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Premio(e?.premio?.ifBlank { null } ?: "JBL PartyBox 330")
            Contador(e, cargando)

            val d = datos
            when {
                d != null -> Dentro(
                    datos = d,
                    onInvitar = {
                        val texto = context.getString(R.string.prerregistro_invitar_texto, e?.premio ?: "JBL PartyBox 330", e?.meta ?: 100, PAGINA)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), null))
                    },
                    onSalir = { confirmarSalida = true },
                )
                e?.abierto == true -> Formulario(onBases = { uri.openUri(BASES) })
                else -> {}
            }

            Text(
                stringResource(R.string.prerregistro_sin_lista),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { uri.openUri(BASES) }) { Text(stringResource(R.string.prerregistro_ver_bases)) }
        }
    }
}

/**
 * El video de la pagina, en bucle y sin sonido; un toque lo pone con sonido
 * desde el principio (y entonces pide el foco de audio, asi que la musica se
 * pausa). Va en un TextureView para que respete las esquinas redondeadas.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoDelConcurso(url: String) {
    val context = LocalContext.current
    var conSonido by remember { mutableStateOf(false) }
    val jugador = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(jugador) { onDispose { jugador.release() } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
            .clickable {
                if (!conSonido) {
                    conSonido = true
                    jugador.setAudioAttributes(
                        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                        true,
                    )
                    jugador.seekTo(0)
                    jugador.volume = 1f
                } else {
                    jugador.playWhenReady = !jugador.playWhenReady
                }
            },
    ) {
        AndroidView(
            factory = { TextureView(it).also(jugador::setVideoTextureView) },
            modifier = Modifier.fillMaxSize(),
        )
        if (!conSonido) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Text(
                    stringResource(R.string.prerregistro_video_sonido),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Premio(premio: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.trophy), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column {
            Text(
                stringResource(R.string.prerregistro_premio_confirmado).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(premio, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Contador(e: EstadoPrerregistro?, cargando: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (e == null) "—" else e.contador.toString(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.prerregistro_de_meta, e?.meta ?: 100),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (e == null) 0f else (e.contador.toFloat() / e.meta.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(10.dp))
            val texto = when {
                e == null -> if (cargando) "" else stringResource(R.string.prerregistro_sin_red)
                !e.empezo -> stringResource(R.string.prerregistro_no_ha_empezado)
                e.abierto && e.alargado -> stringResource(R.string.prerregistro_alargado, e.meta)
                e.abierto -> {
                    val dias = e.fin?.let { runCatching { Duration.between(Instant.now(), Instant.parse(it)).toHours() }.getOrNull() }
                        ?.let { ((it + 23) / 24).toInt().coerceAtLeast(0) } ?: 0
                    if (dias <= 1) stringResource(R.string.prerregistro_ultimo_dia) else stringResource(R.string.prerregistro_quedan_dias, dias)
                }
                else -> stringResource(R.string.prerregistro_cerrado)
            }
            if (texto.isNotEmpty()) Text(texto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Formulario(onBases: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var mayor by remember { mutableStateOf(false) }
    var acepta by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var enviando by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it.take(60); error = null },
            label = { Text(stringResource(R.string.prerregistro_nombre)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = correo,
            onValueChange = { correo = it.take(120); error = null },
            label = { Text(stringResource(R.string.prerregistro_correo)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Casilla(mayor, stringResource(R.string.prerregistro_mayor)) { mayor = it; error = null }
        Casilla(acepta, stringResource(R.string.prerregistro_acepto)) { acepta = it; error = null }
        TextButton(onClick = onBases) { Text(stringResource(R.string.prerregistro_ver_bases)) }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                val falta = Prerregistro.validar(nombre, correo, mayor, acepta)
                if (falta != null) {
                    error = when (falta) {
                        Prerregistro.Falta.NOMBRE -> R.string.prerregistro_falta_nombre
                        Prerregistro.Falta.CORREO -> R.string.prerregistro_falta_correo
                        Prerregistro.Falta.EDAD -> R.string.prerregistro_falta_edad
                        Prerregistro.Falta.BASES -> R.string.prerregistro_falta_bases
                    }
                    return@Button
                }
                enviando = true
                scope.launch {
                    val r = Prerregistro.registrar(context, nombre, correo)
                    enviando = false
                    error = when {
                        r == null -> R.string.prerregistro_sin_red
                        r.ok -> null
                        r.error == "cerrado" -> R.string.prerregistro_error_cerrado
                        r.error == "nombre" -> R.string.prerregistro_falta_nombre
                        r.error == "correo" -> R.string.prerregistro_falta_correo
                        else -> R.string.prerregistro_sin_red
                    }
                }
            },
            enabled = !enviando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.prerregistro_boton), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Casilla(marcada: Boolean, texto: String, alCambiar: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { alCambiar(!marcada) },
    ) {
        Checkbox(checked = marcada, onCheckedChange = alCambiar)
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Dentro(datos: Prerregistro.Datos, onInvitar: () -> Unit, onSalir: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.prerregistro_dentro_titulo),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.prerregistro_dentro_texto, datos.nombre, datos.correo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onInvitar, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.prerregistro_invitar))
            }
            OutlinedButton(onClick = onSalir, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.prerregistro_salir))
            }
        }
    }
}
