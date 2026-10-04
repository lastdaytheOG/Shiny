package com.shiny.music.spotifyimport

/**
 * Reads the playlist out of whatever was pasted: a web link (with or without the tracking
 * that Share adds), a `spotify:` URI, or the bare id.
 */
object SpotifyPlaylistLink {
    private const val ID_LENGTH = 22
    private const val SEGMENT = "playlist"

    /** The playlist id in [text], or null when [text] does not point at a playlist. */
    fun idOf(text: String): String? {
        val pasted = text.trim()
        if (pasted.isEmpty()) return null
        if (isId(pasted)) return pasted
        if (pasted.startsWith("spotify:", ignoreCase = true)) return idAfterSegment(pasted.split(':'))

        val withoutScheme = pasted.substringAfter("://")
        val host = withoutScheme.substringBefore('/').substringBefore('?').lowercase()
        if (host != "spotify.com" && !host.endsWith(".spotify.com")) return null
        val path = withoutScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
        return idAfterSegment(path.split('/'))
    }

    /** [text] as the plain web link to its playlist, or null when it is not one. */
    fun canonical(text: String): String? = idOf(text)?.let { "https://open.spotify.com/playlist/$it" }

    private fun idAfterSegment(parts: List<String>): String? {
        val at = parts.indexOfFirst { it.equals(SEGMENT, ignoreCase = true) }
        if (at < 0) return null
        return parts.getOrNull(at + 1)?.takeIf(::isId)
    }

    private fun isId(candidate: String): Boolean =
        candidate.length == ID_LENGTH && candidate.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
}
