package com.shiny.music.ui.liquid.player.styles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.customize.PlayerStylePrefs
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference
import androidx.compose.runtime.collectAsState

/**
 * Timeline, and with [lyrics] the Lyrics stage. The screen is the song as time: above the
 * playhead what has been played, below it what is left, the line moving down as it plays,
 * with the elapsed and total times set huge either side of it. Tapping the split seeks.
 * The Lyrics stage keeps the top for the line being sung, set big.
 */
@Composable
internal fun TimelinePlayer(song: StyleSong, lyrics: Boolean) {
    val coverPref by rememberPreference(PlayerStylePrefs.TimelineCover, true)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val timeSize = (width.value * 0.19f).coerceIn(56f, 104f).sp
        val timeStyle = TextStyle(fontSize = timeSize, lineHeight = timeSize, fontWeight = FontWeight.Bold, letterSpacing = (-0.06 * timeSize.value).sp)
        val timeHeight = remember(timeStyle) { measurer.measure("0:00", timeStyle).size.height.toFloat() }
        val topReserve = if (lyrics) maxHeight * 0.36f else 132.dp
        val bottomReserve = 150.dp

        // the playhead's travel: from under the header to above the controls, with room for a time either side
        val lo = with(density) { topReserve.toPx() } + timeHeight
        val hi = with(density) { (maxHeight - bottomReserve).toPx() } - timeHeight
        val lineAt = { lo + (hi - lo).coerceAtLeast(0f) * song.clock.fraction }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val y = lineAt()
                    drawRect(song.tones.light.copy(alpha = 0.55f), size = androidx.compose.ui.geometry.Size(size.width, y))
                    drawRect(Color.Black.copy(alpha = 0.42f), topLeft = androidx.compose.ui.geometry.Offset(0f, y))
                    drawLine(Color.White.copy(alpha = 0.9f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 2.dp.toPx())
                }
                .pointerInput(song.clock, lo, hi) {
                    detectTapGestures { tap ->
                        if (song.clock.durationMs <= 0 || hi <= lo) return@detectTapGestures
                        val f = ((tap.y - lo) / (hi - lo)).coerceIn(0f, 1f)
                        song.actions.seek((f * song.clock.durationMs).toLong())
                    }
                },
        )

        // the elapsed time rides just above the line, the total just below it
        val played by rememberPlayedText(song.clock)
        val gap = with(density) { 4.dp.toPx() }
        val start = with(density) { 24.dp.roundToPx() }
        Text(
            played,
            color = StyleInk,
            style = timeStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    p.placeWithLayer(start, 0) { translationY = lineAt() - p.height - gap }
                }
            },
        )
        Text(
            totalText(song.clock),
            color = StyleInk.copy(alpha = 0.72f),
            style = timeStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    p.placeWithLayer(start, 0) { translationY = lineAt() + gap }
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            StyleTopBar(onClose = song.actions.close, onMore = song.actions.more, modifier = Modifier.padding(top = 8.dp))
            if (lyrics) {
                StageWords(song, Modifier.fillMaxWidth().height(topReserve - 60.dp).padding(horizontal = 24.dp))
            } else {
                SongHeader(song, cover = coverPref, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            }
            Spacer(Modifier.weight(1f))
            if (lyrics) SongHeader(song, cover = coverPref, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            StyleTransport(
                isPlaying = song.isPlaying,
                canPrevious = song.canPrevious,
                canNext = song.canNext,
                actions = song.actions,
                size = 60.dp,
                gap = 28.dp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 28.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun SongHeader(song: StyleSong, cover: Boolean, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { 52.dp.roundToPx() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (cover) {
            Artwork(
                model = song.artwork?.resize(px, px),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(song.title, color = StyleInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = StyleInkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The line being sung, set big; the title when the song has no synced lines. */
@Composable
internal fun StageWords(song: StyleSong, modifier: Modifier = Modifier, size: Float = 40f) {
    val playerConnection = LocalPlayerConnection.current
    val current by (playerConnection?.currentSong?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(null) })
    val offset = (current?.song?.lyricsOffset ?: 0).toLong()
    val lines = rememberSyncedLines(song.metadata)
    val index by rememberLineIndex(lines, song.clock, offset)
    Box(modifier) {
        if (lines.isEmpty()) {
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    song.title,
                    color = StyleInk.copy(alpha = 0.8f),
                    fontSize = size.sp,
                    lineHeight = (size * 1.06f).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.04 * size).sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (slideInVertically(tween(420)) { it / 3 } + fadeIn(tween(320))) togetherWith
                        (slideOutVertically(tween(380)) { -it / 3 } + fadeOut(tween(220)))
                },
                label = "stageLine",
                modifier = Modifier.align(Alignment.BottomStart),
            ) { i ->
                Text(
                    text = lines.getOrNull(i)?.text ?: "♪",
                    color = StyleInk,
                    fontSize = size.sp,
                    lineHeight = (size * 1.06f).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.04 * size).sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
