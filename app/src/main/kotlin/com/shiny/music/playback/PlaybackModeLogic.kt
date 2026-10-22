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

internal const val QUEUE_PAGE_LOAD_BELOW = 5

internal fun canLoadQueuePage(
    hasNextPage: Boolean,
    pagesAreSource: Boolean,
    autoplayAllowed: Boolean,
): Boolean = hasNextPage && (pagesAreSource || autoplayAllowed)

internal fun shouldLoadQueuePage(
    upcomingInPlayOrder: Int,
    hasNextPage: Boolean,
    pagesAreSource: Boolean,
    autoplayAllowed: Boolean,
    isRepeatTransition: Boolean,
): Boolean = !isRepeatTransition &&
    upcomingInPlayOrder <= QUEUE_PAGE_LOAD_BELOW &&
    canLoadQueuePage(hasNextPage, pagesAreSource, autoplayAllowed)

internal enum class ShufflePlacement { Next, End, Mixed }

// order is read after the insert, so it already contains the new indices
internal fun playOrderWithInserted(
    order: IntArray,
    currentIndex: Int,
    inserted: IntRange,
    placement: ShufflePlacement,
    random: Random = Random.Default,
): IntArray {
    val kept = order.filter { it !in inserted }
    val currentPosition = kept.indexOf(currentIndex)
    val played = kept.subList(0, currentPosition + 1)
    val upcoming = kept.subList(currentPosition + 1, kept.size).toMutableList()
    val added = inserted.toList()
    when (placement) {
        ShufflePlacement.Next -> upcoming.addAll(0, added)
        ShufflePlacement.End -> upcoming.addAll(added)
        ShufflePlacement.Mixed -> added.shuffled(random).forEach { index ->
            upcoming.add(random.nextInt(upcoming.size + 1), index)
        }
    }
    return (played + upcoming).toIntArray()
}

internal fun playOrderWithMoved(
    order: IntArray,
    currentIndex: Int,
    item: Int,
    toUpcomingSlot: Int,
): IntArray {
    if (item == currentIndex || item !in order) return order
    val list = order.toMutableList()
    list.remove(item)
    val currentPosition = list.indexOf(currentIndex)
    val target = (currentPosition + 1 + toUpcomingSlot).coerceIn(currentPosition + 1, list.size)
    list.add(target, item)
    return list.toIntArray()
}

internal data class QueueSummary(val count: Int, val totalSeconds: Long?)

internal fun queueSummary(durationsSeconds: List<Int?>): QueueSummary {
    var total = 0L
    var allKnown = true
    for (seconds in durationsSeconds) {
        if (seconds == null || seconds <= 0) allKnown = false else total += seconds
    }
    return QueueSummary(durationsSeconds.size, if (allKnown && durationsSeconds.isNotEmpty()) total else null)
}

internal const val QUEUE_RENDER_PAGE = 50

internal fun nextRenderCount(
    rendered: Int,
    total: Int,
    lastVisible: Int,
    headerCount: Int,
    loadAhead: Int = 8,
    page: Int = QUEUE_RENDER_PAGE,
): Int {
    val shown = rendered.coerceIn(0, total)
    if (shown >= total) return shown
    val nearEnd = lastVisible >= headerCount + shown - loadAhead
    return if (nearEnd) (shown + page).coerceAtMost(total) else shown
}

internal fun shuffleOrderFrom(
    totalCount: Int,
    currentIndex: Int,
    keepPlayed: Boolean,
    originalSize: Int = 0,
    random: Random = Random.Default,
): IntArray {
    require(currentIndex in 0 until totalCount)
    val played = if (keepPlayed) (0 until currentIndex).toList() else emptyList()
    val rest = (0 until totalCount).filter { it != currentIndex && (!keepPlayed || it > currentIndex) }
    val ordered = if (originalSize in 1 until totalCount) {
        rest.filter { it < originalSize }.shuffled(random) + rest.filter { it >= originalSize }.shuffled(random)
    } else {
        rest.shuffled(random)
    }
    return (played + currentIndex + ordered).toIntArray()
}

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
