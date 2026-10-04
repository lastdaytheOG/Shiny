package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.utils.parseCookieString
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.HomeChartsCountryKey
import com.shiny.music.constants.HomeChartsGlobalKey
import com.shiny.music.constants.InnerTubeCookieKey
import com.shiny.music.constants.QuickPicks
import com.shiny.music.constants.QuickPicksKey
import com.shiny.music.constants.ShowSpeedDialKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.extensions.toEnum
import com.shiny.music.home.Daypart
import com.shiny.music.home.HomeCharts
import com.shiny.music.home.HomeContext
import com.shiny.music.home.HomeFeed
import com.shiny.music.home.HomeFeedBuilder
import com.shiny.music.home.HomeInterest
import com.shiny.music.home.HomeInterestStore
import com.shiny.music.home.HomeRemoteRepository
import com.shiny.music.spotifyimport.SpotifyMixesRepository
import com.shiny.music.constants.SpotifyHomeMixesKey
import com.shiny.music.constants.SpotifySpDcKey
import com.shiny.music.home.HomeSignals
import com.shiny.music.home.HomeSignalsLoader
import com.shiny.music.home.HomeSurprise
import com.shiny.music.home.SurprisePick
import com.shiny.music.home.SurpriseSection
import com.shiny.music.home.homeOnlineFlow
import com.shiny.music.home.homeWallNowMs
import com.shiny.music.utils.SyncUtils
import com.shiny.music.utils.dataStore
import com.shiny.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random

