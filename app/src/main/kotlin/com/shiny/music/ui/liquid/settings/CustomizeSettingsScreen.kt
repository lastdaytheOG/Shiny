package com.shiny.music.ui.liquid.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import com.shiny.music.constants.LiquidGlassGlobalEnabledKey
import com.shiny.music.ui.liquid.customize.BlurPreview
import com.shiny.music.ui.liquid.customize.BlurQuality
import com.shiny.music.ui.liquid.customize.HomeStyle
import com.shiny.music.ui.liquid.customize.HomeStylePreview
import com.shiny.music.ui.liquid.customize.LyricsStyle
import com.shiny.music.ui.liquid.customize.LyricsStylePreview
import com.shiny.music.ui.liquid.customize.PlayerStyleTiles
import com.shiny.music.ui.liquid.customize.PlaylistStyle
import com.shiny.music.ui.liquid.customize.PlaylistStylePreview
import com.shiny.music.ui.liquid.customize.StyleTiles
import com.shiny.music.ui.liquid.customize.rememberPlayerStyle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.constants.SwipeToSongKey
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.customize.AnimationSpeeds
import com.shiny.music.ui.liquid.customize.CustomizePrefs
import com.shiny.music.ui.liquid.customize.DefaultSwipeLeft
import com.shiny.music.ui.liquid.customize.DefaultSwipeRight
import com.shiny.music.ui.liquid.customize.SongSwipeAction
import com.shiny.music.ui.liquid.customize.animationSpeedLabel
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import kotlin.math.roundToInt

/**
 * Settings › Customise: how covers are rounded, how the app moves, what sliding a song does,
 * and what the open player shows around it. Every default is the Shiny people already had.
 */
