// SongVideoMap.kt

package com.shiny.music.video

import kotlin.math.abs
import kotlin.math.roundToLong

// where a song's moments sit in its music video: stretches of steady shift, split where the video adds an intro or a skit, or cuts a part
class SongVideoMap(val runs: List<Run>) {

    // from one song time to another, the video runs deltaMs ahead of the song (behind, when negative); points, when there are any, follow a slow drift inside it
    class Run(
        val fromSongMs: Long,
        val toSongMs: Long,
        val deltaMs: Long,
        val points: List<Point> = emptyList(),
    ) {
        // the shift at a song time in this stretch, read off its points where it has them; a little past either end its drift carries on
        fun deltaAt(songMs: Long): Long {
            if (points.size < 2) return points.firstOrNull()?.deltaMs ?: deltaMs
            val first = points.first()
            val last = points.last()
            if (songMs <= first.songMs) return first.deltaMs - (driftRate * minOf(first.songMs - songMs, MaxReachMs)).roundToLong()
            if (songMs >= last.songMs) return last.deltaMs + (driftRate * minOf(songMs - last.songMs, MaxReachMs)).roundToLong()
            val i = points.indexOfFirst { it.songMs >= songMs }
            return between(points[i - 1].songMs, points[i - 1].deltaMs, points[i].songMs, points[i].deltaMs, songMs)
        }

        val firstDeltaMs: Long get() = points.firstOrNull()?.deltaMs ?: deltaMs
        val lastDeltaMs: Long get() = points.lastOrNull()?.deltaMs ?: deltaMs

        // how much the shift grows per ms of song across this stretch: 0 for a video at the album's own speed
        val driftRate: Double
            get() {
                if (points.size < 2) return 0.0
                val span = (points.last().songMs - points.first().songMs).coerceAtLeast(1L)
                return (points.last().deltaMs - points.first().deltaMs).toDouble() / span
            }
    }

    // the shift measured at one song time
    class Point(val songMs: Long, val deltaMs: Long)

    // a shift under this is only timing noise, not worth moving anything for
    val isShifted: Boolean
        get() = runs.any { run -> abs(run.deltaMs) >= MinShiftMs || run.points.any { abs(it.deltaMs) >= MinShiftMs } }

    // how far the video is from the song at a song time; between two stretches a drift is carried across, a cut goes to the nearer one
    fun deltaAt(songMs: Long): Long {
        val after = runs.indexOfFirst { it.toSongMs >= songMs }
        if (after == -1) return runs.last().deltaAt(songMs)
        val run = runs[after]
        if (songMs >= run.fromSongMs || after == 0) return run.deltaAt(songMs)
        val before = runs[after - 1]
        val gap = run.fromSongMs - before.toSongMs
        val jump = run.firstDeltaMs - before.lastDeltaMs
        // the next stretch starts about where the last one's drift was heading: one slow drift split in two, joined straight across the gap
        // only stretches read off captions drift; the audio match's are the same recording, so a jump between them is a real edit
        val drifting = before.points.size > 1 && run.points.size > 1
        val expected = before.lastDeltaMs + before.driftRate * gap
        if (drifting && abs(run.firstDeltaMs - expected) <= DriftJoinMs && abs(jump) * MaxDriftSlope <= gap) {
            return between(before.toSongMs, before.lastDeltaMs, run.fromSongMs, run.firstDeltaMs, songMs)
        }
        return if (songMs - before.toSongMs < run.fromSongMs - songMs) before.deltaAt(songMs) else run.deltaAt(songMs)
    }

    fun videoMsAt(songMs: Long): Long = songMs + deltaAt(songMs)

    override fun toString(): String =
        runs.joinToString { "${it.fromSongMs}..${it.toSongMs}:${it.deltaMs}" + if (it.points.size > 1) "(${it.firstDeltaMs}->${it.lastDeltaMs})" else "" }

    private companion object {
        const val MinShiftMs = 250L

        // the most two stretches' shifts may differ by to still count as one drift
        const val DriftJoinMs = 450L

        // a drift carried across a gap changes by at most a quarter of the gap, so the video's time keeps moving forward
        const val MaxDriftSlope = 4L

        // how far past its ends a stretch's drift is carried before it's held flat
        const val MaxReachMs = 30_000L
    }
}

// the straight line from (fromMs, fromDelta) to (toMs, toDelta), read at atMs
private fun between(fromMs: Long, fromDelta: Long, toMs: Long, toDelta: Long, atMs: Long): Long {
    if (toMs <= fromMs) return toDelta
    val t = (atMs - fromMs).toDouble() / (toMs - fromMs)
    return fromDelta + ((toDelta - fromDelta) * t.coerceIn(0.0, 1.0)).roundToLong()
}
