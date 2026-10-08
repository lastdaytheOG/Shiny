package com.shiny.music.home

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.MoodAndGenres
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.HomeAlbumStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeLibraryCounts
import com.shiny.music.db.entities.HomeRelatedLink
import com.shiny.music.db.entities.HomeSavedArtist
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.Song
import com.shiny.music.db.entities.SpeedDialItem
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.ZoneOffset

const val HOME_DAY_MS = 86_400_000L

/**
 * "Now" on the listen history's clock: the device's wall time read as if it were UTC, which
 * is how `event.timestamp` is written. Comparing history against `System.currentTimeMillis()`
 * would shift every window by the timezone offset.
 */
fun homeWallNowMs(): Long = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()

/** The four parts of the day Home tunes itself to. Same boundaries as the greeting. */
enum class Daypart(val hours: List<Int>) {
    Morning((5..11).toList()),
    Afternoon((12..16).toList()),
    Evening((17..21).toList()),
    Night(listOf(22, 23, 0, 1, 2, 3, 4));

    companion object {
        fun of(hour: Int): Daypart = entries.first { hour in it.hours }
    }
}

/**
 * A coarse label for the listener, derived from [HomeTasteProfile] rather than measured
 * separately, and kept for the things that genuinely want a name for the whole listener
 * (the New tab's copy, analytics of Home's own shape).
 *
 * **Nothing on Home is gated on it.** Sections appear when they have real content and are
 * ordered by [HomeOrder] from continuous weights; this is a description of the result, not
 * a switch that produces it.
 */
enum class ListenerProfile {
    /** Still learning: low confidence, little repetition. */
    New,

    /** Enough history for rotation, discovery and context. */
    Established,

    /** Most listening is of downloads and files on the device. */
    LocalHeavy,
}

/** Everything the feed is built for, other than data. */
data class HomeContext(
    /** Wall time as UTC (see [homeWallNowMs]). */
    val nowMs: Long,
    val daypart: Daypart,
    val online: Boolean,
    /** Changes once per daypart and on every pull to refresh: the feed's controlled variety. */
    val seed: Long,
    val hideExplicit: Boolean = false,
    val hideVideoSongs: Boolean = false,
    val showPinned: Boolean = true,
    /** The "Quick picks" content setting set to "Last song listened". */
    val discoverFromLastSong: Boolean = false,
    /** The charts the listener asked for, in order ([HomeCharts.scopes]); null shows whatever is cached. */
    val chartScopes: List<String>? = null,
    /**
     * Signed in to YouTube Music, so the liked songs are that account's and Home has a
     * YouTube Music row. Signed out, the likes are Shiny's own and stay a Lately tile.
     */
    val youtubeSignedIn: Boolean = false,
)

/**
 * The listen history and library as Home needs them, read in one pass on IO.
 *
 * The ranked id lists are computed from the aggregates by [HomeRanking] before the rows are
 * loaded, so only songs that can actually appear on Home are read with their relations.
 */
