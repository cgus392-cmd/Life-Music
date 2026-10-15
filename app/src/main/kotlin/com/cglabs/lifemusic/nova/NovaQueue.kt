package com.cglabs.lifemusic.nova

import androidx.media3.common.MediaItem
import com.cglabs.lifemusic.db.MusicDatabase
import com.cglabs.lifemusic.models.MediaMetadata
import com.cglabs.lifemusic.playback.queues.Queue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La cola de Nova: cada «pagina» es un bloque. El reproductor pide la siguiente
 * cuando quedan pocas canciones (la autocarga de siempre), asi que Nova va
 * armando la musica sobre la marcha, con lo que aprendio hasta ese momento.
 */
class NovaQueue(private val database: MusicDatabase) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status = withContext(Dispatchers.IO) {
        Queue.Status(title = "Nova", items = NovaSesion.siguienteBloque(database), mediaItemIndex = 0)
    }

    override fun hasNextPage(): Boolean = NovaSesion.alAire.value

    override suspend fun nextPage(): List<MediaItem> = withContext(Dispatchers.IO) {
        NovaSesion.siguienteBloque(database)
    }
}
