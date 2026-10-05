package com.shiny.music.ui.liquid.share

import android.content.Context
import android.content.Intent
import com.shiny.music.R
import com.shiny.music.db.entities.Song
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

/**
 * Shares a playlist or album. One that exists on YouTube Music goes as its [link]. One that
 * only lives on this phone (a Spotify import, music on the device) has no page to link to,
 * so it goes as its name and its songs. The link used to be built from the missing id,
 * "…/playlist?list=null", and opening that crashed Shiny.
 */
fun Context.shareCollection(name: String, link: String?, songs: List<Song>) {
    val text = link ?: buildString {
        append(name)
        if (songs.isNotEmpty()) append(" · ").append(getString(R.string.liquid_songs_count, songs.size))
        songs.take(SHARED_SONG_LINES).forEach { song ->
            val artist = song.artists.joinToString { it.name }
            append('\n').append(if (artist.isBlank()) song.song.title else "${song.song.title} · $artist")
        }
        if (songs.size > SHARED_SONG_LINES) append("\n+ ").append(songs.size - SHARED_SONG_LINES)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** How many songs a shared list names before it says how many more there are. */
private const val SHARED_SONG_LINES = 50
