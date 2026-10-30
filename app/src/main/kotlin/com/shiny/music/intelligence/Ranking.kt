package com.shiny.music.intelligence

import com.shiny.music.db.entities.CandidateSource
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class ScoreBreakdown(
    val affinity: Double = 0.0,
    val familiarity: Double = 0.0,
    val satisfaction: Double = 0.0,
    val novelty: Double = 0.0,
    val newArtist: Double = 0.0,
    val graph: Double = 0.0,
    val sourceQuality: Double = 0.0,
    val sourceStrength: Double = 0.0,
    val fatigue: Double = 0.0,
    val rejection: Double = 0.0,
    val ignored: Double = 0.0,
    val context: Double = 0.0,
    val sound: Double = 0.0,
    val embedding: Double = 0.0,
) {
    fun total(w: RankingWeights): Double =
        w.affinity * affinity +
            w.songFamiliarity * familiarity +
            w.satisfaction * satisfaction +
            w.novelty * novelty +
            w.newArtist * newArtist +
            w.graph * graph +
            w.sourceQuality * sourceQuality +
            w.sourceStrength * sourceStrength -
            w.fatigue * fatigue -
            w.rejection * rejection -
            w.ignored * ignored +
            w.context * context +
            w.sound * sound +
            w.embedding * embedding
}

data class ScoredCandidate(
    val candidate: Candidate,
    val score: Double,
    val breakdown: ScoreBreakdown,
    val reach: GraphReach?,
)

data class RankContext(
    val style: RecommendationStyle = RecommendationStyle.Balanced,
    val current: Candidate? = null,
    val recentArtists: List<String> = emptyList(),
    val reach: Map<String, GraphReach> = emptyMap(),
    val graph: MusicGraph = MusicGraph.Empty,
    val embeddings: ArtistEmbeddings = ArtistEmbeddings.Empty,
    val tasteVector: FloatArray? = null,
    val badTransitions: Map<String, Double> = emptyMap(),
) {
    val plan: StylePlan get() = StylePlan.of(style)
}

object Ranker {
    private const val SONG_PRIOR_STRENGTH = 3.0
    private const val ARTIST_PRIOR_STRENGTH = 6.0
    private const val SOURCE_PRIOR_STRENGTH = 12.0
    private const val FATIGUE_HALF_HOURS = 36.0
    private const val MAX_REACH_NORMALISER = 1.5

    fun sourcePrior(taste: TasteProfile, source: CandidateSource?): Double {
        if (source == null) return 0.5
        return (taste.sources[source.name] ?: Beta(0.0, 0.0)).mean(source.defaultQuality, SOURCE_PRIOR_STRENGTH)
    }

    fun predictedSatisfaction(candidate: Candidate, taste: TasteProfile): Double {
        val sourceMean = sourcePrior(taste, candidate.source)
        val artistEvidence = candidate.artistIds.mapNotNull(taste::artist)
        val artistPositive = artistEvidence.sumOf { it.positive + it.plays * 0.5 }
        val artistNegative = artistEvidence.sumOf { it.negative }
        val artistMean = (artistPositive + sourceMean * ARTIST_PRIOR_STRENGTH) /
            (artistPositive + artistNegative + ARTIST_PRIOR_STRENGTH)
        val song = taste.song(candidate.songId) ?: return artistMean
        val songPositive = song.positive + song.plays * 0.5
        return (songPositive + artistMean * SONG_PRIOR_STRENGTH) / (songPositive + song.negative + SONG_PRIOR_STRENGTH)
    }

    fun score(candidate: Candidate, taste: TasteProfile, context: RankContext): ScoredCandidate {
        val plan = context.plan
        val artists = candidate.artistIds.mapNotNull(taste::artist)
        val song = taste.song(candidate.songId)
        val lead = artists.maxByOrNull { it.strength }

        val personal = 0.3 + 0.7 * taste.confidence.coerceIn(0.0, 1.0)
        val affinity = (lead?.let { 0.6 * it.longTerm + 0.4 * it.shortTerm } ?: 0.0).let { base ->
            val boosted = if (candidate.artistIds.any { it in taste.boostedArtists }) base + 0.35 else base
            val saved = if (song?.saved == true) boosted + 0.2 else boosted
            saved.coerceIn(0.0, 1.2)
        }

        val artistHeard = artists.any { it.heard }
        val songHeard = song?.heard == true
        val novelty = when {
            songHeard -> 0.0
            artistHeard -> 0.5
            else -> 1.0
        } * (0.5 + taste.discovery.appetite)

        val reach = candidate.artistIds.mapNotNull { context.reach[it] }.maxByOrNull { it.score }
        val graph = reach?.let { min(1.0, it.score / MAX_REACH_NORMALISER) + if (it.isBridge) 0.25 else 0.0 } ?: 0.0

        val fatigue = song?.let { fatigueOf(it, taste.nowMs) } ?: 0.0
        val fadedOut = if (song?.fadedOut == true) 0.6 else 0.0
        val lessLike = if (candidate.artistIds.any { it in taste.lessLikeArtists }) 0.6 else 0.0
        val lessLikeNeighbour = lessLikeNeighbourPenalty(candidate, taste, context.graph)
        val rejection = (max((lead?.rejection ?: 0.0) * 0.8, fadedOut) + lessLike + lessLikeNeighbour).coerceIn(0.0, 1.5)

        val missed = candidate.hits.fold(1.0) { acc, hit -> acc * (1.0 - hit.strength.coerceIn(0.0, 1.0)) }
        val agreement = 1.0 - missed
        val source = sourcePrior(taste, candidate.source) - (candidate.source?.defaultQuality ?: 0.5)

        val breakdown = ScoreBreakdown(
            affinity = affinity * personal,
            familiarity = (song?.familiarity ?: 0.0) * personal,
            satisfaction = predictedSatisfaction(candidate, taste) - 0.5,
            novelty = novelty,
            newArtist = if (artistHeard || candidate.artists.isEmpty()) 0.0 else 1.0,
            graph = graph,
            sourceQuality = source,
            sourceStrength = agreement * (1.0 - 0.4 * taste.confidence.coerceIn(0.0, 1.0)),
            fatigue = fatigue,
            rejection = rejection,
            ignored = taste.ignored[candidate.songId] ?: 0.0,
            context = contextOf(candidate, taste),
            sound = soundOf(candidate, taste, context),
            embedding = embeddingOf(candidate, context),
        )
        return ScoredCandidate(candidate, breakdown.total(plan.weights), breakdown, reach)
    }

