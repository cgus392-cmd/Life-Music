package com.cglabs.lifemusic.car

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.cglabs.lifemusic.BuildConfig
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.AppLanguageKey
import com.cglabs.lifemusic.constants.SYSTEM_DEFAULT
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** Una linea de la bitacora: cuando (reloj de pared) y que paso. */
data class Suceso(val cuando: Long, val texto: String)

/**
 * La bitacora del carro: los ultimos sucesos que importan para entender un radio
 * (abrir, encender, apagar, botones que llegan, canciones guardadas…), con su
 * hora. Se ve en Ajustes › Diagnostico.
 *
 * Existe porque en el radio no hay cable ni adb: cada marca se porta distinto al
 * encender y con el volante, y la unica forma de saberlo es que la app lo anote y
 * CG lo lea en la pantalla. Nunca se anota nada de la cuenta ni datos personales.
 *
 * Se guarda en un archivo pequeno (las ultimas [MAXIMO]) para sobrevivir a un
 * reinicio del radio, que es justo cuando mas interesa. Lo nuevo va primero.
 */
object Bitacora {
    private const val MAXIMO = 50
    private const val ARCHIVO = "bitacora-carro.txt"
    private const val ETIQUETA = "LifeMusicCarro"

    private val _sucesos = MutableStateFlow<List<Suceso>>(emptyList())
    val sucesos: StateFlow<List<Suceso>> = _sucesos.asStateFlow()

    /** Un solo hilo para el disco: las escrituras salen en orden y fuera del hilo principal. */
    private val disco = Executors.newSingleThreadExecutor()

    @Volatile
    private var cargada = false

    fun anotar(context: Context, texto: String) {
        cargar(context)
        val limpio = texto.replace('\n', ' ').replace('\t', ' ')
        Log.i(ETIQUETA, "bitacora: $limpio")
        _sucesos.update { (listOf(Suceso(System.currentTimeMillis(), limpio)) + it).take(MAXIMO) }
        guardar(context)
    }

    fun borrar(context: Context) {
        _sucesos.value = emptyList()
        guardar(context)
    }

    /** Lee el archivo la primera vez (son 50 lineas: no vale la pena otro hilo). */
    fun cargar(context: Context) {
        if (cargada) return
        synchronized(this) {
            if (cargada) return
            val leidos = runCatching {
                archivo(context).takeIf { it.exists() }?.readLines()?.mapNotNull { linea ->
                    val corte = linea.indexOf('\t')
                    if (corte <= 0) return@mapNotNull null
                    linea.substring(0, corte).toLongOrNull()?.let { Suceso(it, linea.substring(corte + 1)) }
                }
            }.getOrNull().orEmpty()
            _sucesos.update { (it + leidos).take(MAXIMO) }
            cargada = true
        }
    }

    private fun guardar(context: Context) {
        val copia = _sucesos.value
        val destino = archivo(context)
        disco.execute {
            runCatching { destino.writeText(copia.joinToString("\n") { "${it.cuando}\t${it.texto}" }) }
                .onFailure { Log.w(ETIQUETA, "no se pudo guardar la bitacora", it) }
        }
    }

    private fun archivo(context: Context) = File(context.applicationContext.filesDir, ARCHIVO)

    /**
     * Un contexto en el idioma elegido en Ajustes. CarActivity cambia el suyo,
     * pero lo que se anota desde fuera de la pantalla (la sesion de medios, el
     * encendido) usa el de la aplicacion, que sigue en el idioma del radio.
     */
    fun enIdiomaDeLaApp(context: Context): Context {
        val elegido = runCatching { context.dataStore[AppLanguageKey] }.getOrNull()
            ?.takeUnless { it == SYSTEM_DEFAULT } ?: return context
        val configuracion = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(elegido)) }
        return context.createConfigurationContext(configuracion)
    }

    /**
     * La linea de cada arranque en frio: version, Android y el aparato (en los
     * radios chinos el fabricante suele ser «alps» y el hardware dice el chip),
     * mas la version del navegador interno, que decide si se puede iniciar
     * sesion con Google.
     */
    fun anotarArranque(context: Context) {
        val navegador = runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "?"
        val aparato = listOf(Build.MANUFACTURER, Build.MODEL, Build.HARDWARE)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        anotar(
            context,
            enIdiomaDeLaApp(context).getString(R.string.carro_bitacora_arranque,BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, aparato, navegador),
        )
    }
}
