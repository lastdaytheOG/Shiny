package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.LocalItem
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.Song
import com.shiny.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.shiny.music.utils.getOrNull

/**
 * The listener's own music, searched as they type.
 *
 * Two sizes: the typeahead wants a taste of each kind ([PREVIEW_SIZE]), the Library scope
 * wants the list ([WIDE_SIZE]). [wide] switches between them rather than the screen running
 * a second view model.
 *
 * Lyrics are searched too, over the lines Shiny has already cached — nothing is fetched, and
 * a query shorter than [MIN_LYRICS_QUERY] does not ask at all, since a one- or two-letter
 * `LIKE` over every stored lyric matches everything and means nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalSearchViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LocalFilter.ALL)

    /** Set while the Library scope is showing: every match rather than the first few. */
    val wide = MutableStateFlow(false)

    private data class Args(val query: String, val filter: LocalFilter, val wide: Boolean, val hideVideoSongs: Boolean)

    val result =
        combine(
            query,
            filter,
            wide,
            context.dataStore.data
                .map { it.getOrNull(HideVideoSongsKey) ?: false }
                .distinctUntilChanged(),
            ::Args,
        ).flatMapLatest { (query, filter, wide, hideVideoSongs) ->
            if (query.isEmpty()) {
                flowOf(LocalSearchResult("", filter, emptyMap()))
            } else {
                val size = if (wide) WIDE_SIZE else PREVIEW_SIZE
                when (filter) {
                    LocalFilter.ALL ->
                        combine(
                            database.searchSongs(query, size),
                            database.searchAlbums(query, size),
                            database.searchArtists(query, size),
                            database.searchPlaylists(query, size),
                            lyricsMatches(database, query, size),
                        ) { songs, albums, artists, playlists, lyrics ->
                            val filteredSongs = if (hideVideoSongs) songs.filter { !it.song.isVideo } else songs
                            val titleIds = filteredSongs.map { it.id }.toSet()
                            LocalSearchResult(
                                query = query,
                                filter = filter,
                                map = group(filteredSongs + albums + artists + playlists),
                                // A song whose title already matched is not also a lyric find.
                                lyrics = lyrics.filter { it.id !in titleIds && !(hideVideoSongs && it.song.isVideo) },
                            )
                        }

                    LocalFilter.SONG -> database.searchSongs(query, size).map { songs ->
                        val filtered = if (hideVideoSongs) songs.filter { !it.song.isVideo } else songs
                        LocalSearchResult(query, filter, group(filtered))
                    }

                    LocalFilter.ALBUM -> database.searchAlbums(query, size).map { LocalSearchResult(query, filter, group(it)) }
                    LocalFilter.ARTIST -> database.searchArtists(query, size).map { LocalSearchResult(query, filter, group(it)) }
                    LocalFilter.PLAYLIST -> database.searchPlaylists(query, size).map { LocalSearchResult(query, filter, group(it)) }
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            LocalSearchResult("", filter.value, emptyMap()),
        )

    /**
     * Songs whose cached lyrics contain [query]. That is a scan of every stored lyric, so it
     * waits for typing to pause (a newer query cancels the wait) instead of running on each
     * keystroke; the title matches above it answer at once.
     */
    private fun lyricsMatches(database: MusicDatabase, query: String, size: Int): Flow<List<Song>> =
        if (query.length < MIN_LYRICS_QUERY) {
            flowOf(emptyList())
        } else {
            flow {
                delay(LYRICS_DEBOUNCE_MS)
                emitAll(database.searchLyrics(query, size))
            }.onStart { emit(emptyList()) }
        }

    private fun group(items: List<LocalItem>): Map<LocalFilter, List<LocalItem>> =
        items.groupBy {
            when (it) {
                is Song -> LocalFilter.SONG
                is Album -> LocalFilter.ALBUM
                is Artist -> LocalFilter.ARTIST
                is Playlist -> LocalFilter.PLAYLIST
            }
        }

    companion object {
        const val PREVIEW_SIZE = 4
        const val WIDE_SIZE = 60
        const val MIN_LYRICS_QUERY = 3
        const val LYRICS_DEBOUNCE_MS = 300L
    }
}

enum class LocalFilter {
    ALL,
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
}

data class LocalSearchResult(
    val query: String,
    val filter: LocalFilter,
    val map: Map<LocalFilter, List<LocalItem>>,
    /** Songs found by a line of their lyrics rather than their title. */
    val lyrics: List<Song> = emptyList(),
) {
    val isEmpty: Boolean get() = map.values.all { it.isEmpty() } && lyrics.isEmpty()
}
