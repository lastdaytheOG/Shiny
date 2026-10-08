package com.shiny.music.ui.liquid.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.constants.AlbumFilter
import com.shiny.music.constants.AlbumFilterKey
import com.shiny.music.constants.ArtistFilter
import com.shiny.music.constants.ArtistFilterKey
import com.shiny.music.constants.ListenTogetherInTopBarKey
import com.shiny.music.constants.SongFilter
import com.shiny.music.constants.SongFilterKey
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.Playlist
import com.shiny.music.ui.component.CreatePlaylistDialog
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidIcons
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.NavigationRow
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.ShelfGap
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberEnumPreference
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.LibraryAlbumsViewModel
import com.shiny.music.viewmodels.LibraryArtistsViewModel
import com.shiny.music.viewmodels.LibraryPlaylistsViewModel
import com.shiny.music.viewmodels.LibrarySongsViewModel
import com.shiny.music.viewmodels.LiquidLibraryViewModel

/**
 * Library as the Music app has it: a large title, the categories as a list of tinted
 * glyph rows, and the newest additions as a two-column grid of covers below. Every
 * category is its own page, pushed.
 */
@Composable
fun LiquidLibraryScreen(
    navController: NavController,
    viewModel: LiquidLibraryViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val recent by viewModel.recentlyAdded.collectAsState()
    val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)

    data class Category(val icon: ImageVector, val title: String, val route: String)

    val categories = listOfNotNull(
        Category(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.playlists), "library/playlists"),
        Category(Icons.Rounded.Mic, stringResource(R.string.artists), "library/artists"),
        Category(Icons.Rounded.Album, stringResource(R.string.albums), "library/albums"),
        Category(Icons.Rounded.MusicNote, stringResource(R.string.songs), "library/songs"),
        Category(Icons.Rounded.Favorite, stringResource(R.string.liquid_liked_songs), "auto_playlist/liked"),
        Category(Icons.Rounded.Download, stringResource(R.string.liquid_downloaded), "auto_playlist/downloaded"),
        Category(Icons.Rounded.PhoneAndroid, stringResource(R.string.liquid_local_files), "local_songs"),
        Category(Icons.Rounded.History, stringResource(R.string.liquid_history), "history"),
        Category(Icons.Rounded.BarChart, stringResource(R.string.liquid_stats), "stats"),
        if (listenTogetherInTopBar) Category(LiquidIcons.Waves, stringResource(R.string.listen_together), "together") else null,
        Category(Icons.Rounded.GraphicEq, stringResource(R.string.recognition), "recognition"),
    )

    LargeTitlePage(
        title = stringResource(R.string.liquid_library),
        scrollToTopSignal = navController,
    ) {
        items(categories, key = { "cat_${it.route}" }) { c ->
            NavigationRow(
                icon = c.icon,
                title = c.title,
                onClick = { navController.navigate(c.route) },
                showSeparator = c != categories.last(),
            )
        }

        if (recent.isNotEmpty()) {
            item(key = "recent_header") {
                SectionHeader(title = stringResource(R.string.liquid_recently_added))
            }
            items(recent.chunked(2), key = { row -> "recent_${row.first().id}" }) { row ->
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
                                title = item.title,
                                subtitle = when (item) {
                                    is Album -> item.artists.joinToString { it.name }
                                    is Playlist -> stringResource(R.string.liquid_songs_count, item.songCount)
                                    else -> null
                                },
                                artwork = when (item) {
                                    is Playlist -> item.thumbnails.firstOrNull()
                                    else -> item.thumbnailUrl?.resize(544, 544)
                                },
                                width = Dp.Unspecified,
                                onClick = { actions.open(item) },
                                onLongClick = { actions.menu(item) },
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.liquid_library_empty_title),
                    message = stringResource(R.string.liquid_library_empty_message),
                )
            }
        }
    }
}

