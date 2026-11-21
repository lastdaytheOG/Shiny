package com.shiny.music.ui.liquid.player.styles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.shiny.music.LocalPlayerConnection

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
