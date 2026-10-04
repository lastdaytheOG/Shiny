

package com.shiny.music.lyrics

import kotlinx.coroutines.flow.MutableStateFlow

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double
)

data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val romanizedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val translatedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val agent: String? = null,
    val isBackground: Boolean = false
) : Comparable<LyricsEntry> {
    // compareTo(Long) rather than `(time - other.time).toInt()`: the subtraction overflows
    // Int for timestamps more than ~24 days apart, which a malformed file can produce,
    // and an inconsistent comparator both breaks sort() and silently invalidates the
    // binary search in LyricsUtils.findCurrentLineIndex that assumes this ordering.
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}
