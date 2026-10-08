package com.shiny.music.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsMotionTest {

    private val master = "https://mvod.itunes.apple.com/itunes-assets/HLSMusic/v4/aa/bb/P123_default.m3u8"

    private val masterText = """
        #EXTM3U
        #EXT-X-VERSION:6
        #EXT-X-STREAM-INF:BANDWIDTH=2400000,AVERAGE-BANDWIDTH=2100000,CODECS="hvc1.2.4.L123.B0",RESOLUTION=1080x1080
        P123_1080_hevc.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=900000,AVERAGE-BANDWIDTH=800000,CODECS="avc1.64001f",RESOLUTION=480x480
        P123_480_avc.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1500000,AVERAGE-BANDWIDTH=1300000,CODECS="avc1.640028",RESOLUTION=640x640
        P123_640_avc.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=3500000,CODECS="avc1.640032",RESOLUTION=1080x1080
        https://other.example.com/P123_1080_avc.m3u8
    """.trimIndent()

    @Test
    fun `a master playlist lists every size of the clip`() {
        val variants = HlsMotion.variants(master, masterText)
        assertEquals(4, variants.size)
        assertEquals("https://mvod.itunes.apple.com/itunes-assets/HLSMusic/v4/aa/bb/P123_1080_hevc.m3u8", variants[0].playlistUrl)
        assertEquals(1080, variants[0].width)
        // The average is what a clip really costs; the peak only where no average is given.
        assertEquals(2_100_000, variants[0].bandwidth)
        assertEquals(3_500_000, variants[3].bandwidth)
        assertEquals("https://other.example.com/P123_1080_avc.m3u8", variants[3].playlistUrl)
        assertTrue("hvc1" in variants[0].codecs)
    }

    @Test
    fun `the plain file is the h264 size nearest a phone's width`() {
        val chosen = HlsMotion.standard(HlsMotion.variants(master, masterText))
        assertEquals(640, chosen?.width)
        assertTrue("avc1" in chosen!!.codecs)
    }

    @Test
    fun `with no h264 at all the clip is still playable from what there is`() {
        val hevcOnly = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=900000,CODECS="hvc1.2.4.L93.B0",RESOLUTION=540x720
            tall_540.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,CODECS="hvc1.2.4.L123.B0",RESOLUTION=1080x1440
            tall_1080.m3u8
        """.trimIndent()
        assertEquals(540, HlsMotion.standard(HlsMotion.variants(master, hevcOnly))?.width)
        assertNull(HlsMotion.standard(emptyList()))
    }

    /** The portrait clip of SZA's SOS as Apple lists it: one line of every size and codec it comes in. */
    private val tallText = listOf(
        "664x886" to "avc1.64001f" to 2140560, "310x414" to "avc1.64001f" to 263275, "486x648" to "avc1.64001f" to 1126394,
        "486x648" to "avc1.64001f" to 1540672, "830x1106" to "avc1.640020" to 2822052, "1078x1438" to "avc1.640028" to 5000951,
        "1078x1438" to "avc1.640028" to 3786990, "1080x1440" to "avc1.640028" to 9752475, "1078x1438" to "hvc1.2.20000000.L123.B0" to 2738985,
        "1662x2216" to "hvc1.2.20000000.L150.B0" to 11760618, "2048x2732" to "hvc1.2.20000000.L153.B0" to 20197852,
    ).joinToString("\n", prefix = "#EXTM3U\n") { (shape, average) ->
        val (resolution, codecs) = shape
        "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=$average,BANDWIDTH=${average + 100000},VIDEO-RANGE=SDR,CODECS=\"$codecs\",RESOLUTION=$resolution,FRAME-RATE=24.000\n" +
            "P525436235_Anull_video_${average}_sdr_$resolution.m3u8"
    }

    @Test
    fun `every size says its shape, and the portrait clip is told from the square one`() {
        val tall = HlsMotion.variants(master, tallText)
        assertEquals(11, tall.size)
        assertEquals(664, tall[0].width)
        assertEquals(886, tall[0].height)
        assertTrue(tall.all { it.tall })
        assertTrue(HlsMotion.variants(master, masterText).none { it.tall })
    }

    @Test
    fun `the plain file of the portrait clip is the size that fills a phone's width, not the first one listed`() {
        val variants = HlsMotion.variants(master, tallText)
        // The first line of the playlist is 664 pixels across: drawn the width of a phone it would be enlarged.
        assertEquals(664, HlsMotion.standard(variants)?.width)
        val sharp = HlsMotion.sharp(variants)!!
        assertEquals(1078, sharp.width)
        assertEquals(1438, sharp.height)
        // H.264, and of the files that size the lightest: megabytes that add nothing are not fetched.
        assertTrue("avc1" in sharp.codecs)
        assertEquals(3_786_990, sharp.bandwidth)
        // A clip with nothing that wide gives the widest it has.
        assertEquals(640, HlsMotion.sharp(HlsMotion.variants(master, masterText).filter { (it.width ?: 0) < 1000 })?.width)
        assertNull(HlsMotion.sharp(emptyList()))
    }

    @Test
    fun `a variant playlist names the one mp4 it plays`() {
        val variant = "https://mvod.itunes.apple.com/itunes-assets/HLSMusic/v4/aa/bb/P123_640_avc.m3u8"
        val text = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MAP:URI="P123_640_avc-.mp4",BYTERANGE="1234@0"
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:400000@1234
            P123_640_avc-.mp4
            #EXT-X-ENDLIST
        """.trimIndent()
        assertEquals(
            "https://mvod.itunes.apple.com/itunes-assets/HLSMusic/v4/aa/bb/P123_640_avc-.mp4",
            HlsMotion.directVideoUrl(variant, text),
        )
        // Without a map line, the first media line that is an mp4.
        val plain = "#EXTM3U\n#EXTINF:6.0,\nclip.mp4\n#EXT-X-ENDLIST"
        assertEquals("https://mvod.itunes.apple.com/itunes-assets/HLSMusic/v4/aa/bb/clip.mp4", HlsMotion.directVideoUrl(variant, plain))
        // A playlist of transport-stream segments has no single file to name.
        assertNull(HlsMotion.directVideoUrl(variant, "#EXTM3U\n#EXTINF:6.0,\nseg0.ts\n"))
    }

    @Test
    fun `text that is not a master playlist has no variants`() {
        assertTrue(HlsMotion.variants(master, "#EXTM3U\n#EXTINF:6.0,\nclip.mp4").isEmpty())
        assertTrue(HlsMotion.variants(master, "<html>Access denied</html>").isEmpty())
        assertTrue(HlsMotion.variants(master, "").isEmpty())
    }
}
