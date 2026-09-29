package com.cglabs.lifemusic.car

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.MedidorDeDescargas
import com.music.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class TipoRed { WIFI, DATOS, CABLE, NINGUNA }

/** Lo que el indicador dice de la red, de mejor a peor. */
enum class CalidadRed(val texto: Int) {
    MIDIENDO(R.string.carro_red_midiendo),
    ESTABLE(R.string.carro_red_estable),
    LENTA(R.string.carro_red_lenta),
    DEFICIENTE(R.string.carro_red_deficiente),
    SIN_INTERNET(R.string.carro_red_sin_internet),
}

/**
 * La red en un momento dado. [barras] va de 0 a 4 (-1 si el radio no lo dice),
 * [medida] es una red de datos o un hotspot que se declara de pago,
 * [latenciaMs] es la mediana de las ultimas pruebas y [velocidadBps] la ultima
 * velocidad medida con el audio en esta misma red (null si aun no hay).
 */
data class EstadoRed(
    val tipo: TipoRed = TipoRed.NINGUNA,
    val barras: Int = -1,
    val medida: Boolean = false,
    val latenciaMs: Int? = null,
    val velocidadBps: Long? = null,
    val calidad: CalidadRed = CalidadRed.MIDIENDO,
)

/**
 * Vigila la red mientras la app esta a la vista (CarActivity lo enciende en
 * onStart y lo apaga en onStop): que red hay y con que señal, lo que tarda en
 * responder y lo que da de velocidad.
 *
 * - La estabilidad sale de una prueba minima cada [INTERVALO_MS]: una peticion
 *   HTTPS vacia (generate_204, unos cientos de bytes) sobre una conexion que se
 *   mantiene abierta, asi que mide ida y vuelta y no el apreton de manos. Son
 *   unos 150 KB por hora con la app a la vista; en segundo plano, nada.
 * - La velocidad no se prueba bajando nada: la mide [MedidorDeDescargas] con el
 *   audio que la app ya descarga.
 *
 * El mismo estado decide cuando cargar el canvas.
 */
class MonitorDeRed(private val contexto: Context) {

    private val _estado = MutableStateFlow(EstadoRed())
    val estado: StateFlow<EstadoRed> = _estado

    private val conectividad = contexto.getSystemService(ConnectivityManager::class.java)
    private val wifi = contexto.getSystemService(WifiManager::class.java)
    private val telefonia = contexto.getSystemService(TelephonyManager::class.java)

    private var alcance: CoroutineScope? = null
    private var capacidades: NetworkCapabilities? = null

    /** Las ultimas pruebas, la mas nueva al final: milisegundos, o null si fallo. Solo se toca en el hilo principal. */
    private val pruebas = ArrayDeque<Int?>()

    /** Cuando cambio la red: una velocidad medida antes es de otra red y no cuenta. */
    private var cambioMs = 0L

    private val despertar = Channel<Unit>(Channel.CONFLATED)

    private val cliente by lazy {
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val alCambiarRed = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            alcance?.launch {
                val antes = capacidades
                capacidades = caps
                if (antes == null || tipoDe(antes) != tipoDe(caps)) redNueva()
                recalcular()
            }
        }

