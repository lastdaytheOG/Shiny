package com.shiny.music.intelligence

import com.shiny.music.db.entities.DiscoveryOutcomeStat
import com.shiny.music.db.entities.HomeArtistStat
import com.shiny.music.db.entities.HomeSavedArtist
import com.shiny.music.db.entities.HomeSongStat
import com.shiny.music.db.entities.MusicFeedback
import com.shiny.music.db.entities.SongFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteInsightsTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val names = mapOf("a" to "Artist A", "b" to "Artist B", "bridge" to "The Bridge", "saved" to "Saved One")

    private fun build(input: TasteInputs, reach: Map<String, GraphReach> = emptyMap(), stat: DiscoveryOutcomeStat? = null) =
        TasteInsights.build(TasteProfileBuilder.build(input), reach, names, stat)

    @Test
    fun `a new listener gets no insights at all`() {
        assertTrue(build(TasteInputs(nowMs = now)).isEmpty())
    }

    @Test
    fun `a rising artist needs recent plays, a loyal one needs real depth`() {
        val out = build(
            TasteInputs(
                nowMs = now,
                artistStats = listOf(
                    HomeArtistStat("a", 4, 4, 4, now - day, 2),
                    HomeArtistStat("b", 60, 0, 0, now - 40 * day, 12),
                ),
            ),
        )
        assertTrue(out.any { it is TasteInsight.Rising && it.artistId == "a" })
        assertTrue(out.any { it is TasteInsight.Loyal && it.artistId == "b" && it.plays == 60 })
    }

    @Test
    fun `two plays are not coming back to someone`() {
        val out = build(TasteInputs(nowMs = now, artistStats = listOf(HomeArtistStat("a", 2, 2, 2, now - day, 2))))
        assertTrue(out.none { it is TasteInsight.Rising })
    }

    @Test
    fun `an artist without a known name is never mentioned`() {
        val out = build(TasteInputs(nowMs = now, artistStats = listOf(HomeArtistStat("nameless", 60, 0, 0, now - 40 * day, 12))))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `hidden artists are never brought up`() {
        val out = build(
            TasteInputs(
                nowMs = now,
                artistStats = listOf(HomeArtistStat("b", 60, 0, 0, now - 40 * day, 12)),
                feedback = listOf(MusicFeedback("artist", "b", FeedbackKind.HideArtist.name, null, now, null)),
            ),
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `exploring needs new songs that actually worked`() {
        val input = TasteInputs(nowMs = now)
        assertTrue(build(input, stat = DiscoveryOutcomeStat(10, 2, 8, 0, 0, 20, 5)).none { it is TasteInsight.ExploringMore })
        assertTrue(build(input, stat = DiscoveryOutcomeStat(10, 7, 2, 0, 0, 20, 5)).any { it is TasteInsight.ExploringMore })
    }

    @Test
    fun `a bridge names both of the listener's artists and the new one`() {
        val reach = mapOf("bridge" to GraphReach("bridge", 1.0, 1, listOf("a", "b"), "a"))
        val insight = build(TasteInputs(nowMs = now), reach).filterIsInstance<TasteInsight.Bridge>().single()
        assertEquals("Artist A", insight.first)
        assertEquals("Artist B", insight.second)
        assertEquals("The Bridge", insight.bridge)
    }

    @Test
    fun `a bridge to an artist they already play is not news`() {
        val reach = mapOf("a" to GraphReach("a", 1.0, 1, listOf("b", "bridge"), "b"))
        val input = TasteInputs(nowMs = now, artistStats = listOf(HomeArtistStat("a", 3, 0, 0, now, 1)))
        assertTrue(build(input, reach).none { it is TasteInsight.Bridge })
    }

    @Test
    fun `saved but never played is an unexplored artist`() {
        val out = build(TasteInputs(nowMs = now, savedArtists = listOf(HomeSavedArtist("saved", "Saved One", 6, 1))))
        assertEquals(6, out.filterIsInstance<TasteInsight.Unexplored>().single().saved)
    }

    @Test
    fun `a tempo claim needs enough measured songs`() {
        fun input(count: Int) = TasteInputs(
            nowMs = now,
            songStats = (0 until count).map { HomeSongStat("s$it", 3, 1, 0, 0, 0, 0, 0, 0) },
            features = (0 until count).map { SongFeature("s$it", 120f + it, 0.8f, null, null, null, null, null) },
        )
        assertTrue(build(input(3)).none { it is TasteInsight.Tempo })
        assertTrue(build(input(12)).any { it is TasteInsight.Tempo })
    }

    @Test
    fun `at most one insight a day, and none below the confidence bar`() {
        assertNull(TasteInsights.pickForToday(emptyList(), 5))
        assertNull(TasteInsights.pickForToday(listOf(TasteInsight.ExploringMore(1, 1, 0.2)), 5))
        val pick = TasteInsights.pickForToday(
            listOf(TasteInsight.ExploringMore(1, 1, 0.8), TasteInsight.Tempo(100, 120, 20, 0.7)),
            16,
        )
        assertEquals(TasteInsights.pickForToday(listOf(TasteInsight.ExploringMore(1, 1, 0.8), TasteInsight.Tempo(100, 120, 20, 0.7)), 31), pick)
    }
}
