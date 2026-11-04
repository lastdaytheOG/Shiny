package com.shiny.music.ui.liquid.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The queue's top section — the playing song, the modes and the heading — as one region
 * that scrolls away with the list and comes back as soon as the list is pulled down.
 * The list stays the only scroller: this only takes the part of each scroll that moves
 * the region, so the rows under the finger always move with it.
 */
@Stable
class QueueHeaderCollapse internal constructor(private val scope: CoroutineScope) {
    /** How far the region has gone up, in px: 0 shown, [range] gone. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** The playing song's header, from the top of the screen's content to the pane, in px. */
    var headerBlock by mutableFloatStateOf(0f)

    /** The pane's own modes and heading, in px. */
    var paneHead by mutableFloatStateOf(0f)

    /** The whole region's height above the list's resting top, in px. */
    val range: Float get() = if (paneHead <= 0f) 0f else headerBlock + paneHead

    val fraction: Float get() = if (range <= 0f) 0f else (offset / range).coerceIn(0f, 1f)

    private var settle: Job? = null

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (range <= 0f || available.y == 0f) return Offset.Zero
            settle?.cancel()
            val next = (offset - available.y).coerceIn(0f, range)
            val consumed = offset - next
            offset = next
            return Offset(0f, consumed)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settleToNearest()
            return Velocity.Zero
        }
    }

    // never left halfway: a region half up reads as a mistake
    private fun settleToNearest() {
        if (offset <= 0f || offset >= range) return
        animateTo(if (offset > range / 2f) range else 0f)
    }

    fun animateTo(target: Float) {
        settle?.cancel()
        val from = offset
        if (from == target) return
        settle = scope.launch {
            val anim = Animatable(from)
            anim.animateTo(target.coerceIn(0f, range.coerceAtLeast(0f)), spring(dampingRatio = 1f, stiffness = 500f)) {
                offset = value
            }
        }
    }
}

@Composable
internal fun rememberQueueHeaderCollapse(): QueueHeaderCollapse {
    val scope = rememberCoroutineScope()
    return remember { QueueHeaderCollapse(scope) }
}
