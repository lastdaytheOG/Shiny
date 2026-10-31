package com.shiny.music.intelligence

import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiny.music.constants.PauseListenHistoryKey
import com.shiny.music.constants.PersonalisedRecommendationsKey
import com.shiny.music.constants.RecommendationStyleKey
import com.shiny.music.db.InternalDatabase
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotFreshnessTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "snapshot-freshness-test.db"
    private lateinit var room: InternalDatabase
    private lateinit var database: MusicDatabase
    private lateinit var repository: IntelligenceRepository
    private val db get() = database.openHelper.writableDatabase

    @Before
    fun open() = runBlocking {
        context.dataStore.edit {
            it[PersonalisedRecommendationsKey] = true
            it[PauseListenHistoryKey] = false
            it.remove(RecommendationStyleKey)
        }
        context.deleteDatabase(name)
        room = Room.databaseBuilder(context, InternalDatabase::class.java, name).build()
        database = MusicDatabase(room)
        repository = IntelligenceRepository(context, database)
        listOf("a", "b", "c").forEach { id ->
            db.execSQL("INSERT INTO artist (id, name, lastUpdateTime, isLocal) VALUES ('UC$id', 'Artist $id', 0, 0)")
            db.execSQL("INSERT INTO song (id, title, duration, liked, totalPlayTime, isLocal, romanizeLyrics, isDownloaded, isUploaded, isVideo, lyricsOffset, explicit) VALUES ('$id', 'Song $id', 200, 0, 0, 0, 1, 0, 0, 0, 0, 0)")
            db.execSQL("INSERT INTO song_artist_map (songId, artistId, position) VALUES ('$id', 'UC$id', 0)")
        }
    }

    @After
    fun close() {
        room.close()
        context.deleteDatabase(name)
    }

    private fun feedback(kind: FeedbackKind, target: String) =
        MusicFeedback(kind.targetType, target, kind.name, null, System.currentTimeMillis(), null)

    @Test
    fun swappingOneLikeForAnotherIsSeenAtOnce() = runBlocking {
        db.execSQL("UPDATE song SET liked = 1, likedDate = 1 WHERE id = 'a'")
        assertTrue(repository.snapshot().taste.song("a")?.saved == true)
        db.execSQL("UPDATE song SET liked = 0, likedDate = NULL WHERE id = 'a'")
        db.execSQL("UPDATE song SET liked = 1, likedDate = 1 WHERE id = 'b'")
        val after = repository.snapshot().taste
        assertFalse("the unliked song is still saved", after.song("a")?.saved == true)
        assertTrue("the newly liked song is not saved", after.song("b")?.saved == true)
    }

    @Test
    fun hidingADifferentArtistWithTheSameRowCountIsSeenAtOnce() = runBlocking {
        val dao = database.intelligenceDao
        dao.upsertFeedback(feedback(FeedbackKind.HideArtist, "UCa"))
        assertEquals(setOf("UCa"), repository.snapshot().taste.hiddenArtists)
        dao.deleteFeedback("artist", "UCa", FeedbackKind.HideArtist.name)
        dao.upsertFeedback(feedback(FeedbackKind.HideArtist, "UCb"))
        assertEquals(setOf("UCb"), repository.snapshot().taste.hiddenArtists)
    }

    @Test
    fun savingASongToTheLibraryIsSeenAtOnce() = runBlocking {
        assertFalse(repository.snapshot().taste.song("c")?.saved == true)
        db.execSQL("UPDATE song SET inLibrary = 1000 WHERE id = 'c'")
        assertTrue(repository.snapshot().taste.song("c")?.saved == true)
    }

    @Test
    fun aSongInABookmarkedPlaylistIsSeenAtOnce() = runBlocking {
        assertFalse(repository.snapshot().taste.song("c")?.saved == true)
        db.execSQL("INSERT INTO playlist (id, name, isEditable, bookmarkedAt, isLocal, isAutoSync) VALUES ('p', 'P', 1, 1000, 0, 0)")
        db.execSQL("INSERT INTO playlist_song_map (playlistId, songId, position, setVideoId) VALUES ('p', 'c', 0, NULL)")
        assertTrue(repository.snapshot().taste.song("c")?.saved == true)
    }

    @Test
    fun callersAtTheSameMomentShareOneBuild() = runBlocking {
        val pending = List(4) { async(Dispatchers.IO) { repository.snapshot() } }
        val results = pending.map { it.await() }
        results.forEach { assertSame(results.first(), it) }
    }

    @Test
    fun aCancelledCallerDoesNotThrowTheBuildAway() = runBlocking {
        val first = launch(Dispatchers.IO) { repository.snapshot() }
        first.cancel()
        val second = repository.snapshot()
        assertSame(second, repository.snapshot())
    }

    @Test
    fun anUnchangedDatabaseKeepsTheSameSnapshot() = runBlocking {
        val first = repository.snapshot()
        assertSame(first, repository.snapshot())
    }

    @Test
    fun playbackBookkeepingOnSongRowsKeepsTheSameSnapshot() = runBlocking {
        val first = repository.snapshot()
        db.execSQL("UPDATE song SET totalPlayTime = totalPlayTime + 1000 WHERE id = 'a'")
        assertSame(first, repository.snapshot())
    }

    @Test
    fun aListenIsSeenAtOnce() = runBlocking {
        val first = repository.snapshot()
        val at = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO listen_outcome (songId, sessionId, startedAt, endedAt, zoneOffsetMinutes, listenedMs, durationMs, completion, endReason, kind, surface, selection, queuePosition, firstListen) " +
                "VALUES ('a', 1, $at, $at, 330, 190000, 200000, 0.95, 'Finished', 'Completed', 'Radio', 'Autoplay', 0, 1)",
        )
        val second = repository.snapshot()
        assertNotSame(first, second)
        assertTrue(second.taste.song("a")!!.positive > 0.0)
    }
}
