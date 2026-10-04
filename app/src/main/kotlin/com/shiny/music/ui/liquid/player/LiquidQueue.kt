package com.shiny.music.ui.liquid.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.constants.AutoLoadMoreKey
import com.shiny.music.extensions.metadata
import com.shiny.music.ui.component.backdrop.Backdrop
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.liquidGlass
import com.shiny.music.ui.liquid.rememberGlassPress
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LiquidQueuePane(
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windows by playerConnection.queueWindows.collectAsState()
    val currentIndex by playerConnection.currentWindowIndex.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val shuffle by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val (autoplay, setAutoplay) = rememberPreference(AutoLoadMoreKey, defaultValue = true)

    // With Autoplay on, Up Next always offers a real run of songs: keep 15 queued, topped
    // up from the song's radio once the original queue runs out.
    LaunchedEffect(currentIndex, windows.size, autoplay, enabled) {
        if (autoplay && enabled) playerConnection.service.ensureUpNext(15)
    }

    val upcoming = remember { mutableStateListOf<Timeline.Window>() }
    LaunchedEffect(windows, currentIndex) {
        upcoming.clear()
        if (currentIndex in windows.indices) upcoming.addAll(windows.drop(currentIndex + 1))
        else upcoming.addAll(windows)
    }
    val history = remember(windows, currentIndex) {
        if (currentIndex in windows.indices) windows.take(currentIndex) else emptyList()
    }

    val listState = rememberLazyListState()
    // Header rows before the reorderable run: history rows, the controls row, the heading.
    val headerCount = history.size + 2
    var dragRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.index - headerCount
        val t = to.index - headerCount
        if (f in upcoming.indices && t in upcoming.indices) {
            upcoming.add(t, upcoming.removeAt(f))
            dragRange = (dragRange?.first ?: f) to t
        }
    }
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging) {
            dragRange?.let { (f, t) ->
                val base = currentIndex + 1
                if (!playerConnection.player.shuffleModeEnabled && f != t) {
                    playerConnection.player.moveMediaItem(base + f, base + t)
                }
            }
            dragRange = null
        }
    }

    LaunchedEffect(Unit) {
        if (history.isNotEmpty()) listState.scrollToItem(history.size)
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        if (history.isNotEmpty()) {
            itemsIndexed(history, key = { _, w -> "h_${w.uid}" }) { index, window ->
                QueueRow(
                    window = window,
                    dimmed = true,
                    onClick = {
                        if (enabled) {
                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                            playerConnection.player.playWhenReady = true
                        }
                    },
                    trailing = {},
                )
            }
        }

        item(key = "queue_controls") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QueueToggle(
                    icon = Icons.Rounded.Shuffle,
                    label = stringResource(R.string.liquid_shuffle),
                    active = shuffle,
                    backdrop = backdrop,
                    onClick = { if (enabled) playerConnection.player.shuffleModeEnabled = !shuffle },
                    modifier = Modifier.weight(1f),
                )
                QueueToggle(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    label = stringResource(R.string.liquid_repeat),
                    active = repeatMode != Player.REPEAT_MODE_OFF,
                    backdrop = backdrop,
                    onClick = {
                        if (enabled) {
                            playerConnection.player.repeatMode = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                QueueToggle(
                    icon = Icons.Rounded.AllInclusive,
                    label = stringResource(R.string.liquid_autoplay),
                    active = autoplay,
                    backdrop = backdrop,
                    onClick = { setAutoplay(!autoplay) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "queue_heading") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (autoplay && upcoming.isEmpty()) R.string.liquid_continue_playing else R.string.liquid_playing_next),
                        style = LiquidTypography.headline,
                        color = NowPlayingInk.primary,
                    )
                    if (!queueTitle.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.liquid_from, queueTitle!!),
                            style = LiquidTypography.footnote,
                            color = NowPlayingInk.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        itemsIndexed(upcoming, key = { _, w -> "u_${w.uid}" }) { index, window ->
            ReorderableItem(reorderState, key = "u_${window.uid}") { dragging ->
                QueueRow(
                    window = window,
                    dimmed = false,
                    lifted = dragging,
                    onClick = {
                        if (enabled) {
                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                            playerConnection.player.playWhenReady = true
                        }
                    },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .draggableHandle(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.DragHandle, null, tint = NowPlayingInk.tertiary, modifier = Modifier.size(22.dp))
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    window: Timeline.Window,
    dimmed: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    lifted: Boolean = false,
) {
    val metadata = window.mediaItem.metadata ?: return
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        if (lifted) Color.White.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = spring(stiffness = 600f),
        label = "lift",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
            .graphicsLayer {
                alpha = if (dimmed) 0.5f else 1f
                // The fade applied to each draw, not through a layer: nothing in a row overlaps,
                // so the pixels are the same without a framebuffer per played row.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            model = metadata.thumbnailUrl,
            shape = RoundedCornerShape(6.dp),
            hairline = false,
            modifier = Modifier.size(46.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 4.dp),
        ) {
            Text(
                metadata.title,
                style = LiquidTypography.body.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = NowPlayingInk.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                metadata.artists.joinToString { it.name },
                style = LiquidTypography.subheadline,
                color = NowPlayingInk.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
        Spacer(Modifier.width(4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val fill by animateColorAsState(
        if (active) Color.White.copy(alpha = 0.9f) else Color.Transparent,
        animationSpec = spring(stiffness = 600f),
        label = "toggleFill",
    )
    val ink = if (active) Color(0xFF1C1C1E) else NowPlayingInk.primary
    Row(
        modifier = modifier
            .height(40.dp)
            .glassPressScale(press, 0.05f)
            .liquidGlass(CircleShape, GlassKind.Clear, backdrop = backdrop, pressProgress = { press.value })
            .background(fill, CircleShape)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = LiquidTypography.footnote.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1)
    }
}
