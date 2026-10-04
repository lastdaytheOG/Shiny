package com.shiny.music.home

import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.Song
import kotlin.math.pow
import kotlin.random.Random

data class SurprisePick(val song: Song, val kind: SurpriseKind)

/**
 * Surprise Me: one song from the listener's own music universe, drawn with controlled
 * randomness rather than a uniform pick.
 *
 * - A pool is chosen by weight (forgotten favourites and unexplored library songs most
 *   often, new-to-you songs a little less, device files least), so every press is a
 *   different *kind* of surprise without any one kind taking over.
 * - Inside a pool the head is favoured: pools are ranked, and squaring a uniform draw
 *   lands in the first half about 70% of the time without always taking the top.
 * - Anything played in the last three days, or picked in the last twenty presses, is
 *   skipped while something else is available.
 */
object HomeSurprise {
    const val RECENT_MEMORY = 20
    const val COOLDOWN_DAYS = 3

    fun pick(
        section: SurpriseSection,
        stats: Map<String, HomeSongStat>,
        recent: Collection<String>,
        nowMs: Long,
        random: Random,
    ): SurprisePick? {
        fun fresh(song: Song) = song.id !in recent &&
            (stats[song.id]?.lastPlayed?.let { nowMs - it > COOLDOWN_DAYS * HOME_DAY_MS } ?: true)

        val pools = section.pools
            .map { it to it.songs.filter(::fresh) }
            .filter { it.second.isNotEmpty() }
            .ifEmpty { section.pools.filter { it.songs.isNotEmpty() }.map { it to it.songs } }
        if (pools.isEmpty()) return null

        var r = random.nextDouble() * pools.sumOf { it.first.weight }
        val (pool, songs) = pools.firstOrNull { r -= it.first.weight; r <= 0.0 } ?: pools.last()
        val index = (random.nextDouble().pow(2) * songs.size).toInt().coerceIn(0, songs.lastIndex)
        return SurprisePick(songs[index], pool.kind)
    }
}
