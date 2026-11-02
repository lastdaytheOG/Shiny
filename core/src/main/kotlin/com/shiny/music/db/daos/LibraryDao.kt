package com.shiny.music.db.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.shiny.music.db.entities.EventWithSong
import com.shiny.music.db.entities.Song
import kotlinx.coroutines.flow.Flow

// what counts as a song in your library: saved, downloaded, on the phone, or inside a playlist you imported
private const val EVERY_SONG =
    "inLibrary IS NOT NULL OR isDownloaded = 1 OR isLocal = 1 OR id IN (" +
        "SELECT psm.songId FROM playlist_song_map psm JOIN playlist p ON p.id = psm.playlistId " +
        "WHERE p.id LIKE 'SPOTIFY_PLAYLIST_%' OR p.id = 'SPOTIFY_LIKED_SONGS' " +
        "OR (p.browseId IS NOT NULL AND p.bookmarkedAt IS NOT NULL))"

@Dao
interface LibraryDao {
    @Transaction
    @Query("SELECT * FROM event WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT 3000")
    suspend fun eventsSince(since: Long): List<EventWithSong>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY likedDate DESC LIMIT :limit")
    suspend fun recentLikes(limit: Int): List<Song>

    @Query("SELECT COUNT(1) FROM event")
    fun eventCount(): Flow<Int>

    @Transaction
    @Query("SELECT song.* FROM song JOIN (SELECT songId, MAX(timestamp) AS lastPlayed FROM event GROUP BY songId) e ON e.songId = song.id ORDER BY e.lastPlayed DESC LIMIT :limit")
    fun recentHistory(limit: Int): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE $EVERY_SONG")
    fun everySong(): Flow<List<Song>>

    @Query("SELECT COUNT(1) FROM song WHERE ($EVERY_SONG) AND NOT (:hideExplicit AND explicit) AND NOT (:hideVideos AND isVideo)")
    fun everySongCount(hideExplicit: Boolean, hideVideos: Boolean): Flow<Int>

    @Query("SELECT thumbnailUrl FROM song WHERE ($EVERY_SONG) AND thumbnailUrl IS NOT NULL ORDER BY COALESCE(inLibrary, dateDownload, 0) DESC, rowid DESC LIMIT :limit")
    fun everySongCovers(limit: Int): Flow<List<String>>

    @Query("SELECT thumbnailUrl FROM song WHERE liked AND thumbnailUrl IS NOT NULL ORDER BY likedDate DESC LIMIT :limit")
    fun likedCovers(limit: Int): Flow<List<String>>

    @Query("SELECT thumbnailUrl FROM song WHERE isDownloaded = 1 AND thumbnailUrl IS NOT NULL ORDER BY dateDownload DESC LIMIT :limit")
    fun downloadedCovers(limit: Int): Flow<List<String>>

    @Query("SELECT thumbnailUrl FROM song WHERE isLocal = 1 AND thumbnailUrl IS NOT NULL ORDER BY rowid DESC LIMIT :limit")
    fun deviceCovers(limit: Int): Flow<List<String>>

    @Query("SELECT COUNT(1) FROM song WHERE isLocal = 1")
    fun deviceCount(): Flow<Int>
}
