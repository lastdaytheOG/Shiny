package com.shiny.music.ui.liquid.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.SongItem
import com.shiny.music.LocalDatabase
import com.shiny.music.R
import com.shiny.music.playback.SmartShuffleDraw
import com.shiny.music.constants.MyTopFilter
import com.shiny.music.constants.PlaylistSongSortType
import com.shiny.music.constants.PlaylistSongSortTypeKey
import com.shiny.music.db.entities.PlaylistEntity
import com.shiny.music.db.entities.PlaylistSong
import com.shiny.music.db.entities.PlaylistSongMap
import com.shiny.music.db.entities.Song
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.models.toMediaMetadata
import com.shiny.music.playback.queues.ListQueue
import com.shiny.music.playback.queues.YouTubePlaylistQueue
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.menu.PlaylistMenu
import com.shiny.music.ui.menu.SongMenu
import com.shiny.music.ui.menu.YouTubePlaylistMenu
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.viewmodels.AutoPlaylistViewModel
import com.shiny.music.viewmodels.CachePlaylistViewModel
import com.shiny.music.viewmodels.LocalPlaylistViewModel
import com.shiny.music.viewmodels.OnlinePlaylistViewModel
import com.shiny.music.viewmodels.SharedPlaylistState
import com.shiny.music.viewmodels.SharedPlaylistViewModel
import com.shiny.music.viewmodels.TopPlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LiquidOnlinePlaylistScreen(
    navController: NavController,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playerConnection = actions.playerConnection
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val related by viewModel.relatedItems.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState(initial = null)
    val listState = rememberLazyListState()

    val current = playlist
    if (current == null) {
        LiquidLoadingPage(onBack = { navController.navigateUp() })
        return
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (last != null && total > 0 && last >= total - 6) viewModel.loadMoreSongs()
            }
    }

    fun queueFrom(index: Int, shuffled: Boolean = false) = YouTubePlaylistQueue(
        playlistId = current.id,
        playlistTitle = current.title,
        initialSongs = if (shuffled) songs.shuffled() else songs,
        initialContinuation = viewModel.continuation,
        startIndex = if (shuffled) 0 else index,
    )

    val saved = dbPlaylist?.playlist?.bookmarkedAt != null
    val footerText = stringResource(R.string.liquid_songs_count, songs.size)

    LiquidCollectionPage(
        title = current.title,
        subtitle = current.author?.name,
        onSubtitleClick = current.author?.id?.let { id -> { navController.navigate("artist/$id") } },
        meta = listOfNotNull(stringResource(R.string.liquid_playlist), current.songCountText).joinToString(" · "),
        artwork = current.thumbnail?.resize(1080, 1080),
        toneSource = current.thumbnail?.resize(544, 544),
        playEnabled = songs.isNotEmpty(),
        listState = listState,
        onBack = { navController.navigateUp() },
        onPlay = { if (songs.isNotEmpty()) playerConnection.playQueue(queueFrom(0)) },
        onShuffle = { if (songs.isNotEmpty()) playerConnection.playQueue(queueFrom(0, shuffled = true)) },
        // Over the songs loaded so far; scrolling loads the rest of a long playlist.
        onSmartShuffle = rememberSmartShuffle(
            items = songs,
            id = { it.id },
            artist = { it.artists.firstOrNull()?.name },
            play = { ordered -> actions.playPreShuffledItems(current.title, ordered) },
        ),
        topActions = { ink ->
            GlassIconButton(
                icon = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val existing = dbPlaylist
                        if (existing != null) {
                            database.withTransaction {
                                update(existing.playlist, current)
                                update(existing.playlist.toggleLike())
                            }
                        } else {
                            database.withTransaction {
                                val entity = PlaylistEntity(
                                    name = current.title,
                                    browseId = current.id,
                                    thumbnailUrl = current.thumbnail,
                                    isEditable = current.isEditable,
                                    remoteSongCount = current.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                                    playEndpointParams = current.playEndpoint?.params,
                                    shuffleEndpointParams = current.shuffleEndpoint?.params,
                                    radioEndpointParams = current.radioEndpoint?.params,
                                ).toggleLike()
                                insert(entity)
                                songs.map { it.toMediaMetadata() }
                                    .onEach { insert(it) }
                                    .mapIndexed { index, song ->
                                        PlaylistSongMap(songId = song.id, playlistId = entity.id, position = index, setVideoId = song.setVideoId)
                                    }
                                    .forEach { insert(it) }
                            }
                        }
                    }
                },
                tint = ink.primary,
                kind = GlassKind.Clear,
            )
            GlassIconButton(
                icon = Icons.Rounded.MoreHoriz,
                onClick = {
                    menuState.show {
                        YouTubePlaylistMenu(
                            playlist = current,
                            songs = songs,
                            coroutineScope = scope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                tint = ink.primary,
                kind = GlassKind.Clear,
            )
        },
    ) { ink ->
        itemsIndexed(songs, key = { index, s -> "ps_${index}_${s.id}" }) { index, song ->
            SongRow(
                songId = song.id,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                artwork = song.thumbnail,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                explicit = song.explicit,
                onClick = {
                    if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause()
                    else playerConnection.playQueue(queueFrom(index))
                },
                onLongClick = { actions.menu(song) },
                onMore = { actions.menu(song) },
                titleColor = ink.primary,
                subtitleColor = ink.secondary,
                separatorColor = ink.separator,
                accent = ink.primary,
                showSeparator = index != songs.lastIndex,
            )
        }

        collectionFooter(listOf(footerText), ink)

        if (related.isNotEmpty()) {
            item(key = "related_header") {
                SectionHeader(title = stringResource(R.string.liquid_you_might_also_like), color = ink.primary, secondaryColor = ink.secondary)
            }
            item(key = "related") {
                Shelf(items = related.distinctBy { it.id }, key = { "rel_${it.id}" }) { item ->
                    MediaTile(
                        songId = (item as? com.music.innertube.models.SongItem)?.id,
                        title = item.title,
                        subtitle = item.subtitleText(),
                        artwork = item.thumbnail?.resize(544, 544),
                        circular = item is com.music.innertube.models.ArtistItem,
                        onClick = { actions.open(item) },
                        onLongClick = { actions.menu(item) },
                        titleColor = ink.primary,
                        subtitleColor = ink.secondary,
                    )
                }
            }
        }
    }
}

