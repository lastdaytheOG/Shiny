package com.shiny.music.ui.liquid.player

import androidx.compose.runtime.Immutable
import com.shiny.music.lyrics.LyricsEntry

/*
 * The lyrics' timing, prepared once per song for the animation engine: every line's timed
 * pieces as primitive arrays, and the pure functions a frame evaluates against them — which
 * line is sung, how far the page is through handing over to it, which words are lit and how
 * bright each line is. Nothing here depends on Compose, so all of it is tested directly, and
 * nothing here allocates once built.
 */

/** A silence this long between two sung lines is an interlude, and shows the dots. */
internal const val InterludeGapMs = 4500L

/** How long the sung line takes to step back once the interlude dots appear. */
internal const val InterludeFadeMs = 700L

/** How long a word's glow takes to come up once it starts. */
internal const val GlowAttackMs = 140f

/** How long a word's glow lingers and fades after the word ends. */
internal const val GlowReleaseMs = 400L

/** A note held this long glows brighter. */
internal const val HeldNoteMs = 1100L

/**
 * How long the highlight takes to pass from one line to the next, starting
 * [LyricClockPolicy.LineLeadMs] before the new line's timestamp. Shorter when the next line
 * follows sooner, so a hand-over always finishes before the next one begins.
 */
internal const val LineTransitionMs = 380L

/** Brightness of a word not yet sung — and of the line waiting next. */
internal const val UnsungAlpha = 0.40f

/** Every line while the reader scrolls by hand: all of them readable. */
internal const val BrowsingAlpha = 0.55f

/** A line range meaning "no line is live". */
internal const val NoLive = -1L

/**
 * One synced line: its text, and its timed pieces — words, or syllables from a provider with
 * syllable timing — with where each sits in the text.
 */
