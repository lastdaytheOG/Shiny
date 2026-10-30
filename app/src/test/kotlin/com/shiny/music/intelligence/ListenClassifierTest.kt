package com.shiny.music.intelligence

import com.shiny.music.db.entities.RecSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenClassifierTest {
    private val song = 200_000L

    private fun kind(
        listened: Long,
        reason: EndReason,
        selection: Selection = Selection.QueueAdvance,
        burst: Boolean = false,
        duration: Long = song,
    ) = ListenClassifier.classify(listened, duration, reason, selection, burst)

    @Test
    fun `a song that ends on its own after being heard is completed`() {
        assertEquals(ListenKind.Completed, kind(190_000, EndReason.Finished))
        assertEquals(ListenKind.Completed, kind(110_000, EndReason.Finished))
    }

    @Test
    fun `seeking to the end is not a completed listen`() {
        assertEquals(ListenKind.Partial, kind(20_000, EndReason.Finished))
    }

    @Test
    fun `half a song or two minutes is meaningful even when skipped`() {
        assertEquals(ListenKind.Meaningful, kind(105_000, EndReason.SkippedNext))
        assertEquals(ListenKind.Meaningful, kind(121_000, EndReason.SkippedNext, duration = 600_000))
    }

    @Test
    fun `a quick skip of a song Shiny picked is a real skip`() {
        assertEquals(ListenKind.Skipped, kind(3_000, EndReason.SkippedNext))
        assertEquals(ListenKind.Skipped, kind(40_000, EndReason.SkippedNext))
    }

    @Test
    fun `leaving a song that was still buffering says nothing about the song`() {
        assertEquals(ListenKind.Interrupted, kind(0, EndReason.SkippedNext))
        assertEquals(ListenKind.Interrupted, kind(400, EndReason.SkippedNext, Selection.Autoplay))
        assertEquals(ListenKind.Interrupted, kind(0, EndReason.Finished))
    }

    @Test
    fun `a song the listener tapped and left at once is a mis-tap, not a dislike`() {
        assertEquals(ListenKind.Accidental, kind(3_000, EndReason.SkippedNext, Selection.Manual))
    }

    @Test
    fun `skipping through a queue quickly does not condemn each song`() {
        assertEquals(ListenKind.Accidental, kind(2_000, EndReason.SkippedNext, burst = true))
    }

    @Test
    fun `being replaced or stopped early says nothing`() {
        assertEquals(ListenKind.Interrupted, kind(20_000, EndReason.Replaced))
        assertEquals(ListenKind.Interrupted, kind(20_000, EndReason.Stopped))
        assertEquals(ListenKind.Interrupted, kind(90_000, EndReason.Failed))
    }

    @Test
    fun `leaving past the skip window is partial`() {
        assertEquals(ListenKind.Partial, kind(70_000, EndReason.SkippedNext))
        assertEquals(ListenKind.Partial, kind(70_000, EndReason.Replaced))
    }

    @Test
    fun `an unknown length never divides by zero`() {
        assertEquals(0.0, ListenClassifier.completion(10_000, 0), 0.0)
        assertEquals(ListenKind.Completed, kind(30_000, EndReason.Finished, duration = -1))
        assertEquals(ListenKind.Skipped, kind(30_000, EndReason.SkippedNext, duration = -1))
    }

    private class Harness {
        var uptime = 1_000L
        var wall = 1_000_000L
        val out = ArrayList<ListenDraft>()
        val tracker = ListenSessionTracker({ uptime }, { wall }) { out += it }

        fun track(id: String, selection: Selection = Selection.QueueAdvance, duration: Long = 200_000L) =
            ListenSessionTracker.Track(id, duration, RecSurface.Radio, null, selection, 0)

        fun pass(ms: Long) {
            uptime += ms
            wall += ms
        }
    }

    @Test
    fun `only sounding time counts, pauses do not`() {
        val h = Harness()
        h.tracker.start(h.track("a"), playing = true)
        h.pass(30_000)
        h.tracker.setPlaying(false)
        h.pass(600_000)
        h.tracker.setPlaying(true)
        h.pass(30_000)
        h.tracker.end(EndReason.SkippedNext)

        assertEquals(60_000L, h.out.single().listenedMs)
        assertEquals(0.3, h.out.single().completion, 1e-9)
    }

    @Test
    fun `a pending play started paused hears nothing until it plays`() {
        val h = Harness()
        h.tracker.start(h.track("a"), playing = false)
        h.pass(50_000)
        h.tracker.end(EndReason.Stopped)
        assertEquals(0L, h.out.single().listenedMs)
        assertEquals(ListenKind.Interrupted, h.out.single().kind)
    }

    @Test
    fun `the length arriving late still gives a completion`() {
        val h = Harness()
        h.tracker.start(h.track("a", duration = -1), playing = true)
        h.pass(150_000)
        h.tracker.setDuration(200_000)
        h.tracker.end(EndReason.Finished)
        assertEquals(0.75, h.out.single().completion, 1e-9)
    }

    @Test
    fun `two fast skips in a row make the second a browse`() {
        val h = Harness()
        h.tracker.start(h.track("a"), playing = true)
        h.pass(4_000)
        h.tracker.end(EndReason.SkippedNext)
        h.tracker.start(h.track("b"), playing = true)
        h.pass(3_000)
        h.tracker.end(EndReason.SkippedNext)

        assertEquals(ListenKind.Skipped, h.out[0].kind)
        assertEquals(ListenKind.Accidental, h.out[1].kind)
    }

    @Test
    fun `a long gap starts a new listening session`() {
        val h = Harness()
        h.tracker.start(h.track("a"), playing = true)
        h.pass(200_000)
        h.tracker.end(EndReason.Finished)
        h.tracker.start(h.track("b"), playing = true)
        h.pass(200_000)
        h.tracker.end(EndReason.Finished)
        h.pass(ListenSessionTracker.SESSION_GAP_MS + 1)
        h.tracker.start(h.track("c"), playing = true)
        h.pass(200_000)
        h.tracker.end(EndReason.Finished)

        assertEquals(h.out[0].sessionId, h.out[1].sessionId)
        assertNotEquals(h.out[1].sessionId, h.out[2].sessionId)
    }

    @Test
    fun `ending twice emits once`() {
        val h = Harness()
        h.tracker.start(h.track("a"), playing = true)
        h.tracker.end(EndReason.Finished)
        h.tracker.end(EndReason.Finished)
        assertEquals(1, h.out.size)
        assertFalse(h.tracker.current != null)
        assertTrue(h.out.single().track.songId == "a")
    }
}
