package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import com.shiny.music.constants.AlbumSortType
import com.shiny.music.constants.ArtistSortType
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.PlaylistSortType
import com.shiny.music.constants.SongSortType
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.LibraryCounts
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.Song
import com.shiny.music.db.entities.SpeedDialItem
import com.shiny.music.di.DownloadCache
import com.shiny.music.di.PlayerCache
import com.shiny.music.extensions.filterExplicit
import com.shiny.music.extensions.filterVideoSongs
import com.shiny.music.spotifyimport.SpotifyImportRepository
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import java.time.LocalDateTime
import javax.inject.Inject

data class LibraryCovers(
    val songs: List<String> = emptyList(),
    val liked: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val playlists: List<String> = emptyList(),
    val downloaded: List<String> = emptyList(),
    val device: List<String> = emptyList(),
)

sealed interface LibraryPin {
    val key: String
    val thumbnail: String?

    data class Saved(val item: SpeedDialItem) : LibraryPin {
        override val key get() = "pin_${item.id}"
        override val thumbnail get() = item.thumbnailUrl
    }

    data class PinnedPlaylist(val playlist: Playlist) : LibraryPin {
        override val key get() = "pl_${playlist.id}"
        override val thumbnail get() = playlist.thumbnails.firstOrNull() ?: playlist.thumbnailUrl
    }
}

sealed interface LibraryHit {
    val key: String

    data class SongHit(val song: Song) : LibraryHit {
        override val key get() = "s_${song.id}"
    }

    data class ArtistHit(val artist: Artist) : LibraryHit {
        override val key get() = "ar_${artist.id}"
    }

    data class AlbumHit(val album: Album) : LibraryHit {
        override val key get() = "al_${album.id}"
    }

    data class PlaylistHit(val playlist: Playlist) : LibraryHit {
        override val key get() = "pl_${playlist.id}"
    }
}