data class HomeSignals(
    val counts: HomeLibraryCounts,
    val songStats: Map<String, HomeSongStat>,
    val artistStats: List<HomeArtistStat>,
    val albumStats: List<HomeAlbumStat>,
    /** Distinct days with plays in the current daypart, over [HomeRanking.DAYPART_WINDOW_DAYS]. */
    val daypartDays: Int,
    /** Plays in the last 30 days of songs that play offline. */
    val monthOfflinePlays: Int,
    val rotationIds: List<String>,
    val daypartIds: List<String>,
    val rediscoverIds: List<String>,
    /** The most recently played songs, newest first: the mix's fallback for a young history. */
    val recentIds: List<String> = emptyList(),
    /** Never-played songs related to what the user plays, best first, each with its seed. */
    val discovery: List<HomeRelatedLink>,
    val songs: Map<String, Song>,
    val artists: Map<String, Artist>,
    val albums: Map<String, Album>,
    val recentlyAdded: List<Song>,
    val offlineSongs: List<Song>,
    val unexplored: List<Song>,
    val playlists: List<Playlist>,
    val pinned: List<SpeedDialItem>,
    /**
     * The pins that are library playlists (an imported Spotify playlist, one made here), by
     * id. A pin only carries an id, and opened as a YouTube playlist a library id is an
     * empty page, so these are shown as the library playlist itself.
     */
    val pinnedPlaylists: Map<String, Playlist> = emptyMap(),
    /**
     * Discovery seeds, song id → weight: the listener's played favourites first, then the
     * songs they chose without playing much (see [HomeRanking.savedSeedWeights]).
     */
    val seeds: Map<String, Double> = emptyMap(),
    /** Streamable songs the listener chose (liked, saved, in their playlists or Spotify mixes). */
    val savedSongs: Int = 0,
    /** Artists across everything the listener chose, strongest first; ids may be local. */
    val savedArtists: List<HomeSavedArtist> = emptyList(),
    /** Covers of the most recently liked songs, for the Liked Songs tile. */
    val likedCovers: List<String> = emptyList(),
    /** The imported Spotify Liked Songs; null when the listener never imported them. */
    val spotifyLiked: Playlist? = null,
) {
    val monthPlays: Int get() = songStats.values.sumOf { it.monthPlays }

    companion object {
        val Empty = HomeSignals(
            counts = HomeLibraryCounts(0, 0, 0, 0, 0, 0),
            songStats = emptyMap(),
            artistStats = emptyList(),
            albumStats = emptyList(),
            daypartDays = 0,
            monthOfflinePlays = 0,
            rotationIds = emptyList(),
            daypartIds = emptyList(),
            rediscoverIds = emptyList(),
            discovery = emptyList(),
            songs = emptyMap(),
            artists = emptyMap(),
            albums = emptyMap(),
            recentlyAdded = emptyList(),
            offlineSongs = emptyList(),
            unexplored = emptyList(),
            playlists = emptyList(),
            pinned = emptyList(),
        )
    }
}

// ---------------------------------------------------------------------------------------
// Remote — one cached block, refreshed per part
// ---------------------------------------------------------------------------------------

/** One ranked chart exactly as YouTube Charts publishes it. */
@Serializable
data class HomeChart(
    val playlistId: String,
    /** YouTube's own name for the chart, shown verbatim ("Top 100 Music Videos India"). */
    val title: String,
    /** The country code asked for; "ZZ" is Global. */
    val scope: String,
    val songs: List<SongItem>,
    /**
     * Where each song stood the last time this chart was fetched from a *different* day,
     * so a rank can be shown as a climb, a fall or a new entry.
     *
     * Kept by [HomeRemoteRepository] across refreshes rather than requested: YouTube
     * publishes today's chart, not yesterday's, and comparing two of our own fetches is
     * the only honest way to show movement. A song missing from the map is new to the
     * chart *as far as Shiny has seen it*, which is why the UI says "new" only once the
     * map is non-empty — before the second day there is nothing to compare against.
     */
    val previous: Map<String, Int> = emptyMap(),
    /** The day ([previous] was captured on, as `System.currentTimeMillis() / HOME_DAY_MS`. */
    val previousDay: Long = 0,
)

/** How a song moved since the last chart Shiny saw. */
sealed interface ChartMove {
    data object Steady : ChartMove
    data class Up(val places: Int) : ChartMove
    data class Down(val places: Int) : ChartMove
    data object New : ChartMove
    /** No earlier chart to compare against yet. */
    data object Unknown : ChartMove
}

/** Where [songId] at [position] stood before, if this chart has seen a previous day. */
fun HomeChart.moveOf(songId: String, position: Int): ChartMove {
    if (previous.isEmpty()) return ChartMove.Unknown
    val before = previous[songId] ?: return ChartMove.New
    return when {
        before == position -> ChartMove.Steady
        before > position -> ChartMove.Up(before - position)
        else -> ChartMove.Down(position - before)
    }
}

/**
 * The charts [ctx] asks for, in the order it asks for them, filtered to what the listener is
 * allowed to see. Filtering keeps each song's true rank rather than renumbering the
 * survivors, and a chart left with fewer than five songs is dropped rather than shown short.
 *
 * Home and New both read the same cached block through this, so neither can show a different
 * country from the other.
 */
