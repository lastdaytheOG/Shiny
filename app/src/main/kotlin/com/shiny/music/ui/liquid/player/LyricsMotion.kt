package com.shiny.music.ui.liquid.player

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.Player
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The lyrics' animation engine: one frame loop, driven by the playback position, publishing a
 * handful of numbers that everything on the page is a function of.
 *
 * - [time]: the playback time this frame shows (per-song offset included), continuous and
 *   frame-locked ([LyricClockEstimator]). Only lines being sung read it, in their draw.
 * - [cursor]: where the highlight is between lines ([LyricTimeline.cursor]). Lines read it in
 *   their layer to set their opacity; it only moves during a hand-over.
 * - [liveRange]: which lines' ink depends on the exact time; changes a few times a line.
 * - [velocity]: the page's own glide speed, for the trail of the lines below.
 *
 * Nothing here is read in composition, so the page never recomposes as the song plays: a line
 * change is a new value of [cursor], not a new list. There are no per-line animations to start
 * or restart — every line's look is evaluated from these values in its layer and draw.
 */
@Stable
internal class LyricsMotion(
    val timeline: LyricTimeline,
    /** Whether words fill one by one (the Word by word setting). */
    val words: Boolean,
) {
    private val timeState = mutableDoubleStateOf(0.0)
    private val cursorState = mutableFloatStateOf(-1f)
    private val pageCursorState = mutableFloatStateOf(-1f)
    private val lineState = mutableIntStateOf(-1)
    private val liveState = mutableLongStateOf(NoLive)
    private val seekState = mutableIntStateOf(0)
    private val velocityState = mutableFloatStateOf(0f)

    /** The time the current frame shows, in ms. Reading it redraws the reader every frame. */
    val time: Double get() = timeState.doubleValue

    /** The same time without subscribing to it, for a reader already woken by something coarser. */
    var timeNow: Double = 0.0
        private set

    val cursor: Float get() = cursorState.floatValue

    /** The highlight a little ahead, for the page: its glide then lands as the line is sung. */
    val pageCursor: Float get() = pageCursorState.floatValue

    /** The sung line, -1 before the first. */
    val line: Int get() = lineState.intValue

    val liveRange: Long get() = liveState.longValue

    /** Bumped on every seek, so the page can cut to the new place. */
    val seeks: Int get() = seekState.intValue

    var velocity: Float
        get() = velocityState.floatValue
        set(value) {
            velocityState.floatValue = value
        }

    fun isLive(index: Int): Boolean = rangeContains(liveState.longValue, index)

    /** Publishes the frame at display time [t]. Allocation-free; unchanged values notify no one. */
    fun show(t: Double) {
        timeNow = t
        timeState.doubleValue = t
        val k = timeline.lineAt(t)
        lineState.intValue = k
        cursorState.floatValue = timeline.cursor(t, k)
        pageCursorState.floatValue = timeline.cursor(t + PageLeadMs)
        liveState.longValue = if (words) timeline.liveRange(t, k) else NoLive
    }

    fun seeked() {
        seekState.intValue++
    }

    private companion object {
        /** How far ahead of the highlight the page aims, to make up for its spring's lag. */
        const val PageLeadMs = 110.0
    }
}

/**
 * The player the lyrics listen to for seeks. Crossfade swaps the service's player for a new
 * instance mid-playback, so the listener moves to whichever instance is current.
 */
private class SeekTap(private val onJump: (seek: Boolean) -> Unit) : Player.Listener {
    var player: Player? = null
        private set

    fun attach(target: Player?) {
        if (target === player) return
        player?.removeListener(this)
        player = target
        target?.addListener(this)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        onJump(reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)
    }
}

/**
 * Runs the engine for [timeline] while it is on screen.
 *
 * Playing, it ticks once per display frame; paused, it does nothing until the position moves
 * (a seek), so a paused lyrics page costs no frames at all. A seek on the local player arrives
 * as a position discontinuity, on the main thread and in order, and is shown at once. With
 * no player to read, the polled position is extrapolated instead; a reading far from the
 * expected position counts as a seek.
 */
