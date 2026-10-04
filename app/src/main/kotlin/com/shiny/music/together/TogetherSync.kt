package com.shiny.music.together

import kotlin.math.abs

/**
 * The server's clock, estimated from ping/pong the way NTP does: each round trip gives an
 * offset (server time minus the local time at the midpoint), and the round trip with the
 * least delay is the one to believe, because its midpoint guess can be off by at most half
 * of that delay.
 */
class TogetherClock {
    private val samples = ArrayDeque<Sample>()

    private data class Sample(val rtt: Long, val offset: Long)

    @Volatile
    var offsetMs: Long = 0L
        private set

    @Volatile
    var rttMs: Long = -1L
        private set

    val synced: Boolean get() = rttMs >= 0

    @Synchronized
    fun onPong(clientSent: Long, server: Long, clientReceived: Long) {
        val rtt = clientReceived - clientSent
        if (rtt < 0 || rtt > 15_000) return
        samples.addLast(Sample(rtt, server - (clientSent + rtt / 2)))
        while (samples.size > MaxSamples) samples.removeFirst()
        val best = samples.minBy { it.rtt }
        offsetMs = best.offset
        rttMs = best.rtt
    }

    /** A server timestamp that arrived without a round trip (welcome): good until pings land. */
    @Synchronized
    fun seed(server: Long, clientReceived: Long) {
        if (!synced) offsetMs = server - clientReceived
    }

    @Synchronized
    fun reset() {
        samples.clear()
        offsetMs = 0L
        rttMs = -1L
    }

    fun serverNow(local: Long = System.currentTimeMillis()): Long = local + offsetMs

    private companion object {
        const val MaxSamples = 10
    }
}

/**
 * How a following phone keeps up with the host.
 *
 * Small drift is corrected by playing a few percent fast or slow (with pitch held), which
 * nobody hears; only a big gap — a late start, a stall — is closed by jumping. A jump is
 * aimed a little ahead to cover the time the player takes to resume.
 */
object TogetherSyncPolicy {
    /** Beyond this the player jumps. */
    const val SeekThresholdMs = 1_000L

    /** Within this the phone is in sync and plays at normal speed. */
    const val InSyncMs = 35L

    /** Once bending, keep going until this close, so the speed doesn't flutter at the edge. */
    const val SettleMs = 12L

    /** The most the speed is bent, either way. */
    const val MaxRateBend = 0.05f

    /** Where a jump lands ahead of the target, for the player's own restart time. */
    const val SeekLeadMs = 90L

    sealed interface Correction {
        data object None : Correction
        data class Rate(val speed: Float) : Correction
        data class Seek(val toMs: Long) : Correction
    }

    fun expectedPosition(playback: TogetherPlayback, serverNow: Long): Long {
        if (!playback.playing || playback.buffering) return playback.positionMs
        val elapsed = (serverNow - playback.at).coerceAtLeast(0L)
        return playback.positionMs + (elapsed * playback.rate).toLong()
    }

    /**
     * [actualMs] is where this phone is, [expectedMs] where the room is (already adjusted
     * for this phone's output latency), [currentSpeed] what the player plays at now.
     *
     * The bend comes in three steps rather than a continuous curve: every change of speed
     * makes the player re-prime its time-stretcher, and a speed that changed on every tick
     * cost about as much as it corrected.
     */
    fun correction(actualMs: Long, expectedMs: Long, currentSpeed: Float = 1f): Correction {
        val drift = actualMs - expectedMs
        val size = abs(drift)
        val bending = abs(currentSpeed - 1f) > 0.001f
        return when {
            size > SeekThresholdMs -> Correction.Seek(expectedMs + SeekLeadMs)
            size <= (if (bending) SettleMs else InSyncMs) -> Correction.None
            else -> {
                val bend = when {
                    size <= 150 -> 0.015f
                    size <= 400 -> 0.03f
                    else -> MaxRateBend
                }
                // Ahead of the room: slow down. Behind: speed up.
                Correction.Rate(if (drift > 0) 1f - bend else 1f + bend)
            }
        }
    }
}
