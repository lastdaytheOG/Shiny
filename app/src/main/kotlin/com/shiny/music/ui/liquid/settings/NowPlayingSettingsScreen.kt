package com.shiny.music.ui.liquid.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.shiny.music.constants.KeepScreenOn
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.utils.rememberPreference

/**
 * How Now Playing behaves.
 *
 * How it *looks* — the atmosphere, the glow around the cover and how much the artwork
 * moves — lives in Appearance, next to the live preview that shows it. What stays here is
 * behaviour: whether a song's motion artwork is fetched and played, the controls, and
 * the lyrics (hidden while [SettingsVisibility.LYRICS] is off).
 */
@Composable
fun NowPlayingSettingsScreen(navController: NavController) {
    var motionCovers by rememberPreference(LiquidPrefs.MotionArtwork, true)
    var showVolume by rememberPreference(LiquidPrefs.ShowVolume, true)
    var keepScreenOn by rememberPreference(KeepScreenOn, false)

    SettingsPage(title = "Now Playing", navController = navController) {
        item(key = "motion") {
            SettingsSection(
                title = "Artwork",
                footer = "Motion artwork is a short video some songs ship with. It is downloaded the " +
                    "first time a song plays and never fetched while Data Saver is on.",
            ) {
                // "Artwork, atmosphere and motion" is not repeated here: it all lives in
                // Appearance (with the live preview), and the listener found the duplicate row confusing.
                SettingsToggleRow(
                    title = "Animated covers",
                    subtitle = "Play a song's motion artwork in place of the cover when it has one",
                    checked = motionCovers,
                    onCheckedChange = { motionCovers = it },
                    divider = false,
                )
            }
        }

        item(key = "controls") {
            SettingsSection(title = "Controls") {
                SettingsToggleRow(
                    title = "Volume slider",
                    subtitle = "Show the system volume under the transport",
                    checked = showVolume,
                    onCheckedChange = { showVolume = it },
                )
                SettingsToggleRow(
                    title = "Keep the screen on",
                    subtitle = "While Now Playing is open and music is playing",
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it },
                    divider = false,
                )
            }
        }

        if (SettingsVisibility.LYRICS) {
            item(key = "lyrics") {
                SettingsSection(title = "Lyrics") {
                    SettingsNavRow(
                        title = "Lyrics",
                        subtitle = "Word timing, focus and pronunciation",
                        onClick = { navController.navigate("settings/lyrics") },
                        divider = false,
                    )
                }
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}

/**
 * Lyrics, for both places Shiny shows them: the Now Playing pane and the full-screen
 * ambient view. They are one renderer, so this is one set of controls.
 */
@Composable
fun LyricsSettingsScreen(navController: NavController) {
    var wordByWord by rememberPreference(LiquidPrefs.WordByWord, true)

    SettingsPage(title = "Lyrics", navController = navController) {
        item(key = "motion") {
            SettingsSection(
                title = "Timing",
                footer = "Songs whose provider supplies per-word timings fill as they are sung. " +
                    "The rest estimate word timing from the line, so they fill too.",
            ) {
                SettingsToggleRow(
                    title = "Fill word by word",
                    subtitle = "Light each word as it is sung rather than the whole line",
                    checked = wordByWord,
                    onCheckedChange = { wordByWord = it },
                    divider = false,
                )
            }
        }

        item(key = "language") {
            SettingsSection(
                title = "Language",
                footer = "Romanisation appears under the original line for the scripts you choose.",
            ) {
                SettingsNavRow(
                    title = "Pronunciation",
                    subtitle = "Romanise Japanese, Korean, Chinese, Cyrillic and more",
                    onClick = { navController.navigate("settings/content/romanization") },
                )
                SettingsNavRow(
                    title = "Lyrics sources",
                    subtitle = "Which providers to search, and in what order",
                    onClick = { navController.navigate("settings/content") },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}
