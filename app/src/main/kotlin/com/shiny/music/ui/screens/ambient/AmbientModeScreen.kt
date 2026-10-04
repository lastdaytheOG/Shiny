package com.shiny.music.ui.screens.ambient

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.extensions.togglePlayPause
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidBackdrop
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.appearance.PageTransitions
import com.shiny.music.ui.liquid.player.LiquidLyricsPane
import com.shiny.music.ui.liquid.player.LiquidScrubber
import com.shiny.music.ui.liquid.player.LiquidTransport
import com.shiny.music.ui.liquid.player.LivingArtwork
import com.shiny.music.ui.liquid.player.NowPlayingBackground
import com.shiny.music.ui.liquid.player.NowPlayingInk
import com.shiny.music.ui.liquid.player.rememberNowPlayingBackdrop
import com.shiny.music.ui.liquid.player.balancedScrimFor
import com.shiny.music.ui.player.PlayerBackdrop
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/** How long the controls stay after the last touch. */
private const val ControlsLingerMs = 4000L

/** Apple's ease-out: quick to answer, long and soft to settle. */
private val SettleEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private val CoverShape = RoundedCornerShape(14.dp)

/**
 * Ambient Mode: the phone propped up, the song's words filling the room.
 *
 * Lyrics lead. The cover, title and artist sit quietly to one side, and nothing else is on
 * screen until a tap: then the player's own scrubber and transport fade in beneath the title,
 * with a way out in the corner, and fade away again a few seconds after the last touch.
 *
 * It is the Now Playing lyric engine and background, not second copies — the same karaoke,
 * the same atmosphere. Lines soften into the backdrop at the top and bottom instead of being
 * cut by the screen edge. Horizontal swipes skip, vertical swipes change the volume.
 */
