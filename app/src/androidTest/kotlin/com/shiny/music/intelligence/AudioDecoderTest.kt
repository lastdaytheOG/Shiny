package com.shiny.music.intelligence

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class AudioDecoderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clickTrackWav(bpm: Double, seconds: Int, rate: Int = 44_100): File {
        val samples = ShortArray(rate * seconds)
        val beat = (60.0 / bpm * rate).toInt()
        var i = 0
        while (i < samples.size) {
            for (j in 0 until minOf(1_600, samples.size - i)) {
                samples[i + j] = (exp(-j / 320.0) * sin(2 * PI * 880.0 * j / rate) * 20_000).toInt().toShort()
            }
            i += beat
        }
        val data = samples.size * 2
        val bytes = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        bytes.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(data)
        samples.forEach { bytes.putShort(it) }
        return File(context.cacheDir, "click-$bpm.wav").apply { writeBytes(bytes.array()) }
    }

    @Test
    fun aRealFileIsDecodedAndItsTempoMeasured() {
        val file = clickTrackWav(120.0, 40)
        try {
            val audio = AudioDecoder.decode(context, Uri.fromFile(file))
            assertNotNull(audio)
            assertTrue("about 40 s of mono at the target rate, got ${audio!!.size}", audio.size in (AudioDecoder.TARGET_RATE * 35)..(AudioDecoder.TARGET_RATE * 45))
            val measured = AudioFeatures.analyse(audio, AudioDecoder.TARGET_RATE)
            assertNotNull(measured)
            val closest = listOf(measured!!.bpm / 2.0, measured.bpm.toDouble(), measured.bpm * 2.0).minBy { abs(it - 120.0) }
            assertEquals("measured ${measured.bpm}", 120.0, closest, 3.0)
        } finally {
            file.delete()
        }
    }

    @Test
    fun aFileThatIsNotAudioGivesNothing() {
        val file = File(context.cacheDir, "not-audio.bin").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
        try {
            assertNull(runCatching { AudioDecoder.decode(context, Uri.fromFile(file)) }.getOrNull())
        } finally {
            file.delete()
        }
    }
}
