package com.shiny.music.ui.liquid.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.ui.res.stringResource
import com.shiny.music.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.player.ArtworkAnchor
import com.shiny.music.ui.player.playerArtworkAnchor
import com.shiny.music.ui.liquid.appearance.glowPauseFactor

/** How far the landscape artwork settles back while paused. */
private const val LandscapePausedScale = 0.86f

/**
 * Now Playing on its side: the artwork holds the left half at full height, the right half
 * carries either the title and transport or — in lyrics and queue — the page, with a slim
 * transport underneath so playback stays in reach.
 */
@Composable
internal fun NowPlayingLandscape(
    title: String,
    artist: String,
    artwork: Any?,
    thumbnail: String?,
    metadata: MediaMetadata?,
    living: Boolean,
    motionVideos: Boolean,
    glowColor: State<Color>,
    glowStrength: Float,
    liked: Boolean,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    controlsEnabled: Boolean,
    mode: NowPlayingMode,
    onMode: (NowPlayingMode) -> Unit,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    onSeek: (Long) -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
    onOutput: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    lyricsPane: @Composable (PaddingValues) -> Unit,
    queuePane: @Composable (PaddingValues) -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val dir = LocalLayoutDirection.current
    val restScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else LandscapePausedScale,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 210f),
        label = "landscapeArtRest",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(
                start = insets.calculateStartPadding(dir) + 24.dp,
                end = insets.calculateEndPadding(dir) + 24.dp,
                top = insets.calculateTopPadding() + 12.dp,
                bottom = insets.calculateBottomPadding() + 12.dp,
            ),
    ) {
        val artSize = minOf(maxHeight, maxWidth * 0.45f)
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            Box(
                Modifier
                    .size(artSize)
                    .then(
                        if (glowStrength > 0f) {
                            Modifier.artworkGlow(
                                color = glowColor,
                                strength = { glowStrength * glowPauseFactor(restScale, LandscapePausedScale) },
                                sizeFactor = { restScale },
                            )
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        scaleX = restScale
                        scaleY = restScale
                        shadowElevation = 26.dp.toPx()
                        shape = RoundedCornerShape(12.dp)
                        clip = true
                    }
                    .playerArtworkAnchor(ArtworkAnchor.Full, model = thumbnail, cornerRadius = 12.dp),
            ) {
                LivingArtwork(
                    model = artwork,
                    metadata = metadata,
                    animate = isPlaying && mode == NowPlayingMode.Stage,
                    living = living,
                    motionVideos = motionVideos,
                    // Only while the artwork is the thing on screen: in Lyrics and Queue the
                    // pane covers it, and a decoder running behind it is pure drain.
                    videoVisible = mode == NowPlayingMode.Stage,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = PlayerType.stageTitle, color = NowPlayingInk.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artist, style = LiquidTypography.body, color = NowPlayingInk.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StageGlassButton(
                        icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = stringResource(if (liked) R.string.liked else R.string.like),
                        iconSize = 20.dp,
                        onClick = onLike,
                    )
                    StageGlassButton(
                        icon = Icons.Rounded.MoreHoriz,
                        label = stringResource(R.string.liquid_more_options),
                        iconSize = 22.dp,
                        onClick = onMore,
                    )
                }

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(spring(stiffness = 400f)).togetherWith(fadeOut(spring(stiffness = 600f))) },
                    modifier = Modifier.weight(1f),
                    label = "landscapePane",
                ) { pane ->
                    when (pane) {
                        NowPlayingMode.Lyrics -> lyricsPane(PaddingValues(top = 12.dp, bottom = 24.dp))
                        NowPlayingMode.Queue -> queuePane(PaddingValues(top = 4.dp, bottom = 16.dp))
                        NowPlayingMode.Stage -> Box(Modifier.fillMaxSize())
                    }
                }

                val queue = mode == NowPlayingMode.Queue
                AnimatedVisibility(
                    visible = !queue,
                    enter = fadeIn(PlayerMotion.queue()) + expandVertically(PlayerMotion.queue()),
                    exit = fadeOut(PlayerMotion.small()) + shrinkVertically(PlayerMotion.queue()),
                ) {
                    LiquidScrubber(
                        positionProvider = positionProvider,
                        durationProvider = durationProvider,
                        onSeek = onSeek,
                        enabled = controlsEnabled,
                    )
                }
                LiquidTransport(
                    isPlaying = isPlaying,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    enabled = controlsEnabled,
                    playSize = if (queue) 56.dp else 72.dp,
                    skipSize = if (queue) 44.dp else 56.dp,
                )
                if (mode == NowPlayingMode.Stage) {
                    LiquidVolumeSlider()
                    Spacer(Modifier.height(6.dp))
                }
                NowPlayingUtilityRow(
                    lyricsActive = mode == NowPlayingMode.Lyrics,
                    queueActive = mode == NowPlayingMode.Queue,
                    onLyrics = { onMode(if (mode == NowPlayingMode.Lyrics) NowPlayingMode.Stage else NowPlayingMode.Lyrics) },
                    onOutput = onOutput,
                    onQueue = { onMode(if (mode == NowPlayingMode.Queue) NowPlayingMode.Stage else NowPlayingMode.Queue) },
                )
            }
        }
    }
}
