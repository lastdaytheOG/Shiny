package com.shiny.music.ui.liquid.player.styles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.ui.component.BottomSheetState
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.ui.liquid.customize.PlayerStyle
import com.shiny.music.ui.menu.PlayerMenu

/**
 * The open player in one of the styles that are complete designs of their own. It sits in the
 * same sheet as Shiny's player, so the swipe down, the back button and the mini player behave
 * exactly as they always have; only what is drawn changes.
 */
@Composable
internal fun AlternatePlayer(style: PlayerStyle, sheet: BottomSheetState, navController: NavController) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    val metadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canNext by playerConnection.canSkipNext.collectAsState()

    BackHandler(enabled = sheet.isExpanded) { sheet.collapseSoft() }

    // every style is dark, so the status bar's icons turn light while it is up
    DisposableEffect(sheet.isExpanded) {
        val window = (context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val wasLight = controller?.isAppearanceLightStatusBars
        if (sheet.isExpanded) controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (wasLight != null && sheet.isExpanded) controller.isAppearanceLightStatusBars = wasLight
        }
    }

    val wordsLed = style == PlayerStyle.Lyrics || style == PlayerStyle.Poster || style == PlayerStyle.Video || style.bedside
    val clock = rememberStyleClock(sheet.isExpanded, isPlaying, metadata, fast = wordsLed)
    val tones = rememberStyleTones(metadata?.thumbnailUrl)
    val actions = remember(playerConnection, sheet, metadata, navController) {
        StyleActions(
            previous = { playerConnection.seekToPrevious() },
            playPause = { playerConnection.togglePlayPause() },
            next = { playerConnection.seekToNext() },
            seek = { target -> playerConnection.seekTo(target) },
            close = { sheet.collapseSoft() },
            more = {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = metadata,
                        navController = navController,
                        playerBottomSheetState = sheet,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
        )
    }
    val seekWithClock = remember(actions, clock) {
        StyleActions(
            previous = actions.previous,
            playPause = actions.playPause,
            next = actions.next,
            seek = { target ->
                clock.moved(target)
                actions.seek(target)
            },
            close = actions.close,
            more = actions.more,
        )
    }
    val song = StyleSong(metadata, isPlaying, canPrevious, canNext, clock, tones, seekWithClock)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tones.light, tones.dark)))
    ) {
        Crossfade(targetState = style, animationSpec = tween(260), label = "playerStyle") { shown ->
            when (shown) {
                PlayerStyle.Turntable -> TurntablePlayer(song)
                PlayerStyle.Poster -> PosterPlayer(song)
                PlayerStyle.Timeline -> TimelinePlayer(song, lyrics = false)
                PlayerStyle.Lyrics -> TimelinePlayer(song, lyrics = true)
                PlayerStyle.Video -> VideoPlayer(song, expanded = sheet.isExpanded)
                PlayerStyle.Bedside -> BedsidePlayer(song, expanded = sheet.isExpanded)
                PlayerStyle.BedsideTiles -> BedsideTilesPlayer(song, expanded = sheet.isExpanded)
                PlayerStyle.Shiny, PlayerStyle.Soft -> Unit
            }
        }
    }
}
