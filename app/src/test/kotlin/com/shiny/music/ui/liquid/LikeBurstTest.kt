package com.shiny.music.ui.liquid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class LikeBurstTest {
    @Test
    fun `only a change from not liked to liked animates`() {
        assertTrue(LikeBurst.startsOn(previous = false, liked = true))
        assertFalse("first appearance, liked", LikeBurst.startsOn(previous = null, liked = true))
        assertFalse("first appearance, not liked", LikeBurst.startsOn(previous = null, liked = false))
        assertFalse("unlike", LikeBurst.startsOn(previous = true, liked = false))
        assertFalse("still liked", LikeBurst.startsOn(previous = true, liked = true))
        assertFalse("still not liked", LikeBurst.startsOn(previous = false, liked = false))
    }

    @Test
    fun `the heart starts and ends at its own size and swells in between`() {
        assertEquals(1f, LikeBurst.scale(0f), 0f)
        assertEquals(1f, LikeBurst.scale(1f), 0f)
        val samples = (0..100).map { LikeBurst.scale(it / 100f) }
        assertEquals(1.3f, samples.max(), 0.01f)
        assertTrue("never collapses", samples.min() > 0.9f)
        // No jump between neighbouring frames, including where the swell hands over to the settle.
        samples.zipWithNext().forEach { (a, b) -> assertTrue(kotlin.math.abs(a - b) < 0.06f) }
    }

    @Test
    fun `the dots only move outwards, fade out and shrink`() {
        val steps = (0..20).map { it / 20f }
        steps.map(LikeBurst::reach).zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
        assertEquals(1f, LikeBurst.reach(1f), 1e-6f)
        assertEquals(1f, LikeBurst.dotAlpha(0f), 0f)
        assertEquals(0f, LikeBurst.dotAlpha(1f), 0f)
        steps.map(LikeBurst::dotAlpha).zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
        steps.map(LikeBurst::dotRadius).zipWithNext().forEach { (a, b) -> assertTrue(b < a) }
    }

    @Test
    fun `the dots are spread evenly, starting straight up`() {
        assertEquals(-PI.toFloat() / 2, LikeBurst.angle(0), 1e-6f)
        val gap = (2 * PI / LikeBurst.DOTS).toFloat()
        (1 until LikeBurst.DOTS).forEach { assertEquals(gap, LikeBurst.angle(it) - LikeBurst.angle(it - 1), 1e-5f) }
    }
}
