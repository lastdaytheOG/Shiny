package com.shiny.music.discover

import androidx.compose.runtime.Immutable

@Immutable
data class ChartArtist(
    val key: String,
    val name: String,
    val appleId: String?,
    val appleUrl: String?,
    val songs: List<ChartEntry>,
    val featuredOn: Int,
) {
    val songCount: Int get() = songs.size
    val bestPosition: Int get() = songs.first().position
    val artwork: String? get() = songs.firstNotNullOfOrNull { it.artwork }
}

/**
 * Derived from the song chart alone, never an official ranking: one lead credit per song,
 * ordered by lead songs, then by songs featured on, then by the highest position.
 */
object ChartArtistRanking {
    fun rank(songs: List<ChartEntry>): List<ChartArtist> {
        data class Acc(val key: String, val name: String, val appleId: String?, val appleUrl: String?) {
            val songs = mutableListOf<ChartEntry>()
            val featured = HashSet<String>()
        }

        val byKey = LinkedHashMap<String, Acc>()
        val byName = HashMap<String, Acc>()
        val ordered = songs.sortedBy { it.position }

        ordered.forEach { song ->
            val lead = ChartText.leadArtist(song.artist, song.artistUrl)
            val nameKey = ChartText.nameKey(lead)
            if (nameKey.isEmpty()) return@forEach
            val key = song.artistId?.let { "apple:$it" } ?: "name:$nameKey"
            val acc = byKey[key] ?: byName[nameKey] ?: Acc(key, lead, song.artistId, song.artistUrl).also {
                byKey[key] = it
            }
            byName.putIfAbsent(nameKey, acc)
            acc.songs += song
        }

        ordered.forEach { song ->
            val leadKey = ChartText.nameKey(ChartText.leadArtist(song.artist, song.artistUrl))
            (ChartText.splitCredits(song.artist) + ChartText.featuredInTitle(song.title))
                .map(ChartText::nameKey)
                .filter { it.isNotEmpty() && it != leadKey }
                .distinct()
                .forEach { name -> byName[name]?.featured?.add(song.id) }
        }

        return byKey.values
            .map { acc ->
                ChartArtist(
                    key = acc.key,
                    name = acc.name,
                    appleId = acc.appleId,
                    appleUrl = acc.appleUrl,
                    songs = acc.songs.toList(),
                    featuredOn = (acc.featured - acc.songs.map { it.id }.toSet()).size,
                )
            }
            .sortedWith(
                compareByDescending<ChartArtist> { it.songCount }
                    .thenByDescending { it.featuredOn }
                    .thenBy { it.bestPosition }
                    .thenBy { it.name.lowercase() }
            )
    }
}