@Composable
internal fun rememberLyricsMotion(
    timeline: LyricTimeline,
    words: Boolean,
    playerProvider: () -> Player?,
    positionProvider: () -> Long,
    playing: Boolean,
    songOffsetMs: Long,
): LyricsMotion {
    val motion = remember(timeline, words) { LyricsMotion(timeline, words) }
    val sample: (Player?) -> Long = remember(positionProvider) {
        { player ->
            if (player == null) {
                positionProvider()
            } else {
                try {
                    player.currentPosition
                } catch (e: RuntimeException) {
                    positionProvider()
                }
            }
        }
    }
    val source = playerProvider
    val local = remember(motion) { LyricClockEstimator() }
    val polled = remember(motion) { LyricClockEstimator(maxExtrapolationMs = Double.MAX_VALUE) }
    val tap = remember(motion, songOffsetMs) {
        SeekTap { seek ->
            val p = source()
            val estimator = if (p == null) polled else local
            estimator.reset(sample(p))
            motion.show(estimator.shown + songOffsetMs)
            if (seek) motion.seeked()
        }
    }
    DisposableEffect(tap) {
        tap.attach(source())
        onDispose { tap.attach(null) }
    }

    LaunchedEffect(motion, tap, playing, songOffsetMs) {
        run {
            val p = source()
            tap.attach(p)
            val estimator = if (p == null) polled else local
            estimator.reset(sample(p))
            motion.show(estimator.shown + songOffsetMs)
        }
        if (!playing) {
            // Paused, the position only moves on a seek. The listener catches seeks on the
            // player; with no player to listen to, a seek only shows up in the polled
            // position. Nothing runs while the position stands still.
            snapshotFlow { positionProvider() }.collect {
                val p = source()
                val estimator = if (p == null) polled else local
                val position = sample(p)
                if (p == null && LyricClockPolicy.isPolledJump(estimator.shown.toLong(), position)) motion.seeked()
                estimator.reset(position)
                motion.show(estimator.shown + songOffsetMs)
            }
            return@LaunchedEffect
        }
        // One callback object for the whole run: a frame allocates nothing of its own.
        val onFrame: (Long) -> Unit = { frame ->
            val p = source()
            if (p !== tap.player) {
                // The service swapped players (crossfade): start again from the new one.
                tap.attach(p)
                local.reset(sample(p))
            }
            if (p == null) {
                if (polled.onFrame(frame, positionProvider(), 1f, advancing = true, jumpMs = LyricClockPolicy.PolledJumpMs)) {
                    motion.seeked()
                }
                motion.show(polled.shown + songOffsetMs)
            } else {
                // `isPlaying` rather than the sheet's playing flag: it is false while buffering,
                // when the audio stands still and the lyrics must too.
                local.onFrame(frame, sample(p), p.playbackParameters.speed, advancing = p.isPlaying)
                motion.show(local.shown + songOffsetMs)
            }
        }
        while (true) withFrameNanos(onFrame)
    }
    return motion
}

/** Where the page holds the sung line: this far down the viewport. */
private const val AnchorFraction = 0.24f

/** How firmly the page follows the highlight: a critically damped spring of this stiffness. */
private const val FollowStiffness = 230f

/**
 * How far, in pixels, the page must scroll to put the highlight at its reading place, or null
 * when a line it needs is not laid out (the page is far from it and must jump there). With
 * the highlight between two lines the place is between theirs, so the page's target moves
 * continuously through a hand-over. Before the first line the place is the top of the page.
 */
private fun pageError(listState: LazyListState, cursor: Float): Float? {
    val info = listState.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return null
    val anchor = (info.viewportEndOffset - info.viewportStartOffset) * AnchorFraction
    val c = cursor.coerceAtLeast(-1f)
    val j = floor(c).toInt()
    val f = c - j
    val e0 = lineError(listState, info, j, anchor) ?: return null
    // The next line may not be laid out yet (a line taller than the page below the anchor):
    // aim at this one until it is.
    val e1 = if (f > 0.0001f) lineError(listState, info, j + 1, anchor) else null
    val e = if (e1 != null) e0 + (e1 - e0) * f else e0
    // Past the ends of the page there is nowhere to go; that is not an error to chase.
    return when {
        e < 0f && !listState.canScrollBackward -> 0f
        e > 0f && !listState.canScrollForward -> 0f
        else -> e
    }
}

