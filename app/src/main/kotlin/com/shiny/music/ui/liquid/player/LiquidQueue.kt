package com.shiny.music.ui.liquid.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.constants.AutoLoadMoreKey
import com.shiny.music.extensions.metadata
import com.shiny.music.playback.QUEUE_RENDER_PAGE
import com.shiny.music.playback.nextRenderCount
import com.shiny.music.playback.nextRepeatMode
import com.shiny.music.playback.queueSummary
import com.shiny.music.ui.component.backdrop.Backdrop
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidPullDownMenu
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidColors
import com.shiny.music.ui.liquid.MenuAction
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.liquidDarkColors
import com.shiny.music.ui.liquid.liquidGlass
import com.shiny.music.ui.liquid.rememberGlassPress
import com.shiny.music.utils.makeTimeString
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val pageStatus by playerConnection.queuePageStatus.collectAsState()
    val queueSessionId by playerConnection.queueSessionId.collectAsState()
    val undo by playerConnection.queueUndo.collectAsState()
    val shuffle by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val (autoplay, setAutoplay) = rememberPreference(AutoLoadMoreKey, defaultValue = true)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(currentIndex, windows.size, autoplay, enabled) {
        if (autoplay && enabled) playerConnection.service.ensureUpNext(15)
    }

    val past = remember {
        mutableStateListOf<Timeline.Window>().apply {
            if (currentIndex in windows.indices) addAll(windows.subList(0, currentIndex))
        }
    }
    var now by remember { mutableStateOf(windows.getOrNull(currentIndex)) }
    val upcoming = remember {
        mutableStateListOf<Timeline.Window>().apply {
            if (currentIndex in windows.indices) addAll(windows.subList(currentIndex + 1, windows.size))
        }
    }
    val listState = rememberLazyListState()
    var draggedUid by remember { mutableStateOf<Any?>(null) }
    var dragSlot by remember { mutableIntStateOf(-1) }

    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = upcoming.indexOfFirst { queueKey(it) == from.key }
        val t = upcoming.indexOfFirst { queueKey(it) == to.key }
        if (f >= 0 && t >= 0) {
            upcoming.add(t, upcoming.removeAt(f))
            draggedUid = upcoming[t].uid
            dragSlot = t
        }
    }
    val dragging = reorderState.isAnyItemDragging

    LaunchedEffect(windows, currentIndex, dragging) {
        if (dragging) return@LaunchedEffect
        draggedUid?.let { uid ->
            val live = playerConnection.queueWindows.value
            val liveCurrent = playerConnection.currentWindowIndex.value
            val position = live.indexOfFirst { it.uid == uid }
            if (position > liveCurrent && position - liveCurrent - 1 != dragSlot) {
                playerConnection.moveUpcoming(live[position].firstPeriodIndex, dragSlot)
            }
            draggedUid = null
            dragSlot = -1
        }
        val live = playerConnection.queueWindows.value
        val liveCurrent = playerConnection.currentWindowIndex.value
        val inRange = liveCurrent in live.indices
        past.clear()
        if (inRange) past.addAll(live.subList(0, liveCurrent))
        now = live.getOrNull(liveCurrent)
        upcoming.clear()
        upcoming.addAll(if (inRange) live.subList(liveCurrent + 1, live.size) else live)
    }

    var renderCount by rememberSaveable(queueSessionId) { mutableIntStateOf(QUEUE_RENDER_PAGE) }
    val rendered = upcoming.size.coerceAtMost(renderCount)
    val summary = remember(windows, currentIndex) {
        queueSummary(
            if (currentIndex in windows.indices) {
                windows.subList(currentIndex + 1, windows.size).map { it.mediaItem.metadata?.duration }
            } else {
                emptyList()
            },
        )
    }

    val headerCount = past.size + (if (now != null) 1 else 0)
    val latestRendered by rememberUpdatedState(rendered)
    val latestTotal by rememberUpdatedState(upcoming.size)
    val latestStatus by rememberUpdatedState(pageStatus)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestHeaderCount by rememberUpdatedState(headerCount)
    LaunchedEffect(listState, queueSessionId) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (!latestEnabled || lastVisible < 0) return@collect
                val next = nextRenderCount(latestRendered, latestTotal, lastVisible, latestHeaderCount)
                if (next > latestRendered) {
                    renderCount = next
                } else if (latestRendered >= latestTotal &&
                    lastVisible >= latestHeaderCount + latestRendered - 4 &&
                    latestStatus.hasMore && !latestStatus.isLoading && latestStatus.error == null
                ) {
                    playerConnection.loadMoreQueuePage()
                }
            }
    }

    LaunchedEffect(queueSessionId) {
        if (past.isNotEmpty()) listState.scrollToItem(past.size)
    }

    LaunchedEffect(undo?.id) {
        val id = undo?.id ?: return@LaunchedEffect
        delay(5_000)
        playerConnection.dismissQueueUndo(id)
    }

    fun play(window: Timeline.Window) {
        if (!enabled || listState.isScrollInProgress) return
        playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
        playerConnection.player.playWhenReady = true
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            QueueModes(
                shuffle = shuffle,
                repeatMode = repeatMode,
                autoplay = autoplay,
                onShuffle = { if (enabled) playerConnection.player.shuffleModeEnabled = !shuffle },
                onRepeat = { if (enabled) playerConnection.player.repeatMode = nextRepeatMode(repeatMode) },
                onAutoplay = {
                    val next = !autoplay
                    playerConnection.service.setAutoplayEnabled(next)
                    setAutoplay(next)
                },
            )
            QueueHeading(
                title = stringResource(
                    if (autoplay && upcoming.isEmpty()) R.string.liquid_continue_playing else R.string.liquid_playing_next,
                ),
                source = queueTitle?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.liquid_from, it) },
                count = if (summary.count > 0) stringResource(R.string.liquid_songs_count, summary.count) else null,
                duration = summary.totalSeconds?.let { makeTimeString(it * 1000L) },
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 6.dp, bottom = contentPadding.calculateBottomPadding()),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadingEdges(top = 10.dp, bottom = 28.dp)
                    .nestedScroll(nestedScrollConnection),
            ) {
                items(past, key = ::queueKey) { window ->
                    QueueRow(
                        window = window,
                        dimmed = true,
                        onClick = { play(window) },
                        menu = {
                            listOf(
                                MenuAction(
                                    title = stringResource(R.string.liquid_queue_remove),
                                    icon = Icons.Rounded.RemoveCircleOutline,
                                    destructive = true,
                                    onClick = { if (enabled) playerConnection.removeQueueItem(window.firstPeriodIndex) },
                                ),
                            )
                        },
                    )
                }

                now?.let { window ->
                    item(key = queueKey(window)) {
                        QueueRow(window = window, dimmed = false, current = true, onClick = {}, menu = null)
                    }
                }

                if (upcoming.isEmpty() && !pageStatus.isLoading) {
                    item(key = "queue_empty") {
                        QueueNote(
                            text = stringResource(if (autoplay) R.string.liquid_queue_empty_autoplay else R.string.liquid_queue_empty),
                        )
                    }
                }

                items(upcoming.take(renderCount), key = ::queueKey) { window ->
                    ReorderableItem(reorderState, key = queueKey(window), enabled = enabled) { lifted ->
                        val moveUp = stringResource(R.string.liquid_queue_move_up)
                        val moveDown = stringResource(R.string.liquid_queue_move_down)
                        fun move(by: Int): Boolean {
                            val slot = upcoming.indexOfFirst { it.uid == window.uid }
                            val target = slot + by
                            if (!enabled || slot < 0 || target !in upcoming.indices) return false
                            playerConnection.moveUpcoming(window.firstPeriodIndex, target)
                            return true
                        }
                        QueueRow(
                            window = window,
                            dimmed = false,
                            lifted = lifted,
                            gestures = Modifier
                                .longPressDraggableHandle(
                                    enabled = enabled,
                                    onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                )
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(moveUp) { move(-1) },
                                        CustomAccessibilityAction(moveDown) { move(1) },
                                    )
                                },
                            onClick = { play(window) },
                            menu = {
                                listOf(
                                    MenuAction(
                                        title = stringResource(R.string.liquid_queue_play_next),
                                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                        onClick = { if (enabled) playerConnection.playQueueItemNext(window.firstPeriodIndex) },
                                    ),
                                    MenuAction(
                                        title = stringResource(R.string.liquid_queue_remove),
                                        icon = Icons.Rounded.RemoveCircleOutline,
                                        destructive = true,
                                        onClick = { if (enabled) playerConnection.removeQueueItem(window.firstPeriodIndex) },
                                    ),
                                    MenuAction(
                                        title = stringResource(R.string.liquid_queue_clear_upcoming),
                                        icon = Icons.Rounded.ClearAll,
                                        destructive = true,
                                        onClick = { if (enabled) playerConnection.clearUpcoming() },
                                    ),
                                )
                            },
                        )
                    }
                }

                when {
                    pageStatus.isLoading -> item(key = "queue_page_loading") {
                        QueuePageFeedback(loading = true, onRetry = null)
                    }
                    pageStatus.error != null -> item(key = "queue_page_error") {
                        QueuePageFeedback(loading = false, onRetry = playerConnection::loadMoreQueuePage)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = undo != null,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            val shown = remember { mutableStateOf(undo) }
            undo?.let { shown.value = it }
            shown.value?.let { edit ->
                UndoPill(
                    text = if (edit.title != null) {
                        stringResource(R.string.liquid_queue_removed, edit.title)
                    } else {
                        stringResource(R.string.liquid_queue_cleared, edit.count)
                    },
                    backdrop = backdrop,
                    onUndo = { playerConnection.undoQueueEdit(edit.id) },
                )
            }
        }
    }
}

