package com.shiny.music.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier.Node
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.shiny.music.ui.utils.resize
import com.shiny.music.ui.component.backdrop.Backdrop
import com.shiny.music.ui.theme.pressScale
import kotlinx.coroutines.launch
import kotlin.math.floor

/** iOS page margin: large titles, section headers and the first tile all sit on it. */
val PageMargin = 20.dp

/** Gap between tiles on a shelf. */
val ShelfGap = 12.dp

/**
 * Height of the floating chrome at the bottom of the window — tab bar, mini player and
 * the system navigation bar — so scrolling content can end above it.
 */
val LocalLiquidBottomInset = compositionLocalOf { 0.dp }

/** Bottom content padding for a page: clears the floating chrome with room to breathe. */
@Composable
fun liquidBottomPadding(extra: Dp = 24.dp): Dp = LocalLiquidBottomInset.current + extra

/**
 * The iOS table-cell highlight: a flat fill that appears on touch-down and fades on
 * release. No ripple — a ripple is the single most Android thing a list can do.
 */
class LiquidHighlight(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        HighlightNode(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is LiquidHighlight && other.color == color
    override fun hashCode(): Int = color.hashCode()
}

private class HighlightNode(
    private val interactionSource: InteractionSource,
    private val color: Color,
) : Node(), DrawModifierNode {
    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> launch { alpha.snapTo(1f); invalidateDraw() }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                        alpha.animateTo(0f, tween(durationMillis = 280)) { invalidateDraw() }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val a = alpha.value
        if (a > 0f) drawRect(color.copy(alpha = color.alpha * a))
        drawContent()
    }
}

@Composable
fun rememberRowHighlight(): LiquidHighlight {
    val color = Liquid.colors.fill
    return remember(color) { LiquidHighlight(color) }
}

/** Corner radius for artwork of a given edge length — covers stay crisp, never card-like. */
fun artworkRadius(size: Dp): Dp = (size.value * 0.055f).coerceIn(4f, 14f).dp

