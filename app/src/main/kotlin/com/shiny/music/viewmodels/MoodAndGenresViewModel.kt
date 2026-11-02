package com.shiny.music.viewmodels

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.pages.MoodAndGenres
import com.shiny.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * YouTube's own browse catalogue, grouped as it publishes it ("Moods & moments", "Genres",
 * and whatever else a locale has). Fetched once; [failed] lets a screen say so instead of
 * spinning forever on a device with no network.
 */
@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor() : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

    private val _failed = MutableStateFlow(false)
    val failed = _failed.asStateFlow()

    fun load() {
        if (moodAndGenres.value != null) return
        viewModelScope.launch {
            _failed.value = false
            YouTube
                .moodAndGenres()
                .onSuccess {
                    moodAndGenres.value = it
                }.onFailure {
                    _failed.value = true
                    reportException(it)
                }
        }
    }

    val covers = mutableStateMapOf<String, List<String>>()
    private val requested = java.util.Collections.synchronizedSet(HashSet<String>())
    private val gate = Semaphore(3)

    // two playlist covers per card, fetched the first time a card shows and kept while the screen lives
    fun requestCovers(browseId: String, params: String?) {
        val key = coverKey(browseId, params)
        if (!requested.add(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            gate.withPermit {
                YouTube.browse(browseId, params)
                    .onSuccess { page ->
                        val urls = page.items.asSequence()
                            .flatMap { it.items.asSequence() }
                            .mapNotNull { it.thumbnail }
                            .distinct()
                            .take(2)
                            .toList()
                        withContext(Dispatchers.Main) { if (urls.isNotEmpty()) covers[key] = urls }
                    }
                    .onFailure { requested.remove(key) }
            }
        }
    }

    init {
        load()
    }
}

fun coverKey(browseId: String, params: String?): String = "$browseId|${params.orEmpty()}"