@Composable
fun LiquidSharedPlaylistScreen(
    navController: NavController,
    viewModel: SharedPlaylistViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playerConnection = actions.playerConnection
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val state by viewModel.state.collectAsState()
    val inLibrary by viewModel.inLibrary.collectAsState()

    val current = when (val loaded = state) {
        SharedPlaylistState.Loading -> {
            LiquidLoadingPage(onBack = { navController.navigateUp() })
            return
        }
        is SharedPlaylistState.Failed -> {
            SharedPlaylistUnavailable(gone = loaded.gone, onRetry = viewModel::load, onBack = { navController.navigateUp() })
            return
        }
        is SharedPlaylistState.Ready -> loaded
    }
    val songs = current.songs
    val covers = remember(songs) { songs.map { it.thumbnail }.distinct().take(4) }
    val footerText = stringResource(R.string.liquid_songs_count, songs.size)

    LiquidCollectionPage(
        title = current.name,
        subtitle = null,
        meta = stringResource(R.string.liquid_playlist) + " · " + footerText,
        artwork = null,
        collage = covers,
        toneSource = covers.firstOrNull(),
        playEnabled = songs.isNotEmpty(),
        onBack = { navController.navigateUp() },
        onPlay = { actions.playSongItems(current.name, songs) },
        onShuffle = { actions.playSongItems(current.name, songs, shuffle = true) },
        onSmartShuffle = rememberSmartShuffle(
            items = songs,
            id = { it.id },
            artist = { it.artists.firstOrNull()?.name },
            play = { ordered -> actions.playPreShuffledItems(current.name, ordered) },
        ),
        topActions = { ink ->
            // Added once, it is a playlist of the library like any other: the tick goes to it.
            GlassIconButton(
                icon = if (inLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                onClick = {
                    if (inLibrary) navController.navigate("local_playlist/${viewModel.libraryPlaylistId}")
                    else viewModel.addToLibrary()
                },
                tint = ink.primary,
                kind = GlassKind.Clear,
            )
        },
    ) { ink ->
        itemsIndexed(songs, key = { index, s -> "sp_${index}_${s.id}" }) { index, song ->
            SongRow(
                songId = song.id,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                artwork = song.thumbnail,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                explicit = song.explicit,
                onClick = {
                    if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause()
                    else actions.playSongItems(current.name, songs, startIndex = index)
                },
                onLongClick = { actions.menu(song) },
                onMore = { actions.menu(song) },
                titleColor = ink.primary,
                subtitleColor = ink.secondary,
                separatorColor = ink.separator,
                accent = ink.primary,
                showSeparator = index != songs.lastIndex,
            )
        }

        collectionFooter(listOf(footerText), ink)
    }
}

/** The link's playlist didn't load: it leads nowhere ([gone]), or the server wasn't reached. */
@Composable
private fun SharedPlaylistUnavailable(gone: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    val colors = Liquid.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        EmptyState(
            icon = Icons.Rounded.LinkOff,
            title = stringResource(if (gone) R.string.shared_playlist_gone else R.string.shared_playlist_unreachable),
            message = stringResource(if (gone) R.string.shared_playlist_gone_body else R.string.shared_playlist_unreachable_body),
            modifier = Modifier.align(Alignment.Center),
            actionLabel = if (gone) null else stringResource(R.string.retry),
            onAction = if (gone) null else onRetry,
        )
        GlassIconButton(
            icon = Icons.Rounded.ChevronLeft,
            onClick = onBack,
            iconSize = 28.dp,
            tint = colors.label,
            kind = GlassKind.Clear,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = statusBarHeight() + 4.dp, start = 14.dp),
        )
    }
}

