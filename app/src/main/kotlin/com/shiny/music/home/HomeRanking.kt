package com.shiny.music.home

import com.shiny.music.db.entities.HomeRelatedLink
import com.shiny.music.db.entities.HomeSavedSong
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.Song
import kotlin.math.ln
import kotlin.math.min
import kotlin.random.Random

/**
 * How Home ranks the listen history. Pure functions over the aggregates, so they are cheap
 * enough to run on every rebuild and can be tested without a database.
 *
 * Every score is built from counted plays only: a play is a listen of at least the history
 * duration (30s by default), from any source — streamed, downloaded or a file on the device.
 */
object HomeRanking {
    const val WEEK_DAYS = 7
    const val MONTH_DAYS = 30
    const val DAYPART_WINDOW_DAYS = 60
    const val ROTATION_MAX_AGE_DAYS = 30
    const val REDISCOVER_MIN_AGE_DAYS = 45

    /** Recent plays count most, the long run a little: a month's favourite beats a year's. */
    fun rotationScore(s: HomeSongStat): Double =
        s.weekPlays * 3.0 + s.monthPlays * 1.5 + ln(1.0 + s.plays)

    /** Songs played at least twice this month and in the last [ROTATION_MAX_AGE_DAYS] days. */
    fun rotation(stats: Collection<HomeSongStat>, nowMs: Long, limit: Int = 40): List<String> =
        stats.asSequence()
            .filter { it.monthPlays >= 2 && nowMs - it.lastPlayed <= ROTATION_MAX_AGE_DAYS * HOME_DAY_MS }
            .sortedByDescending(::rotationScore)
            .take(limit)
            .map { it.songId }
            .toList()

    /** Songs played at least twice at this time of day in the daypart window. */
    fun daypart(stats: Collection<HomeSongStat>, limit: Int = 50): List<String> =
        stats.asSequence()
            .filter { it.daypartPlays >= 2 }
            .sortedByDescending { it.daypartPlays * 2.0 + it.monthPlays * 0.3 }
            .take(limit)
            .map { it.songId }
            .toList()

    /** Songs played four or more times that have not been played in [REDISCOVER_MIN_AGE_DAYS] days. */
    fun rediscover(stats: Collection<HomeSongStat>, nowMs: Long, limit: Int = 60): List<String> =
        stats.asSequence()
            .filter { it.plays >= 4 && nowMs - it.lastPlayed >= REDISCOVER_MIN_AGE_DAYS * HOME_DAY_MS }
            .sortedByDescending { ln(1.0 + it.plays) + it.playTime / 3_600_000.0 * 0.1 }
            .take(limit)
            .map { it.songId }
            .toList()

    /** The most recently played songs, newest first. */
    fun recent(stats: Collection<HomeSongStat>, limit: Int = 30): List<String> =
        stats.sortedByDescending { it.lastPlayed }.take(limit).map { it.songId }

    /** Files on the device have content-URI ids; YouTube has nothing related to them. */
    fun isStreamableId(id: String): Boolean = !id.startsWith("content://") && !id.startsWith("LOCAL")

