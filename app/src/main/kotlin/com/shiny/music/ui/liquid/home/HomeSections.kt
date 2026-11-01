package com.shiny.music.ui.liquid.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.shiny.music.R
import com.shiny.music.constants.CombineDownloadsWithDeviceKey
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.db.entities.Song
import com.shiny.music.home.AlbumTile
import com.shiny.music.home.ArtistTile
import com.shiny.music.home.ContinueKind
import com.shiny.music.home.ContinueSection
import com.shiny.music.home.DeepDiveSection
import com.shiny.music.home.DeviceTile
import com.shiny.music.home.DiscoverPick
import com.shiny.music.home.DiscoverSection
import com.shiny.music.home.DownloadsTile
import com.shiny.music.home.HomeLikedSongs
import com.shiny.music.home.HomeSpotifyMix
import com.shiny.music.home.LatelySection
import com.shiny.music.home.LatelyTile
import com.shiny.music.home.LikedTile
import com.shiny.music.home.MoreLikeSection
import com.shiny.music.home.ShortcutsSection
import com.shiny.music.home.PinnedTile
import com.shiny.music.home.PlaylistTile
import com.shiny.music.home.RotationSection
import com.shiny.music.home.SpotifySection
import com.shiny.music.home.YouTubeSection
import com.shiny.music.home.combineDeviceTiles
import com.shiny.music.intelligence.NewToYouPick
import com.shiny.music.playback.queues.PlaybackOrigin
import com.shiny.music.spotifyimport.SpotifyImportRepository
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.SongGrid
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.artworkRadius
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.liquid.screens.YouTubeTile
import com.shiny.music.ui.liquid.settings.SpotifyGreen
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference

private val TileWidth = 150.dp
private val RecentWidth = 132.dp
private val ArtistWidth = 108.dp
private val AlbumWidth = 172.dp
private val YouTubeMusicRed = Color(0xFFFF0033)
private val RotationOrigin = PlaybackOrigin(RecSurface.HomeRotation)
private val ContinueOrigin = PlaybackOrigin(RecSurface.HomeContinue)

private fun Song.artistLine(): String = artists.joinToString { it.name }

@Composable
internal fun HomeHeader(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SectionHeader(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = trailing,
        modifier = Modifier.semantics { heading() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HeaderPlay(onClick: () -> Unit, label: String = stringResource(R.string.liquid_play)) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .pressScale(interaction, 0.94f)
            .clip(CircleShape)
            .background(colors.tertiaryFill)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = LiquidTypography.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.accent, maxLines = 1)
    }
}

@Composable
internal fun RecentlyPlayedShelf(section: LatelySection, actions: LiquidActions) {
    val combine by rememberPreference(CombineDownloadsWithDeviceKey, false)
    val tiles = remember(section, combine) { if (combine) combineDeviceTiles(section.tiles) else section.tiles }
    HomeHeader(
        title = stringResource(R.string.liquid_recently_played),
        onClick = { actions.navController.navigate("history") },
    )
    Shelf(items = tiles, key = { it.key }) { tile -> RecentTile(tile, actions) }
}

