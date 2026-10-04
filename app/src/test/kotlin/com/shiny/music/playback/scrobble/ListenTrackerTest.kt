package com.shiny.music.playback.scrobble

import com.shiny.music.playback.scrobble.ListenTracker.Submission.Listen
import com.shiny.music.playback.scrobble.ListenTracker.Submission.PlayingNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTrackerTest {
    private var uptime = 5_000L
    private var wall = 1_700_000_000_000L
    private val sent = mutableListOf<ListenTracker.Submission>()
    private val tracker = ListenTracker({ uptime }, { wall }, { sent += it })

    private fun track(id: String = "a", durationMs: Long = 200_000L) =
        ListenTracker.Track(id, "Title $id", "Artist", "Album", durationMs)

    private fun pass(ms: Long) {
        uptime += ms
        wall += ms
    }

    private fun listens() = sent.filterIsInstance<Listen>()
    private fun announcements() = sent.filterIsInstance<PlayingNow>()

    @Test
    fun `needed play time is half the track, four minutes at most, never for a jingle`() {
        assertEquals(100_000L, ListenTracker.neededMs(200_000L))
        assertEquals(240_000L, ListenTracker.neededMs(480_000L))
        assertEquals(240_000L, ListenTracker.neededMs(3_600_000L))
        assertEquals(15_000L, ListenTracker.neededMs(30_000L))
        assertNull(ListenTracker.neededMs(29_999L))
        // Length not known yet: only the four-minute rule can apply.
        assertEquals(240_000L, ListenTracker.neededMs(0L))
    }

    @Test
    fun `playing now goes out once, when the track first sounds`() {
        assertNull(tracker.update(track(), playing = false))
        assertTrue(sent.isEmpty())

        tracker.update(track(), playing = true)
        tracker.update(track(), playing = true)
        tracker.update(track(), playing = false)
        tracker.update(track(), playing = true)
        assertEquals(listOf(PlayingNow(track())), sent)
    }

    @Test
    fun `the listen goes out at half the track and carries when the play began`() {
        val startedAt = wall
        assertEquals(100_000L, tracker.update(track(), playing = true))

        pass(99_999)
        assertEquals(1L, tracker.update(track(), playing = true))
        assertTrue(listens().isEmpty())

        pass(1)
        assertNull(tracker.update(track(), playing = true))
        assertEquals(listOf(Listen(track(), startedAt, startedAt + 100_000)), listens())
    }

    @Test
    fun `a long track needs four minutes`() {
        val long = track(durationMs = 900_000L)
        assertEquals(240_000L, tracker.update(long, playing = true))
        pass(240_000)
        tracker.update(long, playing = true)
        assertEquals(1, listens().size)
    }

    @Test
    fun `time spent paused does not count`() {
        tracker.update(track(), playing = true)
        pass(60_000)
        assertNull(tracker.update(track(), playing = false))
        pass(600_000)
        assertNull(tracker.update(track(), playing = false))
        assertTrue(listens().isEmpty())

        assertEquals(40_000L, tracker.update(track(), playing = true))
        pass(40_000)
        tracker.update(track(), playing = true)
        assertEquals(1, listens().size)
    }

    @Test
    fun `one play is one listen however long it goes on`() {
        tracker.update(track(), playing = true)
        pass(100_000)
        tracker.update(track(), playing = true)
        repeat(5) {
            pass(30_000)
            assertNull(tracker.update(track(), playing = it % 2 == 0))
        }
        assertEquals(1, listens().size)
    }

    @Test
    fun `a track skipped early is not a listen`() {
        tracker.update(track("a"), playing = true)
        pass(50_000)
        assertEquals(100_000L, tracker.update(track("b"), playing = true))
        pass(100_000)
        tracker.update(track("b"), playing = true)
        assertEquals(listOf("a", "b"), announcements().map { it.track.id })
        assertEquals(listOf("b"), listens().map { it.track.id })
    }

    @Test
    fun `the same track on repeat is a new play each time`() {
        tracker.update(track(), playing = true)
        pass(100_000)
        tracker.update(track(), playing = true)
        pass(100_000)
        assertEquals(100_000L, tracker.update(track(), playing = true, newPlay = true))
        pass(100_000)
        tracker.update(track(), playing = true)
        assertEquals(2, announcements().size)
        assertEquals(2, listens().size)
        assertEquals(200_000L, listens()[1].startedAtMs - listens()[0].startedAtMs)
    }

    @Test
    fun `a jingle sends nothing at all`() {
        val jingle = track(durationMs = 20_000L)
        assertNull(tracker.update(jingle, playing = true))
        pass(20_000)
        assertNull(tracker.update(jingle, playing = true))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `nothing to scrobble ends the play`() {
        tracker.update(track(), playing = true)
        pass(90_000)
        assertNull(tracker.update(null, playing = true))
        // The earlier ninety seconds are gone with that play.
        assertEquals(100_000L, tracker.update(track(), playing = true))
        assertEquals(2, announcements().size)
        assertTrue(listens().isEmpty())
    }

    @Test
    fun `a length learned late shortens the wait`() {
        assertEquals(240_000L, tracker.update(track(durationMs = 0L), playing = true))
        pass(10_000)
        assertEquals(40_000L, tracker.update(track(durationMs = 100_000L), playing = true))
        pass(40_000)
        tracker.update(track(durationMs = 100_000L), playing = true)
        assertEquals(1, listens().size)
        assertEquals(100_000L, listens().single().track.durationMs)
    }
}
