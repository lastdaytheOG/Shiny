package com.shiny.music.ui.liquid.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.theme.pressScale
import kotlinx.coroutines.launch

@Immutable
class LibraryPalette(
    val tile: Color,
    val tileHigh: Color,
    val track: Color,
    val chip: Color,
    val thumb: Color,
    val field: Color,
    val rule: Color,
    val count: Color,
    val placeholder: Color,
)

@Composable
fun libraryPalette(): LibraryPalette {
    val colors = Liquid.colors
    return remember(colors) {
        if (colors.isDark) {
            LibraryPalette(
                tile = Color(0xFF121214),
                tileHigh = Color(0xFF1C1C1F),
                track = Color(0xFF161618),
                chip = Color(0xFF232326),
                thumb = Color(0xFF3D3D41),
                field = Color(0xFF141416),
                rule = Color(0xFF232326),
                count = Color(0xFF9A9AA0),
                placeholder = Color(0xFF77777D),
            )
        } else {
            LibraryPalette(
                tile = Color(0xFFF2F2F7),
                tileHigh = Color(0xFFE5E5EA),
                track = Color(0xFFEFEFF4),
                chip = Color(0xFFE3E3E8),
                thumb = Color(0xFFFFFFFF),
                field = Color(0xFFEFEFF4),
                rule = Color(0xFFE5E5EA),
                count = Color(0xFF8A8A8F),
                placeholder = Color(0xFF9A9AA0),
            )
        }
    }
}

private val CountStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 36.sp,
    letterSpacing = (-1).sp,
)

private val CardTitleStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 19.sp,
    letterSpacing = (-0.2).sp,
)

// cards fade and rise in one after another when the tab changes. draw phase only, nothing recomposes
fun Modifier.libraryReveal(progress: () -> Float, order: Int, rise: Dp = 18.dp): Modifier = graphicsLayer {
    val t = ((progress() - order * 0.07f) / 0.55f).coerceIn(0f, 1f)
    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
    alpha = eased
    translationY = (1f - eased) * rise.toPx()
}

@Composable
fun LibraryRule(modifier: Modifier = Modifier) {
    val palette = libraryPalette()
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin)
            .height(1.dp)
            .background(palette.rule),
    )
}

@Composable
fun LibrarySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LiquidTypography.title3.copy(fontWeight = FontWeight.Bold),
        color = Liquid.colors.label,
        modifier = modifier.padding(horizontal = PageMargin),
    )
}

@Composable
fun LibraryCapsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Liquid.colors.label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(horizontal = PageMargin),
    )
}

@Composable
private fun LibraryCount(count: Int) {
    val palette = libraryPalette()
    // numbers roll up or down when they change instead of just swapping
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(spring(0.85f, Spring.StiffnessMediumLow)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(180))) togetherWith
                (slideOutVertically(spring(0.85f, Spring.StiffnessMediumLow)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(120))) using
                SizeTransform(clip = false)
        },
        label = "libraryCount",
    ) { value ->
        Text(text = value.toString(), style = CountStyle, color = palette.count, maxLines = 1)
    }
}

@Composable
private fun CoverTile(url: String?, round: Boolean, corner: Dp, modifier: Modifier) {
    val palette = libraryPalette()
    val shape = if (round) CircleShape else RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(palette.tileHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            Artwork(model = url, shape = shape, hairline = false, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = palette.placeholder, modifier = Modifier.fillMaxSize(0.4f))
        }
    }
}

