package com.shiny.music.ui.liquid.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroToneTest {

    @Test
    fun `a cover with no colour gets the neutral tone, not a dull grey`() {
        assertEquals(NeutralHeroTone, heroTone(Color(0xFF6B6B6B), Color(0xFF9A9A9A)))
    }

    @Test
    fun `a near black cover with no vivid colour gets the neutral tone`() {
        assertEquals(NeutralHeroTone, heroTone(Color(0xFF0E0505), Color(0xFF0E0505)))
    }

    @Test
    fun `a colourful cover keeps its hue and is held dark enough for white type`() {
        val tone = heroTone(Color(0xFFC0392B), Color(0xFFE74C3C))
        assertNotEquals(NeutralHeroTone, tone)
        assertTrue(tone.band.luminance() <= 0.09f)
        assertTrue(tone.band.red > tone.band.green && tone.band.red > tone.band.blue)
        assertTrue(tone.field.red > tone.field.green && tone.field.red > tone.field.blue)
    }

    @Test
    fun `a pure colour is muted, so it never reads as an alarm`() {
        val tone = heroTone(Color(0xFF8B0000), Color(0xFFFF0000))
        assertTrue(tone.band.green > 0.02f && tone.band.blue > 0.02f)
    }

    @Test
    fun `every band keeps the artists line above 4_5 to 1`() {
        listOf(
            Color(0xFFC0392B) to Color(0xFFE74C3C),
            Color(0xFF1E8449) to Color(0xFF2ECC71),
            Color(0xFF2471A3) to Color(0xFF3498DB),
            Color(0xFFF1C40F) to Color(0xFFF7DC6F),
            Color(0xFF8E44AD) to Color(0xFFBB8FCE),
        ).forEach { (deep, vivid) ->
            val band = heroTone(deep, vivid).band
            val alpha = 0.82f
            val line = Color(
                red = alpha + band.red * (1f - alpha),
                green = alpha + band.green * (1f - alpha),
                blue = alpha + band.blue * (1f - alpha),
            )
            val contrast = (line.luminance() + 0.05f) / (band.luminance() + 0.05f)
            assertTrue("contrast $contrast on $band", contrast >= 4.5f)
        }
    }
}
