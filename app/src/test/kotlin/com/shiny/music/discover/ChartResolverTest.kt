package com.shiny.music.discover

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.shiny.music.viewmodels.DiscoverNotice
import com.shiny.music.viewmodels.noticeFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChartResolverTest {

    private val atv = "MUSIC_VIDEO_TYPE_ATV"
    private val omv = "MUSIC_VIDEO_TYPE_OMV"
    private val ugc = "MUSIC_VIDEO_TYPE_UGC"

    private fun song(
        id: String,
        title: String,
        vararg artists: String,
        explicit: Boolean = false,
        type: String? = atv,
        album: Album? = null,
    ) = SongItem(id = id, title = title, artists = artists.map { Artist(it, null) }, album = album, thumbnail = "t", explicit = explicit, musicVideoType = type)

    private fun album(id: String, title: String, vararg artists: String, year: Int? = null) =
        AlbumItem(browseId = id, playlistId = "OLAK$id", title = title, artists = artists.map { Artist(it, null) }, year = year, thumbnail = "")

    private fun entry(title: String, credit: String, slug: String? = null, explicit: Boolean = false, released: String? = null) = ChartEntry(
        position = 1,
        id = "apple-$title",
        title = title,
        artist = credit,
        artistUrl = slug?.let { "https://music.apple.com/in/artist/$it/1" },
        releaseDate = released,
        explicit = explicit,
    )

    private class FakeCatalog(
        var songs: Map<String, List<SongItem>?> = emptyMap(),
        var videos: Map<String, List<SongItem>?> = emptyMap(),
        var albums: List<AlbumItem>? = emptyList(),
        var artists: List<ArtistItem>? = emptyList(),
    ) : ChartCatalog {
        val queries = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun songs(query: String): List<SongItem>? {
            synchronized(queries) { queries += "song:$query" }
            gate?.await()
            return if (query in songs) songs[query] else songs["*"] ?: emptyList<SongItem>().takeIf { "*" !in songs }
        }
        override suspend fun videos(query: String): List<SongItem>? {
            synchronized(queries) { queries += "video:$query" }
            return if (query in videos) videos[query] else videos["*"] ?: emptyList<SongItem>().takeIf { "*" !in videos }
        }
        override suspend fun albums(query: String) = albums.also { synchronized(queries) { queries += "album:$query" } }
        override suspend fun artists(query: String) = artists.also { synchronized(queries) { queries += "artist:$query" } }
    }

    private fun candidates(vararg songs: SongItem) = songs.map(ChartResolver::candidate)

    private fun pick(e: ChartEntry, vararg songs: SongItem) = ChartMatcher.decide(MatchTarget.song(e), candidates(*songs))

    @Test
    fun `the exact recording wins over remixes, live takes, covers and karaoke`() {
        val e = entry("Ashke", "Karan Aujla & MXRCI", "karan-aujla")
        val decision = pick(
            e,
            song("1", "Ashke (Slowed + Reverb)", "Karan Aujla"),
            song("2", "Ashke (Live)", "Karan Aujla"),
            song("3", "Ashke - Karaoke Version", "Sing King"),
            song("4", "Ashke", "Karan Aujla", "MXRCI"),
            song("5", "Ashke (Remix)", "Karan Aujla"),
        )
        assertEquals(3, decision?.index)
        assertEquals(Fit.Exact, decision?.fit)
    }

    @Test
    fun `a remix on the chart only matches a remix`() {
        val e = entry("KALYANI (with Shreya Ghoshal) [Remix]", "ARJN, KDS, FIFTY4 & Shreya Ghoshal", "arjn")
        assertEquals(1, pick(e, song("a", "Kalyani", "ARJN"), song("b", "Kalyani (Remix)", "ARJN", "Shreya Ghoshal"))?.index)
        assertNull(pick(e, song("a", "Kalyani", "ARJN")))
    }

    @Test
    fun `film credits in brackets do not stop a match`() {
        val e = entry("One Sun One Moon (From \"Jailer 2\")", "Anirudh Ravichander, Super Subu", "anirudh-ravichander")
        assertEquals(0, pick(e, song("x", "One Sun One Moon", "Anirudh Ravichander"))?.index)
    }

    @Test
    fun `same title by somebody else is not a match`() {
        val e = entry("Alfaaz", "Hamza Malik & Zain Zohaib", "hamza-malik")
        assertNull(pick(e, song("x", "Alfaaz", "Some Cover Band")))
    }

    @Test
    fun `a different song by the right artist is not a match`() {
        val e = entry("Ashke", "Karan Aujla", "karan-aujla")
        assertNull(pick(e, song("x", "Softly", "Karan Aujla")))
    }

    @Test
    fun `a featured artist alone needs the exact title`() {
        val e = entry("Tum Ho", "Pritam & Mohit Chauhan", "pritam")
        assertEquals(0, pick(e, song("x", "Tum Ho", "Mohit Chauhan"))?.index)
        assertNull(pick(e, song("x", "Tum Ho Paas", "Mohit Chauhan")))
    }

    @Test
    fun `the explicit version is preferred when the chart lists it`() {
        val e = entry("Song", "Artist", explicit = true)
        assertEquals(1, pick(e, song("c", "Song", "Artist"), song("e", "Song", "Artist", explicit = true))?.index)
    }

    @Test
    fun `unofficial uploads are never chosen, official videos are`() {
        val e = entry("Ask of You", "Raphael Saadiq", "raphael-saadiq")
        assertNull(pick(e, song("u", "Ask of You", "Raphael Saadiq", type = ugc)))
        assertEquals(0, pick(e, song("o", "Ask of You (Official HD Video)", "Raphael Saadiq", type = omv))?.index)
    }

    @Test
    fun `a confident match is found with one search, then remembered`() = runBlocking {
        val catalog = FakeCatalog(songs = mapOf("*" to listOf(song("v1", "Ashke", "Karan Aujla"))))
        val resolver = ChartResolver(catalog, io = Dispatchers.Unconfined)
        val e = entry("Ashke", "Karan Aujla & MXRCI", "karan-aujla")
        val first = resolver.song(e)
        assertTrue(first is Resolution.Found && first.item.id == "v1" && !first.alternative)
        assertEquals(listOf("song:Ashke Karan Aujla"), catalog.queries)
        resolver.song(e)
        assertEquals(1, catalog.queries.size)
        assertEquals("v1", resolver.knownSong(e)?.id)
    }

    @Test
    fun `a miss searches a bounded number of times and is not guessed`() = runBlocking {
        val catalog = FakeCatalog(songs = mapOf("*" to listOf(song("v", "Ashke (Lofi)", "Karan Aujla"))))
        val result = ChartResolver(catalog, io = Dispatchers.Unconfined).song(entry("Ashke", "Karan Aujla & MXRCI", "karan-aujla"))
        assertTrue(result is Resolution.NotFound)
        assertEquals(
            listOf("song:Ashke Karan Aujla", "song:Ashke Karan Aujla & MXRCI", "video:Ashke Karan Aujla"),
            catalog.queries,
        )
    }

    @Test
    fun `network failures are not remembered, so the next tap tries again`() = runBlocking {
        val catalog = FakeCatalog(songs = mapOf("*" to null), videos = mapOf("*" to null))
        val resolver = ChartResolver(catalog, io = Dispatchers.Unconfined)
        val e = entry("Ashke", "Karan Aujla", "karan-aujla")
        assertTrue(resolver.song(e) is Resolution.Failed)
        catalog.songs = mapOf("*" to listOf(song("v1", "Ashke", "Karan Aujla")))
        assertTrue(resolver.song(e) is Resolution.Found)
    }

    @Test
    fun `simultaneous taps on one song make one lookup`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val catalog = FakeCatalog(songs = mapOf("*" to listOf(song("v1", "Ashke", "Karan Aujla"))))
        catalog.gate = gate
        val resolver = ChartResolver(catalog, io = Dispatchers.Default)
        val e = entry("Ashke", "Karan Aujla", "karan-aujla")
        val calls = List(4) { async(Dispatchers.Default) { resolver.song(e) } }
        kotlinx.coroutines.delay(100)
        gate.complete(Unit)
        calls.awaitAll().forEach { assertTrue(it is Resolution.Found) }
        assertEquals(1, catalog.queries.size)
    }

    @Test
    fun `a music video found only as an official video plays`() = runBlocking {
        val catalog = FakeCatalog(
            songs = mapOf("*" to listOf(song("s", "Rikers Island", "Raphael Saadiq"))),
            videos = mapOf("*" to listOf(song("u", "Raphael Saadiq - Ask of you", "ladygirl99", type = ugc), song("o", "Ask of You", "Raphael Saadiq", type = omv))),
        )
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined).song(entry("Ask of You", "Raphael Saadiq", "raphael-saadiq"), ChartKind.Videos)
        assertTrue(r is Resolution.Found && r.item.id == "o")
        assertEquals(listOf("song:Ask of You Raphael Saadiq", "video:Ask of You Raphael Saadiq"), catalog.queries)
    }

    @Test
    fun `a live chart video plays the same performance when it exists`() = runBlocking {
        val e = entry("Housewife (Live from The Victoria's Secret Fashion Show 2026)", "Tate McRae", "tate-mcrae")
        val catalog = FakeCatalog(
            songs = mapOf("*" to listOf(song("studio", "Housewife", "Tate McRae"), song("other", "Housewife", "Brooke Alexx"))),
            videos = mapOf("*" to listOf(song("live", "Housewife (Live from The Victoria's Secret Fashion Show 2026)", "Tate McRae", type = omv))),
        )
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined).song(e, ChartKind.Videos)
        assertTrue(r is Resolution.Found && r.item.id == "live" && !r.alternative)
    }

    @Test
    fun `otherwise a live chart item falls back to the studio recording, and says so`() = runBlocking {
        val e = entry("Housewife (Live from The Victoria's Secret Fashion Show 2026)", "Tate McRae", "tate-mcrae")
        val catalog = FakeCatalog(
            songs = mapOf("*" to listOf(song("studio", "Housewife", "Tate McRae"))),
            videos = mapOf("*" to listOf(song("8d", "Tate McRae - Housewife (8D Audio)", "8D Lev", type = ugc))),
        )
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined).song(e, ChartKind.Videos)
        assertTrue(r is Resolution.Found && r.item.id == "studio" && r.alternative)
    }

    @Test
    fun `a studio chart song never plays a live take`() {
        assertNull(pick(entry("Housewife", "Tate McRae", "tate-mcrae"), song("live", "Housewife (Live)", "Tate McRae")))
    }

    @Test
    fun `a video named after an album offers that album instead of a guess`() = runBlocking {
        val record = Album("you seem pretty sad for a girl so in love", "MPREb_album")
        val catalog = FakeCatalog(
            songs = mapOf("*" to listOf(song("less", "less", "Olivia Rodrigo", album = record), song("honey", "honeybee", "Olivia Rodrigo", album = record))),
            videos = mapOf("*" to listOf(song("k", "Olivia Rodrigo - begged (Acoustic Karaoke)", "Sing King", type = ugc))),
        )
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined)
            .song(entry("you seem pretty sad for a girl so in love", "Olivia Rodrigo", "olivia-rodrigo"), ChartKind.Videos)
        assertTrue(r is Resolution.Ambiguous)
        r as Resolution.Ambiguous
        assertEquals(1, r.choices.size)
        assertTrue(r.choices.single().isAlbum)
        assertEquals("MPREb_album", r.choices.single().item.id)
    }

    @Test
    fun `a title that only partly matches the right artist is offered, never played`() = runBlocking {
        val catalog = FakeCatalog(
            songs = mapOf("*" to listOf(song("p", "petal", "Ariana Grande"), song("b", "better off", "Ariana Grande"))),
            videos = mapOf("*" to listOf(song("pv", "petal (official visualizer)", "Ariana Grande", type = omv))),
        )
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined)
            .song(entry("petal in the pavement", "Ariana Grande", "ariana-grande"), ChartKind.Videos)
        assertTrue(r is Resolution.Ambiguous)
        r as Resolution.Ambiguous
        assertEquals(listOf("petal"), r.choices.map { it.title }.distinct().take(1))
        assertFalse(r.choices.any { it.title == "better off" })
    }

    @Test
    fun `covers, karaoke and tutorials of a missing song leave nothing to choose`() = runBlocking {
        val catalog = FakeCatalog(
            songs = mapOf(
                "*" to listOf(
                    song("t", "Controlla (Tutorial)", "JustAcoustic"),
                    song("k", "Controlla (Originally Performed by Drake) [Instrumental]", "Singer's Edge Karaoke"),
                    song("r", "Controlla", "Raissia"),
                    song("p", "Princess", "Drake"),
                )
            ),
            videos = mapOf("*" to listOf(song("u", "Drake - Controlla", "Drake Media", type = ugc))),
        )
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined).song(entry("Controlla", "Drake", "drake"))
        assertTrue(r is Resolution.NotFound)
    }

    @Test
    fun `a various-artists soundtrack is found by its title and year`() = runBlocking {
        val catalog = FakeCatalog(
            albums = listOf(
                album("MPREb_bhat", "Dhurandhar Bhatawdekar (Original Motion Picture Soundtrack)", "Rishikesh Kamerkar", year = 2015),
                album("MPREb_dh", "Dhurandhar", "Shashwat Sachdev", year = 2025),
                album("MPREb_rev", "Dhurandhar The Revenge", "Shashwat Sachdev", year = 2026),
            )
        )
        val e = entry("Dhurandhar (Original Motion Picture Soundtrack)", "Various Artists", released = "2025-12-05")
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined).album(e)
        assertTrue(r is Resolution.Found && r.item.id == "MPREb_dh")
        assertEquals("album:Dhurandhar (Original Motion Picture Soundtrack)", catalog.queries.first())
    }

    @Test
    fun `a soundtrack whose title several releases share is offered, not guessed`() = runBlocking {
        val catalog = FakeCatalog(
            albums = listOf(
                album("a", "SAIYAARA", "Shreya Ghoshal", "Irshad Kamil", year = 2025),
                album("b", "SAIYAARA", "Sachet-Parampara", year = 2025),
                album("c", "SAIYAARA", "Arijit Singh", "Mithoon", year = 2025),
            )
        )
        val e = entry("Saiyaara (Extended Album) [Original Motion Picture Soundtrack]", "Various Artists", released = "2025-07-18")
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined).album(e)
        assertTrue(r is Resolution.Ambiguous)
        assertEquals(3, (r as Resolution.Ambiguous).choices.size)
    }

    @Test
    fun `a soundtrack credited mid-list to its composer is searched under that composer`() = runBlocking {
        val catalog = FakeCatalog(albums = listOf(album("MPREb_j2", "Jailer 2 (Original Motion Picture Soundtrack)", "Various Artists", year = 2026)))
        val e = entry(
            "Jailer 2 (Original Motion Picture Soundtrack)",
            "Super Subu, Ritesh G Rao, HEISENBERG, Anirudh Ravichander & Arunraja Kamaraj",
            "anirudh-ravichander",
            released = "2026-01-09",
        )
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined).album(e)
        assertTrue(r is Resolution.Found && r.item.id == "MPREb_j2")
        assertEquals("album:Jailer 2 Anirudh Ravichander", catalog.queries.first())
    }

    @Test
    fun `albums and artists resolve only on a confident name`() = runBlocking {
        val kaam = AlbumItem(browseId = "MPRE1", playlistId = "OLAK1", title = "Kaam Bhaari", artists = listOf(Artist("Karan Aujla", null)), thumbnail = "")
        val artist = ArtistItem(id = "UC1", title = "Karan Aujla", thumbnail = "t", shuffleEndpoint = null, radioEndpoint = null)
        val stranger = ArtistItem(id = "UC2", title = "Karan Aujla Fans", thumbnail = "t", shuffleEndpoint = null, radioEndpoint = null)
        val catalog = FakeCatalog(albums = listOf(kaam), artists = listOf(stranger, artist))
        val resolver = ChartResolver(catalog, io = Dispatchers.Unconfined)
        val found = resolver.album(entry("Kaam Bhaari - EP", "Karan Aujla", "karan-aujla"))
        assertTrue(found is Resolution.Found && found.item.id == "MPRE1")
        assertTrue(resolver.album(entry("Something Else", "Karan Aujla")) is Resolution.NotFound)
        val chartArtist = ChartArtist("apple:1", "Karan Aujla", "1", null, listOf(entry("Ashke", "Karan Aujla")), 0)
        val a = resolver.artist(chartArtist)
        assertTrue(a is Resolution.Found && a.item.id == "UC1")
        assertEquals("UC1", resolver.artists.value["apple:1"]?.id)
    }

    @Test
    fun `a stylised artist name finds its plain-spelled page`() = runBlocking {
        val krsna = ArtistItem(id = "UCk", title = "Krsna", thumbnail = "t", shuffleEndpoint = null, radioEndpoint = null)
        val other = ArtistItem(id = "UCd", title = "Dhanda Nyoliwala", thumbnail = "t", shuffleEndpoint = null, radioEndpoint = null)
        val catalog = FakeCatalog(artists = listOf(krsna, other))
        val r = ChartResolver(catalog, io = Dispatchers.Unconfined)
            .artist(ChartArtist("apple:1462405977", "KR\$NA", "1462405977", null, listOf(entry("Song", "KR\$NA")), 0))
        assertTrue(r is Resolution.Found && r.item.id == "UCk")
        assertEquals(listOf("artist:KR\$NA"), catalog.queries)
    }

    @Test
    fun `resolved artists survive a restart through the store`() = runBlocking {
        val saved = HashMap<String, StoredArtist>()
        val store = object : ArtistStore {
            override fun read() = HashMap(saved)
            override fun write(artists: Map<String, StoredArtist>) {
                saved.clear()
                saved.putAll(artists)
            }
        }
        val artist = ArtistItem(id = "UC1", title = "Karan Aujla", thumbnail = "t", shuffleEndpoint = null, radioEndpoint = null)
        val chartArtist = ChartArtist("apple:1", "Karan Aujla", "1", null, listOf(entry("Ashke", "Karan Aujla")), 0)
        ChartResolver(FakeCatalog(artists = listOf(artist)), store, clock = { 1_000 }, io = Dispatchers.Unconfined).artist(chartArtist)
        kotlinx.coroutines.yield()
        assertEquals("UC1", saved["apple:1"]?.id)
        val catalog = FakeCatalog(artists = null)
        val restarted = ChartResolver(catalog, store, clock = { 2_000 }, io = Dispatchers.Unconfined)
        val again = restarted.artist(chartArtist)
        assertTrue(again is Resolution.Found && again.item.id == "UC1")
        assertTrue(catalog.queries.isEmpty())
    }

    @Test
    fun `each outcome maps to what the listener sees`() {
        val s = song("v", "Song", "A")
        assertNull(noticeFor(Resolution.Found(s), "Song", 1))
        assertEquals(DiscoverNotice.Kind.Alternative, noticeFor(Resolution.Found(s, alternative = true), "Song", 1)?.kind)
        val choice = ChartChoice(s, "Song", "A", null, isAlbum = false)
        val choices = noticeFor(Resolution.Ambiguous("Song A", listOf(choice)), "Song", 2)
        assertEquals(DiscoverNotice.Kind.Choices, choices?.kind)
        assertEquals(listOf(choice), choices?.choices)
        val miss = noticeFor(Resolution.NotFound("Song A"), "Song", 3)
        assertEquals(DiscoverNotice.Kind.NoMatch, miss?.kind)
        assertEquals("Song A", miss?.query)
        assertEquals(DiscoverNotice.Kind.Unreachable, noticeFor(Resolution.Failed("Song A"), "Song", 4)?.kind)
    }

    @Test
    fun `a confident match hands the player its song first, with the rest of the chart to follow`() {
        val first = song("v0", "Zero", "A")
        val queue = AppleChartQueue("Top 100: India", first, listOf(ChartEntry(2, "e2", "Two", "A"))) { Resolution.NotFound(it.title) }
        assertEquals("v0", queue.preloadItem.id)
        assertTrue(queue.hasNextPage())
        assertTrue(queue.pagesAreSource)
    }

    @Test
    fun `the chart queue pages on from the tapped song, skipping misses and repeats`() = runBlocking {
        val first = song("v0", "Zero", "A")
        val rest = (1..8).map { ChartEntry(it, "e$it", "S$it", "A") }
        val resolve: suspend (ChartEntry) -> Resolution<SongItem> = { e ->
            when (e.position) {
                2 -> Resolution.NotFound("S2")
                3 -> Resolution.Found(first)
                else -> Resolution.Found(song("v${e.position}", e.title, "A"))
            }
        }
        val pager = ChartPager(first, rest, resolve)
        assertTrue(pager.hasMore)
        assertEquals(listOf("v1", "v4", "v5", "v6"), pager.next().map { it.id })
        assertEquals(listOf("v7", "v8"), pager.next().map { it.id })
        assertTrue(!pager.hasMore)
        assertTrue(pager.next().isEmpty())
    }

    @Test
    fun `a page that could not be looked up at all is retried, not skipped`() = runBlocking {
        var offline = true
        val rest = (1..3).map { ChartEntry(it, "e$it", "S$it", "A") }
        val pager = ChartPager(song("v0", "Zero", "A"), rest) { e ->
            if (offline) Resolution.Failed(e.title) else Resolution.Found(song("v${e.position}", e.title, "A"))
        }
        try {
            pager.next()
            throw AssertionError("expected a failure")
        } catch (_: IOException) {
        }
        offline = false
        assertEquals(listOf("v1", "v2", "v3"), pager.next().map { it.id })
    }
}
