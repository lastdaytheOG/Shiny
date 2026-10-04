package com.shiny.music.ui.liquid.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.ui.component.backdrop.Backdrop
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidBackdrop
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.liquidGlass
import com.shiny.music.ui.liquid.rememberGlassPress
import com.shiny.music.ui.player.ArtworkAnchor
import com.shiny.music.ui.player.playerArtworkAnchor
import com.shiny.music.ui.theme.pressScale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val TabBarHeight = 62.dp
val AccessoryHeight = 54.dp
val ChromeGap = 8.dp

@Immutable
data class LiquidTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Whether the tab bar is minimised, driven by the direction content is being scrolled.
 *
 * iOS 26 shrinks the tab bar to a single button while you read downwards and restores
 * it the moment you scroll back up; the mini player slides into the space it frees.
 */
@Stable
class LiquidChromeState internal constructor() {
    internal val collapse = Animatable(0f)
    var collapsed by mutableStateOf(false)
        private set
    private var accumulated = 0f

    val progress: Float get() = collapse.value

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput && source != NestedScrollSource.SideEffect) return Offset.Zero
            val dy = consumed.y
            if (dy == 0f) return Offset.Zero
            if ((dy < 0f && accumulated > 0f) || (dy > 0f && accumulated < 0f)) accumulated = 0f
            accumulated += dy
            if (accumulated < -ThresholdPx && !collapsed) {
                collapsed = true
                accumulated = 0f
            } else if (accumulated > ThresholdPx && collapsed) {
                collapsed = false
                accumulated = 0f
            }
            return Offset.Zero
        }
    }

    fun expand() {
        collapsed = false
        accumulated = 0f
    }

    private companion object {
        const val ThresholdPx = 60f
    }
}

@Composable
fun rememberLiquidChromeState(): LiquidChromeState {
    val state = remember { LiquidChromeState() }
    LaunchedEffect(state.collapsed) {
        state.collapse.animateTo(
            if (state.collapsed) 1f else 0f,
            spring(dampingRatio = 0.82f, stiffness = 360f),
        )
    }
    return state
}

