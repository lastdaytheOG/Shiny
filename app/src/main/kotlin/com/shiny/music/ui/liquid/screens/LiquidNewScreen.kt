package com.shiny.music.ui.liquid.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.shiny.music.R
import com.shiny.music.home.AdjacentSection
import com.shiny.music.home.AppleSection
import com.shiny.music.home.FeaturedSection
import com.shiny.music.home.FreshSection
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
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.MoreButton
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.Shelf
import com.shiny.music.ui.liquid.ShelfGap
import com.shiny.music.ui.liquid.artworkRadius
import com.shiny.music.ui.liquid.SkeletonShelf
import com.shiny.music.ui.liquid.rememberListState
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberRowHighlight
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.ui.liquid.appearance.rememberReducedMotion
import com.shiny.music.ui.liquid.discover.DiscoverEyebrow
import com.shiny.music.ui.liquid.discover.DiscoverHeader
import com.shiny.music.ui.liquid.discover.DiscoverEvents
import com.shiny.music.ui.liquid.discover.DiscoverLifecycle
import com.shiny.music.ui.liquid.discover.DiscoverNoticeHost
import com.shiny.music.ui.liquid.discover.discoverCharts
import com.shiny.music.ui.liquid.discover.rememberDiscoverUi
import com.shiny.music.viewmodels.DiscoverViewModel
import com.shiny.music.viewmodels.NewViewModel

/**
 * Discover: Apple's public charts for the chosen region (see [discoverCharts]), then what is
 * worth hearing that the listener has not heard.
 *
 * Three distances, each with its own shape, so the page has a rhythm rather than five
 * carousels — one release set apart, the listener's own artists as a list to
 * read down, adjacent artists as a shelf to browse, the rest as a grid to scan, and the
 * Apple Music's current releases as a small discovery shelf.
 *
 * Every section comes from [com.shiny.music.home.NewFeedBuilder], and none of them is drawn
 * when it has nothing real in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidNewScreen(
    navController: NavController,
    viewModel: NewViewModel = hiltViewModel(),
    discover: DiscoverViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val feed by viewModel.feed.collectAsState()
    val online by viewModel.online.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val mediaMetadata by actions.playerConnection.mediaMetadata.collectAsState()
    val isPlaying by actions.playerConnection.isPlaying.collectAsState()

    val charts by discover.charts.collectAsState()
    val newToYou by discover.newToYou.collectAsState()
    val busy by discover.busy.collectAsState()
    val notice by discover.notice.collectAsState()
    val artistItems by discover.artistItems.collectAsState()
    val chartsRefreshing by discover.isRefreshing.collectAsState()
    val reduceMotion = rememberReducedMotion()
    val listState = rememberListState(discover.scroll)
    val pullState = rememberPullToRefreshState()
    val ui = rememberDiscoverUi(discover, actions, reduceMotion, listState)
    DiscoverLifecycle(discover)
    DiscoverEvents(discover, actions)

    val lifecycleOwner = LocalLifecycleOwner.current
    val viewer = remember { Any() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setVisible(viewer, true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setVisible(viewer, false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setVisible(viewer, false)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing || chartsRefreshing,
        onRefresh = {
            viewModel.refresh()
            discover.refresh()
        },
        state = pullState,
        indicator = { RefreshIndicator(pullState, isRefreshing || chartsRefreshing) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LargeTitlePage(
            title = stringResource(R.string.liquid_tab_discover),
            state = listState,
            scrollToTopSignal = navController,
        ) {
            if (newToYou.isNotEmpty()) {
                item(key = "new_to_you", contentType = "new_to_you") {
                    com.shiny.music.ui.liquid.home.NewToYouShelf(newToYou, actions)
                }
            }
            discoverCharts(
                charts = charts,
                ui = ui,
                activeId = mediaMetadata?.id,
                isPlaying = isPlaying,
                busy = busy,
                artistItems = artistItems,
            )

            val current = feed
            val chartsShown = charts?.isEmpty == false
            when {
                current == null -> item(key = "skeleton_new", contentType = "skeleton") { SkeletonShelf() }

                current.isEmpty -> if (!chartsShown) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = stringResource(R.string.liquid_new_nothing_title),
                            message = stringResource(R.string.liquid_new_nothing_body),
                            actionLabel = if (online) stringResource(R.string.retry) else null,
                            onAction = if (online) viewModel::refresh else null,
                        )
                    }
                }

                else -> {
                    if (chartsShown) item(key = "new_gap") { Spacer(Modifier.height(34.dp)) }
                    current.sections.forEach { section ->
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
        DiscoverNoticeHost(notice, reduceMotion, navController, discover::dismissNotice, discover::choose)
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
            item(key = "${section.key}_h") { DiscoverHeader(title = stringResource(R.string.liquid_new_from_your_artists), onSeeAll = null) }
            items(section.releases, key = { "ya_${it.album.id}" }) { release ->
                ReleaseRow(release, actions)
            }
        }

        is AdjacentSection -> {
            item(key = "${section.key}_h") { DiscoverHeader(title = stringResource(R.string.liquid_new_adjacent), onSeeAll = null) }
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
                DiscoverHeader(
                    title = stringResource(R.string.liquid_new_fresh),
                    onSeeAll = { navController.navigate("new_release") },
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

        is AppleSection -> {
            item(key = "${section.key}_h") {
                DiscoverHeader(title = stringResource(R.string.liquid_new_from_apple), onSeeAll = null)
            }
            item(key = section.key) {
                Shelf(items = section.albums, key = { "apple_${it.id}" }) { album ->
                    MediaTile(
                        title = album.title,
                        subtitle = album.subtitleText(),
                        artwork = album.thumbnail.resize(544, 544),
                        width = 152.dp,
                        onClick = { actions.open(album) },
                        onLongClick = { actions.menu(album) },
                    )
                }
            }
        }
    }
}

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
            .padding(top = 6.dp, bottom = 6.dp),
    ) {
        DiscoverEyebrow(eyebrow, colors.secondaryLabel, Modifier.padding(horizontal = PageMargin))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction, 0.985f)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { actions.open(album) },
                    onLongClick = { actions.menu(album) },
                )
                .padding(horizontal = PageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                model = album.thumbnail.resize(544, 544),
                shape = RoundedCornerShape(artworkRadius(FeaturedArtwork)),
                modifier = Modifier.size(FeaturedArtwork),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = album.title,
                    style = LiquidTypography.title3.copy(fontWeight = FontWeight.Bold),
                    color = colors.label,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                album.subtitleText()?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = LiquidTypography.subheadline,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (detail != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = detail,
                        style = LiquidTypography.footnote,
                        color = colors.tertiaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val FeaturedArtwork = 132.dp

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