fun chartsFor(remote: HomeRemote, ctx: HomeContext): List<HomeChart> {
    val wanted = ctx.chartScopes
    val chosen = if (wanted == null) remote.charts else wanted.mapNotNull { scope -> remote.charts.firstOrNull { it.scope == scope } }
    return chosen.mapNotNull { chart ->
        val songs = chart.songs.mapIndexedNotNull { index, song ->
            if (ctx.hideExplicit && song.explicit) return@mapIndexedNotNull null
            if (ctx.hideVideoSongs && song.isVideoSong) return@mapIndexedNotNull null
            song.copy(chartPosition = index + 1)
        }
        chart.copy(songs = songs).takeIf { songs.size >= 5 }
    }
}

/** What Home keeps of an artist page: the newest release, other albums, similar artists. */
@Serializable
data class HomeArtistDigest(
    val artistId: String,
    val name: String,
    val thumbnail: String?,
    val fetchedAt: Long,
    val latest: AlbumItem? = null,
    val albums: List<AlbumItem> = emptyList(),
    val related: List<ArtistItem> = emptyList(),
)

/**
 * Home's network content, persisted between launches. Each part has its own age: charts go
 * stale after an hour, releases and moods after six, an artist digest after a day.
 */
@Serializable
data class HomeRemote(
    val version: Int = VERSION,
    /** The chart scopes [charts] were fetched for. */
    val chartScopes: List<String> = emptyList(),
    val account: String? = null,
    val chartsAt: Long = 0,
    val charts: List<HomeChart> = emptyList(),
    val exploreAt: Long = 0,
    val newReleases: List<AlbumItem> = emptyList(),
    val moods: List<MoodAndGenres.Item> = emptyList(),
    val artists: Map<String, HomeArtistDigest> = emptyMap(),
    /**
     * Artist name (lowercase) → YouTube channel id, for artists known only from device files.
     * An empty id records a search that found nobody, so it is not repeated.
     */
    val artistIds: Map<String, String> = emptyMap(),
    /** When [youtubeMixes] were read from the signed-in YouTube Music Home. */
    val youtubeMixesAt: Long = 0,
    /** The mixes YouTube Music made for the signed-in account (Supermix, Discover Mix...). */
    val youtubeMixes: List<PlaylistItem> = emptyList(),
) {
    companion object {
        const val VERSION = 2
    }
}

data class HomeFeed(
    val sections: List<HomeSection>,
    val profile: ListenerProfile,
    val daypart: Daypart,
    val online: Boolean,
    /** What Home knew about the listener when it built this feed. */
    val taste: HomeTasteProfile = HomeTasteProfile.Unknown,
) {
    companion object {
        val Empty = HomeFeed(emptyList(), ListenerProfile.New, Daypart.Morning, true)
    }
}

/** One block of Home. Every section exists only when it has real content to show. */
sealed interface HomeSection {
    val key: String
}

/**
 * The day's mix: what the listener keeps returning to at this hour, with a measured share
 * of music they have never played.
 *
 * It exists from the first counted play rather than from the twelfth. With almost no
 * history the pool is simply what was played lately plus what the library holds, the
 * [fresh] share is at its widest, and [confidence] tells the card to say so — "a start",
 * not "your evenings". As the rotation becomes real the same section fills out underneath
 * the same title. Nothing switches on.
 */
data class HeroMixSection(
    val daypart: Daypart,
    val songs: List<Song>,
    val familiar: Int,
    val fresh: Int,
    val offlineOnly: Boolean,
    /** [HomeTasteProfile.confidence] when the mix was built: how much of it is really theirs. */
    val confidence: Double = 0.0,
) : HomeSection {
    override val key get() = "hero_mix"

    /** Below this the mix is mostly Shiny's suggestion, and the card says so. */
    val tentative: Boolean get() = confidence < 0.18
}

