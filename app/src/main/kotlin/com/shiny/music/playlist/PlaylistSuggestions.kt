package com.shiny.music.playlist

import kotlin.random.Random

/**
 * Chooses what to suggest adding to a playlist.
 *
 * A few of the playlist's songs are used as examples ("seeds"); the catalogue is asked what goes
 * with each of them, and the answers are combined here. Plain logic, so it is tested without the
 * network.
 */
object PlaylistSuggestions {
    /** How many songs of the playlist are used as examples. Each one costs a request. */
    const val MAX_SEEDS = 3

    /** More than the screen shows at once, so adding a suggestion brings up another. */
    const val LIMIT = 24

    /** A few songs picked at random, so a long playlist is not always represented by its start. */
    fun seeds(songIds: List<String>, random: Random = Random.Default): List<String> =
        songIds.distinct().shuffled(random).take(MAX_SEEDS)

    /**
     * Combines the songs [related] to each seed. The seeds take turns, so one seed's results do
     * not crowd out the others; songs already [inPlaylist] and repeats are left out.
     */
    fun <T> merge(
        related: List<List<T>>,
        inPlaylist: Set<String>,
        id: (T) -> String,
        limit: Int = LIMIT,
    ): List<T> {
        val seen = HashSet(inPlaylist)
        val result = ArrayList<T>()
        val longest = related.maxOfOrNull { it.size } ?: 0
        for (rank in 0 until longest) {
            for (list in related) {
                val candidate = list.getOrNull(rank) ?: continue
                if (seen.add(id(candidate))) result += candidate
                if (result.size == limit) return result
            }
        }
        return result
    }
}
