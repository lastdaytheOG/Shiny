package com.shiny.music.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.NextResult
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    private var pendingAutomix: List<MediaItem> = emptyList()

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            var lastException: Throwable? = null


            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    val source = pagesAreSource
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = ownItems(nextResult, source),
                        mediaItemIndex = nextResult.currentIndex ?: 0,
                    )
                } catch (e: Exception) {
                    lastException = e

                    if (attempt == 0 && endpoint.videoId != null && endpoint.playlistId == null) {
                        endpoint = WatchEndpoint(
                            videoId = endpoint.videoId,
                            playlistId = "RDAMVM${endpoint.videoId}"
                        )
                    }
                }
            }
            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    override fun hasNextPage(): Boolean = pendingAutomix.isNotEmpty() || continuation != null

    override val pagesAreSource: Boolean
        get() = pendingAutomix.isEmpty() && endpoint.playlistId?.let { !isRadioPlaylistId(it) } == true

    override suspend fun nextPage(): List<MediaItem> {
        if (pendingAutomix.isNotEmpty()) {
            return pendingAutomix.also { pendingAutomix = emptyList() }
        }
        return withContext(IO) {
            var lastException: Throwable? = null

            for (attempt in 0..maxRetries) {
                try {
                    val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                    val source = pagesAreSource
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    retryCount = 0
                    return@withContext ownItems(nextResult, source)
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    private fun ownItems(result: NextResult, source: Boolean): List<MediaItem> {
        val items = result.items.map { it.toMediaItem() }
        val automixFrom = result.automixStartIndex?.coerceIn(0, items.size)
        if (!source || automixFrom == null) return items
        pendingAutomix = items.subList(automixFrom, items.size).toList()
        return items.subList(0, automixFrom).toList()
    }

    companion object {

        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                WatchEndpoint(videoId = song.id),
                song
            )
        }

        // RDCLAK5uy_ ids are curated playlists, not radios
        fun isRadioPlaylistId(playlistId: String): Boolean =
            playlistId.startsWith("RD") && !playlistId.startsWith("RDCLAK")
    }
}