    /** How strongly each played song should pull its related songs into Discover. */
    fun seedWeights(stats: Collection<HomeSongStat>, limit: Int = 25): Map<String, Double> =
        stats.asSequence()
            .filter { isStreamableId(it.songId) }
            .map { it.songId to (it.monthPlays * 2.0 + it.weekPlays * 2.0 + ln(1.0 + it.plays)) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .toMap()

    /**
     * Seeds from what the listener chose rather than played. A like says the most, then one
     * of their most played on Spotify, then a song saved to the library, then one in their
     * own playlist, then one Spotify put in a mix for them. All sit well below a single real play (at least ~2.7 in [seedWeights]),
     * so listening overrides them the moment it exists.
     */
    fun savedSeedWeights(saved: Collection<HomeSavedSong>): Map<String, Double> =
        saved.asSequence()
            .filter { isStreamableId(it.songId) }
            .map { s ->
                s.songId to when {
                    s.liked -> 0.8
                    s.inTop -> 0.7
                    s.inLibrary -> 0.6
                    s.inPlaylist -> 0.5
                    s.inMix -> 0.35
                    else -> 0.0
                }
            }
            .filter { it.second > 0.0 }
            .toMap()

    /** Played seeds, topped up with saved ones, strongest first. */
    fun mergeSeeds(played: Map<String, Double>, saved: Map<String, Double>, limit: Int = 25): Map<String, Double> =
        (saved + played).entries
            .sortedByDescending { it.value }
            .take(limit)
            .associate { it.key to it.value }

    /** The single most recent play, for the "Last song listened" setting. */
    fun lastPlayed(stats: Collection<HomeSongStat>): HomeSongStat? = stats.maxByOrNull { it.lastPlayed }

    /**
     * Never-played related songs, best first: a song related to several of the listener's
     * songs outranks one related to a single favourite. Each keeps the seed that pulled it
     * hardest, which becomes its "because".
     */
    fun discovery(links: List<HomeRelatedLink>, seedWeights: Map<String, Double>, limit: Int = 60): List<HomeRelatedLink> =
        links.groupBy { it.songId }
            .map { (songId, edges) ->
                val best = edges.maxBy { seedWeights[it.seedId] ?: 0.0 }
                Triple(songId, best.seedId, edges.sumOf { seedWeights[it.seedId] ?: 0.0 })
            }
            .sortedByDescending { it.third }
            .take(limit)
            .map { HomeRelatedLink(seedId = it.second, songId = it.first) }

    /**
     * Mean share of the song heard per counted play, from its own duration. A song that is
     * counted but usually abandoned early drifts down the rotation instead of leading it.
     */
    fun completionFactor(song: Song, stat: HomeSongStat): Double {
        val durationMs = song.song.duration * 1000L
        if (durationMs <= 0 || stat.plays <= 0) return 1.0
        val meanShare = min(1.0, stat.playTime.toDouble() / stat.plays / durationMs)
        return (meanShare / 0.6).coerceIn(0.4, 1.0)
    }

    // ---- ordering helpers ------------------------------------------------------------------

    fun primaryArtistKey(song: Song): String =
        song.artists.firstOrNull()?.let { it.id.ifBlank { it.name } } ?: song.song.albumName.orEmpty()

    /** Keeps [items] in order but lets no artist appear more than [maxPerArtist] times. */
    fun <T> capPerArtist(items: List<T>, maxPerArtist: Int, artistOf: (T) -> String): List<T> {
        val seen = HashMap<String, Int>()
        return items.filter { item ->
            val key = artistOf(item)
            val n = seen[key] ?: 0
            if (n < maxPerArtist) {
                seen[key] = n + 1
                true
            } else {
                false
            }
        }
    }

    /** Nudges the order so one artist does not play twice in a row when another is close by. */
    fun <T> spreadArtists(items: List<T>, lookahead: Int = 4, artistOf: (T) -> String): List<T> {
        val out = items.toMutableList()
        for (i in 1 until out.size) {
            if (artistOf(out[i]) != artistOf(out[i - 1])) continue
            val swap = (i + 1 until min(out.size, i + 1 + lookahead)).firstOrNull { j ->
                artistOf(out[j]) != artistOf(out[i - 1])
            } ?: continue
            val moved = out.removeAt(swap)
            out.add(i, moved)
        }
        return out
    }

    /**
     * A seeded shuffle of the top of a ranking: the best [head] items change order from one
     * daypart to the next while the ranking itself still decides who is in them.
     */
    fun <T> rotateHead(items: List<T>, head: Int, seed: Long): List<T> {
        if (items.size <= 1) return items
        val top = items.take(head).shuffled(Random(seed))
        return top + items.drop(head)
    }
}
