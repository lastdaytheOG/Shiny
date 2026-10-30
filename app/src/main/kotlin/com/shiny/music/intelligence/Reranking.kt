package com.shiny.music.intelligence

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

object DiversityReranker {
    fun rerank(ranked: List<ScoredCandidate>, limit: Int, plan: StylePlan): List<ScoredCandidate> {
        if (ranked.size <= 1) return ranked.take(limit)
        val remaining = ranked.toMutableList()
        val chosen = ArrayList<ScoredCandidate>(minOf(limit, ranked.size))
        val artistCount = HashMap<String, Int>()
        val albumCount = HashMap<String, Int>()
        val sourceCount = HashMap<String, Int>()
        while (chosen.size < limit && remaining.isNotEmpty()) {
            var bestIndex = -1
            var bestValue = Double.NEGATIVE_INFINITY
            for (index in remaining.indices) {
                val item = remaining[index]
                val artist = item.candidate.leadArtist
                if ((artistCount[artist] ?: 0) >= plan.maxPerArtist) continue
                val album = item.candidate.albumId
                val value = item.score -
                    plan.diversity * (artistCount[artist] ?: 0) -
                    plan.diversity * 0.5 * (album?.let { albumCount[it] } ?: 0) -
                    plan.diversity * 0.1 * (item.candidate.source?.name?.let { sourceCount[it] } ?: 0)
                if (value > bestValue) {
                    bestValue = value
                    bestIndex = index
                }
            }
            if (bestIndex < 0) break
            val pick = remaining.removeAt(bestIndex)
            chosen += pick
            artistCount.merge(pick.candidate.leadArtist, 1, Int::plus)
            pick.candidate.albumId?.let { albumCount.merge(it, 1, Int::plus) }
            pick.candidate.source?.name?.let { sourceCount.merge(it, 1, Int::plus) }
        }
        return chosen
    }
}

object SequenceBuilder {
    private const val TEMPO_TOLERANCE = 12.0

    fun transition(
        from: Candidate,
        to: Candidate,
        run: Int,
        sinceFamiliar: Int,
        taste: TasteProfile,
        context: RankContext,
    ): Double {
        val plan = context.plan
        val sameArtist = to.artistIds.any { it in from.artistIds }
        var value = 0.0
        value += if (sameArtist) {
            if (run >= plan.artistRunLength) -1.2 else plan.sameArtistBonus
        } else {
            val link = from.artistIds.maxOfOrNull { a -> to.artistIds.maxOfOrNull { b -> context.graph.strength(a, b) } ?: 0.0 } ?: 0.0
            if (link > 0.0) {
                plan.relatedArtistBonus * link
            } else if (hasEvidenceOf(to, taste)) {
                -plan.abruptPenalty * 0.4
            } else {
                -plan.abruptPenalty + plan.unexpectedBonus
            }
        }
        val learned = from.artistIds.maxOfOrNull { a -> to.artistIds.maxOfOrNull { b -> context.badTransitions["$a>$b"] ?: 0.0 } ?: 0.0 } ?: 0.0
        value -= learned * 0.6
        value += tempoFit(from, to, taste) * (0.15 + plan.abruptPenalty * 0.25)
        value += keyFit(from, to, taste) * 0.08
        value += eraFit(from, to) * 0.06
        if (from.albumId != null && from.albumId == to.albumId) value += plan.sameArtistBonus * 0.5

        val familiar = taste.song(to.songId)?.heard == true
        if (sinceFamiliar >= plan.familiarEvery) value += if (familiar) 0.5 else -0.3
        return value
    }

    private fun hasEvidenceOf(to: Candidate, taste: TasteProfile): Boolean =
        to.artistIds.any { taste.artist(it)?.heard == true }

