package com.shiny.music.playback

import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Smart Shuffle for the downloaded songs: a shuffle in which the songs you play most come
 * up earlier more often, without taking the queue over.
 *
 * Every song is in the order exactly once. The order is drawn one song at a time, each
 * remaining song being picked with probability proportional to
 *
 *     weight = cooldown × (1 + 0.5 × ln(1 + plays) × (1 − filled))
 *
 * - `plays` are the plays Shiny already counts (a listen of at least the history
 *   duration), each counting half as much for every six months of age.
 * - `cooldown` is 1, except for a song played in the last 24 hours: 0.3 right after the
 *   play, climbing back to 1 over the day.
 * - `filled` is the share of the queue already drawn, so the boost is full for the first
 *   pick and gone for the last ones.
 *
 * A never-played song has weight 1 and can be drawn first. Afterwards the order is nudged
 * so the same artist does not play twice in a row where a different one is close by, and
 * so the queue does not open with the song already playing.
 *
 * Why half strength and a fade: drawn without replacement, the plain 1 + ln(1 + plays)
 * put songs with 100 plays (a fifth of a 100-song library) in the first half of the queue
 * 93% of the time, using the favourites up early. With both, 78%, against 50% for
 * Shuffle, while their share of the first ten songs still doubles.
 */
object SmartShuffle {
    data class Track(
        val id: String,
        val artist: String?,
        /** Counted plays, already aged with [agedPlays]. */
        val plays: Double,
        val lastPlayedMs: Long?,
    )

    const val FREQUENCY_STRENGTH = 0.5
    const val PLAY_HALF_LIFE_MONTHS = 6.0
    const val COOLDOWN_MS = 24L * 60 * 60 * 1000
    const val COOLDOWN_FLOOR = 0.3
    const val ARTIST_LOOKAHEAD = 5

    /** Monthly play counts, as (year, month, count), summed with a [PLAY_HALF_LIFE_MONTHS] half-life. */
    fun agedPlays(months: List<Triple<Int, Int, Int>>, nowYear: Int, nowMonth: Int): Double {
        val now = nowYear * 12 + nowMonth
        return months.sumOf { (year, month, count) ->
            if (count <= 0) return@sumOf 0.0
            val age = (now - (year * 12 + month)).coerceAtLeast(0)
            count * 0.5.pow(age / PLAY_HALF_LIFE_MONTHS)
        }
    }

    fun cooldown(lastPlayedMs: Long?, nowMs: Long): Double {
        if (lastPlayedMs == null) return 1.0
        val since = (nowMs - lastPlayedMs).coerceAtLeast(0L)
        if (since >= COOLDOWN_MS) return 1.0
        return COOLDOWN_FLOOR + (1.0 - COOLDOWN_FLOOR) * since / COOLDOWN_MS
    }

    fun boost(plays: Double): Double = FREQUENCY_STRENGTH * ln(1.0 + plays.coerceAtLeast(0.0))

    /** The pull of one song when [filled] (0 to 1) of the queue has been drawn. */
    fun weight(plays: Double, lastPlayedMs: Long?, nowMs: Long, filled: Double = 0.0): Double =
        cooldown(lastPlayedMs, nowMs) * (1.0 + boost(plays) * (1.0 - filled))

    /**
     * The song ids in Smart Shuffle order. Duplicate ids are kept once. [avoidFirst] is
     * the song playing now, which should not start the new queue.
     */
    fun order(tracks: List<Track>, nowMs: Long, random: Random, avoidFirst: String? = null): List<String> {
        val unique = tracks.distinctBy { it.id }
        if (unique.size <= 1) return unique.map { it.id }

        val count = unique.size
        val pool = unique.mapTo(ArrayList(count)) { Candidate(it, cooldown(it.lastPlayedMs, nowMs), boost(it.plays)) }
        val ranked = ArrayList<Track>(count)
        while (pool.isNotEmpty()) {
            val fade = 1.0 - ranked.size.toDouble() / count
            var total = 0.0
            for (candidate in pool) total += candidate.weight(fade)
            val target = random.nextDouble() * total
            var reached = 0.0
            var pick = pool.lastIndex
            for (index in pool.indices) {
                reached += pool[index].weight(fade)
                if (target < reached) {
                    pick = index
                    break
                }
            }
            ranked += pool[pick].track
            // Order within the pool does not matter, so the last candidate fills the gap.
            pool[pick] = pool[pool.lastIndex]
            pool.removeAt(pool.lastIndex)
        }

        if (ranked[0].id == avoidFirst) ranked.add(1, ranked.removeAt(0))

        val artists = ranked.associate { it.id to it.artist?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
        for (i in 1 until ranked.size) {
            val previous = artists[ranked[i - 1].id] ?: continue
            if (artists[ranked[i].id] != previous) continue
            val other = (i + 1 until minOf(ranked.size, i + 1 + ARTIST_LOOKAHEAD))
                .firstOrNull { artists[ranked[it].id] != previous }
                ?: continue
            ranked.add(i, ranked.removeAt(other))
        }
        return ranked.map { it.id }
    }

    private class Candidate(val track: Track, val cooldown: Double, val boost: Double) {
        fun weight(fade: Double) = cooldown * (1.0 + boost * fade)
    }
}
