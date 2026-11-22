package com.shiny.music.stats

import java.util.concurrent.CopyOnWriteArraySet

object LiveListens {
    private val plays = CopyOnWriteArraySet<PlayCounter.Play<*>>()

    fun add(play: PlayCounter.Play<*>) {
        plays.add(play)
    }

    fun remove(play: PlayCounter.Play<*>) {
        plays.remove(play)
    }

    fun heard(): Map<Long, Long> = buildMap {
        plays.forEach { play -> play.eventId?.let { put(it, play.heardMs()) } }
    }
}
