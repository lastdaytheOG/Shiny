package com.shiny.music.ui.liquid.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.ui.screens.OptionStats
import com.shiny.music.db.entities.Artist
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.utils.resize
import com.shiny.music.viewmodels.StatsViewModel

/** The four ranges offered, as indexes into the view model's continuous periods. */
private val Ranges = listOf(R.string.liquid_range_week to 0, R.string.liquid_range_month to 1, R.string.liquid_range_year to 4, R.string.liquid_range_all to 5)

/**
 * Listening stats in the spirit of Replay: one big number for the time you spent, the
 * counts beside it, then your top songs ranked and your top artists and albums as shelves.
 */
@Composable
fun LiquidStatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val option by viewModel.selectedOption.collectAsState()
    val index by viewModel.indexChips.collectAsState()
    val playTime by viewModel.totalPlayTime.collectAsState()
    val songCount by viewModel.uniqueSongsCount.collectAsState()
    val artistCount by viewModel.uniqueArtistsCount.collectAsState()
    val albumCount by viewModel.uniqueAlbumsCount.collectAsState()
    val songStats by viewModel.mostPlayedSongsStats.collectAsState()
    val songs by viewModel.mostPlayedSongs.collectAsState()
    val artists by viewModel.mostPlayedArtists.collectAsState()
    val albums by viewModel.mostPlayedAlbums.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()

    val selectedRange = if (option == OptionStats.CONTINUOUS) Ranges.indexOfFirst { it.second == index }.coerceAtLeast(0) else 0
    val minutes = playTime / 60_000L
    val playsById = songStats.associate { it.id to it.songCountListened }
    val title = stringResource(R.string.liquid_stats)

    LargeTitlePage(
        title = title,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
    ) {
        item(key = "range") {
            LiquidSegmentedControl(
                items = Ranges.map { stringResource(it.first) },
                selectedIndex = selectedRange,
                onSelect = {
                    viewModel.selectedOption.value = OptionStats.CONTINUOUS
                    viewModel.indexChips.value = Ranges[it].second
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageMargin, vertical = 8.dp),
            )
        }

        item(key = "hero") {
            val top = artists.firstOrNull()
            ReplayCard(
                minutes = minutes,
                songs = songCount,
                artists = artistCount,
                albums = albumCount,
                topArtist = top,
                onTopArtist = { top?.let(actions::open) },
            )
        }

        if (songs.isEmpty() && artists.isEmpty()) {
            item(key = "empty") { EmptyState(icon = Icons.Rounded.BarChart, title = stringResource(R.string.liquid_stats_empty)) }
        }

        if (songs.isNotEmpty()) {
            item(key = "songs_header") {
                SectionHeader(title = stringResource(R.string.liquid_top_songs), onClick = { actions.playSongs(title, songs) })
            }
            itemsIndexed(songs.take(10), key = { _, s -> "st_${s.id}" }) { i, song ->
                val plays = playsById[song.id]
                SongRow(
                    songId = song.id,
                    title = song.title,
                    subtitle = listOfNotNull(song.artists.joinToString { it.name }.takeIf { it.isNotBlank() }, plays?.let { pluralStringResource(R.plurals.liquid_plays, it, it) }).joinToString(" · "),
                    artwork = song.thumbnailUrl,
                    leadingRank = i + 1,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    onClick = { actions.playSongs(title, songs, startIndex = i) },
                    onLongClick = { actions.menu(song) },
                    onMore = { actions.menu(song) },
                )
            }
        }

        if (artists.isNotEmpty()) {
            item(key = "artists_header") { SectionHeader(title = stringResource(R.string.artists)) }
            item(key = "artists") {
                Shelf(items = artists.take(15), key = { "sa_${it.id}" }) { artist ->
                    MediaTile(
                        title = artist.title,
                        subtitle = pluralStringResource(R.plurals.liquid_plays, artist.songCount, artist.songCount),
                        artwork = artist.thumbnailUrl?.resize(544, 544),
                        circular = true,
                        width = 132.dp,
                        onClick = { actions.open(artist) },
                        onLongClick = { actions.menu(artist) },
                    )
                }
            }
        }

        if (albums.isNotEmpty()) {
            item(key = "albums_header") { SectionHeader(title = stringResource(R.string.albums)) }
            item(key = "albums") {
                Shelf(items = albums.take(15), key = { "sal_${it.id}" }) { album ->
                    MediaTile(
                        title = album.title,
                        subtitle = album.artists.joinToString { it.name },
                        artwork = album.thumbnailUrl?.resize(544, 544),
                        onClick = { actions.open(album) },
                        onLongClick = { actions.menu(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, style = LiquidTypography.title2, color = Color.White)
        Text(label, style = LiquidTypography.footnote, color = Color.White.copy(alpha = 0.8f))
    }
}

/**
 * The Replay card: coloured from your top artist's photo, the minutes as the one big
 * number, the three counts under it, and the artist themselves at the foot.
 */
@Composable
private fun ReplayCard(
    minutes: Long,
    songs: Int,
    artists: Int,
    albums: Int,
    topArtist: Artist?,
    onTopArtist: () -> Unit,
) {
    val colors = Liquid.colors
    val fallback = ArtworkTones(deep = lerp(colors.accent, Color.Black, 0.35f), vivid = colors.accent)
    val tones = rememberArtworkTones(topArtist?.thumbnailUrl?.resize(544, 544), fallback)
    val deep by animateColorAsState(tones.deep, tween(600), label = "replayDeep")
    val vivid by animateColorAsState(tones.vivid, tween(600), label = "replayVivid")
    Column(
        Modifier
            .padding(horizontal = PageMargin)
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(lerp(vivid, deep, 0.25f), deep), end = Offset(size.width * 0.4f, size.height)))
                drawRect(Brush.radialGradient(listOf(vivid.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.9f))
            }
            .padding(20.dp),
    ) {
        Text(
            stringResource(R.string.liquid_minutes_listened).uppercase(),
            style = LiquidTypography.caption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
            color = Color.White.copy(alpha = 0.78f),
        )
        Text(
            text = "%,d".format(minutes),
            style = LiquidTypography.largeTitle.copy(fontSize = 60.sp, lineHeight = 66.sp, letterSpacing = (-1).sp),
            color = Color.White,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("%,d".format(songs), stringResource(R.string.songs), Modifier.weight(1f))
            StatTile("%,d".format(artists), stringResource(R.string.artists), Modifier.weight(1f))
            StatTile("%,d".format(albums), stringResource(R.string.albums), Modifier.weight(1f))
        }
        if (topArtist != null) {
            val interaction = remember { MutableInteractionSource() }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(interactionSource = interaction, indication = null, onClick = onTopArtist)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    model = topArtist.thumbnailUrl?.resize(240, 240),
                    shape = CircleShape,
                    placeholder = Icons.Rounded.Person,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.liquid_top_artist).uppercase(),
                        style = LiquidTypography.caption2.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    Text(topArtist.title, style = LiquidTypography.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralStringResource(R.plurals.liquid_plays, topArtist.songCount, topArtist.songCount),
                        style = LiquidTypography.footnote,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
