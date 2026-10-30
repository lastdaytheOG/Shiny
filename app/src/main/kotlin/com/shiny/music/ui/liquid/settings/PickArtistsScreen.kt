package com.shiny.music.ui.liquid.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.shiny.music.R
import com.shiny.music.db.MusicDatabase
import com.shiny.music.home.HomeRanking
import com.shiny.music.home.HomeRemoteRepository
import com.shiny.music.intelligence.FeedbackKind
import com.shiny.music.intelligence.IntelligenceRepository
import com.shiny.music.ui.liquid.Liquid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PickableArtist(val id: String, val name: String)

@HiltViewModel
class PickArtistsViewModel @Inject constructor(
    private val intelligence: IntelligenceRepository,
    private val remote: HomeRemoteRepository,
    private val database: MusicDatabase,
) : ViewModel() {
    val chosen: StateFlow<Set<String>> = intelligence.feedback()
        .map { rows -> rows.filter { it.kind == FeedbackKind.SeedArtist.name }.mapTo(HashSet()) { it.targetId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val yours = MutableStateFlow<List<PickableArtist>>(emptyList())
    val suggested = MutableStateFlow<List<PickableArtist>>(emptyList())
    val results = MutableStateFlow<List<PickableArtist>>(emptyList())
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            remote.restore()
            yours.value = database.homeSavedArtists(limit = 12)
                .filter { HomeRanking.isStreamableId(it.artistId) && it.name.isNotBlank() }
                .map { PickableArtist(it.artistId, it.name) }
            suggested.value = remote.state.value?.charts.orEmpty()
                .flatMap { chart -> chart.songs }
                .flatMap { it.artists }
                .mapNotNull { artist -> artist.id?.let { PickableArtist(it, artist.name) } }
                .distinctBy { it.id }
                .take(20)
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            results.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            val found = YouTube.search(query.trim(), YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items.orEmpty()
            results.value = found.filterIsInstance<ArtistItem>().take(12).map { PickableArtist(it.id, it.title) }
        }
    }

    fun toggle(artist: PickableArtist, picked: Boolean) {
        if (picked) {
            intelligence.removeFeedback(FeedbackKind.SeedArtist, artist.id)
        } else {
            intelligence.setFeedback(FeedbackKind.SeedArtist, artist.id, artist.name)
            viewModelScope.launch { remote.fetchArtistSongs(listOf(artist.id)) }
        }
    }
}

@Composable
fun PickArtistsScreen(navController: NavController, viewModel: PickArtistsViewModel = hiltViewModel()) {
    val chosen by viewModel.chosen.collectAsState()
    val yours by viewModel.yours.collectAsState()
    val suggested by viewModel.suggested.collectAsState()
    val results by viewModel.results.collectAsState()
    var query by remember { mutableStateOf("") }

    @Composable
    fun ArtistRows(list: List<PickableArtist>) {
        list.forEachIndexed { index, artist ->
            val picked = artist.id in chosen
            SettingsRow(
                title = artist.name,
                trailing = if (picked) {
                    { Icon(Icons.Rounded.Check, null, tint = Liquid.colors.accent) }
                } else {
                    null
                },
                divider = index < list.lastIndex,
                onClick = { viewModel.toggle(artist, picked) },
            )
        }
    }

    SettingsPage(title = stringResource(R.string.pick_artists_page), navController = navController) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.search(it)
                },
                placeholder = { Text(stringResource(R.string.pick_artists_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsGutter, vertical = 8.dp),
            )
        }
        if (results.isNotEmpty()) {
            item(key = "results") {
                SettingsSection(title = stringResource(R.string.pick_artists_results)) { ArtistRows(results) }
            }
        }
        if (yours.isNotEmpty()) {
            item(key = "yours") {
                SettingsSection(title = stringResource(R.string.pick_artists_yours)) { ArtistRows(yours) }
            }
        }
        if (suggested.isNotEmpty()) {
            item(key = "suggested") {
                SettingsSection(title = stringResource(R.string.pick_artists_suggested)) { ArtistRows(suggested) }
            }
        }
        item(key = "tail") { SettingsTailSpace() }
    }
}
