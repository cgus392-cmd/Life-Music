package echo.music.iad1tya.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import echo.music.iad1tya.BuildConfig
import echo.music.iad1tya.R

private const val TOTAL_PASOS = 4

/**
 * Introduccion de primer arranque (patron CG LABS §8.2): recorrido a pantalla
 * completa con titular a dos tonos, tarjetas con azulejo de color, indicador de
 * progreso y navegacion anterior/siguiente.
 *
 * El ultimo paso presenta la marca paraguas y da los creditos al proyecto del que
 * nace este fork, atribucion que la GPL-3.0 obliga a conservar.
 */
@Composable
fun WelcomeDialog(
    onDismissRequest: () -> Unit
) {
    var paso by remember { mutableIntStateOf(0) }
    val esUltimo = paso == TOTAL_PASOS - 1

    Dialog(
        onDismissRequest = { /* la introduccion se completa, no se descarta */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                AnimatedContent(
                    targetState = paso,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val haciaAdelante = targetState > initialState
                        val despl = if (haciaAdelante) 1 else -1
                        (slideInHorizontally(tween(320)) { it / 4 * despl } + fadeIn(tween(320)))
                            .togetherWith(
                                slideOutHorizontally(tween(220)) { -it / 4 * despl } + fadeOut(tween(180))
                            )
                    },
                    label = "pasoIntro",
                ) { actual ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when (actual) {
                            0 -> PasoBienvenida()
                            1 -> PasoPermisos()
                            2 -> PasoFunciones()
                            else -> PasoCgLabs()
                        }
                    }
                }

                IndicadorPasos(actual = paso, total = TOTAL_PASOS)

                BarraNavegacion(
                    primerPaso = paso == 0,
                    esUltimo = esUltimo,
                    onAtras = { paso-- },
                    onSiguiente = { if (esUltimo) onDismissRequest() else paso++ },
                )
            }
        }
    }
}

/* ------------------------------------------------------------ paso 1: hola */

@Composable
private fun PasoBienvenida() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(60.dp))

        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
        )

        Spacer(Modifier.height(40.dp))

        TituloDisplay(primeraLinea = "Bienvenido a", segundaLinea = "Life Music")

        Spacer(Modifier.height(28.dp))

        Chip(icono = R.drawable.language, texto = "Predeterminado del sistema")
        Spacer(Modifier.height(10.dp))
        Chip(
            icono = R.drawable.info,
            texto = "Edicion FOSS v${BuildConfig.VERSION_NAME}",
        )

        Spacer(Modifier.height(24.dp))
    }
}

/* --------------------------------------------------------- paso 2: permisos */

