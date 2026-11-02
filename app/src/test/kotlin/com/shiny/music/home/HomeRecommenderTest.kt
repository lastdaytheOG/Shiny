package com.shiny.music.home

import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HomeRecommenderTest {
    private fun song(id: String, artist: String) = SongItem(id = id, title = id, artists = listOf(Artist(artist, "id_$artist")), thumbnail = "")

    private fun row(kind: RecRowKind, subject: String, ids: List<String>) =
        RecommendationRowSection(kind, subject, null, ids.map { song(it, "artist_$it") })

    @Test
    fun `recent plays count most and old ones fade out`() {
        assertEquals(1.0, TasteMath.decay(0.0), 1e-9)
        val twoWeeks = TasteMath.decay(14.0)
        assertTrue("two weeks ago is about a third, was $twoWeeks", twoWeeks in 0.33..0.40)
        assertTrue(TasteMath.decay(90.0) < 0.01)
    }

    @Test
    fun `an artist skipped three times sits out`() {
        val out = TasteMath.avoidArtists(listOf(listOf("Loud"), listOf("loud"), listOf("LOUD", "Calm"), listOf("Calm")))
        assertTrue("loud" in out)
        assertFalse("calm" in out)
    }

    @Test
    fun `weighted sampling favours stronger seeds but takes only as many as asked`() {
        val candidates = listOf("strong" to 50.0, "weak" to 0.01, "mid" to 1.0)
        var strongFirst = 0
        repeat(200) { i -> if (TasteMath.weightedSample(candidates, 2, Random(i)).first() == "strong") strongFirst++ }
        assertTrue("strong led $strongFirst of 200", strongFirst > 150)
        assertEquals(2, TasteMath.weightedSample(candidates, 2, Random(1)).size)
    }

    @Test
    fun `picks are mostly unheard, never skipped, spread across artists`() {
        val pool = (1..6).map { song("a$it", "A") } + (1..12).map { song("n$it", "N$it") } + song("skip", "S")
        val heard = setOf("n1", "n2", "n3", "n4", "n5")
        val picks = TasteMath.pick(pool, heard, avoidSongs = setOf("skip"), avoidArtists = emptySet(), limit = 12, perArtist = 2, random = Random(3))
        assertTrue(picks.count { it.artists.first().name == "A" } <= 2)
        assertTrue("at most a quarter heard", picks.count { it.id in heard } <= 3)
        assertFalse(picks.any { it.id == "skip" })
    }

    @Test
    fun `rows take turns, share no songs and drop when too short`() {
        val parts = mapOf(
            RecRowKind.BecauseYouLike to listOf(row(RecRowKind.BecauseYouLike, "s", listOf("1", "2", "3", "4", "5"))),
            RecRowKind.MoreLike to listOf(row(RecRowKind.MoreLike, "a", listOf("1", "2", "6", "7", "8", "9"))),
            RecRowKind.Genre to listOf(row(RecRowKind.Genre, "g", listOf("1", "2", "3", "10"))),
            RecRowKind.NewReleases to listOf(row(RecRowKind.NewReleases, "", listOf("r1", "r2", "r3", "r4"))),
            RecRowKind.NewGenre to listOf(row(RecRowKind.NewGenre, "f", listOf("f1", "f2", "f3", "f4"))),
        )
        val out = TasteMath.arrange(parts)
        assertEquals(listOf(RecRowKind.BecauseYouLike, RecRowKind.MoreLike, RecRowKind.NewReleases, RecRowKind.NewGenre), out.map { it.kind })
        assertEquals(listOf("6", "7", "8", "9"), out[1].items.map { it.id })
    }

    @Test
    fun `a playlist follows every three songs`() {
        val songs = (1..7).map { song("s$it", "A$it") }
        val lists = (1..3).map { PlaylistItem(id = "p$it", title = "p$it", author = null, songCountText = null, thumbnail = null, playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null) }
        assertEquals(listOf("s1", "s2", "s3", "p1", "s4", "s5", "s6", "p2", "s7", "p3"), TasteMath.weave(songs, lists).map { it.id })
    }

    @Test
    fun `an itunes genre finds its youtube tile, exact title first`() {
        val tiles = listOf("K-Pop", "Pop", "Hip-hop", "Bollywood & Indian", "Rock", "Decades")
        assertEquals("Pop", HomeGenres.match("Pop", tiles) { it })
        assertEquals("K-Pop", HomeGenres.match("K-Pop", tiles) { it })
        assertEquals("Hip-hop", HomeGenres.match("Hip-Hop/Rap", tiles) { it })
        assertEquals("Bollywood & Indian", HomeGenres.match("Bollywood", tiles) { it })
        assertFalse(HomeGenres.isGenre("Decades"))
    }

    @Test
    fun `a skip is under thirty seconds and under half the song, short tracks don't count`() {
        ListeningSignals.markRefreshed()
        ListeningSignals.onTrackLeft("intro", listOf("X"), durationMs = 20_000, listenedMs = 3_000)
        ListeningSignals.onTrackLeft("never", listOf("X"), durationMs = 200_000, listenedMs = 100)
        assertEquals(0, ListeningSignals.skipsSinceRefresh)
        ListeningSignals.onTrackLeft("skip", listOf("X"), durationMs = 200_000, listenedMs = 10_000)
        ListeningSignals.onTrackLeft("heard", listOf("X"), durationMs = 200_000, listenedMs = 150_000)
        assertEquals(1, ListeningSignals.skipsSinceRefresh)
        assertEquals(1, ListeningSignals.playsSinceRefresh)
        assertTrue(ListeningSignals.recentSkips().any { it.songId == "skip" })
    }
}
