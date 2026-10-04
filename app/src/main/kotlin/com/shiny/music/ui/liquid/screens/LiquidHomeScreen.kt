package com.shiny.music.ui.liquid.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.shiny.music.db.entities.Song
import com.shiny.music.home.AlbumsInProgressSection
import com.shiny.music.home.AroundNowSection
import com.shiny.music.home.ChartsSection
import com.shiny.music.home.ContinueSection
import com.shiny.music.home.DeepDiveSection
import com.shiny.music.home.DiscoverSection
import com.shiny.music.home.HeroMixSection
import com.shiny.music.home.HomeInterest
import com.shiny.music.home.HomeSection
import com.shiny.music.home.InsightsSection
import com.shiny.music.home.LatelySection
import com.shiny.music.home.MoodsSection
import com.shiny.music.home.OfflineNoticeSection
import com.shiny.music.home.ReadyOfflineSection
import com.shiny.music.home.RecentlyAddedSection
import com.shiny.music.home.RediscoverSection
import com.shiny.music.home.RotationSection
import com.shiny.music.home.SpotifySection
import com.shiny.music.home.YouTubeSection
import com.shiny.music.home.SurpriseKind
import com.shiny.music.home.SurpriseSection
import com.shiny.music.home.StartHereSection
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidActions
import com.shiny.music.ui.liquid.MediaTile
import com.shiny.music.ui.liquid.SkeletonShelf
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.appearance.PageTransitions
import com.shiny.music.ui.liquid.home.AlbumsInProgressShelf
import com.shiny.music.ui.liquid.home.AroundNowCard
import com.shiny.music.ui.liquid.home.ChartsBlock
import com.shiny.music.ui.liquid.home.ContinueShelf
import com.shiny.music.ui.liquid.home.DeepDiveBlock
import com.shiny.music.ui.liquid.home.DiscoverShelf
import com.shiny.music.ui.liquid.home.HeroMixCard
import com.shiny.music.ui.liquid.home.HomePath
import com.shiny.music.ui.liquid.home.InsightsBlock
import com.shiny.music.ui.liquid.home.LatelyGrid
import com.shiny.music.ui.liquid.home.SpotifyShelf
import com.shiny.music.ui.liquid.home.YouTubeShelf
import com.shiny.music.ui.liquid.home.MoodsBlock
import com.shiny.music.ui.liquid.home.OfflineNotice
import com.shiny.music.ui.liquid.home.ReadyOfflineCard
import com.shiny.music.ui.liquid.home.RecentlyAddedGrid
import com.shiny.music.ui.liquid.home.RediscoverShelf
import com.shiny.music.ui.liquid.home.ResumeCard
import com.shiny.music.ui.liquid.home.RotationList
import com.shiny.music.ui.liquid.home.StartHereBlock
import com.shiny.music.ui.liquid.home.SurpriseCard
import com.shiny.music.ui.liquid.home.mixTitle
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.liquid.subtitleText
import com.shiny.music.ui.screens.Screens
import com.shiny.music.ui.theme.pressScale
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference
import com.shiny.music.viewmodels.HomeViewModel