@Composable
fun AmbientModeScreen(navController: NavController) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val living by rememberPreference(LiquidPrefs.LivingArtwork, true)
    val motionCovers by rememberPreference(LiquidPrefs.MotionArtwork, true)
    val backdropMotion by rememberPreference(LiquidPrefs.PlayerMotion, true)
    val instant = LocalShinyAppearance.current.transitions == PageTransitions.Instant

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        var insetsController: WindowInsetsControllerCompat? = null
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { navController.popBackStack() }

    // The lyric pane and the scrubber run off the player's position; poll it at the rate the
    // lyrics need rather than on every frame.
    val position = remember { mutableLongStateOf(0L) }
    val duration = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying, mediaMetadata?.id) {
        while (isActive) {
            val player = playerConnection.player
            position.longValue = player.currentPosition
            duration.longValue = player.duration.takeIf { it > 0 } ?: ((mediaMetadata?.duration ?: 0) * 1000L)
            delay(100)
        }
    }
    val positionProvider = remember { { position.longValue } }
    val durationProvider = remember { { duration.longValue } }
    val onSeek: (Long) -> Unit = remember { { target -> playerConnection.seekTo(target) } }
    val noNestedScroll = remember { object : NestedScrollConnection {} }

    // Controls: shown by a tap, kept while a finger is down, gone a few seconds after the last
    // one lifts. Counting from the touch-down instead hid them under a long scrub, which
    // disabled the scrubber mid-drag and lost the seek.
    var controlsShown by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(controlsShown, touches, held) {
        if (controlsShown && !held) {
            delay(ControlsLingerMs)
            controlsShown = false
        }
    }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsShown) 1f else 0f,
        animationSpec = if (instant) snap() else tween(if (controlsShown) 320 else 520, easing = SettleEasing),
        label = "ambientControls",
    )
    // Hidden controls are left out, not drawn at zero opacity: the glass buttons would otherwise
    // keep re-recording their layers on every tick of the moving background.
    val controlsPresent by remember { derivedStateOf { controlsAlpha > 0f } }

    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    // Which way this drag goes, decided once it leaves the touch slop: sideways skips,
    // up and down is volume, never both.
    var dragSideways by remember { mutableStateOf<Boolean?>(null) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val playerBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Any tap shows the controls; a tap on empty space while they are up hides them.
            // Any touch at all keeps them from fading while it lasts.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touches++
                    held = true
                    var moved = false
                    var claimed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (!change.pressed) {
                                claimed = change.isConsumed
                                break
                            }
                        }
                    } finally {
                        held = false
                    }
                    // A lyric line seeks and brings the controls up; an empty tap toggles them.
                    if (!moved) controlsShown = if (claimed) true else !controlsShown
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                        dragSideways = null
                    },
                    onDragEnd = {
                        if (dragSideways == true && abs(dragX) > 150f) {
                            if (dragX > 0) playerConnection.seekToPrevious()
                            else playerConnection.seekToNext()
                        }
                        dragX = 0f
                        dragY = 0f
                        dragSideways = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                        if (dragSideways == null) dragSideways = abs(dragX) > abs(dragY)
                        if (dragSideways == false && abs(dragAmount.y) > 10f && abs(dragAmount.y) > abs(dragAmount.x)) {
                            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            if (dragAmount.y < 0 && current < max) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                            } else if (dragAmount.y > 0 && current > 0) {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                            }
                        }
                    },
                )
            },
    ) {
        val backdrop = rememberNowPlayingBackdrop(mediaMetadata?.thumbnailUrl)
        NowPlayingBackground(
            backdrop = backdrop,
            playing = isPlaying && backdropMotion,
            modifier = Modifier.fillMaxSize().layerBackdrop(playerBackdrop),
        )
        // A bright cover would wash the words out: the lyric side is darkened just enough to
        // keep them readable, per song, and dark covers are left almost as they are.
        val veilTarget = rememberLyricVeil(backdrop.value)
        val veil by animateFloatAsState(
            targetValue = veilTarget,
            animationSpec = if (instant) snap() else tween(700),
            label = "ambientVeil",
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawRect(
                            if (landscape) {
                                Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = veil * 0.35f),
                                    0.4f to Color.Black.copy(alpha = veil * 0.7f),
                                    1f to Color.Black.copy(alpha = veil),
                                )
                            } else {
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = veil * 0.6f),
                                    1f to Color.Black.copy(alpha = veil),
                                )
                            }
                        )
                    }
            )
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides playerBackdrop) {
            val lyrics: @Composable (Modifier, PaddingValues) -> Unit = { modifier, padding ->
                // Lines dissolve into the backdrop near the top and bottom instead of meeting an
                // edge. The pane fades its own lines: a mask over it re-rendered the whole page
                // offscreen on every frame a word moved.
                Box(modifier) {
                    LiquidLyricsPane(
                        metadata = mediaMetadata,
                        positionProvider = positionProvider,
                        onSeek = onSeek,
                        contentPadding = padding,
                        nestedScrollConnection = noNestedScroll,
                        horizontalPadding = 0.dp,
                        fadeTop = 0.16f,
                        fadeBottom = 0.28f,
                    )
                }
            }
            // One floating bar along the foot of the screen, over the part of the page the
            // lyrics already fade out of, so showing it moves nothing.
            val controls: @Composable (Modifier, Boolean) -> Unit = { modifier, landscape ->
                Row(
                    modifier.graphicsLayer { alpha = controlsAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // On the page's own grid: the transport under the cover, the progress bar
                    // starting where the lyrics start.
                    Box(Modifier.weight(if (landscape) 0.4f else 0.45f), contentAlignment = Alignment.Center) {
                    LiquidTransport(
                        isPlaying = isPlaying,
                        canSkipPrevious = canSkipPrevious,
                        canSkipNext = canSkipNext,
                        onPrevious = { playerConnection.seekToPrevious() },
                        onPlayPause = { playerConnection.togglePlayPause() },
                        onNext = { playerConnection.seekToNext() },
                        enabled = controlsShown,
                        playSize = 52.dp,
                        skipSize = 40.dp,
                        modifier = Modifier.width(180.dp),
                    )
                    }
                    LiquidScrubber(
                        positionProvider = positionProvider,
                        durationProvider = durationProvider,
                        onSeek = onSeek,
                        enabled = controlsShown,
                        modifier = Modifier
                            .weight(if (landscape) 0.6f else 0.55f)
                            .padding(end = if (landscape) 48.dp else 28.dp, top = 14.dp),
                    )
                }
            }

            // A shade under the bar so the time labels hold over any lyric or cover; edge to
            // edge, under the camera cutout too.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(130.dp)
                    .graphicsLayer { alpha = controlsAlpha }
                    .drawWithContent {
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)))
                    }
            )

            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val areaWidth = maxWidth
                val areaHeight = maxHeight
                if (areaWidth > areaHeight) {
                    // Landscape: the cover and its song on the left third, the words on the rest.
                    val cover = minOf(areaHeight * 0.5f, areaWidth * 0.26f)
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(0.4f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Makes room for the bar in one soft move, instead of being covered by it.
                            Column(
                                Modifier
                                    .width(cover)
                                    .graphicsLayer { translationY = -30.dp.toPx() * controlsAlpha },
                            ) {
                                Cover(mediaMetadata, isPlaying, living, motionCovers, Modifier.size(cover))
                                Spacer(Modifier.height(20.dp))
                                SongTitle(mediaMetadata)
                            }
                        }
                        lyrics(
                            Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .padding(end = 48.dp),
                            PaddingValues(top = areaHeight * 0.18f, bottom = areaHeight * 0.5f),
                        )
                    }
                } else {
                    // Portrait (a locked rotation): the song as a header, the words below it,
                    // the controls at the foot when asked for.
                    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
                        Row(
                            Modifier.padding(top = 64.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(mediaMetadata, isPlaying, living, motionCovers, Modifier.size(64.dp))
                            Spacer(Modifier.width(16.dp))
                            SongTitle(mediaMetadata)
                        }
                        lyrics(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            PaddingValues(top = areaHeight * 0.12f, bottom = areaHeight * 0.4f),
                        )
                    }
                }

                if (controlsPresent) {
                    controls(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        areaWidth > areaHeight,
                    )
                }

                // The way out, only while the controls are, in the one corner nothing else uses.
                if (controlsPresent) {
                    GlassIconButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        onClick = { navController.popBackStack() },
                        size = 40.dp,
                        iconSize = 22.dp,
                        kind = GlassKind.Clear,
                        tint = NowPlayingInk.primary,
                        contentDescription = "Close",
                        enabled = controlsShown,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 20.dp, top = 16.dp)
                            .graphicsLayer { alpha = controlsAlpha },
                    )
                }
            }
        }
    }
}

