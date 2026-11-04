package com.shiny.music.ui.liquid.library

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.shiny.music.R
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.InterDisplay
import com.shiny.music.ui.liquid.InterText
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.NavBarHeight
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.cachedArtworkTones
import com.shiny.music.ui.liquid.customize.CustomizeLook
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.loadArtworkTones
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private val Slot = 68.dp
private val PeekBelow = 70.dp
private val CardHeight = 190.dp
private val CardMargin = 30.dp
private val CardRadius = 26.dp
private val Overlap = 30.dp
private val ArtSize = 128.dp
private val ArtShape = RoundedCornerShape(16.dp)
private val Rise = 150.dp
private const val Shrink = 0.06f
private const val MinScale = 0.62f
private val FieldHeight = 48.dp
private val HeaderEase = CubicBezierEasing(0.55f, 0.5f, 0f, 1f)

private val PosterTitle = TextStyle(
    fontFamily = InterDisplay,
    fontWeight = FontWeight.Bold,
    fontSize = 25.sp,
    lineHeight = 1.1.em,
    letterSpacing = (-0.03).em,
    color = Color.White,
)
private val PosterArtist = TextStyle(
    fontFamily = InterText,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 19.sp,
    letterSpacing = (-0.01).em,
    color = Color.White.copy(alpha = 0.8f),
)

class PosterRail(val sections: List<IndexSection>, val positions: Map<String, Int>)

class StackAction(val label: String, val onClick: () -> Unit, val icon: ImageVector? = null, val painter: Painter? = null)

private class StackMetrics(
    val focus: Float,
    val slot: Float,
    val peekBelow: Float,
    val card: Float,
    val overlap: Float,
    val radius: Float,
    val margin: Float,
    val rise: Float,
) {
    fun scale(d: Float): Float = (1f - Shrink * abs(d)).coerceAtLeast(MinScale)

    private val flat = (1f - MinScale) / Shrink

    private fun travelBelow(d: Float): Float {
        val e = min(d, flat)
        return peekBelow * (e - Shrink * e * e / 2f + (d - e) * MinScale)
    }

    fun top(d: Float): Float =
        if (d <= 0f) focus + d * slot else focus + card + travelBelow(d) - card * scale(d)

    fun visible(d: Float): Float {
        val full = card * scale(d)
        if (d >= 0f) return full
        if (d >= -0.5f) {
            val t = (d + 0.5f) / 0.5f
            val seam = top(0.5f) - top(-0.5f)
            return seam + (full - seam) * t
        }
        val gap = top(d + 1f) - top(d)
        val r = ((-d - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val ramp = r * r * (3f - 2f * r)
        return min(gap + overlap * ramp, full)
    }

    fun alpha(d: Float): Float = when {
        d < -3.4f -> (1f - (-d - 3.4f) / 1.1f).coerceIn(0f, 1f)
        d > 7f -> (1f - (d - 7f) / 1.5f).coerceIn(0f, 1f)
        else -> 1f
    }

    fun depth(d: Float): Float = -abs(d) - if (d > 0f) 0.001f else 0f
}

private data class CutCorners(val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val h = height.coerceIn(0f, size.height)
        val r = min(radius, h / 2f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, h, CornerRadius(r)))
    }
}

private fun Modifier.stackSlot(metrics: StackMetrics, appear: State<Float>?): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val cardWidth = (width - 2 * metrics.margin).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(cardWidth, metrics.card.roundToInt()))
    val slot = metrics.slot.roundToInt()
    layout(width, slot) {
        val y = coordinates?.positionInParent()?.y
        val x = (width - cardWidth) / 2
        if (y == null) {
            placeable.placeWithLayer(x, 0) { alpha = 0f }
            return@layout
        }
        val d = (y - metrics.focus) / metrics.slot
        placeable.placeWithLayer(x, 0, zIndex = metrics.depth(d)) {
            val a = appear?.value ?: 1f
            val s = metrics.scale(d) * (0.9f + 0.1f * a)
            transformOrigin = TransformOrigin(0.5f, 0f)
            scaleX = s
            scaleY = s
            translationY = metrics.top(d) - y + (1f - a) * metrics.rise
            alpha = metrics.alpha(d) * a
            shape = CutCorners(metrics.visible(d) / metrics.scale(d), metrics.radius)
            clip = true
        }
    }
}

