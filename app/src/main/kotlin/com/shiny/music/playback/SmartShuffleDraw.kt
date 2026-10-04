package com.shiny.music.playback

import com.shiny.music.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * Smart Shuffle for any collection — a playlist, Liked, Downloaded, a YouTube playlist: the
 * play counts and last plays Shiny already keeps, drawn by [SmartShuffle]. Two queries and
 * the draw, off the main thread, once per tap.
 */
object SmartShuffleDraw {
    data class Candidate(val id: String, val artist: String?)

    /** [candidates] in Smart Shuffle order, as ids; the song [playingId] is not put first. */
    suspend fun order(database: MusicDatabase, candidates: List<Candidate>, playingId: String?): List<String> {
        val unique = candidates.distinctBy { it.id }
        if (unique.size < 2) return unique.map { it.id }
        val ids = unique.mapTo(HashSet()) { it.id }
        val (monthPlays, lastPlayed) = withContext(Dispatchers.IO) {
            database.songMonthPlays().filter { it.songId in ids }.groupBy { it.songId } to
                database.songLastPlayed().filter { it.songId in ids }.associate { it.songId to it.lastPlayed }
        }
        return withContext(Dispatchers.Default) {
            val nowMs = System.currentTimeMillis()
            // History is written with the device's wall-clock time (LocalDateTime.now()), and
            // play counts by its calendar month, so both are read back in the device's zone.
            val zone = ZoneId.systemDefault()
            val month = YearMonth.now(zone)
            val tracks = unique.map { candidate ->
                SmartShuffle.Track(
                    id = candidate.id,
                    artist = candidate.artist,
                    plays = SmartShuffle.agedPlays(
                        monthPlays[candidate.id].orEmpty().map { Triple(it.year, it.month, it.count) },
                        month.year,
                        month.monthValue,
                    ),
                    lastPlayedMs = lastPlayed[candidate.id]?.let { wallClock ->
                        Instant.ofEpochMilli(wallClock).atOffset(ZoneOffset.UTC).toLocalDateTime()
                            .atZone(zone).toInstant().toEpochMilli()
                    },
                )
            }
            val seed = Random.nextLong()
            val order = SmartShuffle.order(tracks, nowMs, Random(seed), avoidFirst = playingId)
            Timber.tag("SmartShuffle").d(
                "%d songs, %d with plays, seed %d",
                tracks.size, tracks.count { it.plays > 0 }, seed,
            )
            order
        }
    }
}
