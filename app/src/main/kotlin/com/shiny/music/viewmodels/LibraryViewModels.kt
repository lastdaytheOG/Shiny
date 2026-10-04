

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.shiny.music.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.shiny.music.constants.AlbumFilter
import com.shiny.music.constants.AlbumFilterKey
import com.shiny.music.constants.AlbumSortDescendingKey
import com.shiny.music.constants.AlbumSortType
import com.shiny.music.constants.AlbumSortTypeKey
import com.shiny.music.constants.ArtistFilter
import com.shiny.music.constants.ArtistFilterKey
import com.shiny.music.constants.ArtistSongSortDescendingKey
import com.shiny.music.constants.ArtistSongSortType
import com.shiny.music.constants.ArtistSongSortTypeKey
import com.shiny.music.constants.ArtistSortDescendingKey
import com.shiny.music.constants.ArtistSortType
import com.shiny.music.constants.ArtistSortTypeKey
import com.shiny.music.constants.ExportedSongIdsKey
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.HideYoutubeShortsKey
import com.shiny.music.constants.LibraryFilter
import com.shiny.music.constants.PlaylistSortDescendingKey
import com.shiny.music.constants.PlaylistSortType
import com.shiny.music.constants.PlaylistSortTypeKey
import com.shiny.music.constants.SongFilter
import com.shiny.music.constants.SongFilterKey
import com.shiny.music.constants.SongSortDescendingKey
import com.shiny.music.db.entities.Song
import com.shiny.music.constants.SongSortType
import com.shiny.music.constants.SongSortTypeKey
import com.shiny.music.constants.TopSize
import com.shiny.music.db.MusicDatabase
import com.shiny.music.extensions.filterExplicit
import com.shiny.music.extensions.filterExplicitAlbums
import com.shiny.music.extensions.filterVideoSongs
import com.shiny.music.extensions.filterYoutubeShorts
import com.shiny.music.extensions.toEnum
import com.shiny.music.playback.DownloadUtil
import com.shiny.music.utils.SyncUtils
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import com.shiny.music.utils.getOrNull

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it.getOrNull(SongFilterKey).toEnum(SongFilter.LIKED),
                        it.getOrNull(SongSortTypeKey).toEnum(SongSortType.CREATE_DATE),
                        (it.getOrNull(SongSortDescendingKey) ?: true),
                    ),
                    it.getOrNull(ExportedSongIdsKey) ?: "",
                    Pair(it.getOrNull(HideExplicitKey) ?: false, it.getOrNull(HideVideoSongsKey) ?: false)
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, exportedSongIds, hideConfig) ->
                val (filter, sortType, descending) = filterSort
                val (hideExplicit, hideVideoSongs) = hideConfig
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.LIKED -> database.likedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.DOWNLOADED -> database.downloadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.UPLOADED -> database.uploadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.EXPORTED -> {
                        val ids = exportedSongIds.split(",").filter { it.isNotBlank() }
                        database.getSongsByIdsFlow(ids).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncLibrarySongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    it.getOrNull(ArtistFilterKey).toEnum(ArtistFilter.LIKED),
                    it.getOrNull(ArtistSortTypeKey).toEnum(ArtistSortType.CREATE_DATE),
                    it.getOrNull(ArtistSortDescendingKey) ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allAlbums =
        context.dataStore.data
            .map {
                Pair(
                    Triple(
                        it.getOrNull(AlbumFilterKey).toEnum(AlbumFilter.LIKED),
                        it.getOrNull(AlbumSortTypeKey).toEnum(AlbumSortType.CREATE_DATE),
                        it.getOrNull(AlbumSortDescendingKey) ?: true,
                    ),
                    it.getOrNull(HideExplicitKey) ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.LIKED -> database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.LIBRARY -> database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.UPLOADED -> database.albumsUploaded(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    /**
     * Cover art for the automatic collections.
     *
     * Liked, Downloaded and My top N are playlists with real contents, and every other
     * playlist in the grid shows what is inside it. Drawing these five as grey plates with
     * a glyph on them was the one thing left in Library that looked like a settings screen:
     * the collections a listener uses most were the only ones with nothing to look at.
     *
     * Four covers is what [com.shiny.music.ui.component.PlaylistThumbnail] mosaics, and it
     * falls back to the glyph on its own when a collection is genuinely empty - so a new
     * account still gets a clean placeholder rather than a broken grid.
     */
    private fun List<Song>.covers() = mapNotNull { it.song.thumbnailUrl }.distinct().take(4)

    val likedThumbnails =
        database.likedSongs(SongSortType.CREATE_DATE, descending = true)
            .map { it.covers() }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topThumbnails =
        database.mostPlayedSongs(fromTimeStamp = 0L, limit = 8)
            .map { it.covers() }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val downloadedThumbnails =
        database.songsByRowIdAsc()
            .map { songs -> songs.filter { it.song.dateDownload != null }.covers() }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPlaylists =
        context.dataStore.data
            .map {
                Triple(
                    it.getOrNull(PlaylistSortTypeKey).toEnum(PlaylistSortType.CREATE_DATE),
                    it.getOrNull(PlaylistSortDescendingKey) ?: true,
                    it.getOrNull(HideYoutubeShortsKey) ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending, hideYoutubeShorts) ->
                database.playlists(sortType, descending).map { it.filterYoutubeShorts(hideYoutubeShorts) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
    }

    val topValue =
        context.dataStore.data
            .map { it.getOrNull(TopSize) ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map {
                Triple(
                    it.getOrNull(ArtistSongSortTypeKey).toEnum(ArtistSongSortType.CREATE_DATE) to (it.getOrNull(ArtistSongSortDescendingKey)
                        ?: true),
                    it.getOrNull(HideExplicitKey) ?: false,
                    it.getOrNull(HideVideoSongsKey) ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit, hideVideoSongs) ->
                val (sortType, descending) = sortDesc
                database.artistSongs(artistId, sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    /**
     * How many of the three library sources have produced their first value.
     *
     * `stateIn` seeds each source with an empty list, which makes "the query has not run
     * yet" and "you have nothing saved" the same value downstream — so the screen had no
     * way to tell a cold open from an empty library, and drew the empty one for both.
     * Counting first emissions gives it the distinction without changing what the flows
     * emit.
     */
    private val sourcesLoaded = MutableStateFlow(0)

    private fun <T> Flow<T>.countFirstEmission(): Flow<T> {
        var counted = false
        return onEach {
            if (!counted) {
                counted = true
                sourcesLoaded.update { it + 1 }
            }
        }
    }

    val isLoading: StateFlow<Boolean> = sourcesLoaded
        .map { it < SOURCE_COUNT }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val syncAllLibrary = {
         viewModelScope.launch(Dispatchers.IO) {
             syncUtils.tryAutoSync()
         }
    }

    fun refresh() {
        // Matches HomeViewModel: a second pull while the first sync is still running used
        // to stack another full sync behind it, and whichever finished first cleared the
        // indicator for both. `performFullSyncSuspend` already runs to completion before
        // returning, so the flag only needed to be honest about re-entry.
        if (_isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isRefreshing.value = true
                syncUtils.performFullSyncSuspend()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    val topValue =
        context.dataStore.data
            .map { it.getOrNull(TopSize) ?: "50" }
            .distinctUntilChanged()
    var artists =
        database
            .artistsBookmarked(
                ArtistSortType.CREATE_DATE,
                true,
            ).countFirstEmission()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = context.dataStore.data
        .map { it.getOrNull(HideExplicitKey) ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.albumsLiked(AlbumSortType.CREATE_DATE, true).map { it.filterExplicitAlbums(hideExplicit) }
        }.countFirstEmission()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = context.dataStore.data
        .map { it.getOrNull(HideYoutubeShortsKey) ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideYoutubeShorts ->
            database.playlists(PlaylistSortType.CREATE_DATE, true).map { it.filterYoutubeShorts(hideYoutubeShorts) }
        }.countFirstEmission()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private companion object {
        /** artists, albums, playlists. */
        const val SOURCE_COUNT = 3
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            albums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            artists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null ||
                                Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
                                ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
