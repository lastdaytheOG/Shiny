package com.shiny.music.ui.liquid.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Song
import com.shiny.music.ui.component.CreatePlaylistDialog
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.viewmodels.LibraryAlbumsViewModel
import com.shiny.music.viewmodels.LibraryArtistsViewModel
import com.shiny.music.viewmodels.LibraryHomeViewModel
import com.shiny.music.viewmodels.LibraryPlaylistsViewModel
import com.shiny.music.viewmodels.LibrarySongsViewModel
import kotlinx.coroutines.launch

private val RailGutter = 14.dp
private val RowCover = 54.dp
private val RowCoverShape = RoundedCornerShape(14.dp)
private val TileShape = RoundedCornerShape(24.dp)

private sealed interface IndexedRow<out T> {
    data class Header(val section: IndexSection) : IndexedRow<Nothing>
    data class Entry<T>(val item: T, val position: Int) : IndexedRow<T>
}

private class IndexedList<T>(
    val rows: List<IndexedRow<T>>,
    val ordered: List<T>,
    val sections: List<IndexSection>,
    val positions: Map<String, Int>,
)

// flattens the sections into one list for the lazy column and remembers where each letter starts
private fun <T> buildIndexed(items: List<T>, leading: Int, nameOf: (T) -> String): IndexedList<T> {
    val grouped = indexSections(items, nameOf)
    val rows = ArrayList<IndexedRow<T>>(items.size + grouped.size)
    val ordered = ArrayList<T>(items.size)
    val positions = HashMap<String, Int>()
    grouped.forEach { (section, entries) ->
        positions[section.key] = leading + rows.size
        rows += IndexedRow.Header(section)
        entries.forEach {
            rows += IndexedRow.Entry(it, ordered.size)
            ordered += it
        }
    }
    return IndexedList(rows, ordered, grouped.map { it.first }, positions)
}

private fun <T> LazyListScope.indexedItems(
    indexed: IndexedList<T>,
    idOf: (T) -> String,
    row: @Composable (T, Int) -> Unit,
) {
    items(
        items = indexed.rows,
        key = { r ->
            when (r) {
                is IndexedRow.Header -> "h_${r.section.key}"
                is IndexedRow.Entry -> "e_${idOf(r.item)}"
            }
        },
        contentType = { r -> if (r is IndexedRow.Header) "header" else "entry" },
    ) { r ->
        when (r) {
            is IndexedRow.Header -> LetterHeader(r.section.label)
            is IndexedRow.Entry -> row(r.item, r.position)
        }
    }
}

@Composable
private fun LetterHeader(label: String) {
    Text(
        text = label,
        color = Liquid.colors.label,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
        modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 14.dp, bottom = 6.dp),
    )
}

private fun LazyListScope.topRule() {
    item(key = "rule", contentType = "rule") {
        LibraryRule()
        Spacer(Modifier.height(10.dp))
    }
}

private fun LazyListScope.emptyLine(text: @Composable () -> String) {
    item(key = "empty", contentType = "empty") { LibraryEmptyLine(text()) }
}

@Composable
private fun matchingText(query: String): String =
    if (query.isNotBlank()) stringResource(R.string.library_no_results, query.trim())
    else stringResource(R.string.liquid_library_empty_title)

@Composable
private fun railTop(): Dp = statusBarHeight() + 18.dp + 48.dp + 28.dp

