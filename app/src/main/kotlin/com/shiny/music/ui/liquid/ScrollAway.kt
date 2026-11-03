package com.shiny.music.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val ScrollAwayEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * 0 while the bar is shown, 1 once it has gone. It goes when the list has clearly moved
 * down by [threshold] and comes back when it clearly moves up, or reaches the top, so small
 * wobbles of the finger never make it flicker.
 */
@Composable
fun rememberScrollAway(state: LazyListState, threshold: Dp = 28.dp): State<Float> {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    val hidden = remember(state) { Animatable(0f) }
    LaunchedEffect(state, thresholdPx) {
        var index = state.firstVisibleItemIndex
        var offset = state.firstVisibleItemScrollOffset
        var travel = 0f
        var target = 0f
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (i, o) ->
            val delta = when {
                i == index -> (o - offset).toFloat()
                i > index -> thresholdPx
                else -> -thresholdPx
            }
            index = i
            offset = o
            if (delta == 0f) return@collect
            if ((delta > 0f) != (travel > 0f)) travel = 0f
            travel += delta
            val next = when {
                i == 0 && o < thresholdPx -> 0f
                travel >= thresholdPx -> 1f
                travel <= -thresholdPx -> 0f
                else -> target
            }
            if (next != target) {
                target = next
                launch { hidden.animateTo(next, tween(420, easing = ScrollAwayEasing)) }
            }
        }
    }
    return hidden.asState()
}
