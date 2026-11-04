package com.shiny.music.ui.liquid.customize

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.rememberGlassPress

/*
 * The little phone screens on Settings › Customise: each style drawn in bars and blocks, so
 * a choice can be seen before it is made. Nothing here reads the player or the library.
 */

private val FrameShape = RoundedCornerShape(16.dp)
private val FrameDark = Color(0xFF232325)
private val ArtFill = Brush.verticalGradient(listOf(Color(0xFF6A6A6E), Color(0xFF3E3E42)))
private val ArtSoft = Brush.verticalGradient(listOf(Color(0xFF55555A), FrameDark))
private val ArtLight = Color(0xFF8A8A8E)
private val Ink = Color.White.copy(alpha = 0.92f)
private val Bone = Color.White.copy(alpha = 0.22f)
private val BoneDim = Color.White.copy(alpha = 0.11f)
private val Pill = RoundedCornerShape(50)

/**
 * A row of styles to pick from, each a small drawn screen with its name under it; the chosen
 * one is ringed. Up to four share the row's width, more scroll sideways.
 */
@Composable
internal fun <T> StyleTiles(
    styles: List<T>,
    selected: T,
    name: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    if (styles.size <= 4) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            styles.forEach { style ->
                StyleTile(
                    label = name(style),
                    selected = style == selected,
                    enabled = enabled,
                    onClick = { onSelect(style) },
                    modifier = Modifier.weight(1f),
                ) { preview(style) }
            }
            // three or fewer still take the same tile size, so rows on one page line up
            repeat(4 - styles.size.coerceAtLeast(3)) { Spacer(Modifier.weight(1f)) }
        }
    } else {
        val state = rememberLazyListState()
        LaunchedEffect(Unit) {
            val index = styles.indexOf(selected)
            if (index > 1) state.scrollToItem(index - 1)
        }
        LazyRow(
            state = state,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(styles, key = { it.toString() }) { style ->
                StyleTile(
                    label = name(style),
                    selected = style == selected,
                    enabled = enabled,
                    onClick = { onSelect(style) },
                    modifier = Modifier.width(88.dp),
                ) { preview(style) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StyleTile(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val ring by animateColorAsState(if (selected) colors.label else Color.Transparent, tween(180), label = "styleRing")
    Column(
        modifier
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
                .glassPressScale(press, 0.04f)
                .border(2.dp, ring, FrameShape)
                .padding(4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FrameDark),
            content = content,
        )
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
    when (style) {
        PlaylistStyle.Classic -> Column(Modifier.fillMaxSize().background(ArtSoft).padding(top = 16.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
                    .background(ArtLight, RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(9.dp))
            TitleBars(Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(7.dp))
            Buttons(Modifier.padding(start = 9.dp))
        }
        PlaylistStyle.Flow -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(ArtFill)) {
                TitleBars(Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 8.dp))
            }
            Spacer(Modifier.height(7.dp))
            Buttons(Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(9.dp))
            TrackRows(2)
        }
        PlaylistStyle.Minimal -> Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color(0xFF5A5A60), 0.55f to FrameDark))
                .padding(top = 22.dp),
        ) {
            Column(Modifier.padding(start = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Bar(46.dp, 7.dp)
                Bar(26.dp, 4.dp, Bone)
            }
            Spacer(Modifier.height(8.dp))
            Buttons(Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(10.dp))
            TrackRows(4)
        }
    }
}

@Composable
internal fun HomeStylePreview(style: HomeStyle) {
    when (style) {
        HomeStyle.Hero -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(78.dp).background(ArtFill)) {
                TitleBars(Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 8.dp))
            }
            Spacer(Modifier.height(9.dp))
            Bar(30.dp, 4.dp, Ink, Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(6.dp))
            Shelf()
        }
        HomeStyle.ListenNow -> Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
            Bar(40.dp, 5.dp, Ink, Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(7.dp))
            TrackRows(3)
            Spacer(Modifier.height(8.dp))
            Bar(30.dp, 4.dp, Ink, Modifier.padding(start = 9.dp))
            Spacer(Modifier.height(6.dp))
            Shelf()
        }
        HomeStyle.HopIn -> Column(Modifier.fillMaxSize().padding(top = 18.dp, start = 7.dp, end = 7.dp)) {
            Bar(26.dp, 5.dp, Ink, Modifier.padding(start = 2.dp))
            Spacer(Modifier.height(7.dp))
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) {
                        Row(
                            Modifier
                                .weight(1f)
                                .height(15.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BoneDim),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(15.dp).background(Bone))
                            Spacer(Modifier.width(3.dp))
                            Bar(12.dp, 3.dp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(5.dp))
            Bar(30.dp, 4.dp, Ink, Modifier.padding(start = 2.dp))
            Spacer(Modifier.height(6.dp))
            Shelf(Modifier.padding(start = 0.dp))
        }
    }
}

