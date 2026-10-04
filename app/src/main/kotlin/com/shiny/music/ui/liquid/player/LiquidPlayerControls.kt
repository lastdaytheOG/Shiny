package com.shiny.music.ui.liquid.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.liquid.LiquidIcons
import com.shiny.music.ui.liquid.LiquidTypography
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** White at the three intensities the Now Playing controls use. */
internal object NowPlayingInk {
    val primary = Color.White
    val secondary = Color.White.copy(alpha = 0.62f)
    val tertiary = Color.White.copy(alpha = 0.38f)
    val track = Color.White.copy(alpha = 0.22f)
}

internal fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * The progress bar. A thin capsule at rest that swells and brightens while it is held,
 * dragged relative to where the finger went down — so grabbing it never makes the song
 * jump — and committed on release.
 *
 * Position arrives as a provider and is read only while drawing, so the bar repaints on
 * every tick without recomposing anything; the time labels recompose once a second.
 */
@Composable
fun LiquidScrubber(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val swell = remember { Animatable(0f) }

    val elapsedSeconds by remember {
        derivedStateOf {
            val d = durationProvider().coerceAtLeast(1L)
            val p = scrubFraction?.let { (it * d).toLong() } ?: positionProvider()
            p / 1000L
        }
    }
    val totalSeconds by remember { derivedStateOf { durationProvider() / 1000L } }

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .scrubGesture(
                    enabled = enabled && durationProvider() > 0L,
                    startFraction = {
                        (positionProvider().toFloat() / durationProvider().coerceAtLeast(1L)).coerceIn(0f, 1f)
                    },
                    onStart = { scope.launch { swell.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f)) } },
                    onFraction = { f ->
                        if (scrubFraction == null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scrubFraction = f
                    },
                    onEnd = { committed, f ->
                        if (committed) onSeek((f * durationProvider()).toLong())
                        scrubFraction = null
                        scope.launch { swell.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 420f)) }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .graphicsLayer {
                        val s = 1f + 0.035f * swell.value
                        scaleX = s
                    }
            ) {
                val s = swell.value
                val h = (7.dp.toPx() + 6.dp.toPx() * s)
                val top = (size.height - h) / 2f
                val d = durationProvider().coerceAtLeast(1L)
                val f = scrubFraction ?: (positionProvider().toFloat() / d).coerceIn(0f, 1f)
                drawRoundRect(
                    color = NowPlayingInk.track,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, h),
                    cornerRadius = CornerRadius(h / 2f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.68f + 0.32f * s),
                    topLeft = Offset(0f, top),
                    size = Size((size.width * f).coerceAtLeast(h), h),
                    cornerRadius = CornerRadius(h / 2f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val remaining = (totalSeconds - elapsedSeconds).coerceAtLeast(0L)
            Text(
                text = formatTime(elapsedSeconds * 1000L),
                style = TimeStyle,
                color = NowPlayingInk.secondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "-" + formatTime(remaining * 1000L),
                style = TimeStyle,
                color = NowPlayingInk.secondary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

private val TimeStyle = LiquidTypography.caption1.copy(
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    fontSize = 12.sp,
)

/**
 * The system media volume, as a slider. Follows hardware-key changes through the
 * platform's volume broadcast so the thumb never disagrees with the device.
 */
@Composable
fun LiquidVolumeSlider(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var systemFraction by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max)
    }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val swell = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    systemFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val shown = dragFraction ?: systemFraction

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, tint = NowPlayingInk.secondary, modifier = Modifier.size(18.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .padding(horizontal = 10.dp)
                .scrubGesture(
                    enabled = true,
                    startFraction = { dragFraction ?: systemFraction },
                    onStart = { scope.launch { swell.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f)) } },
                    onFraction = { fraction ->
                        dragFraction = fraction
                        val target = (fraction * max).roundToInt()
                        if (target != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                        }
                    },
                    onEnd = { _, fraction ->
                        systemFraction = fraction
                        dragFraction = null
                        scope.launch { swell.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 420f)) }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                val s = swell.value
                val h = 6.dp.toPx() + 6.dp.toPx() * s
                val top = (size.height - h) / 2f
                drawRoundRect(NowPlayingInk.track, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2f))
                drawRoundRect(
                    Color.White.copy(alpha = 0.68f + 0.32f * s),
                    Offset(0f, top),
                    Size((size.width * shown).coerceAtLeast(h), h),
                    CornerRadius(h / 2f),
                )
            }
        }
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = NowPlayingInk.secondary, modifier = Modifier.size(18.dp))
    }
}

/** Lyrics · output device · queue — the row under the volume slider. */
@Composable
fun NowPlayingUtilityRow(
    lyricsActive: Boolean,
    queueActive: Boolean,
    onLyrics: () -> Unit,
    onOutput: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onLyricsLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UtilityButton(LiquidIcons.Lyrics, lyricsActive, onLyrics, onLyricsLongClick)
        UtilityButton(LiquidIcons.Output, false, onOutput)
        UtilityButton(LiquidIcons.Queue, queueActive, onQueue)
    }
}

@Composable
private fun UtilityButton(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val activeness by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "utilityActive",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = if (pressed) 1400f else 400f),
        label = "utilityPress",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                val s = 1f - 0.1f * press
                scaleX = s
                scaleY = s
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    alpha = activeness
                    val s = 0.6f + 0.4f * activeness
                    scaleX = s
                    scaleY = s
                }
                .background(Color.White.copy(alpha = 0.92f), CircleShape)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.lerp(NowPlayingInk.secondary, Color(0xFF1C1C1E), activeness),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun Grabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(38.dp)
            .height(5.dp)
            .background(Color.White.copy(alpha = 0.38f), CircleShape)
    )
}

@Composable
internal fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/**
 * A relative horizontal scrub: nothing moves until the finger has travelled the touch
 * slop sideways, so a vertical swipe that starts on a slider still drags the player.
 */
private fun Modifier.scrubGesture(
    enabled: Boolean,
    startFraction: () -> Float,
    onStart: () -> Unit,
    onFraction: (Float) -> Unit,
    onEnd: (committed: Boolean, fraction: Float) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onStart()
        var fraction = startFraction()
        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            change.consume()
            fraction = (fraction + over / size.width).coerceIn(0f, 1f)
            onFraction(fraction)
        }
        var committed = false
        if (slop != null) {
            committed = true
            horizontalDrag(slop.id) { change ->
                fraction = (fraction + change.positionChange().x / size.width).coerceIn(0f, 1f)
                onFraction(fraction)
                change.consume()
            }
        }
        onEnd(committed, fraction)
    }
}
