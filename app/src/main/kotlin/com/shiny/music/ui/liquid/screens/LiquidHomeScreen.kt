package com.shiny.music.ui.liquid.screens

import com.shiny.music.home.HistorySection
import com.shiny.music.home.ArtistListSection
import com.shiny.music.ui.liquid.home.HistoryShelf
import com.shiny.music.ui.liquid.home.ArtistListShelf
import com.shiny.music.ui.liquid.home.HomePage
import com.shiny.music.ui.liquid.home.HeroHeightFraction
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.shiny.music.home.RecommendationRowSection
import com.shiny.music.ui.liquid.home.RecommendationShelf
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.YTItem
import com.music.innertube.utils.parseCookieString
import com.shiny.music.R
import com.shiny.music.constants.InnerTubeCookieKey
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.LocalItem
import com.shiny.music.db.entities.Playlist
import com.shiny.music.home.ContinueSection
import com.shiny.music.home.DeepDiveSection
import com.shiny.music.home.DiscoverSection
import com.shiny.music.home.HeroMixSection
import com.shiny.music.home.HomeInterest
import com.shiny.music.home.HomeSection
import com.shiny.music.home.LatelySection
import com.shiny.music.home.MoreLikeSection
import com.shiny.music.home.OfflineNoticeSection
import com.shiny.music.home.PickArtistsSection
import com.shiny.music.home.PinnedSection
import com.shiny.music.home.RecentArtistsSection
import com.shiny.music.home.RotationSection
import com.shiny.music.home.ShortcutsSection
import com.shiny.music.home.SpotifySection
import com.shiny.music.home.StartHereSection
import com.shiny.music.home.YouTubeSection
import com.shiny.music.home.barCovers
import com.shiny.music.home.isTopPick
import com.shiny.music.home.moreLikeLists
import com.shiny.music.ui.component.LocalMenuState
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.SkeletonShelf
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.appearance.PageTransitions
import com.shiny.music.ui.liquid.home.ContinueShelf
import com.shiny.music.ui.liquid.home.DiscoverShelf
import com.shiny.music.ui.liquid.home.HeroCarousel
import com.shiny.music.ui.liquid.home.HeroSkeleton
import com.shiny.music.ui.liquid.home.HomeAmbience
import com.shiny.music.ui.liquid.home.HomeHeroState
import com.shiny.music.ui.liquid.home.rememberReveal
import com.shiny.music.ui.liquid.home.MoreFromArtistShelf
import com.shiny.music.ui.liquid.home.MoreLikeShelf
import com.shiny.music.ui.liquid.home.OfflineNotice
import com.shiny.music.ui.liquid.home.PickArtistsCard
import com.shiny.music.ui.liquid.home.PinnedShelf
import com.shiny.music.ui.liquid.home.QuietStart
import com.shiny.music.ui.liquid.home.RecentArtistsShelf
import com.shiny.music.ui.liquid.home.RecentlyPlayedShelf
import com.shiny.music.ui.liquid.home.RecommendationStyleSheet
import com.shiny.music.ui.liquid.home.RotationGrid
import com.shiny.music.ui.liquid.home.ShortcutsRow
import com.shiny.music.ui.liquid.home.SpotifyShelf
import com.shiny.music.ui.liquid.home.YouTubeShelf
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.rememberListState
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.PerfTrace
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.HomeViewModel
import kotlinx.coroutines.delay

