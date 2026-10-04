

package com.shiny.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs


@UnstableApi
@Suppress("DEPRECATION")
class SilenceDetectorAudioProcessor(
    private val minSilenceDurationUs: Long = 2_000_000L,
    private val silenceThreshold: Int = 256,
    private val onLongSilence: () -> Unit,
) : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    @Volatile
    var instantModeEnabled: Boolean = false

    @Volatile
    private var consecutiveSilentFrames: Long = 0

    @Volatile
    private var inSilence: Boolean = false

    private var notifiedThisSilence = false

    /** [minSilenceDurationUs] in frames: a comparison instead of a division per frame. */
    private var minSilentFrames = Long.MAX_VALUE

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        if (encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        minSilentFrames = if (sampleRate > 0) minSilenceDurationUs * sampleRate / 1_000_000L else Long.MAX_VALUE

        return inputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            outputBuffer = EMPTY_BUFFER
            return
        }

        
        if (instantModeEnabled && sampleRate > 0 && channelCount > 0) {
            detectSilence(inputBuffer)
        } else {
            clearSilenceState()
        }

        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    /**
     * Only the ends of a buffer matter: the silent frames at its start extend a silence that
     * was already running, and those at its end start (or continue) the next one. So it reads
     * forward to the first loud frame and back to the last one; in music both are usually the
     * very first sample checked, where the old frame-by-frame scan read every sample.
     */
    private fun detectSilence(inputBuffer: ByteBuffer) {
        val samples = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frameCount = samples.remaining() / channelCount
        if (frameCount == 0) return
        var leading = 0
        while (leading < frameCount && !isLoud(samples, leading)) leading++
        if (leading == frameCount) {
            // All silent: the silence simply goes on.
            addSilentFrames(frameCount.toLong())
            return
        }
        // The silence running into this buffer ends at its first loud frame...
        addSilentFrames(leading.toLong())
        clearSilenceState()
        // ...and a new one starts after its last.
        var trailing = 0
        while (trailing < frameCount && !isLoud(samples, frameCount - 1 - trailing)) trailing++
        addSilentFrames(trailing.toLong())
    }

    private fun isLoud(samples: java.nio.ShortBuffer, frame: Int): Boolean {
        val base = frame * channelCount
        for (c in 0 until channelCount) {
            if (abs(samples.get(base + c).toInt()) >= silenceThreshold) return true
        }
        return false
    }

    private fun addSilentFrames(frames: Long) {
        if (frames == 0L) return
        consecutiveSilentFrames += frames
        if (consecutiveSilentFrames >= minSilentFrames) {
            inSilence = true
            if (!notifiedThisSilence) {
                notifiedThisSilence = true
                onLongSilence()
            }
        }
    }

    private fun clearSilenceState() {
        consecutiveSilentFrames = 0
        inSilence = false
        notifiedThisSilence = false
    }

    fun resetTracking() {
        clearSilenceState()
    }

    fun isCurrentlySilent(): Boolean = inSilence

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        clearSilenceState()
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        return outputBuffer
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
