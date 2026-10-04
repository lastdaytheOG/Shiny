package com.shiny.music.ui.liquid.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.LocalDatabase
import com.shiny.music.R
import com.shiny.music.db.entities.Album
import com.shiny.music.playback.queues.LocalAlbumRadio
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.GlassIconButton
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.NavPeek
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.menu.AlbumMenu
import com.shiny.music.ui.utils.resize
import com.shiny.music.viewmodels.AlbumViewModel

@Composable
fun LiquidAlbumScreen(
    navController: NavController,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playerConnection = actions.playerConnection
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val otherVersions by viewModel.otherVersions.collectAsState()
    val releasesForYou by viewModel.releasesForYou.collectAsState()
    val description by viewModel.description.collectAsState()
    val playlistId by viewModel.playlistId.collectAsState()

    val album = albumWithSongs
    // A first visit, before the album is stored: the tile tapped to get here already showed
    // its title and cover, so the page shows them while the tracks load.
    val peek = remember(viewModel.albumId) { NavPeek.get(viewModel.albumId) }
    if (album == null && peek != null) {
        LiquidCollectionPlaceholder(peek, onBack = { navController.navigateUp() })
        return
    }
    if (album == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF232327))) {
            ActivityIndicator(color = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
            GlassIconButton(
                icon = Icons.Rounded.ChevronLeft,
                onClick = { navController.navigateUp() },
                iconSize = 28.dp,
                tint = Color.White,
                kind = GlassKind.Clear,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = statusBarHeight() + 4.dp, start = 14.dp),
            )
        }
        return
    }

    val songs = album.songs
    val totalMinutes = songs.sumOf { it.song.duration } / 60
    val isThisAlbumPlaying = mediaMetadata?.album?.id == album.album.id
    val artistNames = album.artists.joinToString { it.name }
    val kind = if (songs.size <= 3) stringResource(R.string.liquid_single) else stringResource(R.string.liquid_album)
    val footer = listOfNotNull(
        album.album.year?.let { stringResource(R.string.liquid_released, it.toString()) },
        stringResource(R.string.liquid_songs_minutes, songs.size, totalMinutes),
    )

    LiquidCollectionPage(
        title = album.album.title,
        subtitle = artistNames,
        onSubtitleClick = album.artists.firstOrNull()?.let { artist -> { navController.navigate("artist/${artist.id}") } },
        meta = listOfNotNull(kind, album.album.year?.toString()).joinToString(" · "),
        artwork = album.album.thumbnailUrl?.resize(1080, 1080),
        toneSource = album.album.thumbnailUrl?.resize(544, 544),
        description = description,
        onBack = { navController.navigateUp() },
        onPlay = {
            if (isThisAlbumPlaying) {
                playerConnection.togglePlayPause()
            } else {
                playerConnection.service.getAutomix(playlistId)
                playerConnection.playQueue(LocalAlbumRadio(album))
            }
        },
        onShuffle = {
            playerConnection.service.getAutomix(playlistId)
            playerConnection.playQueue(LocalAlbumRadio(album.copy(songs = songs.shuffled())))
        },
        topActions = { ink ->
            GlassIconButton(
                icon = if (album.album.bookmarkedAt != null) Icons.Rounded.Check else Icons.Rounded.Add,
                onClick = { database.query { update(album.album.toggleLike()) } },
                tint = ink.primary,
                kind = GlassKind.Clear,
            )
            GlassIconButton(
                icon = Icons.Rounded.MoreHoriz,
                onClick = {
                    menuState.show {
                        AlbumMenu(
                            originalAlbum = Album(album.album, album.artists),
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                tint = ink.primary,
                kind = GlassKind.Clear,
            )
        },
    ) { ink ->
        itemsIndexed(songs, key = { _, s -> "track_${s.id}" }) { index, song ->
            val songArtists = song.artists.joinToString { it.name }
            SongRow(
                songId = song.id,
                title = song.title,
                subtitle = songArtists.takeIf { it.isNotBlank() && it != artistNames },
                artwork = null,
                trackNumber = index + 1,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                explicit = song.song.explicit,
                onClick = {
                    if (song.id == mediaMetadata?.id) {
                        playerConnection.togglePlayPause()
                    } else {
                        playerConnection.service.getAutomix(playlistId)
                        playerConnection.playQueue(LocalAlbumRadio(album, startIndex = index))
                    }
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

        collectionFooter(footer, ink)

        if (otherVersions.isNotEmpty()) {
            item(key = "other_versions_header") {
                SectionHeader(
                    title = stringResource(R.string.liquid_other_versions),
                    color = ink.primary,
                    secondaryColor = ink.secondary,
                )
            }
            item(key = "other_versions") {
                Shelf(items = otherVersions.distinctBy { it.id }, key = { "ov_${it.id}" }) { item ->
                    MediaTile(
                        songId = (item as? com.music.innertube.models.SongItem)?.id,
                        title = item.title,
                        subtitle = item.subtitleText(),
                        artwork = item.thumbnail.resize(544, 544),
                        onClick = { actions.open(item) },
                        onLongClick = { actions.menu(item) },
                        titleColor = ink.primary,
                        subtitleColor = ink.secondary,
                    )
                }
            }
        }

        if (releasesForYou.isNotEmpty()) {
            item(key = "releases_header") {
                SectionHeader(
                    title = stringResource(R.string.liquid_you_might_also_like),
                    color = ink.primary,
                    secondaryColor = ink.secondary,
                )
            }
            item(key = "releases") {
                Shelf(items = releasesForYou.distinctBy { it.id }, key = { "rfy_${it.id}" }) { item ->
                    MediaTile(
                        songId = (item as? com.music.innertube.models.SongItem)?.id,
                        title = item.title,
                        subtitle = item.subtitleText(),
                        artwork = item.thumbnail.resize(544, 544),
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