@Composable
internal fun ShortcutsRow(section: ShortcutsSection, actions: LiquidActions) {
    val combine by rememberPreference(CombineDownloadsWithDeviceKey, false)
    val tiles = remember(section, combine) { if (combine) combineDeviceTiles(section.tiles) else section.tiles }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = PageMargin, end = PageMargin, top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tiles.forEach { tile ->
            when (tile) {
                is DownloadsTile -> ShortcutCell(
                    title = stringResource(R.string.home_tile_downloads),
                    count = tile.count,
                    icon = Icons.Rounded.DownloadDone,
                    alone = tiles.size == 1,
                    onClick = { actions.navController.navigate("auto_playlist/downloaded") },
                    modifier = Modifier.weight(1f),
                )
                is DeviceTile -> ShortcutCell(
                    title = stringResource(R.string.home_tile_device),
                    count = tile.count,
                    icon = Icons.Rounded.PhoneAndroid,
                    alone = tiles.size == 1,
                    onClick = { actions.navController.navigate("local_songs") },
                    modifier = Modifier.weight(1f),
                )
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutCell(title: String, count: Int, icon: ImageVector, alone: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = Liquid.colors
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRowHighlight(),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(title, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.home_songs, count, count), style = LiquidTypography.caption1, color = colors.secondaryLabel, maxLines = 1)
        }
        if (alone) Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun RecentTile(tile: LatelyTile, actions: LiquidActions) {
    val nav = actions.navController
    when (tile) {
        is PinnedTile -> {
            val item = remember(tile) { runCatching { tile.item.toYTItem() }.getOrNull() }
            MediaTile(
                title = tile.item.title,
                subtitle = tile.item.subtitle,
                artwork = tile.item.thumbnailUrl?.resize(544, 544),
                circular = item is ArtistItem,
                width = if (item is ArtistItem) ArtistWidth else RecentWidth,
                onClick = { item?.let(actions::open) },
                onLongClick = item?.let { { actions.menu(it) } },
            )
        }
        is AlbumTile -> MediaTile(
            title = tile.album.title,
            subtitle = tile.album.artists.firstOrNull()?.name,
            artwork = tile.album.thumbnailUrl?.resize(544, 544),
            width = RecentWidth,
            onClick = { actions.open(tile.album) },
            onLongClick = { actions.menu(tile.album) },
        )
        is ArtistTile -> MediaTile(
            title = tile.artist.title,
            subtitle = null,
            artwork = tile.image?.resize(544, 544),
            circular = true,
            width = ArtistWidth,
            onClick = { actions.open(tile.artist) },
            onLongClick = { actions.menu(tile.artist) },
        )
        is PlaylistTile -> MediaTile(
            title = tile.playlist.title,
            subtitle = stringResource(R.string.home_tile_playlist),
            artwork = tile.playlist.thumbnails.firstOrNull(),
            width = RecentWidth,
            onClick = { actions.open(tile.playlist) },
            onLongClick = { actions.menu(tile.playlist) },
        )
        is LikedTile -> GlyphTile(
            title = stringResource(R.string.home_tile_liked),
            count = tile.count,
            icon = Icons.Rounded.Favorite,
            onClick = { nav.navigate("auto_playlist/liked") },
        )
        is DownloadsTile, is DeviceTile -> Unit
    }
}

@Composable
private fun GlyphTile(title: String, count: Int, icon: ImageVector, onClick: () -> Unit) {
    val accent = Liquid.colors.accent
    MediaTile(
        title = title,
        subtitle = pluralStringResource(R.plurals.home_songs, count, count),
        artwork = null,
        width = TileWidth,
        onClick = onClick,
        badge = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(artworkRadius(TileWidth)))
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
        },
    )
}

