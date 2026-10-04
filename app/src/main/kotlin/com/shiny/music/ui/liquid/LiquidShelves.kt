package com.shiny.music.ui.liquid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.theme.pressScale

/** A horizontal row of tiles on the page keyline, snapping tile by tile. */
@Composable
fun <T> Shelf(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    gap: Dp = ShelfGap,
    itemContent: @Composable (T) -> Unit,
) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PageMargin),
        horizontalArrangement = Arrangement.spacedBy(gap),
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
    ) {
        items(items, key = key) { itemContent(it) }
    }
}

/**
 * Songs laid out as the Music app's "Top Songs": columns of [rows] rows that page
 * sideways, each column a little narrower than the screen so the next one peeks in.
 */
@Composable
fun <T> SongGrid(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    rows: Int = 4,
    rowHeight: Dp = 62.dp,
    cell: @Composable (item: T, index: Int, modifier: Modifier, isLastInColumn: Boolean) -> Unit,
) {
    val state = rememberLazyGridState()
    val effectiveRows = rows.coerceAtMost(items.size).coerceAtLeast(1)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnWidth = maxWidth - PageMargin - 30.dp
        LazyHorizontalGrid(
            rows = GridCells.Fixed(effectiveRows),
            state = state,
            contentPadding = PaddingValues(horizontal = PageMargin),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * effectiveRows),
        ) {
            items(items.size, key = { key(items[it]) }) { index ->
                cell(
                    items[index],
                    index,
                    Modifier.width(columnWidth),
                    index % effectiveRows == effectiveRows - 1,
                )
            }
        }
    }
}

/** A colour tile with its name set bottom-left — Browse Categories, moods and genres. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryTile(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 172.dp,
    height: Dp = 104.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(if (width == Dp.Unspecified) Modifier.fillMaxWidth().height(height) else Modifier.size(width, height))
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(lerp(color, Color.White, 0.08f), lerp(color, Color.Black, 0.28f)),
                )
            )
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            style = LiquidTypography.headline.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Placeholder shelf shown while a page's first data is still on its way. */
@Composable
fun SkeletonShelf(modifier: Modifier = Modifier, tile: Dp = 164.dp) {
    Column(modifier.fillMaxWidth()) {
        SkeletonBlock(
            Modifier
                .padding(start = PageMargin, top = 28.dp, bottom = 12.dp)
                .size(width = 180.dp, height = 22.dp),
            shape = RoundedCornerShape(6.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageMargin),
            horizontalArrangement = Arrangement.spacedBy(ShelfGap),
            userScrollEnabled = false,
        ) {
            items(4) {
                Column {
                    SkeletonBlock(Modifier.size(tile), shape = RoundedCornerShape(artworkRadius(tile)))
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(Modifier.size(width = tile * 0.8f, height = 12.dp), shape = RoundedCornerShape(4.dp))
                    Spacer(Modifier.height(5.dp))
                    SkeletonBlock(Modifier.size(width = tile * 0.5f, height = 12.dp), shape = RoundedCornerShape(4.dp))
                }
            }
        }
    }
}
