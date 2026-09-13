package com.cglabs.lifemusic.concurso

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.cglabs.lifemusic.LocalDatabase
import com.cglabs.lifemusic.LocalPlayerAwareWindowInsets
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.ui.component.IconButton
import com.cglabs.lifemusic.ui.utils.backToMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Pantalla del reto: premio y cuenta atras, tu semana (si participas),
 * ranking, reglas y la salida. Todo lo que se ve del resto de participantes es
 * apodo y minutos; nombre y correo no salen del servidor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcursoScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    var participacion by remember { mutableStateOf(ConcursoRepository.participacion(context)) }
    var porDia by remember { mutableStateOf<Map<LocalDate, Int>>(emptyMap()) }
    var ranking by remember { mutableStateOf<List<PuestoRanking>?>(null) }
    var cargandoRanking by remember { mutableStateOf(true) }
    var mostrarRegistro by remember { mutableStateOf(false) }
    var confirmarAbandono by remember { mutableStateOf(false) }

    suspend fun refrescar() {
        porDia = Concurso.minutosPorDia(database)
        if (participacion != null) ConcursoRepository.sincronizar(context, database, forzar = true)
        participacion = ConcursoRepository.participacion(context)
        ranking = ConcursoApi.ranking()
        cargandoRanking = false
    }

    LaunchedEffect(Unit) { refrescar() }

    val hoy = Concurso.hoy()
    val totalLocal = Concurso.totalContado(porDia)
    val minutosHoy = Concurso.minutosHoy(porDia)
    val minutosMostrados = participacion?.minutosServidor?.takeIf { it >= 0 } ?: totalLocal

    if (confirmarAbandono) {
        AlertDialog(
            onDismissRequest = { confirmarAbandono = false },
            title = { Text(stringResource(R.string.concurso_abandonar)) },
            text = { Text(stringResource(R.string.concurso_abandonar_confirmacion)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarAbandono = false
                    scope.launch {
                        val ok = ConcursoRepository.abandonar(context)
                        if (ok) {
                            participacion = null
                            Toast.makeText(context, context.getString(R.string.concurso_abandonado), Toast.LENGTH_SHORT).show()
                            ranking = ConcursoApi.ranking()
                        } else {
                            Toast.makeText(context, context.getString(R.string.concurso_sin_red), Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(stringResource(R.string.concurso_abandonar)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarAbandono = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            ),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Cabecera(hoy = hoy, participantes = participacion?.participantes ?: ranking?.size ?: 0)
            }

            item { Spacer(Modifier.height(16.dp)) }

            val p = participacion
            if (p != null) {
                item {
                    TuSemana(
                        apodo = p.apodo,
                        minutos = minutosMostrados,
                        minutosHoy = minutosHoy,
                        puesto = p.puesto,
                        participantes = p.participantes,
                        terminado = Concurso.terminado(hoy),
                    )
                }
            } else if (!Concurso.terminado(hoy)) {
                item {
                    if (mostrarRegistro) {
                        FormularioRegistro(
                            onCancelar = { mostrarRegistro = false },
                            onRegistrado = {
                                mostrarRegistro = false
                                scope.launch { refrescar() }
                            },
                        )
                    } else {
                        LlamadaAParticipar(
                            minutosYaContados = totalLocal,
                            onParticipar = { mostrarRegistro = true },
                            onNo = {
                                scope.launch { ConcursoRepository.marcarRechazado(context, true) }
                                navController.navigateUp()
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            item {
                TituloSeccion(stringResource(R.string.concurso_ranking))
            }
            if (cargandoRanking) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (ranking == null) {
                item { Nota(stringResource(R.string.concurso_sin_red)) }
            } else if (ranking!!.isEmpty()) {
                item { Nota(stringResource(R.string.concurso_ranking_vacio)) }
            } else {
                items(ranking!!, key = { it.puesto }) { fila ->
                    FilaRanking(fila, esMio = fila.apodo.equals(p?.apodo, ignoreCase = true))
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
            item { TituloSeccion(stringResource(R.string.concurso_reglas)) }
            item { Reglas() }

            item { Spacer(Modifier.height(24.dp)) }
            item { Nota(stringResource(R.string.concurso_privacidad)) }

            if (p != null) {
                item {
                    TextButton(
                        onClick = { confirmarAbandono = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(stringResource(R.string.concurso_abandonar), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }

        TopAppBar(
            title = { Text(stringResource(R.string.concurso_titulo)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

/* ------------------------------------------------------------- cabecera */

@Composable
private fun Cabecera(hoy: LocalDate, participantes: Int) {
    val estado = when {
        Concurso.porEmpezar(hoy) -> stringResource(R.string.concurso_empieza, Concurso.INICIO.dayOfMonth)
        Concurso.terminado(hoy) -> stringResource(R.string.concurso_terminado)
        else -> {
            val d = Concurso.diasRestantes(hoy)
            if (d == 1) stringResource(R.string.concurso_ultimo_dia) else stringResource(R.string.concurso_quedan_dias, d)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.trophy),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.concurso_gana, Concurso.PREMIO),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.concurso_fechas,
                        Concurso.INICIO.dayOfMonth, Concurso.FIN.dayOfMonth,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Text(
                    text = if (participantes > 0) "$estado · " + stringResource(R.string.concurso_participantes, participantes) else estado,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/* ------------------------------------------------------------ tu semana */

@Composable
private fun TuSemana(
    apodo: String,
    minutos: Int,
    minutosHoy: Int,
    puesto: Int,
    participantes: Int,
    terminado: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capa = rememberGraphicsLayer()
    var compartiendo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // La tarjeta se dibuja en una capa aparte para poder exportarla tal cual.
        Box(
            modifier = Modifier.drawWithContent {
                capa.record { this@drawWithContent.drawContent() }
                drawLayer(capa)
            }
        ) {
            TarjetaConcurso(apodo, minutos, minutosHoy, puesto, participantes, terminado)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (compartiendo) return@Button
                compartiendo = true
                scope.launch {
                    try {
                        val bitmap = capa.toImageBitmap().asAndroidBitmap()
                        val uri = withContext(Dispatchers.IO) {
                            val carpeta = File(context.cacheDir, "compartir").apply { mkdirs() }
                            val fichero = File(carpeta, "reto-life-music.png")
                            fichero.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", fichero)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.concurso_compartir_texto, Concurso.formatear(minutos)))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.concurso_compartir)))
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                    } finally {
                        compartiendo = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
        ) {
            Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.concurso_compartir), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * La tarjeta compartible: tu apodo, tus minutos, tu puesto, y la marca. Se
 * pinta con colores propios y no del tema, para que salga igual en cualquier
 * telefono y se reconozca en Instagram como de Life Music.
 */
@Composable
private fun TarjetaConcurso(
    apodo: String,
    minutos: Int,
    minutosHoy: Int,
    puesto: Int,
    participantes: Int,
    terminado: Boolean,
) {
    val fondo = Brush.linearGradient(listOf(Color(0xFF1B1B2F), Color(0xFF3A1C4A)))
    val acento = Color(0xFFF8BBD0)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .background(fondo)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.trophy), contentDescription = null, tint = acento, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.concurso_titulo).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = acento,
                    letterSpacing = 1.6.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = apodo,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = Concurso.formatear(minutos),
                style = MaterialTheme.typography.displaySmall,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                color = acento,
                lineHeight = 48.sp,
            )
            Text(
                text = if (puesto > 0 && participantes > 0) stringResource(R.string.concurso_puesto, puesto, participantes)
                else stringResource(R.string.concurso_puesto_pendiente),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            if (!terminado) {
                Spacer(Modifier.height(8.dp))
                val fraccion = (minutosHoy.toFloat() / Concurso.TOPE_MIN_DIA).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraccion },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = acento,
                    trackColor = Color.White.copy(alpha = 0.18f),
                )
                Text(
                    text = stringResource(
                        R.string.concurso_hoy,
                        Concurso.formatear(minOf(minutosHoy, Concurso.TOPE_MIN_DIA)),
                        Concurso.TOPE_MIN_DIA / 60,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Life Music · CG LABS",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

/* -------------------------------------------------------- participar */

@Composable
private fun LlamadaAParticipar(minutosYaContados: Int, onParticipar: () -> Unit, onNo: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.concurso_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp,
        )
        if (minutosYaContados > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.concurso_ya_llevas, Concurso.formatear(minutosYaContados)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onNo,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(50),
            ) { Text(stringResource(R.string.concurso_no_participar)) }
            Button(
                onClick = onParticipar,
                modifier = Modifier
                    .weight(1.2f)
                    .height(56.dp),
                shape = RoundedCornerShape(50),
            ) { Text(stringResource(R.string.concurso_participar), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun FormularioRegistro(onCancelar: () -> Unit, onRegistrado: () -> Unit) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    var apodo by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    val correoValido = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(correo.trim())
    val listo = apodo.trim().length in 2..24 && nombre.trim().length in 2..80 && correoValido && !enviando

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.concurso_registro_titulo),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = apodo,
                onValueChange = { apodo = it.take(24); error = null },
                label = { Text(stringResource(R.string.concurso_apodo)) },
                supportingText = { Text(stringResource(R.string.concurso_apodo_ayuda)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it.take(80) },
                label = { Text(stringResource(R.string.concurso_nombre)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it.take(120) },
                label = { Text(stringResource(R.string.concurso_correo)) },
                singleLine = true,
                isError = correo.isNotEmpty() && !correoValido,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.concurso_consentimiento),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancelar,
                    enabled = !enviando,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(50),
                ) { Text(stringResource(android.R.string.cancel)) }
                Button(
                    onClick = {
                        enviando = true
                        error = null
                        scope.launch {
                            when (ConcursoRepository.registrar(context, database, apodo, nombre, correo)) {
                                ResultadoRegistro.Ok -> onRegistrado()
                                ResultadoRegistro.ApodoEnUso -> error = context.getString(R.string.concurso_error_apodo)
                                ResultadoRegistro.Cerrado -> error = context.getString(R.string.concurso_error_cerrado)
                                ResultadoRegistro.SinRed -> error = context.getString(R.string.concurso_sin_red)
                                ResultadoRegistro.Datos -> error = context.getString(R.string.concurso_error_datos)
                            }
                            enviando = false
                        }
                    },
                    enabled = listo,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    if (enviando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.concurso_participar), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------- ranking */

@Composable
private fun FilaRanking(fila: PuestoRanking, esMio: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (esMio) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "${fila.puesto}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (fila.puesto) {
                    1 -> Color(0xFFD4A017)
                    2 -> Color(0xFF9E9E9E)
                    3 -> Color(0xFFB87333)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(28.dp),
            )
            Text(
                text = fila.apodo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (esMio) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = Concurso.formatear(fila.minutos),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ------------------------------------------------------------ reglas */

@Composable
private fun Reglas() {
    val reglas = listOf(
        stringResource(R.string.concurso_regla_1, Concurso.INICIO.dayOfMonth, Concurso.FIN.dayOfMonth),
        stringResource(R.string.concurso_regla_2),
        stringResource(R.string.concurso_regla_3, Concurso.TOPE_MIN_DIA / 60),
        stringResource(R.string.concurso_regla_4),
        stringResource(R.string.concurso_regla_5),
        stringResource(R.string.concurso_regla_6),
    )
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        reglas.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = r,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Nota(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
