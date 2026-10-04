package com.shiny.music.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepFadeTest {
    @Test
    fun `the level runs from full to silent and never rises`() {
        assertEquals(1f, SleepFade.level(0), 0f)
        assertEquals(0f, SleepFade.level(SleepFade.DURATION_MS), 0f)
        assertEquals(0f, SleepFade.level(SleepFade.DURATION_MS * 3), 0f)
        assertEquals(1f, SleepFade.level(-10), 0f)
        val levels = (0..SleepFade.DURATION_MS step SleepFade.STEP_MS).map { SleepFade.level(it) }
        levels.zipWithNext().forEach { (a, b) -> assertTrue(b < a) }
        assertTrue(levels.all { it in 0f..1f })
        // Half-way through, most of the volume is already gone.
        assertEquals(0.25f, SleepFade.level(SleepFade.DURATION_MS / 2), 1e-6f)
    }

    @Test
    fun `a zero duration is silent at once`() {
        assertEquals(0f, SleepFade.level(0, durationMs = 0), 0f)
    }

    @Test
    fun `the fade starts at the current volume, ends at zero and takes the whole duration`() = runBlocking {
        val volumes = mutableListOf<Float>()
        var slept = 0L
        SleepFade.run(from = 0.6f, setVolume = { volumes += it }, pause = { slept += it })
        assertEquals(0.6f, volumes.first(), 0f)
        assertEquals(0f, volumes.last(), 0f)
        assertEquals(SleepFade.DURATION_MS, slept)
        volumes.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
        assertTrue(volumes.all { it in 0f..0.6f })
    }

    @Test
    fun `cancelling stops the fade without going on to silence`() {
        val volumes = mutableListOf<Float>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                SleepFade.run(from = 1f, setVolume = { volumes += it }, pause = {
                    if (volumes.size == 10) throw CancellationException("timer cleared")
                })
            }
        }
        assertEquals(10, volumes.size)
        assertTrue(volumes.last() > 0f)
    }
}
