package com.cglabs.lifemusic.car

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.Player
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.playback.MusicService
import com.cglabs.lifemusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin

/** Que hace la app al encender el carro (Ajustes). */
enum class AlEncender(val titulo: Int, val detalle: Int) {
    SEGUIR_SONANDO(R.string.carro_al_encender_seguir, R.string.carro_al_encender_seguir_texto),
    SOLO_ABRIR(R.string.carro_al_encender_abrir, R.string.carro_al_encender_abrir_texto),
    NADA(R.string.carro_al_encender_nada, R.string.carro_al_encender_nada_texto),
}

val CarroAlEncenderKey = stringPreferencesKey("carroAlEncender")

/** El reloj de vida: la ultima vez que la app estaba viva, y la ultima que sonaba (reloj de pared). */
private val CarroVivoKey = longPreferencesKey("carroVivo")
private val CarroSonabaKey = longPreferencesKey("carroSonaba")

/**
 * Reanudar al encender el carro (ficha docs/versiones/carro-0.3.0/reanudar.md).
 *
 * Hay dos clases de radio:
 *  - los que se APAGAN del todo: al encender arrancan de cero y llega
 *    BOOT_COMPLETED ([EncendidoReceiver] → [alArrancar]);
 *  - los que se DUERMEN: la app sigue en memoria, la pantalla se apaga y vuelve
 *    (SCREEN_OFF/SCREEN_ON) y, segun la marca, llega un aviso de ACC.
 *
 * Para saber si sonaba al apagar no hay tiempo de guardar nada cuando se corta
 * la corriente; por eso el reloj de vida: cada [LATIDO_MS] se anota «sigo viva»
 * y, si suena, «sonaba». Al volver: sonaba si las dos marcas estan a menos de
 * [MARGEN_SONABA_MS]. Una pausa que llega justo antes del apagado (el radio
 * pausa al quitar el contacto) cae dentro del margen y no cuenta como pausa.
 *
 * Vive con el proceso y mantiene su propia conexion con MusicService: la
 * pantalla suelta la suya cuando Waze o Maps quedan delante.
 */
object Encendido {
    private const val LATIDO_MS = 15_000L
    private const val MARGEN_SONABA_MS = 20_000L
    private const val MINIMO_APAGADO_MS = 30_000L
    private const val ESPERA_MS = 4_000L
    private const val RAMPA_MS = 2_000L
    private const val ESPERA_COLA_MS = 20_000L

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var servicio: MusicService? = null
    private var iniciado = false
    private var oyente: Player? = null

    /** Cuando se apago la pantalla (elapsedRealtime), o 0 si esta encendida. */
    private var apagadoEn = 0L
    private var sonabaAlApagar = false
    /** La ultima vez que se vio sonando (elapsedRealtime), para el margen del apagado. */
    private var ultimaVezSonando = 0L
    private var reanudando: Job? = null