data class ImportedService(
    val name: String,
    val playlists: List<Playlist>,
) {
    val songCount: Int get() = playlists.sumOf { it.songCount }
    val covers: List<String> get() = playlists.flatMap { it.thumbnails }.distinct().take(2)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache,
    private val downloadFolderStore: com.shiny.music.downloads.DownloadFolderStore,
) : ViewModel() {
    val scroll = ListScroll()

    private val hideFlags = context.dataStore.data
        .map { (it[HideExplicitKey] ?: false) to (it[HideVideoSongsKey] ?: false) }
        .distinctUntilChanged()

    val counts: StateFlow<LibraryCounts> = hideFlags
        .flatMapLatest { (explicit, videos) -> database.libraryCounts(explicit, videos) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCounts())

    private val playlists = database.playlists(PlaylistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val dao = database.libraryDao

    private val libraryAlbums = database.albums(AlbumSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val libraryArtists = database.artists(ArtistSortType.NAME, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    val covers: StateFlow<LibraryCovers> = combine(
        combine(dao.everySongCovers(4), dao.likedCovers(4)) { s, l -> Triple(s, l, Unit) },
        combine(libraryArtists, dao.downloadedCovers(4), dao.deviceCovers(4)) { ar, d, dv -> Triple(ar, d, dv) },
        combine(playlists, libraryAlbums) { lists, albums -> lists to albums },
    ) { (songs, liked, _), (artists, downloaded, device), (lists, albums) ->
        LibraryCovers(
            songs = songs.distinct(),
            liked = liked.distinct(),
            albums = albums.mapNotNull { it.thumbnailUrl }.take(2),
            artists = artists.mapNotNull { it.thumbnailUrl }.take(2),
            playlists = lists.flatMap { it.thumbnails.take(1) }.take(2),
            downloaded = downloaded.distinct(),
            device = device.distinct(),
        )
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryCovers())

    val songCount: StateFlow<Int> = hideFlags
        .flatMapLatest { (explicit, videos) -> dao.everySongCount(explicit, videos) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val albumCount: StateFlow<Int> = libraryAlbums.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val artistCount: StateFlow<Int> = libraryArtists.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recentSongs: StateFlow<List<Song>> = combine(dao.everySong(), hideFlags) { songs, (explicit, videos) ->
        songs.filterExplicit(explicit)
            .filterVideoSongs(videos)
            .sortedByDescending { it.song.inLibrary ?: it.song.dateDownload ?: LocalDateTime.MIN }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deviceCount: StateFlow<Int> = dao.deviceCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pins: StateFlow<List<LibraryPin>> = combine(database.speedDialDao.getAll(), playlists) { saved, lists ->
        val pinnedPlaylists = lists.filter { it.playlist.isPinned }.map { LibraryPin.PinnedPlaylist(it) }
        val savedIds = pinnedPlaylists.map { it.playlist.id }.toSet()
        (pinnedPlaylists + saved.filter { it.id !in savedIds }.map { LibraryPin.Saved(it) }).take(9)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val imported: StateFlow<List<ImportedService>> = playlists.map { lists ->
        val spotify = lists.filter {
            it.id.startsWith("SPOTIFY_PLAYLIST_") || it.id == SpotifyImportRepository.LIKED_SONGS_PLAYLIST_ID
        }
        val youtube = lists.filter { it.playlist.browseId != null && !it.id.startsWith("SPOTIFY_") }
        listOf(ImportedService("Spotify", spotify), ImportedService("YouTube Music", youtube))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val localSongs: StateFlow<List<Song>?> = combine(
        database.downloadedSongs(SongSortType.CREATE_DATE, true),
        database.localSongs(),
        hideFlags,
    ) { downloaded, device, (explicit, videos) ->
        (downloaded + device).distinctBy { it.id }.filterExplicit(explicit).filterVideoSongs(videos)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    // the player cache has no change events, so this looks again every few seconds, but only while the cached tab is open
    val cachedSongs: StateFlow<List<Song>?> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadCached())
            delay(5_000)
        }
    }.distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)

    val query = MutableStateFlow("")

    // waits a beat after typing so every key press doesn't filter the whole library again
    val hits: StateFlow<List<LibraryHit>> = query
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce(120)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else search(q) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    private fun search(q: String): Flow<List<LibraryHit>> = combine(
        dao.everySong(),
        database.artists(ArtistSortType.NAME, false),
        database.albums(AlbumSortType.NAME, false),
        playlists,
    ) { songs, artists, albums, lists ->
        buildList {
            songs.asSequence()
                .filter { s -> s.title.contains(q, true) || s.artists.any { it.name.contains(q, true) } }
                .take(12).forEach { add(LibraryHit.SongHit(it)) }
            artists.asSequence().filter { it.title.contains(q, true) }.take(6).forEach { add(LibraryHit.ArtistHit(it)) }
            albums.asSequence().filter { it.title.contains(q, true) }.take(6).forEach { add(LibraryHit.AlbumHit(it)) }
            lists.asSequence().filter { it.title.contains(q, true) }.take(6).forEach { add(LibraryHit.PlaylistHit(it)) }
        }
    }

    // same rules as the cache page: a song counts once its whole stream is on disk, downloads don't
    private suspend fun loadCached(): List<Song> {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideos = context.dataStore.get(HideVideoSongsKey, false)
        val downloaded = downloadCache.keys.toSet() + downloadFolderStore.entries.value.keys
        val ids = playerCache.keys.toSet().subtract(downloaded)
        if (ids.isEmpty()) return emptyList()
        val complete = database.getSongsByIds(ids.toList()).filter {
            val length = it.format?.contentLength
            length != null && playerCache.isCached(it.song.id, 0, length)
        }
        val stamped = complete.filter { it.song.dateDownload == null }
        if (stamped.isNotEmpty()) {
            database.query { stamped.forEach { update(it.song.copy(dateDownload = LocalDateTime.now())) } }
        }
        return complete
            .sortedByDescending { it.song.dateDownload ?: LocalDateTime.now() }
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideos)
    }
}
