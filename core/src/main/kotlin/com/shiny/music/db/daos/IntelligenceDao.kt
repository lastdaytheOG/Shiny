package com.shiny.music.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shiny.music.db.entities.AnalysisCandidate
import com.shiny.music.db.entities.ArtistDaypartStat
import com.shiny.music.db.entities.ArtistEdgeRow
import com.shiny.music.db.entities.ArtistNameRow
import com.shiny.music.db.entities.ArtistOutcomeBucket
import com.shiny.music.db.entities.ArtistSongRow
import com.shiny.music.db.entities.BeatInfoEntity
import com.shiny.music.db.entities.CandidateRow
import com.shiny.music.db.entities.DiscoveryOutcomeStat
import com.shiny.music.db.entities.ImpressionFatigue
import com.shiny.music.db.entities.ListenOutcome
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.MusicRelation
import com.shiny.music.db.entities.RecentListen
import com.shiny.music.db.entities.RecommendationImpression
import com.shiny.music.db.entities.SongFeature
import com.shiny.music.db.entities.SongOutcomeBucket
import com.shiny.music.db.entities.SongTitleRow
import com.shiny.music.db.entities.SourceOutcomeStat
import com.shiny.music.db.entities.SurfaceListenRow
import com.shiny.music.db.entities.SurfaceMetricRow
import kotlinx.coroutines.flow.Flow

@Dao
interface IntelligenceDao {
    @Insert
    fun insertOutcome(outcome: ListenOutcome): Long

    @Query("SELECT MAX(startedAt) FROM listen_outcome WHERE songId = :songId")
    fun lastOutcomeAt(songId: String): Long?

    @Query("SELECT MAX(timestamp) FROM event WHERE songId = :songId AND timestamp < :wallBefore")
    fun lastEventBefore(songId: String, wallBefore: Long): Long?

    @Query("SELECT artistId FROM song_artist_map WHERE songId = :songId ORDER BY position LIMIT 1")
    fun leadArtist(songId: String): String?

    @Query(
        """
        SELECT songId,
               MIN((:now - startedAt) / :bucketMs, :maxBucket) AS bucket,
               SUM(kind IN ('Meaningful', 'Completed')) AS positive,
               SUM(kind = 'Partial') AS partial,
               SUM(kind = 'Skipped') AS negative,
               SUM(listenedMs) AS listenedMs,
               MAX(startedAt) AS lastAt
        FROM listen_outcome
        GROUP BY songId, bucket
        """,
    )
    suspend fun songOutcomeBuckets(now: Long, bucketMs: Long, maxBucket: Int): List<SongOutcomeBucket>

    @Query(
        """
        SELECT sam.artistId AS artistId,
               MIN((:now - o.startedAt) / :bucketMs, :maxBucket) AS bucket,
               SUM(o.kind IN ('Meaningful', 'Completed')) AS positive,
               SUM(o.kind = 'Partial') AS partial,
               SUM(o.kind = 'Skipped') AS negative,
               MAX(o.startedAt) AS lastAt
        FROM listen_outcome o JOIN song_artist_map sam ON sam.songId = o.songId
        GROUP BY sam.artistId, bucket
        """,
    )
    suspend fun artistOutcomeBuckets(now: Long, bucketMs: Long, maxBucket: Int): List<ArtistOutcomeBucket>

    @Query(
        """
        SELECT pickSource AS source,
               SUM(kind IN ('Meaningful', 'Completed')) AS positive,
               SUM(kind = 'Skipped') AS negative
        FROM listen_outcome
        WHERE pickSource IS NOT NULL
        GROUP BY pickSource
        """,
    )
    suspend fun sourceOutcomeStats(): List<SourceOutcomeStat>

    @Query(
        """
        SELECT IFNULL(SUM(firstListen = 1 AND startedAt > :recentFrom AND selection != 'Manual'), 0) AS recentFirstListens,
               IFNULL(SUM(firstListen = 1 AND startedAt > :recentFrom AND selection != 'Manual' AND kind IN ('Meaningful', 'Completed')), 0) AS recentFirstPositive,
               IFNULL(SUM(firstListen = 1 AND startedAt > :recentFrom AND selection != 'Manual' AND kind = 'Skipped'), 0) AS recentFirstNegative,
               IFNULL(SUM(firstListen = 1 AND startedAt <= :recentFrom), 0) AS priorFirstListens,
               IFNULL(SUM(firstListen = 1 AND startedAt <= :recentFrom AND kind IN ('Meaningful', 'Completed')), 0) AS priorFirstPositive,
               COUNT(*) AS totalListens,
               IFNULL(SUM(firstListen = 0), 0) AS repeatListens
        FROM listen_outcome
        WHERE kind NOT IN ('Accidental', 'Interrupted')
        """,
    )
    suspend fun discoveryOutcomes(recentFrom: Long): DiscoveryOutcomeStat