private fun posterFill(tones: ArtworkTones?): Color {
    if (tones == null) return Color(0xFF2E2C2B)
    var c = lerp(tones.vivid, tones.deep, 0.4f)
    var guard = 0
    while (c.luminance() > 0.12f && guard < 10) {
        c = lerp(c, Color.Black, 0.14f)
        guard++
    }
    return c
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Poster(
    title: String,
    artist: String,
    artwork: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val tones by produceState(artwork?.let(::cachedArtworkTones), artwork) {
        if (value == null && artwork != null) value = loadArtworkTones(context, artwork)
    }
    val fill = animateColorAsState(posterFill(tones), tween(420), label = "posterFill")
    Box(
        modifier
            .drawBehind { drawRect(fill.value) }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, top = 20.dp, end = ArtSize + 30.dp),
        ) {
            BasicText(
                text = title,
                style = PosterTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = 19.sp, maxFontSize = 25.sp, stepSize = 1.sp),
            )
            if (artist.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artist,
                    style = PosterArtist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Artwork(
            model = artwork?.resize(400, 400),
            shape = ArtShape,
            hairline = false,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 30.dp, end = 15.dp)
                .size(ArtSize),
        )
    }
}

@Composable
private fun rememberStackMetrics(focus: Dp): StackMetrics {
    val density = LocalDensity.current
    return remember(density, focus) {
        with(density) {
            StackMetrics(
                focus = focus.toPx(),
                slot = Slot.roundToPx().toFloat(),
                peekBelow = PeekBelow.toPx(),
                card = CardHeight.roundToPx().toFloat(),
                overlap = Overlap.toPx(),
                radius = CardRadius.toPx(),
                margin = CardMargin.toPx(),
                rise = Rise.toPx(),
            )
        }
    }
}

private fun LazyListState.stackPosition(slot: Float): Float {
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    return first.index - first.offset / slot
}

@Composable
private fun <T> PosterStack(
    items: List<T>,
    state: LazyListState,
    metrics: StackMetrics,
    focus: Dp,
    viewport: Dp,
    keyOf: (T) -> String,
    titleOf: (T) -> String,
    artistOf: (T) -> String,
    artworkOf: (T) -> String?,
    onOpen: (Int) -> Unit,
    onMenu: (T) -> Unit,
    modifier: Modifier = Modifier,
    entrance: Boolean = false,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val born = remember { System.nanoTime() }
    LaunchedEffect(state, metrics) {
        snapshotFlow { floor(state.stackPosition(metrics.slot) + 0.5f).toInt() }
            .distinctUntilChanged()
            .filter { state.isScrollInProgress }
            .collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    }
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = focus, bottom = (viewport - focus - Slot).coerceAtLeast(0.dp)),
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        overscrollEffect = null,
        userScrollEnabled = enabled,
    ) {
        itemsIndexed(items, key = { _, item -> keyOf(item) }, contentType = { _, _ -> "poster" }) { index, item ->
            val appear = if (entrance) {
                val anim = remember {
                    val fresh = System.nanoTime() - born < 450_000_000L
                    Animatable(if (fresh) 0f else 1f)
                }
                LaunchedEffect(anim) {
                    if (anim.value < 1f) {
                        delay(index.coerceAtMost(7) * 48L)
                        anim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 240f))
                    }
                }
                anim.asState()
            } else {
                null
            }
            Poster(
                title = titleOf(item),
                artist = artistOf(item),
                artwork = artworkOf(item),
                onClick = { onOpen(index) },
                onLongClick = { onMenu(item) },
                modifier = Modifier.stackSlot(metrics, appear),
            )
        }
    }
}

