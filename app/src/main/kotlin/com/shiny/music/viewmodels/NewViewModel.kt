package com.shiny.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiny.music.constants.HideExplicitKey
import com.shiny.music.constants.HideVideoSongsKey
import com.shiny.music.constants.HomeChartsCountryKey
import com.shiny.music.constants.HomeChartsGlobalKey
import com.shiny.music.constants.InnerTubeCookieKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.home.Daypart
import com.shiny.music.home.HomeCharts
import com.shiny.music.home.HomeContext
import com.shiny.music.home.HomeFeedBuilder
import com.shiny.music.home.HomeRemoteRepository
import com.shiny.music.home.HomeSignals
import com.shiny.music.home.HomeSignalsLoader
import com.shiny.music.home.NewFeed
import com.shiny.music.home.NewFeedBuilder
import com.shiny.music.home.homeOnlineFlow
import com.shiny.music.home.homeWallNowMs
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

/**
 * New's single source of truth, built the same way Home's is and from the same two sources:
 * the listen history read as aggregates by [HomeSignalsLoader], and the one disk-cached
 * network block held by [HomeRemoteRepository] — a `@Singleton`, so New and Home share the
 * cache, the TTLs and the chart country rather than each keeping their own.
 *
 * Nothing runs while nothing is watching. [feed] is `WhileSubscribed`, so New costs nothing
 * while any other tab is open, and at most one of Home's and New's pipelines is ever alive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    private val remote: HomeRemoteRepository,
) : ViewModel() {
    private companion object {
        /** Failsafe ceiling on a pull to refresh; a healthy one finishes far inside it. */
        const val REFRESH_CEILING_MS = 30_000L
    }

    private data class Prefs(
        val hideExplicit: Boolean,
        val hideVideoSongs: Boolean,
        val chartScopes: List<String>,
        val accountKey: String?,
    )

    private data class Moment(val epochDay: Long, val daypart: Daypart)

    private val loader = HomeSignalsLoader(database)

    val isRefreshing = MutableStateFlow(false)

    /** Set by the screen while New is resumed; network refreshes only happen then. */
    private val visible = MutableStateFlow(false)
    private val refreshNonce = MutableStateFlow(0)
    private val lastSignals = MutableStateFlow<HomeSignals?>(null)

    /** Read once: the network and SIM country do not change under a running app. */
    private val detectedCountry by lazy { HomeCharts.detectCountry(context) }

    private val prefs = context.dataStore.data.map { p ->
        Prefs(
            hideExplicit = p[HideExplicitKey] ?: false,
            hideVideoSongs = p[HideVideoSongsKey] ?: false,
            chartScopes = HomeCharts.scopes(p[HomeChartsCountryKey], p[HomeChartsGlobalKey] ?: true, detectedCountry),
            accountKey = p[InnerTubeCookieKey]?.takeIf { it.isNotEmpty() }?.hashCode()?.toString(),
        )
    }.distinctUntilChanged()

    val online: StateFlow<Boolean> = context.homeOnlineFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val moment = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(Moment(now.toLocalDate().toEpochDay(), Daypart.of(now.hour)))
            delay(60_000)
        }
    }.distinctUntilChanged().shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    /**
     * The history, re-read when the day turns or the listener pulls to refresh.
     *
     * Deliberately *not* keyed on every play the way Home is. What is new this week does not
     * change because one more song finished, and a release page that reshuffled under the
     * listener's thumb mid-scroll would be worse, not better.
     */
    private val signals = combine(moment, refreshNonce) { m, nonce -> m to nonce }
        .distinctUntilChanged()
        .mapLatest { (m, nonce) ->
            try {
                loader.load(contextFor(m, nonce, online = true, prefs = null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep what New already shows rather than blanking it over one failed read.
                reportException(e)
                lastSignals.value ?: HomeSignals.Empty
            }
        }
        .onEach { lastSignals.value = it }

    val feed: StateFlow<NewFeed?> = combine(
        signals,
        remote.state,
        online,
        prefs,
        combine(moment, refreshNonce) { m, n -> m to n },
    ) { signals, remote, online, prefs, (m, nonce) ->
        NewFeedBuilder.build(signals, remote, contextFor(m, nonce, online, prefs))
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun contextFor(m: Moment, nonce: Int, online: Boolean, prefs: Prefs?) = HomeContext(
        nowMs = homeWallNowMs(),
        daypart = m.daypart,
        online = online,
        // One seed per daypart per day; each pull to refresh deals a new one.
        seed = m.epochDay * 4 + m.daypart.ordinal + nonce * 7_919L,
        hideExplicit = prefs?.hideExplicit ?: false,
        hideVideoSongs = prefs?.hideVideoSongs ?: false,
        chartScopes = prefs?.chartScopes,
    )

    fun setVisible(isVisible: Boolean) {
        visible.value = isVisible
    }

    /** Brings the stale parts of the shared block up to date; each is TTL-guarded. */
    private suspend fun refreshRemote(force: Boolean) {
        if (!online.value) return
        val p = prefs.first()
        val topArtists = lastSignals.value
            ?.let { HomeFeedBuilder.topArtists(it, 6).map { a -> a.artistId } }
            .orEmpty()
        try {
            remote.refresh(p.chartScopes, p.accountKey, topArtists, force)
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
    }

    init {
        viewModelScope.launch { remote.restore() }

        viewModelScope.launch {
            combine(
                visible,
                online,
                lastSignals.map { s -> s?.let { HomeFeedBuilder.topArtists(it, 6).map { a -> a.artistId } } }
                    .distinctUntilChanged(),
                prefs.map { it.chartScopes to it.accountKey }.distinctUntilChanged(),
            ) { isVisible, isOnline, _, _ -> isVisible && isOnline }
                .filter { it }
                .collect { refreshRemote(force = false) }
        }
    }
}
