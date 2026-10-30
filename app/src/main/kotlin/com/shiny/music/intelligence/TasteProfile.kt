package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistDaypartStat
import com.shiny.music.db.entities.ArtistOutcomeBucket
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.DiscoveryOutcomeStat
import com.shiny.music.db.entities.HomeAlbumStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSavedArtist
import com.shiny.music.db.entities.HomeSavedSong
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.ImpressionFatigue
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.SongFeature
import com.shiny.music.db.entities.SongOutcomeBucket
import com.shiny.music.db.entities.SourceOutcomeStat
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

enum class FeedbackKind(val targetType: String, val ttlDays: Int?) {
    NotInterested("song", 180),
    LessLikeThis("artist", 120),
    BlockSong("song", null),
    HideArtist("artist", null),
    MoreFromArtist("artist", 120),
    SeedArtist("artist", null),
    SaveForLater("song", null),
    ExploreMore("song", 90),
}

data class ArtistTaste(
    val artistId: String,
    val longTerm: Double,
    val shortTerm: Double,
    val rejection: Double,
    val positive: Double,
    val negative: Double,
    val plays: Int,
    val songs: Int,
    val saved: Int,
    val lastHeardMs: Long,
) {
    val known: Boolean get() = plays > 0 || saved > 0 || positive > 0.0
    val heard: Boolean get() = plays > 0 || positive > 0.0 || negative > 0.0
    val strength: Double get() = max(longTerm, shortTerm)
}

data class SongTaste(
    val songId: String,
    val plays: Int,
    val familiarity: Double,
    val positive: Double,
    val negative: Double,
    val lastHeardMs: Long,
    val weekPlays: Int,
    val saved: Boolean,
    val fadedOut: Boolean,
) {
    val heard: Boolean get() = plays > 0 || positive > 0.0 || negative > 0.0
}

data class DiscoveryPersonality(
    val appetite: Double,
    val noveltyAcceptance: Double,
    val familiarShare: Double,
    val evidence: Int,
)

data class SoundProfile(
    val bpm: Double,
    val bpmSpread: Double,
    val energy: Double?,
    val brightness: Double?,
    val measuredSongs: Int,
)

data class Beta(val positive: Double, val negative: Double) {
    fun mean(prior: Double, strength: Double): Double = (positive + prior * strength) / (positive + negative + strength)
}

data class TasteProfile(
    val confidence: Double,
    val artists: Map<String, ArtistTaste>,
    val songs: Map<String, SongTaste>,
    val albums: Map<String, Double>,
    val discovery: DiscoveryPersonality,
    val sources: Map<String, Beta>,
    val hiddenArtists: Set<String>,
    val blockedSongs: Set<String>,
    val softRejectedSongs: Set<String>,
    val lessLikeArtists: Set<String>,
    val boostedArtists: Set<String>,
    val seedArtists: Set<String>,
    val daypartLift: Map<String, Double>,
    val ignored: Map<String, Double>,
    val sound: SoundProfile?,
    val features: Map<String, SongFeature>,
    val recentSearches: List<String>,
    val nowMs: Long,
) {
    fun artist(id: String): ArtistTaste? = artists[id]
    fun song(id: String): SongTaste? = songs[id]
    fun isRejected(songId: String, artistIds: Collection<String>): Boolean =
        songId in blockedSongs || songId in softRejectedSongs || artistIds.any { it in hiddenArtists }

    companion object {
        val Empty = TasteProfile(
            confidence = 0.0,
            artists = emptyMap(),
            songs = emptyMap(),
            albums = emptyMap(),
            discovery = DiscoveryPersonality(appetite = 0.5, noveltyAcceptance = 0.55, familiarShare = 0.5, evidence = 0),
            sources = emptyMap(),
            hiddenArtists = emptySet(),
            blockedSongs = emptySet(),
            softRejectedSongs = emptySet(),
            lessLikeArtists = emptySet(),
            boostedArtists = emptySet(),
            seedArtists = emptySet(),
            daypartLift = emptyMap(),
            ignored = emptyMap(),
            sound = null,
            features = emptyMap(),
            recentSearches = emptyList(),
            nowMs = 0L,
        )
    }
}

