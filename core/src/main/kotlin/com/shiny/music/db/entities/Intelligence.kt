package com.shiny.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecSurface {
    Direct,
    Collection,
    HomeMix,
    HomeDiscover,
    HomeRotation,
    HomeAroundNow,
    HomeRediscover,
    HomeContinue,
    Discover,
    Radio,
    Autoplay,
    SmartShuffle,
    OfflineAutoplay,
    ExploreMore,
    ;

    val recommended: Boolean get() = this != Direct && this != Collection
}

enum class CandidateSource(val kindIsDiscovery: Boolean) {
    RelatedSong(true),
    GraphNear(true),
    GraphFar(true),
    GraphBridge(true),
    CoListen(true),
    PlaylistNeighbour(true),
    ArtistTopSong(true),
    SeedArtist(true),
    SpotifyMix(true),
    Chart(true),
    NewRelease(true),
    YouTubeRadio(true),
    LibraryUnheard(false),
    Rotation(false),
    Daypart(false),
    Rediscovery(false),
    Recent(false),
    OnDevice(false),
}

@Immutable
@Entity(
    tableName = "listen_outcome",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("songId"), Index("startedAt"), Index("sessionId")],
)
data class ListenOutcome(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val zoneOffsetMinutes: Int,
    val listenedMs: Long,
    val durationMs: Long,
    val completion: Float,
    val endReason: String,
    val kind: String,
    val surface: String,
    val pickSource: String?,
    val selection: String,
    val queuePosition: Int,
    val firstListen: Boolean,
    val replayGapDays: Int?,
    val impressionId: Long?,
)

@Immutable
@Entity(
    tableName = "recommendation_impression",
    indices = [Index("songId"), Index("shownAt")],
)
data class RecommendationImpression(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val surface: String,
    val shownAt: Long,
    val position: Int,
    val candidateSource: String?,
    val reason: String?,
    val sessionId: Long,
    val selectedAt: Long? = null,
    val playedAt: Long? = null,
    val outcomeKind: String? = null,
)

@Immutable
@Entity(
    tableName = "music_feedback",
    primaryKeys = ["targetType", "targetId", "kind"],
)
data class MusicFeedback(
    val targetType: String,
    val targetId: String,
    val kind: String,
    val label: String?,
    val createdAt: Long,
    val expiresAt: Long?,
)

@Immutable
@Entity(
    tableName = "music_relation",
    primaryKeys = ["fromId", "toId", "kind"],
    indices = [Index("toId")],
)
data class MusicRelation(
    val fromId: String,
    val toId: String,
    val kind: String,
    val weight: Float,
    val evidence: Int,
    val toName: String?,
    val updatedAt: Long,
)

data class SongOutcomeBucket(
    val songId: String,
    val bucket: Int,
    val positive: Int,
    val partial: Int,
    val negative: Int,
    val listenedMs: Long,
    val lastAt: Long,
)

data class ArtistOutcomeBucket(
    val artistId: String,
    val bucket: Int,
    val positive: Int,
    val partial: Int,
    val negative: Int,
    val lastAt: Long,
)

data class SourceOutcomeStat(
    val source: String,
    val positive: Int,
    val negative: Int,
)

data class DiscoveryOutcomeStat(
    val recentFirstListens: Int,
    val recentFirstPositive: Int,
    val recentFirstNegative: Int,
    val priorFirstListens: Int,
    val priorFirstPositive: Int,
    val totalListens: Int,
    val repeatListens: Int,
)

data class ArtistDaypartStat(
    val artistId: String,
    val here: Int,
    val total: Int,
)

data class ImpressionFatigue(
    val songId: String,
    val shown: Int,
    val played: Int,
    val lastShown: Long,
)

data class RecentListen(
    val songId: String,
    val kind: String,
    val startedAt: Long,
    val surface: String,
    val selection: String,
    val artistId: String?,
)

data class ArtistEdgeRow(
    val fromId: String,
    val toId: String,
    val weight: Float,
    val kind: String,
    val toName: String?,
)

data class CandidateRow(
    val songId: String,
    val title: String,
    val albumId: String?,
    val year: Int?,
    val duration: Int,
    val explicit: Boolean,
    val isVideo: Boolean,
    val isLocal: Boolean,
    val isDownloaded: Boolean,
    val artistId: String?,
    val artistName: String?,
    val position: Int?,
)

data class SurfaceMetricRow(
    val surface: String,
    val shown: Int,
    val selected: Int,
    val played: Int,
    val meaningful: Int,
    val skipped: Int,
    val distinctSongs: Int,
)

data class SurfaceListenRow(
    val surface: String,
    val listens: Int,
    val completed: Int,
    val meaningful: Int,
    val partial: Int,
    val skipped: Int,
    val accidental: Int,
    val interrupted: Int,
    val replays: Int,
    val firstListens: Int,
    val firstPositive: Int,
    val distinctArtists: Int,
)

data class SongFeature(
    val songId: String,
    val bpm: Float,
    val confidence: Float,
    val energy: Float?,
    val brightness: Float?,
    val dynamics: Float?,
    val keyPitchClass: Int?,
    val keyIsMinor: Boolean?,
)

data class AnalysisCandidate(
    val songId: String,
    val isLocal: Boolean,
)

data class ArtistSongRow(
    val songId: String,
    val artistId: String,
)

data class ArtistNameRow(
    val id: String,
    val name: String,
)
