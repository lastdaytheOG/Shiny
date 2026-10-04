package com.shiny.music.social

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceFreshnessTest {
    private val now = 1_000_000_000L

    private fun song(playing: Boolean, ageMs: Long) =
        NowPlaying(trackId = "dQw4w9WgXcQ", isPlaying = playing, updatedAt = now - ageMs)

    @Test
    fun `a playing song heard from recently is live`() {
        assertEquals(ListeningState.Playing, PresenceFreshness.state(song(true, 90_000L), now))
    }

    @Test
    fun `a playing song the phone stopped reporting is over`() {
        assertEquals(ListeningState.NotListening, PresenceFreshness.state(song(true, 4 * 60_000L), now))
    }

    @Test
    fun `a pause shows as a pause, then as not listening`() {
        assertEquals(ListeningState.Paused, PresenceFreshness.state(song(false, 5 * 60_000L), now))
        assertEquals(ListeningState.NotListening, PresenceFreshness.state(song(false, 11 * 60_000L), now))
    }

    @Test
    fun `no song is not listening`() {
        assertEquals(ListeningState.NotListening, PresenceFreshness.state(null, now))
    }
}
