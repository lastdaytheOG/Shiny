package com.shiny.music.ui.liquid.discover

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.innertube.models.ArtistItem
import com.shiny.music.R
import com.shiny.music.discover.ChartKind
import com.shiny.music.discover.ChartRegions
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.ShelfGap
import com.shiny.music.ui.liquid.SongGrid
import com.shiny.music.viewmodels.DiscoverCharts
import com.shiny.music.viewmodels.DiscoverViewModel

@Stable
internal class DiscoverUi(
    val viewModel: DiscoverViewModel,
    val actions: LiquidActions,
    val reduceMotion: Boolean,
    val listState: LazyListState? = null,
) {
    fun navigate(route: String) = actions.navController.navigate(route)

    val heroDrift: (() -> Float)? = listState?.takeUnless { reduceMotion }?.let { state -> { heroDrift(state) } }
}

@Composable
internal fun rememberDiscoverUi(
    viewModel: DiscoverViewModel,
    actions: LiquidActions,
    reduceMotion: Boolean,
    listState: LazyListState? = null,
): DiscoverUi = remember(viewModel, actions, reduceMotion, listState) { DiscoverUi(viewModel, actions, reduceMotion, listState) }

private const val HERO_KEY = "dc_hero"

private fun heroDrift(state: LazyListState): Float {
    val items = state.layoutInfo.visibleItemsInfo
    for (i in items.indices) {
        val item = items[i]
        if (item.key == HERO_KEY) return (-item.offset.toFloat() / item.size).coerceIn(0f, 1f)
    }
    return if (state.firstVisibleItemIndex > 1) 1f else 0f
}

internal const val DISCOVER_SONGS = "discover/songs"
internal const val DISCOVER_ALBUMS = "discover/albums"
internal const val DISCOVER_ARTISTS = "discover/artists"
internal const val DISCOVER_VIDEOS = "discover/videos"

internal const val ARTIST_SHELF = 15

