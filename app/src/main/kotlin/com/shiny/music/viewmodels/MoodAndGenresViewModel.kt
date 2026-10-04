package com.shiny.music.viewmodels

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

    init {
        load()
    }
}
