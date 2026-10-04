package com.shiny.music.ui.liquid

import androidx.compose.runtime.Immutable

/**
 * What a tapped tile already showed (title, who made it, cover), kept for the page it opens.
 * A first visit to an album or artist then shows them at once instead of a blank spinner
 * while the page loads; the page's own data replaces them as soon as it arrives.
 *
 * In memory only, the most recent few dozen taps.
 */
object NavPeek {
    @Immutable
    data class Peek(val title: String, val subtitle: String?, val artwork: String?)

    private const val MAX_ENTRIES = 48

    private val entries = object : LinkedHashMap<String, Peek>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Peek>?) = size > MAX_ENTRIES
    }

    fun put(id: String, peek: Peek) {
        if (peek.title.isBlank()) return
        synchronized(entries) { entries[id] = peek }
    }

    fun get(id: String): Peek? = synchronized(entries) { entries[id] }
}
