/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * Modifications Copyright (C) 2026 Shiny Project.
 * Modified from the original work by the Shiny Project in 2026; the git history records
 * each change and its date. Distributed under GPL-3.0 as part of Shiny.
 */

package com.shiny.music.spotifyimport

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.shiny.music.R
import com.shiny.music.constants.SpotifyAccessTokenExpiresAtKey
import com.shiny.music.constants.SpotifyAccessTokenKey
import com.shiny.music.constants.SpotifyAccountAvatarUrlKey
import com.shiny.music.constants.SpotifyAccountNameKey
import com.shiny.music.constants.SpotifySpDcKey
import com.shiny.music.constants.SpotifySpKeyKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.PlaylistEntity
import com.shiny.music.db.entities.PlaylistSongMap
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.pages.SearchResult
import com.shiny.music.models.MediaMetadata
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.spotify.Spotify
import com.shiny.music.spotify.SpotifyAuth
import com.shiny.music.spotify.SpotifyMapper
import com.shiny.music.spotify.models.SpotifyPlaylist
import com.shiny.music.spotify.models.SpotifyPlaylistTracksRef
import com.shiny.music.spotify.models.SpotifyTrack
import com.shiny.music.utils.clearWebAuthSession
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.reportException
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SpotifyImportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val mapperMutex = Mutex()
    private val matchCache = SpotifyMatchCache(context)

    private val _waitingForNetwork = MutableStateFlow(false)
    private var networkWaiters = 0

    /**
     * True while an import is paused on a network the app cannot use, so the progress
     * dialog can say it is waiting rather than look stuck.
     */
    val waitingForNetwork: StateFlow<Boolean> = _waitingForNetwork.asStateFlow()

    /**
     * The sources the last [loadSources] returned in this process, so reopening the Spotify
     * page shows them at once while the refresh runs. Dropped on [logout].
     */
    @Volatile
    var cachedSources: List<SpotifyImportSource>? = null
        private set

    suspend fun restoreSession(): SpotifyImportSession =
        withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            val token = prefs[SpotifyAccessTokenKey].orEmpty()
            val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            val accountName = prefs[SpotifyAccountNameKey].orEmpty()
            val avatarUrl = prefs[SpotifyAccountAvatarUrlKey]

            if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
                Spotify.accessToken = token
                return@withContext SpotifyImportSession(
                    isAuthenticated = true,
                    accountName = accountName,
                    accountAvatarUrl = avatarUrl,
                )
            }

            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) {
                return@withContext SpotifyImportSession()
            }

            refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty())
                .fold(
                    onSuccess = {
                        val refreshed = context.dataStore.data.first()
                        SpotifyImportSession(
                            isAuthenticated = true,
                            accountName = refreshed[SpotifyAccountNameKey].orEmpty(),
                            accountAvatarUrl = refreshed[SpotifyAccountAvatarUrlKey],
                        )
                    },
                    onFailure = {
                        if (it is CancellationException) throw it
                        reportException(it)
                        if (it is Spotify.SpotifyException && (it.statusCode == 401 || it.statusCode == 403)) {
                            // Spotify turned the saved login down: this really is signed out.
                            SpotifyImportSession()
                        } else {
                            // No answer (offline, a timeout) says nothing about the login,
                            // which is still saved; the next request refreshes the token.
                            SpotifyImportSession(
                                isAuthenticated = true,
                                accountName = accountName,
                                accountAvatarUrl = avatarUrl,
                            )
                        }
                    },
                )
        }

    suspend fun connectWithCookies(
        spDc: String,
        spKey: String,
    ): SpotifyImportSession =
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[SpotifySpDcKey] = spDc
                if (spKey.isNotBlank()) {
                    prefs[SpotifySpKeyKey] = spKey
                } else {
                    prefs.remove(SpotifySpKeyKey)
                }
            }
            refreshAccessToken(spDc = spDc, spKey = spKey).getOrThrow()
            val prefs = context.dataStore.data.first()
            SpotifyImportSession(
                isAuthenticated = true,
                accountName = prefs[SpotifyAccountNameKey].orEmpty(),
                accountAvatarUrl = prefs[SpotifyAccountAvatarUrlKey],
            )
        }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs.remove(SpotifySpDcKey)
                prefs.remove(SpotifySpKeyKey)
                prefs.remove(SpotifyAccessTokenKey)
                prefs.remove(SpotifyAccessTokenExpiresAtKey)
                prefs.remove(SpotifyAccountNameKey)
                prefs.remove(SpotifyAccountAvatarUrlKey)
            }
            Spotify.accessToken = null
            cachedSources = null
            runCatching { clearWebAuthSession(context) }
                .onFailure(::reportException)
        }
    }

    suspend fun loadSources(): List<SpotifyImportSource> =
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            refreshProfile()

            val likedSongs = spotifyCallWithTokenRetry {
                Spotify.likedSongs(limit = 1, offset = 0).getOrThrow()
            }
            val playlists = fetchAllPlaylists()

            buildList {
                add(
                    SpotifyImportSource.LikedSongs(
                        title = context.getString(R.string.spotify_liked_songs),
                        trackCount = likedSongs.total,
                    ),
                )
                playlists.forEach { playlist ->
                    if (playlist.id.isNotBlank()) {
                        add(SpotifyImportSource.Playlist(playlist))
                    }
                }
            }.also { cachedSources = it }
        }

    suspend fun addPlaylistByUrl(url: String): SpotifyImportSource.Playlist =
        withContext(Dispatchers.IO) {
            val playlistId = parsePlaylistId(url)
                ?: throw IllegalArgumentException(context.getString(R.string.spotify_invalid_playlist_link))
            ensureAuthenticated()

            val playlist = spotifyCallWithTokenRetry {
                Spotify.playlist(playlistId).getOrThrow()
            }

            val resolved =
                if (playlist.tracks?.total != null) {
                    playlist
                } else {
                    playlistTrackCount(playlist.id)
                        ?.let { count -> playlist.copy(tracks = SpotifyPlaylistTracksRef(total = count)) }
                        ?: playlist
                }

            SpotifyImportSource.Playlist(resolved)
        }

    suspend fun importSources(
        sources: List<SpotifyImportSource>,
        onProgress: (SpotifyImportProgressUi) -> Unit,
    ): SpotifyImportSummaryUi =
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val summaries = ArrayList<SpotifyImportSourceSummaryUi>(sources.size)
            var firstFailure: Throwable? = null

            sources.forEachIndexed { sourceIndex, source ->
                onProgress(
                    SpotifyImportProgressUi(
                        sourceTitle = source.title,
                        completedSources = sourceIndex,
                        totalSources = sources.size,
                        matchedTracks = 0,
                        totalTracks = source.trackCount ?: 0,
                        percent = progressPercent(sourceIndex, sources.size, 0, source.trackCount ?: 0),
                    ),
                )

                // One playlist that still fails after the retries costs that playlist, not the
                // ones already imported or the ones after it. A lost login is the exception:
                // every later source would fail the same way.
                val summary =
                    try {
                        importSource(source, sourceIndex, sources.size, onProgress)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        if (error.isSignedOut()) throw error
                        reportException(error)
                        if (firstFailure == null) firstFailure = error
                        val expected = source.trackCount ?: 0
                        SpotifyImportSourceSummaryUi(
                            title = source.title,
                            totalTracks = expected,
                            importedTracks = 0,
                            failedTracks = expected,
                            failed = true,
                        )
                    }
                summaries += summary
                onProgress(
                    SpotifyImportProgressUi(
                        sourceTitle = source.title,
                        completedSources = sourceIndex + 1,
                        totalSources = sources.size,
                        matchedTracks = summary.importedTracks,
                        totalTracks = summary.totalTracks,
                        percent = progressPercent(sourceIndex + 1, sources.size, 0, 0),
                    ),
                )
            }

            // Nothing got through: say why, as before, rather than show a summary of zeros.
            firstFailure?.let { failure -> if (summaries.all { it.failed }) throw failure }
            SpotifyImportSummaryUi(summaries)
        }

    private suspend fun importSource(
        source: SpotifyImportSource,
        sourceIndex: Int,
        sourceCount: Int,
        onProgress: (SpotifyImportProgressUi) -> Unit,
    ): SpotifyImportSourceSummaryUi {
        val tracks = fetchAllTracks(source)
        if (tracks.isEmpty()) {
            mirrorPlaylist(source.localPlaylistId, source.title, source.thumbnailUrl, emptyList())
            return SpotifyImportSourceSummaryUi(
                title = source.title,
                totalTracks = 0,
                importedTracks = 0,
                failedTracks = 0,
            )
        }

        val matched = matchTracks(
            sourceIndex = sourceIndex,
            sourceCount = sourceCount,
            sourceTitle = source.title,
            tracks = tracks,
            onProgress = onProgress,
        )

        mirrorPlaylist(source.localPlaylistId, source.title, source.thumbnailUrl, matched.map { it.metadata })
        return SpotifyImportSourceSummaryUi(
            title = source.title,
            totalTracks = tracks.size,
            importedTracks = matched.size,
            failedTracks = tracks.size - matched.size,
        )
    }

    /**
     * Mirrors one Spotify playlist into a hidden local playlist (not bookmarked, so it stays
     * out of the Library) and returns how many songs were matched. Songs matched before come
     * from [matchCache], so a daily re-sync only searches what is new in the playlist.
     */
    suspend fun mirrorMix(
        spotifyId: String,
        title: String,
        thumbnailUrl: String?,
        localPlaylistId: String,
        maxTracks: Int,
    ): Int =
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val source = SpotifyImportSource.Playlist(
                SpotifyPlaylist(id = spotifyId, name = title, images = emptyList()),
            )
            val tracks = fetchAllTracks(source, maxTracks)
            val matched = matchTracks(
                sourceIndex = 0,
                sourceCount = 1,
                sourceTitle = title,
                tracks = tracks,
                onProgress = {},
            )
            mirrorPlaylist(
                playlistId = localPlaylistId,
                title = title,
                thumbnailUrl = thumbnailUrl,
                tracks = matched.map { it.metadata },
                bookmark = false,
            )
            matched.size
        }

    /**
     * The listener's most played Spotify tracks — the last four weeks first, then the last six
     * months — matched on YouTube and mirrored into the hidden [localPlaylistId], like a mix.
     * Returns how many were matched. Throws when Spotify refuses the request.
     */
    suspend fun mirrorTopTracks(localPlaylistId: String, title: String, maxTracks: Int): Int =
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val recent = spotifyCallWithTokenRetry { Spotify.topTracks(timeRange = "short_term", limit = 50).getOrThrow() }
            val months = spotifyCallWithTokenRetry { Spotify.topTracks(timeRange = "medium_term", limit = 50).getOrThrow() }
            val tracks = (recent.items + months.items)
                .filter { it.name.isNotBlank() && it.id.isNotBlank() }
                .distinctBy { it.id }
                .take(maxTracks)
            if (tracks.isEmpty()) return@withContext 0
            val matched = matchTracks(
                sourceIndex = 0,
                sourceCount = 1,
                sourceTitle = title,
                tracks = tracks,
                onProgress = {},
            )
            mirrorPlaylist(
                playlistId = localPlaylistId,
                title = title,
                thumbnailUrl = null,
                tracks = matched.map { it.metadata },
                bookmark = false,
            )
            matched.size
        }

    /**
     * Brings the imported Spotify Liked Songs up to date, so a song liked on Spotify reaches
     * Shiny's Home. Only for a listener who imported them: with no such playlist it returns
     * null and asks Spotify nothing. Songs matched before come from the match cache, so a
     * daily run only searches the new likes. An empty answer leaves the playlist as it was.
     */
    suspend fun refreshLikedSongs(): Int? =
        withContext(Dispatchers.IO) {
            val existing = database.getPlaylistById(LIKED_SONGS_PLAYLIST_ID) ?: return@withContext null
            ensureAuthenticated()
            val title = existing.playlist.name
            val tracks = fetchAllTracks(SpotifyImportSource.LikedSongs(title = title, trackCount = 0))
            if (tracks.isEmpty()) return@withContext existing.songCount
            val matched = matchTracks(
                sourceIndex = 0,
                sourceCount = 1,
                sourceTitle = title,
                tracks = tracks,
                onProgress = {},
            )
            if (matched.isEmpty()) return@withContext existing.songCount
            mirrorPlaylist(LIKED_SONGS_PLAYLIST_ID, title, existing.playlist.thumbnailUrl, matched.map { it.metadata })
            matched.size
        }

    /**
     * Deletes the hidden playlists [mirrorMix] and [mirrorTopTracks] made. They shape Home's
     * taste, so they leave with the Spotify account; one the listener saved to the Library stays.
     */
    suspend fun deleteMirrors() = withContext(Dispatchers.IO) {
        database.deleteHiddenSpotifyPlaylists()
    }

    /** Runs a Spotify call with a live token, refreshing it once if Spotify rejects it. */
    suspend fun <T> withSpotify(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            spotifyCallWithTokenRetry(block = block)
        }

    /** Whether a Spotify session is stored; says nothing about whether it still works. */
    suspend fun hasSession(): Boolean =
        context.dataStore.data.first()[SpotifySpDcKey].orEmpty().isNotBlank()

    private suspend fun ensureAuthenticated() {
        val prefs = context.dataStore.data.first()
        val token = prefs[SpotifyAccessTokenKey].orEmpty()
        val expiresAt = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
        if (token.isNotBlank() && expiresAt > System.currentTimeMillis() + TOKEN_EXPIRY_GRACE_MS) {
            Spotify.accessToken = token
            return
        }

        val spDc = prefs[SpotifySpDcKey].orEmpty()
        if (spDc.isBlank()) {
            throw IllegalStateException(context.getString(R.string.spotify_not_connected))
        }
        refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
    }

    private suspend fun refreshAccessToken(
        spDc: String,
        spKey: String,
    ): Result<Unit> =
        SpotifyAuth.fetchAccessToken(spDc = spDc, spKey = spKey)
            .mapCatching { token ->
                Spotify.accessToken = token.accessToken
                context.dataStore.edit { prefs ->
                    prefs[SpotifyAccessTokenKey] = token.accessToken
                    prefs[SpotifyAccessTokenExpiresAtKey] = token.accessTokenExpirationTimestampMs
                }
                refreshProfile()
            }

    private suspend fun refreshProfile() {
        Spotify.me()
            .onSuccess { user ->
                context.dataStore.edit { prefs ->
                    prefs[SpotifyAccountNameKey] = user.displayName.orEmpty()
                    user.images.firstOrNull()?.url?.let { prefs[SpotifyAccountAvatarUrlKey] = it }
                        ?: prefs.remove(SpotifyAccountAvatarUrlKey)
                }
            }
            .onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
            }
    }

    private suspend fun fetchAllPlaylists(): List<SpotifyPlaylist> {
        val playlists = ArrayList<SpotifyPlaylist>()
        var offset = 0
        val limit = 50

        while (true) {
            val page = spotifyCallWithTokenRetry {
                Spotify.myPlaylists(limit = limit, offset = offset).getOrThrow()
            }
            if (page.items.isEmpty()) break
            playlists += enrichPlaylistTrackCounts(page.items)
            offset += page.items.size
            if (offset >= page.total || page.items.size < limit) break
        }

        return playlists
    }

    private suspend fun enrichPlaylistTrackCounts(playlists: List<SpotifyPlaylist>): List<SpotifyPlaylist> =
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_SPOTIFY_COUNT_REQUESTS)
            playlists.map { playlist ->
                async {
                    if (playlist.tracks?.total != null) {
                        playlist
                    } else {
                        semaphore.withPermit {
                            playlistTrackCount(playlist.id)
                                ?.let { count -> playlist.copy(tracks = SpotifyPlaylistTracksRef(total = count)) }
                                ?: playlist
                        }
                    }
                }
            }.awaitAll()
        }

    private suspend fun playlistTrackCount(playlistId: String): Int? =
        try {
            spotifyCallWithTokenRetry {
                Spotify.playlistTracks(
                    playlistId = playlistId,
                    limit = 1,
                    offset = 0,
                ).getOrThrow()
            }.total
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            reportException(error)
            null
        }

    private suspend fun fetchAllTracks(
        source: SpotifyImportSource,
        maxTracks: Int = Int.MAX_VALUE,
    ): List<SpotifyTrack> {
        val tracks = ArrayList<SpotifyTrack>()
        var offset = 0
        val limit = minOf(100, maxTracks)

        while (true) {
            val page =
                when (source) {
                    is SpotifyImportSource.LikedSongs -> {
                        val paging = spotifyCallWithTokenRetry(patient = true) {
                            Spotify.likedSongs(limit = limit, offset = offset).getOrThrow()
                        }
                        SpotifyTrackPage(
                            items = paging.items.map { it.track },
                            total = paging.total,
                        )
                    }
                    is SpotifyImportSource.Playlist -> {
                        val paging = spotifyCallWithTokenRetry(patient = true) {
                            Spotify.playlistTracks(
                                playlistId = source.spotifyId,
                                limit = limit,
                                offset = offset,
                            ).getOrThrow()
                        }
                        SpotifyTrackPage(
                            items = paging.items.mapNotNull { it.track },
                            total = paging.total,
                        )
                    }
                }

            if (page.items.isEmpty()) break
            tracks += page.items.filter { it.name.isNotBlank() }
            offset += page.items.size
            if (offset >= page.total || page.items.size < limit || tracks.size >= maxTracks) break
        }

        return tracks.take(maxTracks)
    }

    /**
     * Extracts a Spotify playlist id from any of the forms a user might paste:
     *  - a share URL: https://open.spotify.com/playlist/{id}?si=...
     *  - a localized URL: https://open.spotify.com/intl-de/playlist/{id}
     *  - a URI: spotify:playlist:{id}
     *  - a bare base62 id
     * Returns null when the input is not a recognizable playlist reference.
     */
    private fun parsePlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        PLAYLIST_REFERENCE_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        if (trimmed.matches(BARE_ID_REGEX)) return trimmed
        return null
    }

    /**
     * Runs a Spotify call, refreshing the token once if Spotify rejects it and sending it
     * again after a network failure (see [isTransientNetworkFailure]).
     *
     * [patient] is for imports and syncs, which nobody is watching a spinner for: before each
     * retry it waits for the network to come back. Without it, a page load retries a blip
     * once and otherwise fails at once, as it always did.
     */
    private suspend fun <T> spotifyCallWithTokenRetry(
        patient: Boolean = false,
        block: suspend () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return callWithTokenRefresh(block)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!error.isTransientNetworkFailure()) throw error
                attempt++
                val attempts = if (patient) PATIENT_ATTEMPTS else QUICK_ATTEMPTS
                if (attempt >= attempts || !awaitRetry(attempt, patient)) throw error
            }
        }
    }

    private suspend fun <T> callWithTokenRefresh(block: suspend () -> T): T =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if ((error as? Spotify.SpotifyException)?.statusCode != 401) {
                throw error
            }
            val prefs = context.dataStore.data.first()
            val spDc = prefs[SpotifySpDcKey].orEmpty()
            if (spDc.isBlank()) {
                throw error
            }
            refreshAccessToken(spDc = spDc, spKey = prefs[SpotifySpKeyKey].orEmpty()).getOrThrow()
            block()
        }

    /**
     * Waits before retry [attempt]: until the network is usable again when [patient], then a
     * growing pause so a recovering network is not hit at once by every waiting request.
     * Returns false when the network stayed unusable for [NETWORK_WAIT_MS].
     */
    private suspend fun awaitRetry(attempt: Int, patient: Boolean): Boolean {
        if (patient && !awaitUsableNetwork()) return false
        delay((RETRY_BASE_DELAY_MS shl (attempt - 1).coerceAtMost(4)).coerceAtMost(RETRY_MAX_DELAY_MS))
        return true
    }

    private suspend fun awaitUsableNetwork(): Boolean = coroutineScope {
        // Only a wait that lasts is worth telling the listener about; most end at once.
        val notice = launch {
            delay(WAITING_NOTICE_DELAY_MS)
            countNetworkWaiter(+1)
            try {
                awaitCancellation()
            } finally {
                countNetworkWaiter(-1)
            }
        }
        try {
            withTimeoutOrNull(NETWORK_WAIT_MS) { context.usableNetworkFlow().first { it } } != null
        } finally {
            notice.cancel()
        }
    }

    private fun countNetworkWaiter(delta: Int) {
        synchronized(_waitingForNetwork) {
            networkWaiters += delta
            _waitingForNetwork.value = networkWaiters > 0
        }
    }

    /** Not connected, or Spotify refused the login even after a token refresh. */
    private fun Throwable.isSignedOut(): Boolean =
        this is IllegalStateException ||
            (this as? Spotify.SpotifyException)?.statusCode.let { it == 401 || it == 403 }

    private suspend fun matchTracks(
        sourceIndex: Int,
        sourceCount: Int,
        sourceTitle: String,
        tracks: List<SpotifyTrack>,
        onProgress: (SpotifyImportProgressUi) -> Unit,
    ): List<MatchedTrack> =
        coroutineScope {
            val semaphore = Semaphore(MAX_CONCURRENT_MATCHES)
            val completed = AtomicInteger(0)

            tracks.mapIndexed { index, track ->
                async {
                    semaphore.withPermit {
                        val matched =
                            try {
                                matchTrack(track, index)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                reportException(error)
                                null
                            }
                        val completedCount = completed.incrementAndGet()
                        onProgress(
                            SpotifyImportProgressUi(
                                sourceTitle = sourceTitle,
                                completedSources = sourceIndex,
                                totalSources = sourceCount,
                                matchedTracks = completedCount,
                                totalTracks = tracks.size,
                                percent = progressPercent(sourceIndex, sourceCount, completedCount, tracks.size),
                            ),
                        )
                        matched
                    }
                }
            }.awaitAll()
                .filterNotNull()
                .sortedBy { it.index }
                .also { matchCache.save() }
        }

    private suspend fun matchTrack(
        track: SpotifyTrack,
        index: Int,
    ): MatchedTrack? {
        matchCache.get(track.id)?.let { videoId ->
            database.getSongById(videoId)?.let { song ->
                return MatchedTrack(index = index, metadata = song.toMediaMetadata())
            }
        }
        val searchResult = searchSongs(SpotifyMapper.buildSearchQuery(track)) ?: return null
        val candidates = searchResult.items
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }

        val best = mapperMutex.withLock {
            candidates.maxByOrNull { candidate ->
                SpotifyMapper.matchScore(
                    spotifyTitle = track.name,
                    spotifyArtist = track.artists.joinToString(" ") { it.name },
                    spotifyDurationMs = track.durationMs,
                    candidateTitle = candidate.title,
                    candidateArtist = candidate.artists.joinToString(" ") { it.name },
                    candidateDurationSec = candidate.duration,
                )
            }
        } ?: return null

        matchCache.put(track.id, best.id)
        return MatchedTrack(index = index, metadata = best.toMediaMetadata())
    }

    /**
     * A YouTube song search that outlasts the network dropping out. A failed search used to
     * count the song as unmatched, so an import that lost the network part-way quietly
     * mirrored playlists with most of their songs missing.
     */
    private suspend fun searchSongs(query: String): SearchResult? {
        var attempt = 0
        while (true) {
            val result = YouTube.search(query = query, filter = YouTube.SearchFilter.FILTER_SONG)
            result.onSuccess { return it }
            val error = result.exceptionOrNull() ?: return null
            if (error is CancellationException) throw error
            if (!error.isTransientNetworkFailure()) return null
            attempt++
            if (attempt >= PATIENT_ATTEMPTS || !awaitRetry(attempt, patient = true)) return null
        }
    }

    private suspend fun mirrorPlaylist(
        playlistId: String,
        title: String,
        thumbnailUrl: String?,
        tracks: List<MediaMetadata>,
        bookmark: Boolean = true,
    ) {
        database.withTransaction {
            val existing = getPlaylistById(playlistId)
            val now = LocalDateTime.now()
            val bookmarkedAt = if (bookmark) existing?.playlist?.bookmarkedAt ?: now else existing?.playlist?.bookmarkedAt
            val entity =
                existing?.playlist?.copy(
                    name = title,
                    bookmarkedAt = bookmarkedAt,
                    lastUpdateTime = now,
                    thumbnailUrl = thumbnailUrl,
                    isEditable = true,
                ) ?: PlaylistEntity(
                    id = playlistId,
                    name = title,
                    bookmarkedAt = bookmarkedAt,
                    lastUpdateTime = now,
                    thumbnailUrl = thumbnailUrl,
                    isEditable = true,
                )

            if (existing == null) {
                insert(entity)
            } else {
                update(entity)
            }

            tracks.forEach { metadata ->
                insert(metadata)
            }

            clearPlaylist(playlistId)
            tracks.forEachIndexed { index, metadata ->
                insert(
                    PlaylistSongMap(
                        playlistId = playlistId,
                        songId = metadata.id,
                        position = index,
                        setVideoId = metadata.setVideoId,
                    ),
                )
            }
            update(entity.copy(lastUpdateTime = now))
        }
    }

    private fun progressPercent(
        completedSources: Int,
        totalSources: Int,
        completedTracks: Int,
        totalTracks: Int,
    ): Int {
        if (totalSources <= 0) return 0
        val sourceProgress =
            if (totalTracks <= 0) {
                0f
            } else {
                completedTracks.toFloat() / totalTracks.toFloat()
            }
        return (((completedSources + sourceProgress) / totalSources.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private data class SpotifyTrackPage(
        val items: List<SpotifyTrack>,
        val total: Int,
    )

    private data class MatchedTrack(
        val index: Int,
        val metadata: MediaMetadata,
    )

    companion object {
        /** The library playlist an import of Spotify's Liked Songs fills. */
        const val LIKED_SONGS_PLAYLIST_ID = "SPOTIFY_LIKED_SONGS"

        private const val MAX_CONCURRENT_MATCHES = 4
        private const val MAX_CONCURRENT_SPOTIFY_COUNT_REQUESTS = 4
        private const val TOKEN_EXPIRY_GRACE_MS = 60_000L

        /** Tries per call for a page load: one retry covers a blip. */
        private const val QUICK_ATTEMPTS = 2

        /** Tries per call for an import, each once the network is usable again. */
        private const val PATIENT_ATTEMPTS = 6

        /** How long one retry waits for a usable network before the call gives up. */
        private const val NETWORK_WAIT_MS = 5 * 60_000L
        private const val RETRY_BASE_DELAY_MS = 1_000L
        private const val RETRY_MAX_DELAY_MS = 15_000L
        private const val WAITING_NOTICE_DELAY_MS = 1_500L
        private val PLAYLIST_REFERENCE_REGEX = Regex("""playlist[/:]([A-Za-z0-9]+)""")
        private val BARE_ID_REGEX = Regex("""[A-Za-z0-9]{16,}""")
    }
}

data class SpotifyImportSession(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
)

sealed interface SpotifyImportSource {
    val id: String
    val title: String
    val subtitle: String
    val thumbnailUrl: String?
    val trackCount: Int?
    val localPlaylistId: String
    val type: SpotifyImportSourceType

    data class Playlist(
        val playlist: SpotifyPlaylist,
    ) : SpotifyImportSource {
        val spotifyId: String = playlist.id
        override val id: String = "playlist:${playlist.id}"
        override val title: String = playlist.name
        override val subtitle: String = playlist.owner?.displayName.orEmpty()
        override val thumbnailUrl: String? = SpotifyMapper.getPlaylistThumbnail(playlist)
        override val trackCount: Int? = playlist.tracks?.total
        override val localPlaylistId: String = "SPOTIFY_PLAYLIST_${playlist.id}"
        override val type: SpotifyImportSourceType = SpotifyImportSourceType.PLAYLIST
    }

    data class LikedSongs(
        override val title: String,
        override val trackCount: Int,
    ) : SpotifyImportSource {
        override val id: String = "liked_songs"
        override val subtitle: String = ""
        override val thumbnailUrl: String? = null
        override val localPlaylistId: String = SpotifyImportRepository.LIKED_SONGS_PLAYLIST_ID
        override val type: SpotifyImportSourceType = SpotifyImportSourceType.LIKED_SONGS
    }
}
