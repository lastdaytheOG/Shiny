package com.shiny.music.home

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.HomePage
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.RelatedSongMap
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

/**
 * Home's network content: YouTube Charts, new releases and moods (the New tab reads those),
 * the signed-in account's YouTube Music mixes, and a digest of the
 * listener's top artists. Kept on disk as one block and refreshed part by part, only when a
 * part has gone stale — opening Home never calls the network for content it already has.
 *
 * Offline, or when YouTube cannot be reached, the last good block stays in place with its
 * age, and a part that has never been fetched is simply absent (its section is hidden).
 */
class HomeRemoteRepository(
    private val context: Context,
    private val database: MusicDatabase,
) {
    companion object {
        private const val FILE = "home_remote.json"
        private const val LEGACY_SNAPSHOT = "home_snapshot.json"
        const val CHARTS_TTL_MS = 60L * 60 * 1000
        const val EXPLORE_TTL_MS = 6L * 60 * 60 * 1000
        const val ARTIST_TTL_MS = 24L * 60 * 60 * 1000

        /** Even a forced refresh does not re-ask sooner than this. */
        const val MIN_FORCED_INTERVAL_MS = 10L * 60 * 1000
        const val MAX_DIGESTS = 4

        /**
         * The chart to show for a country: the weekly "Top 100 Music Videos", else the daily
         * one, else any other Top 100 that is not the live-performance chart (which YouTube
         * lists first in some countries), else the first chart that is not live performances.
         */
        fun pickChart(playlists: List<PlaylistItem>): PlaylistItem? {
            fun has(p: PlaylistItem, s: String) = p.title.contains(s, ignoreCase = true)
            val studio = playlists.filterNot { has(it, "Live") }
            return studio.firstOrNull { has(it, "Top 100") && has(it, "Music Videos") }
                ?: studio.firstOrNull { has(it, "Daily") }
                ?: studio.firstOrNull { has(it, "Top 100") }
                ?: studio.firstOrNull()
                ?: playlists.firstOrNull()
        }

        const val MAX_YOUTUBE_MIXES = 8

        /** The "Mixed for you" shelf's own page, which its "More" button opens. */
        private const val MIXED_FOR_YOU = "FEmusic_mixed_for_you"

        /**
         * The mixes YouTube Music made for this account: the "Mixed for you" shelf, plus any
         * personal mix elsewhere on its Home. Those carry `RDTMAK5uy_` ids (Supermix, My Mix,
         * Discover Mix, Replay Mix); curated playlists carry other prefixes.
         */
        fun pickYouTubeMixes(sections: List<HomePage.Section>): List<PlaylistItem> {
            val shelf = sections.firstOrNull { it.endpoint?.browseId == MIXED_FOR_YOU }
                ?.items?.filterIsInstance<PlaylistItem>().orEmpty()
            val personal = sections.flatMap { it.items }.filterIsInstance<PlaylistItem>()
                .filter { it.id.startsWith("RDTMAK5uy_") }
            return (shelf + personal).distinctBy { it.id }.take(MAX_YOUTUBE_MIXES)
        }

        /** What Home keeps of an artist page. */
        fun digest(page: ArtistPage, fetchedAt: Long): HomeArtistDigest {
            val albumSections = page.sections.filter { it.items.firstOrNull() is AlbumItem }
            // Each shelf lists newest first; the latest release is the newest of their heads.
            val latest = albumSections.mapNotNull { it.items.firstOrNull() as? AlbumItem }
                .maxWithOrNull(compareBy { it.year ?: 0 })
            return HomeArtistDigest(
                artistId = page.artist.id,
                name = page.artist.title,
                thumbnail = page.artist.thumbnail,
                fetchedAt = fetchedAt,
                latest = latest,
                albums = albumSections.flatMap { it.items.filterIsInstance<AlbumItem>() }.distinctBy { it.id }.take(12),
                related = page.sections.flatMap { it.items.filterIsInstance<ArtistItem>() }
                    .filter { it.id != page.artist.id }
                    .distinctBy { it.id }
                    .take(12),
            )
        }
    }

    private val file = File(context.filesDir, FILE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow<HomeRemote?>(null)
    val state: StateFlow<HomeRemote?> = _state.asStateFlow()

    /** Paints the last good block. Also removes the old Home's snapshot, which nothing reads now. */
    suspend fun restore() = withContext(Dispatchers.IO) {
        File(context.filesDir, LEGACY_SNAPSHOT).takeIf { it.exists() }?.delete()
        if (_state.value != null || !file.exists()) return@withContext
        runCatching { json.decodeFromString(HomeRemote.serializer(), file.readText()) }
            .onFailure { Timber.tag("HomeRemote").w(it, "unreadable, ignored") }
            .getOrNull()
            ?.takeIf { it.version == HomeRemote.VERSION }
            ?.let { restored -> if (_state.value == null) _state.value = restored }
    }

    /**
     * Brings stale parts up to date. [chartScopes] are the charts the listener chose (none
     * when charts are off); [topArtists] are the artists whose pages feed Deep Dive and
     * personal new releases; [force] (pull to refresh) re-asks anything older than
     * [MIN_FORCED_INTERVAL_MS]. Returns true when anything changed.
     */
    suspend fun refresh(
        chartScopes: List<String>,
        account: String?,
        topArtists: List<String>,
        force: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val current = _state.value ?: HomeRemote()
            fun stale(at: Long, ttl: Long) = now - at >= (if (force) MIN_FORCED_INTERVAL_MS else ttl)

            val exploreDue = stale(current.exploreAt, EXPLORE_TTL_MS) || current.account != account
            // Only a signed-in account has mixes; signing out (or switching) drops the old ones.
            val mixesDue = account != null && (stale(current.youtubeMixesAt, EXPLORE_TTL_MS) || current.account != account)
            val mixesGone = account != current.account && current.youtubeMixes.isNotEmpty()
            val chartsDue = chartScopes.isNotEmpty() &&
                (stale(current.chartsAt, CHARTS_TTL_MS) || current.chartScopes != chartScopes)
            val wantedArtists = topArtists.filter(HomeRanking::isStreamableId).take(MAX_DIGESTS)
            val artistsDue = wantedArtists.filter { id ->
                val digest = current.artists[id]
                digest == null || stale(digest.fetchedAt, ARTIST_TTL_MS)
            }
            if (!exploreDue && !chartsDue && !mixesDue && !mixesGone && artistsDue.isEmpty()) return@withLock false

            var next = current.copy(account = account)
            if (mixesGone) next = next.copy(youtubeMixes = emptyList(), youtubeMixesAt = 0)
            supervisorScope {
                val explore = if (exploreDue) async { YouTube.explore().onFailure(::reportException).getOrNull() } else null
                val charts = if (chartsDue) async { fetchCharts(chartScopes, current, now) } else null
                val mixes = if (mixesDue) async { fetchYouTubeMixes() } else null
                val digests = artistsDue.map { id ->
                    async { YouTube.artist(id).onFailure(::reportException).getOrNull()?.let { digest(it, now) } }
                }

                explore?.await()?.let { page ->
                    next = next.copy(exploreAt = now, newReleases = page.newReleaseAlbums, moods = page.moodAndGenres)
                }
                // A failed fetch keeps the charts already held, with their own age.
                charts?.await()?.takeIf { it.isNotEmpty() }?.let {
                    next = next.copy(chartsAt = now, charts = it, chartScopes = chartScopes)
                }
                // A failed read keeps the mixes already held; an account with none gets none.
                mixes?.await()?.let { next = next.copy(youtubeMixesAt = now, youtubeMixes = it) }
                val fresh = digests.awaitAll().filterNotNull().associateBy { it.artistId }
                // Keep digests of artists still near the top; drop week-old ones for artists who left it.
                val kept = next.artists.filter { (id, d) -> id in wantedArtists || now - d.fetchedAt < 7 * ARTIST_TTL_MS }
                next = next.copy(artists = kept + fresh)
            }

            val changed = next != current
            if (changed) {
                _state.value = next
                save(next)
            }
            changed
        }
    }

    /**
     * One chart per scope, in order, each fetched in full (up to 100 songs), carrying the
     * ranking Shiny last saw so movement can be shown.
     *
     * YouTube publishes today's chart and nothing else, so a climb or a fall can only come
     * from comparing two of our own fetches. The snapshot is only replaced when the chart
     * we held was captured on an *earlier day* — the charts refresh hourly, and comparing
     * this hour against the last would show every song as steady and call it movement.
     */
    private suspend fun fetchCharts(scopes: List<String>, current: HomeRemote, now: Long): List<HomeChart> = supervisorScope {
        val heldDay = current.chartsAt / HOME_DAY_MS
        val today = now / HOME_DAY_MS
        scopes.distinct().map { scope ->
            async {
                val playlist = YouTube.chartPlaylists(scope).onFailure(::reportException).getOrNull()
                    ?.let(::pickChart) ?: return@async null
                val songs = YouTube.playlist(playlist.id).onFailure(::reportException).getOrNull()
                    ?.songs?.take(100).orEmpty()
                if (songs.isEmpty()) return@async null
                val held = current.charts.firstOrNull { it.scope == scope }
                val rolled = held != null && current.chartsAt > 0 && heldDay < today
                HomeChart(
                    playlistId = playlist.id,
                    title = playlist.title,
                    scope = scope,
                    songs = songs,
                    previous = when {
                        held == null -> emptyMap()
                        rolled -> held.songs.mapIndexed { index, song -> song.id to index + 1 }.toMap()
                        else -> held.previous
                    },
                    previousDay = when {
                        held == null -> 0L
                        rolled -> heldDay
                        else -> held.previousDay
                    },
                )
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * The signed-in account's YouTube Music mixes, from its YouTube Music Home: the first page,
     * and the next one when the mixes are not on the first. Null when YouTube could not be read.
     */
    private suspend fun fetchYouTubeMixes(): List<PlaylistItem>? {
        val first = YouTube.home().onFailure(::reportException).getOrNull() ?: return null
        pickYouTubeMixes(first.sections).takeIf { it.isNotEmpty() }?.let { return it }
        val more = first.continuation?.let { YouTube.home(continuation = it).getOrNull() } ?: return emptyList()
        return pickYouTubeMixes(first.sections + more.sections)
    }

    /**
     * Fetches related songs for favourites the related-song graph has nothing for yet, and
     * stores them exactly as `MusicService` does while a song plays. Returns how many seeds
     * gained related songs.
     */
    suspend fun fetchRelated(seedIds: List<String>): Int = withContext(Dispatchers.IO) {
        supervisorScope {
            seedIds.map { seedId ->
                async {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()?.relatedEndpoint
                        ?: return@async false
                    val songs: List<SongItem> = YouTube.related(endpoint).getOrNull()?.songs.orEmpty()
                    if (songs.isEmpty()) return@async false
                    database.withTransaction {
                        songs.forEach { song ->
                            insert(song.toMediaMetadata())
                            insert(RelatedSongMap(songId = seedId, relatedSongId = song.id))
                        }
                    }
                    true
                }
            }.awaitAll().count { it }
        }
    }

    /**
     * Finds the YouTube channel of artists known only by name from device files, so Home can
     * treat them like any other artist. Only an exact (case-insensitive) name match is kept;
     * a miss is stored as an empty id so the same search is not made again.
     */
    suspend fun resolveArtists(names: List<String>): Int = withContext(Dispatchers.IO) {
        val wanted = names.filter { it.lowercase() !in _state.value?.artistIds.orEmpty() }
        if (wanted.isEmpty()) return@withContext 0
        val found = supervisorScope {
            wanted.map { name ->
                async {
                    val result = YouTube.search(name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                        ?: return@async null // a network failure is not a miss: ask again later
                    val match = result.items.filterIsInstance<ArtistItem>()
                        .firstOrNull { it.title.trim().equals(name.trim(), ignoreCase = true) }
                    name.lowercase() to (match?.id ?: "")
                }
            }.awaitAll().filterNotNull().toMap()
        }
        if (found.isEmpty()) return@withContext 0
        mutex.withLock {
            val next = (_state.value ?: HomeRemote()).let { it.copy(artistIds = it.artistIds + found) }
            _state.value = next
            save(next)
        }
        found.values.count { it.isNotBlank() }
    }

    private fun save(remote: HomeRemote) {
        var tmp: File? = null
        runCatching {
            tmp = File.createTempFile(FILE, ".tmp", context.filesDir).also {
                it.writeText(json.encodeToString(HomeRemote.serializer(), remote))
                check(it.renameTo(file)) { "rename failed" }
            }
        }.onFailure {
            tmp?.delete()
            Timber.tag("HomeRemote").w(it, "not saved")
        }
    }
}
