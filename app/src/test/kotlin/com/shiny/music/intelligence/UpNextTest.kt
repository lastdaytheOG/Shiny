package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistEdgeRow
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.MusicFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun radio(ids: List<Pair<String, String>>) = ids.mapIndexed { index, (id, artist) ->
        Candidate(
            id,
            id,
            listOf(ArtistRef(artist, artist)),
            hits = listOf(SourceHit(CandidateSource.YouTubeRadio, 1.0 - index.toDouble() / ids.size)),
        )
    }

    private fun snapshot(input: TasteInputs.() -> TasteInputs = { this }, confidence: Double = 0.6, graph: MusicGraph = MusicGraph.Empty) =
        IntelligenceSnapshot.Neutral.copy(
            enabled = true,
            taste = TasteProfileBuilder.build(TasteInputs(confidence = confidence, nowMs = now).input()),
            graph = graph,
        )

    private fun ids(list: List<Recommendation>) = list.map { it.song.songId }

    @Test
    fun `with no taste the provider's order mostly stands`() {
        val list = radio(listOf("a" to "A", "b" to "B", "c" to "C", "d" to "D"))
        assertEquals(listOf("a", "b", "c", "d"), ids(RecommendationEngine.upNext(null, list, snapshot(confidence = 0.0))))
    }

    @Test
    fun `the provider is a candidate list, not the final word`() {
        val list = radio(listOf("first" to "stranger", "second" to "other", "third" to "loved"))
        val loved = snapshot({ copy(artistStats = listOf(HomeArtistStat("loved", 60, 8, 20, now - day, 12))) })
        assertEquals("third", ids(RecommendationEngine.upNext(null, list, loved)).first())
    }

    @Test
    fun `a hidden artist is removed from radio and nothing else is lost`() {
        val list = radio(listOf("a" to "hidden", "b" to "fine", "c" to "fine2"))
        val snap = snapshot({ copy(feedback = listOf(MusicFeedback("artist", "hidden", FeedbackKind.HideArtist.name, null, now, null))) })
        val out = ids(RecommendationEngine.upNext(null, list, snap))
        assertFalse("a" in out)
        assertEquals(setOf("b", "c"), out.toSet())
    }

    @Test
    fun `everything hidden gives an empty page rather than a crash`() {
        val list = radio(listOf("a" to "hidden"))
        val snap = snapshot({ copy(feedback = listOf(MusicFeedback("artist", "hidden", FeedbackKind.HideArtist.name, null, now, null))) })
        assertTrue(RecommendationEngine.upNext(null, list, snap).isEmpty())
    }

    @Test
    fun `a page stays within its limit`() {
        val list = radio((0 until 30).map { "s$it" to "a$it" })
        assertEquals(7, RecommendationEngine.upNext(null, list, snapshot(), limit = 7).size)
    }

    @Test
    fun `next song follows a real relationship with what is playing`() {
        val graph = MusicGraph.build(listOf(ArtistEdgeRow("now", "linked", 8f, RelationKind.CoListen.name, "linked")))
        val current = Candidate("playing", "playing", listOf(ArtistRef("now", "now")))
        val page = (0 until 20).map { "s$it" to "stranger$it" }.toMutableList()
        page.add(2, "linked" to "linked")
        assertEquals("linked", ids(RecommendationEngine.upNext(current, radio(page), snapshot(graph = graph), limit = 1)).single())
    }

    @Test
    fun `artists heard moments ago wait their turn`() {
        val list = radio(listOf("a" to "recent") + (0 until 19).map { "s$it" to "other$it" })
        val plain = ids(RecommendationEngine.upNext(null, list, snapshot()))
        val remembering = ids(RecommendationEngine.upNext(null, list, snapshot(), recentArtists = listOf("recent")))
        assertEquals("a", plain.first())
        assertEquals("s0", remembering.first())
    }

    @Test
    fun `offline autoplay moves on from what it just played`() {
        val songs = (0 until 8).map { Candidate("s$it", "s$it", listOf(ArtistRef("a$it", "a$it")), hits = listOf(SourceHit(CandidateSource.OnDevice, 0.4))) }
        val first = ids(RecommendationEngine.upNext(null, songs, snapshot(), limit = 3))
        val heard = first.map { HomeSongStat(it, 2, 1, now - 90 * day, now - 3_600_000, 1, 0, 1, 0) }
        val second = ids(RecommendationEngine.upNext(null, songs, snapshot({ copy(songStats = heard) }), limit = 3))
        assertTrue("$first then $second", first.toSet().intersect(second.toSet()).isEmpty())
    }

    @Test
    fun `a song nobody can explain carries no reason`() {
        val out = RecommendationEngine.upNext(null, radio(listOf("a" to "A")), snapshot())
        assertEquals(null, out.single().reason)
        assertEquals(CandidateSource.YouTubeRadio, out.single().source)
    }

    private fun scored(id: String, score: Double, known: Boolean) = ScoredCandidate(
        Candidate(id, id, listOf(ArtistRef(id, id))),
        score,
        ScoreBreakdown(newArtist = if (known) 0.0 else 1.0),
        null,
    )

    @Test
    fun `the blend gives new artists the asked-for share, spread through the list`() {
        val known = (0 until 20).map { scored("k$it", 2.0, true) }
        val strangers = (0 until 20).map { scored("n$it", 1.0, false) }
        val out = DiscoveryMix.blend(known, strangers, 0.3, 10).map { it.candidate.songId }
        assertEquals(10, out.size)
        assertEquals(3, out.count { it.startsWith("n") })
        assertTrue("the first slot goes to something known: $out", out.first().startsWith("k"))
        assertFalse("not bunched at the end: $out", out.takeLast(3).all { it.startsWith("n") })
    }

    @Test
    fun `the blend fills from the other side when one runs short`() {
        val known = listOf(scored("k", 2.0, true))
        val strangers = (0 until 9).map { scored("n$it", 1.0, false) }
        assertEquals(10, DiscoveryMix.blend(known, strangers, 0.1, 10).size)
        assertEquals(1, DiscoveryMix.blend(known, emptyList(), 0.5, 10).size)
        assertEquals(9, DiscoveryMix.blend(emptyList(), strangers, 0.1, 10).size)
    }

    @Test
    fun `a listener who keeps enjoying new music gets more of it, one who skips it gets less`() {
        val base = TasteProfile.Empty
        val keen = base.copy(discovery = DiscoveryPersonality(0.9, 0.9, 0.2, 40))
        val shy = base.copy(discovery = DiscoveryPersonality(0.1, 0.1, 0.9, 40))
        val neutral = DiscoveryMix.newArtistShare(RecommendationStyle.Balanced, base)
        assertTrue(DiscoveryMix.newArtistShare(RecommendationStyle.Balanced, keen) > neutral)
        assertTrue(DiscoveryMix.newArtistShare(RecommendationStyle.Balanced, shy) < neutral)
        assertEquals(
            "a chosen style is respected as is",
            StylePlan.of(RecommendationStyle.Adventure).newArtistShare,
            DiscoveryMix.newArtistShare(RecommendationStyle.Adventure, shy),
            0.0,
        )
    }

    @Test
    fun `a graph-sourced candidate is labelled by how far away it is`() {
        val near = GraphReach("x", 1.0, 1, listOf("a"), "a")
        val far = GraphReach("x", 1.0, 3, listOf("a"), "b")
        val bridge = GraphReach("x", 1.0, 2, listOf("a", "b"), "b")
        assertEquals(CandidateSource.GraphNear, RecommendationEngine.graphSources(near).first)
        assertEquals(CandidateSource.GraphFar, RecommendationEngine.graphSources(far).first)
        assertEquals(CandidateSource.GraphBridge, RecommendationEngine.graphSources(bridge).first)
        assertNotEquals(RecommendationEngine.graphSources(near).first, RecommendationEngine.graphSources(far).first)
    }

    @Test
    fun `seeds leave out hidden and less-like artists`() {
        val taste = TasteProfileBuilder.build(
            TasteInputs(
                nowMs = now,
                artistStats = listOf(
                    HomeArtistStat("keep", 20, 5, 9, now - day, 4),
                    HomeArtistStat("hidden", 20, 5, 9, now - day, 4),
                    HomeArtistStat("less", 20, 5, 9, now - day, 4),
                ),
                feedback = listOf(
                    MusicFeedback("artist", "hidden", FeedbackKind.HideArtist.name, null, now, null),
                    MusicFeedback("artist", "less", FeedbackKind.LessLikeThis.name, null, now, null),
                ),
            ),
        )
        assertEquals(setOf("keep"), RecommendationEngine.seedWeights(taste).keys)
    }
}
