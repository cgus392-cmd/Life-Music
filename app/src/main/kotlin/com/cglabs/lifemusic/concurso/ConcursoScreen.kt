package com.cglabs.lifemusic.concurso

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.playback.MusicService
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.ui.component.IconButton
import com.cglabs.lifemusic.ui.screens.Escalonado
import com.cglabs.lifemusic.ui.screens.recordarSiAnimar
import com.cglabs.lifemusic.ui.utils.backToMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Verde del reto: el de la imagen de los AirPods, para que todo case con ella. */
private val VERDE_RETO = Color(0xFF34D399)
private val NEGRO_RETO = Color(0xFF07100C)

/**
 * Pantalla del reto: premio y cuenta atras, tu semana (si participas),
 * ranking, reglas y la salida. Todo lo que se ve del resto de participantes es
 * apodo y minutos; nombre y correo no salen del servidor.
 *
 * Se mueve a proposito: es una campana, no un ajuste. Entrada escalonada de
 * cada bloque, minutos que cuentan desde cero, barras que suben, imagen con
 * zoom lentisimo, filas del ranking una a una con la tuya latiendo, y confeti
 * al inscribirse. Todo respeta el ajuste de animaciones del sistema.
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
    val animar = recordarSiAnimar()
    val conexion = LocalPlayerConnection.current
    // Lo que cuenta el servicio en vivo: base + segundos de la cancion en curso.
    val progreso = conexion?.progresoReto?.collectAsState()?.value ?: MusicService.ProgresoReto()

    var participacion by remember { mutableStateOf(ConcursoRepository.participacion(context)) }
    var porDia by remember { mutableStateOf<Map<LocalDate, Int>>(emptyMap()) }
    var ranking by remember { mutableStateOf<List<PuestoRanking>?>(null) }
    var cargandoRanking by remember { mutableStateOf(true) }
    var mostrarRegistro by remember { mutableStateOf(false) }
    var confirmarAbandono by remember { mutableStateOf(false) }
    var confeti by remember { mutableStateOf(false) }
    var refrescando by remember { mutableStateOf(false) }

    suspend fun refrescar() {
        refrescando = true
        conexion?.service?.refrescarProgresoReto()
        porDia = Concurso.minutosPorDia(database)
        if (participacion != null) ConcursoRepository.sincronizar(context, database, forzar = true)
        participacion = ConcursoRepository.participacion(context)
        ranking = ConcursoApi.ranking()
        cargandoRanking = false
        refrescando = false
    }

    LaunchedEffect(Unit) { refrescar() }
    // Mientras la pantalla esta abierta, la grafica se relee sola cada medio minuto.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            porDia = Concurso.minutosPorDia(database)
        }
    }

    val hoy = Concurso.hoy()
    val terminado = Concurso.terminado(hoy)
    val totalLocal = Concurso.totalContado(porDia)
    val minutosServidor = participacion?.minutosServidor?.takeIf { it >= 0 }
    // En vivo: lo que ya cuenta la base mas la cancion que suena ahora.
    // Terminado el reto, la cifra es la que confirmo el servidor (con tope y
    // bonus) y ya no se mueve: lo que suene desde entonces no cuenta.
    val enCursoMin = if (terminado) 0 else progreso.segundosEnCurso / 60
    val minutosVivos = if (terminado) (minutosServidor ?: totalLocal)
        else maxOf(totalLocal, progreso.minutosTotal) + enCursoMin
    val minutosHoy = maxOf(Concurso.minutosHoy(porDia), progreso.minutosHoy) + enCursoMin

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
        PullToRefreshBox(
            isRefreshing = refrescando,
            onRefresh = { scope.launch { refrescar() } },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        ) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Escalonado(0, animar) {
                    Cabecera(hoy = hoy, participantes = participacion?.participantes ?: ranking?.size ?: 0, animar = animar)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            val p = participacion
            if (p != null) {
                item {
                    Escalonado(1, animar) {
                        TuSemana(
                            apodo = p.apodo,
                            minutos = minutosVivos,
                            minutosHoy = minutosHoy,
                            minutosServidor = minutosServidor,
                            segundosEnCurso = if (progreso.reproduciendo && !terminado) progreso.segundosEnCurso else 0,
                            porDia = porDia,
                            puesto = p.puesto,
                            participantes = p.participantes,
                            terminado = terminado,
                            animar = animar,
                        )
                    }
                }
            } else if (terminado) {
                // Sin inscripcion y con el reto cerrado no hay nada que hacer:
                // se dice, en vez de dejar un hueco entre la cabecera y el ranking.
                item { Escalonado(1, animar) { Nota(stringResource(R.string.concurso_terminado_gracias, Concurso.FIN.dayOfMonth)) } }
            } else {
                item {
                    Escalonado(1, animar) {
                        if (mostrarRegistro) {
                            FormularioRegistro(
                                onCancelar = { mostrarRegistro = false },
                                onRegistrado = {
                                    mostrarRegistro = false
                                    confeti = animar
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
            }

            item { Spacer(Modifier.height(24.dp)) }

            item { Escalonado(2, animar) { TituloSeccion(stringResource(if (terminado) R.string.concurso_clasificacion_final else R.string.concurso_ranking)) } }
            if (cargandoRanking) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (ranking == null) {
                item { Nota(stringResource(R.string.concurso_sin_red)) }
            } else if (ranking!!.isEmpty()) {
                item { Escalonado(3, animar) { Nota(stringResource(R.string.concurso_ranking_vacio)) } }
            } else {
                itemsIndexed(ranking!!, key = { _, fila -> fila.puesto }) { i, fila ->
                    Escalonado(3 + minOf(i, 8), animar) {
                        FilaRanking(fila, esMio = fila.apodo.equals(p?.apodo, ignoreCase = true), animar = animar)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
            item { TituloSeccion(stringResource(R.string.concurso_reglas)) }
            item { Reglas() }

            if (p != null && !terminado) {
                item { Spacer(Modifier.height(16.dp)) }
                item { InterruptorRecordatorio() }
            }

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
        }

        if (confeti) {
            Confeti(onFin = { confeti = false })
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

/**
 * La imagen del reto con un zoom lentisimo (Ken Burns), y debajo el premio,
 * las fechas y la cuenta atras con un punto que late mientras el reto vive.
 */