private const val IMPRESSION_DELAY_MS = 1_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val menuState = LocalMenuState.current
    val playerConnection = actions.playerConnection
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val feed by viewModel.homeFeed.collectAsState()
    LaunchedEffect(Unit) { viewModel.onHomeShown() }
    ReportDrawnWhen {
        (feed != null).also { ready -> if (ready) PerfTrace.homeReadyEnd() }
    }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(cookie) { "SAPISID" in parseCookieString(cookie) }
    val reduceMotion = LocalShinyAppearance.current.transitions == PageTransitions.Instant

    val listState = rememberListState(viewModel.scroll)
    val pullState = rememberPullToRefreshState()
    val heroList = rememberLazyListState()
    val heroHeightPx = with(LocalDensity.current) { (LocalConfiguration.current.screenHeightDp.dp * HeroHeightFraction).toPx() }
    val hero = remember(heroList) { HomeHeroState(heroList) }

    val (topPicks, shelves) = remember(feed) { feed?.sections.orEmpty().partition { it.isTopPick } }
    val mix = remember(shelves) { shelves.filterIsInstance<HeroMixSection>().firstOrNull() }
    val mixCovers = remember(mix) { mix?.barCovers().orEmpty() }
    val listShelves = remember(shelves) { moreLikeLists(shelves.filterNot { it is OfflineNoticeSection || it is HeroMixSection }) }
    val likelyIds = remember(feed) {
        feed?.sections.orEmpty().mapNotNull { section ->
            when (section) {
                is HeroMixSection -> section.songs.firstOrNull()?.id
                is ContinueSection -> section.songs.firstOrNull()?.id
                is RotationSection -> section.songs.firstOrNull()?.song?.id
                else -> null
            }
        }
    }
    LaunchedEffect(likelyIds) {
        com.shiny.music.playback.PlaybackPrewarm.likely(likelyIds)
    }
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
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        state = pullState,
        indicator = { RefreshIndicator(pullState, isRefreshing) },
        modifier = Modifier.fillMaxSize(),
    ) {
        HomePage(
            state = listState,
            heroHeightPx = heroHeightPx,
            navController = navController,
            behind = { HomeAmbience(hero, listState, reduceMotion) },
            avatar = {
                AccountAvatar(
                    imageUrl = if (isLoggedIn) accountImageUrl else null,
                    onClick = { navController.navigate("settings") },
                )
            },
        ) {
            if (feed == null) {
                item(key = "skeleton_hero", contentType = "skeleton_hero") { HeroSkeleton() }
                item(key = "skeleton_shelf", contentType = "skeleton") { SkeletonShelf(tile = 156.dp) }
                return@HomePage
            }

            if (OfflineNoticeSection in shelves) {
                item(key = OfflineNoticeSection.key, contentType = "offline") {
                    HomeItem(reduceMotion, OfflineNoticeSection.key, viewModel.revealed) { OfflineNotice() }
                }
            }

            if (mix != null || topPicks.isNotEmpty()) {
                item(key = "hero", contentType = "hero") {
                    HomeItem(reduceMotion, "hero", viewModel.revealed) {
                        HeroCarousel(
                            hero = hero,
                            mix = mix,
                            picks = topPicks,
                            takenCovers = mixCovers,
                            queueTitle = queueTitle,
                            isPlaying = isPlaying,
                            reduceMotion = reduceMotion,
                            actions = actions,
                            onPlayedDiscovery = { viewModel.note(HomeInterest.Kind.Discovery) },
                            onChooseStyle = {
                                menuState.show {
                                    RecommendationStyleSheet(viewModel.intelligence, onDone = menuState::dismiss)
                                }
                            },
                            onShown = { section -> NoteShown(section, viewModel) },
                        )
                    }
                }
            }

            shelves.filterNot { it is OfflineNoticeSection || it is HeroMixSection }.forEach { section ->
                item(key = section.key, contentType = section::class.simpleName) {
                    NoteShown(section, viewModel)
                    HomeItem(reduceMotion, section.key, viewModel.revealed) {
                        HomeShelf(
                            section = section,
                            asList = section.key in listShelves,
                            actions = actions,
                            activeId = mediaMetadata?.id,
                            isPlaying = isPlaying,
                            onPlayedDiscovery = { viewModel.note(HomeInterest.Kind.Discovery) },
                            onPickArtists = { navController.navigate("settings/recommendations/artists") },
                            onDismissPickArtists = viewModel::dismissPickArtists,
                        )
                    }
                }
            }

            item(key = "home_footer") { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun NoteShown(section: HomeSection, viewModel: HomeViewModel) {
    LaunchedEffect(section) {
        delay(IMPRESSION_DELAY_MS)
        viewModel.noteShown(section)
    }
}

@Composable
private fun LazyItemScope.HomeItem(reduceMotion: Boolean, key: String, revealed: MutableSet<String>, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(rememberReveal(key, revealed, reduceMotion))
            .then(
                if (reduceMotion) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        fadeInSpec = spring(stiffness = 300f),
                        placementSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                        fadeOutSpec = spring(stiffness = 600f),
                    )
                }
            ),
    ) { content() }
}

