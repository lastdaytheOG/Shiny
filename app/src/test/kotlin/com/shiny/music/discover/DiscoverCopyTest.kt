package com.shiny.music.discover

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DiscoverCopyTest {

    private val strings = File("src/main/res/values/discover_strings.xml").readText()
    private val ui = File("src/main/kotlin/com/shiny/music/ui/liquid/discover").walk()
        .filter { it.extension == "kt" }
        .associate { it.name to it.readText() }
    private val discoverScreen = File("src/main/kotlin/com/shiny/music/ui/liquid/screens/LiquidNewScreen.kt").readText()

    @Test
    fun `source lines, timestamps and disclaimers are gone from Discover`() {
        listOf(
            "Apple Music ·", "updated", "saved %1", "Ranked by Shiny", "Not an official Apple chart",
            "Videos play in Apple Music", "public RSS", "not affiliated",
        ).forEach { assertTrue("\"$it\" is still in discover_strings.xml", it !in strings) }
        ui.forEach { (name, text) ->
            listOf("sourceLine", "AttributionFooter", "discover_source", "discover_attribution", "DateUtils")
                .forEach { assertTrue("$it is still used in $name", it !in text) }
        }
        assertTrue("AttributionFooter" !in discoverScreen)
    }

    @Test
    fun `chart cards offer no Apple Music action`() {
        assertTrue("Watch in Apple Music" !in strings && "Open in Apple Music" !in strings)
        ui.forEach { (name, text) ->
            listOf("OpenInNew", "ACTION_VIEW", "startActivity", "entry.url", "openExternal", "VideoMenu")
                .forEach { assertTrue("$it is still in $name", it !in text) }
        }
    }

    @Test
    fun `the region is chosen in Settings only`() {
        ui.forEach { (name, text) ->
            listOf("RegionButton", "DiscoverRegionsScreen", "setRegion").forEach { assertTrue("$it is still in $name", it !in text) }
        }
        assertTrue("RegionButton" !in discoverScreen && "titleAccessory" !in discoverScreen)
        assertTrue("discover_region" !in strings)
    }
}