// scrollToItem leaves the letter under the collapsed header, so check where it landed and nudge it down
@Composable
private fun rememberRailJump(state: LazyListState): (Int) -> Unit {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val landing = with(density) { (statusBarHeight() + 18.dp + 48.dp + 24.dp).roundToPx() }
    return remember(state, landing) {
        { index ->
            scope.launch {
                state.scrollToItem(index)
                val layout = state.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
                state.scrollBy((item.offset - layout.viewportStartOffset - landing).toFloat())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryTrackRow(
    title: String,
    subtitle: String?,
    artwork: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    round: Boolean = false,
    active: Boolean = false,
    placeholder: ImageVector = Icons.Rounded.MusicNote,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = Liquid.colors.label,
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
            .padding(horizontal = PageMargin, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else {
            Artwork(
                model = artwork,
                shape = if (round) CircleShape else RowCoverShape,
                placeholder = placeholder,
                hairline = false,
                modifier = Modifier.size(RowCover),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (active) colors.accent else titleColor,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = colors.label.copy(alpha = 0.5f),
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// same pill and circle as the playlist pages, just softer so it doesn't fight the covers
@Composable
private fun SoftPlayBar(onPlay: () -> Unit, onShuffle: () -> Unit, enabled: Boolean) {
    val colors = Liquid.colors
    val fill = if (colors.isDark) Color.White.copy(alpha = 0.64f) else Color.Black.copy(alpha = 0.08f)
    val ink = if (colors.isDark) Color(0xFF2F2F2F) else colors.label
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val play = remember { MutableInteractionSource() }
    val shuffle = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(horizontal = PageMargin)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .pressScale(play, 0.95f)
                .clip(CircleShape)
                .background(fill)
                .clickable(interactionSource = play, indication = null, enabled = enabled, role = Role.Button, onClick = onPlay)
                .height(36.dp)
                .padding(start = 18.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.liquid_play), color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .size(36.dp)
                .pressScale(shuffle, 0.9f)
                .clip(CircleShape)
                .background(fill)
                .clickable(interactionSource = shuffle, indication = null, enabled = enabled, role = Role.Button) {
                    scope.launch {
                        spin.snapTo(0f)
                        spin.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
                    }
                    onShuffle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.liquid_shuffle),
                tint = ink,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = spin.value * 360f },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(
    title: String,
    subtitle: String?,
    artwork: Any?,
    placeholder: ImageVector,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    active: Boolean = false,
) {
    val colors = Liquid.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction, 0.96f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Artwork(
            model = artwork,
            shape = TileShape,
            placeholder = placeholder,
            hairline = false,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = if (active) colors.accent else colors.label.copy(alpha = 0.92f),
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = colors.label.copy(alpha = 0.5f),
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun <T> LazyListScope.grid(
    entries: List<T>,
    key: (T) -> String,
    tile: @Composable (T, Int, Modifier) -> Unit,
) {
    val rows = entries.chunked(2)
    items(rows.size, key = { "g_${key(rows[it].first())}" }, contentType = { "grid_row" }) { index ->
        val pair = rows[index]
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PageMargin)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            pair.forEachIndexed { i, entry -> tile(entry, index * 2 + i, Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun LibrarySongsPage(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val songs by viewModel.everySong.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val state = rememberLazyListState()
    val jump = rememberRailJump(state)
    val shown = remember(songs, query) {
        val q = query.trim()
        if (q.isBlank()) songs else songs.filter { song ->
            song.title.contains(q, true) ||
                song.artists.any { it.name.contains(q, true) } ||
                song.song.albumName.orEmpty().contains(q, true)
        }
    }
    val indexed = remember(shown) { buildIndexed(shown, 1) { it.title } }
    val nowPlaying = actions.playerConnection.mediaMetadata.collectAsState()
    val title = stringResource(R.string.songs)

    LibraryScaffold(
        title = title,
        subtitle = stringResource(R.string.library_songs_subtitle),
        state = state,
        query = query,
        onQueryChange = { query = it },
        placeholder = stringResource(R.string.library_find_song),
        overlay = {
            LibraryIndexRail(
                sections = indexed.sections,
                onSelect = { key -> indexed.positions[key]?.let(jump) },
                topInset = railTop(),
                bottomInset = liquidBottomPadding(),
            )
        },
    ) {
        topRule()
        if (indexed.ordered.isEmpty()) emptyLine { matchingText(query) }
        indexedItems(indexed, idOf = { it.id }) { song, position ->
            val active = nowPlaying.value?.id == song.id
            LibraryTrackRow(
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                artwork = song.thumbnailUrl,
                active = active,
                modifier = Modifier.padding(end = RailGutter),
                onClick = {
                    if (active) actions.togglePlayPause() else actions.playSongs(title, indexed.ordered, startIndex = position)
                },
                onLongClick = { actions.menu(song) },
            )
        }
    }
}

@Composable
fun LibraryArtistsPage(
    navController: NavController,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val artists by viewModel.libraryArtists.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val state = rememberLazyListState()
    val jump = rememberRailJump(state)
    val shown = remember(artists, query) {
        val q = query.trim()
        if (q.isBlank()) artists else artists.filter { it.title.contains(q, true) }
    }
    val indexed = remember(shown) { buildIndexed(shown, 1) { it.title } }

    LibraryScaffold(
        title = stringResource(R.string.artists),
        subtitle = stringResource(R.string.library_artists_subtitle),
        state = state,
        query = query,
        onQueryChange = { query = it },
        placeholder = stringResource(R.string.library_find_artist),
        overlay = {
            LibraryIndexRail(
                sections = indexed.sections,
                onSelect = { key -> indexed.positions[key]?.let(jump) },
                topInset = railTop(),
                bottomInset = liquidBottomPadding(),
            )
        },
    ) {
        topRule()
        if (indexed.ordered.isEmpty()) emptyLine { matchingText(query) }
        indexedItems(indexed, idOf = { it.id }) { artist, _ ->
            LibraryTrackRow(
                title = artist.title,
                subtitle = artist.songCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.liquid_songs_count, it, it) },
                artwork = artist.thumbnailUrl?.resize(240, 240),
                round = true,
                placeholder = Icons.Rounded.Mic,
                modifier = Modifier.padding(end = RailGutter),
                onClick = { navController.navigate("artist/${artist.id}") },
                onLongClick = { actions.menu(artist) },
            )
        }
    }
}

@Composable
fun LibraryAlbumsPage(
    navController: NavController,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val albums by viewModel.libraryAlbums.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val state = rememberLazyListState()
    val shown = remember(albums, query) {
        val q = query.trim()
        if (q.isBlank()) albums else albums.filter { album ->
            album.title.contains(q, true) || album.artists.any { it.name.contains(q, true) }
        }
    }

    LibraryScaffold(
        title = stringResource(R.string.albums),
        subtitle = stringResource(R.string.library_albums_subtitle),
        state = state,
        query = query,
        onQueryChange = { query = it },
        placeholder = stringResource(R.string.library_find_album),
    ) {
        topRule()
        if (shown.isEmpty()) emptyLine { matchingText(query) }
        grid(shown, key = { it.id }) { album, _, modifier -> AlbumTile(album, actions, modifier) }
    }
}

@Composable
private fun AlbumTile(album: Album, actions: LiquidActions, modifier: Modifier) {
    GridTile(
        title = album.title,
        subtitle = album.artists.joinToString { it.name },
        artwork = album.thumbnailUrl?.resize(544, 544),
        placeholder = Icons.Rounded.Album,
        onClick = { actions.open(album) },
        onLongClick = { actions.menu(album) },
        modifier = modifier,
    )
}

@Composable
fun LibraryAddedPage(
    navController: NavController,
    viewModel: LibraryHomeViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val songs by viewModel.recentSongs.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val state = rememberLazyListState()
    val shown = remember(songs, query) {
        val q = query.trim()
        if (q.isBlank()) songs else songs.filter { song ->
            song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) }
        }
    }
    val nowPlaying = actions.playerConnection.mediaMetadata.collectAsState()
    val title = stringResource(R.string.liquid_recently_added)

    LibraryScaffold(
        title = stringResource(R.string.library_added_title),
        subtitle = stringResource(R.string.library_added_subtitle),
        state = state,
        query = query,
        onQueryChange = { query = it },
        placeholder = stringResource(R.string.library_find_added),
    ) {
        topRule()
        item(key = "play", contentType = "play") {
            SoftPlayBar(
                enabled = shown.isNotEmpty(),
                onPlay = { actions.playSongs(title, shown) },
                onShuffle = { actions.playSongs(title, shown, shuffle = true) },
            )
            Spacer(Modifier.height(20.dp))
        }
        if (shown.isEmpty()) emptyLine { matchingText(query) }
        grid(shown, key = { it.id }) { song: Song, index, modifier ->
            val active = nowPlaying.value?.id == song.id
            GridTile(
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                artwork = song.thumbnailUrl?.resize(544, 544),
                placeholder = Icons.Rounded.MusicNote,
                active = active,
                onClick = { if (active) actions.togglePlayPause() else actions.playSongs(title, shown, startIndex = index) },
                onLongClick = { actions.menu(song) },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun LibraryPlaylistsPage(
    navController: NavController,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playlists by viewModel.allPlaylists.collectAsState()
    val top by viewModel.topThumbnails.collectAsState()
    val topValue by viewModel.topValue.collectAsState(initial = "50")
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val state = rememberLazyListState()
    val jump = rememberRailJump(state)
    val shown = remember(playlists, query) {
        val q = query.trim()
        if (q.isBlank()) playlists else playlists.filter { it.title.contains(q, true) }
    }
    val leading = if (query.isBlank()) 4 else 1
    val indexed = remember(shown, leading) { buildIndexed(shown, leading) { it.title } }

    if (showCreate) CreatePlaylistDialog(onDismiss = { showCreate = false })

    LibraryScaffold(
        title = stringResource(R.string.playlists),
        subtitle = stringResource(R.string.library_playlists_subtitle),
        state = state,
        query = query,
        onQueryChange = { query = it },
        placeholder = stringResource(R.string.library_find_playlist),
        overlay = {
            LibraryIndexRail(
                sections = indexed.sections,
                onSelect = { key -> indexed.positions[key]?.let(jump) },
                topInset = railTop(),
                bottomInset = liquidBottomPadding(),
            )
        },
    ) {
        topRule()
        if (query.isBlank()) {
            item(key = "new", contentType = "row") {
                LibraryTrackRow(
                    title = stringResource(R.string.liquid_new_playlist),
                    subtitle = null,
                    artwork = null,
                    titleColor = Liquid.colors.accent,
                    leading = { GlyphTile(Icons.Rounded.Add) },
                    onClick = { showCreate = true },
                )
            }
            item(key = "liked", contentType = "row") {
                LibraryTrackRow(
                    title = stringResource(R.string.liquid_liked_songs),
                    subtitle = null,
                    artwork = null,
                    leading = { LikedArt(Modifier.size(RowCover).clip(RowCoverShape)) },
                    onClick = { navController.navigate("auto_playlist/liked") },
                )
            }
            item(key = "top", contentType = "row") {
                LibraryTrackRow(
                    title = "${stringResource(R.string.my_top)} $topValue",
                    subtitle = null,
                    artwork = top.firstOrNull(),
                    placeholder = Icons.AutoMirrored.Rounded.TrendingUp,
                    onClick = { navController.navigate("top_playlist/$topValue") },
                )
            }
        }
        if (shown.isEmpty() && query.isNotBlank()) emptyLine { matchingText(query) }
        indexedItems(indexed, idOf = { it.id }) { playlist, _ ->
            LibraryTrackRow(
                title = playlist.playlist.name,
                subtitle = pluralStringResource(R.plurals.liquid_songs_count, playlist.songCount, playlist.songCount),
                artwork = playlist.thumbnails.firstOrNull(),
                placeholder = Icons.AutoMirrored.Rounded.QueueMusic,
                modifier = Modifier.padding(end = RailGutter),
                onClick = { navController.navigate("local_playlist/${playlist.id}") },
                onLongClick = { actions.menu(playlist) },
            )
        }
    }
}

@Composable
private fun GlyphTile(icon: ImageVector) {
    Box(
        Modifier
            .size(RowCover)
            .clip(RowCoverShape)
            .background(libraryPalette().tileHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Liquid.colors.accent, modifier = Modifier.size(26.dp))
    }
}