internal fun LazyListScope.discoverCharts(
    charts: DiscoverCharts?,
    ui: DiscoverUi,
    activeId: String?,
    isPlaying: Boolean,
    busy: String?,
    artistItems: Map<String, ArtistItem>,
) {
    val vm = ui.viewModel
    if (charts == null || (charts.isEmpty && charts.loading)) {
        item(key = "dc_hero_skeleton", contentType = "dc_hero_skeleton") { ChartHeroSkeleton() }
        item(key = "dc_grid_skeleton", contentType = "dc_grid_skeleton") { ChartGridSkeleton() }
        return
    }
    if (charts.isEmpty) {
        item(key = "dc_unavailable", contentType = "dc_unavailable") {
            EmptyState(
                icon = Icons.Rounded.BarChart,
                title = stringResource(R.string.discover_unavailable_title),
                message = if (charts.error == null) {
                    stringResource(R.string.discover_empty_chart)
                } else {
                    stringResource(R.string.discover_unavailable_body, ChartRegions.name(charts.region.storefront))
                },
                actionLabel = stringResource(R.string.retry),
                onAction = vm::refresh,
            )
        }
        return
    }

    val region = charts.region
    val songs = charts.songs.entries
    if (songs.isNotEmpty()) {
        item(key = HERO_KEY, contentType = "dc_hero") {
            val title = stringResource(R.string.discover_chart_queue_title, ChartRegions.name(region.storefront))
            ChartHero(
                songs = songs,
                region = region,
                drift = ui.heroDrift,
                onPlay = { vm.playAll(songs, title, shuffle = false) },
                onShuffle = { vm.playAll(songs, title, shuffle = true) },
                onOpen = { ui.navigate(DISCOVER_SONGS) },
            )
        }
        item(key = "dc_songs_h", contentType = "dc_header") {
            DiscoverHeader(title = stringResource(R.string.discover_top_songs), onSeeAll = { ui.navigate(DISCOVER_SONGS) })
        }
        item(key = "dc_songs", contentType = "dc_song_grid") {
            val title = stringResource(R.string.discover_chart_queue_title, ChartRegions.name(region.storefront))
            val preview = remember(songs) { songs.take(20) }
            SongGrid(items = preview, key = { it.id }, rows = 4, rowHeight = ChartRowHeight, peek = 48.dp) { entry, _, modifier, last ->
                val active = activeId != null && vm.playingId(entry) == activeId
                ChartSongRow(
                    entry = entry,
                    isActive = active,
                    isPlaying = isPlaying,
                    busy = busy == vm.songKey(entry),
                    onClick = { if (active) ui.actions.togglePlayPause() else vm.playSong(entry, songs, title) },
                    onMore = { vm.songMenu(entry) },
                    showSeparator = !last,
                    startPadding = 0.dp,
                    highestRank = preview.size,
                    modifier = modifier,
                )
            }
        }
    }

    val artists = charts.artists
    if (artists.isNotEmpty()) {
        item(key = "dc_artists_h", contentType = "dc_header") {
            DiscoverHeader(title = stringResource(R.string.discover_trending_artists), onSeeAll = { ui.navigate(DISCOVER_ARTISTS) })
        }
        item(key = "dc_artists", contentType = "dc_artist_shelf") {
            val shown = remember(artists) { artists.take(ARTIST_SHELF).withIndex().toList() }
            LaunchedEffect(artists) { vm.ensureArtists(artists.take(ARTIST_SHELF)) }
            BoxWithConstraints {
                val width = peekWidth(maxWidth, visible = 3.4f, gap = 14.dp, min = 84.dp, max = 128.dp)
                Shelf(items = shown, key = { it.value.key }, gap = 14.dp) { (index, artist) ->
                    ArtistTile(
                        artist = artist,
                        rank = index + 1,
                        photo = artistItems[artist.key],
                        busy = busy == vm.artistKey(artist),
                        onClick = { vm.openArtist(artist) },
                        width = width,
                    )
                }
            }
        }
    }

    val albums = charts.albums.entries
    if (albums.isNotEmpty()) {
        item(key = "dc_albums_h", contentType = "dc_header") {
            DiscoverHeader(title = stringResource(R.string.discover_top_albums), onSeeAll = { ui.navigate(DISCOVER_ALBUMS) })
        }
        item(key = "dc_albums", contentType = "dc_album_shelf") {
            BoxWithConstraints {
                val width = peekWidth(maxWidth, visible = 2.4f, gap = ShelfGap, min = 128.dp, max = 196.dp)
                Shelf(items = albums, key = { it.id }) { entry ->
                    AlbumTile(
                        entry = entry,
                        busy = busy == vm.albumKey(entry),
                        onClick = { vm.openAlbum(entry) },
                        width = width,
                    )
                }
            }
        }
    }

    val videos = charts.videos.entries
    if (videos.isNotEmpty()) {
        item(key = "dc_videos_h", contentType = "dc_header") {
            DiscoverHeader(title = stringResource(R.string.discover_top_videos), onSeeAll = { ui.navigate(DISCOVER_VIDEOS) })
        }
        item(key = "dc_videos", contentType = "dc_video_shelf") {
            val title = stringResource(R.string.discover_videos_queue_title, ChartRegions.name(region.storefront))
            BoxWithConstraints {
                val width = peekWidth(maxWidth, visible = 1.3f, gap = ShelfGap, min = 240.dp, max = 340.dp)
                Shelf(items = videos, key = { it.id }) { entry ->
                    VideoCard(
                        entry = entry,
                        busy = busy == vm.songKey(entry),
                        onPlay = { vm.playSong(entry, videos, title, ChartKind.Videos) },
                        onMore = { vm.songMenu(entry, ChartKind.Videos) },
                        modifier = Modifier.padding(bottom = 2.dp),
                        width = width,
                    )
                }
            }
        }
    }
}
