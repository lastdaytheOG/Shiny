package com.shiny.music.ui.liquid.settings

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shiny.music.LocalDatabase
import com.shiny.music.constants.HistoryDuration
import com.shiny.music.constants.PauseListenHistoryKey
import com.shiny.music.constants.PauseSearchHistoryKey
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidAlert
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.utils.rememberPreference
import kotlin.math.roundToInt

/**
 * What Shiny remembers about you, and how to stop it.
 *
 * Only the controls over recorded history live here: the two histories Shiny keeps, how to
 * pause each one and how to clear it. The screenshot block is a device setting, not a record
 * of anything, and lives in Advanced.
 */
@Composable
fun PrivacySettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current

    var pauseListenHistory by rememberPreference(PauseListenHistoryKey, false)
    var pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, false)
    // 30 seconds is what MusicService has always used; the old screen declared 1.
    var historySeconds by rememberPreference(HistoryDuration, 30f)

    var confirmClearListens by remember { mutableStateOf(false) }
    var confirmClearSearches by remember { mutableStateOf(false) }

    SettingsPage(title = "Privacy", navController = navController) {
        item(key = "intro") {
            SettingsIntro(
                "SHINY uses your listening and search history for stats and recommendations. " +
                    "You can pause or clear either at any time.",
            )
        }

        item(key = "history") {
            SettingsSection(title = "Listening history") {
                SettingsToggleRow(
                    title = "Pause listening history",
                    checked = pauseListenHistory,
                    onCheckedChange = { pauseListenHistory = it },
                )
                SettingsSliderRow(
                    title = "Count a listen after",
                    subtitle = "How long before a song counts as listened",
                    value = historySeconds,
                    onValueChange = { historySeconds = it },
                    valueRange = 0f..120f,
                    steps = 23,
                    valueLabel = listenLabel(historySeconds.roundToInt()),
                )
                SettingsActionRow(
                    title = "Clear listening history",
                    subtitle = "Delete your listening history from this device",
                    onClick = { confirmClearListens = true },
                    destructive = true,
                    divider = false,
                )
                PrivacyAside("Your YouTube Music account may keep its own playback history.")
            }
        }

        item(key = "search") {
            SettingsSection(title = "Search") {
                SettingsToggleRow(
                    title = "Pause search history",
                    checked = pauseSearchHistory,
                    onCheckedChange = { pauseSearchHistory = it },
                )
                SettingsActionRow(
                    title = "Clear search history",
                    onClick = { confirmClearSearches = true },
                    destructive = true,
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }

    if (confirmClearListens) {
        LiquidAlert(
            title = "Clear listening history?",
            message = "Your stats, top songs and recommendations will start again from nothing. " +
                "This can't be undone.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClearListens = false
                database.query { clearListenHistory() }
                Toast.makeText(context, "Listening history cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClearListens = false },
        )
    }
    if (confirmClearSearches) {
        LiquidAlert(
            title = "Clear search history?",
            message = "Your recent searches will be removed. This can't be undone.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                confirmClearSearches = false
                database.query { clearSearchHistory() }
                Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClearSearches = false },
        )
    }
}

/**
 * A caveat that belongs to the section but isn't about this device: set a step quieter than
 * the rows' own supporting lines, so it reads as a side note rather than a setting.
 */
@Composable
private fun PrivacyAside(text: String) {
    Text(
        text = text,
        style = LiquidTypography.caption1,
        color = Liquid.colors.tertiaryLabel,
        modifier = Modifier.padding(start = SettingsGutter, end = SettingsGutter, top = 6.dp),
    )
}

private fun listenLabel(seconds: Int): String = when {
    seconds <= 0 -> "Right away"
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60}m ${seconds % 60}s"
}
