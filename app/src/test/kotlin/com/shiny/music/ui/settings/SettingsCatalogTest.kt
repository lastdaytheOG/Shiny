package com.shiny.music.ui.settings

import com.shiny.music.ui.liquid.settings.SettingsVisibility
import com.shiny.music.ui.liquid.settings.search
import com.shiny.music.ui.liquid.settings.settingsCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hidden settings must not come back through search: a result would open a page without the row. */
class SettingsCatalogTest {
    private val catalog = settingsCatalog()

    @Test
    fun `hidden lyrics options are not searchable`() {
        if (SettingsVisibility.LYRICS) return
        assertEquals(emptyList<Any>(), catalog.search("lyrics"))
        assertTrue(catalog.none { it.route == "settings/lyrics" || it.route == "settings/content/romanization" })
    }

    @Test
    fun `hidden playback logs are not searchable`() {
        if (SettingsVisibility.PLAYBACK_LOGS) return
        assertTrue(catalog.none { it.title == "Playback logs" })
    }

    @Test
    fun `the rest of Content is still searchable`() {
        assertTrue(catalog.search("proxy").any { it.route == "settings/content" })
    }
}
