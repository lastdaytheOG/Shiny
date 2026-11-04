package com.shiny.music.ui.liquid.player.studio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.InterText
import com.shiny.music.ui.liquid.customize.PlayerStyle
import com.shiny.music.ui.liquid.player.NowPlayingBackground
import com.shiny.music.ui.liquid.player.NowPlayingUtilityRow
import com.shiny.music.ui.liquid.player.styles.BedsidePlayer
import com.shiny.music.ui.liquid.player.styles.BedsideTilesPlayer
import com.shiny.music.ui.liquid.player.styles.DialPlayer
import com.shiny.music.ui.liquid.player.styles.LocalStyleBottomReserve
import com.shiny.music.ui.liquid.player.styles.PosterPlayer
import com.shiny.music.ui.liquid.player.styles.StyleActions
import com.shiny.music.ui.liquid.player.styles.StyleClock
import com.shiny.music.ui.liquid.player.styles.StyleRowHeight
import com.shiny.music.ui.liquid.player.styles.StyleSong
import com.shiny.music.ui.liquid.player.styles.TimelinePlayer
import com.shiny.music.ui.liquid.player.styles.TurntablePlayer
import com.shiny.music.ui.liquid.player.styles.rememberStyleTones
import com.shiny.music.ui.liquid.player.styles.rememberSyncedLines
import com.shiny.music.ui.liquid.player.styles.ring.RingPosterPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

object PlayerStudioRequests {
    val requests = MutableStateFlow(0L)

    fun request() {
        requests.value = System.nanoTime()
    }
}

private const val PrepareFrames = 2
private const val HandoffFrames = 3
private const val CardScale = 0.72f
private const val CardCenterY = 0.465f
private val CardCorner: Dp = 34.dp
private val CardGap: Dp = 18.dp

internal val StudioSpring: AnimationSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 210f)

internal val StudioColumns: List<List<PlayerStyle>> = listOf(
    listOf(PlayerStyle.Shiny),
    listOf(PlayerStyle.Soft),
    listOf(PlayerStyle.Turntable),
    listOf(PlayerStyle.Poster, PlayerStyle.PosterRing),
    listOf(PlayerStyle.Dial),
    listOf(PlayerStyle.Timeline),
    listOf(PlayerStyle.Lyrics),
    listOf(PlayerStyle.Video),
    listOf(PlayerStyle.BedsideTiles, PlayerStyle.Bedside),
)

internal val StudioStyles: List<PlayerStyle> = StudioColumns.flatten()

private fun studioText(size: TextUnit, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = InterText, fontSize = size, fontWeight = weight, letterSpacing = (-0.05).em)

@Stable
class StudioMotion internal constructor(private val scope: CoroutineScope, initial: Float = 0f) {
    private val state = mutableFloatStateOf(initial)
    private var job: Job? = null

    var value: Float
        get() = state.floatValue
        set(v) {
            job?.cancel()
            job = null
            state.floatValue = v
        }

    val settling: Boolean get() = job?.isActive == true

    fun stop() {
        job?.cancel()
        job = null
    }

    fun animateTo(target: Float, velocity: Float = 0f, spec: AnimationSpec<Float> = StudioSpring, onEnd: (() -> Unit)? = null) {
        job?.cancel()
        val from = state.floatValue
        if (abs(from - target) < 0.0005f && abs(velocity) < 0.01f) {
            state.floatValue = target
            onEnd?.invoke()
            return
        }
        job = scope.launch {
            animate(from, target, velocity, spec) { v, _ -> state.floatValue = v }
            onEnd?.invoke()
        }
    }
}

@Stable
class PlayerStudioState internal constructor(private val scope: CoroutineScope) {
    private val columns = StudioColumns.map { column -> column.map { StudioStyles.indexOf(it) } }
    private val rowMotions = HashMap<Int, StudioMotion>()

    init {
        columns.indices.forEach { rows(it) }
    }

    val columnCount: Int get() = columns.size

    fun columnOf(style: Int): Int = columns.indexOfFirst { style in it }.coerceAtLeast(0)
    fun rowOf(style: Int): Int = columns.getOrNull(columnOf(style))?.indexOf(style)?.coerceAtLeast(0) ?: 0
    fun rowCount(column: Int): Int = columns.getOrNull(column)?.size ?: 1
    fun styleAt(column: Int, row: Int): Int =
        columns.getOrNull(column)?.let { it.getOrNull(row.coerceIn(0, it.lastIndex)) } ?: 0

