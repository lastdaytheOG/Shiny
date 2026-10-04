package com.shiny.music.ui.liquid.player

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
import com.shiny.music.ui.liquid.GlassKind
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkipDisc(forward = false, size = skipSize, enabled = enabled && canSkipPrevious, onClick = onPrevious)
        PlayDisc(isPlaying = isPlaying, size = playSize, enabled = enabled, onClick = onPlayPause)
        SkipDisc(forward = true, size = skipSize, enabled = enabled && canSkipNext, onClick = onNext)
    }
}

@Composable
private fun PlayDisc(isPlaying: Boolean, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val haptics = LocalHapticFeedback.current
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
            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" }
            .graphicsLayer {
                val s = 1f + 0.07f * press.value
                scaleX = s
                scaleY = s
            }
            .discGlow(press = { press.value })
            .liquidGlass(CircleShape, GlassKind.Clear, tint = Color.White.copy(alpha = 0.13f), pressProgress = { press.value })
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
        Canvas(Modifier.size(size * 0.40f)) { drawPlayPause(morph, ink) }
    }
}

@Composable
private fun SkipDisc(forward: Boolean, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val nudge = remember { Animatable(0f) }
    val ink = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Box(
        Modifier
            .size(size)
            .semantics { contentDescription = if (forward) "Next" else "Previous" }
            .graphicsLayer {
                val s = 1f + 0.08f * press.value
                scaleX = s
                scaleY = s
            }
            .discGlow(press = { press.value })
            .liquidGlass(CircleShape, GlassKind.Clear, tint = Color.White.copy(alpha = 0.08f), pressProgress = { press.value })
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
        Canvas(
            Modifier
                .size(size * 0.46f)
                .graphicsLayer {
                    val dir = if (forward) 1f else -1f
                    translationX = dir * this.size.width * 0.16f * nudge.value
                }
        ) { drawSkip(forward, ink) }
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

/**
 * Pause bars ↔ play triangle. The triangle is cut into two quads at its middle so each
 * bar has four points to travel to; drawn with rounded corners like SF Symbols' fill.
 */
private fun DrawScope.drawPlayPause(toPlay: Float, color: Color) {
    val w = size.width
    val h = size.height
    val t = toPlay.coerceIn(0f, 1.1f)
    val bar = w * 0.30f
    // Pause geometry.
    val lp = listOf(Offset(w * 0.06f, 0f), Offset(w * 0.06f + bar, 0f), Offset(w * 0.06f + bar, h), Offset(w * 0.06f, h))
    val rp = listOf(Offset(w * 0.94f - bar, 0f), Offset(w * 0.94f, 0f), Offset(w * 0.94f, h), Offset(w * 0.94f - bar, h))
    // Play geometry (triangle pointing right, optically centred a touch to the right).
    val x0 = w * 0.12f
    val x1 = w * 1.0f
    val xm = (x0 + x1) / 2f
    val lq = listOf(Offset(x0, 0f), Offset(xm, h * 0.25f), Offset(xm, h * 0.75f), Offset(x0, h))
    val rq = listOf(Offset(xm, h * 0.25f), Offset(x1, h * 0.5f), Offset(x1, h * 0.5f), Offset(xm, h * 0.75f))
    fun lerp(a: Offset, b: Offset) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    listOf(lp to lq, rp to rq).forEach { (from, to) ->
        val path = Path().apply {
            val pts = from.indices.map { lerp(from[it], to[it]) }
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawPath(path, color, style = Fill)
        drawPath(path, color, style = Stroke(width = w * 0.09f, join = StrokeJoin.Round))
    }
}

/** Two rounded triangles, iOS's forward / backward glyph. */
private fun DrawScope.drawSkip(forward: Boolean, color: Color) {
    val w = size.width
    val h = size.height
    val tri = w * 0.52f
    fun triangle(x: Float) = Path().apply {
        if (forward) {
            moveTo(x, h * 0.12f)
            lineTo(x + tri, h * 0.5f)
            lineTo(x, h * 0.88f)
        } else {
            moveTo(x + tri, h * 0.12f)
            lineTo(x, h * 0.5f)
            lineTo(x + tri, h * 0.88f)
        }
        close()
    }
    translate(left = 0f) {
        drawPath(triangle(0f), color, style = Fill)
        drawPath(triangle(0f), color, style = Stroke(width = w * 0.07f, join = StrokeJoin.Round))
        drawPath(triangle(w - tri), color, style = Fill)
        drawPath(triangle(w - tri), color, style = Stroke(width = w * 0.07f, join = StrokeJoin.Round))
    }
}