/**
 * Home's single source of truth: one [feed] assembled by [HomeFeedBuilder] from
 *
 * - **Room** — read by [HomeSignalsLoader] as aggregates, again only when a play, like,
 *   download, save or playlist actually changes something (`homeInvalidation`), the part of
 *   the day turns, or the listener pulls to refresh;
 * - **the network** — one cached block ([HomeRemoteRepository]) whose parts refresh on their
 *   own schedule, only while Home is on screen and the device is online;
 * - **the moment** — the part of the day, connectivity and the content filters.
 *
 * Nothing runs while nothing is watching: the feed pipeline is shared `WhileSubscribed`, so
 * the activity-scoped instance costs nothing while another tab is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    /** Shared with the New tab, which reads the same cached block (see `AppModule`). */
    private val remote: HomeRemoteRepository,
    /** The listener's own Spotify mixes, when a Spotify account is connected. */
    private val spotifyMixes: SpotifyMixesRepository,
) : ViewModel() {
    private companion object {
        /** Failsafe ceiling on a pull to refresh; a healthy one finishes far inside it. */
        const val REFRESH_CEILING_MS = 30_000L
        const val RELATED_TOP_UP_BELOW = 12
    }

    private data class Prefs(
        val hideExplicit: Boolean,
        val hideVideoSongs: Boolean,
        val showPinned: Boolean,
        val discoverFromLastSong: Boolean,
        val chartScopes: List<String>,
        val accountKey: String?,
        /** A cookie that actually signs someone in, not merely a saved one. */
        val youtubeSignedIn: Boolean,
    )

    private data class Moment(val epochDay: Long, val daypart: Daypart)

    private data class SignalsKey(
        val moment: Moment,
        val nonce: Int,
        val discoverFromLastSong: Boolean,
        val invalidation: String,
        val localNonce: Int,
    )

    private val loader = HomeSignalsLoader(database)

    /**
     * What the listener reaches for on Home. The listen history cannot see that someone
     * opens the charts every morning or lives in moods, and [HomeOrder] needs to know.
     */
    private val interest = HomeInterestStore(context, viewModelScope)

    val isRefreshing = MutableStateFlow(false)
    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    /** The last Surprise Me pick, shown on its card until the next one. */
    val lastSurprise = MutableStateFlow<SurprisePick?>(null)
    private val recentSurprises = ArrayDeque<String>()

    /** Set by the screen while Home is resumed; network refreshes only happen then. */
    private val visible = MutableStateFlow(false)
    private val refreshNonce = MutableStateFlow(0)
    private val localNonce = MutableStateFlow(0)
    private val lastSignals = MutableStateFlow<HomeSignals?>(null)
    private val relatedAttempted = mutableSetOf<String>()

    /** Read once: the network and SIM country do not change under a running Home. */
    private val detectedCountry by lazy { HomeCharts.detectCountry(context) }

    private val prefs = context.dataStore.data.map { p ->
        Prefs(
            hideExplicit = p[HideExplicitKey] ?: false,
            hideVideoSongs = p[HideVideoSongsKey] ?: false,
            showPinned = p[ShowSpeedDialKey] ?: true,
            discoverFromLastSong = p[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS) == QuickPicks.LAST_LISTEN,
            chartScopes = HomeCharts.scopes(p[HomeChartsCountryKey], p[HomeChartsGlobalKey] ?: true, detectedCountry),
            accountKey = p[InnerTubeCookieKey]?.takeIf { it.isNotEmpty() }?.hashCode()?.toString(),
            youtubeSignedIn = p[InnerTubeCookieKey]?.let { "SAPISID" in parseCookieString(it) } == true,
        )
    }.distinctUntilChanged()

    val online: StateFlow<Boolean> = context.homeOnlineFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The part of the day, re-read every minute while Home is watched. */
    private val moment = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(Moment(now.toLocalDate().toEpochDay(), Daypart.of(now.hour)))
            delay(60_000)
        }
    }.distinctUntilChanged().shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val signals = combine(
        moment,
        refreshNonce,
        prefs.map { it.discoverFromLastSong }.distinctUntilChanged(),
        database.homeInvalidation().distinctUntilChanged(),
        localNonce,
    ) { m, nonce, fromLast, invalidation, local -> SignalsKey(m, nonce, fromLast, invalidation, local) }
        .distinctUntilChanged()
        .mapLatest { key ->
            try {
                loader.load(contextFor(key.moment, key.nonce, online = true, prefs = null, fromLast = key.discoverFromLastSong))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep what Home already shows rather than blanking it over one failed read.
                reportException(e)
                lastSignals.value ?: HomeSignals.Empty
            }
        }
        .onEach { lastSignals.value = it }

    /**
     * The moment, the refresh counter and what the listener reaches for, as one upstream.
     *
     * Noting an interest moves a saturating weight by a fraction of a percent, and
     * rebuilding the whole feed for that would be pure waste — so this only passes a change
     * on once it is large enough to reorder anything (5% of a lean).
     */
    private val occasion = combine(
        moment,
        refreshNonce,
        interest.state.distinctUntilChangedBy { i -> HomeInterest.Kind.entries.map { (i.lean(it) * 20).toInt() } },
    ) { m, nonce, interest -> Triple(m, nonce, interest) }

    /** Off when the setting is off or Spotify is logged out, without waiting for a refresh to clear the mixes. */
    private val spotifyShown = context.dataStore.data
        .map { p -> (p[SpotifyHomeMixesKey] ?: true) && !p[SpotifySpDcKey].isNullOrBlank() }
        .distinctUntilChanged()

    private val network = combine(remote.state, spotifyMixes.state, spotifyShown) { remote, spotify, shown ->
        remote to (if (shown) spotify?.mixes.orEmpty() else emptyList())
    }

    val feed: StateFlow<HomeFeed?> = combine(
        signals,
        network,
        online,
        prefs,
        occasion,
    ) { signals, (remote, spotify), online, prefs, (m, nonce, interest) ->
        HomeFeedBuilder.build(signals, remote, contextFor(m, nonce, online, prefs, prefs.discoverFromLastSong), interest, spotify)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Records that the listener reached for one kind of content, so Home can lead with it
     * next time. Cheap and debounced — safe to call straight from a tap handler.
     */
    fun note(kind: HomeInterest.Kind) = interest.note(kind)

    private fun contextFor(m: Moment, nonce: Int, online: Boolean, prefs: Prefs?, fromLast: Boolean) = HomeContext(
        nowMs = homeWallNowMs(),
        daypart = m.daypart,
        online = online,
        // One seed per daypart per day; each pull to refresh deals a new one.
        seed = m.epochDay * 4 + m.daypart.ordinal + nonce * 7_919L,
        hideExplicit = prefs?.hideExplicit ?: false,
        hideVideoSongs = prefs?.hideVideoSongs ?: false,
        showPinned = prefs?.showPinned ?: true,
        discoverFromLastSong = fromLast,
        chartScopes = prefs?.chartScopes,
        youtubeSignedIn = prefs?.youtubeSignedIn ?: false,
    )

    fun setVisible(isVisible: Boolean) {
        visible.value = isVisible
    }

    /**
     * Brings the network parts up to date. Without [force] every part is checked against its
     * own age and nothing is fetched that is still fresh. Also tops up the related-song graph
     * for favourites it knows nothing about, so Discover has something to offer listeners
     * whose favourites were mostly played offline.
     */
    private suspend fun refreshRemote(force: Boolean) {
        if (!online.value) return
        // Matching a mix's songs takes a while and must not hold the pull-to-refresh spinner;
        // the repository ignores the call while a refresh of its own is still running.
        viewModelScope.launch { spotifyMixes.refresh(force) }
        val p = prefs.first()
        val signals = lastSignals.value
        try {
            // Artists known only from device files get their YouTube channel first, so the
            // digests below can already cover them.
            signals?.let { s ->
                val names = HomeFeedBuilder.unresolvedSavedArtists(s, remote.state.value?.artistIds.orEmpty(), limit = 5)
                if (names.isNotEmpty()) remote.resolveArtists(names)
            }
            val artistIds = remote.state.value?.artistIds.orEmpty()
            val topArtists = signals?.let { HomeFeedBuilder.topArtists(it, 6, artistIds).map { a -> a.artistId } }.orEmpty()
            remote.refresh(p.chartScopes, p.accountKey, topArtists, force)
            if (signals != null && signals.discovery.size < RELATED_TOP_UP_BELOW) {
                // An empty Discover is a listener Shiny has only just met: fill it faster.
                val limit = if (signals.discovery.isEmpty()) 6 else 3
                val seeds = loader.seedsWithoutRelated(signals, limit = limit).filter { relatedAttempted.add(it) }
                if (seeds.isNotEmpty() && remote.fetchRelated(seeds) > 0) localNonce.value++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportException(e)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            try {
                isRefreshing.value = true
                refreshNonce.value++
                withTimeoutOrNull(REFRESH_CEILING_MS) { refreshRemote(force = true) }
            } finally {
                isRefreshing.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) { syncUtils.tryAutoSync() }
    }

    /** Draws a Surprise Me song; the screen plays it. */
    fun surprise(section: SurpriseSection): SurprisePick? {
        val pick = HomeSurprise.pick(
            section = section,
            stats = lastSignals.value?.songStats.orEmpty(),
            recent = recentSurprises,
            nowMs = homeWallNowMs(),
            random = Random(System.nanoTime()),
        ) ?: return null
        recentSurprises.addLast(pick.song.id)
        while (recentSurprises.size > HomeSurprise.RECENT_MEMORY) recentSurprises.removeFirst()
        lastSurprise.value = pick
        return pick
    }

    init {
        viewModelScope.launch { remote.restore() }
        viewModelScope.launch { spotifyMixes.restore() }
        // The screen subscribes to the feed only after the first frame, about a second after
        // this ViewModel exists, and the build (a ~50 ms Room read) used to wait for it. Built
        // now, Home's first composition already has the feed: no skeleton, no second pass.
        viewModelScope.launch { feed.first { it != null } }
        viewModelScope.launch { interest.restore() }

        // Stale network parts are refreshed when Home comes on screen, when the network comes
        // back, and when the listener's top artists or chosen charts change — each TTL-guarded.
        viewModelScope.launch {
            combine(
                visible,
                online,
                lastSignals.map { s -> s?.let { HomeFeedBuilder.topArtists(it, 6, remote.state.value?.artistIds.orEmpty()).map { a -> a.artistId } } }.distinctUntilChanged(),
                prefs.map { it.chartScopes to it.accountKey }.distinctUntilChanged(),
            ) { isVisible, isOnline, _, _ -> isVisible && isOnline }
                .filter { it }
                .collect { refreshRemote(force = false) }
        }

        viewModelScope.launch(Dispatchers.IO) { syncUtils.tryAutoSync() }

        // The signed-in account's name and avatar, for the greeting and the avatar button.
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { runCatching { it[InnerTubeCookieKey] }.getOrNull() }
                .distinctUntilChanged()
                .collect { cookie ->
                    if (!cookie.isNullOrEmpty()) {
                        YouTube.cookie = cookie
                        YouTube.accountInfo().onSuccess { info ->
                            accountName.value = info.name
                            accountImageUrl.value = info.thumbnailUrl
                        }.onFailure(::reportException)
                    } else {
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                    }
                }
        }
    }
}
