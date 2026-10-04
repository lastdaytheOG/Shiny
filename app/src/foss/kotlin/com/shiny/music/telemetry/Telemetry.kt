package com.shiny.music.telemetry

import android.content.Context

/** The FOSS build has no Firebase: playback health stays in the local log. */
object Telemetry {
    fun install(context: Context) = Unit
}
