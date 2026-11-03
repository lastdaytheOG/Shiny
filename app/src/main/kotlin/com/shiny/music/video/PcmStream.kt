// PcmStream.kt

package com.shiny.music.video

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.coroutines.coroutineContext

// decodes a stream to 16-bit pcm for the aligner, through any media3 data source
@UnstableApi
internal object StreamPcmDecoder {
    private const val TimeoutUs = 10_000L

    /** How a decode ended: whether the stream ran out, how long it said it was, and how far it got. */
    class Outcome(val reachedEnd: Boolean, val durationUs: Long, val lastPresentationUs: Long)

    /**
     * Decodes [uri], cached under [key], up to [limitUs]. Each block of PCM goes to [onPcm] with its
     * channel count, sample rate and the time it starts at in the song, µs. That time is the
     * decoder's own: for a stream whose first samples are encoder padding it starts below zero,
     * which is what the player skips too.
     */
    suspend fun decode(
        factory: DataSource.Factory,
        uri: Uri,
        key: String,
        limitUs: Long,
        onPcm: (pcm: ShortBuffer, channels: Int, sampleRate: Int, presentationUs: Long) -> Unit,
    ): Outcome {
        val source = Media3MediaDataSource(factory, uri, key)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("no audio track in the stream")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("no audio format")
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            var sampleRate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 0)
            var channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var reachedEnd = false
            var lastPresentationUs = 0L
            while (true) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TimeoutUs)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        val size = if (input != null) extractor.readSampleData(input, 0) else -1
                        if (size < 0 || extractor.sampleTime > limitUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                            if (size < 0) reachedEnd = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, TimeoutUs)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = out.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        val encoding = out.intOr(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                        if (encoding != android.media.AudioFormat.ENCODING_PCM_16BIT) error("decoder gives PCM encoding $encoding")
                    }
                    outputIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0 && sampleRate > 0 && channels > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            onPcm(buffer.order(ByteOrder.nativeOrder()).asShortBuffer(), channels, sampleRate, info.presentationTimeUs)
                            lastPresentationUs = info.presentationTimeUs
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return Outcome(reachedEnd = reachedEnd, durationUs = durationUs, lastPresentationUs = lastPresentationUs)
        } finally {
            codec?.runCatching { stop() }
            codec?.runCatching { release() }
            runCatching { extractor.release() }
            runCatching { source.close() }
        }
    }

    private fun MediaFormat.intOr(key: String, default: Int): Int = if (containsKey(key)) getInteger(key) else default
}

// MediaExtractor reading through a media3 data source; a source that stops at a chunk is reopened where it stopped
@UnstableApi
private class Media3MediaDataSource(
    private val factory: DataSource.Factory,
    private val uri: Uri,
    private val key: String,
) : MediaDataSource() {
    private var source: DataSource? = null
    private var position = 0L
    private var length = C.LENGTH_UNSET.toLong()
    private var ended = false

    override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, size: Int): Int {
        if (size == 0) return 0
        if (length != C.LENGTH_UNSET.toLong() && offset >= length) return -1
        if (source == null || offset != position) {
            if (!reopen(offset)) return -1
        }
        var total = 0
        var reopened = false
        while (total < size) {
            val current = source ?: break
            val read = current.read(buffer, bufferOffset + total, size - total)
            if (read == C.RESULT_END_OF_INPUT) {
                // the end of this open: the end of the file, or only of the chunk it was given
                if (length != C.LENGTH_UNSET.toLong() && position >= length) break
                if (reopened || ended || !reopen(position)) break
                reopened = true
                continue
            }
            reopened = false
            total += read
            position += read
        }
        return if (total == 0) -1 else total
    }

    override fun getSize(): Long {
        if (length == C.LENGTH_UNSET.toLong() && source == null) runCatching { reopen(0L) }
        return if (length == C.LENGTH_UNSET.toLong()) -1L else length
    }

    override fun close() {
        runCatching { source?.close() }
        source = null
    }

    /** false once there's nothing left to open: asked past the end of the stream */
    private fun reopen(offset: Long): Boolean {
        close()
        val next = factory.createDataSource()
        // youtube serves an open-ended range at listening speed; small pieces come at full speed
        val spec = DataSpec.Builder().setUri(uri).setKey(key).setPosition(offset).setLength(ChunkBytes).build()
        val opened = try {
            next.open(spec)
        } catch (error: HttpDataSource.InvalidResponseCodeException) {
            runCatching { next.close() }
            if (error.responseCode == 416 && offset > 0) {
                ended = true
                if (length == C.LENGTH_UNSET.toLong()) length = offset
                return false
            }
            throw error
        }
        val headers = next.responseHeaders
        totalFromContentRange(headers)?.let { length = it }
        // nothing came from the network: the cache served it, and knows how much there is
        if (length == C.LENGTH_UNSET.toLong() && headers.isEmpty() && opened != C.LENGTH_UNSET.toLong()) {
            length = offset + opened
        }
        source = next
        position = offset
        return true
    }

    private fun totalFromContentRange(headers: Map<String, List<String>>): Long? {
        val value = headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull() ?: return null
        return value.substringAfterLast('/', "").trim().toLongOrNull()
    }
}

private const val ChunkBytes = 512L * 1024
