package com.shiny.music.localmedia

import java.util.Locale
import kotlin.math.abs

/**
 * Finds local files that were moved or renamed, so their listening history can follow them.
 *
 * MediaStore gives a moved file a new id, which to the scanner looks like one song deleted
 * and another added. Deleting the old row cascaded its plays, so a song you'd played a
 * hundred times dropped out of your stats the moment you tidied a folder.
 *
 * A match needs the same title and artist (ignoring case and spacing) and a duration within
 * [DURATION_TOLERANCE_S]. Only unambiguous pairs move: if two new files could be the old
 * one, or one new file could be two old ones, nothing moves rather than guessing wrong.
 */
object LocalHistoryRelink {

    data class Track(val id: String, val title: String, val artist: String, val durationSeconds: Int)

    private const val DURATION_TOLERANCE_S = 2

    /** Old id to new id, for every file that clearly moved. */
    fun match(gone: List<Track>, arrived: List<Track>): Map<String, String> {
        if (gone.isEmpty() || arrived.isEmpty()) return emptyMap()
        val arrivedByKey = arrived.groupBy(::key)
        val candidates = gone.associate { old ->
            old.id to arrivedByKey[key(old)].orEmpty().filter {
                abs(it.durationSeconds - old.durationSeconds) <= DURATION_TOLERANCE_S
            }
        }
        val claims = candidates.values.flatten().groupingBy { it.id }.eachCount()
        return candidates
            .filterValues { it.size == 1 && claims[it.single().id] == 1 }
            .mapValues { (_, matches) -> matches.single().id }
    }

    private fun key(track: Track): String =
        normalize(track.title) + "\u0000" + normalize(track.artist)

    private fun normalize(text: String): String =
        text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
