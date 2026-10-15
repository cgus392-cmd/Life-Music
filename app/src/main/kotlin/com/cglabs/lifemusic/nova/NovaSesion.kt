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

    private val lugares = ConcurrentHashMap<String, Lugar>()
    private val puestas: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val turnoMutex = Mutex()
    @Volatile private var turno = 0

    fun empezar() {
        lugares.clear()
        puestas.clear()
        turno = 0
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
    const val TAMANO = 4
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
                    .take(20).shuffled().take(TAMANO)
                elegidas.map { it.toMediaItem() } to null
            }

            NovaBloque.AL_PASADO -> {
                val elegidas = limpiar(db.forgottenFavorites().first()).take(30).shuffled().take(TAMANO)
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
                ).shuffled().take(TAMANO)
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
                val nuevas = candidatas.filter { it.mediaId !in yaSonaron }.take(TAMANO)
                nuevas to semilla.artists.firstOrNull()?.name
            }
        }
    }
}
