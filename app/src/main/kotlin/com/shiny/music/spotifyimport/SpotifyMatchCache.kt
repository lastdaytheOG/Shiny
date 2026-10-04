package com.shiny.music.spotifyimport

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Spotify track id → the YouTube video id it was matched to.
 *
 * Spotify's mixes repeat most of their songs from one day to the next, so without this every
 * daily re-sync would search YouTube for the same songs again. Only a hit whose song is still
 * in the database is used (see `SpotifyImportRepository.matchTrack`), so a stale entry costs
 * one search, never a wrong song.
 */
internal class SpotifyMatchCache(context: Context) {
    private companion object {
        const val FILE = "spotify_matches.json"

        /** Enough for every mix many times over; the oldest entries go first past it. */
        const val MAX_ENTRIES = 8_000
    }

    private val file = File(context.filesDir, FILE)
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val entries: ConcurrentHashMap<String, String> by lazy {
        val stored = runCatching { Json.decodeFromString(serializer, file.readText()) }
            .onFailure { if (file.exists()) Timber.tag("SpotifyMatchCache").w(it, "unreadable, starting empty") }
            .getOrNull()
            .orEmpty()
        ConcurrentHashMap(stored)
    }

    @Volatile
    private var dirty = false

    fun get(spotifyTrackId: String): String? =
        spotifyTrackId.takeIf { it.isNotBlank() }?.let { entries[it] }

    fun put(spotifyTrackId: String, videoId: String) {
        if (spotifyTrackId.isBlank()) return
        if (entries.put(spotifyTrackId, videoId) != videoId) dirty = true
    }

    /** Writes the cache if anything changed since the last save. Call off the main thread. */
    @Synchronized
    fun save() {
        if (!dirty) return
        dirty = false
        // Insertion order is not kept by the map, so trimming drops arbitrary entries; each
        // costs one search if its song comes back, which is the same as never having cached it.
        val snapshot = entries.entries.take(MAX_ENTRIES).associate { it.key to it.value }
        runCatching {
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(Json.encodeToString(serializer, snapshot))
            tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
        }.onFailure { Timber.tag("SpotifyMatchCache").w(it, "save failed") }
    }
}
