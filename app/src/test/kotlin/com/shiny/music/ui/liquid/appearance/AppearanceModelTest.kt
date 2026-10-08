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

        // An untouched install: the backdrop drifts and the cover settles on pause, and the
        // cover itself is left as it is. Motion Shiny makes up is off until it is asked for.
        assertEquals(false, com.shiny.music.ui.liquid.LiquidPrefs.LivingArtworkDefault)
        val motion = artworkMotionOf(backdropDrift = true, breathe = true, living = com.shiny.music.ui.liquid.LiquidPrefs.LivingArtworkDefault)
        assertEquals(ArtworkMotion.Subtle, motion)
        val mix = ExperienceMix(defaults.atmosphere, defaults.glow, motion, defaults.transitions)
        assertEquals(Experience.Balanced, experienceOf(mix))
    }

    @Test
    fun `the living cover is something chosen, never something a default or the balanced mix turns on`() {
        assertEquals(false, Experience.Balanced.mix!!.motion.switches.living)
        assertEquals(false, Experience.Minimal.mix!!.motion.switches.living)
        // It is still there for whoever wants it: on the motion scale, and in the Immersive mix.
        assertEquals(true, ArtworkMotion.Living.switches.living)
        assertEquals(ArtworkMotion.Living, Experience.Immersive.mix!!.motion)
        // And a choice already made is read back as made.
        assertEquals(ArtworkMotion.Living, artworkMotionOf(backdropDrift = true, breathe = true, living = true))
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

    @Test
    fun `the default presentation is the square stage Shiny already had`() {
        assertEquals(ArtworkPresentation.Card, ShinyAppearance.Baseline.artwork)
        assertEquals(ArtworkPresentation.Card, ArtworkPresentation.entries.first())
    }

    @Test
    fun `the shade at the head of a poster follows the atmosphere`() {
        // Immersive lets the most of the picture through; Off the least.
        val tops = listOf(Atmosphere.Immersive, Atmosphere.Balanced, Atmosphere.Soft, Atmosphere.Off)
            .map { posterTopScrim(it) }
        assertEquals(tops.sorted(), tops)
        assertTrue(tops.all { it in 0.1f..0.5f })
    }

    @Test
    fun `there are two presentations, and a choice that no longer exists falls back to the card`() {
        assertEquals(listOf(ArtworkPresentation.Card, ArtworkPresentation.Poster), ArtworkPresentation.entries.toList())
        assertEquals(ArtworkPresentation.Card, runCatching { ArtworkPresentation.valueOf("FullScreen") }.getOrDefault(ArtworkPresentation.Card))
    }

    @Test
    fun `on a tall screen the poster dissolves over the last part of the cover itself`() {
        // 1080 wide, title far below the cover: the dissolve ends on the cover's bottom edge.
        val melt = posterMelt(coverSide = 1080f, titleTop = 1500f, gap = 26f)
        assertEquals(1080f, melt.end, 0f)
        assertEquals(1080f * (1f - PosterMeltFraction), melt.start, 0.5f)
    }

    @Test
    fun `on a short screen the dissolve is over before the title begins`() {
        // The title sits over the cover: 1080 wide, title at 900.
        val melt = posterMelt(coverSide = 1080f, titleTop = 900f, gap = 26f)
        assertEquals(874f, melt.end, 0f)
        assertTrue(melt.start < melt.end)
        // Never more than the lower half, and a third of the picture always stays sharp.
        val squat = posterMelt(coverSide = 1080f, titleTop = 200f, gap = 26f)
        assertEquals(540f, squat.end, 0f)
        assertTrue(squat.start >= 1080f * 0.34f - 0.5f)
        assertTrue(squat.start < squat.end)
    }

    @Test
    fun `the cover is whole above the dissolve and gone by its end`() {
        val melt = PosterMelt(700f, 1000f)
        assertEquals(0f, posterMeltAt(0f, melt), 0f)
        assertEquals(0f, posterMeltAt(700f, melt), 0f)
        assertEquals(0.5f, posterMeltAt(850f, melt), 1e-4f)
        assertEquals(1f, posterMeltAt(1000f, melt), 0f)
        assertEquals(1f, posterMeltAt(2400f, melt), 0f)
        // It only ever thickens on the way down.
        val steps = (700..1000 step 10).map { posterMeltAt(it.toFloat(), melt) }
        assertEquals(steps.sorted(), steps)
    }

    @Test
    fun `the ground under the title is dark enough for white type on any cover`() {
        Atmosphere.entries.forEach { atmosphere ->
            listOf(0.02f, 0.2f, 0.5f, 1f).forEach { brightness ->
                val scrim = posterTitleScrim(atmosphere, brightness)
                // What is left of the ground's luminance after the scrim (display gamma 2.2).
                val shown = brightness * Math.pow((1f - scrim).toDouble(), 2.2).toFloat()
                // 4.5:1 against white needs a ground at or under 0.183.
                assertTrue("$atmosphere/$brightness shows $shown", shown <= 0.183f)
                assertTrue(scrim <= 0.9f)
            }
        }
    }

    @Test
    fun `a dark cover is barely darkened and a pale one as much as it takes`() {
        Atmosphere.entries.forEach { atmosphere ->
            val dark = posterTitleScrim(atmosphere, 0.03f)
            val pale = posterTitleScrim(atmosphere, 0.9f)
            assertTrue("$atmosphere", pale > dark)
        }
        // Immersive lets the most of the colour through, Off the least.
        val byAtmosphere = listOf(Atmosphere.Immersive, Atmosphere.Balanced, Atmosphere.Soft, Atmosphere.Off)
            .map { posterTitleScrim(it, 0.03f) }
        assertEquals(byAtmosphere.sorted(), byAtmosphere)
    }

    @Test
    fun `the poster's shade starts below the sharp cover and only deepens`() {
        val title = 0.4f
        val foot = posterFootScrim(title)
        assertTrue(foot > title)
        fun at(y: Float) = posterScrimAt(y, meltStart = 700f, titleTop = 1500f, bottom = 2400f, titleScrim = title, footScrim = foot)
        assertEquals(0f, at(0f), 0f)
        assertEquals(0f, at(700f), 0f)
        assertEquals(title, at(1500f), 1e-4f)
        assertEquals(foot, at(2400f), 1e-4f)
        val down = (0..2400 step 50).map { at(it.toFloat()) }
        assertEquals(down.sorted(), down)
    }
}
