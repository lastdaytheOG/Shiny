/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

/*
 * Modifications Copyright (C) 2026 Shiny Project.
 * Modified from the original work by the Shiny Project in 2026; the git history records
 * each change and its date. Distributed under GPL-3.0 as part of Shiny.
 */

package com.shiny.music.spotifyimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.shiny.music.constants.SpotifyAccessTokenExpiresAtKey
import com.shiny.music.constants.SpotifyAccessTokenKey
import com.shiny.music.constants.SpotifyAccountAvatarUrlKey
import com.shiny.music.constants.SpotifyAccountNameKey
import com.shiny.music.constants.SpotifySpDcKey
import com.shiny.music.utils.PreferencesSnapshot
import com.shiny.music.utils.reportException
import javax.inject.Inject
import kotlin.jvm.Volatile

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
    private val repository: SpotifyImportRepository,
    private val mixes: SpotifyMixesRepository,
) : ViewModel() {
    // Declared before the state it seeds, so the seed is not overwritten by this initializer.
    @Volatile
    private var sources: List<SpotifyImportSource> = repository.cachedSources.orEmpty()

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<SpotifyImportUiState> = _uiState.asStateFlow()

    private val sourcesLock = Any()
    private var importJob: Job? = null

    /**
     * Changes the known sources and the page's state in one step, so the list on screen and
     * the list an import reads never disagree. [change] gets the sources as they are and
     * returns the new ones with the edit to the page that goes with them.
     */
    private fun changeSources(
        change: (List<SpotifyImportSource>) -> Pair<List<SpotifyImportSource>, (SpotifyImportUiState) -> SpotifyImportUiState>,
    ) {
        synchronized(sourcesLock) {
            val (next, edit) = change(sources)
            sources = next
            _uiState.update(edit)
        }
    }

    init {
        restoreSession()
        viewModelScope.launch {
            repository.waitingForNetwork.collect { waiting ->
                _uiState.update { it.copy(waitingForNetwork = waiting) }
            }
        }
    }

    /**
     * What the page can say before any request: the saved login, name and picture, and the
     * sources from the last visit in this process. The saved login is what "connected"
     * means — Account shows Connected from the same key — so the page opens connected and
     * the token refresh and library load run behind it. The page used to open signed out
     * with a spinner and say Connected only once Spotify had answered.
     */
    private fun initialState(): SpotifyImportUiState {
        val token = PreferencesSnapshot.read(SpotifyAccessTokenKey, "")
        val expiresAt = PreferencesSnapshot.read(SpotifyAccessTokenExpiresAtKey, 0L)
        val connected = PreferencesSnapshot.read(SpotifySpDcKey, "").isNotBlank() ||
            (token.isNotBlank() && expiresAt > System.currentTimeMillis())
        if (!connected) {
            // Signed out, as far as the preferences can tell; until they are read, say nothing.
            return SpotifyImportUiState(isLoading = !PreferencesSnapshot.isLoaded)
        }
        return SpotifyImportUiState(
            isAuthenticated = true,
            accountName = PreferencesSnapshot.read(SpotifyAccountNameKey, ""),
            accountAvatarUrl = PreferencesSnapshot.read(SpotifyAccountAvatarUrlKey, "").ifBlank { null },
            isLoading = true,
            sources = sources.map(SpotifyImportSource::toUi),
            selectedSourceIds = sources.mapTo(LinkedHashSet()) { it.id },
        )
    }

    fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.restoreSession() }
                .onSuccess { session ->
                    if (session.isAuthenticated) {
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                accountName = session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                            )
                        }
                        loadSources(quiet = true)
                    } else {
                        changeSources {
                            emptyList<SpotifyImportSource>() to { state ->
                                state.copy(
                                    isAuthenticated = false,
                                    accountName = "",
                                    accountAvatarUrl = null,
                                    sources = emptyList(),
                                    selectedSourceIds = emptySet(),
                                    isLoading = false,
                                )
                            }
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isAuthenticated = false,
                            isLoading = false,
                            errorMessage = error.message,
                        )
                    }
                }
        }
    }

    fun connectWithCookies(
        spDc: String,
        spKey: String,
    ) {
        if (spDc.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.connectWithCookies(spDc = spDc, spKey = spKey) }
                .onSuccess { session ->
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            accountName = session.accountName,
                            accountAvatarUrl = session.accountAvatarUrl,
                            isLoading = false,
                        )
                    }
                    loadSources()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message,
                        )
                    }
                }
        }
    }

    /**
     * Refreshes the list of what can be imported. [quiet] is the refresh the page starts on
     * its own when it opens: a failure there leaves what is shown instead of opening an
     * error over the page; the Refresh row reports it.
     */
    fun loadSources(quiet: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { repository.loadSources() }
                .onSuccess { loadedSources ->
                    changeSources { previous ->
                        val previousIds = previous.mapTo(HashSet()) { it.id }
                        loadedSources to { state ->
                            // New sources arrive selected; ones already shown keep the choice
                            // made while this refresh ran.
                            val selectedIds = loadedSources
                                .filter { it.id !in previousIds || it.id in state.selectedSourceIds }
                                .mapTo(LinkedHashSet()) { it.id }
                            state.copy(
                                isAuthenticated = true,
                                sources = loadedSources.map(SpotifyImportSource::toUi),
                                selectedSourceIds = selectedIds,
                                isLoading = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (quiet) null else error.message,
                        )
                    }
                }
        }
    }

    /**
     * Adds one playlist from a link to it, for a playlist that is not in the account's
     * library. It joins the list selected, ready to import; one already listed is refreshed
     * in place.
     */
    fun addPlaylistByUrl(url: String) {
        if (url.isBlank()) return
        // A link this app can read is passed on in its plain form; anything else goes as
        // typed, and the repository says what is wrong with it.
        val link = SpotifyPlaylistLink.canonical(url) ?: url.trim()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val playlist = repository.addPlaylistByUrl(link)
                changeSources { known ->
                    val listed = known.any { it.id == playlist.id }
                    val next = if (listed) known.map { if (it.id == playlist.id) playlist else it } else known + playlist
                    next to { state ->
                        state.copy(
                            sources = next.map(SpotifyImportSource::toUi),
                            selectedSourceIds = state.selectedSourceIds + playlist.id,
                            isLoading = false,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportException(error)
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }

    fun toggleSource(sourceId: String) {
        _uiState.update { state ->
            val selected =
                if (sourceId in state.selectedSourceIds) {
                    state.selectedSourceIds - sourceId
                } else {
                    state.selectedSourceIds + sourceId
                }
            state.copy(selectedSourceIds = selected)
        }
    }

    fun selectAllSources() {
        _uiState.update { state ->
            state.copy(selectedSourceIds = state.sources.mapTo(LinkedHashSet()) { it.id })
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedSourceIds = emptySet()) }
    }

    fun logout() {
        if (uiState.value.progress != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.logout()
                // The mixes and top tracks go with the account, off Home and out of its taste.
                mixes.clear()
            }
                .onSuccess {
                    changeSources {
                        emptyList<SpotifyImportSource>() to { SpotifyImportUiState() }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message,
                        )
                    }
                }
        }
    }

    fun importSelectedSources() {
        val selectedIds = uiState.value.selectedSourceIds
        if (selectedIds.isEmpty() || importJob?.isActive == true || uiState.value.progress != null) return
        val selectedSources = sources.filter { it.id in selectedIds }
        if (selectedSources.isEmpty()) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(summary = null, errorMessage = null) }
            try {
                val summary =
                    repository.importSources(selectedSources) { progress ->
                        _uiState.update { it.copy(progress = progress) }
                    }
                _uiState.update {
                    it.copy(
                        progress = null,
                        summary = summary,
                    )
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(progress = null) }
                throw error
            } catch (error: Throwable) {
                reportException(error)
                _uiState.update {
                    it.copy(
                        progress = null,
                        errorMessage = error.message,
                    )
                }
            } finally {
                if (importJob === coroutineContext[Job]) {
                    importJob = null
                }
            }
        }
        importJob = job
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _uiState.update { it.copy(progress = null) }
    }

    fun dismissSummary() {
        _uiState.update { it.copy(summary = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

private fun SpotifyImportSource.toUi(): SpotifyImportSourceUi =
    SpotifyImportSourceUi(
        id = id,
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        trackCount = trackCount,
        type = type,
    )
