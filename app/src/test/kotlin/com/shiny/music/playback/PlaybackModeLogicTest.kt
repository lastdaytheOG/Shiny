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
    fun `shuffle keeps the current track playing and visits every other track once`() {
        val order = shuffledIndicesKeepingCurrentFirst(6, currentIndex = 3, random = Random(42))

        assertEquals(3, order.first())
        assertEquals((0 until 6).toSet(), order.toSet())
        assertEquals(6, order.distinct().size)
    }
}
