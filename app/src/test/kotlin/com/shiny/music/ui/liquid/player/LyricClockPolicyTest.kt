package com.shiny.music.ui.liquid.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LyricClockPolicyTest {

    private val frameNanos = 8_333_333L // 120 Hz

    /**
     * Plays [seconds] of a song through the estimator the way the phone does: frames at 120 Hz,
     * the player's position only rewritten every [stepMs] of real time (dynamic scheduling).
     * Returns (shown, true position) per frame.
     */
    private fun play(
        seconds: Int,
        stepMs: Long,
        startMs: Long = 30_000L,
        readingOffsetMs: Long = 0L,
    ): List<Pair<Double, Double>> {
        val clock = LyricClockEstimator()
        clock.reset(startMs)
        val out = ArrayList<Pair<Double, Double>>()
        var sample = startMs
        var nextWrite = 0.0
        val frames = seconds * 120
        for (f in 0 until frames) {
            val elapsed = f * frameNanos / 1e6
            // The playback thread wrote at some moment inside the last frame.
            while (nextWrite <= elapsed) {
                sample = (startMs + nextWrite).toLong() + readingOffsetMs
                nextWrite += stepMs + 0.37 // a little off the frame grid, as on a device
            }
            clock.onFrame(1_000_000_000L + f * frameNanos, sample, speed = 1f, advancing = true)
            out += clock.shown to (startMs + elapsed)
        }
        return out
    }

    @Test
    fun `a position written every quarter second plays back as a smooth clock`() {
        val frames = play(seconds = 6, stepMs = 250L)
        val frameMs = frameNanos / 1e6
        for (i in 60 until frames.size) {
            val step = frames[i].first - frames[i - 1].first
            // The raw reading jumps 250 ms at a time; the shown time moves every frame by about a frame.
            assertTrue("frame $i stepped $step ms", step >= frameMs * 0.5 && step <= frameMs * 1.5)
            assertTrue("frame $i is ${frames[i].first - frames[i].second} ms off", abs(frames[i].first - frames[i].second) < 12.0)
        }
    }

    @Test
    fun `readings that land a little behind never move the clock backwards`() {
        val clock = LyricClockEstimator()
        clock.reset(10_000L)
        var last = Double.NEGATIVE_INFINITY
        for (f in 0 until 240) {
            // Every 20th frame the player re-anchors 30 ms behind where it had been heading.
            val t = 10_000L + (f * frameNanos / 1_000_000L) - if (f % 20 == 0 && f > 0) 30L else 0L
            clock.onFrame(f * frameNanos, t, speed = 1f, advancing = true)
            assertTrue("frame $f went back", clock.shown >= last)
            last = clock.shown
        }
    }

    @Test
    fun `a large move is taken at once`() {
        val clock = LyricClockEstimator()
        clock.reset(10_000L)
        clock.onFrame(0L, 10_000L, 1f, advancing = true)
        clock.onFrame(frameNanos, 10_008L, 1f, advancing = true)
        clock.onFrame(2 * frameNanos, 90_000L, 1f, advancing = true)
        assertEquals(90_000.0, clock.shown, 5.0)
    }

    @Test
    fun `a seek is shown exactly, in either direction and at any size`() {
        val clock = LyricClockEstimator()
        for (target in listOf(9_950L, 225_000L, 5_000L, 240_000L, 80_000L)) {
            clock.reset(target)
            assertEquals(target.toDouble(), clock.shown, 0.0)
            clock.onFrame(frameNanos, target, 1f, advancing = true)
            assertEquals(target.toDouble(), clock.shown, 0.0)
        }
    }

    @Test
    fun `a player that is not advancing is shown exactly where it is`() {
        val clock = LyricClockEstimator()
        clock.reset(45_000L)
        for (f in 0 until 30) {
            clock.onFrame(f * frameNanos, 45_000L, 1f, advancing = false)
            assertEquals(45_000.0, clock.shown, 0.0)
        }
    }

    @Test
    fun `a stall the player does not report cannot run the lyrics ahead`() {
        val clock = LyricClockEstimator()
        clock.reset(60_000L)
        var last = 0.0
        for (f in 0 until 120 * 4) {
            clock.onFrame(f * frameNanos, 60_000L, 1f, advancing = true)
            assertTrue("frame $f went back", clock.shown >= last)
            last = clock.shown
        }
        assertTrue(clock.shown <= 60_000.0 + LyricClockEstimator.DefaultMaxExtrapolationMs + 1.0)
    }

    @Test
    fun `playback speed scales the clock`() {
        val clock = LyricClockEstimator()
        clock.reset(0L)
        clock.onFrame(0L, 0L, 1.5f, advancing = true)
        for (f in 1..120) clock.onFrame(f * frameNanos, 0L, 1.5f, advancing = true)
        // One second of frames at 1.5×, with no new reading yet: 1.5 s of song.
        assertEquals(1_500.0, clock.shown, 20.0)
    }

    @Test
    fun `polled readings far from the expected position count as seeks`() {
        assertFalse(LyricClockPolicy.isPolledJump(expected = 60_000L, polled = 60_400L))
        assertTrue(LyricClockPolicy.isPolledJump(expected = 60_000L, polled = 30_000L))
        assertTrue(LyricClockPolicy.isPolledJump(expected = 60_000L, polled = 62_000L))

        val clock = LyricClockEstimator(maxExtrapolationMs = Double.MAX_VALUE)
        clock.reset(60_000L)
        assertFalse(clock.onFrame(0L, 60_000L, 1f, advancing = true, jumpMs = LyricClockPolicy.PolledJumpMs))
        assertFalse(clock.onFrame(frameNanos * 60, 60_480L, 1f, advancing = true, jumpMs = LyricClockPolicy.PolledJumpMs))
        assertTrue(clock.onFrame(frameNanos * 61, 30_000L, 1f, advancing = true, jumpMs = LyricClockPolicy.PolledJumpMs))
        assertEquals(30_000.0, clock.shown, 10.0)
    }

    @Test
    fun `the page jumps only for a seek that moves more than one line`() {
        assertTrue(LyricClockPolicy.shouldJump(fromIndex = 40, toIndex = 3, afterSeek = true))
        assertTrue(LyricClockPolicy.shouldJump(fromIndex = -1, toIndex = 12, afterSeek = true))
        assertFalse(LyricClockPolicy.shouldJump(fromIndex = 12, toIndex = 13, afterSeek = true))
        assertFalse(LyricClockPolicy.shouldJump(fromIndex = 12, toIndex = 12, afterSeek = true))
        // Ordinary progression always glides.
        assertFalse(LyricClockPolicy.shouldJump(fromIndex = 3, toIndex = 40, afterSeek = false))
    }

    @Test
    fun `a line becomes the sung line a beat before its timestamp, and not sooner`() {
        val lines = listOf(
            com.shiny.music.lyrics.LyricsEntry(10_000L, "first"),
            com.shiny.music.lyrics.LyricsEntry(14_000L, "second"),
        )
        fun sungAt(clock: Long, offset: Long = 0L) =
            com.shiny.music.lyrics.LyricsUtils.findCurrentLineIndex(lines, LyricClockPolicy.lineLookupPosition(clock, offset))
        assertEquals(-1, sungAt(10_000L - LyricClockPolicy.LineLeadMs - 1))
        assertEquals(0, sungAt(10_000L - LyricClockPolicy.LineLeadMs))
        assertEquals(0, sungAt(13_700L))
        assertEquals(1, sungAt(13_750L))
        // A per-song offset still applies on top: +500 ms shows lines half a second sooner.
        assertEquals(1, sungAt(13_250L, offset = 500L))
        // Tapping a line seeks to its timestamp, which is always inside that line.
        assertEquals(1, sungAt(14_000L))

        // The engine's own lookup agrees with the list's.
        val timeline = buildTimeline(lines)
        for (t in listOf(9_000L, 9_750L, 13_700L, 13_750L, 14_000L, 20_000L)) {
            assertEquals("at $t", sungAt(t), timeline.lineAt(t.toDouble()))
        }
    }
}
