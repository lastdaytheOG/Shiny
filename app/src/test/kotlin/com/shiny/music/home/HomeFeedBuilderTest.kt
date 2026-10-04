package com.shiny.music.home

import com.music.innertube.models.Artist as YTArtist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.AlbumEntity
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.ArtistEntity
import com.shiny.music.db.entities.HomeAlbumStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeLibraryCounts
import com.shiny.music.db.entities.HomeRelatedLink
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.PlaylistEntity
import com.shiny.music.db.entities.Song
import com.shiny.music.db.entities.SongEntity
import com.shiny.music.db.entities.SpeedDialItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Home for every kind of listener, built from an in-memory history that is aggregated the
 * way the Room queries aggregate it (see `DatabaseDao.homeSongStats` and friends).
 */
class HomeFeedBuilderTest {

    private val day = HOME_DAY_MS
    private val hour = 3_600_000L

    private fun wall(h: Int, m: Int = 30) = LocalDateTime.of(2026, 9, 18, h, m).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val evening = wall(19)
    private val morning = wall(8)
    private val afternoon = wall(14)

    // ---- fixture ---------------------------------------------------------------------------

    private fun song(
        id: String,
        artist: String = "artist_$id",
        local: Boolean = false,
        downloaded: Boolean = false,
        video: Boolean = false,
        explicit: Boolean = false,
        album: String? = null,
        addedAt: Long? = null,
        duration: Int = 200,
    ) = Song(
        song = SongEntity(
            id = id,
            title = "Song $id",
            duration = duration,
            thumbnailUrl = "https://img.test/$id.jpg",
            albumId = album,
            explicit = explicit,
            isLocal = local,
            isDownloaded = downloaded,
            dateDownload = if (downloaded) addedAt?.let(::toLdt) else null,
            inLibrary = if (!downloaded && !local) addedAt?.let(::toLdt) else null,
            dateModified = if (local) addedAt?.let(::toLdt) else null,
            isVideo = video,
        ),
        artists = listOf(ArtistEntity(id = artist, name = "Name $artist", isLocal = local)),
    )

    private fun toLdt(ms: Long) = LocalDateTime.ofEpochSecond(ms / 1000, 0, ZoneOffset.UTC)

    private data class Play(val songId: String, val at: Long, val playTime: Long = 180_000)

    private fun ctx(
        now: Long = evening,
        online: Boolean = true,
        seed: Long = 3,
        hideVideo: Boolean = false,
        hideExplicit: Boolean = false,
        youtubeSignedIn: Boolean = false,
    ) = HomeContext(
        nowMs = now,
        daypart = Daypart.of(((now / hour) % 24).toInt()),
        online = online,
        seed = seed,
        hideExplicit = hideExplicit,
        hideVideoSongs = hideVideo,
        youtubeSignedIn = youtubeSignedIn,
    )

    /** What `HomeSignalsLoader` would read for this library and history. */
    private fun signals(
        library: List<Song>,
        plays: List<Play>,
        ctx: HomeContext,
        related: List<HomeRelatedLink> = emptyList(),
        albums: List<AlbumEntity> = emptyList(),
    ): HomeSignals {
        val now = ctx.nowMs
        val weekFrom = now - 7 * day
        val monthFrom = now - 30 * day
        val daypartFrom = now - 60 * day
        val byId = library.associateBy { it.id }
        fun inDaypart(p: Play) = p.at > daypartFrom && ((p.at / hour) % 24).toInt() in ctx.daypart.hours

        val stats = plays.groupBy { it.songId }.map { (id, ps) ->
            HomeSongStat(
                songId = id,
                plays = ps.size,
                playTime = ps.sumOf { it.playTime },
                firstPlayed = ps.minOf { it.at },
                lastPlayed = ps.maxOf { it.at },
                weekPlays = ps.count { it.at > weekFrom },
                weekPlayTime = ps.filter { it.at > weekFrom }.sumOf { it.playTime },
                monthPlays = ps.count { it.at > monthFrom },
                daypartPlays = ps.count(::inDaypart),
            )
        }
        val artistStats = plays.groupBy { byId.getValue(it.songId).artists.first().id }.map { (artistId, ps) ->
            HomeArtistStat(
                artistId = artistId,
                plays = ps.size,
                weekPlays = ps.count { it.at > weekFrom },
                monthPlays = ps.count { it.at > monthFrom },
                lastPlayed = ps.maxOf { it.at },
                songs = ps.map { it.songId }.distinct().size,
            )
        }.sortedByDescending { it.lastPlayed }
        val albumStats = plays.filter { byId.getValue(it.songId).song.albumId != null }
            .groupBy { byId.getValue(it.songId).song.albumId!! }
            .map { (albumId, ps) -> HomeAlbumStat(albumId, ps.map { it.songId }.distinct().size, ps.size, ps.maxOf { it.at }) }
            .sortedByDescending { it.lastPlayed }
        val played = stats.map { it.songId }.toSet()
        val seeds = HomeRanking.seedWeights(stats)
        val discovery = HomeRanking.discovery(related.filter { it.seedId in seeds && it.songId !in played }, seeds)

        return HomeSignals(
            counts = HomeLibraryCounts(
                localSongs = library.count { it.song.isLocal },
                downloadedSongs = library.count { it.song.isDownloaded },
                likedSongs = 0,
                librarySongs = library.count { it.song.inLibrary != null },
                events = plays.size,
                playlists = 0,
            ),
            songStats = stats.associateBy { it.songId },
            artistStats = artistStats,
            albumStats = albumStats,
            daypartDays = plays.filter(::inDaypart).map { it.at / day }.distinct().size,
            monthOfflinePlays = plays.count { p ->
                p.at > monthFrom && byId.getValue(p.songId).song.let { it.isLocal || it.isDownloaded }
            },
            rotationIds = HomeRanking.rotation(stats, now),
            daypartIds = HomeRanking.daypart(stats),
            rediscoverIds = HomeRanking.rediscover(stats, now),
            recentIds = HomeRanking.recent(stats),
            discovery = discovery,
            songs = byId,
            artists = library.flatMap { it.artists }.distinctBy { it.id }.associate { it.id to Artist(it, 0) },
            albums = albums.associate { it.id to Album(it) },
            recentlyAdded = library.filter { HomeFeedBuilder.addedAt(it) != null }
                .sortedByDescending { HomeFeedBuilder.addedAt(it) },
            offlineSongs = library.filter { it.song.isLocal || it.song.isDownloaded },
            unexplored = library.filter { it.id !in played }.take(40),
            playlists = emptyList(),
            pinned = emptyList(),
        )
    }

