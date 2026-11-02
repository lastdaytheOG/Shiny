package com.shiny.music.ui.liquid.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.music.innertube.utils.parseCookieString
import com.shiny.music.R
import com.shiny.music.constants.CombineDownloadsWithDeviceKey
import com.shiny.music.constants.InnerTubeCookieKey
import com.shiny.music.constants.ListenTogetherInTopBarKey
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.Song
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidIcons
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberListState
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.ImportedService
import com.shiny.music.viewmodels.LibraryCovers
import com.shiny.music.viewmodels.LibraryHit
import com.shiny.music.viewmodels.LibraryHomeViewModel
import com.shiny.music.viewmodels.LibraryPin

private enum class LibraryTab { All, Local, Cached, Imported }

@Composable
fun LibraryHomeScreen(
    navController: NavController,
    accountImageUrl: String? = null,
    viewModel: LibraryHomeViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val query by viewModel.query.collectAsState()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = LibraryTab.entries[tabIndex]
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val loggedIn = remember(cookie) { "SAPISID" in parseCookieString(cookie) }
    val state = rememberListState(viewModel.scroll)
    val scope = rememberCoroutineScope()
    val reveal = remember { Animatable(1f) }
    val seenTab = remember { intArrayOf(tabIndex) }

    LaunchedEffect(tabIndex) {
        if (seenTab[0] == tabIndex) return@LaunchedEffect
        seenTab[0] = tabIndex
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(620))
    }
    val progress = { reveal.value }

    val placeholder = stringResource(
        when (tab) {
            LibraryTab.All -> R.string.library_search_all
            LibraryTab.Local -> R.string.library_search_local
            LibraryTab.Cached -> R.string.library_search_cached
            LibraryTab.Imported -> R.string.library_search_imported
        },
    )
    val tabLabels = listOf(
        stringResource(R.string.library_tab_all),
        stringResource(R.string.library_tab_local),
        stringResource(R.string.cached),
        stringResource(R.string.library_tab_imported),
    )
    // only the open tab's list is collected, the cached one polls and shouldn't run when nobody's looking
    val songs = when (tab) {
        LibraryTab.Local -> viewModel.localSongs.collectAsStateWithLifecycle().value
        LibraryTab.Cached -> viewModel.cachedSongs.collectAsStateWithLifecycle().value
        else -> null
    }
    val shown = remember(songs, query) { songs?.matching(query) }
    // kept as State and read inside the rows, so a song change only redraws rows and not the whole tab
    val nowPlaying = actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying = actions.playerConnection.isPlaying.collectAsState()
    val haptics = LocalHapticFeedback.current
    val rows = SongRows(
        songs = shown,
        activeId = { nowPlaying.value?.id },
        isPlaying = { isPlaying.value },
        actions = actions,
        queueTitle = stringResource(if (tab == LibraryTab.Cached) R.string.cached else R.string.library_tab_local),
        held = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
    )

    LibraryScaffold(
        title = stringResource(R.string.liquid_library),
        state = state,
        query = query,
        onQueryChange = { viewModel.query.value = it },
        placeholder = placeholder,
        scrollToTopSignal = navController,
        avatar = {
            LibraryAvatar(
                imageUrl = if (loggedIn) accountImageUrl else null,
                onClick = { navController.navigate("settings") },
            )
        },
    ) {
        item(key = "tabs", contentType = "tabs") {
            LibraryChips(
                labels = tabLabels,
                selected = tabIndex,
                onSelect = { tabIndex = it },
                modifier = Modifier.padding(horizontal = PageMargin),
            )
            Spacer(Modifier.height(16.dp))
        }

        when (tab) {
            LibraryTab.All -> if (query.isBlank()) allTab(viewModel, navController, actions, progress) else searchHits(viewModel, actions, navController, query)
            LibraryTab.Local -> localTab(viewModel, navController, progress, query, rows, songs, { scope.launch { state.animateScrollToItem(2) } })
            LibraryTab.Cached -> cachedTab(viewModel, navController, progress, query, rows, songs)
            LibraryTab.Imported -> importedTab(viewModel, navController, progress, query)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryAvatar(imageUrl: String?, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.settings)
    Box(
        Modifier
            .size(38.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(colors.gray5)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            Artwork(model = imageUrl, shape = CircleShape, modifier = Modifier.fillMaxSize(), placeholder = Icons.Rounded.Person)
        } else {
            Icon(Icons.Rounded.Person, null, tint = colors.gray, modifier = Modifier.size(24.dp))
        }
    }
}

private fun LazyListScope.allTab(
    viewModel: LibraryHomeViewModel,
    navController: NavController,
    actions: LiquidActions,
    progress: () -> Float,
) {
    item(key = "all_body", contentType = "all_body") {
        val counts by viewModel.counts.collectAsState()
        val songCount by viewModel.songCount.collectAsState()
        val covers by viewModel.covers.collectAsState()
        val pins by viewModel.pins.collectAsState()
        val albumCount by viewModel.albumCount.collectAsState()
        val artistCount by viewModel.artistCount.collectAsState()
        val together by rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
        val empty = songCount == 0 && albumCount == 0 && artistCount == 0 && counts.playlists == 0 && counts.liked == 0 && pins.isEmpty()

        Column {
            var order = 0
            if (pins.isNotEmpty()) {
                Column(Modifier.libraryReveal(progress, order++)) {
                    LibrarySectionTitle(stringResource(R.string.library_pinned))
                    Spacer(Modifier.height(12.dp))
                    LibraryRule()
                    Spacer(Modifier.height(16.dp))
                    LibraryCardGrid(count = pins.size, columns = 3, spacing = 12.dp) { index, modifier ->
                        val pin = pins[index]
                        LibraryPinnedTile(
                            thumbnail = pin.thumbnail?.resize(300, 300),
                            onClick = { openPin(pin, actions, navController) },
                            onLongClick = { menuPin(pin, actions) },
                            modifier = modifier,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    LibraryRule()
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (empty) {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.liquid_library_empty_title),
                    message = stringResource(R.string.liquid_library_empty_message),
                    modifier = Modifier.libraryReveal(progress, order++),
                )
            } else {
                CategoryCards(counts.playlists, songCount, albumCount, artistCount, covers, navController, Modifier.libraryReveal(progress, order++))
                Spacer(Modifier.height(10.dp))
                LibraryWideCard(
                    title = stringResource(R.string.liquid_recently_added),
                    count = songCount,
                    covers = covers.songs,
                    onClick = { navController.navigate("library/added") },
                    modifier = Modifier
                        .padding(horizontal = PageMargin)
                        .libraryReveal(progress, order++),
                )
                if (counts.liked > 0) {
                    Spacer(Modifier.height(10.dp))
                    LibraryWideCard(
                        title = stringResource(R.string.liquid_liked_songs),
                        count = counts.liked,
                        covers = covers.liked,
                        onClick = { navController.navigate("auto_playlist/liked") },
                        front = { LikedArt(it) },
                        modifier = Modifier
                            .padding(horizontal = PageMargin)
                            .libraryReveal(progress, order++),
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Column(Modifier.libraryReveal(progress, order)) {
                LibraryCapsLabel(stringResource(R.string.library_more))
                Spacer(Modifier.height(10.dp))
                val shortcuts = buildList {
                    add(Shortcut(R.string.liquid_history, R.string.library_history_caption, Icons.Rounded.History, "history"))
                    add(Shortcut(R.string.liquid_stats, R.string.library_stats_caption, Icons.Rounded.BarChart, "stats"))
                    if (together) add(Shortcut(R.string.listen_together, R.string.library_together_caption, LiquidIcons.Waves, "together"))
                    add(Shortcut(R.string.recognition, R.string.library_recognition_caption, Icons.Rounded.GraphicEq, "recognition"))
                }
                LibraryCardGrid(count = shortcuts.size) { index, modifier ->
                    val shortcut = shortcuts[index]
                    LibraryShortcutCard(
                        title = stringResource(shortcut.title),
                        caption = stringResource(shortcut.caption),
                        icon = shortcut.icon,
                        onClick = { navController.navigate(shortcut.route) },
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

private class Shortcut(val title: Int, val caption: Int, val icon: ImageVector, val route: String)

@Composable
private fun CategoryCards(
    playlistCount: Int,
    songCount: Int,
    albumCount: Int,
    artistCount: Int,
    covers: LibraryCovers,
    navController: NavController,
    modifier: Modifier,
) {
    val entries = listOf(
        Triple(stringResource(R.string.songs), songCount to covers.songs, "library/songs"),
        Triple(stringResource(R.string.playlists), playlistCount to covers.playlists, "library/playlists"),
        Triple(stringResource(R.string.albums), albumCount to covers.albums, "library/albums"),
        Triple(stringResource(R.string.artists), artistCount to covers.artists, "library/artists"),
    )
    LibraryCardGrid(count = entries.size, modifier = modifier) { index, cell ->
        val (title, data, route) = entries[index]
        LibraryCategoryCard(
            title = title,
            count = data.first,
            covers = data.second.map { it.resize(160, 160) },
            onClick = { navController.navigate(route) },
            round = route == "library/artists",
            modifier = cell,
        )
    }
}

private fun openPin(pin: LibraryPin, actions: LiquidActions, navController: NavController) {
    when (pin) {
        is LibraryPin.PinnedPlaylist -> navController.navigate("local_playlist/${pin.playlist.id}")
        is LibraryPin.Saved -> runCatching { pin.item.toYTItem() }.getOrNull()?.let(actions::open)
    }
}

private fun menuPin(pin: LibraryPin, actions: LiquidActions) {
    when (pin) {
        is LibraryPin.PinnedPlaylist -> actions.menu(pin.playlist)
        is LibraryPin.Saved -> runCatching { pin.item.toYTItem() }.getOrNull()?.let(actions::menu)
    }
}

private fun LazyListScope.searchHits(
    viewModel: LibraryHomeViewModel,
    actions: LiquidActions,
    navController: NavController,
    query: String,
) {
    item(key = "hits", contentType = "hits") {
        val hits by viewModel.hits.collectAsState()
        val haptics = LocalHapticFeedback.current
        val held = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        val nowPlaying by actions.playerConnection.mediaMetadata.collectAsState()
        val isPlaying by actions.playerConnection.isPlaying.collectAsState()
        if (hits.isEmpty()) {
            LibraryEmptyLine(stringResource(R.string.library_no_results, query.trim()))
            return@item
        }
        Column {
            hits.forEach { hit ->
                when (hit) {
                    is LibraryHit.SongHit -> SongRow(
                        title = hit.song.title,
                        subtitle = hit.song.artists.joinToString { it.name },
                        artwork = hit.song.thumbnailUrl,
                        isActive = hit.song.id == nowPlaying?.id,
                        isPlaying = isPlaying,
                        explicit = hit.song.song.explicit,
                        onClick = {
                            if (hit.song.id == nowPlaying?.id) actions.togglePlayPause() else actions.open(hit.song)
                        },
                        onLongClick = { held(); actions.menu(hit.song) },
                    )
                    is LibraryHit.ArtistHit -> LibraryItemRow(
                        title = hit.artist.title,
                        subtitle = stringResource(R.string.library_kind_artist),
                        artwork = hit.artist.thumbnailUrl?.resize(240, 240),
                        round = true,
                        onClick = { navController.navigate("artist/${hit.artist.id}") },
                        onLongClick = { actions.menu(hit.artist) },
                    )
                    is LibraryHit.AlbumHit -> LibraryItemRow(
                        title = hit.album.title,
                        subtitle = listOf(stringResource(R.string.library_kind_album), hit.album.artists.joinToString { it.name })
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        artwork = hit.album.thumbnailUrl?.resize(240, 240),
                        onClick = { actions.open(hit.album) },
                        onLongClick = { actions.menu(hit.album) },
                    )
                    is LibraryHit.PlaylistHit -> LibraryItemRow(
                        title = hit.playlist.title,
                        subtitle = stringResource(R.string.library_kind_playlist) + " · " +
                            pluralStringResource(R.plurals.liquid_songs_count, hit.playlist.songCount, hit.playlist.songCount),
                        artwork = hit.playlist.thumbnails.firstOrNull(),
                        onClick = { navController.navigate("local_playlist/${hit.playlist.id}") },
                        onLongClick = { actions.menu(hit.playlist) },
                    )
                }
            }
        }
    }
}

private fun List<Song>.matching(query: String): List<Song> {
    val q = query.trim()
    if (q.isBlank()) return this
    return filter { song -> song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) } }
}

private class SongRows(
    val songs: List<Song>?,
    val activeId: () -> String?,
    val isPlaying: () -> Boolean,
    val actions: LiquidActions,
    val queueTitle: String,
    val held: () -> Unit,
)

private fun LazyListScope.localTab(
    viewModel: LibraryHomeViewModel,
    navController: NavController,
    progress: () -> Float,
    query: String,
    rows: SongRows,
    local: List<Song>?,
    onSongs: () -> Unit,
) {
    item(key = "local_head", contentType = "local_head") {
        val deviceCount by viewModel.deviceCount.collectAsState()
        val covers by viewModel.covers.collectAsState()
        Column {
            LibraryHeroCard(
                title = stringResource(R.string.library_device_title),
                count = deviceCount,
                caption = stringResource(R.string.library_device_caption),
                onClick = { navController.navigate("local_songs") },
                modifier = Modifier
                    .padding(horizontal = PageMargin)
                    .libraryReveal(progress, 0),
            )
            Spacer(Modifier.height(10.dp))
            LibraryWideCard(
                title = stringResource(R.string.songs),
                count = local?.size ?: 0,
                covers = remember(covers) { (covers.downloaded + covers.device).distinct().take(2).map { it.resize(200, 200) } },
                onClick = onSongs,
                modifier = Modifier
                    .padding(horizontal = PageMargin)
                    .libraryReveal(progress, 1),
            )
            Spacer(Modifier.height(14.dp))
            LibraryManagePill(
                text = stringResource(R.string.library_manage_local),
                onClick = { navController.navigate("settings/library") },
                modifier = Modifier.libraryReveal(progress, 2),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    songListSection("local", R.string.library_all_local, R.string.library_local_empty, local?.isEmpty(), query, rows, progress)
}

private fun LazyListScope.cachedTab(
    viewModel: LibraryHomeViewModel,
    navController: NavController,
    progress: () -> Float,
    query: String,
    rows: SongRows,
    cached: List<Song>?,
) {
    item(key = "cached_head", contentType = "cached_head") {
        val list = cached.orEmpty()
        Column {
            LibraryWideCard(
                title = stringResource(R.string.cached),
                count = list.size,
                covers = remember(list) { list.mapNotNull { it.thumbnailUrl }.distinct().take(2).map { it.resize(200, 200) } },
                onClick = { navController.navigate("cache_playlist/cached") },
                modifier = Modifier
                    .padding(horizontal = PageMargin)
                    .libraryReveal(progress, 0),
            )
            Spacer(Modifier.height(14.dp))
            LibraryManagePill(
                text = stringResource(R.string.library_manage_cache),
                onClick = { navController.navigate("settings/storage") },
                modifier = Modifier.libraryReveal(progress, 1),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    songListSection("cached", R.string.library_all_cached, R.string.library_cached_empty, cached?.isEmpty(), query, rows, progress)
}

private fun LazyListScope.songListSection(
    key: String,
    label: Int,
    emptyText: Int,
    empty: Boolean?,
    query: String,
    rows: SongRows,
    progress: () -> Float,
) {
    val shown = rows.songs ?: return
    item(key = "${key}_label", contentType = "song_label") {
        Column(Modifier.libraryReveal(progress, 3)) {
            when {
                empty == true -> LibraryEmptyLine(stringResource(emptyText))
                shown.isEmpty() -> LibraryEmptyLine(stringResource(R.string.library_no_results, query.trim()))
                else -> {
                    LibraryCapsLabel(stringResource(label))
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
    itemsIndexed(shown, key = { _, song -> "${key}_${song.id}" }, contentType = { _, _ -> "song" }) { index, song ->
        val active = rows.activeId() == song.id
        SongRow(
            title = song.title,
            subtitle = song.artists.joinToString { it.name },
            artwork = song.thumbnailUrl,
            isActive = active,
            isPlaying = active && rows.isPlaying(),
            explicit = song.song.explicit,
            showSeparator = index != shown.lastIndex,
            modifier = Modifier.libraryReveal(progress, 4 + index.coerceAtMost(6)),
            onClick = {
                if (active) rows.actions.togglePlayPause() else rows.actions.playSongs(rows.queueTitle, shown, index)
            },
            onLongClick = { rows.held(); rows.actions.menu(song) },
        )
    }
}

private fun LazyListScope.importedTab(
    viewModel: LibraryHomeViewModel,
    navController: NavController,
    progress: () -> Float,
    query: String,
) {
    item(key = "imported_head", contentType = "imported_head") {
        val services by viewModel.imported.collectAsState()
        Column {
            LibraryHeroCard(
                title = stringResource(R.string.library_import_title),
                count = null,
                caption = stringResource(R.string.library_import_caption),
                action = stringResource(R.string.library_import_action),
                onClick = { navController.navigate("settings/spotify_import") },
                modifier = Modifier
                    .padding(horizontal = PageMargin)
                    .libraryReveal(progress, 0),
            )
            Spacer(Modifier.height(22.dp))
            Column(Modifier.libraryReveal(progress, 1)) {
                LibraryCapsLabel(stringResource(R.string.playlists))
                Spacer(Modifier.height(10.dp))
                LibraryCardGrid(count = services.size) { index, modifier ->
                    val service = services[index]
                    LibraryCategoryCard(
                        title = service.name,
                        count = service.playlists.size,
                        covers = service.covers,
                        onClick = { if (service.playlists.isEmpty()) navController.navigate("settings/spotify_import") },
                        modifier = modifier,
                    )
                }
            }
        }
    }
    item(key = "imported_lists", contentType = "imported_lists") {
        val services by viewModel.imported.collectAsState()
        Column(Modifier.libraryReveal(progress, 2)) {
            services.forEach { service -> ImportedServiceList(service, query, navController) }
            Spacer(Modifier.height(22.dp))
            LibraryManagePill(
                text = stringResource(R.string.library_manage_imports),
                onClick = { navController.navigate("settings/spotify_import") },
            )
        }
    }
}

@Composable
private fun ImportedServiceList(service: ImportedService, query: String, navController: NavController) {
    val shown = remember(service, query) {
        val q = query.trim()
        if (q.isBlank()) service.playlists else service.playlists.filter { it.title.contains(q, true) }
    }
    if (shown.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    LibraryCapsLabel(stringResource(R.string.library_from, service.name))
    Spacer(Modifier.height(6.dp))
    shown.forEach { playlist -> PlaylistRow(playlist, navController) }
}

@Composable
private fun PlaylistRow(playlist: Playlist, navController: NavController) {
    LibraryItemRow(
        title = playlist.title,
        subtitle = pluralStringResource(R.plurals.liquid_songs_count, playlist.songCount, playlist.songCount),
        artwork = playlist.thumbnails.firstOrNull(),
        placeholder = Icons.AutoMirrored.Rounded.QueueMusic,
        onClick = { navController.navigate("local_playlist/${playlist.id}") },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemRow(
    title: String,
    subtitle: String?,
    artwork: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    round: Boolean = false,
    artworkSize: Dp = 54.dp,
    placeholder: ImageVector = if (round) Icons.Rounded.Mic else Icons.AutoMirrored.Rounded.QueueMusic,
    leading: (@Composable () -> Unit)? = null,
    titleColor: androidx.compose.ui.graphics.Color = Liquid.colors.label,
) {
    val colors = Liquid.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRowHighlight(),
                onClick = onClick,
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
            )
            .padding(horizontal = PageMargin, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else {
            Artwork(
                model = artwork,
                shape = if (round) CircleShape else RoundedCornerShape(12.dp),
                placeholder = placeholder,
                modifier = Modifier.size(artworkSize),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(title, style = LiquidTypography.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = LiquidTypography.subheadline, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
