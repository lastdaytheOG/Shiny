package com.music.innertube.models

import com.music.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_TRACK_LYRICS
import com.music.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_TRACK_RELATED
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchTabsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun tabs(body: String) = json.decodeFromString<Tabs>("""{"tabs":[$body]}""").tabs

    private fun linked(title: String, id: String, page: String) =
        """{"tabRenderer":{"title":"$title","endpoint":{"browseEndpoint":{"browseId":"$id","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"$page"}}}}}}"""

    private fun plain(title: String) = """{"tabRenderer":{"title":"$title"}}"""

    @Test
    fun `related is found with the comments tab YouTube added before it`() {
        val found = tabs(
            listOf(
                plain("Up next"),
                linked("Lyrics", "MPLY1", MUSIC_PAGE_TYPE_TRACK_LYRICS),
                plain("Comments"),
                linked("Related", "MPTR1", MUSIC_PAGE_TYPE_TRACK_RELATED),
            ).joinToString(","),
        )
        assertEquals("MPTR1", found.endpointOfPage(MUSIC_PAGE_TYPE_TRACK_RELATED)?.browseId)
        assertEquals("MPLY1", found.endpointOfPage(MUSIC_PAGE_TYPE_TRACK_LYRICS)?.browseId)
    }

    @Test
    fun `the older layout without comments still works`() {
        val found = tabs(
            listOf(
                plain("Up next"),
                linked("Lyrics", "MPLY1", MUSIC_PAGE_TYPE_TRACK_LYRICS),
                linked("Related", "MPTR1", MUSIC_PAGE_TYPE_TRACK_RELATED),
            ).joinToString(","),
        )
        assertEquals("MPTR1", found.endpointOfPage(MUSIC_PAGE_TYPE_TRACK_RELATED)?.browseId)
    }

    @Test
    fun `a song with no lyrics tab has none, rather than a neighbour's`() {
        val found = tabs(listOf(plain("Up next"), linked("Related", "MPTR1", MUSIC_PAGE_TYPE_TRACK_RELATED)).joinToString(","))
        assertNull(found.endpointOfPage(MUSIC_PAGE_TYPE_TRACK_LYRICS))
    }
}
