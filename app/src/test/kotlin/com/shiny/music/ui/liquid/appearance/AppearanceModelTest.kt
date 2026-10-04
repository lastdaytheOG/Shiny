package com.shiny.music.ui.liquid.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModelTest {

    @Test
    fun `defaults are today's Shiny and read back as Balanced`() {
        val defaults = ShinyAppearance.Baseline
        assertEquals(Atmosphere.Balanced, defaults.atmosphere)
        assertEquals(ArtworkGlow.Off, defaults.glow)
        assertEquals(GlassFinish.Adaptive, defaults.glassFinish)
        assertEquals(PageTransitions.Slide, defaults.transitions)
        assertEquals(false, defaults.amoled)

        // An untouched install: all three motion switches default to true.
        val motion = artworkMotionOf(backdropDrift = true, breathe = true, living = true)
        val mix = ExperienceMix(defaults.atmosphere, defaults.glow, motion, defaults.transitions)
        assertEquals(Experience.Balanced, experienceOf(mix))
    }

    @Test
    fun `every named experience reads back as itself`() {
        for (experience in Experience.entries) {
            val mix = experience.mix ?: continue
            assertEquals(experience, experienceOf(mix))
            // What the preset writes is what the scale reads back.
            val s = mix.motion.switches
            assertEquals(mix.motion, artworkMotionOf(s.backdropDrift, s.breathe, s.living))
        }
        assertNull(Experience.Custom.mix)
    }

    @Test
    fun `any other mix is Custom`() {
        val mix = ExperienceMix(Atmosphere.Balanced, ArtworkGlow.Subtle, ArtworkMotion.Living, PageTransitions.Slide)
        assertEquals(Experience.Custom, experienceOf(mix))
    }

    @Test
    fun `motion scale keeps the old per-switch mapping`() {
        assertEquals(ArtworkMotion.Living, artworkMotionOf(backdropDrift = false, breathe = false, living = true))
        assertEquals(ArtworkMotion.Subtle, artworkMotionOf(backdropDrift = true, breathe = false, living = false))
        assertEquals(ArtworkMotion.Subtle, artworkMotionOf(backdropDrift = false, breathe = true, living = false))
        assertEquals(ArtworkMotion.Off, artworkMotionOf(backdropDrift = false, breathe = false, living = false))
    }

    @Test
    fun `material maps onto the glass switch and the finish`() {
        for (finish in GlassFinish.entries) {
            assertEquals(SurfaceMaterial.Solid, surfaceMaterialOf(glassOn = false, finish = finish))
        }
        for (material in SurfaceMaterial.entries) {
            val finish = material.finish
            if (material == SurfaceMaterial.Solid) {
                assertNull(finish)
            } else {
                assertEquals(material, surfaceMaterialOf(glassOn = true, finish = finish!!))
            }
        }
        // An existing user with glass on and no stored finish sees today's recipe.
        assertEquals(SurfaceMaterial.Adaptive, surfaceMaterialOf(true, GlassFinish.Adaptive))
    }

    @Test
    fun `accent mode reads the existing preferences`() {
        assertEquals(AccentMode.Shiny, accentModeOf(false, AppearanceColor.ShinyTint))
        assertEquals(AccentMode.Mono, accentModeOf(false, AppearanceColor.MonochromeTint))
        assertEquals(AccentMode.Custom, accentModeOf(false, 0xFF0A84FF.toInt()))
        // An existing user with dynamic theme on keeps it, whatever tint is underneath.
        assertEquals(AccentMode.Artwork, accentModeOf(true, 0xFF0A84FF.toInt()))
        assertEquals(AccentMode.Artwork, accentModeOf(true, AppearanceColor.ShinyTint))
    }

    @Test
    fun `balanced atmosphere leaves the scrim and field untouched`() {
        for (scrim in listOf(0.26f, 0.42f)) {
            assertEquals(scrim, atmosphereScrim(Atmosphere.Balanced, scrim))
            assertTrue(atmosphereScrim(Atmosphere.Soft, scrim) > scrim)
            val immersive = atmosphereScrim(Atmosphere.Immersive, scrim)
            assertTrue(immersive < scrim)
            assertTrue(immersive >= 0.2f)
        }
        assertNull(atmosphereSaturation(Atmosphere.Balanced))
        assertNull(atmosphereSaturation(Atmosphere.Off))
    }

    @Test
    fun `glow dims on pause but never goes out`() {
        assertEquals(1f, glowPauseFactor(1f, 0.82f), 1e-6f)
        assertEquals(0.6f, glowPauseFactor(0.82f, 0.82f), 1e-6f)
        assertEquals(0.6f, glowPauseFactor(0.5f, 0.82f), 1e-6f)
        assertEquals(0f, ArtworkGlow.Off.strength, 0f)
        assertTrue(ArtworkGlow.Subtle.strength < ArtworkGlow.Medium.strength)
        assertTrue(ArtworkGlow.Medium.strength <= 0.6f)
    }

    @Test
    fun `density shrinks as asked and grows only while the screen stays big enough`() {
        // Shrinking is never limited, even on a small screen.
        assertEquals(0.55f, effectiveDensityScale(0.55f, 320, 640), 0f)
        assertEquals(1f, effectiveDensityScale(1f, 411, 914), 0f)
        // The test emulator (411 x 914dp): Large fits (342 x 762dp).
        assertEquals(1.2f, effectiveDensityScale(1.2f, 411, 914), 0f)
        // A 360dp-wide tall phone: capped so the result stays 320dp wide.
        val byWidth = effectiveDensityScale(1.2f, 360, 900)
        assertEquals(1.125f, byWidth, 1e-4f)
        assertTrue(360 / byWidth >= MinScaledWidthDp - 0.01f)
        // A 360 x 800dp phone: height is the limit (800 / 720).
        val byHeight = effectiveDensityScale(1.2f, 360, 800)
        assertEquals(800f / 720f, byHeight, 1e-4f)
        assertTrue(800 / byHeight >= MinScaledLongSideDp - 0.01f)
        // Comfortable still fits there.
        assertEquals(1.1f, effectiveDensityScale(1.1f, 360, 800), 0f)
        // A 360 x 640dp (16:9) phone is already too short: never enlarged.
        assertEquals(1f, effectiveDensityScale(1.2f, 360, 640), 0f)
        assertEquals(1f, effectiveDensityScale(1.2f, 300, 900), 0f)
        // Unknown size: leave the request alone rather than guess.
        assertEquals(1.1f, effectiveDensityScale(1.1f, 0, 0), 0f)
    }

    // ---- artwork presentation ----------------------------------------------------------

    @Test
    fun `the default presentation is the square stage Shiny already had`() {
        assertEquals(ArtworkPresentation.Card, ShinyAppearance.Baseline.artwork)
        assertEquals(ArtworkPresentation.Card, ArtworkPresentation.entries.first())
    }

    @Test
    fun `a full-screen cover is scrimmed more heavily than the blurred field ever was`() {
        Atmosphere.entries.forEach { atmosphere ->
            val field = atmosphereScrim(atmosphere, 0.26f)
            val full = fullScreenScrim(atmosphere, 0.26f)
            assertTrue("$atmosphere: $full should exceed $field", full > field)
        }
    }

    @Test
    fun `a bright cover is scrimmed more than a dark one at every atmosphere`() {
        Atmosphere.entries.forEach { atmosphere ->
            val dark = fullScreenScrim(atmosphere, 0.26f)
            val bright = fullScreenScrim(atmosphere, 0.42f)
            assertTrue("$atmosphere: $bright should exceed $dark", bright > dark)
        }
    }

    @Test
    fun `atmosphere still means the same thing full screen`() {
        // Immersive lets the most of the cover through; Off the least.
        val byAtmosphere = listOf(Atmosphere.Immersive, Atmosphere.Balanced, Atmosphere.Soft, Atmosphere.Off)
            .map { fullScreenScrim(it, 0.26f) }
        assertEquals(byAtmosphere.sorted(), byAtmosphere)
        val tops = listOf(Atmosphere.Immersive, Atmosphere.Balanced, Atmosphere.Soft, Atmosphere.Off)
            .map { fullScreenTopScrim(it) }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun `the scrim never reaches opaque, so the cover is always still visible`() {
        Atmosphere.entries.forEach { atmosphere ->
            listOf(0f, 0.26f, 0.42f, 1f).forEach { balanced ->
                val scrim = fullScreenScrim(atmosphere, balanced)
                assertTrue("$atmosphere/$balanced = $scrim", scrim in 0.5f..0.92f)
            }
        }
    }
}