@Composable
private fun PasoPermisos() {
    val contexto = LocalContext.current
    val necesitaPermiso = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var concedido by remember {
        mutableStateOf(
            !necesitaPermiso || ContextCompat.checkSelfPermission(
                contexto, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val solicitar = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { resultado -> concedido = resultado }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(48.dp))
        TituloDisplay(primeraLinea = "Permisos", segundaLinea = "necesarios")
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Life Music necesita este permiso para funcionar bien. " +
                "Puedes cambiarlo cuando quieras desde los ajustes de Android.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(28.dp))

        TarjetaAzulejo(
            icono = R.drawable.notification,
            colorAzulejo = Color(0xFFF48FB1),
            titulo = "Notificaciones",
            descripcion = "Para ver que suena y controlar la reproduccion " +
                "desde la barra de notificaciones.",
            marcaVerificado = concedido,
            onClick = {
                if (!concedido && necesitaPermiso) {
                    solicitar.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        TarjetaAzulejo(
            icono = R.drawable.offline,
            colorAzulejo = Color(0xFF80CBC4),
            titulo = "Descargas sin conexion",
            descripcion = "Se pide solo cuando descargues tu primera cancion.",
            marcaVerificado = null,
        )
        Spacer(Modifier.height(24.dp))
    }
}

/* -------------------------------------------------------- paso 3: funciones */

@Composable
private fun PasoFunciones() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(48.dp))
        TituloDisplay(primeraLinea = "Descubre", segundaLinea = "Life Music")
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Todo lo que puedes hacer desde el primer momento.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(24.dp))

        val funciones = listOf(
            Funcion(R.drawable.lyrics, Color(0xFFCE93D8), "Letras sincronizadas",
                "Palabra por palabra, con traduccion opcional."),
            Funcion(R.drawable.offline, Color(0xFF80CBC4), "Sin conexion",
                "Descarga y escucha donde no hay senal."),
            Funcion(R.drawable.mic, Color(0xFFFFAB91), "Reconocimiento",
                "Averigua que cancion esta sonando a tu alrededor."),
            Funcion(R.drawable.equalizer, Color(0xFF90CAF9), "Ecualizador",
                "Ajusta el sonido a tus audifonos."),
            Funcion(R.drawable.palette, Color(0xFFA5D6A7), "Material You",
                "La app toma los colores de tu fondo de pantalla."),
        )
        funciones.forEachIndexed { i, f ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            TarjetaAzulejo(
                icono = f.icono,
                colorAzulejo = f.color,
                titulo = f.titulo,
                descripcion = f.descripcion,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class Funcion(
    val icono: Int,
    val color: Color,
    val titulo: String,
    val descripcion: String,
)

/* ---------------------------------------------------------- paso 4: CG LABS */

@Composable
private fun PasoCgLabs() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(48.dp))
        TituloDisplay(primeraLinea = "Un desarrollo de", segundaLinea = "CG LABS")

        Spacer(Modifier.height(32.dp))

        // Wordmark theme-adaptive por recursos: negro en drawable/, blanco en
        // drawable-night/. Android elige la variante segun el tema del sistema.
        Image(
            painter = painterResource(R.drawable.cglabs_wordmark),
            contentDescription = "CG LABS",
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Life Music by CG · CG LABS",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Proyecto educativo y sin animo de lucro. Life Music nace como " +
                "fork de Echo Music, que a su vez desciende de Vivi Music, " +
                "Metrolist e InnerTune. Se distribuye bajo licencia GPL-3.0 y " +
                "conserva los creditos de toda la cadena.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )

        Spacer(Modifier.height(24.dp))

        TarjetaAzulejo(
            icono = R.drawable.github,
            colorAzulejo = Color(0xFFB0BEC5),
            titulo = "Echo Music",
            descripcion = "El proyecto del que nace Life Music.",
        )
        Spacer(Modifier.height(12.dp))
        TarjetaAzulejo(
            icono = R.drawable.license_echo,
            colorAzulejo = Color(0xFFC5E1A5),
            titulo = "Licencia GPL-3.0",
            descripcion = "Heredada del proyecto original.",
        )
        Spacer(Modifier.height(24.dp))
    }
}

/* ------------------------------------------------------------- compartidos */

/** Titular a dos tonos, en cursiva: la segunda linea va en el color de acento. */
@Composable
private fun TituloDisplay(primeraLinea: String, segundaLinea: String) {
    Column {
        Text(
            text = primeraLinea,
            style = MaterialTheme.typography.displaySmall,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 52.sp,
        )
        Text(
            text = segundaLinea,
            style = MaterialTheme.typography.displaySmall,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            lineHeight = 52.sp,
        )
    }
}

@Composable
private fun Chip(icono: Int, texto: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(icono),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Tarjeta con azulejo de color a la izquierda. [marcaVerificado] a true pinta un
 * visto, a false una cruz, y a null no muestra indicador (la fila es informativa).
 */
@Composable
private fun TarjetaAzulejo(
    icono: Int,
    colorAzulejo: Color,
    titulo: String,
    descripcion: String,
    marcaVerificado: Boolean? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorAzulejo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icono),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF1B1B1B),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
            marcaVerificado?.let { ok ->
                Icon(
                    painter = painterResource(if (ok) R.drawable.check else R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (ok) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IndicadorPasos(actual: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val activo = i == actual
            val ancho by animateFloatAsState(
                targetValue = if (activo) 24f else 8f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "anchoPunto",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(8.dp)
                    .width(ancho.dp)
                    .clip(CircleShape)
                    .background(
                        if (activo) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    ),
            )
        }
    }
}

@Composable
private fun BarraNavegacion(
    primerPaso: Boolean,
    esUltimo: Boolean,
    onAtras: () -> Unit,
    onSiguiente: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!primerPaso) {
            OutlinedButton(
                onClick = onAtras,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape = RoundedCornerShape(50),
            ) {
                Text("Atras", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }
        Button(
            onClick = onSiguiente,
            modifier = Modifier
                .weight(if (primerPaso) 1f else 1.15f)
                .height(64.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (primerPaso) {
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = "Siguiente",
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Text(
                    text = if (esUltimo) "Comenzar" else "Siguiente",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
