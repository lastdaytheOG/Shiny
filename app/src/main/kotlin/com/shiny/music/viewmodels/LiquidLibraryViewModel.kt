package com.shiny.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiny.music.constants.AlbumSortType
import com.shiny.music.constants.PlaylistSortType
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.LocalItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Library tab's own data: the "Recently Added" grid under the category list.
 * Newest albums first, with the newest playlists folded in so a listener who mostly
 * builds playlists still sees their latest work there.
 */
@HiltViewModel
class LiquidLibraryViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
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
