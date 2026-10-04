package com.shiny.music.social

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What the player is doing, sampled on the main thread. */
data class PlaybackSnapshot(
    val trackId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val thumbnail: String?,
    val durationMs: Long?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val roomCode: String?,
)

/**
 * Tells the social server what this device is playing, so friends and the public profile can see it.
 *
 * Changes are sent a moment after they settle (skipping through songs sends one update). While a song
 * plays, a heartbeat every minute tells friends it is still live (see [PresenceFreshness]); a pause is
 * sent as a pause. Nothing is sent without an account, while sharing is off, or in a private session:
 * then the last song shared is cleared. When the service ends the song is cleared through
 * [ClearPresenceWorker], which survives the process going away.
 */
@Singleton
class SocialPresencePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SocialRepository,
) {
    private var scope: CoroutineScope? = null
    private var sample: (() -> PlaybackSnapshot?)? = null
    private var sendJob: Job? = null
    private var heartbeatJob: Job? = null

    /** True while the server may still be showing a song from this device. */
    private var shared = false

    /** Starts publishing for a player; [sample] runs on the main thread. */
    fun attach(sample: () -> PlaybackSnapshot?) {
        detach(clear = false)
        ClearPresenceWorker.cancel(context)
        this.sample = sample
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        // Signing in or out, the share mode and a private session all change what may be shared.
        newScope.launch {
            combine(repository.token, repository.user, repository.privateSession) { token, user, private ->
                Triple(token != null, user?.shareMode, private)
            }.distinctUntilChanged().collect { notifyChanged() }
        }
    }

    /** Call when the song, play state or position jumps. */
    fun notifyChanged() {
        val scope = scope ?: return
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(DEBOUNCE_MS)
            publish()
        }
    }

    /** Stops publishing; with [clear], also removes the song from the server (the service is ending). */
    fun detach(clear: Boolean = true) {
        scope?.cancel()
        scope = null
        sample = null
        sendJob = null
        heartbeatJob = null
        if (clear && shared) {
            shared = false
            ClearPresenceWorker.enqueue(context)
        }
    }

    private suspend fun publish() {
        heartbeatJob?.cancel()
        val snapshot = sample?.invoke()?.takeIf { YOUTUBE_VIDEO_ID.matches(it.trackId) }
        val token = repository.currentToken()
        val allowed =
            token != null &&
                !repository.privateSession.value &&
                ShareMode.fromWire(repository.user.value?.shareMode) != ShareMode.Off

        if (token == null) {
            shared = false
            return
        }
        if (!allowed || snapshot == null) {
            if (shared) {
                send(token) { repository.api.clearPresence(token) }.onSuccess { shared = false }
            }
            return
        }

        send(token) {
            repository.api.putPresence(
                token,
                PresenceUpdate(
                    trackId = snapshot.trackId,
                    title = snapshot.title,
                    artist = snapshot.artist,
                    album = snapshot.album,
                    thumbnail = snapshot.thumbnail,
                    durationMs = snapshot.durationMs?.takeIf { it > 0L },
                    positionMs = snapshot.positionMs.coerceAtLeast(0L),
                    isPlaying = snapshot.isPlaying,
                    roomCode = snapshot.roomCode,
                ),
            )
        }.onSuccess { shared = true }

        if (snapshot.isPlaying) {
            heartbeatJob = scope?.launch {
                delay(HEARTBEAT_MS)
                publish()
            }
        }
    }

    private suspend fun send(token: String, request: suspend () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { request() }.onFailure { error ->
                if (error is SocialApiException && error.status == 401) repository.onSessionRejected()
                Timber.tag(TAG).d(error, "presence not sent")
            }
        }

    companion object {
        private const val TAG = "SocialPresence"
        private const val DEBOUNCE_MS = 1_500L

        /**
         * How often a playing song is re-sent. Friends treat one not heard of for three of these as
         * over ([PresenceFreshness.PLAYING_FRESH_MS]); it used to be four minutes, which is why a
         * friend who had closed Shiny still showed as listening for up to twelve.
         */
        const val HEARTBEAT_MS = 60_000L
        private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    }
}
