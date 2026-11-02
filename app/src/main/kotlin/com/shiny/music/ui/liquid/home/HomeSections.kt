package com.shiny.music.ui.liquid.home

import com.music.innertube.models.SongItem
import com.music.innertube.models.AlbumItem
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.shiny.music.ui.liquid.prewarmOnTouch
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryAdd
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
import com.shiny.music.db.entities.Playlist
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
import com.shiny.music.home.HistoryTile
import com.shiny.music.home.LatelyTile
import com.shiny.music.home.LikedTile
import com.shiny.music.home.MoreLikeSection
import com.shiny.music.home.ShortcutsSection
import com.shiny.music.home.PinnedSection
import com.shiny.music.home.PinnedTile
import com.shiny.music.home.PlaylistTile
import com.shiny.music.home.RecentArtistsSection
import com.shiny.music.home.RecentlyAddedTile
import com.shiny.music.home.RotationSection
import com.shiny.music.home.SongTile
import com.shiny.music.home.SpotifySection
import com.shiny.music.home.YouTubeSection
import com.shiny.music.home.RecommendationRowSection
import com.shiny.music.home.RecRowKind
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

private val TileWidth = HomeCardSize
private val RecentWidth = HomeCardSize
private val ArtistWidth = HomeCardSize
private val AlbumWidth = HomeCardSize
private val ShortcutHeight = 58.dp
private val HomeHeadingStyle = LiquidTypography.title2
private val HomeHeadingTop = 34.dp
private val HomeHeadingBottom = 12.dp
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
    HomeSectionTitle(title = title, onClick = onClick)
}

