package com.shiny.music.discover

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AppleChartsLiveTest {

    @Test
    fun `every category comes back from apple's public feed`() = runBlocking {
        assumeTrue("set SHINY_CHARTS_LIVE=1 to ask Apple's live feed", System.getenv("SHINY_CHARTS_LIVE") == "1")
        val source = AppleRssSource()
        var complete = 0
        for (storefront in listOf("in", "us", "jp")) {
            val charts = ChartKind.entries.associateWith { kind ->
                runCatching { source.fetch(storefront, kind) }.onFailure { println("$storefront $kind failed: $it") }.getOrNull()
            }
            println("$storefront " + charts.entries.joinToString { (k, c) -> "$k=${c?.entries?.size}" })
            val songs = charts[ChartKind.Songs] ?: continue
            val albums = charts[ChartKind.Albums] ?: continue
            val videos = charts[ChartKind.Videos] ?: continue
            assertEquals(100, songs.entries.size)
            assertEquals((1..100).toList(), songs.entries.map { it.position })
            assertTrue(albums.entries.size in 1..20)
            assertTrue(videos.entries.size in 1..20)
            assertTrue(songs.entries.all { it.artwork != null && AppleArtwork.square(it.artwork, 300) != it.artwork })
            assertTrue(videos.entries.all { it.artwork != null && AppleArtwork.landscape(it.artwork, 640) != it.artwork })
            ChartArtistRanking.rank(songs.entries).take(5).forEachIndexed { i, a ->
                println("  ${i + 1}. ${a.name} — ${a.songCount} lead, ${a.featuredOn} featured, best #${a.bestPosition}")
            }
            complete++
        }
        assertTrue("no storefront answered every category", complete > 0)
    }
}