    private fun lessLikeNeighbourPenalty(candidate: Candidate, taste: TasteProfile, graph: MusicGraph): Double {
        if (taste.lessLikeArtists.isEmpty()) return 0.0
        val closest = candidate.artistIds.maxOfOrNull { id ->
            taste.lessLikeArtists.maxOfOrNull { other -> graph.strength(id, other) } ?: 0.0
        } ?: 0.0
        return closest * 0.35
    }

    private fun fatigueOf(song: SongTaste, nowMs: Long): Double {
        if (song.lastHeardMs <= 0L) return 0.0
        val hours = max(0L, nowMs - song.lastHeardMs) / 3_600_000.0
        val recently = exp(-hours / FATIGUE_HALF_HOURS)
        val heavy = min(1.0, song.weekPlays / 12.0)
        return (recently * 0.9 + heavy * 0.3).coerceIn(0.0, 1.0)
    }

    private fun contextOf(candidate: Candidate, taste: TasteProfile): Double {
        val lift = candidate.artistIds.mapNotNull { taste.daypartLift[it] }.maxOrNull() ?: 0.0
        val searched = if (taste.recentSearches.isEmpty()) 0.0 else {
            val names = candidate.artists.map { it.name.lowercase() }.filter { it.length >= 3 }
            if (taste.recentSearches.any { query -> names.any { query.contains(it) } }) 0.5 else 0.0
        }
        return (lift + searched).coerceIn(-1.0, 1.0)
    }

    private fun soundOf(candidate: Candidate, taste: TasteProfile, context: RankContext): Double {
        val feature = taste.features[candidate.songId]?.takeIf { it.confidence >= 0.3f } ?: return 0.0
        val current = context.current?.let { taste.features[it.songId] }?.takeIf { it.confidence >= 0.3f }
        val target = current?.bpm?.toDouble() ?: taste.sound?.bpm ?: return 0.0
        val spread = max(8.0, taste.sound?.bpmSpread ?: 14.0)
        val gap = minOf(abs(feature.bpm - target), abs(feature.bpm * 2 - target), abs(feature.bpm / 2 - target))
        return exp(-gap / spread) * 2.0 - 1.0
    }

    private fun embeddingOf(candidate: Candidate, context: RankContext): Double {
        val taste = context.tasteVector ?: return 0.0
        val best = candidate.artistIds.maxOfOrNull { context.embeddings.similarity(it, taste) } ?: return 0.0
        return best.coerceIn(0.0, 1.0)
    }

    fun rank(candidates: List<Candidate>, taste: TasteProfile, context: RankContext): List<ScoredCandidate> =
        CandidateAdapters.merge(candidates)
            .filterNot { taste.isRejected(it.songId, it.artistIds) }
            .map { score(it, taste, context) }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.candidate.songId })
}

object DiscoveryMix {
    fun newArtistShare(style: RecommendationStyle, taste: TasteProfile): Double {
        val plan = StylePlan.of(style)
        if (style != RecommendationStyle.Balanced || taste.discovery.evidence == 0) return plan.newArtistShare
        val trust = min(1.0, taste.discovery.evidence / 20.0)
        return (plan.newArtistShare * (1.0 + (taste.discovery.appetite - 0.5) * 2.0 * trust)).coerceIn(0.05, 0.6)
    }

    fun blend(known: List<ScoredCandidate>, strangers: List<ScoredCandidate>, share: Double, limit: Int): List<ScoredCandidate> {
        val wanted = (limit * share).toInt().coerceAtMost(strangers.size)
        val takeKnown = (limit - wanted).coerceAtMost(known.size)
        val takeStrangers = (limit - takeKnown).coerceAtMost(strangers.size)
        val a = known.take(takeKnown)
        val b = strangers.take(takeStrangers)
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        val out = ArrayList<ScoredCandidate>(a.size + b.size)
        var ai = 0
        var bi = 0
        while (ai < a.size || bi < b.size) {
            val dueStranger = bi < b.size && (ai >= a.size || bi.toDouble() / b.size < ai.toDouble() / a.size)
            if (dueStranger) out += b[bi++] else out += a[ai++]
        }
        return out
    }

    fun freshShare(base: Double, style: RecommendationStyle, taste: TasteProfile): Double {
        val plan = StylePlan.of(style)
        val personal = if (style == RecommendationStyle.Balanced && taste.discovery.evidence > 0) {
            val confidenceInEstimate = min(1.0, taste.discovery.evidence / 20.0)
            1.0 + (taste.discovery.appetite - 0.5) * confidenceInEstimate
        } else {
            1.0
        }
        return (base * plan.freshShareScale * personal).coerceIn(plan.freshShareFloor, plan.freshShareCeiling)
    }
}
