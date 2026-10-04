package com.shiny.music.ui.liquid.home

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.pages.MoodAndGenres
import com.shiny.music.R
import com.shiny.music.db.entities.Song
import com.shiny.music.spotifyimport.SpotifyImportRepository
import com.shiny.music.home.AlbumTile
import com.shiny.music.home.AlbumsInProgressSection
import com.shiny.music.home.AroundNowSection
import com.shiny.music.home.ArtistTile
import com.shiny.music.home.AffinityTier
import com.shiny.music.home.ChartMove
import com.shiny.music.home.ChartsSection
import com.shiny.music.home.ContinueKind
import com.shiny.music.home.ContinueSection
import com.shiny.music.home.Daypart
import com.shiny.music.home.DeepDiveSection
import com.shiny.music.home.DeviceTile
import com.shiny.music.home.DiscoverPick
import com.shiny.music.home.DiscoverSection
import com.shiny.music.home.DownloadsTile
import com.shiny.music.home.InsightsSection
import com.shiny.music.home.LatelySection
import com.shiny.music.home.LatelyTile
import com.shiny.music.home.LikedTile
import com.shiny.music.home.moveOf
import com.shiny.music.home.MoodsSection
import com.shiny.music.home.HomeLikedSongs
import com.shiny.music.home.HomeSpotifyMix
import com.shiny.music.home.PinnedTile
import com.shiny.music.home.PlaylistTile
import com.shiny.music.home.ReadyOfflineSection
import com.shiny.music.home.RecentlyAddedSection
import com.shiny.music.home.RediscoverSection
import com.shiny.music.home.RotationSection
import com.shiny.music.home.SpotifySection
import com.shiny.music.home.SurpriseKind
import com.shiny.music.home.SurprisePick
import com.shiny.music.home.YouTubeSection
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.ButtonTone
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidButton
import com.shiny.music.ui.liquid.LiquidSegmentedControl
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
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------------------

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

/** The small tinted "Play" capsule a section header carries when the section is playable. */
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
private fun SubLabel(text: String) {
    Text(
        text = text,
        style = LiquidTypography.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = Liquid.colors.secondaryLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = PageMargin, end = PageMargin, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val colors = Liquid.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(colors.tertiaryFill),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(colors.label.copy(alpha = 0.7f)),
        )
    }
}

private fun Song.artistLine(): String = artists.joinToString { it.name }

/** Month and year of a history timestamp (wall time stored as UTC). */
private fun monthYear(wallMs: Long): String =
    LocalDateTime.ofEpochSecond(wallMs / 1000, 0, ZoneOffset.UTC)
        .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))

/** [template] with its `%n$s` arguments set in [emphasis], the rest left as it is. */
private fun emphasized(template: String, args: List<String>, emphasis: SpanStyle): AnnotatedString = buildAnnotatedString {
    var last = 0
    Regex("%(\\d)\\\$s").findAll(template).forEach { match ->
        append(template.substring(last, match.range.first))
        withStyle(emphasis) { append(args.getOrElse(match.groupValues[1].toInt() - 1) { "" }) }
        last = match.range.last + 1
    }
    append(template.substring(last))
}

// ---------------------------------------------------------------------------------------
// Lately — a two-column index of what was just playing
// ---------------------------------------------------------------------------------------

