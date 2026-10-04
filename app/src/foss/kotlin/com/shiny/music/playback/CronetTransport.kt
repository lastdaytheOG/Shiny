package com.shiny.music.playback

import android.content.Context

/** FOSS builds carry no Play Services Cronet: streams stay on OkHttp. */
object CronetTransport {
    fun install(context: Context) = Unit
}
