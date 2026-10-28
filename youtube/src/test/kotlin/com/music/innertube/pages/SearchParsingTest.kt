package com.music.innertube.pages

import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.SectionListRenderer
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_PODCAST_EPISODE
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchParsingTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val episodeRow = """
        {
          "flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "What Aphria Financials Mean for Investors"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
              {"text": "Episode"}, {"text": " • "}, {"text": "Feb 6, 2019"}, {"text": " • "},
              {"text": "RICH TV LIVE PODCAST", "navigationEndpoint": {"browseEndpoint": {"browseId": "MPSPPLtest",
                "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"}}}}}
            ]}}}
          ],
          "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://i.ytimg.com/vi/ep1/hq.jpg", "width": 120, "height": 120}]}}},
          "overlay": {"musicItemThumbnailOverlayRenderer": {"content": {"musicPlayButtonRenderer": {"playNavigationEndpoint": {
            "watchEndpoint": {"videoId": "ep1", "watchEndpointMusicSupportedConfigs": {"watchEndpointMusicConfig": {"musicVideoType": "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"}}}
          }}}}},
          "playlistItemData": {"videoId": "ep1"}
        }
    """.trimIndent()

    @Test
    fun `a podcast episode row parses as a song item that carries the episode type`() {
        val renderer = json.decodeFromString<MusicResponsiveListItemRenderer>(episodeRow)

        val summaryItem = SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer)
        val filterItem = SearchPage.toYTItem(renderer)

        for (item in listOf(summaryItem, filterItem)) {
            assertTrue(item is SongItem)
            item as SongItem
            assertEquals("ep1", item.id)
            assertEquals(MUSIC_VIDEO_TYPE_PODCAST_EPISODE, item.musicVideoType)
            assertTrue(item.isVideoSong)
        }
    }

    @Test
    fun `the corrected query is read from either correction renderer`() {
        val didYouMean = json.decodeFromString<List<SectionListRenderer.Content>>(
            """[{"itemSectionRenderer": {"contents": [{"didYouMeanRenderer": {"correctedQuery": {"runs": [{"text": "terranova", "italics": true}, {"text": " janina"}]}}}]}}]""",
        )
        val showingResultsFor = json.decodeFromString<List<SectionListRenderer.Content>>(
            """[{"itemSectionRenderer": {"contents": [{"showingResultsForRenderer": {"correctedQuery": {"runs": [{"text": "kesariya arijit"}]}}}]}}]""",
        )
        val none = json.decodeFromString<List<SectionListRenderer.Content>>(
            """[{"itemSectionRenderer": {"contents": [{"musicResponsiveListItemRenderer": null}]}}]""",
        )

        assertEquals("terranova janina", SearchSummaryPage.correctedQuery(didYouMean))
        assertEquals("kesariya arijit", SearchSummaryPage.correctedQuery(showingResultsFor))
        assertNull(SearchSummaryPage.correctedQuery(none))
    }
}
