package com.cglabs.lifemusic.nova

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import com.cglabs.lifemusic.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import kotlin.random.Random

/**
 * Nova al aire. Vive en MusicService, asi habla tambien con la pantalla apagada.
 *
 * En cada cambio NATURAL de cancion (la anterior termino sola o la mezclo el
 * Automix) cuenta; cada [CADA] canciones prepara una frase cierta, la convierte
 * en voz y la dice encima del arranque de la nueva, con la musica al 30 %. Como
 * en la radio: baja en 0,4 s, habla, sube en 0,6 s. La primera vez despues de
 * prenderla se presenta.
 *
 * Si la persona toca algo mientras habla (salta, adelanta, elige otra, pausa),
 * Nova se calla al instante y la musica vuelve a su volumen.
 *
 * Tanda 0: voz del telefono y una frase puente. Los bloques, Life Line y la
 * pantalla llegan despues. Ficha: docs/versiones/2.0/nova.md.
 */
class NovaLocutor(
    context: Context,
    private val scope: CoroutineScope,
    private val mezcla: Mezcla,
) {
    /** Lo que Nova necesita del reproductor. Lo implementa MusicService. */
    interface Mezcla {
        /** Se puede hablar ahora: suena, sin transicion ni fundido, sin TV, sin silencio, sin otra app hablando. */
        fun puedeHablar(): Boolean

        /** Sigue sonando (para callarse si la persona pausa). */
        fun sigueSonando(): Boolean

        /** El id de la cancion que suena. */
        fun idSonando(): String?

        /** Lleva la musica a [nivel] (1 = el volumen de la persona) en [ms], con rampa. */
        suspend fun llevarA(nivel: Float, ms: Long)

        /** Devuelve el volumen normal ya, sin rampa: la red de seguridad. */
        fun devolver()
    }

    private val contexto = context.applicationContext
    private val voz = NovaVoz(contexto)
    private var activa = false
    private var trabajo: Job? = null
    private var reproductor: MediaPlayer? = null

    /** Las dos ultimas canciones que se vieron sonar, para el puente. */
    private var previa: NovaFrases.Pista? = null
    private var actual: NovaFrases.Pista? = null

    private var cancionesSinHablar = 0
    private var yaSePresento = false
    private var ultimoPuente = -1

    /** Como le dice Nova a la persona (por ahora, el primer nombre de la cuenta). */
    var nombre: String? = null

    fun activar(prendida: Boolean) {
        if (prendida == activa) return
        activa = prendida
        if (prendida) {
            voz.prender()
            // La primera cancion que termine despues de prenderla ya trae presentacion.
            cancionesSinHablar = CADA - 1
            yaSePresento = false
        } else {
            callar()
            voz.apagar()
        }
        android.util.Log.i(ETIQUETA, "activa=$prendida")
    }

    /** Lo que suena ahora. MusicService lo avisa cada vez que cambia la cancion, por la razon que sea. */
    fun sonando(meta: MediaMetadata?) {
        val pista = meta?.let { m -> NovaFrases.Pista(m.id, m.title, m.artists.map { it.name }) } ?: return
        if (pista.id == actual?.id) return
        previa = actual
        actual = pista
    }

    /** Termino una cancion sola (o la mezclo el Automix) y empezo la siguiente. */
    fun cambioNatural() {
        if (!activa) return
        cancionesSinHablar++
        if (cancionesSinHablar < CADA) return
        cancionesSinHablar = 0
        hablar()
    }

    /** La persona hizo algo: Nova se calla ya y la musica vuelve. */
    fun callar() {
        trabajo?.cancel()
        trabajo = null
    }

    fun apagar() {
        activar(false)
    }

    private fun hablar() {
        callar()
        trabajo = scope.launch {
            var bajo = false
            try {
                // El aviso puede llegar antes de que «sonando» vea la nueva: se espera un poco.
                val idNueva = esperarLaNueva() ?: return@launch
                val nueva = actual ?: return@launch
                val anterior = previa?.takeIf { it.id != nueva.id }

                val eleccion = elegirPuente()
                val texto = if (!yaSePresento) {
                    NovaFrases.presentacion(LocalTime.now().hour, nombre, anterior, nueva, eleccion)
                } else {
                    NovaFrases.puente(anterior, nueva, eleccion)
                }
                val archivo = File(contexto.cacheDir, "nova/frase.wav")
                val inicio = SystemClock.elapsedRealtime()
                if (!voz.sintetizar(texto, archivo)) {
                    android.util.Log.w(ETIQUETA, "sin voz esta vez (idioma=${voz.idioma})")
                    return@launch
                }
                android.util.Log.i(ETIQUETA, "frase lista en ${SystemClock.elapsedRealtime() - inicio} ms")

                // Esperar a que la nueva suene de verdad y nada este mezclando.
                val limite = SystemClock.elapsedRealtime() + ESPERA_SONAR_MS
                while (!mezcla.puedeHablar()) {
                    if (mezcla.idSonando() != idNueva || SystemClock.elapsedRealtime() > limite) {
                        android.util.Log.i(ETIQUETA, "no hablo: la cancion cambio o no arranco")
                        return@launch
                    }
                    delay(100)
                }
                if (mezcla.idSonando() != idNueva) return@launch

                bajo = true
                mezcla.llevarA(NIVEL_MUSICA, BAJAR_MS)
                yaSePresento = true
                decir(archivo, idNueva)
                mezcla.llevarA(1f, SUBIR_MS)
                bajo = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un motor de voz raro nunca puede tumbar la musica: se calla y sigue.
                android.util.Log.w(ETIQUETA, "fallo al hablar", e)
            } finally {
                soltarReproductor()
                if (bajo) mezcla.devolver()
            }
        }
    }

    /** Espera hasta 2 s a que la cancion que suena sea la que vio «sonando». */
    private suspend fun esperarLaNueva(): String? {
        val limite = SystemClock.elapsedRealtime() + 2_000
        while (SystemClock.elapsedRealtime() < limite) {
            val id = mezcla.idSonando()
            if (id != null && id == actual?.id) return id
            delay(50)
        }
        return null
    }

    private fun elegirPuente(): Int {
        var eleccion = Random.nextInt(NovaFrases.PUENTES)
        if (eleccion == ultimoPuente) eleccion = (eleccion + 1) % NovaFrases.PUENTES
        ultimoPuente = eleccion
        return eleccion
    }

    /** Reproduce la frase; vuelve cuando termina o cuando la persona pausa o cambia de cancion. */
    private suspend fun decir(archivo: File, idNueva: String) {
        val mp = MediaPlayer()
        reproductor = mp
        var termino = false
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setDataSource(archivo.path)
        mp.setOnCompletionListener { termino = true }
        mp.prepare()
        mp.start()
        val limite = SystemClock.elapsedRealtime() + MAXIMO_HABLANDO_MS
        while (!termino && SystemClock.elapsedRealtime() < limite) {
            if (!mezcla.sigueSonando() || mezcla.idSonando() != idNueva) {
                android.util.Log.i(ETIQUETA, "me callo: pausa o cambio de cancion")
                return
            }
            delay(50)
        }
    }

    private fun soltarReproductor() {
        reproductor?.let { mp -> runCatching { mp.stop() }; runCatching { mp.release() } }
        reproductor = null
    }

    companion object {
        private const val ETIQUETA = "LifeMusicNova"

        /** Cada cuantas canciones habla (en la tanda 5 se ajusta de 2 a 8). */
        const val CADA = 3

        /** La musica mientras Nova habla: 30 % del volumen de la persona. */
        const val NIVEL_MUSICA = 0.3f
        const val BAJAR_MS = 400L
        const val SUBIR_MS = 600L

        /** Si la nueva no suena en este tiempo, esa vez no habla. */
        const val ESPERA_SONAR_MS = 10_000L

        /** Ninguna frase dura mas que esto: si el motor se cuelga, la musica vuelve igual. */
        const val MAXIMO_HABLANDO_MS = 15_000L
    }
}
