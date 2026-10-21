package com.shiny.music.ui.liquid.settings

import com.shiny.music.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun `bundled current release matches installed version`() {
        assertEquals(BuildConfig.VERSION_NAME, ShinyReleases.first().version)
        assertTrue(ShinyReleases.any { it.version == BuildConfig.VERSION_NAME })
    }

    @Test
    fun `version 1 2 6 includes the requested playback and home notes`() {
        val release = ShinyReleases.first { it.version == "1.2.6" }
        val notes = release.new + release.improved + release.fixed

        assertTrue(notes.any { it.startsWith("Cleaner Home:") })
        assertTrue(notes.any { it.startsWith("Improved Shuffle:") })
        assertTrue(notes.any { it.startsWith("Better Repeat Controls:") })
        assertTrue(notes.any { it.startsWith("Autoplay Improvements:") })
        assertTrue(notes.any { it.startsWith("Queue Improvements:") })
    }
}
