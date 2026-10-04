package com.shiny.music.ui.liquid.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.shiny.music.R
import com.shiny.music.ui.component.backdrop.backdrops.layerBackdrop
import com.shiny.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.DetailTopBar
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.LocalLiquidBackdrop
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.liquid.rememberGlassPress
import com.shiny.music.ui.liquid.smartShuffleGlyph
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.NavPeek
import com.shiny.music.ui.utils.resize

/** Ink for white type on an artwork-coloured page. */
@Immutable
data class CollectionInk(
    val primary: Color = Color.White,
    val secondary: Color = Color.White.copy(alpha = 0.68f),
    val tertiary: Color = Color.White.copy(alpha = 0.42f),
    val separator: Color = Color.White.copy(alpha = 0.16f),
    val fill: Color = Color.White.copy(alpha = 0.14f),
)

val DefaultCollectionInk = CollectionInk()

/**
 * An album or playlist, laid out as the Music app lays them out: the cover centred high
 * on a page that has taken the cover's colour, the title and who made it underneath, the
 * paired Play and Shuffle buttons (and, for playlists, the round Smart Shuffle button), an
 * editorial note, then the tracks — and a quiet footer of facts. Everything below the
 * header is supplied by the caller.
 */
@Composable
fun LiquidCollectionPage(
    title: String,
    subtitle: String?,
    meta: String?,
    artwork: Any?,
    toneSource: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    collage: List<String>? = null,
    placeholder: ImageVector? = null,
    placeholderTint: Color? = null,
    description: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    playEnabled: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    headerExtra: (@Composable () -> Unit)? = null,
    onSmartShuffle: (() -> Unit)? = null,
    topActions: @Composable RowScope.(CollectionInk) -> Unit = {},
    content: LazyListScope.(CollectionInk) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tones = rememberArtworkTones(toneSource, ArtworkTones(placeholderTint ?: Color(0xFF232327), Color(0xFF3A3A40)))
    val pageColor = tones.deep
    val ink = DefaultCollectionInk

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    val pageBackdrop = rememberLayerBackdrop {
        drawRect(pageColor)
        drawContent()
    }
    val topInset = statusBarHeight()

    Box(
        modifier
            .fillMaxSize()
            .background(pageColor)
    ) {
        // A glow of the cover's livelier colour behind the header, fading into the page.
        Box(
            Modifier
                .fillMaxWidth()
                .height(560.dp)
                .graphicsLayer {
                    val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else size.height
                    translationY = -offset * 0.5f
                    alpha = (1f - offset / size.height).coerceIn(0f, 1f)
                }
                .background(
                    Brush.verticalGradient(
                        0f to tones.vivid.copy(alpha = 0.55f),
                        0.55f to tones.vivid.copy(alpha = 0.18f),
                        1f to Color.Transparent,
                    )
                )
        )

        CompositionLocalProvider(LocalLiquidBackdrop provides null) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageBackdrop),
                contentPadding = PaddingValues(bottom = liquidBottomPadding()),
            ) {
                item(key = "collection_header") {
                    CollectionHeader(
                        title = title,
                        subtitle = subtitle,
                        meta = meta,
                        artwork = artwork,
                        collage = collage,
                        placeholder = placeholder,
                        description = description,
                        onSubtitleClick = onSubtitleClick,
                        playEnabled = playEnabled,
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        onSmartShuffle = onSmartShuffle,
                        topInset = topInset,
                        ink = ink,
                        extra = headerExtra,
                    )
                }
                content(ink)
            }
        }

        val headerPx = with(density) { 380.dp.toPx() }
        DetailTopBar(
            backdrop = pageBackdrop,
            edgeBackdrop = pageBackdrop,
            edgeBackground = pageColor,
            title = title,
            onBack = onBack,
            contentColor = ink.primary,
            titleAlpha = {
                if (listState.firstVisibleItemIndex > 0) 1f
                else ((listState.firstVisibleItemScrollOffset - headerPx * 0.7f) / (headerPx * 0.15f)).coerceIn(0f, 1f)
            },
            actions = { topActions(ink) },
        )
    }
}

/**
 * The page a tapped tile opens, before its tracks arrive: the title, maker and cover the
 * tile already showed, on the cover's colour, with the tracks still loading below.
 */
