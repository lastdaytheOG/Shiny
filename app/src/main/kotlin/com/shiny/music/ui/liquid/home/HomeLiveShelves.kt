package com.shiny.music.ui.liquid.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.shiny.music.R
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.Song
import com.shiny.music.home.CommunityPlaylist
import com.shiny.music.home.HistorySection
import com.shiny.music.home.HomeShelf
import com.shiny.music.home.HomeVideo
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize

private fun Song.artistLine(): String = artists.joinToString { it.name }

internal fun YTItem.cardSubtitle(): String? = when (this) {
    is SongItem -> artists.joinToString { it.name }
    is AlbumItem -> listOfNotNull(artists?.firstOrNull()?.name, year?.toString()).joinToString(" · ")
    is PlaylistItem -> author?.name ?: songCountText
    is ArtistItem -> null
    else -> null
}

@Composable
internal fun YTCard(item: YTItem, actions: LiquidActions, activeId: String?, onSong: ((SongItem) -> Unit)? = null) {
    HomeCard(
        songId = (item as? SongItem)?.id,
        title = item.title,
        subtitle = item.cardSubtitle(),
        artwork = item.thumbnail?.resize(544, 544),
        circular = item is ArtistItem,
        titleColor = if (item.id == activeId) Liquid.colors.accent else Liquid.colors.label,
        onClick = { if (item is SongItem && onSong != null) onSong(item) else actions.open(item) },
        onLongClick = { actions.menu(item) },
    )
}

@Composable
internal fun MostPlayedShelf(songs: List<Song>, activeId: String?, actions: LiquidActions) {
    val title = stringResource(R.string.home_most_played)
    HomeSectionTitle(title, onClick = { actions.navController.navigate("stats") })
    HomeTrackGrid(items = songs, key = { it.id }) { song, index, modifier ->
        HomeTrackRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl?.resize(240, 240),
            isActive = song.id == activeId,
            onClick = { if (song.id == activeId) actions.togglePlayPause() else actions.playSongs(title, songs, index) },
            onLongClick = { actions.menu(song) },
            rank = index + 1,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ForgottenShelf(songs: List<Song>, activeId: String?, actions: LiquidActions) {
    val title = stringResource(R.string.home_dont_forget)
    HomeSectionTitle(title)
    HomeTrackGrid(items = songs, key = { it.id }) { song, index, modifier ->
        HomeTrackRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl?.resize(240, 240),
            isActive = song.id == activeId,
            onClick = { if (song.id == activeId) actions.togglePlayPause() else actions.playSongs(title, songs, index) },
            onLongClick = { actions.menu(song) },
            modifier = modifier,
        )
    }
}

@Composable
internal fun HistoryRows(songs: List<Song>, activeId: String?, actions: LiquidActions) =
    HistoryShelf(HistorySection(songs), activeId, actions)

@Composable
internal fun CommunityShelf(lists: List<CommunityPlaylist>, actions: LiquidActions) {
    HomeSectionTitle(stringResource(R.string.home_from_community))
    HomeRow(items = lists, key = { it.playlist.id }) { c ->
        HomeCard(
            title = c.playlist.title,
            subtitle = c.playlist.author?.name ?: c.playlist.songCountText,
            artwork = c.playlist.thumbnail?.resize(544, 544),
            onClick = { actions.open(c.playlist) },
            onLongClick = { actions.menu(c.playlist) },
        )
    }
}

@Composable
internal fun VideoShelf(title: String, videos: List<HomeVideo>, activeId: String?, actions: LiquidActions) {
    HomeSectionTitle(title)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth - HomeInset - 46.dp
        HomeRow(items = videos, key = { it.item.id }) { v -> HomeVideoCard(v, width, v.item.id == activeId) { actions.open(v.item) } }
    }
}