@Composable
fun Artwork(
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    placeholder: ImageVector = Icons.Rounded.MusicNote,
    contentDescription: String? = null,
    hairline: Boolean = true,
    dissolve: Boolean = false,
) {
    val colors = Liquid.colors
    val context = LocalContext.current

    // YouTube serves `maxresdefault` only for videos that have one, and 404s for the rest.
    // Asking for it is how full-screen artwork gets a 1280px still instead of a 480px one
    // (see `String.resize`), so the miss is handled rather than avoided: on failure the
    // same image is re-requested at the largest size that always exists. Held in state
    // keyed on `model` so a track change starts again from the sharp variant.
    var resolved by remember(model) { mutableStateOf(model) }
    // With [dissolve], a change of picture in this same place eases from the picture it
    // replaces rather than from nothing: the one just shown is still in memory, and stands in
    // as the placeholder the new one fades up over. Off everywhere a slot is reused for a
    // different thing (a list row), where the old picture would be the wrong one to show.
    val shown = remember { arrayOfNulls<coil3.memory.MemoryCache.Key>(1) }
    val request = remember(resolved) {
        val previous = if (dissolve) shown[0] else null
        if (previous != null) {
            ImageRequest.Builder(context).data(resolved).placeholderMemoryCacheKey(previous).crossfade(420).build()
        } else {
            ImageRequest.Builder(context).data(resolved).crossfade(220).build()
        }
    }
    // Covers on the first screen of a process are noted for the next cold start (see CoverWarmup).
    val warmupNote = remember(resolved) {
        (resolved as? String)?.takeIf { CoverWarmup.isRecording() }?.let(CoverWarmup::Note)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (colors.isDark) colors.gray5 else colors.gray5),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = placeholder,
            contentDescription = null,
            tint = colors.gray,
            modifier = Modifier.fillMaxSize(0.36f),
        )
        if (resolved != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (warmupNote == null) Modifier else Modifier.onGloballyPositioned { c ->
                            // Clipped to the scrolling viewports above it: empty when out of view.
                            if (warmupNote.done || c.boundsInWindow().isEmpty) return@onGloballyPositioned
                            warmupNote.width = c.size.width
                            warmupNote.height = c.size.height
                            warmupNote.offer()
                        }
                    ),
                onSuccess = {
                    shown[0] = it.result.memoryCacheKey
                    warmupNote?.loaded = true
                    warmupNote?.offer()
                },
                onError = {
                    val url = resolved as? String
                    if (url != null && url.contains("maxresdefault.jpg")) {
                        resolved = url.replace("maxresdefault.jpg", "hqdefault.jpg")
                    }
                },
            )
        }
        if (hairline) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(0.5.dp, colors.label.copy(alpha = 0.09f), shape)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    kind: GlassKind = GlassKind.Regular,
    tint: Color = Liquid.colors.label,
    contentDescription: String? = null,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    Box(
        modifier = modifier
            .size(size)
            .glassPressScale(press)
            .liquidGlass(CircleShape, kind, backdrop = backdrop, pressProgress = { press.value })
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** A capsule of Liquid Glass holding arbitrary content — toolbars, grouped buttons. */
@Composable
fun GlassCapsule(
    modifier: Modifier = Modifier,
    kind: GlassKind = GlassKind.Regular,
    shape: CornerBasedShape = CircleShape,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.glassPressScale(press, 0.04f) else Modifier)
            .liquidGlass(shape, kind, backdrop = backdrop, pressProgress = { press.value })
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

enum class ButtonTone { Tinted, Filled, Glass }

/**
 * The Music app's paired "Play" / "Shuffle" buttons: a capsule of grey fill with the
 * label in the tint colour ([ButtonTone.Tinted]), or a solid tint ([ButtonTone.Filled]).
 */
@Composable
fun LiquidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ButtonTone = ButtonTone.Tinted,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val container = when {
        containerColor != Color.Unspecified -> containerColor
        tone == ButtonTone.Filled -> colors.accent
        tone == ButtonTone.Tinted -> colors.tertiaryFill
        else -> Color.Transparent
    }
    val content = when {
        contentColor != Color.Unspecified -> contentColor
        tone == ButtonTone.Filled -> colors.onAccent
        tone == ButtonTone.Tinted -> colors.accent
        else -> colors.label
    }
    val press = rememberGlassPress(interaction)
    Row(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                val s = 1f - 0.03f * press.value
                scaleX = s
                scaleY = s
            }
            .then(
                if (tone == ButtonTone.Glass) {
                    Modifier.liquidGlass(CircleShape, GlassKind.Regular, backdrop = backdrop, pressProgress = { press.value })
                } else {
                    Modifier
                        .clip(CircleShape)
                        .background(container)
                }
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = if (tone == ButtonTone.Glass) null else rememberRowHighlight(),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = LiquidTypography.headline,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Recently Played ›" — a shelf heading, tappable when the shelf has a "see all". */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    color: Color = Liquid.colors.label,
    secondaryColor: Color = Liquid.colors.secondaryLabel,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PageMargin, end = PageMargin - 4.dp, top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onClick != null) {
                        Modifier
                            .pressScale(interaction, 0.98f)
                            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        ) {
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = LiquidTypography.footnote,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = LiquidTypography.title2,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = secondaryColor,
                        modifier = Modifier
                            .padding(start = 1.dp)
                            .size(26.dp),
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
    color: Color = Liquid.colors.separator,
) {
    val px = with(LocalDensity.current) { 1f / density }
    Box(
        modifier
            .padding(start = startIndent)
            .fillMaxWidth()
            .height(px.dp.coerceAtLeast(0.33.dp))
            .background(color)
    )
}

@Composable
fun ExplicitBadge(modifier: Modifier = Modifier, color: Color = Liquid.colors.secondaryLabel) {
    Box(
        modifier = modifier
            .size(15.dp)
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "E",
            style = TextStyle(fontFamily = InterCaption, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 10.sp),
            color = color,
        )
    }
}