        override fun onLost(network: Network) {
            alcance?.launch {
                capacidades = null
                pruebas.clear()
                recalcular()
            }
        }
    }

    fun empezar() {
        if (alcance != null) return
        val a = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        alcance = a
        capacidades = runCatching { conectividad?.getNetworkCapabilities(conectividad.activeNetwork) }.getOrNull()
        cambioMs = SystemClock.elapsedRealtime()
        runCatching { conectividad?.registerDefaultNetworkCallback(alCambiarRed) }
        a.launch { MedidorDeDescargas.ultima.collect { recalcular() } }
        a.launch {
            while (isActive) {
                probar()
                withTimeoutOrNull(INTERVALO_MS) { despertar.receive() }
            }
        }
        // La señal del Wi-Fi cambia sin que Android avise en Android 8: se relee cada tanto.
        a.launch {
            while (isActive) {
                delay(SENAL_MS)
                recalcular()
            }
        }
    }

    fun parar() {
        runCatching { conectividad?.unregisterNetworkCallback(alCambiarRed) }
        alcance?.cancel()
        alcance = null
    }

    /** Tocar el indicador: probar ya, sin esperar a la siguiente vuelta. */
    fun medirAhora() {
        despertar.trySend(Unit)
    }

    private fun redNueva() {
        cambioMs = SystemClock.elapsedRealtime()
        pruebas.clear()
        despertar.trySend(Unit)
    }

    private suspend fun probar() {
        if (capacidades == null) {
            recalcular()
            return
        }
        // La primera prueba en una red abre la conexion (DNS y TLS): tarda de mas y no cuenta.
        val calentar = pruebas.isEmpty()
        val ms = withContext(Dispatchers.IO) {
            if (calentar) ping()
            ping()
        }
        pruebas.addLast(ms)
        while (pruebas.size > VENTANA) pruebas.removeFirst()
        recalcular()
    }

    private fun ping(): Int? = runCatching {
        val t0 = SystemClock.elapsedRealtime()
        cliente.newCall(Request.Builder().url(PRUEBA_URL).build()).execute().use { r ->
            if (r.code == 204) (SystemClock.elapsedRealtime() - t0).toInt() else null
        }
    }.getOrNull()

    private fun recalcular() {
        val caps = capacidades
        val tipo = caps?.let(::tipoDe) ?: TipoRed.NINGUNA
        val validas = pruebas.filterNotNull().sorted()
        val fallos = pruebas.count { it == null }
        val latencia = validas.getOrNull(validas.size / 2)
        val ahora = SystemClock.elapsedRealtime()
        val velocidad = MedidorDeDescargas.ultima.value
            ?.takeIf { it.cuandoMs >= cambioMs && ahora - it.cuandoMs < VIGENCIA_VELOCIDAD_MS }
            ?.bitsPorSegundo
        val ultimasFallaron = pruebas.size >= 2 && pruebas.takeLast(2).all { it == null }
        val calidad = when {
            caps == null -> CalidadRed.SIN_INTERNET
            pruebas.isEmpty() -> CalidadRed.MIDIENDO
            latencia == null || ultimasFallaron -> CalidadRed.SIN_INTERNET
            fallos >= 2 || latencia > LATENCIA_MALA_MS || (velocidad != null && velocidad < VELOCIDAD_MALA_BPS) -> CalidadRed.DEFICIENTE
            fallos == 1 || latencia > LATENCIA_LENTA_MS || (velocidad != null && velocidad < VELOCIDAD_LENTA_BPS) -> CalidadRed.LENTA
            else -> CalidadRed.ESTABLE
        }
        _estado.value = EstadoRed(
            tipo = tipo,
            barras = barrasDe(tipo),
            medida = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            latenciaMs = latencia,
            velocidadBps = velocidad,
            calidad = calidad,
        )
    }

    private fun tipoDe(caps: NetworkCapabilities): TipoRed = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TipoRed.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TipoRed.DATOS
        else -> TipoRed.CABLE
    }

    @Suppress("DEPRECATION")
    private fun barrasDe(tipo: TipoRed): Int = when (tipo) {
        TipoRed.WIFI -> runCatching {
            val rssi = wifi?.connectionInfo?.rssi ?: return@runCatching -1
            if (rssi <= -127) -1 else WifiManager.calculateSignalLevel(rssi, 5)
        }.getOrDefault(-1)
        TipoRed.DATOS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { telefonia?.signalStrength?.level ?: -1 }.getOrDefault(-1)
        } else {
            -1
        }
        else -> -1
    }

    private companion object {
        /** La de Google para comprobar conexion: responde vacia (204) y rapido desde cualquier parte. */
        const val PRUEBA_URL = "https://www.gstatic.com/generate_204"
        const val INTERVALO_MS = 12_000L
        const val SENAL_MS = 5_000L
        const val VENTANA = 5
        const val VIGENCIA_VELOCIDAD_MS = 10 * 60_000L

        // Ida y vuelta: el 4G bueno anda por 40-80 ms; por encima de 300 ya se nota, de 800 se corta.
        const val LATENCIA_LENTA_MS = 300
        const val LATENCIA_MALA_MS = 800

        // Velocidad: el audio pide ~160 kbps; con menos de 400 una cancion tarda en arrancar y el canvas no cabe.
        const val VELOCIDAD_LENTA_BPS = 1_000_000L
        const val VELOCIDAD_MALA_BPS = 400_000L
    }
}

val LocalMonitorDeRed = staticCompositionLocalOf<MonitorDeRed?> { null }

/** Colores de estado: fijos, no salen de la caratula (verde siempre es bien, rojo siempre es mal). */
private val VERDE_RED = Color(0xFF7BE0A8)
private val AMBAR_RED = Color(0xFFF4C56A)
private val ROJO_RED = Color(0xFFFF8A7A)

