package com.shiny.music.ui.liquid.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.shiny.music.constants.GridItemSize
import com.shiny.music.constants.GridItemsSizeKey
import com.shiny.music.constants.ListenTogetherInTopBarKey
import com.shiny.music.constants.ShowSpeedDialKey
import com.shiny.music.constants.SwipeToSongKey
import com.shiny.music.constants.TopSize
import com.shiny.music.constants.DefaultOpenTabKey
import com.shiny.music.constants.HomeChartsCountryKey
import com.shiny.music.constants.SpotifyHomeMixesKey
import com.shiny.music.constants.SpotifySpDcKey
import com.shiny.music.R
import androidx.compose.ui.res.stringResource
import com.shiny.music.home.HomeCharts
import com.shiny.music.ui.screens.settings.NavigationTab
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import kotlin.math.roundToInt

/** The tab bar, what Home shows, and the gestures that act on a song row. */
@Composable
fun LibraryHomeSettingsScreen(navController: NavController) {
    var defaultTab by rememberEnumPreference(DefaultOpenTabKey, NavigationTab.HOME)
    var togetherInLibrary by rememberPreference(ListenTogetherInTopBarKey, true)
    var pinned by rememberPreference(ShowSpeedDialKey, true)
    var gridSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.SMALL)
    var swipeToQueue by rememberPreference(SwipeToSongKey, true)
    var topLength by rememberPreference(TopSize, "50")
    val chartsSetting by rememberPreference(HomeChartsCountryKey, HomeCharts.AUTO)
    var spotifyMixes by rememberPreference(SpotifyHomeMixesKey, true)
    val spotifySession by rememberPreference(SpotifySpDcKey, "")
    val context = LocalContext.current
    val detectedCountry = remember { HomeCharts.detectCountry(context) }

    SettingsPage(title = "Library & Home", navController = navController) {
        item(key = "start") {
            SettingsSection(title = "Opening") {
                SettingsChoiceRow(
                    title = "Open Shiny on",
                    options = listOf(NavigationTab.HOME, NavigationTab.SEARCH, NavigationTab.LIBRARY),
                    selected = defaultTab,
                    label = {
                        when (it) {
                            NavigationTab.HOME -> "Home"
                            NavigationTab.SEARCH -> "Search"
                            NavigationTab.LIBRARY -> "Library"
                        }
                    },
                    onSelect = { defaultTab = it },
                    divider = false,
                )
            }
        }

        item(key = "tabs") {
            SettingsSection(
                title = "Tab bar",
                footer = "Listen Together can have the fourth tab to itself, or sit inside Library.",
            ) {
                SettingsToggleRow(
                    title = "Listen Together tab",
                    subtitle = "Give Together its own tab instead of Library",
                    checked = !togetherInLibrary,
                    onCheckedChange = { togetherInLibrary = !it },
                    divider = false,
                )
            }
        }

        item(key = "home") {
            SettingsSection(title = "Home") {
                SettingsToggleRow(
                    title = "Pinned shelf",
                    subtitle = "Your pins first in Lately, at the top of Home",
                    checked = pinned,
                    onCheckedChange = { pinned = it },
                )
                SettingsNavRow(
                    title = "Charts",
                    subtitle = if (chartsSetting == HomeCharts.AUTO) "From your location" else "Which country's YouTube chart Home shows",
                    value = homeChartsLabel(chartsSetting, detectedCountry),
                    onClick = { navController.navigate("settings/library/charts") },
                )
                if (spotifySession.isNotBlank()) {
                    SettingsToggleRow(
                        title = stringResource(R.string.spotify_home_mixes),
                        subtitle = stringResource(R.string.spotify_home_mixes_desc),
                        checked = spotifyMixes,
                        onCheckedChange = { spotifyMixes = it },
                    )
                } else {
                    SettingsNavRow(
                        title = stringResource(R.string.spotify_home_mixes),
                        subtitle = "Connect Spotify to bring your mixes to Home",
                        value = "Connect",
                        onClick = { navController.navigate("settings/spotify_import") },
                    )
                }
                SettingsSliderRow(
                    title = "Top list length",
                    subtitle = "How far your most-played list runs",
                    value = topLength.toFloatOrNull() ?: 50f,
                    onValueChange = { topLength = it.roundToInt().toString() },
                    valueRange = 5f..100f,
                    steps = 18,
                    valueLabel = "$topLength songs",
                    divider = false,
                )
            }
        }

        item(key = "browsing") {
            SettingsSection(title = "Browsing") {
                SettingsChoiceRow(
                    title = "Grid size",
                    options = listOf(GridItemSize.SMALL, GridItemSize.BIG),
                    selected = gridSize,
                    label = { if (it == GridItemSize.BIG) "Large" else "Small" },
                    onSelect = { gridSize = it },
                )
                SettingsToggleRow(
                    title = "Swipe a song row",
                    subtitle = "Swipe left to queue a song, right to play it next",
                    checked = swipeToQueue,
                    onCheckedChange = { swipeToQueue = it },
                    divider = false,
                )
            }
        }

        item(key = "device") {
            SettingsSection(title = "On this device") {
                SettingsNavRow(
                    title = "Music sources",
                    subtitle = "What counts as music, and which folders to include",
                    onClick = { navController.navigate("settings/library/sources") },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}