    private fun tempoFit(from: Candidate, to: Candidate, taste: TasteProfile): Double {
        val a = taste.features[from.songId]?.takeIf { it.confidence >= 0.3f }?.bpm?.toDouble() ?: return 0.0
        val b = taste.features[to.songId]?.takeIf { it.confidence >= 0.3f }?.bpm?.toDouble() ?: return 0.0
        val gap = minOf(abs(a - b), abs(a * 2 - b), abs(a - b * 2))
        return exp(-gap / TEMPO_TOLERANCE) * 2.0 - 1.0
    }

    private fun keyFit(from: Candidate, to: Candidate, taste: TasteProfile): Double {
        val a = taste.features[from.songId]?.keyPitchClass ?: return 0.0
        val b = taste.features[to.songId]?.keyPitchClass ?: return 0.0
        val distance = ((b - a) * 7).mod(12).let { minOf(it, 12 - it) }
        return when (distance) {
            0 -> 1.0
            1 -> 0.6
            2 -> 0.1
            else -> -0.3
        }
    }

    private fun eraFit(from: Candidate, to: Candidate): Double {
        val a = from.year ?: return 0.0
        val b = to.year ?: return 0.0
        return exp(-abs(a - b) / 8.0) * 2.0 - 1.0
    }

    fun arrange(
        current: Candidate?,
        ranked: List<ScoredCandidate>,
        taste: TasteProfile,
        context: RankContext,
        limit: Int = ranked.size,
        recentArtists: List<String> = context.recentArtists,
    ): List<ScoredCandidate> {
        if (ranked.isEmpty()) return ranked
        val plan = context.plan
        val remaining = ranked.toMutableList()
        val out = ArrayList<ScoredCandidate>(minOf(limit, ranked.size))
        var previous = current
        var run = 1
        var sinceFamiliar = 0
        val memory = ArrayDeque(recentArtists.take(ARTIST_MEMORY))
        while (remaining.isNotEmpty() && out.size < limit) {
            var bestIndex = 0
            var bestValue = Double.NEGATIVE_INFINITY
            for (index in remaining.indices) {
                val item = remaining[index]
                val artist = item.candidate.leadArtist
                val echo = memory.indexOf(artist).let { if (it < 0) 0.0 else (ARTIST_MEMORY - it).toDouble() / ARTIST_MEMORY }
                val flow = previous?.let { transition(it, item.candidate, run, sinceFamiliar, taste, context) } ?: 0.0
                val value = item.score + flow - echo * plan.diversity * if (previous?.leadArtist == artist) 0.0 else 1.0
                if (value > bestValue) {
                    bestValue = value
                    bestIndex = index
                }
            }
            val pick = remaining.removeAt(bestIndex)
            out += pick
            val sameArtist = previous != null && pick.candidate.artistIds.any { it in previous!!.artistIds }
            run = if (sameArtist) run + 1 else 1
            sinceFamiliar = if (taste.song(pick.candidate.songId)?.heard == true) 0 else sinceFamiliar + 1
            memory.addFirst(pick.candidate.leadArtist)
            if (memory.size > ARTIST_MEMORY) memory.removeLast()
            previous = pick.candidate
        }
        return out
    }

    fun coherence(sequence: List<Candidate>, graph: MusicGraph): Double {
        if (sequence.size < 2) return 1.0
        val linked = sequence.zipWithNext().count { (a, b) ->
            a.artistIds.any { it in b.artistIds } ||
                a.artistIds.any { x -> b.artistIds.any { y -> graph.strength(x, y) > 0.0 } }
        }
        return linked.toDouble() / (sequence.size - 1)
    }

    fun worstRepeatGap(sequence: List<Candidate>): Int {
        val last = HashMap<String, Int>()
        var gap = Int.MAX_VALUE
        sequence.forEachIndexed { index, item ->
            last[item.leadArtist]?.let { gap = minOf(gap, index - it) }
            last[item.leadArtist] = index
        }
        return max(0, gap)
    }

    private const val ARTIST_MEMORY = 6
}