data class TasteInputs(
    val confidence: Double = 0.0,
    val songStats: Collection<HomeSongStat> = emptyList(),
    val artistStats: Collection<HomeArtistStat> = emptyList(),
    val albumStats: Collection<HomeAlbumStat> = emptyList(),
    val savedSongs: Collection<HomeSavedSong> = emptyList(),
    val savedArtists: Collection<HomeSavedArtist> = emptyList(),
    val songOutcomes: Collection<SongOutcomeBucket> = emptyList(),
    val artistOutcomes: Collection<ArtistOutcomeBucket> = emptyList(),
    val sourceStats: Collection<SourceOutcomeStat> = emptyList(),
    val discovery: DiscoveryOutcomeStat? = null,
    val feedback: Collection<MusicFeedback> = emptyList(),
    val daypart: Collection<ArtistDaypartStat> = emptyList(),
    val impressions: Collection<ImpressionFatigue> = emptyList(),
    val features: Collection<SongFeature> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val nowMs: Long,
    val wallNowMs: Long = nowMs,
)

object TasteProfileBuilder {
    const val BUCKET_DAYS = 14L
    const val MAX_BUCKET = 26
    const val LONG_HALF_LIFE_DAYS = 180.0
    const val SHORT_HALF_LIFE_DAYS = 14.0
    private const val DAY_MS = 86_400_000L

    private fun sat(x: Double, k: Double): Double = if (x <= 0.0) 0.0 else x / (x + k)

    private fun halfLife(ageDays: Double, days: Double): Double = 0.5.pow(ageDays.coerceAtLeast(0.0) / days)

    private fun bucketWeight(bucket: Int, days: Double): Double =
        halfLife(bucket * BUCKET_DAYS.toDouble(), days)

