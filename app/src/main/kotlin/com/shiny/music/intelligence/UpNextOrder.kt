package com.shiny.music.intelligence

/**
 * The one rule for what plays next: YouTube's songs in YouTube's order. A song is only left
 * out when it repeats one already kept, or the listener turned it down.
 */
object UpNextOrder {
    /**
     * Indices into [songs] (id to artist ids) of the songs to keep, in their original order,
     * at most [limit] of them.
     */
    fun keep(
        songs: List<Pair<String, List<String>>>,
        limit: Int = songs.size,
        rejected: (id: String, artists: List<String>) -> Boolean = { _, _ -> false },
    ): List<Int> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>()
        val kept = ArrayList<Int>()
        for ((index, song) in songs.withIndex()) {
            val (id, artists) = song
            if (id.isBlank() || !seen.add(id) || rejected(id, artists)) continue
            kept += index
            if (kept.size == limit) break
        }
        return kept
    }
}
