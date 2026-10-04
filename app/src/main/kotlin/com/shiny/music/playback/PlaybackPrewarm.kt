package com.shiny.music.playback

/**
 * How screens tell the playback service what is about to be played, before it is.
 *
 * [touched] fires when a finger lands on a song and stays put; a tap takes 80–150 ms from
 * touch to release, and the resolve started here is the one the tap then joins rather than
 * starts. [likely] names the songs a screen expects to be started next (the top of Home), so
 * their first seconds can wait on disk.
 *
 * Both are hints: nothing happens when the service isn't running, and the service decides
 * whether the network allows it.
 */
object PlaybackPrewarm {

    interface Target {
        /** Null: something that leads to music (an album, a playlist), not a song itself. */
        fun touched(mediaId: String?)
        fun likely(mediaIds: List<String>)
    }

    @Volatile
    var target: Target? = null

    fun touched(mediaId: String?) {
        target?.touched(mediaId)
    }

    fun likely(mediaIds: List<String>) {
        if (mediaIds.isNotEmpty()) target?.likely(mediaIds)
    }
}