    fun build(input: TasteInputs): TasteProfile {
        val now = input.nowMs
        val wallNow = input.wallNowMs

        val songOutcome = input.songOutcomes.groupBy { it.songId }
        val artistOutcome = input.artistOutcomes.groupBy { it.artistId }
        val savedSongs = input.savedSongs.associateBy { it.songId }
        val savedArtists = input.savedArtists.associateBy { it.artistId }

        val active = input.feedback.filter { it.expiresAt == null || it.expiresAt!! > now }
        fun ids(kind: FeedbackKind) = active.filter { it.kind == kind.name }.mapTo(HashSet()) { it.targetId }
        val hiddenArtists = ids(FeedbackKind.HideArtist)
        val blocked = ids(FeedbackKind.BlockSong)
        val soft = ids(FeedbackKind.NotInterested)
        val lessLike = ids(FeedbackKind.LessLikeThis)
        val boosted = ids(FeedbackKind.MoreFromArtist)
        val seeds = ids(FeedbackKind.SeedArtist)
        val explored = ids(FeedbackKind.ExploreMore)
        val forLater = ids(FeedbackKind.SaveForLater)

        val songIds = buildSet {
            input.songStats.forEach { add(it.songId) }
            addAll(songOutcome.keys)
            addAll(savedSongs.keys)
            addAll(explored)
            addAll(forLater)
        }
        val statBySong = input.songStats.associateBy { it.songId }
        val songs = songIds.associateWith { id ->
            val stat = statBySong[id]
            val outcomes = songOutcome[id].orEmpty()
            val positive = outcomes.sumOf { it.positive * bucketWeight(it.bucket, LONG_HALF_LIFE_DAYS) }
            val negative = outcomes.sumOf { it.negative * bucketWeight(it.bucket, LONG_HALF_LIFE_DAYS) }
            val recentPositive = outcomes.filter { it.bucket <= 2 }.sumOf { it.positive }
            val recentNegative = outcomes.filter { it.bucket <= 2 }.sumOf { it.negative }
            val plays = stat?.plays ?: 0
            SongTaste(
                songId = id,
                plays = plays,
                familiarity = sat(plays.toDouble(), 3.0),
                positive = positive,
                negative = negative,
                lastHeardMs = max(stat?.lastPlayed ?: 0L, outcomes.maxOfOrNull { it.lastAt } ?: 0L),
                weekPlays = stat?.weekPlays ?: 0,
                saved = id in savedSongs || id in forLater || id in explored,
                fadedOut = plays >= 3 && recentNegative >= 2 && recentPositive == 0,
            )
        }

        val artistIds = buildSet {
            input.artistStats.forEach { add(it.artistId) }
            addAll(artistOutcome.keys)
            addAll(savedArtists.keys)
            addAll(boosted)
            addAll(seeds)
            addAll(hiddenArtists)
            addAll(lessLike)
        }
        val statByArtist = input.artistStats.associateBy { it.artistId }
        val artists = artistIds.associateWith { id ->
            val stat = statByArtist[id]
            val outcomes = artistOutcome[id].orEmpty()
            val positiveLong = outcomes.sumOf { (it.positive + 0.25 * it.partial) * bucketWeight(it.bucket, LONG_HALF_LIFE_DAYS) }
            val positiveShort = outcomes.sumOf { (it.positive + 0.25 * it.partial) * bucketWeight(it.bucket, SHORT_HALF_LIFE_DAYS) }
            val negativeLong = outcomes.sumOf { it.negative * bucketWeight(it.bucket, LONG_HALF_LIFE_DAYS) }
            val saved = savedArtists[id]
            val savedStrength = 0.54 * sat(((saved?.songs ?: 0) + (saved?.liked ?: 0)).toDouble(), 6.0)
            val plays = stat?.plays ?: 0
            val lastPlayedAge = stat?.lastPlayed?.let { (wallNow - it).toDouble() / DAY_MS } ?: 365.0
            val depth = sat(plays.toDouble(), 6.0)
            val spread = sat((stat?.songs ?: 0).toDouble(), 3.0)
            val heardLong = ((0.65 * depth + 0.35 * spread) * (0.4 + 0.6 * halfLife(lastPlayedAge, LONG_HALF_LIFE_DAYS))).coerceIn(0.0, 1.0)
            val eventShort = (stat?.weekPlays ?: 0) * 1.0 + (stat?.monthPlays ?: 0) * 0.4
            val shortTerm = sat(eventShort + positiveShort, 5.0)
            val seedStrength = if (id in seeds) 0.45 else 0.0
            val rejection = negativeLong / (negativeLong + positiveLong + plays * 0.35 + 2.0)
            ArtistTaste(
                artistId = id,
                longTerm = max(max(heardLong, savedStrength), seedStrength),
                shortTerm = shortTerm,
                rejection = rejection,
                positive = positiveLong,
                negative = negativeLong,
                plays = plays,
                songs = stat?.songs ?: 0,
                saved = saved?.songs ?: 0,
                lastHeardMs = max(stat?.lastPlayed ?: 0L, outcomes.maxOfOrNull { it.lastAt } ?: 0L),
            )
        }

        val featureById = input.features.associateBy { it.songId }
        return TasteProfile(
            confidence = input.confidence,
            artists = artists,
            songs = songs,
            albums = input.albumStats.associate { album ->
                val age = (wallNow - album.lastPlayed).toDouble() / DAY_MS
                album.albumId to sat(album.plays.toDouble(), 8.0) * (0.4 + 0.6 * halfLife(age, LONG_HALF_LIFE_DAYS))
            },
            discovery = discoveryPersonality(input.discovery),
            sources = input.sourceStats.associate { it.source to Beta(it.positive.toDouble(), it.negative.toDouble()) },
            hiddenArtists = hiddenArtists,
            blockedSongs = blocked,
            softRejectedSongs = soft,
            lessLikeArtists = lessLike,
            boostedArtists = boosted,
            seedArtists = seeds,
            daypartLift = daypartLift(input.daypart),
            ignored = ignored(input.impressions, now),
            sound = soundProfile(input.songStats, featureById),
            features = featureById,
            recentSearches = input.recentSearches.map { it.trim().lowercase() }.filter { it.isNotEmpty() },
            nowMs = now,
        )
    }