/**
 * The floating chrome: tab capsule, search button and the mini player accessory, all
 * Liquid Glass over [backdrop] (the NavHost's recorded content).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidTabBar(
    tabs: List<LiquidTab>,
    selectedRoute: String?,
    searchRoute: String?,
    onTabClick: (LiquidTab, Boolean) -> Unit,
    onSearchClick: (Boolean) -> Unit,
    backdrop: Backdrop,
    chromeState: LiquidChromeState,
    showTabs: Boolean,
    showAccessory: Boolean,
    onAccessoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = Liquid.colors
    val selectedIndex = tabs.indexOfFirst { it.route == selectedRoute }
    val searchSelected = searchRoute != null && selectedRoute == searchRoute
    // Written by the bar's own measure pass, read by the capsule's, within the same pass.
    val expandedTabsWidth = remember { IntArray(1) }

    CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
        Layout(
            modifier = modifier.fillMaxWidth(),
            content = {
                if (showTabs) {
                    TabCapsule(
                        tabs = tabs,
                        selectedIndex = selectedIndex,
                        collapse = { chromeState.progress },
                        expandedWidth = { expandedTabsWidth[0] },
                        onTabClick = { tab, reselect ->
                            chromeState.expand()
                            onTabClick(tab, reselect)
                        },
                        onCollapsedClick = { chromeState.expand() },
                        modifier = Modifier.layoutId("tabs"),
                    )
                    if (searchRoute != null) {
                        SearchOrb(
                            selected = searchSelected,
                            onClick = { onSearchClick(searchSelected) },
                            modifier = Modifier.layoutId("search"),
                        )
                    }
                }
                if (showAccessory) {
                    MiniPlayerAccessory(
                        onClick = onAccessoryClick,
                        compact = { if (showTabs) chromeState.progress else 0f },
                        modifier = Modifier.layoutId("accessory"),
                    )
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val tabH = TabBarHeight.roundToPx()
            val accH = AccessoryHeight.roundToPx()
            val gap = ChromeGap.roundToPx()
            val orb = tabH
            val c = if (showTabs) chromeState.progress else 0f

            val tabsM = measurables.firstOrNull { it.layoutId == "tabs" }
            val searchM = measurables.firstOrNull { it.layoutId == "search" }
            val accM = measurables.firstOrNull { it.layoutId == "accessory" }

            val searchWidth = if (searchM != null) orb + gap else 0
            val tabsFull = width - searchWidth
            expandedTabsWidth[0] = tabsFull
            val tabsWidth = lerp(tabsFull.toFloat(), orb.toFloat(), c).roundToInt()
            val tabsP = tabsM?.measure(Constraints.fixed(tabsWidth.coerceAtLeast(orb), tabH))
            val searchP = searchM?.measure(Constraints.fixed(orb, orb))

            val accExpandedW = width
            val accCollapsedW = (width - (orb + gap) - searchWidth).coerceAtLeast(orb)
            val accW = if (showTabs) lerp(accExpandedW.toFloat(), accCollapsedW.toFloat(), c).roundToInt() else width
            val accHeightNow = if (showTabs) lerp(accH.toFloat(), tabH.toFloat(), c).roundToInt() else accH
            val accP = accM?.measure(Constraints.fixed(accW, accHeightNow))

            val hasTabs = tabsP != null
            val hasAcc = accP != null
            val totalH = when {
                hasTabs && hasAcc -> lerp((tabH + gap + accH).toFloat(), tabH.toFloat(), c).roundToInt()
                hasTabs -> tabH
                hasAcc -> accH
                else -> 0
            }

            layout(width, totalH) {
                val tabsY = totalH - tabH
                tabsP?.placeRelative(0, tabsY)
                searchP?.placeRelative(width - orb, tabsY)
                if (accP != null) {
                    if (hasTabs) {
                        val expandedX = 0f
                        val collapsedX = (orb + gap).toFloat()
                        val x = lerp(expandedX, collapsedX, c).roundToInt()
                        val expandedY = 0f
                        val collapsedY = (totalH - accHeightNow).toFloat()
                        val y = lerp(expandedY, collapsedY, c).roundToInt()
                        accP.placeRelative(x, y)
                    } else {
                        accP.placeRelative(0, 0)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCapsule(
    tabs: List<LiquidTab>,
    selectedIndex: Int,
    collapse: () -> Float,
    expandedWidth: () -> Int,
    onTabClick: (LiquidTab, Boolean) -> Unit,
    onCollapsedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    val indicator = remember { Animatable(selectedIndex.coerceAtLeast(0).toFloat()) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            indicator.animateTo(selectedIndex.toFloat(), spring(dampingRatio = 0.72f, stiffness = 420f))
        }
    }
    val outer = remember { MutableInteractionSource() }
    val press = rememberGlassPress(outer)
    val lozenge = if (colors.isDark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.07f)

    Box(
        modifier = modifier
            .glassPressScale(press, 0.03f)
            .liquidGlass(CircleShape, GlassKind.Regular, pressProgress = { press.value }),
    ) {
        // The tabs are always laid out at the bar's full width and slid so the selected
        // one is centred as the capsule shrinks to a single button — the capsule's clip
        // does the hiding, so the icons never squash. Everything that moves is read at
        // placement or in a layer, so a collapse recomposes nothing.
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(lozenge)
                )
                Row {
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == selectedIndex
                        val interaction = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    val c = collapse()
                                    alpha = if (selected || (selectedIndex < 0 && index == 0)) 1f else (1f - c * 2.2f).coerceIn(0f, 1f)
                                }
                                .pressScale(interaction, 0.9f)
                                .combinedClickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = {
                                        if (collapse() > 0.5f) onCollapsedClick() else onTabClick(tab, selected)
                                    },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) colors.accent else colors.label,
                                modifier = Modifier.size(25.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                style = LiquidTypography.tabLabel,
                                color = if (selected) colors.accent else colors.label,
                                maxLines = 1,
                                modifier = Modifier.graphicsLayer {
                                    alpha = (1f - collapse() * 2.5f).coerceIn(0f, 1f)
                                },
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val full = maxOf(expandedWidth(), width)
            val inset = 4.dp.roundToPx()
            val count = tabs.size.coerceAtLeast(1)
            val tabW = (full - inset * 2) / count
            val lozengeP = measurables[0].measure(Constraints.fixed(tabW, (height - inset * 2).coerceAtLeast(0)))
            val rowP = measurables[1].measure(Constraints.fixed(tabW * count, height))
            layout(width, height) {
                val c = collapse()
                val selected = selectedIndex.coerceAtLeast(0)
                val selectedCenter = inset + tabW * selected + tabW / 2f
                val shift = lerp(0f, width / 2f - selectedCenter, c).roundToInt()
                lozengeP.placeRelativeWithLayer(inset + (tabW * indicator.value).roundToInt() + shift, inset) {
                    alpha = if (selectedIndex < 0) 0f else (1f - c * 1.6f).coerceIn(0f, 1f)
                }
                rowP.placeRelative(inset + shift, 0)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchOrb(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    Box(
        modifier = modifier
            .glassPressScale(press)
            .liquidGlass(CircleShape, GlassKind.Regular, pressProgress = { press.value })
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.label,
            modifier = Modifier.size(27.dp),
        )
    }
}

/**
 * The now-playing accessory. Tap or swipe up to open the player; swipe sideways to change
 * track — the title follows the finger and the next one slides in behind it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerAccessory(
    onClick: () -> Unit,
    compact: () -> Float,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val colors = Liquid.colors
    val density = LocalDensity.current
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val thresholdPx = with(density) { 72.dp.toPx() }

    Box(
        modifier = modifier
            .glassPressScale(press, 0.025f)
            .liquidGlass(CircleShape, GlassKind.Regular, pressProgress = { press.value })
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -12f) onClick()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val v = drag.value
                        scope.launch {
                            if (abs(v) > thresholdPx) {
                                val direction = if (v < 0) -1f else 1f
                                drag.animateTo(direction * thresholdPx * 3f, spring(stiffness = 900f))
                                if (direction < 0) playerConnection.seekToNext() else playerConnection.seekToPrevious()
                                drag.snapTo(-direction * thresholdPx * 2f)
                            }
                            drag.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 520f))
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + amount * 0.9f) }
                }
            }
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                model = metadata?.thumbnailUrl,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .size(40.dp)
                    .playerArtworkAnchor(
                        anchor = ArtworkAnchor.Mini,
                        model = metadata?.thumbnailUrl,
                        cornerRadius = 8.dp,
                    ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp, end = 4.dp)
                    .graphicsLayer {
                        translationX = drag.value
                        alpha = (1f - abs(drag.value) / (thresholdPx * 2.4f)).coerceIn(0f, 1f)
                    },
            ) {
                Text(
                    text = metadata?.title.orEmpty(),
                    style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                    style = LiquidTypography.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = (1f - compact() * 1.4f).coerceIn(0f, 1f) },
                )
            }
            ChromeGlyphButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                onClick = { playerConnection.togglePlayPause() },
                size = 30.dp,
            )
            Box(
                Modifier.graphicsLayer {
                    val c = compact()
                    alpha = (1f - c * 1.6f).coerceIn(0f, 1f)
                }
            ) {
                ChromeGlyphButton(
                    icon = Icons.Rounded.FastForward,
                    onClick = { playerConnection.seekToNext() },
                    size = 28.dp,
                    enabled = canSkipNext,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChromeGlyphButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp,
    enabled: Boolean = true,
    tint: Color = Liquid.colors.label,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressScale(interaction, 0.8f)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(size))
    }
}

/** Chrome height to reserve at the bottom of pages, excluding the system bar. */
fun chromeReservedHeight(showTabs: Boolean, showAccessory: Boolean): Dp = when {
    showTabs && showAccessory -> TabBarHeight + ChromeGap + AccessoryHeight
    showTabs -> TabBarHeight
    showAccessory -> AccessoryHeight
    else -> 0.dp
}

@Composable
fun Dp.toPxInt(): Int = with(LocalDensity.current) { roundToPx() }

@Suppress("unused")
private val keepWidth = Modifier.width(0.dp)