/**
 * Home: a feed that answers, in order of what matters at this moment, *what to play now,
 * what was just playing, what the listener keeps returning to, what to discover next and
 * what is happening in music* — assembled by [HomeViewModel] from real listening only.
 *
 * The screen does not decide what exists or in which order; the feed does. It only adds
 * the one live element the feed cannot know about: a paused session to pick up again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidHomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val actions = rememberLiquidActions(navController) ?: return
    val playerConnection = actions.playerConnection
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val feed by viewModel.feed.collectAsState()
    val online by viewModel.online.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastSurprise by viewModel.lastSurprise.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(cookie) { "SAPISID" in parseCookieString(cookie) }
    val reduceMotion = LocalShinyAppearance.current.transitions == PageTransitions.Instant

    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    // "Pick up where you left off" is decided when Home comes on screen, not live: pausing
    // from the mini player while reading Home should not push the whole page down. It goes
    // as soon as the session plays again.
    var resumeVisible by remember {
        mutableStateOf(playerConnection.mediaMetadata.value != null && !playerConnection.isPlaying.value)
    }
    LaunchedEffect(isPlaying, mediaMetadata == null) {
        if (isPlaying || mediaMetadata == null) resumeVisible = false
    }
    // The queue is restored a moment after launch, often after Home has composed. A session
    // that appears already paused (rather than one the listener just paused) earns the card.
    val hasSession = mediaMetadata != null
    LaunchedEffect(hasSession) {
        if (hasSession && !playerConnection.isPlaying.value) resumeVisible = true
    }
    // The songs a tap on Home most likely starts: the mix's first song and the heads of the
    // two personal shelves. Their first seconds are cached (on Wi-Fi) so they start at once.
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
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setVisible(true)
                    resumeVisible = playerConnection.mediaMetadata.value != null &&
                        !playerConnection.isPlaying.value
                }
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
    val resumePosition = remember(mediaMetadata?.id, resumeVisible) {
        if (resumeVisible) runCatching { playerConnection.player.currentPosition }.getOrDefault(0L) else 0L
    }

    /** One song: a streamed one opens a radio that carries on from it; a file or download plays alone. */
    fun playOne(song: Song, title: String) {
        if (online && !song.song.isLocal) actions.open(song, radio = true) else actions.playSongs(title, listOf(song))
    }

    val surpriseTitle = stringResource(R.string.home_surprise)
    fun surprise(section: SurpriseSection) {
        val pick = viewModel.surprise(section) ?: return
        val song = pick.song
        if (online && !song.song.isLocal && pick.kind != SurpriseKind.OnDevice) {
            actions.open(song, radio = true)
        } else {
            val pool = section.pools.firstOrNull { it.kind == pick.kind }?.songs.orEmpty()
            actions.playSongs(surpriseTitle, listOf(song) + pool.filter { it.id != song.id }.shuffled().take(30))
        }
    }

    fun openPath(path: HomePath) = when (path) {
        HomePath.Charts -> navController.navigate("charts_screen")
        HomePath.NewReleases -> navController.navigate("new_release")
        HomePath.Moods -> navController.navigate("mood_and_genres")
        HomePath.Search -> navController.navigate(Screens.Search.route)
        HomePath.Device -> navController.navigate("local_songs")
        HomePath.Identify -> navController.navigate("recognition")
    }

    /** What the listener reached for, so Home can lead with it next time. */
    fun note(kind: HomeInterest.Kind) = viewModel.note(kind)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        state = pullState,
        indicator = { RefreshIndicator(pullState, isRefreshing) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LargeTitlePage(
            title = stringResource(R.string.liquid_home),
            state = listState,
            scrollToTopSignal = navController,
            titleAccessory = {
                AccountAvatar(
                    imageUrl = if (isLoggedIn) accountImageUrl else null,
                    onClick = { navController.navigate("settings") },
                )
            },
        ) {
            val current = feed
            if (current == null) {
                items(3, key = { "skeleton_$it" }, contentType = { "skeleton" }) { SkeletonShelf() }
                return@LargeTitlePage
            }

            val live = mediaMetadata
            if (resumeVisible && live != null) {
                item(key = "resume", contentType = "resume") {
                    HomeItem(reduceMotion) {
                        ResumeCard(
                            metadata = live,
                            queueTitle = queueTitle,
                            positionMs = resumePosition,
                            onResume = actions::togglePlayPause,
                        )
                    }
                }
            }

            current.sections.forEach { section ->
                item(key = section.key, contentType = section.contentType()) {
                    HomeItem(reduceMotion) {
                        HomeSectionContent(
                            section = section,
                            actions = actions,
                            activeId = live?.id,
                            isPlaying = isPlaying,
                            compactHero = resumeVisible && live != null,
                            reduceMotion = reduceMotion,
                            lastSurprise = lastSurprise,
                            onSurprise = ::surprise,
                            onPath = ::openPath,
                            onNote = ::note,
                            onPlaySong = { playOne(it, it.title) },
                        )
                    }
                }
            }

            item(key = "home_footer") { Spacer(Modifier.height(20.dp)) }
        }
    }
}

