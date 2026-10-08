package com.shiny.music.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.shiny.music.eq.audio.CustomEqualizerAudioProcessor
import com.shiny.music.eq.data.FilterType
import com.shiny.music.eq.data.ParametricEQ
import com.shiny.music.eq.data.ParametricEQBand
import com.shiny.music.eq.fx.EffectPreset
import com.shiny.music.eq.fx.SoundFxAudioProcessor
import com.shiny.music.eq.fx.SoundFxEngine
import com.shiny.music.eq.fx.SoundFxSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The effects run on real PCM here, so these are listening tests without ears: exact
 * pass-through at Normal, no clipping on the worst combination, no clicks when things are
 * switched, and 8D never leaving an ear silent.
 */
class SoundFxProcessingTest {
    private val rate = 48_000

    @Before
    fun neutral() {
        SoundFxEngine.update { SoundFxSettings() }
        SoundFxEngine.setEnabled(false)
        SoundFxEngine.setOriginal(false)
        SoundFxEngine.eqHeadroomDb = 0f
    }

    @Test
    fun `changing an effect turns the Equaliser on, and off plays the song as it is`() {
        SoundFxEngine.update { EffectPreset.EightDReverb.settings(it) }
        assertTrue(SoundFxEngine.enabled.value)
        val p = fx()
        val input = stereoSine(440.0, 0.5, rate / 10)
        val processed = process(p, input, channels = 2)
        assertTrue("effects on should change the sound", !processed.contentEquals(input))

        SoundFxEngine.setEnabled(false)
        // It glides back (and lets the reverb ring out) before it is an exact copy again.
        repeat(80) { process(p, ShortArray(rate / 10 * 2), channels = 2) }
        assertArrayEquals(input, process(p, input, channels = 2))
    }

    @Test
    fun `the silence detector fires once for a long silence and resets on sound`() {
        var fired = 0
        val detector = com.shiny.music.playback.audio.SilenceDetectorAudioProcessor(minSilenceDurationUs = 2_000_000L) { fired++ }
        detector.instantModeEnabled = true
        detector.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        detector.flush()
        val loud = stereoSine(440.0, 0.5, rate / 50)
        val quiet = ShortArray(rate / 50 * 2)
        process(detector, loud, channels = 2)
        repeat(99) { process(detector, quiet, channels = 2) } // 1.98 s
        assertEquals(0, fired)
        repeat(3) { process(detector, quiet, channels = 2) } // past 2 s
        assertEquals(1, fired)
        repeat(50) { process(detector, quiet, channels = 2) }
        assertEquals("once per silence", 1, fired)
        process(detector, loud, channels = 2)
        assertTrue(!detector.isCurrentlySilent())
    }

    @Test
    fun `Normal is an exact copy`() {
        val input = stereoSine(440.0, 0.8, 4_800)
        val out = run(fx(), input)
        assertArrayEquals(input, out)
    }

    @Test
    fun `mono is widened to two identical channels`() {
        val p = fx(channels = 1)
        val mono = ShortArray(1_000) { (sin(2 * PI * 440 * it / rate) * 12_000).toInt().toShort() }
        val out = process(p, mono, channels = 1)
        assertEquals(2_000, out.size)
        for (i in mono.indices) {
            assertEquals(mono[i], out[2 * i])
            assertEquals(mono[i], out[2 * i + 1])
        }
    }

    @Test
    fun `the heaviest combination never clips`() {
        SoundFxEngine.update { EffectPreset.EightDSlowedReverb.settings(it).copy(bassDb = 12f, clarityDb = 6f, stereoWidth = 2f) }
        SoundFxEngine.eqHeadroomDb = 12f // a big bass boost upstream, half given back here
        // A full-scale bass-heavy signal: the worst case for every boost at once.
        val input = ShortArray(rate * 2 * 3) { i ->
            val t = (i / 2).toDouble() / rate
            ((sin(2 * PI * 55 * t) * 0.7 + sin(2 * PI * 110 * t) * 0.3) * 32_767).toInt().toShort()
        }
        val out = run(fx(), input)
        // Flat-topped runs are what clipping sounds like; the limiter and soft knee leave none.
        var run = 0
        var longest = 0
        for (s in out) {
            run = if (abs(s.toInt()) >= 32_766) run + 1 else 0
            longest = max(longest, run)
        }
        assertTrue("clipped run of $longest samples", longest < 3)
    }

