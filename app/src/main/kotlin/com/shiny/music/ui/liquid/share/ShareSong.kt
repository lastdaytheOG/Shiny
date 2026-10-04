package com.shiny.music.ui.liquid.share

import android.content.Context
import android.content.Intent
import com.shiny.music.models.MediaMetadata
import com.shiny.music.social.ShinyLinks

/** The link a recipient taps to hear the song in Shiny (or on the web without it). */
fun songLink(id: String) = "${ShinyLinks.WEB_BASE}/watch?v=$id"

/**
 * Shares a song as one line of text and its link: "Title · Artist" then the URL, so the
 * message reads well in any app and the link previews on its own line.
 */
fun Context.shareSong(id: String, title: String, artist: String) {
    val line = if (artist.isBlank()) title else "$title · $artist"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, line)
        putExtra(Intent.EXTRA_TEXT, "$line\n${songLink(id)}")
    }
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.shareSong(metadata: MediaMetadata) =
    shareSong(metadata.id, metadata.title, metadata.artists.joinToString { it.name })