    fun rows(column: Int): StudioMotion = rowMotions.getOrPut(column) { StudioMotion(scope) }

    val liveColumn: Int get() = columnOf(livePage)
    val liveRow: Int get() = rowOf(livePage)

    var open by mutableStateOf(false)
        private set

    val selector = StudioMotion(scope)
    val page = StudioMotion(scope)
    val handoff = StudioMotion(scope)
    var livePage by mutableIntStateOf(0)

    val engaged: Boolean get() = open || selector.value > 0.0005f

    fun open(live: PlayerStyle) {
        if (open) return
        livePage = StudioStyles.indexOf(live).coerceAtLeast(0)
        page.value = liveColumn.toFloat()
        rowMotions.forEach { (column, motion) -> motion.value = if (column == liveColumn) liveRow.toFloat() else 0f }
        handoff.value = 0f
        open = true
        scope.launch {
            repeat(PrepareFrames) { withFrameNanos { } }
            if (open) selector.animateTo(1f)
        }
    }

    fun settleOn(index: Int, restyled: Boolean, velocity: Float = 0f, ready: suspend () -> Unit = {}) {
        livePage = index
        if (!restyled) {
            close(velocity)
            return
        }
        handoff.value = 1f
        open = false
        page.animateTo(liveColumn.toFloat())
        rows(liveColumn).animateTo(liveRow.toFloat())
        scope.launch {
            ready()
            repeat(HandoffFrames) { withFrameNanos { } }
            if (open) return@launch
            close(velocity)
        }
    }

    fun close(velocity: Float = 0f) {
        open = false
        if (handoff.value > 0f) handoff.animateTo(0f, spec = tween(280, easing = LinearOutSlowInEasing))
        selector.animateTo(0f, velocity = velocity)
        page.animateTo(liveColumn.toFloat())
        rows(liveColumn).animateTo(liveRow.toFloat())
    }

    fun reset() {
        open = false
        selector.value = 0f
        handoff.value = 0f
        page.value = liveColumn.toFloat()
        rows(liveColumn).value = liveRow.toFloat()
    }
}

@Composable
fun rememberPlayerStudioState(): PlayerStudioState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerStudioState(scope) }
}

private fun rowLeaving(row: Int, rowPosition: Float): Float = (1f - (rowPosition - row)).coerceIn(0f, 1f)

