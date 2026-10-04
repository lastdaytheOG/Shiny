package com.shiny.music.ui.liquid.screens

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.home.AdjacentSection
import com.shiny.music.home.FeaturedSection
import com.shiny.music.home.FreshSection
import com.shiny.music.home.HomeCharts
import com.shiny.music.home.NewChartsSection
import com.shiny.music.home.NewReason
import com.shiny.music.home.NewRelease
import com.shiny.music.home.NewSection
import com.shiny.music.home.YourArtistsSection
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.LiquidSegmentedControl
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.MoreButton
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SectionHeader
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.ShelfGap
import com.shiny.music.ui.liquid.SkeletonShelf
import com.shiny.music.ui.liquid.SongRow
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.viewmodels.NewViewModel
import java.util.Locale

/**
 * New: what is worth hearing that the listener has not heard.
 *
 * Three distances, each with its own shape, so the page has a rhythm rather than five
 * carousels — one release given the whole width, the listener's own artists as a list to
 * read down, adjacent artists as a shelf to browse, the rest as a grid to scan, and the
 * charts as what they are, a ranked list.
 *
 * Every section comes from [com.shiny.music.home.NewFeedBuilder], and none of them is drawn
 * when it has nothing real in it.
 */
/** How much of a chart New shows inline before sending the listener to the full one. */
private const val CHART_ROWS = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidNewScreen(
    navController: NavController,
    viewModel: NewViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val feed by viewModel.feed.collectAsState()
    val online by viewModel.online.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()

    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setVisible(false)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        state = pullState,
        indicator = { RefreshIndicator(pullState, isRefreshing) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LargeTitlePage(
            title = stringResource(R.string.liquid_tab_new),
            state = listState,
            scrollToTopSignal = navController,
        ) {
            val current = feed
            when {
                current == null -> items(3, key = { "skeleton_$it" }, contentType = { "skeleton" }) { SkeletonShelf() }

                current.isEmpty -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = stringResource(R.string.liquid_new_nothing_title),
                        message = stringResource(R.string.liquid_new_nothing_body),
                        actionLabel = if (online) stringResource(R.string.retry) else null,
                        onAction = if (online) viewModel::refresh else null,
                    )
                }

                else -> current.sections.forEach { section ->
                    newSection(
                        section = section,
                        actions = actions,
                        navController = navController,
                        activeId = mediaMetadata?.id,
                        isPlaying = isPlaying,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.newSection(
    section: NewSection,
    actions: LiquidActions,
    navController: NavController,
    activeId: String?,
    isPlaying: Boolean,
) {
    when (section) {
        is FeaturedSection -> item(key = section.key) { FeaturedRelease(section.release, actions) }

        is YourArtistsSection -> {
            item(key = "${section.key}_h") { SectionHeader(title = stringResource(R.string.liquid_new_from_your_artists)) }
            items(section.releases, key = { "ya_${it.album.id}" }) { release ->
                ReleaseRow(release, actions)
            }
        }

        is AdjacentSection -> {
            item(key = "${section.key}_h") { SectionHeader(title = stringResource(R.string.liquid_new_adjacent)) }
            item(key = section.key) {
                Shelf(items = section.releases, key = { "adj_${it.album.id}" }) { release ->
                    val reason = release.reason
                    MediaTile(
                        title = release.album.title,
                        subtitle = if (reason is NewReason.Like) {
                            stringResource(R.string.liquid_new_because_related, reason.to)
                        } else {
                            release.album.subtitleText()
                        },
                        artwork = release.album.thumbnail.resize(544, 544),
                        width = 152.dp,
                        onClick = { actions.open(release.album) },
                        onLongClick = { actions.menu(release.album) },
                    )
                }
            }
        }

        is FreshSection -> {
            item(key = "${section.key}_h") {
                SectionHeader(
                    title = stringResource(R.string.liquid_new_fresh),
                    onClick = { navController.navigate("new_release") },
                )
            }
            items(section.albums.chunked(2), key = { "fresh_${it.first().id}" }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageMargin)
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(ShelfGap),
                ) {
                    row.forEach { album ->
                        Box(Modifier.weight(1f)) {
                            MediaTile(
                                title = album.title,
                                subtitle = album.subtitleText(),
                                artwork = album.thumbnail.resize(544, 544),
                                width = Dp.Unspecified,
                                onClick = { actions.open(album) },
                                onLongClick = { actions.menu(album) },
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        is NewChartsSection -> item(key = section.key) {
            ChartsPanel(section, actions, navController, activeId, isPlaying)
        }
    }
}

// ---------------------------------------------------------------------------------------
// The lead
// ---------------------------------------------------------------------------------------

/**
 * The one release Shiny is recommending, given the whole width.
 *
 * Full bleed and set over the artwork rather than beside it: this is the only block on the
 * page that breaks the keyline, which is what makes it read as a lead rather than as the
 * first item of a list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedRelease(release: NewRelease, actions: LiquidActions) {
    val colors = Liquid.colors
    val album = release.album
    val interaction = remember { MutableInteractionSource() }
    val eyebrow = when (val reason = release.reason) {
        is NewReason.Plays -> stringResource(R.string.home_new_from, reason.artist)
        is NewReason.Like -> stringResource(R.string.liquid_new_because_related, reason.to)
        NewReason.Fresh -> stringResource(R.string.liquid_new_featured)
    }
    val detail = (release.reason as? NewReason.Plays)
        ?.takeIf { it.plays > 0 }
        ?.let { pluralStringResource(R.plurals.home_new_reason, it.plays, it.plays) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .pressScale(interaction, 0.985f)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { actions.open(album) },
                onLongClick = { actions.menu(album) },
            ),
    ) {
        Text(
            text = eyebrow.uppercase(Locale.getDefault()),
            style = LiquidTypography.caption2.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
            color = colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = PageMargin),
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(maxWidth * 0.78f)) {
                Artwork(
                    model = album.thumbnail.resize(1080, 1080),
                    shape = RoundedCornerShape(0.dp),
                    hairline = false,
                    modifier = Modifier.fillMaxSize(),
                )
                // The title sits in the lower third, so the scrim has to be real by the time
                // it gets there — a pale sleeve is the case that decides this, not a dark one
                // — while the top third stays clear and the cover is still the cover.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.30f to Color.Transparent,
                                0.62f to Color.Black.copy(alpha = 0.34f),
                                0.82f to Color.Black.copy(alpha = 0.64f),
                                1f to Color.Black.copy(alpha = 0.86f),
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = PageMargin)
                        .padding(bottom = 18.dp),
                ) {
                    Text(
                        text = album.title,
                        style = LiquidTypography.title1.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    album.subtitleText()?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = LiquidTypography.subheadline,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (detail != null) {
            Text(
                text = detail,
                style = LiquidTypography.footnote,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = PageMargin)
                    .padding(top = 8.dp),
            )
        }
    }
}

/**
 * A release by somebody the listener plays: a row, not a tile, because the reason it is here
 * needs a line of its own and a tile has nowhere to put one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReleaseRow(release: NewRelease, actions: LiquidActions) {
    val colors = Liquid.colors
    val album = release.album
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                onClick = { actions.open(album) },
                onLongClick = { actions.menu(album) },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = PageMargin)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                model = album.thumbnail.resize(360, 360),
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.size(64.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 4.dp),
            ) {
                Text(
                    text = album.title,
                    style = LiquidTypography.body.copy(fontWeight = FontWeight.Medium),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Releases pulled from an artist's own page often carry no artist list, so
                // their subtitle is a bare year. The reason knows whose release it is —
                // which is the useful half of that line anyway.
                val plays = release.reason as? NewReason.Plays
                val subtitle = listOfNotNull(
                    plays?.artist ?: album.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() },
                    album.year?.toString(),
                ).joinToString(" · ").ifBlank { album.subtitleText().orEmpty() }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = LiquidTypography.subheadline,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                plays?.takeIf { it.plays > 0 }?.let { reason ->
                    Text(
                        text = pluralStringResource(R.plurals.home_new_reason, reason.plays, reason.plays),
                        style = LiquidTypography.caption1,
                        color = colors.tertiaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MoreButton(onClick = { actions.menu(album) })
        }
        Hairline(Modifier.align(Alignment.BottomStart), startIndent = PageMargin + 64.dp + 14.dp)
    }
}

// ---------------------------------------------------------------------------------------
// The charts — the source's own ranking, named as the source names it
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartsPanel(
    section: NewChartsSection,
    actions: LiquidActions,
    navController: NavController,
    activeId: String?,
    isPlaying: Boolean,
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

    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_charts),
            subtitle = stringResource(R.string.home_charts_source, refreshed),
        )
        if (section.charts.size > 1) {
            val global = stringResource(R.string.home_charts_global)
            LiquidSegmentedControl(
                items = section.charts.map { c ->
                    if (c.scope == "ZZ") global else HomeCharts.countryName(c.scope)
                },
                selectedIndex = index,
                onSelect = { selected = it },
                modifier = Modifier
                    .padding(horizontal = PageMargin)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(),
            )
        }
        Text(
            text = chart.title,
            style = LiquidTypography.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = PageMargin, vertical = 4.dp),
        )
        // A straight column, not the side-paging grid the shelves use: a ranked list that
        // pages sideways shows the *next* column's rank digits at the screen edge with their
        // rows still off screen, which reads as a rendering fault rather than as a peek.
        val songs = chart.songs
        val shown = songs.take(CHART_ROWS)
        shown.forEachIndexed { i, song ->
            SongRow(
                songId = song.id,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                artwork = song.thumbnail,
                isActive = song.id == activeId,
                isPlaying = isPlaying,
                explicit = song.explicit,
                onClick = {
                    if (song.id == activeId) actions.togglePlayPause() else actions.playSongItems(chart.title, songs, i)
                },
                onLongClick = { actions.menu(song) },
                onMore = { actions.menu(song) },
                showSeparator = i != shown.lastIndex,
                artworkSize = 48.dp,
                leadingRank = song.chartPosition ?: (i + 1),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRowHighlight(),
                    onClick = { navController.navigate("online_playlist/${chart.playlistId}") },
                )
                .padding(horizontal = PageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_charts_full),
                style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        // The charts close the page, and the floating tab bar shrinks to a pill once the page
        // is scrolled — which is exactly when this row is on screen. Clear it.
        Spacer(Modifier.height(20.dp))
    }
}
