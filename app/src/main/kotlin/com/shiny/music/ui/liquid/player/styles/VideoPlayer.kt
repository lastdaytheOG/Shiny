package com.shiny.music.ui.liquid.player.styles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.customize.PlayerStylePrefs
import com.shiny.music.ui.liquid.player.LiquidScrubber
import com.shiny.music.ui.liquid.player.SongVideoState
import com.shiny.music.ui.liquid.player.SongVideoSurface
import com.shiny.music.ui.liquid.player.VideoSlot
import com.shiny.music.ui.liquid.player.VideoView
import com.shiny.music.ui.liquid.player.rememberSongVideo
import com.shiny.music.ui.liquid.player.rememberVideoTrack
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.delay

@Composable
internal fun VideoPlayer(song: StyleSong, expanded: Boolean) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val wordsPref by rememberPreference(PlayerStylePrefs.VideoWords, true)

    DisposableEffect(expanded) {
        val before = SongVideoState.view
        if (expanded && before != VideoView.Full) SongVideoState.view = VideoView.Full
        onDispose { if (expanded && before != VideoView.Full) SongVideoState.view = before }
    }
    val video = rememberSongVideo(song.metadata)
    val track = rememberVideoTrack(video)

    var chrome by remember { mutableStateOf(true) }
    LaunchedEffect(chrome, song.isPlaying) {
        if (chrome && song.isPlaying) {
            delay(3_000)
            chrome = false
        }
    }
    val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, tween(260), label = "videoStyleChrome")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { chrome = !chrome },
    ) {
        if (track != null) {
            SongVideoSurface(
                track = track,
                slot = VideoSlot.Full,
                leader = { playerConnection.player },
                fill = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val side = minOf(maxWidth - 48.dp, maxHeight * 0.5f)
            val px = with(LocalDensity.current) { side.roundToPx() }
            Artwork(
                model = song.artwork?.resize(px, px),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(side),
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(maxHeight * 0.4f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            StyleTopBar(
                onClose = song.actions.close,
                onMore = song.actions.more,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .graphicsLayer { alpha = chromeAlpha },
            )
            Spacer(Modifier.weight(1f))
            if (wordsPref) {
                StageWords(song, Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 24.dp), size = 28f)
            }
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 10.dp)
                    .graphicsLayer { alpha = 0.35f + 0.65f * chromeAlpha },
            ) {
                Text(song.title, color = StyleInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = StyleInkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Box(Modifier.fillMaxWidth().height(124.dp).graphicsLayer { alpha = chromeAlpha }) {
                if (chromeAlpha > 0f) Column {
                    LiquidScrubber(
                        positionProvider = { song.clock.positionMs },
                        durationProvider = { song.clock.durationMs },
                        onSeek = song.actions.seek,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        enabled = chrome,
                    )
                    StyleTransport(
                        isPlaying = song.isPlaying,
                        canPrevious = song.canPrevious,
                        canNext = song.canNext,
                        actions = song.actions,
                        size = 56.dp,
                        gap = 28.dp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 22.dp + LocalStyleBottomReserve.current),
                    )
                }
            }
        }
    }
}
