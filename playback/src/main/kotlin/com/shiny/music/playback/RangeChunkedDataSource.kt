package com.shiny.music.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * A [DataSource] that fetches from [upstream] in ranges of [chunkBytes], so a whole-file read
 * (a download, an export) is never one long request. [RangeChunker] holds the rules.
 */
@UnstableApi
class RangeChunkedDataSource(
    private val upstream: DataSource,
    chunkBytes: Long = DEFAULT_CHUNK_BYTES,
) : DataSource {
    private var request: DataSpec? = null

    private val chunker = RangeChunker(
        object : RangeSource {
            override fun open(position: Long, length: Long): Long {
                val range = checkNotNull(request).buildUpon().setPosition(position).setLength(length).build()
                return try {
                    upstream.open(range)
                } catch (e: IOException) {
                    throw if (DataSourceException.isCausedByPositionOutOfRange(e)) PastEndException(e) else e
                }
            }

            override fun read(target: ByteArray, offset: Int, length: Int): Int = upstream.read(target, offset, length)

            override fun close() = upstream.close()
        },
        chunkBytes,
    )

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        request = dataSpec
        return chunker.open(dataSpec.position, dataSpec.length)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = chunker.read(buffer, offset, length)

    // Between two ranges the upstream is closed and has no URI of its own.
    override fun getUri(): Uri? = upstream.uri ?: request?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        try {
            chunker.close()
        } finally {
            request = null
        }
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): RangeChunkedDataSource =
            RangeChunkedDataSource(upstream.createDataSource(), chunkBytes)
    }

    companion object {
        /** Small enough that YouTube serves each range unthrottled, large enough to keep requests few. */
        const val DEFAULT_CHUNK_BYTES = 5L * 1024 * 1024
    }
}
