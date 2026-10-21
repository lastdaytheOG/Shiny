package com.shiny.music.playback

import androidx.media3.common.Player
import kotlin.random.Random

/**
 * Small, side-effect-free decisions shared by the queue controls and the service.
 * Keeping these decisions here makes the UI's displayed mode and the engine's completion
 * behaviour testable without constructing a service or a player.
 */
internal fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

internal fun canAutoplay(
    enabled: Boolean,
    followingTogetherHost: Boolean,
    repeatMode: Int,
): Boolean = enabled && !followingTogetherHost && repeatMode == Player.REPEAT_MODE_OFF

/** A result fetched before this version changed must never mutate the queue. */
internal fun isCurrentAutoplayRequest(
    enabled: Boolean,
    requestVersion: Long,
    currentVersion: Long,
): Boolean = enabled && requestVersion == currentVersion

/** Builds a Media3 shuffle order which preserves the song currently playing at its head. */
internal fun shuffledIndicesKeepingCurrentFirst(
    totalCount: Int,
    currentIndex: Int,
    random: Random = Random.Default,
): IntArray {
    require(totalCount > 0)
    require(currentIndex in 0 until totalCount)
    return IntArray(totalCount) { it }
        .also { it.shuffle(random) }
        .also { indices ->
            val currentPosition = indices.indexOf(currentIndex)
            val first = indices[0]
            indices[0] = indices[currentPosition]
            indices[currentPosition] = first
        }
}
