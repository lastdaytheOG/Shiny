package com.shiny.music.ui.liquid.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.LocalItem
import com.shiny.music.db.entities.Playlist
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.ShelfGap
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberListState
import com.shiny.music.ui.utils.resize
import com.shiny.music.viewmodels.LiquidLibraryViewModel

private fun LazyListScope.recentGrid(recent: List<LocalItem>, actions: LiquidActions) {
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
                            is Playlist -> pluralStringResource(R.plurals.liquid_songs_count, item.songCount, item.songCount)
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
}

@Composable
fun LiquidRecentlyAddedScreen(
    navController: NavController,
    viewModel: LiquidLibraryViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val recent by viewModel.recentlyAddedPage.collectAsState()
    val songs by viewModel.recentlyAddedSongs.collectAsState()
    val activeId by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()
    val title = stringResource(R.string.liquid_recently_added)

    LargeTitlePage(
        title = title,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
        state = rememberListState(viewModel.scroll),
    ) {
        if (recent.isEmpty() && songs.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(R.string.liquid_library_empty_title),
                    message = stringResource(R.string.liquid_library_empty_message),
                )
            }
            return@LargeTitlePage
        }
        recentGrid(recent, actions)
        if (songs.isNotEmpty()) {
            if (recent.isNotEmpty()) {
                item(key = "songs_header") { SectionHeader(title = stringResource(R.string.songs)) }
            }
            itemsIndexed(songs, key = { _, song -> "song_${song.id}" }) { index, song ->
                SongRow(
                    songId = song.id,
                    title = song.title,
                    subtitle = song.artists.joinToString { it.name },
                    artwork = song.thumbnailUrl,
                    isActive = song.id == activeId?.id,
                    isPlaying = isPlaying,
                    explicit = song.song.explicit,
                    onClick = {
                        if (song.id == activeId?.id) actions.togglePlayPause() else actions.playSongs(title, songs, index)
                    },
                    onLongClick = { actions.menu(song) },
                    onMore = { actions.menu(song) },
                    showSeparator = index != songs.lastIndex,
                )
            }
        }
    }
}
