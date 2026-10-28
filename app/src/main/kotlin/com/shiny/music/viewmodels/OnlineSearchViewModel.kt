

package com.shiny.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchResult
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.HideYoutubeShortsKey
import com.shiny.music.models.ItemsPage
import com.shiny.music.search.RankedSummary
import com.shiny.music.search.SearchResults
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.get
import com.shiny.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summary by mutableStateOf<RankedSummary?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()
    private val corrections = mutableMapOf<String, String?>()
    private val pagesRead = mutableMapOf<String, Int>()
    private var loadingMore: Job? = null

    init {
        viewModelScope.launch {
            filter.collect { filter ->
                if (filter == null) {
                    if (summary == null) {
                        YouTube
                            .searchSummary(query)
                            .onSuccess {
                                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                                val page = it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                                summary = withContext(Dispatchers.Default) { SearchResults.summary(query, page) }
                                    .also { ranked -> SearchResults.log(ranked.report) }
                            }.onFailure {
                                reportException(it)
                            }
                    }
                } else {
                    if (viewStateMap[filter.value] == null) {
                        YouTube
                            .search(query, filter)
                            .onSuccess { result ->
                                corrections[filter.value] = result.correctedQuery
                                viewStateMap[filter.value] = collect(filter, emptyList(), result)
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val filter = filter.value ?: return
        if (loadingMore?.isActive == true) return
        loadingMore = viewModelScope.launch {
            val viewState = viewStateMap[filter.value] ?: return@launch
            val continuation = viewState.continuation ?: return@launch
            val searchResult = YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
            viewStateMap[filter.value] = collect(filter, viewState.items, searchResult)
        }
    }

    private suspend fun collect(filter: YouTube.SearchFilter, shown: List<YTItem>, first: SearchResult): ItemsPage {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        var items = shown
        var result = first
        var barren = 0
        while (true) {
            val offered = result.items
                .filterExplicit(hideExplicit)
                .let { if (filter.value == YouTube.SearchFilter.FILTER_VIDEO.value) it else it.filterVideoSongs(hideVideoSongs) }
                .filterYoutubeShorts(hideYoutubeShorts)
            val page = (pagesRead[filter.value] ?: 0) + 1
            pagesRead[filter.value] = page
            val current = items
            val ranked = withContext(Dispatchers.Default) {
                SearchResults.page(query, corrections[filter.value], filter, offered, current, page)
            }
            SearchResults.log(ranked.report)
            items = items + ranked.items
            val next = result.continuation
            if (ranked.items.isNotEmpty() || next == null) return ItemsPage(items, next)
            if (++barren >= SearchResults.MAX_BARREN_PAGES) return ItemsPage(items, null)
            result = YouTube.searchContinuation(next).getOrNull() ?: return ItemsPage(items, next)
        }
    }
}
