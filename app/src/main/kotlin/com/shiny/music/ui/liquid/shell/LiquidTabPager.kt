package com.shiny.music.ui.liquid.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.shiny.music.viewmodels.HomeViewModel
import com.shiny.music.ui.liquid.LocalPageIsCurrent
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.appearance.PageTransitions
import com.shiny.music.ui.liquid.screens.LiquidHomeScreen
import com.shiny.music.ui.liquid.screens.LiquidLibraryScreen
import com.shiny.music.ui.liquid.screens.LiquidNewScreen
import com.shiny.music.ui.screens.Screens
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SwipeDistanceThreshold = 0.28f
private const val SwipeVelocityThreshold = 900f

/**
 * One absolute progress value drives both the page translation and the tab capsule.
 * Home is 0, New is 1 and Library is 2.
 */
@Stable
class LiquidTabSwipeState internal constructor(private val pageCount: Int) {
    private val position = Animatable(0f)
    private var settledIndex by mutableIntStateOf(0)

    var isDragging by mutableStateOf(false)
        private set

    val progress: Float
        get() = position.value

    /** Starts a drag without waiting for the first delta; used by the small glass handle. */
    fun beginDragImmediately(index: Int) {
        settledIndex = index.coerceIn(0, pageCount - 1)
        isDragging = true
    }

    suspend fun syncTo(index: Int, instant: Boolean) {
        if (isDragging) return
        val target = index.coerceIn(0, pageCount - 1)
        settledIndex = target
        if (abs(position.value - target) < 0.001f) return
        if (instant) position.snapTo(target.toFloat()) else position.animateTo(target.toFloat(), settleSpring())
    }

    suspend fun dragBy(deltaPx: Float, pageWidthPx: Float) {
        if (!isDragging || pageWidthPx <= 0f) return
        val raw = position.value - deltaPx / pageWidthPx
        val maxPage = (pageCount - 1).toFloat()
        val next = when {
            raw < 0f -> raw * 0.18f
            raw > maxPage -> maxPage + (raw - maxPage) * 0.18f
            else -> raw
        }
        position.snapTo(next.coerceIn(-0.18f, maxPage + 0.18f))
    }

    suspend fun settle(velocityX: Float, instant: Boolean, onCommit: (Int) -> Unit) {
        if (!isDragging) return
        val current = position.value
        val target = resolveLiquidTabTarget(settledIndex, current, velocityX, pageCount)
        val changed = target != settledIndex

        if (instant) position.snapTo(target.toFloat()) else position.animateTo(target.toFloat(), settleSpring())
        isDragging = false
        settledIndex = target
        if (changed) onCommit(target)
    }

    suspend fun cancel(instant: Boolean) {
        if (!isDragging) return
        if (instant) position.snapTo(settledIndex.toFloat()) else position.animateTo(settledIndex.toFloat(), settleSpring())
        isDragging = false
    }

    private fun settleSpring() = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessMedium,
    )
}

@Composable
fun rememberLiquidTabSwipeState(pageCount: Int): LiquidTabSwipeState =
    remember(pageCount) { LiquidTabSwipeState(pageCount) }

/** Pure target selection used by the gesture state and focused unit tests. */
internal fun resolveLiquidTabTarget(
    settledIndex: Int,
    progress: Float,
    velocityX: Float,
    pageCount: Int,
): Int {
    val direction = when {
        velocityX < -SwipeVelocityThreshold -> 1
        velocityX > SwipeVelocityThreshold -> -1
        else -> 0
    }
    val candidate = if (direction != 0) {
        settledIndex + direction
    } else when {
        progress > settledIndex + SwipeDistanceThreshold -> settledIndex + 1
        progress < settledIndex - SwipeDistanceThreshold -> settledIndex - 1
        else -> settledIndex
    }
    return candidate.coerceIn(0, pageCount - 1)
}

/**
 * The three root pages remain in one composition while the user is on a root tab. Each page
 * owns its existing lazy-list state through SaveableStateProvider; pushed destinations still
 * use the regular NavHost and are never duplicated here.
 */
@Composable
fun LiquidTopLevelPager(
    navController: NavController,
    homeViewModel: HomeViewModel,
    selectedIndex: Int,
    swipeState: LiquidTabSwipeState,
    onPageCommitted: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val instantTransitions = LocalShinyAppearance.current.transitions == PageTransitions.Instant
    val stateHolder = rememberSaveableStateHolder()
    val pageRoutes = remember {
        listOf(Screens.Home.route, Screens.Explore.route, Screens.Library.route)
    }

    LaunchedEffect(selectedIndex, instantTransitions) {
        swipeState.syncTo(selectedIndex, instantTransitions)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(swipeState, selectedIndex) {
                val edgeSlop = with(density) { 24.dp.toPx() }
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (offset.x !in edgeSlop..(size.width - edgeSlop)) return@detectHorizontalDragGestures
                        tracker.resetTracking()
                        swipeState.beginDragImmediately(selectedIndex)
                    },
                    onHorizontalDrag = { change: PointerInputChange, dragAmount ->
                        if (!swipeState.isDragging) return@detectHorizontalDragGestures
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        scope.launch {
                            swipeState.dragBy(dragAmount, size.width.toFloat())
                        }
                    },
                    onDragEnd = {
                        val velocityX = tracker.calculateVelocity().x
                        scope.launch {
                            swipeState.settle(velocityX, instantTransitions) { target ->
                                onPageCommitted(target)
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { swipeState.cancel(instantTransitions) }
                    },
                )
            },
    ) {
        // Precompose the current page and its neighbours. This keeps the next page warm before
        // the first pixel of a drag, while avoiding three full feeds on the Home/Library edges.
        val visiblePages = (selectedIndex - 1..selectedIndex + 1)
            .filter { it in pageRoutes.indices }

        visiblePages.forEach { index ->
            val route = pageRoutes[index]
            stateHolder.SaveableStateProvider(route) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (index == selectedIndex) 1f else 0f)
                        .graphicsLayer {
                            translationX = (index - swipeState.progress) * size.width
                        },
                ) {
                    CompositionLocalProvider(LocalPageIsCurrent provides (index == selectedIndex)) {
                        when (index) {
                            0 -> LiquidHomeScreen(navController, homeViewModel)
                            1 -> LiquidNewScreen(navController)
                            2 -> LiquidLibraryScreen(navController)
                        }
                    }
                }
            }
        }
    }
}
