package com.shiny.music.ui.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.LocalItem
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.Song
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.playback.PlayerConnection
import com.shiny.music.playback.queues.ListQueue
import com.shiny.music.playback.queues.YouTubeQueue
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.ui.component.MenuState
import com.shiny.music.ui.menu.AlbumMenu
import com.shiny.music.ui.menu.ArtistMenu
import com.shiny.music.ui.menu.PlaylistMenu
import com.shiny.music.ui.menu.SongMenu
import com.shiny.music.ui.menu.YouTubeAlbumMenu
import com.shiny.music.ui.menu.YouTubeArtistMenu
import com.shiny.music.ui.menu.YouTubePlaylistMenu
import com.shiny.music.ui.menu.YouTubeSongMenu
import kotlinx.coroutines.CoroutineScope

/**
 * What tapping and long-pressing an item means, in one place.
 *
 * Every Liquid screen shows the same four kinds of thing — songs, albums, artists,
 * playlists — from two sources, the library and YouTube Music. The old screens each
 * carried their own copy of this dispatch; here it is written once, so a song behaves the
 * same on Home, in Search and on an artist page.
 */
@Stable
class LiquidActions internal constructor(
    val navController: NavController,
    val playerConnection: PlayerConnection,
    private val menuState: MenuState,
    private val scope: CoroutineScope,
    private val haptic: HapticFeedback,
) {
    fun open(item: YTItem) {
        when (item) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata())
            )
            is AlbumItem -> {
                NavPeek.put(item.id, NavPeek.Peek(item.title, item.artists?.joinToString { it.name }, item.thumbnail))
                navController.navigate("album/${item.id}")
            }
            is ArtistItem -> {
                NavPeek.put(item.id, NavPeek.Peek(item.title, null, item.thumbnail))
                navController.navigate("artist/${item.id}")
            }
            is PlaylistItem -> openPlaylist(item)
        }
    }

    fun menu(item: YTItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
            }
        }
    }

    fun open(item: LocalItem, radio: Boolean = true) {
        when (item) {
            is Song -> if (radio) {
                playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
            } else {
                playerConnection.playQueue(ListQueue(items = listOf(item.toMediaItem())))
            }
            is Album -> navController.navigate("album/${item.id}")
            is Artist -> navController.navigate("artist/${item.id}")
            is Playlist -> navController.navigate("local_playlist/${item.id}")
        }
    }

    fun menu(item: LocalItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is Song -> SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss)
                is Album -> AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss)
                is Artist -> ArtistMenu(originalArtist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                is Playlist -> PlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
            }
        }
    }

    /** Plays [songs] as a list, starting at [startIndex]. */
    fun playSongs(title: String?, songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(title = title, items = ordered.map { it.toMediaItem() }, startIndex = if (shuffle) 0 else startIndex)
        )
    }

    /** Plays [ordered], a Smart Shuffle order, from the top; shuffle mode keeps that order. */
    fun playPreShuffled(title: String?, ordered: List<Song>) {
        if (ordered.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(title = title, items = ordered.map { it.toMediaItem() }, preShuffled = true)
        )
    }

    /** Plays [ordered] YouTube songs, a Smart Shuffle order, from the top; shuffle mode keeps that order. */
    fun playPreShuffledItems(title: String?, ordered: List<SongItem>) {
        if (ordered.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(title = title, items = ordered.map { it.toMediaMetadata().toMediaItem() }, preShuffled = true)
        )
    }

    fun playSongItems(title: String?, songs: List<SongItem>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(
                title = title,
                items = ordered.map { it.toMediaMetadata().toMediaItem() },
                startIndex = if (shuffle) 0 else startIndex,
            )
        )
    }

    fun openPlaylist(playlist: PlaylistItem) {
        when (val id = playlist.id.removePrefix("VL")) {
            "LM" -> navController.navigate("auto_playlist/liked")
            "SE" -> navController.navigate("auto_playlist/downloaded")
            else -> navController.navigate("online_playlist/$id")
        }
    }

    fun openBrowse(endpoint: BrowseEndpoint) {
        when {
            endpoint.browseId == "FEmusic_moods_and_genres" -> navController.navigate("mood_and_genres")
            endpoint.params != null -> navController.navigate("youtube_browse/${endpoint.browseId}?params=${endpoint.params}")
            else -> navController.navigate("browse/${endpoint.browseId}")
        }
    }

    fun togglePlayPause() = playerConnection.togglePlayPause()
}

@Composable
fun rememberLiquidActions(navController: NavController): LiquidActions? {
    val playerConnection = LocalPlayerConnection.current ?: return null
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    return remember(navController, playerConnection, menuState, scope, haptic) {
        LiquidActions(navController, playerConnection, menuState, scope, haptic)
    }
}

/** "Artist · Artist" for a YouTube item's subtitle line. */
fun YTItem.subtitleText(): String? = when (this) {
    is SongItem -> artists.joinToString { it.name }
    is AlbumItem -> listOfNotNull(artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }, year?.toString()).joinToString(" · ")
    is PlaylistItem -> author?.name ?: songCountText
    is ArtistItem -> null
}

fun LocalItem.subtitleText(): String? = when (this) {
    is Song -> artists.joinToString { it.name }
    is Album -> artists.joinToString { it.name }
    is Artist -> null
    is Playlist -> null
}
