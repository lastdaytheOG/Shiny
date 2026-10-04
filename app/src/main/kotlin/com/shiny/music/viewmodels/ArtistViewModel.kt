

package com.shiny.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ArtistPage
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.HideYoutubeShortsKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.extensions.filterExplicit
import com.shiny.music.extensions.filterExplicitAlbums
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import com.shiny.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.shiny.music.extensions.filterVideoSongs as filterVideoSongsLocal
import com.shiny.music.artistvideo.ArtistVideoCanvasProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.shiny.music.utils.getOrNull

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)
    
    private val _artistVideoUrl = MutableStateFlow<String?>(null)
    val artistVideoUrl: StateFlow<String?> = _artistVideoUrl

    private val _artistVideoSong = MutableStateFlow<com.music.innertube.models.SongItem?>(null)
    val artistVideoSong: StateFlow<com.music.innertube.models.SongItem?> = _artistVideoSong
    
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { (it.getOrNull(HideExplicitKey) ?: false) to (it.getOrNull(HideVideoSongsKey) ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (hideExplicit, hideVideoSongs) ->
            database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit).filterVideoSongsLocal(hideVideoSongs) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it.getOrNull(HideExplicitKey) ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        
        viewModelScope.launch {
            context.dataStore.data
                .map {
                    Triple(
                        it.getOrNull(HideExplicitKey) ?: false,
                        it.getOrNull(HideVideoSongsKey) ?: false,
                        it.getOrNull(HideYoutubeShortsKey) ?: false
                    )
                }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    private companion object {
        /** How long an artist page is reused; artist pages change slowly. */
        const val PAGE_TTL_MS = 10 * 60_000L

        /** The last few artist pages, unfiltered (the filters apply on every use). */
        val recentPages = android.util.LruCache<String, Pair<Long, ArtistPage>>(12)
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            // Back on an artist opened a moment ago: the page YouTube sent then, not a spinner.
            val cached = recentPages.get(artistId)
                ?.takeIf { android.os.SystemClock.elapsedRealtime() - it.first < PAGE_TTL_MS }
                ?.second
            val result = cached?.let { Result.success(it) }
                ?: YouTube.artist(artistId).onSuccess {
                    recentPages.put(artistId, android.os.SystemClock.elapsedRealtime() to it)
                }
            result
                .onSuccess { page ->
                    val filteredSections = page.sections
                        .map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                        }
                        .filter { section -> section.items.isNotEmpty() }

                    artistPage = page.copy(sections = filteredSections)
                    
                    
                    val topSongsSection = page.sections.find { it.items.firstOrNull() is com.music.innertube.models.SongItem }
                    topSongsSection?.items?.forEach { item ->
                        if (item is com.music.innertube.models.SongItem) {
                            val canvas = ArtistVideoCanvasProvider.getBySongArtist(
                                song = item.title,
                                artist = page.artist?.title ?: ""
                            )
                            if (canvas?.preferredAnimationUrl != null) {
                                _artistVideoUrl.value = canvas.preferredAnimationUrl
                                _artistVideoSong.value = item
                                return@forEach
                            }
                        }
                    }
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
