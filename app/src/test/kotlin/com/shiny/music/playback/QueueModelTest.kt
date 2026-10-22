package com.shiny.music.playback

import androidx.media3.common.MediaItem
import com.music.innertube.models.WatchEndpoint
import com.shiny.music.playback.queues.EmptyQueue
import com.shiny.music.playback.queues.ListQueue
import com.shiny.music.playback.queues.YouTubePlaylistQueue
import com.shiny.music.playback.queues.YouTubeQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelTest {
    @Test
    fun `list queue keeps every item beyond the former initial page size`() = runBlocking {
        val items = (0 until 1_000).map { index -> mediaItem("song-$index") }

        val status = ListQueue(items = items, startIndex = 777).getInitialStatus()

        assertEquals(1_000, status.items.size)
        assertEquals(777, status.mediaItemIndex)
        assertEquals("song-999", status.items.last().mediaId)
    }

    @Test
    fun `thousands of songs keep their order`() = runBlocking {
        val items = (0 until 5_000).map { index -> mediaItem("song-$index") }

        val status = ListQueue(items = items).getInitialStatus().filterExplicit().filterVideoSongs(true)

        assertEquals(items.map { it.mediaId }, status.items.map { it.mediaId })
        assertEquals(0, status.mediaItemIndex)
    }

    @Test
    fun `list queue preserves duplicate occurrences and their selected position`() = runBlocking {
        val first = mediaItem("duplicate")
        val middle = mediaItem("between")
        val second = mediaItem("duplicate")

        val status = ListQueue(items = listOf(first, middle, second), startIndex = 2).getInitialStatus()

        assertEquals(listOf("duplicate", "between", "duplicate"), status.items.map { it.mediaId })
        assertEquals(2, status.mediaItemIndex)
        assertSame(second, status.items[2])
    }

    @Test
    fun `an empty list queue is safe`() = runBlocking {
        val status = ListQueue(items = emptyList()).getInitialStatus()
        assertTrue(status.items.isEmpty())
        assertFalse(ListQueue(items = emptyList()).hasNextPage())
        assertFalse(EmptyQueue.hasNextPage())
    }

    @Test
    fun `radio ids are told apart from playlists`() {
        assertTrue(YouTubeQueue.isRadioPlaylistId("RDAMVMabc"))
        assertTrue(YouTubeQueue.isRadioPlaylistId("RDEMxyz"))
        assertTrue(YouTubeQueue.isRadioPlaylistId("RDAMPLxyz"))
        assertFalse(YouTubeQueue.isRadioPlaylistId("RDCLAK5uy_abc"))
        assertFalse(YouTubeQueue.isRadioPlaylistId("PLabc"))
        assertFalse(YouTubeQueue.isRadioPlaylistId("OLAK5uy_abc"))
    }

    @Test
    fun `only playlist continuations count as the queue itself`() {
        assertTrue(YouTubePlaylistQueue(playlistId = "PLabc").pagesAreSource)
        assertTrue(YouTubeQueue(WatchEndpoint(playlistId = "PLabc")).pagesAreSource)
        assertFalse(YouTubeQueue(WatchEndpoint(videoId = "abc")).pagesAreSource)
        assertFalse(YouTubeQueue(WatchEndpoint(videoId = "abc", playlistId = "RDAMVMabc")).pagesAreSource)
        assertFalse(ListQueue(items = emptyList()).pagesAreSource)
    }

    private fun mediaItem(id: String): MediaItem = MediaItem.Builder().setMediaId(id).build()
}
