package com.cglabs.lifemusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
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
                // Temas del TV: solo con nuestro receptor (el reproductor por defecto no los entiende).
                val propio by handler.receptorPropio.collectAsState()
                val temaActual by handler.tema.collectAsState()
                if (propio) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.cast_tema_titulo), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TarjetaDeTema(CastConnectionHandler.TEMA_AMBIENTE, R.string.cast_tema_ambiente, temaActual, Modifier.weight(1f), handler::ponerTema)
                        TarjetaDeTema(CastConnectionHandler.TEMA_CRISTAL, R.string.cast_tema_cristal, temaActual, Modifier.weight(1f), handler::ponerTema)
                        TarjetaDeTema(CastConnectionHandler.TEMA_ESCENARIO, R.string.cast_tema_escenario, temaActual, Modifier.weight(1f), handler::ponerTema)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TarjetaDeTema(CastConnectionHandler.TEMA_VINILO, R.string.cast_tema_vinilo, temaActual, Modifier.weight(1f), handler::ponerTema)
                        TarjetaDeTema(CastConnectionHandler.TEMA_GALERIA, R.string.cast_tema_galeria, temaActual, Modifier.weight(1f), handler::ponerTema)
                        TarjetaDeTema(CastConnectionHandler.TEMA_NOCTURNO, R.string.cast_tema_nocturno, temaActual, Modifier.weight(1f), handler::ponerTema)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Temas pro: paisaje o foto real de fondo (ver web/cast/temas/).
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TarjetaDeTema(CastConnectionHandler.TEMA_ATARDECER, R.string.cast_tema_atardecer, temaActual, Modifier.weight(1f), handler::ponerTema, pro = true)
                        TarjetaDeTema(CastConnectionHandler.TEMA_TOCADISCOS, R.string.cast_tema_tocadiscos, temaActual, Modifier.weight(1f), handler::ponerTema, pro = true)
                        Spacer(Modifier.weight(1f))
                    }
                    if (temaActual == CastConnectionHandler.TEMA_TOCADISCOS) {
                        // Mini menu: cual de las dos fotos del tocadiscos.
                        val version by handler.versionTocadiscos.collectAsState()
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.cast_tema_version), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            for (v in 1..2) {
                                androidx.compose.material3.FilterChip(
                                    selected = version == v,
                                    onClick = { handler.ponerVersionTocadiscos(v) },
                                    label = { Text("V$v", fontWeight = FontWeight.SemiBold) },
                                )
                            }
                        }
                    }
                    if (temaActual == CastConnectionHandler.TEMA_ATARDECER) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.cast_tema_atardecer_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (temaActual == CastConnectionHandler.TEMA_CRISTAL) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.cast_tema_cristal_nota), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { handler.disconnect(); onCerrar() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cast_desconectar))
                }
            } else {
                Text(stringResource(R.string.cast_transmitir), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.cast_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                val renderizadores by handler.renderizadores.collectAsState()
                if (aparatos.isEmpty() && renderizadores.isEmpty()) {
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
                    // Reproductores DLNA: el mismo aparato puede salir aqui y arriba (un TV
                    // con Chromecast y DLNA); la etiqueta dice por cual se transmite.
                    renderizadores.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = !conectando) { handler.conectarDlna(r) }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                        ) {
                            Icon(painterResource(R.drawable.cast_tv), contentDescription = null, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.nombre, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    listOfNotNull("DLNA", r.fabricante, r.modelo).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                // TV con navegador (lifemusic.pages.dev/tv): los ya enlazados y el enlace por codigo.
                SeccionTvWeb(handler = handler, conectando = conectando, onConectado = onCerrar)
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

/**
 * TV que abren lifemusic.pages.dev/tv en su navegador: los ya enlazados se tocan
 * y entran sin codigo; «Enlazar un TV con código» abre el dialogo del codigo.
 */
@Composable
private fun SeccionTvWeb(handler: CastConnectionHandler, conectando: Boolean, onConectado: () -> Unit) {
    val tvs by handler.tvsWeb.collectAsState()
    var dialogo by remember { mutableStateOf(false) }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.cast_web_titulo), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    tvs.forEach { tv ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = !conectando) { handler.conectarTvWeb(tv); onConectado() }
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(painterResource(R.drawable.cast_tv), contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.nombre, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.cast_web_recordado), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.IconButton(onClick = { handler.olvidarTvWeb(tv) }) {
                Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.cast_web_olvidar), modifier = Modifier.size(20.dp))
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !conectando) { dialogo = true }
            .padding(horizontal = 8.dp, vertical = 14.dp),
    ) {
        Icon(painterResource(R.drawable.link), contentDescription = null, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.cast_web_enlazar), style = MaterialTheme.typography.bodyLarge)
    }
    if (dialogo) DialogoCodigoTv(handler = handler, codigoInicial = "", onCerrar = { dialogo = false }, onEnlazado = onConectado)
}

