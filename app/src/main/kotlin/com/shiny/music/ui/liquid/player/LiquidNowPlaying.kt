package com.shiny.music.ui.liquid.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.shiny.music.constants.TogetherPlayerReactionsKey
import com.shiny.music.together.TogetherSession
import com.shiny.music.ui.liquid.together.LocalTogether
import com.shiny.music.ui.liquid.together.TogetherPlayerBadge
import com.shiny.music.ui.liquid.together.TogetherReactionsLayer
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.constants.PlayerBackgroundStyle
import com.shiny.music.extensions.metadata
import com.shiny.music.shinymusic.AudioDeviceBottomSheet
import com.shiny.music.ui.component.BottomSheetState
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidBackdrop
import com.shiny.music.ui.menu.PlayerMenu
import com.shiny.music.ui.player.ArtworkAnchor
import com.shiny.music.ui.player.playerArtworkAnchor
import com.shiny.music.ui.player.rememberPlayerBackdrop
import com.shiny.music.ui.player.PlayerBackdrop
import com.shiny.music.ui.liquid.appearance.ArtworkPresentation
import com.shiny.music.ui.liquid.appearance.Atmosphere
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.appearance.atmosphereSaturation
import com.shiny.music.ui.liquid.appearance.atmosphereScrim
import com.shiny.music.ui.liquid.appearance.posterTopScrim
import com.shiny.music.ui.liquid.appearance.glowPauseFactor
import com.shiny.music.ui.liquid.appearance.posterMelt
import com.shiny.music.ui.liquid.appearance.strength
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.utils.rememberPreference
import com.shiny.music.constants.KeepScreenOn
import androidx.compose.ui.unit.IntSize
import com.shiny.music.constants.DataSaverEnabledKey
import com.shiny.music.artwork.ArtworkMatcher
import com.shiny.music.ui.liquid.appearance.posterCoverHeight
import androidx.compose.ui.platform.LocalConfiguration
import com.shiny.music.ui.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class NowPlayingMode { Stage, Lyrics, Queue }

/**
 * The full-screen player, riding [state]: a card that rises from the mini player, follows
 * the finger when pulled down, and dims the app behind it as it comes up.
 */