@Composable
fun LiquidCollectionPlaceholder(peek: NavPeek.Peek, onBack: () -> Unit) {
    LiquidCollectionPage(
        title = peek.title,
        subtitle = peek.subtitle,
        meta = null,
        artwork = peek.artwork?.resize(1080, 1080),
        toneSource = peek.artwork?.resize(544, 544),
        playEnabled = false,
        onBack = onBack,
        onPlay = {},
        onShuffle = {},
    ) { ink ->
        item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                ActivityIndicator(color = ink.secondary)
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionHeader(
    title: String,
    subtitle: String?,
    meta: String?,
    artwork: Any?,
    collage: List<String>?,
    placeholder: ImageVector?,
    description: String?,
    onSubtitleClick: (() -> Unit)?,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onSmartShuffle: (() -> Unit)?,
    topInset: Dp,
    ink: CollectionInk,
    extra: (@Composable () -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val coverSize = (maxWidth * 0.66f).coerceAtMost(300.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset + 58.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(coverSize)
                    .shadow(28.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                if (!collage.isNullOrEmpty() && collage.size >= 4 && artwork == null) {
                    CollageCover(collage.take(4), Modifier.fillMaxSize())
                } else {
                    Artwork(
                        model = artwork ?: collage?.firstOrNull(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = placeholder ?: androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = title,
                style = LiquidTypography.title2,
                color = ink.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (!subtitle.isNullOrBlank()) {
                val interaction = remember { MutableInteractionSource() }
                Text(
                    text = subtitle,
                    style = LiquidTypography.title3.copy(fontWeight = FontWeight.Normal),
                    color = ink.primary.copy(alpha = 0.86f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 2.dp)
                        .then(
                            if (onSubtitleClick != null) {
                                Modifier.combinedClickable(interactionSource = interaction, indication = null, onClick = onSubtitleClick)
                            } else {
                                Modifier
                            }
                        ),
                )
            }
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = LiquidTypography.footnote.copy(fontWeight = FontWeight.Medium),
                    color = ink.secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CollectionButton(
                    text = stringResource(R.string.liquid_play),
                    icon = Icons.Rounded.PlayArrow,
                    onClick = onPlay,
                    enabled = playEnabled,
                    filled = true,
                    modifier = Modifier.weight(1f),
                )
                CollectionButton(
                    text = stringResource(R.string.liquid_shuffle),
                    icon = Icons.Rounded.Shuffle,
                    onClick = onShuffle,
                    enabled = playEnabled,
                    filled = false,
                    modifier = Modifier.weight(1f),
                )
                if (onSmartShuffle != null) {
                    SmartShuffleButton(onClick = onSmartShuffle, enabled = playEnabled)
                }
            }
            extra?.invoke()
            if (!description.isNullOrBlank()) {
                val interaction = remember { MutableInteractionSource() }
                Text(
                    text = description,
                    style = LiquidTypography.subheadline,
                    color = ink.secondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageMargin)
                        .padding(top = 18.dp)
                        .combinedClickable(interactionSource = interaction, indication = null) { expanded = !expanded },
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val bg = if (filled) Color.White else Color.White.copy(alpha = 0.16f)
    val fg = if (filled) Color.Black else Color.White
    Row(
        modifier = modifier
            .height(48.dp)
            .glassPressScale(press, -0.03f)
            .clip(CircleShape)
            .background(bg)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = LiquidTypography.headline, color = fg)
    }
}

/**
 * Smart Shuffle as a round button at the end of the Play and Shuffle pair: the mark alone,
 * in the Shuffle button's material. A long press says what it does, since there is no label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmartShuffleButton(onClick: () -> Unit, enabled: Boolean) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val glyph = remember { smartShuffleGlyph(Color.White) }
    val label = stringResource(R.string.liquid_smart_shuffle)
    val explanation = stringResource(R.string.liquid_smart_shuffle_subtitle)
    Box(
        modifier = Modifier
            .size(48.dp)
            .glassPressScale(press, -0.06f)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = label,
                onClick = onClick,
                onLongClick = { Toast.makeText(context, "$label · $explanation", Toast.LENGTH_SHORT).show() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = rememberVectorPainter(glyph),
            contentDescription = label,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        )
    }
}

/** Four covers in a square, for playlists that have no artwork of their own. */
@Composable
fun CollageCover(urls: List<String>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        items(urls.take(4)) { url ->
            Artwork(
                model = url,
                shape = RoundedCornerShape(0.dp),
                hairline = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }
    }
}

/** The small print under a track list: date, length, and the like. */
fun LazyListScope.collectionFooter(lines: List<String>, ink: CollectionInk) {
    if (lines.isEmpty()) return
    item(key = "collection_footer") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PageMargin, vertical = 18.dp),
        ) {
            lines.forEach { line ->
                Text(line, style = LiquidTypography.footnote, color = ink.secondary)
            }
        }
    }
}

/** The neutral stand-in for a collection page while its data loads. */
@Composable
fun LiquidLoadingPage(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF232327))
    ) {
        com.shiny.music.ui.liquid.ActivityIndicator(
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.Center),
        )
        com.shiny.music.ui.liquid.GlassIconButton(
            icon = androidx.compose.material.icons.Icons.Rounded.ChevronLeft,
            onClick = onBack,
            iconSize = 28.dp,
            tint = Color.White,
            kind = com.shiny.music.ui.liquid.GlassKind.Clear,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = statusBarHeight() + 4.dp, start = 14.dp),
        )
    }
}
