package com.shiny.music.ui.liquid.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.constants.AppLanguageKey
import com.shiny.music.constants.AutoSkipNextOnErrorKey
import com.shiny.music.constants.ContentCountryKey
import com.shiny.music.constants.CountryCodeToName
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.constants.HistoryDuration
import com.shiny.music.constants.KeepScreenOn
import com.shiny.music.constants.LanguageCodeToName
import com.shiny.music.constants.SYSTEM_DEFAULT
import com.shiny.music.constants.SkipSilenceKey
import com.shiny.music.constants.StopMusicOnTaskClearKey
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.utils.rememberPreference
import kotlin.math.roundToInt

// the everyday switches in one place; each is the same setting the older pages used, so
// nothing changes for anyone who had set them before
@Composable
fun BehaviourSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var dataSaver by rememberPreference(DataSaverEnabledKey, false)
    var skipSilence by rememberPreference(SkipSilenceKey, false)
    var historySeconds by rememberPreference(HistoryDuration, 30f)
    var skipOnError by rememberPreference(AutoSkipNextOnErrorKey, true)
    var stopOnTaskClear by rememberPreference(StopMusicOnTaskClearKey, false)
    var showVolume by rememberPreference(LiquidPrefs.ShowVolume, true)
    var keepScreenOn by rememberPreference(KeepScreenOn, false)
    var country by rememberPreference(ContentCountryKey, "system")
    var appLanguage by rememberPreference(AppLanguageKey, SYSTEM_DEFAULT)
    val systemDefault = stringResource(R.string.system_default)

    SettingsPage(title = "App Behaviour", navController = navController) {
        item(key = "playback") {
            SettingsSection(title = "Playback") {
                SettingsToggleRow(
                    title = "Data Saver",
                    subtitle = "Turns off preloading, animated covers and videos",
                    checked = dataSaver,
                    onCheckedChange = { dataSaver = it },
                )
                SettingsToggleRow(
                    title = "Skip silence",
                    subtitle = "Jump over quiet gaps in a song",
                    checked = skipSilence,
                    onCheckedChange = { skipSilence = it },
                )
                SettingsSliderRow(
                    title = "Count songs after",
                    subtitle = "How long before a song counts as listened",
                    value = historySeconds,
                    onValueChange = { historySeconds = it },
                    valueRange = 0f..120f,
                    steps = 23,
                    valueLabel = secondsLabel(historySeconds.roundToInt()),
                )
                SettingsToggleRow(
                    title = "Skip tracks that fail",
                    subtitle = "Move on when a song can't be played",
                    checked = skipOnError,
                    onCheckedChange = { skipOnError = it },
                )
                SettingsToggleRow(
                    title = "Stop when SHINY is swiped away",
                    subtitle = "Clearing it from recents stops the music",
                    checked = stopOnTaskClear,
                    onCheckedChange = { stopOnTaskClear = it },
                )
            }
        }
        item(key = "player") {
            SettingsSection(title = "Player") {
                SettingsToggleRow(
                    title = "Volume slider",
                    subtitle = "Show a volume slider in Now Playing",
                    checked = showVolume,
                    onCheckedChange = { showVolume = it },
                )
                SettingsToggleRow(
                    title = "Keep screen on while playing",
                    subtitle = "The screen stays awake with the player open",
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it },
                )
            }
        }
        item(key = "region") {
            SettingsSection(title = "Region & language") {
                SettingsChoiceRow(
                    title = "Default country",
                    subtitle = "Charts and recommendations follow it",
                    options = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
                    selected = if (country in CountryCodeToName) country else SYSTEM_DEFAULT,
                    label = { CountryCodeToName[it] ?: systemDefault },
                    onSelect = { country = it },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // android keeps the app's language itself from 13 on
                    SettingsNavRow(
                        title = stringResource(R.string.app_language),
                        subtitle = stringResource(R.string.app_language_desc),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri()))
                        },
                    )
                } else {
                    SettingsChoiceRow(
                        title = stringResource(R.string.app_language),
                        options = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                        selected = if (appLanguage in LanguageCodeToName) appLanguage else SYSTEM_DEFAULT,
                        label = { LanguageCodeToName[it] ?: systemDefault },
                        onSelect = { appLanguage = it },
                    )
                }
            }
        }
        item(key = "tail") { SettingsTailSpace(40.dp) }
    }
}

private fun secondsLabel(seconds: Int): String = when {
    seconds <= 0 -> "Right away"
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60}m ${seconds % 60}s"
}
