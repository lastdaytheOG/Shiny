package com.shiny.music.social

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.shiny.music.db.entities.Song
import kotlinx.serialization.Serializable

/**
 * A playlist shared as a link, `shinymusic.in/p/ID`. A playlist that only lives on this phone (a
 * Spotify import, one made in Shiny) has no page anywhere else, so the server keeps its songs and
 * anyone with the link gets them back. See "Shared playlists" in `server/src/index.js`.
 */
@Serializable
data class SharedPlaylist(
    val id: String,
    val name: String,
    val songs: List<SharedSong> = emptyList(),
)

@Serializable
data class SharedSong(
    val id: String,
    val title: String,
    val artists: List<SharedArtist> = emptyList(),
    val duration: Int? = null,
    val thumbnail: String? = null,
    val explicit: Boolean = false,
)

@Serializable
data class SharedArtist(val name: String, val id: String? = null)

@Serializable
data class SharedPlaylistUpload(val name: String, val songs: List<SharedSong>)

/** [songCount] is how many songs the link holds, which can be fewer than were sent. */
@Serializable
data class SharedPlaylistLink(val id: String, val songCount: Int)

/** The server keeps this many songs of a playlist; the rest of a longer one is left out of the link. */
const val SHARED_PLAYLIST_MAX_SONGS = 2000

private val YouTubeVideoId = Regex("[A-Za-z0-9_-]{11}")

/** Null for a song only this phone can play: a file on the device has nothing to link to. */
fun Song.toSharedSong(): SharedSong? {
    if (song.isLocal || !YouTubeVideoId.matches(song.id)) return null
    return SharedSong(
        id = song.id,
        title = song.title,
        // An artist made on this phone has an id of its own that means nothing elsewhere.
        artists = artists.map { artist -> SharedArtist(artist.name, artist.id.takeIf { it.startsWith("UC") }) },
        duration = song.duration.takeIf { it > 0 },
        thumbnail = song.thumbnailUrl,
        explicit = song.explicit,
    )
}

fun SharedSong.toSongItem() = SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(name = it.name, id = it.id) },
    duration = duration,
    thumbnail = thumbnail ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    explicit = explicit,
)
