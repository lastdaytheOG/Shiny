package com.shiny.music.ui.liquid.settings

import com.shiny.music.spotifyimport.SpotifyImportProgressUi
import com.shiny.music.spotifyimport.SpotifyImportSourceSummaryUi
import com.shiny.music.spotifyimport.SpotifyImportSourceType
import com.shiny.music.spotifyimport.SpotifyImportSourceUi
import com.shiny.music.spotifyimport.SpotifyImportSummaryUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyImportTextTest {
    private fun source(subtitle: String, count: Int?) =
        SpotifyImportSourceUi("id", "Title", subtitle, null, count, SpotifyImportSourceType.PLAYLIST)

    private fun progress(done: Int, sources: Int, matched: Int, tracks: Int) =
        SpotifyImportProgressUi("Road trip", done, sources, matched, tracks, percent = 0)

    @Test
    fun `a source says what it is and how big`() {
        assertEquals("Playlist · 42 songs", sourceSubtitle(source("Playlist", 42)))
        assertEquals("Playlist · 1 song", sourceSubtitle(source("Playlist", 1)))
        assertEquals("Playlist", sourceSubtitle(source("Playlist", null)))
        assertEquals("0 songs", sourceSubtitle(source(" ", 0)))
        assertEquals("", sourceSubtitle(source("", null)))
    }

    @Test
    fun `progress names the source in turn only when there are several`() {
        assertEquals("12 of 40 songs", progressLine(progress(done = 0, sources = 1, matched = 12, tracks = 40)))
        assertEquals("1 of 3 · 12 of 40 songs", progressLine(progress(done = 0, sources = 3, matched = 12, tracks = 40)))
        assertEquals("3 of 3 · 40 of 40 songs", progressLine(progress(done = 3, sources = 3, matched = 40, tracks = 40)))
    }

    @Test
    fun `the summary counts what came over and admits what did not`() {
        val whole = SpotifyImportSourceSummaryUi("A", totalTracks = 10, importedTracks = 10, failedTracks = 0)
        val partial = SpotifyImportSourceSummaryUi("B", totalTracks = 10, importedTracks = 7, failedTracks = 3)
        val unread = SpotifyImportSourceSummaryUi("C", totalTracks = 0, importedTracks = 0, failedTracks = 0, failed = true)

        assertEquals("10 of 10", summaryValue(whole))
        assertEquals("7 of 10", summaryValue(partial))
        assertEquals("Couldn't be read", summaryValue(unread))

        assertNull(summaryFooter(SpotifyImportSummaryUi(listOf(whole, unread))))
        assertEquals(
            "3 songs could not be matched and were left out.",
            summaryFooter(SpotifyImportSummaryUi(listOf(whole, partial))),
        )
        val one = partial.copy(importedTracks = 9, failedTracks = 1)
        assertEquals(
            "1 song could not be matched and was left out.",
            summaryFooter(SpotifyImportSummaryUi(listOf(one))),
        )
    }
}
