package com.shiny.music.utils

import android.os.Build
import android.os.Trace

/**
 * Trace sections read by the macrobenchmarks in `:baselineprofile`, which match them by name.
 * Measurement only: nothing here changes what playback does.
 *
 * [TAP_TO_AUDIBLE] is an async section that opens when the UI asks the player to start and
 * closes when ExoPlayer first reports the audio position advancing — samples reaching the
 * output. [RESOLVE] wraps the stream lookup on the loader thread. Outside a trace capture both
 * reduce to an `isEnabled` check.
 */
object PlaybackTrace {
    const val TAP_TO_AUDIBLE = "ShinyTapToAudible"
    const val RESOLVE = "ShinyResolve"

    // Main thread only: play requests come from the UI, and the audio callback arrives on the
    // player's application looper, which is the main looper.
    private var openCookie = 0
    private var lastCookie = 0

    fun playRequested() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !Trace.isEnabled()) return
        // A request that never became audible (paused before it started, say) is closed here
        // rather than left open to be closed by this one's audio.
        if (openCookie != 0) Trace.endAsyncSection(TAP_TO_AUDIBLE, openCookie)
        openCookie = ++lastCookie
        Trace.beginAsyncSection(TAP_TO_AUDIBLE, openCookie)
    }

    fun audible() {
        if (openCookie == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        Trace.endAsyncSection(TAP_TO_AUDIBLE, openCookie)
        openCookie = 0
    }

    /** A synchronous section; [block] must not suspend or hop threads. */
    inline fun <T> section(name: String, block: () -> T): T {
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }
}