@Composable
internal fun LatelyGrid(section: LatelySection, actions: LiquidActions) {
    HomeHeader(stringResource(R.string.home_lately))
    Column(
        Modifier.padding(horizontal = PageMargin),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        section.tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> LatelyCell(tile, actions, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LatelyCell(tile: LatelyTile, actions: LiquidActions, modifier: Modifier) {
    val colors = Liquid.colors
    val nav = actions.navController
    val pinnedItem = remember(tile) { (tile as? PinnedTile)?.let { runCatching { it.item.toYTItem() }.getOrNull() } }
    val title: String
    val meta: String
    var artwork: String? = null
    var icon: ImageVector? = null
    var circular = false
    val onClick: () -> Unit
    var onLongClick: (() -> Unit)? = null
    when (tile) {
        is PinnedTile -> {
            title = tile.item.title
            meta = listOfNotNull(stringResource(R.string.home_tile_pinned), tile.item.subtitle).joinToString(" · ")
            artwork = tile.item.thumbnailUrl?.resize(144, 144)
            circular = pinnedItem is ArtistItem
            onClick = { pinnedItem?.let(actions::open) }
            onLongClick = pinnedItem?.let { item -> { actions.menu(item) } }
        }
        is AlbumTile -> {
            title = tile.album.title
            meta = listOfNotNull(
                stringResource(R.string.home_tile_album),
                tile.album.artists.firstOrNull()?.name,
            ).joinToString(" · ")
            artwork = tile.album.thumbnailUrl?.resize(144, 144)
            onClick = { actions.open(tile.album) }
            onLongClick = { actions.menu(tile.album) }
        }
        is ArtistTile -> {
            title = tile.artist.title
            meta = stringResource(R.string.home_tile_artist) + " · " +
                pluralStringResource(R.plurals.liquid_plays, tile.plays, tile.plays)
            artwork = tile.image?.resize(144, 144)
            circular = true
            onClick = { actions.open(tile.artist) }
            onLongClick = { actions.menu(tile.artist) }
        }
        is PlaylistTile -> {
            title = tile.playlist.title
            meta = stringResource(if (tile.pinned) R.string.home_tile_pinned else R.string.home_tile_playlist) + " · " +
                pluralStringResource(R.plurals.home_songs, tile.playlist.songCount, tile.playlist.songCount)
            artwork = tile.playlist.thumbnails.firstOrNull()
            onClick = { actions.open(tile.playlist) }
            onLongClick = { actions.menu(tile.playlist) }
        }
        is LikedTile -> {
            title = stringResource(R.string.home_tile_liked)
            meta = pluralStringResource(R.plurals.home_songs, tile.count, tile.count)
            icon = Icons.Rounded.Favorite
            onClick = { nav.navigate("auto_playlist/liked") }
        }
        is DownloadsTile -> {
            title = stringResource(R.string.home_tile_downloads)
            meta = pluralStringResource(R.plurals.home_songs, tile.count, tile.count)
            icon = Icons.Rounded.DownloadDone
            onClick = { nav.navigate("auto_playlist/downloaded") }
        }
        is DeviceTile -> {
            title = stringResource(R.string.home_tile_device)
            meta = pluralStringResource(R.plurals.home_songs, tile.count, tile.count)
            icon = Icons.Rounded.PhoneAndroid
            onClick = { nav.navigate("local_songs") }
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
        } else {
            Artwork(
                model = artwork,
                shape = if (circular) CircleShape else RoundedCornerShape(8.dp),
                modifier = Modifier.size(52.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(title, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = LiquidTypography.caption1, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Your Rotation — ranked, vertical, with the numbers that ranked it
// ---------------------------------------------------------------------------------------

@Composable
internal fun RotationList(
    section: RotationSection,
    activeId: String?,
    isPlaying: Boolean,
    actions: LiquidActions,
) {
    val title = stringResource(R.string.home_rotation)
    val songs = remember(section) { section.songs.map { it.song } }
    HomeHeader(
        title = title,
        subtitle = stringResource(R.string.home_rotation_subtitle),
        trailing = { HeaderPlay(onClick = { actions.playSongs(title, songs) }) },
    )
    val shown = section.songs.take(6)
    shown.forEachIndexed { index, ranked ->
        val song = ranked.song
        SongRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine() + " · " + pluralStringResource(R.plurals.home_plays_month, ranked.monthPlays, ranked.monthPlays),
            artwork = song.thumbnailUrl,
            leadingRank = index + 1,
            isActive = song.id == activeId,
            isPlaying = isPlaying,
            explicit = song.song.explicit,
            onClick = {
                if (song.id == activeId) actions.togglePlayPause() else actions.playSongs(title, songs, index)
            },
            onLongClick = { actions.menu(song) },
            onMore = { actions.menu(song) },
            showSeparator = index < shown.lastIndex,
            artworkSize = 48.dp,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Around this time — the listener's own pattern, and moods to choose from
// ---------------------------------------------------------------------------------------

@Composable
private fun aroundTitle(daypart: Daypart) = stringResource(
    when (daypart) {
        Daypart.Morning -> R.string.home_around_morning
        Daypart.Afternoon -> R.string.home_around_afternoon
        Daypart.Evening -> R.string.home_around_evening
        Daypart.Night -> R.string.home_around_night
    }
)

/**
 * What the listener actually plays at this hour, as the songs themselves.
 *
 * This used to be a grey panel with a four-up mosaic and a sentence counting the songs —
 * a summary of a thing instead of the thing. The paged grid shows real titles the listener
 * recognises, which is both more useful and less like a dashboard tile.
 */
@Composable
internal fun AroundNowCard(
    section: AroundNowSection,
    activeId: String?,
    isPlaying: Boolean,
    actions: LiquidActions,
    onMoodOpened: () -> Unit,
) {
    val title = aroundTitle(section.daypart)
    HomeHeader(
        title = title,
        subtitle = daypartEyebrow(section.daypart),
        trailing = { HeaderPlay(onClick = { actions.playSongs(title, section.songs, shuffle = true) }, label = stringResource(R.string.liquid_shuffle)) },
    )
    SongGrid(items = section.songs.take(12), key = { it.id }, rows = 3) { song, index, modifier, last ->
        SongRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl,
            isActive = song.id == activeId,
            isPlaying = isPlaying,
            explicit = song.song.explicit,
            onClick = {
                if (song.id == activeId) actions.togglePlayPause() else actions.playSongs(title, section.songs, index)
            },
            onLongClick = { actions.menu(song) },
            onMore = { actions.menu(song) },
            showSeparator = !last,
            startPadding = 0.dp,
            artworkSize = 48.dp,
            modifier = modifier,
        )
    }
    if (section.moods.isNotEmpty()) {
        SubLabel(stringResource(R.string.home_or_set_mood))
        MoodRail(section.moods, actions, rows = 1, onOpened = onMoodOpened)
    }
}

/**
 * Moods and genres as compact editorial tiles rather than coloured chips.
 *
 * YouTube publishes a stripe colour with every mood, and the obvious thing to do with it —
 * flood a big rounded rectangle — gives a page of primary-coloured lozenges that looks
 * like a settings screen for a toy. Here the colour is spent where colour is cheap and
 * reads well: a hairline rule down the leading edge and a barely-there wash behind the
 * type. The tile itself stays a neutral Liquid surface, so a rail of twelve moods reads as
 * one calm block of typography with twelve accents in it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoodTile(mood: MoodAndGenres.Item, actions: LiquidActions, onOpened: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val stripe = remember(mood.stripeColor) { Color(mood.stripeColor) }
    Row(
        modifier = Modifier
            .size(width = MoodTileWidth, height = MoodTileHeight)
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.secondaryBackground)
            .background(Brush.horizontalGradient(listOf(stripe.copy(alpha = 0.16f), Color.Transparent)))
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button) {
                onOpened()
                actions.navController.navigate("youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(vertical = 10.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(stripe),
        )
        Text(
            text = mood.title,
            style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
        )
    }
}

private val MoodTileWidth = 158.dp
private val MoodTileHeight = 58.dp

/** A rail of [MoodTile]s, one row inline under a section or two as a section of its own. */
@Composable
private fun MoodRail(moods: List<MoodAndGenres.Item>, actions: LiquidActions, rows: Int, onOpened: () -> Unit) {
    if (rows <= 1) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = PageMargin),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(moods, key = { it.title }) { mood -> MoodTile(mood, actions, onOpened) }
        }
        return
    }
    val state = rememberLazyGridState()
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows.coerceAtMost(moods.size).coerceAtLeast(1)),
        state = state,
        contentPadding = PaddingValues(horizontal = PageMargin),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(MoodTileHeight * rows + 10.dp * (rows - 1)),
    ) {
        items(moods, key = { it.title }) { mood -> MoodTile(mood, actions, onOpened) }
    }
}

@Composable
internal fun MoodsBlock(section: MoodsSection, actions: LiquidActions, onOpened: () -> Unit) {
    HomeHeader(stringResource(R.string.home_set_mood), subtitle = daypartEyebrow(section.daypart))
    MoodRail(section.moods, actions, rows = 2, onOpened = onOpened)
}

// ---------------------------------------------------------------------------------------
// Keep listening — the thread out of the last thing played
// ---------------------------------------------------------------------------------------

/**
 * The first personal shelf any listener ever sees, and the one that needs the least to
 * exist: one counted play.
 *
 * Its heading is written from what the play actually supports. An artist heard once gets
 * "More from <artist>", which is a true statement about a song that was played, not a
 * claim about taste. Only when the same artist has been come back to does the wording
 * step up ([AffinityTier]), and it never reaches "artists you love" from a single listen.
 * The whole section retires on its own once the rotation has something better to say.
 */
@Composable
internal fun ContinueShelf(section: ContinueSection, activeId: String?, actions: LiquidActions) {
    val name = section.throughName
    val title = when {
        section.kind == ContinueKind.Artist && name != null -> when (section.tier) {
            AffinityTier.Favourite -> stringResource(R.string.home_continue_favourite, name)
            AffinityTier.Warming -> stringResource(R.string.home_continue_warming, name)
            AffinityTier.Exploring -> stringResource(R.string.home_continue_artist, name)
        }
        else -> stringResource(R.string.home_continue_related, section.seed.title)
    }
    HomeHeader(
        title = title,
        subtitle = stringResource(R.string.home_continue_subtitle, section.seed.title),
        trailing = { HeaderPlay(onClick = { actions.playSongs(title, section.songs) }) },
    )
    Shelf(items = section.songs, key = { it.id }) { song ->
        MediaTile(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl?.resize(544, 544),
            width = 148.dp,
            titleColor = if (song.id == activeId) Liquid.colors.accent else Liquid.colors.label,
            onClick = { actions.playSongs(title, section.songs, section.songs.indexOf(song)) },
            onLongClick = { actions.menu(song) },
        )
    }
}

// ---------------------------------------------------------------------------------------
// Discover — never played, each with its reason
// ---------------------------------------------------------------------------------------

/**
 * Never-played songs, each tied to one the listener has.
 *
 * The heading is written from the data rather than stamped on it: when most of the shelf
 * came from one song or one artist it says so by name, and when the seeds are spread
 * across a whole history it falls back to the plain editorial title. Nothing here reads
 * "Because you listened to X" over a shelf that has nothing to do with X.
 */
@Composable
internal fun DiscoverShelf(section: DiscoverSection, actions: LiquidActions, onPlayed: () -> Unit) {
    val title = when {
        section.lead != null -> stringResource(R.string.home_discover_like_song, section.lead!!.title)
        section.leadArtist != null -> stringResource(R.string.home_discover_like_artist, section.leadArtist!!)
        else -> stringResource(R.string.home_discover)
    }
    val songs = remember(section) { section.picks.map { it.song } }
    HomeHeader(
        title = title,
        subtitle = stringResource(R.string.home_discover_subtitle),
        trailing = {
            HeaderPlay(label = stringResource(R.string.home_play_all), onClick = {
                onPlayed()
                actions.playSongs(title, songs)
            })
        },
    )
    Shelf(items = section.picks, key = { it.song.id }) { pick ->
        DiscoverTile(
            pick = pick,
            onClick = {
                onPlayed()
                actions.playSongs(title, songs, section.picks.indexOf(pick))
            },
            onLongClick = { actions.menu(pick.song) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverTile(pick: DiscoverPick, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val because = stringResource(R.string.home_because, pick.because.title)
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
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = colors.tertiaryLabel, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(because, style = LiquidTypography.caption1, color = colors.tertiaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Go deeper — one of the listener's artists, opened up
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeepDiveBlock(section: DeepDiveSection, actions: LiquidActions) {
    val colors = Liquid.colors
    val digest = section.digest
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(start = PageMargin, end = PageMargin - 4.dp, top = 28.dp, bottom = 4.dp)
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .combinedClickable(interactionSource = interaction, indication = null) {
                actions.navController.navigate("artist/${digest.artistId}")
            }
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(model = digest.thumbnail?.resize(240, 240), shape = CircleShape, modifier = Modifier.size(58.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Eyebrow(stringResource(R.string.home_deep_eyebrow), colors.accent)
            Text(digest.name, style = LiquidTypography.title2, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(
                    if (section.fromLibrary) R.plurals.home_deep_meta_saved else R.plurals.home_deep_meta,
                    section.songsHeard,
                    section.songsHeard,
                ),
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                maxLines = 1,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondaryLabel, modifier = Modifier.size(26.dp))
    }
    if (section.albums.isNotEmpty()) {
        SubLabel(stringResource(R.string.home_deep_albums))
        Shelf(items = section.albums, key = { it.id }) { album -> YouTubeTile(album, actions, width = 140.dp) }
    }
    if (section.related.isNotEmpty()) {
        SubLabel(stringResource(R.string.home_deep_related, digest.name))
        Shelf(items = section.related, key = { it.id }) { artist -> YouTubeTile(artist, actions, width = 104.dp) }
    }
}

// ---------------------------------------------------------------------------------------
// The charts — the source's own ranking, named as the source names it
// ---------------------------------------------------------------------------------------

/**
 * The charts, as a chart rather than as an API response.
 *
 * Three things do the work. The rank is set in the display cut at 22pt and right-aligned,
 * so the numbers form a real column and the top three carry full-strength label colour
 * while the rest recede — the page reads as a ranking at a glance, before any title is
 * read. The movement flag beside each row is Shiny's own: YouTube publishes today's chart
 * and nothing else, so a climb or a fall can only come from comparing two of our own
 * fetches, and it appears only from the second day onwards ([ChartMove.Unknown] draws
 * nothing rather than inventing a "new"). And the source is named in full, verbatim,
 * with its age — this is YouTube's ranking, presented, not Shiny's ranking, implied.
 */
@Composable
internal fun ChartsBlock(
    section: ChartsSection,
    activeId: String?,
    isPlaying: Boolean,
    actions: LiquidActions,
    onOpenChart: () -> Unit,
) {
    val colors = Liquid.colors
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, section.charts.lastIndex)
    val chart = section.charts[index]
    val now = System.currentTimeMillis()
    val refreshed = if (now - section.refreshedAt < 60_000) {
        stringResource(R.string.home_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(section.refreshedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
            .let { if (Locale.getDefault().language == "en") it.replaceFirstChar { c -> c.lowercase() } else it }
    }
    HomeHeader(stringResource(R.string.home_charts), subtitle = stringResource(R.string.home_charts_source, refreshed))
    if (section.charts.size > 1) {
        val global = stringResource(R.string.home_charts_global)
        LiquidSegmentedControl(
            items = section.charts.map { c ->
                if (c.scope == "ZZ") global
                else runCatching { Locale.Builder().setRegion(c.scope).build().displayCountry }.getOrNull()?.ifBlank { null } ?: c.scope
            },
            selectedIndex = index,
            onSelect = {
                selected = it
                onOpenChart()
            },
            modifier = Modifier
                .padding(horizontal = PageMargin)
                .padding(bottom = 10.dp)
                .fillMaxWidth(),
        )
    }
    Eyebrow(
        text = chart.title,
        color = colors.tertiaryLabel,
        modifier = Modifier.padding(horizontal = PageMargin, vertical = 6.dp),
    )
    val shown = remember(chart) { chart.songs.take(5) }
    shown.forEachIndexed { i, song ->
        val position = song.chartPosition ?: (i + 1)
        SongRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artists.joinToString { it.name },
            artwork = song.thumbnail,
            leadingRank = position,
            leadingRankStyle = ChartRankStyle,
            leadingRankColor = if (position <= 3) colors.label else colors.secondaryLabel,
            isActive = song.id == activeId,
            isPlaying = isPlaying,
            explicit = song.explicit,
            trailing = { ChartMovement(chart.moveOf(song.id, position)) },
            onClick = {
                if (song.id == activeId) actions.togglePlayPause() else actions.playSongItems(chart.title, chart.songs, i)
            },
            onLongClick = { actions.menu(song) },
            onMore = { actions.menu(song) },
            showSeparator = i < shown.lastIndex,
            artworkSize = 52.dp,
        )
    }
    SeeAllRow(stringResource(R.string.home_charts_full)) {
        onOpenChart()
        actions.navController.navigate("online_playlist/${chart.playlistId}")
    }
}

/** The chart rank: display cut, right-aligned, so the numerals stack into a column. */
private val ChartRankStyle = LiquidTypography.title2.copy(fontSize = 21.sp, textAlign = TextAlign.End)

/**
 * How far a song moved since the last chart Shiny held. Nothing is drawn until there is a
 * previous day to compare against, and nothing is drawn for a song that has not moved —
 * an unbroken column of "–" is noise, and the absence of a flag already says "steady".
 */
@Composable
private fun ChartMovement(move: ChartMove) {
    val colors = Liquid.colors
    val (glyph, places, tint) = when (move) {
        is ChartMove.Up -> Triple("▲", move.places.toString(), colors.green)
        is ChartMove.Down -> Triple("▼", move.places.toString(), colors.gray)
        ChartMove.New -> Triple(null, stringResource(R.string.home_chart_new), colors.accent)
        ChartMove.Steady, ChartMove.Unknown -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 6.dp, end = 2.dp),
    ) {
        if (glyph != null) {
            Text(glyph, style = LiquidTypography.caption2.copy(fontSize = 8.sp), color = tint)
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = places,
            style = LiquidTypography.caption2.copy(fontWeight = FontWeight.Bold),
            color = tint,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeeAllRow(text: String, onClick: () -> Unit) {
    val colors = Liquid.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(indication = rememberRowHighlight(), interactionSource = remember { MutableInteractionSource() }, role = Role.Button, onClick = onClick)
            .padding(horizontal = PageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.accent, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.accent, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------------------------------
// The library, alive
// ---------------------------------------------------------------------------------------

@Composable
internal fun RediscoverShelf(section: RediscoverSection, actions: LiquidActions) {
    val title = stringResource(R.string.home_rediscover)
    val songs = remember(section) { section.songs.map { it.song } }
    HomeHeader(
        title = title,
        subtitle = stringResource(R.string.home_rediscover_subtitle),
        trailing = { HeaderPlay(onClick = { actions.playSongs(title, songs) }) },
    )
    Shelf(items = section.songs, key = { it.song.id }) { ranked ->
        MediaTile(
            songId = ranked.song.id,
            title = ranked.song.title,
            subtitle = stringResource(R.string.home_last_played, monthYear(ranked.lastPlayed)),
            artwork = ranked.song.thumbnailUrl?.resize(544, 544),
            width = 140.dp,
            onClick = { actions.playSongs(title, songs, songs.indexOf(ranked.song)) },
            onLongClick = { actions.menu(ranked.song) },
        )
    }
}

// ---------------------------------------------------------------------------------------
// The listener's services — Spotify and YouTube Music, each in a row of its own
// ---------------------------------------------------------------------------------------

/** YouTube's red, as the sign-in header draws it. */
private val YouTubeMusicRed = Color(0xFFFF0033)

private val ServiceTileWidth = 150.dp

/** One tile of a service's row: its liked songs first, then that service's own mixes. */
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

/**
 * The listener's Spotify: their Liked Songs, then the mixes Spotify made for them, each opening
 * the library playlist its songs were matched into. Only Spotify's music — YouTube Music has its
 * own row. Spotify's covers are its own URLs, not Google's, so they are shown as served.
 */
@Composable
internal fun SpotifyShelf(section: SpotifySection, actions: LiquidActions) {
    val tiles = remember(section) {
        listOfNotNull(section.liked?.let(ServiceTile::Liked)) + section.mixes.map(ServiceTile::SpotifyMix)
    }
    HomeHeader(
        title = stringResource(R.string.home_spotify_title),
        subtitle = stringResource(R.string.home_spotify_subtitle),
        trailing = { SpotifyMark() },
    )
    Shelf(items = tiles, key = { it.key }) { tile ->
        when (tile) {
            is ServiceTile.Liked -> LikedSongsTile(tile.liked, SpotifyGreen) {
                actions.navController.navigate("local_playlist/${SpotifyImportRepository.LIKED_SONGS_PLAYLIST_ID}")
            }
            is ServiceTile.SpotifyMix -> MediaTile(
                title = tile.mix.name,
                subtitle = tile.mix.description ?: pluralStringResource(R.plurals.n_song, tile.mix.songCount, tile.mix.songCount),
                artwork = tile.mix.imageUrl,
                width = ServiceTileWidth,
                onClick = { actions.navController.navigate("local_playlist/${tile.mix.localPlaylistId}") },
            )
            is ServiceTile.YouTubeMix -> Unit
        }
    }
}

/**
 * The signed-in YouTube Music account: its liked songs, then the mixes YouTube Music made for
 * it (Supermix, Discover Mix...). Only YouTube Music's music — Spotify has its own row.
 */
@Composable
internal fun YouTubeShelf(section: YouTubeSection, actions: LiquidActions) {
    val tiles = remember(section) {
        listOfNotNull(section.liked?.let(ServiceTile::Liked)) + section.mixes.map(ServiceTile::YouTubeMix)
    }
    HomeHeader(
        title = stringResource(R.string.home_youtube_title),
        subtitle = stringResource(R.string.home_youtube_subtitle),
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
                width = ServiceTileWidth,
                onClick = { actions.open(tile.playlist) },
                onLongClick = { actions.menu(tile.playlist) },
            )
            is ServiceTile.SpotifyMix -> Unit
        }
    }
}

/**
 * A service's liked songs as the first sleeve of its row: the four latest covers, and a heart
 * in the service's own colour so the two rows' Liked Songs are never mistaken for each other.
 */
@Composable
private fun LikedSongsTile(liked: HomeLikedSongs, tint: Color, onClick: () -> Unit) {
    MediaTile(
        title = stringResource(R.string.home_tile_liked),
        subtitle = pluralStringResource(R.plurals.home_songs, liked.count, liked.count),
        artwork = null,
        width = ServiceTileWidth,
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

/** Four covers two by two; with fewer, the newest alone; with none, the service's colour. */
@Composable
private fun CoverMosaic(covers: List<String>, tint: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(artworkRadius(ServiceTileWidth)))
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

/** Spotify's own mark in its green, beside the row's title. */
@Composable
private fun SpotifyMark() {
    Icon(
        painterResource(R.drawable.ic_spotify),
        contentDescription = null,
        tint = SpotifyGreen,
        modifier = Modifier.size(24.dp),
    )
}

/** YouTube Music's red disc and play glyph, as the sign-in page draws it. */
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumsInProgressShelf(section: AlbumsInProgressSection, actions: LiquidActions) {
    val colors = Liquid.colors
    HomeHeader(stringResource(R.string.home_albums_started))
    Shelf(items = section.albums, key = { it.album.id }) { progress ->
        val interaction = remember { MutableInteractionSource() }
        Column(
            Modifier
                .width(140.dp)
                .pressScale(interaction, 0.965f)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { actions.open(progress.album) },
                    onLongClick = { actions.menu(progress.album) },
                )
                .semantics(mergeDescendants = true) {},
        ) {
            Artwork(
                model = progress.album.thumbnailUrl?.resize(544, 544),
                shape = RoundedCornerShape(artworkRadius(140.dp)),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(Modifier.height(8.dp))
            ProgressLine(progress.heard.toFloat() / progress.total)
            Spacer(Modifier.height(6.dp))
            Text(progress.album.title, style = LiquidTypography.footnote.copy(fontWeight = FontWeight.Medium), color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.home_album_progress, progress.heard, progress.total),
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun RecentlyAddedGrid(section: RecentlyAddedSection, activeId: String?, isPlaying: Boolean, actions: LiquidActions) {
    val title = stringResource(R.string.liquid_recently_added)
    HomeHeader(
        title = title,
        subtitle = stringResource(R.string.home_recently_added_subtitle),
        trailing = { HeaderPlay(onClick = { actions.playSongs(title, section.songs) }) },
    )
    SongGrid(items = section.songs, key = { it.id }, rows = 3) { song, index, modifier, last ->
        SongRow(
            songId = song.id,
            title = song.title,
            subtitle = song.artistLine(),
            artwork = song.thumbnailUrl,
            isActive = song.id == activeId,
            isPlaying = isPlaying,
            explicit = song.song.explicit,
            onClick = { if (song.id == activeId) actions.togglePlayPause() else actions.playSongs(title, section.songs, index) },
            onLongClick = { actions.menu(song) },
            onMore = { actions.menu(song) },
            showSeparator = !last,
            startPadding = 0.dp,
            artworkSize = 48.dp,
            modifier = modifier,
        )
    }
}

/**
 * Everything that plays with no network at all.
 *
 * The sleeves stand in a crate across the page keyline and the sentence sits under them —
 * no panel, because the shelf above and the shelf below are already the page's rhythm and
 * a grey rectangle here just interrupts it.
 */
@Composable
internal fun ReadyOfflineCard(section: ReadyOfflineSection, actions: LiquidActions) {
    val colors = Liquid.colors
    val title = stringResource(R.string.home_ready_offline)
    val total = section.localSongs + section.downloadedSongs
    HomeHeader(
        title = title,
        subtitle = stringResource(R.string.home_ready_offline_split, section.downloadedSongs, section.localSongs),
        trailing = { HeaderPlay(onClick = { actions.playSongs(title, section.songs) }) },
    )
    Column(Modifier.padding(horizontal = PageMargin)) {
        CoverStrip(section.songs.mapNotNull { it.thumbnailUrl }.distinct().take(7))
        Spacer(Modifier.height(14.dp))
        Text(
            text = pluralStringResource(R.plurals.home_ready_offline_body, total, total),
            style = LiquidTypography.headline,
            color = colors.label,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton(
                text = stringResource(R.string.liquid_play),
                icon = Icons.Rounded.PlayArrow,
                tone = ButtonTone.Filled,
                height = 40.dp,
                onClick = { actions.playSongs(title, section.songs) },
            )
            LiquidButton(
                text = stringResource(R.string.liquid_shuffle),
                icon = Icons.Rounded.Shuffle,
                tone = ButtonTone.Tinted,
                height = 40.dp,
                onClick = { actions.playSongs(title, section.songs, shuffle = true) },
            )
        }
    }
}

/** Covers overlapping in a row, like sleeves standing in a crate. */
@Composable
private fun CoverStrip(covers: List<String>) {
    val size = 44.dp
    Box(Modifier.height(size)) {
        covers.forEachIndexed { i, cover ->
            Artwork(
                model = cover.resize(144, 144),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .offset(x = size * 0.72f * i)
                    .size(size),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------
// Your week — true sentences, not a dashboard
// ---------------------------------------------------------------------------------------

@Composable
internal fun InsightsBlock(section: InsightsSection, actions: LiquidActions, onPlaySong: (Song) -> Unit) {
    val colors = Liquid.colors
    HomeHeader(stringResource(R.string.home_week), onClick = { actions.navController.navigate("stats") })
    val duration = if (section.weekMinutes >= 60) {
        stringResource(R.string.home_week_hours, section.weekMinutes / 60, section.weekMinutes % 60)
    } else {
        stringResource(R.string.home_week_minutes, section.weekMinutes)
    }
    val sentence = emphasized(
        template = stringResource(R.string.home_week_sentence),
        args = listOf(
            pluralStringResource(R.plurals.home_songs, section.weekSongs, section.weekSongs),
            pluralStringResource(R.plurals.home_week_artists, section.weekArtists, section.weekArtists),
            duration,
        ),
        emphasis = SpanStyle(color = colors.label, fontWeight = FontWeight.SemiBold),
    )
    Text(
        text = sentence,
        style = LiquidTypography.title3.copy(fontWeight = FontWeight.Normal),
        color = colors.secondaryLabel,
        modifier = Modifier.padding(horizontal = PageMargin),
    )
    Spacer(Modifier.height(10.dp))
    section.topArtist?.let { artist ->
        InsightRow(
            label = stringResource(R.string.home_week_top_artist),
            value = artist.title,
            meta = pluralStringResource(R.plurals.home_week_plays, section.topArtistPlays, section.topArtistPlays),
            artwork = section.topArtistImage?.resize(144, 144),
            circular = true,
            onClick = { actions.open(artist) },
        )
    }
    section.replayed?.let { song ->
        InsightRow(
            label = stringResource(R.string.home_week_top_song),
            value = song.title,
            meta = pluralStringResource(R.plurals.home_week_plays, section.replayedPlays, section.replayedPlays),
            artwork = song.thumbnailUrl,
            circular = false,
            onClick = { onPlaySong(song) },
        )
    }
    if (section.newSongs > 0) {
        InsightRow(
            label = null,
            value = pluralStringResource(R.plurals.home_week_new, section.newSongs, section.newSongs),
            meta = null,
            artwork = null,
            circular = false,
            icon = Icons.Rounded.AutoAwesome,
            onClick = { actions.navController.navigate("stats") },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InsightRow(
    label: String?,
    value: String,
    meta: String?,
    artwork: String?,
    circular: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val colors = Liquid.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(indication = rememberRowHighlight(), interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = PageMargin, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
        } else {
            Artwork(model = artwork, shape = if (circular) CircleShape else RoundedCornerShape(8.dp), modifier = Modifier.size(44.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            label?.let { Text(it, style = LiquidTypography.caption1, color = colors.secondaryLabel, maxLines = 1) }
            Text(value, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        meta?.let { Text(it, style = LiquidTypography.footnote, color = colors.secondaryLabel, maxLines = 1, modifier = Modifier.padding(start = 8.dp)) }
    }
}

// ---------------------------------------------------------------------------------------
// Surprise Me
// ---------------------------------------------------------------------------------------

@Composable
internal fun SurpriseCard(last: SurprisePick?, onSurprise: () -> Unit) {
    val colors = Liquid.colors
    Column(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 28.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.accent.copy(alpha = 0.12f))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Casino, null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.home_surprise),
                style = LiquidTypography.title3.copy(fontWeight = FontWeight.Bold),
                color = colors.label,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.home_surprise_body), style = LiquidTypography.subheadline, color = colors.secondaryLabel)
        if (last != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(model = last.song.thumbnailUrl, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(42.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(last.song.title, style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(
                            when (last.kind) {
                                SurpriseKind.Forgotten -> R.string.home_surprise_forgotten
                                SurpriseKind.Unexplored -> R.string.home_surprise_unexplored
                                SurpriseKind.NewToYou -> R.string.home_surprise_new
                                SurpriseKind.OnDevice -> R.string.home_surprise_device
                            }
                        ),
                        style = LiquidTypography.caption1,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LiquidButton(
            text = stringResource(if (last == null) R.string.home_surprise_button else R.string.home_surprise_again),
            icon = Icons.Rounded.Casino,
            tone = ButtonTone.Filled,
            height = 44.dp,
            onClick = onSurprise,
        )
    }
}