/**
 * The opening of a Home with no listening behind it at all.
 *
 * Not an onboarding wall and not a progress meter — those told a new listener to come back
 * later, which is the one thing a music app must never say. This is the best real content
 * Shiny has for someone it has never heard: the top of the chart for where they are, or
 * the music already on their device. It is a *place to start*, and it disappears the
 * moment there is a mix to put there instead.
 */
data class StartHereSection(
    /** The chart to open on, when there is a network and a chart for this country. */
    val chart: HomeChart?,
    /** Songs already on the device, when there are any: playable with no network at all. */
    val deviceSongs: List<Song>,
    val localSongs: Int,
    val downloadedSongs: Int,
    val online: Boolean,
) : HomeSection {
    override val key get() = "start_here"
}

/**
 * The thread back into whatever was last played: more of that artist, the rest of that
 * album, or songs like it.
 *
 * This is the first personal section a listener ever sees, and it needs exactly one
 * counted play to exist. It fades out as [RotationSection] and [AroundNowSection] fill
 * in — by then there is something better to say than "you played this once".
 */
data class ContinueSection(
    val seed: Song,
    val kind: ContinueKind,
    val songs: List<Song>,
    /** The artist or album the thread runs through, when there is one to name. */
    val throughName: String?,
    val throughId: String?,
    /** How sure Shiny is that this is a preference rather than a single listen. */
    val tier: AffinityTier,
) : HomeSection {
    override val key get() = "continue"
}

enum class ContinueKind {
    /** More by the artist just played. */
    Artist,

    /** The rest of an album that was started. */
    Album,

    /** Songs related to the one just played. */
    Related,
}

data class OfflineNoticeSection(val playable: Int) : HomeSection {
    override val key get() = "offline_notice"
}

/** Quick way back into what was just playing: albums, artists, playlists, pins. */
data class LatelySection(val tiles: List<LatelyTile>) : HomeSection {
    override val key get() = "lately"
}

sealed interface LatelyTile {
    val key: String
}

data class PinnedTile(val item: SpeedDialItem) : LatelyTile {
    override val key get() = "pin_${item.id}"
}

data class AlbumTile(val album: Album, val songsHeard: Int) : LatelyTile {
    override val key get() = "album_${album.id}"
}

data class ArtistTile(val artist: Artist, val plays: Int, val image: String?) : LatelyTile {
    override val key get() = "artist_${artist.id}"
}

data class PlaylistTile(val playlist: Playlist, val pinned: Boolean = false) : LatelyTile {
    override val key get() = "playlist_${playlist.id}"
}

data class LikedTile(val count: Int) : LatelyTile {
    override val key get() = "liked"
}

data class DownloadsTile(val count: Int) : LatelyTile {
    override val key get() = "downloads"
}

data class DeviceTile(val count: Int) : LatelyTile {
    override val key get() = "device"
}

/** A song with the numbers that put it where it is. */
data class RankedSong(
    val song: Song,
    val plays: Int,
    val monthPlays: Int,
    val weekPlays: Int,
    /** Wall time as UTC, like the history (see [homeWallNowMs]). */
    val lastPlayed: Long,
)

/** What the listener keeps returning to this month. */
data class RotationSection(val songs: List<RankedSong>) : HomeSection {
    override val key get() = "rotation"
}

/** What the listener tends to play at this time of day, and moods to set one. */
data class AroundNowSection(
    val daypart: Daypart,
    val songs: List<Song>,
    val moods: List<MoodAndGenres.Item>,
) : HomeSection {
    override val key get() = "around_now"
}

/** Moods for this part of the day when there is not yet a pattern to show. */
data class MoodsSection(
    val daypart: Daypart,
    val moods: List<MoodAndGenres.Item>,
) : HomeSection {
    override val key get() = "moods"
}

data class DiscoverPick(val song: Song, val because: Song)

/**
 * Songs the listener has never played, each tied to one they have.
 *
 * [lead] is the song most of these came from, when one song dominates the seeds — that is
 * what lets the shelf be headed "More like <song>" instead of the same flat "Discover"
 * every time, without ever claiming a connection the related-song graph does not hold.
 */
