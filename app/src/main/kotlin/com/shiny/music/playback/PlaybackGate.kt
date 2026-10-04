package com.shiny.music.playback

import androidx.media3.common.MediaItem
import com.shiny.music.playback.queues.Queue

/**
 * A say over playback requests before they reach the player. Listen Together installs one:
 * while you are a guest, a song you pick goes into the session's shared queue instead of
 * replacing what the room is hearing, and the transport becomes the room's.
 *
 * Every method returns true when it took the request, so the caller must not act on it.
 */
interface PlaybackGate {
    fun interceptPlay(queue: Queue): Boolean
    fun interceptEnqueue(items: List<MediaItem>, next: Boolean): Boolean
    fun interceptTransport(action: TransportAction, positionMs: Long = 0L): Boolean
}

enum class TransportAction { Toggle, Play, Pause, Seek, Next, Previous, Radio }
