package com.shiny.music.intelligence

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiny.music.db.InternalDatabase
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.db.entities.RecommendationImpression
import com.shiny.music.extensions.toMediaItem
import com.shiny.music.models.MediaMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntelligenceRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "intelligence-repository-test.db"
    private lateinit var room: InternalDatabase
    private lateinit var database: MusicDatabase
    private lateinit var repository: IntelligenceRepository
    private val now = System.currentTimeMillis()

    @Before
    fun open() {
        context.deleteDatabase(name)
        room = Room.databaseBuilder(context, InternalDatabase::class.java, name).build()
        database = MusicDatabase(room)
        repository = IntelligenceRepository(context, database)
        val db = database.openHelper.writableDatabase
        listOf("a", "b", "c", "d").forEach { id ->
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

    private fun draft(
        song: String,
        kind: ListenKind,
        session: Long = 1L,
        surface: RecSurface = RecSurface.Radio,
        selection: Selection = Selection.Autoplay,
        startedAt: Long = now,
    ) = ListenDraft(
        track = ListenSessionTracker.Track(song, 200_000L, surface, CandidateSource.YouTubeRadio.name, selection, 0),
        sessionId = session,
        startedAt = startedAt,
        endedAt = startedAt + 1_000,
        listenedMs = if (kind.positive) 190_000L else 5_000L,
        completion = if (kind.positive) 0.95 else 0.02,
        reason = if (kind.positive) EndReason.Finished else EndReason.SkippedNext,
        kind = kind,
    )

    private suspend fun recordAndWait(vararg drafts: ListenDraft) {
        drafts.forEach { d ->
            val before = outcomeCount()
            repository.record(d)
            repeat(100) {
                if (outcomeCount() > before) return@forEach
                delay(20)
            }
        }
        delay(150)
    }

    private suspend fun outcomeCount() = database.intelligenceDao.discoveryOutcomes(0).totalListens +
        database.intelligenceDao.surfaceListens(0).sumOf { it.accidental + it.interrupted }

    private suspend fun relations(kind: String) =
        database.intelligenceDao.relationEdges(listOf(kind), 50).map { "${it.fromId}>${it.toId}" }

    @Test
    fun twoGoodListensInASessionConnectTheArtists() = runBlocking {
        recordAndWait(draft("a", ListenKind.Completed), draft("b", ListenKind.Meaningful))
        assertEquals(listOf("UCa>UCb"), relations("CoListen"))
    }

    @Test
    fun aDifferentSessionConnectsNothing() = runBlocking {
        recordAndWait(draft("a", ListenKind.Completed, session = 1), draft("b", ListenKind.Completed, session = 2))
        assertTrue(relations("CoListen").isEmpty())
    }

    @Test
    fun aSkipAfterAGoodListenIsABadTransition() = runBlocking {
        recordAndWait(draft("a", ListenKind.Completed), draft("b", ListenKind.Skipped))
        assertEquals(listOf("UCa>UCb"), relations(IntelligenceRepository.SKIP_AFTER))
        assertTrue(relations("CoListen").isEmpty())
    }

    @Test
    fun skippingASongYouChoseIsNotBlamedOnTheTransition() = runBlocking {
        recordAndWait(draft("a", ListenKind.Completed), draft("b", ListenKind.Skipped, selection = Selection.Manual))
        assertTrue(relations(IntelligenceRepository.SKIP_AFTER).isEmpty())
    }

    @Test
    fun anOutcomeIsLinkedToItsImpressionOnlyOnARecommendationSurface() = runBlocking {
        val dao = database.intelligenceDao
        dao.insertImpressions(
            listOf(
                RecommendationImpression(songId = "a", surface = RecSurface.Radio.name, shownAt = now - 1000, position = 0, candidateSource = null, reason = null, sessionId = 1),
                RecommendationImpression(songId = "b", surface = RecSurface.Radio.name, shownAt = now - 1000, position = 1, candidateSource = null, reason = null, sessionId = 1),
            ),
        )
        recordAndWait(
            draft("a", ListenKind.Completed),
            draft("b", ListenKind.Completed, surface = RecSurface.Direct, selection = Selection.Manual, startedAt = now + 5),
        )
        assertNull("the shown one was played", dao.openImpression("a", RecSurface.Radio.name, now - 5000))
        assertNotNull("a song the listener searched for is not credited to radio", dao.openImpression("b", RecSurface.Radio.name, now - 5000))
    }

    @Test
    fun aSongFromTheHistoryIsNotAFirstListen() = runBlocking {
        val offset = java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.ofEpochMilli(now)).totalSeconds * 1000L
        val threeDaysAgo = now + offset - 3 * 86_400_000L
        database.openHelper.writableDatabase.execSQL("INSERT INTO event (songId, timestamp, playTime) VALUES ('a', $threeDaysAgo, 190000)")
        recordAndWait(draft("a", ListenKind.Completed), draft("b", ListenKind.Completed))
        val stat = database.intelligenceDao.surfaceListens(0).single()
        assertEquals(1, stat.firstListens)
        assertEquals(1, stat.replays)
    }

    @Test
    fun aSongWithNoRowIsIgnoredWithoutACrash() = runBlocking {
        repository.record(draft("missing", ListenKind.Completed))
        delay(300)
        assertEquals(0, outcomeCount())
    }

    @Test
    fun clearingHistoryForgetsListeningButKeepsChoices() = runBlocking {
        repository.setFeedback(FeedbackKind.HideArtist, "UCd", "Artist d")
        recordAndWait(draft("a", ListenKind.Completed), draft("b", ListenKind.Completed))
        repository.clearLearned()
        delay(300)
        assertEquals(0, outcomeCount())
        assertTrue(relations("CoListen").isEmpty())
        assertEquals(1, database.intelligenceDao.activeFeedback(now).size)
    }

    private fun item(id: String) = MediaMetadata(
        id = id,
        title = "Song $id",
        artists = listOf(MediaMetadata.Artist(id = "UC$id", name = "Artist $id")),
        duration = 200,
    ).toMediaItem()

    @Test
    fun aHiddenArtistLeavesRadioAndUndoBringsThemBack() = runBlocking {
        val page = listOf("a", "b", "c", "d").map(::item)
        assertEquals(4, repository.rerankUpNext(null, page, RecSurface.Radio).size)

        repository.setFeedback(FeedbackKind.HideArtist, "UCb", "Artist b")
        delay(200)
        val hidden = repository.rerankUpNext(null, page, RecSurface.Radio).map { it.item.mediaId }
        assertFalse("b" in hidden)
        assertEquals(3, hidden.size)

        repository.removeFeedback(FeedbackKind.HideArtist, "UCb")
        delay(200)
        assertEquals(4, repository.rerankUpNext(null, page, RecSurface.Radio).size)
    }

    @Test
    fun theSnapshotFollowsNewListeningWithoutARestart() = runBlocking {
        assertNull(repository.snapshot().taste.song("a"))
        recordAndWait(draft("a", ListenKind.Completed))
        assertNotNull(repository.snapshot().taste.song("a"))
        assertTrue(repository.snapshot().taste.artist("UCa")!!.positive > 0.0)
    }
}