    /**
     * A listener of two months: 40 songs by 14 artists, a handful played daily in the
     * evening, others through the day, a few old favourites untouched for months.
     */
    private fun establishedLibrary(local: Boolean = false, downloadedShare: Int = 0): Pair<List<Song>, List<Play>> {
        val library = (0 until 40).map { i ->
            song(
                id = "s$i",
                artist = "a${i % 14}",
                local = local,
                downloaded = !local && i < downloadedShare,
                album = "al${i % 8}",
            )
        }
        val plays = buildList {
            // Evening regulars: s0..s11, most days of the last month.
            for (d in 0 until 30) for (i in 0 until 12) if ((d + i) % 3 != 0) add(Play("s$i", evening - d * day - (i % 3) * hour))
            // Daytime songs: s12..s27, a few plays each.
            for (d in 0 until 20 step 2) for (i in 12 until 28) if ((d + i) % 4 == 0) add(Play("s$i", afternoon - d * day))
            // Old favourites: s28..s33, played a lot four months ago.
            for (i in 28 until 34) repeat(6) { add(Play("s$i", evening - (120 + it) * day)) }
        }
        return library to plays
    }

    private fun charts() = HomeRemote(
        chartsAt = 1_000,
        charts = listOf(
            HomeChart(
                "PLin", "Top 100 Music Videos India", "IN",
                (1..20).map { SongItem(id = "c$it", title = "Chart $it", artists = listOf(YTArtist("Chart artist $it", null)), thumbnail = "t", explicit = it == 1) },
            ),
        ),
    )

    private fun related(): List<HomeRelatedLink> =
        (0 until 12).flatMap { seed -> (0 until 5).map { HomeRelatedLink("s$seed", "n${(seed * 3 + it) % 30}") } }

    /** The same graph `MusicService` fills on first play, for a library of any naming. */
    private fun relatedTo(seedIds: List<String>): List<HomeRelatedLink> =
        seedIds.flatMapIndexed { i, seed -> (0 until 5).map { HomeRelatedLink(seed, "n${(i * 3 + it) % 30}") } }

    private fun relatedSongs() = (0 until 30).map { song("n$it", artist = "new_artist_${it % 10}") }

    private inline fun <reified T : HomeSection> HomeFeed.find(): T? = sections.filterIsInstance<T>().firstOrNull()

    private fun HomeFeed.kinds() = sections.map { it::class.simpleName }

    // ---- first use -------------------------------------------------------------------------

    @Test
    fun `a listener with nothing at all opens on real music, not on a wall`() {
        val c = ctx()
        val feed = HomeFeedBuilder.build(HomeSignals.Empty, charts(), c)

        assertEquals(ListenerProfile.New, feed.profile)
        val start = feed.sections.first() as StartHereSection
        assertNotNull("the opening is the chart, not an empty state", start.chart)
        assertEquals("IN", start.chart!!.scope)
        // Nothing personal is invented from a history that does not exist.
        listOf(HeroMixSection::class, RotationSection::class, DiscoverSection::class, InsightsSection::class, RediscoverSection::class)
            .forEach { kind -> assertTrue("$kind must not appear", feed.sections.none { kind.isInstance(it) }) }
        assertNotNull("charts are real data, and welcome a new listener", feed.find<ChartsSection>())
    }

    @Test
    fun `a listener with nothing at all and no network gets the honest empty state`() {
        val feed = HomeFeedBuilder.build(HomeSignals.Empty, charts(), ctx(online = false))
        assertEquals(listOf("OfflineNoticeSection", "StartHereSection"), feed.kinds())
        assertNull("no chart can be offered offline", feed.find<StartHereSection>()!!.chart)
    }

    @Test
    fun `files on the device are a mix from the first launch, with no plays at all`() {
        val library = (0 until 30).map { song("f$it", local = true, addedAt = evening - it * hour) }
        val c = ctx()
        val feed = HomeFeedBuilder.build(signals(library, emptyList(), c), null, c)

        // The old Home refused a mix without twelve plays and showed a welcome instead.
        val hero = feed.sections.first() as HeroMixSection
        assertTrue("a mix of their own files", hero.songs.isNotEmpty())
        assertTrue("all of it is theirs; nothing was invented", hero.fresh == 0)
        assertTrue("and it says so", hero.tentative)
        val offline = feed.find<ReadyOfflineSection>()
        assertNotNull(offline)
        assertEquals(30, offline!!.localSongs)
        assertNotNull(feed.find<RecentlyAddedSection>())
        assertNotNull("never-played files feed Surprise Me", feed.find<SurpriseSection>())
    }

    // ---- pins --------------------------------------------------------------------------------

    private fun libraryPlaylist(id: String, songs: Int) = Playlist(
        playlist = PlaylistEntity(id = id, name = "Playlist $id"),
        songCount = songs,
        songThumbnails = emptyList(),
    )

    private fun pin(id: String) = SpeedDialItem(id = id, title = "Pin $id", type = "PLAYLIST")

