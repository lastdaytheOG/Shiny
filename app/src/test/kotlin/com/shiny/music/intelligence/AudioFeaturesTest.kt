package com.shiny.music.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class AudioFeaturesTest {
    private val rate = 11_025

    private fun clicks(bpm: Double, seconds: Int): FloatArray {
        val samples = FloatArray(rate * seconds)
        val beat = (60.0 / bpm * rate).toInt()
        var i = 0
        while (i < samples.size) {
            for (j in 0 until minOf(400, samples.size - i)) {
                samples[i + j] = (exp(-j / 80.0) * sin(2 * PI * 880.0 * j / rate)).toFloat()
            }
            i += beat
        }
        return samples
    }

    private fun tone(frequency: Double, seconds: Int, amplitude: Float = 0.5f) =
        FloatArray(rate * seconds) { (amplitude * sin(2 * PI * frequency * it / rate)).toFloat() }

    private fun noise(seconds: Int, amplitude: Float = 0.5f): FloatArray {
        val random = Random(1)
        return FloatArray(rate * seconds) { (random.nextFloat() * 2 - 1) * amplitude }
    }

    @Test
    fun `tempo of a click track is found within two beats`() {
        listOf(80.0, 100.0, 120.0, 140.0, 170.0).forEach { bpm ->
            val measured = AudioFeatures.analyse(clicks(bpm, 30), rate)!!
            val closest = listOf(measured.bpm / 2.0, measured.bpm.toDouble(), measured.bpm * 2.0).minBy { kotlin.math.abs(it - bpm) }
            assertEquals("at $bpm got ${measured.bpm}", bpm, closest, 2.0)
            assertTrue(measured.tempoConfidence > 0.2f)
        }
    }

    @Test
    fun `a tempo with no real peak is not trusted`() {
        val swell = DoubleArray(1_200) { sin(2 * PI * it / 900.0) + 1.0 }
        val (bpm, confidence) = AudioFeatures.tempo(swell, 43.0)
        assertEquals(0.0, bpm, 0.0)
        assertEquals(0.0, confidence, 0.0)
    }

    @Test
    fun `noise is brighter than a low tone`() {
        val low = AudioFeatures.analyse(tone(110.0, 15), rate)!!
        val bright = AudioFeatures.analyse(noise(15), rate)!!
        assertTrue("${low.brightness} vs ${bright.brightness}", bright.brightness > low.brightness * 3)
    }

    @Test
    fun `louder audio measures as more energetic`() {
        val quiet = AudioFeatures.analyse(tone(440.0, 15, amplitude = 0.03f), rate)!!
        val loud = AudioFeatures.analyse(tone(440.0, 15, amplitude = 0.7f), rate)!!
        assertTrue(loud.energy > quiet.energy)
    }

    @Test
    fun `clicks are more dynamic than a steady tone`() {
        val steady = AudioFeatures.analyse(tone(440.0, 15), rate)!!
        val punchy = AudioFeatures.analyse(clicks(120.0, 15), rate)!!
        assertTrue(punchy.dynamics > steady.dynamics)
    }

    @Test
    fun `a C major chord reads as C`() {
        val chord = FloatArray(rate * 20) {
            val t = it.toDouble() / rate
            (0.2 * sin(2 * PI * 261.63 * t) + 0.2 * sin(2 * PI * 329.63 * t) + 0.2 * sin(2 * PI * 392.0 * t) + 0.1 * sin(2 * PI * 523.25 * t)).toFloat()
        }
        val key = AudioFeatures.key(chord, rate)
        assertNotNull(key)
        assertEquals(0, key!!.first)
    }

    @Test
    fun `silence and very short audio are not measured`() {
        assertNull(AudioFeatures.analyse(FloatArray(rate * 20), rate))
        assertNull(AudioFeatures.analyse(tone(440.0, 3), rate))
    }
}
