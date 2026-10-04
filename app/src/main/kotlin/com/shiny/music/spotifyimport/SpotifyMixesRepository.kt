package com.shiny.music.spotifyimport

import android.content.Context
import com.shiny.music.constants.SpotifyHomeMixesKey
import com.shiny.music.home.HomeSpotifyMix
import com.shiny.music.spotify.Spotify
import com.shiny.music.spotify.models.SpotifyHomeFeed
import com.shiny.music.spotify.models.SpotifyHomeFeedItem
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SpotifyMixes(
    val version: Int = VERSION,
    /** When Spotify's Home was last read, in System.currentTimeMillis(). */
    val fetchedAt: Long = 0,
    val mixes: List<HomeSpotifyMix> = emptyList(),
    /** When the listener's top tracks were last mirrored, or last refused by Spotify. */
    val topSyncedAt: Long = 0,
    /** How many of them were matched on YouTube. */
    val topSongCount: Int = 0,
    /** When the imported Liked Songs were last brought up to date with Spotify. */
    val likedSyncedAt: Long = 0,
) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * The listener's own Spotify mixes — Daily Mixes, Discover Weekly, Release Radar, daylist —
 * read from their Spotify Home and kept playable in Shiny.
 *
 * Each mix is mirrored into a hidden local playlist (`SPOTIFY_MIX_<id>`) whose songs were
 * matched on YouTube, so opening one from Home is instant and needs no Spotify call. Spotify
 * rewrites these mixes daily or weekly; they are re-read and re-matched on the same rhythm,
 * and [SpotifyMatchCache] makes a re-match cost only the songs that are new.
 *
 * Alongside the mixes, the listener's most played Spotify tracks are mirrored into the hidden
 * [TOP_PLAYLIST_ID]. They never show on Home; they tell Home's taste what this listener plays
 * most, so recommendations are personal from the first launch (see `HomeRanking.savedSeedWeights`).
 *
 * Kept on disk as one block, like `HomeRemoteRepository`. Everything here uses Spotify's
 * private web API, which can change without notice: a failure keeps the last good block.
 */
