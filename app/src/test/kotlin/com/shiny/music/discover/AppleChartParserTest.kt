package com.shiny.music.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleChartParserTest {

    private val songs = """
        {"feed":{"title":"Top Songs","country":"in","updated":"Fri, 23 Oct 2026 01:47:15 +0000","results":[
          {"artistName":"Karan Aujla & MXRCI","id":"6814002647","name":"Ashke","releaseDate":"2026-09-25","kind":"songs",
           "artistId":"914892130","artistUrl":"https://music.apple.com/in/artist/karan-aujla/914892130",
           "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/f7/6d/78/f76d78c5/5064089826918_cover.jpg/100x100bb.jpg",
           "genres":[{"genreId":"18","name":"Hip-Hop/Rap"},{"genreId":"34","name":"Music"}],
           "url":"https://music.apple.com/in/album/ashke/6814002539?i=6814002647"},
          {"artistName":"Anirudh Ravichander","id":"6816001788","name":"One Sun One Moon (From \"Jailer 2\")",
           "contentAdvisoryRating":"Explict","url":"https://music.apple.com/in/album/x/1?i=6816001788"}
        ]}}
    """.trimIndent()

    @Test
    fun `maps every field the songs feed carries, in source order`() {
        val chart = AppleChartParser.parse(songs, "in", ChartKind.Songs, fetchedAt = 42)
        assertEquals("Top Songs", chart.title)
        assertEquals(42, chart.fetchedAt)
        assertEquals(2, chart.entries.size)
        val first = chart.entries[0]
        assertEquals(1, first.position)
        assertEquals("6814002647", first.id)
        assertEquals("Ashke", first.title)
        assertEquals("Karan Aujla & MXRCI", first.artist)
        assertEquals("914892130", first.artistId)
        assertEquals("https://music.apple.com/in/artist/karan-aujla/914892130", first.artistUrl)
        assertTrue(first.artwork!!.endsWith("100x100bb.jpg"))
        assertEquals("2026-09-25", first.releaseDate)
        assertEquals(listOf("Hip-Hop/Rap"), first.genres)
        assertFalse(first.explicit)
        assertEquals(2, chart.entries[1].position)
        assertTrue("Apple's own misspelling still marks explicit", chart.entries[1].explicit)
        assertNull(chart.entries[1].artwork)
    }

    @Test
    fun `albums and videos parse with the same shape`() {
        val albums = """{"feed":{"results":[{"artistName":"A","id":"1","name":"Album One","kind":"albums","url":"https://music.apple.com/x"}]}}"""
        val videos = """{"feed":{"results":[{"artistName":"B","id":"9","name":"Clip","kind":"music-videos",
            "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Video221/v4/a/b.jpg/100x56mv.jpg"}]}}"""
        assertEquals("Album One", AppleChartParser.parse(albums, "us", ChartKind.Albums, 0).entries.single().title)
        val clip = AppleChartParser.parse(videos, "us", ChartKind.Videos, 0).entries.single()
        assertEquals("9", clip.id)
        assertNull(clip.url)
    }

    @Test
    fun `skipped entries keep the ranks of the ones around them`() {
        val body = """{"feed":{"results":[
            {"artistName":"A","id":"1","name":"One"},
            {"artistName":"","id":"2","name":"No artist"},
            "not an object",
            {"id":"4","name":"No artist field"},
            {"artistName":"E","id":"5","name":"Five","artworkUrl100":"javascript:alert(1)"},
            {"artistName":"A","id":"1","name":"Duplicate id"}
        ]}}"""
        val entries = AppleChartParser.parse(body, "us", ChartKind.Songs, 0).entries
        assertEquals(listOf(1, 5), entries.map { it.position })
        assertNull("non-web artwork is dropped", entries[1].artwork)
    }

    @Test
    fun `an entry without an id still gets a stable key`() {
        val body = """{"feed":{"results":[{"artistName":"A","name":"One"},{"artistName":"A","name":"Two"}]}}"""
        val entries = AppleChartParser.parse(body, "us", ChartKind.Songs, 0).entries
        assertEquals(2, entries.map { it.id }.distinct().size)
    }

    @Test
    fun `an empty or missing result list is an empty chart, not an error`() {
        assertTrue(AppleChartParser.parse("""{"feed":{"results":[]}}""", "us", ChartKind.Songs, 0).entries.isEmpty())
        assertTrue(AppleChartParser.parse("""{"feed":{}}""", "us", ChartKind.Songs, 0).entries.isEmpty())
    }

    @Test(expected = ChartFormatException::class)
    fun `html instead of json is malformed`() {
        AppleChartParser.parse("<html>500</html>", "us", ChartKind.Songs, 0)
    }

    @Test(expected = ChartFormatException::class)
    fun `json without a feed is malformed`() {
        AppleChartParser.parse("""{"error":"x"}""", "us", ChartKind.Songs, 0)
    }

    @Test(expected = ChartFormatException::class)
    fun `results that are not a list are malformed`() {
        AppleChartParser.parse("""{"feed":{"results":"nope"}}""", "us", ChartKind.Songs, 0)
    }

    @Test
    fun `songs cap at the hundred the endpoint was asked for`() {
        val many = (1..120).joinToString(",") { """{"artistName":"A","id":"$it","name":"S$it"}""" }
        assertEquals(100, AppleChartParser.parse("""{"feed":{"results":[$many]}}""", "us", ChartKind.Songs, 0).entries.size)
    }

    @Test
    fun `artwork is only resized where the mzstatic pattern is recognised`() {
        val square = "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/f7/x/cover.jpg/100x100bb.jpg"
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/f7/x/cover.jpg/600x600bb.webp", AppleArtwork.square(square, 600))
        val still = "https://is1-ssl.mzstatic.com/image/thumb/Video221/v4/a/b.jpg/100x75mv.jpg"
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Video221/v4/a/b.jpg/640x480mv.webp", AppleArtwork.landscape(still, 640))
        val unknown = "https://example.com/cover/100x100bb.jpg"
        assertEquals(unknown, AppleArtwork.square(unknown, 600))
        assertEquals("a still is not squared", still, AppleArtwork.square(still, 600))
        assertEquals("a cover is not treated as a still", square, AppleArtwork.landscape(square, 600))
        assertNull(AppleArtwork.square(null, 600))
    }
}