private fun HomeSection.contentType(): String = this::class.simpleName ?: "section"

/** Sections fade in and slide into place when the feed changes; still when motion is reduced. */
@Composable
private fun LazyItemScope.HomeItem(reduceMotion: Boolean, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
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
private fun HomeSectionContent(
    section: HomeSection,
    actions: LiquidActions,
    activeId: String?,
    isPlaying: Boolean,
    compactHero: Boolean,
    reduceMotion: Boolean,
    lastSurprise: com.shiny.music.home.SurprisePick?,
    onSurprise: (SurpriseSection) -> Unit,
    onPath: (HomePath) -> Unit,
    onNote: (HomeInterest.Kind) -> Unit,
    onPlaySong: (Song) -> Unit,
) {
    when (section) {
        is OfflineNoticeSection -> OfflineNotice(section)
        is HeroMixSection -> {
            val title = mixTitle(section)
            HeroMixCard(
                section = section,
                reduceMotion = reduceMotion,
                onPlay = { actions.playSongs(title, section.songs) },
                onShuffle = { actions.playSongs(title, section.songs, shuffle = true) },
                compact = compactHero,
            )
        }
        is StartHereSection -> {
            val chartTitle = section.chart?.title.orEmpty()
            val deviceTitle = stringResource(R.string.home_start_device_title)
            StartHereBlock(
                section = section,
                reduceMotion = reduceMotion,
                onPlayChart = { songs, shuffle ->
                    onNote(HomeInterest.Kind.Charts)
                    actions.playSongItems(chartTitle, songs, shuffle = shuffle)
                },
                onPlayDevice = { shuffle -> actions.playSongs(deviceTitle, section.deviceSongs, shuffle = shuffle) },
            )
        }
        is ContinueSection -> ContinueShelf(section, activeId, actions)
        is LatelySection -> LatelyGrid(section, actions)
        is RotationSection -> RotationList(section, activeId, isPlaying, actions)
        is AroundNowSection -> AroundNowCard(section, activeId, isPlaying, actions) { onNote(HomeInterest.Kind.Moods) }
        is MoodsSection -> MoodsBlock(section, actions) { onNote(HomeInterest.Kind.Moods) }
        is DiscoverSection -> DiscoverShelf(section, actions) { onNote(HomeInterest.Kind.Discovery) }
        is DeepDiveSection -> DeepDiveBlock(section, actions)
        is ChartsSection -> ChartsBlock(section, activeId, isPlaying, actions) { onNote(HomeInterest.Kind.Charts) }
        is RediscoverSection -> RediscoverShelf(section, actions)
        is SpotifySection -> SpotifyShelf(section, actions)
        is YouTubeSection -> YouTubeShelf(section, actions)
        is AlbumsInProgressSection -> AlbumsInProgressShelf(section, actions)
        is RecentlyAddedSection -> RecentlyAddedGrid(section, activeId, isPlaying, actions)
        is ReadyOfflineSection -> ReadyOfflineCard(section, actions)
        is InsightsSection -> InsightsBlock(section, actions, onPlaySong)
        is SurpriseSection -> SurpriseCard(lastSurprise, onSurprise = {
            onNote(HomeInterest.Kind.Discovery)
            onSurprise(section)
        })
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
            .size(38.dp)
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
    MediaTile(
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
    MediaTile(
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

/** Pull-to-refresh with the iOS spinner instead of Material's arrow disc. */
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
        // Composed only while visible: the spinner is an infinite animation, and an
        // invisible one still redraws the whole window sixty times a second.
        if (refreshing || state.distanceFraction > 0f) ActivityIndicator(size = 28.dp)
    }
}
