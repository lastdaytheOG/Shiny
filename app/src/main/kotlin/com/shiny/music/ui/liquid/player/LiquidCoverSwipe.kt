package com.shiny.music.ui.liquid.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private val FlingVelocity = 700.dp
private const val CommitFraction = 0.24f
private const val BlockedResistance = 0.28f
private const val ExitFraction = 1.15f

@Stable
internal class CoverSwipe(private val scope: CoroutineScope) {
    private val offset = mutableFloatStateOf(0f)
    private var settle: Job? = null
    private var leaving = false
    internal var travel = 0f

    val value: Float get() = offset.floatValue

    fun progress(): Float =
        if (travel <= 0f) 0f else (abs(offset.floatValue) / travel).coerceIn(0f, 1f)

    internal fun grab(): Boolean {
        if (leaving) return false
        settle?.cancel()
        return true
    }

    internal fun drag(amount: Float, canLeft: Boolean, canRight: Boolean) {
        val x = offset.floatValue
        val heading = x + amount
        val free = when {
            heading < 0f -> canLeft
            heading > 0f -> canRight
            else -> true
        }
        offset.floatValue = x + if (free) amount else amount * BlockedResistance
    }

    internal fun release(
        velocity: Float,
        flingPx: Float,
        onLeft: (() -> Unit)?,
        onRight: (() -> Unit)?,
    ): Boolean {
        val x = offset.floatValue
        val far = travel * CommitFraction
        val change = when {
            x < 0f && (x < -far || velocity < -flingPx) -> onLeft
            x > 0f && (x > far || velocity > flingPx) -> onRight
            else -> null
        }
        if (change == null || travel <= 0f) {
            settle = scope.launch {
                animate(x, 0f, velocity, spring(dampingRatio = 0.72f, stiffness = 520f)) { v, _ ->
                    offset.floatValue = v
                }
            }
            return false
        }
        val direction = if (x < 0f) -1f else 1f
        val exit = direction * travel * ExitFraction
        val speed = max(abs(velocity), travel * 3.2f)
        leaving = true
        settle = scope.launch {
            try {
                val millis = (abs(exit - x) / speed * 1000f).toInt().coerceIn(80, 200)
                animate(x, exit, animationSpec = tween(millis, easing = LinearEasing)) { v, _ ->
                    offset.floatValue = v
                }
            } finally {
                leaving = false
                change()
                offset.floatValue = -exit
            }
            animate(-exit, 0f, direction * speed, spring(dampingRatio = 0.86f, stiffness = 380f)) { v, _ ->
                offset.floatValue = v
            }
        }
        return true
    }

    internal fun reset() {
        if (leaving || offset.floatValue == 0f) return
        settle?.cancel()
        val from = offset.floatValue
        settle = scope.launch {
            animate(from, 0f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f)) { v, _ ->
                offset.floatValue = v
            }
        }
    }
}

@Composable
internal fun rememberCoverSwipe(): CoverSwipe {
    val scope = rememberCoroutineScope()
    return remember(scope) { CoverSwipe(scope) }
}

internal fun Modifier.coverSwipe(
    swipe: CoverSwipe,
    onLeft: (() -> Unit)?,
    onRight: (() -> Unit)?,
): Modifier = composed {
    val left by rememberUpdatedState(onLeft)
    val right by rememberUpdatedState(onRight)
    val haptic = LocalHapticFeedback.current
    val enabled = onLeft != null || onRight != null
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val flingPx = FlingVelocity.toPx()
        val tracker = VelocityTracker()
        var tracking = false
        try {
            detectHorizontalDragGestures(
                onDragStart = {
                    tracking = swipe.grab()
                    tracker.resetTracking()
                },
                onDragEnd = {
                    if (tracking && swipe.release(tracker.calculateVelocity().x, flingPx, left, right)) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    tracking = false
                },
                onDragCancel = {
                    if (tracking) swipe.reset()
                    tracking = false
                },
            ) { change, amount ->
                change.consume()
                if (tracking) {
                    tracker.addPointerInputChange(change)
                    swipe.drag(amount, left != null, right != null)
                }
            }
        } finally {
            if (tracking) swipe.reset()
        }
    }
}
