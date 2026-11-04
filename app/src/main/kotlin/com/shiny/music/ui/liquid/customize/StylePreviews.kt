package com.shiny.music.ui.liquid.customize

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.rememberGlassPress

internal val LocalPreviewArtwork = compositionLocalOf<String?> { null }
internal val LocalPreviewTitle = compositionLocalOf { "Shiny Music" }
internal val LocalPreviewArtist = compositionLocalOf { "" }

private val FrameShape = RoundedCornerShape(16.dp)
private val FrameDark = Color(0xFF232325)
private val ArtSoft = Brush.verticalGradient(listOf(Color(0xFF55555A), FrameDark))
private val ArtLight = Color(0xFF8A8A8E)
private val Ink = Color.White.copy(alpha = 0.92f)
private val Bone = Color.White.copy(alpha = 0.22f)
private val BoneDim = Color.White.copy(alpha = 0.11f)
private val Pill = RoundedCornerShape(50)

/** A ready-from-frame-one Shiny cover mark for previews with no track artwork yet. */
@Composable
internal fun PreviewArtwork(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val artwork = LocalPreviewArtwork.current
    if (!artwork.isNullOrBlank()) {
        Artwork(
            model = artwork,
            modifier = modifier,
            shape = shape,
            placeholderContent = { ShinyCoverMark(Modifier.fillMaxSize(), shape) },
            hairline = false,
        )
        return
    }
    ShinyCoverMark(modifier, shape)
}

@Composable
private fun ShinyCoverMark(modifier: Modifier, shape: Shape) {
    Box(
        modifier.clip(shape).background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF35131F), Color(0xFF171316), Color(0xFF111113)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val diameter = minOf(w, h) * 0.44f
            val center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
            drawCircle(Color.White.copy(alpha = 0.08f), diameter / 2f, center, style = Stroke(width = minOf(w, h) * 0.008f))
            drawCircle(Color(0xFFFF375F).copy(alpha = 0.22f), diameter * 0.32f, center)
        }
        Text(
            text = "S",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-1.4).sp,
        )
    }
}

@Composable
internal fun <T> StyleTiles(
    styles: List<T>,
    selected: T,
    name: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tileWidth: Dp = 88.dp,
    stacks: Map<T, List<T>> = emptyMap(),
    preview: @Composable BoxScope.(T) -> Unit,
) {
    fun shownFor(style: T): T = stacks[style]?.takeIf { selected in it }?.let { selected } ?: style
    if (styles.size <= 4) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            styles.forEach { style ->
                val shown = shownFor(style)
                StyleTile(
                    label = name(shown),
                    selected = shown == selected,
                    enabled = enabled,
                    onClick = { onSelect(shown) },
                    modifier = Modifier.weight(1f),
                    stack = stacks[style],
                    shown = shown,
                    onStack = onSelect,
                ) { preview(shown) }
            }

            repeat(4 - styles.size.coerceAtLeast(3)) { Spacer(Modifier.weight(1f)) }
        }
    } else {
        val state = rememberLazyListState()
        LaunchedEffect(Unit) {
            val index = styles.indexOfFirst { shownFor(it) == selected }
            if (index > 1) state.scrollToItem(index - 1)
        }
        LazyRow(
            state = state,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(styles, key = { it.toString() }) { style ->
                val shown = shownFor(style)
                StyleTile(
                    label = name(shown),
                    selected = shown == selected,
                    enabled = enabled,
                    onClick = { onSelect(shown) },
                    modifier = Modifier.width(tileWidth),
                    stack = stacks[style],
                    shown = shown,
                    onStack = onSelect,
                ) { preview(shown) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> StyleTile(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    stack: List<T>? = null,
    shown: T? = null,
    onStack: (T) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val stackModifier = if (stack == null || shown == null) {
        Modifier
    } else {
        Modifier.pointerInput(stack, shown) {
            var travel = 0f
            detectVerticalDragGestures(
                onDragStart = { travel = 0f },
                onVerticalDrag = { change, amount ->
                    travel += amount
                    change.consume()
                },
                onDragEnd = {
                    if (kotlin.math.abs(travel) > 24.dp.toPx()) {
                        val i = stack.indexOf(shown).coerceAtLeast(0)
                        val next = if (travel < 0) (i + 1) % stack.size else (i - 1 + stack.size) % stack.size
                        onStack(stack[next])
                    }
                },
            )
        }
    }
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val ring by animateColorAsState(if (selected) colors.accent else Color.Transparent, tween(220), label = "styleRing")
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.99f,
        animationSpec = spring(dampingRatio = 0.94f, stiffness = 520f),
        label = "styleSelectionScale",
    )
    Column(
        modifier
            .then(stackModifier)
            .semantics { this.selected = selected }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.6f)
                .scale(selectedScale)
                .glassPressScale(press, 0.04f)
                .border(if (selected) 1.5.dp else 1.dp, ring, FrameShape)
                .padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FrameDark),
        ) {
            content()
            if (stack != null) {
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    stack.forEach { item ->
                        Box(Modifier.size(4.dp).background(if (item == shown) Color.White else Color.White.copy(alpha = 0.3f), CircleShape))
                    }
                }
            }
        }
        Text(
            text = label,
            style = LiquidTypography.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) colors.label else colors.secondaryLabel,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
internal fun PlaylistStylePreview(style: PlaylistStyle) {
    val title = LocalPreviewTitle.current
    val artist = LocalPreviewArtist.current.ifBlank { "Shiny Music" }
    when (style) {
        PlaylistStyle.Classic -> Column(Modifier.fillMaxSize().background(ArtSoft).padding(top = 16.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
            ) { PreviewArtwork(Modifier.fillMaxSize(), RoundedCornerShape(6.dp)) }
            Spacer(Modifier.height(9.dp))
            PreviewTitle(title, artist, Modifier.padding(horizontal = 9.dp))
            Spacer(Modifier.height(7.dp))
            ActionChips(Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(8.dp))
            PreviewSongRow(title, artist)
        }
        PlaylistStyle.Flow -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                PreviewArtwork(Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(62.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)))))
                PreviewTitle(title, artist, Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 8.dp))
            }
            Spacer(Modifier.height(7.dp))
            ActionChips(Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(9.dp))
            PreviewSongRow(title, artist)
            PreviewSongRow("A song for the drive", "Shiny Music")
        }
        PlaylistStyle.Minimal -> Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color(0xFF5A5A60), 0.55f to FrameDark))
                .padding(top = 22.dp),
        ) {
            PreviewTitle(title, artist, Modifier.padding(horizontal = 9.dp))
            Spacer(Modifier.height(8.dp))
            ActionChips(Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(10.dp))
            PreviewSongRow(title, artist)
            PreviewSongRow("A song for the drive", "Shiny Music")
        }
    }
}