    @Test
    fun `a pinned library playlist is one tile that opens its songs, not a second empty one`() {
        val liked = libraryPlaylist("SPOTIFY_LIKED_SONGS", 120)
        val other = libraryPlaylist("LPother", 8)
        val sig = HomeSignals.Empty.copy(
            pinned = listOf(pin(liked.id)),
            pinnedPlaylists = mapOf(liked.id to liked),
            playlists = listOf(liked, other),
        )
        listOf(true, false).forEach { online ->
            val tiles = HomeFeedBuilder.build(sig, null, ctx(online = online)).find<LatelySection>()!!.tiles
            // Opened as a YouTube playlist, a library id is an empty page: never a PinnedTile.
            assertTrue(tiles.none { it is PinnedTile })
            val forLiked = tiles.filterIsInstance<PlaylistTile>().filter { it.playlist.id == liked.id }
            assertEquals("exactly one tile for the pinned playlist (online=$online)", 1, forLiked.size)
            assertTrue("and it says it is pinned", forLiked.single().pinned)
            assertFalse(tiles.filterIsInstance<PlaylistTile>().single { it.playlist.id == other.id }.pinned)
        }
    }

    @Test
    fun `a pinned YouTube playlist stays a pin, and only while online`() {
        val sig = HomeSignals.Empty.copy(
            pinned = listOf(pin("PLyoutube")),
            playlists = listOf(libraryPlaylist("LPa", 3), libraryPlaylist("LPb", 4)),
        )
        val online = HomeFeedBuilder.build(sig, null, ctx()).find<LatelySection>()!!.tiles
        assertEquals(1, online.count { it is PinnedTile })
        val offline = HomeFeedBuilder.build(sig, null, ctx(online = false)).find<LatelySection>()?.tiles.orEmpty()
        assertTrue(offline.none { it is PinnedTile })
    }

    // ---- Spotify and YouTube Music: a row each ------------------------------------------------

    private fun mix(id: String, songs: Int) =
        HomeSpotifyMix(spotifyId = id, name = "Daily Mix $id", localPlaylistId = "SPOTIFY_MIX_$id", songCount = songs, syncedAt = 1)

    private fun youtubeMix(id: String) = PlaylistItem(
        id = id, title = "My Mix $id", author = null, songCountText = null, thumbnail = "t",
        playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null,
    )

    private fun spotifyLiked(songs: Int) = Playlist(
        playlist = PlaylistEntity(id = "SPOTIFY_LIKED_SONGS", name = "Spotify Liked Songs"),
        songCount = songs,
        songThumbnails = listOf("a", "b", "c", "d", "e"),
    )

    private fun likedCounts(liked: Int) = HomeLibraryCounts(0, 0, liked, 0, 0, 0)

    @Test
    fun `someone arriving from Spotify sees their mixes near the top of a new Home`() {
        val c = ctx()
        val feed = HomeFeedBuilder.build(HomeSignals.Empty, charts(), c, spotifyMixes = listOf(mix("1", 50), mix("2", 3)))

        val section = feed.find<SpotifySection>()
        assertNotNull(section)
        assertEquals("a mix still matching (or nearly empty) is not shown", listOf("1"), section!!.mixes.map { it.spotifyId })
        val kinds = feed.kinds()
        assertTrue("their own mixes beat the charts while Shiny knows nothing", kinds.indexOf("SpotifySection") < kinds.indexOf("ChartsSection"))
    }

    @Test
    fun `Spotify mixes need the network and never appear without one`() {
        val feed = HomeFeedBuilder.build(HomeSignals.Empty, charts(), ctx(online = false), spotifyMixes = listOf(mix("1", 50)))
        assertNull(feed.find<SpotifySection>())
    }

    @Test
    fun `imported Spotify Liked Songs lead the Spotify row and leave Lately`() {
        val sig = HomeSignals.Empty.copy(
            spotifyLiked = spotifyLiked(206),
            playlists = listOf(spotifyLiked(206), libraryPlaylist("LPa", 3), libraryPlaylist("LPb", 4)),
        )
        val feed = HomeFeedBuilder.build(sig, charts(), ctx(), spotifyMixes = listOf(mix("1", 50)))

        val spotify = feed.find<SpotifySection>()!!
        assertEquals(206, spotify.liked!!.count)
        assertEquals("four covers for the mosaic", listOf("a", "b", "c", "d"), spotify.liked!!.covers)
        val lately = feed.find<LatelySection>()?.tiles.orEmpty()
        assertTrue("not a second time in Lately", lately.none { it.key == "playlist_SPOTIFY_LIKED_SONGS" })
    }

    @Test
    fun `YouTube Music gets its own row only while signed in`() {
        val sig = HomeSignals.Empty.copy(counts = likedCounts(40), likedCovers = listOf("y1", "y2"))
        val remote = charts().copy(youtubeMixes = listOf(youtubeMix("RDTMAK5uy_1")))

        assertNull(HomeFeedBuilder.build(sig, remote, ctx()).find<YouTubeSection>())
        val youtube = HomeFeedBuilder.build(sig, remote, ctx(youtubeSignedIn = true)).find<YouTubeSection>()!!
        assertEquals(40, youtube.liked!!.count)
        assertEquals(listOf("RDTMAK5uy_1"), youtube.mixes.map { it.id })
    }

