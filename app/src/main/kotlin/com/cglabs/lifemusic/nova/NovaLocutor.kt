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
 * Dos maneras de hablar:
 * - **Con Nova al aire** (la encendiste desde el Inicio): habla al empezar cada
 *   bloque, que dura 3 o 4 canciones, y al encenderla o cambiar de onda.
 * - **Sin Nova al aire**, pero con el interruptor prendido: cada [CADA] canciones
 *   que terminan solas dice el puente (lo que sono y lo que suena).
 *
 * Siempre igual que en la radio: baja en 0,4 s, habla, sube en 0,6 s. Si la
 * persona toca algo mientras habla (salta, adelanta, elige otra, pausa), Nova
 * se calla al instante y la musica vuelve a su volumen.
 *
 * Ficha: docs/versiones/2.0/nova.md.
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

    /** Por que habla esta vez. */
    private enum class Motivo { PUENTE, BLOQUE, ENCENDER, ONDA }

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
        NovaSesion.sonando(pista.id)
        if (pista.id == actual?.id) return
        previa = actual
        actual = pista
    }

    /** Termino una cancion sola (o la mezclo el Automix) y empezo la siguiente. */
    fun cambioNatural() {
        if (!activa) return
        if (NovaSesion.alAire.value) {
            // Al aire habla al empezar cada bloque; si no empieza uno, no dice nada.
            hablar(Motivo.BLOQUE)
            return
        }
        cancionesSinHablar++
        if (cancionesSinHablar < CADA) return
        cancionesSinHablar = 0
        hablar(Motivo.PUENTE)
    }

    /** Se encendio Nova desde el Inicio, o se cambio de onda: anuncia el bloque que arranca. */
    fun anunciarBloque(alEncender: Boolean) {
        if (!activa) return
        hablar(if (alEncender) Motivo.ENCENDER else Motivo.ONDA)
    }

    /** La persona hizo algo: Nova se calla ya y la musica vuelve. */
    fun callar() {
        trabajo?.cancel()
        trabajo = null
    }

    fun apagar() {
        activar(false)
    }

    private fun hablar(motivo: Motivo) {
        callar()
        trabajo = scope.launch {
            var bajo = false
            try {
                // Al encender o cambiar de onda, la cola nueva puede tardar en cargar
                // (Algo nuevo va por la red): se espera hasta que suene una de Nova.
                val idNueva = if (motivo == Motivo.ENCENDER || motivo == Motivo.ONDA) {
                    esperarUnaDeNova()
                } else {
                    esperarLaNueva()
                } ?: return@launch
                val nueva = actual ?: return@launch
                val anterior = previa?.takeIf { it.id != nueva.id }

                val texto = when (motivo) {
                    Motivo.PUENTE -> {
                        val eleccion = elegirPuente()
                        if (!yaSePresento) {
                            NovaFrases.presentacion(LocalTime.now().hour, nombre, anterior, nueva, eleccion)
                        } else {
                            NovaFrases.puente(anterior, nueva, eleccion)
                        }
                    }
                    Motivo.BLOQUE -> {
                        if (!NovaSesion.esInicioDeBloque(idNueva)) return@launch
                        val bloque = NovaSesion.bloqueDe(idNueva) ?: return@launch
                        val eso = anterior?.let { "Eso fue ${NovaFrases.tituloHablado(it.titulo)}. " }.orEmpty()
                        eso + NovaFrases.anuncio(bloque, NovaSesion.datoDe(idNueva), nueva)
                    }
                    Motivo.ENCENDER, Motivo.ONDA -> {
                        val bloque = NovaSesion.bloqueDe(idNueva) ?: return@launch
                        val cabeza = if (motivo == Motivo.ENCENDER) {
                            val saludo = NovaFrases.saludo(LocalTime.now().hour)
                            (if (nombre != null) "$saludo, $nombre." else "$saludo.") + " Soy Nova, tu DJ. "
                        } else {
                            "Cambio de onda. "
                        }
                        cabeza + NovaFrases.anuncio(bloque, NovaSesion.datoDe(idNueva), nueva)
                    }
                }

                val archivo = File(contexto.cacheDir, "nova/frase.wav")
                val inicio = SystemClock.elapsedRealtime()
                if (!voz.sintetizar(texto, archivo)) {
                    android.util.Log.w(ETIQUETA, "sin voz esta vez (idioma=${voz.idioma})")
                    return@launch
                }
                android.util.Log.i(ETIQUETA, "frase ($motivo) lista en ${SystemClock.elapsedRealtime() - inicio} ms")

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
                val hablando = SystemClock.elapsedRealtime()
                decir(archivo, idNueva)
                mezcla.llevarA(1f, SUBIR_MS)
                bajo = false
                android.util.Log.i(ETIQUETA, "hable ${SystemClock.elapsedRealtime() - hablando} ms")
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

    /** Espera hasta 20 s a que suene una cancion armada por Nova. */
    private suspend fun esperarUnaDeNova(): String? {
        val limite = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < limite) {
            val id = mezcla.idSonando()
            if (id != null && id == actual?.id && NovaSesion.esDeNova(id)) return id
            delay(100)
        }
        android.util.Log.i(ETIQUETA, "no sono ninguna de Nova a tiempo")
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

        /** Sin Nova al aire: cada cuantas canciones dice el puente (en la tanda 5 se ajusta de 2 a 8). */
        const val CADA = 3

        /** La musica mientras Nova habla: 30 % del volumen de la persona. */
        const val NIVEL_MUSICA = 0.3f
        const val BAJAR_MS = 400L
        const val SUBIR_MS = 600L

        /** Si la nueva no suena en este tiempo, esa vez no habla. */
        const val ESPERA_SONAR_MS = 10_000L

        /** Ninguna frase dura mas que esto: si el motor se cuelga, la musica vuelve igual. */
        const val MAXIMO_HABLANDO_MS = 18_000L
    }
}
