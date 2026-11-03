package com.shiny.music.ui.liquid.home

import androidx.compose.foundation.lazy.itemsIndexed
import com.shiny.music.home.ArtistGroup
import com.shiny.music.home.SimilarPicks
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.requiredSize
import com.shiny.music.ui.liquid.cachedArtworkTones
import com.shiny.music.ui.liquid.loadArtworkTones
import com.music.innertube.models.YTItem
import com.shiny.music.home.SimilarRow
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.shiny.music.R
import com.shiny.music.db.entities.Song
import com.shiny.music.home.ArtistListSection
import com.shiny.music.home.DeepDiveSection
import com.shiny.music.home.HistorySection
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.ScrollToTopEffect
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.prewarmOnTouch
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize

internal val HomeInset = 36.dp
internal val HomeGap = 15.dp
internal val HomeCardSize = 137.dp
internal val HomeCardRadius = 34.dp
internal val HomeRowHeight = 54.dp
private val HomeTracking = (-0.06).em

internal val HomeLabelStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 19.sp, letterSpacing = HomeTracking)
internal val HomeTitleStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = HomeTracking)
internal val HomeCardTitleStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = (-0.03).em)
internal val HomeCardSubtitleStyle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = (-0.02).em)
private val HomeRankStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = HomeTracking)

// the page: hero runs up under the status bar, the logo sits over it and turns into "Home"
// once the hero has scrolled away
@Composable
internal fun HomePage(
    state: LazyListState,
    heroHeightPx: Float,
    navController: NavController,
    avatar: @Composable () -> Unit,
    behind: @Composable BoxScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val colors = Liquid.colors
    val topInset = statusBarHeight()
    ScrollToTopEffect(navController, state)
    val progress by remember(state, heroHeightPx) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / heroHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
    }
    val bar by animateFloatAsState(
        targetValue = if (progress > 0.5f) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "homeBar",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        behind()
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = liquidBottomPadding()),
            content = content,
        )
        val ink = colors.background
        Box(
            Modifier
                .fillMaxWidth()
                .height(topInset + 92.dp)
                .drawWithCache {
                    val fade = Brush.verticalGradient(
                        0f to ink.copy(alpha = 0.9f),
                        0.3f to ink.copy(alpha = 0.6f),
                        0.6f to ink.copy(alpha = 0.3f),
                        1f to Color.Transparent,
                    )
                    onDrawBehind { drawRect(brush = fade, alpha = progress) }
                },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset + 14.dp, bottom = 16.dp, start = HomeInset, end = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_nobg),
                    contentDescription = null,
                    tint = colors.label,
                    modifier = Modifier
                        .offset(x = (-8).dp)
                        .size(34.dp)
                        .graphicsLayer {
                            alpha = 1f - bar
                            val scale = 1f - bar * 0.25f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            renderEffect = blur(bar * 15f)
                        },
                )
                Text(
                    text = stringResource(R.string.liquid_home),
                    color = colors.label,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = HomeTracking,
                    maxLines = 1,
                    modifier = Modifier
                        .semantics { heading() }
                        .graphicsLayer {
                            alpha = bar
                            renderEffect = blur((1f - bar) * 15f)
                        },
                )
            }
            Box(
                Modifier.graphicsLayer {
                    val scale = 1f - bar * 0.1f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(1f, 0.5f)
                },
            ) { avatar() }
        }
    }
}

