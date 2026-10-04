

package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.PlaylistSongSortDescendingKey
import com.shiny.music.constants.PlaylistSongSortType
import com.shiny.music.constants.PlaylistSongSortTypeKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.PlaylistSong
import com.shiny.music.extensions.reversed
import com.shiny.music.extensions.toEnum
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.playlist.PlaylistSuggestions
import com.shiny.music.utils.SyncUtils
import com.shiny.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import com.shiny.music.utils.getOrNull

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            // Room re-runs this query on any write to the song, artist or album tables — a
            // play count, a download, a like anywhere in the library — and hands back an
            // equal list. Passed on, each one re-sorted the playlist on the main thread and
            // rebuilt every visible row: the stutter on big imported playlists. Only a real
            // change goes through now, and the sorting runs on Default (see flowOn below).
            database.playlistSongs(playlistId).distinctUntilChanged(),
            context.dataStore.data
                .map {
                    Triple(
                        it.getOrNull(PlaylistSongSortTypeKey).toEnum(PlaylistSongSortType.CUSTOM),
                        it.getOrNull(PlaylistSongSortDescendingKey) ?: true,
                        it.getOrNull(HideVideoSongsKey) ?: false
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs
                        .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { it.name } })
                        .groupBy { it.song.album?.title }
                        .flatMap { (_, songsByAlbum) ->
                            songsByAlbum.sortedBy {
                                it.song.artists.joinToString(
                                    ""
                                ) { it.name }
                            }
                        }
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            
            playlist.first { it != null }?.playlist?.browseId?.let { browseId ->
                syncUtils.syncPlaylist(browseId, playlistId)
            }
        }

        viewModelScope.launch {
            val sortedSongs =
                playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, playlistSong ->
                    if (playlistSong.map.position != index) {
                        update(playlistSong.map.copy(position = index))
                    }
                }
            }
        }
    }

    private val _suggestions = MutableStateFlow<List<SongItem>>(emptyList())

    /** Songs that would fit this playlist and are not in it yet. Empty until [loadSuggestions] has run. */
    val suggestions = _suggestions.asStateFlow()

    private var suggestionsJob: Job? = null

    /**
     * Asks the catalogue what goes with a few of this playlist's songs. It runs once for the
     * screen; if that attempt finds nothing (no connection, say), the next call tries again.
     */
    fun loadSuggestions() {
        if (suggestionsJob != null) return
        suggestionsJob = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                val songs = database.playlistSongs(playlistId).first().map { it.song.song }
                val seeds = PlaylistSuggestions.seeds(songs.filterNot { it.isLocal }.map { it.id })
                val related = seeds
                    .map { seed -> async { YouTube.next(WatchEndpoint(videoId = seed)).getOrNull()?.items.orEmpty() } }
                    .awaitAll()
                PlaylistSuggestions.merge(related, songs.mapTo(HashSet()) { it.id }, SongItem::id)
            }
            _suggestions.value = found
            if (found.isEmpty()) suggestionsJob = null
        }
    }

    /** Adds one suggestion to the end of the playlist, and to its YouTube copy if it has one. */
    fun addSuggestion(song: SongItem) {
        _suggestions.update { shown -> shown.filterNot { it.id == song.id } }
        viewModelScope.launch(Dispatchers.IO) {
            val target = database.playlist(playlistId).first() ?: return@launch
            runCatching { database.insert(song.toMediaMetadata()) }
            database.withTransaction { addSongToPlaylist(target, listOf(song.id)) }
            target.playlist.browseId?.let { YouTube.addToPlaylist(it, song.id) }
        }
    }
}