/**
 * El indicador de red de Inicio: el icono de la red con su señal, cuan estable
 * esta y lo que da (la velocidad medida con el audio o, si aun no hay, el
 * tiempo de respuesta). Tocarlo vuelve a medir.
 */
@Composable
fun IndicadorDeRed(modifier: Modifier = Modifier) {
    val monitor = LocalMonitorDeRed.current ?: return
    val estado by monitor.estado.collectAsState()
    val sinConexion = estado.tipo == TipoRed.NINGUNA
    val color = when {
        sinConexion -> ROJO_RED
        else -> when (estado.calidad) {
            CalidadRed.ESTABLE -> VERDE_RED
            CalidadRed.LENTA -> AMBAR_RED
            CalidadRed.DEFICIENTE, CalidadRed.SIN_INTERNET -> ROJO_RED
            CalidadRed.MIDIENDO -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    val etiqueta = stringResource(if (sinConexion) R.string.carro_red_sin_conexion else estado.calidad.texto)
    val velocidad = estado.velocidadBps
    val latencia = estado.latenciaMs
    val metrica = when {
        sinConexion || estado.calidad == CalidadRed.SIN_INTERNET || estado.calidad == CalidadRed.MIDIENDO -> null
        velocidad != null -> velocidadLegible(velocidad)
        latencia != null -> "$latencia ms"
        else -> null
    }
    val nombreRed = stringResource(
        when (estado.tipo) {
            TipoRed.WIFI -> R.string.carro_red_wifi
            TipoRed.DATOS -> R.string.carro_red_datos
            TipoRed.CABLE -> R.string.carro_red_cable
            TipoRed.NINGUNA -> R.string.carro_red_sin_conexion
        },
    )
    val descripcion = stringResource(R.string.carro_red_descripcion, nombreRed, listOfNotNull(etiqueta, metrica).joinToString(", "))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(56.dp)
            .cristal(RoundedCornerShape(28.dp))
            .clickable { monitor.medirAhora() }
            .semantics(mergeDescendants = true) { contentDescription = descripcion }
            .padding(horizontal = 18.dp),
    ) {
        IconoDeRed(estado.tipo, estado.barras, color, Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(etiqueta, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        if (metrica != null) {
            Spacer(Modifier.width(10.dp))
            Text(metrica, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** «850 kB/s», «2,4 MB/s»: en bytes, como los muestra un navegador o una descarga. */
private fun velocidadLegible(bps: Long): String {
    val bytes = bps / 8.0
    return if (bytes >= 1_000_000) "%.1f MB/s".format(Locale.getDefault(), bytes / 1_000_000) else "${(bytes / 1000).toInt()} kB/s"
}

/**
 * El icono, dibujado: el abanico del Wi-Fi (un punto y tres arcos) o las cuatro
 * barras de los datos, encendidas segun la señal; tachado si no hay red. Si el
 * radio no dice la señal (-1), todo encendido.
 */
@Composable
private fun IconoDeRed(tipo: TipoRed, barras: Int, color: Color, modifier: Modifier = Modifier) {
    if (tipo == TipoRed.CABLE) {
        Icon(painterResource(R.drawable.network_node), contentDescription = null, tint = color, modifier = modifier)
        return
    }
    val apagado = color.copy(alpha = 0.25f)
    val nivel = when {
        tipo == TipoRed.NINGUNA -> 0
        barras < 0 -> 4
        else -> barras
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (tipo == TipoRed.DATOS) {
            val ancho = w / 7f
            for (i in 0 until 4) {
                val alto = h * (0.3f + 0.2f * i)
                drawRoundRect(
                    color = if (i < nivel) color else apagado,
                    topLeft = Offset(ancho * (0.5f + 1.7f * i), h - alto),
                    size = Size(ancho, alto),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(ancho / 3f),
                )
            }
            return@Canvas
        }
        val centro = Offset(w / 2f, h * 0.88f)
        val trazo = h * 0.11f
        drawCircle(if (nivel >= 1) color else apagado, radius = h * 0.09f, center = centro)
        listOf(0.36f, 0.6f, 0.84f).forEachIndexed { i, r ->
            val radio = h * r
            drawArc(
                color = if (nivel >= i + 2) color else apagado,
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(centro.x - radio, centro.y - radio),
                size = Size(radio * 2, radio * 2),
                style = Stroke(width = trazo, cap = StrokeCap.Round),
            )
        }
        if (tipo == TipoRed.NINGUNA) {
            drawLine(color, Offset(w * 0.12f, h * 0.1f), Offset(w * 0.88f, h * 0.9f), strokeWidth = trazo, cap = StrokeCap.Round)
        }
    }
}
