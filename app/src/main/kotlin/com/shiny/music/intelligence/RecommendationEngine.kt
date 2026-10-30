package com.shiny.music.intelligence

import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.HomeAlbumStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.home.Daypart
import kotlin.math.ln

class HomeReads(
    val daypart: Daypart,
    val songStats: List<HomeSongStat>,
    val artistStats: List<HomeArtistStat>,
    val albumStats: List<HomeAlbumStat>,
    val daypartDays: Int,
)

data class IntelligenceSnapshot(
    val enabled: Boolean,
    val style: RecommendationStyle,
    val taste: TasteProfile,
    val graph: MusicGraph,
    val reach: Map<String, GraphReach>,
    val embeddings: ArtistEmbeddings,
    val tasteVector: FloatArray?,
    val badTransitions: Map<String, Double>,
    val homeReads: HomeReads? = null,
) {
    fun rankContext(current: Candidate? = null, recentArtists: List<String> = emptyList()) = RankContext(
        style = style,
        current = current,
        recentArtists = recentArtists,
        reach = reach,
        graph = graph,
        embeddings = embeddings,
        tasteVector = tasteVector,
        badTransitions = badTransitions,
    )

    fun isRejected(songId: String, artistIds: Collection<String>): Boolean =
        enabled && taste.isRejected(songId, artistIds)

    companion object {
        val Neutral = IntelligenceSnapshot(
            enabled = false,
            style = RecommendationStyle.Balanced,
            taste = TasteProfile.Empty,
            graph = MusicGraph.Empty,
            reach = emptyMap(),
            embeddings = ArtistEmbeddings.Empty,
            tasteVector = null,
            badTransitions = emptyMap(),
        )
    }
}

object RecommendationEngine {
    fun discover(
        candidates: List<Candidate>,
        snapshot: IntelligenceSnapshot,
        songTitles: Map<String, String>,
        limit: Int,
    ): List<Recommendation> {
        val context = snapshot.rankContext()
        val ranked = Ranker.rank(candidates, snapshot.taste, context)
        val (strangers, known) = ranked.partition { it.breakdown.newArtist > 0.0 }
        val chosen = DiscoveryMix.blend(
            DiversityReranker.rerank(known, limit, context.plan),
            DiversityReranker.rerank(strangers, limit, context.plan),
            DiscoveryMix.newArtistShare(snapshot.style, snapshot.taste),
            limit,
        )
        return chosen.map { recommendation(it, snapshot, songTitles) }
    }

    fun upNext(
        current: Candidate?,
        candidates: List<Candidate>,
        snapshot: IntelligenceSnapshot,
        recentArtists: List<String> = emptyList(),
        limit: Int = candidates.size,
    ): List<Recommendation> {
        val context = snapshot.rankContext(current, recentArtists)
        val ranked = Ranker.rank(candidates, snapshot.taste, context)
        return SequenceBuilder.arrange(current, ranked, snapshot.taste, context, limit, recentArtists)
            .map { recommendation(it, snapshot, emptyMap()) }
    }

    private fun recommendation(scored: ScoredCandidate, snapshot: IntelligenceSnapshot, titles: Map<String, String>) = Recommendation(
        song = scored.candidate,
        score = scored.score,
        breakdown = scored.breakdown,
        reason = Explainer.reason(scored, snapshot.taste, snapshot.graph, titles),
        source = scored.candidate.source,
    )

    fun rankingFactor(songId: String, artistIds: Collection<String>, snapshot: IntelligenceSnapshot): Double {
        if (!snapshot.enabled) return 1.0
        val probe = Candidate(songId, "", artistIds.map { ArtistRef(it, "") })
        val satisfaction = Ranker.predictedSatisfaction(probe, snapshot.taste)
        val song = snapshot.taste.song(songId)
        val faded = if (song?.fadedOut == true) 0.4 else 1.0
        return (satisfaction / 0.5 * faded).coerceIn(0.2, 1.3)
    }

    fun shufflePreference(songId: String, artistIds: Collection<String>, snapshot: IntelligenceSnapshot): Double {
        if (!snapshot.enabled) return 1.0
        val taste = snapshot.taste
        if (songId in taste.blockedSongs || artistIds.any { it in taste.hiddenArtists }) return 0.05
        if (songId in taste.softRejectedSongs) return 0.15
        val song = taste.song(songId)
        val base = rankingFactor(songId, artistIds, snapshot)
        val lessLike = if (artistIds.any { it in taste.lessLikeArtists }) 0.5 else 1.0
        val more = if (artistIds.any { it in taste.boostedArtists }) 1.4 else 1.0
        val novelty = if (song?.heard != true) 1.0 + (taste.discovery.appetite - 0.5) * 0.6 else 1.0
        return (base * lessLike * more * novelty).coerceIn(0.05, 2.0)
    }

    fun seedWeights(taste: TasteProfile, limit: Int = 24): Map<String, Double> =
        taste.artists.values
            .asSequence()
            .filter { it.artistId !in taste.hiddenArtists && it.artistId !in taste.lessLikeArtists }
            .map { it.artistId to it.strength * (1.0 - it.rejection) * if (it.artistId in taste.boostedArtists) 1.4 else 1.0 }
            .filter { it.second > 0.05 }
            .sortedByDescending { it.second }
            .take(limit)
            .toMap()

    fun graphSources(reach: GraphReach): Pair<CandidateSource, Double> {
        val source = when {
            reach.isBridge -> CandidateSource.GraphBridge
            reach.hops <= 1 -> CandidateSource.GraphNear
            else -> CandidateSource.GraphFar
        }
        return source to (ln(1.0 + reach.score) / ln(3.0)).coerceIn(0.0, 1.0)
    }
}
