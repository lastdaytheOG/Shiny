package com.shiny.music.artwork

import com.music.innertube.SharedHttp
import com.shiny.music.canvas.HlsMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The artwork pipeline against Apple's live catalogue: the real search, the real matcher, the
 * real web-player token, the real album reads and the real streams, end to end. Each song
 * here is one whose answer is known, and is given the way the player gives it.
 *
 * It needs the network and an interface Apple does not document, so it is not part of an
 * ordinary test run: set `SHINY_ARTWORK_LIVE=1` to run it. What it prints is the whole trail of
 * every song, one line a step.
 */
class ArtworkLiveTest {

    private enum class Has { BOTH, SQUARE_ONLY, TALL_ONLY, NONE }

    private class Case(val query: ArtworkQuery, val album: String, val has: Has)

    private val cover = "https://lh3.googleusercontent.com/a-real-cover=w544-h544"
    private val still = "https://i.ytimg.com/vi/abc/maxresdefault.jpg"

    private fun song(title: String, artist: String, album: String?, seconds: Int, own: String = cover) =
        ArtworkQuery(title = title, artists = listOf(artist), album = album, durationSeconds = seconds, ownArtwork = own)

    private val cases = listOf(
        // Albums with motion artwork in both shapes.
        Case(song("Kill Bill", "SZA", "SOS", 154), "SOS", Has.BOTH),
        Case(song("Blinding Lights", "The Weeknd", "After Hours", 200), "After Hours", Has.BOTH),
        Case(song("Icarus", "ARTMS", "Club Icarus", 173), "<Club Icarus> - EP", Has.BOTH),
        // The edition: the catalogue lists "GUTS (spilled)", which has none, ahead of "GUTS".
        Case(song("vampire", "Olivia Rodrigo", "GUTS", 220), "GUTS", Has.BOTH),
        // A music video: no album to go by, a length that is the video's, a still for a cover.
        Case(song("Olivia Rodrigo - vampire (Official Video)", "Olivia Rodrigo", null, 0, still), "GUTS", Has.BOTH),
        Case(song("The Weeknd - Blinding Lights (Official Video)", "The Weeknd", null, 262, still), "After Hours", Has.BOTH),
        // One shape only: about one album in two hundred.
        Case(song("Stop Breathing", "Playboi Carti", "Whole Lotta Red", 218), "Whole Lotta Red", Has.SQUARE_ONLY),
        Case(song("High Hopes 3000", "ROLE MODEL", "Chuck Timely & The Hourglass", 245), "Chuck Timely & The Hourglass", Has.TALL_ONLY),
        // Apple has no motion artwork for these: the cover is all there is.
        Case(song("Blue Blood", "ARTMS", "Hyper-Ego", 147), "<Hyper-Ego> - EP", Has.NONE),
        Case(song("BIRDS OF A FEATHER", "Billie Eilish", "HIT ME HARD AND SOFT", 210), "HIT ME HARD AND SOFT", Has.NONE),
        Case(song("Karma Police", "Radiohead", "OK Computer", 264), "OK Computer", Has.NONE),
    )

    private fun fetch(url: String, range: String? = null): okhttp3.Response =
        SharedHttp.client.newCall(Request.Builder().url(url).apply { range?.let { header("Range", it) } }.build()).execute()

