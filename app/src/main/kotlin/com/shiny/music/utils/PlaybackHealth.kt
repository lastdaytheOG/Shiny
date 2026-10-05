package com.shiny.music.utils

import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException

/**
 * Thrown when the resolver runs its whole client cascade and still has no stream.
 *
 * It is an [IOException] so the player reports it like any other load failure, and it carries
 * what the cascade saw, because Media3 wraps it into a generic `IO_UNSPECIFIED` and the message
 * alone never says which clients were tried.
 *
 * [permanent] means YouTube itself said the video can't be played (removed, region-locked,
 * sign-in required). Retrying that does another full cascade and fails the same way, so the
 * player skips it straight away instead of retrying three times.
 */
class StreamResolveException(
    val why: String,
    val permanent: Boolean,
    val status: String? = null,
    val reason: String? = null,
    val cascade: String = "",
) : IOException(reason ?: why)

/**
 * What happened to playback, in one place: every stream resolution, every recovery the service
 * attempts and every song it gives up on.
 *
 * Nothing here knows about Firebase. The Play build sets [sink] to forward events to Analytics
 * and Crashlytics (see `Telemetry`); the FOSS build leaves it null. No video ids or titles are
 * forwarded: an event says *how* playback failed, never *what* was playing.
 */
object PlaybackHealth {

    sealed interface Event {
        /** A stream URL was obtained. [source] is `play`, `prefetch` or `download`. */
        data class Resolved(val source: String, val client: String, val ms: Long) : Event

        /** The resolver ran out of clients. */
        data class ResolveFailed(
            val source: String,
            val why: String,
            val permanent: Boolean,
            val status: String?,
            val cascade: String,
            val ms: Long,
        ) : Event

        /** The player hit an error and the service is trying [action] (attempt [attempt]). */
        data class Recovering(val errorCode: String, val action: String, val attempt: Int) : Event

        /** The service gave up on a song. [outcome] is `skipped` or `stopped`. */
        data class GaveUp(val errorCode: String, val why: String, val outcome: String) : Event
    }

    /**
     * A message for the listener, shown by the activity if it is on screen.
     *
     * [needsAudioAccess]: the song is a file on the phone that Shiny is not allowed to read.
     * Playback has stopped on it, and the activity asks for the permission instead.
     */
    data class Notice(
        val title: String?,
        val skipped: Boolean,
        val unavailable: Boolean,
        val needsAudioAccess: Boolean = false,
    )

    @Volatile
    var sink: ((Event) -> Unit)? = null

    private val _notices = MutableSharedFlow<Notice>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notices: SharedFlow<Notice> = _notices.asSharedFlow()

    // Counters for this process, for the debug log and anyone checking health by hand.
    @Volatile var resolved = 0; private set
    @Volatile var resolveFailures = 0; private set
    @Volatile var gaveUp = 0; private set

    fun now(): Long = SystemClock.elapsedRealtime()

    fun record(event: Event) {
        when (event) {
            is Event.Resolved -> resolved++
            is Event.ResolveFailed -> resolveFailures++
            is Event.GaveUp -> gaveUp++
            is Event.Recovering -> Unit
        }
        timber.log.Timber.tag("PlaybackHealth").i("%s (ok=%d fail=%d gaveUp=%d)", event, resolved, resolveFailures, gaveUp)
        try {
            sink?.invoke(event)
        } catch (e: Exception) {
            // Telemetry must never break playback.
            timber.log.Timber.tag("PlaybackHealth").w(e, "sink failed")
        }
    }

    fun notify(notice: Notice) {
        _notices.tryEmit(notice)
    }

    /** The [StreamResolveException] somewhere in [t]'s cause chain, if there is one. */
    fun resolveFailureIn(t: Throwable?): StreamResolveException? {
        var cause = t
        var depth = 0
        while (cause != null && depth++ < 12) {
            if (cause is StreamResolveException) return cause
            cause = cause.cause
        }
        return null
    }
}