private fun lineError(listState: LazyListState, info: LazyListLayoutInfo, index: Int, anchor: Float): Float? {
    if (index < 0) {
        return if (listState.firstVisibleItemIndex == 0) -listState.firstVisibleItemScrollOffset.toFloat() else null
    }
    val items = info.visibleItemsInfo
    val item = items.getOrNull(index - items[0].index)?.takeIf { it.index == index } ?: return null
    return item.offset - anchor
}

/**
 * Keeps the sung line at its reading place while [following], for as long as the page is on
 * screen. One spring, never restarted mid-song: when lines come quickly its target simply keeps
 * moving and the page flows from one to the next with its speed intact. A finger on the page
 * interrupts it (and [following] turns off elsewhere); a seek turns following back on, cutting
 * to the new place when it is more than a line away.
 */
internal suspend fun followSungLine(
    listState: LazyListState,
    motion: LyricsMotion,
    following: MutableState<Boolean>,
) = coroutineScope {
    snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
    var seeksSeen = motion.seeks
    // Nothing followed yet: the first landing — the pane opening, a new song — is a cut,
    // never a slide down through the lyrics from the top.
    var followed = Int.MIN_VALUE
    while (true) {
        snapshotFlow {
            motion.seeks != seeksSeen ||
                (following.value && pageError(listState, motion.pageCursor).let { it == null || abs(it) > 0.5f })
        }.first { it }

        val seek = motion.seeks != seeksSeen
        if (seek) {
            seeksSeen = motion.seeks
            // Moving the playhead is the user asking to see that moment now; it overrides a
            // finger that was browsing the lyrics a moment ago.
            following.value = true
        }
        val cut = followed == Int.MIN_VALUE ||
            (seek && LyricClockPolicy.shouldJump(followed, motion.line, afterSeek = true))
        if (cut || pageError(listState, motion.pageCursor) == null) {
            motion.velocity = 0f
            val info = listState.layoutInfo
            val anchor = ((info.viewportEndOffset - info.viewportStartOffset) * AnchorFraction).toInt()
            val line = motion.line
            if (line < 0) {
                if (cut) listState.scrollToItem(0) else listState.animateScrollToItem(0)
            } else if (cut) {
                listState.scrollToItem(line, -anchor)
            } else {
                listState.animateScrollToItem(line, -anchor)
            }
            followed = line
            // At most one landing per frame, whatever the page reports next.
            withFrameNanos { }
            continue
        }

        val glide = launch {
            listState.scroll { springFollow(listState, motion, following, seeksSeen) }
        }
        // Ends when the page settles, a seek arrives, or a finger takes the page (which
        // cancels the scroll without cancelling this loop).
        glide.join()
        motion.velocity = 0f
        followed = motion.line
    }
}

private suspend fun ScrollScope.springFollow(
    listState: LazyListState,
    motion: LyricsMotion,
    following: MutableState<Boolean>,
    seeksSeen: Int,
) {
    val omega = sqrt(FollowStiffness)
    var v = 0f
    var last = Long.MIN_VALUE
    var frame = 0L
    val tick: (Long) -> Unit = { frame = it }
    while (following.value && motion.seeks == seeksSeen) {
        withFrameNanos(tick)
        val dt =if (last == Long.MIN_VALUE) 1f / 120f else ((frame - last) / 1e9f).coerceIn(0f, 0.05f)
        last = frame
        val error = pageError(listState, motion.pageCursor) ?: break
        // The exact step of a critically damped spring towards a target held still for this
        // frame: stable at any frame rate, and the speed carries into the next frame.
        val y0 = -error
        val c = v + omega * y0
        val decay = exp(-omega * dt)
        val y1 = (y0 + c * dt) * decay
        v = (v - omega * c * dt) * decay
        val want = y1 - y0
        val got = scrollBy(want)
        if (abs(got - want) > 0.5f) v = 0f
        motion.velocity = v
        if (abs(error - got) < 0.5f && abs(v) < 12f) break
    }
}
