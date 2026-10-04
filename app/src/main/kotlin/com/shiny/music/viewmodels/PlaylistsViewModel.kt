

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiny.music.constants.AddToPlaylistSortDescendingKey
import com.shiny.music.constants.AddToPlaylistSortTypeKey
import com.shiny.music.constants.PlaylistSortType
import com.shiny.music.db.MusicDatabase
import com.shiny.music.extensions.toEnum
import com.shiny.music.utils.SyncUtils
import com.shiny.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.shiny.music.utils.getOrNull

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it.getOrNull(AddToPlaylistSortTypeKey).toEnum(PlaylistSortType.CREATE_DATE) to (it.getOrNull(AddToPlaylistSortDescendingKey)
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    
    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }
}
