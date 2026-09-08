package com.cglabs.lifemusic.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.cglabs.lifemusic.BuildConfig
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.DarkModeKey
import com.cglabs.lifemusic.constants.DynamicThemeKey
import com.cglabs.lifemusic.constants.PlayerBackgroundStyle
import com.cglabs.lifemusic.constants.PlayerBackgroundStyleDefault
import com.cglabs.lifemusic.constants.PlayerBackgroundStyleKey
import com.cglabs.lifemusic.constants.UseNewPlayerDesignKey
import com.cglabs.lifemusic.ui.screens.settings.DarkMode
import com.cglabs.lifemusic.utils.rememberEnumPreference
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TOTAL_PASOS = 5

/** Lobulos de la insignia. Doce leen como «estrella blanda» sin parecer un engranaje. */
private const val LOBULOS = 12

/** Cuanto se hunde cada valle respecto al radio. Por encima de 0,15 parece una sierra. */
private const val AMPLITUD = 0.11f

/** Retardo entre bloques de una misma pantalla al entrar. */
private const val ESCALON_MS = 70L

/**
 * Introduccion de primer arranque (patron CG LABS §8.2).
 *
 * Cinco pasos: saludo, preajustes, permisos, funciones y marca. El segundo deja
 * elegir tema, color y reproductor sin salir de aqui: son los ajustes que la
 * gente cambia el primer dia, y mandarla a buscarlos en un menu que todavia no
 * conoce es pedirle que se pierda antes de empezar.
 *
 * El ultimo paso da los creditos al proyecto del que nace este fork, atribucion
 * que la GPL-3.0 obliga a conservar.
 */
@Composable
fun WelcomeDialog(
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* la introduccion se completa, no se descarta */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        VentanaAPantallaCompleta()
        Contenido(onDismissRequest = onDismissRequest)
    }
}

/**
 * Un Dialog abre su PROPIA ventana, y esa ventana no hereda el borde a borde que
 * MainActivity le pone a la suya. Por eso la introduccion salia con dos franjas
 * grises —la de estado y la de navegacion—: no eran de la aplicacion, eran el
 * decorado por defecto del dialogo.
 *
 * El tono de los iconos se decide por la luminancia del fondo REAL de la
 * aplicacion, no por el tema del sistema: aqui se puede tener la app en oscuro
 * con el sistema en claro, y al reves.
 */
@Composable
private fun VentanaAPantallaCompleta() {
    val vista = LocalView.current
    val fondoClaro = MaterialTheme.colorScheme.background.luminance() > 0.5f

    LaunchedEffect(vista, fondoClaro) {
        val ventana = (vista.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        ventana.setDimAmount(0f)
        WindowCompat.setDecorFitsSystemWindows(ventana, false)
        @Suppress("DEPRECATION")
        ventana.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        ventana.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(ventana, vista).apply {
            isAppearanceLightStatusBars = fondoClaro
            isAppearanceLightNavigationBars = fondoClaro
        }
    }
}

@Composable
private fun Contenido(onDismissRequest: () -> Unit) {
    var paso by remember { mutableIntStateOf(0) }
    val esUltimo = paso == TOTAL_PASOS - 1
    val animar = recordarSiAnimar()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Va por detras de las barras del sistema a proposito: el color tiene
            // que llegar hasta el borde, que es justo lo que faltaba.
            FondoVivo(animar = animar)

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
                            0 -> PasoBienvenida(animar)
                            1 -> PasoPreajustes(animar)
                            2 -> PasoPermisos(animar)
                            3 -> PasoFunciones(animar)
                            else -> PasoCgLabs(animar)
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

/**
 * Si el sistema tiene las animaciones desactivadas —accesibilidad, o ahorro de
 * bateria en algunos equipos— nada de esto se mueve. Un adorno que ignora ese
 * ajuste no es vistoso, es maleducado.
 */
@Composable
private fun recordarSiAnimar(): Boolean {
    val contexto = LocalContext.current
    return remember(contexto) {
        Settings.Global.getFloat(
            contexto.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}

/* ------------------------------------------------------------------ ambiente */

/**
 * Dos manchas de luz muy tenues que derivan despacio. Toman el color del tema,
 * asi que acompanan a Material You en vez de pelearse con el: con color dinamico
 * activado, el fondo de la introduccion ya es el del telefono de cada uno.
 */
@Composable
private fun FondoVivo(animar: Boolean) {
    val primario = MaterialTheme.colorScheme.primary
    val terciario = MaterialTheme.colorScheme.tertiary

    val avance = if (animar) {
        val transicion = rememberInfiniteTransition(label = "aurora")
        val v by transicion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(26_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "deriva",
        )
        v
    } else {
        0.5f
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val ancho = size.width
        val alto = size.height

        val radioA = ancho * 0.9f
        val centroA = Offset(ancho * (0.12f + 0.26f * avance), alto * (0.08f + 0.07f * avance))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primario.copy(alpha = 0.22f), Color.Transparent),
                center = centroA,
                radius = radioA,
            ),
            radius = radioA,
            center = centroA,
        )

        val radioB = ancho * 0.8f
        val centroB = Offset(ancho * (0.92f - 0.3f * avance), alto * (0.78f - 0.1f * avance))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(terciario.copy(alpha = 0.18f), Color.Transparent),
                center = centroB,
                radius = radioB,
            ),
            radius = radioB,
            center = centroB,
        )
    }
}

