package com.cglabs.lifemusic.playback

import com.cglabs.lifemusic.db.entities.SongEntity

interface ISyncUtils {
    fun likeSong(song: SongEntity)
}
