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
    private val now = 1_800_000_000_000L
    private val day = TasteMath.DAY

    private fun play(id: String, artist: String, daysAgo: Int, ms: Long = 180_000) =
        TastePlay(id, "song $id", listOf("id_$artist"), listOf(artist), null, now - daysAgo * day, ms)

    private fun song(id: String, artist: String) = SongItem(id = id, title = id, artists = listOf(Artist(artist, "id_$artist")), thumbnail = "")

    private fun row(kind: RecRowKind, subject: String, ids: List<String>) =
        RecommendationRowSection(kind, subject, null, ids.map { song(it, "artist_$it") })

    @Test
    fun `recent plays count most and old ones fade out`() {
        assertEquals(1.0, TasteMath.decay(0), 1e-9)
        val twoWeeks = TasteMath.decay(14 * day)
        assertTrue("two weeks ago is about a third, was $twoWeeks", twoWeeks in 0.28..0.40)
        assertTrue(TasteMath.decay(90 * day) < 0.01)
    }

    @Test
    fun `seeds are one per artist and never a skipped song`() {
        val plays = listOf(
            play("a1", "A", 0), play("a1", "A", 1), play("a2", "A", 0),
            play("b1", "B", 2), play("c1", "C", 5),
            play("s1", "D", 0, ms = 5_000), play("s1", "D", 1, ms = 4_000),
        )
        val skipped = TasteMath.skipped(plays, now)
        assertTrue("s1" in skipped)
        val seeds = TasteMath.seeds(plays, emptyList(), now, skipped, Random(7), 3)
        assertEquals(3, seeds.size)
        assertEquals(seeds.size, seeds.map { it.artistName }.toSet().size)
        assertFalse(seeds.any { it.songId == "s1" })
    }

    @Test
    fun `an artist skipped three times sits out`() {
        val plays = (1..3).map { play("x$it", "Loud", 1, ms = 3_000) } + play("y", "Calm", 1, ms = 3_000)
        val out = TasteMath.skippedArtists(plays, now)
        assertTrue("loud" in out)
        assertFalse("calm" in out)
    }

    @Test
    fun `picks are mostly unheard and spread across artists`() {
        val pool = (1..6).map { song("a$it", "A") } + (1..10).map { song("n$it", "N$it") }
        val heard = setOf("n1", "n2", "n3", "n4", "n5")
        val picks = TasteMath.pickSongs(pool, heard, emptySet(), emptySet(), size = 12, perArtist = 2, heardAllowed = 2)
        assertTrue(picks.count { it.artists.first().name == "A" } <= 2)
        assertTrue(picks.count { it.id in heard } <= 2)
    }

    @Test
    fun `rows interleave, share no songs and drop when too short`() {
        val songRow = row(RecRowKind.BecauseYouLike, "s", listOf("1", "2", "3", "4", "5"))
        val artistRow = row(RecRowKind.MoreLike, "a", listOf("1", "2", "6", "7", "8", "9"))
        val tiny = row(RecRowKind.Genre, "g", listOf("1", "2", "3", "10"))
        val releases = row(RecRowKind.NewReleases, "", listOf("r1", "r2", "r3", "r4"))
        val fresh = row(RecRowKind.NewGenre, "f", listOf("f1", "f2", "f3", "f4"))
        val out = TasteMath.arrange(listOf(songRow), listOf(artistRow), listOf(tiny), releases, listOf(fresh))
        assertEquals(listOf(RecRowKind.BecauseYouLike, RecRowKind.MoreLike, RecRowKind.NewReleases, RecRowKind.NewGenre), out.map { it.kind })
        assertEquals(listOf("6", "7", "8", "9"), out[1].items.map { it.id })
    }

    @Test
    fun `a playlist follows every three songs`() {
        val songs = (1..7).map { song("s$it", "A$it") }
        val lists = (1..3).map { PlaylistItem(id = "p$it", title = "p$it", author = null, songCountText = null, thumbnail = null, playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null) }
        val woven = TasteMath.weave(songs, lists).map { it.id }
        assertEquals(listOf("s1", "s2", "s3", "p1", "s4", "s5", "s6", "p2", "s7", "p3"), woven)
    }

    @Test
    fun `an iTunes genre finds its YouTube shelf`() {
        assertTrue(TasteMath.genreMatches("Hip-Hop/Rap", "Hip-hop"))
        assertTrue(TasteMath.genreMatches("Bollywood", "Bollywood & Indian"))
        assertFalse(TasteMath.genreMatches("Rock", "Jazz"))
    }
}
