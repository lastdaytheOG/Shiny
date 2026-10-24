package com.shiny.music.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.shiny.music.constants.HomeChartsCountryKey
import com.shiny.music.discover.AppleChart
import com.shiny.music.discover.AppleChartQueue
import com.shiny.music.discover.AppleChartsRepository
import com.shiny.music.discover.ChartArtist
import com.shiny.music.discover.ChartArtistRanking
import com.shiny.music.discover.ChartChoice
import com.shiny.music.discover.ChartEntry
import com.shiny.music.discover.ChartError
import com.shiny.music.discover.ChartKind
import com.shiny.music.discover.ChartRegion
import com.shiny.music.discover.ChartRegions
import com.shiny.music.discover.ChartResolver
import com.shiny.music.discover.Resolution
import com.shiny.music.playback.queues.Queue
import com.shiny.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ChartSlot(
    val chart: AppleChart? = null,
    val loading: Boolean = false,
    val error: ChartError? = null,
) {
    val entries: List<ChartEntry> get() = chart?.entries.orEmpty()
}

@Immutable
data class DiscoverCharts(
    val region: ChartRegion,
    val songs: ChartSlot,
    val albums: ChartSlot,
    val videos: ChartSlot,
    val artists: List<ChartArtist>,
) {
    private val slots get() = listOf(songs, albums, videos)
    val loading: Boolean get() = slots.any { it.loading }
    val isEmpty: Boolean get() = slots.all { it.entries.isEmpty() }
    val error: ChartError? get() = slots.firstNotNullOfOrNull { it.error }
}

sealed interface DiscoverEvent {
    data class Play(val queue: Queue) : DiscoverEvent
    data class Open(val item: YTItem) : DiscoverEvent
    data class SongMenu(val song: SongItem) : DiscoverEvent
}

@Immutable
data class DiscoverNotice(
    val id: Long,
    val kind: Kind,
    val title: String,
    val query: String,
    val choices: List<ChartChoice> = emptyList(),
) {
    enum class Kind { NoMatch, Choices, Unreachable, Alternative }
}