@Composable
private fun CoverStack(
    covers: List<String>,
    tile: Dp,
    round: Boolean,
    modifier: Modifier = Modifier,
    front: (@Composable (Modifier) -> Unit)? = null,
) {
    val shown = remember(covers) { covers.take(2) }
    if (shown.isEmpty() && front == null) return
    val back = if (front != null) shown.firstOrNull() else shown.getOrNull(1)
    val corner = tile * 0.22f
    Box(modifier.size(width = tile * 1.55f, height = tile)) {
        // back cover first so the front one sits on top of it
        if (back != null) {
            CoverTile(
                url = back,
                round = round,
                corner = corner,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(tile)
                    .graphicsLayer {
                        alpha = 0.55f
                        rotationZ = if (round) 0f else -9f
                    },
            )
        }
        val frontModifier = Modifier
            .align(Alignment.CenterEnd)
            .size(tile)
            .graphicsLayer { rotationZ = if (round) 0f else 7f }
        if (front != null) {
            front(frontModifier.clip(RoundedCornerShape(corner)))
        } else {
            CoverTile(url = shown[0], round = round, corner = corner, modifier = frontModifier)
        }
    }
}

@Composable
fun LikedArt(modifier: Modifier) {
    val accent = Liquid.colors.accent
    Box(
        modifier.background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.46f))
    }
}

@Composable
private fun CardSurface(
    onClick: () -> Unit,
    height: Dp,
    corner: Dp,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val palette = libraryPalette()
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    @OptIn(ExperimentalFoundationApi::class)
    Box(
        modifier
            .height(height)
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(corner))
            .background(palette.tile)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            ),
        content = content,
    )
}

@Composable
fun LibraryCategoryCard(
    title: String,
    count: Int,
    covers: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
) {
    // the covers hang off the corner on purpose, the card's clip cuts them
    CardSurface(onClick = onClick, height = 78.dp, corner = 16.dp, modifier = modifier) {
        CoverStack(
            covers = covers,
            tile = 46.dp,
            round = round,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 12.dp),
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 11.dp, end = 12.dp),
        ) {
            Text(title, style = CardTitleStyle, color = Liquid.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LibraryCount(count)
        }
    }
}

