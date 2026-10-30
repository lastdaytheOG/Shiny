package com.shiny.music.intelligence

import com.shiny.music.db.entities.RecSurface

class ListenSessionTracker(
    private val uptimeMs: () -> Long,
    private val wallTimeMs: () -> Long,
    private val emit: (ListenDraft) -> Unit,
) {
    data class Track(
        val songId: String,
        val durationMs: Long,
        val surface: RecSurface,
        val pickSource: String?,
        val selection: Selection,
        val queuePosition: Int,
    )

    private class Play(var track: Track, val startedAt: Long) {
        var heardMs = 0L
        var soundingSince = NOT_SOUNDING
    }

    private var play: Play? = null
    private var sessionId = 0L
    private var lastEndWall = Long.MIN_VALUE
    private var lastSkipUptime = Long.MIN_VALUE

    val current: Track? get() = play?.track

    fun start(track: Track, playing: Boolean) {
        val wall = wallTimeMs()
        if (sessionId == 0L || lastEndWall == Long.MIN_VALUE || wall - lastEndWall > SESSION_GAP_MS) {
            sessionId = wall
        }
        val next = Play(track, wall)
        if (playing) next.soundingSince = uptimeMs()
        play = next
    }

    fun setPlaying(playing: Boolean) {
        val current = play ?: return
        val now = uptimeMs()
        if (playing) {
            if (current.soundingSince == NOT_SOUNDING) current.soundingSince = now
        } else if (current.soundingSince != NOT_SOUNDING) {
            current.heardMs += now - current.soundingSince
            current.soundingSince = NOT_SOUNDING
        }
    }

    fun setDuration(durationMs: Long) {
        val current = play ?: return
        if (durationMs > 0L && current.track.durationMs <= 0L) current.track = current.track.copy(durationMs = durationMs)
    }

    fun end(reason: EndReason) {
        val finished = play ?: return
        play = null
        val now = uptimeMs()
        val heard = finished.heardMs + if (finished.soundingSince == NOT_SOUNDING) 0L else now - finished.soundingSince
        val track = finished.track
        val userLeft = reason == EndReason.SkippedNext || reason == EndReason.SkippedBack
        val burst = userLeft && lastSkipUptime != Long.MIN_VALUE && now - lastSkipUptime <= ListenClassifier.SKIP_BURST_MS
        if (userLeft) lastSkipUptime = now
        val wall = wallTimeMs()
        lastEndWall = wall
        emit(
            ListenDraft(
                track = track,
                sessionId = sessionId,
                startedAt = finished.startedAt,
                endedAt = wall,
                listenedMs = heard,
                completion = ListenClassifier.completion(heard, track.durationMs),
                reason = reason,
                kind = ListenClassifier.classify(heard, track.durationMs, reason, track.selection, burst),
            ),
        )
    }

    companion object {
        const val SESSION_GAP_MS = 30L * 60 * 1000
        private const val NOT_SOUNDING = -1L
    }
}

data class ListenDraft(
    val track: ListenSessionTracker.Track,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val listenedMs: Long,
    val completion: Double,
    val reason: EndReason,
    val kind: ListenKind,
)
