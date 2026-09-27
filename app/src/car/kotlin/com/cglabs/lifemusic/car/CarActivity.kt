package com.cglabs.lifemusic.car

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import com.cglabs.lifemusic.db.MusicDatabase
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
        setContent { PantallaCarro(conexion) }
        ocultarBarraDeEstado()
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

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MusicService::class.java), alServicio, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        unbindService(alServicio)
        super.onStop()
    }
}
