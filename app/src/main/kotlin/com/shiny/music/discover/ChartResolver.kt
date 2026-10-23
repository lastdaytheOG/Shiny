package com.shiny.music.discover

import androidx.compose.runtime.Immutable
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.music.innertube.models.YTItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

sealed interface Resolution<out T> {
    data class Found<T>(val item: T, val alternative: Boolean = false) : Resolution<T>
    data class Ambiguous(val query: String, val choices: List<ChartChoice>) : Resolution<Nothing>
    data class NotFound(val query: String) : Resolution<Nothing>
    data class Failed(val query: String) : Resolution<Nothing>
}

@Immutable
data class ChartChoice(
    val item: YTItem,
    val title: String,
    val artists: String,
    val thumbnail: String?,
    val isAlbum: Boolean,
)

interface ChartCatalog {
    suspend fun songs(query: String): List<SongItem>?
    suspend fun videos(query: String): List<SongItem>?
    suspend fun albums(query: String): List<AlbumItem>?
    suspend fun artists(query: String): List<ArtistItem>?
}

object YouTubeChartCatalog : ChartCatalog {
    override suspend fun songs(query: String) =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()

    override suspend fun videos(query: String) =
        YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items?.filterIsInstance<SongItem>()

    override suspend fun albums(query: String) =
        YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()?.items?.filterIsInstance<AlbumItem>()

    override suspend fun artists(query: String) =
        YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items?.filterIsInstance<ArtistItem>()
}

interface ArtistStore {
    fun read(): Map<String, StoredArtist>
    fun write(artists: Map<String, StoredArtist>)
}

@Serializable
data class StoredArtist(val id: String, val title: String, val thumbnail: String?, val at: Long)

class FileArtistStore(private val file: File) : ArtistStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), StoredArtist.serializer())

    override fun read(): Map<String, StoredArtist> =
        runCatching { if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyMap() }
            .getOrDefault(emptyMap())

    override fun write(artists: Map<String, StoredArtist>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, artists))
            check(tmp.renameTo(file)) { "rename failed" }
        }.onFailure { Timber.tag("Discover").w(it, "artist cache not saved") }
    }
}