private fun ease(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun variantFade(column: Int, row: Int, studio: PlayerStudioState, e: Float): Float =
    if (column == studio.liveColumn && row != studio.liveRow) ease((e - 0.35f) / 0.5f) else 1f

private class CardGeometry(val e: Float, val w: Float, val h: Float, gapPx: Float) {
    val scale = 1f + (CardScale - 1f) * e
    val stride = w * scale + gapPx
    val centerY = h / 2f + (CardCenterY * h - h / 2f) * e
    val cardW = w * scale
    val cardH = h * scale
    val strideY = h * scale + gapPx
    fun centerX(page: Int, position: Float) = w / 2f + (page - position) * stride
    fun centerY(row: Int, rowPosition: Float) = centerY + (row - rowPosition) * strideY
    fun visible(page: Int, position: Float) = abs(centerX(page, position) - w / 2f) < w / 2f + cardW / 2f
    fun visibleY(row: Int, rowPosition: Float) = abs(centerY(row, rowPosition) - h / 2f) < h / 2f + cardH / 2f
}

fun Modifier.studioSheetLayer(studio: PlayerStudioState): Modifier = graphicsLayer {
    val e = studio.selector.value
    if (e <= 0.0005f) return@graphicsLayer
    val w = size.width
    val h = size.height
    val g = CardGeometry(e, w, h, CardGap.toPx())
    val column = studio.liveColumn
    val rowPosition = studio.rows(column).value
    val leaving = rowLeaving(studio.liveRow, rowPosition)
    if (leaving < 1f) alpha = leaving
    scaleX = g.scale
    scaleY = g.scale
    translationX = g.centerX(column, studio.page.value) - w / 2f
    translationY = g.centerY(studio.liveRow, rowPosition) - h / 2f
    shape = RoundedCornerShape((CardCorner.toPx() * e.coerceIn(0f, 1f)).coerceAtLeast(0f) / g.scale.coerceAtLeast(0.01f))
    clip = true
}

@Composable
fun PlayerSelectorOverlay(
    studio: PlayerStudioState,
    onSet: (PlayerStyle, Float) -> Unit,
    onEdit: (PlayerStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown by remember(studio) { derivedStateOf { studio.selector.value > 0.0005f || studio.open } }
    if (!shown) return
    val active = studio.open
    BackHandler(enabled = active) { studio.close() }

    val styles = StudioStyles
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnSet by rememberUpdatedState(onSet)
    val columnCount = studio.columnCount
    val lastColumn = columnCount - 1
    val focusedColumn by remember(studio) {
        derivedStateOf { studio.page.value.roundToInt().coerceIn(0, lastColumn) }
    }
    val focused by remember(studio) {
        derivedStateOf {
            val column = focusedColumn
            val row = studio.rows(column).value.roundToInt().coerceIn(0, studio.rowCount(column) - 1)
            studio.styleAt(column, row).coerceIn(0, styles.lastIndex)
        }
    }
    val previewSong = rememberPreviewSong()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val gapPx = with(density) { CardGap.toPx() }
        fun geometry() = CardGeometry(studio.selector.value, w, h, gapPx)
        val restingBottomPx = CardGeometry(1f, w, h, gapPx).let { it.centerY + it.cardH / 2f }
        val cardsTopPx = with(density) { (statusTop + 50.dp).toPx() }
        val cardsBottomPx = restingBottomPx + with(density) { 18.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!active) Modifier else Modifier.pointerInput(columnCount, w, h, cardsTopPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val tracker = VelocityTracker()
                            tracker.addPointerInputChange(down)
                            val column = focusedColumn
                            val rows = studio.rows(column)
                            val caught = studio.page.settling || rows.settling
                            studio.page.stop()
                            rows.stop()
                            val startPage = studio.page.value
                            val startRow = rows.value
                            val lastRow = studio.rowCount(column) - 1
                            var dx = 0f
                            var dy = 0f
                            var mode = 0
                            var released = false
                            val resting = CardGeometry(1f, w, h, gapPx)
                            val sinkRange = h * 0.9f
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    released = true
                                    change.consume()
                                    break
                                }
                                val delta = change.positionChangeIgnoreConsumed()
                                dx += delta.x
                                dy += delta.y
                                tracker.addPointerInputChange(change)
                                change.consume()
                                if (mode == 0 && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                                    mode = if (abs(dx) >= abs(dy)) 1 else 2
                                }
                                if (mode == 1) {
                                    val raw = startPage - dx / resting.stride
                                    studio.page.value = when {
                                        raw < 0f -> raw * 0.3f
                                        raw > lastColumn -> lastColumn + (raw - lastColumn) * 0.3f
                                        else -> raw
                                    }
                                } else if (mode == 2) {
                                    val raw = startRow - dy / resting.strideY
                                    if (raw < 0f) {
                                        rows.value = 0f
                                        studio.selector.value = (1f + raw * resting.strideY / sinkRange).coerceIn(0f, 1f)
                                    } else {
                                        studio.selector.value = 1f
                                        rows.value = if (raw > lastRow) lastRow + (raw - lastRow) * 0.3f else raw
                                    }
                                }
                            }
                            val velocity = tracker.calculateVelocity()
                            fun settlePage() {
                                if (abs(studio.page.value - column) > 0.0005f) studio.page.animateTo(column.toFloat())
                            }
                            when (mode) {
                                0 -> if (released) {
                                    if (caught) {
                                        studio.page.animateTo(studio.page.value.roundToInt().coerceIn(0, lastColumn).toFloat())
                                        rows.animateTo(rows.value.roundToInt().coerceIn(0, lastRow).toFloat())
                                        return@awaitEachGesture
                                    }
                                    val tap = down.position
                                    if (tap.y < cardsTopPx || tap.y > cardsBottomPx) return@awaitEachGesture
                                    val position = studio.page.value
                                    val tappedColumn = (position + (tap.x - w / 2f) / resting.stride).roundToInt()
                                    if (tappedColumn !in 0..lastColumn) return@awaitEachGesture
                                    val rowPosition = studio.rows(tappedColumn).value
                                    val tappedRow = (rowPosition + (tap.y - resting.centerY) / resting.strideY).roundToInt()
                                    if (tappedRow !in 0 until studio.rowCount(tappedColumn)) return@awaitEachGesture
                                    val onCard = abs(tap.x - resting.centerX(tappedColumn, position)) < resting.cardW / 2f &&
                                        abs(tap.y - resting.centerY(tappedRow, rowPosition)) < resting.cardH / 2f
                                    if (!onCard || rowLeaving(tappedRow, rowPosition) < 0.5f) return@awaitEachGesture
                                    when {
                                        tappedColumn != focusedColumn -> studio.page.animateTo(tappedColumn.toFloat())
                                        tappedRow != rowPosition.roundToInt() -> studio.rows(tappedColumn).animateTo(tappedRow.toFloat())
                                        else -> currentOnSet(styles[studio.styleAt(tappedColumn, tappedRow)], 0f)
                                    }
                                }
                                1 -> {
                                    val flick = (-velocity.x / resting.stride).coerceIn(-3f, 3f)
                                    val target = (studio.page.value + flick * 0.3f).roundToInt().coerceIn(0, lastColumn)
                                    studio.page.animateTo(target.toFloat(), velocity = flick)
                                }
                                2 -> if (studio.selector.value < 0.999f) {
                                    val sinkVelocity = -velocity.y / sinkRange
                                    val pulled = (1f - studio.selector.value) * sinkRange
                                    if (pulled > 120.dp.toPx() || velocity.y > 1_400f) {
                                        currentOnSet(styles[studio.styleAt(column, 0)], sinkVelocity)
                                    } else {
                                        studio.selector.animateTo(1f, velocity = sinkVelocity)
                                        settlePage()
                                    }
                                } else {
                                    val rowVelocity = (-velocity.y / resting.strideY).coerceIn(-4f, 4f)
                                    val position = rows.value
                                    val target = when {
                                        rowVelocity > 0.35f -> maxOf(ceil(position), (position + rowVelocity * 0.25f).roundToInt().toFloat())
                                        rowVelocity < -0.35f -> minOf(floor(position), (position + rowVelocity * 0.25f).roundToInt().toFloat())
                                        else -> position.roundToInt().toFloat()
                                    }.coerceIn(0f, lastRow.toFloat())
                                    rows.animateTo(target, velocity = rowVelocity)
                                    settlePage()
                                }
                            }
                        }
                    },
                ),
        ) {
            styles.forEachIndexed { index, style ->
                key(style) {
                    val column = studio.columnOf(index)
                    val row = studio.rowOf(index)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val g = geometry()
                                val position = studio.page.value
                                val rowPosition = studio.rows(column).value
                                val live = index == studio.livePage
                                val leaving = rowLeaving(row, rowPosition) * variantFade(column, row, studio, g.e)
                                val visible = g.visible(column, position) && g.visibleY(row, rowPosition) && leaving > 0f &&
                                    (!live || studio.handoff.value > 0.001f)
                                alpha = if (!visible) 0f else if (live) studio.handoff.value.coerceIn(0f, 1f) * leaving else leaving * ease(g.e * 1.6f)
                                scaleX = g.scale
                                scaleY = g.scale
                                translationX = g.centerX(column, position) - w / 2f
                                translationY = g.centerY(row, rowPosition) - h / 2f
                                shape = RoundedCornerShape(CardCorner.toPx() * g.e.coerceIn(0f, 1f) / g.scale.coerceAtLeast(0.01f))
                                clip = true
                            }
                            .background(Color.Black),
                    ) {
                        StylePreviewCard(style, previewSong)
                        Box(
                            Modifier
                                .matchParentSize()
                                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                        )
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val g = geometry()
                if (g.e < 0.02f) return@Canvas
                val e = g.e.coerceIn(0f, 1f)
                val stroke = 1.dp.toPx()
                val radius = CardCorner.toPx() * e
                val position = studio.page.value
                styles.indices.forEach { index ->
                    val column = studio.columnOf(index)
                    val row = studio.rowOf(index)
                    val rowPosition = studio.rows(column).value
                    if (!g.visible(column, position) || !g.visibleY(row, rowPosition)) return@forEach
                    val cx = g.centerX(column, position)
                    val cy = g.centerY(row, rowPosition)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.16f * e * rowLeaving(row, rowPosition) * variantFade(column, row, studio, e)),
                        topLeft = Offset(cx - g.cardW / 2f + stroke / 2f, cy - g.cardH / 2f + stroke / 2f),
                        size = Size(g.cardW - stroke, g.cardH - stroke),
                        cornerRadius = CornerRadius(radius),
                        style = Stroke(stroke),
                    )
                }
                val column = focusedColumn
                val count = studio.rowCount(column)
                if (count > 1) {
                    val settled = (1f - abs(position - column) * 2f).coerceIn(0f, 1f)
                    val dot = 8.dp.toPx()
                    val step = 17.dp.toPx()
                    val x = (w - g.cardW) / 4f
                    val top = g.centerY - (count - 1) * step / 2f
                    val rowPosition = studio.rows(column).value.coerceIn(0f, (count - 1).toFloat())
                    repeat(count) { i ->
                        val near = (1f - abs(rowPosition - i)).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = (0.32f + 0.6f * near) * settled * e),
                            radius = dot / 2f,
                            center = Offset(x, top + i * step),
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val from = restingBottomPx + 2.dp.toPx()
                        val scrim = Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.9f),
                            startY = from,
                            endY = from + 64.dp.toPx(),
                        )
                        onDrawBehind {
                            val e = studio.selector.value.coerceIn(0f, 1f)
                            if (e > 0.01f) drawRect(scrim, topLeft = Offset(0f, from), alpha = e)
                        }
                    },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = statusTop + 10.dp, start = 22.dp, end = 22.dp)
                    .height(40.dp)
                    .graphicsLayer {
                        val e = studio.selector.value.coerceIn(0f, 1f)
                        translationY = -(1f - e) * (statusTop.toPx() + 70.dp.toPx())
                        alpha = e
                    },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(enabled = active) { studio.close() },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Styles", color = Color.White, style = studioText(16.sp, FontWeight.Bold))
                    AnimatedContent(
                        targetState = styles.getOrNull(focused)?.title.orEmpty(),
                        transitionSpec = {
                            (slideInVertically(tween(320)) { it } + fadeIn(tween(240))) togetherWith
                                (slideOutVertically(tween(280)) { -it } + fadeOut(tween(180)))
                        },
                        label = "selectorStyleName",
                    ) { name ->
                        Text(name, color = Color.White.copy(alpha = 0.6f), style = studioText(11.sp, FontWeight.Medium))
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            p.placeWithLayer(0, (restingBottomPx + 18.dp.toPx()).roundToInt()) {
                                val e = studio.selector.value.coerceIn(0f, 1f)
                                translationY = (1f - e) * 160.dp.toPx()
                                alpha = e
                            }
                        }
                    },
            ) {
                Canvas(modifier = Modifier.size(width = (columnCount * 11).dp, height = 5.dp)) {
                    val dot = 5.dp.toPx()
                    val step = 11.dp.toPx()
                    val position = studio.page.value.coerceIn(0f, lastColumn.toFloat())
                    val start = (size.width - (columnCount - 1) * step - dot) / 2f
                    repeat(columnCount) { i ->
                        val near = (1f - abs(position - i)).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.32f + 0.68f * near),
                            radius = dot / 2f,
                            center = Offset(start + i * step + dot / 2f, size.height / 2f),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                val style = styles.getOrNull(focused)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val editable = style != null && style != PlayerStyle.PosterRing && style != PlayerStyle.Dial
                    CardButton("Edit", enabled = active && editable, dimmed = !editable, onClick = { style?.let(onEdit) }, modifier = Modifier.weight(1f))
                    CardButton("Set", enabled = active, onClick = { style?.let { currentOnSet(it, 0f) } }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CardButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (dimmed) 0.06f else 0.15f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White.copy(alpha = if (dimmed) 0.35f else 1f), style = studioText(14.sp))
    }
}

