package com.shiny.music.intelligence

import com.shiny.music.db.entities.DiscoveryOutcomeStat
import kotlin.math.min

sealed interface TasteInsight {
    val key: String
    val confidence: Double

    data class Rising(val artistId: String, val artist: String, val recent: Double, override val confidence: Double) : TasteInsight {
        override val key get() = "rising_$artistId"
    }

    data class Loyal(val artistId: String, val artist: String, val plays: Int, val songs: Int, override val confidence: Double) : TasteInsight {
        override val key get() = "loyal_$artistId"
    }

    data class ExploringMore(val recentNew: Int, val accepted: Int, override val confidence: Double) : TasteInsight {
        override val key get() = "exploring"
    }

    data class Bridge(
        val first: String,
        val second: String,
        val bridgeId: String,
        val bridge: String,
        override val confidence: Double,
    ) : TasteInsight {
        override val key get() = "bridge_$bridgeId"
    }

    data class Unexplored(val artistId: String, val artist: String, val saved: Int, override val confidence: Double) : TasteInsight {
        override val key get() = "unexplored_$artistId"
    }

    data class Tempo(val lowBpm: Int, val highBpm: Int, val measuredSongs: Int, override val confidence: Double) : TasteInsight {
        override val key get() = "tempo"
    }
}

object TasteInsights {
    private const val MIN_TEMPO_SONGS = 8

    fun build(
        taste: TasteProfile,
        reach: Map<String, GraphReach>,
        names: Map<String, String>,
        discovery: DiscoveryOutcomeStat?,
    ): List<TasteInsight> {
        val insights = ArrayList<TasteInsight>()
        fun name(id: String): String? = names[id]?.takeIf { it.isNotBlank() }
        val visible = taste.artists.values.filter { it.artistId !in taste.hiddenArtists }

        visible
            .filter { it.shortTerm > it.longTerm + 0.12 && it.plays >= 4 }
            .maxByOrNull { it.shortTerm - it.longTerm }
            ?.let { artist ->
                name(artist.artistId)?.let {
                    insights += TasteInsight.Rising(artist.artistId, it, artist.shortTerm, min(0.9, artist.shortTerm))
                }
            }

        visible
            .filter { it.plays >= 8 && it.songs >= 3 && it.rejection < 0.3 }
            .maxByOrNull { it.plays }
            ?.let { artist ->
                name(artist.artistId)?.let {
                    insights += TasteInsight.Loyal(artist.artistId, it, artist.plays, artist.songs, min(0.95, artist.longTerm))
                }
            }

        discovery?.let { stat ->
            if (stat.recentFirstPositive >= 3 && stat.recentFirstPositive > stat.recentFirstNegative) {
                insights += TasteInsight.ExploringMore(
                    recentNew = stat.recentFirstListens,
                    accepted = stat.recentFirstPositive,
                    confidence = min(0.9, stat.recentFirstPositive / 10.0 + 0.3),
                )
            }
        }

        reach.values
            .filter { it.isBridge && taste.artist(it.artistId)?.heard != true && it.artistId !in taste.hiddenArtists }
            .maxByOrNull { it.score }
            ?.let { bridge ->
                val first = name(bridge.seeds[0])
                val second = name(bridge.seeds[1])
                val middle = name(bridge.artistId)
                if (first != null && second != null && middle != null) {
                    insights += TasteInsight.Bridge(first, second, bridge.artistId, middle, min(0.85, bridge.score))
                }
            }

        visible
            .filter { it.saved >= 3 && it.plays == 0 }
            .maxByOrNull { it.saved }
            ?.let { artist ->
                name(artist.artistId)?.let {
                    insights += TasteInsight.Unexplored(artist.artistId, it, artist.saved, min(0.8, artist.saved / 10.0 + 0.2))
                }
            }

        taste.sound?.takeIf { it.measuredSongs >= MIN_TEMPO_SONGS && it.bpmSpread <= 28.0 }?.let { sound ->
            insights += TasteInsight.Tempo(
                lowBpm = (sound.bpm - sound.bpmSpread).toInt(),
                highBpm = (sound.bpm + sound.bpmSpread).toInt(),
                measuredSongs = sound.measuredSongs,
                confidence = min(0.8, sound.measuredSongs / 40.0 + 0.2),
            )
        }

        return insights.sortedByDescending { it.confidence }
    }

    fun pickForToday(insights: List<TasteInsight>, seed: Long): TasteInsight? {
        val worth = insights.filter { it.confidence >= 0.35 }
        if (worth.isEmpty()) return null
        return worth[Math.floorMod(seed / 16, worth.size.toLong()).toInt()]
    }
}
