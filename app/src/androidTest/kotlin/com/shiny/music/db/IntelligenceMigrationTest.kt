package com.shiny.music.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.ListenOutcome
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.MusicRelation
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.db.entities.RecommendationImpression
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class IntelligenceMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "intelligence-migration-test.db"
    private val file: File get() = context.getDatabasePath(name)
    private var database: InternalDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(name)
    }

    private fun createVersion45() {
        file.parentFile?.mkdirs()
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("schema45.sql").bufferedReader().readLines()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.filter { it.isNotBlank() }.forEach(db::execSQL)
            db.execSQL("INSERT INTO song (id, title, duration, liked, totalPlayTime, isLocal, romanizeLyrics, isDownloaded, isUploaded, isVideo, lyricsOffset, explicit) VALUES ('kept', 'Kept song', 200, 1, 500, 0, 1, 0, 0, 0, 0, 0)")
            db.execSQL("INSERT INTO event (songId, timestamp, playTime) VALUES ('kept', 1800000000000, 190000)")
            db.execSQL("INSERT INTO beat_info (songId, bpm, firstBeatOffsetMs, confidence, analyzedAt) VALUES ('kept', 120.0, 10, 0.5, 1)")
            db.version = 45
        }
    }

    private fun open(): InternalDatabase = Room.databaseBuilder(context, InternalDatabase::class.java, name)
        .addMigrations(MIGRATION_45_46)
        .allowMainThreadQueries()
        .build()
        .also { database = it }

    @Test
    fun existingDataSurvivesAndNewTablesWork() {
        createVersion45()
        val db = open()
        val dao = db.intelligenceDao
        runBlocking {
            assertEquals(1, db.dao.homeLibraryCounts(hideExplicit = false, hideVideos = false).events)
            assertEquals(120f, dao.songFeatures().single().bpm)
            assertNull(dao.songFeatures().single().energy)

            val now = 1_800_000_100_000L
            dao.insertImpressions(
                listOf(RecommendationImpression(songId = "kept", surface = RecSurface.Radio.name, shownAt = now, position = 0, candidateSource = CandidateSource.YouTubeRadio.name, reason = null, sessionId = 1)),
            )
            val impression = dao.openImpression("kept", RecSurface.Radio.name, now - 1000)
            assertNotNull(impression)
            dao.markImpressionPlayed(impression!!, now, "Completed")
            assertNull(dao.openImpression("kept", RecSurface.Radio.name, now - 1000))

            dao.insertOutcome(
                ListenOutcome(songId = "kept", sessionId = 1, startedAt = now, endedAt = now + 1, zoneOffsetMinutes = 330, listenedMs = 190_000, durationMs = 200_000, completion = 0.95f, endReason = "Finished", kind = "Completed", surface = RecSurface.Radio.name, pickSource = CandidateSource.YouTubeRadio.name, selection = "Autoplay", queuePosition = 3, firstListen = false, replayGapDays = 2, impressionId = impression),
            )
            assertEquals(1, dao.songOutcomeBuckets(now, 86_400_000L * 14, 26).single().positive)
            assertEquals(1, dao.sourceOutcomeStats().single().positive)
            assertEquals(1, dao.surfaceListens(now - 1000).single().completed)
            assertEquals(1, dao.surfaceMetrics(now - 1000).single().played)

            dao.upsertFeedback(MusicFeedback("song", "kept", "BlockSong", "Kept song", now, null))
            assertEquals(1, dao.activeFeedback(now).size)
            dao.deleteFeedback("song", "kept", "BlockSong")
            assertEquals(0, dao.activeFeedback(now).size)

            dao.addRelationEvidence(MusicRelation("a", "b", "CoListen", 1f, 1, null, now))
            dao.addRelationEvidence(MusicRelation("a", "b", "CoListen", 1f, 1, null, now + 1))
            assertEquals(2f, dao.relationEdges(listOf("CoListen"), 10).single().weight)

            db.dao.clearListenHistory()
            dao.clearOutcomes()
            dao.clearImpressions()
            assertEquals(0, dao.songOutcomeBuckets(now, 86_400_000L * 14, 26).size)
        }
    }

    @Test
    fun migratingTwiceDoesNotFail() {
        createVersion45()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE beat_info ADD COLUMN energy REAL DEFAULT NULL")
        }
        val db = open()
        runBlocking { assertEquals(1, db.intelligenceDao.songFeatures().size) }
    }
}
