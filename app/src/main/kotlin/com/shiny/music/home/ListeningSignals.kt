package com.shiny.music.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// what the player tells home between loads: songs dropped in the first seconds and songs heard
// through. skips survive restarts (newest 200) so a skipped song stays out of picks for two weeks
// and an artist skipped three times sits out too
object ListeningSignals {
    @Serializable
    data class Skip(val songId: String, val artists: List<String>, val at: Long)

    private const val KEY = "skips"
    private const val MAX = 200
    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null
    private val skips = ArrayDeque<Skip>()

    @Volatile
    var skipsSinceRefresh = 0
        private set

    @Volatile
    var playsSinceRefresh = 0
        private set

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("listening_signals", Context.MODE_PRIVATE)
        prefs = p
        runCatching { json.decodeFromString<List<Skip>>(p.getString(KEY, null) ?: "[]") }.getOrNull()?.let(skips::addAll)
    }

    @Synchronized
    fun onTrackLeft(songId: String, artists: List<String>, durationMs: Long, listenedMs: Long) {
        when {
            // queued but never heard, like the song put back at launch
            listenedMs < 300 -> Unit
            // an intro or a jingle says nothing
            durationMs in 1 until 40_000 -> Unit
            listenedMs < 30_000 && (durationMs <= 0 || listenedMs < durationMs / 2) -> {
                skips.addLast(Skip(songId, artists, System.currentTimeMillis()))
                while (skips.size > MAX) skips.removeFirst()
                skipsSinceRefresh++
                prefs?.edit()?.putString(KEY, json.encodeToString(skips.toList()))?.apply()
            }
            else -> playsSinceRefresh++
        }
    }

    @Synchronized
    fun recentSkips(days: Int = 14): List<Skip> {
        val since = System.currentTimeMillis() - 86_400_000L * days
        return skips.filter { it.at >= since }
    }

    fun markRefreshed() {
        skipsSinceRefresh = 0
        playsSinceRefresh = 0
    }
}
