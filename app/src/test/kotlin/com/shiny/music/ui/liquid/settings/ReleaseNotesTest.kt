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
}