/** Pide el codigo de 6 caracteres que muestra el TV y enlaza con el. */
@Composable
internal fun DialogoCodigoTv(
    handler: CastConnectionHandler,
    codigoInicial: String,
    onCerrar: () -> Unit,
    onEnlazado: () -> Unit,
) {
    var texto by remember { mutableStateOf(codigoInicial) }
    var error by remember { mutableStateOf<String?>(null) }
    var enlazando by remember { mutableStateOf(false) }
    fun enlazar() {
        if (texto.length != 6 || enlazando) return
        enlazando = true
        handler.enlazarTvWeb(texto) { e ->
            enlazando = false
            if (e == null) { onCerrar(); onEnlazado() } else error = e
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!enlazando) onCerrar() },
        icon = { Icon(painterResource(R.drawable.cast_tv), contentDescription = null) },
        title = { Text(stringResource(R.string.cast_web_enlazar)) },
        text = {
            Column {
                // Desde el QR, el codigo ya viene puesto: solo se confirma.
                Text(
                    if (codigoInicial.length == 6) stringResource(R.string.cast_web_enlazar_con, codigoInicial.take(3) + " " + codigoInicial.drop(3))
                    else stringResource(R.string.cast_web_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = texto,
                    onValueChange = { v -> texto = v.uppercase().filter { it.isLetterOrDigit() }.take(6); error = null },
                    label = { Text(stringResource(R.string.cast_web_codigo)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { e -> { Text(e) } },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { enlazar() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { enlazar() }, enabled = texto.length == 6 && !enlazando) {
                if (enlazando) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.cast_web_conectar))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCerrar, enabled = !enlazando) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

/**
 * Una tarjeta de tema con su vista previa en miniatura (dibujada, no una
 * captura): el fondo de luces y la silueta de como queda el TV. La elegida
 * lleva borde del color principal.
 */
@Composable
private fun TarjetaDeTema(
    id: String,
    nombre: Int,
    actual: String,
    modifier: Modifier = Modifier,
    alElegir: (String) -> Unit,
    pro: Boolean = false,
) {
    val elegida = id == actual
    val forma = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(forma)
            .border(
                width = if (elegida) 2.dp else 1.dp,
                color = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = forma,
            )
            .clickable { alElegir(id) }
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(11.dp)),
            ) { vistaPrevia(id) }
            // Sello PRO: dorado, en la esquina de la miniatura.
            if (pro) {
                Text(
                    stringResource(R.string.cast_tema_pro),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFF2A1A05),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFE8A93A))))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(nombre),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (elegida) FontWeight.SemiBold else FontWeight.Normal,
            color = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.vistaPrevia(tema: String) {
    val w = size.width
    val h = size.height
    fun luz(color: Color, x: Float, y: Float, r: Float) = drawRect(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(color.copy(alpha = 0.85f), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(x * w, y * h),
            radius = r * w,
        ),
    )
    drawRect(Color(0xFF070707))
    luz(Color(0xFFEF4444), 0.15f, 0.2f, 0.75f)
    luz(Color(0xFF7C3AED), 0.9f, 0.9f, 0.7f)
    luz(Color(0xFFF59E0B), 0.55f, 0.1f, 0.45f)

    fun caja(x: Float, y: Float, bw: Float, bh: Float, color: Color, radio: Float = 0.06f) = drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(x * w, y * h),
        size = androidx.compose.ui.geometry.Size(bw * w, bh * h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radio * h, radio * h),
    )
    fun renglon(x: Float, y: Float, largo: Float, alfa: Float, grueso: Float = 0.07f) =
        caja(x, y, largo, grueso, Color.White.copy(alpha = alfa), grueso / 2)

    val caratula = Color(0xFFFFB86B)
    when (tema) {
        "cristal" -> {
            // Dos paneles de vidrio con su canto de luz.
            val vidrio = Color.White.copy(alpha = 0.16f)
            val canto = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
            caja(0.06f, 0.16f, 0.34f, 0.68f, vidrio, 0.14f)
            caja(0.44f, 0.10f, 0.50f, 0.80f, vidrio, 0.14f)
            for ((x, bw) in listOf(0.06f to 0.34f, 0.44f to 0.50f)) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = androidx.compose.ui.geometry.Offset(x * w, (if (x < 0.3f) 0.16f else 0.10f) * h),
                    size = androidx.compose.ui.geometry.Size(bw * w, (if (x < 0.3f) 0.68f else 0.80f) * h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.14f * h, 0.14f * h),
                    style = canto,
                )
            }
            caja(0.11f, 0.25f, 0.24f, 0.36f, caratula, 0.08f)
            renglon(0.11f, 0.68f, 0.2f, 0.9f)
            renglon(0.50f, 0.40f, 0.36f, 1f)
            renglon(0.50f, 0.54f, 0.30f, 0.45f)
            renglon(0.50f, 0.68f, 0.33f, 0.3f)
        }
        "escenario" -> {
            // Una linea enorme al centro y la cancion en la esquina.
            renglon(0.14f, 0.36f, 0.72f, 1f, 0.13f)
            renglon(0.30f, 0.58f, 0.40f, 0.4f, 0.06f)
            caja(0.05f, 0.76f, 0.30f, 0.16f, Color.Black.copy(alpha = 0.35f), 0.05f)
            caja(0.07f, 0.79f, 0.07f, 0.10f, caratula, 0.03f)
            renglon(0.16f, 0.81f, 0.15f, 0.8f, 0.05f)
        }
        "vinilo" -> {
            // Un disco con la caratula de etiqueta y el brazo; letra a la derecha.
            drawRect(Color(0xFFFF9640).copy(alpha = 0.10f))
            val centro = androidx.compose.ui.geometry.Offset(0.26f * w, 0.5f * h)
            drawCircle(Color(0xFF111113), radius = 0.40f * h, center = centro)
            for (k in 1..4) drawCircle(Color.White.copy(alpha = 0.06f), radius = (0.18f + 0.055f * k) * h, center = centro, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.6.dp.toPx()))
            drawCircle(caratula, radius = 0.14f * h, center = centro)
            drawCircle(Color(0xFFD8D8D8), radius = 0.025f * h, center = centro)
            drawLine(Color(0xFFD0D0D0), start = androidx.compose.ui.geometry.Offset(0.50f * w, 0.10f * h), end = androidx.compose.ui.geometry.Offset(0.44f * w, 0.66f * h), strokeWidth = 1.4.dp.toPx())
            drawCircle(Color(0xFFBDBDBD), radius = 0.06f * h, center = androidx.compose.ui.geometry.Offset(0.50f * w, 0.10f * h))
            renglon(0.60f, 0.34f, 0.32f, 0.95f)
            renglon(0.60f, 0.50f, 0.26f, 0.45f)
            renglon(0.60f, 0.66f, 0.29f, 0.3f)
        }
        "galeria" -> {
            // La caratula como un cuadro, sobre si misma difuminada.
            drawRect(Color(0xFF0B0B0B).copy(alpha = 0.55f))
            caja(0.36f, 0.10f, 0.28f, 0.50f, caratula, 0.06f)
            renglon(0.34f, 0.70f, 0.32f, 0.9f, 0.06f)
            renglon(0.40f, 0.82f, 0.20f, 0.45f, 0.045f)
        }
        "atardecer" -> {
            // Cielo de atardecer, el sol en el horizonte y su reflejo; la cancion abajo a la izquierda.
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color(0xFF3B1A10), 0.5f to Color(0xFFF2963F), 0.5f to Color(0xFF6B3417), 1f to Color(0xFF120804),
            ))
            val sol = androidx.compose.ui.geometry.Offset(0.5f * w, 0.5f * h)
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xCCFFD27A), Color.Transparent), center = sol, radius = 0.42f * h), radius = 0.42f * h, center = sol)
            drawCircle(Color(0xFFFFF1C9), radius = 0.12f * h, center = sol)
            drawRect(Color(0xFF120804), topLeft = androidx.compose.ui.geometry.Offset(0f, 0.5f * h), size = androidx.compose.ui.geometry.Size(w, 0.5f * h), alpha = 0.35f)
            for (k in 0..3) caja(0.5f - 0.05f + 0.012f * k, 0.56f + 0.07f * k, 0.1f - 0.024f * k, 0.025f, Color(0xFFFFD27A).copy(alpha = 0.7f - 0.15f * k), 0.012f)
            caja(0.06f, 0.70f, 0.08f, 0.14f, caratula, 0.03f)
            renglon(0.17f, 0.72f, 0.22f, 0.95f, 0.06f)
            renglon(0.06f, 0.90f, 0.88f, 0.55f, 0.025f)
        }
        "tocadiscos" -> {
            // Sala calida: el disco en perspectiva con la caratula de etiqueta, el brazo y la letra a la derecha.
            drawRect(Color(0xFF140B06))
            luz(Color(0xFFE8933A).copy(alpha = 0.5f), 0.78f, 0.25f, 0.45f)
            for ((bx, by) in listOf(0.66f to 0.2f, 0.74f to 0.14f, 0.84f to 0.22f)) drawCircle(Color(0xFFFFC27A).copy(alpha = 0.35f), radius = 0.05f * h, center = androidx.compose.ui.geometry.Offset(bx * w, by * h))
            fun ovalo(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) = drawOval(color, topLeft = androidx.compose.ui.geometry.Offset((cx - rx) * w, (cy - ry) * h), size = androidx.compose.ui.geometry.Size(2 * rx * w, 2 * ry * h))
            ovalo(0.27f, 0.58f, 0.27f, 0.40f, Color(0xFF6B4226))
            ovalo(0.26f, 0.55f, 0.23f, 0.34f, Color(0xFF0E0C0B))
            ovalo(0.26f, 0.55f, 0.09f, 0.13f, caratula)
            ovalo(0.26f, 0.55f, 0.012f, 0.02f, Color(0xFFDADADA))
            drawLine(Color(0xFFD9D4CC), start = androidx.compose.ui.geometry.Offset(0.50f * w, 0.12f * h), end = androidx.compose.ui.geometry.Offset(0.42f * w, 0.70f * h), strokeWidth = 1.4.dp.toPx())
            renglon(0.60f, 0.40f, 0.32f, 0.95f, 0.09f)
            renglon(0.60f, 0.58f, 0.24f, 0.4f, 0.06f)
        }
        "nocturno" -> {
            // Negro, la hora grande y la linea que suena.
            drawRect(Color.Black)
            caja(0.28f, 0.22f, 0.44f, 0.26f, Color.White.copy(alpha = 0.82f), 0.04f)
            renglon(0.38f, 0.56f, 0.24f, 0.35f, 0.045f)
            renglon(0.30f, 0.72f, 0.40f, 0.6f, 0.06f)
        }
        else -> {
            // Ambiente: caratula a la izquierda, letra a la derecha.
            caja(0.08f, 0.20f, 0.30f, 0.52f, caratula, 0.08f)
            renglon(0.08f, 0.78f, 0.22f, 0.9f)
            renglon(0.48f, 0.30f, 0.42f, 1f)
            renglon(0.48f, 0.46f, 0.36f, 0.45f)
            renglon(0.48f, 0.62f, 0.40f, 0.3f)
        }
    }
}