@Composable
internal fun HomeStylePreview(style: HomeStyle) {
    val title = LocalPreviewTitle.current
    val artist = LocalPreviewArtist.current.ifBlank { "Shiny Music" }
    when (style) {
        HomeStyle.Hero -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(78.dp)) {
                PreviewArtwork(Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(52.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
                PreviewTitle(title, artist, Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 8.dp))
            }
            Spacer(Modifier.height(9.dp))
            Text("Recently played", color = Ink, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(6.dp))
            ArtworkShelf()
        }
        HomeStyle.ListenNow -> Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
            Text("Listen now", color = Ink, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(7.dp))
            PreviewSongRow(title, artist)
            PreviewSongRow("A song for the drive", "Shiny Music")
            PreviewSongRow("Your recent mix", "Made for you")
            Spacer(Modifier.height(8.dp))
            Text("Made for you", color = Ink, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(6.dp))
            ArtworkShelf()
        }
        HomeStyle.HopIn -> Column(Modifier.fillMaxSize().padding(top = 18.dp, start = 7.dp, end = 7.dp)) {
            Text("Your library", color = Ink, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp))
            Spacer(Modifier.height(7.dp))
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).background(Color(0xFF28282B))) {
                            PreviewArtwork(Modifier.fillMaxWidth().aspectRatio(1.2f), RoundedCornerShape(5.dp))
                            Text(title.take(13), color = Ink, fontSize = 5.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
internal fun LyricsStylePreview(style: LyricsStyle) {
    Column(
        Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxSize()) {
            PreviewArtwork(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.67f)))
            Column(
                Modifier.align(Alignment.CenterStart).padding(horizontal = 9.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("The night moves", color = Bone, fontSize = 7.sp, fontWeight = FontWeight.Medium)
                Text("a little slower", color = Bone, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "with every word",
                    color = Ink,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = if (style.cascade) 4.dp else 0.dp, top = if (style.rise) 2.dp else 0.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (style.glow) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = if (style.glow) 4.dp else 0.dp, vertical = if (style.glow) 2.dp else 0.dp),
                )
                if (!style.words) Text("Let the whole line land", color = Bone, fontSize = 6.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun BlurPreview(on: Boolean, progressive: Boolean, strength: Float, title: String, artist: String) {
    val radius = if (on) (strength * 0.28f).coerceIn(0f, 12f).dp else 0.dp
    val glassAlpha = if (on) (0.28f + strength / 100f).coerceIn(0.28f, 0.72f) else 0.94f
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
        PreviewArtwork(Modifier.fillMaxSize().blur(radius))
        Box(
            Modifier.fillMaxSize().background(
                if (progressive) {
                    Brush.verticalGradient(
                        0f to Color(0xFF101116).copy(alpha = if (on) 0.03f else 0.34f),
                        0.48f to Color(0xFF101116).copy(alpha = if (on) 0.10f else 0.62f),
                        1f to Color(0xFF101116).copy(alpha = if (on) 0.65f else 0.94f),
                    )
                } else {
                    Brush.linearGradient(listOf(Color(0xFF17171C).copy(alpha = glassAlpha), Color(0xFF17171C).copy(alpha = glassAlpha)))
                },
            ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(14.dp).fillMaxWidth(0.76f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF17171C).copy(alpha = glassAlpha))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))) { PreviewArtwork(Modifier.fillMaxSize()) }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist.ifBlank { "Shiny Music" }, color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Text("Ⅱ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewTitle(title: String, artist: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(title, color = Ink, fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(artist, color = Bone, fontSize = 5.5.sp, lineHeight = 7.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActionChips(modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        Text("Play", color = Color(0xFF171316), fontSize = 5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(Pill).background(Ink).padding(horizontal = 7.dp, vertical = 3.dp))
        Text("Save", color = Ink, fontSize = 5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(Pill).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun PreviewSongRow(title: String, artist: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        PreviewArtwork(Modifier.size(14.dp), RoundedCornerShape(3.dp))
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = Ink, fontSize = 5.5.sp, lineHeight = 6.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Bone, fontSize = 4.5.sp, lineHeight = 5.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("•••", color = Bone, fontSize = 5.sp)
    }
}

@Composable
private fun ArtworkShelf() {
    Row(Modifier.padding(horizontal = 9.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PreviewArtwork(Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(4.dp))
                Text(
                    text = when (index) { 0 -> "Recently played"; 1 -> "For you"; else -> "Your mix" },
                    color = Bone,
                    fontSize = 4.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun Bar(width: Dp, height: Dp, color: Color = Ink, modifier: Modifier = Modifier) {
    Box(modifier.size(width, height).background(color, Pill))
}
