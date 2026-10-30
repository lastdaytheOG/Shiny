package com.shiny.music.intelligence

import com.shiny.music.db.entities.ArtistDaypartStat
import com.shiny.music.db.entities.ArtistOutcomeBucket
import com.shiny.music.db.entities.DiscoveryOutcomeStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSavedArtist
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.ImpressionFatigue
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.SongOutcomeBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteProfileTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun stat(id: String, plays: Int, daysAgo: Int = 1, week: Int = 0, month: Int = 0, songs: Int = 3) =
        HomeArtistStat(id, plays = plays, weekPlays = week, monthPlays = month, lastPlayed = now - daysAgo * day, songs = songs)

    private fun songStat(id: String, plays: Int = 1, week: Int = 0) =
        HomeSongStat(id, plays, plays * 180_000L, now - 90 * day, now - day, week, week * 180_000L, week, 0)

    private fun feedback(kind: FeedbackKind, id: String, expires: Long? = null) =
        MusicFeedback(kind.targetType, id, kind.name, null, now - day, expires)

    private fun build(input: TasteInputs) = TasteProfileBuilder.build(input)

    @Test
    fun `a new listener has no taste and no invented confidence`() {
        val taste = build(TasteInputs(nowMs = now))
        assertTrue(taste.artists.isEmpty())
        assertEquals(0.0, taste.confidence, 0.0)
        assertNull(taste.sound)
    }

    @Test
    fun `long-term taste survives a quiet spell, short-term does not`() {
        val taste = build(TasteInputs(nowMs = now, artistStats = listOf(stat("old", plays = 80, daysAgo = 120, songs = 12))))
        val artist = taste.artist("old")!!
        assertTrue("still known", artist.longTerm > 0.4)
        assertEquals("nothing recent", 0.0, artist.shortTerm, 0.0)
    }

    @Test
    fun `recent plays lift short-term taste above long-term`() {
        val taste = build(
            TasteInputs(nowMs = now, artistStats = listOf(stat("new", plays = 6, week = 6, month = 6, songs = 2))),
        )
        val artist = taste.artist("new")!!
        assertTrue(artist.shortTerm > artist.longTerm)
    }

    @Test
    fun `decay is monotonic with age`() {
        fun longTerm(days: Int) = build(
            TasteInputs(nowMs = now, artistStats = listOf(stat("a", plays = 20, daysAgo = days))),
        ).artist("a")!!.longTerm
        assertTrue(longTerm(1) > longTerm(100))
        assertTrue(longTerm(100) > longTerm(400))
    }

    @Test
    fun `skips of an artist the listener rarely finishes make a rejection`() {
        val taste = build(
            TasteInputs(
                nowMs = now,
                artistOutcomes = listOf(ArtistOutcomeBucket("meh", 0, positive = 0, partial = 0, negative = 8, lastAt = now - day)),
            ),
        )
        assertTrue(taste.artist("meh")!!.rejection > 0.7)
    }

    @Test
    fun `conflicting evidence stays conflicted rather than flipping`() {
        val taste = build(
            TasteInputs(
                nowMs = now,
                artistStats = listOf(stat("both", plays = 30)),
                artistOutcomes = listOf(ArtistOutcomeBucket("both", 0, positive = 12, partial = 0, negative = 4, lastAt = now - day)),
            ),
        )
        val artist = taste.artist("both")!!
        assertTrue("mostly liked", artist.rejection < 0.2)
        assertTrue(artist.negative > 0.0)
    }

    @Test
    fun `a song once loved and now skipped has faded out`() {
        val taste = build(
            TasteInputs(
                nowMs = now,
                songStats = listOf(songStat("s", plays = 12)),
                songOutcomes = listOf(SongOutcomeBucket("s", 0, positive = 0, partial = 0, negative = 3, listenedMs = 9_000, lastAt = now - day)),
            ),
        )
        assertTrue(taste.song("s")!!.fadedOut)
    }

    @Test
    fun `explicit feedback lands in the matching sets and expired feedback is ignored`() {
        val taste = build(
            TasteInputs(
                nowMs = now,
                feedback = listOf(
                    feedback(FeedbackKind.HideArtist, "hidden"),
                    feedback(FeedbackKind.BlockSong, "blocked"),
                    feedback(FeedbackKind.NotInterested, "soft"),
                    feedback(FeedbackKind.LessLikeThis, "less"),
                    feedback(FeedbackKind.MoreFromArtist, "more"),
                    feedback(FeedbackKind.SeedArtist, "seed"),
                    feedback(FeedbackKind.NotInterested, "lapsed", expires = now - 1),
                ),
            ),
        )
        assertTrue(taste.isRejected("x", listOf("hidden")))
        assertTrue(taste.isRejected("blocked", emptyList()))
        assertTrue(taste.isRejected("soft", emptyList()))
        assertFalse("the expiry gave it back", taste.isRejected("lapsed", emptyList()))
        assertTrue("less" in taste.lessLikeArtists)
        assertTrue("more" in taste.boostedArtists)
        assertEquals(0.45, taste.artist("seed")!!.longTerm, 1e-9)
    }

    @Test
    fun `saved artists are known but a library alone never counts as heard`() {
        val taste = build(TasteInputs(nowMs = now, savedArtists = listOf(HomeSavedArtist("s", "S", songs = 8, liked = 2))))
        val artist = taste.artist("s")!!
        assertTrue(artist.known)
        assertFalse(artist.heard)
        assertTrue(artist.longTerm < 0.55)
    }

    @Test
    fun `discovery appetite follows what happened to new songs`() {
        fun appetite(pos: Int, neg: Int) = TasteProfileBuilder.discoveryPersonality(
            DiscoveryOutcomeStat(pos + neg, pos, neg, 0, 0, 100, 50),
        ).appetite
        assertTrue(appetite(20, 2) > appetite(2, 20))
    }

    @Test
    fun `discovery appetite starts in the middle with no evidence`() {
        val p = TasteProfileBuilder.discoveryPersonality(DiscoveryOutcomeStat(0, 0, 0, 0, 0, 0, 0))
        assertEquals(0, p.evidence)
        assertTrue(p.appetite in 0.3..0.6)
    }

    @Test
    fun `a daypart lift needs real evidence, one play moves it little`() {
        val taste = build(
            TasteInputs(
                nowMs = now,
                daypart = listOf(
                    ArtistDaypartStat("night", here = 40, total = 44),
                    ArtistDaypartStat("once", here = 1, total = 1),
                    ArtistDaypartStat("day", here = 2, total = 60),
                ),
            ),
        )
        assertTrue(taste.daypartLift.getValue("night") > taste.daypartLift.getValue("once"))
        assertTrue(taste.daypartLift.getValue("day") < 0.0)
    }

    @Test
    fun `songs shown again and again without a play build up as ignored`() {
        val taste = build(
            TasteInputs(
                nowMs = now,
                impressions = listOf(
                    ImpressionFatigue("ignored", shown = 9, played = 0, lastShown = now),
                    ImpressionFatigue("played", shown = 3, played = 3, lastShown = now),
                ),
            ),
        )
        assertTrue(taste.ignored.getValue("ignored") > 0.5)
        assertFalse("played" in taste.ignored)
    }
}