// the feed's cards: big squircles, the title and the artist underneath
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeCard(
    title: String,
    subtitle: String?,
    artwork: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = TileWidth,
    circular: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    badge: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    titleColor: Color = Liquid.colors.label,
    subtitleColor: Color = Liquid.colors.secondaryLabel,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.MusicNote,
    songId: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = if (circular) CircleShape else RoundedCornerShape(HomeCardRadius)
    val label = Liquid.colors.label
    Column(
        modifier
            .width(width)
            .prewarmOnTouch(songId)
            .pressScale(interaction, 0.96f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            Artwork(model = artwork, shape = shape, placeholder = placeholder, hairline = false, modifier = Modifier.fillMaxSize())
            badge?.invoke(this)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = HomeCardTitleStyle,
            color = if (titleColor == label) label.copy(alpha = 0.92f) else titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circular) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = HomeCardSubtitleStyle,
                color = label.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (circular) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
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
    val title = stringResource(R.string.liquid_recently_played)
    val songs = remember(tiles) { tiles.filterIsInstance<SongTile>().map { it.song } }
    HomeHeader(
        title = title,
        onClick = { actions.navController.navigate("history") },
    )
    HomeRow(items = tiles, key = { it.key }) { tile ->
        when (tile) {
            is SongTile -> HomeCard(
                songId = tile.song.id,
                title = tile.song.title,
                subtitle = tile.song.artistLine(),
                artwork = tile.song.thumbnailUrl?.resize(544, 544),
                width = RecentWidth,
                onClick = { actions.playSongs(title, songs, songs.indexOf(tile.song)) },
                onLongClick = { actions.menu(tile.song) },
            )
            else -> RecentTile(tile, actions)
        }
    }
}

@Composable
internal fun PinnedShelf(section: PinnedSection, actions: LiquidActions) {
    HomeHeader(stringResource(R.string.home_pinned))
    val rows = remember(section) { section.tiles.chunked(3) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMargin),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> Box(Modifier.weight(1f)) { PinCell(tile, actions) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinCell(tile: LatelyTile, actions: LiquidActions) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val pin = tile as? PinnedTile ?: return
    val item = remember(pin) { runCatching { pin.item.toYTItem() }.getOrNull() }
    val circular = item is ArtistItem
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.95f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { item?.let(actions::open) },
                onLongClick = { item?.let { actions.menu(it) } },
            )
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            model = pin.item.thumbnailUrl?.resize(360, 360),
            shape = if (circular) CircleShape else RoundedCornerShape(artworkRadius(RecentWidth)),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = pin.item.title,
            style = LiquidTypography.footnote.copy(fontWeight = FontWeight.Medium),
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistCoverTile(playlist: Playlist, actions: LiquidActions) {
    val colors = Liquid.colors
    val covers = remember(playlist) { playlist.thumbnails.distinct() }
    val count = playlist.playlist.remoteSongCount ?: playlist.songCount
    HomeCard(
        title = playlist.title,
        subtitle = if (count > 0) pluralStringResource(R.plurals.home_songs, count, count) else null,
        artwork = null,
        width = TileWidth,
        onClick = { actions.open(playlist) },
        onLongClick = { actions.menu(playlist) },
        badge = if (covers.isEmpty()) null else ({ CoverMosaic(covers, colors.gray5, TileWidth) }),
    )
}

@Composable
private fun PinnedItemTile(tile: PinnedTile, actions: LiquidActions, width: androidx.compose.ui.unit.Dp) {
    val item = remember(tile) { runCatching { tile.item.toYTItem() }.getOrNull() }
    HomeCard(
        title = tile.item.title,
        subtitle = tile.item.subtitle,
        artwork = tile.item.thumbnailUrl?.resize(544, 544),
        circular = item is ArtistItem,
        width = width,
        onClick = { item?.let(actions::open) },
        onLongClick = item?.let { { actions.menu(it) } },
    )
}

@Composable
internal fun RecentArtistsShelf(section: RecentArtistsSection, actions: LiquidActions) {
    HomeHeader(stringResource(R.string.home_recent_artists))
    HomeRow(items = section.tiles, key = { it.key }) { tile -> RecentTile(tile, actions) }
}

@Composable
internal fun ShortcutsRow(section: ShortcutsSection, actions: LiquidActions) {
    val combine by rememberPreference(CombineDownloadsWithDeviceKey, false)
    val tiles = remember(section, combine) { if (combine) combineDeviceTiles(section.tiles) else section.tiles }
    val rows = remember(tiles) {
        if (tiles.size % 2 == 1) listOf(tiles.take(1)) + tiles.drop(1).chunked(2) else tiles.chunked(2)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = PageMargin, end = PageMargin, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile -> Shortcut(tile, actions, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Shortcut(tile: LatelyTile, actions: LiquidActions, modifier: Modifier) {
    val nav = actions.navController
    when (tile) {
        is LikedTile -> ShortcutCell(
            title = stringResource(R.string.home_tile_liked),
            cover = null,
            icon = Icons.Rounded.Favorite,
            plate = Liquid.colors.accent,
            onClick = {
                nav.navigate(if (tile.fromSpotify) "local_playlist/${SpotifyImportRepository.LIKED_SONGS_PLAYLIST_ID}" else "auto_playlist/liked")
            },
            modifier = modifier,
        )
        is PlaylistTile -> ShortcutCell(
            title = tile.playlist.title,
            cover = tile.playlist.thumbnails.firstOrNull(),
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            onClick = { actions.open(tile.playlist) },
            onLongClick = { actions.menu(tile.playlist) },
            modifier = modifier,
        )
        is PinnedTile -> {
            val item = remember(tile) { runCatching { tile.item.toYTItem() }.getOrNull() }
            ShortcutCell(
                title = tile.item.title,
                cover = tile.item.thumbnailUrl,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                onClick = { item?.let(actions::open) },
                onLongClick = item?.let { { actions.menu(it) } },
                modifier = modifier,
            )
        }
        is DownloadsTile -> ShortcutCell(
            title = stringResource(R.string.home_tile_downloads),
            cover = tile.cover,
            icon = Icons.Rounded.DownloadDone,
            onClick = { nav.navigate("auto_playlist/downloaded") },
            modifier = modifier,
        )
        is DeviceTile -> ShortcutCell(
            title = stringResource(R.string.home_tile_device),
            cover = tile.cover,
            icon = Icons.Rounded.PhoneAndroid,
            onClick = { nav.navigate("local_songs") },
            modifier = modifier,
        )
        is RecentlyAddedTile -> ShortcutCell(
            title = stringResource(R.string.liquid_recently_added),
            cover = tile.cover,
            icon = Icons.Rounded.LibraryAdd,
            onClick = { nav.navigate("recently_added") },
            modifier = modifier,
        )
        is HistoryTile -> ShortcutCell(
            title = stringResource(R.string.liquid_history),
            cover = tile.cover,
            icon = Icons.Rounded.History,
            onClick = { nav.navigate("history") },
            modifier = modifier,
        )
        else -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutCell(
    title: String,
    cover: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    plate: Color? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val px = with(LocalDensity.current) { ShortcutHeight.roundToPx() }
    val model = remember(cover, px) { cover?.resize(px, px) }
    val fill = if (colors.isDark) Color.White.copy(alpha = 0.1f) else colors.gray6
    Row(
        modifier
            .height(ShortcutHeight)
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model != null) {
            Artwork(model = model, shape = RectangleShape, hairline = false, modifier = Modifier.size(ShortcutHeight))
        } else {
            Box(
                Modifier
                    .size(ShortcutHeight)
                    .background(plate ?: colors.gray5),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (plate != null) Color.White else colors.label, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = title,
            style = LiquidTypography.footnote.copy(fontWeight = FontWeight.Bold),
            color = colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun RecentTile(tile: LatelyTile, actions: LiquidActions) {
    val nav = actions.navController
    when (tile) {
        is PinnedTile -> PinnedItemTile(tile, actions, width = RecentWidth)
        is AlbumTile -> HomeCard(
            title = tile.album.title,
            subtitle = tile.album.artists.firstOrNull()?.name,
            artwork = tile.album.thumbnailUrl?.resize(544, 544),
            width = RecentWidth,
            onClick = { actions.open(tile.album) },
            onLongClick = { actions.menu(tile.album) },
        )
        is ArtistTile -> HomeCard(
            title = tile.artist.title,
            subtitle = null,
            artwork = tile.image?.resize(544, 544),
            circular = true,
            width = ArtistWidth,
            onClick = { actions.open(tile.artist) },
            onLongClick = { actions.menu(tile.artist) },
        )
        is PlaylistTile -> HomeCard(
            title = tile.playlist.title,
            subtitle = stringResource(R.string.home_tile_playlist),
            artwork = tile.playlist.thumbnails.firstOrNull(),
            width = RecentWidth,
            onClick = { actions.open(tile.playlist) },
            onLongClick = { actions.menu(tile.playlist) },
        )
        else -> Unit
    }
}

@Composable
internal fun RotationGrid(section: RotationSection, activeId: String?, isPlaying: Boolean, actions: LiquidActions) {
    val title = stringResource(R.string.home_most_played)
    val songs = remember(section) { section.songs.map { it.song } }
    HomeSectionTitle(title, onClick = { actions.navController.navigate("stats") })
    HomeTrackGrid(items = songs, key = { it.id }) { song, index, modifier ->
        HomeTrackRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl?.resize(240, 240),
            isActive = song.id == activeId,
            onClick = {
                if (song.id == activeId) actions.togglePlayPause() else actions.playSongs(title, songs, index, origin = RotationOrigin)
            },
            onLongClick = { actions.menu(song) },
            rank = index + 1,
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
    HomeRow(items = section.songs, key = { it.id }) { song ->
        HomeCard(
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
        PicksShelf(title, section.picks, activeId, actions, onPlayed, width = RecentWidth, kicker = stringResource(R.string.home_kicker_more_like), name = section.artist)
        return
    }
    val songs = remember(section) { section.picks.map { it.song } }
    val origin = remember(section) { discoverOrigin(section.picks) }
    ArtistHeader(kicker = stringResource(R.string.home_kicker_more_like), name = section.artist, photo = null, onClick = null)
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
    kicker: String? = null,
    name: String? = null,
) {
    val songs = remember(picks) { picks.map { it.song } }
    val origin = remember(picks) { discoverOrigin(picks) }
    if (kicker != null && name != null) ArtistHeader(kicker = kicker, name = name, photo = null, onClick = null) else HomeHeader(title)
    HomeRow(items = songs, key = { it.id }) { song ->
        HomeCard(
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
    SectionHeader(
        title = title,
        subtitle = stringResource(R.string.new_to_you_subtitle),
        trailing = { HeaderPlay(label = stringResource(R.string.home_play_all), onClick = { actions.playSongs(title, songs, origin = origin) }) },
        modifier = Modifier.semantics { heading() },
    )
    HomeRow(items = picks, key = { it.song.id }) { pick ->
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
            kicker = stringResource(R.string.home_kicker_more_from),
            name = digest.name,
            photo = digest.thumbnail,
            onClick = { actions.navController.navigate("artist/${digest.artistId}") },
        )
        HomeRow(items = section.albums, key = { it.id }) { album -> YouTubeTile(album, actions, width = AlbumWidth) }
    }
    if (section.related.size >= 3) {
        ArtistHeader(kicker = stringResource(R.string.home_kicker_similar), name = digest.name, photo = digest.thumbnail, onClick = null)
        HomeRow(items = section.related, key = { it.id }) { artist -> YouTubeTile(artist, actions, width = ArtistWidth) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ArtistHeader(kicker: String, name: String, photo: String?, onClick: (() -> Unit)?) {
    HomeSectionTitle(
        title = name,
        label = kicker,
        thumbnail = photo?.let { url ->
            { Artwork(model = url.resize(120, 120), shape = CircleShape, hairline = false, modifier = Modifier.size(30.dp)) }
        },
        onClick = onClick,
    )
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
    val nav = actions.navController
    val openLiked = { nav.navigate("local_playlist/${SpotifyImportRepository.LIKED_SONGS_PLAYLIST_ID}") }
    ServiceBlock(SpotifyGreen) {
        if (tiles.isNotEmpty()) {
            ServiceHeader(title = stringResource(R.string.home_spotify_title), mark = { SpotifyMark() })
            val lone = tiles.singleOrNull() as? ServiceTile.Liked
            if (lone != null) {
                LoneLikedSongs(lone.liked, SpotifyGreen, openLiked)
            } else {
                HomeRow(items = tiles, key = { it.key }) { tile ->
                    when (tile) {
                        is ServiceTile.Liked -> LikedSongsTile(tile.liked, SpotifyGreen, openLiked)
                        is ServiceTile.SpotifyMix -> SpotifyMixTile(tile.mix, actions)
                        is ServiceTile.YouTubeMix -> Unit
                    }
                }
            }
        }
        if (section.discoveries.isNotEmpty()) {
            ServiceHeader(title = stringResource(R.string.home_spotify_discoveries), mark = { SpotifyMark() })
            HomeRow(items = section.discoveries, key = { it.spotifyId }) { mix -> SpotifyMixTile(mix, actions) }
        }
    }
}

@Composable
private fun SpotifyMixTile(mix: HomeSpotifyMix, actions: LiquidActions) {
    HomeCard(
        title = mix.name,
        subtitle = pluralStringResource(R.plurals.n_song, mix.songCount, mix.songCount),
        artwork = mix.imageUrl,
        width = TileWidth,
        onClick = { actions.navController.navigate("local_playlist/${mix.localPlaylistId}") },
    )
}

@Composable
private fun ServiceBlock(tint: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val end = 340.dp.toPx().coerceAtMost(size.height)
                drawRect(
                    Brush.verticalGradient(
                        0f to tint.copy(alpha = 0f),
                        0.18f to tint.copy(alpha = 0.15f),
                        1f to tint.copy(alpha = 0f),
                        startY = 0f,
                        endY = end,
                    ),
                    size = Size(size.width, end),
                )
            },
    ) { content() }
}

@Composable
private fun ServiceHeader(title: String, mark: @Composable () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .padding(start = PageMargin, end = PageMargin, top = HomeHeadingTop, bottom = HomeHeadingBottom)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = HomeHeadingStyle,
            color = Liquid.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServicePill(text: String, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = LiquidTypography.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = colors.label,
        maxLines = 1,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .pressScale(interaction, 0.95f)
            .clip(CircleShape)
            .background(colors.tertiaryFill)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
internal fun YouTubeShelf(section: YouTubeSection, actions: LiquidActions) {
    val tiles = remember(section) {
        listOfNotNull(section.liked?.let(ServiceTile::Liked)) + section.mixes.map(ServiceTile::YouTubeMix)
    }
    val nav = actions.navController
    ServiceBlock(YouTubeMusicRed) {
        if (tiles.isNotEmpty() || section.signIn) {
            ServiceHeader(
                title = stringResource(R.string.home_youtube_title),
                mark = { YouTubeMark() },
                trailing = if (section.signIn) {
                    { ServicePill(stringResource(R.string.home_sign_in)) { nav.navigate("login") } }
                } else {
                    null
                },
            )
        }
        val openLiked = { nav.navigate("auto_playlist/liked") }
        val lone = tiles.singleOrNull() as? ServiceTile.Liked
        if (lone != null) {
            LoneLikedSongs(lone.liked, YouTubeMusicRed, openLiked)
        } else if (tiles.isNotEmpty()) {
            HomeRow(items = tiles, key = { it.key }) { tile ->
                when (tile) {
                    is ServiceTile.Liked -> LikedSongsTile(tile.liked, YouTubeMusicRed, openLiked)
                    is ServiceTile.YouTubeMix -> YouTubeMixTile(tile.playlist, actions)
                    is ServiceTile.SpotifyMix -> Unit
                }
            }
        }
        if (section.discoveries.isNotEmpty()) {
            ServiceHeader(title = stringResource(R.string.home_youtube_discoveries), mark = { YouTubeMark() })
            HomeRow(items = section.discoveries, key = { it.id }) { mix -> YouTubeMixTile(mix, actions) }
        }
        if (section.playlists.isNotEmpty()) {
            ServiceHeader(title = stringResource(R.string.home_youtube_playlists), mark = { YouTubeMark() })
            HomeRow(items = section.playlists, key = { it.id }) { playlist -> PlaylistCoverTile(playlist, actions) }
        }
    }
}

@Composable
private fun YouTubeMixTile(mix: PlaylistItem, actions: LiquidActions) {
    HomeCard(
        title = mix.title,
        subtitle = mix.subtitleText(),
        artwork = mix.thumbnail?.resize(544, 544),
        width = TileWidth,
        onClick = { actions.open(mix) },
        onLongClick = { actions.menu(mix) },
    )
}

@Composable
private fun LikedSongsTile(liked: HomeLikedSongs, tint: Color, onClick: () -> Unit) {
    HomeCard(
        title = stringResource(R.string.home_tile_liked),
        subtitle = pluralStringResource(R.plurals.home_songs, liked.count, liked.count),
        artwork = null,
        width = TileWidth,
        onClick = onClick,
        badge = {
            CoverMosaic(liked.covers, tint, TileWidth)
            HeartMark(tint, 28.dp, Modifier.align(Alignment.BottomStart).padding(8.dp))
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoneLikedSongs(liked: HomeLikedSongs, tint: Color, onClick: () -> Unit) {
    val colors = Liquid.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRowHighlight(),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .padding(start = PageMargin, end = PageMargin - 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp)) {
            CoverMosaic(liked.covers, tint, 72.dp)
            HeartMark(tint, 22.dp, Modifier.align(Alignment.BottomStart).padding(4.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(stringResource(R.string.home_tile_liked), style = LiquidTypography.headline, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.home_songs, liked.count, liked.count), style = LiquidTypography.footnote, color = colors.secondaryLabel, maxLines = 1)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiaryLabel, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun HeartMark(tint: Color, size: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
private fun CoverMosaic(covers: List<String>, tint: Color, side: androidx.compose.ui.unit.Dp) {
    val px = with(LocalDensity.current) { side.roundToPx() }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(artworkRadius(side).coerceAtLeast(6.dp)))
            .background(tint),
    ) {
        when {
            covers.size >= 4 -> Column(Modifier.fillMaxSize()) {
                covers.take(4).chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        pair.forEach { cover ->
                            Artwork(
                                model = cover.resize(px * 2 / 3, px * 2 / 3),
                                shape = RectangleShape,
                                hairline = false,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            covers.isNotEmpty() -> Artwork(
                model = covers.first().resize(px * 4 / 3, px * 4 / 3),
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

@Composable
internal fun RecommendationShelf(section: RecommendationRowSection, activeId: String?, actions: LiquidActions) {
    val songs = remember(section) { section.items.filterIsInstance<SongItem>() }
    val queueTitle = when (section.kind) {
        RecRowKind.NewReleases -> stringResource(R.string.home_new_from_artists)
        else -> section.subject
    }
    val browse: (() -> Unit)? = section.browseId?.let { id ->
        if (section.kind == RecRowKind.MoreLike) {
            { actions.navController.navigate("artist/$id") }
        } else {
            { actions.openBrowse(com.music.innertube.models.BrowseEndpoint(browseId = id, params = section.params)) }
        }
    }
    when (section.kind) {
        RecRowKind.NewReleases -> HomeHeader(stringResource(R.string.home_new_from_artists))
        RecRowKind.BecauseYouLike -> ArtistHeader(kicker = stringResource(R.string.home_kicker_because_like), name = section.subject, photo = section.photo, onClick = null)
        RecRowKind.MoreLike -> ArtistHeader(kicker = stringResource(R.string.home_kicker_more_like), name = section.subject, photo = section.photo, onClick = browse)
        RecRowKind.Genre -> ArtistHeader(kicker = stringResource(R.string.home_kicker_listen), name = section.subject, photo = null, onClick = browse)
        RecRowKind.NewGenre -> ArtistHeader(kicker = stringResource(R.string.home_kicker_try), name = section.subject, photo = null, onClick = browse)
    }
    HomeRow(items = section.items, key = { it.id }) { item ->
        val subtitle = when (item) {
            is AlbumItem -> listOfNotNull(item.artists?.firstOrNull()?.name, item.year?.toString()).joinToString(" · ")
            is SongItem -> item.artists.joinToString { it.name }
            is PlaylistItem -> item.author?.name ?: stringResource(R.string.home_tile_playlist)
            else -> null
        }
        HomeCard(
            songId = (item as? SongItem)?.id,
            title = item.title,
            subtitle = subtitle,
            artwork = item.thumbnail?.resize(544, 544),
            circular = item is ArtistItem,
            titleColor = if (item.id == activeId) Liquid.colors.accent else Liquid.colors.label,
            onClick = {
                if (item is SongItem) actions.playSongItems(queueTitle, songs, songs.indexOf(item)) else actions.open(item)
            },
            onLongClick = { actions.menu(item) },
        )
    }
}
