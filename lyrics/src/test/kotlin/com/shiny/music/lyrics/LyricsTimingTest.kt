package com.shiny.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timing half of the lyrics pipeline: what a timestamp parses to, and which line is
 * current at a given playback position.
 *
 * These two things together decide whether lyrics look in sync, so they are worth pinning
 * down. Every case here is one that was previously wrong.
 */
class LyricsTimingTest {

    private fun times(lrc: String) = LyricsUtils.parseLyrics(lrc).map { it.time }

    @Test
    fun `two-digit minutes with centiseconds parse to milliseconds`() {
        assertEquals(listOf(62_340L), times("[01:02.34]hello"))
    }

    @Test
    fun `three-digit fraction is milliseconds`() {
        assertEquals(listOf(62_500L), times("[01:02.500]hello"))
    }

    @Test
    fun `one-digit fraction is tenths`() {
        // Not accepted at all by the previous pattern, which required two or three digits.
        assertEquals(listOf(62_500L), times("[01:02.5]hello"))
    }

    @Test
    fun `single-digit minutes are accepted`() {
        // Previously required exactly two minute digits, so these lines were dropped
        // outright and the song appeared to have missing or mistimed lyrics.
        assertEquals(listOf(62_340L), times("[1:02.34]hello"))
    }

    @Test
    fun `timestamp without a fractional part is accepted`() {
        assertEquals(listOf(62_000L), times("[01:02]hello"))
    }

    @Test
    fun `colon-separated fraction is accepted`() {
        assertEquals(listOf(62_340L), times("[01:02:34]hello"))
    }

    @Test
    fun `a bare timestamp with no text is not a lyric line`() {
        val parsed = LyricsUtils.parseLyrics("[00:10.00]first\n[00:20.00]\n[00:30.00]third")
        assertEquals(listOf(10_000L, 30_000L), parsed.map { it.time })
    }

    @Test
    fun `metadata tags are ignored`() {
        val parsed = LyricsUtils.parseLyrics(
            "[ar:Someone]\n[ti:A Song]\n[00:10.00]only real line"
        )
        assertEquals(listOf(10_000L), parsed.map { it.time })
    }

    @Test
    fun `positive offset tag shifts lyrics earlier`() {
        // LRC defines a positive offset as "show the lyrics this many ms sooner".
        assertEquals(listOf(9_500L), times("[offset:500]\n[00:10.00]line"))
    }

    @Test
    fun `negative offset tag shifts lyrics later`() {
        assertEquals(listOf(10_500L), times("[offset:-500]\n[00:10.00]line"))
    }

    @Test
    fun `offset tag never pushes a line before zero`() {
        assertEquals(listOf(0L), times("[offset:5000]\n[00:01.00]line"))
    }

    @Test
    fun `offset tag is not itself rendered as a lyric`() {
        val parsed = LyricsUtils.parseLyrics("[offset:500]\n[00:10.00]line")
        assertEquals(1, parsed.size)
        assertEquals("line", parsed[0].text)
    }

    @Test
    fun `absent offset tag leaves timings untouched`() {
        assertEquals(listOf(10_000L, 20_000L), times("[00:10.00]a\n[00:20.00]b"))
    }

    private val lines = listOf(
        LyricsEntry(10_000L, "first"),
        LyricsEntry(20_000L, "second"),
        LyricsEntry(30_000L, "third"),
    )