    @Test
    fun `switching effects on and off never clicks`() {
        val p = fx()
        val chunk = stereoSine(100.0, 0.5, 4_800)
        // A 100 Hz sine at half scale moves at most ~215 per sample; a click is a jump far past that.
        val natural = 2 * PI * 100 / rate * 0.5 * 32_767
        var previous: Short? = null
        var worst = 0
        for (step in 0 until 12) {
            when (step) {
                2 -> SoundFxEngine.update { it.copy(eightD = true, eightDIntensity = 1f, stereoWidth = 1.8f, bassDb = 9f) }
                5 -> SoundFxEngine.setOriginal(true)
                7 -> SoundFxEngine.setOriginal(false)
                9 -> SoundFxEngine.update { SoundFxSettings() }
            }
            val out = process(p, chunk, channels = 2)
            for (i in out.indices step 2) {
                previous?.let { worst = max(worst, abs(out[i] - it)) }
                previous = out[i]
            }
        }
        assertTrue("largest step $worst vs natural $natural", worst < natural * 3)
    }

    @Test
    fun `8D never silences an ear`() {
        SoundFxEngine.update { it.copy(eightD = true, eightDIntensity = 1f, rotationHz = 0.5f, depth = 1f) }
        val input = stereoSine(300.0, 0.4, rate * 4)
        val out = run(fx(), input)
        val window = rate / 20
        val inputRms = rms(input, 0, window, 0)
        // Skip the first half second while the effect glides in.
        var start = rate / 2
        while (start + window < out.size / 2) {
            val left = rms(out, start, window, 0)
            val right = rms(out, start, window, 1)
            assertTrue("left fell to ${left / inputRms}", left > inputRms * 0.25)
            assertTrue("right fell to ${right / inputRms}", right > inputRms * 0.25)
            start += window
        }
    }

    @Test
    fun `reverb rings out after it is switched off, then stops`() {
        SoundFxEngine.update { EffectPreset.SlowedReverb.settings(it).copy(speed = 1f) }
        val p = fx()
        process(p, stereoSine(440.0, 0.5, rate), channels = 2)
        SoundFxEngine.update { SoundFxSettings() }
        val silence = ShortArray(rate * 2 * 6)
        val tail = process(p, silence, channels = 2)
        val early = rms(tail, 0, rate / 10, 0)
        val late = rms(tail, rate * 5, rate / 10, 0)
        assertTrue("the tail should still be heard right after", early > 1.0)
        assertTrue("and gone five seconds later, not $late", late < 0.5)
    }

    @Test
    fun `an EQ change mid-song crossfades and a boost is kept under full scale`() {
        val eq = CustomEqualizerAudioProcessor()
        eq.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        eq.flush()
        val loud = stereoSine(60.0, 0.95, 4_800)
        var previous: Short? = null
        var worst = 0
        var peak = 0
        for (step in 0 until 10) {
            if (step == 3) eq.applyProfile(boost(12.0))
            if (step == 7) eq.disable()
            val out = process(eq, loud, channels = 2)
            for (i in out.indices step 2) {
                previous?.let { worst = max(worst, abs(out[i] - it)) }
                previous = out[i]
                if (step in 4..6) peak = max(peak, abs(out[i].toInt()))
            }
        }
        val natural = 2 * PI * 60 / rate * 0.95 * 32_767
        assertTrue("largest step $worst vs natural $natural", worst < natural * 3)
        assertTrue("a +12 dB boost reached $peak", peak < 32_700)
    }

    private fun boost(db: Double) = ParametricEQ(
        preamp = 0.0,
        bands = listOf(ParametricEQBand(frequency = 62.0, gain = db, q = 1.41, filterType = FilterType.PK, enabled = true)),
    )

    private fun fx(channels: Int = 2): SoundFxAudioProcessor = SoundFxAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun stereoSine(hz: Double, amplitude: Double, frames: Int) = ShortArray(frames * 2) { i ->
        (sin(2 * PI * hz * (i / 2) / rate) * amplitude * 32_767).toInt().toShort()
    }

    /** Runs [input] through in 10 ms buffers, as the player does. */
    private fun run(p: AudioProcessor, input: ShortArray): ShortArray {
        val out = ArrayList<Short>(input.size)
        val step = rate / 100 * 2
        var i = 0
        while (i < input.size) {
            val end = minOf(input.size, i + step)
            process(p, input.copyOfRange(i, end), channels = 2).forEach { out.add(it) }
            i = end
        }
        return out.toShortArray()
    }

    private fun process(p: AudioProcessor, samples: ShortArray, channels: Int): ShortArray {
        val buffer = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { buffer.putShort(it) }
        buffer.flip()
        p.queueInput(buffer)
        val out = p.output
        val result = ShortArray(out.remaining() / 2)
        for (k in result.indices) result[k] = out.getShort()
        return result
    }

    private fun rms(samples: ShortArray, startFrame: Int, frames: Int, channel: Int): Double {
        var sum = 0.0
        for (f in startFrame until startFrame + frames) {
            val v = samples[f * 2 + channel].toDouble()
            sum += v * v
        }
        return sqrt(sum / frames)
    }
}
