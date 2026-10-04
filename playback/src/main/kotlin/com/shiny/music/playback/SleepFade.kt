package com.shiny.music.playback

import kotlinx.coroutines.delay

/** The sleep timer's fade to silence, as plain numbers and a loop that can run without a player. */
object SleepFade {
    const val DURATION_MS = 4_000L
    const val STEP_MS = 50L

    /**
     * The share of the starting volume that is left after [elapsedMs]: 1 at the start, 0 at the
     * end. It falls quickly at first and slowly near silence, which is how an even fade sounds,
     * because loudness does not follow amplitude in a straight line.
     */
    fun level(elapsedMs: Long, durationMs: Long = DURATION_MS): Float {
        if (durationMs <= 0 || elapsedMs >= durationMs) return 0f
        if (elapsedMs <= 0) return 1f
        val left = 1f - elapsedMs.toFloat() / durationMs
        return left * left
    }

    /** Steps the volume from [from] down to 0 over [DURATION_MS]. Cancelling stops it where it is. */
    suspend fun run(
        from: Float,
        setVolume: (Float) -> Unit,
        pause: suspend (Long) -> Unit = { delay(it) },
    ) {
        var elapsed = 0L
        while (elapsed < DURATION_MS) {
            setVolume(from * level(elapsed))
            pause(STEP_MS)
            elapsed += STEP_MS
        }
        setVolume(0f)
    }
}