/** The cover as an object on the backdrop: rounded, lifted by a soft shadow, clipped. */
@Composable
private fun Cover(
    metadata: MediaMetadata?,
    isPlaying: Boolean,
    living: Boolean,
    motionCovers: Boolean,
    modifier: Modifier,
) {
    val framed = modifier
        .shadow(elevation = 22.dp, shape = CoverShape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(CoverShape)
    if (metadata != null) {
        LivingArtwork(
            model = metadata.thumbnailUrl,
            metadata = metadata,
            animate = isPlaying,
            living = living,
            motionVideos = motionCovers,
            videoVisible = true,
            modifier = framed,
        )
    } else {
        Artwork(model = null, shape = CoverShape, modifier = framed)
    }
}

@Composable
private fun SongTitle(metadata: MediaMetadata?) {
    Column {
        Text(
            text = metadata?.title.orEmpty(),
            style = LiquidTypography.headline.copy(fontWeight = FontWeight.SemiBold),
            color = NowPlayingInk.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metadata?.artists?.joinToString { it.name }.orEmpty(),
            style = LiquidTypography.body,
            color = NowPlayingInk.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * How much extra darkness the lyric side needs over this backdrop, 0..1.
 *
 * Read from the bright end of the blurred field (its 90th percentile, not its average: one
 * bright patch behind a line is enough to lose it), less the player's own scrim, and solved
 * so the ground under the words stays near 14% luminance — where even the dimmed, unsung
 * lines hold. Dark covers get a whisper of veil; a white cover gets the most.
 */
@Composable
private fun rememberLyricVeil(backdrop: PlayerBackdrop): Float = remember(backdrop.key, backdrop.field) {
    val bright = runCatching {
        val bitmap = backdrop.field!!.asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val lum = FloatArray(pixels.size) { android.graphics.Color.luminance(pixels[it]) }
        lum.sort()
        lum[(lum.size * 0.9f).toInt().coerceAtMost(lum.size - 1)]
    }.getOrElse { backdrop.gradient.firstOrNull()?.luminance() ?: 0f }
    val scrim = backdrop.gradient.firstOrNull()?.let(::balancedScrimFor) ?: 0.26f
    val shown = bright * (1f - scrim)
    if (shown <= 0.14f) 0.08f else (1f - 0.14f / shown).coerceIn(0.08f, 0.75f)
}

