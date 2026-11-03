package com.shiny.music.ui.liquid.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.stats.StatsKind
import com.shiny.music.stats.StatsRange
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val ItemWidth = 160.dp
private val RulerHeight = 88.dp
private val RestAnchor = 56.dp
private val LabelBottom = 40.dp
private val TicksCentre = 66.dp
private val MajorTick = 34.dp
private val MinorTick = 22.dp
private val HighlightTick = 44.dp
private val EdgeFade = 40.dp
private val BigLabel = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-1.1).sp)
private const val SmallScale = 0.48f
private val GreyLabel = Color(0xFF474747)
private val GreyLabelLit = Color(0xFF6E6E6E)

internal fun rulerLabel(range: StatsRange): String =
    if (range.kind == StatsKind.Month) Month.of(range.month).getDisplayName(JavaTextStyle.FULL, Locale.getDefault()) else "${range.year}"

// every month of the year (or a run of years) on one ruler, a tick a day. the chosen one sits at
// the left with its name large; dragging lifts the marker to the middle and slides the ruler
// under it, letting go settles what's under it back at the left. periods with nothing in them
// are grey and landing on one goes back
@Composable
internal fun StatsRuler(selected: StatsRange, available: Set<StatsRange>, today: LocalDate, onSelect: (StatsRange) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val items = remember(selected.kind, if (selected.kind == StatsKind.Month) selected.year else 0, today, available) {
        if (selected.kind == StatsKind.Month) {
            (1..12).map { StatsRange(StatsKind.Month, selected.year, it) }
        } else {
            val first = minOf(available.minOfOrNull { it.year } ?: today.year, today.year) - 2
            (first..today.year + 1).map { StatsRange(StatsKind.Year, it) }
        }
    }
    val labels = remember(items, measurer) { items.map { measurer.measure(rulerLabel(it), BigLabel) } }
    val committed = items.indexOf(selected).coerceAtLeast(0)
    val currentCommitted by rememberUpdatedState(committed)
    val currentAvailable by rememberUpdatedState(available)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val pos = remember(items) { Animatable(committed.toFloat()) }
    val lift = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var lastTick by remember { mutableIntStateOf(committed) }
    LaunchedEffect(committed, items) {
        if (!dragging && pos.targetValue != committed.toFloat()) pos.animateTo(committed.toFloat(), spring(dampingRatio = 0.85f, stiffness = 240f))
    }
    val itemPx = with(density) { ItemWidth.toPx() }
    fun settle(velocity: Float) {
        dragging = false
        val projected = pos.value - velocity / itemPx * 0.18f
        var target = projected.roundToInt().coerceIn(0, items.lastIndex)
        if (items[target] !in currentAvailable) target = currentCommitted
        scope.launch { lift.animateTo(0f, tween(460, easing = StatsEmphasized)) }
        scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = 0.82f, stiffness = 260f)) }
        if (target != currentCommitted) currentOnSelect(items[target])
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(RulerHeight)
            .semantics { contentDescription = rulerLabel(selected) }
            .pointerInput(items) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        dragging = true
                        scope.launch { pos.stop() }
                        scope.launch { lift.animateTo(1f, tween(340, easing = StatsEmphasized)) }
                    },
                    onDragEnd = { settle(tracker.calculateVelocity().x) },
                    onDragCancel = { settle(0f) },
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch {
                        pos.snapTo((pos.value - dx / itemPx).coerceIn(-0.45f, items.lastIndex + 0.45f))
                        val under = pos.value.roundToInt().coerceIn(0, items.lastIndex)
                        if (under != lastTick) {
                            lastTick = under
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    }
                }
            }
            .pointerInput(items) {
                detectTapGestures { at ->
                    val i = (pos.value + (at.x - RestAnchor.toPx()) / itemPx).roundToInt()
                    if (i !in items.indices || items[i] !in currentAvailable) return@detectTapGestures
                    lastTick = i
                    scope.launch { pos.animateTo(i.toFloat(), spring(dampingRatio = 0.82f, stiffness = 260f)) }
                    if (i != currentCommitted) currentOnSelect(items[i])
                }
            },
    ) {
        val p = pos.value
        val anchor = RestAnchor.toPx() + (size.width / 2f - RestAnchor.toPx()) * lift.value
        val fade = EdgeFade.toPx()
        val cy = TicksCentre.toPx()
        val labelBottom = LabelBottom.toPx()
        fun smooth(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        fun edge(x: Float) = smooth(x / fade) * smooth((size.width - x) / fade)
        items.forEachIndexed { i, period ->
            val x0 = anchor + (i - p) * itemPx
            if (x0 > size.width + itemPx || x0 + itemPx < -itemPx) return@forEachIndexed
            val open = period in available
            val ticks = if (period.kind == StatsKind.Month) YearMonth.of(period.year, period.month).lengthOfMonth() else 12
            val step = itemPx / ticks
            for (k in 0 until ticks) {
                val x = x0 + k * step
                if (x < -2f || x > size.width + 2f) continue
                val major = k == 0
                val h = (if (major) MajorTick else MinorTick).toPx()
                val a = (if (open) (if (major) 0.9f else 0.58f) else (if (major) 0.3f else 0.18f)) * edge(x)
                if (a <= 0.01f) continue
                drawLine(Color.White.copy(alpha = a), Offset(x, cy - h / 2f), Offset(x, cy + h / 2f), strokeWidth = (if (major) 2.dp else 1.3.dp).toPx(), cap = StrokeCap.Round)
            }
            // its name over its first tick, full size under the marker
            val near = (1f - abs(i - p)).coerceIn(0f, 1f)
            val layout = labels[i]
            val k = SmallScale + (1f - SmallScale) * near
            val w = layout.size.width * k
            val h = layout.size.height * k
            val left = max(x0 - w / 2f, 6.dp.toPx())
            val color = if (open) lerp(StatsInkMuted, StatsInk, near) else lerp(GreyLabel, GreyLabelLit, near)
            val alpha = max(edge(x0), near)
            if (alpha > 0.01f) {
                translate(left, labelBottom - h) {
                    scale(k, k, pivot = Offset.Zero) { drawText(layout, color = color, alpha = alpha) }
                }
            }
        }
        val under = p.roundToInt().coerceIn(0, items.lastIndex)
        val lit = items[under] in available
        val hh = HighlightTick.toPx()
        drawLine(if (lit) Color.White else GreyLabelLit, Offset(anchor, cy - hh / 2f), Offset(anchor, cy + hh / 2f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}
