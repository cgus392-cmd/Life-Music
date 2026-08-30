

package com.cglabs.lifemusic.lyrics

import android.content.Context
import com.cglabs.lifemusic.betterlyrics.BetterLyrics
import com.cglabs.lifemusic.constants.EnableBetterLyricsKey
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, album, callback)
    }
}
