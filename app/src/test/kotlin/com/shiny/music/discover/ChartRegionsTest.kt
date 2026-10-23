package com.shiny.music.discover

import com.shiny.music.home.HomeCharts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ChartRegionsTest {

    @Test
    fun `the commonly requested storefronts are all supported`() {
        listOf("in", "us", "gb", "ca", "au", "jp", "de", "fr").forEach {
            assertEquals(it, ChartRegions.normalize(it.uppercase()))
        }
    }

    @Test
    fun `unknown, global and malformed codes are rejected`() {
        listOf("zz", "xx", "bd", "", " ", "usa", null).forEach { assertNull(ChartRegions.normalize(it)) }
        assertFalse("no worldwide storefront is offered", "zz" in ChartRegions.STOREFRONTS)
    }

    @Test
    fun `the country chosen in Settings drives Discover`() {
        assertEquals(ChartRegion("jp", ChartRegion.Source.Chosen), ChartRegions.resolve("JP", "in"))
        assertEquals(ChartRegion("pk", ChartRegion.Source.Chosen), ChartRegions.resolve("PK", "in"))
    }

    @Test
    fun `automatic, Global and Off all follow the listener's location`() {
        for (setting in listOf(HomeCharts.AUTO, HomeCharts.GLOBAL, HomeCharts.OFF, null)) {
            assertEquals(ChartRegion("in", ChartRegion.Source.Detected), ChartRegions.resolve(setting, "IN"))
        }
    }

    @Test
    fun `a country Apple does not chart falls back to the location rather than to a guess`() {
        assertEquals(ChartRegion("gb", ChartRegion.Source.Detected), ChartRegions.resolve("UY", "gb"))
    }

    @Test
    fun `no usable location falls back to the US and says so`() {
        assertEquals(ChartRegion("us", ChartRegion.Source.Fallback), ChartRegions.resolve(HomeCharts.AUTO, null))
        assertEquals(ChartRegion("us", ChartRegion.Source.Fallback), ChartRegions.resolve(HomeCharts.AUTO, "bd"))
    }

    @Test
    fun `detection takes the network first, then the SIM, then the locale`() {
        assertEquals("in", ChartRegions.detect(listOf("IN", "us", "GB")))
        assertEquals("us", ChartRegions.detect(listOf("", "US", "GB")))
        assertEquals("gb", ChartRegions.detect(listOf(null, "bd", "GB")))
        assertNull(ChartRegions.detect(listOf(null, "", "bd")))
    }

    @Test
    fun `every country YouTube or Apple charts can be chosen in Settings`() {
        val choices = HomeCharts.choosableCountries()
        assertTrue(HomeCharts.COUNTRIES.all { it in choices })
        assertTrue(ChartRegions.STOREFRONTS.all { it.uppercase(Locale.ROOT) in choices })
        assertEquals(choices.size, choices.distinct().size)
        assertTrue(ChartRegions.has("PK") && "PK" !in HomeCharts.COUNTRIES)
    }

    @Test
    fun `names come from the locale`() {
        assertEquals("India", ChartRegions.name("in", Locale.ENGLISH))
    }
}
