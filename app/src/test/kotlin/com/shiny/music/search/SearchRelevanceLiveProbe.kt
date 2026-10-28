package com.shiny.music.search

import com.music.innertube.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SearchRelevanceLiveProbe {

    @Test
    fun probe() = runBlocking {
        assumeTrue("set SHINY_SEARCH_LIVE=1 to rank live YouTube Music searches", System.getenv("SHINY_SEARCH_LIVE") == "1")
        val queries = System.getenv("SHINY_SEARCH_QUERIES")?.split('|')
            ?: listOf(TERRANOVA, "kesariya arijit", "Terra Nova gold", "Aphria financials", "blinding lights cover")

        for (query in queries) {
            val ranked = SearchResults.summary(query, YouTube.searchSummary(query).getOrThrow())
            println(ranked.report.lines().joinToString("\n"))
            println("  => top: ${ranked.top?.title} | " + ranked.sections.joinToString(" | ") { s -> "${s.shelf}: ${s.items.size}" })
            assertTrue(ranked.all().none { it.searchKind() == SearchKind.Unsupported })
        }

        val terranova = SearchResults.summary(TERRANOVA, YouTube.searchSummary(TERRANOVA).getOrThrow())
        assertTrue(terranova.top?.title.orEmpty().contains("terranova", ignoreCase = true))
        assertTrue(terranova.shelf(SearchShelf.Songs).all { it.title.contains("terranova", ignoreCase = true) })

        val songs = YouTube.search(TERRANOVA, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        val songPage = SearchResults.page(TERRANOVA, songs.correctedQuery, YouTube.SearchFilter.FILTER_SONG, songs.items, emptyList(), 1)
        println(songPage.report.lines().joinToString("\n"))
        assertTrue(songPage.items.all { it.searchKind() == SearchKind.Song })
    }
}