/** Contorno de la insignia: radio modulado por un coseno, muestreado fino. */
private fun trazarEstrella(centro: Offset, radio: Float): Path {
    val ruta = Path()
    val muestras = 480
    for (i in 0..muestras) {
        val angulo = (i.toFloat() / muestras) * 2f * PI.toFloat()
        val r = radio * (1f - AMPLITUD + AMPLITUD * cos(LOBULOS * angulo))
        val x = centro.x + r * cos(angulo)
        val y = centro.y + r * sin(angulo)
        if (i == 0) ruta.moveTo(x, y) else ruta.lineTo(x, y)
    }
    ruta.close()
    return ruta
}

/**
 * Insignia del saludo: dos estrellas blandas girando en sentidos opuestos, muy
 * despacio, con el icono encima. El giro contrario es lo que da sensacion de
 * profundidad; con las dos en el mismo sentido parece un solo adorno plano.
 */
@Composable
private fun InsigniaHero(animar: Boolean) {
    val halo = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val cuerpo = MaterialTheme.colorScheme.primaryContainer

    var giro = 0f
    var contragiro = 0f
    var latido = 1f
    if (animar) {
        val transicion = rememberInfiniteTransition(label = "insignia")
        giro = transicion.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(72_000, easing = LinearEasing)),
            label = "giro",
        ).value
        contragiro = transicion.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(52_000, easing = LinearEasing)),
            label = "contragiro",
        ).value
        latido = transicion.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(4_600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "latido",
        ).value
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(210.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = contragiro },
            ) {
                drawPath(trazarEstrella(center, size.minDimension / 2f), halo)
            }
            Canvas(
                modifier = Modifier
                    .size(164.dp)
                    .graphicsLayer {
                        rotationZ = giro
                        scaleX = latido
                        scaleY = latido
                    },
            ) {
                drawPath(trazarEstrella(center, size.minDimension / 2f), cuerpo)
            }
            AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            )
        }
    }
}

/**
 * Entrada escalonada: cada bloque aparece un pelin despues del anterior. Es lo
 * que separa una pantalla que «se muestra» de una que «llega».
 */
@Composable
private fun Escalonado(
    indice: Int,
    animar: Boolean,
    content: @Composable () -> Unit,
) {
    if (!animar) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(indice * ESCALON_MS)
        visible = true
    }
    val opacidad by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "opacidadEntrada",
    )
    val desplazamiento by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "desplEntrada",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = opacidad
                translationY = desplazamiento.dp.toPx()
            }
    ) {
        content()
    }
}

/* ------------------------------------------------------------- paso 1: hola */

