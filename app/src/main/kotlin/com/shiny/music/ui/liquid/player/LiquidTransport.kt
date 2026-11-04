package com.shiny.music.ui.liquid.player

import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shiny.music.R
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.liquidGlass
import com.shiny.music.ui.liquid.rememberGlassPress
import kotlinx.coroutines.launch

/**
 * Previous · Play/Pause · Next as three glass discs over the player's own background: the
 * large centre one carries a glyph that morphs between the pause bars and the play
 * triangle; the skip discs nudge their arrows in the direction of travel when tapped, the
 * way iOS's transport answers a press. Each disc swells slightly under the finger and its
 * rim brightens, and the glass refracts whatever colour the artwork has put behind it.
 */
@Composable
fun LiquidTransport(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    playSize: Dp = 86.dp,
    skipSize: Dp = 66.dp,
) {
    val soft = LocalShinyAppearance.current.softPlayer
    if (soft) {
        AlternateTransport(
            isPlaying = isPlaying,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            enabled = enabled,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            SkipDisc(forward = false, size = skipSize, enabled = enabled && canSkipPrevious, onClick = onPrevious)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PlayDisc(isPlaying = isPlaying, size = playSize, enabled = enabled, onClick = onPlayPause)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            SkipDisc(forward = true, size = skipSize, enabled = enabled && canSkipNext, onClick = onNext)
        }
    }
}

/** The alternate transport is pure white glyphs with generous touch targets, not button discs. */
@Composable
private fun AlternateTransport(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlternateSkipGlyph(forward = false, enabled = enabled && canSkipPrevious, onClick = onPrevious)
        AlternatePlayGlyph(isPlaying = isPlaying, enabled = enabled, onClick = onPlayPause)
        AlternateSkipGlyph(forward = true, enabled = enabled && canSkipNext, onClick = onNext)
    }
}

@Composable
private fun AlternatePlayGlyph(isPlaying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val haptics = LocalHapticFeedback.current
    val morph by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 640f),
        label = "alternatePlayMorph",
    )
    Box(
        Modifier
            .size(92.dp)
            .graphicsLayer {
                val s = 1f - 0.10f * press.value
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.32f
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(60.dp),
        )
    }
}

@Composable
private fun AlternateSkipGlyph(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val nudge = remember { Animatable(0f) }
    Box(
        Modifier
            .size(82.dp)
            .graphicsLayer {
                val s = 1f - 0.10f * press.value
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.32f
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                    scope.launch {
                        nudge.snapTo(0f)
                        nudge.animateTo(if (forward) 1f else -1f, tween(120))
                        nudge.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 460f))
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            if (forward) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer { translationX = nudge.value * 7.dp.toPx() },
        )
    }
}

@Composable
private fun PlayDisc(isPlaying: Boolean, size: Dp, enabled: Boolean, onClick: () -> Unit, soft: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val haptics = LocalHapticFeedback.current
    val playLabel = stringResource(if (isPlaying) R.string.pause else R.string.play)
    // 0 = pause bars, 1 = play triangle.
    val morph by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "playMorph",
    )
    val ink = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Box(
        Modifier
            .size(size)
            .semantics { contentDescription = playLabel }
            .then(
                if (soft) {
                    // the soft look's discs sink under the finger instead of swelling
                    Modifier.glassPressScale(press, 0.09f).frostDisc(press = { press.value }, fill = 0.14f)
                } else {
                    Modifier
                        .graphicsLayer {
                            val s = 1f + 0.07f * press.value
                            scaleX = s
                            scaleY = s
                        }
                        .discGlow(press = { press.value })
                        .liquidGlass(CircleShape, GlassKind.Clear, tint = Color.White.copy(alpha = 0.13f), pressProgress = { press.value })
                }
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

@Composable
private fun SkipDisc(forward: Boolean, size: Dp, enabled: Boolean, onClick: () -> Unit, soft: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val haptics = LocalHapticFeedback.current
    val skipLabel = stringResource(if (forward) R.string.next else R.string.previous)
    val scope = rememberCoroutineScope()
    val nudge = remember { Animatable(0f) }
    val ink = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Box(
        Modifier
            .size(size)
            .semantics { contentDescription = skipLabel }
            .then(
                if (soft) {
                    Modifier.glassPressScale(press, 0.09f).frostDisc(press = { press.value }, fill = 0.12f)
                } else {
                    Modifier
                        .graphicsLayer {
                            val s = 1f + 0.08f * press.value
                            scaleX = s
                            scaleY = s
                        }
                        .discGlow(press = { press.value })
                        .liquidGlass(CircleShape, GlassKind.Clear, tint = Color.White.copy(alpha = 0.08f), pressProgress = { press.value })
                }
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                    scope.launch {
                        nudge.snapTo(0f)
                        nudge.animateTo(1f, tween(140))
                        nudge.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f))
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            if (forward) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious,
            contentDescription = null,
            tint = ink,
            modifier = Modifier
                .size(size * 0.5f)
                .graphicsLayer {
                    val dir = if (forward) 1f else -1f
                    translationX = dir * this.size.width * 0.16f * nudge.value
                },
        )
    }
}

/** A soft white bloom behind a disc while it is pressed. Draw phase only. */
private fun Modifier.discGlow(press: () -> Float): Modifier = drawBehind {
    val p = press()
    if (p <= 0.01f) return@drawBehind
    val r = size.minDimension / 2f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.28f * p), Color.Transparent),
            center = center,
            radius = r * 1.55f,
        ),
        radius = r * 1.55f,
    )
}
