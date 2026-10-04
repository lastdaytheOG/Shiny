package com.shiny.music.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherSyncTest {

    @Test
    fun `clock trusts the round trip with the least delay`() {
        val clock = TogetherClock()
        // Server is 5 000 ms ahead. A slow trip (400 ms) gives a worse estimate than a fast one.
        clock.onPong(clientSent = 1_000, server = 6_300, clientReceived = 1_400) // midpoint 1 200 → +5 100
        assertEquals(5_100, clock.offsetMs)
        clock.onPong(clientSent = 2_000, server = 7_020, clientReceived = 2_040) // midpoint 2 020 → +5 000
        assertEquals(5_000, clock.offsetMs)
        assertEquals(40, clock.rttMs)
        // A later slow sample doesn't override the better one.
        clock.onPong(clientSent = 3_000, server = 8_500, clientReceived = 3_600)
        assertEquals(5_000, clock.offsetMs)
        assertEquals(15_000, clock.serverNow(local = 10_000))
    }

    @Test
    fun `clock ignores impossible round trips`() {
        val clock = TogetherClock()
        clock.onPong(clientSent = 2_000, server = 9_000, clientReceived = 1_000)
        assertTrue(!clock.synced)
    }

    @Test
    fun `expected position advances only while the room plays`() {
        val playing = TogetherPlayback(positionMs = 10_000, at = 50_000, playing = true)
        assertEquals(12_500, TogetherSyncPolicy.expectedPosition(playing, serverNow = 52_500))
        val paused = playing.copy(playing = false)
        assertEquals(10_000, TogetherSyncPolicy.expectedPosition(paused, serverNow = 52_500))
        val buffering = playing.copy(buffering = true)
        assertEquals(10_000, TogetherSyncPolicy.expectedPosition(buffering, serverNow = 52_500))
        val fast = playing.copy(rate = 1.5f)
        assertEquals(13_750, TogetherSyncPolicy.expectedPosition(fast, serverNow = 52_500))
    }

    @Test
    fun `small drift bends the speed, large drift jumps`() {
        assertEquals(TogetherSyncPolicy.Correction.None, TogetherSyncPolicy.correction(actualMs = 10_020, expectedMs = 10_000))

        val ahead = TogetherSyncPolicy.correction(actualMs = 10_300, expectedMs = 10_000)
        assertTrue(ahead is TogetherSyncPolicy.Correction.Rate && ahead.speed < 1f && ahead.speed >= 0.95f)

        val behind = TogetherSyncPolicy.correction(actualMs = 9_700, expectedMs = 10_000)
        assertTrue(behind is TogetherSyncPolicy.Correction.Rate && behind.speed > 1f && behind.speed <= 1.05f)

        val far = TogetherSyncPolicy.correction(actualMs = 4_000, expectedMs = 10_000)
        assertEquals(TogetherSyncPolicy.Correction.Seek(10_000 + TogetherSyncPolicy.SeekLeadMs), far)
    }

    @Test
    fun `codes are read from anything a person might paste`() {
        assertEquals("ABC234", TogetherSession.normalizeCode("abc234"))
        assertEquals("ABC234", TogetherSession.normalizeCode(" ABC-234 "))
        assertEquals("ABC234", TogetherSession.normalizeCode("Join me https://shiny-together.x.workers.dev/j/ABC234"))
        assertEquals("ABC234", TogetherSession.normalizeCode("shinymusic://listen?code=abc234"))
        // Letters that are easy to misread aren't in the alphabet.
        assertNull(TogetherSession.normalizeCode("ABC10O"))
        assertNull(TogetherSession.normalizeCode("ABC23"))
    }

    @Test
    fun `only YouTube ids can be shared with the room`() {
        assertTrue(TogetherSession.isShareable("dQw4w9WgXcQ"))
        assertTrue(!TogetherSession.isShareable("content://media/external/audio/media/42"))
    }
}
