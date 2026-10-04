package com.shiny.music.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.shiny.music.utils.PlaybackHealth
import com.shiny.music.utils.PlaybackHealth.Event
import com.shiny.music.utils.exceptionReporter
import timber.log.Timber

/**
 * Sends handled exceptions and playback health to Firebase (project `shinymusic-1`).
 *
 * Until this existed, `reportException` had no reporter: every caught playback failure went to
 * a log that release builds don't keep, so a YouTube change that broke playback was invisible
 * until people complained.
 *
 * Events carry how playback failed, never what was playing: no video ids, titles or URLs.
 */
object Telemetry {

    fun install(context: Context) {
        // No google-services.json means the Firebase plugin never ran and there is no app.
        if (FirebaseApp.getApps(context).isEmpty()) {
            Timber.w("Firebase is not configured; telemetry off")
            return
        }
        val crashlytics = FirebaseCrashlytics.getInstance()
        val analytics = FirebaseAnalytics.getInstance(context)

        exceptionReporter = { crashlytics.recordException(it) }

        PlaybackHealth.sink = { event ->
            when (event) {
                is Event.Resolved -> analytics.logEvent("stream_resolved", Bundle().apply {
                    putString("source", event.source)
                    putString("client", event.client)
                    putLong("ms", event.ms)
                    putString("speed", speedBucket(event.ms))
                })

                is Event.ResolveFailed -> {
                    analytics.logEvent("stream_resolve_failed", Bundle().apply {
                        putString("source", event.source)
                        putString("why", event.why)
                        putString("status", event.status ?: "-")
                        putLong("permanent", if (event.permanent) 1 else 0)
                        putLong("ms", event.ms)
                        // Analytics caps a value at 100 characters; the full one goes to Crashlytics.
                        putString("cascade", event.cascade.take(100))
                    })
                    crashlytics.log("resolve failed: ${event.why} status=${event.status} cascade=${event.cascade}")
                    if (!event.permanent) {
                        // One non-fatal issue for all of them (same stack), with the reason in its
                        // message: a YouTube-side break shows up as one count that climbs.
                        crashlytics.setCustomKey("resolve_cascade", event.cascade.take(1000))
                        crashlytics.recordException(StreamResolveFailure(event.why, event.status))
                    }
                }

                is Event.Recovering -> {
                    analytics.logEvent("playback_recovering", Bundle().apply {
                        putString("error", event.errorCode)
                        putString("action", event.action)
                        putLong("attempt", event.attempt.toLong())
                    })
                    crashlytics.log("recovering: ${event.errorCode} -> ${event.action} #${event.attempt}")
                }

                is Event.GaveUp -> analytics.logEvent("playback_gave_up", Bundle().apply {
                    putString("error", event.errorCode)
                    putString("why", event.why)
                    putString("outcome", event.outcome)
                })
            }
        }
    }

    private fun speedBucket(ms: Long): String = when {
        ms < 500 -> "<0.5s"
        ms < 1000 -> "0.5-1s"
        ms < 2000 -> "1-2s"
        ms < 5000 -> "2-5s"
        else -> ">5s"
    }

    /** Its own type, so these never merge into an issue for a real crash. */
    private class StreamResolveFailure(why: String, status: String?) :
        Exception("Stream resolve failed: $why" + (status?.let { " ($it)" } ?: ""))
}
