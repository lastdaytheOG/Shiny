package com.shiny.music.ui.liquid.player.styles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.shiny.music.shinymusic.AudioDeviceBottomSheet
import com.shiny.music.ui.liquid.player.LiquidLyricsPane
import com.shiny.music.ui.liquid.player.LiquidQueuePane
import com.shiny.music.ui.liquid.player.NowPlayingUtilityRow
import com.shiny.music.ui.liquid.player.styles.ring.StyleMenu
import com.shiny.music.ui.liquid.player.styles.ring.StyleTones as RingTones
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

@Composable
internal fun AlternatePlayer(style: PlayerStyle, sheet: BottomSheetState, navController: NavController) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    val metadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canNext by playerConnection.canSkipNext.collectAsState()

    var mode by rememberSaveable { mutableStateOf(StyleMode.Stage) }
    var paneMode by remember { mutableStateOf(StyleMode.Lyrics) }
    if (mode != StyleMode.Stage) paneMode = mode
    var menuOpen by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }
    LaunchedEffect(sheet.isExpanded) { if (!sheet.isExpanded) menuOpen = false }

    BackHandler(enabled = sheet.isExpanded && menuOpen) { menuOpen = false }
    BackHandler(enabled = sheet.isExpanded && !menuOpen && mode != StyleMode.Stage) { mode = StyleMode.Stage }
    BackHandler(enabled = sheet.isExpanded && !menuOpen && mode == StyleMode.Stage) { sheet.collapseSoft() }

    DisposableEffect(sheet.isExpanded) {
        val window = (context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val wasLight = controller?.isAppearanceLightStatusBars
        if (sheet.isExpanded) controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (wasLight != null && sheet.isExpanded) controller.isAppearanceLightStatusBars = wasLight
        }
    }

    val wordsLed = style == PlayerStyle.Lyrics || style == PlayerStyle.Dial || style == PlayerStyle.Poster || style == PlayerStyle.PosterRing || style == PlayerStyle.Video || style.bedside
    val clock = rememberStyleClock(sheet.isExpanded, isPlaying, metadata, fast = wordsLed || mode == StyleMode.Lyrics)
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
    val editPlayer = LocalEditPlayer.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tones.light, tones.dark)))
    ) {
        val posterLike = style == PlayerStyle.Poster || style == PlayerStyle.PosterRing
        CompositionLocalProvider(LocalStyleBottomReserve provides if (posterLike) 0.dp else StyleRowHeight) {
            Crossfade(targetState = style, animationSpec = tween(260), label = "playerStyle") { shown ->
                when (shown) {
                    PlayerStyle.Turntable -> TurntablePlayer(song)
                    PlayerStyle.Poster -> PosterPlayer(song)
                    PlayerStyle.PosterRing -> {
                        val lines = rememberSyncedLines(metadata)
                        val sung = remember(lines) { lines.filter { !it.isBackground } }
                        com.shiny.music.ui.liquid.player.styles.ring.RingPosterPlayer(
                            metadata = metadata,
                            lines = sung,
                            showing = { sheet.progress > 0f && mode == StyleMode.Stage },
                        )
                    }
                    PlayerStyle.Dial -> DialPlayer(song)
                    PlayerStyle.Timeline -> TimelinePlayer(song, lyrics = false)
                    PlayerStyle.Lyrics -> TimelinePlayer(song, lyrics = true)
                    PlayerStyle.Video -> VideoPlayer(song, expanded = sheet.isExpanded)
                    PlayerStyle.Bedside -> BedsidePlayer(song, expanded = sheet.isExpanded)
                    PlayerStyle.BedsideTiles -> BedsideTilesPlayer(song, expanded = sheet.isExpanded)
                    PlayerStyle.Shiny, PlayerStyle.Soft -> Unit
                }
            }
        }

        val paneAlpha by animateFloatAsState(if (mode == StyleMode.Stage) 0f else 1f, tween(240), label = "stylePane")
        if (paneAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = paneAlpha }
                    .background(Brush.verticalGradient(listOf(tones.dark.copy(alpha = 0.94f), Color.Black.copy(alpha = 0.96f))))
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                val padding = PaddingValues(top = statusTop + 72.dp, bottom = navBottom + (if (posterLike) 0.dp else StyleRowHeight) + 16.dp)
                when (paneMode) {
                    StyleMode.Lyrics -> LiquidLyricsPane(
                        metadata = metadata,
                        positionProvider = { clock.positionMs },
                        onSeek = seekWithClock.seek,
                        contentPadding = padding,
                        nestedScrollConnection = sheet.preUpPostDownNestedScrollConnection,
                        horizontalPadding = 8.dp,
                    )
                    StyleMode.Queue -> LiquidQueuePane(
                        contentPadding = padding,
                        nestedScrollConnection = sheet.preUpPostDownNestedScrollConnection,
                        backdrop = null,
                        compact = true,
                    )
                    StyleMode.Stage -> Unit
                }
            }
        }

        if (!posterLike) NowPlayingUtilityRow(
            lyricsActive = mode == StyleMode.Lyrics,
            queueActive = mode == StyleMode.Queue,
            onLyrics = { mode = if (mode == StyleMode.Lyrics) StyleMode.Stage else StyleMode.Lyrics },
            onOutput = { showOutput = true },
            onQueue = { mode = if (mode == StyleMode.Queue) StyleMode.Stage else StyleMode.Queue },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 44.dp, end = 44.dp, bottom = navBottom + 8.dp),
        )

        StyleMenu(
            open = menuOpen,
            onOpenChange = { menuOpen = it },
            tones = RingTones(),
            onOtherMenu = actions.more,
            onEditPlayer = editPlayer,
            end = 18.dp,
            onLyrics = if (posterLike) ({ mode = if (mode == StyleMode.Lyrics) StyleMode.Stage else StyleMode.Lyrics }) else null,
            lyricsActive = mode == StyleMode.Lyrics,
            onQueue = if (posterLike) ({ mode = if (mode == StyleMode.Queue) StyleMode.Stage else StyleMode.Queue }) else null,
            queueActive = mode == StyleMode.Queue,
            edge = statusTop + 6.dp,
            buttonSize = 43.dp,
            pill = true,
            showRepeat = style != PlayerStyle.Dial,
        )
    }

    if (showOutput) {
        AudioDeviceBottomSheet(onDismiss = { showOutput = false })
    }
}

internal enum class StyleMode { Stage, Lyrics, Queue }

internal val StyleRowHeight = 56.dp

internal val LocalStyleBottomReserve = compositionLocalOf { 0.dp }

val LocalEditPlayer = compositionLocalOf<() -> Unit> { {} }
