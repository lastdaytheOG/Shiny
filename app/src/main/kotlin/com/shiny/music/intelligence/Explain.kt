package com.shiny.music.intelligence

import com.shiny.music.db.entities.CandidateSource

sealed interface Reason {
    val code: String

    data class BecauseSong(val songId: String, val title: String) : Reason {
        override val code get() = "song"
    }

    data class SimilarToArtist(val artist: String) : Reason {
        override val code get() = "similar"
    }

    data class ThroughArtist(val artist: String, val via: String) : Reason {
        override val code get() = "through"
    }

    data class Connects(val first: String, val second: String) : Reason {
        override val code get() = "connects"
    }

    data class YourArtist(val artist: String) : Reason {
        override val code get() = "artist"
    }

    data class YouChose(val artist: String) : Reason {
        override val code get() = "chose"
    }

    data class OnChart(val chart: String) : Reason {
        override val code get() = "chart"
    }
}

data class Recommendation(
    val song: Candidate,
    val score: Double,
    val breakdown: ScoreBreakdown,
    val reason: Reason?,
    val source: CandidateSource?,
)

object Explainer {
    fun reason(
        scored: ScoredCandidate,
        taste: TasteProfile,
        graph: MusicGraph,
        songTitles: Map<String, String>,
    ): Reason? {
        val candidate = scored.candidate
        fun name(id: String): String? = graph.names[id] ?: candidate.artists.firstOrNull { it.id == id }?.name?.takeIf { it.isNotBlank() }

        scored.reach?.takeIf { it.isBridge }?.let { reach ->
            val first = name(reach.seeds[0])
            val second = name(reach.seeds[1])
            if (first != null && second != null) return Reason.Connects(first, second)
        }

        candidate.hits.sortedByDescending { it.strength }.firstOrNull { it.viaSong != null }?.let { hit ->
            val title = songTitles[hit.viaSong]
            if (title != null) return Reason.BecauseSong(hit.viaSong!!, title)
        }

        scored.reach?.let { reach ->
            val seed = reach.seeds.firstOrNull()?.let(::name)
            if (seed != null) {
                val via = reach.via?.takeIf { it != reach.seeds.first() }?.let(::name)
                return if (reach.hops >= 2 && via != null) Reason.ThroughArtist(seed, via) else Reason.SimilarToArtist(seed)
            }
        }

        candidate.artists.firstOrNull { it.id in taste.seedArtists }?.let { artist ->
            artist.name.takeIf { it.isNotBlank() }?.let { return Reason.YouChose(it) }
        }

        val own = candidate.artists.maxByOrNull { taste.artist(it.id)?.strength ?: 0.0 }
        if (own != null && own.name.isNotBlank() && (taste.artist(own.id)?.strength ?: 0.0) >= 0.3) {
            return Reason.YourArtist(own.name)
        }
        return null
    }
}
