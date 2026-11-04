package com.shiny.music.ui.liquid.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.ui.liquid.customize.PlayerStyle
import com.shiny.music.ui.liquid.customize.PlayerStylePreview
import com.shiny.music.ui.liquid.customize.PlayerStylePrefs
import com.shiny.music.ui.liquid.customize.PlayerStyleTiles
import com.shiny.music.ui.liquid.customize.rememberPlayerStyle
import com.shiny.music.utils.rememberPreference

/**
 * Settings › Customise › Player styles: every style large enough to judge, and under it the
 * chosen style's own options. Changing anything here changes the open player at once.
 */
@Composable
fun PlayerStylesScreen(navController: NavController) {
    val style by rememberPlayerStyle()

    SettingsPage(title = "Player styles", navController = navController) {
        item(key = "preview") {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Crossfade(targetState = style, animationSpec = tween(220), label = "playerStylePreview") { shown ->
                    Box(
                        Modifier
                            .width(176.dp)
                            .aspectRatio(0.5f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF232325))
                    ) { PlayerStylePreview(shown) }
                }
            }
        }

        item(key = "styles") {
            SettingsSection(title = "Style", footer = style.blurb) {
                PlayerStyleTiles(selected = style)
            }
        }

        item(key = "options_${style.name}") {
            StyleOptions(style, navController)
        }

        item(key = "tail") { SettingsTailSpace() }
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
                        subtitle = "The phone's volume under the buttons",
                        checked = volume,
                        onCheckedChange = { volume = it },
                    )
                }
                SettingsToggleRow(
                    title = "Cover breathes",
                    subtitle = "The cover settles back when paused and swells when playing",
                    checked = breathe,
                    onCheckedChange = { breathe = it },
                )
                SettingsNavRow(
                    title = "More in Appearance",
                    subtitle = "Colour, glow and the Poster artwork",
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
                    subtitle = "The record turns while the song plays and slows to a stop on pause",
                    checked = spin,
                    onCheckedChange = { spin = it },
                )
                SettingsToggleRow(
                    title = "Drag the arm to seek",
                    subtitle = "Move the arm across the record to jump in the song",
                    checked = armSeeks,
                    onCheckedChange = { armSeeks = it },
                    divider = false,
                )
            }
        }
        PlayerStyle.Poster -> {
            var words by rememberPreference(PlayerStylePrefs.PosterWords, true)
            SettingsSection(title = "Poster options") {
                SettingsToggleRow(
                    title = "Sing the words",
                    subtitle = if (words) "Synced lyrics are set big under the cover" else "The title is set big under the cover",
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
                    subtitle = "The song's cover beside its title at the top",
                    checked = cover,
                    onCheckedChange = { cover = it },
                    divider = false,
                )
            }
        }
        PlayerStyle.Video -> {
            var words by rememberPreference(PlayerStylePrefs.VideoWords, true)
            SettingsSection(title = "Full video options", footer = "A song without a video shows its cover instead") {
                SettingsToggleRow(
                    title = "Words over the video",
                    subtitle = "The line being sung, at the foot of the video",
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
                    subtitle = "A dim red clock from 10 pm to 6 am, easy on eyes in the dark",
                    checked = night,
                    onCheckedChange = { night = it },
                )
                if (style == PlayerStyle.BedsideTiles) {
                    SettingsToggleRow(
                        title = "Clock tile",
                        subtitle = if (clockTile) "The second tile is a clock" else "The second tile is the lyrics",
                        checked = clockTile,
                        onCheckedChange = { clockTile = it },
                    )
                }
                SettingsToggleRow(
                    title = "Keep the screen on",
                    subtitle = "While this player is open",
                    checked = awake,
                    onCheckedChange = { awake = it },
                    divider = false,
                )
            }
        }
    }
}
