package com.shiny.music.ui.liquid.discover

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.discover.AppleArtwork
import com.shiny.music.discover.ChartArtist
import com.shiny.music.discover.ChartEntry
import com.shiny.music.discover.ChartKind
import com.shiny.music.discover.ChartRegions
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.ShelfGap
import com.shiny.music.ui.liquid.SkeletonBlock
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.appearance.PageTransitions
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.liquid.screens.RefreshIndicator
import com.shiny.music.viewmodels.ChartSlot
import com.shiny.music.viewmodels.DiscoverCharts
import com.shiny.music.viewmodels.DiscoverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverCollectionPage(
    navController: NavController,
    title: String,
    viewModel: DiscoverViewModel,
    content: LazyListScope.(DiscoverCharts, DiscoverUi) -> Unit,
) {
    val actions = rememberLiquidActions(navController) ?: return
    val reduceMotion = LocalShinyAppearance.current.transitions == PageTransitions.Instant
    val ui = rememberDiscoverUi(viewModel, actions, reduceMotion)
    val charts by viewModel.charts.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    DiscoverLifecycle(viewModel)
    DiscoverEvents(viewModel, actions)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        state = pullState,
        indicator = { RefreshIndicator(pullState, isRefreshing) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LargeTitlePage(
            title = title,
            state = listState,
            navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
        ) {
            val current = charts
            when {
                current == null || (current.isEmpty && current.loading) ->
                    items(8, key = { "skeleton_$it" }, contentType = { "row_skeleton" }) { RowSkeleton() }

                current.isEmpty -> item(key = "unavailable") {
                    EmptyState(
                        icon = Icons.Rounded.BarChart,
                        title = stringResource(R.string.discover_unavailable_title),
                        message = stringResource(R.string.discover_unavailable_body, ChartRegions.name(current.region.storefront)),
                        actionLabel = stringResource(R.string.retry),
                        onAction = viewModel::refresh,
                    )
                }

                else -> content(current, ui)
            }
        }
        DiscoverNoticeHost(notice, reduceMotion, navController, viewModel::dismissNotice, viewModel::choose)
    }
}

@Composable
private fun RowSkeleton() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(52.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.width(13.dp))
        Column {
            SkeletonBlock(Modifier.size(width = 180.dp, height = 13.dp), RoundedCornerShape(4.dp))
            Spacer(Modifier.height(6.dp))
            SkeletonBlock(Modifier.size(width = 110.dp, height = 12.dp), RoundedCornerShape(4.dp))
        }
    }
}

@Composable
private fun SlotMissing(slot: ChartSlot, onRetry: () -> Unit) {
    EmptyState(
        icon = Icons.Rounded.BarChart,
        title = stringResource(R.string.discover_unavailable_title),
        message = if (slot.error == null) stringResource(R.string.discover_empty_chart) else null,
        actionLabel = if (slot.error != null) stringResource(R.string.retry) else null,
        onAction = if (slot.error != null) onRetry else null,
    )
}