@Composable
fun CustomizeSettingsScreen(navController: NavController) {
    var rounding by rememberPreference(CustomizePrefs.CoverRounding, CustomizePrefs.CoverRoundingDefault)
    var animations by rememberPreference(CustomizePrefs.Animations, true)
    var speed by rememberPreference(CustomizePrefs.AnimationSpeed, 1f)
    var swipeOn by rememberPreference(SwipeToSongKey, true)
    var swipeRight by rememberEnumPreference(CustomizePrefs.SwipeRight, DefaultSwipeRight)
    var swipeLeft by rememberEnumPreference(CustomizePrefs.SwipeLeft, DefaultSwipeLeft)
    var anywhere by rememberPreference(CustomizePrefs.SwipeDownAnywhere, true)
    var statusBar by rememberPreference(CustomizePrefs.PlayerStatusBar, true)
    var navBar by rememberPreference(CustomizePrefs.PlayerNavBar, true)

    var playlistStyle by rememberEnumPreference(CustomizePrefs.PlaylistStyle, PlaylistStyle.Classic)
    var homeStyle by rememberEnumPreference(CustomizePrefs.HomeStyle, HomeStyle.Hero)
    var lyricsStyle by rememberEnumPreference(CustomizePrefs.LyricsStyle, LyricsStyle.Shiny)
    var glass by rememberPreference(LiquidGlassGlobalEnabledKey, true)
    var progressive by rememberPreference(CustomizePrefs.ProgressiveBlur, true)
    var blurQuality by rememberEnumPreference(CustomizePrefs.BlurQuality, BlurQuality.High)
    var blurStrength by rememberPreference(CustomizePrefs.BlurStrength, CustomizePrefs.BlurStrengthDefault)
    var elementBlur by rememberPreference(CustomizePrefs.ElementBlur, true)
    val playerStyle by rememberPlayerStyle()

    // dragging moves a local value; it is saved once, when the finger lifts
    var roundingDrag by remember { mutableStateOf<Float?>(null) }
    var speedDrag by remember { mutableStateOf<Float?>(null) }
    var strengthDrag by remember { mutableStateOf<Float?>(null) }

    SettingsPage(title = "Customise", navController = navController) {
        item(key = "rounding") {
            SettingsSection(title = "Covers") {
                CoverRoundingPreview(rounding = roundingDrag ?: rounding)
                SettingsSliderRow(
                    title = "Cover rounding",
                    subtitle = "From square corners to soft ones, on every cover in the app",
                    value = roundingDrag ?: rounding,
                    valueRange = 0f..CustomizePrefs.CoverRoundingMax,
                    valueLabel = "${(roundingDrag ?: rounding).roundToInt()} px",
                    onValueChange = { roundingDrag = it },
                    onValueChangeFinished = {
                        roundingDrag?.let { rounding = it.roundToInt().toFloat() }
                        roundingDrag = null
                    },
                    divider = false,
                )
            }
        }

        item(key = "playlist") {
            SettingsSection(title = "Playlist styles", footer = playlistStyle.blurb) {
                StyleTiles(
                    styles = PlaylistStyle.entries,
                    selected = playlistStyle,
                    name = { it.title },
                    onSelect = { playlistStyle = it },
                ) { PlaylistStylePreview(it) }
            }
        }

        item(key = "player") {
            SettingsSection(title = "Player styles", footer = playerStyle.blurb) {
                PlayerStyleTiles(selected = playerStyle)
                SettingsNavRow(
                    title = "Edit player styles",
                    subtitle = "Bigger previews and each style's own options",
                    onClick = { navController.navigate("settings/customize/player") },
                    divider = false,
                )
            }
        }

        item(key = "home") {
            SettingsSection(title = "Home styles", footer = homeStyle.blurb) {
                StyleTiles(
                    styles = HomeStyle.entries,
                    selected = homeStyle,
                    name = { it.title },
                    onSelect = { homeStyle = it },
                ) { HomeStylePreview(it) }
            }
        }

        item(key = "blur") {
            val strength = strengthDrag ?: blurStrength
            SettingsSection(title = "Blur") {
                Box(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFF232325)),
                ) { BlurPreview(on = glass, progressive = progressive, strength = strength) }
                SettingsToggleRow(
                    title = "Blur",
                    subtitle = if (glass) "Bars, buttons and sheets frost what's behind them" else "Bars, buttons and sheets are solid",
                    checked = glass,
                    onCheckedChange = { glass = it },
                )
                SettingsToggleRow(
                    title = "Progressive blur",
                    subtitle = if (progressive) "Content blurs more the closer it gets to the top" else "A plain fade under the top bar",
                    checked = progressive,
                    onCheckedChange = { progressive = it },
                    enabled = glass,
                )
                SettingsSegmentedRow(
                    title = "Blur quality",
                    subtitle = if (blurQuality == BlurQuality.High) "Sharpest glass" else "Lighter on the phone, a little softer",
                    options = BlurQuality.entries,
                    selected = blurQuality,
                    label = { it.title },
                    onSelect = { blurQuality = it },
                )
                SettingsSliderRow(
                    title = "Blur strength",
                    subtitle = if (strength.roundToInt() == CustomizePrefs.BlurStrengthDefault.roundToInt()) "Shiny's own" else "How much glass frosts what's behind it",
                    value = strength,
                    valueRange = CustomizePrefs.BlurStrengthMin..CustomizePrefs.BlurStrengthMax,
                    valueLabel = "${strength.roundToInt()} px",
                    onValueChange = { strengthDrag = it },
                    onValueChangeFinished = {
                        strengthDrag?.let { blurStrength = it.roundToInt().toFloat() }
                        strengthDrag = null
                    },
                    divider = false,
                )
            }
        }

        item(key = "element_blur") {
            SettingsSection(title = "Element blur") {
                SettingsToggleRow(
                    title = "Element blur",
                    subtitle = "Home's top card and Library's titles soften as they scroll away",
                    checked = elementBlur,
                    onCheckedChange = { elementBlur = it },
                    divider = false,
                )
            }
        }

        item(key = "animation") {
            val stops = AnimationSpeeds
            val index = speedDrag ?: (stops.indexOfFirst { it >= speed - 0.01f }.takeIf { it >= 0 } ?: stops.lastIndex).toFloat()
            val shownSpeed = stops[index.roundToInt().coerceIn(0, stops.lastIndex)]
            SettingsSection(title = "Animation") {
                SettingsToggleRow(
                    title = "Animations",
                    subtitle = if (animations) "Pages, sheets and the player move" else "Everything jumps straight to where it ends",
                    checked = animations,
                    onCheckedChange = { animations = it },
                )
                SettingsSliderRow(
                    title = "Animation speed",
                    subtitle = if (shownSpeed == 1f) "Follows the phone's own animation speed" else "On top of the phone's own animation speed",
                    value = index,
                    valueRange = 0f..stops.lastIndex.toFloat(),
                    steps = stops.size - 2,
                    valueLabel = if (animations) animationSpeedLabel(shownSpeed) else "Off",
                    onValueChange = { speedDrag = it },
                    onValueChangeFinished = {
                        speedDrag?.let { speed = stops[it.roundToInt().coerceIn(0, stops.lastIndex)] }
                        speedDrag = null
                    },
                    divider = false,
                )
            }
        }

        item(key = "lyrics") {
            SettingsSection(title = "Lyrics animation", footer = lyricsStyle.blurb) {
                StyleTiles(
                    styles = LyricsStyle.entries,
                    selected = lyricsStyle,
                    name = { it.title },
                    onSelect = { lyricsStyle = it },
                ) { LyricsStylePreview(it) }
            }
        }

        item(key = "gestures") {
            SettingsSection(title = "Gestures") {
                SettingsToggleRow(
                    title = "Swipe a song row",
                    subtitle = "Slide any song sideways for a quick action",
                    checked = swipeOn,
                    onCheckedChange = { swipeOn = it },
                )
                SettingsChoiceRow(
                    title = "Swipe right",
                    options = SongSwipeAction.entries,
                    selected = swipeRight,
                    label = { it.title },
                    onSelect = { swipeRight = it },
                    enabled = swipeOn,
                )
                SettingsChoiceRow(
                    title = "Swipe left",
                    options = SongSwipeAction.entries,
                    selected = swipeLeft,
                    label = { it.title },
                    onSelect = { swipeLeft = it },
                    enabled = swipeOn,
                )
                SettingsToggleRow(
                    title = "Swipe down anywhere to close",
                    subtitle = if (anywhere) "A swipe down anywhere on the player closes it" else "Only a swipe down from the top part of the player closes it",
                    checked = anywhere,
                    onCheckedChange = { anywhere = it },
                    divider = false,
                )
            }
        }

        item(key = "bars") {
            SettingsSection(title = "Player") {
                SettingsToggleRow(
                    title = "Top bar on player",
                    subtitle = "The clock, battery and signal over the open player",
                    checked = statusBar,
                    onCheckedChange = { statusBar = it },
                )
                SettingsToggleRow(
                    title = "Bottom bar on player",
                    subtitle = "The phone's navigation bar under the open player",
                    checked = navBar,
                    onCheckedChange = { navBar = it },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}

// three covers at the sizes the app uses them, rounded as the slider says
@Composable
private fun CoverRoundingPreview(rounding: Float) {
    val playerConnection = LocalPlayerConnection.current
    val metadata = playerConnection?.mediaMetadata?.collectAsState()?.value
    val scale = rounding / CustomizePrefs.CoverRoundingDefault
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // the radii each size has by default, scaled like every other cover
        listOf(120.dp to 12.dp, 80.dp to 8.dp, 50.dp to 10.dp).forEach { (size, radius) ->
            Artwork(
                model = metadata?.thumbnailUrl,
                shape = RoundedCornerShape((radius * scale).coerceAtMost(size / 2)),
                scaleCorners = false,
                modifier = Modifier.size(size),
            )
        }
    }
}