@Composable
fun LiquidLibraryPlaylistsScreen(
    navController: NavController,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playlists by viewModel.allPlaylists.collectAsState()
    val liked by viewModel.likedThumbnails.collectAsState()
    val top by viewModel.topThumbnails.collectAsState()
    val downloaded by viewModel.downloadedThumbnails.collectAsState()
    val topValue by viewModel.topValue.collectAsState(initial = "50")
    var showCreate by rememberSaveable { mutableStateOf(false) }

    if (showCreate) {
        CreatePlaylistDialog(onDismiss = { showCreate = false })
    }

    LargeTitlePage(
        title = stringResource(R.string.playlists),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "new_playlist") {
            LibraryRow(
                leading = {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Liquid.colors.tertiaryFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Liquid.colors.accent, modifier = Modifier.size(28.dp))
                    }
                },
                title = stringResource(R.string.liquid_new_playlist),
                titleColor = Liquid.colors.accent,
                onClick = { showCreate = true },
            )
        }
        item(key = "auto_liked") {
            LibraryRow(
                leading = { CoverThumb(liked, Icons.Rounded.Favorite) },
                title = stringResource(R.string.liquid_liked_songs),
                onClick = { navController.navigate("auto_playlist/liked") },
            )
        }
        item(key = "auto_top") {
            LibraryRow(
                leading = { CoverThumb(top, Icons.AutoMirrored.Rounded.TrendingUp) },
                title = "${stringResource(R.string.my_top)} $topValue",
                onClick = { navController.navigate("top_playlist/$topValue") },
            )
        }
        item(key = "auto_downloaded") {
            LibraryRow(
                leading = { CoverThumb(downloaded, Icons.Rounded.Download) },
                title = stringResource(R.string.liquid_downloaded),
                onClick = { navController.navigate("auto_playlist/downloaded") },
                showSeparator = playlists.isNotEmpty(),
            )
        }
        items(playlists, key = { "pl_${it.id}" }) { playlist ->
            LibraryRow(
                leading = { CoverThumb(playlist.thumbnails, Icons.AutoMirrored.Rounded.QueueMusic) },
                title = playlist.playlist.name,
                subtitle = stringResource(R.string.liquid_songs_count, playlist.songCount),
                onClick = { navController.navigate("local_playlist/${playlist.id}") },
                onLongClick = { actions.menu(playlist) },
                showSeparator = playlist != playlists.lastOrNull(),
            )
        }
    }
}

@Composable
private fun CoverThumb(thumbs: List<String>, glyph: ImageVector, size: Dp = 56.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        when {
            thumbs.size >= 4 -> CollageCover(thumbs.take(4), Modifier.size(size))
            thumbs.isNotEmpty() -> Artwork(model = thumbs.first(), shape = RoundedCornerShape(8.dp), modifier = Modifier.size(size))
            else -> Box(
                Modifier
                    .size(size)
                    .background(
                        Brush.linearGradient(listOf(Liquid.colors.accent, Liquid.colors.accent.copy(alpha = 0.6f)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(glyph, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    leading: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null,
    showSeparator: Boolean = true,
    titleColor: Color = Liquid.colors.label,
    chevron: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = Liquid.colors
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = PageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(title, style = LiquidTypography.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = LiquidTypography.subheadline, color = colors.secondaryLabel, maxLines = 1)
                }
            }
            if (chevron) {
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(24.dp))
            }
        }
        if (showSeparator) {
            Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + 70.dp)
        }
    }
}

