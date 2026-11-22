package com.shiny.music.stats

class PlayCounter<T>(
    private val clock: () -> Long,
    private val thresholdMs: () -> Long,
    private var allowed: Boolean,
    private val save: (Play<T>, Long) -> Unit,
    private val finished: (Play<T>) -> Unit,
) {
    class Play<T>(val songId: String, val item: T, private val clock: () -> Long) {
        @Volatile
        private var state = 0L to NOT_RUNNING

        @Volatile
        var eventId: Long? = null

        var counted = false
            internal set

        internal var savedMs = 0L

        fun heardMs(): Long {
            val (banked, since) = state
            return if (since == NOT_RUNNING) banked else banked + clock() - since
        }

        internal fun run(running: Boolean) {
            state = heardMs() to if (running) clock() else NOT_RUNNING
        }
    }

    var play: Play<T>? = null
        private set

    var playing = false
        private set

    fun start(songId: String, item: T) {
        finish()
        play = Play(songId, item, clock).also { it.run(playing && allowed) }
    }

    fun setPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        play?.run(value && allowed)
        checkpoint()
    }

    fun setAllowed(value: Boolean) {
        if (allowed == value) return
        allowed = value
        play?.run(playing && value)
        checkpoint()
    }

    fun checkpoint() {
        val current = play ?: return
        val heard = current.heardMs()
        if (heard <= current.savedMs) return
        if (!current.counted && (!allowed || heard < thresholdMs())) return
        current.counted = true
        current.savedMs = heard
        save(current, heard)
    }

    fun untilCheckpointMs(): Long? {
        val current = play ?: return null
        if (!playing || !allowed) return null
        if (current.counted) return SAVE_EVERY_MS
        return (thresholdMs() - current.heardMs()).coerceIn(1_000L, RECHECK_MS)
    }

    fun finish() {
        val current = play ?: return
        current.run(false)
        checkpoint()
        play = null
        if (current.counted) finished(current)
    }

    private companion object {
        const val SAVE_EVERY_MS = 5 * 60_000L
        const val RECHECK_MS = 15_000L
    }
}

private const val NOT_RUNNING = -1L
