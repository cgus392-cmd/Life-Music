package com.cglabs.lifemusic.nova

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import com.cglabs.lifemusic.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalTime
import kotlin.random.Random

/**
 * Nova al aire. Vive en MusicService, asi habla tambien con la pantalla apagada.
 *
 * Dos maneras de hablar:
 * - **Con Nova al aire** (la encendiste desde el Inicio): habla al empezar cada
 *   bloque, que dura 3 o 4 canciones, y al encenderla o cambiar de onda.
 * - **Sin Nova al aire**, pero con el interruptor prendido: cada [cada] canciones
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

        /** Cuanto le falta a la que suena, o null si no se sabe. */
        fun restanteMs(): Long?

        /** La que viene en la cola. */
        fun siguiente(): MediaMetadata?

        /** Cuando empieza a cantar [id], segun su letra sincronizada guardada; null sin letra. */
        suspend fun primeraLetraMs(id: String): Long?

        /** Hay transicion suave o Automix: el final de la que suena no queda libre. */
        fun mezclaActiva(): Boolean

        /** Se esta transmitiendo al TV: la voz sonaria en el telefono, asi que solo subtitulos. */
        fun transmitiendo(): Boolean

        /** Detiene la que acaba de arrancar (si va en sus primeros segundos) mientras Nova termina su frase. */
        fun retener(): Boolean

        /** Sigue la que se retuvo. */
        fun soltarRetencion()

        /** Lo que se sabe de verdad de una cancion (me gusta, cuantas veces, cuando). */
        suspend fun hechos(id: String): NovaFrases.Hechos?

        /** Para el registro: que condicion impide hablar ahora. */
        fun porQueNoHabla(): String
    }

    /** Por que habla esta vez. */
    private enum class Motivo { PUENTE, BLOQUE, ENCENDER, ONDA }

    private val contexto = context.applicationContext
    private val voz = NovaVoz(contexto)

    /** La voz HD (Piper con sherpa-onnx), si la persona bajo y eligio una. */
    private val vozHD = NovaVozHD(contexto)

    /** La voz HD elegida y su calidad, o null para la del telefono. */
    private var elegidaHD: Pair<VozHD, CalidadVoz>? = null
    private var activa = false
    private var trabajo: Job? = null
    private var reproductor: MediaPlayer? = null

    /** Las dos ultimas canciones que se vieron sonar, para el puente. */
    private var previa: NovaFrases.Pista? = null
    private var actual: NovaFrases.Pista? = null

    private var cancionesSinHablar = 0
    private var yaSePresento = false
    private var ultimoPuente = -1

    /** Lo ultimo que dijo, para repetirlo si tocan el subtitulo. */
    private var ultimoTexto: String? = null

    /** Vigila el final de cada cancion, por si la siguiente arranca cantando. */
    private var vigia: Job? = null

    /** El cambio que ya se reviso (id de la que viene), para no revisarlo dos veces. */
    private var revisadaPara: String? = null

    /** Lo que ya se dijo sobre el final de la anterior: al entrar esta, no se repite. */
    private var anunciadaAntes: String? = null

    /** Entradas que arrancan cantando con mezcla activa: se anuncian corto. */
    private val cortas = mutableSetOf<String>()

    /** Una frase lista para decir sobre el final de la que suena. */
    private class Salida(val id: String, val texto: String, val archivo: File, val duracionMs: Long)
    private var salida: Salida? = null

    /**
     * La frase del proximo cambio, ya convertida en voz: se dice sobre la intro de
     * la siguiente sin esperar al motor. Clave con las voces «high» (Daniela),
     * que tardan casi un segundo por cada segundo de voz.
     */
    private var intro: Salida? = null

    /** La frase del proximo cambio que se esta preparando ahora (id de la cancion y su tarea). */
    private var preparando: Pair<String, Job>? = null

    /** Como le dice Nova a la persona (por ahora, el primer nombre de la cuenta). */
    var nombre: String? = null

    /** Como se llama ella (Personalizar). */
    var nombreDJ: String = "Nova"

    /** Cada cuantas canciones habla sin Nova al aire (al aire, lo marca el largo del bloque). */
    var cada: Int = 1

    /** El estilo de Nova (Personalizar): como lo dice; los datos son siempre ciertos. */
    var estilo: NovaFrases.Estilo = NovaFrases.Estilo.CALIDA

    /** Solo subtitulos: no habla ni baja la musica; lo que diria sale escrito en Life Line. */
    var soloSubtitulos: Boolean = false

    /** A que ritmo habla, elegido en Personalizar («Ritmo de Nova»). Vale para la frase siguiente. */
    var ritmo: RitmoNova = RitmoNova.NORMAL

    /** El acento de la voz del telefono: «latam» o «espana». */
    fun region(region: String) = voz.cambiarRegion(region)

    /**
     * La voz elegida en Personalizar: «id:calidad» de una voz HD, o vacio para la
     * del telefono. Si la HD no esta en el telefono (se borro), vuelve a la del telefono.
     */
    fun elegirVoz(valor: String?) {
        // Una voz que este telefono no mueve bien no se usa nunca: vuelve a la del telefono.
        val elegida = NovaVocesHD.leer(valor)?.takeIf { (v, c) ->
            NovaCapacidad.veredictoDe(contexto, v.clave(c)) != NovaCapacidad.Veredicto.NO_APTA
        }
        val nueva = elegida
        if (nueva == elegidaHD) return
        elegidaHD = nueva
        android.util.Log.i(ETIQUETA, "voz elegida: ${nueva?.let { (v, c) -> v.clave(c) } ?: "telefono"}")
        scope.launch {
            if (nueva != null) runCatching { vozHD.precargar(nueva.first, nueva.second) } else vozHD.soltar()
        }
    }

    /** Sintetiza con la voz elegida; si la HD falla, con la del telefono, para no quedarse muda. */
    private suspend fun sintetizarCon(texto: String, archivo: File): Boolean {
        val hd = elegidaHD
        if (hd != null) {
            // Si se cancela (la persona cambio de onda otra vez), no es un fallo: se sigue cancelando.
            val ok = try {
                vozHD.sintetizar(texto, archivo, hd.first, hd.second, ritmo.vozHD)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(ETIQUETA, "la voz HD dio error", e)
                false
            }
            if (ok) return true
            android.util.Log.w(ETIQUETA, "la voz HD fallo; esta vez uso la del telefono")
        }
        return voz.sintetizar(texto, archivo, ritmo.telefono)
    }

    fun activar(prendida: Boolean) {
        if (prendida == activa) return
        activa = prendida
        if (prendida) {
            voz.prender()
            // La primera cancion que termine despues de prenderla ya trae presentacion.
            cancionesSinHablar = cada - 1
            yaSePresento = false
            vigilarFinales()
        } else {
            callar()
            vigia?.cancel()
            vigia = null
            preparando?.second?.cancel()
            preparando = null
            salida = null
            intro = null
            voz.apagar()
            scope.launch { vozHD.soltar() }
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
        val entra = mezcla.idSonando()
        if (entra != null && entra == anunciadaAntes) {
            anunciadaAntes = null
            cancionesSinHablar = 0
            return
        }
        // Al aire, la que abre un bloque se anuncia como estacion; las demas, con su estilo.
        if (NovaSesion.alAire.value && NovaSesion.esInicioDeBloque(entra)) {
            cancionesSinHablar = 0
            hablar(Motivo.BLOQUE)
            return
        }
        cancionesSinHablar++
        if (cancionesSinHablar < cada) return
        cancionesSinHablar = 0
        hablar(Motivo.PUENTE)
    }

    /** Una frase lista para salir al aire apenas suene [id] ([archivo] null: solo subtitulos). */
    class Anuncio(val id: String, val texto: String, val archivo: File?)

    /**
     * Prepara el anuncio de encender o cambiar de onda ANTES de que arranque la
     * cancion (pedido de CG, 15-10: en telefonos lentos el anuncio sonaba cuando
     * la cancion ya iba avanzada). Mientras tanto Nova piensa, y la que sonaba
     * sigue sonando. Si la voz tarda mas de [ESPERA_MAXIMA_ARRANQUE_MS], la musica
     * arranca igual y el anuncio sale solo escrito (archivo null).
     */
    suspend fun prepararAnuncio(alEncender: Boolean, primera: MediaMetadata): Anuncio? {
        if (!activa) return null
        val bloque = NovaSesion.bloqueDe(primera.id) ?: return null
        val nueva = NovaFrases.Pista(primera.id, primera.title, primera.artists.map { it.name })
        val cabeza = if (alEncender) {
            val saludo = NovaFrases.saludo(LocalTime.now().hour)
            (if (nombre != null) "$saludo, $nombre." else "$saludo.") + " Soy $nombreDJ. "
        } else {
            "Cambio de onda. "
        }
        val anuncio = NovaFrases.anuncio(bloque, NovaSesion.datoDe(primera.id), nueva)
        // Con una voz lenta, la frase larga podria pasarse del tope y la musica arrancaria sin
        // Nova (paso con Daniela: 8,6 s de voz en 12 s). Se elige la mas larga que alcanza.
        val texto = listOf(
            cabeza + anuncio,
            (if (alEncender) "Soy $nombreDJ. " else "") + anuncio,
            anuncio,
        ).firstOrNull { alcanza(it) } ?: anuncio
        if (soloSubtitulos || mezcla.transmitiendo()) return Anuncio(primera.id, texto, null)
        val archivo = File(contexto.cacheDir, "nova/anuncio.wav")
        val inicio = SystemClock.elapsedRealtime()
        NovaSesion.pensar(true)
        try {
            val ok = sintetizarConTope(texto, archivo, esperaMaxima())
            val falta = PENSAR_MINIMO_MS - (SystemClock.elapsedRealtime() - inicio)
            if (falta > 0) delay(falta)
            android.util.Log.i(ETIQUETA, "anuncio ${if (ok) "listo" else "no alcanzo: va en subtitulos"} en ${SystemClock.elapsedRealtime() - inicio} ms; recien ahora arranca la musica")
            // Si la voz no alcanzo (CG, 27-10: cambio de onda y de voz seguidos, «duro un
            // microsegundo y no hizo mas nada»), Nova no se queda muda: lo dice escrito en
            // Life Line sobre el arranque, como en «solo subtitulos». Nunca suena tarde.
            return Anuncio(primera.id, texto, if (ok) archivo else null)
        } finally {
            NovaSesion.pensar(false)
        }
    }

    /** Dice el anuncio ya preparado apenas suene su cancion: sobre el primer segundo, nunca tarde. */
    fun decirAnuncio(anuncio: Anuncio) {
        if (!activa) return
        callar()
        trabajo = scope.launch {
            try {
                val limite = SystemClock.elapsedRealtime() + 20_000L
                while (mezcla.idSonando() != anuncio.id) {
                    if (SystemClock.elapsedRealtime() > limite) return@launch
                    delay(50)
                }
                yaSePresento = true
                if (anuncio.archivo == null) {
                    ultimoTexto = null
                    mostrarSinVoz(anuncio.texto, anuncio.id)
                    return@launch
                }
                ultimoTexto = anuncio.texto
                salirAlAire(anuncio.texto, anuncio.archivo, anuncio.id, ESPERA_SONAR_MS)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(ETIQUETA, "fallo el anuncio", e)
            }
        }
    }

    /**
     * Sintetiza con un tope de tiempo de verdad. El motor nativo no se deja
     * interrumpir, asi que corre aparte y aqui solo se espera hasta el tope: si no
     * alcanza, la musica sigue y esa frase se descarta cuando termine.
     */
    private suspend fun sintetizarConTope(texto: String, archivo: File, topeMs: Long): Boolean {
        val diferido = scope.async { sintetizarCon(texto, archivo) }
        return withTimeoutOrNull(topeMs) { diferido.await() } ?: run {
            android.util.Log.i(ETIQUETA, "la voz no alcanzo en $topeMs ms")
            false
        }
    }

    /**
     * Si la frase alcanza a estar lista dentro del tope, segun lo que midio la prueba
     * de esta voz en este telefono (unos 14 caracteres por segundo de voz, con margen).
     */
    private fun alcanza(texto: String): Boolean {
        val (voz, calidad) = elegidaHD ?: return true
        val porSegundo = NovaCapacidad.medido(contexto, voz.clave(calidad)) ?: return true
        // Mas pausada, mas segundos de voz por el mismo texto (y mas tiempo para sintetizarlos).
        val segundosDeVoz = texto.length / (14f * ritmo.vozHD)
        val estimadoMs = segundosDeVoz * porSegundo * 1000f * 1.35f
        return estimadoMs <= esperaMaxima() * 0.85f
    }

    /** Cuanto espera la musica a Nova: mas con una voz que tarda en este telefono. */
    private fun esperaMaxima() = if (vozLenta()) ESPERA_MAXIMA_LENTA_MS else ESPERA_MAXIMA_ARRANQUE_MS

    /** La voz elegida tarda en este telefono (HD sin veredicto «fluida»). */
    private fun vozLenta(): Boolean {
        val (voz, calidad) = elegidaHD ?: return false
        return NovaCapacidad.veredictoDe(contexto, voz.clave(calidad)) != NovaCapacidad.Veredicto.FLUIDA
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

                // ¿Se esta preparando justo ahora (voz lenta)? Se espera esa misma, con la
                // cancion retenida, en vez de empezar otra y quedar en fila detras del motor.
                val natural = motivo == Motivo.PUENTE || motivo == Motivo.BLOQUE
                val enCurso = preparando?.takeIf { it.first == idNueva && it.second.isActive }
                if (natural && enCurso != null) {
                    val retenida = mezcla.retener()
                    android.util.Log.i(ETIQUETA, "espero la frase que se estaba preparando" + if (retenida) " (cancion retenida)" else "")
                    NovaSesion.pensar(true)
                    try {
                        withTimeoutOrNull(RETENCION_MAXIMA_MS) { enCurso.second.join() }
                    } finally {
                        NovaSesion.pensar(false)
                        if (retenida) mezcla.soltarRetencion()
                    }
                    if (intro?.id != idNueva) {
                        android.util.Log.i(ETIQUETA, "no hablo: la frase no alcanzo a estar lista (mejor callar que hablar tarde)")
                        return@launch
                    }
                }

                // ¿Se preparo durante la cancion anterior? Entonces sale sin esperar al motor.
                val preparada = intro?.takeIf {
                    (motivo == Motivo.PUENTE || motivo == Motivo.BLOQUE) && it.id == idNueva && it.archivo.exists()
                }
                intro = null
                if (preparada != null) cortas.remove(idNueva)

                val texto = preparada?.texto ?: when (motivo) {
                    Motivo.PUENTE -> {
                        val eleccion = elegirPuente()
                        if (cortas.remove(idNueva) && yaSePresento) {
                            NovaFrases.corto(null, null, nueva)
                        } else if (!yaSePresento) {
                            NovaFrases.presentacion(LocalTime.now().hour, nombre, anterior, nueva, eleccion, nombreDJ)
                        } else {
                            NovaFrases.entreCanciones(estilo, anterior, nueva, mezcla.hechos(idNueva), eleccion)
                        }
                    }
                    Motivo.BLOQUE -> {
                        if (!NovaSesion.esInicioDeBloque(idNueva)) return@launch
                        val bloque = NovaSesion.bloqueDe(idNueva) ?: return@launch
                        // Arranca cantando y hay mezcla: sin «Eso fue», directo al anuncio.
                        val eso = if (cortas.remove(idNueva)) "" else anterior?.let { "Eso fue ${NovaFrases.tituloHablado(it.titulo)}. " }.orEmpty()
                        eso + NovaFrases.anuncio(bloque, NovaSesion.datoDe(idNueva), nueva)
                    }
                    Motivo.ENCENDER, Motivo.ONDA -> {
                        val bloque = NovaSesion.bloqueDe(idNueva) ?: return@launch
                        val cabeza = if (motivo == Motivo.ENCENDER) {
                            val saludo = NovaFrases.saludo(LocalTime.now().hour)
                            (if (nombre != null) "$saludo, $nombre." else "$saludo.") + " Soy $nombreDJ. "
                        } else {
                            "Cambio de onda. "
                        }
                        cabeza + NovaFrases.anuncio(bloque, NovaSesion.datoDe(idNueva), nueva)
                    }
                }

                if (soloSubtitulos || mezcla.transmitiendo()) {
                    ultimoTexto = null
                    mostrarSinVoz(texto, idNueva)
                    return@launch
                }
                val archivo = preparada?.archivo ?: File(contexto.cacheDir, "nova/frase.wav")
                val inicio = SystemClock.elapsedRealtime()
                // Si no estaba lista y la voz tarda en este telefono, la cancion espera a Nova
                // (pedido de CG, 15-10): mejor una pausa de locutor que un anuncio tarde.
                val retenida = preparada == null && vozLenta() && mezcla.retener()
                if (retenida) android.util.Log.i(ETIQUETA, "la cancion espera a que Nova este lista")
                // Mientras prepara la frase, piensa: su mirada sube y una chispa orbita.
                NovaSesion.pensar(true)
                val lista = try {
                    val hecha = preparada != null || if (retenida) {
                        sintetizarConTope(texto, archivo, RETENCION_MAXIMA_MS)
                    } else {
                        sintetizarCon(texto, archivo)
                    }
                    // El motor tarda 0,1 a 0,2 s: se piensa al menos medio segundo, para que se vea.
                    val falta = PENSAR_MINIMO_MS - (SystemClock.elapsedRealtime() - inicio)
                    if (falta > 0) delay(falta)
                    hecha
                } finally {
                    NovaSesion.pensar(false)
                    if (retenida) mezcla.soltarRetencion()
                }
                if (!lista) {
                    android.util.Log.w(ETIQUETA, "sin voz esta vez (idioma=${voz.idioma})")
                    return@launch
                }
                android.util.Log.i(
                    ETIQUETA,
                    "frase ($motivo) lista en ${SystemClock.elapsedRealtime() - inicio} ms" + if (preparada != null) " (preparada antes)" else "",
                )
                ultimoTexto = texto
                salirAlAire(texto, archivo, idNueva, ESPERA_SONAR_MS)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un motor de voz raro nunca puede tumbar la musica: se calla y sigue.
                android.util.Log.w(ETIQUETA, "fallo al hablar", e)
            }
        }
    }

    /**
     * No hablar encima del cantante (ficha 4.4). Cerca del final de cada cancion
     * mira la que viene: si a Nova le toca hablar en ese cambio y la siguiente
     * arranca cantando (su letra sincronizada empieza antes de [ARRANCA_CANTANDO_MS]),
     * prepara la frase y la dice sobre los ultimos segundos de la que suena, como
     * en la radio. Con mezcla activa el final no queda libre: entonces el anuncio
     * se dice corto sobre la intro.
     */
    private fun vigilarFinales() {
        vigia?.cancel()
        vigia = scope.launch {
            while (true) {
                delay(400)
                try {
                    if (!activa || soloSubtitulos || trabajo?.isActive == true) continue
                    if (!mezcla.sigueSonando() || mezcla.transmitiendo()) continue
                    val siguiente = mezcla.siguiente() ?: continue
                    val restante = mezcla.restanteMs() ?: continue
                    val lista = salida
                    if (lista != null && lista.id == siguiente.id) {
                        if (restante <= lista.duracionMs + MARGEN_FINAL_MS) {
                            salida = null
                            hablarSobreElFinal(lista)
                        }
                        continue
                    }
                    if (revisadaPara == siguiente.id || restante !in 6_000L..20_000L) continue
                    revisadaPara = siguiente.id
                    val tarea = scope.launch {
                        try {
                            prepararSobreElFinal(siguiente)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w(ETIQUETA, "fallo preparando la frase", e)
                        }
                    }
                    preparando = siguiente.id to tarea
                    tarea.join()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(ETIQUETA, "fallo la vigia", e)
                }
            }
        }
    }

    /**
     * Prepara la frase del proximo cambio natural, unos segundos antes, para que
     * al llegar el momento no haya que esperar al motor de voz. Si la siguiente
     * arranca cantando y el final esta libre, la frase va sobre el final de la que
     * suena; si no, sobre la intro de la siguiente, como siempre.
     */
    private suspend fun prepararSobreElFinal(siguiente: MediaMetadata) {
        val abreBloque = NovaSesion.alAire.value && NovaSesion.esInicioDeBloque(siguiente.id)
        val toca = abreBloque || cancionesSinHablar + 1 >= cada
        if (!toca) return
        val nueva = NovaFrases.Pista(siguiente.id, siguiente.title, siguiente.artists.map { it.name })
        val bloque = if (abreBloque) NovaSesion.bloqueDe(siguiente.id) else null
        val dato = NovaSesion.datoDe(siguiente.id)
        val primera = mezcla.primeraLetraMs(siguiente.id)
        val cantaPronto = primera != null && primera <= ARRANCA_CANTANDO_MS
        if (cantaPronto) android.util.Log.i(ETIQUETA, "la que viene arranca cantando a los ${primera} ms")

        // Arranca cantando y el final esta libre: sobre el final de la que suena.
        if (cantaPronto && yaSePresento && !mezcla.mezclaActiva()) {
            val texto = NovaFrases.sobreElFinal(actual, nueva, bloque, dato)
            val archivo = File(contexto.cacheDir, "nova/final.wav")
            val duracion = sintetizarYMedir(texto, archivo) ?: return
            salida = Salida(siguiente.id, texto, archivo, duracion)
            android.util.Log.i(ETIQUETA, "frase lista para el final (${duracion} ms)")
            return
        }

        // Sobre la intro de la siguiente, pero ya lista. Con mezcla y la siguiente
        // cantando pronto, el anuncio va corto.
        val corto = cantaPronto && yaSePresento
        if (corto) cortas += siguiente.id
        val texto = when {
            bloque != null -> (if (corto) "" else actual?.let { "Eso fue ${NovaFrases.tituloHablado(it.titulo)}. " }.orEmpty()) +
                NovaFrases.anuncio(bloque, dato, nueva)
            corto -> NovaFrases.corto(null, null, nueva)
            !yaSePresento -> NovaFrases.presentacion(LocalTime.now().hour, nombre, actual, nueva, elegirPuente(), nombreDJ)
            else -> NovaFrases.entreCanciones(estilo, actual, nueva, mezcla.hechos(siguiente.id), elegirPuente())
        }
        val archivo = File(contexto.cacheDir, "nova/intro.wav")
        val duracion = sintetizarYMedir(texto, archivo) ?: return
        intro = Salida(siguiente.id, texto, archivo, duracion)
        android.util.Log.i(ETIQUETA, "frase lista antes del cambio (${duracion} ms)")
    }

    /** Convierte la frase en voz y devuelve cuanto dura, o null si no se pudo. */
    private suspend fun sintetizarYMedir(texto: String, archivo: File): Long? {
        if (!sintetizarCon(texto, archivo)) return null
        val onda = withContext(Dispatchers.IO) {
            runCatching { NovaOnda.envolvente(archivo.readBytes()) }.getOrDefault(FloatArray(0))
        }
        return (onda.size * NovaOnda.TRAMO_MS.toLong()).takeIf { it > 0 } ?: 6_000L
    }

    private fun hablarSobreElFinal(lista: Salida) {
        val idActual = mezcla.idSonando() ?: return
        anunciadaAntes = lista.id
        ultimoTexto = lista.texto
        trabajo = scope.launch {
            try {
                NovaSesion.pensar(true)
                delay(PENSAR_MINIMO_MS)
                NovaSesion.pensar(false)
                salirAlAire(lista.texto, lista.archivo, idActual, 2_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(ETIQUETA, "fallo al hablar sobre el final", e)
            } finally {
                NovaSesion.pensar(false)
            }
        }
    }

    /**
     * Solo subtitulos: la frase sale escrita en Life Line el tiempo que tardaria
     * en decirla, sin voz y sin tocar la musica. Para el bus, el trabajo o
     * cuando alguien duerme.
     */
    private suspend fun mostrarSinVoz(texto: String, idNueva: String) {
        val limite = SystemClock.elapsedRealtime() + ESPERA_SONAR_MS
        // Al transmitir, el reproductor del telefono puede no figurar como sonando.
        while (!(mezcla.sigueSonando() || mezcla.transmitiendo()) || mezcla.idSonando() != idNueva) {
            if (SystemClock.elapsedRealtime() > limite) return
            delay(100)
        }
        val palabras = texto.split(' ').count { it.isNotBlank() }
        val duracion = (palabras * ritmo.msPorPalabra).coerceIn(2_500L, 14_000L)
        val habla = NovaSesion.Habla(texto, SystemClock.elapsedRealtime(), duracion, FloatArray(0))
        NovaSesion.empiezaAHablar(habla)
        try {
            delay(duracion)
        } finally {
            NovaSesion.callada(soloSi = habla)
        }
        android.util.Log.i(ETIQUETA, "subtitulo sin voz ${duracion} ms")
    }

    /**
     * Personalizar: una muestra de como suena con el nombre y el acento elegidos.
     * Si suena musica, la baja mientras habla; si no, habla sola.
     */
    fun probarVoz() {
        if (!activa) return
        callar()
        trabajo = scope.launch {
            val archivo = File(contexto.cacheDir, "nova/muestra.wav")
            var bajo = false
            try {
                if (!sintetizarCon(NovaFrases.muestra(estilo, nombreDJ), archivo)) return@launch
                if (mezcla.sigueSonando()) {
                    bajo = true
                    mezcla.llevarA(NIVEL_MUSICA, BAJAR_MS)
                }
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
                while (!termino && SystemClock.elapsedRealtime() < limite) delay(50)
                if (bajo) {
                    mezcla.llevarA(1f, SUBIR_MS)
                    bajo = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(ETIQUETA, "fallo la muestra", e)
            } finally {
                soltarReproductor()
                if (bajo) mezcla.devolver()
            }
        }
    }

    /**
     * Tocar el subtitulo repite lo ultimo que dijo (cambio aprobado por CG). Solo
     * si no esta hablando ya y la cancion sigue sonando.
     */
    fun repetir() {
        if (!activa || trabajo?.isActive == true) return
        val texto = ultimoTexto ?: return
        val archivo = File(contexto.cacheDir, "nova/frase.wav")
        if (!archivo.exists()) return
        trabajo = scope.launch {
            try {
                val id = mezcla.idSonando() ?: return@launch
                salirAlAire(texto, archivo, id, 3_000L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(ETIQUETA, "fallo al repetir", e)
            }
        }
    }

    /**
     * Lo comun a hablar y repetir: esperar a que se pueda, bajar la musica, decir
     * la frase con su subtitulo y su onda, y subir. Si algo la interrumpe, el
     * subtitulo se va y la musica vuelve ya.
     */
    private suspend fun salirAlAire(texto: String, archivo: File, idNueva: String, esperaMs: Long) {
        // Esperar a que la nueva suene de verdad y nada este mezclando.
        val limite = SystemClock.elapsedRealtime() + esperaMs
        while (!mezcla.puedeHablar()) {
            if (mezcla.idSonando() != idNueva || SystemClock.elapsedRealtime() > limite) {
                android.util.Log.i(
                    ETIQUETA,
                    "no hablo: " + if (mezcla.idSonando() != idNueva) "cambio la cancion" else mezcla.porQueNoHabla(),
                )
                return
            }
            delay(100)
        }
        if (mezcla.idSonando() != idNueva) return

        val onda = withContext(Dispatchers.IO) {
            runCatching { NovaOnda.envolvente(archivo.readBytes()) }.getOrDefault(FloatArray(0))
        }
        var bajo = false
        var publicada: NovaSesion.Habla? = null
        var completa = false
        try {
            bajo = true
            mezcla.llevarA(NIVEL_MUSICA, BAJAR_MS)
            yaSePresento = true
            val hablando = SystemClock.elapsedRealtime()
            completa = decir(archivo, idNueva) { duracionMs ->
                publicada = NovaSesion.Habla(texto, SystemClock.elapsedRealtime(), duracionMs, onda)
                    .also { NovaSesion.empiezaAHablar(it) }
            }
            mezcla.llevarA(1f, SUBIR_MS)
            bajo = false
            android.util.Log.i(ETIQUETA, "hable ${SystemClock.elapsedRealtime() - hablando} ms (onda ${onda.size} tramos)")
        } finally {
            soltarReproductor()
            if (bajo) mezcla.devolver()
            if (completa) {
                // Entera: el subtitulo se queda un momento, quieto, por si la tocan para repetir.
                val quieta = NovaSesion.terminoDeHablar()
                scope.launch {
                    delay(SUBTITULO_DESPUES_MS)
                    NovaSesion.callada(soloSi = quieta)
                }
            } else {
                NovaSesion.callada(soloSi = publicada)
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

    /**
     * Reproduce la frase y avisa en [alEmpezar] cuanto dura, justo al arrancar.
     * Devuelve verdadero si la dijo entera; falso si la persona pauso o cambio
     * de cancion (o el motor se colgo).
     */
    private suspend fun decir(archivo: File, idNueva: String, alEmpezar: (Long) -> Unit): Boolean {
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
        alEmpezar(mp.duration.toLong().coerceAtLeast(1L))
        val limite = SystemClock.elapsedRealtime() + MAXIMO_HABLANDO_MS
        while (!termino && SystemClock.elapsedRealtime() < limite) {
            if (!mezcla.sigueSonando() || mezcla.idSonando() != idNueva) {
                android.util.Log.i(ETIQUETA, "me callo: pausa o cambio de cancion")
                return false
            }
            delay(50)
        }
        return termino
    }

    private fun soltarReproductor() {
        reproductor?.let { mp -> runCatching { mp.stop() }; runCatching { mp.release() } }
        reproductor = null
    }

    companion object {
        private const val ETIQUETA = "LifeMusicNova"

        /** Se piensa al menos esto antes de hablar: la mirada sube y la chispa orbita. */
        const val PENSAR_MINIMO_MS = 500L

        /** Lo maximo que la musica espera a Nova al encender, cambiar de onda o en un cambio retenido. */
        const val ESPERA_MAXIMA_ARRANQUE_MS = 6_000L

        /** Con una voz que tarda (Daniela en el S25: ~1 s por segundo de voz), la musica espera mas. */
        const val ESPERA_MAXIMA_LENTA_MS = 10_000L

        /** En un cambio natural la cancion espera a Nova como mucho esto: un hueco mas largo se nota. */
        const val RETENCION_MAXIMA_MS = 4_000L

        /** Si la siguiente canta antes de esto, Nova habla sobre el final de la anterior. */
        const val ARRANCA_CANTANDO_MS = 7_000L

        /** Termina de hablar este rato antes de que acabe la cancion. */
        const val MARGEN_FINAL_MS = 1_800L

        /** La musica mientras Nova habla: 30 % del volumen de la persona. */
        const val NIVEL_MUSICA = 0.3f
        const val BAJAR_MS = 400L
        const val SUBIR_MS = 600L

        /** Si la nueva no suena en este tiempo, esa vez no habla. */
        const val ESPERA_SONAR_MS = 10_000L

        /** Cuanto se queda el subtitulo, ya quieto, despues de la frase: el rato para tocarlo y repetir. */
        const val SUBTITULO_DESPUES_MS = 4_000L

        /** Ninguna frase dura mas que esto: si el motor se cuelga, la musica vuelve igual. */
        const val MAXIMO_HABLANDO_MS = 18_000L
    }
}
