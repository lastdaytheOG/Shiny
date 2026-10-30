package com.shiny.music.intelligence

import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shiny.music.constants.PersonalisedRecommendationsKey
import com.shiny.music.db.InternalDatabase
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.CandidateSource
import com.shiny.music.db.entities.RecSurface
import com.shiny.music.home.Daypart
import com.shiny.music.home.HomeContext
import com.shiny.music.home.HomeSignalsLoader
import com.shiny.music.home.homeWallNowMs
import com.shiny.music.utils.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class IntelligenceScaleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "intelligence-scale-test.db"
    private var internal: InternalDatabase? = null

    @After
    fun close() {
        internal?.close()
        context.deleteDatabase(name)
    }

    private fun fill(database: MusicDatabase, songs: Int, artists: Int, events: Int, outcomes: Int, related: Int) {
        val db = database.openHelper.writableDatabase
        val random = Random(1)
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (a in 0 until artists) db.execSQL("INSERT INTO artist (id, name, lastUpdateTime, isLocal) VALUES ('UC$a', 'Artist $a', 0, 0)")
            for (s in 0 until songs) {
                db.execSQL("INSERT INTO song (id, title, duration, liked, totalPlayTime, isLocal, romanizeLyrics, isDownloaded, isUploaded, isVideo, lyricsOffset, explicit) VALUES ('v$s', 'Song $s', 200, ${if (s % 40 == 0) 1 else 0}, 0, 0, 1, 0, 0, 0, 0, 0)")
                db.execSQL("INSERT INTO song_artist_map (songId, artistId, position) VALUES ('v$s', 'UC${s % artists}', 0)")
            }
            for (e in 0 until events) {
                val song = (random.nextDouble().let { it * it } * songs / 2).toInt()
                val at = now - random.nextLong(500L * 86_400_000L)
                db.execSQL("INSERT INTO event (songId, timestamp, playTime) VALUES ('v$song', $at, 180000)")
            }
            val kinds = listOf("Completed", "Meaningful", "Skipped", "Partial", "Accidental", "Interrupted")
            for (o in 0 until outcomes) {
                val song = random.nextInt(songs)
                val at = now - random.nextLong(300L * 86_400_000L)
                db.execSQL(
                    "INSERT INTO listen_outcome (songId, sessionId, startedAt, endedAt, zoneOffsetMinutes, listenedMs, durationMs, completion, endReason, kind, surface, pickSource, selection, queuePosition, firstListen, replayGapDays, impressionId) " +
                        "VALUES ('v$song', ${at / 1800000}, $at, ${at + 1000}, 330, 100000, 200000, 0.5, 'Finished', '${kinds[o % kinds.size]}', '${RecSurface.Radio.name}', '${CandidateSource.YouTubeRadio.name}', 'Autoplay', 0, ${if (o % 5 == 0) 1 else 0}, NULL, NULL)",
                )
            }
            for (r in 0 until related) {
                db.execSQL("INSERT INTO related_song_map (songId, relatedSongId) VALUES ('v${random.nextInt(songs / 2)}', 'v${random.nextInt(songs)}')")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private suspend fun compareHomeLoads(label: String, database: MusicDatabase) {
        val ctx = HomeContext(nowMs = homeWallNowMs(), daypart = Daypart.of(java.time.LocalDateTime.now().hour), online = true, seed = 1L)
        suspend fun ms(block: suspend () -> Unit): Long {
            val started = System.nanoTime()
            block()
            return (System.nanoTime() - started) / 1_000_000
        }
        val plain = mutableListOf<Long>()
        val cold = mutableListOf<Long>()
        val cached = mutableListOf<Long>()
        repeat(6) {
            plain += ms { HomeSignalsLoader(database).load(ctx) }
            val loader = HomeSignalsLoader(database, IntelligenceRepository(context, database))
            cold += ms { loader.load(ctx) }
            cached += ms { loader.load(ctx) }
        }
        fun List<Long>.middle() = drop(1).sorted()[(size - 1) / 2]
        Log.i("IntelligenceScale", "$label home off=${plain.middle()} on cold=${cold.middle()} on cached=${cached.middle()} ms, runs off=$plain cold=$cold cached=$cached")
    }

    @Test
    fun homeLoadWithAndWithoutPersonalisation() = runBlocking {
        context.dataStore.edit { it[PersonalisedRecommendationsKey] = true }
        context.deleteDatabase(name)
        val room = Room.databaseBuilder(context, InternalDatabase::class.java, name).build().also { internal = it }
        val database = MusicDatabase(room)
        fill(database, songs = 300, artists = 60, events = 1_500, outcomes = 800, related = 1_500)
        compareHomeLoads("typical", database)
        fill2(database)
        compareHomeLoads("heavy", database)
    }

    private fun fill2(database: MusicDatabase) {
        val db = database.openHelper.writableDatabase
        for (table in listOf("related_song_map", "listen_outcome", "event", "song_artist_map", "song", "artist")) db.execSQL("DELETE FROM $table")
        fill(database, songs = 4_000, artists = 600, events = 60_000, outcomes = 40_000, related = 40_000)
    }

    @Test
    fun aHeavyListenerStaysFast() = runBlocking {
        context.dataStore.edit { it[PersonalisedRecommendationsKey] = true }
        context.deleteDatabase(name)
        val room = Room.databaseBuilder(context, InternalDatabase::class.java, name).build().also { internal = it }
        val database = MusicDatabase(room)
        fill(database, songs = 4_000, artists = 600, events = 60_000, outcomes = 40_000, related = 40_000)

        val dao = database.intelligenceDao
        suspend fun <T> timed(label: String, block: suspend () -> T): Pair<T, Long> {
            val started = System.nanoTime()
            val value = block()
            val ms = (System.nanoTime() - started) / 1_000_000
            Log.i("IntelligenceScale", "$label: $ms ms")
            println("SCALE $label: $ms ms")
            return value to ms
        }

        val (edges) = timed("relatedArtistEdges") { dao.relatedArtistEdges(8_000) }
        val repository = IntelligenceRepository(context, database)
        timed("upsert graph") {
            val rows = edges.map {
                com.shiny.music.db.entities.MusicRelation(it.fromId, it.toId, it.kind, it.weight, it.weight.toInt(), it.toName, 1L)
            }
            dao.upsertRelations(rows)
        }
        val (snapshot, firstMs) = timed("snapshot cold") { repository.snapshot() }
        val (_, cachedMs) = timed("snapshot cached") { repository.snapshot() }
        val seeds = snapshot.taste.songs.values.sortedByDescending { it.plays }.take(25).associate { it.songId to it.plays.toDouble() }
        val (pool) = timed("discovery candidates") { repository.discoveryCandidates(snapshot, seeds, emptySet()) }
        timed("rank and diversify") { RecommendationEngine.discover(pool.first, snapshot, pool.second, 60) }
        timed("up next arrange") { RecommendationEngine.upNext(null, pool.first.take(60), snapshot, emptyList(), 20) }
        timed("insights") { repository.insights() }
        timed("top artists playlist edges") { dao.playlistArtistEdges(snapshot.taste.artists.keys.take(24).toList(), 1_500) }

        Log.i("IntelligenceScale", "artists=${snapshot.taste.artists.size} songs=${snapshot.taste.songs.size} nodes=${snapshot.graph.nodeCount} reach=${snapshot.reach.size} pool=${pool.first.size}")
        assertTrue("graph was used", snapshot.graph.nodeCount > 0)
        assertTrue("cold snapshot took $firstMs ms", firstMs < 5_000)
        assertTrue("cached snapshot took $cachedMs ms", cachedMs < firstMs)
    }
}
