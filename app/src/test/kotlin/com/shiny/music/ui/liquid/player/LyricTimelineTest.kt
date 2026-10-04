package com.shiny.music.ui.liquid.player

import com.shiny.music.lyrics.LyricsEntry
import com.shiny.music.lyrics.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LyricTimelineTest {

    @Test
    fun `syllables of one word land side by side inside it`() {
        // How a syllable-timed provider sends "I've been quiet for too long".
        val text = "I've been quiet for too long"
        val parts = listOf("I've", "been", "qui", "et", "for", "too", "l", "ong")
        val (from, to) = pieceRanges(text, parts)
        parts.forEachIndexed { i, part -> assertEquals(part, text.substring(from[i], to[i])) }
        // "qui"+"et" and "l"+"ong" meet with no gap: one word each, laid out whole.
        assertEquals(to[2], from[3])
        assertEquals(to[6], from[7])
        assertEquals("quiet", text.substring(from[2], to[3]))
        assertEquals("long", text.substring(from[6], to[7]))
    }

    @Test
    fun `punctuation attached to a syllable stays with it`() {
        val text = "violence, yeah"
        val (from, to) = pieceRanges(text, listOf("vio", "lence,", "yeah"))
        assertEquals("violence,", text.substring(from[0], to[1]))
        assertEquals("yeah", text.substring(from[2], to[2]))
    }

    @Test
    fun `a piece the text does not contain lights nothing and moves nothing`() {
        val (from, to) = pieceRanges("hello world", listOf("hel", "lo", "(oh)", "world"))
        assertEquals(from[2], to[2])
        assertEquals(5, from[2])
        // The pieces after it are still found.
        assertEquals(6, from[3])
        assertEquals(11, to[3])
    }

    /** A song with line-synced lines, one word-synced line, a fast pair and an interlude. */
    private val song = listOf(
        LyricsEntry(10_000L, "Water or wine don't make me choose"),
        LyricsEntry(
            13_000L,
            "I've been quiet for too long",
            words = listOf(
                WordTimestamp("I've", 13.00, 13.30),
                WordTimestamp("been", 13.30, 13.55),
                WordTimestamp("qui", 13.55, 13.70),
                WordTimestamp("et", 13.70, 13.95),
                WordTimestamp("for", 13.95, 14.20),
                WordTimestamp("too", 14.20, 14.45),
                WordTimestamp("l", 14.45, 14.60),
                WordTimestamp("ong", 14.60, 16.40),
            ),
        ),
        LyricsEntry(16_600L, "fast"),
        LyricsEntry(16_900L, "faster still"),
        LyricsEntry(26_000L, "after the break"),
        LyricsEntry(28_500L, "the end"),
    )

    @Test
    fun `the highlight moves continuously through a song`() {
        val timeline = buildTimeline(song)
        var last = timeline.cursor(0.0)
        assertEquals(-1f, last, 0f)
        var t = 0.0
        while (t < 40_000.0) {
            t += 1.0
            val c = timeline.cursor(t)
            // Smoothstep over the shortest hand-over (300 ms) moves at most 1.5/300 per ms.
            assertTrue("jump of ${c - last} at $t", abs(c - last) <= 1.5f / 300f + 1e-4f)
            assertTrue("went back at $t", c >= last - 1e-6f)
            last = c
        }
        assertEquals(song.size - 1f, last, 0f)
        // Settled on a line between hand-overs, and exactly on it.
        assertEquals(0f, timeline.cursor(11_000.0), 0f)
        assertEquals(4f, timeline.cursor(27_000.0), 0f)
    }

    /**
     * What the eye sees of piece [i] of line [j] at [t]: the line's layer opacity times its ink,
     * as the renderer draws it — a live line's words sung, being sung or not yet sung; any
     * other line white.
     */
    private fun seen(timeline: LyricTimeline, j: Int, i: Int, t: Double): Float {
        val line = timeline.lines[j]
        val k = timeline.lineAt(t)
        val live = rangeContains(timeline.liveRange(t, k), j)
        val alpha = lineAlpha(line, j, timeline.cursor(t, k), live, t, words = true, browse = 0f)
        val ink = if (!live) {
            1f
        } else {
            val q = line.startedAt(t) - 1
            when {
                i < q -> 1f
                i == q -> UnsungAlpha + (1f - UnsungAlpha) * line.progress(i, t)
                else -> UnsungAlpha
            }
        }
        return alpha * ink
    }

    @Test
    fun `no word on screen ever jumps in brightness`() {
        val timeline = buildTimeline(song)
        for (j in 0 until timeline.size) {
            val line = timeline.lines[j]
            for (i in 0 until line.pieceCount) {
                var last = seen(timeline, j, i, 0.0)
                var t = 0.0
                while (t < 40_000.0) {
                    t += 1.0
                    val now = seen(timeline, j, i, t)
                    // A word revealed over its own time (the shortest here is 150 ms) moves at
                    // most 0.6/150 per ms; anything bigger is a visible jump.
                    assertTrue(
                        "line $j piece $i jumped ${now - last} at $t (live ${timeline.liveRange(t, timeline.lineAt(t))})",
                        abs(now - last) < 0.02f,
                    )
                    last = now
                }
            }
        }
    }

    @Test
    fun `a line's pronunciation never jumps in brightness`() {
        // The romanised line under a lyric has no words of its own: it shows the brightness the
        // line would have as plain text, whether or not the line is live.
        val timeline = buildTimeline(song)
        for (j in 0 until timeline.size) {
            val line = timeline.lines[j]
            fun seenSub(t: Double): Float {
                val k = timeline.lineAt(t)
                val c = timeline.cursor(t, k)
                val live = rangeContains(timeline.liveRange(t, k), j)
                val layer = lineAlpha(line, j, c, live, t, words = true, browse = 0f)
                val plain = lineAlpha(line, j, c, false, t, words = true, browse = 0f)
                return layer * if (live) (plain / layer).coerceAtMost(1f) else 1f
            }
            var last = seenSub(0.0)
            var t = 0.0
            while (t < 40_000.0) {
                t += 1.0
                val now = seenSub(t)
                assertTrue("line $j pronunciation jumped ${now - last} at $t", abs(now - last) < 0.02f)
                last = now
            }
        }
    }

    @Test
    fun `only lines being sung depend on the exact time`() {
        val timeline = buildTimeline(song)
        fun liveAt(t: Double) = timeline.liveRange(t, timeline.lineAt(t))
        // Long before the song: nothing live.
        assertEquals(NoLive, liveAt(1_000.0))
        // While the word-synced line is sung, it is live.
        assertTrue(rangeContains(liveAt(14_000.0), 1))
        // In the middle of the interlude, after the step-back, nothing is.
        assertEquals(NoLive, liveAt(22_000.0))
        assertTrue(timeline.lines[3].interlude)
        assertFalse(timeline.lines[1].interlude)
    }

    @Test
    fun `a glow comes up, holds and lets go, and is dark otherwise`() {
        val line = buildTimeline(song).lines[1]
        val long = line.pieceCount - 1 // "ong", held for 1.8 s
        assertEquals(0f, line.glow(long, 14_599.0), 0f)
        assertTrue(line.glow(long, 14_700.0) > 0.2f)
        assertEquals(0.45f, line.glow(long, 16_000.0), 1e-4f)
        assertEquals(0f, line.glow(long, 16_400.0 + GlowReleaseMs), 0f)
        var last = 0f
        var t = 14_500.0
        while (t < 17_000.0) {
            val g = line.glow(long, t)
            assertTrue("glow jumped at $t", abs(g - last) < 0.01f)
            last = g
            t += 1.0
        }
    }

    @Test
    fun `line-synced lines get word timings spread over the line`() {
        val line = buildTimeline(song).lines[0]
        assertEquals(7, line.pieceCount)
        assertEquals(10_000L, line.pieceStart[0])
        for (i in 1 until line.pieceCount) assertEquals(line.pieceEnd[i - 1], line.pieceStart[i])
        assertTrue(line.end < 13_000L)
    }
}
