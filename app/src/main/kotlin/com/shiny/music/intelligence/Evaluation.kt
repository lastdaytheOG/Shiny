package com.shiny.music.intelligence

import com.shiny.music.db.entities.SurfaceListenRow
import com.shiny.music.db.entities.SurfaceMetricRow
import kotlin.math.ln

data class SurfaceReport(
    val surface: String,
    val impressions: Int,
    val selectionRate: Double?,
    val listens: Int,
    val meaningfulRate: Double?,
    val completionRate: Double?,
    val skipRate: Double?,
    val replayRate: Double?,
    val discoverySuccess: Double?,
    val distinctArtists: Int,
    val repeatedRate: Double?,
)

object RecommendationMetrics {
    private fun rate(part: Int, whole: Int): Double? = if (whole <= 0) null else part.toDouble() / whole

    fun reports(shown: List<SurfaceMetricRow>, listens: List<SurfaceListenRow>): List<SurfaceReport> {
        val byShown = shown.associateBy { it.surface }
        val byListen = listens.associateBy { it.surface }
        return (byShown.keys + byListen.keys).map { surface ->
            val s = byShown[surface]
            val l = byListen[surface]
            val counted = l?.let { it.listens - it.accidental - it.interrupted } ?: 0
            SurfaceReport(
                surface = surface,
                impressions = s?.shown ?: 0,
                selectionRate = s?.let { rate(it.selected, it.shown) },
                listens = l?.listens ?: 0,
                meaningfulRate = l?.let { rate(it.meaningful + it.completed, counted) },
                completionRate = l?.let { rate(it.completed, counted) },
                skipRate = l?.let { rate(it.skipped, counted) },
                replayRate = l?.let { rate(it.replays, it.listens) },
                discoverySuccess = l?.let { rate(it.firstPositive, it.firstListens) },
                distinctArtists = l?.distinctArtists ?: 0,
                repeatedRate = s?.let { rate(it.shown - it.distinctSongs, it.shown) },
            )
        }.sortedByDescending { it.impressions + it.listens }
    }
}

data class OfflineScore(
    val hitRate: Double,
    val ndcg: Double,
    val artistDiversity: Double,
    val newArtistShare: Double,
    val newArtistHits: Double,
    val cases: Int,
) {
    val newArtistPrecision: Double get() = if (newArtistShare <= 0.0) 0.0 else newArtistHits / newArtistShare

    override fun toString(): String =
        "hit@k=%.3f ndcg@k=%.3f artists=%.3f new=%.3f newHits=%.3f newPrecision=%.3f n=%d".format(
            hitRate, ndcg, artistDiversity, newArtistShare, newArtistHits, newArtistPrecision, cases,
        )
}

object OfflineEvaluator {
    data class Case(
        val recommended: List<Candidate>,
        val heldOut: Set<String>,
        val knownArtists: Set<String>,
    )

    fun score(cases: List<Case>, k: Int): OfflineScore {
        if (cases.isEmpty()) return OfflineScore(0.0, 0.0, 0.0, 0.0, 0.0, 0)
        var hits = 0
        var ndcg = 0.0
        var diversity = 0.0
        var new = 0.0
        var newHits = 0.0
        for (case in cases) {
            val top = case.recommended.take(k)
            val idealHits = minOf(case.heldOut.size, k)
            var gain = 0.0
            top.forEachIndexed { index, candidate ->
                if (candidate.songId in case.heldOut) gain += 1.0 / (ln(index + 2.0) / ln(2.0))
            }
            val ideal = (0 until idealHits).sumOf { 1.0 / (ln(it + 2.0) / ln(2.0)) }
            if (ideal > 0.0) ndcg += gain / ideal
            if (top.any { it.songId in case.heldOut }) hits++
            if (top.isNotEmpty()) {
                diversity += top.map { it.leadArtist }.distinct().size.toDouble() / top.size
                new += top.count { it.leadArtist !in case.knownArtists }.toDouble() / top.size
                newHits += top.count { it.leadArtist !in case.knownArtists && it.songId in case.heldOut }.toDouble() / top.size
            }
        }
        val n = cases.size.toDouble()
        return OfflineScore(hits / n, ndcg / n, diversity / n, new / n, newHits / n, cases.size)
    }
}
