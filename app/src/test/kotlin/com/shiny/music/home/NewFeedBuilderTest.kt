package com.shiny.music.home

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.ArtistEntity
import com.shiny.music.db.entities.HomeAlbumStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeLibraryCounts
import com.shiny.music.db.entities.HomeSongStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import com.music.innertube.models.Artist as YTArtist

/**
 * New for every kind of listener.
 *
 * The point of these is the promise the page makes: that a section headed "From artists you
 * play" is built from plays that exist, that an album already heard never appears, and that a
 * listener Shiny knows nothing about is told nothing personal rather than something invented.
 */
class NewFeedBuilderTest {

    private val now = LocalDateTime.of(2026, 9, 21, 19, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val thisYear = 2026

    // ---- fixture ---------------------------------------------------------------------------

    private fun ctx(
        seed: Long = 3,
        online: Boolean = true,
        hideExplicit: Boolean = false,
        chartScopes: List<String>? = null,
    ) = HomeContext(
        nowMs = now,
        daypart = Daypart.Evening,
        online = online,
        seed = seed,
        hideExplicit = hideExplicit,
        chartScopes = chartScopes,
    )

    private fun album(
        id: String,
        artistId: String?,
        artistName: String = "Name $artistId",
        year: Int? = thisYear,
        explicit: Boolean = false,
    ) = AlbumItem(
        browseId = id,
        playlistId = "PL$id",
        title = "Album $id",
        artists = listOf(YTArtist(name = artistName, id = artistId)),
        year = year,
        thumbnail = "https://img.test/$id.jpg",
        explicit = explicit,
    )

    private fun artistItem(id: String) = ArtistItem(
        id = id,
        title = "Name $id",
        thumbnail = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    /**
     * A listener who plays [plays], keyed by artist id, most-played first. Enough counted
     * events go in the counts that `HomeFeedBuilder.profile` calls them Established.
     */
    private fun signals(
        plays: Map<String, Int>,
        heardAlbums: List<String> = emptyList(),
    ): HomeSignals {
        val artistStats = plays.entries.mapIndexed { index, (artistId, count) ->
            HomeArtistStat(
                artistId = artistId,
                plays = count,
                weekPlays = count / 3,
                monthPlays = count,
                lastPlayed = now - index * HOME_DAY_MS,
                songs = 4,
            )
        }
        // One song stat per artist, so the profile has distinct songs to count.
        val songStats = plays.entries.associate { (artistId, count) ->
            "song_$artistId" to HomeSongStat(
                songId = "song_$artistId",
                plays = count,
                playTime = count * 180_000L,
                firstPlayed = now - 30 * HOME_DAY_MS,
                lastPlayed = now,
                weekPlays = count / 3,
                weekPlayTime = 0,
                monthPlays = count,
                daypartPlays = 0,
            )
        }
        return HomeSignals.Empty.copy(
            counts = HomeLibraryCounts(
                localSongs = 0,
                downloadedSongs = 0,
                likedSongs = 0,
                librarySongs = plays.size,
                events = plays.values.sum(),
                playlists = 0,
            ),
            songStats = songStats,
            artistStats = artistStats,
            albumStats = heardAlbums.map { HomeAlbumStat(it, 5, 10, now) },
            artists = plays.keys.associateWith { id ->
                Artist(ArtistEntity(id = id, name = "Name $id"), 0)
            },
        )
    }

    private fun remote(
        releases: List<AlbumItem>,
        digests: Map<String, HomeArtistDigest> = emptyMap(),
        charts: List<HomeChart> = emptyList(),
    ) = HomeRemote(
        exploreAt = 5_000,
        newReleases = releases,
        chartsAt = 1_000,
        charts = charts,
    ).copy(artists = digests)

    private fun digest(artistId: String, latest: AlbumItem? = null, related: List<ArtistItem> = emptyList()) =
        HomeArtistDigest(
            artistId = artistId,
            name = "Name $artistId",
            thumbnail = null,
            fetchedAt = now,
            latest = latest,
            related = related,
        )

    private fun chart() = HomeChart(
        playlistId = "PLin",
        title = "Top 100 Music Videos India",
        scope = "IN",
        songs = (1..20).map {
            SongItem(
                id = "c$it",
                title = "Chart $it",
                artists = listOf(YTArtist("Chart artist $it", null)),
                thumbnail = "t",
                explicit = it == 1,
            )
        },
    )

    private inline fun <reified T : NewSection> NewFeed.find(): T? = sections.filterIsInstance<T>().firstOrNull()

    private fun NewFeed.kinds() = sections.map { it::class.simpleName }

    /** Every album id the page shows, in order, however it shows it. */
    private fun everyAlbumId(feed: NewFeed): List<String> = feed.sections.flatMap { section ->
        when (section) {
            is FeaturedSection -> listOf(section.release.album.id)
            is YourArtistsSection -> section.releases.map { it.album.id }
            is AdjacentSection -> section.releases.map { it.album.id }
            is FreshSection -> section.albums.map { it.id }
            is NewChartsSection -> emptyList()
        }
    }

    // ---- a listener Shiny knows nothing about ------------------------------------------------

    @Test
    fun `no history gives no personal sections`() {
        val feed = NewFeedBuilder.build(
            HomeSignals.Empty,
            remote((1..20).map { album("r$it", artistId = "stranger$it") }),
            ctx(),
        )

        assertNull("a listener with no plays is told nothing personal", feed.find<YourArtistsSection>())
        assertNull(feed.find<AdjacentSection>())
        assertNotNull("but they still get the catalogue", feed.find<FreshSection>())
        assertEquals(ListenerProfile.New, feed.profile)
    }

    @Test
    fun `a page with nothing personal on it opens the broad shelf wider`() {
        val strangers = (1..30).map { album("r$it", artistId = "stranger$it") }
        val cold = NewFeedBuilder.build(HomeSignals.Empty, remote(strangers), ctx())
        // One release by an artist they play is enough to make the page personal.
        val warm = NewFeedBuilder.build(
            signals(mapOf("a1" to 40)),
            remote(strangers + album("mine", artistId = "a1")),
            ctx(),
        )

        assertEquals(NewFeedBuilder.MAX_FRESH_UNKNOWN, cold.find<FreshSection>()!!.albums.size)
        assertEquals(NewFeedBuilder.MAX_FRESH_PERSONAL, warm.find<FreshSection>()!!.albums.size)
        assertEquals("mine", warm.find<FeaturedSection>()!!.release.album.id)
    }

    @Test
    fun `no cached block at all yields an empty feed rather than a fabricated one`() {
        val feed = NewFeedBuilder.build(signals(mapOf("a1" to 40)), null, ctx())
        assertTrue(feed.isEmpty)
        assertTrue(feed.sections.isEmpty())
    }

    // ---- a listener with a history -----------------------------------------------------------

    @Test
    fun `releases by artists the listener plays are separated out and ordered by plays`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 50, "a2" to 12, "a3" to 3)),
            remote(
                listOf(
                    album("r_stranger", artistId = "nobody"),
                    album("r3", artistId = "a3"),
                    album("r1", artistId = "a1"),
                    album("r2", artistId = "a2"),
                )
            ),
            ctx(),
        )

        val featured = feed.find<FeaturedSection>()!!.release
        assertEquals("the most-played artist leads", "r1", featured.album.id)
        assertEquals(NewReason.Plays("Name a1", 50), featured.reason)

        val yours = feed.find<YourArtistsSection>()!!.releases
        assertEquals(listOf("r2", "r3"), yours.map { it.album.id })
        assertTrue(yours.all { it.reason is NewReason.Plays })

        // The stranger is never called personal, and never repeated from a section above.
        val fresh = feed.find<FreshSection>()!!.albums.map { it.id }
        assertEquals(listOf("r_stranger"), fresh)
    }

    @Test
    fun `the feature is not simply the first release`() {
        // The listener's artist is last in YouTube's order; affinity still has to win.
        val releases = (1..10).map { album("r$it", artistId = "stranger$it") } + album("mine", artistId = "a1")
        val feed = NewFeedBuilder.build(signals(mapOf("a1" to 30)), remote(releases), ctx())
        assertEquals("mine", feed.find<FeaturedSection>()!!.release.album.id)
    }

    @Test
    fun `prominence still orders a page with no affinity to go on`() {
        val head = NewFeedBuilder.featureScore(rank = 0, total = 20, reason = NewReason.Fresh, thisYear = false)
        val tail = NewFeedBuilder.featureScore(rank = 19, total = 20, reason = NewReason.Fresh, thisYear = false)
        assertTrue(head > tail)
    }

    @Test
    fun `affinity outweighs both prominence and freshness`() {
        val played = NewFeedBuilder.featureScore(rank = 19, total = 20, reason = NewReason.Plays("a", 30), thisYear = false)
        val promoted = NewFeedBuilder.featureScore(rank = 0, total = 20, reason = NewReason.Fresh, thisYear = true)
        assertTrue(played > promoted)
    }

    @Test
    fun `the feature changes with the daypart seed without leaving the top of the ranking`() {
        // Four releases by four artists the listener plays about equally.
        val releases = (1..4).map { album("r$it", artistId = "a$it") }
        val sig = signals(mapOf("a1" to 20, "a2" to 20, "a3" to 20, "a4" to 20))
        val chosen = (0L until 12L).map { seed ->
            NewFeedBuilder.build(sig, remote(releases), ctx(seed = seed)).find<FeaturedSection>()!!.release.album.id
        }
        assertTrue("the lead should not be the same album every daypart", chosen.distinct().size > 1)
        assertTrue("and never from outside the pool", chosen.all { it in setOf("r1", "r2", "r3", "r4") })
    }

    // ---- adjacency ---------------------------------------------------------------------------

    @Test
    fun `artists listed beside the listener's, whom they never play, become the adjacent shelf`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40)),
            remote(
                releases = listOf(
                    album("r_mine", artistId = "a1"),
                    album("r_near", artistId = "near1"),
                    album("r_far", artistId = "nobody"),
                ),
                digests = mapOf("a1" to digest("a1", related = listOf(artistItem("near1")))),
            ),
            ctx(),
        )

        val adjacent = feed.find<AdjacentSection>()!!.releases
        assertEquals(listOf("r_near"), adjacent.map { it.album.id })
        assertEquals(NewReason.Like("Name near1", "Name a1"), adjacent.single().reason)
        assertEquals(listOf("r_far"), feed.find<FreshSection>()!!.albums.map { it.id })
    }

    @Test
    fun `an artist the listener already plays is never called adjacent`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40, "a2" to 20)),
            remote(
                releases = listOf(album("r2", artistId = "a2")),
                // a1's page lists a2 as similar, but the listener plays a2 already.
                digests = mapOf("a1" to digest("a1", related = listOf(artistItem("a2")))),
            ),
            ctx(),
        )
        assertNull(feed.find<AdjacentSection>())
        assertEquals(NewReason.Plays("Name a2", 20), feed.find<FeaturedSection>()!!.release.reason)
    }

    // ---- honesty rules -----------------------------------------------------------------------

    @Test
    fun `an album the listener has already heard never appears`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40), heardAlbums = listOf("r1")),
            remote(listOf(album("r1", artistId = "a1"), album("r2", artistId = "a1"))),
            ctx(),
        )
        val everyId = everyAlbumId(feed)
        assertFalse("r1" in everyId)
        assertTrue("r2" in everyId)
    }

    @Test
    fun `hiding explicit content hides it here too`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40)),
            remote(listOf(album("clean", artistId = "a1"), album("rude", artistId = "a1", explicit = true))),
            ctx(hideExplicit = true),
        )
        assertEquals("clean", feed.find<FeaturedSection>()!!.release.album.id)
        assertNull(feed.find<YourArtistsSection>())
    }

    @Test
    fun `an artist's own newest release reaches them even when it never made the global shelf`() {
        val own = album("own", artistId = "a1")
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40)),
            remote(
                releases = listOf(album("global", artistId = "nobody")),
                digests = mapOf("a1" to digest("a1", latest = own)),
            ),
            ctx(),
        )
        assertEquals("own", feed.find<FeaturedSection>()!!.release.album.id)
    }

    @Test
    fun `an artist's newest release from an earlier year is not called new`() {
        val old = album("old", artistId = "a1", year = thisYear - 3)
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40)),
            remote(
                releases = listOf(album("global", artistId = "nobody")),
                digests = mapOf("a1" to digest("a1", latest = old)),
            ),
            ctx(),
        )
        assertEquals("global", feed.find<FeaturedSection>()!!.release.album.id)
        assertTrue("a stale release is not new anywhere on the page", "old" !in everyAlbumId(feed))
    }

    @Test
    fun `nothing is listed twice across the page`() {
        val releases = (1..24).map { album("r$it", artistId = if (it <= 6) "a$it" else "stranger$it") }
        val feed = NewFeedBuilder.build(
            signals((1..6).associate { "a$it" to (30 - it * 2) }),
            remote(releases),
            ctx(),
        )
        val everyId = everyAlbumId(feed)
        assertEquals(everyId.size, everyId.distinct().size)
    }

    // ---- charts ------------------------------------------------------------------------------

    @Test
    fun `charts come from the same cached block and honour the same setting`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40)),
            remote(listOf(album("r1", artistId = "a1")), charts = listOf(chart())),
            ctx(chartScopes = listOf("IN")),
        )
        val charts = feed.find<NewChartsSection>()!!
        assertEquals(listOf("IN"), charts.charts.map { it.scope })
        assertEquals(1, charts.charts.single().songs.first().chartPosition)
    }

    @Test
    fun `a chart scope the listener did not ask for is not shown`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40)),
            remote(listOf(album("r1", artistId = "a1")), charts = listOf(chart())),
            ctx(chartScopes = listOf("ZZ")),
        )
        assertNull(feed.find<NewChartsSection>())
    }

    @Test
    fun `filtering a chart keeps every survivor's true rank`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40)),
            remote(listOf(album("r1", artistId = "a1")), charts = listOf(chart())),
            ctx(hideExplicit = true, chartScopes = listOf("IN")),
        )
        val songs = feed.find<NewChartsSection>()!!.charts.single().songs
        assertEquals("the explicit song at rank 1 is gone", "c2", songs.first().id)
        assertEquals("and rank 2 is still rank 2", 2, songs.first().chartPosition)
    }

    // ---- shape -------------------------------------------------------------------------------

    @Test
    fun `the page runs from nearest to furthest`() {
        val feed = NewFeedBuilder.build(
            signals(mapOf("a1" to 40, "a2" to 20)),
            remote(
                releases = listOf(
                    album("r1", artistId = "a1"),
                    album("r2", artistId = "a2"),
                    album("r_near", artistId = "near1"),
                    album("r_far", artistId = "nobody"),
                ),
                digests = mapOf("a1" to digest("a1", related = listOf(artistItem("near1")))),
                charts = listOf(chart()),
            ),
            ctx(chartScopes = listOf("IN")),
        )
        assertEquals(
            listOf("FeaturedSection", "YourArtistsSection", "AdjacentSection", "FreshSection", "NewChartsSection"),
            feed.kinds(),
        )
    }
}