@Composable
fun LibraryShortcutCard(
    title: String,
    caption: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    CardSurface(onClick = onClick, height = 78.dp, corner = 16.dp, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 10.dp)
                .size(54.dp)
                .graphicsLayer { rotationZ = -10f },
        )
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp, end = 52.dp),
        ) {
            Text(title, style = CardTitleStyle, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(caption, style = LiquidTypography.footnote, color = colors.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LibraryWideCard(
    title: String,
    count: Int,
    covers: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    front: (@Composable (Modifier) -> Unit)? = null,
) {
    CardSurface(onClick = onClick, height = 104.dp, corner = 18.dp, modifier = modifier.fillMaxWidth()) {
        CoverStack(
            covers = covers,
            tile = 60.dp,
            round = false,
            front = front,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 14.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp, end = 110.dp),
        ) {
            Text(title, style = CardTitleStyle, color = Liquid.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LibraryCount(count)
        }
    }
}

@Composable
fun LibraryHeroCard(
    title: String,
    count: Int?,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null,
) {
    val colors = Liquid.colors
    val palette = libraryPalette()
    CardSurface(onClick = onClick, height = if (count != null) 128.dp else 112.dp, corner = 20.dp, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = colors.label,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                letterSpacing = (-0.3).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                if (count != null) {
                    Text(
                        text = count.toString(),
                        color = palette.count,
                        fontSize = 44.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Thin,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = caption,
                    color = palette.count,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (count != null) 7.dp else 0.dp),
                )
                if (action != null) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(colors.accent)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(action, color = colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryPinnedTile(
    thumbnail: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = libraryPalette()
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    @OptIn(ExperimentalFoundationApi::class)
    Box(
        modifier
            .aspectRatio(1f)
            .pressScale(interaction, 0.95f)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.tileHigh)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Artwork(model = thumbnail, shape = RoundedCornerShape(18.dp), hairline = false, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = palette.placeholder, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun LibraryCardGrid(
    count: Int,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    spacing: Dp = 10.dp,
    cell: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        (0 until count).chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { index -> cell(index, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun LibraryManagePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = libraryPalette()
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .pressScale(interaction, 0.94f)
                .clip(CircleShape)
                .background(palette.chip)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .heightIn(min = 32.dp)
                .padding(horizontal = 18.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = Liquid.colors.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LibraryChips(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = libraryPalette()
    val colors = Liquid.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val starts = remember(labels.size) { FloatArray(labels.size) }
    val widths = remember(labels.size) { FloatArray(labels.size) }
    var measured by remember(labels.size) { mutableStateOf(false) }
    val thumbX = remember { Animatable(0f) }
    val thumbW = remember { Animatable(0f) }
    val stretch = remember { Animatable(0f) }
    val current by rememberUpdatedState(selected)

    // first time the pill just sits under the selected tab, after that it slides over and squashes a little
    LaunchedEffect(selected, measured) {
        if (!measured) return@LaunchedEffect
        val x = starts[selected]
        val w = widths[selected]
        if (thumbW.value == 0f) {
            thumbX.snapTo(x)
            thumbW.snapTo(w)
            return@LaunchedEffect
        }
        val motion = spring<Float>(dampingRatio = 0.78f, stiffness = 420f)
        launch { thumbX.animateTo(x, motion) }
        launch { thumbW.animateTo(w, motion) }
        launch {
            stretch.snapTo(1f)
            stretch.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
        }
    }

    Row(
        modifier
            .clip(CircleShape)
            .background(palette.track)
            .drawBehind {
                val h = size.height - 6.dp.toPx()
                val top = 3.dp.toPx()
                val r = CornerRadius(h / 2f)
                for (i in starts.indices) {
                    if (widths[i] > 0f) {
                        drawRoundRect(palette.chip, Offset(starts[i], top), Size(widths[i], h), r)
                    }
                }
                if (thumbW.value > 0f) {
                    val squash = stretch.value * 0.06f
                    val w = thumbW.value * (1f + squash)
                    val x = thumbX.value - (w - thumbW.value) / 2f
                    drawRoundRect(palette.thumb, Offset(x, top), Size(w, h), r)
                }
            }
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            val text by animateColorAsState(
                targetValue = if (isSelected) colors.label else colors.label.copy(alpha = 0.62f),
                animationSpec = tween(220),
                label = "chipText",
            )
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .onPlaced { coords ->
                        starts[index] = coords.positionInParent().x + with(density) { 3.dp.toPx() }
                        widths[index] = coords.size.width.toFloat()
                        if (starts.indices.all { widths[it] > 0f } && !measured) measured = true
                    }
                    .clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Tab) {
                        if (index != current) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        } else {
                            scope.launch {
                                stretch.snapTo(0.6f)
                                stretch.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 380f))
                            }
                        }
                    }
                    .heightIn(min = 30.dp)
                    .padding(horizontal = 15.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun LibraryPlayBar(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Liquid.colors
    val palette = libraryPalette()
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val playInteraction = remember { MutableInteractionSource() }
    val shuffleInteraction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .pressScale(playInteraction, 0.95f)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(interactionSource = playInteraction, indication = null, enabled = enabled, role = Role.Button, onClick = onPlay)
                .height(46.dp)
                .padding(start = 18.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = colors.onAccent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(com.shiny.music.R.string.liquid_play),
                color = colors.onAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(46.dp)
                .pressScale(shuffleInteraction, 0.9f)
                .clip(CircleShape)
                .background(palette.tileHigh)
                .clickable(interactionSource = shuffleInteraction, indication = null, enabled = enabled, role = Role.Button) {
                    scope.launch {
                        spin.snapTo(0f)
                        spin.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
                    }
                    onShuffle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = androidx.compose.ui.res.stringResource(com.shiny.music.R.string.liquid_shuffle),
                tint = colors.label,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationY = spin.value * 180f },
            )
        }
    }
}

@Composable
fun LibraryEmptyLine(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = libraryPalette().count,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
