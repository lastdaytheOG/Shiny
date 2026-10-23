package com.shiny.music.discover

import androidx.media3.common.MediaItem
import com.music.innertube.models.SongItem
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.models.MediaMetadata
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.playback.queues.Queue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException

class ChartPager(
    first: SongItem,
    private val rest: List<ChartEntry>,
    private val resolve: suspend (ChartEntry) -> Resolution<SongItem>,
) {
    companion object {
        const val PAGE = 6
        const val MAX_PAGES_PER_CALL = 4
    }

    private var cursor = 0
    private val queued = hashSetOf(first.id)

    val hasMore: Boolean get() = cursor < rest.size

    suspend fun next(): List<SongItem> {
        repeat(MAX_PAGES_PER_CALL) {
            if (cursor >= rest.size) return emptyList()
            val start = cursor
            val page = rest.subList(start, minOf(start + PAGE, rest.size))
            val results = coroutineScope { page.map { async { resolve(it) } }.awaitAll() }
            if (results.all { it is Resolution.Failed }) throw IOException("Chart songs could not be looked up")
            cursor = start + page.size
            val found = results.mapNotNull { (it as? Resolution.Found)?.item }.filter { queued.add(it.id) }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }
}

class AppleChartQueue(
    private val title: String?,
    first: SongItem,
    rest: List<ChartEntry>,
    resolve: suspend (ChartEntry) -> Resolution<SongItem>,
) : Queue {
    private val pager = ChartPager(first, rest, resolve)

    override val preloadItem: MediaMetadata = first.toMediaMetadata()

    override val pagesAreSource: Boolean get() = true

    override suspend fun getInitialStatus() = Queue.Status(title, listOf(preloadItem.toMediaItem()), 0)

    override fun hasNextPage(): Boolean = pager.hasMore

    override suspend fun nextPage(): List<MediaItem> = pager.next().map { it.toMediaMetadata().toMediaItem() }
}