@Composable
fun DiscoverSongsScreen(navController: NavController, viewModel: DiscoverViewModel = hiltViewModel()) {
    val actions = rememberLiquidActions(navController) ?: return
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val busy by viewModel.busy.collectAsState()
    DiscoverCollectionPage(navController, stringResource(R.string.discover_top_songs), viewModel) { charts, _ ->
        val songs = charts.songs.entries
        if (songs.isEmpty()) {
            item(key = "missing") { SlotMissing(charts.songs, viewModel::refresh) }
            return@DiscoverCollectionPage
        }
        item(key = "actions", contentType = "actions") {
            val title = stringResource(R.string.discover_chart_queue_title, ChartRegions.name(charts.region.storefront))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(ShelfGap),
            ) {
                LiquidButton(
                    text = stringResource(R.string.liquid_play),
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { viewModel.playAll(songs, title, shuffle = false) },
                    modifier = Modifier.weight(1f),
                )
                LiquidButton(
                    text = stringResource(R.string.liquid_shuffle),
                    icon = Icons.Rounded.Shuffle,
                    onClick = { viewModel.playAll(songs, title, shuffle = true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val highest = songs.last().position
        itemsIndexed(songs, key = { _, e -> e.id }, contentType = { _, _ -> "chart_song" }) { index, entry ->
            val title = stringResource(R.string.discover_chart_queue_title, ChartRegions.name(charts.region.storefront))
            val active = mediaMetadata?.id != null && viewModel.playingId(entry) == mediaMetadata?.id
            ChartSongRow(
                entry = entry,
                isActive = active,
                isPlaying = isPlaying,
                busy = busy == viewModel.songKey(entry),
                onClick = { if (active) actions.togglePlayPause() else viewModel.playSong(entry, songs, title) },
                onMore = { viewModel.songMenu(entry) },
                showSeparator = index < songs.lastIndex,
                highestRank = highest,
            )
        }
    }
}

@Composable
fun DiscoverAlbumsScreen(navController: NavController, viewModel: DiscoverViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsState()
    DiscoverCollectionPage(navController, stringResource(R.string.discover_top_albums), viewModel) { charts, _ ->
        val albums = charts.albums.entries
        if (albums.isEmpty()) {
            item(key = "missing") { SlotMissing(charts.albums, viewModel::refresh) }
            return@DiscoverCollectionPage
        }
        items(albums.chunked(2), key = { "albums_${it.first().id}" }, contentType = { "album_pair" }) { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(ShelfGap),
            ) {
                pair.forEach { entry ->
                    Box(Modifier.weight(1f)) {
                        AlbumTile(
                            entry = entry,
                            busy = busy == viewModel.albumKey(entry),
                            onClick = { viewModel.openAlbum(entry) },
                            width = Dp.Unspecified,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DiscoverArtistsScreen(navController: NavController, viewModel: DiscoverViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsState()
    val photos by viewModel.artistItems.collectAsState()
    DiscoverCollectionPage(navController, stringResource(R.string.discover_trending_artists), viewModel) { charts, _ ->
        val artists = charts.artists
        if (artists.isEmpty()) {
            item(key = "missing") { SlotMissing(charts.songs, viewModel::refresh) }
            return@DiscoverCollectionPage
        }
        val column = rankWidth(artists.size)
        itemsIndexed(artists, key = { _, a -> a.key }, contentType = { _, _ -> "chart_artist" }) { index, artist ->
            LaunchedEffect(artist.key) { viewModel.ensureArtists(listOf(artist)) }
            ArtistRow(
                artist = artist,
                rank = index + 1,
                rankColumn = column,
                photo = photos[artist.key]?.thumbnail,
                busy = busy == viewModel.artistKey(artist),
                showSeparator = index < artists.lastIndex,
                onClick = { viewModel.openArtist(artist) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistRow(
    artist: ChartArtist,
    rank: Int,
    rankColumn: Dp,
    photo: String?,
    busy: Boolean,
    showSeparator: Boolean,
    onClick: () -> Unit,
) {
    val colors = Liquid.colors
    val px = with(LocalDensity.current) { 56.dp.roundToPx() * 2 }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(interactionSource = interaction, indication = rememberRowHighlight(), role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(start = PageMargin, end = PageMargin - 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rank.toString(),
                style = ChartRankStyle,
                color = if (rank <= 3) colors.label else colors.secondaryLabel,
                maxLines = 1,
                modifier = Modifier
                    .width(rankColumn)
                    .padding(end = 8.dp),
            )
            ArtistPhoto(artist.name, photo, px, Modifier.size(56.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 13.dp, end = 4.dp),
            ) {
                Text(
                    text = artist.name,
                    style = LiquidTypography.body.copy(fontWeight = FontWeight.Medium),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.discover_artist_songs, artist.songCount, artist.songCount) +
                        " · " + stringResource(R.string.discover_artist_peak, artist.bestPosition),
                    style = LiquidTypography.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.featuredOn > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.discover_artist_featured, artist.featuredOn, artist.featuredOn),
                        style = LiquidTypography.caption1,
                        color = colors.tertiaryLabel,
                        maxLines = 1,
                    )
                }
            }
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    ActivityIndicator(size = 18.dp)
                } else {
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (showSeparator) {
            Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + rankColumn + 56.dp + 13.dp)
        }
    }
}

@Composable
fun DiscoverVideosScreen(navController: NavController, viewModel: DiscoverViewModel = hiltViewModel()) {
    val actions = rememberLiquidActions(navController) ?: return
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val busy by viewModel.busy.collectAsState()
    DiscoverCollectionPage(navController, stringResource(R.string.discover_top_videos), viewModel) { charts, _ ->
        val videos = charts.videos.entries
        if (videos.isEmpty()) {
            item(key = "missing") { SlotMissing(charts.videos, viewModel::refresh) }
            return@DiscoverCollectionPage
        }
        val column = rankWidth(videos.last().position)
        itemsIndexed(videos, key = { _, e -> e.id }, contentType = { _, _ -> "chart_video" }) { index, entry ->
            val title = stringResource(R.string.discover_videos_queue_title, ChartRegions.name(charts.region.storefront))
            val active = mediaMetadata?.id != null && viewModel.playingId(entry) == mediaMetadata?.id
            VideoRow(
                entry = entry,
                rankColumn = column,
                isActive = active,
                busy = busy == viewModel.songKey(entry),
                showSeparator = index < videos.lastIndex,
                onPlay = { if (active) actions.togglePlayPause() else viewModel.playSong(entry, videos, title, ChartKind.Videos) },
                onMore = { viewModel.songMenu(entry, ChartKind.Videos) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoRow(
    entry: ChartEntry,
    rankColumn: Dp,
    isActive: Boolean,
    busy: Boolean,
    showSeparator: Boolean,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = Liquid.colors
    val px = with(LocalDensity.current) { 132.dp.roundToPx() }
    val still = remember(entry.artwork, px) { AppleArtwork.landscape(entry.artwork, px * 2) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                role = Role.Button,
                onClick = onPlay,
                onLongClick = onMore,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = PageMargin, end = PageMargin - 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.position.toString(),
                style = ChartRankStyle,
                color = if (entry.position <= 3) colors.label else colors.secondaryLabel,
                maxLines = 1,
                modifier = Modifier
                    .width(rankColumn)
                    .padding(end = 8.dp),
            )
            Artwork(
                model = still,
                shape = RoundedCornerShape(9.dp),
                placeholder = Icons.Rounded.Videocam,
                modifier = Modifier.size(width = 132.dp, height = 74.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 13.dp, end = 4.dp),
            ) {
                Text(
                    text = entry.title,
                    style = LiquidTypography.body.copy(fontWeight = FontWeight.Medium),
                    color = if (isActive) colors.accent else colors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.artist,
                    style = LiquidTypography.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (busy) ActivityIndicator(size = 18.dp)
            }
        }
        if (showSeparator) {
            Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + rankColumn + 132.dp + 13.dp)
        }
    }
}
