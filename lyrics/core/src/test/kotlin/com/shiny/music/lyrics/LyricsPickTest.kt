package com.shiny.music.lyrics

import com.shiny.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing between the providers' answers. The order and timings in these cases are the
 * ones seen on real Hindi songs, where a hand-timed file often arrived first and was late.
 */
class LyricsPickTest {

    private val order = listOf("YouLyPlus", "Paxsenix", "Unison", "BetterLyrics", "SimpMusic", "LrcLib", "Kugou")

    private val studio = "[00:10.23]Hum tere bin ab reh nahi sakte\n[00:15.39]Tere bina kya wajood mera\n[04:02.29]Tum hi ho"
    private val handTimed = "[00:11.20]Hum tere bin ab reh nahi sakte\n[00:15.55]Tere bina kya wajood mera\n[03:59.69]Tum hi ho"
    private val allAtZero = "[00:00.00]Tere vaaste falak se\n[00:00.00]Main chaand launga\n[00:00.00]Solah satrah sitaare"

    @Test
    fun `a studio-timed synced file wins at once`() {
        val pick = LyricsPick(order, durationSeconds = 262)
        val chosen = pick.answer("YouLyPlus", studio)
        assertEquals("YouLyPlus", chosen?.providerName)
    }

    @Test
    fun `a hand-timed file that arrives first waits for the studio-timed providers`() {
        val pick = LyricsPick(order, durationSeconds = 262)
        assertNull(pick.answer("Kugou", handTimed))
        assertTrue(pick.holding)
        assertNull(pick.answer("LrcLib", handTimed))
        // BetterLyrics (Apple's timings) arrives later and still wins.
        assertEquals("BetterLyrics", pick.answer("BetterLyrics", studio)?.providerName)
    }

    @Test
    fun `a hand-timed file is used once every studio-timed provider came back empty`() {
        val pick = LyricsPick(order, durationSeconds = 262)
        assertNull(pick.answer("Kugou", handTimed))
        assertNull(pick.answer("YouLyPlus", null))
        assertNull(pick.answer("Paxsenix", LYRICS_NOT_FOUND))
        assertNull(pick.answer("Unison", "plain words only"))
        assertNull(pick.answer("LrcLib", handTimed))
        // The last studio-timed provider has nothing: the hand-timed files are all there is,
        // and preference order picks LrcLib over KuGou although KuGou answered first.
        assertEquals("LrcLib", pick.answer("BetterLyrics", null)?.providerName)
    }

    @Test
    fun `when the grace period ends a held hand-timed file is taken`() {
        val pick = LyricsPick(order, durationSeconds = 262)
        pick.answer("Kugou", handTimed)
        assertEquals("Kugou", pick.timeUp()?.providerName)
    }

    @Test
    fun `timing up with nothing held gives nothing`() {
        assertNull(LyricsPick(order, durationSeconds = 262).timeUp())
    }

    @Test
    fun `lyrics with every line at zero are plain lyrics, shown without timestamps`() {
        assertFalse(LyricsPick.hasRealTimings(allAtZero))
        val pick = LyricsPick(listOf("YouLyPlus"), durationSeconds = 190)
        val chosen = pick.answer("YouLyPlus", allAtZero)
        assertEquals("Tere vaaste falak se\nMain chaand launga\nSolah satrah sitaare", chosen?.lyrics)
        assertFalse(LyricsPick.looksSynced(chosen!!.lyrics!!))
    }

    @Test
    fun `a title line at zero does not make a file unsynced`() {
        assertTrue(LyricsPick.hasRealTimings("[00:00.00]Tere Vaaste - Varun Jain\n$handTimed"))
    }

    @Test
    fun `a studio-timed file for a longer recording loses to a hand-timed one that fits`() {
        val pick = LyricsPick(listOf("YouLyPlus", "LrcLib"), durationSeconds = 200)
        // Last line at 4:02 for a 3:20 track: another recording.
        assertNull(pick.answer("YouLyPlus", studio))
        val fits = "[00:11.16]Ishq mein dil bana hai\n[03:09.04]Chaleya"
        assertEquals("LrcLib", pick.answer("LrcLib", fits)?.providerName)
    }

    @Test
    fun `nothing synced falls back to plain lyrics, then to not found`() {
        val pick = LyricsPick(listOf("YouLyPlus", "LrcLib"), durationSeconds = 200)
        assertNull(pick.answer("LrcLib", "just the words"))
        assertEquals("LrcLib", pick.answer("YouLyPlus", null)?.providerName)

        val none = LyricsPick(listOf("YouLyPlus"), durationSeconds = 200)
        assertEquals(LYRICS_NOT_FOUND, none.answer("YouLyPlus", null)?.lyrics)
    }

    @Test
    fun `only the streaming services' own timings count as studio-timed`() {
        listOf("YouLyPlus", "Paxsenix", "BetterLyrics", "Unison").forEach { assertTrue(it, LyricsPick.isStudioTimed(it)) }
        listOf("LrcLib", "Kugou", "SimpMusic", "YouTube Subtitle", "YouTube Music", "Unknown").forEach {
            assertFalse(it, LyricsPick.isStudioTimed(it))
        }
    }
}