@Composable
private fun Cabecera(hoy: LocalDate, participantes: Int, animar: Boolean) {
    val estado = when {
        Concurso.porEmpezar(hoy) -> stringResource(R.string.concurso_empieza, Concurso.INICIO.dayOfMonth)
        Concurso.terminado(hoy) -> stringResource(R.string.concurso_terminado)
        else -> {
            val d = Concurso.diasRestantes(hoy)
            if (d == 1) stringResource(R.string.concurso_ultimo_dia) else stringResource(R.string.concurso_quedan_dias, d)
        }
    }
    val zoom = if (animar) {
        val t = rememberInfiniteTransition(label = "kenburns")
        val z by t.animateFloat(
            initialValue = 1f, targetValue = 1.07f,
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "zoom",
        )
        z
    } else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = NEGRO_RETO,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1200f / 896f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.reto_airpods),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = zoom; scaleY = zoom },
                )
                // Fundido hacia el bloque de texto, para que la imagen no acabe a tajo.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, NEGRO_RETO))),
                )
            }
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.concurso_gana, Concurso.PREMIO),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.concurso_fechas, Concurso.INICIO.dayOfMonth, Concurso.FIN.dayOfMonth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Concurso.enVentana(hoy)) PuntoQueLate(animar)
                    Text(
                        text = if (participantes > 0) "$estado · " + stringResource(R.string.concurso_participantes, participantes) else estado,
                        style = MaterialTheme.typography.labelLarge,
                        color = VERDE_RETO,
                    )
                }
            }
        }
    }
}

@Composable
private fun PuntoQueLate(animar: Boolean) {
    val alfa = if (animar) {
        val t = rememberInfiniteTransition(label = "late")
        val a by t.animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "alfa",
        )
        a
    } else 1f
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { alpha = alfa }
            .background(VERDE_RETO, CircleShape),
    )
}

/* ------------------------------------------------------------ tu semana */

