package com.shiny.music.intelligence

import android.media.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmFramesTest {
    private fun buffer(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun `float output from a decoder is read as float, not as noise`() {
        val bytes = buffer(16).apply {
            putFloat(0.5f).putFloat(0.25f).putFloat(-0.5f).putFloat(-0.25f)
            flip()
        }
        val frames = PcmFrames(bytes, channels = 2, encoding = AudioFormat.ENCODING_PCM_FLOAT)
        assertEquals(2, frames.count)
        assertEquals(0.375f, frames.mono(0), 1e-6f)
        assertEquals(-0.375f, frames.mono(1), 1e-6f)
    }

    @Test
    fun `sixteen bit output keeps working`() {
        val bytes = buffer(4).apply {
            putShort(16_384).putShort(-16_384)
            flip()
        }
        val frames = PcmFrames(bytes, channels = 1, encoding = AudioFormat.ENCODING_PCM_16BIT)
        assertEquals(2, frames.count)
        assertEquals(0.5f, frames.mono(0), 1e-4f)
        assertEquals(-0.5f, frames.mono(1), 1e-4f)
    }

    @Test
    fun `thirty two and twenty four bit output are scaled to the same range`() {
        val wide = buffer(4).apply {
            putInt(1 shl 30)
            flip()
        }
        assertEquals(0.5f, PcmFrames(wide, 1, AudioFormat.ENCODING_PCM_32BIT).mono(0), 1e-4f)

        val packed = buffer(3).apply {
            put(0).put(0).put(0xC0.toByte())
            flip()
        }
        assertEquals(-0.5f, PcmFrames(packed, 1, AudioFormat.ENCODING_PCM_24BIT_PACKED).mono(0), 1e-4f)
    }

    @Test
    fun `a buffer that starts part way through is read from where it starts`() {
        val bytes = buffer(8).apply {
            putFloat(0.9f).putFloat(0.1f)
            position(4)
        }
        val frames = PcmFrames(bytes, 1, AudioFormat.ENCODING_PCM_FLOAT)
        assertEquals(1, frames.count)
        assertEquals(0.1f, frames.mono(0), 1e-6f)
    }
}