@Composable
private fun HomeShelf(
    section: HomeSection,
    asList: Boolean,
    actions: LiquidActions,
    activeId: String?,
    isPlaying: Boolean,
    onPlayedDiscovery: () -> Unit,
    onPickArtists: () -> Unit,
    onDismissPickArtists: () -> Unit,
) {
    when (section) {
        is StartHereSection -> QuietStart(section.online)
        is ShortcutsSection -> ShortcutsRow(section, actions)
        is PinnedSection -> PinnedShelf(section, actions)
        is RecentArtistsSection -> RecentArtistsShelf(section, actions)
        is LatelySection -> RecentlyPlayedShelf(section, actions)
        is ContinueSection -> ContinueShelf(section, activeId, actions)
        is RotationSection -> RotationGrid(section, activeId, isPlaying, actions)
        is DiscoverSection -> DiscoverShelf(section, activeId, actions, onPlayedDiscovery)
        is MoreLikeSection -> MoreLikeShelf(section, asList, activeId, isPlaying, actions, onPlayedDiscovery)
        is DeepDiveSection -> MoreFromArtistShelf(section, actions)
        is SpotifySection -> SpotifyShelf(section, actions)
        is YouTubeSection -> YouTubeShelf(section, actions)
        is RecommendationRowSection -> RecommendationShelf(section, activeId, actions)
        is HistorySection -> HistoryShelf(section, activeId, actions)
        is ArtistListSection -> ArtistListShelf(section, actions)
        PickArtistsSection -> PickArtistsCard(onPick = onPickArtists, onDismiss = onDismissPickArtists)
        else -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountAvatar(imageUrl: String?, onClick: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.settings)
    Box(
        modifier = Modifier
            .size(36.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(colors.gray5)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            Artwork(model = imageUrl, shape = CircleShape, modifier = Modifier.fillMaxSize(), placeholder = Icons.Rounded.Person)
        } else {
            Icon(Icons.Rounded.Person, null, tint = colors.gray, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun YouTubeTile(
    item: YTItem,
    actions: LiquidActions,
    isActive: Boolean = false,
    width: androidx.compose.ui.unit.Dp = 164.dp,
) {
    com.shiny.music.ui.liquid.home.HomeCard(
        songId = (item as? com.music.innertube.models.SongItem)?.id,
        title = item.title,
        subtitle = item.subtitleText(),
        artwork = item.thumbnail?.resize(544, 544),
        circular = item is ArtistItem,
        width = width,
        onClick = { actions.open(item) },
        onLongClick = { actions.menu(item) },
        titleColor = if (isActive) Liquid.colors.accent else Liquid.colors.label,
    )
}

@Composable
internal fun LocalTile(item: LocalItem, actions: LiquidActions, width: androidx.compose.ui.unit.Dp = 164.dp) {
    com.shiny.music.ui.liquid.home.HomeCard(
        songId = (item as? com.shiny.music.db.entities.Song)?.id,
        title = item.title,
        subtitle = item.subtitleText(),
        artwork = when (item) {
            is Playlist -> item.thumbnails.firstOrNull()
            else -> item.thumbnailUrl?.resize(544, 544)
        },
        circular = item is Artist,
        width = width,
        onClick = { actions.open(item) },
        onLongClick = { actions.menu(item) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.RefreshIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val top = statusBarHeight()
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = top + 10.dp)
            .graphicsLayer {
                val f = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
                alpha = f
                val s = 0.6f + 0.4f * f
                scaleX = s
                scaleY = s
            },
    ) {
        // An invisible infinite spinner would still redraw the window sixty times a second.
        if (refreshing || state.distanceFraction > 0f) ActivityIndicator(size = 28.dp)
    }
}
