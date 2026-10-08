package com.shiny.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.models.SongItem
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.PlaylistEntity
import com.shiny.music.db.entities.PlaylistSongMap
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.social.SocialApiException
import com.shiny.music.social.SocialRepository
import com.shiny.music.social.toSongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface SharedPlaylistState {
    data object Loading : SharedPlaylistState

    data class Ready(val name: String, val songs: List<SongItem>) : SharedPlaylistState

    /** [gone]: the link leads to no playlist. Otherwise the server wasn't reached, and trying again can work. */
    data class Failed(val gone: Boolean) : SharedPlaylistState
}

/** A playlist opened from a `shinymusic.in/p/ID` link: its songs come from Shiny's server. */
@HiltViewModel
class SharedPlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val social: SocialRepository,
    private val database: MusicDatabase,
) : ViewModel() {
    private val shareId = savedStateHandle.get<String>("shareId")!!

    /** The library copy's id comes from the link, so one link is one playlist however often it is opened. */
    val libraryPlaylistId = "SHARED_$shareId"

    val inLibrary = database.playlist(libraryPlaylistId)
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow<SharedPlaylistState>(SharedPlaylistState.Loading)
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = SharedPlaylistState.Loading
        viewModelScope.launch {
            _state.value = try {
                val playlist = social.api.sharedPlaylist(shareId)
                SharedPlaylistState.Ready(playlist.name, playlist.songs.map { it.toSongItem() })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SharedPlaylistState.Failed(gone = error is SocialApiException && error.status == 404)
            }
        }
    }

    fun addToLibrary() {
        val playlist = _state.value as? SharedPlaylistState.Ready ?: return
        viewModelScope.launch {
            database.withTransaction {
                if (getPlaylistById(libraryPlaylistId) != null) return@withTransaction
                insert(PlaylistEntity(id = libraryPlaylistId, name = playlist.name, bookmarkedAt = LocalDateTime.now()))
                playlist.songs.forEachIndexed { index, song ->
                    insert(song.toMediaMetadata())
                    insert(PlaylistSongMap(playlistId = libraryPlaylistId, songId = song.id, position = index))
                }
            }
        }
    }
}
