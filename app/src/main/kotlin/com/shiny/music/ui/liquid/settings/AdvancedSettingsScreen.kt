package com.shiny.music.ui.liquid.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.shiny.music.BuildConfig
import com.shiny.music.constants.DisableScreenshotKey
import com.shiny.music.constants.PlaybackEngine
import com.shiny.music.constants.PlaybackEngineKey
import com.shiny.music.utils.YTPlayerUtils
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference

/**
 * The technical shelf: things worth having when something is wrong, kept away from the
 * settings people use.
 *
 * Endpoint diagnostics (Service status) are for whoever is debugging Shiny, not for the
 * people listening to it, so they only appear in debug builds.
 *
 * The stream resolver is named for what the code does. It was previously offered as
 * "PoToken (Recommended)" while the default was Automatic and the path it selects is
 * Shiny's own signature decipher — three claims, none of them accurate.
 */
@Composable
fun AdvancedSettingsScreen(navController: NavController) {
    var engine by rememberEnumPreference(PlaybackEngineKey, PlaybackEngine.AUTO)
    var disableScreenshot by rememberPreference(DisableScreenshotKey, false)

    SettingsPage(title = "Advanced", navController = navController) {
        item(key = "resolver") {
            SettingsSection(
                title = "Streaming",
                footer = "Shiny has to work out a playable URL for every song. If playback starts " +
                    "failing across the board, a different resolver is the first thing to try.",
            ) {
                SettingsChoiceRow(
                    title = "Stream resolver",
                    options = listOf(PlaybackEngine.AUTO, PlaybackEngine.POTOKEN, PlaybackEngine.BRAVEPIPE),
                    selected = engine,
                    label = {
                        when (it) {
                            PlaybackEngine.AUTO -> "Automatic"
                            PlaybackEngine.POTOKEN -> "Built-in decipher"
                            PlaybackEngine.BRAVEPIPE -> "NewPipe extractor"
                        }
                    },
                    optionSubtitle = {
                        when (it) {
                            PlaybackEngine.AUTO -> "Shiny's decipher first, NewPipe if it fails"
                            PlaybackEngine.POTOKEN -> "Shiny's own signature decipher only"
                            PlaybackEngine.BRAVEPIPE -> "The NewPipe library only"
                        }
                    },
                    onSelect = {
                        engine = it
                        // The resolver reads a plain field, seeded from this preference at
                        // startup, so the change has to be pushed to it as well as stored.
                        YTPlayerUtils.playbackEngine = it
                    },
                    divider = false,
                )
            }
        }

        item(key = "device") {
            SettingsSection(title = "This device") {
                SettingsToggleRow(
                    title = "Block screenshots",
                    subtitle = "Also hides Shiny's contents in the app switcher",
                    checked = disableScreenshot,
                    onCheckedChange = { disableScreenshot = it },
                    divider = false,
                )
            }
        }

        if (BuildConfig.DEBUG) {
            item(key = "developer") {
                SettingsSection(
                    title = "Developer",
                    footer = "Only in debug builds. Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                        "${BuildConfig.FLAVOR} · ${BuildConfig.BUILD_TYPE}",
                ) {
                    SettingsNavRow(
                        title = "Service status",
                        subtitle = "Which endpoints Shiny depends on are reachable",
                        onClick = { navController.navigate("uptime") },
                        divider = false,
                    )
                }
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}
