package com.shiny.music.ui.liquid.customize

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.music.innertube.YouTube
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalDownloadUtil
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.constants.SwipeToSongKey
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.models.MediaMetadata
import com.shiny.music.playback.ExoDownloadService
import com.shiny.music.ui.component.LocalBottomSheetPageState
import com.shiny.music.ui.menu.AddToPlaylistDialog
import com.shiny.music.ui.utils.ShowMediaInfo
import com.shiny.music.utils.isLocalMediaId
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val SwipeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private val PillEdgeInset = 10.dp
private val PillGap = 8.dp
private val SlabInset = 10.dp
private val PillPadding = 13.dp
private val PillIconSize = 18.dp
private val PillLabelSpacing = 6.dp
private val PillIdle = Color.White.copy(alpha = 0.12f)
private val SlabColor = Color.White.copy(alpha = 0.08f)
private const val Resistance = 0.35f
private const val SwipeHoldMillis = 650L

@Composable
fun SongSwipeBox(
    song: MediaMetadata?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val swipeOn by rememberPreference(SwipeToSongKey, true)
    if (song == null || !swipeOn) {
        Box(modifier) { content() }
        return
    }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val right by rememberEnumPreference(CustomizePrefs.SwipeRight, DefaultSwipeRight)
    val left by rememberEnumPreference(CustomizePrefs.SwipeLeft, DefaultSwipeLeft)

    val startAction = right
    val endAction = left

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableFloatStateOf(0f) }
    var travel by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableIntStateOf(0) }
    var fired by remember { mutableIntStateOf(0) }
    val settle = remember { arrayOfNulls<Job>(1) }
    var startNeed by remember { mutableFloatStateOf(0f) }
    var endNeed by remember { mutableFloatStateOf(0f) }
    val runner = rememberSwipeRunner(song)

    val baseThreshold = with(density) { (rowSize.width * 0.3f).coerceIn(100.dp.toPx(), 150.dp.toPx()) }
    val pillRoom = with(density) { (PillEdgeInset + PillGap - SlabInset).toPx() }
    val deadSide = with(density) { 22.dp.toPx() }
    val hysteresis = with(density) { 4.dp.toPx() }

    fun line(direction: Float) = maxOf(baseThreshold, (if (direction > 0f) startNeed else endNeed) + pillRoom)
    fun resisted(raw: Float): Float {
        val allowed = if (raw > 0f) startAction != null else endAction != null
        val distance = abs(raw)
        val l = line(raw)
        val shown = when {
            !allowed -> minOf(distance * 0.2f, deadSide)
            distance <= l -> distance
            else -> l + (distance - l) * Resistance
        }
        return sign(raw) * shown
    }
    fun unresisted(shown: Float): Float {
        val distance = abs(shown)
        val l = line(shown)
        return if (distance <= l) shown else sign(shown) * (l + (distance - l) / Resistance)
    }

    val dragState = rememberDraggableState { delta ->
        travel += delta
        offset = resisted(travel)
        val side = when {
            offset > 0f && startAction != null && offset >= line(1f) -> 1
            offset < 0f && endAction != null && -offset >= line(-1f) -> -1
            armed != 0 && sign(offset) == armed.toFloat() && abs(offset) >= line(offset) - hysteresis -> armed
            else -> 0
        }
        if (side != armed) {
            armed = side
            haptic.performHapticFeedback(if (side != 0) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick)
        }
    }
    val showStart by remember { derivedStateOf { offset > 0.5f } }
    val showEnd by remember { derivedStateOf { offset < -0.5f } }

    val baseConfiguration = androidx.compose.ui.platform.LocalViewConfiguration.current
    val longHold = remember(baseConfiguration) {
        object : androidx.compose.ui.platform.ViewConfiguration by baseConfiguration {
            override val longPressTimeoutMillis: Long
                get() = maxOf(baseConfiguration.longPressTimeoutMillis, SwipeHoldMillis)
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalViewConfiguration provides longHold) {
    Box(
        modifier = modifier
            .onSizeChanged { rowSize = it }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    settle[0]?.cancel()
                    fired = 0
                    travel = unresisted(offset)
                },
                onDragStopped = { velocity ->
                    val side = armed
                    val action = when (side) {
                        1 -> startAction
                        -1 -> endAction
                        else -> null
                    }
                    if (action != null) {
                        fired = side
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        runner.run(action)
                    }
                    armed = 0
                    settle[0] = scope.launch {
                        animate(
                            initialValue = offset,
                            targetValue = 0f,
                            initialVelocity = velocity.coerceIn(-3000f, 3000f) * 0.35f,
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f),
                        ) { value, _ ->
                            offset = value
                            travel = value
                        }
                        fired = 0
                    }
                },
            ),
    ) {
        if (showStart && startAction != null) {
            SwipePill(
                icon = startAction.icon,
                label = runner.label(startAction),
                color = startAction.color,
                atStart = true,
                room = { (offset - pillRoom).coerceAtLeast(0f) },
                rowHeight = rowSize.height,
                armed = armed == 1,
                fired = fired == 1,
                onNeed = { startNeed = it },
            )
        }
        if (showEnd && endAction != null) {
            SwipePill(
                icon = endAction.icon,
                label = runner.label(endAction),
                color = endAction.color,
                atStart = false,
                room = { (-offset - pillRoom).coerceAtLeast(0f) },
                rowHeight = rowSize.height,
                armed = armed == -1,
                fired = fired == -1,
                onNeed = { endNeed = it },
            )
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset }
                .drawBehind {
                    val strength = (abs(offset) / 20.dp.toPx()).coerceIn(0f, 1f)
                    if (strength > 0f) {
                        val inset = SlabInset.toPx()
                        drawRoundRect(
                            color = SlabColor.copy(alpha = SlabColor.alpha * strength),
                            topLeft = Offset(inset, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height),
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                },
        ) {
            content()
        }
    }
    }
}