@Composable
private fun TuSemana(
    apodo: String,
    minutos: Int,
    minutosHoy: Int,
    minutosServidor: Int?,
    segundosEnCurso: Int,
    porDia: Map<LocalDate, Int>,
    puesto: Int,
    participantes: Int,
    terminado: Boolean,
    animar: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capa = rememberGraphicsLayer()
    var compartiendo by remember { mutableStateOf(false) }

    // Los minutos cuentan desde cero al abrir; el objetivo se actualiza cuando
    // llega el dato del servidor, y la cifra lo sigue sin saltar.
    var objetivo by remember { mutableIntStateOf(0) }
    LaunchedEffect(minutos) { objetivo = minutos }
    val minutosAnimados by animateIntAsState(
        targetValue = objetivo,
        animationSpec = if (animar) tween(1_400, easing = FastOutSlowInEasing) else tween(0),
        label = "minutos",
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier.drawWithContent {
                capa.record { this@drawWithContent.drawContent() }
                drawLayer(capa)
            }
        ) {
            TarjetaConcurso(
                apodo = apodo,
                minutos = minutosAnimados,
                minutosHoy = minutosHoy,
                porDia = porDia,
                puesto = puesto,
                participantes = participantes,
                terminado = terminado,
                animar = animar,
            )
        }

        Spacer(Modifier.height(10.dp))

        // Lo que esta pasando ahora mismo, debajo de la tarjeta (no se comparte).
        if (segundosEnCurso > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PuntoQueLate(animar)
                Text(
                    text = stringResource(R.string.concurso_en_curso, "%d:%02d".format(segundosEnCurso / 60, segundosEnCurso % 60)),
                    style = MaterialTheme.typography.bodySmall,
                    color = VERDE_RETO,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        if (minutosServidor != null && !terminado) {
            Text(
                text = stringResource(R.string.concurso_confirmado, Concurso.formatear(minutosServidor)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = if (terminado) stringResource(R.string.concurso_terminado_nota, Concurso.FIN.dayOfMonth)
                else stringResource(R.string.concurso_como_cuenta),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp,
        )

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
                            putExtra(
                                Intent.EXTRA_TEXT,
                                context.getString(
                                    if (terminado) R.string.concurso_compartir_texto_final else R.string.concurso_compartir_texto,
                                    Concurso.formatear(minutos),
                                ),
                            )
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
            colors = ButtonDefaults.buttonColors(containerColor = VERDE_RETO, contentColor = NEGRO_RETO),
        ) {
            Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(if (terminado) R.string.concurso_compartir_resultado else R.string.concurso_compartir), fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * La tarjeta compartible, en vertical (4:5, la proporcion de Instagram): la
 * imagen del reto arriba fundiendose con el fondo, tu apodo, tus minutos en
 * grande, tu puesto, la grafica de tus dias con la linea del tope, y la marca.
 * Colores propios y no del tema, para que salga igual en cualquier telefono y
 * se reconozca como de Life Music.
 */
@Composable
private fun TarjetaConcurso(
    apodo: String,
    minutos: Int,
    minutosHoy: Int,
    porDia: Map<LocalDate, Int>,
    puesto: Int,
    participantes: Int,
    terminado: Boolean,
    animar: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f),
        shape = RoundedCornerShape(24.dp),
        color = NEGRO_RETO,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Imagen arriba, fundida con el fondo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.46f)
                    .align(Alignment.TopCenter),
            ) {
                Image(
                    painter = painterResource(R.drawable.reto_airpods),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to NEGRO_RETO,
                            )
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.trophy), contentDescription = null, tint = VERDE_RETO, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(if (terminado) R.string.concurso_resultado_final else R.string.concurso_titulo).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = VERDE_RETO,
                        letterSpacing = 1.8.sp,
                    )
                }

                Spacer(Modifier.weight(0.42f))

                Text(
                    text = apodo,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Text(
                    text = Concurso.formatear(minutos),
                    style = MaterialTheme.typography.displayMedium,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    color = VERDE_RETO,
                    lineHeight = 52.sp,
                )
                Text(
                    text = if (puesto > 0 && participantes > 0) stringResource(R.string.concurso_puesto, puesto, participantes)
                    else stringResource(R.string.concurso_puesto_pendiente),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )

                Spacer(Modifier.height(14.dp))

                BarrasDeLaSemana(porDia = porDia, animar = animar)

                if (!terminado) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.concurso_hoy,
                            Concurso.formatear(minOf(minutosHoy, Concurso.TOPE_MIN_DIA)),
                            Concurso.TOPE_MIN_DIA / 60,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                Spacer(Modifier.weight(0.15f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (terminado) stringResource(R.string.concurso_fechas, Concurso.INICIO.dayOfMonth, Concurso.FIN.dayOfMonth)
                            else stringResource(R.string.concurso_gana, Concurso.PREMIO),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Text(
                        text = "Life Music · CG LABS",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/**
 * Siete barras, una por dia del reto, con la linea del tope. Los dias por
 * venir se dibujan huecos. Al abrir, las barras suben desde cero.
 */
@Composable
private fun BarrasDeLaSemana(porDia: Map<LocalDate, Int>, animar: Boolean) {
    var arranque by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arranque = true }
    val factor by animateFloatAsState(
        targetValue = if (arranque) 1f else 0f,
        animationSpec = if (animar) tween(1_200, delayMillis = 300, easing = FastOutSlowInEasing) else tween(0),
        label = "barras",
    )
    val hoy = LocalDate.now()
    val dias = (0..6).map { Concurso.INICIO.plusDays(it.toLong()) }
    val techo = Concurso.TOPE_MIN_DIA.toFloat() * 1.15f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        val hueco = 6.dp.toPx()
        val ancho = (size.width - hueco * 6) / 7f
        val yTope = size.height - (Concurso.TOPE_MIN_DIA / techo) * size.height
        // Linea del tope, discontinua a mano.
        var x = 0f
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.25f), Offset(x, yTope), Offset(minOf(x + 6.dp.toPx(), size.width), yTope), strokeWidth = 1.dp.toPx())
            x += 12.dp.toPx()
        }
        val radio = CornerRadius(4.dp.toPx())
        dias.forEachIndexed { i, dia ->
            val minutos = porDia[dia] ?: 0
            val alto = (minOf(minutos, Concurso.TOPE_MIN_DIA) / techo) * size.height * factor
            val izquierda = i * (ancho + hueco)
            val futuro = dia.isAfter(hoy)
            drawRoundRect(
                color = Color.White.copy(alpha = if (futuro) 0.06f else 0.10f),
                topLeft = Offset(izquierda, 0f),
                size = Size(ancho, size.height),
                cornerRadius = radio,
            )
            if (alto > 0f) {
                drawRoundRect(
                    color = if (dia == hoy) VERDE_RETO else VERDE_RETO.copy(alpha = 0.7f),
                    topLeft = Offset(izquierda, size.height - alto),
                    size = Size(ancho, alto),
                    cornerRadius = radio,
                )
            }
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
                colors = ButtonDefaults.buttonColors(containerColor = VERDE_RETO, contentColor = NEGRO_RETO),
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
                    colors = ButtonDefaults.buttonColors(containerColor = VERDE_RETO, contentColor = NEGRO_RETO),
                ) {
                    if (enviando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NEGRO_RETO)
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
private fun FilaRanking(fila: PuestoRanking, esMio: Boolean, animar: Boolean) {
    // Tu fila late despacio: se encuentra sin buscarla.
    val brillo = if (esMio && animar) {
        val t = rememberInfiniteTransition(label = "miFila")
        val b by t.animateFloat(
            initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "brillo",
        )
        b
    } else 1f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (esMio) VERDE_RETO.copy(alpha = 0.16f + 0.14f * brillo) else MaterialTheme.colorScheme.surfaceContainerLow,
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
        stringResource(R.string.concurso_regla_7),
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
                        .background(VERDE_RETO),
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

/** Recordatorio diario a las 8 de la noche: cuanto llevas y tu puesto. */
@Composable
private fun InterruptorRecordatorio() {
    val context = LocalContext.current
    val (activo, setActivo) = com.cglabs.lifemusic.utils.rememberPreference(
        com.cglabs.lifemusic.constants.ConcursoRecordatorioKey, defaultValue = true,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.notification),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.concurso_recordatorio_t),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.concurso_recordatorio_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Switch(
            checked = activo,
            onCheckedChange = { encendido ->
                setActivo(encendido)
                if (encendido) RecordatorioReto.programar(context) else RecordatorioReto.cancelar(context)
            },
        )
    }
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

/* ----------------------------------------------------------- confeti */

private class Particula(
    val x: Float,          // 0..1 del ancho
    val retraso: Float,    // 0..0.35 del recorrido
    val color: Color,
    val ancho: Float,      // dp
    val alto: Float,       // dp
    val giro: Float,       // vueltas durante la caida
    val vaiven: Float,     // amplitud lateral, 0..1
)

/**
 * Lluvia de confeti al inscribirse: dos segundos y medio, y desaparece sola.
 * Un Canvas encima de todo, que no captura toques.
 */
@Composable
private fun Confeti(onFin: () -> Unit) {
    val particulas = remember {
        val colores = listOf(VERDE_RETO, Color(0xFFA7F3D0), Color.White, Color(0xFFFDE68A), Color(0xFFF9A8D4))
        List(90) {
            Particula(
                x = Random.nextFloat(),
                retraso = Random.nextFloat() * 0.35f,
                color = colores[Random.nextInt(colores.size)],
                ancho = 6f + Random.nextFloat() * 6f,
                alto = 3f + Random.nextFloat() * 5f,
                giro = 1f + Random.nextFloat() * 3f,
                vaiven = Random.nextFloat(),
            )
        }
    }
    val avance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        avance.animateTo(1f, tween(2_600, easing = LinearEasing))
        onFin()
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = avance.value
        particulas.forEach { p ->
            val local = ((t - p.retraso) / (1f - p.retraso)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val y = -40.dp.toPx() + local * (size.height + 80.dp.toPx())
            val x = p.x * size.width + sin(local * PI.toFloat() * 2f * p.giro) * 24.dp.toPx() * p.vaiven
            val alfa = if (local > 0.8f) (1f - local) / 0.2f else 1f
            rotate(degrees = local * 360f * p.giro, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = p.color.copy(alpha = alfa),
                    topLeft = Offset(x - p.ancho.dp.toPx() / 2, y - p.alto.dp.toPx() / 2),
                    size = Size(p.ancho.dp.toPx(), p.alto.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
        }
    }
}