class ChartResolver(
    private val catalog: ChartCatalog,
    private val artistStore: ArtistStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val MAX_REMEMBERED = 400
        const val ARTIST_TTL_MS = 14L * 24 * 60 * 60 * 1000
        const val MAX_STORED_ARTISTS = 300
        const val MAX_CHOICES = 3

        fun candidate(song: SongItem) = MatchCandidate(
            title = song.title,
            artists = song.artists.map { it.name },
            explicit = song.explicit,
            kind = when (song.musicVideoType) {
                null, MUSIC_VIDEO_TYPE_ATV -> CandidateKind.Song
                MUSIC_VIDEO_TYPE_OMV -> CandidateKind.OfficialVideo
                else -> CandidateKind.Other
            },
        )

        fun candidate(album: AlbumItem) = MatchCandidate(
            title = album.title,
            artists = album.artists.orEmpty().map { it.name },
            explicit = album.explicit,
            kind = CandidateKind.Album,
            year = album.year,
        )
    }

    private enum class Catalog { Songs, Videos }

    private data class Stage(val catalog: Catalog, val query: String)

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val lock = Mutex()
    private val searches = Semaphore(4)
    private val remembered = object : LinkedHashMap<String, Resolution<Any>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Resolution<Any>>) = size > MAX_REMEMBERED
    }
    private val inFlight = HashMap<String, CompletableDeferred<Resolution<Any>>>()

    private val _artists = MutableStateFlow<Map<String, ArtistItem>>(emptyMap())
    val artists: StateFlow<Map<String, ArtistItem>> = _artists.asStateFlow()

    @Volatile
    private var storeLoaded = artistStore == null

    fun songKey(entry: ChartEntry) = "song:${entry.id}"
    fun albumKey(entry: ChartEntry) = "album:${entry.id}"

    fun knownSong(entry: ChartEntry): SongItem? =
        ((synchronized(remembered) { remembered[songKey(entry)] }) as? Resolution.Found<*>)?.item as? SongItem

    private fun stages(entry: ChartEntry, kind: ChartKind): List<Stage> {
        val lead = if (ChartText.isVarious(entry.artist)) null else ChartText.leadArtist(entry.artist, entry.artistUrl)
        val core = ChartText.displayCoreTitle(entry.title)
        val byLead = listOfNotNull(core, lead).joinToString(" ")
        val full = if (lead == null) entry.title else "${entry.title} ${entry.artist}"
        val second = Stage(Catalog.Songs, if (full != byLead) full else core)
        val video = Stage(Catalog.Videos, listOfNotNull(entry.title, lead).joinToString(" "))
        val ordered = if (kind == ChartKind.Videos) {
            listOf(Stage(Catalog.Songs, byLead), video, second)
        } else {
            listOf(Stage(Catalog.Songs, byLead), second, video)
        }
        return ordered.distinct()
    }

    suspend fun song(entry: ChartEntry, kind: ChartKind = ChartKind.Songs): Resolution<SongItem> = shared(songKey(entry)) {
        val target = MatchTarget.song(entry)
        val stages = stages(entry, kind)
        val pool = LinkedHashMap<String, SongItem>()
        var alternative: SongItem? = null
        var failed = false
        for (stage in stages) {
            val results = searches.withPermit {
                when (stage.catalog) {
                    Catalog.Songs -> catalog.songs(stage.query)
                    Catalog.Videos -> catalog.videos(stage.query)
                }
            }
            if (results == null) {
                failed = true
                continue
            }
            results.forEach { pool.putIfAbsent(it.id, it) }
            val decision = ChartMatcher.decide(target, results.map(::candidate)) ?: continue
            when (decision.fit) {
                Fit.Exact -> return@shared Resolution.Found(results[decision.index])
                Fit.Alternative -> if (alternative == null) alternative = results[decision.index]
                else -> Unit
            }
        }
        alternative?.let { return@shared Resolution.Found(it, alternative = true) }
        val query = stages.first().query
        val choices = songChoices(target, pool.values.toList())
        when {
            choices.isNotEmpty() -> Resolution.Ambiguous(query, choices)
            failed -> Resolution.Failed(query)
            else -> Resolution.NotFound(query)
        }
    }

    private fun songChoices(target: MatchTarget, pool: List<SongItem>): List<ChartChoice> {
        val wantedCore = ChartText.coreTitle(target.title)
        val albums = pool.mapNotNull { song ->
            val album = song.album ?: return@mapNotNull null
            if (ChartText.coreTitle(album.name) != wantedCore) return@mapNotNull null
            val artist = ChartMatcher.artistScore(target.credit, target.artistUrl, song.artists.map { it.name }) ?: 0.0
            if (artist < 0.7) return@mapNotNull null
            val item = AlbumItem(
                browseId = album.id,
                playlistId = "",
                title = album.name,
                artists = song.artists,
                thumbnail = song.thumbnail,
                explicit = song.explicit,
            )
            ChartChoice(item, album.name, song.artists.joinToString { it.name }, song.thumbnail, isAlbum = true)
        }
        val candidates = pool.map(::candidate)
        val songs = ChartMatcher.partials(target, candidates).map { pool[it] }.map { song ->
            ChartChoice(song, song.title, song.artists.joinToString { it.name }, song.thumbnail, isAlbum = false)
        }
        return (albums + songs).distinctBy { it.item.id }.distinctBy { "${it.isAlbum}:${ChartText.normalize(it.title)}:${ChartText.normalize(it.artists)}" }.take(MAX_CHOICES)
    }

    suspend fun album(entry: ChartEntry): Resolution<AlbumItem> = shared(albumKey(entry)) {
        val target = MatchTarget.album(entry)
        val various = ChartText.isVarious(entry.artist)
        val core = ChartText.displayCoreTitle(entry.title)
        val queries = listOf(
            if (various) entry.title else "$core ${ChartText.leadArtist(entry.artist, entry.artistUrl)}",
            if (various) core else entry.title,
        ).distinct()
        val pool = LinkedHashMap<String, AlbumItem>()
        var alternative: AlbumItem? = null
        var failed = false
        for (query in queries) {
            val results = searches.withPermit { catalog.albums(query) }
            if (results == null) {
                failed = true
                continue
            }
            results.forEach { pool.putIfAbsent(it.id, it) }
            val decision = ChartMatcher.decide(target, results.map(::candidate)) ?: continue
            when (decision.fit) {
                Fit.Exact -> return@shared Resolution.Found(results[decision.index])
                Fit.Alternative -> if (alternative == null) alternative = results[decision.index]
                else -> Unit
            }
        }
        alternative?.let { return@shared Resolution.Found(it, alternative = true) }
        val all = pool.values.toList()
        val choices = ChartMatcher.partials(target, all.map(::candidate)).map { all[it] }
            .map { ChartChoice(it, it.title, it.artists.orEmpty().joinToString { a -> a.name }, it.thumbnail, isAlbum = true) }
            .take(MAX_CHOICES)
        when {
            choices.isNotEmpty() -> Resolution.Ambiguous(queries.first(), choices)
            failed -> Resolution.Failed(queries.first())
            else -> Resolution.NotFound(queries.first())
        }
    }

    suspend fun artist(artist: ChartArtist): Resolution<ArtistItem> {
        loadStore()
        _artists.value[artist.key]?.let { return Resolution.Found(it) }
        return shared("artist:${artist.key}") {
            val results = searches.withPermit { catalog.artists(artist.name) }
                ?: return@shared Resolution.Failed(artist.name)
            val match = results.firstOrNull { ChartMatcher.sameArtist(artist.name, it.title) }
                ?: return@shared Resolution.NotFound(artist.name)
            _artists.update { it + (artist.key to match) }
            persistArtists()
            Resolution.Found(match)
        }
    }

    private val storedAt = HashMap<String, Long>()

    private suspend fun loadStore() {
        if (storeLoaded) return
        val store = artistStore ?: return
        val now = clock()
        val restored = withContext(io) { store.read() }.filterValues { now - it.at in 0 until ARTIST_TTL_MS }
        synchronized(storedAt) { restored.forEach { (key, s) -> storedAt.putIfAbsent(key, s.at) } }
        _artists.update { current ->
            restored.mapValues { (_, s) ->
                ArtistItem(id = s.id, title = s.title, thumbnail = s.thumbnail, shuffleEndpoint = null, radioEndpoint = null)
            } + current
        }
        storeLoaded = true
    }

    private fun persistArtists() {
        val store = artistStore ?: return
        val now = clock()
        val snapshot = synchronized(storedAt) {
            _artists.value.entries
                .map { (key, item) -> StoredArtist(item.id, item.title, item.thumbnail, storedAt.getOrPut(key) { now }) to key }
                .sortedByDescending { it.first.at }
                .take(MAX_STORED_ARTISTS)
                .associate { (stored, key) -> key to stored }
        }
        scope.launch { store.write(snapshot) }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> shared(key: String, block: suspend () -> Resolution<T>): Resolution<T> {
        synchronized(remembered) { remembered[key] }?.let { return it as Resolution<T> }
        val (deferred, owner) = lock.withLock {
            synchronized(remembered) { remembered[key] }?.let { return it as Resolution<T> }
            inFlight[key]?.let { it to false } ?: CompletableDeferred<Resolution<Any>>().also { inFlight[key] = it } to true
        }
        if (owner) {
            scope.launch {
                val result: Resolution<Any> = try {
                    block()
                } catch (e: CancellationException) {
                    Resolution.Failed(key)
                } catch (e: Exception) {
                    Timber.tag("Discover").w(e, "resolve failed for %s", key)
                    Resolution.Failed(key)
                }
                if (result !is Resolution.Failed) synchronized(remembered) { remembered[key] = result }
                lock.withLock { if (inFlight[key] === deferred) inFlight.remove(key) }
                deferred.complete(result)
            }
        }
        return deferred.await() as Resolution<T>
    }
}