    @Test
    fun `before the first line nothing is current`() {
        // The spoken-intro case: a song whose first lyric is well into the track must not
        // highlight anything while the intro plays.
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(lines, 0L))
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(lines, 9_999L))
    }

    @Test
    fun `closely spaced lines are not skipped`() {
        // Fast passages — rap, a Hindi antara, any call-and-response — put lines a couple
        // of hundred milliseconds apart. The old resolver compared against position+300ms,
        // so at the moment one of these lines began, the *next* one already counted as
        // current: the page ran a line ahead of the singing for the whole passage, and
        // tapping a line landed past it. With no look-ahead each line holds its own slot.
        val fast = listOf(
            LyricsEntry(10_000L, "one"),
            LyricsEntry(10_200L, "two"),
            LyricsEntry(10_400L, "three"),
        )
        assertEquals(0, LyricsUtils.findCurrentLineIndex(fast, 10_000L))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(fast, 10_199L))
        assertEquals(1, LyricsUtils.findCurrentLineIndex(fast, 10_200L))
        assertEquals(2, LyricsUtils.findCurrentLineIndex(fast, 10_400L))
    }

    @Test
    fun `a line stays current for its whole duration`() {
        // The last 300ms of every line used to belong to the next one.
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 19_800L))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 19_999L))
    }

    @Test
    fun `a line becomes current exactly at its own timestamp`() {
        // No look-ahead: the previous implementation compared against position + 300ms, so
        // every line lit up 300ms before it was sung.
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 10_000L))
        assertEquals(0, LyricsUtils.findCurrentLineIndex(lines, 19_999L))
        assertEquals(1, LyricsUtils.findCurrentLineIndex(lines, 20_000L))
    }

    @Test
    fun `seeking to a line's timestamp selects that line and not the next`() {
        // Tapping a lyric seeks to its timestamp. Resolving that same position must give
        // back the line that was tapped; with the old look-ahead it could return the one
        // after it, which is why the song appeared to start slightly ahead.
        lines.forEachIndexed { index, entry ->
            assertEquals(index, LyricsUtils.findCurrentLineIndex(lines, entry.time))
        }
    }

    @Test
    fun `past the last line the last line stays current`() {
        assertEquals(2, LyricsUtils.findCurrentLineIndex(lines, 999_999L))
    }

    @Test
    fun `empty lyrics have no current line`() {
        assertEquals(-1, LyricsUtils.findCurrentLineIndex(emptyList(), 5_000L))
    }

    @Test
    fun `lines sharing a timestamp resolve to the last of them`() {
        val duplicated = listOf(
            LyricsEntry(10_000L, "a"),
            LyricsEntry(10_000L, "b"),
            LyricsEntry(20_000L, "c"),
        )
        assertEquals(1, LyricsUtils.findCurrentLineIndex(duplicated, 10_000L))
    }

    @Test
    fun `binary search agrees with a linear scan across the whole track`() {
        // findCurrentLineIndex is a binary search over the sorted line times; this pins it
        // against the obvious definition at every second of a five-minute song.
        val song = (0 until 60).map { LyricsEntry(it * 5_000L + 3_000L, "line $it") }
        for (positionMs in 0L..300_000L step 250L) {
            val expected = song.indexOfLast { it.time <= positionMs }
            assertEquals(
                "position $positionMs",
                expected,
                LyricsUtils.findCurrentLineIndex(song, positionMs),
            )
        }
    }

    @Test
    fun `parsed lyrics come back sorted by time`() {
        val parsed = LyricsUtils.parseLyrics("[00:30.00]c\n[00:10.00]a\n[00:20.00]b")
        assertEquals(listOf(10_000L, 20_000L, 30_000L), parsed.map { it.time })
        assertEquals(listOf("a", "b", "c"), parsed.map { it.text })
    }

    @Test
    fun `a line repeated at several timestamps yields one entry per timestamp`() {
        val parsed = LyricsUtils.parseLyrics("[00:10.00][00:40.00]chorus")
        assertEquals(listOf(10_000L, 40_000L), parsed.map { it.time })
    }

    @Test
    fun `non-latin lyrics parse with the same timings as latin ones`() {
        // The sync path must not depend on the script. Same timestamps, Devanagari text.
        val devanagari = LyricsUtils.parseLyrics("[00:10.00]तुम ही हो\n[00:20.50]मेरा")
        assertEquals(listOf(10_000L, 20_500L), devanagari.map { it.time })
        assertEquals(0, LyricsUtils.findCurrentLineIndex(devanagari, 10_000L))
        assertEquals(1, LyricsUtils.findCurrentLineIndex(devanagari, 20_500L))
    }

    @Test
    fun `word timings are shifted with their line by the offset tag`() {
        val parsed = LyricsUtils.parseLyrics(
            "[offset:1000]\n[00:10.00]<00:10.00> hello <00:11.00> world"
        )
        assertEquals(1, parsed.size)
        assertEquals(9_000L, parsed[0].time)
        val words = parsed[0].words
        assertNotNull(words)
        // Words carry seconds, so a one-second shift moves the first word to 9.0s.
        assertEquals(9.0, words!![0].startTime, 0.001)
        assertTrue(words.all { it.startTime >= 0.0 })
    }

    private val timeline = LyricsUtils.parseLyrics(
        "[00:00.000]Line A\n[00:05.000]Line B\n[00:10.000]Line C\n[00:15.000]Line D\n[00:20.000]Line E"
    )

    private fun lineAt(positionMs: Long) =
        timeline.getOrNull(LyricsUtils.findCurrentLineIndex(timeline, positionMs))?.text

    @Test
    fun `every boundary of a five line timeline resolves to the right line`() {
        assertEquals("Line A", lineAt(0L))
        assertEquals("Line A", lineAt(4_999L))
        assertEquals("Line B", lineAt(5_000L))
        assertEquals("Line B", lineAt(9_999L))
        assertEquals("Line C", lineAt(10_000L))
        assertEquals("Line D", lineAt(15_000L))
        assertEquals("Line E", lineAt(20_000L))
        // After the final line it stays on the final line.
        assertEquals("Line E", lineAt(10 * 60_000L))
    }

    @Test
    fun `a series of seeks resolves each position independently of the last`() {
        // The lookup has no memory: whatever came before, a position means one line.
        val seeks = listOf(18_000L to "Line D", 3_000L to "Line A", 12_000L to "Line C", 5_000L to "Line B", 19_000L to "Line D")
        for ((position, expected) in seeks) {
            assertEquals("seek to $position", expected, lineAt(position))
        }
    }
}
