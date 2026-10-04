package com.shiny.music.playback

import java.io.EOFException
import java.io.IOException
import kotlin.math.min

/**
 * What [RangeChunker] needs from the thing it reads: one byte range at a time.
 *
 * It is the part of a Media3 `DataSource` that range chunking uses, without the Android types, so
 * the chunking rules can be tested on the JVM.
 */
interface RangeSource {
    /**
     * Opens [length] bytes starting at [position]. Returns how many bytes can be read, or
     * [RangeChunker.UNSET] if the source does not say. Throws [PastEndException] when [position]
     * is at or beyond the end of the resource.
     */
    @Throws(IOException::class)
    fun open(position: Long, length: Long): Long

    /** Reads up to [length] bytes; returns the count, or [RangeChunker.END] when the range is spent. */
    @Throws(IOException::class)
    fun read(target: ByteArray, offset: Int, length: Int): Int

    @Throws(IOException::class)
    fun close()
}

/** The source was asked for a position at or past its end. [original] is what it threw. */
class PastEndException(val original: IOException) : IOException(original)

/**
 * Serves one long read as a run of bounded range requests.
 *
 * YouTube's media servers slow a connection down once it has delivered more than a few megabytes;
 * a request for a bounded range is served at full speed. So a download asks for [chunkBytes] at a
 * time and opens the next range where the last one stopped. The caller sees one continuous stream.
 *
 * Rules:
 * - A request of known length is split into ranges of at most [chunkBytes]; the last one is the
 *   remainder.
 * - A request of unknown length ends at the first range that comes back short, or when the source
 *   reports that the next position is past its end.
 * - A range that stops early inside a request of known length is reopened where it stopped. If it
 *   delivered nothing at all, the data is missing and the read fails.
 */
class RangeChunker(private val source: RangeSource, private val chunkBytes: Long) {
    private var position = 0L
    private var end = UNSET
    private var chunkOpen = false
    private var chunkLeft = 0L
    private var chunkDelivered = 0L
    private var finished = true

    init {
        require(chunkBytes > 0) { "chunkBytes must be positive" }
    }

    /**
     * Starts a read of [length] bytes at [position] ([UNSET] reads to the end). Returns the number
     * of bytes that will be delivered, or [UNSET] when that is not known yet.
     */
    @Throws(IOException::class)
    fun open(position: Long, length: Long): Long {
        closeChunk()
        this.position = position
        end = if (length == UNSET) UNSET else position + length
        finished = length == 0L
        if (finished) return 0
        openChunk(first = true)
        return if (end == UNSET) UNSET else end - position
    }

    @Throws(IOException::class)
    fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            if (finished) return END
            if (end != UNSET && position >= end) {
                closeChunk()
                finished = true
                return END
            }
            if (!chunkOpen) {
                openChunk(first = false)
                if (finished) return END
            }
            if (chunkLeft == 0L) {
                closeChunk()
                continue
            }
            val count = source.read(target, offset, min(length.toLong(), chunkLeft).toInt())
            if (count > 0) {
                position += count
                chunkLeft -= count
                chunkDelivered += count
                return count
            }
            if (count == 0) return 0
            // The range ended before it delivered what was asked for.
            val delivered = chunkDelivered
            closeChunk()
            when {
                end == UNSET -> finished = true
                delivered == 0L -> throw EOFException("No data at $position, ${end - position} bytes short")
            }
        }
    }

    @Throws(IOException::class)
    fun close() {
        finished = true
        closeChunk()
    }

    /** Opens the range at [position]. */
    private fun openChunk(first: Boolean) {
        val wanted = if (end == UNSET) chunkBytes else min(chunkBytes, end - position)
        // Set before the call: a source whose open() throws must still be closed.
        chunkOpen = true
        chunkDelivered = 0
        chunkLeft = 0
        val available = try {
            source.open(position, wanted)
        } catch (pastEnd: PastEndException) {
            // Only a read of unknown length may run off the end, and only after its first range.
            if (first || end != UNSET) throw pastEnd.original
            closeChunk()
            finished = true
            return
        }
        chunkLeft = if (available == UNSET) wanted else min(available, wanted)
        // With the length unknown, a range shorter than the one requested is the last one.
        if (end == UNSET && available != UNSET && available < wanted) end = position + available
    }

    private fun closeChunk() {
        if (!chunkOpen) return
        chunkOpen = false
        chunkLeft = 0
        source.close()
    }

    companion object {
        /** Same value as Media3's `C.LENGTH_UNSET`. */
        const val UNSET = -1L

        /** Same value as Media3's `C.RESULT_END_OF_INPUT`. */
        const val END = -1
    }
}
