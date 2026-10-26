

package com.shiny.music.lyrics

import android.content.Context
import android.util.LruCache
import com.shiny.music.constants.LyricsProviderOrderKey
import com.shiny.music.constants.PreferredLyricsProvider
import com.shiny.music.constants.PreferredLyricsProviderKey
import com.shiny.music.constants.FetchFasterLyricsKey
import com.shiny.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.shiny.music.extensions.toEnum
import com.shiny.music.models.MediaMetadata
import com.shiny.music.playback.LyricsWithProvider
import com.shiny.music.utils.NetworkConnectivityObserver
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    
    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        
        val preferredEnum = preferences[PreferredLyricsProviderKey]
            .toEnum(PreferredLyricsProvider.YOULYPLUS)
        val preferredName = LyricsProviderRegistry.getProviderNameForEnum(preferredEnum)
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredName) + defaultOrder.filter { it != preferredName }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }



    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        // `isEnabled` reads DataStore with `runBlocking` behind a non-suspend signature.
        // This runs on the service's main-thread scope when a track starts, so filtering
        // here parked the main thread on one blocking read per provider — nine of them —
        // at the exact moment the new track's artwork and title were being drawn.
        val providers = withContext(Dispatchers.IO) {
            resolveLyricsProviders().filter { it.isEnabled(context) }
        }
        if (providers.isEmpty()) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

        // Providers all run in parallel either way; the difference is what we wait for.
        // The old default awaited them in preference order, so a slow first provider held
        // up a synced result another had already returned. Racing is the default now, with
        // one rule on top: a synced file timed by the streaming services wins at once, and
        // a hand-timed one waits a moment for them (see [LyricsPick] for why and how long).
        // Preference order still decides between results of the same kind, and a user who
        // wants strict preference order can turn racing off.
        val fetchFaster = context.dataStore.data.first()[FetchFasterLyricsKey] ?: true

        return coroutineScope {
            if (fetchFaster) {
                val channel = Channel<Answer>(providers.size)
                providers.forEach { provider ->
                    launch {
                        val lyrics = try {
                            provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            ).onFailure(::reportException).getOrNull()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            reportException(e)
                            null
                        }
                        channel.send(Answer(provider.name, lyrics))
                    }
                }

                // Every provider answers exactly once, and the last answer always settles
                // it, so this ends; a held hand-timed result ends it sooner at the deadline.
                val pick = LyricsPick(providers.map { it.name }, mediaMetadata.duration)
                val startedAt = System.nanoTime()
                var chosen: LyricsWithProvider? = null
                while (chosen == null) {
                    val answer = if (pick.holding) {
                        val left = LyricsPick.CommunityGraceMs - (System.nanoTime() - startedAt) / 1_000_000L
                        if (left > 0) withTimeoutOrNull(left) { channel.receive() } else null
                    } else {
                        channel.receive()
                    }
                    chosen = if (answer == null) pick.timeUp() else pick.answer(answer.provider, answer.lyrics)
                }
                coroutineContext.cancelChildren()
                chosen
            } else {
                val deferreds = providers.associateWith { provider ->
                    async {
                        try {
                            provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            ).getOrNull()
                        } catch (e: Exception) {
                            reportException(e)
                            null
                        }
                    }
                }
                
                // Strict preference order: the first provider in the list with a synced file
                // that fits wins, whoever it is. The same checks as the race decide what
                // counts as synced (see [LyricsPick]).
                var bestUnsynced: LyricsWithProvider? = null
                var illFitting: LyricsWithProvider? = null
                for (provider in providers) {
                    val result = deferreds[provider]?.await()
                    if (result != null && result != LYRICS_NOT_FOUND && result.isNotBlank()) {
                        val synced = LyricsPick.looksSynced(result)
                        if (synced && LyricsPick.hasRealTimings(result)) {
                            if (LyricsPick.fitsRecording(result, mediaMetadata.duration)) {
                                coroutineContext.cancelChildren()
                                return@coroutineScope LyricsWithProvider(result, provider.name)
                            }
                            if (illFitting == null) illFitting = LyricsWithProvider(result, provider.name)
                        } else if (bestUnsynced == null) {
                            val text = if (synced) LyricsPick.withoutTimestamps(result) else result
                            bestUnsynced = LyricsWithProvider(text, provider.name)
                        }
                    }
                }
                return@coroutineScope illFitting
                    ?: bestUnsynced
                    ?: LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
            }
        }
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }
        
        if (!isNetworkAvailable) {
            return
        }

        val allResult = java.util.concurrent.CopyOnWriteArrayList<LyricsResult>()
        val providers = resolveLyricsProviders()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val jobs = providers.mapNotNull { provider ->
                if (provider.isEnabled(context)) {
                    launch {
                        try {
                            provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                                val result = LyricsResult(provider.name, lyrics)
                                allResult += result
                                callback(result)
                            }
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }
                } else null
            }
            jobs.forEach { it.join() }
            cache.put(cacheKey, allResult.toList())
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    /** One provider's reply to the race: its lyrics, or null for nothing. */
    private class Answer(val provider: String, val lyrics: String?)

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

