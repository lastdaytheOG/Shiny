package com.shiny.music.stats

import android.content.Context
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Song
import com.shiny.music.home.HomeGenres
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class StatsKind { Day, Week, Month, Year, All }

// the stretch of time the page is about. today and this week are always the current ones,
// months and years are picked on the ruler
data class StatsRange(val kind: StatsKind, val year: Int = LocalDate.now().year, val month: Int = 1) {
    val start: LocalDateTime
        get() = when (kind) {
            StatsKind.Day -> LocalDate.now().atStartOfDay()
            StatsKind.Week -> LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay()
            StatsKind.Month -> LocalDate.of(year, month, 1).atStartOfDay()
            StatsKind.Year -> LocalDate.of(year, 1, 1).atStartOfDay()
            StatsKind.All -> LocalDateTime.of(1970, 1, 1, 0, 0)
        }

    val end: LocalDateTime
        get() = when (kind) {
            StatsKind.Day -> start.plusDays(1)
            StatsKind.Week -> start.plusWeeks(1)
            StatsKind.Month -> start.plusMonths(1)
            StatsKind.Year -> start.plusYears(1)
            StatsKind.All -> LocalDateTime.now().plusDays(1)
        }

    val isCurrent: Boolean
        get() {
            val now = LocalDateTime.now()
            return !now.isBefore(start) && now.isBefore(end)
        }
}

class StatArtist(val id: String, val name: String, val photo: String?, val playMs: Long, val plays: Int)

class StatSong(val song: Song, val playMs: Long, val plays: Int) {
    val title get() = song.title
    val artist get() = song.artists.joinToString { it.name }
    val cover get() = song.thumbnailUrl
}

class GenreShare(val name: String, val fraction: Float, val playMs: Long)

class ListeningStats(
    val range: StatsRange,
    val totalMs: Long,
    val plays: Int,
    val songCount: Int,
    val artistCount: Int,
    val albumCount: Int,
    // listening per hour (today), per day (week, month), per month (year) or per year (all time)
    val buckets: List<Long>,
    val bucketStart: Int,
    val topArtists: List<StatArtist>,
    val topSongs: List<StatSong>,
    val weekdayMs: List<Long>,
    val hourMs: List<Long>,
) {
    val isEmpty get() = plays == 0
}

object StatsReport {
    // event times are local wall time stored as utc, so they're read as local times directly
    suspend fun load(database: MusicDatabase, range: StatsRange): Pair<ListeningStats, LocalDate?> = withContext(Dispatchers.Default) {
        val all = database.events().first()
        val first = all.minOfOrNull { it.event.timestamp }?.toLocalDate()
        val start = range.start
        val end = range.end
        val events = all.filter { !it.event.timestamp.isBefore(start) && it.event.timestamp.isBefore(end) }

        val (bucketCount, bucketStart) = when (range.kind) {
            StatsKind.Day -> 24 to 0
            StatsKind.Week -> 7 to 0
            StatsKind.Month -> YearMonth.of(range.year, range.month).lengthOfMonth() to 0
            StatsKind.Year -> 12 to 0
            StatsKind.All -> {
                val from = first?.year ?: LocalDate.now().year
                (LocalDate.now().year - from + 1).coerceAtLeast(1) to from
            }
        }
        val buckets = LongArray(bucketCount)
        val weekday = LongArray(7)
        val hour = LongArray(24)
        val artistMs = HashMap<String, Long>()
        val artistPlays = HashMap<String, Int>()
        val artistInfo = HashMap<String, Pair<String, String?>>()
        val songMs = HashMap<String, Long>()
        val songPlays = HashMap<String, Int>()
        val songs = HashMap<String, Song>()
        val albums = HashSet<String>()
        var total = 0L
        events.forEach { e ->
            val t = e.event.timestamp
            val ms = e.event.playTime.coerceAtLeast(0)
            total += ms
            val b = when (range.kind) {
                StatsKind.Day -> t.hour
                StatsKind.Week -> t.dayOfWeek.value - 1
                StatsKind.Month -> t.dayOfMonth - 1
                StatsKind.Year -> t.monthValue - 1
                StatsKind.All -> t.year - bucketStart
            }
            if (b in 0 until bucketCount) buckets[b] += ms
            weekday[t.dayOfWeek.value - 1] += ms
            hour[t.hour] += ms
            val song = e.song
            songs[song.id] = song
            songMs.merge(song.id, ms, Long::plus)
            songPlays.merge(song.id, 1, Int::plus)
            song.song.albumId?.let(albums::add)
            song.artists.forEach { a ->
                artistMs.merge(a.id, ms, Long::plus)
                artistPlays.merge(a.id, 1, Int::plus)
                artistInfo.putIfAbsent(a.id, a.name to a.thumbnailUrl)
            }
        }
        val topArtists = artistMs.entries.sortedByDescending { it.value }.take(25).map { (id, ms) ->
            val (name, photo) = artistInfo.getValue(id)
            StatArtist(id, name, photo, ms, artistPlays[id] ?: 0)
        }
        val topSongs = songMs.entries.sortedByDescending { it.value }.take(10).map { (id, ms) ->
            StatSong(songs.getValue(id), ms, songPlays[id] ?: 0)
        }
        ListeningStats(
            range = range,
            totalMs = total,
            plays = events.size,
            songCount = songs.size,
            artistCount = artistMs.size,
            albumCount = albums.size,
            buckets = buckets.toList(),
            bucketStart = bucketStart,
            topArtists = topArtists,
            topSongs = topSongs,
            weekdayMs = weekday.toList(),
            hourMs = hour.toList(),
        ) to first
    }
}

// genres come from itunes by artist name, remembered on the phone so each artist is asked once
object StatsGenres {
    private val mutex = Mutex()
    private var cache: MutableMap<String, String>? = null
    private const val MaxLookups = 12

    suspend fun shares(context: Context, artists: List<StatArtist>): List<GenreShare>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = context.applicationContext.getSharedPreferences("stats_genres", Context.MODE_PRIVATE)
            val known = cache ?: runCatching {
                val o = JSONObject(prefs.getString("genres", "{}") ?: "{}")
                o.keys().asSequence().associateWith { o.getString(it) }.toMutableMap()
            }.getOrDefault(mutableMapOf()).also { cache = it }
            var asked = 0
            var dirty = false
            for (artist in artists) {
                val key = artist.name.trim().lowercase()
                if (key.isEmpty() || key in known) continue
                if (asked >= MaxLookups) break
                if (asked > 0) delay(220)
                asked++
                val genre = HomeGenres.itunesGenre(artist.name) ?: continue
                known[key] = genre
                dirty = true
            }
            if (dirty) prefs.edit().putString("genres", JSONObject(known as Map<*, *>).toString()).apply()
            val totals = LinkedHashMap<String, Long>()
            artists.forEach { a ->
                val g = known[a.name.trim().lowercase()] ?: return@forEach
                totals[g] = (totals[g] ?: 0L) + a.playMs
            }
            val sum = totals.values.sum().takeIf { it > 0 } ?: return@withLock null
            totals.entries.sortedByDescending { it.value }.take(10).map { GenreShare(it.key, it.value.toFloat() / sum, it.value) }
        }
    }
}