@Immutable
internal class LyricLineTiming(
    val text: String,
    /** The line's own timestamp. */
    val start: Long,
    /** When the next line starts (a long silence between shows the interlude dots). */
    val nextStart: Long,
    /** Piece start times, never decreasing. */
    val pieceStart: LongArray,
    val pieceEnd: LongArray,
    /** Where each piece is in [text]: `from` inclusive, `to` exclusive; empty if unmatched. */
    val pieceFrom: IntArray,
    val pieceTo: IntArray,
) {
    val pieceCount: Int get() = pieceStart.size

    /** When the singing of the line ends. */
    val end: Long = pieceEnd.maxOrNull() ?: start

    val interlude: Boolean = nextStart - end >= InterludeGapMs

    /**
     * The span in which what the line shows depends on the exact time: from the moment it
     * starts to become the sung line until its last word has let its glow go (and, before an
     * interlude, until it has stepped back). Outside it the line is fixed ink.
     */
    val liveFrom: Long = minOf(start - LyricClockPolicy.LineLeadMs, pieceStart.firstOrNull() ?: start)
    val liveUntil: Long = end + if (interlude) maxOf(GlowReleaseMs, InterludeFadeMs) else GlowReleaseMs

    /** How many pieces have started at [t]. Binary search: pieces are ordered by start. */
    fun startedAt(t: Double): Int {
        var low = 0
        var high = pieceStart.size - 1
        var count = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pieceStart[mid] <= t) {
                count = mid + 1
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return count
    }

    /** How far through piece [i] the voice is at [t]: `(t - start) / (end - start)`, clamped. */
    fun progress(i: Int, t: Double): Float {
        val s = pieceStart[i]
        val d = (pieceEnd[i] - s).coerceAtLeast(1L)
        return ((t - s) / d).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Piece [i]'s glow at [t]: comes up over [GlowAttackMs] as the piece starts, holds while
     * it is sung — brighter for a held note — and fades over [GlowReleaseMs] after it.
     */
    fun glow(i: Int, t: Double): Float {
        val s = pieceStart[i]
        val e = pieceEnd[i]
        if (t < s || t >= e + GlowReleaseMs) return 0f
        val peak = if (e - s >= HeldNoteMs) 0.45f else 0.30f
        val up = smoothstep(((t - s) / GlowAttackMs).toFloat().coerceIn(0f, 1f))
        val down = if (t <= e) 1f else 1f - smoothstep(((t - e) / GlowReleaseMs).toFloat().coerceIn(0f, 1f))
        return peak * up * down
    }
}

/** All the synced lines of a song, ordered by time. */
@Immutable
internal class LyricTimeline(val lines: List<LyricLineTiming>) {
    val size: Int get() = lines.size
    private val starts = LongArray(lines.size) { lines[it].start }
    private val transition = LongArray(lines.size) { j ->
        val next = if (j + 1 < lines.size) lines[j + 1].start - lines[j].start else LineTransitionMs
        minOf(LineTransitionMs, next)
    }

    /**
     * The sung line at display time [t]: the last one whose timestamp, less the line lead,
     * has passed; -1 before the first. Binary search.
     */
    fun lineAt(t: Double): Int {
        val lookup = t + LyricClockPolicy.LineLeadMs
        var low = 0
        var high = starts.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= lookup) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Where the highlight is, as a continuous line position: exactly `k` while line `k` is
     * sung, moving smoothly from `k - 1` to `k` over the hand-over that begins as line `k`
     * becomes current. -1 before the first line. Every line's brightness and the page's
     * position are functions of this one number, so none of them can jump between frames.
     */
    fun cursor(t: Double, k: Int = lineAt(t)): Float {
        if (k < 0) return -1f
        val span = transition[k]
        val f = if (span <= 0L) 1f else ((t + LyricClockPolicy.LineLeadMs - starts[k]) / span).toFloat().coerceIn(0f, 1f)
        return k - 1 + smoothstep(f)
    }

    /**
     * The lines whose ink depends on the exact time at [t] (see [LyricLineTiming.liveFrom]),
     * as a packed `lo..hi` range, or [NoLive]. [k] is the sung line.
     */
    fun liveRange(t: Double, k: Int): Long {
        var lo = -1
        var hi = -1
        val from = (k - LiveLookBack).coerceAtLeast(0)
        val to = minOf(k + 1, lines.size - 1)
        for (j in from..to) {
            val line = lines[j]
            if (line.liveFrom <= t && t <= line.liveUntil) {
                if (lo < 0) lo = j
                hi = j
            }
        }
        return if (lo < 0) NoLive else packRange(lo, hi)
    }

    private companion object {
        /** How many lines back a glow or step-back can still be running. */
        const val LiveLookBack = 8
    }
}

internal fun packRange(lo: Int, hi: Int): Long = (lo.toLong() shl 32) or (hi.toLong() and 0xFFFFFFFFL)

internal fun rangeContains(range: Long, index: Int): Boolean =
    range != NoLive && index >= (range ushr 32).toInt() && index <= range.toInt()

internal fun smoothstep(x: Float) = x * x * (3f - 2f * x)

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/**
 * How bright a line is at [distance] lines from the highlight, as white text: the sung line
 * full, the lines either side at [UnsungAlpha] — a word not yet sung — and further lines
 * receding. Continuous in the distance, so a line passing through the hand-over changes
 * brightness smoothly.
 */
internal fun lineDim(distance: Float): Float = when {
    distance <= 0f -> 1f
    distance <= 1f -> lerp(1f, UnsungAlpha, distance)
    distance <= 2f -> lerp(UnsungAlpha, 0.30f, distance - 1f)
    distance <= 3f -> lerp(0.30f, 0.22f, distance - 2f)
    else -> 0.22f
}

/**
 * The opacity of line [index]'s layer with the highlight at [cursor], while the page follows.
 *
 * A [live] line draws its words itself — sung ones white, the rest at [UnsungAlpha] — so on
 * its way in it stays at full opacity and its words carry the dimming: a line one away looks
 * exactly as it did as plain dimmed text, and becoming the sung line changes nothing on
 * screen until the voice reaches its first word. Every other line is white text dimmed here.
 */
internal fun followAlpha(index: Int, cursor: Float, live: Boolean): Float {
    val d = index - cursor
    return when {
        d <= 0f -> lineDim(-d)
        live -> (lineDim(maxOf(d, 1f)) / UnsungAlpha).coerceAtMost(1f)
        else -> lineDim(d)
    }
}

/**
 * Before an interlude, once its last word is sung the line steps back so the dots, not the
 * old line, hold the eye.
 */
internal fun interludeStepBack(t: Double, end: Long): Float {
    val since = t - end
    return if (since <= 0.0) 1f else 1f - 0.62f * (since / InterludeFadeMs).toFloat().coerceIn(0f, 1f)
}

/**
 * The full opacity of line [index]'s layer: [followAlpha], the interlude step-back, and
 * [browse] (0 following, 1 browsing by hand) blending towards [BrowsingAlpha].
 */
internal fun lineAlpha(line: LyricLineTiming, index: Int, cursor: Float, live: Boolean, t: Double, words: Boolean, browse: Float): Float {
    var a = followAlpha(index, cursor, live)
    if (words && line.interlude) a = minOf(a, interludeStepBack(t, line.end))
    if (browse > 0f) a = lerp(a, if (live && index >= cursor) 1f else BrowsingAlpha, browse)
    return a
}

/**
 * How long, in seconds, a line [below] lines under the highlight trails the page's glide:
 * nothing for the sung line and those above it, a little more for each line below, capped so
 * the far lines stay a calm block. Continuous in [below], so the trail never jumps as the
 * highlight moves on.
 */
internal fun waveLag(below: Float): Float = 0.009f * below.coerceIn(0f, 5f)

private val CjkRegex = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")

/**
 * Where each of [parts] sits in [text], searched in order: a provider's words and syllables
 * are the text in pieces, so syllables of one word ("l", "ong") land side by side inside it
 * and the word is laid out whole. A piece the text does not contain gets an empty range at
 * the point reached, and lights nothing of its own.
 */
internal fun pieceRanges(text: String, parts: List<String>): Pair<IntArray, IntArray> {
    val from = IntArray(parts.size)
    val to = IntArray(parts.size)
    var cursor = 0
    parts.forEachIndexed { i, part ->
        var at = text.indexOf(part, cursor)
        if (at < 0) at = text.indexOf(part, cursor, ignoreCase = true)
        if (at >= 0) {
            from[i] = at
            to[i] = at + part.length
            cursor = at + part.length
        } else {
            from[i] = cursor
            to[i] = cursor
        }
    }
    return from to to
}

/**
 * Gives every synced line word timings. Providers with word sync keep theirs; line-synced
 * lyrics get an estimate — the line's words spread over its time by length, at a singing
 * pace — so every song fills word by word rather than lighting whole lines.
 */
internal fun buildTimeline(lines: List<LyricsEntry>): LyricTimeline = LyricTimeline(
    lines.mapIndexed { i, line ->
        val text = line.text.trim()
        val nextTime = lines.getOrNull(i + 1)?.time ?: (line.time + 8000L)
        val spaced = text.contains(' ') || !CjkRegex.containsMatchIn(text)
        val texts = ArrayList<String>()
        val starts = ArrayList<Long>()
        val ends = ArrayList<Long>()
        line.words?.forEach { w ->
            val word = w.text.trim()
            if (word.isEmpty()) return@forEach
            val start = maxOf((w.startTime * 1000).toLong(), starts.lastOrNull() ?: Long.MIN_VALUE)
            texts += word
            starts += start
            ends += maxOf((w.endTime * 1000).toLong(), start + 80L)
        }
        if (texts.isEmpty()) {
            val parts = if (spaced) {
                text.split(' ').filter { it.isNotBlank() }
            } else {
                text.map { it.toString() }.filter { it.isNotBlank() }
            }
            val weight = parts.sumOf { it.length + 1 }.coerceAtLeast(1)
            val room = (nextTime - line.time).coerceAtLeast(400L)
            val span = (weight * 85L).coerceIn(700L, 7000L).coerceAtMost((room * 0.92f).toLong())
            var t = line.time
            parts.forEach { p ->
                val d = span * (p.length + 1) / weight
                texts += p
                starts += t
                ends += t + d
                t += d
            }
        }
        val (from, to) = pieceRanges(text, texts)
        LyricLineTiming(
            text = text,
            start = line.time,
            nextStart = nextTime,
            pieceStart = starts.toLongArray(),
            pieceEnd = ends.toLongArray(),
            pieceFrom = from,
            pieceTo = to,
        )
    },
)
