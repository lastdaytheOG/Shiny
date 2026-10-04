package com.shiny.music.ui.liquid.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.shiny.music.R
import com.shiny.music.constants.HistorySource
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.CategoryTile
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.ShelfGap
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.utils.resize
import com.shiny.music.viewmodels.AccountContentType
import com.shiny.music.viewmodels.AccountViewModel
import com.shiny.music.viewmodels.ArtistAlbumsViewModel
import com.shiny.music.viewmodels.ArtistItemsViewModel
import com.shiny.music.viewmodels.ArtistSongsViewModel
import com.shiny.music.viewmodels.BrowseViewModel
import com.shiny.music.viewmodels.DateAgo
import com.shiny.music.viewmodels.HistoryViewModel
import com.shiny.music.viewmodels.MoodAndGenresViewModel
import com.shiny.music.viewmodels.NewReleaseViewModel
import com.shiny.music.viewmodels.YouTubeBrowseViewModel
import java.time.format.DateTimeFormatter

/** A list of YouTube items: songs as rows, everything else as a two-column grid. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsGrid(
    keyPrefix: String,
    items: List<YTItem>,
    actions: LiquidActions,
    activeId: String?,
    isPlaying: Boolean,
    songsTitle: String? = null,
) {
    val songs = items.filterIsInstance<SongItem>()
    if (songs.size == items.size && songs.isNotEmpty()) {
        itemsIndexed(songs, key = { i, s -> "${keyPrefix}_s_${i}_${s.id}" }) { index, song ->
            SongRow(
                songId = song.id,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                artwork = song.thumbnail,
                isActive = song.id == activeId,
                isPlaying = isPlaying,
                explicit = song.explicit,
                onClick = { actions.playSongItems(songsTitle, songs, startIndex = index) },
                onLongClick = { actions.menu(song) },
                onMore = { actions.menu(song) },
            )
        }
    } else {
        items(items.chunked(2), key = { row -> "${keyPrefix}_g_${row.first().id}" }) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(ShelfGap + 4.dp),
            ) {
                row.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        MediaTile(
                            songId = (item as? com.music.innertube.models.SongItem)?.id,
                            title = item.title,
                            subtitle = item.subtitleText(),
                            artwork = item.thumbnail?.resize(544, 544),
                            circular = item is ArtistItem,
                            width = Dp.Unspecified,
                            onClick = { actions.open(item) },
                            onLongClick = { actions.menu(item) },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LoadingItem() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { ActivityIndicator() }
}

// ---------------------------------------------------------------------------------------

@Composable
fun LiquidHistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val events by viewModel.events.collectAsState()
    val remote by viewModel.historyPage.collectAsState()
    val source by viewModel.historySource.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
    val labels = mapOf(
        "today" to stringResource(R.string.today),
        "yesterday" to stringResource(R.string.yesterday),
        "this_week" to stringResource(R.string.this_week),
        "last_week" to stringResource(R.string.last_week),
    )
    val allSongs = events.values.flatten().map { it.song }

    LargeTitlePage(
        title = stringResource(R.string.history),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "source") {
            LiquidSegmentedControl(
                items = listOf(stringResource(R.string.liquid_library), "YouTube Music"),
                selectedIndex = if (source == HistorySource.LOCAL) 0 else 1,
                onSelect = { viewModel.historySource.value = if (it == 0) HistorySource.LOCAL else HistorySource.REMOTE },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 8.dp),
            )
        }
        if (source == HistorySource.LOCAL) {
            if (allSongs.isNotEmpty()) {
                item(key = "play_row") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PageMargin, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        LiquidButton(stringResource(R.string.liquid_play), onClick = { actions.playSongs(null, allSongs) }, icon = Icons.Rounded.PlayArrow, tone = ButtonTone.Tinted, modifier = Modifier.weight(1f))
                        LiquidButton(stringResource(R.string.liquid_shuffle), onClick = { actions.playSongs(null, allSongs, shuffle = true) }, icon = Icons.Rounded.Shuffle, tone = ButtonTone.Tinted, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                item(key = "empty") { EmptyState(icon = Icons.Rounded.History, title = stringResource(R.string.history)) }
            }
            events.forEach { (date, list) ->
                val label = when (date) {
                    DateAgo.Today -> labels["today"]!!
                    DateAgo.Yesterday -> labels["yesterday"]!!
                    DateAgo.ThisWeek -> labels["this_week"]!!
                    DateAgo.LastWeek -> labels["last_week"]!!
                    is DateAgo.Other -> date.date.format(monthFormat)
                }
                item(key = "h_header_$label") { SectionHeader(title = label) }
                items(list, key = { "h_${label}_${it.event.id}" }) { event ->
                    SongRow(
                        songId = event.song.id,
                        title = event.song.title,
                        subtitle = event.song.artists.joinToString { it.name },
                        artwork = event.song.thumbnailUrl,
                        isActive = event.song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        explicit = event.song.song.explicit,
                        onClick = { actions.open(event.song) },
                        onLongClick = { actions.menu(event.song) },
                        onMore = { actions.menu(event.song) },
                    )
                }
            }
        } else {
            val sections = remote?.sections
            if (sections == null) {
                item(key = "remote_loading") { LoadingItem() }
            } else {
                sections.forEachIndexed { index, section ->
                    item(key = "r_header_$index") { SectionHeader(title = section.title) }
                    items(section.songs, key = { "r_${index}_${it.id}" }) { song ->
                        SongRow(
                            songId = song.id,
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            artwork = song.thumbnail,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            explicit = song.explicit,
                            onClick = { actions.open(song) },
                            onLongClick = { actions.menu(song) },
                            onMore = { actions.menu(song) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------

@Composable
fun LiquidArtistItemsScreen(
    navController: NavController,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val title by viewModel.title.collectAsState()
    val page by viewModel.itemsPage.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (last != null && total > 0 && last >= total - 4) viewModel.loadMore()
            }
    }

    LargeTitlePage(
        title = title,
        state = listState,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        val items = page?.items
        if (items == null) item(key = "loading") { LoadingItem() }
        else itemsGrid("ai", items, actions, mediaMetadata?.id, isPlaying, songsTitle = title)
    }
}

@Composable
fun LiquidArtistSongsScreen(
    navController: NavController,
    viewModel: ArtistSongsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val title = artist?.artist?.name ?: stringResource(R.string.songs)

    LargeTitlePage(
        title = title,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "play_row") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LiquidButton(stringResource(R.string.liquid_play), onClick = { actions.playSongs(title, songs) }, icon = Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f), enabled = songs.isNotEmpty())
                LiquidButton(stringResource(R.string.liquid_shuffle), onClick = { actions.playSongs(title, songs, shuffle = true) }, icon = Icons.Rounded.Shuffle, modifier = Modifier.weight(1f), enabled = songs.isNotEmpty())
            }
        }
        itemsIndexed(songs, key = { _, s -> "as_${s.id}" }) { index, song ->
            SongRow(
                songId = song.id,
                title = song.title,
                subtitle = song.album?.title ?: song.artists.joinToString { it.name },
                artwork = song.thumbnailUrl,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                explicit = song.song.explicit,
                onClick = { actions.playSongs(title, songs, startIndex = index) },
                onLongClick = { actions.menu(song) },
                onMore = { actions.menu(song) },
            )
        }
    }
}

@Composable
fun LiquidArtistAlbumsScreen(
    navController: NavController,
    viewModel: ArtistAlbumsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val artist by viewModel.artist.collectAsState(initial = null)
    val albums by viewModel.albums.collectAsState(initial = emptyList())

    LargeTitlePage(
        title = artist?.artist?.name ?: stringResource(R.string.albums),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        items(albums.chunked(2), key = { row -> "aa_${row.first().id}" }) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(ShelfGap + 4.dp),
            ) {
                row.forEach { album ->
                    Box(Modifier.weight(1f)) {
                        MediaTile(
                            title = album.title,
                            subtitle = album.album.year?.toString(),
                            artwork = album.thumbnailUrl?.resize(544, 544),
                            width = Dp.Unspecified,
                            onClick = { actions.open(album) },
                            onLongClick = { actions.menu(album) },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------

@Composable
fun LiquidMoodAndGenresScreen(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val groups by viewModel.moodAndGenres.collectAsState()
    LargeTitlePage(
        title = stringResource(R.string.liquid_moods),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        val list = groups
        if (list == null) {
            item(key = "loading") { LoadingItem() }
        } else {
            list.forEach { group ->
                item(key = "mg_header_${group.title}") { SectionHeader(title = group.title) }
                items(group.items.chunked(2), key = { row -> "mg_${group.title}_${row.first().title}" }) { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PageMargin)
                            .padding(bottom = ShelfGap),
                        horizontalArrangement = Arrangement.spacedBy(ShelfGap),
                    ) {
                        row.forEach { mood ->
                            Box(Modifier.weight(1f)) {
                                CategoryTile(
                                    title = mood.title,
                                    color = Color(mood.stripeColor),
                                    width = Dp.Unspecified,
                                    height = 100.dp,
                                    onClick = { navController.navigate("youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}") },
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidYouTubeBrowseScreen(
    navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val result by viewModel.result.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()

    LargeTitlePage(
        title = result?.title.orEmpty(),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        val r = result
        if (r == null) {
            item(key = "loading") { LoadingItem() }
        } else {
            r.items.forEachIndexed { index, section ->
                if (section.items.isEmpty()) return@forEachIndexed
                val songs = section.items.filterIsInstance<SongItem>()
                if (!section.title.isNullOrBlank()) {
                    item(key = "yb_header_$index") { SectionHeader(title = section.title!!) }
                }
                if (songs.size == section.items.size) {
                    itemsIndexed(songs.take(8), key = { i, s -> "yb_${index}_${i}_${s.id}" }) { i, song ->
                        SongRow(
                            songId = song.id,
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            artwork = song.thumbnail,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            explicit = song.explicit,
                            onClick = { actions.playSongItems(section.title, songs, startIndex = i) },
                            onLongClick = { actions.menu(song) },
                            onMore = { actions.menu(song) },
                        )
                    }
                } else {
                    item(key = "yb_$index") {
                        Shelf(items = section.items, key = { "ybi_${index}_${it.id}" }) { item ->
                            YouTubeTile(item, actions, isActive = item.id == mediaMetadata?.id, width = if (item is ArtistItem) 140.dp else 164.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidBrowseScreen(
    navController: NavController,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val title by viewModel.title.collectAsState()
    val items by viewModel.items.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    LargeTitlePage(
        title = title.orEmpty(),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        val list = items
        if (list.isNullOrEmpty()) item(key = "loading") { LoadingItem() }
        else itemsGrid("br", list, actions, mediaMetadata?.id, isPlaying, songsTitle = title)
    }
}

@Composable
fun LiquidNewReleaseScreen(
    navController: NavController,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val albums by viewModel.newReleaseAlbums.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    LargeTitlePage(
        title = stringResource(R.string.liquid_new_releases),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        if (albums.isEmpty()) item(key = "loading") { LoadingItem() }
        else itemsGrid("nr", albums, actions, mediaMetadata?.id, isPlaying)
    }
}

@Composable
fun LiquidAccountScreen(
    navController: NavController,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val selected by viewModel.selectedContentType.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val types = listOf(AccountContentType.PLAYLISTS, AccountContentType.ALBUMS, AccountContentType.ARTISTS)

    LargeTitlePage(
        title = stringResource(R.string.account),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "types") {
            LiquidSegmentedControl(
                items = listOf(stringResource(R.string.playlists), stringResource(R.string.albums), stringResource(R.string.artists)),
                selectedIndex = types.indexOf(selected).coerceAtLeast(0),
                onSelect = { viewModel.setSelectedContentType(types[it]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(top = 8.dp, bottom = 16.dp),
            )
        }
        val list: List<YTItem>? = when (selected) {
            AccountContentType.PLAYLISTS -> playlists
            AccountContentType.ALBUMS -> albums
            AccountContentType.ARTISTS -> artists
        }
        if (list == null) item(key = "loading") { LoadingItem() }
        else itemsGrid("acc_${selected.name}", list, actions, mediaMetadata?.id, isPlaying)
    }
}

@Suppress("unused")
private val keepAlbumItem = AlbumItem::class

@Composable
fun LiquidChartsScreen(
    navController: NavController,
    viewModel: com.shiny.music.viewmodels.ChartsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val charts by viewModel.chartsPage.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()

    LaunchedEffect(Unit) {
        if (viewModel.chartsPage.value == null) viewModel.loadCharts()
    }

    LargeTitlePage(
        title = stringResource(R.string.liquid_charts),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        val sections = charts?.sections
        if (sections == null) {
            item(key = "loading") { LoadingItem() }
        } else {
            sections.forEachIndexed { index, section ->
                if (section.items.isEmpty()) return@forEachIndexed
                val songs = section.items.filterIsInstance<SongItem>()
                item(key = "ch_header_$index") { SectionHeader(title = section.title) }
                if (songs.size == section.items.size) {
                    itemsIndexed(songs.take(20), key = { i, s -> "ch_${index}_${i}_${s.id}" }) { i, song ->
                        SongRow(
                            songId = song.id,
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            artwork = song.thumbnail,
                            leadingRank = i + 1,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            explicit = song.explicit,
                            onClick = { actions.playSongItems(section.title, songs, startIndex = i) },
                            onLongClick = { actions.menu(song) },
                            onMore = { actions.menu(song) },
                        )
                    }
                } else {
                    item(key = "ch_$index") {
                        Shelf(items = section.items, key = { "chi_${index}_${it.id}" }) { item ->
                            YouTubeTile(item, actions, isActive = item.id == mediaMetadata?.id, width = if (item is ArtistItem) 140.dp else 164.dp)
                        }
                    }
                }
            }
        }
    }
}
