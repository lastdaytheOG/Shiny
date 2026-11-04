package com.shiny.music.ui.liquid.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.ui.liquid.customize.CustomizePrefs
import com.shiny.music.ui.liquid.customize.PlayerStyle
import com.shiny.music.ui.liquid.customize.PlayerStylePrefs
import com.shiny.music.ui.liquid.customize.PlayerStyleTiles
import com.shiny.music.ui.liquid.customize.LocalPreviewArtwork
import com.shiny.music.ui.liquid.customize.LocalPreviewArtist
import com.shiny.music.ui.liquid.customize.LocalPreviewTitle
import com.shiny.music.ui.liquid.customize.rememberPlayerStyle
import com.shiny.music.utils.rememberPreference

@Composable
fun PlayerStylesScreen(navController: NavController) {
    val style by rememberPlayerStyle()
    val rounding by rememberPreference(CustomizePrefs.CoverRounding, CustomizePrefs.CoverRoundingDefault)
    val playerConnection = LocalPlayerConnection.current
    val metadata = playerConnection?.mediaMetadata?.collectAsState()?.value

    CompositionLocalProvider(
        LocalPreviewArtwork provides metadata?.thumbnailUrl,
        LocalPreviewTitle provides (metadata?.title?.takeIf { it.isNotBlank() } ?: "Shiny Music"),
        LocalPreviewArtist provides (metadata?.artists?.joinToString { it.name }.orEmpty()),
    ) {
    SettingsPage(title = "Player styles", navController = navController) {
        item(key = "preview") {
            AnimatedContent(
                targetState = style,
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.88f, stiffness = 430f)) { direction * it / 8 } + fadeIn(tween(140)))
                        .togetherWith(slideOutHorizontally(tween(110)) { -direction * it / 10 } + fadeOut(tween(80)))
                },
                label = "playerStyleShowcase",
            ) { shown -> PlayerStyleShowcase(shown, rounding, metadata) }
        }

        item(key = "styles") {
            SettingsSection(title = "Choose a player") {
                PlayerStyleTiles(selected = style)
            }
        }

        item(key = "options_${style.name}") {
            AnimatedContent(
                targetState = style,
                transitionSpec = {
                    (fadeIn(tween(180)) + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.9f, stiffness = 500f), initialScale = 0.985f))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "playerStyleOptions",
            ) { shown -> StyleOptions(shown, navController) }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
    }
}

@Composable
private fun StyleOptions(style: PlayerStyle, navController: NavController) {
    when (style) {
        PlayerStyle.Shiny, PlayerStyle.Soft -> {
            var volume by rememberPreference(LiquidPrefs.ShowVolume, true)
            var breathe by rememberPreference(LiquidPrefs.ArtworkBreathe, true)
            SettingsSection(title = "${style.title} options") {
                if (style == PlayerStyle.Shiny) {
                    SettingsToggleRow(
                        title = "Volume slider",
                        checked = volume,
                        onCheckedChange = { volume = it },
                    )
                }
                SettingsToggleRow(
                    title = "Cover breathes",
                    checked = breathe,
                    onCheckedChange = { breathe = it },
                )
                SettingsNavRow(
                    title = "More in Appearance",
                    onClick = { navController.navigate("settings/appearance") },
                    divider = false,
                )
            }
        }
        PlayerStyle.Turntable -> {
            var spin by rememberPreference(PlayerStylePrefs.TurntableSpin, true)
            var armSeeks by rememberPreference(PlayerStylePrefs.TurntableArmSeeks, true)
            SettingsSection(title = "Turntable options") {
                SettingsToggleRow(
                    title = "Record spins",
                    checked = spin,
                    onCheckedChange = { spin = it },
                )
                SettingsToggleRow(
                    title = "Drag the arm to seek",
                    checked = armSeeks,
                    onCheckedChange = { armSeeks = it },
                    divider = false,
                )
            }
        }
        PlayerStyle.PosterRing, PlayerStyle.Dial -> Unit
        PlayerStyle.Poster -> {
            var words by rememberPreference(PlayerStylePrefs.PosterWords, true)
            SettingsSection(title = "Poster options") {
                SettingsToggleRow(
                    title = "Sing the words",
                    checked = words,
                    onCheckedChange = { words = it },
                    divider = false,
                )
            }
        }
        PlayerStyle.Timeline, PlayerStyle.Lyrics -> {
            var cover by rememberPreference(PlayerStylePrefs.TimelineCover, true)
            SettingsSection(title = "${style.title} options") {
                SettingsToggleRow(
                    title = "Cover in the corner",
                    checked = cover,
                    onCheckedChange = { cover = it },
                    divider = false,
                )
            }
        }
        PlayerStyle.Video -> {
            var words by rememberPreference(PlayerStylePrefs.VideoWords, true)
            SettingsSection(title = "Full video options") {
                SettingsToggleRow(
                    title = "Words over the video",
                    checked = words,
                    onCheckedChange = { words = it },
                    divider = false,
                )
            }
        }
        PlayerStyle.Bedside, PlayerStyle.BedsideTiles -> {
            var h24 by rememberPreference(PlayerStylePrefs.Bedside24h, false)
            var seconds by rememberPreference(PlayerStylePrefs.BedsideSeconds, false)
            var night by rememberPreference(PlayerStylePrefs.BedsideNight, true)
            var awake by rememberPreference(PlayerStylePrefs.BedsideAwake, true)
            var clockTile by rememberPreference(PlayerStylePrefs.BedsideClockTile, true)
            SettingsSection(title = "Bedside options") {
                SettingsToggleRow(
                    title = "24-hour clock",
                    checked = h24,
                    onCheckedChange = { h24 = it },
                )
                SettingsToggleRow(
                    title = "Show seconds",
                    checked = seconds,
                    onCheckedChange = { seconds = it },
                )
                SettingsToggleRow(
                    title = "Night colours",
                    checked = night,
                    onCheckedChange = { night = it },
                )
                if (style == PlayerStyle.BedsideTiles) {
                    SettingsToggleRow(
                        title = "Clock tile",
                        checked = clockTile,
                        onCheckedChange = { clockTile = it },
                    )
                }
                SettingsToggleRow(
                    title = "Keep the screen on",
                    checked = awake,
                    onCheckedChange = { awake = it },
                    divider = false,
                )
            }
        }
    }
}