    /** Width and height out of a JPEG's frame header. */
    private fun jpegSize(bytes: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 9 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) return null
            val marker = bytes[i + 1].toInt() and 0xFF
            val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val height = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                val width = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                return width to height
            }
            i += 2 + length
        }
        return null
    }

    private fun describe(label: String, url: String) {
        val variants = fetch(url).use { HlsMotion.variants(url, it.body?.string().orEmpty()) }
        assertTrue("$label: a master playlist with sizes in it", variants.size > 1)
        val widths = variants.mapNotNull { it.width }.distinct().sorted()
        val tallest = variants.maxBy { (it.width ?: 0) * (it.height ?: 0) }
        println("      $label stream  ${variants.size} sizes, ${if (variants.all { it.tall }) "all portrait" else if (variants.none { it.tall }) "all square" else "MIXED"}; widths $widths; largest ${tallest.width}x${tallest.height}; first listed ${variants.first().width}x${variants.first().height}")
        HlsMotion.sharp(variants)?.let { println("      $label for a 1080 px phone  ${it.width}x${it.height} ${it.codecs.substringBefore('.')} ${it.bandwidth / 1000} kbps") }
    }

    private fun assertPlays(label: String, mp4: String?) {
        assertNotNull("$label: a plain file behind the stream", mp4)
        fetch(mp4!!, "bytes=0-63").use { response ->
            assertTrue("$label: $mp4 answers ${response.code}", response.code == 200 || response.code == 206)
            assertTrue("$label: is a video (${response.header("Content-Type")})", response.header("Content-Type").orEmpty().startsWith("video/"))
            println("      $label file    ${response.code} ${response.header("Content-Type")} ${response.header("Content-Range")?.substringAfter('/')} bytes  ${mp4.substringAfterLast('/')}")
        }
    }

    @Test
    fun `apple's artwork is found, chosen and playable for songs whose answer is known`() = runBlocking {
        assumeTrue("set SHINY_ARTWORK_LIVE=1 to ask Apple's live catalogue", System.getenv("SHINY_ARTWORK_LIVE") == "1")
        val trail = ArrayList<String>()
        val resolver = ArtworkResolver(
            index = object : ArtworkIndex {
                override fun load(): Map<String, ArtworkEntry> = emptyMap()
                override fun save(entries: Collection<ArtworkEntry>) = Unit
            },
            // Exactly what the app is given: the same searches, in the same storefronts.
            catalog = AppleCatalog,
            motionProvider = DefaultAppleMotionArtworkProvider,
            countries = { listOf(Artworks.storefront(), "us").distinct() },
            scope = CoroutineScope(Dispatchers.IO),
            log = { trail += it },
        )
        for (case in cases) {
            trail.clear()
            val result = resolver.resolve(case.query)
            println("\n== ${case.query.title} / ${case.query.artists.first()} / album ${case.query.album}")
            trail.forEach { println("   $it") }
            val apple = result.apple
            assertNotNull("${case.query.title}: found in the catalogue", apple)
            assertEquals("${case.query.title}: the album it is on", case.album, apple!!.albumName)

            // Static artwork: what the image server really sends for the master, and for a phone.
            val master = fetch(apple.staticArtworkUri!!).use { it.body!!.bytes() }
            val size = jpegSize(master)
            println("   static    ${apple.staticArtworkUri} -> ${size?.first}x${size?.second} JPEG, ${master.size / 1024} KB")
            assertNotNull("${case.query.title}: the cover is a picture", size)
            assertTrue("${case.query.title}: the cover is at least 1000 px (${size!!.first})", size.first >= 1000 && size.first == size.second)
            println("   shown     ${result.source}: ${result.staticArtworkUri}")
            assertEquals(if (case.query.ownArtworkIsVideoStill) ArtworkSource.APPLE else ArtworkSource.FALLBACK, result.source)

            val square = apple.squareMotion
            val tall = apple.tallMotion
            println("   motion    square=${square != null} tall=${tall != null} (expected ${case.has})")
            when (case.has) {
                Has.NONE -> { assertNull(square); assertNull(tall) }
                Has.BOTH -> { assertNotNull(square); assertNotNull(tall) }
                Has.SQUARE_ONLY -> { assertNotNull(square); assertNull(tall) }
                Has.TALL_ONLY -> { assertNull(square); assertNotNull(tall) }
            }
            square?.let {
                assertEquals(MotionArtworkType.HLS, it.type)
                describe("square", it.hlsUri!!)
                assertPlays("square", it.mp4Uri)
            }
            tall?.let {
                assertEquals(MotionArtworkType.HLS, it.type)
                assertTrue("portrait, about 3:4 (${it.aspect})", it.aspect in 0.7f..0.8f)
                assertNotNull("a still of it, to size", it.still)
                describe("tall  ", it.hlsUri!!)
                assertPlays("tall  ", it.mp4Uri)
                assertTrue("the plain portrait file fills a phone's width: ${it.mp4Uri}", Regex("""_(\d+)x(\d+)""").find(it.mp4Uri!!)!!.groupValues[1].toInt() >= 1000)
            }
            // The catalogue search allows about twenty questions a minute.
            delay(3_200)
        }
    }
}