data class DiscoverSection(
    val picks: List<DiscoverPick>,
    val lead: Song? = null,
    val leadArtist: String? = null,
) : HomeSection {
    override val key get() = "discover"
}

/** One of the listener's artists, opened up: their unheard albums and artists like them. */
data class DeepDiveSection(
    val digest: HomeArtistDigest,
    val plays: Int,
    val songsHeard: Int,
    val albums: List<AlbumItem>,
    val related: List<ArtistItem>,
    /** Known from the listener's saved songs rather than plays: [songsHeard] counts saved songs. */
    val fromLibrary: Boolean = false,
) : HomeSection {
    override val key get() = "deep_dive"
}

data class ChartsSection(
    val charts: List<HomeChart>,
    /** When Shiny last fetched them, in System.currentTimeMillis(). */
    val refreshedAt: Long,
) : HomeSection {
    override val key get() = "charts"
}

/** Songs once played a lot and not in a long while. */
data class RediscoverSection(val songs: List<RankedSong>) : HomeSection {
    override val key get() = "rediscover"
}

data class AlbumProgress(val album: Album, val heard: Int, val total: Int)

/** Albums the listener has started and not finished. */
data class AlbumsInProgressSection(val albums: List<AlbumProgress>) : HomeSection {
    override val key get() = "albums_in_progress"
}

data class RecentlyAddedSection(val songs: List<Song>) : HomeSection {
    override val key get() = "recently_added"
}

/** Everything that plays without a network: downloads and files on the device. */
data class ReadyOfflineSection(
    val localSongs: Int,
    val downloadedSongs: Int,
    val songs: List<Song>,
) : HomeSection {
    override val key get() = "ready_offline"
}

/** A few true sentences about the week's listening. */
data class InsightsSection(
    val weekPlays: Int,
    val weekSongs: Int,
    val weekArtists: Int,
    val weekMinutes: Int,
    val newSongs: Int,
    val topArtist: Artist?,
    /** The artist's photo, from the library or else from their cached artist page. */
    val topArtistImage: String?,
    val topArtistPlays: Int,
    val replayed: Song?,
    val replayedPlays: Int,
) : HomeSection {
    override val key get() = "insights"
}

enum class SurpriseKind { Forgotten, Unexplored, NewToYou, OnDevice }

data class SurprisePool(val kind: SurpriseKind, val weight: Double, val songs: List<Song>)

/**
 * One of the listener's Spotify mixes (Daily Mix, Discover Weekly, Release Radar...),
 * mirrored into a local playlist whose songs were matched on YouTube.
 */
@Serializable
data class HomeSpotifyMix(
    val spotifyId: String,
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    /** The hidden local playlist the mix plays from. */
    val localPlaylistId: String,
    /** Songs matched on YouTube at the last sync; 0 until the first sync finishes. */
    val songCount: Int = 0,
    /** When the songs were last matched, in System.currentTimeMillis(); 0 = never. */
    val syncedAt: Long = 0,
)

/** A service's liked songs, as the first tile of that service's row. */
data class HomeLikedSongs(val count: Int, val covers: List<String>)

/**
 * The listener's Spotify, as Spotify made it: their Liked Songs, then the mixes Spotify made
 * for them. Spotify and YouTube Music each have a row of their own and never share one — the
 * listener reads them as two libraries and wants to see which is which (2026-09-30).
 */
data class SpotifySection(
    /** The imported Liked Songs; null when they were never imported. */
    val liked: HomeLikedSongs?,
    val mixes: List<HomeSpotifyMix>,
) : HomeSection {
    override val key get() = "spotify"
}

/** The signed-in YouTube Music account: its liked songs, then the mixes it made for them. */
data class YouTubeSection(
    val liked: HomeLikedSongs?,
    val mixes: List<PlaylistItem>,
) : HomeSection {
    override val key get() = "youtube"
}

/** One button that picks something from the listener's own music universe. */
data class SurpriseSection(val pools: List<SurprisePool>) : HomeSection {
    override val key get() = "surprise"
    val size: Int get() = pools.sumOf { it.songs.size }
}