fun noticeFor(resolution: Resolution<*>, title: String, id: Long): DiscoverNotice? = when (resolution) {
    is Resolution.Found -> if (resolution.alternative) DiscoverNotice(id, DiscoverNotice.Kind.Alternative, title, title) else null
    is Resolution.Ambiguous -> DiscoverNotice(id, DiscoverNotice.Kind.Choices, title, resolution.query, resolution.choices)
    is Resolution.NotFound -> DiscoverNotice(id, DiscoverNotice.Kind.NoMatch, title, resolution.query)
    is Resolution.Failed -> DiscoverNotice(id, DiscoverNotice.Kind.Unreachable, title, resolution.query)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppleChartsRepository,
    private val resolver: ChartResolver,
) : ViewModel() {
    private val detected: String? by lazy { ChartRegions.detect(ChartRegions.candidates(context)) }

    val region: StateFlow<ChartRegion?> = context.dataStore.data
        .map { ChartRegions.resolve(it[HomeChartsCountryKey], detected) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val visible = MutableStateFlow(false)
    private val loadedRegion = MutableStateFlow<ChartRegion?>(null)
    private val slots = MutableStateFlow(ChartKind.entries.associateWith { ChartSlot() })

    val isRefreshing = MutableStateFlow(false)

    val scroll = ListScroll()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _notice = MutableStateFlow<DiscoverNotice?>(null)
    val notice: StateFlow<DiscoverNotice?> = _notice.asStateFlow()

    private val _events = Channel<DiscoverEvent>(Channel.BUFFERED)
    val events: Flow<DiscoverEvent> = _events.receiveAsFlow()

    val artistItems: StateFlow<Map<String, ArtistItem>> = resolver.artists

    private var rankedFrom: AppleChart? = null
    private var ranked: List<ChartArtist> = emptyList()

    val charts: StateFlow<DiscoverCharts?> = combine(loadedRegion.filterNotNull(), slots) { region, s ->
        val songs = s.getValue(ChartKind.Songs)
        if (songs.chart !== rankedFrom) {
            rankedFrom = songs.chart
            ranked = ChartArtistRanking.rank(songs.entries)
        }
        DiscoverCharts(region, songs, s.getValue(ChartKind.Albums), s.getValue(ChartKind.Videos), ranked)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var resolveJob: Job? = null
    private val requestedArtists = HashSet<String>()
    private var noticeId = 0L

    init {
        viewModelScope.launch {
            combine(region.filterNotNull(), visible) { r, v -> r to v }
                .distinctUntilChanged()
                .collectLatest { (r, isVisible) -> load(r, force = false, network = isVisible) }
        }
    }

    private val viewers = mutableSetOf<Any>()

    fun setVisible(viewer: Any, isVisible: Boolean) {
        if (isVisible) viewers += viewer else viewers -= viewer
        visible.value = viewers.isNotEmpty()
    }

    fun refresh() {
        val r = region.value ?: return
        if (isRefreshing.value) return
        requestedArtists.clear()
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                load(r, force = true, network = true)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private suspend fun load(r: ChartRegion, force: Boolean, network: Boolean) = coroutineScope {
        if (loadedRegion.value != r) {
            val held = ChartKind.entries.associateWith { kind ->
                ChartSlot(chart = repository.cached(r.storefront, kind), loading = network)
            }
            slots.value = held
            loadedRegion.value = r
        }
        if (!network) return@coroutineScope
        ChartKind.entries.forEach { kind ->
            launch {
                val held = repository.cached(r.storefront, kind)
                val needsNetwork = force || held == null || !repository.isFresh(held)
                slots.update { it + (kind to ChartSlot(held, loading = needsNetwork)) }
                if (!needsNetwork) return@launch
                val result = repository.get(r.storefront, kind, force)
                if (loadedRegion.value == r) {
                    slots.update { it + (kind to ChartSlot(result.chart, loading = false, error = result.error)) }
                }
            }
        }
    }

    fun playingId(entry: ChartEntry): String? = resolver.knownSong(entry)?.id

    fun songKey(entry: ChartEntry) = resolver.songKey(entry)
    fun albumKey(entry: ChartEntry) = resolver.albumKey(entry)
    fun artistKey(artist: ChartArtist) = "artist:${artist.key}"

    fun playSong(entry: ChartEntry, list: List<ChartEntry>, title: String?, kind: ChartKind = ChartKind.Songs) {
        resolve(resolver.songKey(entry), entry.title) {
            val r = resolver.song(entry, kind)
            if (r is Resolution.Found) {
                val index = list.indexOfFirst { it.id == entry.id }
                val rest = if (index >= 0) list.drop(index + 1) else emptyList()
                _events.send(DiscoverEvent.Play(AppleChartQueue(title, r.item, rest) { resolver.song(it, kind) }))
            }
            r
        }
    }

    fun playAll(list: List<ChartEntry>, title: String?, shuffle: Boolean) {
        if (list.isEmpty()) return
        val order = if (shuffle) list.shuffled() else list
        resolve("play_all", title.orEmpty()) {
            var last: Resolution<SongItem> = Resolution.NotFound(order.first().title)
            var unreachable: Resolution.Failed? = null
            for ((index, entry) in order.withIndex().take(6)) {
                when (val r = resolver.song(entry)) {
                    is Resolution.Found -> {
                        _events.send(DiscoverEvent.Play(AppleChartQueue(title, r.item, order.drop(index + 1), resolver::song)))
                        return@resolve null
                    }
                    is Resolution.Failed -> unreachable = r
                    is Resolution.Ambiguous -> last = Resolution.NotFound(r.query)
                    is Resolution.NotFound -> last = r
                }
            }
            unreachable ?: last
        }
    }

    fun songMenu(entry: ChartEntry, kind: ChartKind = ChartKind.Songs) {
        resolve(resolver.songKey(entry), entry.title) {
            val r = resolver.song(entry, kind)
            if (r is Resolution.Found) _events.send(DiscoverEvent.SongMenu(r.item))
            if (r is Resolution.Found) null else r
        }
    }

    fun openAlbum(entry: ChartEntry) {
        resolve(resolver.albumKey(entry), entry.title) {
            val r = resolver.album(entry)
            if (r is Resolution.Found) _events.send(DiscoverEvent.Open(r.item))
            if (r is Resolution.Found) null else r
        }
    }

    fun openArtist(artist: ChartArtist) {
        resolve(artistKey(artist), artist.name) {
            val r = resolver.artist(artist)
            if (r is Resolution.Found) _events.send(DiscoverEvent.Open(r.item))
            if (r is Resolution.Found) null else r
        }
    }

    fun choose(noticeId: Long, choice: ChartChoice) {
        dismissNotice(noticeId)
        viewModelScope.launch { _events.send(DiscoverEvent.Open(choice.item)) }
    }

    fun ensureArtists(artists: List<ChartArtist>) {
        val wanted = artists.filter { it.key !in artistItems.value && requestedArtists.add(it.key) }
        if (wanted.isEmpty()) return
        viewModelScope.launch {
            wanted.forEach { artist ->
                launch {
                    if (resolver.artist(artist) is Resolution.Failed) requestedArtists.remove(artist.key)
                }
            }
        }
    }

    fun dismissNotice(id: Long) {
        _notice.update { if (it?.id == id) null else it }
    }

    private fun resolve(key: String, title: String, block: suspend () -> Resolution<*>?) {
        if (_busy.value == key && resolveJob?.isActive == true) return
        resolveJob?.cancel()
        _busy.value = key
        _notice.value = null
        resolveJob = viewModelScope.launch {
            try {
                block()?.let { r -> _notice.value = noticeFor(r, title, ++noticeId) }
            } finally {
                if (_busy.value == key) _busy.value = null
            }
        }
    }
}
