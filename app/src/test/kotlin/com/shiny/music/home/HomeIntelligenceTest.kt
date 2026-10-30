package com.shiny.music.home

import com.shiny.music.db.entities.ArtistEntity
import com.shiny.music.db.entities.HomeLibraryCounts
import com.shiny.music.db.entities.HomeRelatedLink
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.db.entities.Song
import com.shiny.music.db.entities.SongEntity
import com.shiny.music.db.entities.SongOutcomeBucket
import com.shiny.music.intelligence.Candidate
import com.shiny.music.intelligence.FeedbackKind
import com.shiny.music.intelligence.IntelligenceSnapshot
import com.shiny.music.intelligence.Reason
import com.shiny.music.intelligence.Recommendation
import com.shiny.music.intelligence.RecommendationStyle
import com.shiny.music.intelligence.ScoreBreakdown
import com.shiny.music.intelligence.TasteInputs
import com.shiny.music.intelligence.TasteInsight
import com.shiny.music.intelligence.TasteProfileBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class HomeIntelligenceTest {
    private val now = LocalDateTime.of(2026, 10, 30, 19, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val day = HOME_DAY_MS

    private fun song(id: String, artist: String = "artist_$id") = Song(
        song = SongEntity(id = id, title = "Song $id", duration = 200, thumbnailUrl = "https://img.test/$id.jpg"),
        artists = listOf(ArtistEntity(id = artist, name = "Name $artist")),
    )

    private fun stat(id: String, plays: Int = 6, month: Int = 4, week: Int = 2, daysAgo: Int = 2) = HomeSongStat(
        songId = id,
        plays = plays,
        playTime = plays * 190_000L,
        firstPlayed = now - 100 * day,
        lastPlayed = now - daysAgo * day,
        weekPlays = week,
        weekPlayTime = week * 190_000L,
        monthPlays = month,
        daypartPlays = 0,
    )

    private val ctx = HomeContext(nowMs = now, daypart = Daypart.Evening, online = true, seed = 3)

    private fun snapshot(style: RecommendationStyle = RecommendationStyle.Balanced, input: TasteInputs.() -> TasteInputs = { this }) =
        IntelligenceSnapshot.Neutral.copy(
            enabled = true,
            style = style,
            taste = TasteProfileBuilder.build(TasteInputs(confidence = 0.6, nowMs = now).input()),
        )

    private fun signals(
        songs: List<Song>,
        stats: List<HomeSongStat>,
        intelligence: IntelligenceSnapshot = IntelligenceSnapshot.Neutral,
        discovery: List<HomeRelatedLink> = emptyList(),
        recommendations: Map<String, Recommendation> = emptyMap(),
    ) = HomeSignals.Empty.copy(
        counts = HomeLibraryCounts(0, 0, 0, 0, stats.sumOf { it.plays }, 0),
        songStats = stats.associateBy { it.songId },
        rotationIds = HomeRanking.rotation(stats, now),
        rediscoverIds = HomeRanking.rediscover(stats, now),
        recentIds = HomeRanking.recent(stats),
        songs = songs.associateBy { it.id },
        intelligence = intelligence,
        discovery = discovery,
        recommendations = recommendations,
    )

    private fun rejected(kind: FeedbackKind, id: String) = MusicFeedback(kind.targetType, id, kind.name, null, now, null)

    private fun recommendation(id: String, reason: Reason?) = Recommendation(
        song = Candidate(id, id, emptyList()),
        score = 1.0,
        breakdown = ScoreBreakdown(),
        reason = reason,
        source = null,
    )

    @Test
    fun `a hidden artist disappears from the rotation and the mix`() {
        val songs = (0 until 8).map { song("s$it", artist = if (it == 0) "hidden" else "a$it") }
        val stats = songs.map { stat(it.id) }
        val before = HomeFeedBuilder.build(signals(songs, stats), null, ctx)
        val after = HomeFeedBuilder.build(
            signals(songs, stats, snapshot { copy(feedback = listOf(rejected(FeedbackKind.HideArtist, "hidden"))) }),
            null,
            ctx,
        )

        fun rotation(feed: HomeFeed) = feed.sections.filterIsInstance<RotationSection>().single().songs.map { it.song.id }
        fun mix(feed: HomeFeed) = feed.sections.filterIsInstance<HeroMixSection>().single().songs.map { it.id }
        assertTrue("s0" in rotation(before))
        assertFalse("s0" in rotation(after))
        assertFalse("s0" in mix(after))
    }

    @Test
    fun `a blocked song leaves Home but the rest stays`() {
        val songs = (0 until 6).map { song("s$it") }
        val stats = songs.map { stat(it.id) }
        val feed = HomeFeedBuilder.build(
            signals(songs, stats, snapshot { copy(feedback = listOf(rejected(FeedbackKind.BlockSong, "s1"))) }),
            null,
            ctx,
        )
        val rotation = feed.sections.filterIsInstance<RotationSection>().single().songs.map { it.song.id }
        assertFalse("s1" in rotation)
        assertEquals(5, rotation.size)
    }

    @Test
    fun `the rotation follows meaningful listening rather than raw counts`() {
        val songs = listOf(song("finished"), song("abandoned"), song("x"), song("y"))
        val stats = songs.map { stat(it.id) }
        val feed = HomeFeedBuilder.build(
            signals(
                songs,
                stats,
                snapshot {
                    copy(
                        songStats = stats,
                        songOutcomes = listOf(
                            SongOutcomeBucket("finished", 0, positive = 10, partial = 0, negative = 0, listenedMs = 2_000_000, lastAt = now),
                            SongOutcomeBucket("abandoned", 0, positive = 0, partial = 0, negative = 10, listenedMs = 40_000, lastAt = now),
                        ),
                    )
                },
            ),
            null,
            ctx,
        )
        val rotation = feed.sections.filterIsInstance<RotationSection>().single().songs.map { it.song.id }
        assertEquals("finished", rotation.first())
        assertEquals("abandoned", rotation.last())
    }

    @Test
    fun `a song once loved and now skipped is not offered for rediscovery`() {
        val songs = (0 until 8).map { song("old$it") }
        val stats = songs.map { stat(it.id, plays = 10, month = 0, week = 0, daysAgo = 80) }
        val faded = snapshot {
            copy(
                songStats = stats,
                songOutcomes = listOf(SongOutcomeBucket("old0", 0, positive = 0, partial = 0, negative = 3, listenedMs = 9_000, lastAt = now)),
            )
        }
        val feed = HomeFeedBuilder.build(signals(songs, stats, faded), null, ctx)
        val rediscover = feed.sections.filterIsInstance<RediscoverSection>().single().songs.map { it.song.id }
        assertFalse("old0" in rediscover)
        assertTrue(rediscover.isNotEmpty())
    }

    @Test
    fun `a recommendation without a seed song still reaches Discover, with its own reason`() {
        val fresh = (0 until 6).map { song("n$it") }
        val played = (0 until 6).map { song("p$it") }
        val links = fresh.map { HomeRelatedLink(seedId = "", songId = it.id) }
        val recs = fresh.associate { it.id to recommendation(it.id, Reason.SimilarToArtist("Someone")) }
        val feed = HomeFeedBuilder.build(
            signals(fresh + played, played.map { stat(it.id) }, snapshot(), links, recs),
            null,
            ctx.copy(seed = 4),
        )
        val discover = feed.sections.filterIsInstance<DiscoverSection>().single()
        assertTrue(discover.picks.all { it.because == null && it.reason == Reason.SimilarToArtist("Someone") })
        assertNull(discover.lead)
    }

    @Test
    fun `without personalisation a seedless link is dropped as before`() {
        val fresh = (0 until 6).map { song("n$it") }
        val played = (0 until 6).map { song("p$it") }
        val links = fresh.map { HomeRelatedLink(seedId = "", songId = it.id) }
        val feed = HomeFeedBuilder.build(signals(fresh + played, played.map { stat(it.id) }, discovery = links), null, ctx)
        assertTrue(feed.sections.none { it is DiscoverSection })
    }

    @Test
    fun `comfort leaves almost no room for new music in the mix, adventure leaves plenty`() {
        val familiar = (0 until 24).map { song("f$it") }
        val fresh = (0 until 24).map { song("n$it") }
        val links = fresh.map { HomeRelatedLink(seedId = "f0", songId = it.id) }
        fun mix(style: RecommendationStyle) = HomeFeedBuilder.build(
            signals(familiar + fresh, familiar.map { stat(it.id) }, snapshot(style), links),
            null,
            ctx,
        ).sections.filterIsInstance<HeroMixSection>().single()

        assertTrue(mix(RecommendationStyle.Comfort).fresh <= 4)
        assertTrue(mix(RecommendationStyle.Adventure).fresh >= 12)
        assertTrue(mix(RecommendationStyle.Discovery).fresh > mix(RecommendationStyle.Comfort).fresh)
    }

    @Test
    fun `the taste note appears only with evidence, and only when personalised`() {
        val songs = (0 until 4).map { song("s$it") }
        val stats = songs.map { stat(it.id) }
        val insight = TasteInsight.ExploringMore(5, 5, 0.8)
        val withInsight = signals(songs, stats, snapshot()).copy(insights = listOf(insight))
        assertNotNull(HomeFeedBuilder.build(withInsight, null, ctx).sections.filterIsInstance<TasteNoteSection>().singleOrNull())

        val empty = signals(songs, stats, snapshot())
        assertNull(HomeFeedBuilder.build(empty, null, ctx).sections.filterIsInstance<TasteNoteSection>().singleOrNull())

        val off = signals(songs, stats).copy(insights = listOf(insight))
        assertNull(HomeFeedBuilder.build(off, null, ctx).sections.filterIsInstance<TasteNoteSection>().singleOrNull())
    }

    @Test
    fun `the artist prompt is for a new listener only, and goes when dismissed`() {
        val asked = ctx.copy(pickArtistsDismissed = false)
        val fresh = HomeSignals.Empty.copy(intelligence = snapshot())
        assertTrue(HomeFeedBuilder.build(fresh, null, asked).sections.contains(PickArtistsSection))
        assertFalse(HomeFeedBuilder.build(fresh, null, ctx).sections.contains(PickArtistsSection))
        assertFalse("not with personalisation off", HomeFeedBuilder.build(HomeSignals.Empty, null, asked).sections.contains(PickArtistsSection))

        val songs = (0 until 40).map { song("s$it") }
        val busy = signals(songs, songs.map { stat(it.id) }, snapshot())
        assertFalse(HomeFeedBuilder.build(busy, null, asked).sections.contains(PickArtistsSection))
    }

    @Test
    fun `three chosen artists are enough to stop asking`() {
        val chosen = snapshot { copy(feedback = (0 until 3).map { rejected(FeedbackKind.SeedArtist, "a$it") }) }
        val feed = HomeFeedBuilder.build(
            HomeSignals.Empty.copy(intelligence = chosen),
            null,
            ctx.copy(pickArtistsDismissed = false),
        )
        assertFalse(feed.sections.contains(PickArtistsSection))
    }

    @Test
    fun `only recommendation sections log impressions, with the visible songs`() {
        val songs = (0 until 12).map { song("s$it") }
        val hero = HeroMixSection(Daypart.Evening, songs, 12, 0, offlineOnly = false)
        val (surface, items) = HomeImpressions.of(hero)!!
        assertEquals(RecSurface.HomeMix, surface)
        assertEquals(HomeImpressions.VISIBLE_ON_A_SHELF, items.size)
        assertNull(HomeImpressions.of(OfflineNoticeSection(3)))
        assertNull(HomeImpressions.of(PickArtistsSection))
    }

    @Test
    fun `a new release by a hidden artist is left off the page`() {
        val hidden = snapshot { copy(feedback = listOf(rejected(FeedbackKind.HideArtist, "gone"))) }
        val keep = com.music.innertube.models.AlbumItem(
            browseId = "keep",
            playlistId = "kp",
            title = "Keep",
            artists = listOf(com.music.innertube.models.Artist("Fine", "fine")),
            thumbnail = "t",
        )
        val drop = keep.copy(browseId = "drop", artists = listOf(com.music.innertube.models.Artist("Gone", "gone")))
        val feed = NewFeedBuilder.build(
            HomeSignals.Empty.copy(intelligence = hidden),
            HomeRemote(newReleases = listOf(keep, drop)),
            ctx,
        )
        val shown = feed.sections.flatMap { section ->
            when (section) {
                is FeaturedSection -> listOf(section.release.album.id)
                is FreshSection -> section.albums.map { it.id }
                else -> emptyList()
            }
        }
        assertTrue("keep" in shown)
        assertFalse("drop" in shown)
    }
}
