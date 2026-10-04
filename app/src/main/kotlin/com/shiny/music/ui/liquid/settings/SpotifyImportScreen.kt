package com.shiny.music.ui.liquid.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.spotifyimport.SpotifyImportProgressUi
import com.shiny.music.spotifyimport.SpotifyImportSourceSummaryUi
import com.shiny.music.spotifyimport.SpotifyImportSourceUi
import com.shiny.music.spotifyimport.SpotifyImportSummaryUi
import com.shiny.music.spotifyimport.SpotifyImportViewModel
import com.shiny.music.spotifyimport.SpotifyPlaylistLink
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography

/**
 * Spotify: who you are connected as, what there is to bring over, and how an import is going.
 *
 * One page of plain rows. An import's progress, its result and anything that went wrong show
 * as sections in place, so nothing covers the list while it runs. Logging in happens on a
 * page of its own ([SpotifyLoginPage]); [openLogin] opens it straight away (from Account).
 */
@Composable
fun SpotifyImportScreen(
    navController: NavController,
    openLogin: Boolean = false,
    viewModel: SpotifyImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var loggingIn by rememberSaveable { mutableStateOf(openLogin) }
    var link by rememberSaveable { mutableStateOf("") }
    val importing = state.progress != null
    val busy = importing || state.isLoading
    val selectedCount = state.selectedSourceIds.size

    val addLink: () -> Unit = {
        viewModel.addPlaylistByUrl(link)
        // A link that could not be read stays in the field to be corrected.
        if (SpotifyPlaylistLink.idOf(link) != null) link = ""
    }
    val check: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Selected",
            tint = Liquid.colors.accent,
            modifier = Modifier.size(20.dp),
        )
    }

    SettingsPage(title = "Spotify", navController = navController) {
        if (state.isAuthenticated) {
            item(key = "account") {
                SettingsSection {
                    SettingsValueRow(
                        title = state.accountName.ifBlank { "Spotify account" },
                        value = "Connected",
                        divider = false,
                    )
                }
            }
        } else {
            item(key = "intro") {
                SettingsIntro("Bring your Spotify playlists and liked songs into your Shiny library.")
            }
            item(key = "login") {
                SettingsSection {
                    SettingsNavRow(
                        title = if (state.isLoading) "Connecting…" else "Log in to Spotify",
                        onClick = { loggingIn = true },
                        divider = false,
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            item(key = "error") {
                SettingsSection(title = "That didn't work") {
                    SettingsNote(message)
                    SettingsActionRow(title = "OK", onClick = viewModel::dismissError, divider = false)
                }
            }
        }

        state.progress?.let { progress ->
            item(key = "progress") {
                SettingsSection(title = "Importing") {
                    SettingsValueRow(
                        title = progress.sourceTitle,
                        subtitle = progressLine(progress),
                        value = "${progress.percent}%",
                    )
                    if (state.waitingForNetwork) {
                        SettingsRow(
                            title = "Waiting for the network",
                            subtitle = "The import carries on by itself once you are back online",
                        )
                    }
                    SettingsActionRow(
                        title = "Stop importing",
                        onClick = viewModel::cancelImport,
                        destructive = true,
                        divider = false,
                    )
                }
            }
        }

        state.summary?.let { summary ->
            item(key = "summary") {
                SettingsSection(title = "Imported", footer = summaryFooter(summary)) {
                    summary.sources.forEach { source ->
                        SettingsValueRow(title = source.title, value = summaryValue(source))
                    }
                    SettingsActionRow(title = "Done", onClick = viewModel::dismissSummary, divider = false)
                }
            }
        }

        if (state.isAuthenticated) {
            item(key = "sources") {
                SettingsSection(title = "Bring over") {
                    if (state.sources.isEmpty()) {
                        SettingsRow(
                            title = if (state.isLoading) "Loading your library…" else "Nothing to bring over yet",
                            enabled = false,
                            divider = false,
                        )
                    }
                    state.sources.forEachIndexed { index, source ->
                        SettingsRow(
                            title = source.title,
                            subtitle = sourceSubtitle(source),
                            trailing = if (source.id in state.selectedSourceIds) check else null,
                            enabled = !importing,
                            divider = index != state.sources.lastIndex,
                            onClick = { viewModel.toggleSource(source.id) },
                        )
                    }
                }
            }
            item(key = "actions") {
                val allSelected = state.hasSources && selectedCount == state.sources.size
                SettingsSection {
                    SettingsActionRow(
                        title = when {
                            importing -> "Importing…"
                            selectedCount == 0 -> "Import"
                            else -> "Import $selectedCount selected"
                        },
                        enabled = state.canImport,
                        onClick = viewModel::importSelectedSources,
                    )
                    SettingsActionRow(
                        title = if (allSelected) "Select none" else "Select all",
                        enabled = state.hasSources && !importing,
                        onClick = if (allSelected) viewModel::clearSelection else viewModel::selectAllSources,
                    )
                    SettingsActionRow(
                        title = if (state.isLoading) "Refreshing…" else "Refresh",
                        enabled = !busy,
                        onClick = { viewModel.loadSources() },
                        divider = false,
                    )
                }
            }
            item(key = "link") {
                SettingsSection(
                    title = "Add by link",
                    footer = "For a playlist that is not in your library. In Spotify, open it, choose Share, then Copy link.",
                ) {
                    LinkField(value = link, onValueChange = { link = it }, onDone = addLink)
                    SettingsActionRow(
                        title = "Add playlist",
                        enabled = link.isNotBlank() && !busy,
                        onClick = addLink,
                        divider = false,
                    )
                }
            }
            item(key = "logout") {
                SettingsSection(footer = "Logging out also takes the Spotify mixes off Home.") {
                    SettingsActionRow(
                        title = "Log out of Spotify",
                        onClick = viewModel::logout,
                        destructive = true,
                        enabled = !importing,
                        divider = false,
                    )
                }
            }
        }
    }

    if (loggingIn) {
        SpotifyLoginPage(
            onDismiss = { loggingIn = false },
            onCookiesCaptured = { spDc, spKey ->
                loggingIn = false
                viewModel.connectWithCookies(spDc, spKey)
            },
        )
    }
}

/** One line of type to paste a link into, set like the rows around it. */
@Composable
private fun LinkField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = Liquid.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LiquidTypography.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsGutter, vertical = 14.dp),
        decorationBox = { field ->
            if (value.isEmpty()) {
                Text(
                    text = "open.spotify.com/playlist/…",
                    style = LiquidTypography.body,
                    color = colors.tertiaryLabel,
                    maxLines = 1,
                )
            }
            field()
        },
    )
}

/** What a source is and how big: "Playlist · 42 songs", or whichever of the two is known. */
internal fun sourceSubtitle(source: SpotifyImportSourceUi): String =
    listOfNotNull(source.subtitle.takeIf { it.isNotBlank() }, source.trackCount?.let(::songCount))
        .joinToString(" · ")

/** Where a running import is: which source of how many, and how far through its songs. */
internal fun progressLine(progress: SpotifyImportProgressUi): String {
    val songs = "${progress.matchedTracks} of ${songCount(progress.totalTracks)}"
    if (progress.totalSources <= 1) return songs
    val current = (progress.completedSources + 1).coerceAtMost(progress.totalSources)
    return "$current of ${progress.totalSources} · $songs"
}

internal fun summaryValue(source: SpotifyImportSourceSummaryUi): String =
    if (source.failed) "Couldn't be read" else "${source.importedTracks} of ${source.totalTracks}"

/** Says what happened to the songs that are missing from the counts, when any are. */
internal fun summaryFooter(summary: SpotifyImportSummaryUi): String? {
    val missing = summary.failedTracks
    if (missing <= 0) return null
    return "${songCount(missing)} could not be matched and ${if (missing == 1) "was" else "were"} left out."
}