/**
 * A cover with one quiet line of title and one of subtitle underneath — the unit every
 * shelf is built from.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    title: String,
    subtitle: String?,
    artwork: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 164.dp,
    circular: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    titleColor: Color = Liquid.colors.label,
    subtitleColor: Color = Liquid.colors.secondaryLabel,
    placeholder: ImageVector = Icons.Rounded.MusicNote,
    /** The song this tile plays, so touching it starts the stream early; null for anything else. */
    songId: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width))
            .prewarmOnTouch(songId)
            .pressScale(interaction, 0.965f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Artwork(
                model = artwork,
                shape = if (circular) CircleShape else RoundedCornerShape(if (width == Dp.Unspecified) 8.dp else artworkRadius(width)),
                placeholder = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
            badge?.invoke(this)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            style = if (circular) LiquidTypography.footnote.copy(fontWeight = FontWeight.Medium) else LiquidTypography.footnote.copy(fontWeight = FontWeight.Medium),
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circular) TextAlign.Center else TextAlign.Start,
        )
        Text(
            text = subtitle.orEmpty(),
            style = LiquidTypography.footnote,
            color = subtitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circular) TextAlign.Center else TextAlign.Start,
        )
    }
}

/**
 * A song in a list: artwork (or a track number), title over subtitle, and an ellipsis.
 * The separator starts under the text, as in iOS, never under the artwork.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    title: String,
    subtitle: String?,
    artwork: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    explicit: Boolean = false,
    trackNumber: Int? = null,
    showSeparator: Boolean = true,
    artworkSize: Dp = 50.dp,
    circularArtwork: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    titleColor: Color = Liquid.colors.label,
    subtitleColor: Color = Liquid.colors.secondaryLabel,
    separatorColor: Color = Liquid.colors.separator,
    accent: Color = Liquid.colors.accent,
    startPadding: Dp = PageMargin,
    leadingRank: Int? = null,
    /** Overrides the rank's type — the charts set it in the display cut. */
    leadingRankStyle: TextStyle? = null,
    /** Overrides the rank's colour; unspecified follows [titleColor]. */
    leadingRankColor: Color = Color.Unspecified,
    /** The song this row plays, so touching it starts the stream early; null for anything else. */
    songId: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val showArtwork = trackNumber == null
    val leadingWidth = if (showArtwork) artworkSize else 28.dp
    // A YouTube Music cover at the row's own size rather than the 500 px a tile gets. Only
    // googleusercontent covers: a small i.ytimg still is a letterboxed 4:3 frame.
    val artworkPx = with(LocalDensity.current) { artworkSize.roundToPx() }
    val rowArtwork = remember(artwork, artworkPx) {
        (artwork as? String)?.takeIf { "googleusercontent.com" in it }?.resize(artworkPx, artworkPx) ?: artwork
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.prewarmOnTouch(songId) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showArtwork) artworkSize + 14.dp else 50.dp)
                .padding(start = startPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingRank != null) {
                Text(
                    text = leadingRank.toString(),
                    style = leadingRankStyle ?: LiquidTypography.headline,
                    color = if (leadingRankColor.isSpecified) leadingRankColor else titleColor,
                    maxLines = 1,
                    // A right-aligned rank would otherwise sit against the cover; the
                    // default left-aligned one already has the whole box to breathe in.
                    modifier = Modifier
                        .width(30.dp)
                        .padding(end = if (leadingRankStyle != null) 8.dp else 0.dp),
                )
            }
            if (showArtwork) {
                Box(Modifier.size(artworkSize)) {
                    Artwork(
                        model = rowArtwork,
                        shape = if (circularArtwork) CircleShape else RoundedCornerShape(artworkRadius(artworkSize).coerceAtLeast(5.dp)),
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (isActive) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(if (circularArtwork) CircleShape else RoundedCornerShape(artworkRadius(artworkSize).coerceAtLeast(5.dp)))
                                .background(Color.Black.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            NowPlayingBars(playing = isPlaying, color = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Box(Modifier.width(leadingWidth), contentAlignment = Alignment.CenterStart) {
                    if (isActive) {
                        NowPlayingBars(playing = isPlaying, color = accent, modifier = Modifier.size(15.dp))
                    } else {
                        Text(
                            text = trackNumber.toString(),
                            style = LiquidTypography.body,
                            color = subtitleColor,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (showArtwork) 13.dp else 6.dp, end = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = LiquidTypography.body.copy(fontSize = 16.sp, lineHeight = 21.sp),
                    color = if (isActive && !showArtwork) accent else if (enabled) titleColor else titleColor.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank() || explicit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (explicit) {
                            ExplicitBadge(color = subtitleColor)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = subtitle.orEmpty(),
                            style = LiquidTypography.subheadline.copy(fontSize = 14.sp, lineHeight = 18.sp),
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (trailing != null) {
                trailing()
            }
            if (onMore != null) {
                MoreButton(onClick = onMore, tint = titleColor)
            } else {
                Spacer(Modifier.width(PageMargin - 4.dp))
            }
        }
        if (showSeparator) {
            Hairline(
                modifier = Modifier.align(Alignment.BottomStart),
                startIndent = startPadding + leadingWidth + (if (leadingRank != null) 30.dp else 0.dp) + if (showArtwork) 13.dp else 6.dp,
                color = separatorColor,
            )
        }
    }
}

@Composable
fun MoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Liquid.colors.label,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 44.dp)
            .pressScale(interaction, 0.85f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MoreHoriz, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** A Library-style navigation row: tinted glyph, large title, chevron. */
@Composable
fun NavigationRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    showSeparator: Boolean = true,
    iconTint: Color = Liquid.colors.accent,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = Liquid.colors
    Box(
        modifier
            .fillMaxWidth()
            .combinedClickable(interactionSource = interaction, indication = rememberRowHighlight(), onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(start = PageMargin, end = PageMargin - 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(25.dp))
            }
            Text(
                text = title,
                style = LiquidTypography.title3.copy(fontWeight = FontWeight.Normal),
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            if (!detail.isNullOrBlank()) {
                Text(detail, style = LiquidTypography.body, color = colors.secondaryLabel, maxLines = 1)
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(24.dp))
        }
        if (showSeparator) {
            Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + 44.dp)
        }
    }
}

/** iOS segmented control: a grey track and a thumb that springs between segments. */
@Composable
fun LiquidSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    val thumbFraction by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "segment",
    )
    val thumbColor = if (colors.isDark) Color(0xFF636366) else Color.White
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(colors.tertiaryFill)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val count = items.size.coerceAtLeast(1)
                    val w = size.width / count
                    drawRoundRect(
                        color = Color.Black.copy(alpha = if (colors.isDark) 0f else 0.06f),
                        topLeft = Offset(w * thumbFraction, 1.dp.toPx()),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(w * thumbFraction, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                    drawContent()
                }
        ) {
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { index, label ->
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .combinedClickable(interactionSource = interaction, indication = null) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = LiquidTypography.footnote.copy(
                                fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = colors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A row of scope capsules (search filters, library scopes). */
@Composable
fun ScopeCapsule(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        if (selected) colors.label else colors.tertiaryFill,
        animationSpec = spring(stiffness = 700f),
        label = "scopeBg",
    )
    val fg by animateColorAsState(
        if (selected) colors.background else colors.label,
        animationSpec = spring(stiffness = 700f),
        label = "scopeFg",
    )
    Box(
        modifier = modifier
            .height(34.dp)
            .pressScale(interaction, 0.95f)
            .clip(CircleShape)
            .background(bg)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

@Composable
fun LiquidSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Liquid.colors
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(colors.tertiaryFill)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = colors.secondaryLabel, modifier = Modifier.size(21.dp))
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(placeholder, style = LiquidTypography.body, color = colors.secondaryLabel, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LiquidTypography.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(value) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
            )
        }
        if (value.isNotEmpty()) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(30.dp)
                    .combinedClickable(interactionSource = interaction, indication = null) { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(colors.gray2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, null, tint = colors.background, modifier = Modifier.size(12.dp))
                }
            }
        } else {
            trailing?.invoke()
        }
    }
}

/** The iOS activity indicator: eight fading spokes. Animates in the draw phase only. */
@Composable
fun ActivityIndicator(
    modifier: Modifier = Modifier,
    color: Color = Liquid.colors.secondaryLabel,
    size: Dp = 26.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "spinnerPhase",
    )
    Canvas(modifier.size(size)) {
        val step = floor(phase.value)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val outer = this.size.minDimension / 2f
        val inner = outer * 0.48f
        val stroke = outer * 0.2f
        for (i in 0 until 8) {
            val age = ((step - i + 8) % 8) / 8f
            val angle = Math.toRadians((i * 45.0) - 90.0)
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = color.copy(alpha = color.alpha * (1f - age * 0.82f)),
                start = Offset(cx + inner * cos, cy + inner * sin),
                end = Offset(cx + (outer - stroke / 2f) * cos, cy + (outer - stroke / 2f) * sin),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * True while something opaque covers the whole page: the fully open player. The page's own
 * animations can rest then, since nothing of them is drawn. Provided around the page only,
 * never around the player.
 */
val LocalPageCovered = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/** Three bars that bounce while the row's track is playing and rest when it is paused. */
@Composable
fun NowPlayingBars(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Bounce only while some part of the bars is actually on screen. A playing row that is
    // composed but scrolled out of view (the Home chart below the fold) otherwise ticks an
    // infinite animation every frame, and each tick redraws the window and re-blurs every
    // glass surface over it, measured at ~2 cores on a phone for three bars nobody can see.
    // `boundsInWindow` is clipped by every parent, so a row outside a list's viewport is empty.
    val onScreen = remember { mutableStateOf(true) }
    @Suppress("NAME_SHADOWING")
    val modifier = modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        onScreen.value = bounds.width > 0f && bounds.height > 0f
    }
    // Under the open player the page is kept but not drawn (alpha 0), so its bounds stay on
    // screen; the page's covered flag says what they cannot.
    val covered = LocalPageCovered.current
    if (!playing || !onScreen.value || covered.value) {
        Canvas(modifier) {
            val w = size.width / 5f
            for (i in 0 until 3) {
                val h = size.height * 0.28f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * w * 2f, size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f),
                )
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "bars")
    val a = transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b = transition.animateFloat(1f, 0.3f, infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c = transition.animateFloat(0.45f, 0.95f, infiniteRepeatable(tween(610, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    Canvas(modifier) {
        val w = size.width / 5f
        val heights = floatArrayOf(a.value, b.value, c.value)
        for (i in 0 until 3) {
            val h = size.height * heights[i]
            drawRoundRect(
                color = color,
                topLeft = Offset(i * w * 2f, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f),
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = colors.tertiaryLabel, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = LiquidTypography.title3, color = colors.label, textAlign = TextAlign.Center)
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = LiquidTypography.subheadline, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            LiquidButton(text = actionLabel, onClick = onAction, modifier = Modifier.widthIn(min = 160.dp))
        }
    }
}

/** A grey block that stands in for content while it loads. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha = transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val color = Liquid.colors.gray5
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(shape)
            .background(color)
    )
}

/** Applies a layer transform only when [block] is non-null — keeps call sites tidy. */
fun Modifier.optionalLayer(block: (GraphicsLayerScope.() -> Unit)?): Modifier =
    if (block == null) this else graphicsLayer(block)

@Composable
fun offsetForRise(progress: Float, rise: Dp): Modifier =
    Modifier.offset(y = rise * (1f - progress))
