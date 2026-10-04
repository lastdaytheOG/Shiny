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

/**
 * Everything about how a song plays: the sound itself, how one track hands over to the
 * next, what the queue does, and what playback does when something happens to the phone.
 *
 * Every row here is read by `MusicService`. Several of them existed in the service for a
 * long time with no way to reach them; they are surfaced rather than invented.
 */
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
    // MainActivity reads this with `false` as its default; the old settings screen claimed
    // `true`, so the switch and the behaviour disagreed for anyone who never touched it.
    var stopOnTaskClear by rememberPreference(StopMusicOnTaskClearKey, false)

    SettingsPage(title = "Playback", navController = navController) {
        item(key = "data_saver") {
            SettingsSection(
                title = "Data",
                footer = "Data Saver stops Shiny fetching lyrics ahead of time, preloading the next " +
                    "song, downloading motion artwork and artist videos, and keeps video tracks out " +
                    "of autoplay. Scrobbling to ListenBrainz pauses while it is on.",
            ) {
                SettingsToggleRow(
                    title = "Data Saver",
                    subtitle = "Use as little mobile data as Shiny can",
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
                    subtitle = "Hold every song at a similar loudness",
                    checked = normalization,
                    onCheckedChange = { normalization = it },
                )
                SettingsToggleRow(
                    title = "Skip silence",
                    subtitle = "Move through silent passages instead of playing them",
                    checked = skipSilence,
                    onCheckedChange = { skipSilence = it },
                    divider = skipSilence,
                )
                if (skipSilence) {
                    SettingsToggleRow(
                        title = "Skip silence instantly",
                        subtitle = "Jump the gap rather than easing across it",
                        checked = skipSilenceInstant,
                        onCheckedChange = { skipSilenceInstant = it },
                        divider = false,
                    )
                }
            }
        }

        item(key = "transitions") {
            SettingsSection(
                title = "Transitions",
                footer = if (crossfade) {
                    "Crossfade runs two players at once, so hardware audio offload stays off while it is on."
                } else null,
            ) {
                SettingsToggleRow(
                    title = "Crossfade",
                    subtitle = "Fade one song into the next",
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
                        subtitle = "Songs recorded to run together still run together",
                        checked = crossfadeGapless,
                        onCheckedChange = { crossfadeGapless = it },
                    )
                }
                SettingsToggleRow(
                    title = "Audio offload",
                    subtitle = if (crossfade) {
                        "Unavailable while crossfade is on"
                    } else {
                        "Let the audio chip decode playback to save battery"
                    },
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
                    subtitle = "Buffer what is coming so it starts the moment it is due",
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
                    subtitle = "Pick up where you left off after Shiny is closed",
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
                    subtitle = "Starting something new stays shuffled",
                    checked = shuffleAcrossQueues,
                    onCheckedChange = { shuffleAcrossQueues = it },
                )
                SettingsToggleRow(
                    title = "Shuffle the whole playlist",
                    subtitle = "Shuffling starts anywhere in it, not from the song you tapped",
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
                    subtitle = "Keep playing in the same vein when an album or playlist ends",
                    checked = similarContent,
                    onCheckedChange = { similarContent = it },
                )
                SettingsToggleRow(
                    title = "Stop adding songs on Repeat All",
                    subtitle = "Repeat the queue you have instead of extending it",
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
                    subtitle = "Move on instead of stopping when a song cannot be played",
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
                    subtitle = "Closing the app from Recents also stops playback",
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
                    subtitle = "Bands, presets and bass",
                    onClick = { navController.navigate("settings/equalizer") },
                    divider = false,
                )
            }
        }

        item(key = "tail") { SettingsTailSpace() }
    }
}
