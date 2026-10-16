package com.cglabs.lifemusic.nova

import androidx.media3.common.MediaItem
import com.cglabs.lifemusic.db.MusicDatabase
import com.cglabs.lifemusic.db.entities.Song
import com.cglabs.lifemusic.extensions.toMediaItem
import com.cglabs.lifemusic.playback.queues.YouTubeQueue
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * Nova al aire: que bloque suena, en que cancion va y que dato cierto tiene cada
 * bloque para anunciarlo. La comparten el servicio (que arma la cola y habla) y
 * el Inicio (la capsula). Vive mientras vive la app; nada se guarda en disco.
 */
object NovaSesion {
    /** Lo que muestra la capsula: el bloque y en que cancion va. */
    data class Estado(val bloque: NovaBloque, val posicion: Int, val total: Int)

    private data class Lugar(val bloque: NovaBloque, val posicion: Int, val total: Int, val dato: String?)

    private val _alAire = MutableStateFlow(false)
    val alAire: StateFlow<Boolean> = _alAire.asStateFlow()

    private val _estado = MutableStateFlow<Estado?>(null)
    val estado: StateFlow<Estado?> = _estado.asStateFlow()

    /**
     * Lo que Nova esta diciendo, para los subtitulos de Life Line: el texto, cuando
     * empezo (elapsedRealtime), cuanto dura y la onda real de su voz. Al terminar
     * queda unos segundos [terminada], por si la persona toca para repetirla.
     */
    class Habla(
        val texto: String,
        val empezoMs: Long,
        val duracionMs: Long,
        val onda: FloatArray,
        val terminada: Boolean = false,
    )

    /** Gestos sueltos de su mirada, por algo que acaba de pasar de verdad. */
    enum class Gesto {
        /** Tocaste una estacion sin canciones: mueve la cabeza a los lados. */
        NIEGA,

        /** Le diste «me gusta» a lo que suena con ella al aire: sonrie y da un saltito. */
        ALEGRE,
    }

    private val _gestos = kotlinx.coroutines.flow.MutableSharedFlow<Gesto>(extraBufferCapacity = 4)
    val gestos: kotlinx.coroutines.flow.SharedFlow<Gesto> = _gestos

    fun gesto(gesto: Gesto) {
        _gestos.tryEmit(gesto)
    }

    private val _sintonizando = MutableStateFlow(false)

    /**
     * Se toco encender, una estacion o «Cambiar de onda» y Nova esta armando el bloque
     * y su frase: la musica ya bajo y la cabina dice «Sintonizando…». Asi el cambio
     * llega como respuesta al toque, no como un salto de la nada (CG, 15-10).
     */
    val sintonizando: StateFlow<Boolean> = _sintonizando.asStateFlow()

    fun sintonizar(si: Boolean) {
        _sintonizando.value = si
    }

    private val _pensando = MutableStateFlow(false)

    /** Prepara la frase (0,3 a 0,6 s): su mirada sube y una chispa orbita. */
    val pensando: StateFlow<Boolean> = _pensando.asStateFlow()

    fun pensar(si: Boolean) {
        _pensando.value = si
    }

    private val _hablando = MutableStateFlow<Habla?>(null)
    val hablando: StateFlow<Habla?> = _hablando.asStateFlow()

    fun empiezaAHablar(habla: Habla) {
        _hablando.value = habla
    }

    /** Termino de decirla entera: el subtitulo se queda un momento, ya quieto. */
    fun terminoDeHablar(): Habla? {
        val antes = _hablando.value ?: return null
        val despues = Habla(antes.texto, antes.empezoMs, antes.duracionMs, antes.onda, terminada = true)
        _hablando.value = despues
        return despues
    }

    /** Se callo (o ya paso el momento): el subtitulo se va y Life Line vuelve a la letra. */
    fun callada(soloSi: Habla? = null) {
        if (soloSi == null || _hablando.value === soloSi) _hablando.value = null
    }

    private val lugares = ConcurrentHashMap<String, Lugar>()
    private val puestas: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val turnoMutex = Mutex()
    @Volatile private var turno = 0

    private val _cabinaAbierta = MutableStateFlow(false)

    /** La cabina de Nova (su pantalla), abierta desde la estrella de la barra o del reproductor. */
    val cabinaAbierta: StateFlow<Boolean> = _cabinaAbierta.asStateFlow()

    fun abrirCabina() {
        _cabinaAbierta.value = true
    }

    fun cerrarCabina() {
        _cabinaAbierta.value = false
    }

    /** Sale al aire. Con [desde], la primera estacion es esa (se eligio en el dial). */
    fun empezar(desde: NovaBloque? = null) {
        lugares.clear()
        puestas.clear()
        turno = desde?.let { NovaBloque.entries.indexOf(it) } ?: 0
        _estado.value = null
        _alAire.value = true
    }

    fun apagar() {
        _alAire.value = false
        _estado.value = null
    }

    /** Lo que suena ahora: si es de Nova, la capsula sabe en que bloque va. */
    fun sonando(id: String?) {
        if (!_alAire.value) return
        val lugar = id?.let { lugares[it] } ?: return
        _estado.value = Estado(lugar.bloque, lugar.posicion + 1, lugar.total)
    }

    fun esDeNova(id: String?): Boolean = id != null && lugares.containsKey(id)
    fun esInicioDeBloque(id: String?): Boolean = id != null && lugares[id]?.posicion == 0
    fun bloqueDe(id: String?): NovaBloque? = id?.let { lugares[it]?.bloque }
    fun datoDe(id: String?): String? = id?.let { lugares[it]?.dato }