@Composable
private fun rememberPreviewSong(): StyleSong {
    val connection = LocalPlayerConnection.current
    val metadata = connection?.mediaMetadata?.collectAsState()?.value
    val isPlaying = connection?.isPlaying?.collectAsState()?.value ?: false
    val tones = rememberStyleTones(metadata?.thumbnailUrl)
    val clock = remember(metadata?.id) {
        val player = connection?.player
        val duration = player?.duration?.takeIf { it > 0 } ?: ((metadata?.duration ?: 0) * 1000L)
        StyleClock(mutableLongStateOf(player?.currentPosition ?: 0L), mutableLongStateOf(duration))
    }
    val actions = remember { StyleActions(previous = {}, playPause = {}, next = {}, seek = {}, close = {}, more = {}) }
    return StyleSong(metadata, isPlaying, canPrevious = true, canNext = true, clock = clock, tones = tones, actions = actions)
}

@Composable
private fun StylePreviewCard(style: PlayerStyle, song: StyleSong) {
    val tones = song.tones
    when (style) {
        PlayerStyle.Shiny, PlayerStyle.Soft -> {
            val appearance = com.shiny.music.ui.liquid.appearance.LocalShinyAppearance.current
            val look = if (style == PlayerStyle.Soft) com.shiny.music.ui.liquid.appearance.PlayerLook.Soft else com.shiny.music.ui.liquid.appearance.PlayerLook.Shiny
            CompositionLocalProvider(com.shiny.music.ui.liquid.appearance.LocalShinyAppearance provides appearance.copy(playerLook = look)) {
                ShinyPreview(song, soft = style == PlayerStyle.Soft)
            }
        }
        else -> Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tones.light, tones.dark))),
        ) {
            val posterLike = style == PlayerStyle.Poster || style == PlayerStyle.PosterRing
            CompositionLocalProvider(LocalStyleBottomReserve provides if (posterLike) 0.dp else StyleRowHeight) {
                when (style) {
                    PlayerStyle.Turntable -> TurntablePlayer(song)
                    PlayerStyle.Poster -> PosterPlayer(song)
                    PlayerStyle.PosterRing -> {
                        val lines = rememberSyncedLines(song.metadata)
                        val sung = remember(lines) { lines.filter { !it.isBackground } }
                        RingPosterPlayer(metadata = song.metadata, lines = sung, showing = { true })
                    }
                    PlayerStyle.Dial -> DialPlayer(song)
                    PlayerStyle.Timeline -> TimelinePlayer(song, lyrics = false)
                    PlayerStyle.Lyrics -> TimelinePlayer(song, lyrics = true)
                    PlayerStyle.Video -> VideoPreview(song)
                    PlayerStyle.Bedside -> BedsidePlayer(song, expanded = false)
                    PlayerStyle.BedsideTiles -> BedsideTilesPlayer(song, expanded = false)
                    else -> Unit
                }
            }
            if (!posterLike) {
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                NowPlayingUtilityRow(
                    lyricsActive = false,
                    queueActive = false,
                    onLyrics = {},
                    onOutput = {},
                    onQueue = {},
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 44.dp, end = 44.dp, bottom = navBottom + 8.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoPreview(song: StyleSong) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val side = minOf(maxWidth - 48.dp, maxHeight * 0.5f)
        Artwork(
            model = song.artwork,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.align(Alignment.Center).size(side),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(maxHeight * 0.4f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = maxHeight * 0.2f),
        ) {
            Text(song.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(alpha = 0.64f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShinyPreview(song: StyleSong, soft: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        if (soft) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(song.tones.light, song.tones.dark))))
        } else {
            NowPlayingBackground(artworkUrl = song.artwork, playing = false, modifier = Modifier.fillMaxSize())
        }
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(Modifier.fillMaxSize().padding(top = statusTop, bottom = navBottom)) {
            if (soft) {
                Artwork(
                    model = song.artwork,
                    shape = RoundedCornerShape(0.dp),
                    hairline = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(w / 0.8f * 0.62f),
                )
                Spacer(Modifier.height(h * 0.05f))
            } else {
                Spacer(Modifier.height(h * 0.03f))
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 5.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape),
                )
                Spacer(Modifier.height(h * 0.1f))
                Artwork(
                    model = song.artwork,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(w * 0.71f),
                )
                Spacer(Modifier.weight(0.6f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Color.White.copy(alpha = 0.64f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundGlyph(Icons.Rounded.FavoriteBorder, 40.dp)
                Spacer(Modifier.size(10.dp))
                RoundGlyph(Icons.Rounded.MoreHoriz, 40.dp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.22f), CircleShape),
            ) {
                Box(Modifier.fillMaxWidth(song.clock.fraction.coerceAtLeast(0.02f)).height(6.dp).background(Color.White.copy(alpha = 0.85f), CircleShape))
            }
            Spacer(Modifier.height(h * 0.06f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundGlyph(Icons.Rounded.SkipPrevious, 64.dp, fill = !soft)
                RoundGlyph(if (song.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 82.dp, fill = !soft)
                RoundGlyph(Icons.Rounded.SkipNext, 64.dp, fill = !soft)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Rounded.FormatQuote, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
                Icon(Icons.Rounded.Speaker, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
                Icon(Icons.Rounded.QueueMusic, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun RoundGlyph(icon: ImageVector, size: Dp, fill: Boolean = true) {
    Box(
        Modifier
            .size(size)
            .background(if (fill) Color.White.copy(alpha = 0.12f) else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
