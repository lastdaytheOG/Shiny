

package com.shiny.music.playback.queues

import androidx.media3.common.MediaItem
import com.shiny.music.models.MediaMetadata

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
    /** The items are already in a shuffle order (Smart Shuffle) that shuffle mode must keep. */
    val preShuffled: Boolean = false,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex, position)

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()
}