@Composable
fun LiquidLocalPlaylistScreen(
    navController: NavController,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playerConnection = actions.playerConnection
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val playlist by viewModel.playlist.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val sortType by rememberEnumPreference(PlaylistSongSortTypeKey, PlaylistSongSortType.CUSTOM)
    var editing by rememberSaveable { mutableStateOf(false) }

    val current = playlist
    if (current == null) {
        LiquidLoadingPage(onBack = { navController.navigateUp() })
        return
    }

    LaunchedEffect(current.id) { viewModel.loadSuggestions() }

    val rows = remember { mutableStateListOf<PlaylistSong>() }
    LaunchedEffect(playlistSongs) {
        rows.clear()
        rows.addAll(playlistSongs)
    }

    val listState = rememberLazyListState()
    val headerCount = 1
    var dragRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.index - headerCount
        val t = to.index - headerCount
        if (f in rows.indices && t in rows.indices) {
            rows.add(t, rows.removeAt(f))
            dragRange = (dragRange?.first ?: f) to t
        }
    }
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging) {
            dragRange?.let { (f, t) ->
                if (f != t) database.transaction { move(viewModel.playlistId, f, t) }
            }
            dragRange = null
        }
    }

    // Derived from the rows rather than recomputed on every recomposition of the page (each
    // play/pause and track change): an imported playlist can hold thousands of songs.
    val songs by remember { derivedStateOf { rows.map { it.song } } }
    val totalMinutes by remember { derivedStateOf { songs.sumOf { it.song.duration } / 60 } }
    val canReorder = current.playlist.isEditable && sortType == PlaylistSongSortType.CUSTOM
    val thumbs = current.thumbnails
    val footerText = stringResource(R.string.liquid_songs_minutes, rows.size, totalMinutes)

    LiquidCollectionPage(
        title = current.playlist.name,
        subtitle = null,
        meta = stringResource(R.string.liquid_songs_count, rows.size),
        artwork = current.playlist.thumbnailUrl,
        collage = thumbs,
        toneSource = current.playlist.thumbnailUrl ?: thumbs.firstOrNull(),
        playEnabled = songs.isNotEmpty(),
        listState = listState,
        onBack = { navController.navigateUp() },
        onPlay = { actions.playSongs(current.playlist.name, songs) },
        onShuffle = { actions.playSongs(current.playlist.name, songs, shuffle = true) },
        onSmartShuffle = rememberSmartShuffle(
            items = songs,
            id = { it.id },
            artist = { it.artists.firstOrNull()?.name },
            play = { ordered -> actions.playPreShuffled(current.playlist.name, ordered) },
        ),
        topActions = { ink ->
            if (canReorder) {
                GlassIconButton(
                    icon = if (editing) Icons.Rounded.Check else Icons.Rounded.Edit,
                    onClick = { editing = !editing },
                    tint = ink.primary,
                    iconSize = 19.dp,
                    kind = GlassKind.Clear,
                )
            }
            GlassIconButton(
                icon = Icons.Rounded.MoreHoriz,
                onClick = {
                    menuState.show {
                        PlaylistMenu(
                            playlist = current,
                            coroutineScope = scope,
                            onDismiss = menuState::dismiss,
                            songList = songs,
                        )
                    }
                },
                tint = ink.primary,
                kind = GlassKind.Clear,
            )
        },
    ) { ink ->
        itemsIndexed(
            rows,
            key = { _, row -> "lp_${row.map.id}" },
            contentType = { _, _ -> "playlist_song" },
        ) { index, row ->
            val openMenu = {
                menuState.show {
                    SongMenu(
                        originalSong = row.song,
                        playlistSong = row,
                        playlistBrowseId = current.playlist.browseId,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
            val songRow: @Composable (trailing: (@Composable RowScope.() -> Unit)?) -> Unit = { trailing ->
                SongRow(
                    songId = row.song.id,
                    title = row.song.title,
                    subtitle = row.song.artists.joinToString { it.name },
                    artwork = row.song.thumbnailUrl,
                    isActive = row.song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    explicit = row.song.song.explicit,
                    onClick = {
                        if (row.song.id == mediaMetadata?.id) playerConnection.togglePlayPause()
                        else playerConnection.playQueue(
                            ListQueue(
                                title = current.playlist.name,
                                items = songs.map { it.toMediaItem() },
                                startIndex = index,
                            )
                        )
                    },
                    onLongClick = openMenu,
                    onMore = if (editing) null else openMenu,
                    trailing = trailing,
                    titleColor = ink.primary,
                    subtitleColor = ink.secondary,
                    separatorColor = ink.separator,
                    accent = ink.primary,
                    showSeparator = index != rows.lastIndex,
                )
            }
            // Only a list being reordered is wrapped for it. A ReorderableItem reads every
            // visible row's position in the window on each scroll frame and animates its
            // placement, which a playlist that is only being scrolled and played from pays
            // for on every frame — the stutter on long imported playlists.
            if (editing && canReorder) {
                ReorderableItem(reorderState, key = "lp_${row.map.id}") { _ ->
                    songRow {
                        Box(
                            Modifier
                                .size(48.dp)
                                .draggableHandle(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.DragHandle, null, tint = ink.secondary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            } else {
                songRow(null)
            }
        }

        collectionFooter(listOf(footerText), ink)

        if (suggestions.isNotEmpty() && current.playlist.isEditable) {
            item(key = "suggestions_header") {
                SectionHeader(title = stringResource(R.string.liquid_you_might_also_like), color = ink.primary, secondaryColor = ink.secondary)
            }
            items(suggestions.take(8), key = { "sug_${it.id}" }) { song ->
                SongRow(
                    songId = song.id,
                    title = song.title,
                    subtitle = song.artists.joinToString { it.name },
                    artwork = song.thumbnail,
                    onClick = { actions.open(song) },
                    onLongClick = { actions.menu(song) },
                    trailing = {
                        GlassIconButton(
                            icon = Icons.Rounded.Add,
                            onClick = { viewModel.addSuggestion(song) },
                            size = 34.dp,
                            iconSize = 18.dp,
                            tint = ink.primary,
                            kind = GlassKind.Clear,
                            backdrop = null,
                        )
                    },
                    titleColor = ink.primary,
                    subtitleColor = ink.secondary,
                    separatorColor = ink.separator,
                    accent = ink.primary,
                )
            }
        }
    }
}

@Composable
fun LiquidAutoPlaylistScreen(
    navController: NavController,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    val songs by viewModel.likedSongs.collectAsState()
    val title = when (viewModel.playlist) {
        "liked" -> stringResource(R.string.liquid_liked_songs)
        "downloaded" -> stringResource(R.string.liquid_downloaded)
        "uploaded" -> stringResource(R.string.liquid_library)
        else -> stringResource(R.string.liquid_playlist)
    }
    LaunchedEffect(viewModel.playlist) {
        if (viewModel.playlist == "liked") viewModel.syncLikedSongs()
    }
    SongCollection(
        navController = navController,
        title = title,
        songs = songs,
    )
}

/**
 * Smart Shuffle for any collection: the order is drawn once per tap, off the main thread,
 * from each song's plays and when it last played ([com.shiny.music.playback.SmartShuffle]),
 * then handed to [play], which starts it with the player keeping that order.
 */
@Composable
private fun <T> rememberSmartShuffle(
    items: List<T>,
    id: (T) -> String,
    artist: (T) -> String?,
    play: (List<T>) -> Unit,
): () -> Unit {
    val database = LocalDatabase.current
    val playerConnection = com.shiny.music.LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val latestItems by rememberUpdatedState(items)
    val latestPlay by rememberUpdatedState(play)
    var drawing by remember { mutableStateOf(false) }
    return remember {
        {
            val list = latestItems
            if (!drawing && list.isNotEmpty()) {
                drawing = true
                scope.launch {
                    try {
                        val order = SmartShuffleDraw.order(
                            database,
                            list.map { SmartShuffleDraw.Candidate(id(it), artist(it)) },
                            playingId = playerConnection?.mediaMetadata?.value?.id,
                        )
                        val byId = list.associateBy(id)
                        latestPlay(order.mapNotNull { byId[it] })
                    } finally {
                        drawing = false
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidTopPlaylistScreen(
    navController: NavController,
    viewModel: TopPlaylistViewModel = hiltViewModel(),
) {
    val songs by viewModel.topSongs.collectAsState()
    val period by viewModel.topPeriod.collectAsState()
    val periods = listOf(MyTopFilter.ALL_TIME, MyTopFilter.YEAR, MyTopFilter.MONTH, MyTopFilter.WEEK, MyTopFilter.DAY)
    val labels = listOf("All", "Year", "Month", "Week", "Day")
    SongCollection(
        navController = navController,
        title = "${stringResource(R.string.my_top)} ${viewModel.top}",
        songs = songs,
        headerExtra = {
            LiquidSegmentedControl(
                items = labels,
                selectedIndex = periods.indexOf(period).coerceAtLeast(0),
                onSelect = { viewModel.topPeriod.value = periods[it] },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(top = 16.dp),
            )
        },
    )
}

@Composable
fun LiquidCachePlaylistScreen(
    navController: NavController,
    viewModel: CachePlaylistViewModel = hiltViewModel(),
) {
    val songs by viewModel.cachedSongs.collectAsState()
    SongCollection(navController = navController, title = stringResource(R.string.liquid_local_files), songs = songs)
}

/** A generated collection: covers of its own songs, then the songs. */
@Composable
private fun SongCollection(
    navController: NavController,
    title: String,
    songs: List<Song>,
    headerExtra: (@Composable () -> Unit)? = null,
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playerConnection = actions.playerConnection
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val thumbs = remember(songs) { songs.mapNotNull { it.thumbnailUrl }.distinct().take(4) }
    val totalMinutes = songs.sumOf { it.song.duration } / 60
    val footerText = stringResource(R.string.liquid_songs_minutes, songs.size, totalMinutes)

    LiquidCollectionPage(
        title = title,
        subtitle = null,
        meta = stringResource(R.string.liquid_songs_count, songs.size),
        artwork = if (thumbs.size < 4) thumbs.firstOrNull() else null,
        collage = thumbs,
        toneSource = thumbs.firstOrNull(),
        playEnabled = songs.isNotEmpty(),
        onBack = { navController.navigateUp() },
        onPlay = { actions.playSongs(title, songs) },
        onShuffle = { actions.playSongs(title, songs, shuffle = true) },
        headerExtra = headerExtra,
        onSmartShuffle = rememberSmartShuffle(
            items = songs,
            id = { it.id },
            artist = { it.artists.firstOrNull()?.name },
            play = { ordered -> actions.playPreShuffled(title, ordered) },
        ),
    ) { ink ->
        if (songs.isEmpty()) {
            item(key = "empty") {
                Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    ActivityIndicator(color = ink.secondary)
                }
            }
        }
        itemsIndexed(songs, key = { _, s -> "sc_${s.id}" }) { index, song ->
            SongRow(
                songId = song.id,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                artwork = song.thumbnailUrl,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                explicit = song.song.explicit,
                onClick = {
                    if (song.id == mediaMetadata?.id) playerConnection.togglePlayPause()
                    else actions.playSongs(title, songs, startIndex = index)
                },
                onLongClick = { actions.menu(song) },
                onMore = { actions.menu(song) },
                titleColor = ink.primary,
                subtitleColor = ink.secondary,
                separatorColor = ink.separator,
                accent = ink.primary,
                showSeparator = index != songs.lastIndex,
            )
        }
        collectionFooter(listOf(footerText), ink)
    }
}

@Suppress("unused")
private val keepSongItem = SongItem::class
