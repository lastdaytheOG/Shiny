package com.shiny.music.ui.liquid.player.styles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.customize.PlayerStylePrefs
import com.shiny.music.ui.liquid.player.LiquidScrubber
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val DegreesPerSecond = 200f

private const val OuterGroove = 0.9f
private const val InnerGroove = 0.47f

@Composable
internal fun TurntablePlayer(song: StyleSong) {
    val spinPref by rememberPreference(PlayerStylePrefs.TurntableSpin, true)
    val armSeeks by rememberPreference(PlayerStylePrefs.TurntableArmSeeks, true)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        StyleTopBar(onClose = song.actions.close, onMore = song.actions.more, modifier = Modifier.padding(top = 8.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val deck = minOf(maxWidth - 36.dp, maxHeight - 16.dp)
            Deck(song = song, spin = spinPref, armSeeks = armSeeks, deck = deck)
        }
        Column(Modifier.padding(horizontal = 28.dp)) {
            Text(
                song.title,
                color = StyleInk,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                color = StyleInkSoft,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(14.dp))
            LiquidScrubber(
                positionProvider = { song.clock.positionMs },
                durationProvider = { song.clock.durationMs },
                onSeek = song.actions.seek,
            )
        }
        StyleTransport(
            isPlaying = song.isPlaying,
            canPrevious = song.canPrevious,
            canNext = song.canNext,
            actions = song.actions,
            size = 68.dp,
            gap = 30.dp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 26.dp + LocalStyleBottomReserve.current),
        )
    }
}

@Composable
private fun Deck(song: StyleSong, spin: Boolean, armSeeks: Boolean, deck: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current

    val turn = remember { mutableFloatStateOf(0f) }
    val velocity = remember { Animatable(0f) }
    LaunchedEffect(song.isPlaying, spin) {
        velocity.animateTo(if (song.isPlaying && spin) DegreesPerSecond else 0f, spring(dampingRatio = 1f, stiffness = 18f))
    }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && velocity.value != 0f) {
                    turn.floatValue = (turn.floatValue + velocity.value * (now - last) / 1_000_000_000f) % 360f
                }
                last = now
            }
        }
    }

    val engaged = remember { Animatable(if (song.isPlaying) 1f else 0f) }
    LaunchedEffect(song.isPlaying) {
        engaged.animateTo(if (song.isPlaying) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 60f))
    }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val record = deck * 0.88f
    val labelPx = with(density) { (record * 0.38f).roundToPx() }
    val labelModel = remember(song.artwork, labelPx) { song.artwork?.resize(labelPx, labelPx) }

    Box(Modifier.size(deck)) {
        Box(
            Modifier
                .size(record)
                .align(Alignment.BottomStart)
                .graphicsLayer { rotationZ = turn.floatValue },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color(0xFF0D0D0F), r)

                var ring = r * 0.97f
                var i = 0
                while (ring > r * 0.42f) {
                    drawCircle(Color.White.copy(alpha = if (i % 5 == 0) 0.07f else 0.035f), ring, style = Stroke(width = 1f))
                    ring -= r * 0.018f
                    i++
                }

                drawCircle(
                    Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.12f to Color.White.copy(alpha = 0.10f),
                        0.25f to Color.Transparent,
                        0.62f to Color.Transparent,
                        0.74f to Color.White.copy(alpha = 0.07f),
                        0.86f to Color.Transparent,
                    ),
                    r,
                )
            }
            Artwork(
                model = labelModel,
                shape = CircleShape,
                hairline = false,
                scaleCorners = false,
                modifier = Modifier.size(record * 0.38f),
            )
            Box(Modifier.size(record * 0.03f).graphicsLayer { shape = CircleShape; clip = true }) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFDADADA)) }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .then(
                    if (armSeeks) {
                        Modifier.pointerInput(song.clock) {
                            val geometry = ArmGeometry(size.width.toFloat(), record.toPx())
                            var dragging = false
                            detectDragGestures(
                                onDragStart = { start ->
                                    val head = geometry.needleAt(geometry.angleFor(song.clock.fraction) * engaged.value)
                                    dragging = song.clock.durationMs > 0 && hypot(start.x - head.x, start.y - head.y) < 56.dp.toPx()
                                    if (dragging) dragFraction = song.clock.fraction
                                },
                                onDrag = { change, _ ->
                                    if (!dragging) return@detectDragGestures
                                    change.consume()
                                    dragFraction = geometry.fractionFor(change.position)
                                },
                                onDragEnd = {
                                    val f = dragFraction
                                    if (dragging && f != null) song.actions.seek((f * song.clock.durationMs).toLong())
                                    dragging = false
                                    dragFraction = null
                                },
                                onDragCancel = {
                                    dragging = false
                                    dragFraction = null
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            val geometry = ArmGeometry(size.width, record.toPx())
            val fraction = dragFraction ?: song.clock.fraction
            val angle = if (dragFraction != null) geometry.angleFor(fraction) else geometry.angleFor(fraction) * engaged.value
            val pivot = geometry.pivot

            drawCircle(Color(0xFF2A2A2E), geometry.baseRadius, pivot)
            drawCircle(Color(0xFF4A4A50), geometry.baseRadius * 0.55f, pivot)
            rotate(angle, pivot) {
                val end = Offset(pivot.x, pivot.y + geometry.length)
                drawLine(Color(0xFFC9C9CE), pivot, end, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)

                drawRoundRect(
                    color = Color(0xFFE6E6EA),
                    topLeft = Offset(end.x - 6.dp.toPx(), end.y - 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 20.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }
            drawCircle(Color(0xFF8E8E93), geometry.baseRadius * 0.22f, pivot)
        }
    }
}

private class ArmGeometry(deck: Float, record: Float) {
    val pivot = Offset(deck * 0.9f, deck * 0.08f)
    val baseRadius = deck * 0.055f
    private val center = Offset(record / 2f, deck - record / 2f)
    private val radius = record / 2f
    val length = hypot(center.x - pivot.x, center.y - pivot.y) * 0.98f

    fun needleAt(angle: Float): Offset {
        val a = Math.toRadians(angle.toDouble())
        return Offset(pivot.x - length * sin(a).toFloat(), pivot.y + length * cos(a).toFloat())
    }

    private fun angleAtRadius(share: Float): Float {
        var best = 0f
        var bestError = Float.MAX_VALUE
        var a = 0f
        while (a <= 70f) {
            val n = needleAt(a)
            val error = kotlin.math.abs(hypot(n.x - center.x, n.y - center.y) - radius * share)
            if (error < bestError) {
                bestError = error
                best = a
            }
            a += 0.25f
        }
        return best
    }

    private val start = angleAtRadius(OuterGroove)
    private val end = angleAtRadius(InnerGroove)

    fun angleFor(fraction: Float): Float = start + (end - start) * fraction.coerceIn(0f, 1f)

    fun fractionFor(point: Offset): Float {
        val angle = Math.toDegrees(atan2(-(point.x - pivot.x).toDouble(), (point.y - pivot.y).toDouble())).toFloat()
        return ((angle - start) / (end - start)).coerceIn(0f, 1f)
    }
}