    /**
     * Una estacion elegida en el dial, sin saltar a otra si no tiene canciones
     * (vacio: la cabina avisa). La rotacion sigue despues de ella.
     */
    suspend fun bloqueElegido(database: MusicDatabase, bloque: NovaBloque): List<MediaItem> = turnoMutex.withLock {
        turno = NovaBloque.entries.indexOf(bloque) + 1
        val (items, dato) = runCatching { NovaArmador.armar(bloque, database, puestas) }
            .getOrDefault(emptyList<MediaItem>() to null)
        if (items.size < NovaArmador.MINIMO) return emptyList()
        items.forEachIndexed { i, item ->
            lugares[item.mediaId] = Lugar(bloque, i, items.size, dato)
            puestas += item.mediaId
        }
        android.util.Log.i("LifeMusicNova", "estacion elegida $bloque con ${items.size} canciones")
        return items
    }

    /**
     * El bloque que toca en la rotacion. Si uno no tiene canciones suficientes
     * (poco historial, sin red para Algo nuevo), se salta y Nova no lo nombra.
     * Vacio solo si ninguno tiene: entonces Nova no sale al aire.
     */
    suspend fun siguienteBloque(database: MusicDatabase): List<MediaItem> = turnoMutex.withLock {
        repeat(NovaBloque.entries.size) {
            val bloque = NovaBloque.entries[turno % NovaBloque.entries.size]
            turno++
            val (items, dato) = runCatching { NovaArmador.armar(bloque, database, puestas) }
                .onFailure { android.util.Log.w("LifeMusicNova", "bloque $bloque fallo", it) }
                .getOrDefault(emptyList<MediaItem>() to null)
            if (items.size >= NovaArmador.MINIMO) {
                items.forEachIndexed { i, item ->
                    lugares[item.mediaId] = Lugar(bloque, i, items.size, dato)
                    puestas += item.mediaId
                }
                android.util.Log.i("LifeMusicNova", "bloque $bloque con ${items.size} canciones")
                return items
            }
            android.util.Log.i("LifeMusicNova", "bloque $bloque sin canciones suficientes (${items.size})")
        }
        emptyList()
    }
}

/** Arma cada bloque desde el historial del telefono (y la radio de YouTube para Algo nuevo). */
internal object NovaArmador {
    /** Canciones por bloque = cada cuantas habla Nova (Personalizar, de 2 a 8). */
    @Volatile var tamano = 4
    const val MINIMO = 2
    private const val DIA = 86_400_000L

    /** La base guarda la hora local como si fuera UTC (Converters): las ventanas se miden igual. */
    private fun ahoraComoLaBase(): Long = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()

    suspend fun armar(bloque: NovaBloque, db: MusicDatabase, excluir: Set<String>): Pair<List<MediaItem>, String?> {
        val saltadas = db.novaMuySaltadas(System.currentTimeMillis() - 60 * DIA).toSet()
        fun limpiar(canciones: List<Song>) =
            canciones.filter { it.id !in excluir && it.id !in saltadas }.distinctBy { it.id }

        return when (bloque) {
            NovaBloque.DE_SIEMPRE -> {
                val elegidas = limpiar(db.mostPlayedSongs(ahoraComoLaBase() - 30 * DIA, limit = 40).first())
                    .take(20).shuffled().take(tamano)
                elegidas.map { it.toMediaItem() } to null
            }

            NovaBloque.AL_PASADO -> {
                val elegidas = limpiar(db.forgottenFavorites().first()).take(30).shuffled().take(tamano)
                // «Volvamos a 2019»: el año en que sono por primera vez la que abre el bloque.
                // Si es de este año, no se dice año: seria raro «volvamos a 2026».
                val anio = elegidas.firstOrNull()?.let { db.novaPrimeraVez(it.id) }
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).year }
                val dato = anio?.takeIf { it < LocalDateTime.now().year }?.toString()
                elegidas.map { it.toMediaItem() } to dato
            }

            NovaBloque.ESTE_MOMENTO -> {
                val ahora = LocalDateTime.now()
                val finDeSemana = ahora.dayOfWeek == DayOfWeek.SATURDAY || ahora.dayOfWeek == DayOfWeek.SUNDAY
                val elegidas = limpiar(
                    db.novaParaEsteMomento(ahora.hour, if (finDeSemana) 1 else 0, ahoraComoLaBase() - 90 * DIA, 30),
                ).shuffled().take(tamano)
                val dato = (if (finDeSemana) "los fines de semana " else "entre semana ") + NovaFrases.franja(ahora.hour)
                elegidas.map { it.toMediaItem() } to dato
            }

            NovaBloque.ALGO_NUEVO -> {
                val semillas = db.mostPlayedSongs(ahoraComoLaBase() - 30 * DIA, limit = 10).first()
                    .ifEmpty { db.topSongs(10).first() }
                val semilla = semillas.filter { it.id !in saltadas }.randomOrNull()
                    ?: return emptyList<MediaItem>() to null
                val locales = db.relatedSongs(semilla.id).map { it.toMediaItem() }
                val radio = runCatching {
                    YouTubeQueue(WatchEndpoint(videoId = semilla.id, playlistId = "RDAMVM${semilla.id}"))
                        .getInitialStatus().items
                }.getOrDefault(emptyList())
                val candidatas = (locales + radio)
                    .filter { it.mediaId != semilla.id && it.mediaId !in excluir && it.mediaId !in saltadas }
                    .distinctBy { it.mediaId }
                // Algo nuevo de verdad: nada que haya sonado alguna vez.
                val yaSonaron = if (candidatas.isEmpty()) emptySet() else db.novaYaSonaron(candidatas.map { it.mediaId }).toSet()
                val nuevas = candidatas.filter { it.mediaId !in yaSonaron }.take(tamano)
                nuevas to semilla.artists.firstOrNull()?.name
            }
        }
    }
}
