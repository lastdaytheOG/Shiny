package com.shiny.music.ui.liquid.player.styles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.utils.resize
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val DialGround = Color(0xFF161618)

@Composable
internal fun DialPlayer(song: StyleSong) {
    val accent = song.tones.vivid
    val ground = lerp(DialGround, song.tones.dark, 0.18f)

    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = LocalStyleBottomReserve.current),
        ) {
            val width = maxWidth
            val head = 58.dp
            val transport = 92.dp
            val minCard = 132.dp
            val dial = minOf(width - 28.dp, maxHeight - head - transport - minCard - 34.dp).coerceAtLeast(160.dp)

            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                DialHead(song, Modifier.height(head))
                DialLyricsCard(
                    song = song,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                )
                DialTransport(song, accent, Modifier.height(transport))
                Dial(song = song, diameter = dial, modifier = Modifier.padding(bottom = 18.dp))
            }
        }
    }
}

@Composable
private fun DialHead(song: StyleSong, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            song.artist.uppercase(),
            color = StyleInkFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.title,
            color = StyleInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun DialLyricsCard(song: StyleSong, modifier: Modifier) {
    val lines = rememberSyncedLines(song.metadata)
    Box(
        modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (lines.isEmpty()) {
            Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    song.title,
                    color = StyleInk,
                    fontSize = 24.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    color = StyleInkSoft,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            DialLines(song, lines)
        }
    }
}

@Composable
private fun DialLines(song: StyleSong, lines: List<com.shiny.music.lyrics.LyricsEntry>) {
    val index by rememberLineIndex(lines, song.clock)
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val half = maxHeight / 2
        LaunchedEffect(lines, index) {
            val target = index.coerceAtLeast(0)
            if (target > lines.lastIndex) return@LaunchedEffect
            if (state.layoutInfo.visibleItemsInfo.none { it.index == target }) state.scrollToItem(target)
            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
            val centre = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2f
            val delta = info.offset + info.size / 2f - centre
            if (kotlin.math.abs(delta) > 0.5f) state.animateScrollBy(delta, tween(520, easing = FastOutSlowInEasing))
        }
        LazyColumn(
            state = state,
            userScrollEnabled = false,
            contentPadding = PaddingValues(vertical = half),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.22f to Color.Black,
                            0.78f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(lines, key = { i, line -> "$i:${line.time}" }) { i, line ->
                val current = i == index
                val ink by animateColorAsState(
                    if (current) StyleInk else Color.White.copy(alpha = 0.38f),
                    tween(320),
                    label = "dialLine",
                )
                Text(
                    line.text,
                    color = ink,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            song.actions.seek(line.time)
                        }
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DialTransport(song: StyleSong, accent: Color, modifier: Modifier) {
    val glyph = if (accent.luminance() > 0.6f) Color.Black else Color.White
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            StyleGlyphButton(
                Icons.Rounded.SkipPrevious,
                "Previous",
                song.actions.previous,
                size = 50.dp,
                iconSize = 24.dp,
                fill = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            StyleGlyphButton(
                if (song.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (song.isPlaying) "Pause" else "Play",
                song.actions.playPause,
                size = 74.dp,
                iconSize = 34.dp,
                fill = accent,
                tint = glyph,
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            StyleGlyphButton(
                Icons.Rounded.SkipNext,
                "Next",
                song.actions.next,
                size = 50.dp,
                iconSize = 24.dp,
                fill = Color.White.copy(alpha = 0.06f),
                enabled = song.canNext,
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
            )
        }
    }
}

@Composable
private fun Dial(song: StyleSong, diameter: Dp, modifier: Modifier) {
    val connection = LocalPlayerConnection.current
    val repeat = connection?.repeatMode?.collectAsState()?.value ?: Player.REPEAT_MODE_OFF
    val liked = connection?.currentSong?.collectAsState()?.value?.song?.liked == true
    val density = LocalDensity.current
    val px = with(density) { diameter.roundToPx() }
    val model = remember(song.artwork, px) { song.artwork?.resize(px, px) }
    val side = 48.dp

    Box(modifier.size(width = diameter, height = diameter)) {
        Box(Modifier.fillMaxSize().clip(CircleShape)) {
            Artwork(
                model = model,
                shape = CircleShape,
                hairline = false,
                scaleCorners = false,
                modifier = Modifier.fillMaxSize(),
            )
            DialRing(song, Modifier.fillMaxSize())
        }
        StyleGlyphButton(
            if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            when (repeat) {
                Player.REPEAT_MODE_ONE -> "Repeat one"
                Player.REPEAT_MODE_ALL -> "Repeat all"
                else -> "Repeat"
            },
            onClick = {
                val player = connection?.player ?: return@StyleGlyphButton
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            },
            size = side,
            iconSize = 20.dp,
            fill = if (repeat != Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.45f),
            tint = if (repeat != Player.REPEAT_MODE_OFF) Color.Black else StyleInk,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 6.dp),
        )
        StyleGlyphButton(
            if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (liked) "Unlike" else "Like",
            onClick = { connection?.toggleLike() },
            size = side,
            iconSize = 20.dp,
            fill = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp, top = 6.dp),
        )
    }
}

@Composable
private fun DialRing(song: StyleSong, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    var scrubMs by remember { androidx.compose.runtime.mutableStateOf(-1L) }
    val currentSong by rememberUpdatedState(song)
    val lastTick = remember { mutableLongStateOf(-1L) }

    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = min(size.width, size.height) / 2f
                val ringR = r - RingInset.toPx()
                val d = hypot(down.position.x - c.x, down.position.y - c.y)
                if (kotlin.math.abs(d - ringR) > 30.dp.toPx()) return@awaitEachGesture
                val duration = currentSong.clock.durationMs
                if (duration <= 0L) return@awaitEachGesture
                down.consume()
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scrubMs = (fractionAt(down.position, c) * duration).toLong()
                lastTick.longValue = scrubMs / 10_000L
                var lastFraction = fractionAt(down.position, c)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.positionChange() != Offset.Zero) change.consume()
                    var f = fractionAt(change.position, c)
                    if (lastFraction > 0.75f && f < 0.25f) f = 1f
                    if (lastFraction < 0.25f && f > 0.75f) f = 0f
                    lastFraction = f
                    val ms = (f * duration).toLong()
                    scrubMs = ms
                    val tick = ms / 10_000L
                    if (tick != lastTick.longValue) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastTick.longValue = tick
                    }
                }
                val target = scrubMs
                if (target >= 0L) currentSong.actions.seek(target.coerceIn(0L, duration))
                scrubMs = -1L
            }
        }
    ) {
        val r = size.minDimension / 2f
        val ringR = r - RingInset.toPx()
        val c = center
        val held = scrubMs >= 0L
        val duration = song.clock.durationMs
        val fraction = when {
            held && duration > 0 -> scrubMs.toFloat() / duration
            else -> song.clock.fraction
        }.coerceIn(0f, 1f)
        val stroke = (if (held) 3.dp else 2.dp).toPx()
        val topLeft = Offset(c.x - ringR, c.y - ringR)
        val arc = Size(ringR * 2f, ringR * 2f)
        drawArc(
            Color.White.copy(alpha = 0.34f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = Stroke(stroke),
        )
        drawArc(
            Color.White,
            startAngle = -90f,
            sweepAngle = 360f * fraction,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val a = (-90f + 360f * fraction) * (PI.toFloat() / 180f)
        val knob = Offset(c.x + ringR * cos(a), c.y + ringR * sin(a))
        drawCircle(Color.Black.copy(alpha = 0.28f), (if (held) 7.dp else 5.5.dp).toPx(), knob)
        drawCircle(Color.White, (if (held) 5.dp else 4.dp).toPx(), knob)
    }
}

private val RingInset = 1.dp

private fun fractionAt(p: Offset, c: Offset): Float {
    val deg = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
    return (((deg + 90f) % 360f + 360f) % 360f) / 360f
}
