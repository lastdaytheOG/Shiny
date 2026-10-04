package com.shiny.music.ui.liquid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.shiny.music.ui.component.backdrop.Backdrop
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop

/** Height of the navigation bar row that floats under the status bar. */
val NavBarHeight = 52.dp

@Composable
fun statusBarHeight(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * A tab root in the iOS idiom: a large bold title that scrolls with the content, a small
 * centred title that takes over once it has gone, and glass buttons floating above a
 * progressive blur instead of a bar with a background.
 *
 * Glass inside [content] never samples the page's own backdrop (it cannot refract
 * something that contains itself), so the backdrop is provided to the bar only.
 */
@Composable
fun LargeTitlePage(
    title: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    background: Color = Liquid.colors.background,
    navigationButton: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleAccessory: (@Composable RowScope.() -> Unit)? = null,
    scrollToTopSignal: NavController? = null,
    bottomPadding: Dp = liquidBottomPadding(),
    content: LazyListScope.() -> Unit,
) {
    val colors = Liquid.colors
    val density = LocalDensity.current
    val topInset = statusBarHeight()
    val backdrop = rememberLiquidBackdrop(background)
    val collapseDistancePx = with(density) { 34.dp.toPx() }

    if (scrollToTopSignal != null) {
        ScrollToTopEffect(scrollToTopSignal, state)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(background)
    ) {
        CompositionLocalProvider(LocalLiquidBackdrop provides null) {
            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = topInset + NavBarHeight - 6.dp, bottom = bottomPadding),
            ) {
                item(key = "liquid_large_title", contentType = "large_title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = PageMargin, end = PageMargin - 4.dp, bottom = 4.dp)
                            .graphicsLayer {
                                val offset = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else collapseDistancePx * 2
                                alpha = (1f - (offset - collapseDistancePx * 0.4f) / collapseDistancePx).coerceIn(0f, 1f)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = LiquidTypography.largeTitle,
                            color = colors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        titleAccessory?.invoke(this)
                    }
                }
                content()
            }
        }

        ScrollEdgeEffect(
            backdrop = backdrop,
            background = background,
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + NavBarHeight + 18.dp),
            alpha = {
                if (state.firstVisibleItemIndex > 0) 1f
                else (state.firstVisibleItemScrollOffset / (collapseDistancePx * 0.6f)).coerceIn(0f, 1f)
            },
        )

        CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .height(NavBarHeight)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
                    navigationButton?.invoke()
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = title,
                        style = LiquidTypography.headline,
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            val offset = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else collapseDistancePx * 2
                            alpha = ((offset - collapseDistancePx * 0.7f) / (collapseDistancePx * 0.6f)).coerceIn(0f, 1f)
                        },
                    )
                }
                Row(
                    modifier = Modifier.width(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/**
 * The floating bar of a pushed page (album, artist, playlist): a glass back button, glass
 * actions, and a title that fades in over a progressive blur once the page's own header
 * has scrolled away.
 */
@Composable
fun BoxScope.DetailTopBar(
    backdrop: Backdrop?,
    title: String,
    onBack: () -> Unit,
    titleAlpha: () -> Float,
    modifier: Modifier = Modifier,
    edgeBackdrop: com.shiny.music.ui.component.backdrop.backdrops.LayerBackdrop? = null,
    edgeBackground: Color = Liquid.colors.background,
    contentColor: Color = Liquid.colors.label,
    onBackLongClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val topInset = statusBarHeight()
    if (edgeBackdrop != null) {
        ScrollEdgeEffect(
            backdrop = edgeBackdrop,
            background = edgeBackground,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topInset + NavBarHeight + 18.dp),
            alpha = titleAlpha,
        )
    }
    CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
        Row(
            modifier = modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topInset)
                .height(NavBarHeight)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(104.dp), contentAlignment = Alignment.CenterStart) {
                GlassIconButton(
                    icon = Icons.Rounded.ChevronLeft,
                    onClick = onBack,
                    onLongClick = onBackLongClick,
                    iconSize = 28.dp,
                    tint = contentColor,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = title,
                    style = LiquidTypography.headline,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = titleAlpha() },
                )
            }
            Row(
                modifier = Modifier.width(104.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** Scrolls [state] to the top when the current tab is re-selected in the tab bar. */
@Composable
fun ScrollToTopEffect(navController: NavController, state: LazyListState) {
    val entry by navController.currentBackStackEntryAsState()
    val flow = entry?.savedStateHandle?.getStateFlow("scrollToTop", false)
    val signal = flow?.collectAsState()
    LaunchedEffect(signal?.value) {
        if (signal?.value == true) {
            state.animateScrollToItem(0)
            entry?.savedStateHandle?.set("scrollToTop", false)
        }
    }
}

/** A glass back button for pages that draw their own bars. */
@Composable
fun LiquidBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassIconButton(icon = Icons.Rounded.ChevronLeft, onClick = onClick, iconSize = 28.dp, modifier = modifier)
}

/** Handles the system back press for an in-page mode (e.g. a search overlay). */
@Composable
fun ModeBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
fun VerticalSpace(height: Dp) = Spacer(Modifier.height(height))
