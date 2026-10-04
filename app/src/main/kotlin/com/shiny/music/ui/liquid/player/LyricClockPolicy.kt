package com.shiny.music.ui.liquid.player

import kotlin.math.abs
import kotlin.math.exp

/**
 * The rules the lyric clock follows, kept free of Compose so they can be tested.
 */
internal object LyricClockPolicy {

    /**
     * A polled position that lands this far from where the clock expected it is a seek.
     * It matters when there is no player to listen to, so no discontinuity event arrives.
     */
    const val PolledJumpMs = 1_500L

    /**
     * How early a line becomes the sung line. Becoming current starts the hand-over — the
     * line brightens and the page brings it up to its reading place. Switching exactly at
     * the timestamp meant a line settled a third of a second after the voice had begun, so
     * even perfectly timed lyrics read as late. The line now arrives as it is sung. Only the
     * choice of line leads: the karaoke fill inside it runs on the exact clock, so no word
     * lights before it is sung.
     */
    const val LineLeadMs = 250L

    /** The position the sung line is looked up at, for a clock reading and per-song offset. */
    fun lineLookupPosition(clockMs: Long, songOffsetMs: Long): Long = clockMs + songOffsetMs + LineLeadMs

    /** Whether a reading of [polled] is a jump away from the [expected] position. */
    fun isPolledJump(expected: Long, polled: Long): Boolean = abs(polled - expected) > PolledJumpMs

    /**
     * Whether the lyrics page should cut straight to the current line instead of gliding.
     * After a seek, anything further than the next line is a jump: gliding through a
     * dozen lines to get there reads as the lyrics catching up, not as having moved.
     */
    fun shouldJump(fromIndex: Int, toIndex: Int, afterSeek: Boolean): Boolean =
        afterSeek && (fromIndex < 0 || abs(toIndex - fromIndex) > 1)
}

/**
 * The playback time each frame shows: continuous, locked to the display's frame times, and
 * never stepping backwards while the song plays.
 *
 * The player's own `currentPosition` cannot be shown as-is. ExoPlayer returns the position
 * its playback thread last wrote — it does not extrapolate between writes — and with dynamic
 * scheduling on (MusicService) that thread only wakes when the audio sink needs data: half
 * the buffered audio, 100–375 ms apart. Read every frame, the position is a staircase, and
 * a word fill driven by it moved in visible chunks a few times a second.
 *
 * So each fresh reading becomes an anchor — taken, as far as a frame can tell, halfway
 * between the frame that saw it and the one before — and frames in between extrapolate from
 * it at the playback speed. The shown time then follows that estimate with a gentle slew,
 * never faster than 1.5× or slower than 0.5× real time, so a re-anchor that lands a few
 * milliseconds off is absorbed over a few frames instead of showing as a jump. A reading far
 * from the estimate (a seek the player did not announce) is taken at once.
 *
 * While the player is not actually advancing (paused, or buffering with play pressed) the
 * shown time is exactly the player's position. Extrapolation is capped at [maxExtrapolationMs]
 * past the last reading, so a stall the player does not report cannot run the lyrics ahead.
 */
internal class LyricClockEstimator(
    private val maxExtrapolationMs: Double = DefaultMaxExtrapolationMs,
) {
    /** The time the current frame shows, in ms. */
    var shown: Double = 0.0
        private set

    private var anchorPosition = 0.0
    private var anchorNanos = NoFrame
    private var lastSample = Long.MIN_VALUE
    private var lastFrameNanos = NoFrame

    /** Shows [position] exactly from the next frame on: a seek, a pause, a new song. */
    fun reset(position: Long) {
        shown = position.toDouble()
        anchorPosition = shown
        anchorNanos = NoFrame
        lastSample = position
        lastFrameNanos = NoFrame
    }

    /**
     * Advances to the frame at [frameNanos], given the player's [sample] read in it.
     * Returns whether the sample jumped more than [jumpMs] from where the clock expected
     * it — how an unannounced seek shows up.
     */
    fun onFrame(
        frameNanos: Long,
        sample: Long,
        speed: Float,
        advancing: Boolean,
        jumpMs: Long = Long.MAX_VALUE,
    ): Boolean {
        val firstFrame = lastFrameNanos == NoFrame
        val dtMs = if (firstFrame) 0.0 else (frameNanos - lastFrameNanos) / 1e6
        var jumped = false
        if (sample != lastSample || anchorNanos == NoFrame) {
            if (sample != lastSample && anchorNanos != NoFrame) {
                jumped = abs(sample - estimateAt(frameNanos, speed)) > jumpMs
            }
            anchorPosition = sample.toDouble()
            anchorNanos = if (firstFrame) frameNanos else frameNanos - (frameNanos - lastFrameNanos) / 2
            lastSample = sample
        }
        lastFrameNanos = frameNanos

        if (!advancing) {
            shown = sample.toDouble()
            anchorPosition = shown
            anchorNanos = frameNanos
            return jumped
        }
        val estimate = estimateAt(frameNanos, speed)
        if (firstFrame || jumped) {
            shown = estimate
            return jumped
        }
        // Out of fresh readings for too long: hold, neither running on nor stepping back.
        val stalled = (frameNanos - anchorNanos) / 1e6 >= maxExtrapolationMs
        val predicted = if (stalled) shown else shown + dtMs * speed
        val error = estimate - predicted
        shown = if (abs(error) > SnapMs && !stalled) {
            estimate
        } else {
            val limit = dtMs * speed * MaxSlew
            predicted + (error * (1.0 - exp(-dtMs / SlewMs))).coerceIn(if (stalled) 0.0 else -limit, limit)
        }
        return jumped
    }

    private fun estimateAt(frameNanos: Long, speed: Float): Double {
        val since = ((frameNanos - anchorNanos) / 1e6).coerceIn(0.0, maxExtrapolationMs)
        return anchorPosition + since * speed
    }

    companion object {
        private const val NoFrame = Long.MIN_VALUE

        /** Past this much time without a fresh reading the clock holds rather than guess. */
        const val DefaultMaxExtrapolationMs = 1_000.0

        /** A difference this large is a move, not drift, and is taken at once. */
        const val SnapMs = 250.0

        /** How quickly the shown time closes on the estimate (time constant, ms). */
        const val SlewMs = 120.0

        /** The most the shown time may run faster or slower than real time while it closes. */
        const val MaxSlew = 0.5
    }
}
