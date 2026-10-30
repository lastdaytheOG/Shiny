package com.shiny.music.intelligence

import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RelatedLiveProbe {
    @Test
    fun probe() = runBlocking {
        assumeTrue("set SHINY_RELATED_LIVE=1 to ask YouTube for related songs", System.getenv("SHINY_RELATED_LIVE") == "1")
        for (id in listOf("ufCaQrNCUC4", "PvM79DJ2PmM", "Ro0vTEuSUuo", "fImgNQqBxOA")) {
            val endpoint = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow().relatedEndpoint
            assertNotNull("no related tab for $id", endpoint)
            val page = YouTube.related(endpoint!!).getOrThrow()
            println("RELATED $id songs=${page.songs.size} albums=${page.albums.size} artists=${page.artists.size} playlists=${page.playlists.size}")
            page.songs.take(3).forEach { println("   ${it.title.take(32)} / ${it.artists.joinToString { a -> a.name }.take(32)}") }
            assertTrue("no related songs for $id", page.songs.isNotEmpty())
        }
    }
}
