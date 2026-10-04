package com.shiny.music.ui.liquid.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AppearanceColorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private val samples = listOf(
        0xFFFFFF00.toInt(), // pure yellow — far too light
        0xFF00FF00.toInt(), // neon green
        0xFFFFFFFF.toInt(), // white
        0xFF000000.toInt(), // black
        0xFF101030.toInt(), // near-black navy
        0xFF3A0000.toInt(), // dried blood
        0xFFFF375F.toInt(), // Shiny Rose, already fine
        0xFF0A84FF.toInt(), // system blue
        0xFF7F7F7F.toInt(), // mid grey
        0xFFFFC0CB.toInt(), // pastel pink
    )

    @Test
    fun `sanitized accents land inside the readable band`() {
        for (argb in samples) {
            val out = AppearanceColor.sanitizeAccent(argb)
            val lum = AppearanceColor.luminance(out)
            assertTrue("${hex(argb)} -> ${hex(out)} lum=$lum", lum >= AppearanceColor.AccentMinLuminance - 1e-3)
            assertTrue("${hex(argb)} -> ${hex(out)} lum=$lum", lum <= AppearanceColor.AccentMaxLuminance + 1e-3)
            // Readable as text on black, and white glyphs readable on it.
            assertTrue(AppearanceColor.contrast(out, black) >= 3.0)
            // 3:1 exactly at the top of the band, so allow for rounding.
            assertTrue(AppearanceColor.contrast(out, white) >= 2.99)
            assertEquals(0xFF, (out ushr 24))
        }
    }

    @Test
    fun `an accent already in the band keeps its hue and lightness`() {
        val rose = 0xFFFF375F.toInt()
        val out = AppearanceColor.sanitizeAccent(rose)
        val (h0, _, l0) = AppearanceColor.toHsl(rose)
        val (h1, _, l1) = AppearanceColor.toHsl(out)
        assertTrue(abs(h0 - h1) < 2.0)
        assertTrue(abs(l0 - l1) < 0.02)
    }

    @Test
    fun `sanitizing keeps the hue of a colour it has to move`() {
        val yellow = 0xFFFFD60A.toInt()
        val (h0, _, _) = AppearanceColor.toHsl(yellow)
        val (h1, _, _) = AppearanceColor.toHsl(AppearanceColor.sanitizeAccent(yellow))
        assertTrue("hue drifted from $h0 to $h1", abs(h0 - h1) < 3.0)
    }

    @Test
    fun `monochrome resolves to graphite that works on both grounds`() {
        assertTrue(AppearanceColor.contrast(AppearanceColor.MonochromeDark, black) >= 4.5)
        assertTrue(AppearanceColor.contrast(AppearanceColor.MonochromeDark, white) >= 3.0)
        assertTrue(AppearanceColor.contrast(AppearanceColor.MonochromeLight, white) >= 4.5)
        // The sentinels never collide with each other or with the real colours.
        val distinct = setOf(
            AppearanceColor.ShinyTint,
            AppearanceColor.MonochromeTint,
            AppearanceColor.MonochromeDark,
            AppearanceColor.MonochromeLight,
        )
        assertEquals(4, distinct.size)
    }

    @Test
    fun `glow tone is mid-light, and grey covers get a soft neutral`() {
        val grey = AppearanceColor.glowTone(0xFF404040.toInt())
        val (_, gs, gl) = AppearanceColor.toHsl(grey)
        assertTrue(gs < 0.01)
        assertTrue(gl > 0.7)
        for (argb in listOf(0xFF003300.toInt(), 0xFFFF00FF.toInt(), 0xFFFFFFA0.toInt())) {
            val (_, s, l) = AppearanceColor.toHsl(AppearanceColor.glowTone(argb))
            assertTrue(l in 0.49..0.67)
            assertTrue(s <= 0.81)
        }
    }

    @Test
    fun `hsl round trips`() {
        for (argb in samples) {
            val (h, s, l) = AppearanceColor.toHsl(argb)
            val back = AppearanceColor.fromHsl(h, s, l)
            for (shift in listOf(0, 8, 16)) {
                val a = (argb shr shift) and 0xFF
                val b = (back shr shift) and 0xFF
                assertTrue("${hex(argb)} vs ${hex(back)}", abs(a - b) <= 1)
            }
        }
    }

    private fun hex(argb: Int) = "#%08X".format(argb)
}
