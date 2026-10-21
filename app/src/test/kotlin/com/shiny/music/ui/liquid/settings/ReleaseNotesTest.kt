package com.shiny.music.ui.liquid.settings

import com.shiny.music.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun `bundled current release matches installed version`() {
        assertEquals(BuildConfig.VERSION_NAME, ShinyReleases.first().version)
        assertTrue(ShinyReleases.any { it.version == BuildConfig.VERSION_NAME })
    }

    @Test
    fun `release history is newest first and keeps 1 2 5 available`() {
        assertEquals(listOf("1.2.6", "1.2.5"), ShinyReleases.map { it.version })
        assertEquals("1.2.6", ShinyReleases.first().version)
        assertEquals("1.2.5", ShinyReleases[1].version)
        val previous = ShinyReleases.first { it.version == "1.2.5" }
        assertNotNull(previous.summary)
        assertEquals(3, previous.featured.size)
        assertEquals(6, previous.new.size)
        assertEquals(4, previous.improved.size)
        assertEquals(4, previous.fixed.size)
    }

    @Test
    fun `obsolete 1 2 2 is absent from release history`() {
        assertFalse(ShinyReleases.any { it.version == "1.2.2" })
    }

    @Test
    fun `future multi digit versions remain ahead of older releases`() {
        val releases = listOf(
            Release("1.2.9", null, null),
            Release("1.2.10", null, null),
            Release("1.2.6", null, null),
        )

        assertEquals(listOf("1.2.10", "1.2.9", "1.2.6"), sortReleaseHistory(releases).map { it.version })
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
