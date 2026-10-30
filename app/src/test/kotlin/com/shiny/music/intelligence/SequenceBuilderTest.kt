package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistEdgeRow
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.SongFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceBuilderTest {
    private val now = 1_800_000_000_000L

    private fun candidate(id: String, artist: String, album: String? = null, year: Int? = null) =
        Candidate(id, id, listOf(ArtistRef(artist, artist)), album, year, 200, hits = listOf(SourceHit(CandidateSource.YouTubeRadio, 0.5)))

    private fun scored(candidates: List<Candidate>, taste: TasteProfile, context: RankContext) =
        candidates.map { ScoredCandidate(it, 0.5, ScoreBreakdown(), null) }.also { assertTrue(taste.nowMs >= 0 && context.plan.diversity >= 0) }

    private val empty = TasteProfile.Empty.copy(nowMs = now)

    @Test
    fun `no artist plays twice in a row while others are available`() {
        val songs = (0 until 12).map { candidate("s$it", "a${it % 3}") }
        val context = RankContext()
        val out = SequenceBuilder.arrange(null, scored(songs, empty, context), empty, context).map { it.candidate }
        assertEquals(12, out.size)
        assertTrue(SequenceBuilder.worstRepeatGap(out) >= 2)
    }

    @Test
    fun `the strongest song is not always next, what fits the last song comes first`() {
        val graph = MusicGraph.build(listOf(ArtistEdgeRow("now", "linked", 8f, RelationKind.CoListen.name, "linked")))
        val context = RankContext(graph = graph)
        val current = candidate("playing", "now")
        val fits = ScoredCandidate(candidate("fits", "linked"), 0.5, ScoreBreakdown(), null)
        val best = ScoredCandidate(candidate("best", "unrelated"), 0.8, ScoreBreakdown(), null)
        val next = SequenceBuilder.arrange(current, listOf(best, fits), empty, context).first()
        assertEquals("fits", next.candidate.songId)
    }

    @Test
    fun `a big enough quality gap still wins over a smooth transition`() {
        val graph = MusicGraph.build(listOf(ArtistEdgeRow("now", "linked", 8f, RelationKind.CoListen.name, "linked")))
        val context = RankContext(graph = graph)
        val current = candidate("playing", "now")
        val fits = ScoredCandidate(candidate("fits", "linked"), 0.2, ScoreBreakdown(), null)
        val best = ScoredCandidate(candidate("best", "unrelated"), 2.0, ScoreBreakdown(), null)
        assertEquals("best", SequenceBuilder.arrange(current, listOf(fits, best), empty, context).first().candidate.songId)
    }

    @Test
    fun `deep listening stays with an artist, discovery moves on`() {
        val songs = listOf(candidate("same1", "a"), candidate("same2", "a"), candidate("other", "b"))
        fun firstAfter(style: RecommendationStyle): String {
            val context = RankContext(style = style)
            val current = candidate("playing", "a")
            return SequenceBuilder.arrange(current, scored(songs, empty, context), empty, context).first().candidate.leadArtist
        }
        assertEquals("a", firstAfter(RecommendationStyle.DeepListening))
        assertEquals("b", firstAfter(RecommendationStyle.Discovery))
    }

    @Test
    fun `a run is ended once it reaches the style's length`() {
        val songs = (0 until 6).map { candidate("a$it", "a") } + (0 until 6).map { candidate("b$it", "b") }
        val context = RankContext(style = RecommendationStyle.DeepListening)
        val out = SequenceBuilder.arrange(candidate("start", "a"), scored(songs, empty, context), empty, context).map { it.candidate.leadArtist }
        val longest = out.fold(listOf(0 to "")) { acc, a ->
            val (n, last) = acc.last()
            acc + ((if (a == last) n + 1 else 1) to a)
        }.maxOf { it.first }
        assertTrue("longest run $longest in $out", longest <= StylePlan.of(RecommendationStyle.DeepListening).artistRunLength + 1)
    }

    @Test
    fun `measured tempo steers transitions only between measured songs`() {
        val taste = empty.copy(
            features = listOf(
                SongFeature("now", 120f, 0.9f, null, null, null, null, null),
                SongFeature("near", 122f, 0.9f, null, null, null, null, null),
                SongFeature("far", 175f, 0.9f, null, null, null, null, null),
            ).associateBy { it.songId },
        )
        val context = RankContext()
        val current = candidate("now", "x")
        val near = SequenceBuilder.transition(current, candidate("near", "y"), 1, 0, taste, context)
        val far = SequenceBuilder.transition(current, candidate("far", "y"), 1, 0, taste, context)
        val unmeasured = SequenceBuilder.transition(current, candidate("unknown", "y"), 1, 0, taste, context)
        assertTrue(near > unmeasured)
        assertTrue(far < unmeasured)
    }

    @Test
    fun `measured energy keeps the mood steady, and unknown energy changes nothing`() {
        fun feature(id: String, energy: Float?) = SongFeature(id, 0f, 0f, energy, null, null, null, null)
        val taste = empty.copy(
            features = listOf(feature("now", 0.7f), feature("close", 0.72f), feature("far", 0.1f), feature("unknown", null)).associateBy { it.songId },
        )
        val context = RankContext()
        val current = candidate("now", "x")
        val close = SequenceBuilder.transition(current, candidate("close", "y"), 1, 0, taste, context)
        val far = SequenceBuilder.transition(current, candidate("far", "y"), 1, 0, taste, context)
        val unknown = SequenceBuilder.transition(current, candidate("unknown", "y"), 1, 0, taste, context)
        assertTrue(close > unknown)
        assertTrue(far < unknown)
    }

    @Test
    fun `half time counts as the same tempo`() {
        val taste = empty.copy(
            features = listOf(
                SongFeature("now", 160f, 0.9f, null, null, null, null, null),
                SongFeature("half", 80f, 0.9f, null, null, null, null, null),
                SongFeature("off", 120f, 0.9f, null, null, null, null, null),
            ).associateBy { it.songId },
        )
        val context = RankContext()
        val current = candidate("now", "x")
        assertTrue(
            SequenceBuilder.transition(current, candidate("half", "y"), 1, 0, taste, context) >
                SequenceBuilder.transition(current, candidate("off", "y"), 1, 0, taste, context),
        )
    }

    @Test
    fun `after a stretch of new music a familiar song is due`() {
        val taste = empty.copy(
            songs = mapOf("known" to SongTaste("known", 5, 0.6, 2.0, 0.0, now - 1_000_000_000, 0, false, false)),
        )
        val context = RankContext()
        val current = candidate("c", "x")
        val due = SequenceBuilder.transition(current, candidate("known", "y"), 1, 4, taste, context)
        val notDue = SequenceBuilder.transition(current, candidate("known", "y"), 1, 0, taste, context)
        assertTrue(due > notDue)
    }

    @Test
    fun `a transition the listener keeps skipping is avoided`() {
        val context = RankContext(badTransitions = mapOf("x>bad" to 1.0))
        val current = candidate("c", "x")
        val bad = SequenceBuilder.transition(current, candidate("a", "bad"), 1, 0, empty, context)
        val fine = SequenceBuilder.transition(current, candidate("b", "fine"), 1, 0, empty, context)
        assertTrue(bad < fine)
    }

    @Test
    fun `a graph-linked sequence measures as more coherent than a random one`() {
        val graph = MusicGraph.build(
            listOf(
                ArtistEdgeRow("a", "b", 8f, RelationKind.CoListen.name, "b"),
                ArtistEdgeRow("b", "c", 8f, RelationKind.CoListen.name, "c"),
            ),
        )
        val pool = listOf(candidate("1", "c"), candidate("2", "z"), candidate("3", "b"), candidate("4", "y"))
        val context = RankContext(graph = graph)
        val arranged = SequenceBuilder.arrange(candidate("start", "a"), scored(pool, empty, context), empty, context).map { it.candidate }
        val coherent = SequenceBuilder.coherence(listOf(candidate("start", "a")) + arranged, graph)
        val shuffled = SequenceBuilder.coherence(listOf(candidate("start", "a")) + pool, graph)
        assertTrue("arranged $coherent vs original $shuffled", coherent > shuffled)
        assertNotEquals(pool.map { it.songId }, arranged.map { it.songId })
    }

    @Test
    fun `diversity caps an artist per style and keeps score order otherwise`() {
        fun item(id: String, artist: String, score: Double) = ScoredCandidate(candidate(id, artist), score, ScoreBreakdown(), null)
        val ranked = listOf(item("a1", "a", 3.0), item("a2", "a", 2.9), item("a3", "a", 2.8), item("b1", "b", 1.0))

        val discovery = DiversityReranker.rerank(ranked, 4, StylePlan.of(RecommendationStyle.Discovery))
        assertEquals(listOf("a1", "b1"), discovery.map { it.candidate.songId })

        val deep = DiversityReranker.rerank(ranked, 4, StylePlan.of(RecommendationStyle.DeepListening))
        assertEquals(4, deep.size)
        assertEquals("a1", deep.first().candidate.songId)
    }

    @Test
    fun `an empty list stays empty`() {
        val context = RankContext()
        assertTrue(SequenceBuilder.arrange(null, emptyList(), empty, context).isEmpty())
    }

    @Test
    fun `songs without artist data still arrange`() {
        val context = RankContext()
        val bare = listOf(Candidate("x", "x", emptyList()), Candidate("y", "y", emptyList()))
        val out = SequenceBuilder.arrange(null, bare.map { ScoredCandidate(it, 0.1, ScoreBreakdown(), null) }, empty, context)
        assertEquals(2, out.size)
    }

    @Test
    fun `a heard song counts as familiar via its stats`() {
        val stats = HomeSongStat("s", 3, 1, 0, 0, 0, 0, 0, 0)
        val taste = TasteProfileBuilder.build(TasteInputs(songStats = listOf(stats), nowMs = now))
        assertTrue(taste.song("s")!!.heard)
    }
}
