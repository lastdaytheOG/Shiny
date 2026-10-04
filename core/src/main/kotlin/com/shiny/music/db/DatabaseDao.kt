

package com.shiny.music.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.RoomWarnings
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.shiny.music.constants.AlbumSortType
import com.shiny.music.constants.ArtistSongSortType
import com.shiny.music.constants.ArtistSortType
import com.shiny.music.constants.PlaylistSortType
import com.shiny.music.constants.SongSortType
import com.shiny.music.db.entities.Album
import com.shiny.music.db.entities.AlbumArtistMap
import com.shiny.music.db.entities.AlbumEntity
import com.shiny.music.db.entities.AlbumWithSongs
import com.shiny.music.db.entities.Artist
import com.shiny.music.db.entities.ArtistEntity
import com.shiny.music.db.entities.Event
import com.shiny.music.db.entities.EventWithSong
import com.shiny.music.db.entities.FormatEntity
import com.shiny.music.db.entities.HomeAlbumStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeLibraryCounts
import com.shiny.music.db.entities.HomeRelatedLink
import com.shiny.music.db.entities.HomeSavedArtist
import com.shiny.music.db.entities.HomeSavedSong
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.LyricsEntity
import com.shiny.music.db.entities.PlayCountEntity
import com.shiny.music.db.entities.Playlist
import com.shiny.music.db.entities.PlaylistEntity
import com.shiny.music.db.entities.PlaylistSong
import com.shiny.music.db.entities.PlaylistSongMap
import com.shiny.music.db.entities.RecognitionHistory
import com.shiny.music.db.entities.RelatedSongMap
import com.shiny.music.db.entities.SearchHistory
import com.shiny.music.db.entities.SetVideoIdEntity
import com.shiny.music.db.entities.Song
import com.shiny.music.db.entities.SongAlbumMap
import com.shiny.music.db.entities.SongArtistMap
import com.shiny.music.db.entities.SongEntity
import com.shiny.music.db.entities.SongLastPlayed
import com.shiny.music.db.entities.SongMonthPlays
import com.shiny.music.db.entities.SongWithStats
import com.shiny.music.extensions.reversed
import com.shiny.music.extensions.toSQLiteQuery
import com.shiny.music.models.MediaMetadata
import com.shiny.music.models.toSongEntity

import com.shiny.music.models.toMediaMetadata
import com.shiny.music.ui.utils.resize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.text.Collator
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

@Dao
interface DatabaseDao {
    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY rowId")
    fun songsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY inLibrary")
    fun songsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY title")
    fun songsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE inLibrary IS NOT NULL ORDER BY totalPlayTime")
    fun songsByPlayTimeAsc(): Flow<List<Song>>


    @Transaction
    @Query("SELECT * FROM song ORDER BY totalPlayTime DESC LIMIT :limit")
    fun topSongs(limit: Int): Flow<List<Song>>

    fun songs(
        sortType: SongSortType,
        descending: Boolean,
    ) = when (sortType) {
        SongSortType.CREATE_DATE -> songsByCreateDateAsc()
        SongSortType.NAME ->
            songsByNameAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }

