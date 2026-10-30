package com.shiny.music.intelligence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.media3.common.MediaItem
import com.shiny.music.constants.IntelligenceGraphAtKey
import com.shiny.music.constants.PauseListenHistoryKey
import com.shiny.music.constants.PersonalisedRecommendationsKey
import com.shiny.music.constants.RecommendationStyleKey
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.ListenOutcome
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.MusicRelation
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.db.entities.RecommendationImpression
import com.shiny.music.db.entities.Song
import com.shiny.music.extensions.metadata
import com.shiny.music.home.Daypart
import com.shiny.music.home.HOME_DAY_MS
import com.shiny.music.home.HomeArtistDigest
import com.shiny.music.home.HomeRanking
import com.shiny.music.home.HomeSignals
import com.shiny.music.home.HomeTaste
import com.shiny.music.home.homeWallNowMs
import com.shiny.music.models.MediaMetadata
import com.shiny.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

data class ImpressionItem(
    val songId: String,
    val source: CandidateSource? = null,
    val reason: Reason? = null,
)

data class NewToYouPick(
    val song: Song,
    val recommendation: Recommendation,
)

data class UpNextPick(
    val item: MediaItem,
    val source: CandidateSource?,
)

@Singleton
class IntelligenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val dao get() = database.intelligenceDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshotLock = Mutex()
    private var cachedKey: String? = null
    private var cached: IntelligenceSnapshot? = null
    private var graphKey: String? = null
    private var graph: GraphBundle? = null
    private val shownRecently = ConcurrentHashMap<String, Long>()
    @Volatile private var lastPositive: PositiveListen? = null
    private var pruned = false
    private var materializing = false

    private class GraphBundle(val graph: MusicGraph, val badTransitions: Map<String, Double>)

    private data class PositiveListen(val sessionId: Long, val artistId: String)

    val style: Flow<RecommendationStyle> = context.dataStore.data
        .map { styleOf(it[RecommendationStyleKey]) }
        .distinctUntilChanged()

    val enabled: Flow<Boolean> = context.dataStore.data
        .map { it[PersonalisedRecommendationsKey] ?: true }
        .distinctUntilChanged()

    private fun styleOf(name: String?) =
        name?.let { runCatching { RecommendationStyle.valueOf(it) }.getOrNull() } ?: RecommendationStyle.Balanced

    suspend fun setStyle(style: RecommendationStyle) {
        context.dataStore.edit { it[RecommendationStyleKey] = style.name }
    }

    suspend fun snapshot(): IntelligenceSnapshot = withContext(Dispatchers.IO) {
        snapshotLock.withLock {
            val prefs = context.dataStore.data.first()
            if (prefs[PersonalisedRecommendationsKey] == false) return@withLock IntelligenceSnapshot.Neutral
            val style = styleOf(prefs[RecommendationStyleKey])
            val now = System.currentTimeMillis()
            val wallNow = homeWallNowMs()
            val daypart = Daypart.of(LocalDateTime.now().hour)
            val key = "${dao.snapshotKey()}|$style|$daypart|${now / HOME_DAY_MS}"
            cached?.takeIf { cachedKey == key }?.let { return@withLock it }

            val started = System.nanoTime()
            val built = build(style, now, wallNow, daypart)
            Timber.tag(TAG).d(
                "snapshot %d ms: %d artists, %d songs, %d graph nodes",
                (System.nanoTime() - started) / 1_000_000,
                built.taste.artists.size,
                built.taste.songs.size,
                built.graph.nodeCount,
            )
            cachedKey = key
            cached = built
            maintain(now)
            built
        }
    }

    private suspend fun build(style: RecommendationStyle, now: Long, wallNow: Long, daypart: Daypart): IntelligenceSnapshot {
        val weekFrom = wallNow - HomeRanking.WEEK_DAYS * HOME_DAY_MS
        val monthFrom = wallNow - HomeRanking.MONTH_DAYS * HOME_DAY_MS
        val daypartFrom = wallNow - HomeRanking.DAYPART_WINDOW_DAYS * HOME_DAY_MS
        val counts = database.homeLibraryCounts(hideExplicit = false, hideVideos = false)
        val songStats = if (counts.events > 0) database.homeSongStats(weekFrom, monthFrom, daypartFrom, daypart.hours) else emptyList()
        val artistStats = if (counts.events > 0) database.homeArtistStats(weekFrom, monthFrom, limit = 300) else emptyList()
        val daypartDays = if (counts.events > 0) database.homeDaypartDays(daypartFrom, daypart.hours) else 0
        val saved = database.homeSavedSongs(limit = SAVED_POOL)
        val savedArtists = database.homeSavedArtists(limit = 60)
        val bucketMs = TasteProfileBuilder.BUCKET_DAYS * HOME_DAY_MS

        val confidence = HomeTaste.confidence(
            HomeSignals.Empty.copy(
                counts = counts,
                songStats = songStats.associateBy { it.songId },
                artistStats = artistStats,
                daypartDays = daypartDays,
                savedSongs = saved.size,
            ),
        )

        val taste = TasteProfileBuilder.build(
            TasteInputs(
                confidence = confidence,
                songStats = songStats,
                artistStats = artistStats,
                savedSongs = saved,
                savedArtists = savedArtists,
                songOutcomes = dao.songOutcomeBuckets(now, bucketMs, TasteProfileBuilder.MAX_BUCKET),
                artistOutcomes = dao.artistOutcomeBuckets(now, bucketMs, TasteProfileBuilder.MAX_BUCKET),
                sourceStats = dao.sourceOutcomeStats(),
                discovery = dao.discoveryOutcomes(now - 30 * HOME_DAY_MS),
                feedback = dao.activeFeedback(now),
                daypart = if (counts.events > 0) dao.artistDaypartStats(daypartFrom, daypart.hours) else emptyList(),
                impressions = dao.impressionFatigue(now - 30 * HOME_DAY_MS),
                features = dao.songFeatures(),
                recentSearches = dao.recentSearches(limit = 8),
                nowMs = now,
                wallNowMs = wallNow,
            ),
        )
        val bundle = graphBundle(now)
        val seeds = RecommendationEngine.seedWeights(taste)
        val plan = StylePlan.of(style)
        return IntelligenceSnapshot(
            enabled = true,
            style = style,
            taste = taste,
            graph = bundle.graph,
            reach = bundle.graph.reach(seeds, plan.graphHops),
            embeddings = ArtistEmbeddings.Empty,
            tasteVector = null,
            badTransitions = bundle.badTransitions,
        )
    }

    private suspend fun graphBundle(now: Long): GraphBundle {
        val key = dao.relationKey()
        graph?.takeIf { graphKey == key }?.let { return it }
        val rows = dao.relationEdges(GRAPH_KINDS, GRAPH_EDGES)
        val names = dao.artistNames().associate { it.id to it.name }
        val bad = dao.relationEdges(listOf(SKIP_AFTER), BAD_TRANSITIONS)
            .associate { "${it.fromId}>${it.toId}" to (ln(1.0 + it.weight) / ln(5.0)).coerceIn(0.0, 1.0) }
        val bundle = GraphBundle(MusicGraph.build(rows, names), bad)
        graph = bundle
        graphKey = key
        return bundle
    }

    private fun maintain(now: Long) {
        if (pruned) return
        pruned = true
        database.query {
            intelligenceDao.pruneFeedback(now)
            intelligenceDao.pruneOutcomes(now - OUTCOME_RETENTION_DAYS * HOME_DAY_MS)
            intelligenceDao.pruneImpressions(now - IMPRESSION_RETENTION_DAYS * HOME_DAY_MS)
        }
    }

    fun refreshGraphFacts(digests: Collection<HomeArtistDigest>) {
        if (materializing) return
        materializing = true
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                if (digests.isNotEmpty()) {
                    val rows = digests.flatMap { digest ->
                        digest.related.map { related ->
                            MusicRelation(digest.artistId, related.id, RelationKind.SimilarArtist.name, 4f, 1, related.title, now)
                        }
                    }
                    if (rows.isNotEmpty()) dao.upsertRelations(rows)
                }
                val prefs = context.dataStore.data.first()
                if (now - (prefs[IntelligenceGraphAtKey] ?: 0L) < GRAPH_REFRESH_MS) return@launch
                val top = RecommendationEngine.seedWeights(snapshot().taste).keys.toList()
                val related = dao.relatedArtistEdges(RELATED_EDGES)
                val playlist = if (top.isEmpty()) emptyList() else dao.playlistArtistEdges(top, PLAYLIST_EDGES)
                val relations = (related + playlist).map { edge ->
                    MusicRelation(edge.fromId, edge.toId, edge.kind, edge.weight, edge.weight.toInt(), edge.toName, now)
                }
                database.withTransaction {
                    intelligenceDao.clearRelations(listOf(RelationKind.RelatedSong.name, RelationKind.Playlist.name))
                    if (relations.isNotEmpty()) intelligenceDao.upsertRelations(relations)
                }
                context.dataStore.edit { it[IntelligenceGraphAtKey] = now }
                Timber.tag(TAG).d("graph facts: %d related, %d playlist", related.size, playlist.size)
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "graph facts not refreshed")
            } finally {
                materializing = false
            }
        }
    }

    fun record(draft: ListenDraft) {
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.ofEpochMilli(draft.startedAt)).totalSeconds / 60
        database.query {
            val dao = intelligenceDao
            val track = draft.track
            val wallStart = draft.startedAt + zoneOffset * 60_000L
            val lastOutcome = dao.lastOutcomeAt(track.songId)
            val lastEvent = dao.lastEventBefore(track.songId, wallStart)?.let { it - zoneOffset * 60_000L }
            val lastHeard = listOfNotNull(lastOutcome, lastEvent).maxOrNull()
            val impressionId = if (track.surface.recommended) {
                dao.openImpression(track.songId, track.surface.name, draft.startedAt - IMPRESSION_WINDOW_MS)
            } else {
                null
            }
            try {
                dao.insertOutcome(
                    ListenOutcome(
                        songId = track.songId,
                        sessionId = draft.sessionId,
                        startedAt = draft.startedAt,
                        endedAt = draft.endedAt,
                        zoneOffsetMinutes = zoneOffset,
                        listenedMs = draft.listenedMs,
                        durationMs = track.durationMs,
                        completion = draft.completion.toFloat(),
                        endReason = draft.reason.name,
                        kind = draft.kind.name,
                        surface = track.surface.name,
                        pickSource = track.pickSource,
                        selection = track.selection.name,
                        queuePosition = track.queuePosition,
                        firstListen = lastHeard == null,
                        replayGapDays = lastHeard?.let { ((draft.startedAt - it) / HOME_DAY_MS).toInt() },
                        impressionId = impressionId,
                    ),
                )
            } catch (error: android.database.SQLException) {
                Timber.tag(TAG).d(error, "no song row for %s", track.songId)
                return@query
            }
            if (impressionId != null) {
                if (track.selection == Selection.Manual) dao.markImpressionSelected(impressionId, draft.startedAt)
                dao.markImpressionPlayed(impressionId, draft.startedAt, draft.kind.name)
            }
            learnTransition(draft)
        }
    }

    private fun MusicDatabase.learnTransition(draft: ListenDraft) {
        val artist = intelligenceDao.leadArtist(draft.track.songId) ?: return
        val previous = lastPositive?.takeIf { it.sessionId == draft.sessionId }
        val at = draft.endedAt
        if (previous != null && previous.artistId != artist) {
            when {
                draft.kind.positive -> intelligenceDao.addRelationEvidence(
                    MusicRelation(previous.artistId, artist, RelationKind.CoListen.name, 1f, 1, null, at),
                )
                draft.kind == ListenKind.Skipped && draft.track.selection != Selection.Manual -> intelligenceDao.addRelationEvidence(
                    MusicRelation(previous.artistId, artist, SKIP_AFTER, 1f, 1, null, at),
                )
            }
        }
        if (draft.kind.positive) lastPositive = PositiveListen(draft.sessionId, artist)
    }

    fun logImpressions(surface: RecSurface, items: List<ImpressionItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val prefs = context.dataStore.data.first()
            if (prefs[PauseListenHistoryKey] == true || prefs[PersonalisedRecommendationsKey] == false) return@launch
            val now = System.currentTimeMillis()
            val fresh = items.withIndex().filter { (_, item) ->
                val key = "${surface.name}:${item.songId}"
                val last = shownRecently[key]
                if (last != null && now - last < IMPRESSION_DEDUPE_MS) return@filter false
                shownRecently[key] = now
                true
            }
            if (fresh.isEmpty()) return@launch
            val rows = fresh.map { (index, item) ->
                RecommendationImpression(
                    songId = item.songId,
                    surface = surface.name,
                    shownAt = now,
                    position = index,
                    candidateSource = item.source?.name,
                    reason = item.reason?.code,
                    sessionId = now / IMPRESSION_SESSION_MS,
                )
            }
            database.query { intelligenceDao.insertImpressions(rows) }
        }
    }

    suspend fun discoveryCandidates(
        snapshot: IntelligenceSnapshot,
        seeds: Map<String, Double>,
        excludeIds: Set<String>,
    ): Pair<List<Candidate>, Map<String, String>> = withContext(Dispatchers.IO) {
        val hits = HashMap<String, MutableList<SourceHit>>()
        fun hit(songId: String, source: SourceHit) {
            if (songId !in excludeIds) hits.getOrPut(songId) { ArrayList() }.add(source)
        }

        val seedIds = seeds.keys.toList()
        val titles = HashMap<String, String>()
        if (seedIds.isNotEmpty()) {
            val links = database.homeRelatedLinks(seedIds)
            val maxSeed = seeds.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
            links.groupBy { it.songId }.forEach { (songId, edges) ->
                val best = edges.maxBy { seeds[it.seedId] ?: 0.0 }
                val total = edges.sumOf { seeds[it.seedId] ?: 0.0 }
                hit(songId, SourceHit(CandidateSource.RelatedSong, (total / (maxSeed * 2.0)).coerceIn(0.05, 1.0), viaSong = best.seedId))
            }
            dao.playlistNeighbours(seedIds.take(10), PLAYLIST_NEIGHBOURS).forEach {
                hit(it, SourceHit(CandidateSource.PlaylistNeighbour, 0.3))
            }
            database.getSongsByIds(seedIds).forEach { titles[it.id] = it.song.title }
        }

        val reached = snapshot.reach.values
            .filter { snapshot.taste.artist(it.artistId)?.heard != true }
            .sortedByDescending { it.score }
            .take(GRAPH_ARTISTS)
        val graphByArtist = reached.associateBy { it.artistId }
        if (reached.isNotEmpty()) {
            dao.unheardSongsByArtistRows(reached.map { it.artistId }, GRAPH_SONGS).forEach { row ->
                val reach = graphByArtist[row.artistId] ?: return@forEach
                val (source, strength) = RecommendationEngine.graphSources(reach)
                hit(row.songId, SourceHit(source, strength, viaArtist = reach.via))
            }
        }

        val chosenArtists = (snapshot.taste.seedArtists + snapshot.taste.boostedArtists).toList()
        if (chosenArtists.isNotEmpty()) {
            dao.unheardSongsByArtists(chosenArtists, CHOSEN_SONGS).forEach {
                hit(it, SourceHit(CandidateSource.SeedArtist, 0.6))
            }
        }

        val rows = hits.keys.chunked(400).flatMap { dao.candidateRows(it) }
        CandidateAdapters.fromRows(rows, hits) to titles
    }

    suspend fun rerankUpNext(
        current: MediaMetadata?,
        items: List<MediaItem>,
        surface: RecSurface,
        limit: Int = items.size,
    ): List<UpNextPick> {
        if (items.isEmpty()) return emptyList()
        val snapshot = runCatching { snapshot() }.getOrElse {
            Timber.tag(TAG).w(it, "keeping the provider's order")
            return items.map { item -> UpNextPick(item, CandidateSource.YouTubeRadio) }
        }
        if (!snapshot.enabled) return items.map { UpNextPick(it, CandidateSource.YouTubeRadio) }
        return withContext(Dispatchers.Default) {
            val byId = items.associateBy { it.mediaId }
            val candidates = items.mapIndexedNotNull { index, item ->
                val metadata = item.metadata ?: return@mapIndexedNotNull null
                val strength = 1.0 - index.toDouble() / items.size
                CandidateAdapters.of(metadata, SourceHit(CandidateSource.YouTubeRadio, strength))
            }
            val recent = recentArtists()
            val ordered = RecommendationEngine.upNext(current?.let { CandidateAdapters.of(it) }, candidates, snapshot, recent, limit)
            val picks = ordered.mapNotNull { rec -> byId[rec.song.songId]?.let { UpNextPick(it, rec.source) } }
            logImpressions(surface, ordered.take(UP_NEXT_IMPRESSIONS).map { ImpressionItem(it.song.songId, it.source, it.reason) })
            picks
        }
    }

    suspend fun offlineNext(
        current: MediaMetadata?,
        songs: List<Song>,
        queued: Set<String>,
        count: Int,
    ): List<Song> {
        val pool = songs.filter { it.id !in queued }
        if (pool.isEmpty() || count <= 0) return emptyList()
        val snapshot = runCatching { snapshot() }.getOrDefault(IntelligenceSnapshot.Neutral)
        if (!snapshot.enabled) {
            val artists = current?.artists.orEmpty().mapTo(HashSet()) { it.name }
            val (same, rest) = pool.partition { song -> song.artists.any { it.name in artists } }
            return (same.shuffled() + rest.shuffled()).take(count)
        }
        return withContext(Dispatchers.Default) {
            val byId = pool.associateBy { it.id }
            val shuffled = pool.shuffled()
            val candidates = shuffled.mapIndexed { index, song ->
                CandidateAdapters.of(song, SourceHit(CandidateSource.OnDevice, 0.3 + 0.2 * (1.0 - index.toDouble() / shuffled.size)))
            }
            val ordered = RecommendationEngine.upNext(current?.let { CandidateAdapters.of(it) }, candidates, snapshot, recentArtists(), count)
            logImpressions(RecSurface.OfflineAutoplay, ordered.map { ImpressionItem(it.song.songId, it.source, it.reason) })
            ordered.mapNotNull { byId[it.song.songId] }
        }
    }

    suspend fun newToYou(limit: Int): List<NewToYouPick> {
        val snapshot = snapshot()
        if (!snapshot.enabled) return emptyList()
        val taste = snapshot.taste
        val heard = taste.songs.values.filter { it.heard }
        val seeds = heard.asSequence()
            .filter { HomeRanking.isStreamableId(it.songId) && !it.fadedOut }
            .sortedByDescending { it.plays + it.positive }
            .take(25)
            .associate { it.songId to ln(2.0 + it.plays + it.positive) }
        val (candidates, titles) = discoveryCandidates(snapshot, seeds, heard.mapTo(HashSet()) { it.songId })
        val unfamiliar = candidates.filter { candidate -> candidate.artists.none { taste.artist(it.id)?.heard == true } }
        val picks = withContext(Dispatchers.Default) { RecommendationEngine.discover(unfamiliar, snapshot, titles, limit) }
        if (picks.isEmpty()) return emptyList()
        val songs = withContext(Dispatchers.IO) { database.getSongsByIds(picks.map { it.song.songId }) }.associateBy { it.id }
        return picks.mapNotNull { rec -> songs[rec.song.songId]?.let { NewToYouPick(it, rec) } }
    }

    suspend fun artistsNeedingSongs(limit: Int): List<String> {
        val snapshot = snapshot()
        if (!snapshot.enabled) return emptyList()
        val wanted = (
            snapshot.taste.seedArtists + snapshot.taste.boostedArtists +
                snapshot.reach.values
                    .filter { snapshot.taste.artist(it.artistId)?.heard != true && HomeRanking.isStreamableId(it.artistId) }
                    .sortedByDescending { it.score }
                    .take(GRAPH_ARTISTS / 2)
                    .map { it.artistId }
            ).toList()
        if (wanted.isEmpty()) return emptyList()
        val known = dao.unheardSongsByArtistRows(wanted, GRAPH_SONGS).groupingBy { it.artistId }.eachCount()
        return wanted.filter { (known[it] ?: 0) < 2 }.take(limit)
    }

    private suspend fun recentArtists(): List<String> = runCatching {
        dao.recentListens(System.currentTimeMillis() - RECENT_WINDOW_MS, RECENT_LISTENS).mapNotNull { it.artistId }
    }.getOrDefault(emptyList())

    fun setFeedback(kind: FeedbackKind, targetId: String, label: String?) {
        val now = System.currentTimeMillis()
        database.query {
            intelligenceDao.upsertFeedback(
                MusicFeedback(kind.targetType, targetId, kind.name, label, now, kind.ttlDays?.let { now + it * HOME_DAY_MS }),
            )
        }
    }

    fun removeFeedback(kind: FeedbackKind, targetId: String) {
        database.query { intelligenceDao.deleteFeedback(kind.targetType, targetId, kind.name) }
    }

    fun feedback(): Flow<List<MusicFeedback>> = dao.activeFeedbackFlow(System.currentTimeMillis())

    fun clearLearned() {
        database.query {
            intelligenceDao.clearOutcomes()
            intelligenceDao.clearImpressions()
            intelligenceDao.clearRelations(listOf(RelationKind.CoListen.name, SKIP_AFTER))
        }
        shownRecently.clear()
        lastPositive = null
    }

    fun clearFeedback() {
        database.query { intelligenceDao.clearFeedback() }
    }

    suspend fun surfaceReports(days: Int = 30): List<SurfaceReport> = withContext(Dispatchers.IO) {
        val from = System.currentTimeMillis() - days * HOME_DAY_MS
        RecommendationMetrics.reports(dao.surfaceMetrics(from), dao.surfaceListens(from))
    }

    suspend fun insights(): List<TasteInsight> {
        val snapshot = snapshot()
        if (!snapshot.enabled) return emptyList()
        return withContext(Dispatchers.Default) {
            val stat = dao.discoveryOutcomes(System.currentTimeMillis() - 30 * HOME_DAY_MS)
            TasteInsights.build(snapshot.taste, snapshot.reach, snapshot.graph.names, stat)
        }
    }

    companion object {
        const val TAG = "Intelligence"
        const val SKIP_AFTER = "SkipAfter"
        private val GRAPH_KINDS = listOf(
            RelationKind.CoListen.name,
            RelationKind.SimilarArtist.name,
            RelationKind.RelatedSong.name,
            RelationKind.Playlist.name,
        )
        private const val SAVED_POOL = 200
        private const val GRAPH_EDGES = 12_000
        private const val BAD_TRANSITIONS = 2_000
        private const val RELATED_EDGES = 8_000
        private const val PLAYLIST_EDGES = 1_500
        private const val PLAYLIST_NEIGHBOURS = 120
        private const val GRAPH_ARTISTS = 40
        private const val GRAPH_SONGS = 240
        private const val CHOSEN_SONGS = 80
        private const val UP_NEXT_IMPRESSIONS = 20
        private const val RECENT_LISTENS = 12
        private const val RECENT_WINDOW_MS = 2L * 60 * 60 * 1000
        private const val GRAPH_REFRESH_MS = 12L * 60 * 60 * 1000
        private const val IMPRESSION_WINDOW_MS = 48L * 60 * 60 * 1000
        private const val IMPRESSION_DEDUPE_MS = 12L * 60 * 60 * 1000
        private const val IMPRESSION_SESSION_MS = 30L * 60 * 1000
        private const val OUTCOME_RETENTION_DAYS = 540L
        private const val IMPRESSION_RETENTION_DAYS = 120L
    }
}