    @Test
    fun `with both connected Spotify and YouTube Music never share a row`() {
        val sig = HomeSignals.Empty.copy(
            counts = likedCounts(40),
            likedCovers = listOf("y1"),
            spotifyLiked = spotifyLiked(206),
            playlists = listOf(libraryPlaylist("LPa", 3), libraryPlaylist("LPb", 4)),
        )
        val remote = charts().copy(youtubeMixes = listOf(youtubeMix("RDTMAK5uy_1"), youtubeMix("RDTMAK5uy_2")))
        val feed = HomeFeedBuilder.build(sig, remote, ctx(youtubeSignedIn = true), spotifyMixes = listOf(mix("1", 50)))

        val spotify = feed.find<SpotifySection>()!!
        val youtube = feed.find<YouTubeSection>()!!
        assertEquals(206, spotify.liked!!.count)
        assertEquals(listOf("1"), spotify.mixes.map { it.spotifyId })
        assertEquals(40, youtube.liked!!.count)
        assertEquals(listOf("RDTMAK5uy_1", "RDTMAK5uy_2"), youtube.mixes.map { it.id })
        assertTrue(
            "the YouTube Music row carries the likes, so Lately does not",
            feed.find<LatelySection>()?.tiles.orEmpty().none { it is LikedTile },
        )
    }

    @Test
    fun `the service rows need the network`() {
        val sig = HomeSignals.Empty.copy(counts = likedCounts(40), spotifyLiked = spotifyLiked(206))
        val feed = HomeFeedBuilder.build(sig, charts(), ctx(online = false, youtubeSignedIn = true), spotifyMixes = listOf(mix("1", 50)))
        assertNull(feed.find<SpotifySection>())
        assertNull(feed.find<YouTubeSection>())
    }

    // ---- an established listener -----------------------------------------------------------

