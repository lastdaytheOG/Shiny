package com.shiny.music.ui.liquid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.shiny.music.playback.PlaybackPrewarm

/**
 * How long a finger has to rest on an item, without moving past the touch slop, to count as
 * the start of a tap. A scroll leaves the slop well inside this, so flinging through a list
 * warms nothing; a tap is released at 80–150 ms, so this still starts the work that much early.
 */
private const val TOUCH_INTENT_MS = 40L

/**
 * Tells playback a tap on [mediaId] is probably coming (see [PlaybackPrewarm]): the stream is
 * resolved while the finger is still down, and the tap joins that work. Null — an album, a
 * playlist — only warms the connection. Observes on the initial pass and consumes nothing, so
 * clicks, long presses and scrolling behave exactly as before.
 */
fun Modifier.prewarmOnTouch(mediaId: String?): Modifier = pointerInput(mediaId) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val intent = withTimeoutOrNull(TOUCH_INTENT_MS) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                // Released inside the window: that was a whole tap.
                if (!change.pressed) return@withTimeoutOrNull true
                if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: true // Still resting on it when the window closed.
        if (intent) PlaybackPrewarm.touched(mediaId)
    }
}
