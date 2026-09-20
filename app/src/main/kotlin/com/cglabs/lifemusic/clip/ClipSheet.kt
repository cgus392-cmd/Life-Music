package com.cglabs.lifemusic.clip

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.ClipAudioKey
import com.cglabs.lifemusic.constants.ClipCaratulaLateKey
import com.cglabs.lifemusic.constants.ClipDuracionDefault
import com.cglabs.lifemusic.constants.ClipDuracionKey
import com.cglabs.lifemusic.constants.ClipDuracionMaxima
import com.cglabs.lifemusic.constants.ClipModo
import com.cglabs.lifemusic.constants.ClipModoKey
import com.cglabs.lifemusic.constants.ClipFondoLateKey
import com.cglabs.lifemusic.constants.ClipGrabacionAudioKey
import com.cglabs.lifemusic.constants.ClipLetraKey
import com.cglabs.lifemusic.constants.ClipVerticalKey
import com.cglabs.lifemusic.lyrics.LyricsUtils
import com.cglabs.lifemusic.utils.rememberEnumPreference
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Hoja del clip: duracion, formato, letra, audio, latidos; barra de progreso
 * mientras se genera; y al final compartir o guardar en la galeria. Vive en
 * MainActivity y se abre desde ClipLanzador (menu del reproductor, modo
 * ambiente). Cada eleccion se recuerda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipSheet() {
    val abierto by ClipLanzador.abierto.collectAsState()
    val rango by ClipLanzador.rango.collectAsState()
    val desdeAmbiente by ClipLanzador.desdeAmbiente.collectAsState()
    if (!abierto) return
    val conexion = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cancion by conexion.mediaMetadata.collectAsState()
    val letraCruda by conexion.currentLyrics.collectAsState(initial = null)
    val letra = remember(letraCruda?.id, cancion?.id) {
        letraCruda?.takeIf { it.id == cancion?.id }?.lyrics?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LyricsUtils.parseLyrics(it) }.getOrNull() }
            ?.takeIf { lista -> lista.any { it.time > 0L } }
    }
    val hayLetra = !letra.isNullOrEmpty()

    var duracion by rememberPreference(ClipDuracionKey, defaultValue = ClipDuracionDefault)
    var vertical by rememberPreference(ClipVerticalKey, defaultValue = true)
    var conLetra by rememberPreference(ClipLetraKey, defaultValue = true)
    var conAudio by rememberPreference(ClipAudioKey, defaultValue = false)
    var grabacionConAudio by rememberPreference(ClipGrabacionAudioKey, defaultValue = true)
    var caratulaLate by rememberPreference(ClipCaratulaLateKey, defaultValue = false)
    var fondoLate by rememberPreference(ClipFondoLateKey, defaultValue = true)
    var modoElegido by rememberEnumPreference(ClipModoKey, defaultValue = ClipModo.TARJETA)
    // La grabacion solo tiene sentido con el modo ambiente en pantalla.
    val modo = if (desdeAmbiente) modoElegido else ClipModo.TARJETA

    var trabajo by remember { mutableStateOf<Job?>(null) }
    var progreso by remember { mutableStateOf<RenderizadorDeClip.Etapa?>(null) }
    // Una grabacion recien terminada llega ya hecha: la hoja abre en «listo».
    var resultado by remember { mutableStateOf<File?>(ClipLanzador.resultado.value) }
    var fallo by remember { mutableStateOf(false) }

    fun cerrar() {
        trabajo?.cancel()
        ClipLanzador.cerrar()
    }

    ModalBottomSheet(onDismissRequest = { cerrar() }, sheetState = estado) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // En horizontal no cabe entera: sin esto el boton de abajo quedaba fuera.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(stringResource(R.string.clip_titulo), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = cancion?.let { "${it.title} · ${it.artists.joinToString { a -> a.name }}" } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            val generando = trabajo != null && resultado == null

            // Con tramo de la letra, la duracion es la del tramo (5-60 s); sin el,
            // chips y un consejo de como elegirlo.
            val tramo = rango
            val duracionTramo = tramo?.let { ((it.second - it.first) / 1000.0).let { s -> Math.ceil(s).toInt() }.coerceIn(5, ClipDuracionMaxima) }
            if (resultado == null) {
                // Desde el modo ambiente hay dos maneras: la tarjeta dibujada o
                // grabar lo que se ve. Desde el menu del reproductor, solo la tarjeta.
                if (desdeAmbiente) {
                    Etiqueta(stringResource(R.string.clip_modo))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = modo == ClipModo.TARJETA, onClick = { modoElegido = ClipModo.TARJETA }, label = { Text(stringResource(R.string.clip_modo_tarjeta)) }, enabled = !generando)
                        FilterChip(selected = modo == ClipModo.AMBIENTE, onClick = { modoElegido = ClipModo.AMBIENTE }, label = { Text(stringResource(R.string.clip_modo_ambiente)) }, enabled = !generando)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (modo == ClipModo.AMBIENTE) {
                    Text(stringResource(R.string.clip_modo_ambiente_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Etiqueta(stringResource(R.string.clip_duracion_maxima))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in listOf(10, 15, 30, 60)) {
                            FilterChip(selected = duracion == s, onClick = { duracion = s }, label = { Text("$s s") })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilaInterruptor(
                        titulo = stringResource(R.string.clip_con_audio),
                        detalle = stringResource(R.string.clip_con_audio_desc),
                        activo = grabacionConAudio, habilitado = true,
                    ) { grabacionConAudio = it }
                    Spacer(Modifier.height(16.dp))
                } else {
                    if (tramo != null && duracionTramo != null) {
                        Etiqueta(stringResource(R.string.clip_tramo))
                        Text(
                            text = stringResource(R.string.clip_tramo_valor, tiempo(tramo.first), tiempo(tramo.first + duracionTramo * 1000L), duracionTramo),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        Etiqueta(stringResource(R.string.clip_duracion))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (s in listOf(10, 15, 30, 60)) {
                                FilterChip(selected = duracion == s, onClick = { duracion = s }, label = { Text("$s s") }, enabled = !generando)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.clip_consejo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    Etiqueta(stringResource(R.string.clip_formato))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = vertical, onClick = { vertical = true }, label = { Text(stringResource(R.string.clip_vertical)) }, enabled = !generando)
                        FilterChip(selected = !vertical, onClick = { vertical = false }, label = { Text(stringResource(R.string.clip_horizontal)) }, enabled = !generando)
                    }
                    Spacer(Modifier.height(8.dp))
                    FilaInterruptor(
                        titulo = stringResource(R.string.clip_con_letra),
                        detalle = if (hayLetra) null else stringResource(R.string.clip_sin_letra),
                        activo = conLetra && hayLetra, habilitado = hayLetra && !generando,
                    ) { conLetra = it }
                    FilaInterruptor(
                        titulo = stringResource(R.string.clip_con_audio),
                        detalle = stringResource(R.string.clip_con_audio_desc),
                        activo = conAudio, habilitado = !generando,
                    ) { conAudio = it }
                    FilaInterruptor(
                        titulo = stringResource(R.string.clip_fondo_late),
                        detalle = null, activo = fondoLate, habilitado = !generando,
                    ) { fondoLate = it }
                    FilaInterruptor(
                        titulo = stringResource(R.string.clip_caratula_late),
                        detalle = null, activo = caratulaLate, habilitado = !generando,
                    ) { caratulaLate = it }
                    Spacer(Modifier.height(16.dp))
                }
            }

            val etapa = progreso
            if (generando && etapa != null) {
                LinearProgressIndicator(progress = { etapa.fraccion }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(stringResource(etapa.texto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { trabajo?.cancel(); trabajo = null; progreso = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(android.R.string.cancel))
                }
            } else if (resultado != null) {
                Text(stringResource(R.string.clip_listo), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { compartir(context, resultado!!) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clip_compartir))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    val ok = guardarEnGaleria(context, resultado!!)
                    Toast.makeText(context, context.getString(if (ok) R.string.clip_guardado else R.string.clip_fallo), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clip_guardar))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { resultado = null; trabajo = null; progreso = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clip_otro))
                }
            } else {
                if (fallo) {
                    Text(stringResource(R.string.clip_fallo), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        val actual = cancion ?: return@Button
                        fallo = false
                        if (modo == ClipModo.AMBIENTE) {
                            // Se cierra la hoja y la grabacion sigue sola (cuenta atras,
                            // REC en un Popup); al terminar vuelve a abrirla con el clip.
                            val activity = context.actividad() ?: return@Button
                            ClipLanzador.cerrar()
                            GrabadorDeAmbiente.iniciar(
                                activity = activity,
                                service = conexion.service,
                                cancion = actual,
                                duracionSeg = duracion.coerceIn(5, ClipDuracionMaxima),
                                conAudio = grabacionConAudio,
                            )
                            return@Button
                        }
                        val opciones = ClipOpciones(
                            duracionSeg = duracionTramo ?: duracion,
                            vertical = vertical,
                            conLetra = conLetra && hayLetra,
                            conAudio = conAudio,
                            caratulaLate = caratulaLate,
                            fondoLate = fondoLate,
                            desdeMs = tramo?.first ?: conexion.player.currentPosition.coerceAtLeast(0L),
                        )
                        progreso = RenderizadorDeClip.Etapa(0f, R.string.clip_etapa_caratula)
                        trabajo = scope.launch {
                            val fichero = RenderizadorDeClip.crear(
                                context = context,
                                service = conexion.service,
                                cancion = actual,
                                letra = letra,
                                opciones = opciones,
                                progreso = { progreso = it },
                                cancelado = { !isActive },
                            )
                            if (fichero != null) resultado = fichero else { fallo = true; trabajo = null; progreso = null }
                        }
                    },
                    enabled = cancion != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (modo == ClipModo.AMBIENTE) R.string.clip_grabar else R.string.clip_crear))
                }
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun FilaInterruptor(titulo: String, detalle: String?, activo: Boolean, habilitado: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            if (detalle != null) {
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = activo, onCheckedChange = onCambio, enabled = habilitado)
    }
}

private fun compartir(context: android.content.Context, fichero: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", fichero)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.clip_compartir)))
}

/** Copia el MP4 a Películas/Life Music por MediaStore (sin permisos en Android 10+). */
private fun guardarEnGaleria(context: android.content.Context, fichero: File): Boolean = runCatching {
    val nombre = "life-music-clip-${System.currentTimeMillis()}.mp4"
    val valores = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, nombre)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Life Music")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val destino = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores) ?: return false
    resolver.openOutputStream(destino)?.use { out -> fichero.inputStream().use { it.copyTo(out) } } ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        valores.clear(); valores.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(destino, valores, null, null)
    }
    true
}.getOrDefault(false)

/** «1:02» a partir de milisegundos. */
private fun tiempo(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