// a wide frame of the video with its title over the top-left corner
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeVideoCard(video: HomeVideo, width: Dp, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    var fallback by remember(video.item.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(HomeCardRadius)
    Box(
        Modifier
            .width(width)
            .height(width * (247f / 314f))
            .pressScale(interaction, 0.97f)
            .clip(shape)
            .background(Liquid.colors.label.copy(alpha = 0.08f))
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(
            model = if (fallback) video.item.thumbnail else "https://i.ytimg.com/vi/${video.item.id}/hq720.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { if (!fallback) fallback = true },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(VideoScrim),
        )
        Column(Modifier.padding(start = 18.dp, top = 16.dp, end = 18.dp)) {
            Text(video.title, style = VideoTitle, color = if (active) Liquid.colors.accent else Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(video.subtitle, style = VideoSubtitle, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val VideoScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.62f),
    0.24f to Color.Black.copy(alpha = 0.34f),
    0.52f to Color.Transparent,
)
private val VideoTitle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = (-0.03).em)
private val VideoSubtitle = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = (-0.02).em)

@Composable
internal fun YourPlaylistsShelf(accountName: String, accountImage: String?, online: List<PlaylistItem>, local: List<Playlist>, actions: LiquidActions) {
    val colors = Liquid.colors
    HomeSectionTitle(
        title = accountName,
        label = stringResource(R.string.home_your_playlists),
        thumbnail = {
            if (accountImage != null) {
                com.shiny.music.ui.liquid.Artwork(model = accountImage, shape = CircleShape, hairline = false, modifier = Modifier.size(30.dp))
            } else {
                Icon(Icons.Rounded.Person, null, tint = colors.label.copy(alpha = 0.69f), modifier = Modifier.size(30.dp))
            }
        },
    )
    val items: List<Any> = remember(online, local) { local + online }
    HomeRow(items = items, key = { if (it is Playlist) "l_${it.id}" else "o_${(it as PlaylistItem).id}" }) { p ->
        when (p) {
            is Playlist -> HomeCard(
                title = p.title,
                subtitle = pluralCount(p.songCount),
                artwork = p.thumbnails.firstOrNull(),
                onClick = { actions.navController.navigate("local_playlist/${p.id}") },
                onLongClick = { actions.menu(p) },
                badge = if (p.thumbnails.isEmpty()) {
                    { EmptyPlaylistCover(p.title) }
                } else {
                    null
                },
            )
            is PlaylistItem -> HomeCard(
                title = p.title,
                subtitle = p.songCountText ?: p.author?.name,
                artwork = p.thumbnail?.resize(544, 544),
                onClick = { actions.open(p) },
                onLongClick = { actions.menu(p) },
            )
        }
    }
}

@Composable
private fun pluralCount(n: Int): String = androidx.compose.ui.res.pluralStringResource(R.plurals.n_song, n, n)

@Composable
internal fun YouTubeShelfRow(shelf: HomeShelf, activeId: String?, actions: LiquidActions) {
    HomeSectionTitle(
        title = shelf.title,
        label = shelf.label,
        onClick = shelf.browseId?.let { id -> { actions.openBrowse(BrowseEndpoint(browseId = id, params = shelf.params)) } },
    )
    HomeRow(items = shelf.items, key = { it.id }) { YTCard(it, actions, activeId) }
}

@Composable
internal fun HitsShelf(items: List<YTItem>, activeId: String?, actions: LiquidActions) {
    HomeSectionTitle(stringResource(R.string.home_all_time_hits))
    HomeRow(items = items, key = { it.id }) { YTCard(it, actions, activeId) }
}

@Composable
internal fun JumpBackInShelf(items: List<YTItem>, activeId: String?, actions: LiquidActions) {
    HomeSectionTitle(stringResource(R.string.home_jump_back_in))
    HomeRow(items = items, key = { it.id }) { YTCard(it, actions, activeId) }
}

private val PlaylistPastels = listOf(
    Color(0xFFDCEAF2) to Color(0xFF6FA3C7),
    Color(0xFFF4D6D6) to Color(0xFFE0474C),
    Color(0xFFE6E0F5) to Color(0xFF8E7CC3),
    Color(0xFFDDF0E2) to Color(0xFF5FAF7A),
    Color(0xFFF7E8CF) to Color(0xFFE09A3E),
)

// a playlist with nothing in it yet: a soft gradient with its name written on it
@Composable
private fun androidx.compose.foundation.layout.BoxScope.EmptyPlaylistCover(name: String) {
    val (light, deep) = PlaylistPastels[(name.hashCode() and 0x7FFFFFFF) % PlaylistPastels.size]
    Box(
        Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(HomeCardRadius))
            .background(Brush.linearGradient(listOf(light, deep))),
    ) {
        Text(
            name,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1C1E),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(14.dp),
        )
    }
}