@Composable
private fun ColumnScope.PasoBienvenida(animar: Boolean) {
    Spacer(Modifier.height(24.dp))

    Escalonado(0, animar) { InsigniaHero(animar) }

    Spacer(Modifier.height(28.dp))

    Escalonado(1, animar) {
        TituloDisplay(primeraLinea = "Bienvenido a", segundaLinea = "Life Music")
    }

    Spacer(Modifier.height(24.dp))

    Escalonado(2, animar) {
        Column {
            Chip(icono = R.drawable.language, texto = "Predeterminado del sistema")
            Spacer(Modifier.height(10.dp))
            Chip(
                icono = R.drawable.info,
                texto = "Edicion ${BuildConfig.FLAVOR_variant.uppercase()} v${BuildConfig.VERSION_NAME}",
            )
        }
    }

    Spacer(Modifier.height(24.dp))
}

/* ------------------------------------------------------- paso 2: preajustes */

@Composable
private fun ColumnScope.PasoPreajustes(animar: Boolean) {
    var modoOscuro by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    var colorDinamico by rememberPreference(DynamicThemeKey, defaultValue = true)
    var fondoReproductor by rememberEnumPreference(
        PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyleDefault,
    )
    var nuevoDiseno by rememberPreference(UseNewPlayerDesignKey, defaultValue = true)

    Spacer(Modifier.height(40.dp))

    Escalonado(0, animar) {
        Column {
            TituloDisplay(primeraLinea = "A tu", segundaLinea = "manera")
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Cuatro ajustes para dejarla como te gusta. Los tienes todos " +
                    "despues en Ajustes, Apariencia, junto a muchos mas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }
    }

    Spacer(Modifier.height(26.dp))

    Escalonado(1, animar) {
        BloqueAjuste(titulo = "Tema") {
            SelectorSegmentado(
                opciones = listOf(
                    DarkMode.AUTO to "Automatico",
                    DarkMode.OFF to "Claro",
                    DarkMode.ON to "Oscuro",
                ),
                seleccion = modoOscuro,
                onSeleccion = { modoOscuro = it },
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Escalonado(2, animar) {
        FilaInterruptor(
            icono = R.drawable.palette,
            colorAzulejo = Color(0xFFA5D6A7),
            titulo = "Color dinamico",
            descripcion = "La aplicacion toma los colores de tu fondo de pantalla.",
            activo = colorDinamico,
            onCambio = { colorDinamico = it },
        )
    }

    Spacer(Modifier.height(12.dp))

    Escalonado(3, animar) {
        BloqueAjuste(titulo = "Fondo del reproductor") {
            SelectorSegmentado(
                opciones = listOf(
                    PlayerBackgroundStyle.APPLE_MUSIC to "Apple Music",
                    PlayerBackgroundStyle.GRADIENT to "Degradado",
                    PlayerBackgroundStyle.BLUR to "Difuminado",
                ),
                seleccion = fondoReproductor,
                onSeleccion = { fondoReproductor = it },
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Escalonado(4, animar) {
        FilaInterruptor(
            icono = R.drawable.tune,
            colorAzulejo = Color(0xFF90CAF9),
            titulo = "Nuevo diseno del reproductor",
            descripcion = "Controles mas amplios y caratula a pantalla completa.",
            activo = nuevoDiseno,
            onCambio = { nuevoDiseno = it },
        )
    }

    Spacer(Modifier.height(24.dp))
}

/* --------------------------------------------------------- paso 3: permisos */

@Composable
private fun ColumnScope.PasoPermisos(animar: Boolean) {
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

    Spacer(Modifier.height(48.dp))

    Escalonado(0, animar) {
        Column {
            TituloDisplay(primeraLinea = "Permisos", segundaLinea = "necesarios")
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Life Music necesita este permiso para funcionar bien. " +
                    "Puedes cambiarlo cuando quieras desde los ajustes de Android.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    Escalonado(1, animar) {
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
    }
    Spacer(Modifier.height(12.dp))
    Escalonado(2, animar) {
        TarjetaAzulejo(
            icono = R.drawable.offline,
            colorAzulejo = Color(0xFF80CBC4),
            titulo = "Descargas sin conexion",
            descripcion = "Se pide solo cuando descargues tu primera cancion.",
            marcaVerificado = null,
        )
    }
    Spacer(Modifier.height(24.dp))
}

/* -------------------------------------------------------- paso 4: funciones */

@Composable
private fun ColumnScope.PasoFunciones(animar: Boolean) {
    Spacer(Modifier.height(48.dp))

    Escalonado(0, animar) {
        Column {
            TituloDisplay(primeraLinea = "Descubre", segundaLinea = "Life Music")
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Todo lo que puedes hacer desde el primer momento.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }
    }

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
        Escalonado(i + 1, animar) {
            TarjetaAzulejo(
                icono = f.icono,
                colorAzulejo = f.color,
                titulo = f.titulo,
                descripcion = f.descripcion,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

private data class Funcion(
    val icono: Int,
    val color: Color,
    val titulo: String,
    val descripcion: String,
)

/* ---------------------------------------------------------- paso 5: CG LABS */

@Composable
private fun ColumnScope.PasoCgLabs(animar: Boolean) {
    Spacer(Modifier.height(48.dp))

    Escalonado(0, animar) {
        TituloDisplay(primeraLinea = "Un desarrollo de", segundaLinea = "CG LABS")
    }

    Spacer(Modifier.height(32.dp))

    Escalonado(1, animar) {
        // Tenido con el color de contenido del tema de la APLICACION. Antes venia
        // por recursos —negro en drawable/, blanco en drawable-night/— y Android
        // elige esa variante por el tema del SISTEMA, que aqui puede no coincidir:
        // con la app en oscuro y el sistema en claro salia negro sobre negro.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.cglabs_wordmark),
                contentDescription = "CG LABS",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    Escalonado(2, animar) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Life Music by CG · CG LABS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
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
        }
    }

    Spacer(Modifier.height(24.dp))

    Escalonado(3, animar) {
        TarjetaAzulejo(
            icono = R.drawable.github,
            colorAzulejo = Color(0xFFB0BEC5),
            titulo = "Echo Music",
            descripcion = "El proyecto del que nace Life Music.",
        )
    }
    Spacer(Modifier.height(12.dp))
    Escalonado(4, animar) {
        TarjetaAzulejo(
            icono = R.drawable.ic_license,
            colorAzulejo = Color(0xFFC5E1A5),
            titulo = "Licencia GPL-3.0",
            descripcion = "Heredada del proyecto original.",
        )
    }
    Spacer(Modifier.height(24.dp))
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

/** Contenedor de un ajuste del paso de preajustes, con el aire de las tarjetas. */
@Composable
private fun BloqueAjuste(titulo: String, contenido: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            contenido()
        }
    }
}

/**
 * Selector de una opcion entre varias. Se reparte el ancho a partes iguales, asi
 * que la etiqueta de cada opcion tiene que ser corta; con tres cabe comodo.
 */
@Composable
private fun <T> SelectorSegmentado(
    opciones: List<Pair<T, String>>,
    seleccion: T,
    onSeleccion: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opciones.forEach { (valor, etiqueta) ->
            val activa = valor == seleccion
            val fondo by animateColorAsState(
                targetValue = if (activa) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                animationSpec = tween(220),
                label = "fondoSegmento",
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSeleccion(valor) },
                shape = RoundedCornerShape(14.dp),
                color = fondo,
            ) {
                Text(
                    text = etiqueta,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (activa) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

/** Tarjeta con azulejo de color e interruptor a la derecha. */
@Composable
private fun FilaInterruptor(
    icono: Int,
    colorAzulejo: Color,
    titulo: String,
    descripcion: String,
    activo: Boolean,
    onCambio: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCambio(!activo) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AzulejoIcono(icono = icono, color = colorAzulejo)
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
            Switch(checked = activo, onCheckedChange = onCambio)
        }
    }
}

@Composable
private fun AzulejoIcono(icono: Int, color: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icono),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Color(0xFF1B1B1B),
        )
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
            AzulejoIcono(icono = icono, color = colorAzulejo)
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