@Composable
fun LiquidPlayerSheet(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    LocalPlayerConnection.current ?: return
    val density = LocalDensity.current

    Box(modifier.fillMaxSize()) {
        if (state.isCollapsed) return@Box

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.5f))
        )

        val velocityTracker = remember { VelocityTracker() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (state.expandedBound - state.value).toPx().coerceAtLeast(0f)
                    val p = state.progress.coerceIn(0f, 1f)
                    val r = 34.dp.toPx() * ((1f - p) / 0.06f).coerceIn(0f, 1f)
                    shape = RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = 0f, bottomStart = 0f)
                    clip = true
                }
                .pointerInput(state) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            velocityTracker.addPointerInputChange(change)
                            state.dispatchRawDelta(dragAmount)
                        },
                        onDragCancel = {
                            velocityTracker.resetTracking()
                            state.snapTo(state.expandedBound)
                        },
                        onDragEnd = {
                            val velocity = -velocityTracker.calculateVelocity().y
                            velocityTracker.resetTracking()
                            state.performFling(velocity, null)
                        },
                    )
                },
        ) {
            NowPlaying(sheet = state, navController = navController)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlaying(
    sheet: BottomSheetState,
    navController: NavController,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val together = LocalTogether.current
    val togetherState by (together?.state?.collectAsState() ?: remember { mutableStateOf(TogetherSession.State()) })
    // A guest the host hasn't handed control to still has a pause (for this phone) and a
    // next (a vote to skip) while following; as a remote, the controls are the host's.
    val controlsEnabled = !(togetherState.isGuest && !togetherState.canControl && !togetherState.following)
    val floatReactions by rememberPreference(TogetherPlayerReactionsKey, true)
    val openTogether = {
        sheet.collapseSoft()
        navController.navigate("together")
    }

    val metadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    var mode by rememberSaveable { mutableStateOf(NowPlayingMode.Stage) }
    val modeProgress = remember { Animatable(if (mode == NowPlayingMode.Stage) 0f else 1f) }
    LaunchedEffect(mode) {
        modeProgress.animateTo(
            if (mode == NowPlayingMode.Stage) 0f else 1f,
            spring(dampingRatio = 0.86f, stiffness = 280f),
        )
    }
    BackHandler(enabled = sheet.isExpanded && mode != NowPlayingMode.Stage) { mode = NowPlayingMode.Stage }
    BackHandler(enabled = sheet.isExpanded && mode == NowPlayingMode.Stage) { sheet.collapseSoft() }

    // The player background is always dark, so the status bar icons turn light while it is up.
    DisposableEffect(sheet.isExpanded) {
        val window = (context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val wasLight = controller?.isAppearanceLightStatusBars
        if (sheet.isExpanded) controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (wasLight != null && sheet.isExpanded) controller.isAppearanceLightStatusBars = wasLight
        }
    }

    // Position: polled from the player — the one authority on where playback is — and
    // published as plain numbers the scrubber and the lyrics read in their own draw and
    // effect scopes. Everything downstream anchors to this; nothing keeps a clock of its
    // own that could drift away from the audio.
    val position = remember { mutableLongStateOf(0L) }
    val duration = remember { mutableLongStateOf(0L) }
    // Keyed on the track as well as the surface state. Without the track in the key, a
    // song change left the last poll's value standing for up to half a second — the
    // previous track's position, read against the new track's lyrics, which resolved to
    // the last line and sent the lyrics page to the very bottom of a song that had not
    // started yet. From there nothing brought it back, because a position before the
    // first line produced no line change to scroll to.
    LaunchedEffect(sheet.isExpanded, isPlaying, mode, metadata?.id) {
        while (isActive) {
            position.longValue = playerConnection.player.currentPosition
            duration.longValue = playerConnection.player.duration.takeIf { it > 0 } ?: ((metadata?.duration ?: 0) * 1000L)
            delay(if (!sheet.isExpanded) 500L else if (mode == NowPlayingMode.Lyrics) 80L else 200L)
        }
    }
    val positionProvider = remember { { position.longValue } }
    val durationProvider = remember { { duration.longValue } }
    val onSeek: (Long) -> Unit = remember {
        { target ->
            position.longValue = target
            playerConnection.seekTo(target)
        }
    }

    var showOutputSheet by remember { mutableStateOf(false) }
    val motionPref by rememberPreference(LiquidPrefs.PlayerMotion, true)
    val breathePref by rememberPreference(LiquidPrefs.ArtworkBreathe, true)
    val livingPref by rememberPreference(LiquidPrefs.LivingArtwork, LiquidPrefs.LivingArtworkDefault)
    val motionVideoPref by rememberPreference(LiquidPrefs.MotionArtwork, true)
    val showVolume by rememberPreference(LiquidPrefs.ShowVolume, true)
    val keepScreenOn by rememberPreference(KeepScreenOn, false)
    DisposableEffect(keepScreenOn, isPlaying, sheet.isExpanded) {
        val window = (context as? android.app.Activity)?.window
        if (keepScreenOn && isPlaying && sheet.isExpanded) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    if (showOutputSheet) {
        AudioDeviceBottomSheet(onDismiss = { showOutputSheet = false })
    }

    val liked = currentSong?.song?.liked == true
    val haptic = LocalHapticFeedback.current
    val artUrl = metadata?.thumbnailUrl
    val appearance = LocalShinyAppearance.current

    val posterChosen = appearance.artwork == ArtworkPresentation.Poster
    val screenPx = remember(context) {
        context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }
    }
    // A cover is asked for at about the number of pixels it covers: fewer would be drawn
    // enlarged, and more cannot be shown.
    val coverPx = remember(screenPx) { ArtworkMatcher.sizeFor(screenPx) }

    // ---- Apple's artwork (see the artwork package) -----------------------------------------
    // The song's own artwork is what shows first, always. A song that only has a music
    // video's still gets its real cover once that has been found and is ready to draw.
    val appleCoversPref by rememberPreference(LiquidPrefs.AppleCovers, true)
    val dataSaver by rememberPreference(DataSaverEnabledKey, false)
    val appleCover = rememberAppleCover(
        metadata = metadata,
        next = {
            val player = playerConnection.player
            val index = player.nextMediaItemIndex
            if (index in 0 until player.mediaItemCount) player.getMediaItemAt(index).metadata else null
        },
        enabled = appleCoversPref,
        sizePx = coverPx,
    )
    // The Poster hangs an album's portrait artwork edge to edge: the picture Apple made for
    // the head of a phone, in its own shape, at the screen's own pixels, with its clip playing
    // over its still. Only that. A square sleeve was never made to stand there (hung that way
    // its foot has to be dissolved, and a cover is drawn to its edges), so a song whose album
    // has no portrait artwork keeps the card, exactly as with the Poster off. Until the
    // portrait artwork is ready the stage is the card too, and the change from one to the
    // other is a short dip of the whole stage rather than a jump.
    val portrait = LocalConfiguration.current.let { it.screenWidthDp <= it.screenHeightDp }
    val stageMotion = rememberMotionArtwork(
        metadata,
        enabled = posterChosen && portrait && !dataSaver && (motionVideoPref || appleCoversPref),
    )
    val tallReady = rememberTallPoster(stageMotion, enabled = posterChosen && portrait, widthPx = screenPx)
    var tallPoster by remember(metadata?.id) { mutableStateOf(tallReady) }
    val stageDip = remember { Animatable(1f) }
    LaunchedEffect(tallReady, metadata?.id) {
        if (tallReady != tallPoster) {
            stageDip.animateTo(0f, tween(200))
            tallPoster = tallReady
        }
        // Always back up, whatever interrupted the way down.
        if (stageDip.value < 1f) stageDip.animateTo(1f, tween(340))
    }
    // The portrait artwork is known of but its picture has not arrived yet. Not waited for for
    // ever: a picture that will not load leaves the card, and the card its own clip.
    var posterPatience by remember(metadata?.id) { mutableStateOf(true) }
    LaunchedEffect(metadata?.id) {
        delay(4_000)
        posterPatience = false
    }
    val awaitingPoster = posterChosen && portrait && posterPatience && tallPoster == null && stageMotion?.tallAnimated != null
    val stageCover = tallPoster ?: appleCover
    val hiRes = remember(artUrl, stageCover) { stageCover?.large ?: artUrl?.resize(1080, 1080) }
    // What the stage's colours are read from: the cover that is actually on it.
    val stageArtUrl = stageCover?.small ?: artUrl

    // Glass buttons in the player refract the player's own background, never the app.
    val playerBackdrop = rememberLayerBackdrop()

    // One extraction per artwork feeds both the background and the glow behind the cover.
    val artworkBackdrop = rememberNowPlayingBackdrop(stageArtUrl)
    val glowColor = rememberArtworkGlowColor(artworkBackdrop, appearance.glow)

    // The poster is the album's portrait artwork; without it the stage is the card.
    val wantsPoster = posterChosen && tallPoster != null
    // Light spilling past the cover's edge only means anything while the cover has an edge.
    val glowStrength = if (wantsPoster) 0f else appearance.glow.strength

    // What still picture the stage is given, and where it came from (adb logcat -s Artwork).
    LaunchedEffect(metadata?.id, hiRes, wantsPoster) {
        val title = metadata?.title ?: return@LaunchedEffect
        val from = when {
            tallPoster != null -> "Apple's portrait still, ${"%.3f".format(tallPoster?.aspect)} wide:high"
            appleCover != null -> "Apple's cover"
            else -> "the song's own artwork"
        }
        com.shiny.music.artwork.Artworks.log(
            "stage     '$title' shows ${hiRes ?: artUrl} ($from) as ${if (wantsPoster) "POSTER" else "CARD"}"
        )
    }

    val containerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { containerCoords[0] = it }) {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val width = maxWidth
        val height = maxHeight
        val stageSize = minOf(width - 56.dp, height * 0.43f)

        var stageRect by remember { mutableStateOf<Rect?>(null) }
        var compactRect by remember { mutableStateOf<Rect?>(null) }
        var controlsTop by remember { mutableFloatStateOf(0f) }

        // ---- Poster: portrait artwork at the head of the player, its foot going out of focus
        // into its own colours. Portrait only: landscape already gives the cover a whole half
        // of the window. The picture hangs without a card: no shadow, no rounding, and no
        // settling back on pause. `FlyingArtwork` measures its child to whatever rect it is
        // given, so the rect is the entire change of shape: no second artwork, no second
        // flight, no change to the transition into lyrics or the queue.
        val poster = wantsPoster && width <= height
        val posterAspect = tallPoster?.aspect ?: 1f
        val posterRect = remember(width, height, posterAspect, density) {
            with(density) { Rect(0f, 0f, width.toPx(), posterCoverHeight(width.toPx(), height.toPx(), posterAspect)) }
        }
        var posterTitleTop by remember { mutableFloatStateOf(0f) }
        val posterGeometry = if (poster) {
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            // Measured once the title has been laid out; until then, where it is about to be.
            val titleTop = if (posterTitleTop > 0f) {
                posterTitleTop
            } else {
                with(density) {
                    val controls = if (controlsTop > 0f) controlsTop else heightPx - (ControlsBlockHeight + bottomInset + 14.dp).toPx()
                    controls - (6.dp + PosterTitleHeight).toPx()
                }
            }
            remember(widthPx, heightPx, titleTop, posterRect, density) {
                val melt = posterMelt(coverSide = posterRect.height, titleTop = titleTop, gap = with(density) { 10.dp.toPx() })
                PosterGeometry(widthPx, heightPx, posterRect.height, melt.start, melt.end, titleTop)
            }
        } else {
            null
        }
        val posterStage = rememberPosterStage(
            artworkUrl = stageArtUrl,
            geometry = posterGeometry,
            atmosphere = appearance.atmosphere,
            amoled = appearance.amoled,
            enabled = posterChosen,
        )
        // Both read in the draw phase only. The ground is there as soon as it has been made,
        // and leaves with the stage when the lyrics or the queue take over. The veil waits
        // for the cover to land: while the sheet is still rising, the copy of the cover that
        // flies up from the mini player is drawn over all of this, and a foot already melted
        // would snap soft the moment the real cover took its place.
        val posterPresence = animateFloatAsState(if (poster && posterStage.ready) 1f else 0f, tween(380), label = "posterPresence")
        val posterSettle = animateFloatAsState(if (sheet.isExpanded) 1f else 0f, tween(420), label = "posterSettle")
        val posterGroundAlpha = remember(posterPresence) {
            { posterPresence.value * stageDip.value * (1f - modeProgress.value * 2.4f).coerceIn(0f, 1f) }
        }
        val posterVeilAlpha = remember(posterGroundAlpha, posterSettle) {
            { posterGroundAlpha() * posterSettle.value }
        }

        // ---- Background -------------------------------------------------------------
        // Still while the lyrics are up: under lyrics that move every frame, the field's slow
        // turn made every frame redraw the backdrop each glass button refracts. The turn is
        // imperceptible behind the words; the cost was dropped frames (phone, 2026-09-30).
        val fieldPlaying = isPlaying && sheet.isExpanded && motionPref && mode != NowPlayingMode.Lyrics
        if (posterChosen) {
            // The poster's ground is part of the background the glass refracts, so a glass
            // button over the cover shows the cover's own blur and not some other picture.
            Box(Modifier.fillMaxSize().layerBackdrop(playerBackdrop)) {
                NowPlayingBackground(
                    backdrop = artworkBackdrop,
                    // Under the ground the field cannot be seen: there it neither turns nor draws.
                    playing = fieldPlaying && !(poster && mode == NowPlayingMode.Stage),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (posterGroundAlpha() >= 0.999f) 0f else 1f },
                )
                if (poster) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind { with(posterStage) { drawPosterGround(posterGroundAlpha()) } }
                    )
                }
            }
        } else {
            NowPlayingBackground(
                backdrop = artworkBackdrop,
                playing = fieldPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(playerBackdrop),
            )
        }

        if (width > height) {
            // iOS 27 landscape: the artwork on one side, the controls — or the lyrics
            // and queue — on the other.
            CompositionLocalProvider(LocalLiquidBackdrop provides playerBackdrop) {
                NowPlayingLandscape(
                    title = metadata?.title.orEmpty(),
                    artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
                    artwork = hiRes ?: artUrl,
                    thumbnail = artUrl,
                    metadata = metadata,
                    living = livingPref,
                    motionVideos = motionVideoPref,
                    glowColor = glowColor,
                    glowStrength = glowStrength,
                    liked = liked,
                    isPlaying = isPlaying,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    controlsEnabled = controlsEnabled,
                    mode = mode,
                    onMode = { mode = it },
                    positionProvider = positionProvider,
                    durationProvider = durationProvider,
                    onSeek = onSeek,
                    onLike = { playerConnection.toggleLike() },
                    onMore = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = metadata,
                                navController = navController,
                                playerBottomSheetState = sheet,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    onOutput = { showOutputSheet = true },
                    onPrevious = { playerConnection.seekToPrevious() },
                    onPlayPause = { playerConnection.togglePlayPause() },
                    onNext = { playerConnection.seekToNext() },
                    lyricsPane = { padding ->
                        LiquidLyricsPane(
                            metadata = metadata,
                            positionProvider = positionProvider,
                            onSeek = { if (controlsEnabled) onSeek(it) },
                            contentPadding = padding,
                            nestedScrollConnection = sheet.preUpPostDownNestedScrollConnection,
                            horizontalPadding = 8.dp,
                        )
                    },
                    queuePane = { padding ->
                        LiquidQueuePane(
                            contentPadding = padding,
                            nestedScrollConnection = sheet.preUpPostDownNestedScrollConnection,
                            backdrop = playerBackdrop,
                            enabled = controlsEnabled,
                        )
                    },
                )
            }
            return@BoxWithConstraints
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides playerBackdrop) {
            // The grabber and the Listen Together badge, at the head of the player. A cover
            // that reaches the top of the screen would lie over them where they have always
            // been drawn, so for those presentations they are drawn after it instead.
            val stageHead: @Composable () -> Unit = {
                Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) { Grabber() }
                if (togetherState.isLive && together != null) {
                    TogetherPlayerBadge(
                        session = together,
                        state = togetherState,
                        onOpen = openTogether,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .graphicsLayer { alpha = (1f - modeProgress.value * 2.2f).coerceIn(0f, 1f) },
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = topInset),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!poster) {
                    stageHead()
                    Spacer(Modifier.weight(0.4f))
                    Box(
                        Modifier
                            .size(stageSize)
                            .onGloballyPositioned { c -> stageRect = c.rectIn(containerCoords[0]) }
                    )
                    Spacer(Modifier.height(30.dp))
                    StageTitleRow(
                        title = metadata?.title.orEmpty(),
                        artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
                        liked = liked,
                        onLike = { playerConnection.toggleLike() },
                        onMore = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = metadata,
                                    navController = navController,
                                    playerBottomSheetState = sheet,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        playing = isPlaying,
                        onArtist = {
                            metadata?.artists?.firstOrNull()?.id?.let { id ->
                                sheet.collapseSoft()
                                navController.navigate("artist/$id")
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 28.dp)
                            .graphicsLayer {
                                val p = modeProgress.value
                                alpha = (1f - p * 2.2f).coerceIn(0f, 1f) * stageDip.value
                                translationY = -24.dp.toPx() * p
                            },
                    )
                    Spacer(Modifier.weight(0.6f))
                    // The controls block reserves its space here; the real one is drawn on top
                    // so it can also sit over the lyrics and queue.
                    val reserved = if (controlsTop > 0f) (height - with(density) { controlsTop.toDp() }) else ControlsBlockHeight + bottomInset + 14.dp
                    Spacer(Modifier.height(reserved.coerceAtLeast(0.dp)))
                }
            }

            val headerTop = topInset + 26.dp + 8.dp
            val contentTop = headerTop + 72.dp
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = modeProgress.value
                        alpha = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                    },
            ) {
                if (modeProgress.value > 0.001f || mode != NowPlayingMode.Stage) {
                    val paneBottom = (height - with(density) { controlsTop.toDp() }).coerceAtLeast(0.dp)
                    val listPadding = PaddingValues(top = 18.dp, bottom = 56.dp)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = contentTop, bottom = paneBottom)
                            .graphicsLayer {
                                translationY = 60.dp.toPx() * (1f - modeProgress.value)
                            },
                    ) {
                        // Each pane keeps its own saved state — chiefly its scroll position
                        // — while it is off screen. Crossfade disposes the pane it leaves,
                        // and without this holder the lyrics came back at the very top and
                        // then visibly slid down to the current line every time the user
                        // looked at the queue and back. Purely a state-retention wrapper:
                        // the Crossfade, its timing and the panes themselves are unchanged.
                        val paneStateHolder = rememberSaveableStateHolder()
                        Crossfade(targetState = mode, label = "pane") { pane ->
                            paneStateHolder.SaveableStateProvider(pane) {
                            when (pane) {
                                NowPlayingMode.Lyrics -> LiquidLyricsPane(
                                    metadata = metadata,
                                    positionProvider = positionProvider,
                                    onSeek = { if (controlsEnabled) onSeek(it) },
                                    contentPadding = listPadding,
                                    nestedScrollConnection = sheet.preUpPostDownNestedScrollConnection,
                                )

                                // The lyrics fade their own lines at the edges; the queue, which
                                // only moves when it is scrolled, keeps the mask.
                                NowPlayingMode.Queue -> Box(Modifier.fillMaxSize().fadingTopEdge()) {
                                    LiquidQueuePane(
                                        contentPadding = listPadding,
                                        nestedScrollConnection = sheet.preUpPostDownNestedScrollConnection,
                                        backdrop = playerBackdrop,
                                        enabled = controlsEnabled,
                                    )
                                }

                                NowPlayingMode.Stage -> Box(Modifier.fillMaxSize())
                            }
                            }
                        }
                    }
                }

                CompactHeader(
                    title = metadata?.title.orEmpty(),
                    artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
                    liked = liked,
                    onLike = { playerConnection.toggleLike() },
                    onMore = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = metadata,
                                navController = navController,
                                playerBottomSheetState = sheet,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    onArtworkSlot = { c -> compactRect = c.rectIn(containerCoords[0]) },
                    onClick = { mode = NowPlayingMode.Stage },
                    modifier = Modifier
                        .padding(top = headerTop)
                        .padding(horizontal = 20.dp),
                )
            }

            FlyingArtwork(
                model = hiRes ?: artUrl,
                // The copy that flies up from the mini player lands on this same picture.
                thumbModel = stageCover?.large ?: artUrl,
                stageRect = if (poster) posterRect else stageRect,
                compactRect = compactRect,
                progress = { modeProgress.value },
                playing = isPlaying || !breathePref,
                metadata = metadata,
                animate = isPlaying && sheet.isExpanded && mode == NowPlayingMode.Stage,
                // The Poster is hung at its own pixels and promises never to be enlarged, so the
                // drift and zoom of a living cover are not put on it.
                living = livingPref && !poster,
                motionVideos = motionVideoPref,
                // While the Poster's own picture is on its way, the square clip is not started
                // in its place only to be dropped a moment later for the portrait one.
                videoVisible = sheet.isExpanded && !awaitingPoster,
                glowColor = glowColor,
                glowStrength = glowStrength,
                bleed = poster,
                tall = poster,
                // A clip plays at the pixels it covers: the poster's, or the card's on the stage.
                sharpPx = if (poster) {
                    IntSize(posterRect.width.roundToInt(), posterRect.height.roundToInt())
                } else {
                    stageRect?.let { IntSize(it.width.roundToInt(), it.height.roundToInt()) }
                },
                // The picture changing to Apple's sharper cover eases from the one it replaces.
                dissolve = poster || appleCover != null,
                coverAlpha = { stageDip.value },
                onTap = { if (mode != NowPlayingMode.Stage) mode = NowPlayingMode.Stage },
                // On the stage only: in the lyrics and queue the cover is a small header
                // whose tap must stay immediate, and whose row sits over scrolling content.
                onSwipeRight = if (mode == NowPlayingMode.Stage && controlsEnabled) ({
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    playerConnection.seekToNext()
                }) else null,
                onDoubleTap = if (mode == NowPlayingMode.Stage) ({
                    // Likes, never un-likes: a double tap is a gesture of approval.
                    if (!liked) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        playerConnection.toggleLike()
                    }
                }) else null,
            )

            // ---- Poster: the veil over the picture's foot, then the title -----------------
            //
            // Drawn after the picture and before the controls. Both fade out as the picture
            // leaves for the lyrics or the queue, where the blurred field takes the stage back.
            if (poster) {
                val topScrim = remember(appearance.atmosphere) { posterTopScrim(appearance.atmosphere) }
                val stageAlpha = { (1f - modeProgress.value * 2.4f).coerceIn(0f, 1f) }
                // The poster darkens nothing over the cover: its ground carries its own
                // shade, under the type only. What is drawn here is the veil that takes
                // the cover's foot out of focus, and a little shade at the very head for
                // the grabber and the status bar. Two draws, no layer.
                val headPx = with(density) { (topInset + 46.dp).toPx() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val head = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = topScrim),
                                1f to Color.Transparent,
                                endY = headPx,
                            )
                            onDrawBehind {
                                with(posterStage) { drawPosterVeil(posterVeilAlpha()) }
                                drawRect(head, size = Size(size.width, headPx), alpha = stageAlpha())
                            }
                        }
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = topInset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { stageHead() }
                val controlsHeight = if (controlsTop > 0f) {
                    (height - with(density) { controlsTop.toDp() }).coerceAtLeast(0.dp)
                } else {
                    ControlsBlockHeight + bottomInset + 14.dp
                }
                // The poster's dissolve is laid out against where the title actually begins,
                // so this box reports it. The fade and the lift on the way to the lyrics are
                // on the row inside, where they cannot move what is measured here.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = controlsHeight + 6.dp)
                        .padding(horizontal = 28.dp)
                        .then(
                            if (poster) {
                                Modifier.onGloballyPositioned { c ->
                                    val top = c.rectIn(containerCoords[0]).top
                                    if (kotlin.math.abs(top - posterTitleTop) > 0.5f) posterTitleTop = top
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    StageTitleRow(
                        title = metadata?.title.orEmpty(),
                        artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
                        liked = liked,
                        onLike = { playerConnection.toggleLike() },
                        onMore = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = metadata,
                                    navController = navController,
                                    playerBottomSheetState = sheet,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        playing = isPlaying,
                        onArtist = {
                            metadata?.artists?.firstOrNull()?.id?.let { id ->
                                sheet.collapseSoft()
                                navController.navigate("artist/$id")
                            }
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                // With the cover, through the change between card and poster.
                                alpha = stageAlpha() * stageDip.value
                                translationY = -24.dp.toPx() * modeProgress.value
                            },
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onGloballyPositioned { c -> controlsTop = c.rectIn(containerCoords[0]).top }
                    .padding(horizontal = 28.dp)
                    .padding(bottom = bottomInset + 14.dp),
            ) {
                LiquidScrubber(
                    positionProvider = positionProvider,
                    durationProvider = durationProvider,
                    onSeek = onSeek,
                    enabled = controlsEnabled,
                )
                Spacer(Modifier.height(20.dp))
                LiquidTransport(
                    isPlaying = isPlaying,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    onPrevious = { playerConnection.seekToPrevious() },
                    onPlayPause = { playerConnection.togglePlayPause() },
                    onNext = { playerConnection.seekToNext() },
                    enabled = controlsEnabled,
                )
                if (showVolume) {
                    Spacer(Modifier.height(18.dp))
                    LiquidVolumeSlider()
                }
                Spacer(Modifier.height(20.dp))
                NowPlayingUtilityRow(
                    lyricsActive = mode == NowPlayingMode.Lyrics,
                    queueActive = mode == NowPlayingMode.Queue,
                    onLyrics = { mode = if (mode == NowPlayingMode.Lyrics) NowPlayingMode.Stage else NowPlayingMode.Lyrics },
                    onOutput = { showOutputSheet = true },
                    onQueue = { mode = if (mode == NowPlayingMode.Queue) NowPlayingMode.Stage else NowPlayingMode.Queue },
                )
            }

            if (togetherState.isLive && floatReactions && together != null) {
                TogetherReactionsLayer(session = together, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** How far the stage artwork settles back while paused. */
private const val PausedArtworkScale = 0.82f

/** The height the controls block occupies, used to reserve room for it on the stage. */
private val ControlsBlockHeight: Dp = 30.dp + 18.dp + 14.dp + 104.dp + 12.dp + 30.dp + 16.dp + 48.dp

/** About how tall the title row is, for laying out the poster before the row has been measured. */
private val PosterTitleHeight: Dp = 56.dp

/** Fades the top of the lyrics / queue page into the header instead of cutting it. */
private fun Modifier.fadingTopEdge(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.06f to Color.Black,
                0.9f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun StageTitleRow(
    title: String,
    artist: String,
    liked: Boolean,
    onLike: () -> Unit,
    onMore: () -> Unit,
    onArtist: () -> Unit,
    playing: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = LiquidTypography.title3.copy(fontWeight = FontWeight.SemiBold),
                color = NowPlayingInk.primary,
                maxLines = 1,
                modifier = if (playing) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2600, repeatDelayMillis = 2600) else Modifier,
            )
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = artist,
                style = LiquidTypography.title3.copy(fontWeight = FontWeight.Normal),
                color = NowPlayingInk.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = onArtist),
            )
        }
        Spacer(Modifier.width(12.dp))
        GlassIconButton(
            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            onClick = onLike,
            size = 36.dp,
            iconSize = 19.dp,
            kind = GlassKind.Clear,
            tint = Color.White,
        )
        Spacer(Modifier.width(10.dp))
        GlassIconButton(
            icon = Icons.Rounded.MoreHoriz,
            onClick = onMore,
            size = 36.dp,
            iconSize = 21.dp,
            kind = GlassKind.Clear,
            tint = Color.White,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactHeader(
    title: String,
    artist: String,
    liked: Boolean,
    onLike: () -> Unit,
    onMore: () -> Unit,
    onArtworkSlot: (LayoutCoordinates) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .onGloballyPositioned { onArtworkSlot(it) }
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp)
                .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            Text(title, style = LiquidTypography.headline, color = NowPlayingInk.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = LiquidTypography.subheadline, color = NowPlayingInk.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GlassIconButton(
            icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            onClick = onLike,
            size = 34.dp,
            iconSize = 18.dp,
            kind = GlassKind.Clear,
            tint = Color.White,
        )
        Spacer(Modifier.width(10.dp))
        GlassIconButton(
            icon = Icons.Rounded.MoreHoriz,
            onClick = onMore,
            size = 34.dp,
            iconSize = 20.dp,
            kind = GlassKind.Clear,
            tint = Color.White,
        )
    }
}

/**
 * One artwork, laid out between the stage rect and the compact header rect by the mode
 * transition's progress. Its position is resolved at layout time, so the flight
 * relayouts one box per frame and recomposes nothing. On the stage it breathes with
 * playback — full size while playing, settling back when paused — like the Music app.
 */
/** How far a rightward swipe on the cover travels before it plays the next song. */
private val SwipeSkipDistance = 56.dp

/**
 * A rightward swipe plays the next song the moment it has travelled [SwipeSkipDistance]: no
 * waiting for the finger to lift, once per swipe. Horizontal only, so a vertical drag still
 * reaches the sheet and closes the player. Nothing moves or redraws while it tracks.
 */
private fun Modifier.swipeRightToSkip(onSwipeRight: () -> Unit): Modifier = composed {
    val latest by rememberUpdatedState(onSwipeRight)
    pointerInput(Unit) {
        val threshold = SwipeSkipDistance.toPx()
        var travelled = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f; fired = false },
            onHorizontalDrag = { change, amount ->
                change.consume()
                travelled += amount
                if (!fired && travelled > threshold) {
                    fired = true
                    latest()
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlyingArtwork(
    model: Any?,
    thumbModel: String?,
    stageRect: Rect?,
    compactRect: Rect?,
    progress: () -> Float,
    playing: Boolean,
    onTap: () -> Unit,
    metadata: com.shiny.music.models.MediaMetadata?,
    animate: Boolean,
    living: Boolean,
    motionVideos: Boolean,
    videoVisible: Boolean,
    glowColor: State<Color>,
    glowStrength: Float,
    /** Hung edge to edge, without a card: the Poster. */
    bleed: Boolean,
    tall: Boolean = false,
    sharpPx: IntSize? = null,
    dissolve: Boolean = false,
    coverAlpha: () -> Float = { 1f },
    onSwipeRight: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    // A full-poster cover does not settle back on pause: there is nothing behind it to reveal,
    // so the breathe would only show a band of background around the edges.
    val restScale by animateFloatAsState(
        targetValue = if (bleed || playing) 1f else PausedArtworkScale,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 210f),
        label = "artRest",
    )
    val interaction = remember { MutableInteractionSource() }
    Layout(
        content = {
            Box(
                Modifier
                    // Before the clipping layer, so the light spills past the cover's edge.
                    // It follows the cover's settle, dims with it on pause, and is gone
                    // by the time the cover reaches the lyrics / queue header.
                    .then(
                        if (glowStrength > 0f) {
                            Modifier.artworkGlow(
                                color = glowColor,
                                strength = {
                                    glowStrength *
                                        glowPauseFactor(restScale, PausedArtworkScale) *
                                        (1f - progress() * 2.5f).coerceAtLeast(0f)
                                },
                                sizeFactor = { lerp(restScale, 1f, progress()) },
                            )
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        val p = progress()
                        val s = lerp(restScale, 1f, p)
                        scaleX = s
                        scaleY = s
                        alpha = coverAlpha()
                        // The mode spring is underdamped, so it settles *through* its
                        // target: the flight is allowed that overshoot, but anything read
                        // off it that has a floor is not. An edge-to-edge picture rests at
                        // corner 0, and a corner a fraction of a pixel below it throws.
                        val t = p.coerceIn(0f, 1f)
                        // Hung edge to edge there is no card to raise: the shadow and the
                        // rounding arrive as the cover shrinks into the lyrics header, not before.
                        val restElevation = when {
                            bleed -> 0f
                            playing -> 30.dp.toPx()
                            else -> 14.dp.toPx()
                        }
                        shadowElevation = with(density) { lerp(restElevation, 6.dp.toPx(), t) }
                        val restCorner = if (bleed) 0f else 12.dp.toPx()
                        val corner = with(density) { lerp(restCorner, 8.dp.toPx(), t) }
                        shape = RoundedCornerShape(corner)
                        clip = true
                        ambientShadowColor = Color.Black
                        spotShadowColor = Color.Black
                    }
                    .playerArtworkAnchor(
                        anchor = ArtworkAnchor.Full,
                        model = thumbModel,
                        cornerRadius = if (bleed) 0.dp else 12.dp,
                        followsSheet = true,
                    )
                    .then(if (onSwipeRight != null) Modifier.swipeRightToSkip(onSwipeRight) else Modifier)
                    // Without onDoubleClick a tap fires at once; with it, only on the stage,
                    // where a single tap does nothing and so waiting for a second costs nothing.
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onTap,
                        onDoubleClick = onDoubleTap,
                    ),
            ) {
                LivingArtwork(
                    model = model,
                    metadata = metadata,
                    animate = animate,
                    living = living,
                    motionVideos = motionVideos,
                    videoVisible = videoVisible,
                    modifier = Modifier.fillMaxSize(),
                    dissolve = dissolve,
                    tall = tall,
                    sharpPx = sharpPx,
                )
            }
        },
    ) { measurables, constraints ->
        val s = stageRect
        val c = compactRect
        if (s == null) {
            val p = measurables.first().measure(Constraints.fixed(0, 0))
            return@Layout layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, 0) }
        }
        val t = if (c == null) 0f else progress()
        val target = c ?: s
        val left = lerp(s.left, target.left, t)
        val top = lerp(s.top, target.top, t)
        val w = lerp(s.width, target.width, t).roundToInt().coerceAtLeast(1)
        val h = lerp(s.height, target.height, t).roundToInt().coerceAtLeast(1)
        val placeable = measurables.first().measure(Constraints.fixed(w, h))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(left.roundToInt(), top.roundToInt())
        }
    }
}

/**
 * The Now Playing artwork, as the background and the glow read it: one decode, one
 * palette and one pre-blurred field per artwork, cached, and held on the previous
 * artwork's answer until the next one is ready.
 */
@Composable
internal fun rememberNowPlayingBackdrop(artworkUrl: String?): State<PlayerBackdrop> =
    rememberPlayerBackdrop(
        thumbnailUrl = artworkUrl,
        style = PlayerBackgroundStyle.APPLE_MUSIC,
        fallbackColor = Color(0xFF202024).toArgb(),
    )

/**
 * The artwork, dissolved into a field of its own colour: the soft blurred copy drawn huge
 * and turned very slowly while music plays — the Music app's living backdrop — under a
 * scrim deep enough for white type on any cover.
 */
@Composable
internal fun NowPlayingBackground(
    artworkUrl: String?,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    NowPlayingBackground(
        backdrop = rememberNowPlayingBackdrop(artworkUrl),
        playing = playing,
        modifier = modifier,
    )
}

/**
 * [NowPlayingBackground] over an already-remembered [backdrop].
 *
 * The Atmosphere setting decides how much of the artwork reaches the stage. Balanced is
 * the background Shiny has always drawn; Soft and Immersive only move the scrim and the
 * field's saturation, both fixed per artwork, so they cost nothing per frame. Off draws
 * what a song without artwork has always had — and on AMOLED, that is true black.
 */
@Composable
internal fun NowPlayingBackground(
    backdrop: State<PlayerBackdrop>,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalShinyAppearance.current
    val atmosphere = appearance.atmosphere
    val showField = atmosphere != Atmosphere.Off
    val current = backdrop.value

    // The field is a heavy blur, so its slow turn is stepped at ~25 fps: the steps are
    // invisible, and the window (and every glass button refracting it) redraws at less
    // than half the rate a per-frame animation would force.
    val drift = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing && showField) {
        if (playing && showField) {
            var last = withFrameMillis { it }
            while (isActive) {
                delay(40)
                val now = withFrameMillis { it }
                drift.floatValue = (drift.floatValue + (now - last) * 360f / 90_000f) % 360f
                last = now
            }
        }
    }
    val base = if (showField) current.gradient.firstOrNull() ?: NeutralStage else NeutralStage
    val balancedScrim = remember(base) { balancedScrimFor(base) }
    val scrim = atmosphereScrim(atmosphere, balancedScrim)
    val fieldFilter = remember(atmosphere) {
        atmosphereSaturation(atmosphere)?.let { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(it) }) }
    }
    // With no field on screen, AMOLED keeps the stage black instead of a lifted grey.
    val ground = if (appearance.amoled && (!showField || current.field == null)) Color.Black else lerp(base, Color.Black, 0.35f)

    // While the field is still — paused, motion off, or under the lyrics — it is drawn once into
    // a cached layer and every later frame reuses that image. Redrawing two screen-and-a-half
    // rotated bitmaps and the scrim cost the whole frame budget, so anything that moved over
    // the player (the lyrics, word by word) dropped frames (measured 2026-09-30).
    val still = !(playing && showField)
    Box(
        modifier
            .graphicsLayer { compositingStrategy = if (still) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .background(ground)
    ) {
        Crossfade(
            targetState = if (showField) current else PlayerBackdrop.Empty,
            animationSpec = tween(700),
            label = "npBackdrop",
        ) { b ->
            val field = b.field
            if (field != null) {
                // Drawn as oversized squares centred on the screen, so no angle of the slow
                // turn can expose a corner of the page behind them.
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val side = maxOf(maxWidth, maxHeight)
                    Image(
                        bitmap = field,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = fieldFilter,
                        modifier = Modifier
                            .requiredSize(side * 1.2f)
                            .graphicsLayer { rotationZ = drift.floatValue },
                    )
                    Image(
                        bitmap = field,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = fieldFilter,
                        modifier = Modifier
                            .requiredSize(side * 1.45f)
                            .graphicsLayer {
                                rotationZ = -drift.floatValue * 1.6f + 140f
                                alpha = 0.55f
                                // One bitmap draw, so applying the alpha to the draw itself is
                                // the same pixels as compositing a layer — without allocating
                                // and filling a 1.45x-screen offscreen buffer on every frame.
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                translationX = size.width * 0.08f
                            },
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = scrim * 0.7f),
                        0.5f to Color.Black.copy(alpha = scrim),
                        1f to Color.Black.copy(alpha = scrim + 0.14f),
                    )
                )
        )
    }
}

/** The stage colour of a song without artwork, before the scrim. */
private val NeutralStage = Color(0xFF2A2A30)

/**
 * The scrim Shiny draws over this artwork's own colour, from how bright that colour is.
 */
internal fun balancedScrimFor(base: Color): Float = if (base.luminance() > 0.45f) 0.42f else 0.26f

/**
 * This node's rect in [container]'s space. Both live inside the sheet's translated layer,
 * so measuring between them cancels the sheet's own movement out of the flight path.
 */
private fun LayoutCoordinates.rectIn(container: LayoutCoordinates?): Rect {
    val origin = if (container != null && container.isAttached && isAttached) {
        container.localPositionOf(this, Offset.Zero)
    } else {
        positionInRoot()
    }
    return Rect(origin, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))
}
