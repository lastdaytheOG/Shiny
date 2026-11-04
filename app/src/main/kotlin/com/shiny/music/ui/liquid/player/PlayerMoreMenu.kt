package com.shiny.music.ui.liquid.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.ui.liquid.LiquidPullDownMenu
import com.shiny.music.ui.liquid.MenuAction

/**
 * The menu beside Like: the song's video, repeat, a sleep timer, and the full song options.
 * Choosing Sleep timer swaps the list for the timer's choices in place.
 */
@Composable
internal fun PlayerMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hasVideo: Boolean,
    onOtherMenu: () -> Unit,
) {
    val connection = LocalPlayerConnection.current ?: return
    val repeatMode by connection.repeatMode.collectAsState()
    val shuffle by connection.shuffleModeEnabled.collectAsState()
    val editPlayer = com.shiny.music.ui.liquid.player.styles.LocalEditPlayer.current
    var sleepPage by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    val close = {
        sleepOpen = false
        sleepPage = false
        onDismiss()
    }
    val timer = runCatching { connection.service.sleepTimer }.getOrNull()
    val entries = if (sleepPage && timer != null) {
        buildList {
            listOf(15, 30, 45, 60).forEach { minutes ->
                add(MenuAction(title = "$minutes minutes", onClick = { timer.start(minutes); sleepPage = false }))
            }
            add(MenuAction(title = "End of song", checked = timer.pauseWhenSongEnd, onClick = { timer.start(-1); sleepPage = false }))
            if (timer.isActive) add(MenuAction(title = "Turn off", destructive = true, onClick = { timer.clear(); sleepPage = false }))
        }
    } else {
        buildList {
            if (hasVideo) {
                val inline = SongVideoState.view == VideoView.Inline
                add(MenuAction(title = if (inline) "Hide video" else "See video", icon = Icons.Rounded.OndemandVideo, onClick = {
                    SongVideoState.view = if (inline) VideoView.Off else VideoView.Inline
                }))
                add(MenuAction(title = "Full video", icon = Icons.Rounded.Fullscreen, onClick = { SongVideoState.view = VideoView.Full }))
            }
            add(MenuAction(title = "Shuffle", icon = Icons.Rounded.Shuffle, checked = shuffle, onClick = {
                connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
            }))
            add(MenuAction(title = "Repeat", icon = Icons.Rounded.RepeatOne, checked = repeatMode == Player.REPEAT_MODE_ONE, onClick = {
                connection.player.repeatMode = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            }))
            if (timer != null) {
                add(MenuAction(
                    title = "Sleep timer",
                    icon = Icons.Rounded.Bedtime,
                    subtitle = timerLabel(timer.triggerTime, timer.pauseWhenSongEnd),
                    onClick = { sleepPage = true; sleepOpen = true },
                ))
            }
            add(MenuAction(title = "Other menu", icon = Icons.Rounded.MoreHoriz, onClick = onOtherMenu))
            add(MenuAction(title = "Edit player", icon = Icons.Rounded.Tune, onClick = editPlayer))
        }
    }
    LiquidPullDownMenu(expanded = expanded || sleepOpen, onDismiss = close, entries = entries)
}

private fun timerLabel(triggerTime: Long, endOfSong: Boolean): String? = when {
    endOfSong -> "End of song"
    triggerTime > 0 -> "${((triggerTime - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)} min left"
    else -> null
}
