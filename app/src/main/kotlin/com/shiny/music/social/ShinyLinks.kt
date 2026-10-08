package com.shiny.music.social

import android.net.Uri

/**
 * Shiny's public web address and every link built on it: shares, listen-along links, profiles, badges
 * and Listen Together invites. The site is the social server in `/server` on Shiny's own domain.
 *
 * Changing the domain: [WEB_HOST] here, the share links in `innertube/.../YTItem.kt` and
 * `core/.../PlaylistEntity.kt` (those modules can't see this one), the app-link hosts in
 * `AndroidManifest.xml`, and `WEB_BASE` + the routes in `server/wrangler.toml`.
 */
object ShinyLinks {
    const val WEB_HOST = "shinymusic.in"
    const val WEB_BASE = "https://$WEB_HOST"

    fun song(videoId: String) = "$WEB_BASE/watch?v=$videoId"

    fun playlist(playlistId: String) = "$WEB_BASE/playlist?list=$playlistId"

    /** A playlist whose songs the server keeps ([SharedPlaylist]), for one that isn't on YouTube Music. */
    fun sharedPlaylist(id: String) = "$WEB_BASE/p/$id"

    fun channel(channelId: String) = "$WEB_BASE/channel/$channelId"

    /** A Listen Together invite: short enough to read out, and it opens the app. */
    fun room(code: String) = "$WEB_BASE/j/${Uri.encode(code)}"

    /** Where people without Shiny get it. */
    const val DOWNLOAD = "$WEB_BASE/download"

    fun profile(username: String) = "$WEB_BASE/u/$username"

    fun badge(username: String) = "$WEB_BASE/badge/$username.svg"

    /**
     * [videoId] from where the sharer is now. `t` is the position and `at` when it was taken, so the page
     * keeps the start time moving while the link sits in a Discord activity; `d` stops it past the end.
     */
    fun listenAlong(
        videoId: String,
        positionMs: Long,
        durationMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): String = buildString {
        append(WEB_BASE).append("/watch?v=").append(videoId)
        append("&t=").append(positionMs.coerceAtLeast(0L) / 1000L)
        append("&at=").append(nowMs)
        if (durationMs != null && durationMs > 0L) append("&d=").append(durationMs / 1000L)
    }
}
