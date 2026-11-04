package com.shiny.music.ui.liquid.player.styles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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

@Composable
internal fun PosterPlayer(song: StyleSong) {
    val words by rememberPreference(PlayerStylePrefs.PosterWords, true)
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val diameter = width * 1.12f

        val px = with(density) { diameter.roundToPx() }
        val model = remember(song.artwork, px) { song.artwork?.resize(px, px) }
        Box(
            Modifier
                .offset(x = -maxWidth * 0.24f, y = -maxWidth * 0.3f)
                .requiredSize(diameter)
                .shadow(30.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black),
        ) {
            Artwork(
                model = model,
                shape = CircleShape,
                hairline = false,
                scaleCorners = false,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp)) {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(43.dp))
            }
            Spacer(Modifier.height(width * 0.58f))
            if (words) {
                StageWords(song, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 26.dp), size = 38f)
            } else {
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 26.dp)) {
                    Text(
                        song.title,
                        color = StyleInk,
                        fontSize = 46.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 26.dp).padding(top = 14.dp)) {
                Text(
                    if (words) "${song.title}  ·  ${song.artist}" else song.artist,
                    color = StyleInkSoft,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
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
                size = 58.dp,
                gap = 28.dp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, bottom = 24.dp + LocalStyleBottomReserve.current),
            )
        }
    }
}
