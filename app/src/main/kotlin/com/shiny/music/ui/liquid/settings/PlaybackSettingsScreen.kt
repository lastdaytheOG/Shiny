package com.shiny.music.ui.liquid.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.shiny.music.constants.AudioNormalizationKey
import com.shiny.music.constants.AudioOffload
import com.shiny.music.constants.AutoSkipNextOnErrorKey
import com.shiny.music.constants.CrossfadeDurationKey
import com.shiny.music.constants.CrossfadeEnabledKey
import com.shiny.music.constants.CrossfadeGaplessKey
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.constants.DisableLoadMoreWhenRepeatAllKey
import com.shiny.music.constants.PauseOnMute
import com.shiny.music.constants.PersistentQueueKey
import com.shiny.music.constants.PersistentShuffleAcrossQueuesKey
import com.shiny.music.constants.PreloadLyricsEnabledKey
import com.shiny.music.constants.PreloadNextSongEnabledKey
import com.shiny.music.constants.PreventDuplicateTracksInQueueKey
import com.shiny.music.constants.RememberShuffleAndRepeatKey
import com.shiny.music.constants.ResumeOnBluetoothConnectKey
import com.shiny.music.constants.ShufflePlaylistFirstKey
import com.shiny.music.constants.SimilarContent
import com.shiny.music.constants.SkipSilenceInstantKey
import com.shiny.music.constants.SkipSilenceKey
import com.shiny.music.constants.StopMusicOnTaskClearKey
import com.shiny.music.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
fun PlaybackSettingsScreen(navController: NavController) {
    var dataSaver by rememberPreference(DataSaverEnabledKey, false)

    var normalization by rememberPreference(AudioNormalizationKey, true)
    var skipSilence by rememberPreference(SkipSilenceKey, false)
    var skipSilenceInstant by rememberPreference(SkipSilenceInstantKey, false)
    var offload by rememberPreference(AudioOffload, false)

    var crossfade by rememberPreference(CrossfadeEnabledKey, false)
    var crossfadeDuration by rememberPreference(CrossfadeDurationKey, 5f)
    var crossfadeGapless by rememberPreference(CrossfadeGaplessKey, true)

    var preload by rememberPreference(PreloadNextSongEnabledKey, true)
    var preloadLyrics by rememberPreference(PreloadLyricsEnabledKey, true)

    var persistentQueue by rememberPreference(PersistentQueueKey, true)
    var rememberShuffle by rememberPreference(RememberShuffleAndRepeatKey, true)
    var shuffleAcrossQueues by rememberPreference(PersistentShuffleAcrossQueuesKey, false)
    var shufflePlaylistFirst by rememberPreference(ShufflePlaylistFirstKey, false)
    var preventDuplicates by rememberPreference(PreventDuplicateTracksInQueueKey, false)
    var similarContent by rememberPreference(SimilarContent, true)
    var stopLoadingOnRepeatAll by rememberPreference(DisableLoadMoreWhenRepeatAllKey, false)

    var skipOnError by rememberPreference(AutoSkipNextOnErrorKey, true)
    var pauseOnMute by rememberPreference(PauseOnMute, false)
    var resumeOnBluetooth by rememberPreference(ResumeOnBluetoothConnectKey, false)
    var stopOnTaskClear by rememberPreference(StopMusicOnTaskClearKey, false)

    SettingsPage(title = "Playback", navController = navController) {
        item(key = "data_saver") {
            SettingsSection(
                title = "Data",
                footer = "Turns off preloading, animated covers and videos.",
            ) {
                SettingsToggleRow(
                    title = "Data Saver",
                    checked = dataSaver,
                    onCheckedChange = { dataSaver = it },
                    divider = false,
                )
            }
        }

        item(key = "sound") {
            SettingsSection(title = "Sound") {
                SettingsToggleRow(
                    title = "Volume levelling",
                    subtitle = "Even loudness across songs",
                    checked = normalization,
                    onCheckedChange = { normalization = it },
                )
                SettingsToggleRow(
                    title = "Skip silence",
                    checked = skipSilence,
                    onCheckedChange = { skipSilence = it },
                    divider = skipSilence,
                )
                if (skipSilence) {
                    SettingsToggleRow(
                        title = "Skip silence instantly",
                        checked = skipSilenceInstant,
                        onCheckedChange = { skipSilenceInstant = it },
                        divider = false,
                    )
                }
            }
        }

        item(key = "transitions") {
            SettingsSection(title = "Transitions") {
                SettingsToggleRow(
                    title = "Crossfade",
                    checked = crossfade,
                    onCheckedChange = { crossfade = it },
                )
                if (crossfade) {
                    SettingsSliderRow(
                        title = "Crossfade length",
                        value = crossfadeDuration,
                        onValueChange = { crossfadeDuration = it },
                        valueRange = 1f..15f,
                        steps = 13,
                        valueLabel = "${crossfadeDuration.roundToInt()}s",
                    )
                    SettingsToggleRow(
                        title = "Keep album tracks gapless",
                        checked = crossfadeGapless,
                        onCheckedChange = { crossfadeGapless = it },
                    )
                }
                SettingsToggleRow(
                    title = "Audio offload",
                    subtitle = if (crossfade) "Off during crossfade" else "Saves battery",
                    checked = offload && !crossfade,
                    enabled = !crossfade,
                    onCheckedChange = { offload = it },
                    divider = false,
                )
            }
        }

        item(key = "up_next") {
            SettingsSection(title = "Up next") {
                SettingsToggleRow(
                    title = "Prepare the next song",
                    checked = preload,
                    onCheckedChange = { preload = it },
                    divider = preload && SettingsVisibility.LYRICS,
                )
                if (preload && SettingsVisibility.LYRICS) {
                    SettingsToggleRow(
                        title = "Prepare its lyrics too",
                        checked = preloadLyrics,
                        onCheckedChange = { preloadLyrics = it },
                        divider = false,
                    )
                }
            }
        }

        item(key = "queue") {
            SettingsSection(title = "Queue") {
                SettingsToggleRow(
                    title = "Restore the queue",
                    checked = persistentQueue,
                    onCheckedChange = { persistentQueue = it },
                )
                SettingsToggleRow(
                    title = "Remember shuffle and repeat",
                    checked = rememberShuffle,
                    onCheckedChange = { rememberShuffle = it },
                )
                SettingsToggleRow(
                    title = "Keep shuffle on across queues",
                    checked = shuffleAcrossQueues,
                    onCheckedChange = { shuffleAcrossQueues = it },
                )
                SettingsToggleRow(
                    title = "Shuffle the whole playlist",
                    subtitle = "Not from the song you tapped",
                    checked = shufflePlaylistFirst,
                    onCheckedChange = { shufflePlaylistFirst = it },
                )
                SettingsToggleRow(
                    title = "Never queue the same song twice",
                    checked = preventDuplicates,
                    onCheckedChange = { preventDuplicates = it },
                )
                SettingsToggleRow(
                    title = "Line up similar music",
                    subtitle = "When an album or playlist ends",
                    checked = similarContent,
                    onCheckedChange = { similarContent = it },
                )
                SettingsToggleRow(
                    title = "Stop adding songs on Repeat All",
                    checked = stopLoadingOnRepeatAll,
                    onCheckedChange = { stopLoadingOnRepeatAll = it },
                    divider = false,
                )
            }
        }

        item(key = "behaviour") {
            SettingsSection(title = "Behaviour") {
                SettingsToggleRow(
                    title = "Skip tracks that fail",
                    checked = skipOnError,
                    onCheckedChange = { skipOnError = it },
                )
                SettingsToggleRow(
                    title = "Pause when muted",
                    checked = pauseOnMute,
                    onCheckedChange = { pauseOnMute = it },
                )
                SettingsToggleRow(
                    title = "Resume when Bluetooth connects",
                    checked = resumeOnBluetooth,
                    onCheckedChange = { resumeOnBluetooth = it },
                )
                SettingsToggleRow(
                    title = "Stop when Shiny is swiped away",
                    checked = stopOnTaskClear,
                    onCheckedChange = { stopOnTaskClear = it },
                    divider = false,
                )
            }
        }

        item(key = "equaliser") {
            SettingsSection(title = "Sound shaping") {
                SettingsNavRow(
                    title = "Equaliser",
                    onClick = { navController.navigate("settings/equalizer") },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}