@Composable
private fun AmbientWash(artworkOf: () -> String?) {
    val colors = Liquid.colors
    val context = LocalContext.current
    val artwork = artworkOf()
    val tones by produceState(artwork?.let(::cachedArtworkTones), artwork) {
        if (artwork != null) value = cachedArtworkTones(artwork) ?: loadArtworkTones(context, artwork)
    }
    val target = tones?.let { lerp(colors.background, posterFill(it), if (colors.isDark) 0.3f else 0.12f) } ?: colors.background
    val wash = animateColorAsState(target, tween(600), label = "stackWash")
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(colors.background)
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to wash.value,
                        1f to wash.value.copy(alpha = 0.6f),
                    ),
                )
            },
    )
}

@Composable
private fun StackCaption(textOf: () -> String?) {
    val text = textOf() ?: return
    Text(
        text = text,
        fontSize = 13.sp,
        color = Liquid.colors.secondaryLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StackRoundButton(action: StackAction) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(38.dp)
            .pressScale(interaction, 0.88f)
            .background(colors.gray5, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = action.onClick)
            .semantics { contentDescription = action.label },
        contentAlignment = Alignment.Center,
    ) {
        when {
            action.painter != null -> androidx.compose.foundation.Image(action.painter, null, Modifier.size(20.dp))
            action.icon != null -> Icon(action.icon, null, tint = colors.label, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
fun <T> PosterStackPage(
    title: String,
    items: List<T>,
    keyOf: (T) -> String,
    titleOf: (T) -> String,
    artistOf: (T) -> String,
    artworkOf: (T) -> String?,
    matches: (T, String) -> Boolean,
    placeholder: String,
    onOpen: (List<T>, Int) -> Unit,
    onMenu: (T) -> Unit,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: List<StackAction> = emptyList(),
    rail: PosterRail? = null,
    captionOf: ((T) -> String?)? = null,
) {
    val colors = Liquid.colors
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var fieldFocused by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(query.trim()) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) settled = "" else if (q != settled) {
            delay(200)
            settled = q
        }
    }
    val searching = fieldFocused || query.isNotEmpty()
    val searchAnim = animateFloatAsState(if (searching) 1f else 0f, tween(380, easing = HeaderEase), label = "stackSearch")

    BackHandler(enabled = searching) {
        query = ""
        focusManager.clearFocus()
    }

    val state = rememberLazyListState()
    val status = statusBarHeight()
    val topBar = if (onBack != null) NavBarHeight else 18.dp
    val titleBlock = if (subtitle != null || captionOf != null) 78.dp else 62.dp
    val headerBottom = status + topBar + titleBlock + FieldHeight + 14.dp
    val travelPx = with(density) { titleBlock.toPx() }
    val blurs = remember { HashMap<Int, RenderEffect>() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        val focus = maxOf(headerBottom + 16.dp, viewport * 0.24f)
        val metrics = rememberStackMetrics(focus)

        val focusedIndex by remember(items, metrics) {
            derivedStateOf { floor(state.stackPosition(metrics.slot) + 0.5f).toInt().coerceIn(0, (items.size - 1).coerceAtLeast(0)) }
        }
        val collapsed by remember(metrics) {
            var last = false
            derivedStateOf {
                val p = state.stackPosition(metrics.slot)
                last = when {
                    p > 0.4f -> true
                    p < 0.1f -> false
                    else -> last
                }
                last
            }
        }
        val collapseAnim = animateFloatAsState(if (collapsed) 1f else 0f, tween(620, easing = HeaderEase), label = "stackCollapse")

        AmbientWash { items.getOrNull(focusedIndex)?.let(artworkOf) }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = searchAnim.value
                    if (p <= 0f) return@graphicsLayer
                    val s = 1f - 0.04f * p
                    scaleX = s
                    scaleY = s
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && CustomizeLook.elementBlur) {
                        val step = ((p * 28f) / 4f).toInt() * 4
                        renderEffect = if (step > 0) {
                            blurs.getOrPut(step) {
                                val px = with(density) { step.dp.toPx() }
                                android.graphics.RenderEffect
                                    .createBlurEffect(px, px, android.graphics.Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            }
                        } else {
                            null
                        }
                    }
                },
        ) {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.liquid_library_empty_title),
                    color = colors.secondaryLabel,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = focus + 24.dp),
                )
            }
            PosterStack(
                items = items,
                state = state,
                metrics = metrics,
                focus = focus,
                viewport = viewport,
                keyOf = keyOf,
                titleOf = titleOf,
                artistOf = artistOf,
                artworkOf = artworkOf,
                onOpen = { onOpen(items, it) },
                onMenu = onMenu,
                enabled = !searching,
            )
        }

        if (searching || searchAnim.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(colors.background.copy(alpha = 0.5f * searchAnim.value)) }
                    .then(
                        if (searching) {
                            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (query.isEmpty()) focusManager.clearFocus()
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        AnimatedContent(
            targetState = settled,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(180)) },
            label = "stackResults",
            modifier = Modifier.fillMaxSize(),
        ) { q ->
            if (q.isNotEmpty()) {
                val results = remember(items, q) { items.filter { matches(it, q) } }
                val resultsState = rememberLazyListState()
                LaunchedEffect(resultsState) {
                    snapshotFlow { resultsState.isScrollInProgress }.filter { it }.collect { focusManager.clearFocus() }
                }
                Box(Modifier.fillMaxSize()) {
                    if (results.isEmpty()) {
                        Text(
                            text = stringResource(R.string.library_no_results, q),
                            color = colors.secondaryLabel,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = focus + 24.dp, start = PageMargin, end = PageMargin),
                        )
                    }
                    PosterStack(
                        items = results,
                        state = resultsState,
                        metrics = metrics,
                        focus = focus,
                        viewport = viewport,
                        keyOf = keyOf,
                        titleOf = titleOf,
                        artistOf = artistOf,
                        artworkOf = artworkOf,
                        onOpen = { index ->
                            focusManager.clearFocus()
                            onOpen(results, index)
                        },
                        onMenu = onMenu,
                        entrance = true,
                    )
                }
            } else {
                Box(Modifier)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(headerBottom + 40.dp)
                .drawBehind {
                    val end = size.height - collapseAnim.value * travelPx
                    val knee = ((end - 44.dp.toPx()) / end).coerceIn(0.1f, 0.95f)
                    drawRect(
                        Brush.verticalGradient(
                            0f to colors.background,
                            knee * 0.6f to colors.background.copy(alpha = 0.94f),
                            knee to colors.background.copy(alpha = 0.72f),
                            1f to Color.Transparent,
                            endY = end,
                        ),
                    )
                },
        )

        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val h = (headerBottom.toPx() - travelPx * maxOf(collapseAnim.value, searchAnim.value)).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                    layout(constraints.maxWidth, h) { placeable.place(0, 0) }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )

        Box(
            Modifier
                .padding(top = status + topBar)
                .height(titleBlock)
                .fillMaxWidth()
                .padding(start = PageMargin, end = PageMargin)
                .graphicsLayer {
                    val p = maxOf(collapseAnim.value, searchAnim.value)
                    alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                    translationY = -p * travelPx
                    val s = 1f - p * 0.18f
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = title,
                        style = LiquidTypography.largeTitle,
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StackCaption { items.getOrNull(focusedIndex)?.let { captionOf?.invoke(it) } ?: subtitle }
                }
                if (onBack == null && actions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions.forEach { StackRoundButton(it) } }
                }
            }
        }

        Box(
            Modifier
                .padding(top = status + topBar + titleBlock, start = PageMargin, end = PageMargin)
                .fillMaxWidth()
                .graphicsLayer { translationY = -maxOf(collapseAnim.value, searchAnim.value) * travelPx },
        ) {
            LibrarySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = placeholder,
                onFocusChange = { fieldFocused = it },
            )
        }

        if (onBack != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = status)
                    .height(NavBarHeight)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidBackButton(onClick = onBack)
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { alpha = 1f - searchAnim.value },
                ) { actions.forEach { StackRoundButton(it) } }
            }
        }

        if (rail != null && !searching) {
            LibraryIndexRail(
                sections = rail.sections,
                onSelect = { key -> rail.positions[key]?.let { index -> scope.launch { state.scrollToItem(index) } } },
                topInset = headerBottom,
                bottomInset = liquidBottomPadding(),
            )
        }
    }
}
