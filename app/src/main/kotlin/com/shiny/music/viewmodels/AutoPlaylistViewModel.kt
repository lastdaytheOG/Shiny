

package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiny.music.constants.ExportedSongIdsKey
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.SongSortDescendingKey
import com.shiny.music.constants.SongSortType
import com.shiny.music.constants.SongSortTypeKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Song
import com.shiny.music.extensions.filterExplicit
import com.shiny.music.extensions.filterVideoSongs
import com.shiny.music.extensions.toEnum
import com.shiny.music.utils.SyncUtils
import com.shiny.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.shiny.music.utils.getOrNull

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val playlist = savedStateHandle.get<String>("playlist")!!

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val likedSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it.getOrNull(SongSortTypeKey).toEnum(SongSortType.CREATE_DATE) to (it.getOrNull(SongSortDescendingKey) ?: true),
                        it.getOrNull(HideExplicitKey) ?: false,
                        it.getOrNull(HideVideoSongsKey) ?: false
                    ),
                    it.getOrNull(ExportedSongIdsKey) ?: "",
                    Unit
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { (triple, exportedSongIds, _) ->
                val (sortDesc, hideExplicit, hideVideoSongs) = triple
                val (sortType, descending) = sortDesc
                when (playlist) {
                    "liked" -> database.likedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "downloaded" -> database.downloadedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "uploaded" -> database.uploadedSongs(sortType, descending)
                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }

                    "exported" -> {
                        val ids = exportedSongIds.split(",").filter { it.isNotBlank() }
                        database.getSongsByIdsFlow(ids)
                            .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    }

                    else -> kotlinx.coroutines.flow.flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            when (playlist) {
                "liked" -> syncUtils.syncLikedSongsSuspend()
                "uploaded" -> syncUtils.syncUploadedSongsSuspend()
            }
            _isRefreshing.value = false
        }
    }
}
