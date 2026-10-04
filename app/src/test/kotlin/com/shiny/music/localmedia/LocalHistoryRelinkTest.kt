package com.shiny.music.localmedia

import com.shiny.music.localmedia.LocalHistoryRelink.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHistoryRelinkTest {

    private fun track(id: String, title: String = "Song", artist: String = "Artist", duration: Int = 200) =
        Track(id, title, artist, duration)

    @Test
    fun `a moved file keeps its history`() {
        val moves = LocalHistoryRelink.match(
            gone = listOf(track("content://media/external/audio/media/10")),
            arrived = listOf(track("content://media/external/audio/media/99", duration = 201)),
        )
        assertEquals(mapOf("content://media/external/audio/media/10" to "content://media/external/audio/media/99"), moves)
    }

    @Test
    fun `case and spacing do not matter`() {
        val moves = LocalHistoryRelink.match(
            gone = listOf(track("old", title = "Blinding  Lights", artist = "The Weeknd")),
            arrived = listOf(track("new", title = "blinding lights ", artist = "the weeknd")),
        )
        assertEquals(mapOf("old" to "new"), moves)
    }

    @Test
    fun `a different length is a different recording`() {
        val moves = LocalHistoryRelink.match(
            gone = listOf(track("old", duration = 200)),
            arrived = listOf(track("new", duration = 260)),
        )
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `two possible new files means nothing moves`() {
        val moves = LocalHistoryRelink.match(
            gone = listOf(track("old")),
            arrived = listOf(track("copy1"), track("copy2")),
        )
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `two old files claiming one new file means nothing moves`() {
        val moves = LocalHistoryRelink.match(
            gone = listOf(track("old1"), track("old2")),
            arrived = listOf(track("new")),
        )
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `other songs arriving are left alone`() {
        val moves = LocalHistoryRelink.match(
            gone = listOf(track("old", title = "A")),
            arrived = listOf(track("new", title = "A"), track("other", title = "B")),
        )
        assertEquals(mapOf("old" to "new"), moves)
    }
}