@Composable
internal fun RotationGrid(section: RotationSection, activeId: String?, isPlaying: Boolean, actions: LiquidActions) {
    val title = stringResource(R.string.home_rotation)
    val songs = remember(section) { section.songs.map { it.song } }
    HomeHeader(title)
    SongGrid(items = songs, key = { it.id }, rows = 3) { song, index, modifier, last ->
        SongRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl,
            isActive = song.id == activeId,
            isPlaying = isPlaying,
            explicit = song.song.explicit,
            onClick = {
                if (song.id == activeId) actions.togglePlayPause() else actions.playSongs(title, songs, index, origin = RotationOrigin)
            },
            onLongClick = { actions.menu(song) },
            onMore = { actions.menu(song) },
            showSeparator = !last,
            startPadding = 0.dp,
            artworkSize = 48.dp,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ContinueShelf(section: ContinueSection, activeId: String?, actions: LiquidActions) {
    val name = section.throughName
    val title = if (section.kind == ContinueKind.Artist && name != null) {
        stringResource(R.string.home_continue_artist, name)
    } else {
        stringResource(R.string.home_continue_related, section.seed.title)
    }
    HomeHeader(title)
    Shelf(items = section.songs, key = { it.id }) { song ->
        MediaTile(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl?.resize(544, 544),
            width = TileWidth,
            titleColor = if (song.id == activeId) Liquid.colors.accent else Liquid.colors.label,
            onClick = { actions.playSongs(title, section.songs, section.songs.indexOf(song), origin = ContinueOrigin) },
            onLongClick = { actions.menu(song) },
        )
    }
}

internal fun discoverOrigin(picks: List<DiscoverPick>) = PlaybackOrigin(
    RecSurface.HomeDiscover,
    picks.mapNotNull { pick -> pick.source?.let { pick.song.id to it.name } }.toMap(),
)

@Composable
internal fun DiscoverShelf(section: DiscoverSection, activeId: String?, actions: LiquidActions, onPlayed: () -> Unit) {
    PicksShelf(stringResource(R.string.home_discover), section.picks, activeId, actions, onPlayed)
}

@Composable
internal fun MoreLikeShelf(
    section: MoreLikeSection,
    asList: Boolean,
    activeId: String?,
    isPlaying: Boolean,
    actions: LiquidActions,
    onPlayed: () -> Unit,
) {
    val title = stringResource(R.string.home_continue_related, section.artist)
    if (!asList) {
        PicksShelf(title, section.picks, activeId, actions, onPlayed, width = RecentWidth)
        return
    }
    val songs = remember(section) { section.picks.map { it.song } }
    val origin = remember(section) { discoverOrigin(section.picks) }
    HomeHeader(title)
    SongGrid(items = songs, key = { it.id }, rows = 3) { song, index, modifier, last ->
        SongRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl,
            isActive = song.id == activeId,
            isPlaying = isPlaying,
            explicit = song.song.explicit,
            onClick = {
                if (song.id == activeId) {
                    actions.togglePlayPause()
                } else {
                    onPlayed()
                    actions.playSongs(title, songs, index, origin = origin)
                }
            },
            onLongClick = { actions.menu(song) },
            onMore = { actions.menu(song) },
            showSeparator = !last,
            startPadding = 0.dp,
            artworkSize = 48.dp,
            modifier = modifier,
        )
    }
}

@Composable
private fun PicksShelf(
    title: String,
    picks: List<DiscoverPick>,
    activeId: String?,
    actions: LiquidActions,
    onPlayed: () -> Unit,
    width: androidx.compose.ui.unit.Dp = TileWidth,
) {
    val songs = remember(picks) { picks.map { it.song } }
    val origin = remember(picks) { discoverOrigin(picks) }
    HomeHeader(title)
    Shelf(items = songs, key = { it.id }) { song ->
        MediaTile(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl?.resize(544, 544),
            width = width,
            titleColor = if (song.id == activeId) Liquid.colors.accent else Liquid.colors.label,
            onClick = {
                onPlayed()
                actions.playSongs(title, songs, songs.indexOf(song), origin = origin)
            },
            onLongClick = { actions.menu(song) },
        )
    }
}

@Composable
internal fun NewToYouShelf(picks: List<NewToYouPick>, actions: LiquidActions) {
    val title = stringResource(R.string.new_to_you)
    val songs = remember(picks) { picks.map { it.song } }
    val origin = remember(picks) {
        PlaybackOrigin(
            RecSurface.Discover,
            picks.mapNotNull { pick -> pick.recommendation.source?.let { pick.song.id to it.name } }.toMap(),
        )
    }
    HomeHeader(
        title = title,
        subtitle = stringResource(R.string.new_to_you_subtitle),
        trailing = { HeaderPlay(label = stringResource(R.string.home_play_all), onClick = { actions.playSongs(title, songs, origin = origin) }) },
    )
    Shelf(items = picks, key = { it.song.id }) { pick ->
        DiscoverTile(
            pick = DiscoverPick(pick.song, null, pick.recommendation.reason, pick.recommendation.source),
            onClick = { actions.playSongs(title, songs, songs.indexOf(pick.song), origin = origin) },
            onLongClick = { actions.menu(pick.song) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverTile(pick: DiscoverPick, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val because = pick.reason?.let { reasonLine(it) } ?: pick.because?.let { stringResource(R.string.home_because, it.title) }
    val width = 148.dp
    Column(
        Modifier
            .width(width)
            .pressScale(interaction, 0.965f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Artwork(
            model = pick.song.thumbnailUrl?.resize(544, 544),
            shape = RoundedCornerShape(artworkRadius(width)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(7.dp))
        Text(pick.song.title, style = LiquidTypography.footnote.copy(fontWeight = FontWeight.Medium), color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(pick.song.artistLine(), style = LiquidTypography.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (because != null) {
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = colors.tertiaryLabel, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(because, style = LiquidTypography.caption1, color = colors.tertiaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun MoreFromArtistShelf(section: DeepDiveSection, actions: LiquidActions) {
    val digest = section.digest
    if (section.albums.isNotEmpty()) {
        ArtistHeader(
            title = stringResource(R.string.home_continue_artist, digest.name),
            photo = digest.thumbnail,
            onClick = { actions.navController.navigate("artist/${digest.artistId}") },
        )
        Shelf(items = section.albums, key = { it.id }) { album -> YouTubeTile(album, actions, width = AlbumWidth) }
    }
    if (section.related.size >= 3) {
        HomeHeader(stringResource(R.string.home_similar_to, digest.name))
        Shelf(items = section.related, key = { it.id }) { artist -> YouTubeTile(artist, actions, width = ArtistWidth) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistHeader(title: String, photo: String?, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(start = PageMargin, end = PageMargin - 4.dp, top = 28.dp, bottom = 12.dp)
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(model = photo?.resize(160, 160), shape = CircleShape, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = LiquidTypography.title2, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondaryLabel, modifier = Modifier.size(26.dp))
    }
}

private sealed interface ServiceTile {
    val key: String

    data class Liked(val liked: HomeLikedSongs) : ServiceTile {
        override val key get() = "liked"
    }

    data class SpotifyMix(val mix: HomeSpotifyMix) : ServiceTile {
        override val key get() = "spotify_${mix.spotifyId}"
    }

    data class YouTubeMix(val playlist: PlaylistItem) : ServiceTile {
        override val key get() = "youtube_${playlist.id}"
    }
}

@Composable
internal fun SpotifyShelf(section: SpotifySection, actions: LiquidActions) {
    val tiles = remember(section) {
        listOfNotNull(section.liked?.let(ServiceTile::Liked)) + section.mixes.map(ServiceTile::SpotifyMix)
    }
    HomeHeader(
        title = stringResource(R.string.home_spotify_title),
        trailing = { SpotifyMark() },
    )
    Shelf(items = tiles, key = { it.key }) { tile ->
        when (tile) {
            is ServiceTile.Liked -> LikedSongsTile(tile.liked, SpotifyGreen) {
                actions.navController.navigate("local_playlist/${SpotifyImportRepository.LIKED_SONGS_PLAYLIST_ID}")
            }
            is ServiceTile.SpotifyMix -> MediaTile(
                title = tile.mix.name,
                subtitle = pluralStringResource(R.plurals.n_song, tile.mix.songCount, tile.mix.songCount),
                artwork = tile.mix.imageUrl,
                width = TileWidth,
                onClick = { actions.navController.navigate("local_playlist/${tile.mix.localPlaylistId}") },
            )
            is ServiceTile.YouTubeMix -> Unit
        }
    }
}

@Composable
internal fun YouTubeShelf(section: YouTubeSection, actions: LiquidActions) {
    val tiles = remember(section) {
        listOfNotNull(section.liked?.let(ServiceTile::Liked)) + section.mixes.map(ServiceTile::YouTubeMix)
    }
    HomeHeader(
        title = stringResource(R.string.home_youtube_title),
        trailing = { YouTubeMark() },
    )
    Shelf(items = tiles, key = { it.key }) { tile ->
        when (tile) {
            is ServiceTile.Liked -> LikedSongsTile(tile.liked, YouTubeMusicRed) {
                actions.navController.navigate("auto_playlist/liked")
            }
            is ServiceTile.YouTubeMix -> MediaTile(
                title = tile.playlist.title,
                subtitle = tile.playlist.subtitleText(),
                artwork = tile.playlist.thumbnail?.resize(544, 544),
                width = TileWidth,
                onClick = { actions.open(tile.playlist) },
                onLongClick = { actions.menu(tile.playlist) },
            )
            is ServiceTile.SpotifyMix -> Unit
        }
    }
}

@Composable
private fun LikedSongsTile(liked: HomeLikedSongs, tint: Color, onClick: () -> Unit) {
    MediaTile(
        title = stringResource(R.string.home_tile_liked),
        subtitle = pluralStringResource(R.plurals.home_songs, liked.count, liked.count),
        artwork = null,
        width = TileWidth,
        onClick = onClick,
        badge = {
            CoverMosaic(liked.covers, tint)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(30.dp)
                    .shadow(6.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        },
    )
}

@Composable
private fun CoverMosaic(covers: List<String>, tint: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(artworkRadius(TileWidth)))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.45f)))),
    ) {
        when {
            covers.size >= 4 -> Column(Modifier.fillMaxSize()) {
                covers.take(4).chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        pair.forEach { cover ->
                            Artwork(
                                model = cover.resize(272, 272),
                                shape = RectangleShape,
                                hairline = false,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            covers.isNotEmpty() -> Artwork(
                model = covers.first().resize(544, 544),
                shape = RectangleShape,
                hairline = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SpotifyMark() {
    Icon(
        painterResource(R.drawable.ic_spotify),
        contentDescription = null,
        tint = SpotifyGreen,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun YouTubeMark() {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(YouTubeMusicRed),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.play), null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
