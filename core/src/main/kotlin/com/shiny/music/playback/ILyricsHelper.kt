package com.shiny.music.playback

import com.shiny.music.models.MediaMetadata

data class LyricsWithProvider(
    val lyrics: String?,
    val providerName: String
)

interface ILyricsHelper {
    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider
}
