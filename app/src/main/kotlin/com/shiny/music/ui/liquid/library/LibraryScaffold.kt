package com.shiny.music.ui.liquid.library

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidBackdrop
import com.shiny.music.ui.liquid.NavBarHeight
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.ScrollEdgeEffect
import com.shiny.music.ui.liquid.ScrollToTopEffect
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.rememberLiquidBackdrop
import com.shiny.music.ui.liquid.statusBarHeight

private val CollapseEase = CubicBezierEasing(0.55f, 0.5f, 0f, 1f)
private val FieldHeight = 48.dp
private val AvatarSize = 38.dp

// latches instead of following the scroll 1:1, so the header always finishes its move even if you stop halfway
@Composable
fun rememberHeaderCollapse(state: LazyListState): State<Float> {
    val density = LocalDensity.current
    val enterPx = with(density) { 24.dp.toPx() }
    val exitPx = with(density) { 6.dp.toPx() }
    val collapsed by remember(state, enterPx, exitPx) {
        var last = false
        derivedStateOf {
            val offset = state.firstVisibleItemScrollOffset
            last = when {
                state.firstVisibleItemIndex > 0 -> true
                offset > enterPx -> true
                offset < exitPx -> false
                else -> last
            }
            last
        }
    }
    return animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(820, easing = CollapseEase),
        label = "libraryCollapse",
    )
}

@Composable
fun LibraryScaffold(
    title: String,
    state: LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationButton: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    avatar: (@Composable () -> Unit)? = null,
    scrollToTopSignal: NavController? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val colors = Liquid.colors
    val density = LocalDensity.current
    val background = colors.background
    val backdrop = rememberLiquidBackdrop(background)
    val status = statusBarHeight()
    val topBar = if (navigationButton != null) NavBarHeight else 18.dp
    val titleBlock = if (subtitle != null) 78.dp else 62.dp
    val headerHeight = status + topBar + titleBlock + FieldHeight + 28.dp
    val collapsedHeight = headerHeight - titleBlock
    val travelPx = with(density) { titleBlock.toPx() }
    val avatarReservePx = with(density) { (AvatarSize + 12.dp).toPx() }
    val avatarDropPx = with(density) { (FieldHeight / 2 - titleBlock / 2).toPx() }
    val collapse = rememberHeaderCollapse(state)
    val progress = { collapse.value.coerceIn(0f, 1f) }
    val blurs = remember { HashMap<Int, RenderEffect>() }
    val focus = LocalFocusManager.current

    if (scrollToTopSignal != null) ScrollToTopEffect(scrollToTopSignal, state)
    // back clears the search first, only then leaves the page
    BackHandler(enabled = query.isNotEmpty()) {
        onQueryChange("")
        focus.clearFocus()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(background),
    ) {
        CompositionLocalProvider(LocalLiquidBackdrop provides null) {
            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = headerHeight, bottom = liquidBottomPadding()),
                content = content,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(collapsedHeight + 14.dp),
        ) {
            ScrollEdgeEffect(
                backdrop = backdrop,
                background = background,
                modifier = Modifier.fillMaxSize(),
                alpha = progress,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val p = progress()
                        if (p <= 0f) return@drawBehind
                        drawRect(
                            Brush.verticalGradient(
                                0f to background.copy(alpha = 0.94f * p),
                                0.5f to background.copy(alpha = 0.72f * p),
                                0.82f to background.copy(alpha = 0.3f * p),
                                1f to background.copy(alpha = 0f),
                            ),
                        )
                    },
            )
        }

        Box(
            Modifier
                .padding(top = status + topBar)
                .height(titleBlock)
                .fillMaxWidth()
                .padding(start = PageMargin, end = PageMargin + if (avatar != null) AvatarSize + 8.dp else 0.dp)
                .graphicsLayer {
                    val p = progress()
                    alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                    translationY = -p * travelPx
                    val scale = 1f - p * 0.18f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    // blur goes up in steps and each step is cached, a new RenderEffect every frame was wasteful
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val step = ((p * 14f) / 3f).toInt() * 3
                        renderEffect = if (step > 0) {
                            blurs.getOrPut(step) {
                                android.graphics.RenderEffect
                                    .createBlurEffect(step.toFloat(), step.toFloat(), android.graphics.Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            }
                        } else {
                            null
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = LiquidTypography.largeTitle,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Box(
            Modifier
                .padding(top = status + topBar + titleBlock, start = PageMargin, end = PageMargin)
                .fillMaxWidth()
                .graphicsLayer { translationY = -progress() * travelPx }
                // the field gives up room on the right while the avatar drops in next to it
                .layout { measurable, constraints ->
                    val reserve = if (avatar != null) (avatarReservePx * progress()).toInt() else 0
                    val width = (constraints.maxWidth - reserve).coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                },
        ) {
            LibrarySearchField(value = query, onValueChange = onQueryChange, placeholder = placeholder)
        }

        if (avatar != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = status + topBar, end = PageMargin)
                    .height(titleBlock)
                    .graphicsLayer { translationY = progress() * avatarDropPx },
                contentAlignment = Alignment.Center,
            ) {
                avatar()
            }
        }

        if (navigationButton != null) {
            CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = status)
                        .height(NavBarHeight)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    navigationButton()
                    Spacer(Modifier.weight(1f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }

        overlay()
    }
}

@Composable
fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    val palette = libraryPalette()
    val focus = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .clip(CircleShape)
            .background(palette.field)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = palette.placeholder, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.label, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(
            visible = value.isNotEmpty(),
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.6f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Cancel, null, tint = palette.placeholder, modifier = Modifier.size(18.dp))
            }
        }
    }
}
