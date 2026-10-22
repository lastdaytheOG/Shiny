package com.shiny.music.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackModeLogicTest {
    @Test
    fun `repeat control cycles off all one off`() {
        assertEquals(Player.REPEAT_MODE_ALL, nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextRepeatMode(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `autoplay completion is allowed only with repeat off`() {
        assertTrue(canAutoplay(true, followingTogetherHost = false, repeatMode = Player.REPEAT_MODE_OFF))
        assertFalse(canAutoplay(true, followingTogetherHost = false, repeatMode = Player.REPEAT_MODE_ALL))
        assertFalse(canAutoplay(true, followingTogetherHost = false, repeatMode = Player.REPEAT_MODE_ONE))
        assertFalse(canAutoplay(false, followingTogetherHost = false, repeatMode = Player.REPEAT_MODE_OFF))
    }

    @Test
    fun `disabling autoplay invalidates an in flight recommendation`() {
        val requestVersion = 7L
        assertTrue(isCurrentAutoplayRequest(true, requestVersion, requestVersion))
        assertFalse(isCurrentAutoplayRequest(false, requestVersion, requestVersion + 1))
    }

    @Test
    fun `a newer autoplay request cannot apply an older result`() {
        assertFalse(isCurrentAutoplayRequest(true, requestVersion = 3L, currentVersion = 4L))
    }

    @Test
    fun `playlist pages load near the end whatever autoplay says`() {
        assertTrue(shouldLoadQueuePage(5, hasNextPage = true, pagesAreSource = true, autoplayAllowed = false, isRepeatTransition = false))
        assertFalse(shouldLoadQueuePage(6, hasNextPage = true, pagesAreSource = true, autoplayAllowed = false, isRepeatTransition = false))
        assertFalse(shouldLoadQueuePage(1, hasNextPage = false, pagesAreSource = true, autoplayAllowed = true, isRepeatTransition = false))
        assertFalse(shouldLoadQueuePage(1, hasNextPage = true, pagesAreSource = true, autoplayAllowed = true, isRepeatTransition = true))
    }

    @Test
    fun `radio pages need autoplay`() {
        assertFalse(shouldLoadQueuePage(0, hasNextPage = true, pagesAreSource = false, autoplayAllowed = false, isRepeatTransition = false))
        assertTrue(shouldLoadQueuePage(0, hasNextPage = true, pagesAreSource = false, autoplayAllowed = true, isRepeatTransition = false))
        assertFalse(canLoadQueuePage(hasNextPage = true, pagesAreSource = false, autoplayAllowed = false))
        assertTrue(canLoadQueuePage(hasNextPage = true, pagesAreSource = true, autoplayAllowed = false))
    }

    @Test
    fun `repeat modes block radio pages but not playlist pages`() {
        for (repeat in listOf(Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE)) {
            val autoplay = canAutoplay(true, followingTogetherHost = false, repeatMode = repeat)
            assertFalse(canLoadQueuePage(true, pagesAreSource = false, autoplayAllowed = autoplay))
            assertTrue(canLoadQueuePage(true, pagesAreSource = true, autoplayAllowed = autoplay))
        }
    }

    @Test
    fun `turning shuffle on keeps the songs already played behind the current one`() {
        val order = shuffleOrderFrom(10, currentIndex = 4, keepPlayed = true, random = Random(7))
        assertEquals(listOf(0, 1, 2, 3, 4), order.take(5))
        assertEquals((5..9).toSet(), order.drop(5).toSet())
    }

    @Test
    fun `a new shuffled queue shuffles everything around the chosen song`() {
        val order = shuffleOrderFrom(10, currentIndex = 4, keepPlayed = false, random = Random(7))
        assertEquals(4, order.first())
        assertEquals((0..9).toSet(), order.toSet())
        assertEquals(10, order.distinct().size)
    }

    @Test
    fun `playlist first shuffles the original songs before added ones`() {
        val order = shuffleOrderFrom(10, currentIndex = 2, keepPlayed = false, originalSize = 6, random = Random(7))
        assertEquals(2, order.first())
        assertEquals((0..5).toSet() - 2, order.slice(1..5).toSet())
        assertEquals((6..9).toSet(), order.drop(6).toSet())
    }

    @Test
    fun `songs added to a shuffle never replay what was already heard`() {
        val before = intArrayOf(4, 1, 3, 0, 2)
        val order = before + intArrayOf(5, 6, 7)
        for (placement in ShufflePlacement.entries) {
            val result = playOrderWithInserted(order, currentIndex = 3, inserted = 5..7, placement = placement, random = Random(1))
            assertEquals(listOf(4, 1, 3), result.take(3))
            assertEquals((0..7).toSet(), result.toSet())
            assertEquals(8, result.distinct().size)
            assertEquals(listOf(0, 2), result.drop(3).filter { it < 5 })
        }
    }

    @Test
    fun `play next and add to queue place songs exactly`() {
        val order = intArrayOf(2, 0, 4, 1, 3, 5, 6)
        assertEquals(
            listOf(2, 0, 5, 6, 4, 1, 3),
            playOrderWithInserted(order, currentIndex = 0, inserted = 5..6, placement = ShufflePlacement.Next).toList(),
        )
        assertEquals(
            listOf(2, 0, 4, 1, 3, 5, 6),
            playOrderWithInserted(order, currentIndex = 0, inserted = 5..6, placement = ShufflePlacement.End).toList(),
        )
    }

    @Test
    fun `shuffled pages from a large playlist keep every song exactly once`() {
        var order = IntArray(100) { it }.also { it.shuffle(Random(3)) }
        val current = order[10]
        repeat(40) { page ->
            val start = order.size
            order = playOrderWithInserted(order + IntArray(100) { start + it }, current, start until start + 100, ShufflePlacement.Mixed, Random(page))
        }
        assertEquals(4_100, order.size)
        assertEquals(4_100, order.distinct().size)
        assertEquals(current, order[10])
    }

    @Test
    fun `moving in play order matches moving in an unshuffled list`() {
        val order = intArrayOf(0, 1, 2, 3, 4, 5)
        assertEquals(listOf(0, 1, 4, 2, 3, 5), playOrderWithMoved(order, currentIndex = 1, item = 4, toUpcomingSlot = 0).toList())
        assertEquals(listOf(0, 1, 3, 4, 5, 2), playOrderWithMoved(order, currentIndex = 1, item = 2, toUpcomingSlot = 99).toList())
        assertEquals(order.toList(), playOrderWithMoved(order, currentIndex = 1, item = 1, toUpcomingSlot = 0).toList())
        val shuffled = intArrayOf(3, 5, 0, 2, 4, 1)
        assertEquals(listOf(3, 5, 1, 0, 2, 4), playOrderWithMoved(shuffled, currentIndex = 5, item = 1, toUpcomingSlot = 0).toList())
    }

    @Test
    fun `queue summary only gives a length when every song has one`() {
        assertEquals(QueueSummary(3, 600L), queueSummary(listOf(200, 200, 200)))
        assertEquals(QueueSummary(3, null), queueSummary(listOf(200, null, 200)))
        assertEquals(QueueSummary(2, null), queueSummary(listOf(200, 0)))
        assertEquals(QueueSummary(0, null), queueSummary(emptyList()))
    }

    @Test
    fun `up next renders fifty rows and grows near the end`() {
        val header = 2
        assertEquals(50, nextRenderCount(QUEUE_RENDER_PAGE, total = 3_000, lastVisible = 10, headerCount = header))
        assertEquals(100, nextRenderCount(50, total = 3_000, lastVisible = header + 50 - 8, headerCount = header))
        assertEquals(120, nextRenderCount(100, total = 120, lastVisible = header + 95, headerCount = header))
        assertEquals(120, nextRenderCount(120, total = 120, lastVisible = header + 119, headerCount = header))
        assertEquals(12, nextRenderCount(QUEUE_RENDER_PAGE, total = 12, lastVisible = 5, headerCount = header))
    }

    @Test
    fun `scrolling to the bottom reaches every row once`() {
        val total = 2_345
        var rendered = QUEUE_RENDER_PAGE
        var lastVisible = 0
        while (lastVisible < 2 + total - 1) {
            lastVisible = (lastVisible + 7).coerceAtMost(2 + rendered - 1)
            rendered = nextRenderCount(rendered, total, lastVisible, headerCount = 2)
        }
        assertEquals(total, rendered)
    }

    @Test
    fun `shuffle keeps the current track playing and visits every other track once`() {
        val order = shuffledIndicesKeepingCurrentFirst(6, currentIndex = 3, random = Random(42))

        assertEquals(3, order.first())
        assertEquals((0 until 6).toSet(), order.toSet())
        assertEquals(6, order.distinct().size)
    }
}