        SongSortType.ARTIST ->
            songsByRowIdAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs
                    .sortedWith(
                        compareBy(collator) { song ->
                            song.artists.joinToString("") { it.name }
                        },
                    ).groupBy { it.album?.title }
                    .flatMap { (_, songsByAlbum) ->
                        songsByAlbum.sortedBy { album ->
                            album.artists.joinToString(
                                "",
                            ) { it.name }
                        }
                    }
            }

        SongSortType.PLAY_TIME -> songsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY rowId")
    fun likedSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY likedDate")
    fun likedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY title")
    fun likedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE liked ORDER BY totalPlayTime")
    fun likedSongsByPlayTimeAsc(): Flow<List<Song>>

    fun likedSongs(
        sortType: SongSortType,
        descending: Boolean,
    ) = when (sortType) {
        SongSortType.CREATE_DATE -> likedSongsByCreateDateAsc()
        SongSortType.NAME ->
            likedSongsByNameAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }

        SongSortType.ARTIST ->
            likedSongsByRowIdAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs
                    .sortedWith(
                        compareBy(collator) { song ->
                            song.artists.joinToString("") { it.name }
                        },
                    ).groupBy { it.album?.title }
                    .flatMap { (_, songsByAlbum) ->
                        songsByAlbum.sortedBy { album ->
                            album.artists.joinToString(
                                "",
                            ) { it.name }
                        }
                    }
            }

        SongSortType.PLAY_TIME -> likedSongsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @Query("SELECT COUNT(1) FROM song WHERE liked")
    fun likedSongsCount(): Flow<Int>

    @Transaction
    @Query("SELECT song.* FROM song JOIN song_album_map ON song.id = song_album_map.songId WHERE song_album_map.albumId = :albumId")
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY inLibrary",
    )
    fun artistSongsByCreateDateAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY title",
    )
    fun artistSongsByNameAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY totalPlayTime",
    )
    fun artistSongsByPlayTimeAsc(artistId: String): Flow<List<Song>>

    fun artistSongs(
        artistId: String,
        sortType: ArtistSongSortType,
        descending: Boolean,
        fromTimeStamp: Long? = null,
        toTimeStamp: Long? = null,
        limit: Int = -1
    ): Flow<List<Song>> {
        val songsFlow = when (sortType) {
            ArtistSongSortType.CREATE_DATE -> artistSongsByCreateDateAsc(artistId)
            ArtistSongSortType.NAME ->
                artistSongsByNameAsc(artistId).map { artistSongs ->
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    artistSongs.sortedWith(compareBy(collator) { it.song.title })
                }

            ArtistSongSortType.PLAY_TIME -> {
                if (fromTimeStamp != null && toTimeStamp != null) {
                    mostPlayedSongsByArtist(artistId, fromTimeStamp, toTimeStamp)
                } else {
                    artistSongsByPlayTimeAsc(artistId)
                }
            }
        }

        return songsFlow.map { songs ->
            val limitedSongs = if (limit > 0) songs.take(limit) else songs
            limitedSongs.reversed(descending)
        }
    }

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT s.*
        FROM song s
        JOIN (
            SELECT e.songId, SUM(e.playTime) as totalPlayTime
            FROM event e
            JOIN song_artist_map sam ON e.songId = sam.songId
            WHERE sam.artistId = :artistId AND e.timestamp >= :fromTimeStamp AND e.timestamp <= :toTimeStamp
            GROUP BY e.songId
        ) AS play_times ON s.id = play_times.songId
        ORDER BY play_times.totalPlayTime DESC
        """
    )
    fun mostPlayedSongsByArtist(artistId: String, fromTimeStamp: Long, toTimeStamp: Long): Flow<List<Song>>

    @Transaction
    @Query(
        "SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL LIMIT :previewSize",
    )
    fun artistSongsPreview(
        artistId: String,
        previewSize: Int = 3,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *, COUNT(1) AS referredCount
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN song ON song.id = map.relatedSongId
        WHERE songId IN (SELECT songId
                         FROM (SELECT songId
                               FROM event
                               ORDER BY ROWID DESC
                               LIMIT 5)
                         UNION
                         SELECT songId
                         FROM (SELECT songId
                               FROM event
                               WHERE timestamp > :now - 86400000 * 7
                               GROUP BY songId
                               ORDER BY SUM(playTime) DESC
                               LIMIT 5)
                         UNION
                         SELECT id
                         FROM (SELECT id
                               FROM song
                               ORDER BY totalPlayTime DESC
                               LIMIT 10))
        ORDER BY referredCount DESC
        LIMIT 100
    """,
    )
    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT
            song.*
        FROM
            event
        JOIN
            song ON event.songId = song.id
        WHERE
            event.timestamp > (:now - 86400000 * 7 * 2)
        GROUP BY
            song.albumId
        HAVING
            song.albumId IS NOT NULL
        ORDER BY
            sum(event.playTime) DESC
        LIMIT :limit
        OFFSET :offset

        """,
    )
    fun getRecommendationAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT s.id, s.title, s.thumbnailUrl, s.isVideo,
               (SELECT name FROM artist WHERE id = sam.artistId) as artistName,
               (SELECT COUNT(1)
                FROM event
                WHERE songId = s.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCountListened,
               (SELECT SUM(event.playTime)
                FROM event
                WHERE songId = s.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM song s
        LEFT JOIN song_artist_map sam ON s.id = sam.songId
        JOIN (SELECT songId
              FROM event
              WHERE timestamp > :fromTimeStamp
                AND timestamp <= :toTimeStamp
              GROUP BY songId
              ORDER BY SUM(playTime) DESC
              LIMIT :limit) AS top_songs ON s.id = top_songs.songId
        GROUP BY s.id
        ORDER BY timeListened DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun mostPlayedSongsStats(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<SongWithStats>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT song.*,
               (SELECT COUNT(1)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCountListened,
               (SELECT SUM(event.playTime)
                FROM event
                WHERE songId = song.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM song
        JOIN (SELECT songId
                     FROM event
                     WHERE timestamp > :fromTimeStamp
                     AND timestamp <= :toTimeStamp
                     GROUP BY songId
                     ORDER BY SUM(playTime) DESC
                     LIMIT :limit)
        ON song.id = songId
        ORDER BY timeListened DESC
        LIMIT :limit
        OFFSET :offset
    """,
    )
    fun mostPlayedSongs(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS songCount,
               (SELECT SUM(event.playTime)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id
                  AND timestamp > :fromTimeStamp AND timestamp <= :toTimeStamp) AS timeListened
        FROM artist
                 JOIN(SELECT artistId, SUM(songTotalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN (SELECT songId, SUM(playTime) AS songTotalPlayTime
                                     FROM event
                                     WHERE timestamp > :fromTimeStamp
                                     AND timestamp <= :toTimeStamp
                                     GROUP BY songId) AS e
                                    ON song_artist_map.songId = e.songId
                      GROUP BY artistId
                      ORDER BY totalPlayTime DESC
                      LIMIT :limit
                      OFFSET :offset)
                     ON artist.id = artistId
    """,
    )
    fun mostPlayedArtists(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
    SELECT album.*,
           COUNT(DISTINCT song_album_map.songId) as downloadCount,
           (SELECT COUNT(1)
            FROM song_album_map
                     JOIN event e ON song_album_map.songId = e.songId
            WHERE albumId = album.id
              AND e.timestamp > :fromTimeStamp
              AND e.timestamp <= :toTimeStamp) AS songCountListened,
           (SELECT SUM(e.playTime)
            FROM song_album_map
                     JOIN event e ON song_album_map.songId = e.songId
            WHERE albumId = album.id
              AND e.timestamp > :fromTimeStamp
              AND e.timestamp <= :toTimeStamp) AS timeListened
    FROM album
    JOIN song_album_map ON album.id = song_album_map.albumId
    WHERE album.id IN (
        SELECT sam.albumId
        FROM event
                 JOIN song_album_map sam ON event.songId = sam.songId
        WHERE event.timestamp > :fromTimeStamp
          AND event.timestamp <= :toTimeStamp
        GROUP BY sam.albumId
        HAVING sam.albumId IS NOT NULL
    )
    GROUP BY album.id
    ORDER BY timeListened DESC
    LIMIT :limit OFFSET :offset
    """
    )
    fun mostPlayedAlbums(
        fromTimeStamp: Long,
        limit: Int = 6,
        offset: Int = 0,
        toTimeStamp: Long? = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli(),
    ): Flow<List<Album>>

    @Query("SELECT SUM(playTime) FROM event WHERE timestamp >= :fromTimeStamp AND timestamp <= :toTimeStamp")
    fun getTotalPlayTimeInRange(fromTimeStamp: Long, toTimeStamp: Long): Flow<Long?>

    @Query("SELECT COUNT(DISTINCT songId) FROM event WHERE timestamp >= :fromTimeStamp AND timestamp <= :toTimeStamp")
    fun getUniqueSongCountInRange(fromTimeStamp: Long, toTimeStamp: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT artistId)
        FROM event
        JOIN song_artist_map ON event.songId = song_artist_map.songId
        WHERE timestamp >= :fromTimeStamp AND timestamp <= :toTimeStamp
    """
    )
    fun getUniqueArtistCountInRange(fromTimeStamp: Long, toTimeStamp: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT albumId)
        FROM event
        JOIN song ON event.songId = song.id
        WHERE timestamp >= :fromTimeStamp AND timestamp <= :toTimeStamp
    """
    )
    fun getUniqueAlbumCountInRange(fromTimeStamp: Long, toTimeStamp: Long): Flow<Int>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*, count(song.dateDownload) downloadCount
        FROM album_artist_map
            JOIN album ON album_artist_map.albumId = album.id
            JOIN song ON album_artist_map.albumId = song.albumId
        WHERE artistId = :artistId
        GROUP BY album.id
        LIMIT :previewSize
    """
    )
    fun artistAlbumsPreview(artistId: String, previewSize: Int = 6): Flow<List<Album>>

    @Query("SELECT sum(count) from playCount WHERE song = :songId")
    fun getLifetimePlayCount(songId: String?): Flow<Int>

    @Query("SELECT sum(count) from playCount WHERE song = :songId AND year = :year")
    fun getPlayCountByYear(songId: String?, year: Int): Flow<Int>

    @Query("SELECT count from playCount WHERE song = :songId AND year = :year AND month = :month")
    fun getPlayCountByMonth(songId: String?, year: Int, month: Int): Flow<Int>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT n.songId      AS eid,
                     SUM(playTime) AS oldPlayTime,
                     newPlayTime
              FROM event
                       JOIN
                   (SELECT songId, SUM(playTime) AS newPlayTime
                    FROM event
                    WHERE timestamp > (:now - 86400000 * 30 * 1)
                    GROUP BY songId
                    ORDER BY newPlayTime) as n
                   ON event.songId = n.songId
              WHERE timestamp < (:now - 86400000 * 30 * 1)
              GROUP BY n.songId
              ORDER BY oldPlayTime) AS t
                 JOIN song on song.id = t.eid
        WHERE 0.2 * t.oldPlayTime > t.newPlayTime
        LIMIT 100
    """
    )
    fun forgottenFavorites(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM event
                 JOIN
             song ON event.songId = song.id
        WHERE event.timestamp > (:now - 86400000 * 7 * 2)
        GROUP BY song.albumId
        HAVING song.albumId IS NOT NULL
        ORDER BY sum(event.playTime) DESC
        LIMIT :limit
        OFFSET :offset
        """,
    )
    fun recommendedAlbum(
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
        offset: Int = 0,
    ): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId")
    fun song(songId: String?): Flow<Song?>

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    suspend fun getSongById(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id = :songId LIMIT 1")
    fun getSongByIdBlocking(songId: String): Song?

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    suspend fun getSongsByIds(songIds: List<String>): List<Song>

    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    fun getSongsByIdsFlow(songIds: List<String>): Flow<List<Song>>


    @Transaction
    @Query("SELECT * FROM song_artist_map WHERE songId = :songId")
    fun songArtistMap(songId: String): List<SongArtistMap>

    @Transaction
    @Query("SELECT * FROM song")
    fun allSongs(): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT DISTINCT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN event ON song_artist_map.songId = event.songId
                WHERE artistId = artist.id) AS songCount
        FROM artist
                 LEFT JOIN(SELECT artistId, SUM(songTotalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN (SELECT songId, SUM(playTime) AS songTotalPlayTime
                                     FROM event
                                     GROUP BY songId) AS e
                                    ON song_artist_map.songId = e.songId
                      GROUP BY artistId
                      ORDER BY totalPlayTime DESC) AS artistTotalPlayTime
                     ON artist.id = artistId
                     OR artist.bookmarkedAt IS NOT NULL
                     ORDER BY
                      CASE
                        WHEN artistTotalPlayTime.artistId IS NULL THEN 1
                        ELSE 0
                      END,
                      artistTotalPlayTime.totalPlayTime DESC
    """,
    )
    fun allArtistsByPlayTime(): Flow<List<Artist>>

    @Query("SELECT * FROM set_video_id WHERE videoId = :videoId")
    suspend fun getSetVideoId(videoId: String): SetVideoIdEntity?

    @Transaction
    @Query("SELECT * FROM format WHERE id = :id")
    fun format(id: String?): Flow<FormatEntity?>

    @Transaction
    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY rowId")
    fun artistsByCreateDateAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY name")
    fun artistsByNameAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE songCount > 0 ORDER BY songCount")
    fun artistsBySongCountAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN song ON song_artist_map.songId = song.id
                WHERE artistId = artist.id
                  AND song.inLibrary IS NOT NULL) AS songCount
        FROM artist
                 JOIN(SELECT artistId, SUM(totalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN song
                                    ON song_artist_map.songId = song.id
                      GROUP BY artistId
                      ORDER BY totalPlayTime)
                     ON artist.id = artistId
        WHERE songCount > 0
    """
    )
    fun artistsByPlayTimeAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt")
    fun artistsBookmarkedByCreateDateAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY name")
    fun artistsBookmarkedByNameAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun artistsBookmarkedBySongCountAsc(): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT artist.*,
               (SELECT COUNT(1)
                FROM song_artist_map
                         JOIN song ON song_artist_map.songId = song.id
                WHERE artistId = artist.id
                  AND song.inLibrary IS NOT NULL) AS songCount
        FROM artist
                 JOIN(SELECT artistId, SUM(totalPlayTime) AS totalPlayTime
                      FROM song_artist_map
                               JOIN song
                                    ON song_artist_map.songId = song.id
                      GROUP BY artistId
                      ORDER BY totalPlayTime)
                     ON artist.id = artistId
        WHERE bookmarkedAt IS NOT NULL
    """
    )
    fun artistsBookmarkedByPlayTimeAsc(): Flow<List<Artist>>

    fun artists(sortType: ArtistSortType, descending: Boolean) =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsByCreateDateAsc()
            ArtistSortType.NAME -> artistsByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsByPlayTimeAsc()
        }.map { artists ->
            artists
                .filter { it.artist.isYouTubeArtist || it.artist.isLocal } 
                .reversed(descending)
        }

    fun artistsBookmarked(sortType: ArtistSortType, descending: Boolean) =
        when (sortType) {
            ArtistSortType.CREATE_DATE -> artistsBookmarkedByCreateDateAsc()
            ArtistSortType.NAME -> artistsBookmarkedByNameAsc()
            ArtistSortType.SONG_COUNT -> artistsBookmarkedBySongCountAsc()
            ArtistSortType.PLAY_TIME -> artistsBookmarkedByPlayTimeAsc()
        }.map { artists ->
            artists
                .filter { it.artist.isYouTubeArtist || it.artist.isLocal } 
                .reversed(descending)
        }

    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND song.inLibrary IS NOT NULL) AS songCount FROM artist WHERE id = :id")
    fun artist(id: String): Flow<Artist?>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY rowId")
    fun albumsByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY title")
    fun albumsByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY year")
    fun albumsByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY songCount")
    fun albumsBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL) ORDER BY duration")
    fun albumsByLengthAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND song.inLibrary IS NOT NULL)
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """,
    )
    fun albumsByPlayTimeAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun albumsLikedByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY title")
    fun albumsLikedByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY year")
    fun albumsLikedByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun albumsLikedBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY duration")
    fun albumsLikedByLengthAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE bookmarkedAt IS NOT NULL
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """
    )
    fun albumsLikedByPlayTimeAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE isUploaded = 1 ORDER BY rowId")
    fun albumsUploadedByCreateDateAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY title")
    fun albumsUploadedByNameAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY year")
    fun albumsUploadedByYearAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun albumsUploadedBySongCountAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY duration")
    fun albumsUploadedByLengthAsc(): Flow<List<Album>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        """
        SELECT album.*
        FROM album
                 JOIN song
                      ON song.albumId = album.id
        WHERE bookmarkedAt IS NOT NULL
        GROUP BY album.id
        ORDER BY SUM(song.totalPlayTime)
    """
    )
    fun albumsUploadedByPlayTimeAsc(): Flow<List<Album>>

    fun albums(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsByCreateDateAsc()
        AlbumSortType.NAME ->
            albumsByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }

        AlbumSortType.ARTIST ->
            albumsByCreateDateAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { it.name } })
            }

        AlbumSortType.YEAR -> albumsByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsBySongCountAsc()
        AlbumSortType.LENGTH -> albumsByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    fun albumsLiked(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsLikedByCreateDateAsc()
        AlbumSortType.NAME ->
            albumsLikedByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }

        AlbumSortType.ARTIST ->
            albumsLikedByCreateDateAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { it.name } })
            }

        AlbumSortType.YEAR -> albumsLikedByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsLikedBySongCountAsc()
        AlbumSortType.LENGTH -> albumsLikedByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsLikedByPlayTimeAsc()
    }.map { it.reversed(descending) }

    fun albumsUploaded(
        sortType: AlbumSortType,
        descending: Boolean,
    ) = when (sortType) {
        AlbumSortType.CREATE_DATE -> albumsUploadedByCreateDateAsc()
        AlbumSortType.NAME ->
            albumsUploadedByNameAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { it.album.title })
            }

        AlbumSortType.ARTIST ->
            albumsUploadedByCreateDateAsc().map { albums ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                albums.sortedWith(compareBy(collator) { album -> album.artists.joinToString("") { it.name } })
            }

        AlbumSortType.YEAR -> albumsUploadedByYearAsc()
        AlbumSortType.SONG_COUNT -> albumsUploadedBySongCountAsc()
        AlbumSortType.LENGTH -> albumsUploadedByLengthAsc()
        AlbumSortType.PLAY_TIME -> albumsUploadedByPlayTimeAsc()
    }.map { it.reversed(descending) }

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id = :id")
    fun album(id: String): Flow<Album?>

    @Transaction
    @Query("SELECT * FROM album WHERE id = :albumId")
    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?>

    @Transaction
    @Query("SELECT * FROM album_artist_map WHERE albumId = :albumId")
    fun albumArtistMaps(albumId: String): List<AlbumArtistMap>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun playlistsByCreateDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY lastUpdateTime",
    )
    fun playlistsByUpdatedDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY name")
    fun playlistsByNameAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE isEditable AND bookmarkedAt IS NOT NULL ORDER BY name")
    fun editablePlaylistsByNameAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE bookmarkedAt IS NOT NULL ORDER BY songCount")
    fun playlistsBySongCountAsc(): Flow<List<Playlist>>

    fun playlists(
        sortType: PlaylistSortType,
        descending: Boolean,
    ) = when (sortType) {
        PlaylistSortType.CREATE_DATE -> playlistsByCreateDateAsc()
        PlaylistSortType.NAME ->
            playlistsByNameAsc().map { playlists ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                playlists.sortedWith(compareBy(collator) { it.playlist.name })
            }

        PlaylistSortType.SONG_COUNT -> playlistsBySongCountAsc()
        PlaylistSortType.LAST_UPDATED -> playlistsByUpdatedDateAsc()
    }.map { list ->
        val reversedList = list.reversed(descending)
        reversedList.filter { it.playlist.isPinned } + reversedList.filterNot { it.playlist.isPinned }
    }

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId")
    fun playlist(playlistId: String): Flow<Playlist?>
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): Playlist?

    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE id = :playlistId")
    fun getPlaylistByIdBlocking(playlistId: String): Playlist?

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE isEditable AND bookmarkedAt IS NOT NULL ORDER BY rowId")
    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE browseId = :browseId")
    fun playlistByBrowseId(browseId: String): Flow<Playlist?>

    @Transaction
    @Query("SELECT COUNT(*) from playlist_song_map WHERE playlistId = :playlistId AND songId = :songId LIMIT 1")
    fun checkInPlaylist(
        playlistId: String,
        songId: String,
    ): Int

    @Query("SELECT songId from playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    fun playlistDuplicates(
        playlistId: String,
        songIds: List<String>,
    ): List<String>

    @Transaction
    fun addSongToPlaylist(playlist: Playlist, songIds: List<String>) {
        var position = playlist.songCount
        songIds.forEach { id ->
            insert(
                PlaylistSongMap(
                    songId = id,
                    playlistId = playlist.id,
                    position = position++
                )
            )
        }
    }

    fun downloadedSongs(
        sortType: SongSortType,
        descending: Boolean
    ): Flow<List<Song>> = when (sortType) {
        SongSortType.CREATE_DATE -> downloadedSongsByCreateDateAsc()
        SongSortType.NAME -> downloadedSongsByNameAsc().map { songs ->
            val collator = Collator.getInstance(Locale.getDefault())
            collator.strength = Collator.PRIMARY
            songs.sortedWith(compareBy(collator) { it.song.title })
        }

        SongSortType.ARTIST -> downloadedSongsByNameAsc().map { songs ->
            val collator = Collator.getInstance(Locale.getDefault())
            collator.strength = Collator.PRIMARY
            songs.sortedWith(compareBy(collator) { song ->
                song.artists.joinToString("") { it.name }
            })
        }

        SongSortType.PLAY_TIME -> downloadedSongsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    /**
     * Counted plays of every song, per month. Read once per Smart Shuffle, which keeps the
     * songs of the collection being shuffled (a playlist can hold more songs than a query
     * may bind, so they are filtered in memory rather than in SQL).
     */
    @Query(
        """
        SELECT playCount.song AS songId, playCount.year AS year, playCount.month AS month, playCount.count AS count
        FROM playCount
        WHERE playCount.count > 0
        """
    )
    fun songMonthPlays(): List<SongMonthPlays>

    /** The last counted play of each song that has one. Read once per Smart Shuffle. */
    @Query(
        """
        SELECT event.songId AS songId, MAX(event.timestamp) AS lastPlayed
        FROM event
        GROUP BY event.songId
        """
    )
    fun songLastPlayed(): List<SongLastPlayed>

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded = 1 ORDER BY dateDownload")
    fun downloadedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded = 1 ORDER BY title")
    fun downloadedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isDownloaded = 1 ORDER BY totalPlayTime")
    fun downloadedSongsByPlayTimeAsc(): Flow<List<Song>>

    @Query("UPDATE song SET isDownloaded = :downloaded, dateDownload = :date WHERE id = :songId")
    fun updateDownloadedInfo(songId: String, downloaded: Boolean, date: LocalDateTime?)

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY dateDownload")
    fun uploadedSongsByCreateDateAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY title")
    fun uploadedSongsByNameAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY totalPlayTime")
    fun uploadedSongsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM song WHERE isUploaded = 1 ORDER BY rowId")
    fun uploadedSongsByRowIdAsc(): Flow<List<Song>>

    fun uploadedSongs(
        sortType: SongSortType,
        descending: Boolean,
    ) = when (sortType) {
        SongSortType.CREATE_DATE -> uploadedSongsByCreateDateAsc()
        SongSortType.NAME ->
            uploadedSongsByNameAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs.sortedWith(compareBy(collator) { it.song.title })
            }

        SongSortType.ARTIST ->
            uploadedSongsByRowIdAsc().map { songs ->
                val collator = Collator.getInstance(Locale.getDefault())
                collator.strength = Collator.PRIMARY
                songs
                    .sortedWith(
                        compareBy(collator) { song ->
                            song.artists.joinToString("") { it.name }
                        },
                    ).groupBy { it.album?.title }
                    .flatMap { (_, songsByAlbum) ->
                        songsByAlbum.sortedBy { album ->
                            album.artists.joinToString(
                                "",
                            ) { it.name }
                        }
                    }
            }

        SongSortType.PLAY_TIME -> uploadedSongsByPlayTimeAsc()
    }.map { it.reversed(descending) }

    /**
     * Songs the listener owns, by title.
     *
     * "Owns" is saved *or* downloaded *or* on the device, the same three ways
     * [homeRecentlyAdded] counts a song as theirs. `inLibrary` alone missed both of the
     * offline cases — the local scanner writes `inLibrary = null` — so a file on the phone
     * could never be found by searching for it.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM song
        WHERE title LIKE '%' || :query || '%'
          AND (inLibrary IS NOT NULL OR dateDownload IS NOT NULL OR isLocal = 1)
        LIMIT :previewSize
        """
    )
    fun searchSongs(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>>

    /**
     * Songs whose cached lyrics contain [query] — finding a song from a line of it.
     *
     * Only lyrics Shiny already holds are searched; nothing is fetched. Songs the listener
     * does not own are excluded, so this never turns into a back door onto the whole
     * `song` table for tracks that merely passed through a queue.
     */
    @Transaction
    @Query(
        """
        SELECT song.* FROM song
        JOIN lyrics ON lyrics.id = song.id
        WHERE lyrics.lyrics LIKE '%' || :query || '%'
          AND lyrics.lyrics != 'LYRICS_NOT_FOUND'
          AND (song.inLibrary IS NOT NULL OR song.dateDownload IS NOT NULL OR song.isLocal = 1)
        LIMIT :previewSize
        """
    )
    fun searchLyrics(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Song>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT *, (SELECT COUNT(1) FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = artist.id AND (song.inLibrary IS NOT NULL OR song.dateDownload IS NOT NULL OR song.isLocal = 1)) AS songCount FROM artist WHERE name LIKE '%' || :query || '%' AND songCount > 0 LIMIT :previewSize",
    )
    fun searchArtists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Artist>>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query(
        "SELECT * FROM album WHERE title LIKE '%' || :query || '%' AND EXISTS(SELECT * FROM song WHERE song.albumId = album.id AND (song.inLibrary IS NOT NULL OR song.dateDownload IS NOT NULL OR song.isLocal = 1)) LIMIT :previewSize",
    )
    fun searchAlbums(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Album>>

    @Transaction
    @Query(
        "SELECT *, (SELECT COUNT(*) FROM playlist_song_map WHERE playlistId = playlist.id) AS songCount FROM playlist WHERE name LIKE '%' || :query || '%' LIMIT :previewSize",
    )
    fun searchPlaylists(
        query: String,
        previewSize: Int = Int.MAX_VALUE,
    ): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId DESC")
    fun events(): Flow<List<EventWithSong>>

    @Transaction
    @Query("SELECT * FROM event ORDER BY rowId ASC LIMIT 1")
    fun firstEvent(): Flow<EventWithSong?>

    @Query("SELECT COUNT(*) FROM event")
    fun eventCount(): Flow<Int>

    @Transaction
    @Query("DELETE FROM event")
    fun clearListenHistory()

    @Transaction
    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Transaction
    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    
    @Transaction
    @Query("SELECT * FROM recognition_history ORDER BY recognizedAt DESC")
    fun recognitionHistory(): Flow<List<RecognitionHistory>>

    @Transaction
    @Query("SELECT * FROM recognition_history WHERE id = :id")
    fun recognitionHistoryById(id: Long): Flow<RecognitionHistory?>

    @Transaction
    @Query("SELECT * FROM recognition_history WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY recognizedAt DESC")
    fun searchRecognitionHistory(query: String): Flow<List<RecognitionHistory>>

    @Transaction
    @Query("DELETE FROM recognition_history")
    fun clearRecognitionHistory()

    @Transaction
    @Query("DELETE FROM recognition_history WHERE id = :id")
    fun deleteRecognitionHistoryById(id: Long)

    @Transaction
    @Query("UPDATE recognition_history SET liked = :liked WHERE id = :id")
    fun updateRecognitionHistoryLiked(id: Long, liked: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(recognitionHistory: RecognitionHistory): Long

    @Delete
    fun delete(recognitionHistory: RecognitionHistory)

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + :playTime WHERE id = :songId")
    fun incrementTotalPlayTime(songId: String, playTime: Long)

    @Query("UPDATE playCount SET count = count + 1 WHERE song = :songId AND year = :year AND month = :month")
    fun incrementPlayCount(songId: String, year: Int, month: Int)

    
    fun incrementPlayCount(songId: String) {
        val time = LocalDateTime.now().atOffset(ZoneOffset.UTC)
        var oldCount: Int
        runBlocking {
            oldCount = getPlayCountByMonth(songId, time.year, time.monthValue).first()
        }

        
        if (oldCount <= 0) {
            insert(PlayCountEntity(songId, time.year, time.monthValue, 0))
        }
        incrementPlayCount(songId, time.year, time.monthValue)
    }

    @Transaction
    @Query("UPDATE song SET inLibrary = :inLibrary WHERE id = :songId")
    fun inLibrary(
        songId: String,
        inLibrary: LocalDateTime?,
    )

    @Transaction
    @Query("UPDATE song SET libraryAddToken = :libraryAddToken, libraryRemoveToken = :libraryRemoveToken WHERE id = :songId")
    fun addLibraryTokens(
        songId: String,
        libraryAddToken: String?,
        libraryRemoveToken: String?,
    )

    @Transaction
    @Query("SELECT COUNT(1) FROM related_song_map WHERE songId = :songId LIMIT 1")
    fun hasRelatedSongs(songId: String): Boolean

    @Transaction
    @Query(
        "SELECT song.* FROM (SELECT * from related_song_map GROUP BY relatedSongId) map JOIN song ON song.id = map.relatedSongId where songId = :songId",
    )
    fun getRelatedSongs(songId: String): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT *
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN
             song
             ON song.id = map.relatedSongId
        WHERE songId = :songId
        """
    )
    fun relatedSongs(songId: String): List<Song>

    @Transaction
    @Query(
        """
        UPDATE playlist_song_map SET position =
            CASE
                WHEN position < :fromPosition THEN position + 1
                WHEN position > :fromPosition THEN position - 1
                ELSE :toPosition
            END
        WHERE playlistId = :playlistId AND position BETWEEN MIN(:fromPosition, :toPosition) AND MAX(:fromPosition, :toPosition)
    """,
    )
    fun move(
        playlistId: String,
        fromPosition: Int,
        toPosition: Int,
    )

    @Transaction
    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    fun clearPlaylist(playlistId: String)

    @Transaction
    @Query("SELECT * FROM artist WHERE name = :name")
    fun artistByName(name: String): ArtistEntity?

    @Query("SELECT * FROM artist WHERE id = :id LIMIT 1")
    fun getArtistById(id: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertInternal(map: SongArtistMap)
    fun insert(map: SongArtistMap) { try { insertInternal(map) } catch (e: Exception) { e.printStackTrace() } }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertInternal(map: SongAlbumMap)
    fun insert(map: SongAlbumMap) { try { insertInternal(map) } catch (e: Exception) { e.printStackTrace() } }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertInternal(map: AlbumArtistMap)
    fun insert(map: AlbumArtistMap) { try { insertInternal(map) } catch (e: Exception) { e.printStackTrace() } }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertInternal(map: PlaylistSongMap)
    fun insert(map: PlaylistSongMap) { try { insertInternal(map) } catch (e: Exception) { e.printStackTrace() } }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchHistory: SearchHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertInternal(map: RelatedSongMap)
    fun insert(map: RelatedSongMap) { try { insertInternal(map) } catch (e: Exception) { e.printStackTrace() } }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playCountEntity: PlayCountEntity): Long

    @Transaction
    fun insert(
        mediaMetadata: MediaMetadata,
        block: (SongEntity) -> SongEntity = { it },
    ) {
        if (insert(mediaMetadata.toSongEntity().let(block)) == -1L) return

        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()

            insert(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                )
            )

            insert(
                SongArtistMap(
                    songId = mediaMetadata.id,
                    artistId = artistId,
                    position = index,
                )
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        if (insert(
                AlbumEntity(
                    id = albumPage.album.browseId,
                    playlistId = albumPage.album.playlistId,
                    title = albumPage.album.title,
                    year = albumPage.album.year,
                    thumbnailUrl = albumPage.album.thumbnail,
                    songCount = albumPage.songs.size,
                    duration = albumPage.songs.sumOf { it.duration ?: 0 },
                    explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
                    description = albumPage.description,
                ),
            ) == -1L
        ) {
            return
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlocking(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id
                    ?: ArtistEntity.generateArtistId(),
                    name = artist.name,
                )
            }?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index,
                )
            }?.forEach(::insert)
    }

    @Transaction
    fun update(
        song: Song,
        mediaMetadata: MediaMetadata,
    ) {
        update(
            song.song.copy(
                title = mediaMetadata.title,
                duration = mediaMetadata.duration,
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumId = mediaMetadata.album?.id,
                albumName = mediaMetadata.album?.title,
                libraryAddToken = mediaMetadata.libraryAddToken,
                libraryRemoveToken = mediaMetadata.libraryRemoveToken
            ),
        )
        songArtistMap(song.id).forEach(::delete)
        mediaMetadata.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()

            insert(
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    channelId = artist.id,
                ),
            )
            insert(
                SongArtistMap(
                    songId = song.id,
                    artistId = artistId,
                    position = index,
                ),
            )
        }
    }

    @Update
    fun update(song: SongEntity)

    @Update
    fun update(artist: ArtistEntity)

    @Update
    fun update(album: AlbumEntity)

    @Update
    fun update(playlist: PlaylistEntity)

    @Update
    fun update(map: PlaylistSongMap)

    @Transaction
    fun update(
        artist: ArtistEntity,
        artistPage: ArtistPage
    ) {
        update(
            artist.copy(
                name = artistPage.artist.title,
                thumbnailUrl = artistPage.artist.thumbnail?.resize(544, 544),
                lastUpdateTime = LocalDateTime.now()
            )
        )
    }

    @Transaction
    fun update(
        album: AlbumEntity,
        albumPage: AlbumPage,
        artists: List<ArtistEntity>? = emptyList(),
    ) {
        update(
            album.copy(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { it.duration ?: 0 },
                explicit = albumPage.album.explicit || albumPage.songs.any { it.explicit },
                description = albumPage.description ?: album.description,
            ),
        )
        if (artists?.size != albumPage.album.artists?.size) {
            artists?.forEach(::delete)
        }
        albumPage.songs
            .map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .onEach {
                val existingSong = getSongByIdBlocking(it.id)
                if (existingSong != null) {
                    update(existingSong, it)
                }
            }.mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index,
                )
            }.forEach(::upsert)

        albumPage.album.artists?.let { artists ->
            
            albumArtistMaps(album.id).forEach(::delete)
            artists
                .map { artist ->
                    ArtistEntity(
                        id = artist.id ?: artistByName(artist.name)?.id
                        ?: ArtistEntity.generateArtistId(),
                        name = artist.name,
                    )
                }.onEach(::insert)
                .mapIndexed { index, artist ->
                    AlbumArtistMap(
                        albumId = albumPage.album.browseId,
                        artistId = artist.id,
                        order = index,
                    )
                }.forEach(::insert)
        }
    }

    @Update
    fun update(playlistEntity: PlaylistEntity, playlistItem: PlaylistItem) {
        update(
            playlistEntity.copy(
                name = playlistItem.title,
                browseId = playlistItem.id,
                thumbnailUrl = playlistItem.thumbnail,
                isEditable = playlistItem.isEditable,
                remoteSongCount = playlistItem.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
                playEndpointParams = playlistItem.playEndpoint?.params,
                shuffleEndpointParams = playlistItem.shuffleEndpoint?.params,
                radioEndpointParams = playlistItem.radioEndpoint?.params
            )
        )
    }

    @Upsert
    fun upsert(map: SongAlbumMap)

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Upsert
    fun upsert(format: FormatEntity)

    @Query("DELETE FROM format WHERE id = :id")
    fun deleteFormat(id: String)

    @Upsert
    fun upsert(song: SongEntity)

    @Delete
    fun delete(song: SongEntity)

    @Delete
    fun delete(songArtistMap: SongArtistMap)

    @Delete
    fun delete(artist: ArtistEntity)

    @Delete
    fun delete(album: AlbumEntity)

    @Delete
    fun delete(albumArtistMap: AlbumArtistMap)

    @Delete
    fun delete(playlist: PlaylistEntity)

    @Delete
    fun delete(playlistSongMap: PlaylistSongMap)

    @Query("DELETE FROM playlist WHERE browseId = :browseId")
    fun deletePlaylistById(browseId: String)

    /** The hidden playlists mirrored from Spotify (mixes, top tracks); any saved to the Library stay. */
    @Query("DELETE FROM playlist WHERE bookmarkedAt IS NULL AND (id LIKE 'SPOTIFY_MIX_%' OR id LIKE 'SPOTIFY_TOP_%')")
    suspend fun deleteHiddenSpotifyPlaylists()

    @Delete
    fun delete(lyrics: LyricsEntity)

    @Delete
    fun delete(searchHistory: SearchHistory)

    @Delete
    fun delete(event: Event)

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE songId = :songId")
    fun playlistSongMaps(songId: String): List<PlaylistSongMap>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId AND position >= :from ORDER BY position")
    fun playlistSongMaps(
        playlistId: String,
        from: Int,
    ): List<PlaylistSongMap>

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }

    @Transaction
    @Query("SELECT * FROM song WHERE isLocal = 1 ORDER BY title COLLATE NOCASE, id")
    fun localSongs(): Flow<List<Song>>

    @Query("SELECT id FROM song WHERE isLocal = 1")
    suspend fun localSongIds(): List<String>

    /**
     * Which of [ids] carry something of the user's that deleting the row would destroy:
     * a like, a library save, a download, a place in a playlist, or play history (both of
     * the last two cascade on delete).
     */
    @Query(
        """
        SELECT id FROM song
        WHERE id IN (:ids) AND (
            liked = 1
            OR inLibrary IS NOT NULL
            OR dateDownload IS NOT NULL
            OR EXISTS (SELECT 1 FROM playlist_song_map WHERE playlist_song_map.songId = song.id)
            OR EXISTS (SELECT 1 FROM event WHERE event.songId = song.id)
        )
        """
    )
    suspend fun referencedSongIds(ids: List<String>): List<String>

    @Query("DELETE FROM song WHERE isLocal = 1")
    fun clearLocalSongs()

    @Query("DELETE FROM album WHERE isLocal = 1 AND id NOT IN (SELECT DISTINCT albumId FROM song WHERE isLocal = 1 AND albumId IS NOT NULL)")
    fun pruneLocalAlbums()

    @Query("DELETE FROM artist WHERE isLocal = 1 AND id NOT IN (SELECT DISTINCT song_artist_map.artistId FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE song.isLocal = 1)")
    fun pruneLocalArtists()

    @Query("SELECT * FROM artist WHERE id IN (:ids)")
    suspend fun getArtistEntitiesByIds(ids: List<String>): List<ArtistEntity>

    @Query("SELECT * FROM album WHERE id IN (:ids)")
    suspend fun getAlbumEntitiesByIds(ids: List<String>): List<AlbumEntity>

    @Query("DELETE FROM song_album_map WHERE songId = :songId")
    fun deleteSongAlbumMaps(songId: String)

    @Query("DELETE FROM format WHERE id NOT IN (SELECT id FROM song)")
    fun pruneFormats()

    @Query("DELETE FROM playCount WHERE song NOT IN (SELECT id FROM song)")
    fun prunePlayCounts()

    /** Of [ids], the songs that have any listening history. */
    @Query("SELECT DISTINCT songId FROM event WHERE songId IN (:ids)")
    suspend fun songIdsWithEvents(ids: List<String>): List<String>

    // Moving a song's listening history to another id: a local file that was moved or renamed
    // comes back from MediaStore under a new id, and deleting the old row cascades its events.
    @Query("UPDATE event SET songId = :toId WHERE songId = :fromId")
    fun moveEvents(fromId: String, toId: String)

    @Query("UPDATE OR IGNORE playCount SET song = :toId WHERE song = :fromId")
    fun movePlayCounts(fromId: String, toId: String)

    @Query("UPDATE song SET totalPlayTime = totalPlayTime + IFNULL((SELECT totalPlayTime FROM song WHERE id = :fromId), 0) WHERE id = :toId")
    fun addTotalPlayTimeFrom(fromId: String, toId: String)

    @Query("DELETE FROM song WHERE id IN (:songIds)")
    fun deleteSongsByIds(songIds: List<String>)

    @Query("DELETE FROM song_artist_map WHERE songId = :songId")
    fun deleteSongArtistMaps(songId: String)

    @Query("DELETE FROM album_artist_map WHERE albumId IN (:albumIds)")
    fun deleteAlbumArtistMapsByAlbumIds(albumIds: List<String>)

    // ---- Home ------------------------------------------------------------------------------
    // Aggregates only: Home reads the history once per rebuild, ranks in memory, and loads
    // full rows for what it shows. Timestamps are wall time stored as UTC (see HomeStats.kt).

    /** Every song ever counted, with its plays overall, this week, this month and at this time of day. */
    @Query(
        """
        SELECT songId,
               COUNT(*) AS plays,
               SUM(playTime) AS playTime,
               MIN(timestamp) AS firstPlayed,
               MAX(timestamp) AS lastPlayed,
               SUM(CASE WHEN timestamp > :weekFrom THEN 1 ELSE 0 END) AS weekPlays,
               SUM(CASE WHEN timestamp > :weekFrom THEN playTime ELSE 0 END) AS weekPlayTime,
               SUM(CASE WHEN timestamp > :monthFrom THEN 1 ELSE 0 END) AS monthPlays,
               SUM(CASE WHEN timestamp > :daypartFrom AND (timestamp / 3600000) % 24 IN (:daypartHours) THEN 1 ELSE 0 END) AS daypartPlays
        FROM event
        GROUP BY songId
        """
    )
    suspend fun homeSongStats(
        weekFrom: Long,
        monthFrom: Long,
        daypartFrom: Long,
        daypartHours: List<Int>,
    ): List<HomeSongStat>

    /** On how many different days anything was played in these hours since [from]. */
    @Query("SELECT COUNT(DISTINCT timestamp / 86400000) FROM event WHERE timestamp > :from AND (timestamp / 3600000) % 24 IN (:hours)")
    suspend fun homeDaypartDays(from: Long, hours: List<Int>): Int

    @Query(
        """
        SELECT sam.artistId AS artistId,
               COUNT(*) AS plays,
               SUM(CASE WHEN e.timestamp > :weekFrom THEN 1 ELSE 0 END) AS weekPlays,
               SUM(CASE WHEN e.timestamp > :monthFrom THEN 1 ELSE 0 END) AS monthPlays,
               MAX(e.timestamp) AS lastPlayed,
               COUNT(DISTINCT e.songId) AS songs
        FROM event e JOIN song_artist_map sam ON sam.songId = e.songId
        GROUP BY sam.artistId
        ORDER BY lastPlayed DESC
        LIMIT :limit
        """
    )
    suspend fun homeArtistStats(weekFrom: Long, monthFrom: Long, limit: Int): List<HomeArtistStat>

    @Query(
        """
        SELECT s.albumId AS albumId,
               COUNT(DISTINCT e.songId) AS songsPlayed,
               COUNT(*) AS plays,
               MAX(e.timestamp) AS lastPlayed
        FROM event e JOIN song s ON s.id = e.songId
        WHERE s.albumId IS NOT NULL
        GROUP BY s.albumId
        ORDER BY lastPlayed DESC
        LIMIT :limit
        """
    )
    suspend fun homeAlbumStats(limit: Int): List<HomeAlbumStat>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT * FROM album WHERE id IN (:ids)")
    suspend fun homeAlbums(ids: List<String>): List<Album>

    @Transaction
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, 0 AS songCount FROM artist WHERE id IN (:ids)")
    suspend fun homeArtists(ids: List<String>): List<Artist>

    /** Of the plays since [from], how many were of songs that play without a network. */
    @Query(
        """
        SELECT IFNULL(SUM(CASE WHEN s.isLocal = 1 OR s.isDownloaded = 1 THEN 1 ELSE 0 END), 0)
        FROM event e JOIN song s ON s.id = e.songId
        WHERE e.timestamp > :from
        """
    )
    suspend fun homeOfflinePlays(from: Long): Int

    /**
     * Streamable songs the listener chose: liked first, then the most recently saved. A
     * playlist counts when it is in the Library, or is one of the Spotify playlists Shiny keeps
     * hidden: the mixes (`SPOTIFY_MIX_…`), Spotify's own reading of the listener's taste, and
     * their most played tracks (`SPOTIFY_TOP_…`).
     */
    @Query(
        """
        SELECT s.id AS songId,
               s.liked AS liked,
               (s.inLibrary IS NOT NULL) AS inLibrary,
               EXISTS (SELECT 1 FROM playlist_song_map psm JOIN playlist p ON p.id = psm.playlistId
                       WHERE psm.songId = s.id AND p.bookmarkedAt IS NOT NULL) AS inPlaylist,
               EXISTS (SELECT 1 FROM playlist_song_map psm
                       WHERE psm.songId = s.id AND psm.playlistId LIKE 'SPOTIFY_MIX_%') AS inMix,
               EXISTS (SELECT 1 FROM playlist_song_map psm
                       WHERE psm.songId = s.id AND psm.playlistId LIKE 'SPOTIFY_TOP_%') AS inTop
        FROM song s
        WHERE s.isLocal = 0
          AND (s.liked = 1 OR s.inLibrary IS NOT NULL OR s.id IN (
                SELECT psm.songId FROM playlist_song_map psm JOIN playlist p ON p.id = psm.playlistId
                WHERE p.bookmarkedAt IS NOT NULL OR p.id LIKE 'SPOTIFY_MIX_%' OR p.id LIKE 'SPOTIFY_TOP_%'))
        ORDER BY s.liked DESC, COALESCE(s.likedDate, s.inLibrary) DESC
        LIMIT :limit
        """
    )
    suspend fun homeSavedSongs(limit: Int): List<HomeSavedSong>

    /** Artists across everything the listener chose, device files and downloads included. */
    @Query(
        """
        SELECT sam.artistId AS artistId,
               a.name AS name,
               COUNT(DISTINCT s.id) AS songs,
               SUM(CASE WHEN s.liked = 1 THEN 1 ELSE 0 END) AS liked
        FROM song s
        JOIN song_artist_map sam ON sam.songId = s.id
        JOIN artist a ON a.id = sam.artistId
        WHERE s.liked = 1 OR s.inLibrary IS NOT NULL OR s.isLocal = 1 OR s.isDownloaded = 1
           OR s.id IN (
                SELECT psm.songId FROM playlist_song_map psm JOIN playlist p ON p.id = psm.playlistId
                WHERE p.bookmarkedAt IS NOT NULL OR p.id LIKE 'SPOTIFY_MIX_%' OR p.id LIKE 'SPOTIFY_TOP_%')
        GROUP BY sam.artistId
        ORDER BY SUM(CASE WHEN s.liked = 1 THEN 1 ELSE 0 END) * 2 + COUNT(DISTINCT s.id) DESC
        LIMIT :limit
        """
    )
    suspend fun homeSavedArtists(limit: Int): List<HomeSavedArtist>

    /** Songs related to [seedIds] that have never been counted as played. */
    @Query(
        """
        SELECT songId AS seedId, relatedSongId AS songId
        FROM related_song_map
        WHERE songId IN (:seedIds)
          AND relatedSongId NOT IN (SELECT DISTINCT songId FROM event)
        """
    )
    suspend fun homeRelatedLinks(seedIds: List<String>): List<HomeRelatedLink>

    /** Songs most recently saved, downloaded or found on the device. */
    @Transaction
    @Query(
        """
        SELECT * FROM song
        WHERE inLibrary IS NOT NULL OR dateDownload IS NOT NULL OR isLocal = 1
        ORDER BY MAX(IFNULL(inLibrary, 0), IFNULL(dateDownload, 0), CASE WHEN isLocal = 1 THEN IFNULL(dateModified, 0) ELSE 0 END) DESC
        LIMIT :limit
        """
    )
    suspend fun homeRecentlyAdded(limit: Int): List<Song>

    /** Covers of the most recently liked songs, for Home's Liked Songs tile. */
    @Query("SELECT thumbnailUrl FROM song WHERE liked = 1 AND thumbnailUrl IS NOT NULL ORDER BY likedDate DESC LIMIT :limit")
    suspend fun homeLikedCovers(limit: Int): List<String>

    /** Songs that play without a network, most listened first. */
    @Transaction
    @Query("SELECT * FROM song WHERE isLocal = 1 OR isDownloaded = 1 ORDER BY totalPlayTime DESC LIMIT :limit")
    suspend fun homeOfflineSongs(limit: Int): List<Song>

    /**
     * The user's own songs (saved, liked, downloaded or on the device) played at most once,
     * in an order fixed by [seed]: the same seed draws the same songs, a new seed new ones.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM song
        WHERE (inLibrary IS NOT NULL OR liked = 1 OR isLocal = 1 OR isDownloaded = 1)
          AND id NOT IN (SELECT songId FROM event GROUP BY songId HAVING COUNT(*) > 1)
        ORDER BY (rowid * :seed) % 1000003
        LIMIT :limit
        """
    )
    suspend fun homeUnexploredLibrary(seed: Long, limit: Int): List<Song>

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM song WHERE isLocal = 1) AS localSongs,
               (SELECT COUNT(*) FROM song WHERE isDownloaded = 1) AS downloadedSongs,
               (SELECT COUNT(*) FROM song WHERE liked = 1) AS likedSongs,
               (SELECT COUNT(*) FROM song WHERE inLibrary IS NOT NULL) AS librarySongs,
               (SELECT COUNT(*) FROM event) AS events,
               (SELECT COUNT(*) FROM playlist WHERE bookmarkedAt IS NOT NULL) AS playlists,
               (SELECT COUNT(*) FROM search_history) AS searches
        """
    )
    suspend fun homeLibraryCounts(): HomeLibraryCounts

    /**
     * Changes whenever something Home is built from changes: a counted play, a like, a
     * download, a device scan, a save, a playlist, a library playlist's songs changing (the
     * daily re-synced Spotify Liked Songs). Cheap to re-run on every table write.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM event) || ':' ||
               (SELECT COUNT(*) FROM song WHERE liked = 1) || ':' ||
               (SELECT COUNT(*) FROM song WHERE isLocal = 1 OR isDownloaded = 1) || ':' ||
               (SELECT COUNT(*) FROM song WHERE inLibrary IS NOT NULL) || ':' ||
               (SELECT COUNT(*) FROM playlist WHERE bookmarkedAt IS NOT NULL) || ':' ||
               IFNULL((SELECT MAX(lastUpdateTime) FROM playlist WHERE bookmarkedAt IS NOT NULL), 0)
        """
    )
    fun homeInvalidation(): Flow<String>
}