@Composable
fun LiquidLibraryArtistsScreen(
    navController: NavController,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val artists by viewModel.allArtists.collectAsState()
    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIKED)

    LargeTitlePage(
        title = stringResource(R.string.artists),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "filter") {
            LiquidSegmentedControl(
                items = listOf(stringResource(R.string.liquid_following), stringResource(R.string.liquid_library)),
                selectedIndex = if (filter == ArtistFilter.LIKED) 0 else 1,
                onSelect = { filter = if (it == 0) ArtistFilter.LIKED else ArtistFilter.LIBRARY },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 8.dp),
            )
        }
        if (artists.isEmpty()) {
            item(key = "empty") {
                EmptyState(icon = Icons.Rounded.Mic, title = stringResource(R.string.liquid_library_empty_title))
            }
        }
        items(artists, key = { "ar_${it.id}" }) { artist ->
            LibraryRow(
                leading = {
                    Artwork(
                        model = artist.thumbnailUrl?.resize(240, 240),
                        shape = CircleShape,
                        placeholder = Icons.Rounded.Mic,
                        modifier = Modifier.size(52.dp),
                    )
                },
                title = artist.title,
                chevron = true,
                onClick = { navController.navigate("artist/${artist.id}") },
                onLongClick = { actions.menu(artist) },
                showSeparator = artist != artists.lastOrNull(),
            )
        }
    }
}

@Composable
fun LiquidLibraryAlbumsScreen(
    navController: NavController,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val albums by viewModel.allAlbums.collectAsState()
    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)

    LargeTitlePage(
        title = stringResource(R.string.albums),
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "filter") {
            LiquidSegmentedControl(
                items = listOf(stringResource(R.string.liquid_following), stringResource(R.string.liquid_library)),
                selectedIndex = if (filter == AlbumFilter.LIKED) 0 else 1,
                onSelect = { filter = if (it == 0) AlbumFilter.LIKED else AlbumFilter.LIBRARY },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(top = 8.dp, bottom = 16.dp),
            )
        }
        if (albums.isEmpty()) {
            item(key = "empty") {
                EmptyState(icon = Icons.Rounded.Album, title = stringResource(R.string.liquid_library_empty_title))
            }
        }
        items(albums.chunked(2), key = { row -> "al_${row.first().id}" }) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin)
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(ShelfGap + 4.dp),
            ) {
                row.forEach { album ->
                    Box(Modifier.weight(1f)) {
                        AlbumCell(album, actions)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlbumCell(album: Album, actions: LiquidActions) {
    MediaTile(
        title = album.title,
        subtitle = album.artists.joinToString { it.name },
        artwork = album.thumbnailUrl?.resize(544, 544),
        width = Dp.Unspecified,
        placeholder = Icons.Rounded.Album,
        onClick = { actions.open(album) },
        onLongClick = { actions.menu(album) },
    )
}

@Composable
fun LiquidLibrarySongsScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playerConnection = actions.playerConnection
    val songs by viewModel.allSongs.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)
    val filters = listOf(SongFilter.LIKED, SongFilter.LIBRARY, SongFilter.DOWNLOADED)
    val title = stringResource(R.string.songs)

    LargeTitlePage(
        title = title,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "filter") {
            LiquidSegmentedControl(
                items = listOf(
                    stringResource(R.string.liked),
                    stringResource(R.string.liquid_library),
                    stringResource(R.string.liquid_downloaded),
                ),
                selectedIndex = filters.indexOf(filter).coerceAtLeast(0),
                onSelect = { filter = filters[it] },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 8.dp),
            )
        }
        item(key = "play_row") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LiquidButton(
                    text = stringResource(R.string.liquid_play),
                    icon = Icons.Rounded.PlayArrow,
                    tone = ButtonTone.Tinted,
                    enabled = songs.isNotEmpty(),
                    onClick = { actions.playSongs(title, songs) },
                    modifier = Modifier.weight(1f),
                )
                LiquidButton(
                    text = stringResource(R.string.liquid_shuffle),
                    icon = Icons.Rounded.Shuffle,
                    tone = ButtonTone.Tinted,
                    enabled = songs.isNotEmpty(),
                    onClick = { actions.playSongs(title, songs, shuffle = true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                EmptyState(icon = Icons.Rounded.MusicNote, title = stringResource(R.string.liquid_library_empty_title))
            }
        }
        items(songs, key = { "so_${it.id}" }) { song ->
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
                    else actions.playSongs(title, songs, startIndex = songs.indexOf(song))
                },
                onLongClick = { actions.menu(song) },
                onMore = { actions.menu(song) },
            )
        }
    }
}

@Suppress("unused")
private val keepArtist = Artist::class
