package com.shiny.music.ui.liquid.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
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
import com.shiny.music.ui.liquid.LiquidPrefs
import com.shiny.music.ui.liquid.LiquidPullDownMenu
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidColors
import com.shiny.music.ui.liquid.MenuAction
import com.shiny.music.ui.liquid.TileShape
import com.shiny.music.ui.liquid.artworkRadius
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
    compact: Boolean = false,
    /** Scrolls the top section away with the list; null keeps it fixed (landscape). */
    collapse: QueueHeaderCollapse? = null,
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
        if (autoplay && enabled) playerConnection.service.ensureUpNext(25)
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
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
    val entries by remember { derivedStateOf { queueEntries(past, now, upcoming, renderCount) } }
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
        // a new queue opens with its whole header showing
        collapse?.animateTo(0f)
        if (past.isNotEmpty()) listState.scrollToItem(past.size)
    }

    LaunchedEffect(undo?.id) {
        val id = undo?.id ?: return@LaunchedEffect
        delay(5_000)
        playerConnection.dismissQueueUndo(id)
    }

    fun play(window: Timeline.Window) {
        if (!enabled || listState.isScrollInProgress || reorderState.isAnyItemDragging) return
        playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
        playerConnection.player.playWhenReady = true
    }

    // A swipe acts on the song by its identity, looked up in the live queue at the moment
    // it lands: the rows may be a beat behind a song change or another swipe.
    fun liveSlot(uid: Any): Pair<Int, Int>? {
        val live = playerConnection.queueWindows.value
        val liveCurrent = playerConnection.currentWindowIndex.value
        val position = live.indexOfFirst { it.uid == uid }
        if (position < 0 || position == liveCurrent) return null
        return position to liveCurrent
    }
    fun removeBySwipe(uid: Any): Boolean {
        if (!enabled) return false
        val (position, _) = liveSlot(uid) ?: return false
        playerConnection.removeQueueItem(playerConnection.queueWindows.value[position].firstPeriodIndex)
        return true
    }
    // false when there is nothing to do, so the row only springs back
    fun playNextBySwipe(uid: Any): Boolean {
        if (!enabled) return false
        val (position, liveCurrent) = liveSlot(uid) ?: return false
        if (position == liveCurrent + 1) return false
        playerConnection.playQueueItemNext(playerConnection.queueWindows.value[position].firstPeriodIndex)
        return true
    }

    Box(
        modifier
            .fillMaxSize()
            .then(
                if (collapse != null) {
                    // the pane rises with the region and grows by as much, so the list takes the room
                    Modifier.layout { measurable, constraints ->
                        val lift = collapse.offset.roundToInt()
                        val height = if (constraints.hasBoundedHeight) constraints.maxHeight + lift else constraints.maxHeight
                        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                        layout(placeable.width, constraints.constrainHeight(placeable.height - lift)) {
                            placeable.place(0, -lift)
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            Column(
                Modifier
                    .then(if (collapse != null) Modifier.onSizeChanged { collapse.paneHead = it.height.toFloat() } else Modifier)
                    .graphicsLayer {
                        if (collapse != null) alpha = (1f - collapse.fraction * 1.25f).coerceIn(0f, 1f)
                    },
            ) {
            QueueModes(
                shuffle = shuffle,
                repeatMode = repeatMode,
                autoplay = autoplay,
                compact = compact,
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
                summary = if (summary.count > 0) {
                    listOfNotNull(
                        pluralStringResource(R.plurals.liquid_songs_count, summary.count, summary.count),
                        summary.totalSeconds?.let { makeTimeString(it * 1000L) },
                    ).joinToString(" · ")
                } else {
                    null
                },
                compact = compact,
            )
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = PlayerSpace.xs,
                    bottom = contentPadding.calculateBottomPadding() + if (undo != null) 64.dp else 0.dp,
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadingEdges(top = 10.dp, bottom = 28.dp)
                    .nestedScroll(nestedScrollConnection)
                    .then(if (collapse != null) Modifier.nestedScroll(collapse.connection) else Modifier),
            ) {
                itemsIndexed(entries, key = { _, entry -> queueKey(entry.item) }) { index, entry ->
                    val window = entry.item
                    val movable = entry.role == QueueRole.Upcoming
                    ReorderableItem(reorderState, key = queueKey(window), enabled = enabled && movable) { lifted ->
                        val moveUp = stringResource(R.string.liquid_queue_move_up)
                        val moveDown = stringResource(R.string.liquid_queue_move_down)
                        val playNextLabel = stringResource(R.string.liquid_queue_play_next)
                        val removeLabel = stringResource(R.string.liquid_queue_remove)
                        fun move(by: Int): Boolean {
                            val slot = upcoming.indexOfFirst { it.uid == window.uid }
                            val target = slot + by
                            if (!enabled || slot < 0 || target !in upcoming.indices) return false
                            playerConnection.moveUpcoming(window.firstPeriodIndex, target)
                            return true
                        }
                        val swipeable = enabled && entry.role != QueueRole.Current && !dragging
                        QueueSwipe(
                            enabled = swipeable,
                            onRemove = { removeBySwipe(window.uid) },
                            onPlayNext = { playNextBySwipe(window.uid) },
                        ) {
                            QueueRow(
                                window = window,
                                role = entry.role,
                                lifted = lifted,
                                divider = index < entries.lastIndex,
                                gestures = when {
                                    movable -> Modifier
                                        .longPressDraggableHandle(
                                            enabled = enabled,
                                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        )
                                        .semantics {
                                            customActions = listOf(
                                                CustomAccessibilityAction(moveUp) { move(-1) },
                                                CustomAccessibilityAction(moveDown) { move(1) },
                                                CustomAccessibilityAction(playNextLabel) { playNextBySwipe(window.uid) },
                                                CustomAccessibilityAction(removeLabel) { removeBySwipe(window.uid) },
                                            )
                                        }
                                    entry.role == QueueRole.Played -> Modifier.semantics {
                                        customActions = listOf(
                                            CustomAccessibilityAction(playNextLabel) { playNextBySwipe(window.uid) },
                                            CustomAccessibilityAction(removeLabel) { removeBySwipe(window.uid) },
                                        )
                                    }
                                    else -> Modifier
                                },
                                onClick = { play(window) },
                            )
                        }
                    }
                }

                if (!autoplay && !pageStatus.isLoading && !pageStatus.hasMore && upcoming.size <= renderCount) {
                    item(key = "queue_autoplay_off") {
                        AutoplayOffNote(
                            ended = upcoming.isEmpty(),
                            onTurnOn = {
                                playerConnection.service.setAutoplayEnabled(true)
                                setAutoplay(true)
                            },
                        )
                    }
                } else if (upcoming.isEmpty() && !pageStatus.isLoading) {
                    item(key = "queue_empty") {
                        QueueNote(text = stringResource(R.string.liquid_queue_empty_autoplay))
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
            enter = fadeIn(tween(PlayerMotion.Small)) + slideInVertically(tween(PlayerMotion.Small)) { it / 2 },
            exit = fadeOut(tween(PlayerMotion.Press)) + slideOutVertically(tween(PlayerMotion.Small)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = PlayerSpace.m),
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
    compact: Boolean,
) {
    val on = stringResource(R.string.liquid_on)
    val off = stringResource(R.string.liquid_off)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerSpace.xl, vertical = if (compact) PlayerSpace.xs else PlayerSpace.s),
        horizontalArrangement = Arrangement.spacedBy(PlayerSpace.s),
    ) {
        QueueModeTile(
            icon = Icons.Rounded.Shuffle,
            label = stringResource(R.string.liquid_shuffle),
            active = shuffle,
            state = if (shuffle) on else off,
            role = Role.Switch,
            onClick = onShuffle,
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        QueueModeTile(
            icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = stringResource(repeatLabel(repeatMode)),
            active = repeatMode != Player.REPEAT_MODE_OFF,
            state = if (repeatMode == Player.REPEAT_MODE_OFF) off else stringResource(repeatLabel(repeatMode)),
            role = Role.Button,
            onClick = onRepeat,
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        QueueModeTile(
            icon = Icons.Rounded.AllInclusive,
            label = stringResource(R.string.liquid_autoplay),
            active = autoplay,
            state = if (autoplay) on else off,
            role = Role.Switch,
            onClick = onAutoplay,
            compact = compact,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QueueHeading(
    title: String,
    summary: String?,
    compact: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = PlayerSpace.xl,
                end = PlayerSpace.xl,
                top = if (compact) PlayerSpace.s else PlayerSpace.l,
                bottom = if (compact) PlayerSpace.xs else PlayerSpace.s,
            ),
    ) {
        Text(
            title,
            style = PlayerType.section,
            color = NowPlayingInk.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
        )
        if (summary != null) {
            Text(
                summary,
                style = LiquidTypography.footnote.copy(fontFeatureSettings = "tnum"),
                color = NowPlayingInk.secondary,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = PlayerSpace.m)
                    .alignByBaseline(),
            )
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
            .padding(horizontal = PlayerSpace.xxxl, vertical = 28.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutoplayOffNote(ended: Boolean, onTurnOn: () -> Unit) {
    val accent = Liquid.colors.accent
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerSpace.xxxl, vertical = PlayerSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (ended) R.string.liquid_queue_empty else R.string.liquid_queue_ends_here),
            style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = NowPlayingInk.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.liquid_queue_autoplay_off),
            style = LiquidTypography.footnote,
            color = NowPlayingInk.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PlayerSpace.xs),
        )
        Row(
            Modifier
                .padding(top = PlayerSpace.m)
                .heightIn(min = QueueMetrics.target)
                .clip(CircleShape)
                .drawBehind { drawRect(accent.copy(alpha = 0.24f)) }
                .combinedClickable(onClick = onTurnOn)
                .semantics { role = Role.Button }
                .padding(horizontal = PlayerSpace.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AllInclusive, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(PlayerSpace.s))
            Text(
                stringResource(R.string.liquid_queue_autoplay_turn_on),
                style = PlayerType.label,
                color = NowPlayingInk.primary,
            )
        }
    }
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

private val RowShape = RoundedCornerShape(14.dp)
private val RowArtShape = RoundedCornerShape(artworkRadius(QueueMetrics.artwork))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    window: Timeline.Window,
    role: QueueRole,
    onClick: () -> Unit,
    lifted: Boolean = false,
    divider: Boolean = false,
    gestures: Modifier = Modifier,
) {
    val metadata = window.mediaItem.metadata ?: return
    val accent = Liquid.colors.accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val current = role == QueueRole.Current
    val fill = animateColorAsState(
        when {
            lifted -> Color.White.copy(alpha = 0.10f)
            pressed && !current -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(PlayerMotion.Press),
        label = "rowFill",
    )
    val lift = animateFloatAsState(if (lifted) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "lift")
    val dim = animateFloatAsState(if (role == QueueRole.Played) 0.5f else 1f, PlayerMotion.small(), label = "dim")
    val nowness = animateFloatAsState(if (current) 1f else 0f, PlayerMotion.small(), label = "now")
    val nowPlaying = stringResource(R.string.liquid_queue_now_playing)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = QueueMetrics.rowMinHeight)
            .padding(horizontal = PlayerSpace.s)
            .graphicsLayer {
                val l = lift.value
                scaleX = 1f + 0.02f * l
                scaleY = 1f + 0.02f * l
                shadowElevation = 8.dp.toPx() * l
                shape = RowShape
                clip = true
            }
            .drawBehind {
                drawRect(fill.value)
                if (divider && lift.value < 0.01f) {
                    val start = (PlayerSpace.m + QueueMetrics.artwork + 14.dp).toPx()
                    val stroke = 0.5.dp.toPx()
                    drawRect(
                        Color.White.copy(alpha = 0.08f * dim.value),
                        topLeft = Offset(start, size.height - stroke),
                        size = Size(size.width - start - PlayerSpace.m.toPx(), stroke),
                    )
                }
            }
            .then(
                if (current) {
                    Modifier.semantics(mergeDescendants = true) { stateDescription = nowPlaying }
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                        onLongClick = {},
                        hapticFeedbackEnabled = false,
                    )
                }
            )
            .then(gestures)
            .padding(start = PlayerSpace.m, end = PlayerSpace.m, top = PlayerSpace.s, bottom = PlayerSpace.s)
            .graphicsLayer {
                alpha = dim.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(QueueMetrics.artwork)) {
            Artwork(
                model = metadata.thumbnailUrl,
                shape = RowArtShape,
                hairline = false,
                modifier = Modifier.fillMaxSize(),
            )
            if (current || nowness.value > 0f) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .clip(RowArtShape)
                        .graphicsLayer { alpha = nowness.value }
                ) { drawNowPlayingMark(accent) }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = PlayerSpace.xs),
        ) {
            Text(
                metadata.title,
                style = PlayerType.rowTitle,
                color = NowPlayingInk.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                metadata.artists.joinToString { it.name },
                style = PlayerType.rowMeta,
                color = NowPlayingInk.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metadata.duration > 0) {
            Text(
                makeTimeString(metadata.duration * 1000L),
                style = PlayerType.rowMeta.copy(fontFeatureSettings = "tnum"),
                color = NowPlayingInk.tertiary,
                maxLines = 1,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNowPlayingMark(accent: Color) {
    drawRect(Color.Black.copy(alpha = 0.5f))
    val bar = 3.dp.toPx()
    val gap = 2.5.dp.toPx()
    val tall = 16.dp.toPx()
    val heights = floatArrayOf(0.55f, 1f, 0.75f)
    val total = bar * 3 + gap * 2
    var x = (size.width - total) / 2f
    val bottom = (size.height + tall) / 2f
    for (h in heights) {
        val barHeight = tall * h
        drawRoundRect(accent, Offset(x, bottom - barHeight), Size(bar, barHeight), CornerRadius(bar / 2f))
        x += bar + gap
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueModeTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    state: String,
    role: Role,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = Liquid.colors.accent
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val on by animateFloatAsState(if (active) 1f else 0f, PlayerMotion.small(), label = "modeOn")
    val tile = modifier
        .heightIn(min = if (compact) QueueMetrics.target else QueueMetrics.modeTileHeight)
        .glassPressScale(press, -0.04f)
            .clip(TileShape)
            .drawBehind {
                drawRect(lerp(Color.White.copy(alpha = 0.07f), accent.copy(alpha = 0.24f), on))
                if (on > 0.01f) {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        accent.copy(alpha = 0.55f * on),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(18.dp.toPx() - stroke / 2f),
                        style = Stroke(stroke),
                    )
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            )
            .semantics(mergeDescendants = true) {
                this.role = role
                stateDescription = state
            }
    val iconTint = lerp(NowPlayingInk.secondary, accent, on)
    val labelInk = lerp(NowPlayingInk.secondary, NowPlayingInk.primary, on)
    if (compact) {
        Row(
            tile.padding(horizontal = PlayerSpace.s),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = PlayerType.label, color = labelInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Column(
            tile.padding(horizontal = PlayerSpace.s, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(PlayerSpace.xs))
            Text(label, style = PlayerType.label, color = labelInk, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UndoPill(text: String, backdrop: Backdrop?, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = PlayerSpace.xl)
            .liquidGlass(CircleShape, GlassKind.Regular, backdrop = backdrop)
            .heightIn(min = 48.dp)
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
            color = Liquid.colors.accent,
            modifier = Modifier
                .clip(CircleShape)
                .combinedClickable(onClick = onUndo)
                .semantics { role = Role.Button }
                .padding(horizontal = 12.dp, vertical = 14.dp),
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