    fun discoveryPersonality(stat: DiscoveryOutcomeStat?): DiscoveryPersonality {
        if (stat == null) return TasteProfile.Empty.discovery
        val tried = stat.recentFirstPositive + stat.recentFirstNegative
        val acceptance = (stat.recentFirstPositive + 0.55 * 6.0) / (tried + 6.0)
        val familiar = (stat.repeatListens + 0.5 * 10.0) / (stat.totalListens + 10.0)
        val trend = sat(stat.recentFirstListens.toDouble(), 12.0)
        return DiscoveryPersonality(
            appetite = (0.55 * acceptance + 0.3 * (1.0 - familiar) + 0.15 * trend).coerceIn(0.05, 0.95),
            noveltyAcceptance = acceptance,
            familiarShare = familiar,
            evidence = tried,
        )
    }

    private fun daypartLift(rows: Collection<ArtistDaypartStat>): Map<String, Double> {
        val here = rows.sumOf { it.here }
        val total = rows.sumOf { it.total }
        if (total == 0) return emptyMap()
        val base = ((here + 1.0) / (total + 4.0)).coerceIn(0.05, 0.95)
        return rows.associate { row ->
            val shrunk = (row.here + base * 6.0) / (row.total + 6.0)
            row.artistId to ln(shrunk / base).coerceIn(-1.0, 1.0)
        }
    }

    private fun ignored(rows: Collection<ImpressionFatigue>, now: Long): Map<String, Double> =
        rows.filter { it.shown > it.played }.associate { row ->
            val age = (now - row.lastShown).toDouble() / DAY_MS
            row.songId to sat((row.shown - row.played).toDouble(), 3.0) * exp(-age / 21.0)
        }

    private fun soundProfile(stats: Collection<HomeSongStat>, features: Map<String, SongFeature>): SoundProfile? {
        val heard = stats.mapNotNull { stat ->
            features[stat.songId]?.takeIf { it.confidence >= 0.3f }?.let { it to stat.plays.toDouble() }
        }
        val weight = heard.sumOf { it.second }
        if (weight <= 0.0) return null
        val mean = heard.sumOf { it.first.bpm * it.second } / weight
        val variance = heard.sumOf { (it.first.bpm - mean) * (it.first.bpm - mean) * it.second } / weight
        fun meanOf(pick: (SongFeature) -> Float?): Double? {
            val known = heard.filter { pick(it.first) != null }
            val w = known.sumOf { it.second }
            return if (w <= 0.0) null else known.sumOf { pick(it.first)!!.toDouble() * it.second } / w
        }
        return SoundProfile(
            bpm = mean,
            bpmSpread = sqrt(variance),
            energy = meanOf { it.energy },
            brightness = meanOf { it.brightness },
            measuredSongs = heard.size,
        )
    }
}

val CandidateSource.defaultQuality: Double
    get() = when (this) {
        CandidateSource.Rotation -> 0.8
        CandidateSource.Daypart -> 0.75
        CandidateSource.Recent -> 0.7
        CandidateSource.Rediscovery -> 0.6
        CandidateSource.OnDevice -> 0.6
        CandidateSource.LibraryUnheard -> 0.6
        CandidateSource.CoListen -> 0.6
        CandidateSource.RelatedSong -> 0.55
        CandidateSource.SpotifyMix -> 0.55
        CandidateSource.PlaylistNeighbour -> 0.52
        CandidateSource.GraphBridge -> 0.5
        CandidateSource.GraphNear -> 0.5
        CandidateSource.SeedArtist -> 0.5
        CandidateSource.ArtistTopSong -> 0.5
        CandidateSource.YouTubeRadio -> 0.5
        CandidateSource.NewRelease -> 0.45
        CandidateSource.GraphFar -> 0.42
        CandidateSource.Chart -> 0.4
    }
