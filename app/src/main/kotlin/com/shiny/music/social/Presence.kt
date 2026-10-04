package com.shiny.music.social

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** What a friend is doing right now, as the Friends list shows it. */
enum class ListeningState { Playing, Paused, NotListening }

/**
 * Whether a friend's song is still live.
 *
 * The server keeps a song after the phone stops reporting it (12 minutes playing, 30
 * paused), and until now anything it returned read as "listening". A phone that is
 * playing reports at least every [SocialPresencePublisher.HEARTBEAT_MS], so a playing
 * song not heard of for [PLAYING_FRESH_MS] means the app stopped without saying so —
 * killed, out of battery, offline. A pause is shown as a pause, and only for a while.
 */
object PresenceFreshness {
    /** Three missed heartbeats. */
    const val PLAYING_FRESH_MS = 3 * 60_000L

    /** After this long a paused song is just the last thing they played. */
    const val PAUSED_FRESH_MS = 10 * 60_000L

    /** [serverNowMs] is the server's clock, since [NowPlaying.updatedAt] is on it. */
    fun state(song: NowPlaying?, serverNowMs: Long): ListeningState {
        if (song == null) return ListeningState.NotListening
        // No timestamp: trust the server's own expiry.
        if (song.updatedAt <= 0L) return if (song.isPlaying) ListeningState.Playing else ListeningState.Paused
        val age = serverNowMs - song.updatedAt
        return when {
            song.isPlaying && age <= PLAYING_FRESH_MS -> ListeningState.Playing
            !song.isPlaying && age <= PAUSED_FRESH_MS -> ListeningState.Paused
            else -> ListeningState.NotListening
        }
    }
}

/**
 * Takes this phone's song off the server once playback has ended for good (the service is
 * being destroyed). A coroutine started in `onDestroy` usually dies with the process before
 * its request is sent, which left friends seeing a song for up to 12 minutes after Shiny
 * closed; WorkManager sends it even after the process is gone, as soon as there is network.
 */
class ClearPresenceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun socialRepository(): SocialRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).socialRepository()
        // Signed out meanwhile: the server dropped the song with the session.
        val token = repository.currentToken() ?: return Result.success()
        return runCatching { repository.api.clearPresence(token) }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                when {
                    error is SocialApiException && error.status == 401 -> Result.success()
                    runAttemptCount < 3 -> Result.retry()
                    // The server's own expiry covers it from here.
                    else -> Result.success()
                }
            },
        )
    }

    companion object {
        private const val WORK = "social-clear-presence"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ClearPresenceWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }

        /** Playback started again before the clear went out: it must not wipe the new song. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
