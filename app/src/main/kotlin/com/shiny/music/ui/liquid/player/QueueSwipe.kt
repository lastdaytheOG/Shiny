package com.shiny.music.ui.liquid.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.liquid.Liquid
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A queue row that can be swiped: right takes the song out of the queue, left puts it next.
 * Only a mostly sideways drag is taken, so the list's vertical scroll and the long-press
 * reorder keep working. The action lands once per gesture, on release, and only past the
 * threshold (or a quick flick most of the way there).
 */
@Composable
internal fun QueueSwipe(
    enabled: Boolean,
    /** Returns false when the song could not be removed, and the row comes back. */
    onRemove: () -> Boolean,
    /** Returns false when the song is already next, and the row only springs back. */
    onPlayNext: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    // a brief wash over the row once it has been put next
    val flash = remember { Animatable(0f) }
    val remove by rememberUpdatedState(onRemove)
    val playNext by rememberUpdatedState(onPlayNext)
    val accent = Liquid.colors.accent
    val removeIcon = rememberVectorPainter(Icons.Rounded.RemoveCircleOutline)
    val nextIcon = rememberVectorPainter(Icons.AutoMirrored.Rounded.PlaylistPlay)

    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val slop = viewConfiguration.touchSlop
                val threshold = size.width * SwipeThreshold
                val flingSpeed = FlingSpeed.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    // decide the gesture's direction before taking it
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        total += change.positionChange()
                        if (total.getDistance() > slop) {
                            if (abs(total.x) > abs(total.y) * 1.4f) {
                                change.consume()
                                break
                            }
                            return@awaitEachGesture
                        }
                    }
                    val tracker = VelocityTracker()
                    var raw = offset.value
                    var armed = false
                    scope.launch { offset.stop() }
                    val completed = horizontalDrag(down.id) { change ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        raw += change.positionChange().x
                        // free up to the threshold, then a little resistance
                        val shown = if (abs(raw) <= threshold) raw else Math.copySign(threshold + (abs(raw) - threshold) * 0.55f, raw)
                        val nowArmed = abs(raw) >= threshold
                        if (nowArmed != armed) {
                            armed = nowArmed
                            if (armed) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                        scope.launch { offset.snapTo(shown) }
                        change.consume()
                    }
                    val velocity = if (completed) tracker.calculateVelocity().x else 0f
                    val flicked = abs(velocity) > flingSpeed && abs(raw) > threshold * 0.4f && Math.signum(velocity) == Math.signum(raw)
                    val commit = completed && (abs(raw) >= threshold || flicked)
                    scope.launch {
                        when {
                            commit && raw > 0f -> {
                                // out to the right, then out of the queue; the rows below close up
                                offset.animateTo(size.width.toFloat(), tween(150, easing = FastOutLinearInEasing))
                                if (!remove()) offset.animateTo(0f, SpringBack)
                                else haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                            commit && raw < 0f -> {
                                val moved = playNext()
                                haptic.performHapticFeedback(if (moved) HapticFeedbackType.Confirm else HapticFeedbackType.SegmentTick)
                                if (moved) launch {
                                    flash.snapTo(1f)
                                    flash.animateTo(0f, tween(520))
                                }
                                offset.animateTo(0f, SpringBack)
                            }
                            else -> offset.animateTo(0f, SpringBack)
                        }
                    }
                }
            }
            .drawWithCache {
                val iconPx = 22.dp.toPx()
                val inset = 28.dp.toPx()
                val removeTint = ColorFilter.tint(RemoveRed)
                val nextTint = ColorFilter.tint(accent)
                val threshold = size.width * SwipeThreshold
                onDrawBehind {
                    val x = offset.value
                    if (flash.value > 0f) drawRect(accent.copy(alpha = 0.14f * flash.value))
                    if (x == 0f) return@onDrawBehind
                    val reach = (abs(x) / threshold).coerceIn(0f, 1f)
                    val wash = if (x > 0f) RemoveRed else accent
                    // the revealed strip only
                    val left = if (x > 0f) 0f else size.width + x
                    drawRect(wash.copy(alpha = 0.10f + 0.10f * reach), topLeft = Offset(left, 0f), size = Size(abs(x), size.height))
                    val s = iconPx * (0.8f + 0.2f * reach)
                    val iconX = if (x > 0f) inset else size.width - inset - s
                    val iconY = (size.height - s) / 2f
                    translate(iconX, iconY) {
                        with(if (x > 0f) removeIcon else nextIcon) {
                            draw(Size(s, s), alpha = 0.35f + 0.65f * reach, colorFilter = if (x > 0f) removeTint else nextTint)
                        }
                    }
                }
            },
    ) {
        Box(Modifier.graphicsLayer { translationX = offset.value }) { content() }
    }
}

/** How far across the row a swipe must travel to act. */
private const val SwipeThreshold = 0.32f

private val FlingSpeed = 900.dp
private val RemoveRed = Color(0xFFFF5A5F)
private val SpringBack = spring<Float>(dampingRatio = 0.85f, stiffness = 520f)
