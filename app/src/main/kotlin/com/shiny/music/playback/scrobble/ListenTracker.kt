package com.shiny.music.playback.scrobble

/**
 * Follows one play at a time and says when ListenBrainz should hear about it.
 *
 * "Playing now" goes out once, when the track first sounds. The listen itself goes out once
 * the track has sounded for half its length or four minutes, whichever is shorter, and never
 * twice for one play. Only time spent sounding counts: a pause or a stall does not move it,
 * and a seek neither adds nor removes any.
 *
 * Nothing here touches a player or the network. The caller reports the state on every change
 * and sends what comes back, so the rules can be tested with a clock that is just a number.
 */
class ListenTracker(
    private val uptimeMs: () -> Long,
    private val wallTimeMs: () -> Long,
    private val submit: (Submission) -> Unit,
) {
    data class Track(
        val id: String,
        val title: String,
        val artists: String,
        val album: String,
        /** 0 while the length is not known yet. */
        val durationMs: Long,
    )

    sealed interface Submission {
        val track: Track

        data class PlayingNow(override val track: Track) : Submission

        data class Listen(
            override val track: Track,
            val startedAtMs: Long,
            val endedAtMs: Long,
        ) : Submission
    }

    private class Play(var track: Track) {
        var startedAtMs = 0L
        var heardMs = 0L
        var soundingSince = NOT_SOUNDING
        var announced = false
        var counted = false
    }

    private var play: Play? = null

    /**
     * Reports the current state. [track] is what the player holds, or null for nothing (or for
     * something that is not scrobbled); [playing] is whether it is sounding right now;
     * [newPlay] says the track has started over even though it is the same one (repeat).
     *
     * Returns how many more milliseconds of playing the listen needs, so the caller can report
     * again then. Null means there is nothing to wait for.
     */
    fun update(track: Track?, playing: Boolean, newPlay: Boolean = false): Long? {
        if (track == null) {
            play = null
            return null
        }
        val now = uptimeMs()
        val known = play?.takeIf { it.track.id == track.id && !newPlay }
        val current = known ?: Play(track).also { play = it }
        // The length often arrives after the track has started.
        current.track = track
        val needed = neededMs(track.durationMs)

        if (playing) {
            if (current.soundingSince == NOT_SOUNDING) current.soundingSince = now
            if (!current.announced && needed != null) {
                current.announced = true
                current.startedAtMs = wallTimeMs()
                submit(Submission.PlayingNow(track))
            }
        } else if (current.soundingSince != NOT_SOUNDING) {
            current.heardMs += now - current.soundingSince
            current.soundingSince = NOT_SOUNDING
        }

        if (current.counted || needed == null || !current.announced) return null
        val heard = current.heardMs +
            if (current.soundingSince == NOT_SOUNDING) 0L else now - current.soundingSince
        if (heard >= needed) {
            current.counted = true
            submit(Submission.Listen(track, current.startedAtMs, wallTimeMs()))
            return null
        }
        return if (playing) needed - heard else null
    }

    companion object {
        /** A track shorter than this is a jingle or a skit, not a listen. */
        const val SHORTEST_TRACK_MS = 30_000L

        /** No track needs more than this much playing, however long it is. */
        const val LONGEST_WAIT_MS = 240_000L

        private const val NOT_SOUNDING = -1L

        /** Play time a track of [durationMs] needs to count, or null when it never does. */
        fun neededMs(durationMs: Long): Long? = when {
            durationMs <= 0L -> LONGEST_WAIT_MS
            durationMs < SHORTEST_TRACK_MS -> null
            else -> minOf(durationMs / 2, LONGEST_WAIT_MS)
        }
    }
}
