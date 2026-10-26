package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiny.music.constants.AlbumSortType
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.PlaylistSortType
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.LibraryCounts
import com.shiny.music.db.entities.LocalItem
import com.shiny.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Library tab's own data: the "Recently Added" grid under the category list.
 * Newest albums first, with the newest playlists folded in so a listener who mostly
 * builds playlists still sees their latest work there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiquidLibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val scroll = ListScroll()

    val counts: StateFlow<LibraryCounts> = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (hideExplicit, hideVideos) -> database.libraryCounts(hideExplicit, hideVideos) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCounts())

    val recentlyAdded: StateFlow<List<LocalItem>> =
        combine(
            database.albums(AlbumSortType.CREATE_DATE, true),
            database.playlists(PlaylistSortType.CREATE_DATE, true),
        ) { albums, playlists ->
            val a = albums.take(12)
            val p = playlists.filter { it.songCount > 0 }.take(4)
            buildList<LocalItem> {
                var ai = 0
                var pi = 0
                while (size < 16 && (ai < a.size || pi < p.size)) {
                    if (ai < a.size) add(a[ai++])
                    if (ai < a.size) add(a[ai++])
                    if (pi < p.size) add(p[pi++])
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