private class SwipeRunner(
    val run: (SongSwipeAction) -> Unit,
    val label: @Composable (SongSwipeAction) -> String,
)

@Composable
private fun rememberSwipeRunner(song: MediaMetadata): SwipeRunner {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val downloadUtil = LocalDownloadUtil.current
    val sheetPage = LocalBottomSheetPageState.current
    val local = song.id.isLocalMediaId()
    var choosePlaylist by remember { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = choosePlaylist,
        onGetSong = { playlist ->
            database.query { insert(song) }
            if (!local) {
                SwipeScope.launch {
                    playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, song.id) }
                }
            }
            listOf(song.id)
        },
        onDismiss = { choosePlaylist = false },
    )

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    return remember(song, playerConnection, sheetPage, local) {
        SwipeRunner(
            run = { action ->
                when (action) {
                    SongSwipeAction.Queue -> {
                        playerConnection?.addToQueue(song.toMediaItem())
                        toast("Added to queue")
                    }
                    SongSwipeAction.PlayNext -> {
                        playerConnection?.playNext(song.toMediaItem())
                        toast("Plays next")
                    }
                    SongSwipeAction.Library -> SwipeScope.launch {
                        val existing = database.getSongById(song.id)
                        val inLibrary = existing?.song?.inLibrary != null
                        val token = existing?.song?.let { if (inLibrary) it.libraryRemoveToken else it.libraryAddToken }
                        if (!local) token?.let { YouTube.feedback(listOf(it)) }
                        database.query {
                            if (existing == null) {
                                insert(song) { it.toggleLibrary(syncToYouTube = !local) }
                            } else {
                                update(existing.song.toggleLibrary(syncToYouTube = !local))
                            }
                        }
                        launch(Dispatchers.Main) { toast(if (inLibrary) "Removed from library" else "Added to library") }
                    }
                    SongSwipeAction.Download -> SwipeScope.launch {
                        if (local) {
                            launch(Dispatchers.Main) { toast("Already on this device") }
                            return@launch
                        }
                        val done = downloadUtil.getDownload(song.id).first()?.state == Download.STATE_COMPLETED
                        if (done) {
                            DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.id, false)
                            launch(Dispatchers.Main) { toast("Download removed") }
                        } else {
                            database.query { insert(song) }
                            val request = DownloadRequest.Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                            DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
                            launch(Dispatchers.Main) { toast("Downloading") }
                        }
                    }
                    SongSwipeAction.AddToPlaylist -> choosePlaylist = true
                    SongSwipeAction.Details -> sheetPage.show { ShowMediaInfo(song.id, fallback = song) }
                }
            },
            label = { action ->
                when (action) {
                    SongSwipeAction.Library -> {
                        val stored by database.song(song.id).collectAsState(initial = null)
                        if (stored?.song?.inLibrary != null) "Remove from library" else "Add to library"
                    }
                    SongSwipeAction.Download -> {
                        val download by downloadUtil.getDownload(song.id).collectAsState(initial = null)
                        if (download?.state == Download.STATE_COMPLETED) "Remove download" else "Download"
                    }
                    else -> action.pill
                }
            },
        )
    }
}

@Composable
private fun BoxScope.SwipePill(
    icon: Int,
    label: String,
    color: Color,
    atStart: Boolean,
    room: () -> Float,
    rowHeight: Int,
    armed: Boolean,
    fired: Boolean,
    onNeed: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = remember { androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp) }
    val pillHeight = with(density) { (rowHeight.toDp() * 0.6f).coerceIn(32.dp, 38.dp).toPx() }
    val labelWidth = remember(label, density) { measurer.measure(label, labelStyle).size.width.toFloat() }
    val need = with(density) { (PillPadding * 2 + PillIconSize + PillLabelSpacing).toPx() } + labelWidth
    androidx.compose.runtime.SideEffect { onNeed(need) }

    val width by animateFloatAsState(room().coerceIn(0f, need), spring(dampingRatio = 0.72f, stiffness = 1100f), label = "pillWidth")
    val fill by animateColorAsState(if (armed || fired) color else PillIdle, spring(stiffness = 700f), label = "pillFill")
    val bump = remember { Animatable(1f) }
    LaunchedEffect(armed, fired) {
        if (armed || fired) {
            bump.animateTo(if (fired) 1.3f else 1.2f, tween(85))
            bump.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 560f))
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (atStart) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = PillEdgeInset)
            .layout { measurable, _ ->

                val w = minOf(width, room()).coerceAtLeast(0f)
                val h = minOf(w, pillHeight)
                val placeable = measurable.measure(Constraints.fixed(w.roundToInt(), h.roundToInt()))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .clip(RoundedCornerShape(50))
            .background(fill),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.wrapContentWidth(unbounded = true)) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier
                    .size(PillIconSize)
                    .graphicsLayer {
                        val grown = (minOf(width, room()) / pillHeight).coerceIn(0f, 1f)
                        val scale = (0.35f + 0.65f * grown) * bump.value
                        scaleX = scale
                        scaleY = scale
                        alpha = grown
                    },
            )
            AnimatedVisibility(
                visible = armed,
                enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 700f), expandFrom = Alignment.Start) + fadeIn(tween(160)),
                exit = shrinkHorizontally(tween(140), shrinkTowards = Alignment.Start) + fadeOut(tween(100)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(PillLabelSpacing))
                    Text(text = label, style = labelStyle, color = Color.White, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
