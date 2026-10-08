package com.shiny.music.ui.liquid.share

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.shiny.music.R
import com.shiny.music.db.entities.Song
import com.shiny.music.models.MediaMetadata
import com.shiny.music.social.SHARED_PLAYLIST_MAX_SONGS
import com.shiny.music.social.ShinyLinks
import com.shiny.music.social.SocialApiException
import com.shiny.music.social.SocialRepository
import com.shiny.music.social.toSharedSong
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The link a recipient taps to hear the song in Shiny (or on the web without it). */
fun songLink(id: String) = "${ShinyLinks.WEB_BASE}/watch?v=$id"

/**
 * Shares a song as one line of text and its link: "Title · Artist" then the URL, so the
 * message reads well in any app and the link previews on its own line.
 */
fun Context.shareSong(id: String, title: String, artist: String) {
    val line = if (artist.isBlank()) title else "$title · $artist"
    sendText(subject = line, text = "$line\n${songLink(id)}")
}

fun Context.shareSong(metadata: MediaMetadata) =
    shareSong(metadata.id, metadata.title, metadata.artists.joinToString { it.name })

/**
 * Shares a playlist or album as a link that opens it. One that exists on YouTube Music goes as
 * its [link]. One that only lives on this phone (a Spotify import, a playlist made here) has no
 * page to link to, so its songs are sent to Shiny's server, which gives it one
 * ([ShinyLinks.sharedPlaylist]). Only music that is files on this device still goes as a list
 * of names: a link to it could play nothing for anyone else.
 *
 * The link was once built from the missing id, "…/playlist?list=null", which crashed Shiny when
 * opened; then the whole playlist went as fifty lines of song names, which opened nothing.
 */
fun Context.shareCollection(name: String, link: String?, songs: List<Song>) {
    if (link != null) return sendText(subject = name, text = link)
    val linkable = songs.mapNotNull { it.toSharedSong() }
    if (linkable.isEmpty()) return sendText(subject = name, text = songList(name, songs))

    val social = EntryPointAccessors.fromApplication(applicationContext, ShareDependencies::class.java).socialRepository()
    Toast.makeText(this, R.string.share_making_link, Toast.LENGTH_SHORT).show()
    // Not the menu's scope: the menu has closed before the server answers.
    CoroutineScope(Dispatchers.Main.immediate).launch {
        runCatching { social.api.sharePlaylist(name, linkable.take(SHARED_PLAYLIST_MAX_SONGS)) }
            .onSuccess { shared ->
                if (shared.songCount < songs.size) {
                    Toast.makeText(
                        this@shareCollection,
                        getString(R.string.share_link_partial, shared.songCount, songs.size),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                sendText(subject = name, text = "$name\n${ShinyLinks.sharedPlaylist(shared.id)}")
            }
            .onFailure { error ->
                // The server's own words when it is asking for a pause; otherwise it wasn't reached.
                val reason = (error as? SocialApiException)?.takeIf { it.status == 429 }?.message
                Toast.makeText(this@shareCollection, reason ?: getString(R.string.share_link_failed), Toast.LENGTH_LONG).show()
            }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareDependencies {
    fun socialRepository(): SocialRepository
}

private fun Context.songList(name: String, songs: List<Song>) = buildString {
    append(name)
    if (songs.isNotEmpty()) append(" · ").append(getString(R.string.liquid_songs_count, songs.size))
    songs.take(SHARED_SONG_LINES).forEach { song ->
        val artist = song.artists.joinToString { it.name }
        append('\n').append(if (artist.isBlank()) song.song.title else "${song.song.title} · $artist")
    }
    if (songs.size > SHARED_SONG_LINES) append("\n+ ").append(songs.size - SHARED_SONG_LINES)
}

private fun Context.sendText(subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** How many songs a shared list names before it says how many more there are. */
private const val SHARED_SONG_LINES = 50