    private val alServicio = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            servicio = (binder as? MusicService.MusicBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            servicio = null
        }
    }

    private fun reproductor(): Player? = runCatching { servicio?.player }.getOrNull()

    /** Se llama una vez por proceso (InicioDelCarro). */
    fun iniciar(context: Context) {
        if (iniciado) return
        iniciado = true
        val app = context.applicationContext
        app.bindService(Intent(app, MusicService::class.java), alServicio, Context.BIND_AUTO_CREATE)
        VigiaDelEncendido.registrar(app)
        alcance.launch { latir(app) }
    }

    private suspend fun latir(app: Context) {
        while (true) {
            val player = reproductor()
            if (player != null && player !== oyente) escuchar(app, player)
            val sonando = player?.isPlaying == true
            if (sonando) ultimaVezSonando = SystemClock.elapsedRealtime()
            val ahora = System.currentTimeMillis()
            runCatching {
                app.dataStore.edit {
                    it[CarroVivoKey] = ahora
                    if (sonando) it[CarroSonabaKey] = ahora
                }
            }
            delay(LATIDO_MS)
        }
    }

    /** Al pausar se anota la hora exacta: el latido podria llegar hasta 15 s tarde. */
    private fun escuchar(app: Context, player: Player) {
        oyente = player
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) return
                ultimaVezSonando = SystemClock.elapsedRealtime()
                val ahora = System.currentTimeMillis()
                alcance.launch { runCatching { app.dataStore.edit { it[CarroSonabaKey] = ahora } } }
            }
        })
    }

    /** El radio arranco de cero (BOOT_COMPLETED). */
    fun alArrancar(context: Context) {
        val app = context.applicationContext
        iniciar(app)
        alcance.launch {
            val prefs = app.dataStore.data.first()
            val vivo = prefs[CarroVivoKey] ?: 0L
            val sonaba = prefs[CarroSonabaKey] ?: 0L
            val sonabaAlApagar = sonaba > 0L && vivo - sonaba <= MARGEN_SONABA_MS
            val es = Bitacora.enIdiomaDeLaApp(app)
            Bitacora.anotar(
                app,
                es.getString(R.string.carro_bitacora_arranque_radio, es.getString(if (sonabaAlApagar) R.string.carro_bitacora_sonaba else R.string.carro_bitacora_en_pausa)),
            )
            val modo = modoActual(app)
            if (modo == AlEncender.NADA) return@launch
            abrirPantalla(app)
            if (modo == AlEncender.SEGUIR_SONANDO) reanudar(app, sonabaAlApagar)
        }
    }

    /** La pantalla (o el ACC) se apago. [fuente] es lo que lo dijo, para la bitacora. */
    fun alApagar(context: Context, fuente: String) {
        if (apagadoEn != 0L) return
        val ahora = SystemClock.elapsedRealtime()
        apagadoEn = ahora
        sonabaAlApagar = reproductor()?.isPlaying == true || ahora - ultimaVezSonando < MARGEN_SONABA_MS
        reanudando?.cancel()
        val es = Bitacora.enIdiomaDeLaApp(context)
        Bitacora.anotar(
            context,
            es.getString(R.string.carro_bitacora_apagado, fuente, es.getString(if (sonabaAlApagar) R.string.carro_bitacora_sonaba else R.string.carro_bitacora_en_pausa)),
        )
    }

    /** La pantalla (o el ACC) volvio. Solo cuenta como «encender el carro» si estuvo apagada un rato. */
    fun alEncender(context: Context, fuente: String) {
        val desde = apagadoEn
        if (desde == 0L) return
        apagadoEn = 0L
        val apagadoMs = SystemClock.elapsedRealtime() - desde
        val es = Bitacora.enIdiomaDeLaApp(context)
        Bitacora.anotar(context, es.getString(R.string.carro_bitacora_encendido, fuente, apagadoMs / 1000))
        if (apagadoMs < MINIMO_APAGADO_MS) return
        alcance.launch {
            val modo = modoActual(context)
            if (modo == AlEncender.NADA) return@launch
            abrirPantalla(context)
            if (modo == AlEncender.SEGUIR_SONANDO) reanudar(context, sonabaAlApagar)
        }
    }

    private suspend fun modoActual(context: Context): AlEncender =
        context.dataStore.data.first()[CarroAlEncenderKey]
            ?.let { nombre -> AlEncender.entries.firstOrNull { it.name == nombre } }
            ?: AlEncender.SEGUIR_SONANDO

    /** Hasta Android 9 una app puede abrir su pantalla desde segundo plano; despues, solo suena. */
    private fun abrirPantalla(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        runCatching {
            context.startActivity(
                Intent(context, CarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    private fun reanudar(context: Context, sonabaAlApagar: Boolean) {
        reanudando?.cancel()
        reanudando = alcance.launch {
            val es = Bitacora.enIdiomaDeLaApp(context)
            fun noSe(motivo: Int) = Bitacora.anotar(context, es.getString(R.string.carro_bitacora_no_reanudo, es.getString(motivo)))

            if (!sonabaAlApagar) return@launch noSe(R.string.carro_motivo_pausa)
            // El servicio restaura la cola solo, en pausa; se espera a que este.
            val player = withTimeoutOrNull(ESPERA_COLA_MS) {
                var p = reproductor()
                while (p == null || p.mediaItemCount == 0) {
                    delay(250)
                    p = reproductor()
                }
                p
            } ?: return@launch noSe(R.string.carro_motivo_cola_vacia)
            // Unos segundos para que el radio termine de poner su lanzador o su FM.
            delay(ESPERA_MS)
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when {
                player.isPlaying -> return@launch noSe(R.string.carro_motivo_ya_sonaba)
                audio.mode != AudioManager.MODE_NORMAL -> return@launch noSe(R.string.carro_motivo_llamada)
                audio.isMusicActive -> return@launch noSe(R.string.carro_motivo_otra_musica)
            }
            // Arranca en silencio y sube suave: nada de golpes al encender.
            player.volume = 0f
            player.play()
            Bitacora.anotar(context, es.getString(R.string.carro_bitacora_reanudado, player.mediaMetadata.title ?: "?"))
            VocesDeCarretera.decir(VozDeCarretera.RETOMANDO, 8_000L)
            try {
                withTimeoutOrNull(10_000L) { while (!player.isPlaying) delay(100) }
                val pasos = 60
                for (i in 1..pasos) {
                    player.volume = sin((i.toFloat() / pasos) * (PI.toFloat() / 2))
                    delay(RAMPA_MS / pasos)
                }
            } finally {
                // Pase lo que pase (cancelado, error), nunca se queda mudo.
                player.volume = 1f
            }
        }
    }
}

/** Arranque en frio del radio. Desde Android 8, BOOT_COMPLETED sigue llegando a un receptor del manifiesto. */
class EncendidoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            -> Encendido.alArrancar(context)
        }
    }
}

/**
 * La pantalla y los avisos de ACC de los radios que se duermen. Registrado en
 * tiempo de ejecucion: SCREEN_ON/OFF nunca llegan a un receptor del manifiesto,
 * y los avisos de fabricante tampoco desde Android 8. La lista de avisos se
 * afina con lo que se vea en el radio de CG (tanda 8).
 */
private object VigiaDelEncendido : BroadcastReceiver() {
    private val ENCENDIDO = setOf(
        Intent.ACTION_SCREEN_ON,
        "autochips.intent.action.QB_POWERON", // MediaTek/Autochips (AC8227L…)
        "com.fyt.boot.ACCON", // FYT (UIS7862…)
    )
    private val APAGADO = setOf(
        Intent.ACTION_SCREEN_OFF,
        "autochips.intent.action.QB_POWEROFF",
        "com.fyt.boot.ACCOFF",
        Intent.ACTION_SHUTDOWN,
    )

    private var registrado = false

    fun registrar(context: Context) {
        if (registrado) return
        val filtro = IntentFilter().apply { (ENCENDIDO + APAGADO).forEach(::addAction) }
        ContextCompat.registerReceiver(context, this, filtro, ContextCompat.RECEIVER_EXPORTED)
        registrado = true
    }

    override fun onReceive(context: Context, intent: Intent) {
        val accion = intent.action ?: return
        val fuente = when (accion) {
            Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> Bitacora.enIdiomaDeLaApp(context).getString(R.string.carro_fuente_pantalla)
            else -> accion
        }
        when (accion) {
            in ENCENDIDO -> Encendido.alEncender(context, fuente)
            in APAGADO -> Encendido.alApagar(context, fuente)
        }
    }
}