private fun queueKey(window: Timeline.Window): String = "q_${window.uid}"

@Composable
private fun QueueModes(
    shuffle: Boolean,
    repeatMode: Int,
    autoplay: Boolean,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onAutoplay: () -> Unit,
) {
    val on = stringResource(R.string.liquid_on)
    val off = stringResource(R.string.liquid_off)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QueueToggle(
            icon = Icons.Rounded.Shuffle,
            label = stringResource(R.string.liquid_shuffle),
            active = shuffle,
            state = if (shuffle) on else off,
            role = Role.Switch,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        )
        QueueToggle(
            icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = stringResource(R.string.liquid_repeat),
            active = repeatMode != Player.REPEAT_MODE_OFF,
            state = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> stringResource(R.string.liquid_repeat_one)
                Player.REPEAT_MODE_ALL -> stringResource(R.string.liquid_repeat_all)
                else -> off
            },
            role = Role.Button,
            onClick = onRepeat,
            modifier = Modifier.weight(1f),
        )
        QueueToggle(
            icon = Icons.Rounded.AllInclusive,
            label = stringResource(R.string.liquid_autoplay),
            active = autoplay,
            state = if (autoplay) on else off,
            role = Role.Switch,
            onClick = onAutoplay,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QueueHeading(
    title: String,
    source: String?,
    count: String?,
    duration: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = LiquidTypography.title3,
                color = NowPlayingInk.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (source != null) {
                Text(
                    source,
                    style = LiquidTypography.footnote,
                    color = NowPlayingInk.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (count != null) {
            Column(Modifier.padding(start = 16.dp), horizontalAlignment = Alignment.End) {
                Text(count, style = LiquidTypography.footnote.copy(fontFeatureSettings = "tnum"), color = NowPlayingInk.secondary, maxLines = 1)
                if (duration != null) {
                    Text(duration, style = LiquidTypography.footnote.copy(fontFeatureSettings = "tnum"), color = NowPlayingInk.secondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun QueueNote(text: String) {
    Text(
        text = text,
        style = LiquidTypography.subheadline,
        color = NowPlayingInk.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 28.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePageFeedback(loading: Boolean, onRetry: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onRetry != null) Modifier.combinedClickable(onClick = onRetry) else Modifier)
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = NowPlayingInk.secondary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.liquid_queue_loading), style = LiquidTypography.footnote, color = NowPlayingInk.secondary)
        } else {
            Text(stringResource(R.string.liquid_queue_retry), style = LiquidTypography.footnote, color = NowPlayingInk.primary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    window: Timeline.Window,
    dimmed: Boolean,
    onClick: () -> Unit,
    menu: (@Composable () -> List<MenuAction>)?,
    lifted: Boolean = false,
    current: Boolean = false,
    gestures: Modifier = Modifier,
) {
    val metadata = window.mediaItem.metadata ?: return
    val interaction = remember { MutableInteractionSource() }
    var menuOpen by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            lifted -> Color.White.copy(alpha = 0.12f)
            current -> Color.White.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = 600f),
        label = "lift",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .then(gestures)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            .graphicsLayer {
                alpha = if (dimmed) 0.5f else 1f
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            model = metadata.thumbnailUrl,
            shape = RoundedCornerShape(8.dp),
            hairline = false,
            modifier = Modifier.size(48.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 4.dp),
        ) {
            Text(
                metadata.title,
                style = LiquidTypography.callout.copy(fontWeight = FontWeight.SemiBold),
                color = NowPlayingInk.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    metadata.artists.joinToString { it.name },
                    style = LiquidTypography.subheadline,
                    color = NowPlayingInk.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (metadata.duration > 0) {
                    Text(
                        " · " + makeTimeString(metadata.duration * 1000L),
                        style = LiquidTypography.subheadline.copy(fontFeatureSettings = "tnum"),
                        color = NowPlayingInk.tertiary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (menu == null) {
            Spacer(Modifier.width(44.dp))
            return@Row
        }
        Box {
            val more = stringResource(R.string.liquid_queue_more, metadata.title)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = { menuOpen = true })
                    .semantics {
                        role = Role.Button
                        contentDescription = more
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (lifted) Icons.Rounded.DragHandle else Icons.Rounded.MoreHoriz,
                    null,
                    tint = NowPlayingInk.secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (menuOpen) {
                val colors = Liquid.colors
                val dark = remember(colors) { if (colors.isDark) colors else liquidDarkColors() }
                CompositionLocalProvider(LocalLiquidColors provides dark) {
                    LiquidPullDownMenu(
                        expanded = true,
                        onDismiss = { menuOpen = false },
                        entries = menu(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    state: String,
    role: Role,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val fill by animateColorAsState(
        if (active) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(140),
        label = "toggleFill",
    )
    val ink by animateColorAsState(
        if (active) Color(0xFF1C1C1E) else NowPlayingInk.primary,
        animationSpec = tween(140),
        label = "toggleInk",
    )
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .glassPressScale(press, -0.04f)
            .clip(shape)
            .background(fill, shape)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.role = role
                stateDescription = state
            }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.Medium),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UndoPill(text: String, backdrop: Backdrop?, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .liquidGlass(CircleShape, GlassKind.Regular, backdrop = backdrop)
            .heightIn(min = 44.dp)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = LiquidTypography.subheadline,
            color = NowPlayingInk.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            stringResource(R.string.liquid_queue_undo),
            style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            color = NowPlayingInk.primary,
            modifier = Modifier
                .clip(CircleShape)
                .combinedClickable(onClick = onUndo)
                .semantics { role = Role.Button }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}


private fun Modifier.fadingEdges(top: Dp, bottom: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val h = size.height.coerceAtLeast(1f)
        val mask = Brush.verticalGradient(
            0f to Color.Transparent,
            (top.toPx() / h).coerceIn(0f, 0.5f) to Color.Black,
            (1f - bottom.toPx() / h).coerceIn(0.5f, 1f) to Color.Black,
            1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = mask, blendMode = BlendMode.DstIn)
        }
    }
