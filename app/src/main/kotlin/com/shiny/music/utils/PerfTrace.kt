package com.shiny.music.utils

import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

object PerfTrace {
    const val HOME_READY = "ShinyHomeReady"
    const val HOME_LOAD = "ShinyHomeLoad"
    const val SNAPSHOT_BUILD = "ShinySnapshotBuild"
    const val FEED_BUILD = "ShinyFeedBuild"
    const val CONTROL_PAUSE = "ShinyControlPause"
    const val CONTROL_RESUME = "ShinyControlResume"
    const val CONTROL_NEXT = "ShinyControlNext"

    private val cookies = AtomicInteger()
    private const val HOME_READY_COOKIE = -1
    private const val CONTROL_COOKIE = -2

    @Volatile private var homeReadyOpen = false
    private var openControl: String? = null

    inline fun <T> async(name: String, block: () -> T): T {
        val cookie = begin(name)
        try {
            return block()
        } finally {
            end(name, cookie)
        }
    }

    fun begin(name: String): Int {
        if (!Trace.isEnabled()) return 0
        val cookie = cookies.incrementAndGet()
        Trace.beginAsyncSection(name, cookie)
        return cookie
    }

    fun end(name: String, cookie: Int) {
        if (cookie != 0) Trace.endAsyncSection(name, cookie)
    }

    fun homeReadyBegin() {
        if (!Trace.isEnabled()) return
        homeReadyOpen = true
        Trace.beginAsyncSection(HOME_READY, HOME_READY_COOKIE)
    }

    fun homeReadyEnd() {
        if (!homeReadyOpen) return
        homeReadyOpen = false
        Trace.endAsyncSection(HOME_READY, HOME_READY_COOKIE)
    }

    fun controlBegin(name: String) {
        if (!Trace.isEnabled()) return
        controlEnd()
        openControl = name
        Trace.beginAsyncSection(name, CONTROL_COOKIE)
    }

    fun controlEnd(name: String? = null) {
        val open = openControl ?: return
        if (name != null && name != open) return
        openControl = null
        Trace.endAsyncSection(open, CONTROL_COOKIE)
    }
}