@Singleton
class SpotifyMixesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spotifyImport: SpotifyImportRepository,
) {
    companion object {
        private const val FILE = "spotify_mixes.json"
        const val FEED_TTL_MS = 6L * 60 * 60 * 1000
        const val MIX_TTL_MS = 20L * 60 * 60 * 1000

        /** Even a pull to refresh does not re-read Spotify's Home sooner than this. */
        const val MIN_FORCED_INTERVAL_MS = 10L * 60 * 1000
        const val MAX_MIXES = 8
        const val MAX_TRACKS_PER_MIX = 50

        /** Top tracks shift slowly; once a day is plenty, and a refusal waits as long. */
        const val TOP_TTL_MS = 24L * 60 * 60 * 1000
        const val MAX_TOP_TRACKS = 60
        const val TOP_PLAYLIST_ID = "SPOTIFY_TOP_TRACKS"

        /** Liked Songs change a few times a day at most; a daily re-sync keeps Home current. */
        const val LIKED_TTL_MS = 24L * 60 * 60 * 1000

        fun localPlaylistId(spotifyId: String) = "SPOTIFY_MIX_$spotifyId"

        /**
         * The playlists on a Spotify Home that belong to this listener: the ones Spotify made
         * for them (marked `madeFor`). When a response carries no such marks, Spotify's own
         * playlists stand in for them, in the order Spotify ranked its sections.
         */
        fun pickMixes(feed: SpotifyHomeFeed, limit: Int = MAX_MIXES): List<SpotifyHomeFeedItem.Playlist> {
            val playlists = feed.sections.flatMap { it.items }
                .filterIsInstance<SpotifyHomeFeedItem.Playlist>()
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                .distinctBy { it.id }
            val madeFor = playlists.filter { !it.madeForUsername.isNullOrBlank() }
            val chosen = madeFor.ifEmpty { playlists.filter { it.ownerName.equals("Spotify", ignoreCase = true) } }
            return chosen.take(limit)
        }

        /**
         * The new list of mixes: what Spotify shows now, in its order, each keeping the sync
         * state it had when it was already known.
         */
        fun merge(previous: List<HomeSpotifyMix>, picked: List<SpotifyHomeFeedItem.Playlist>): List<HomeSpotifyMix> {
            val known = previous.associateBy { it.spotifyId }
            return picked.map { p ->
                val old = known[p.id]
                HomeSpotifyMix(
                    spotifyId = p.id,
                    name = p.name,
                    description = p.description?.let(::plainText)?.takeIf { it.isNotBlank() },
                    imageUrl = p.imageUrl ?: old?.imageUrl,
                    localPlaylistId = localPlaylistId(p.id),
                    songCount = old?.songCount ?: 0,
                    syncedAt = old?.syncedAt ?: 0,
                )
            }
        }

        /** Spotify descriptions carry HTML links and entities ("Made for <a …>you</a>"). */
        fun plainText(html: String): String =
            html.replace(Regex("<[^>]*>"), "")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
    }

    private val file = File(context.filesDir, FILE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val saveLock = Any()
    private val _state = MutableStateFlow<SpotifyMixes?>(null)
    val state: StateFlow<SpotifyMixes?> = _state.asStateFlow()

    suspend fun restore() = withContext(Dispatchers.IO) {
        if (_state.value != null || !file.exists()) return@withContext
        runCatching { json.decodeFromString(SpotifyMixes.serializer(), file.readText()) }
            .onFailure { Timber.tag("SpotifyMixes").w(it, "unreadable, ignored") }
            .getOrNull()
            ?.takeIf { it.version == SpotifyMixes.VERSION }
            ?.let { restored -> if (_state.value == null) _state.value = restored }
    }

    /**
     * Re-reads Spotify's Home when it is stale and re-matches each mix that is. Mixes appear
     * on Home one by one as their songs are matched. Returns at once when a refresh is
     * already running, so Home opening twice never starts two.
     */
    suspend fun refresh(force: Boolean) = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            if (!spotifyImport.hasSession()) {
                if (_state.value != null) clear()
                return@withContext
            }
            val now = System.currentTimeMillis()
            // Side by side: the top tracks only shape Home's taste, and waiting for them held the
            // "From Spotify" row back, while waiting for every mix would hold the taste back.
            coroutineScope {
                launch { syncTop(now) }
                launch { syncLiked(now) }
                syncMixes(force, now)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun syncMixes(force: Boolean, now: Long) {
        try {
            val enabled = context.dataStore.data.first()[SpotifyHomeMixesKey] ?: true
            if (!enabled) {
                val current = _state.value
                if (current != null && (current.mixes.isNotEmpty() || current.fetchedAt != 0L)) {
                    publish { it.copy(fetchedAt = 0, mixes = emptyList()) }
                }
                return
            }

            var mixes = _state.value?.mixes.orEmpty()
            val feedTtl = if (force) MIN_FORCED_INTERVAL_MS else FEED_TTL_MS
            if (now - (_state.value?.fetchedAt ?: 0L) >= feedTtl) {
                val feed = spotifyImport.withSpotify { Spotify.home().getOrThrow() }
                val picked = pickMixes(feed)
                Timber.tag("SpotifyMixes").d("home: ${feed.sections.size} sections, ${picked.size} mixes")
                mixes = publish { it.copy(fetchedAt = now, mixes = merge(it.mixes, picked)) }.mixes
            }

            for (mix in mixes) {
                if (now - mix.syncedAt < MIX_TTL_MS) continue
                val count = try {
                    spotifyImport.mirrorMix(
                        spotifyId = mix.spotifyId,
                        title = mix.name,
                        thumbnailUrl = mix.imageUrl,
                        localPlaylistId = mix.localPlaylistId,
                        maxTracks = MAX_TRACKS_PER_MIX,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One mix failing (a playlist Spotify won't serve) must not stop the rest.
                    reportException(e)
                    continue
                }
                publish { state ->
                    state.copy(
                        mixes = state.mixes.map {
                            if (it.spotifyId == mix.spotifyId) it.copy(songCount = count, syncedAt = System.currentTimeMillis()) else it
                        },
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Spotify's private API changed or the session expired: keep the last good mixes.
            reportException(e)
        }
    }

    /**
     * Mirrors the listener's top tracks once a day. On its own guard: Spotify has restricted
     * this endpoint before, and a refusal must never cost the mixes. A refusal waits a day too;
     * no connection is not a refusal, and is tried again on the next refresh.
     */
    private suspend fun syncTop(now: Long) {
        if (now - (_state.value?.topSyncedAt ?: 0L) < TOP_TTL_MS) return
        val count = try {
            spotifyImport.mirrorTopTracks(TOP_PLAYLIST_ID, "Spotify top tracks", MAX_TOP_TRACKS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOffline(e)) {
                Timber.tag("SpotifyMixes").d("top tracks: offline, tried again next refresh")
                return
            }
            Timber.tag("SpotifyMixes").w(e, "top tracks unavailable")
            reportException(e)
            null
        }
        Timber.tag("SpotifyMixes").d("top tracks: ${count ?: "refused"}")
        publish { it.copy(topSyncedAt = now, topSongCount = count ?: it.topSongCount) }
    }

    /**
     * Keeps the imported Liked Songs, which lead Home's Spotify row, in step with Spotify once
     * a day. Nothing happens for a listener who never imported them. Offline is tried again
     * on the next refresh; any other failure waits the day, like the top tracks.
     */
    private suspend fun syncLiked(now: Long) {
        if (now - (_state.value?.likedSyncedAt ?: 0L) < LIKED_TTL_MS) return
        val count = try {
            spotifyImport.refreshLikedSongs()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isOffline(e)) return
            reportException(e)
            null
        }
        Timber.tag("SpotifyMixes").d("liked songs: ${count ?: "not imported or refused"}")
        publish { it.copy(likedSyncedAt = now) }
    }

    /** The request never got an answer: an I/O failure anywhere in the chain. */
    private fun isOffline(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(8).any { it is IOException }

    /**
     * Forgets the mixes and the top tracks, for a Spotify logout: the sync state and the hidden
     * playlists, which would otherwise keep shaping Home's picks with no way to remove them.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(saveLock) {
            _state.value = null
            file.delete()
        }
        spotifyImport.deleteMirrors()
    }

    /**
     * Applies [change] to the current block, then saves it. The mixes and the top tracks sync
     * side by side, so each change is made to the latest block, one at a time.
     */
    private fun publish(change: (SpotifyMixes) -> SpotifyMixes): SpotifyMixes = synchronized(saveLock) {
        val mixes = change(_state.value ?: SpotifyMixes())
        _state.value = mixes
        runCatching {
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(json.encodeToString(SpotifyMixes.serializer(), mixes))
            tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
        }.onFailure { Timber.tag("SpotifyMixes").w(it, "save failed") }
        mixes
    }
}
