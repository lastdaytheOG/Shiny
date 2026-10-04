package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiny.music.constants.LocalSongsHiddenIdsKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.localmedia.LocalSongScanConfig
import com.shiny.music.localmedia.LocalSongScanSummary
import com.shiny.music.localmedia.LocalSongScanner
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val localSongScanner: LocalSongScanner,
) : ViewModel() {
    private val _scanState = MutableStateFlow(LocalSongsScanState())
    val scanState = _scanState.asStateFlow()

    /** Songs a scan left out but kept for their history; not part of On This Device. */
    private val hiddenIds = context.dataStore.data
        .map { it[LocalSongsHiddenIdsKey].orEmpty() }
        .distinctUntilChanged()

    val songs = combine(database.localSongs(), hiddenIds) { songs, hidden ->
        if (hidden.isEmpty()) songs else songs.filterNot { it.id in hidden }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun scanDevice(scanConfig: LocalSongScanConfig = LocalSongScanConfig()) {
        scan(scanConfig, automatic = false)
    }

    /**
     * Rescans only if the device's audio or the source settings changed since the last
     * scan — the check is a light pass over the audio index, so opening the page when
     * nothing changed costs next to nothing.
     */
    fun refreshIfStale(scanConfig: LocalSongScanConfig) {
        if (_scanState.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            val stale = runCatching { localSongScanner.needsScan(scanConfig) }.getOrDefault(false)
            if (stale) scan(scanConfig, automatic = true)
        }
    }

    private fun scan(scanConfig: LocalSongScanConfig, automatic: Boolean) {
        if (_scanState.value.isScanning) return
        // Marked before launching, so a second request in the same moment is turned away.
        _scanState.value = _scanState.value.copy(isScanning = true, errorMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { localSongScanner.scanDevice(scanConfig, automatic) }
                .onSuccess { summary ->
                    _scanState.value = LocalSongsScanState(
                        isScanning = false,
                        lastSummary = summary,
                        errorMessage = null,
                    )
                }
                .onFailure { error ->
                    reportException(error)
                    _scanState.value = _scanState.value.copy(
                        isScanning = false,
                        errorMessage = error.message,
                    )
                }
        }
    }
}

data class LocalSongsScanState(
    val isScanning: Boolean = false,
    val lastSummary: LocalSongScanSummary? = null,
    val errorMessage: String? = null,
)