    @Test
    fun `an established listener in the evening opens on the mix, then their evenings`() {
        val (library, plays) = establishedLibrary()
        val c = ctx(seed = 3) // seed 3 leaves the order unperturbed
        val feed = HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, related()), charts(), c)

        assertEquals(ListenerProfile.Established, feed.profile)
        val kinds = feed.kinds()
        assertEquals("HeroMixSection", kinds.first())
        assertTrue(kinds.indexOf("AroundNowSection") < kinds.indexOf("RotationSection"))
        assertTrue(kinds.indexOf("RotationSection") < kinds.indexOf("DiscoverSection"))
        assertNotNull(feed.find<InsightsSection>())
        assertNotNull(feed.find<RediscoverSection>())
    }

    @Test
    fun `mornings lead with the familiar, afternoons with discovery`() {
        val (library, plays) = establishedLibrary()
        fun order(now: Long): List<String?> {
            val c = ctx(now = now, seed = 3)
            return HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, related()), charts(), c).kinds()
        }
        val am = order(morning)
        val pm = order(afternoon)
        assertTrue(am.indexOf("RotationSection") < am.indexOf("DiscoverSection"))
        assertTrue(pm.indexOf("DiscoverSection") < pm.indexOf("RotationSection"))
    }

    @Test
    fun `the rotation ranks by recent plays and never lets one artist take it over`() {
        val (library, plays) = establishedLibrary()
        val c = ctx()
        val rotation = HomeFeedBuilder.build(signals(library, plays, c), null, c).find<RotationSection>()!!

        assertTrue(rotation.songs.size in HomeFeedBuilder.MIN_ROTATION_LOW..10)
        val perArtist = rotation.songs.groupingBy { it.song.artists.first().id }.eachCount()
        assertTrue(perArtist.values.all { it <= 2 })
        assertTrue(rotation.songs.all { it.monthPlays >= 2 })
    }

    @Test
    fun `discover offers only never-played songs, each with the song that led to it`() {
        val (library, plays) = establishedLibrary()
        val c = ctx()
        val feed = HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, related()), null, c)
        val played = plays.map { it.songId }.toSet()
        val discover = feed.find<DiscoverSection>()!!

        assertTrue(discover.picks.none { it.song.id in played })
        assertTrue(discover.picks.all { it.because.id in played })
        val perArtist = discover.picks.groupingBy { it.song.artists.first().id }.eachCount()
        assertTrue("breadth first", perArtist.values.all { it <= 2 })
    }

    @Test
    fun `a song related to several favourites outranks one related to a single one`() {
        val weights = mapOf("a" to 1.0, "b" to 1.0, "c" to 5.0)
        val links = listOf(
            HomeRelatedLink("a", "x"), HomeRelatedLink("b", "x"),
            HomeRelatedLink("a", "y"),
            HomeRelatedLink("c", "x"),
        )
        val ranked = HomeRanking.discovery(links, weights)
        assertEquals(listOf("x", "y"), ranked.map { it.songId })
        assertEquals("the strongest seed is the reason", "c", ranked.first().seedId)
    }

    @Test
    fun `the hero mix keeps the part of the day's share of new songs`() {
        val (library, plays) = establishedLibrary()
        val c = ctx(now = afternoon)
        val hero = HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, related()), null, c).find<HeroMixSection>()!!

        assertTrue(hero.songs.size <= HomeFeedBuilder.HERO_SIZE)
        assertEquals(hero.songs.size, hero.songs.distinctBy { it.id }.size)
        val played = plays.map { it.songId }.toSet()
        assertEquals(hero.fresh, hero.songs.count { it.id !in played })
        assertTrue("afternoons are half discovery when there is enough", hero.fresh >= 8)
    }

    @Test
    fun `the same moment builds the same feed and a new daypart builds a different one`() {
        val (library, plays) = establishedLibrary()
        val c1 = ctx(seed = 10)
        val s = signals(library + relatedSongs(), plays, c1, related())
        val a = HomeFeedBuilder.build(s, null, c1)
        val b = HomeFeedBuilder.build(s, null, c1)
        val c = HomeFeedBuilder.build(s, null, c1.copy(seed = 11))
        assertEquals(a, b)
        assertNotEquals(a.find<HeroMixSection>()!!.songs, c.find<HeroMixSection>()!!.songs)
    }

    @Test
    fun `albums started and not finished are offered back`() {
        val library = (0 until 12).map { song("t$it", artist = "band", album = "lp") } +
            (0 until 10).map { song("o$it", artist = "o$it") }
        val plays = listOf("t0", "t1", "t2", "t3").flatMap { id -> List(3) { Play(id, evening - it * day) } } +
            (0 until 10).flatMap { i -> List(3) { Play("o$i", evening - it * day) } }
        val c = ctx()
        val album2 = AlbumEntity(id = "lp2", title = "Other", songCount = 10, duration = 2000)
        val feed = HomeFeedBuilder.build(
            signals(library + song("x0", artist = "band", album = "lp2") + song("x1", artist = "band", album = "lp2"),
                plays + listOf(Play("x0", evening), Play("x1", evening)), c,
                albums = listOf(AlbumEntity(id = "lp", title = "LP", songCount = 12, duration = 3000), album2)),
            null, c,
        )
        val started = feed.find<AlbumsInProgressSection>()!!
        assertEquals(setOf("lp", "lp2"), started.albums.map { it.album.id }.toSet())
        assertEquals(4, started.albums.first { it.album.id == "lp" }.heard)
    }

    // ---- local and offline -----------------------------------------------------------------

    @Test
    fun `a listener who mostly plays files is treated as local-first`() {
        val (library, plays) = establishedLibrary(local = true)
        val c = ctx(seed = 3)
        val feed = HomeFeedBuilder.build(signals(library, plays, c), charts(), c)

        assertEquals(ListenerProfile.LocalHeavy, feed.profile)
        val kinds = feed.kinds()
        assertEquals("HeroMixSection", kinds.first())
        // Downloads and device files are promoted out of the library band into the body of
        // the feed, ahead of everything that needs a network.
        assertNotNull(feed.find<ReadyOfflineSection>())
        assertTrue(
            "what plays offline outranks the charts",
            kinds.indexOf("ReadyOfflineSection") < kinds.indexOf("ChartsSection"),
        )
        assertTrue(
            "and sits in the body of the feed, not in its footnotes",
            kinds.indexOf("ReadyOfflineSection") <= 3,
        )
        listOf("AroundNowSection", "RotationSection", "ReadyOfflineSection").forEach {
            assertTrue("$it outranks the charts for a local listener", kinds.indexOf(it) < kinds.indexOf("ChartsSection"))
        }
    }

    @Test
    fun `offline, Home only offers what plays without a network`() {
        val (library, plays) = establishedLibrary(downloadedShare = 20)
        val c = ctx(online = false)
        val feed = HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, related()), charts(), c)

        assertTrue(feed.sections.first() is OfflineNoticeSection)
        listOf(DiscoverSection::class, ChartsSection::class, DeepDiveSection::class, MoodsSection::class)
            .forEach { kind -> assertTrue("$kind needs a network", feed.sections.none { kind.isInstance(it) }) }

        fun Song.offline() = song.isLocal || song.isDownloaded
        feed.sections.forEach { section ->
            val songs = when (section) {
                is HeroMixSection -> section.songs
                is RotationSection -> section.songs.map { it.song }
                is AroundNowSection -> section.songs
                is RediscoverSection -> section.songs.map { it.song }
                is RecentlyAddedSection -> section.songs
                is ReadyOfflineSection -> section.songs
                is SurpriseSection -> section.pools.flatMap { it.songs }
                else -> emptyList()
            }
            assertTrue("${section.key} offers a song that needs a network", songs.all { it.offline() })
        }
        val hero = feed.find<HeroMixSection>()
        if (hero != null) assertEquals(0, hero.fresh)
    }

    // ---- filters, charts, scale ------------------------------------------------------------

    @Test
    fun `hidden video songs are hidden everywhere`() {
        val (base, plays) = establishedLibrary()
        val library = base.map { if (it.id.removePrefix("s").toInt() % 2 == 0) it.copy(song = it.song.copy(isVideo = true)) else it }
        val c = ctx(hideVideo = true)
        val feed = HomeFeedBuilder.build(signals(library, plays, c), null, c)
        val shown = feed.sections.flatMap { section ->
            when (section) {
                is HeroMixSection -> section.songs
                is RotationSection -> section.songs.map { it.song }
                is AroundNowSection -> section.songs
                is RediscoverSection -> section.songs.map { it.song }
                else -> emptyList()
            }
        }
        assertTrue(shown.isNotEmpty())
        assertTrue(shown.none { it.song.isVideo })
    }

    @Test
    fun `charts keep each song's true rank when some are filtered`() {
        val c = ctx(hideExplicit = true)
        val charts = HomeFeedBuilder.build(HomeSignals.Empty, charts(), c).find<ChartsSection>()!!
        val songs = charts.charts.single().songs
        assertEquals("c2", songs.first().id)
        assertEquals(2, songs.first().chartPosition)
        assertEquals("Top 100 Music Videos India", charts.charts.single().title)
    }

    @Test
    fun `charts too thin to be a chart are not shown`() {
        val thin = charts().let { r -> r.copy(charts = r.charts.map { it.copy(songs = it.songs.take(4)) }) }
        assertNull(HomeFeedBuilder.build(HomeSignals.Empty, thin, ctx()).find<ChartsSection>())
    }

    @Test
    fun `no section is ever empty`() {
        val (library, plays) = establishedLibrary(downloadedShare = 10)
        listOf(true, false).forEach { online ->
            listOf(morning, afternoon, evening, wall(23)).forEach { now ->
                val c = ctx(now = now, online = online)
                val feed = HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, related()), charts(), c)
                feed.sections.forEach { section ->
                    val empty = when (section) {
                        is HeroMixSection -> section.songs.isEmpty()
                        is LatelySection -> section.tiles.isEmpty()
                        is RotationSection -> section.songs.isEmpty()
                        is AroundNowSection -> section.songs.isEmpty()
                        is MoodsSection -> section.moods.isEmpty()
                        is DiscoverSection -> section.picks.isEmpty()
                        is RediscoverSection -> section.songs.isEmpty()
                        is AlbumsInProgressSection -> section.albums.isEmpty()
                        is RecentlyAddedSection -> section.songs.isEmpty()
                        is ReadyOfflineSection -> section.songs.isEmpty()
                        is ChartsSection -> section.charts.isEmpty()
                        is SurpriseSection -> section.size == 0
                        else -> false
                    }
                    assertFalse("${section.key} is empty (online=$online)", empty)
                }
                assertEquals("no section twice", feed.sections.size, feed.sections.map { it.key }.distinct().size)
            }
        }
    }

    @Test
    fun `a very large history builds quickly and stays capped`() {
        val library = (0 until 5_000).map { song("s$it", artist = "a${it % 700}") }
        val random = Random(7)
        val plays = List(60_000) { Play("s${random.nextInt(5_000)}", evening - random.nextLong(365) * day - random.nextLong(24) * hour) }
        val c = ctx()
        val s = signals(library, plays, c)
        val started = System.nanoTime()
        val feed = HomeFeedBuilder.build(s, charts(), c)
        val ms = (System.nanoTime() - started) / 1_000_000
        assertTrue("built in $ms ms", ms < 500)
        feed.find<HeroMixSection>()?.let { assertTrue(it.songs.size <= HomeFeedBuilder.HERO_SIZE) }
        feed.find<RotationSection>()?.let { assertTrue(it.songs.size <= 10) }
        feed.find<RediscoverSection>()?.let { assertTrue(it.songs.size <= 10) }
    }

    // ---- surprise --------------------------------------------------------------------------

    @Test
    fun `surprise me skips recent picks and recent plays while it can`() {
        val pool = (0 until 10).map { song("p$it") }
        val section = SurpriseSection(listOf(SurprisePool(SurpriseKind.Unexplored, 1.0, pool)))
        val stats = mapOf("p1" to HomeSongStat("p1", 1, 1, evening - day, evening - day, 1, 1, 1, 0))
        val recent = setOf("p0", "p2", "p3")
        repeat(200) { n ->
            val pick = HomeSurprise.pick(section, stats, recent, evening, Random(n))!!
            assertTrue(pick.song.id !in recent)
            assertNotEquals("played yesterday", "p1", pick.song.id)
        }
    }

    @Test
    fun `surprise me favours the head of a pool without always taking it`() {
        val pool = (0 until 10).map { song("p$it") }
        val section = SurpriseSection(listOf(SurprisePool(SurpriseKind.Forgotten, 1.0, pool)))
        val picks = (0 until 2_000).map { HomeSurprise.pick(section, emptyMap(), emptyList(), evening, Random(it))!!.song.id }
        val firstHalf = picks.count { it.removePrefix("p").toInt() < 5 }
        assertTrue(firstHalf in 1_300..1_550)
        assertTrue(picks.distinct().size == 10)
    }

    @Test
    fun `the weekly music video chart is chosen over live performances`() {
        // The United States list as YouTube Charts returned it on 2026-09-18, in its order.
        val us = listOf(
            "Top 100 Live Performances - United States", "Trending 20 United States",
            "Daily Top Music Videos - United States", "Top 100 Music Videos United States",
            "Top 50 Pop Music Videos United States",
        ).mapIndexed { i, title ->
            com.music.innertube.models.PlaylistItem("PL$i", title, null, null, null, null, null, null)
        }
        assertEquals("Top 100 Music Videos United States", HomeRemoteRepository.pickChart(us)?.title)
        assertEquals("Daily Top Music Videos - United States", HomeRemoteRepository.pickChart(us.filterNot { it.id == "PL3" })?.title)
        assertEquals("Trending 20 United States", HomeRemoteRepository.pickChart(us.take(2))?.title)
    }

    @Test
    fun `a young history still gets a mix of what was played lately`() {
        // 32 songs, each played once or twice this week: no rotation to speak of yet.
        val library = (0 until 32).map { song("y$it", artist = "ya${it % 20}") }
        val plays = library.mapIndexed { i, s -> Play(s.id, evening - i * hour) } +
            library.take(6).map { Play(it.id, evening - 2 * day) }
        val c = ctx()
        val feed = HomeFeedBuilder.build(signals(library, plays, c), null, c)
        assertEquals(ListenerProfile.Established, feed.profile)
        val hero = feed.find<HeroMixSection>()
        assertNotNull(hero)
        assertTrue(hero!!.songs.size >= HomeFeedBuilder.MIN_HERO_LOW)
    }

    @Test
    fun `moods never outrank the listener's own sections`() {
        val (library, plays) = establishedLibrary()
        val moods = listOf("Chill", "Feel good", "Sleep", "Focus").map {
            com.music.innertube.pages.MoodAndGenres.Item(it, 0xFF00FF00, com.music.innertube.models.BrowseEndpoint("FEmusic_moods_and_genres_category", "p"))
        }
        // Morning: the listener has no morning pattern, so moods stand alone.
        val c = ctx(now = morning, seed = 3)
        val kinds = HomeFeedBuilder.build(signals(library, plays, c), HomeRemote(moods = moods), c).kinds()
        assertTrue(kinds.contains("MoodsSection"))
        assertTrue(kinds.indexOf("RotationSection") < kinds.indexOf("MoodsSection"))
    }

    @Test
    fun `charts default to the listener's location and follow their choice`() {
        // Default: where the listener is, with Global beside it.
        assertEquals(listOf("IN", "ZZ"), HomeCharts.scopes(null, showGlobal = true, detected = "IN"))
        assertEquals(listOf("IN"), HomeCharts.scopes(HomeCharts.AUTO, showGlobal = false, detected = "IN"))
        // No detectable location: Global, not a guessed country.
        assertEquals(listOf("ZZ"), HomeCharts.scopes(HomeCharts.AUTO, showGlobal = false, detected = null))
        // A chosen country wins over the location.
        assertEquals(listOf("GB", "ZZ"), HomeCharts.scopes("GB", showGlobal = true, detected = "IN"))
        assertEquals(listOf("ZZ"), HomeCharts.scopes(HomeCharts.GLOBAL, showGlobal = true, detected = "IN"))
        assertEquals(emptyList<String>(), HomeCharts.scopes(HomeCharts.OFF, showGlobal = true, detected = "IN"))
    }

    @Test
    fun `Home shows only the charts the listener chose, in their order`() {
        val remote = charts().let { r ->
            r.copy(charts = r.charts + r.charts.single().copy(playlistId = "PLus", title = "Top 100 Music Videos United States", scope = "US") +
                r.charts.single().copy(playlistId = "PLzz", title = "Top 100 Music Videos Global", scope = "ZZ"))
        }
        fun shown(scopes: List<String>) =
            HomeFeedBuilder.build(HomeSignals.Empty, remote, ctx().copy(chartScopes = scopes)).find<ChartsSection>()?.charts?.map { it.scope }
        assertEquals(listOf("IN", "ZZ"), shown(listOf("IN", "ZZ")))
        assertEquals(listOf("ZZ"), shown(listOf("ZZ")))
        assertNull("charts off", shown(emptyList()))
        assertNull("a country not fetched yet is not faked", shown(listOf("JP")))
    }

    // ---- progressive personalisation -------------------------------------------------------

    /** A library of [songs] songs by [artists] artists, each artist holding several songs. */
    private fun youngLibrary(songs: Int = 24, artists: Int = 6) =
        (0 until songs).map { song("y$it", artist = "ya${it % artists}") }

    /** [n] counted plays, one every other hour back from the evening, over [songs] songs. */
    private fun firstPlays(library: List<Song>, n: Int, songs: Int = n) =
        (0 until n).map { Play(library[it % songs].id, evening - it * 2 * hour) }

    @Test
    fun `confidence moves from the very first play and never jumps`() {
        val library = youngLibrary()
        val c = ctx()
        val confidences = (0..30).map { n ->
            HomeTaste.confidence(signals(library, firstPlays(library, n, songs = 12), c))
        }

        assertEquals("no history is no confidence", 0.0, confidences[0], 0.0)
        assertTrue("one play is already worth something", confidences[1] > 0.0)
        confidences.zipWithNext().forEach { (before, after) ->
            assertTrue("confidence never falls as listening grows", after >= before)
            // The whole point: no play is a threshold. The largest single step over the
            // first thirty plays is a few percent, not a door opening.
            assertTrue("confidence jumped by ${after - before}", after - before < 0.06)
        }
        assertTrue("and it is still climbing at thirty plays", confidences[30] > confidences[20])
    }

    @Test
    fun `one play is exploring, repeated listening is a preference`() {
        val once = HomeArtistStat(artistId = "a", plays = 1, weekPlays = 1, monthPlays = 1, lastPlayed = evening, songs = 1)
        val twice = once.copy(plays = 3, weekPlays = 3, monthPlays = 3, songs = 2)
        val lived = once.copy(plays = 26, weekPlays = 8, monthPlays = 18, songs = 9)

        assertEquals(AffinityTier.Exploring, HomeTaste.artistAffinity(once, evening).tier)
        assertEquals(AffinityTier.Warming, HomeTaste.artistAffinity(twice, evening).tier)
        assertEquals(AffinityTier.Favourite, HomeTaste.artistAffinity(lived, evening).tier)

        // An artist left alone for months stops shaping Home without being forgotten.
        val faded = HomeTaste.artistAffinity(lived.copy(lastPlayed = evening - 180 * day, weekPlays = 0, monthPlays = 0), evening)
        assertTrue(faded.strength < HomeTaste.artistAffinity(lived, evening).strength)
        assertEquals(AffinityTier.Exploring, faded.tier)
    }

    @Test
    fun `one song does not turn Home into that song`() {
        // Twelve plays: eleven of one artist, one of another. The loud artist may lead,
        // but the mix is still wide and Shiny still says it barely knows them.
        val library = youngLibrary(songs = 24, artists = 6)
        val loud = library.filter { it.artists.first().id == "ya0" }
        val plays = (0 until 11).map { Play(loud[it % loud.size].id, evening - it * 3 * hour) } +
            Play(library.first { it.artists.first().id == "ya3" }.id, evening - 40 * hour)
        val c = ctx()
        val feed = HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, relatedTo(library.map { it.id })), charts(), c)

        val hero = feed.find<HeroMixSection>()!!
        val perArtist = hero.songs.groupingBy { it.artists.first().id }.eachCount()
        assertTrue("no artist may own the mix", perArtist.values.max() <= hero.songs.size / 2)
        // Knowing little is a reason to offer more, not less.
        assertTrue("the mix is still mostly discovery at this depth", hero.fresh > 0)
        assertTrue("and twelve plays is not a listener Shiny claims to know", feed.taste.confidence < 0.35)
    }

    @Test
    fun `the eleventh and twelfth plays look the same - there is no gate left`() {
        val library = youngLibrary(songs = 24, artists = 6)
        val c = ctx()
        fun shapeAt(n: Int): List<String?> =
            HomeFeedBuilder.build(
                signals(library + relatedSongs(), firstPlays(library, n, songs = 8), c, relatedTo(library.map { it.id })),
                charts(),
                c,
            ).kinds()

        // The old Home turned on at twelve counted plays: below it there was no mix, no
        // rotation and a progress meter. These two feeds must be indistinguishable.
        assertEquals(shapeAt(11), shapeAt(12))
        // And a mix existed long before either of them.
        assertTrue(shapeAt(1).contains("HeroMixSection"))
        assertTrue(shapeAt(2).contains("HeroMixSection"))
    }

    @Test
    fun `one play is already enough for Home to say something personal`() {
        val library = youngLibrary(songs = 24, artists = 4)
        val c = ctx()
        val played = library.first()
        val feed = HomeFeedBuilder.build(
            signals(library + relatedSongs(), listOf(Play(played.id, evening - hour)), c, relatedTo(library.map { it.id })),
            charts(),
            c,
        )

        val keep = feed.find<ContinueSection>()
        assertNotNull("one counted play earns a thread back into it", keep)
        assertEquals(played.id, keep!!.seed.id)
        assertEquals(ContinueKind.Artist, keep.kind)
        assertEquals("ya0", keep.throughId)
        // ...but it is framed as one listen, not as a taste.
        assertEquals(AffinityTier.Exploring, keep.tier)
        assertTrue("and it never offers back the song it came from", keep.songs.none { it.id == played.id })
    }

    @Test
    fun `keep listening retires once there is something better to say`() {
        val (library, plays) = establishedLibrary()
        val c = ctx()
        val feed = HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, related()), charts(), c)

        assertTrue(feed.taste.confidence > HomeFeedBuilder.CONTINUE_RETIRES_AT)
        assertNull("the rotation says it better", feed.find<ContinueSection>())
        assertNotNull(feed.find<RotationSection>())
    }

    @Test
    fun `the mix leans on discovery when Shiny knows least`() {
        val base = 0.4
        val knowsNothing = HomeTaste.freshShare(base, confidence = 0.0)
        val knowsSome = HomeTaste.freshShare(base, confidence = 0.4)
        val knowsThem = HomeTaste.freshShare(base, confidence = 0.95)

        assertTrue(knowsNothing > knowsSome)
        assertTrue(knowsSome > knowsThem)
        assertTrue("discovery never disappears, however well Shiny knows them", knowsThem >= base)
        assertTrue("and never takes the mix over either", knowsNothing <= 0.7)
    }

    // ---- an adaptive order -------------------------------------------------------------------

    @Test
    fun `opening the charts keeps them near the top, ignoring them lets them sink`() {
        val (library, plays) = establishedLibrary()
        val c = ctx(seed = 3)
        val sig = signals(library + relatedSongs(), plays, c, related())
        fun chartsAt(interest: HomeInterest) =
            HomeFeedBuilder.build(sig, charts(), c, interest).kinds().indexOf("ChartsSection")

        var reader = HomeInterest.Empty
        repeat(8) { reader = reader.noting(HomeInterest.Kind.Charts, evening) }

        assertTrue(
            "a chart reader sees them earlier than someone who never opens them",
            chartsAt(reader) < chartsAt(HomeInterest.Empty),
        )
    }

    @Test
    fun `the charts lead a new Home and step aside as the listener becomes known`() {
        val c = ctx(seed = 3)
        val newcomer = HomeFeedBuilder.build(HomeSignals.Empty, charts(), c).kinds()
        assertEquals("ChartsSection", newcomer.first { it != "StartHereSection" })

        val (library, plays) = establishedLibrary()
        val known = HomeFeedBuilder.build(signals(library + relatedSongs(), plays, c, related()), charts(), c).kinds()
        listOf("RotationSection", "AroundNowSection", "DiscoverSection").forEach {
            assertTrue(
                "$it outranks the charts once there is a listener to know",
                known.indexOf(it) < known.indexOf("ChartsSection"),
            )
        }
    }

    @Test
    fun `an interest that stops being acted on fades away`() {
        var reader = HomeInterest.Empty
        repeat(8) { reader = reader.noting(HomeInterest.Kind.Charts, evening) }
        val keen = reader[HomeInterest.Kind.Charts]
        val fortnightLater = reader.decayed(evening + 14 * day)
        val seasonLater = reader.decayed(evening + 120 * day)

        assertTrue("eight visits is a habit", reader.lean(HomeInterest.Kind.Charts) > 0.6)
        assertEquals("halved in a fortnight", keen / 2, fortnightLater[HomeInterest.Kind.Charts], 0.05)
        assertTrue("and its weight falls with it", fortnightLater.lean(HomeInterest.Kind.Charts) < reader.lean(HomeInterest.Kind.Charts))
        assertTrue("and is worth nothing within a season", seasonLater.lean(HomeInterest.Kind.Charts) < 0.01)
    }

    @Test
    fun `Home is the same page twice in a row`() {
        val (library, plays) = establishedLibrary()
        val c = ctx(seed = 11)
        val sig = signals(library + relatedSongs(), plays, c, related())
        val once = HomeFeedBuilder.build(sig, charts(), c).kinds()
        val twice = HomeFeedBuilder.build(sig, charts(), c).kinds()
        assertEquals("an adaptive Home is not a shuffling one", once, twice)
    }

    // ---- the charts --------------------------------------------------------------------------

    @Test
    fun `chart movement is only claimed once there is an earlier day to compare`() {
        val chart = charts().charts.single()
        assertEquals(ChartMove.Unknown, chart.moveOf("c1", 1))

        val today = chart.copy(previous = mapOf("c1" to 4, "c2" to 2, "c3" to 3), previousDay = 1)
        assertEquals(ChartMove.Up(3), today.moveOf("c1", 1))
        assertEquals(ChartMove.Down(3), today.moveOf("c2", 5))
        assertEquals(ChartMove.Steady, today.moveOf("c3", 3))
        assertEquals(ChartMove.New, today.moveOf("c9", 7))
    }

    @Test
    fun `dayparts cover every hour`() {
        (0..23).forEach { Daypart.of(it) }
        assertEquals(Daypart.Night, Daypart.of(2))
        assertEquals(Daypart.Evening, Daypart.of(21))
    }
}