    @Query(
        """
        SELECT sam.artistId AS artistId,
               SUM(CASE WHEN (e.timestamp / 3600000) % 24 IN (:hours) THEN 1 ELSE 0 END) AS here,
               COUNT(*) AS total
        FROM event e JOIN song_artist_map sam ON sam.songId = e.songId
        WHERE e.timestamp > :from
        GROUP BY sam.artistId
        """,
    )
    suspend fun artistDaypartStats(from: Long, hours: List<Int>): List<ArtistDaypartStat>

    @Query(
        """
        SELECT o.songId AS songId, o.kind AS kind, o.startedAt AS startedAt, o.surface AS surface, o.selection AS selection,
               (SELECT sam.artistId FROM song_artist_map sam WHERE sam.songId = o.songId ORDER BY sam.position LIMIT 1) AS artistId
        FROM listen_outcome o
        WHERE o.startedAt > :from
        ORDER BY o.startedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentListens(from: Long, limit: Int): List<RecentListen>

    @Query("SELECT query FROM search_history ORDER BY id DESC LIMIT :limit")
    suspend fun recentSearches(limit: Int): List<String>

    @Insert
    fun insertImpressions(impressions: List<RecommendationImpression>)

    @Query("SELECT DISTINCT songId FROM recommendation_impression WHERE surface = :surface AND shownAt > :from")
    suspend fun recentlyShown(surface: String, from: Long): List<String>

    @Query("UPDATE recommendation_impression SET selectedAt = :at WHERE id = :id AND selectedAt IS NULL")
    fun markImpressionSelected(id: Long, at: Long)

    @Query(
        """
        SELECT id FROM recommendation_impression
        WHERE songId = :songId AND surface = :surface AND shownAt > :from AND playedAt IS NULL
        ORDER BY shownAt DESC
        LIMIT 1
        """,
    )
    fun openImpression(songId: String, surface: String, from: Long): Long?

    @Query("UPDATE recommendation_impression SET playedAt = :at, outcomeKind = :kind WHERE id = :id")
    fun markImpressionPlayed(id: Long, at: Long, kind: String)

    @Query(
        """
        SELECT songId,
               COUNT(*) AS shown,
               SUM(playedAt IS NOT NULL) AS played,
               MAX(shownAt) AS lastShown
        FROM recommendation_impression
        WHERE shownAt > :from
        GROUP BY songId
        """,
    )
    suspend fun impressionFatigue(from: Long): List<ImpressionFatigue>

    @Query(
        """
        SELECT surface,
               COUNT(*) AS shown,
               SUM(selectedAt IS NOT NULL) AS selected,
               SUM(playedAt IS NOT NULL) AS played,
               SUM(outcomeKind IN ('Meaningful', 'Completed')) AS meaningful,
               SUM(outcomeKind = 'Skipped') AS skipped,
               COUNT(DISTINCT songId) AS distinctSongs
        FROM recommendation_impression
        WHERE shownAt > :from
        GROUP BY surface
        ORDER BY shown DESC
        """,
    )
    suspend fun surfaceMetrics(from: Long): List<SurfaceMetricRow>

    @Query(
        """
        SELECT o.surface AS surface,
               COUNT(*) AS listens,
               SUM(o.kind = 'Completed') AS completed,
               SUM(o.kind = 'Meaningful') AS meaningful,
               SUM(o.kind = 'Partial') AS partial,
               SUM(o.kind = 'Skipped') AS skipped,
               SUM(o.kind = 'Accidental') AS accidental,
               SUM(o.kind = 'Interrupted') AS interrupted,
               SUM(o.replayGapDays IS NOT NULL) AS replays,
               SUM(o.firstListen = 1) AS firstListens,
               SUM(o.firstListen = 1 AND o.kind IN ('Meaningful', 'Completed')) AS firstPositive,
               COUNT(DISTINCT (SELECT sam.artistId FROM song_artist_map sam WHERE sam.songId = o.songId ORDER BY sam.position LIMIT 1)) AS distinctArtists
        FROM listen_outcome o
        WHERE o.startedAt > :from
        GROUP BY o.surface
        ORDER BY listens DESC
        """,
    )
    suspend fun surfaceListens(from: Long): List<SurfaceListenRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFeedback(feedback: MusicFeedback)

    @Query("DELETE FROM music_feedback WHERE targetType = :targetType AND targetId = :targetId AND kind = :kind")
    fun deleteFeedback(targetType: String, targetId: String, kind: String)

    @Query("DELETE FROM music_feedback WHERE targetType = :targetType AND targetId = :targetId")
    fun deleteFeedbackFor(targetType: String, targetId: String)

    @Query("SELECT * FROM music_feedback WHERE expiresAt IS NULL OR expiresAt > :now")
    suspend fun activeFeedback(now: Long): List<MusicFeedback>

    @Query("SELECT * FROM music_feedback WHERE expiresAt IS NULL OR expiresAt > :now ORDER BY createdAt DESC")
    fun activeFeedbackFlow(now: Long): Flow<List<MusicFeedback>>

    @Query("DELETE FROM music_feedback WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    fun pruneFeedback(now: Long)

    @Query("DELETE FROM music_feedback")
    fun clearFeedback()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRelations(relations: List<MusicRelation>)

    @Query("UPDATE music_relation SET weight = weight + :delta, evidence = evidence + 1, updatedAt = :at WHERE fromId = :fromId AND toId = :toId AND kind = :kind")
    fun bumpRelation(fromId: String, toId: String, kind: String, delta: Float, at: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertRelationIfAbsent(relation: MusicRelation)

    @Transaction
    fun addRelationEvidence(relation: MusicRelation) {
        if (bumpRelation(relation.fromId, relation.toId, relation.kind, relation.weight, relation.updatedAt) == 0) {
            insertRelationIfAbsent(relation)
        }
    }

    @Query("SELECT fromId, toId, weight, kind, toName FROM music_relation WHERE kind IN (:kinds) ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun relationEdges(kinds: List<String>, limit: Int): List<ArtistEdgeRow>

    @Query(
        """
        SELECT a1.artistId AS fromId,
               a2.artistId AS toId,
               COUNT(*) AS weight,
               'RelatedSong' AS kind,
               (SELECT name FROM artist WHERE id = a2.artistId) AS toName
        FROM related_song_map r
        JOIN song_artist_map a1 ON a1.songId = r.songId AND a1.position = 0
        JOIN song_artist_map a2 ON a2.songId = r.relatedSongId AND a2.position = 0
        WHERE a1.artistId != a2.artistId
        GROUP BY a1.artistId, a2.artistId
        ORDER BY weight DESC
        LIMIT :limit
        """,
    )
    suspend fun relatedArtistEdges(limit: Int): List<ArtistEdgeRow>

    @Query(
        """
        SELECT a1.artistId AS fromId,
               a2.artistId AS toId,
               COUNT(DISTINCT p1.playlistId) AS weight,
               'Playlist' AS kind,
               (SELECT name FROM artist WHERE id = a2.artistId) AS toName
        FROM playlist_song_map p1
        JOIN playlist_song_map p2 ON p2.playlistId = p1.playlistId AND p2.songId != p1.songId
        JOIN song_artist_map a1 ON a1.songId = p1.songId AND a1.position = 0
        JOIN song_artist_map a2 ON a2.songId = p2.songId AND a2.position = 0
        WHERE a1.artistId IN (:artistIds) AND a1.artistId != a2.artistId
        GROUP BY a1.artistId, a2.artistId
        ORDER BY weight DESC
        LIMIT :limit
        """,
    )
    suspend fun playlistArtistEdges(artistIds: List<String>, limit: Int): List<ArtistEdgeRow>

    @Query(
        """
        SELECT s.id AS songId, s.title AS title, s.albumId AS albumId, s.year AS year, s.duration AS duration,
               s.explicit AS explicit, s.isVideo AS isVideo, s.isLocal AS isLocal, s.isDownloaded AS isDownloaded,
               sam.artistId AS artistId, a.name AS artistName, sam.position AS position
        FROM song s
        LEFT JOIN song_artist_map sam ON sam.songId = s.id
        LEFT JOIN artist a ON a.id = sam.artistId
        WHERE s.id IN (:songIds)
        """,
    )
    suspend fun candidateRows(songIds: List<String>): List<CandidateRow>

    @Query(
        """
        SELECT s.id FROM song s
        JOIN song_artist_map sam ON sam.songId = s.id
        WHERE sam.artistId IN (:artistIds) AND sam.position = 0
          AND s.id NOT IN (SELECT DISTINCT songId FROM event)
        ORDER BY s.rowid DESC
        LIMIT :limit
        """,
    )
    suspend fun unheardSongsByArtists(artistIds: List<String>, limit: Int): List<String>

    @Query(
        """
        SELECT s.id AS songId, sam.artistId AS artistId FROM song s
        JOIN song_artist_map sam ON sam.songId = s.id
        WHERE sam.artistId IN (:artistIds) AND sam.position = 0
          AND s.id NOT IN (SELECT DISTINCT songId FROM event)
        ORDER BY s.rowid DESC
        LIMIT :limit
        """,
    )
    suspend fun unheardSongsByArtistRows(artistIds: List<String>, limit: Int): List<ArtistSongRow>

    @Query("SELECT id, title FROM song WHERE id IN (:ids)")
    suspend fun songTitles(ids: List<String>): List<SongTitleRow>

    @Query("SELECT id, name FROM artist")
    suspend fun artistNames(): List<ArtistNameRow>

    @Query("SELECT COUNT(*) FROM music_relation")
    suspend fun relationCount(): Int

    @Query(
        """
        SELECT DISTINCT psm.songId FROM playlist_song_map psm
        WHERE psm.playlistId IN (SELECT psm2.playlistId FROM playlist_song_map psm2 WHERE psm2.songId IN (:seedIds))
          AND psm.songId NOT IN (SELECT DISTINCT songId FROM event)
        LIMIT :limit
        """,
    )
    suspend fun playlistNeighbours(seedIds: List<String>, limit: Int): List<String>

    @Query(
        """
        SELECT b.songId AS songId, b.bpm AS bpm, b.confidence AS confidence, b.energy AS energy, b.brightness AS brightness,
               b.dynamics AS dynamics, b.keyPitchClass AS keyPitchClass, b.keyIsMinor AS keyIsMinor
        FROM beat_info b
        """,
    )
    suspend fun songFeatures(): List<SongFeature>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBeatInfo(info: BeatInfoEntity)

    @Query(
        """
        SELECT id AS songId, isLocal AS isLocal FROM song
        WHERE (isLocal = 1 OR isDownloaded = 1 OR totalPlayTime > 0) AND id NOT IN (SELECT songId FROM beat_info)
        ORDER BY totalPlayTime DESC
        LIMIT :limit
        """,
    )
    suspend fun songsAwaitingAnalysis(limit: Int): List<AnalysisCandidate>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM listen_outcome) || ':' ||
               (SELECT COUNT(*) FROM event) || ':' ||
               (SELECT COUNT(*) FROM music_feedback) || ':' ||
               (SELECT COUNT(*) FROM music_relation) || ':' ||
               (SELECT COUNT(*) FROM related_song_map) || ':' ||
               (SELECT COUNT(*) FROM beat_info) || ':' ||
               (SELECT COUNT(*) FROM song WHERE liked = 1)
        """,
    )
    suspend fun snapshotKey(): String

    @Query("DELETE FROM listen_outcome")
    fun clearOutcomes()

    @Query("DELETE FROM recommendation_impression")
    fun clearImpressions()

    @Query("DELETE FROM music_relation WHERE kind IN (:kinds)")
    fun clearRelations(kinds: List<String>)

    @Query("DELETE FROM listen_outcome WHERE startedAt < :before")
    fun pruneOutcomes(before: Long)

    @Query("DELETE FROM recommendation_impression WHERE shownAt < :before")
    fun pruneImpressions(before: Long)

    @Query("UPDATE listen_outcome SET songId = :toId WHERE songId = :fromId")
    fun moveOutcomes(fromId: String, toId: String)

    @Query("UPDATE recommendation_impression SET songId = :toId WHERE songId = :fromId")
    fun moveImpressions(fromId: String, toId: String)
}
