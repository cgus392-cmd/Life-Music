package com.cglabs.lifemusic.car

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import android.content.res.Resources
import com.cglabs.lifemusic.constants.AppLanguageKey
import com.cglabs.lifemusic.constants.SYSTEM_DEFAULT
import androidx.compose.runtime.CompositionLocalProvider
import com.cglabs.lifemusic.db.MusicDatabase
import com.cglabs.lifemusic.playback.DownloadUtil
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.get
import com.cglabs.lifemusic.utils.setAppLocale
import java.util.Locale
import com.cglabs.lifemusic.playback.MusicService
import com.cglabs.lifemusic.playback.MusicService.MusicBinder
import com.cglabs.lifemusic.playback.PlayerConnection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * La unica actividad de Life Music for Car. Se conecta al mismo servicio de
 * reproduccion que la app del telefono (MusicService) y dibuja la interfaz del
 * carro (PantallaCarro). Pantalla completa y siempre encendida: en un radio no
 * hay barra de estado que mirar y la pantalla no debe apagarse en marcha.
 */
@AndroidEntryPoint
class CarActivity : ComponentActivity() {

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var descargas: DownloadUtil

    private var conexion by mutableStateOf<PlayerConnection?>(null)

    private val alServicio = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                conexion = runCatching { PlayerConnection(this@CarActivity, service, database, lifecycleScope) }.getOrNull()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            conexion?.dispose()
            conexion = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        aplicarIdioma()
        setContent { CompositionLocalProvider(LocalDescargas provides descargas) { PantallaCarro(conexion) } }
        ocultarBarraDeEstado()
        vigilarBarraDeEstado()
    }

    /**
     * El idioma elegido en Ajustes (o el del sistema). Muchos radios vienen en
     * ingles de fabrica y cambiarles el idioma del sistema es un laberinto: aqui
     * basta un toque. Se cambia tambien el Locale por defecto para que la fecha
     * del reloj salga en el mismo idioma que el resto.
     */
    private fun aplicarIdioma() {
        val elegido = runCatching { dataStore[AppLanguageKey] }.getOrNull()?.takeUnless { it == SYSTEM_DEFAULT }
        val idioma = elegido?.let { Locale.forLanguageTag(it) } ?: Resources.getSystem().configuration.locales[0]
        Locale.setDefault(idioma)
        setAppLocale(this, idioma)
    }

    /**
     * En Android 8 (lo que corren los radios) la barra de estado vuelve cada vez
     * que la ventana recupera el foco (un aviso, el teclado, volver de otra app):
     * se oculta de nuevo. La de navegacion se deja: muchos radios no tienen otro
     * boton de inicio, y la pantalla se aparta de ella (PantallaCarro).
     */
    private fun ocultarBarraDeEstado() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ocultarBarraDeEstado()
    }

    /**
     * El teclado tambien la trae de vuelta, y ahi la ventana no pierde el foco:
     * se vigila la propia barra y, si aparece, se esconde un momento despues (lo
     * justo para no pelear con el sistema mientras el teclado entra). La API es
     * vieja, pero es la de Android 8, que es lo que corren los radios.
     */
    @Suppress("DEPRECATION")
    private fun vigilarBarraDeEstado() {
        val decor = window.decorView
        decor.setOnSystemUiVisibilityChangeListener { visibilidad ->
            if (visibilidad and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                decor.removeCallbacks(reocultar)
                decor.postDelayed(reocultar, 1_500)
            }
        }
    }

    private val reocultar = Runnable { ocultarBarraDeEstado() }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MusicService::class.java), alServicio, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        unbindService(alServicio)
        super.onStop()
    }
}
