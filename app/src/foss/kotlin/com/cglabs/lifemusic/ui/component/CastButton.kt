package com.cglabs.lifemusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.cast.DescubridorCast
import com.cglabs.lifemusic.playback.CastConnectionHandler
import com.cglabs.lifemusic.playback.CastDeviceKind

/**
 * Boton «Transmitir» de la variante foss (Cast por protocolo abierto). Como
 * fila de menu o como icono. Abre la hoja de aparatos; si ya se transmite,
 * la de la sesion (volumen y desconectar).
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
    asMenuItem: Boolean = false,
) {
    val conexion = LocalPlayerConnection.current ?: return
    val handler = remember(conexion) { runCatching { conexion.service.castConnectionHandler }.getOrNull() } ?: return
    val transmitiendo by handler.isCasting.collectAsState()
    val nombre by handler.castDeviceName.collectAsState()
    val tipo by handler.deviceType.collectAsState()
    var hoja by remember { mutableStateOf(false) }

    val icono = if (transmitiendo) iconoConectado(tipo) else R.drawable.cast
    val color = if (transmitiendo) MaterialTheme.colorScheme.primary else tintColor

    if (asMenuItem) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { hoja = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icono), contentDescription = null, tint = if (transmitiendo) color else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (transmitiendo) stringResource(R.string.cast_sonando_en, nombre ?: "") else stringResource(R.string.cast_transmitir),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { hoja = true },
        ) {
            Icon(painterResource(icono), contentDescription = stringResource(R.string.cast_transmitir), tint = color, modifier = Modifier.size(24.dp))
        }
    }

    if (hoja) HojaDeCast(handler = handler, onCerrar = { hoja = false })
}

private fun iconoConectado(tipo: CastDeviceKind): Int = when (tipo) {
    CastDeviceKind.TV -> R.drawable.cast_tv_connected
    CastDeviceKind.SPEAKER -> R.drawable.cast_speaker_connected
    else -> R.drawable.cast_connected
}

private fun iconoDe(aparato: DescubridorCast.Aparato): Int = when (CastDeviceKind.fromName(aparato.nombre, aparato.modelo)) {
    CastDeviceKind.TV -> R.drawable.cast_tv
    CastDeviceKind.SPEAKER -> R.drawable.cast_speaker
    else -> R.drawable.cast
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaDeCast(handler: CastConnectionHandler, onCerrar: () -> Unit) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val transmitiendo by handler.isCasting.collectAsState()
    val conectando by handler.isConnecting.collectAsState()
    val nombre by handler.castDeviceName.collectAsState()
    val aparatos by handler.aparatos.collectAsState()
    val buscando by handler.descubridor.buscando.collectAsState()
    val volumen by handler.castVolume.collectAsState()

    // La hoja pide busqueda mientras esta abierta (la barra de Inicio la pide
    // aparte; el descubridor cuenta a los dos y apaga la radio con el ultimo).
    DisposableEffect(Unit) {
        val pedida = !transmitiendo
        if (pedida) handler.buscar()
        onDispose { if (pedida) handler.dejarDeBuscar() }
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = estado) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            if (transmitiendo) {
                Text(stringResource(R.string.cast_sonando_en, nombre ?: ""), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.cast_mando_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.cast_volumen), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var arrastre by remember(volumen) { mutableFloatStateOf(volumen) }
                Slider(
                    value = arrastre,
                    onValueChange = { arrastre = it },
                    onValueChangeFinished = { handler.setVolume(arrastre) },
                    valueRange = 0f..1f,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { handler.disconnect(); onCerrar() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cast_desconectar))
                }
            } else {
                Text(stringResource(R.string.cast_transmitir), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.cast_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (aparatos.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        if (buscando) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            stringResource(if (buscando) R.string.cast_buscando else R.string.cast_sin_aparatos),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    aparatos.forEach { aparato ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !conectando) { handler.conectar(aparato) }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                        ) {
                            Icon(painterResource(iconoDe(aparato)), contentDescription = null, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(aparato.nombre, style = MaterialTheme.typography.bodyLarge)
                                aparato.modelo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (conectando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.cast_nota_red), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Para los probadores: copia las ultimas lineas del registro de Cast al
                // portapapeles (lo pegan por WhatsApp). Solo lo que el usuario decide copiar.
                val context = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Life Music Cast", com.cglabs.lifemusic.cast.DiagnosticoCast.texto()))
                    android.widget.Toast.makeText(context, R.string.cast_diagnostico_copiado, android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.cast_diagnostico)) }
            }
        }
    }
}
