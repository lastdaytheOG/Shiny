package com.shiny.music.artwork

import com.shiny.music.canvas.CanvasArtwork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ArtworkResolverTest {

    private val cover = "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/sos.jpg/100x100bb.jpg"
    private val master = "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/sos.jpg/3000x3000bb.jpg"
    private val ownCover = "https://lh3.googleusercontent.com/abc=w544-h544"
    private val still = "https://i.ytimg.com/vi/abc/maxresdefault.jpg"

    private fun song(track: String = "Kill Bill", artist: String = "SZA", album: String = "SOS", art: String = cover) =
        CatalogCandidate(trackId = 11, collectionId = 22, trackName = track, artistName = artist, collectionName = album, artworkUrl100 = art)

    private val videoSong = ArtworkQuery(title = "SZA - Kill Bill (Official Video)", artists = listOf("SZA"), ownArtwork = still)

    /** A catalogue that answers from a list, counts its calls, and can be taken offline. */
    private class FakeCatalog(var results: List<CatalogCandidate>? = emptyList()) : CatalogSearch {
        val calls = AtomicInteger()
        var gate: CompletableDeferred<Unit>? = null

        /** What one storefront answers, where it is not the same as the rest; null in it for "cannot be asked". */
        var byCountry: Map<String, List<CatalogCandidate>?> = emptyMap()
        override suspend fun songs(term: String, country: String): List<CatalogCandidate>? {
            calls.incrementAndGet()
            gate?.await()
            return if (country in byCountry) byCountry[country] else results
        }
    }

    private class FakeMotion(
        var motion: CanvasArtwork? = null,
        var failing: Boolean = false,
        var byIsrc: CatalogSong? = null,
    ) : AppleMotionArtworkProvider {
        val albumCalls = AtomicInteger()
        val isrcCalls = AtomicInteger()
        var askedAlbum: String? = null

        /** Every album asked about, as `storefront/id`, in order. */
        val asked = ArrayList<String>()

        /** Only these album ids have [motion], when set; every other id has none. */
        var onlyAlbums: Set<String>? = null
        override suspend fun motionForAlbum(albumId: String, country: String): CanvasArtwork? {
            albumCalls.incrementAndGet()
            askedAlbum = albumId
            asked += "$country/$albumId"
            if (failing) error("401: the token is no longer accepted")
            return motion?.takeIf { onlyAlbums?.contains(albumId) ?: true }?.copy(albumId = albumId)
        }

        override suspend fun songByIsrc(isrc: String, country: String): CatalogSong? {
            isrcCalls.incrementAndGet()
            return byIsrc
        }
    }

    private class MemoryIndex : ArtworkIndex {
        var stored: Map<String, ArtworkEntry> = emptyMap()
        override fun load(): Map<String, ArtworkEntry> = stored
        override fun save(entries: Collection<ArtworkEntry>) {
            stored = entries.associateBy { it.key }
        }
    }

    private var clock = 1_000_000L

    private fun resolver(
        catalog: CatalogSearch,
        motion: AppleMotionArtworkProvider = FakeMotion(),
        index: ArtworkIndex = MemoryIndex(),
        maxEntries: Int = 1500,
        countries: List<String> = listOf("us"),
    ) = ArtworkResolver(
        index = index,
        catalog = catalog,
        motionProvider = motion,
        countries = { countries },
        now = { clock },
        // Inline, so that what a test checks next has already been written.
        scope = CoroutineScope(Dispatchers.Unconfined),
        maxEntries = maxEntries,
    )

    @Test
    fun `a song with only a video still gets apple's cover`() = runBlocking {
        val catalog = FakeCatalog(listOf(song()))
        val resolver = resolver(catalog)
        assertEquals(cover, resolver.appleCover(videoSong))
        val result = resolver.result(videoSong)
        assertEquals(ArtworkSource.APPLE, result.source)
        // What is handed on is the cover at the size of Apple's master.
        assertEquals(master, result.staticArtworkUri)
        assertEquals("apple:11", result.identifier)
    }

    @Test
    fun `the first search result is not taken on trust`() = runBlocking {
        val wrong = song(track = "Kill Bill (Karaoke)", artist = "Karaoke Stars", art = cover.replace("sos", "karaoke"))
        val resolver = resolver(FakeCatalog(listOf(wrong, song())))
        assertEquals(cover, resolver.appleCover(videoSong))
    }

    @Test
    fun `a song that already has a real cover keeps it and nothing is asked`() = runBlocking {
        val catalog = FakeCatalog(listOf(song()))
        val resolver = resolver(catalog)
        val own = ownCover
        val query = ArtworkQuery(title = "Kill Bill", artists = listOf("SZA"), album = "SOS", ownArtwork = own)
        assertNull(resolver.appleCover(query))
        assertEquals(0, catalog.calls.get())
        assertEquals(ArtworkSource.FALLBACK, resolver.result(query).source)
        assertEquals(own, resolver.result(query).staticArtworkUri)
    }

    @Test
    fun `a file on the phone is never looked up`() = runBlocking {
        val catalog = FakeCatalog(listOf(song()))
        val motion = FakeMotion(motion = CanvasArtwork(animated = "https://example.com/a.m3u8"))
        val resolver = resolver(catalog, motion)
        val file = ArtworkQuery(title = "Kill Bill", artists = listOf("SZA"), ownArtwork = "content://media/external/audio/albumart/7", local = true)
        assertNull(resolver.appleCover(file))
        assertNull(resolver.motion(file))
        assertEquals(0, catalog.calls.get())
        assertEquals(0, motion.albumCalls.get())
        assertEquals(ArtworkSource.EMBEDDED, resolver.result(file).source)
        assertEquals(ArtworkSource.NONE, resolver.result(file.copy(ownArtwork = null)).source)
    }

    @Test
    fun `a song with nothing to identify it is not looked up at all`() = runBlocking {
        val catalog = FakeCatalog(listOf(song()))
        val resolver = resolver(catalog)
        val nameless = ArtworkQuery(title = "Kill Bill", artists = emptyList(), ownArtwork = still)
        assertNull(nameless.key)
        assertNull(resolver.appleCover(nameless))
        assertEquals(0, catalog.calls.get())
    }

    @Test
    fun `a recording is keyed by its isrc, or by artist, album and track, never by title alone`() {
        assertEquals("isrc:USRC12204601", videoSong.copy(isrc = " usrc12204601 ").key)
        assertEquals("n:sza|sos|killbill", ArtworkQuery("Kill Bill", listOf("SZA"), album = "SOS (Deluxe)").key)
        // The same title by someone else, or on another album, is a different recording.
        val one = ArtworkQuery("Animal", listOf("KATSEYE"), album = "WILD").key
        assertTrue(one != ArtworkQuery("Animal", listOf("Maroon 5"), album = "WILD").key)
        assertTrue(one != ArtworkQuery("Animal", listOf("KATSEYE"), album = "Other").key)
    }

    @Test
    fun `an isrc identifies the recording without searching`() = runBlocking {
        val catalog = FakeCatalog(emptyList())
        val template = "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/aa/bb/sos.jpg/{w}x{h}bb.jpg"
        val motion = FakeMotion(byIsrc = CatalogSong(artwork = template, songId = "99", albumId = "22"))
        val resolver = resolver(catalog, motion)
        val query = videoSong.copy(isrc = "USRC12204601")
        assertEquals(template, resolver.appleCover(query))
        assertEquals(1, motion.isrcCalls.get())
        assertEquals(0, catalog.calls.get())
        assertEquals("apple:99", resolver.result(query).identifier)
    }

    @Test
    fun `no apple result is remembered, and asked about again only much later`() = runBlocking {
        val catalog = FakeCatalog(emptyList())
        val resolver = resolver(catalog)
        assertNull(resolver.appleCover(videoSong))
        assertNull(resolver.appleCover(videoSong))
        assertEquals(1, catalog.calls.get())
        // The song keeps what it had.
        assertEquals(ArtworkSource.FALLBACK, resolver.result(videoSong).source)
        assertEquals(still, resolver.result(videoSong).staticArtworkUri)

        clock += ArtworkResolver.CatalogueMissTtl + 1
        catalog.results = listOf(song())
        assertEquals(cover, resolver.appleCover(videoSong))
        assertEquals(2, catalog.calls.get())
    }

    @Test
    fun `a network failure is not mistaken for no artwork`() = runBlocking {
        val catalog = FakeCatalog(results = null)
        val resolver = resolver(catalog)
        assertNull(resolver.appleCover(videoSong))
        // Back online: it is simply asked again.
        catalog.results = listOf(song())
        assertEquals(cover, resolver.appleCover(videoSong))
        assertEquals(2, catalog.calls.get())
    }

    @Test
    fun `a found cover is a cache hit ever after`() = runBlocking {
        val catalog = FakeCatalog(listOf(song()))
        val resolver = resolver(catalog)
        repeat(4) { assertEquals(cover, resolver.appleCover(videoSong)) }
        assertEquals(1, catalog.calls.get())
    }

    @Test
    fun `many askers at once make one request`() = runBlocking {
        val catalog = FakeCatalog(listOf(song())).apply { gate = CompletableDeferred() }
        val resolver = resolver(catalog)
        val askers = List(5) { async(Dispatchers.Default) { resolver.appleCover(videoSong) } }
        while (catalog.calls.get() == 0) delay(5)
        delay(60)
        catalog.gate!!.complete(Unit)
        assertEquals(List(5) { cover }, askers.awaitAll())
        assertEquals(1, catalog.calls.get())
    }

    private val clip = CanvasArtwork(
        albumId = "22",
        animated = "https://mvod.itunes.apple.com/square/P_default.m3u8",
        videoUrl = "https://mvod.itunes.apple.com/square/P_600.mp4",
        tallAnimated = "https://mvod.itunes.apple.com/tall/P_default.m3u8",
        tallStill = "https://is1-ssl.mzstatic.com/image/thumb/Video/tall.jpg/{w}x{h}bb.jpg",
        tallAspect = 0.75f,
        tallVideoUrl = "https://mvod.itunes.apple.com/tall/P_664x886.mp4",
    )

    @Test
    fun `one catalogue lookup gives the cover and both shapes of motion artwork`() = runBlocking {
        val catalog = FakeCatalog(listOf(song()))
        val motion = FakeMotion(motion = clip)
        val resolver = resolver(catalog, motion)

        val apple = resolver.resolve(videoSong).apple!!
        assertEquals(1, catalog.calls.get())
        assertEquals(1, motion.albumCalls.get())
        assertEquals("11", apple.songId)
        assertEquals("22", apple.albumId)

        // Static artwork: the master, and any size of it.
        assertEquals(master, apple.staticArtworkUri)
        assertEquals(cover.replace("100x100bb", "1080x1080bb"), apple.staticAt(1080))
        assertEquals(cover.replace("100x100bb", "1080x1080bb-100"), apple.staticAt(1080, best = true))

        // Editorial video, square: the stream and the plain file behind it.
        val square = apple.squareMotion!!
        assertEquals(clip.animated, square.hlsUri)
        assertEquals(clip.videoUrl, square.mp4Uri)
        assertEquals(MotionArtworkType.HLS, square.type)
        assertEquals(1f, square.aspect)

        // Editorial video, tall.
        val tall = apple.tallMotion!!
        assertEquals(clip.tallAnimated, tall.hlsUri)
        assertEquals(clip.tallVideoUrl, tall.mp4Uri)
        assertEquals(clip.tallStill, tall.still)
        assertEquals(0.75f, tall.aspect)

        // Asking again asks no one.
        resolver.resolve(videoSong)
        assertEquals(1, catalog.calls.get())
        assertEquals(1, motion.albumCalls.get())
    }

    @Test
    fun `an album with only square motion artwork has no tall one, and a clip that is only a file is an mp4`() = runBlocking {
        val onlyFile = CanvasArtwork(albumId = "22", videoUrl = "https://example.com/square.mp4")
        val resolver = resolver(FakeCatalog(listOf(song())), FakeMotion(motion = onlyFile))
        val apple = resolver.resolve(videoSong).apple!!
        assertNull(apple.tallMotion)
        assertNull(apple.squareMotion!!.hlsUri)
        assertEquals(MotionArtworkType.MP4, apple.squareMotion!!.type)
    }

    @Test
    fun `motion artwork is asked for by the album the song was matched to`() = runBlocking {
        val motion = FakeMotion(motion = clip)
        val resolver = resolver(FakeCatalog(listOf(song())), motion)
        val found = resolver.motion(videoSong)
        assertEquals("22", motion.askedAlbum)
        assertNotNull(found)
        val result = resolver.result(videoSong)
        assertEquals(clip.animated, result.squareMotionArtworkUri)
        assertEquals(clip.tallAnimated, result.tallMotionArtworkUri)
        assertEquals(clip.animated, result.motionArtworkUri)
        assertEquals(MotionArtworkType.HLS, result.motionArtworkType)
        // Asked once; after that it is remembered.
        resolver.motion(videoSong)
        assertEquals(1, motion.albumCalls.get())
    }

    @Test
    fun `an album with no motion artwork is the ordinary case, and is not asked about again`() = runBlocking {
        val motion = FakeMotion(motion = null)
        val resolver = resolver(FakeCatalog(listOf(song())), motion)
        assertNull(resolver.motion(videoSong))
        assertNull(resolver.motion(videoSong))
        assertEquals(1, motion.albumCalls.get())
        val result = resolver.result(videoSong)
        assertNull(result.motionArtworkUri)
        assertNull(result.motionArtworkType)
        // The cover is unaffected.
        assertEquals(master, result.staticArtworkUri)

        clock += ArtworkResolver.MotionMissTtl + 1
        motion.motion = clip
        assertNotNull(resolver.motion(videoSong))
    }

    @Test
    fun `a refused token loses the motion artwork and nothing else`() = runBlocking {
        val motion = FakeMotion(failing = true)
        val resolver = resolver(FakeCatalog(listOf(song())), motion)
        assertNull(resolver.motion(videoSong))
        // The cover does not depend on the token.
        assertEquals(cover, resolver.appleCover(videoSong))
        // It was a failure to ask, not an answer: with a working token it is asked again.
        motion.failing = false
        motion.motion = clip
        assertEquals(clip.animated, resolver.motion(videoSong)?.animated)
        assertEquals(2, motion.albumCalls.get())
    }

    private fun onAlbum(id: Long, album: String = "SOS") =
        CatalogCandidate(trackId = id + 1, collectionId = id, trackName = "Kill Bill", artistName = "SZA", collectionName = album, artworkUrl100 = cover)

    @Test
    fun `an album with no motion artwork under one id is asked for under its other ids`() = runBlocking {
        // One album, three ids, and only the last has the clip: the catalogue's own habit.
        val catalog = FakeCatalog(listOf(onAlbum(100), onAlbum(200), onAlbum(300), onAlbum(400, album = "SOS Deluxe: LANA")))
        val motion = FakeMotion(motion = clip).apply { onlyAlbums = setOf("300") }
        val resolver = resolver(catalog, motion)

        val found = resolver.motion(videoSong)
        assertEquals(clip.tallAnimated, found?.tallAnimated)
        assertEquals("300", found?.albumId)
        // In order, stopping at the first that has it.
        assertEquals(listOf("us/100", "us/200", "us/300"), motion.asked)
        // The cover is still the one of the album the song was matched to.
        assertEquals("apple:101", resolver.result(videoSong).identifier)
    }

    @Test
    fun `a song that names no album is given the release of it that has motion artwork, cover and all`() = runBlocking {
        // A music video: the catalogue has the song on its album and as a single, and it is
        // the single that Apple gave a clip to.
        val singleCover = cover.replace("sos", "single")
        val single = CatalogCandidate(trackId = 501, collectionId = 500, trackName = "Kill Bill", artistName = "SZA", collectionName = "Kill Bill - Single", artworkUrl100 = singleCover)
        val catalog = FakeCatalog(listOf(onAlbum(100), single))
        val motion = FakeMotion(motion = clip).apply { onlyAlbums = setOf("500") }
        val resolver = resolver(catalog, motion)

        // Before anything is known about clips, it is on the album.
        assertEquals(cover, resolver.appleCover(videoSong))
        val found = resolver.motion(videoSong)
        assertEquals("500", found?.albumId)
        assertEquals(listOf("us/100", "us/500"), motion.asked)
        // And from then on the single is its release: one picture, not a cover under another's clip.
        assertEquals(singleCover, resolver.appleCover(videoSong))
        val apple = resolver.result(videoSong).apple!!
        assertEquals("500", apple.albumId)
        assertEquals("501", apple.songId)
        assertEquals("Kill Bill - Single", apple.albumName)
        assertEquals(1, catalog.calls.get())
    }

    @Test
    fun `a song that names its album is never moved to another release`() = runBlocking {
        val deluxe = onAlbum(400, album = "SOS Deluxe: LANA")
        val motion = FakeMotion(motion = clip).apply { onlyAlbums = setOf("100") }
        val resolver = resolver(FakeCatalog(listOf(onAlbum(100), deluxe)), motion)
        // It says it is from the deluxe edition, which has no clip. The standard one's is not borrowed.
        val fromDeluxe = ArtworkQuery(title = "Kill Bill", artists = listOf("SZA"), album = "SOS Deluxe: LANA", ownArtwork = ownCover)
        assertNull(resolver.motion(fromDeluxe))
        assertEquals(listOf("us/400"), motion.asked)
    }

    @Test
    fun `an album none of whose ids has motion artwork has none, and that is remembered`() = runBlocking {
        val catalog = FakeCatalog(listOf(onAlbum(100), onAlbum(200)))
        val motion = FakeMotion(motion = null)
        val resolver = resolver(catalog, motion)
        assertNull(resolver.motion(videoSong))
        assertNull(resolver.motion(videoSong))
        assertEquals(listOf("us/100", "us/200"), motion.asked)
    }

    @Test
    fun `the songs of one album ask about it once between them`() = runBlocking {
        val snooze = CatalogCandidate(trackId = 12, collectionId = 22, trackName = "Snooze", artistName = "SZA", collectionName = "SOS", artworkUrl100 = cover)
        val motion = FakeMotion(motion = clip)
        val resolver = resolver(FakeCatalog(listOf(song(), snooze)), motion)
        assertNotNull(resolver.motion(videoSong))
        assertNotNull(resolver.motion(ArtworkQuery(title = "Snooze", artists = listOf("SZA"), album = "SOS", ownArtwork = ownCover)))
        assertEquals(1, motion.albumCalls.get())
    }

    @Test
    fun `an album is asked for in the storefront the song was found in`() = runBlocking {
        // Not in the phone's own storefront, but in the next one: its ids are that one's.
        val catalog = FakeCatalog(listOf(song())).apply { byCountry = mapOf("in" to emptyList()) }
        val motion = FakeMotion(motion = clip)
        val resolver = resolver(catalog, motion, countries = listOf("in", "us"))
        assertNotNull(resolver.motion(videoSong))
        assertEquals(listOf("us/22"), motion.asked)
    }

    @Test
    fun `a storefront that cannot be asked does not stop the next one being asked`() = runBlocking {
        val catalog = FakeCatalog(listOf(song())).apply { byCountry = mapOf("zz" to null) }
        val resolver = resolver(catalog, countries = listOf("zz", "us"))
        assertEquals(cover, resolver.appleCover(videoSong))
        // With none of them answering it is still "could not ask", and is tried again.
        val offline = FakeCatalog(results = null)
        val later = resolver(offline, countries = listOf("zz", "us"))
        assertNull(later.appleCover(videoSong))
        offline.results = listOf(song())
        assertEquals(cover, later.appleCover(videoSong))
    }

    @Test
    fun `what an older matcher chose is looked up again, once`() = runBlocking {
        // As an earlier build left it: the song on an edition with no motion artwork, checked today.
        val old = ArtworkEntry(
            key = videoSong.key!!,
            appleArtwork = cover.replace("sos", "lana"),
            appleSongId = "901",
            appleAlbumId = "900",
            catalogueCheckedAt = clock,
            motion = null,
            motionCheckedAt = clock,
        )
        val index = MemoryIndex().apply { stored = mapOf(old.key to old) }
        val catalog = FakeCatalog(listOf(song()))
        val motion = FakeMotion(motion = clip)
        val resolver = resolver(catalog, motion, index)

        assertEquals(clip.animated, resolver.motion(videoSong)?.animated)
        assertEquals(listOf("us/22"), motion.asked)
        assertEquals(cover, resolver.appleCover(videoSong))
        assertEquals(ArtworkMatcher.Version, index.stored.getValue(old.key).matcher)
        // Once: after that it is an ordinary cache hit.
        resolver.motion(videoSong)
        resolver.appleCover(videoSong)
        assertEquals(1, catalog.calls.get())
        assertEquals(1, motion.albumCalls.get())
    }

    @Test
    fun `an album with only the portrait clip has no square one`() = runBlocking {
        val tallOnly = CanvasArtwork(albumId = "22", tallAnimated = clip.tallAnimated, tallStill = clip.tallStill, tallAspect = 0.75f)
        val resolver = resolver(FakeCatalog(listOf(song())), FakeMotion(motion = tallOnly))
        val result = resolver.resolve(videoSong)
        assertNull(result.apple!!.squareMotion)
        assertNull(result.squareMotionArtworkUri)
        assertEquals(clip.tallAnimated, result.apple!!.tallMotion?.hlsUri)
        assertEquals(clip.tallAnimated, result.tallMotionArtworkUri)
    }

    @Test
    fun `with no connection nothing is remembered as having no motion artwork`() = runBlocking {
        // The player is opened once in a tunnel: the catalogue cannot be asked, and the search
        // by name that stands in for it finds nothing either.
        val catalog = FakeCatalog(results = null)
        val motion = FakeMotion(motion = clip)
        val index = MemoryIndex()
        val resolver = resolver(catalog, motion, index)
        assertNull(resolver.motion(videoSong))
        assertEquals(0L, index.stored[videoSong.key]?.motionCheckedAt ?: 0L)

        // Out of the tunnel, the same day: it is asked, and found.
        catalog.results = listOf(song())
        assertEquals(clip.animated, resolver.motion(videoSong)?.animated)
    }

    @Test
    fun `a song the catalogue does not have, searched for by name and not found, is remembered`() = runBlocking {
        val catalog = FakeCatalog(emptyList())
        val named = AtomicInteger()
        val motion = object : AppleMotionArtworkProvider {
            override suspend fun motionForAlbum(albumId: String, country: String): CanvasArtwork? = null
            override suspend fun motionFor(query: ArtworkQuery): CanvasArtwork? {
                named.incrementAndGet()
                return null
            }
        }
        val resolver = resolver(catalog, motion)
        assertNull(resolver.motion(videoSong))
        assertNull(resolver.motion(videoSong))
        assertEquals(1, named.get())
    }

    @Test
    fun `a clip is an hls stream, and the plain file behind it an mp4`() {
        assertEquals(MotionArtworkType.HLS, motionTypeOf("https://mvod.itunes.apple.com/a/P123_default.m3u8"))
        assertEquals(MotionArtworkType.HLS, motionTypeOf("https://mvod.itunes.apple.com/a/master.m3u8?x=y.mp4"))
        assertEquals(MotionArtworkType.MP4, motionTypeOf("https://mvod.itunes.apple.com/a/P123_600.mp4"))
        assertEquals(MotionArtworkType.MP4, motionTypeOf("https://mvod.itunes.apple.com/a/P123_600.MP4?token=1"))
    }

    @Test
    fun `the portrait still is asked for in its own shape`() {
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Video/tall.jpg/1200x1600bb.jpg", clip.tallStillAt(1200))
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Video/tall.jpg/1080x1440bb-100.jpg", clip.tallStillAt(1080, best = true))
        // Never a pixel short of the width asked for.
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Video/tall.jpg/1080x1441bb.jpg",
            clip.copy(tallAspect = 0.74963397f).tallStillAt(1080),
        )
        assertNull(CanvasArtwork(animated = "x").tallStillAt(1200))
    }

    @Test
    fun `what was found is there offline, in a new run of the app`() = runBlocking {
        val index = MemoryIndex()
        val first = resolver(FakeCatalog(listOf(song())), FakeMotion(motion = clip), index)
        first.appleCover(videoSong)
        first.motion(videoSong)

        // A new process, with no connection at all.
        val catalog = FakeCatalog(results = null)
        val motion = FakeMotion(failing = true)
        val later = resolver(catalog, motion, index)
        assertEquals(cover, later.appleCover(videoSong))
        assertEquals(clip.tallAnimated, later.motion(videoSong)?.tallAnimated)
        assertEquals(0, catalog.calls.get())
        assertEquals(0, motion.albumCalls.get())
    }

    @Test
    fun `a corrupted index is an empty one, not a crash`() = runBlocking {
        val file = File.createTempFile("artwork-index", ".json").apply { writeText("{\"entries\":[{\"key\":") }
        try {
            val index = FileArtworkIndex(file)
            assertTrue(index.load().isEmpty())
            val resolver = resolver(FakeCatalog(listOf(song())), index = index)
            assertEquals(cover, resolver.appleCover(videoSong))
            // And it is written back whole.
            assertEquals(cover, FileArtworkIndex(file).load().values.single().appleArtwork)
        } finally {
            file.delete()
            File(file.parentFile, file.name + ".tmp").delete()
        }
    }

    @Test
    fun `the index keeps the songs used most recently`() = runBlocking {
        val index = MemoryIndex()
        val resolver = resolver(FakeCatalog(emptyList()), index = index, maxEntries = 10)
        repeat(30) { n ->
            clock += 1
            resolver.appleCover(ArtworkQuery(title = "Song number $n", artists = listOf("SZA"), ownArtwork = still))
        }
        assertTrue(index.stored.size <= 10)
        assertTrue("the newest is kept", index.stored.keys.any { it.endsWith("songnumber29") })
    }

    @Test
    fun `when everything fails the answer is simply nothing`() = runBlocking {
        val catalog = CatalogSearch { _, _ -> throw IllegalStateException("the network is on fire") }
        val motion = object : AppleMotionArtworkProvider {
            override suspend fun motionForAlbum(albumId: String, country: String): CanvasArtwork? = throw IllegalStateException("a very large clip")
            override suspend fun motionFor(query: ArtworkQuery): CanvasArtwork? = throw IllegalStateException("no")
            override suspend fun songByIsrc(isrc: String, country: String): CatalogSong? = throw IllegalStateException("no")
        }
        val index = object : ArtworkIndex {
            override fun load(): Map<String, ArtworkEntry> = throw IllegalStateException("disk gone")
            override fun save(entries: Collection<ArtworkEntry>) = throw IllegalStateException("disk full")
        }
        val resolver = resolver(catalog, motion, index)
        assertNull(resolver.appleCover(videoSong.copy(isrc = "USRC12204601")))
        assertNull(resolver.motion(videoSong))
        // The song still has the artwork it came with.
        assertEquals(still, resolver.result(videoSong).staticArtworkUri)
    }
}