/** A lyric style singing its middle line: what is lit, what glows, what rises. */
@Composable
internal fun LyricsStylePreview(style: LyricsStyle) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ArtSoft)
            .padding(start = 9.dp, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Bar(50.dp, 6.dp, Bone)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            // the line being sung: two words lit, one being lit, two to come
            val lit = if (style.words) 2 else 5
            listOf(9.dp, 12.dp, 8.dp, 11.dp, 7.dp).forEachIndexed { i, w ->
                val sung = i < lit
                val color = if (sung) Ink else Bone
                val lift = if (style.rise && sung) 2.dp else 0.dp
                Box(Modifier.padding(bottom = lift)) {
                    if (style.glow && sung) {
                        Box(Modifier.size(w + 4.dp, 12.dp).background(Color.White.copy(alpha = 0.14f), Pill))
                    }
                    Box(Modifier.align(Alignment.Center).size(w, 8.dp).background(color, Pill))
                }
            }
        }
        Bar(56.dp, 6.dp, Bone, Modifier.padding(start = if (style.cascade) 3.dp else 0.dp))
        Bar(40.dp, 6.dp, BoneDim, Modifier.padding(start = if (style.cascade) 6.dp else 0.dp))
        Bar(52.dp, 6.dp, BoneDim, Modifier.padding(start = if (style.cascade) 8.dp else 0.dp))
    }
}

/** A glass bar over a page of covers: sharp or blurred, as the blur settings would draw it. */
@Composable
internal fun BlurPreview(on: Boolean, progressive: Boolean, strength: Float) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { row ->
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(6) { Box(Modifier.weight(1f).aspectRatio(1f).background(if ((row + it) % 2 == 0) ArtLight else Color(0xFF5E5E64), RoundedCornerShape(5.dp))) }
                }
            }
        }
        val alpha = if (on) (0.45f + 0.4f * ((strength - 10f) / 40f)).coerceIn(0f, 0.9f) else 0.96f
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(
                    if (progressive || !on) {
                        Brush.verticalGradient(0f to FrameDark.copy(alpha = alpha), 1f to FrameDark.copy(alpha = 0f))
                    } else {
                        Brush.verticalGradient(0f to FrameDark.copy(alpha = alpha), 1f to FrameDark.copy(alpha = alpha))
                    }
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .fillMaxWidth(0.8f)
                .height(16.dp)
                .background(if (on) Color.White.copy(alpha = 0.32f) else Color(0xFF3A3A3E), Pill),
        )
    }
}

@Composable
private fun TitleBars(modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        Bar(38.dp, 5.dp)
        Bar(24.dp, 4.dp, Bone)
    }
}

@Composable
private fun Buttons(modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        Box(Modifier.size(24.dp, 9.dp).background(Ink, Pill))
        Box(Modifier.size(9.dp).background(Bone, CircleShape))
    }
}

@Composable
private fun TrackRows(count: Int) {
    Column(Modifier.padding(horizontal = 9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Bone, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Bar(30.dp, 3.dp)
                    Bar(18.dp, 2.5.dp, BoneDim)
                }
            }
        }
    }
}

@Composable
private fun Shelf(modifier: Modifier = Modifier.padding(start = 9.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        repeat(3) { Box(Modifier.size(24.dp).background(Bone, RoundedCornerShape(5.dp))) }
    }
}

@Composable
internal fun Bar(width: Dp, height: Dp, color: Color = Ink, modifier: Modifier = Modifier) {
    Box(modifier.size(width, height).background(color, Pill))
}
