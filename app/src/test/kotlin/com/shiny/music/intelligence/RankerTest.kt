package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistEdgeRow
import com.shiny.music.db.entities.ArtistOutcomeBucket
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.SongFeature
import com.shiny.music.db.entities.SongOutcomeBucket
import com.shiny.music.db.entities.SourceOutcomeStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankerTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun candidate(
        id: String,
        artist: String,
        source: CandidateSource = CandidateSource.RelatedSong,
        strength: Double = 0.5,
        album: String? = null,
        year: Int? = null,
    ) = Candidate(id, "Song $id", listOf(ArtistRef(artist, "Name $artist")), album, year, 200, hits = listOf(SourceHit(source, strength)))

    private fun played(id: String, plays: Int, daysAgo: Int = 1, week: Int = 0, month: Int = 0, songs: Int = 3) =
        HomeArtistStat(id, plays, week, month, now - daysAgo * day, songs)

    private fun songPlayed(id: String, plays: Int, daysAgo: Int = 30, week: Int = 0) =
        HomeSongStat(id, plays, plays * 180_000L, now - 200 * day, now - daysAgo * day, week, 0, week, 0)

    private fun taste(input: TasteInputs.() -> TasteInputs = { this }, confidence: Double = 0.7) =
        TasteProfileBuilder.build(TasteInputs(confidence = confidence, nowMs = now).input())

    private fun ranked(candidates: List<Candidate>, taste: TasteProfile, style: RecommendationStyle = RecommendationStyle.Balanced, graph: MusicGraph = MusicGraph.Empty, seeds: Map<String, Double> = emptyMap(), hops: Int = 2) =
        Ranker.rank(candidates, taste, RankContext(style = style, graph = graph, reach = graph.reach(seeds, hops))).map { it.candidate.songId }

    @Test
    fun `an artist the listener loves beats a stranger from the same source`() {
        val t = taste({ copy(artistStats = listOf(played("loved", 50, week = 5, month = 15, songs = 10))) })
        assertEquals("a", ranked(listOf(candidate("a", "loved"), candidate("b", "stranger")), t).first())
    }

    @Test
    fun `hidden artists, blocked songs and not-interested songs never come back`() {
        fun fb(kind: FeedbackKind, id: String) = MusicFeedback(kind.targetType, id, kind.name, null, now, null)
        val t = taste({
            copy(feedback = listOf(fb(FeedbackKind.HideArtist, "hidden"), fb(FeedbackKind.BlockSong, "blocked"), fb(FeedbackKind.NotInterested, "soft")))
        })
        val out = ranked(
            listOf(candidate("x", "hidden"), candidate("blocked", "fine"), candidate("soft", "fine"), candidate("ok", "fine")),
            t,
        )
        assertEquals(listOf("ok"), out)
    }

    @Test
    fun `a frequently skipped artist sinks below an unknown one`() {
        val t = taste({
            copy(artistOutcomes = listOf(ArtistOutcomeBucket("skipped", 0, 0, 0, 12, now - day)))
        })
        assertEquals("fresh", ranked(listOf(candidate("bad", "skipped"), candidate("fresh", "unknown")), t).first())
    }

    @Test
    fun `less like this also pushes down that artist's neighbours`() {
        val graph = MusicGraph.build(listOf(ArtistEdgeRow("disliked", "neighbour", 8f, RelationKind.SimilarArtist.name, "n")))
        val t = taste({ copy(feedback = listOf(MusicFeedback("artist", "disliked", FeedbackKind.LessLikeThis.name, null, now, null))) })
        val c = RankContext(graph = graph)
        val near = Ranker.score(candidate("a", "neighbour"), t, c)
        val far = Ranker.score(candidate("b", "elsewhere"), t, c)
        assertTrue(near.breakdown.rejection > far.breakdown.rejection)
        assertTrue(near.score < far.score)
    }

    @Test
    fun `more from this artist lifts them`() {
        val t = taste({ copy(feedback = listOf(MusicFeedback("artist", "more", FeedbackKind.MoreFromArtist.name, null, now, null))) })
        assertEquals("a", ranked(listOf(candidate("a", "more"), candidate("b", "other")), t).first())
    }

    @Test
    fun `a song just played is held back, one heard long ago is not`() {
        val t = taste({
            copy(songStats = listOf(songPlayed("today", 5, daysAgo = 0), songPlayed("months", 5, daysAgo = 90)))
        })
        val a = Ranker.score(candidate("today", "x"), t, RankContext())
        val b = Ranker.score(candidate("months", "x"), t, RankContext())
        assertTrue(a.breakdown.fatigue > b.breakdown.fatigue)
        assertTrue(a.score < b.score)
    }

    @Test
    fun `predicted satisfaction moves with outcomes and falls back to the source`() {
        val t = taste({
            copy(
                songOutcomes = listOf(SongOutcomeBucket("hit", 0, 8, 0, 0, 1_000_000, now), SongOutcomeBucket("miss", 0, 0, 0, 8, 10_000, now)),
                sourceStats = listOf(SourceOutcomeStat(CandidateSource.GraphFar.name, 2, 40)),
            )
        })
        val hit = Ranker.predictedSatisfaction(candidate("hit", "x"), t)
        val miss = Ranker.predictedSatisfaction(candidate("miss", "x"), t)
        val coldFar = Ranker.predictedSatisfaction(candidate("cold", "y", CandidateSource.GraphFar), t)
        val coldRelated = Ranker.predictedSatisfaction(candidate("cold", "y", CandidateSource.RelatedSong), t)
        assertTrue(hit > 0.7)
        assertTrue(miss < 0.35)
        assertTrue("a source that keeps failing is trusted less", coldFar < coldRelated)
    }

    @Test
    fun `a source that works gets a quality boost once outcomes exist`() {
        val t = taste({ copy(sourceStats = listOf(SourceOutcomeStat(CandidateSource.CoListen.name, 60, 2), SourceOutcomeStat(CandidateSource.Chart.name, 2, 60))) })
        val good = Ranker.score(candidate("a", "x", CandidateSource.CoListen), t, RankContext())
        val bad = Ranker.score(candidate("b", "x", CandidateSource.Chart), t, RankContext())
        assertTrue(good.breakdown.sourceQuality > 0.0)
        assertTrue(bad.breakdown.sourceQuality < 0.0)
    }

    @Test
    fun `graph reach lifts an unheard artist the listener is connected to`() {
        val graph = MusicGraph.build(listOf(ArtistEdgeRow("mine", "near", 8f, RelationKind.CoListen.name, "near")))
        val t = taste()
        assertEquals("a", ranked(listOf(candidate("a", "near"), candidate("b", "far")), t, graph = graph, seeds = mapOf("mine" to 1.0)).first())
    }

    @Test
    fun `an unheard song on an album they keep playing beats one elsewhere by the same artist`() {
        val t = taste({
            copy(albumStats = listOf(com.shiny.music.db.entities.HomeAlbumStat("loved", songsPlayed = 8, plays = 40, lastPlayed = now - day)))
        })
        assertEquals("a", ranked(listOf(candidate("a", "x", album = "loved"), candidate("b", "x", album = "other")), t).first())
    }

    @Test
    fun `an artist in the library is not a new artist, even unplayed`() {
        val t = taste({ copy(savedArtists = listOf(com.shiny.music.db.entities.HomeSavedArtist("saved", "Saved", 5, 0))) })
        assertEquals(0.0, Ranker.score(candidate("a", "saved"), t, RankContext()).breakdown.newArtist, 0.0)
        assertEquals(1.0, Ranker.score(candidate("b", "stranger"), t, RankContext()).breakdown.newArtist, 0.0)
    }

    @Test
    fun `measured tempo counts only when it was measured`() {
        val t = taste({ copy(features = listOf(SongFeature("m", 120f, 0.8f, null, null, null, null, null))) })
        val unmeasured = Ranker.score(candidate("u", "x"), t, RankContext())
        assertEquals(0.0, unmeasured.breakdown.sound, 0.0)
    }

    @Test
    fun `a new listener gets the source's order, not a fake personal one`() {
        val t = taste(confidence = 0.0)
        val out = ranked(listOf(candidate("weak", "a", strength = 0.2), candidate("strong", "b", strength = 0.9)), t)
        assertEquals(listOf("strong", "weak"), out)
    }

    @Test
    fun `with no candidates there are no recommendations`() {
        assertTrue(ranked(emptyList(), taste()).isEmpty())
    }

    @Test
    fun `candidates from two sources merge into one with both hits`() {
        val merged = CandidateAdapters.merge(listOf(candidate("a", "x", CandidateSource.RelatedSong, 0.3), candidate("a", "x", CandidateSource.GraphNear, 0.8)))
        assertEquals(1, merged.size)
        assertEquals(CandidateSource.GraphNear, merged.single().source)
    }

    private fun mixedWorld(): Pair<TasteProfile, List<Candidate>> {
        val t = taste({
            copy(
                artistStats = listOf(played("fav", 40, week = 4, month = 12, songs = 8)),
                songStats = listOf(songPlayed("known", 10, daysAgo = 25)),
            )
        })
        return t to listOf(
            candidate("known", "fav", CandidateSource.Rotation),
            candidate("newsong", "fav", CandidateSource.ArtistTopSong),
            candidate("newartist", "stranger", CandidateSource.GraphFar),
        )
    }

    @Test
    fun `comfort prefers what they know, discovery and adventure prefer what they do not`() {
        val (t, c) = mixedWorld()
        val comfort = ranked(c, t, RecommendationStyle.Comfort)
        val discovery = ranked(c, t, RecommendationStyle.Discovery)
        val adventure = ranked(c, t, RecommendationStyle.Adventure)

        assertEquals("known", comfort.first())
        assertNotEquals("known", discovery.first())
        assertEquals("newartist", adventure.first())
    }

    @Test
    fun `each style has a genuinely different plan`() {
        val plans = RecommendationStyle.entries.map(StylePlan::of)
        assertEquals(plans.size, plans.map { it.graphHops to it.maxPerArtist to it.freshShareCeiling }.distinct().size)
        assertTrue(StylePlan.of(RecommendationStyle.Adventure).graphHops > StylePlan.of(RecommendationStyle.Comfort).graphHops)
        assertTrue(StylePlan.of(RecommendationStyle.DeepListening).artistRunLength > StylePlan.of(RecommendationStyle.Discovery).artistRunLength)
    }

    @Test
    fun `fresh share follows the style and, in Balanced, what new music has done for them`() {
        val base = 0.3
        val empty = TasteProfile.Empty
        assertTrue(DiscoveryMix.freshShare(base, RecommendationStyle.Comfort, empty) <= 0.15)
        assertTrue(DiscoveryMix.freshShare(base, RecommendationStyle.Adventure, empty) >= 0.6)
        assertEquals("no evidence, no change", base, DiscoveryMix.freshShare(base, RecommendationStyle.Balanced, empty), 1e-9)

        val keen = empty.copy(discovery = DiscoveryPersonality(0.9, 0.9, 0.2, 40))
        val shy = empty.copy(discovery = DiscoveryPersonality(0.1, 0.1, 0.9, 40))
        assertTrue(DiscoveryMix.freshShare(base, RecommendationStyle.Balanced, keen) > base)
        assertTrue(DiscoveryMix.freshShare(base, RecommendationStyle.Balanced, shy) < base)
    }

    @Test
    fun `explanations only state what the data supports`() {
        val graph = MusicGraph.build(
            listOf(
                ArtistEdgeRow("rock", "both", 8f, RelationKind.SimilarArtist.name, "Both"),
                ArtistEdgeRow("synth", "both", 8f, RelationKind.SimilarArtist.name, "Both"),
            ),
            knownNames = mapOf("rock" to "Rock Band", "synth" to "Synth Act"),
        )
        val t = taste()
        val c = RankContext(graph = graph, reach = graph.reach(mapOf("rock" to 1.0, "synth" to 1.0), 1))
        val bridged = Ranker.score(candidate("a", "both"), t, c)
        assertEquals(Reason.Connects("Rock Band", "Synth Act"), Explainer.reason(bridged, t, graph, emptyMap()))

        val because = candidate("b", "other").copy(hits = listOf(SourceHit(CandidateSource.RelatedSong, 0.5, viaSong = "seed")))
        val scored = Ranker.score(because, t, RankContext())
        assertEquals(Reason.BecauseSong("seed", "Seed Title"), Explainer.reason(scored, t, MusicGraph.Empty, mapOf("seed" to "Seed Title")))
        assertEquals("no title known, no claim", null, Explainer.reason(scored, t, MusicGraph.Empty, emptyMap()))

        val nothing = Ranker.score(candidate("c", "stranger"), t, RankContext())
        assertEquals(null, Explainer.reason(nothing, t, MusicGraph.Empty, emptyMap()))
    }

    @Test
    fun `a neutral snapshot changes nothing for shuffle or ranking factors`() {
        assertEquals(1.0, RecommendationEngine.shufflePreference("a", listOf("x"), IntelligenceSnapshot.Neutral), 0.0)
        assertEquals(1.0, RecommendationEngine.rankingFactor("a", listOf("x"), IntelligenceSnapshot.Neutral), 0.0)
        assertFalse(IntelligenceSnapshot.Neutral.isRejected("a", listOf("x")))
    }

    @Test
    fun `an unheard song in an enabled snapshot is neutral for the ranking factor`() {
        val snapshot = IntelligenceSnapshot.Neutral.copy(enabled = true, taste = taste())
        assertEquals(1.0, RecommendationEngine.rankingFactor("unknown", listOf("nobody"), snapshot), 1e-9)
    }

    @Test
    fun `shuffle prefers finished songs and buries rejected ones without removing them`() {
        val t = taste({
            copy(
                songOutcomes = listOf(SongOutcomeBucket("loved", 0, 10, 0, 0, 100, now), SongOutcomeBucket("skipped", 0, 0, 0, 10, 100, now)),
                feedback = listOf(MusicFeedback("song", "blocked", FeedbackKind.BlockSong.name, null, now, null)),
            )
        })
        val snapshot = IntelligenceSnapshot.Neutral.copy(enabled = true, taste = t)
        val loved = RecommendationEngine.shufflePreference("loved", listOf("x"), snapshot)
        val skipped = RecommendationEngine.shufflePreference("skipped", listOf("x"), snapshot)
        val blocked = RecommendationEngine.shufflePreference("blocked", listOf("x"), snapshot)
        assertTrue(loved > 1.0)
        assertTrue(skipped < 0.5)
        assertEquals(0.05, blocked, 0.0)
    }
}
