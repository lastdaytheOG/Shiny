package com.shiny.music.ui.liquid.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.db.entities.LyricsEntity
import com.shiny.music.lyrics.LyricsUtils
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.liquid.LiquidIcons
import com.shiny.music.ui.liquid.LiquidTypography

/**
 * The soft player's foot: output on the left, queue on the right, and between and below
 * them the song's own lyrics, the sung line bright and the next one waiting under it. Any
 * line opens the full lyrics. In the lyrics and the queue only the row stays.
 */
@Composable
internal fun SoftLyricsDock(
    metadata: MediaMetadata?,
    positionProvider: () -> Long,
    mode: NowPlayingMode,
    onLyrics: () -> Unit,
    onOutput: () -> Unit,
    onQueue: () -> Unit,
    onRowPlaced: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned(onRowPlaced),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UtilityButton(Icons.Rounded.Bluetooth, stringResource(R.string.liquid_audio_output), false, onOutput)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                LyricsCaption(active = mode == NowPlayingMode.Lyrics, onClick = onLyrics)
            }
            UtilityButton(LiquidIcons.Queue, stringResource(R.string.queue), mode == NowPlayingMode.Queue, onQueue)
        }
        AnimatedVisibility(
            visible = mode == NowPlayingMode.Stage,
            enter = fadeIn(PlayerMotion.queue()) + expandVertically(PlayerMotion.queue()),
            exit = fadeOut(PlayerMotion.small()) + shrinkVertically(PlayerMotion.queue()),
        ) {
            LyricsPreview(metadata = metadata, positionProvider = positionProvider, onOpen = onLyrics)
        }
    }
}

@Composable
private fun LyricsCaption(active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = if (pressed) 1400f else 400f), label = "captionPress")
    val on by animateFloatAsState(if (active) 1f else 0f, tween(260), label = "captionActive")
    Text(
        text = stringResource(R.string.lyrics),
        style = CaptionStyle,
        color = Color.White,
        modifier = Modifier
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                selected = active
                contentDescription = "Lyrics"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer {
                alpha = 0.5f + 0.46f * on
                val s = 1f - 0.06f * press
                scaleX = s
                scaleY = s
            },
    )
}

/** Which line leads the preview, and whether it is being sung or only waiting. */
private data class PreviewAnchor(val index: Int, val sung: Boolean)

@Composable
private fun LyricsPreview(
    metadata: MediaMetadata?,
    positionProvider: () -> Long,
    onOpen: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val lyricsEntity by playerConnection.currentLyrics.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState()
    RequestLyricsIfMissing(metadata, lyricsEntity)
    val raw = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }
    val songOffsetMs = (currentSong?.song?.lyricsOffset ?: 0).toLong()
    val interaction = remember { MutableInteractionSource() }

    // fixed height whatever the lyrics are doing, so nothing above it ever moves
    Box(
        Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .semantics { contentDescription = "Open lyrics" },
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            // still being looked up: nothing yet rather than a spinner under the transport
            raw == null -> Unit

            raw == LyricsEntity.LYRICS_NOT_FOUND || raw.isBlank() -> Text(
                text = stringResource(R.string.liquid_no_lyrics),
                style = NextStyle.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = Color.White.copy(alpha = 0.40f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )

            else -> {
                val synced = remember(raw) { raw.startsWith("[") && Regex("\\[\\d+:\\d+").containsMatchIn(raw) }
                if (!synced) {
                    val plain = remember(raw) { raw.lines().filter { it.isNotBlank() }.take(2) }
                    PreviewLines(first = plain.getOrNull(0).orEmpty(), second = plain.getOrNull(1), firstSung = false)
                } else {
                    val timeline = remember(raw) {
                        buildTimeline(LyricsUtils.parseLyrics(raw).filter { it.text.isNotBlank() }.sortedBy { it.time })
                    }
                    if (timeline.size == 0) return@Box
                    // derived from the shared clock: recomposes only when the leading line changes
                    val anchorState = remember(timeline, songOffsetMs) {
                        derivedStateOf { previewAnchor(timeline, positionProvider() + songOffsetMs) }
                    }
                    val anchor = anchorState.value
                    AnimatedContent(
                        targetState = anchor.index,
                        transitionSpec = {
                            val forward = targetState > initialState
                            (
                                slideInVertically(LineMove) { if (forward) it / 2 else -it / 2 } + fadeIn(tween(320, delayMillis = 60))
                                ) togetherWith (
                                slideOutVertically(LineMove) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(200))
                                ) using SizeTransform(clip = false)
                        },
                        label = "previewLine",
                    ) { index ->
                        PreviewLines(
                            first = timeline.lines[index].text,
                            second = timeline.lines.getOrNull(index + 1)?.text,
                            firstSung = anchor.sung && anchor.index == index,
                        )
                    }
                }
            }
        }
    }
}

private fun previewAnchor(timeline: LyricTimeline, t: Long): PreviewAnchor {
    val k = timeline.lineAt(t.toDouble())
    if (k < 0) return PreviewAnchor(0, false)
    val line = timeline.lines[k]
    // in a long instrumental gap the line has been sung: show what comes next, waiting
    if (line.interlude && t > line.end + InterludeHandOverMs && k + 1 < timeline.size) return PreviewAnchor(k + 1, false)
    return PreviewAnchor(k, true)
}

@Composable
private fun PreviewLines(first: String, second: String?, firstSung: Boolean) {
    val lit by animateFloatAsState(if (firstSung) 1f else 0f, tween(360), label = "previewLit")
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = first,
            style = CurrentStyle,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                alpha = 0.40f + 0.56f * lit
                val s = 0.97f + 0.03f * lit
                scaleX = s
                scaleY = s
            },
        )
        if (second != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = second,
                style = NextStyle,
                color = Color.White.copy(alpha = 0.36f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// the full lyrics' face at preview size
private val CurrentStyle = LiquidTypography.lyrics.copy(fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp)
private val NextStyle = LiquidTypography.lyrics.copy(fontSize = 18.sp, lineHeight = 23.sp, letterSpacing = (-0.3).sp)
private val CaptionStyle = LiquidTypography.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 0.2.sp)

/** Two lines of the current one and one of the next, with the gap between. */
private val PreviewHeight = 27.dp * 2 + 6.dp + 23.dp + 4.dp

private val LineMove = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 1f, stiffness = 260f)

/** How long after a line's last word an instrumental gap hands the preview to the next line. */
private const val InterludeHandOverMs = 1_200L