// blur only while it's actually blurring, a zero radius effect still costs a layer
private fun blur(radius: Float) = if (radius > 0.5f) BlurEffect(radius, radius) else null

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeSectionTitle(
    title: String,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = HomeInset, end = HomeInset, top = 38.dp, bottom = 10.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressScale(interaction, 0.97f)
                        .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) {
            thumbnail()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            if (label != null) {
                Text(label, style = HomeCardSubtitleStyle, color = colors.label.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(title, style = HomeLabelStyle, color = colors.label.copy(alpha = 0.69f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.label.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun <T> HomeRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    gap: Dp = HomeGap,
    itemContent: @Composable (T) -> Unit,
) {
    LazyRow(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = HomeInset),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        itemsIndexed(items, key = { _, it -> key(it) }) { index, item ->
            Box(Modifier.cardReveal("card/${key(item)}", index)) { itemContent(item) }
        }
    }
}

// columns of three song rows that page sideways, the next column peeking in
@Composable
internal fun <T> HomeTrackGrid(
    items: List<T>,
    key: (T) -> Any,
    cell: @Composable (item: T, index: Int, modifier: Modifier) -> Unit,
) {
    val rows = items.size.coerceIn(1, 3)
    val state = rememberLazyGridState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val column = maxWidth - HomeInset - 50.dp
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            state = state,
            contentPadding = PaddingValues(horizontal = HomeInset),
            horizontalArrangement = Arrangement.spacedBy(39.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeRowHeight * rows + 6.dp * (rows - 1)),
        ) {
            items(items.size, key = { key(items[it]) }) { index -> cell(items[index], index, Modifier.width(column)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeTrackRow(
    songId: String?,
    title: String,
    subtitle: String?,
    artwork: Any?,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val cover = RoundedCornerShape((HomeCardRadius * 0.56f).coerceAtMost(20.dp))
    Row(
        modifier
            .height(HomeRowHeight)
            .prewarmOnTouch(songId)
            .pressScale(interaction, 0.97f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(rank.toString(), style = HomeRankStyle, color = colors.label, maxLines = 1, modifier = Modifier.width(37.dp))
        }
        Artwork(model = artwork, shape = cover, hairline = false, modifier = Modifier.size(HomeRowHeight))
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
        ) {
            Text(
                text = title,
                style = HomeCardTitleStyle.copy(fontSize = 15.sp),
                color = if (isActive) Liquid.colors.accent else colors.label.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = HomeCardSubtitleStyle, color = colors.label.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .background(colors.label.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreHoriz, stringResource(R.string.more_options), tint = colors.label.copy(alpha = 0.85f), modifier = Modifier.size(17.dp))
            }
        }
    }
}

private fun Song.artistLine(): String = artists.joinToString { it.name }

@Composable
internal fun HistoryShelf(section: HistorySection, activeId: String?, actions: LiquidActions) {
    val title = stringResource(R.string.history)
    HomeSectionTitle(title, onClick = { actions.navController.navigate("history") })
    HomeTrackGrid(items = section.songs, key = { it.id }) { song, index, modifier ->
        HomeTrackRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl?.resize(240, 240),
            isActive = song.id == activeId,
            onClick = { if (song.id == activeId) actions.togglePlayPause() else actions.playSongs(title, section.songs, index) },
            onLongClick = { actions.menu(song) },
            modifier = modifier,
        )
    }
}

// one box per artist: who they are, then five things around them. the whole box opens the
// similar page, which leads with these same picks
@Composable
internal fun ArtistListShelf(groups: List<ArtistGroup>, actions: LiquidActions) {
    HomeSectionTitle(stringResource(R.string.home_artist_list))
    HomeRow(items = groups, key = { "${it.artistId}_${it.artistName}" }) { ArtistListCard(it, actions) }
}

private val ArtistBoxFallbacks = listOf(
    listOf(Color(0xFF2B1B38), Color(0xFF151521), Color.Black),
    listOf(Color(0xFF1B2838), Color(0xFF0F1521), Color.Black),
    listOf(Color(0xFF381B24), Color(0xFF1F0F15), Color.Black),
    listOf(Color(0xFF1B382D), Color(0xFF0F1F17), Color.Black),
    listOf(Color(0xFF2D2B55), Color(0xFF151521), Color.Black),
)

@Composable
private fun ArtistListCard(group: ArtistGroup, actions: LiquidActions) {
    val context = LocalContext.current
    val fallback = ArtistBoxFallbacks[(group.artistName.hashCode() and 0x7FFFFFFF) % ArtistBoxFallbacks.size]
    var tones by remember(group) { mutableStateOf<List<Color>>(emptyList()) }
    // the box takes its colours from the artist and the first two picks, darkened
    LaunchedEffect(group) {
        val urls = listOfNotNull(group.artistPhoto) + group.items.take(2).mapNotNull { it.thumbnail }
        tones = urls.mapNotNull { url ->
            val small = url.resize(120, 120)
            (cachedArtworkTones(small) ?: loadArtworkTones(context, small))?.vivid
        }.distinct().take(3)
    }
    val c1 by animateColorAsState(tones.getOrNull(0)?.let { lerp(it, Color.Black, 0.65f) } ?: fallback[0], tween(800), label = "box1")
    val c2 by animateColorAsState(tones.getOrNull(1)?.let { lerp(it, Color.Black, 0.75f) } ?: fallback[1], tween(800), label = "box2")
    val c3 by animateColorAsState(tones.getOrNull(2)?.let { lerp(it, Color.Black, 0.85f) } ?: fallback[2], tween(800), label = "box3")
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .width(276.dp)
            .height(344.dp)
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind { drawRect(Brush.verticalGradient(listOf(c1, c2, c3))) }
            .clickable(interactionSource = interaction, indication = null) {
                group.artistId?.let { id ->
                    SimilarPicks.put(id, group)
                    actions.navController.navigate("artist/$id/similar")
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(model = group.artistPhoto?.resize(200, 200), shape = CircleShape, hairline = false, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.home_kicker_similar), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    Text(group.artistName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .padding(start = 24.dp)
                    .wrapContentWidth(Alignment.Start, unbounded = true),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                group.items.take(3).forEach { item ->
                    Artwork(model = item.thumbnail?.resize(300, 300), shape = RoundedCornerShape(16.dp), hairline = false, modifier = Modifier.requiredSize(96.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .padding(start = 24.dp)
                    .offset(y = 20.dp)
                    .wrapContentWidth(Alignment.Start, unbounded = true),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                group.items.drop(3).take(2).forEach { item ->
                    Artwork(model = item.thumbnail?.resize(480, 480), shape = CircleShape, hairline = false, modifier = Modifier.requiredSize(160.dp))
                }
            }
        }
    }
}
