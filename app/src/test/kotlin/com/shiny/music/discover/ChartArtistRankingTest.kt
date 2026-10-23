package com.shiny.music.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartArtistRankingTest {

    private fun song(position: Int, credit: String, artistId: String?, slug: String?, title: String = "Song $position") = ChartEntry(
        position = position,
        id = "s$position",
        title = title,
        artist = credit,
        artistId = artistId,
        artistUrl = slug?.let { "https://music.apple.com/in/artist/$it/${artistId ?: "0"}" },
    )

    @Test
    fun `the lead credit comes from the artist url, so duo names survive`() {
        assertEquals("Arijit Singh", ChartText.leadArtist("Arijit Singh, Shilpa Rao & Vishal & Shekhar", "https://music.apple.com/in/artist/arijit-singh/484568188"))
        assertEquals("Vishal & Shekhar", ChartText.leadArtist("Vishal & Shekhar, Arijit Singh", "https://music.apple.com/in/artist/vishal-shekhar/1"))
        assertEquals("Karan Aujla", ChartText.leadArtist("Karan Aujla & MXRCI", null))
        assertEquals("ADÉLA", ChartText.leadArtist("ADÉLA", "https://music.apple.com/in/artist/adela/1765924916"))
    }

    @Test
    fun `artists rank by lead songs, then features, then highest position`() {
        val songs = listOf(
            song(1, "Karan Aujla & MXRCI", "914892130", "karan-aujla"),
            song(2, "Anirudh Ravichander, Super Subu", "480976476", "anirudh-ravichander"),
            song(3, "Anirudh Ravichander & Super Subu", "480976476", "anirudh-ravichander"),
            song(4, "Karan Aujla", "914892130", "karan-aujla"),
            song(5, "Pritam & Arijit Singh", "213157068", "pritam"),
            song(6, "Arijit Singh", "484568188", "arijit-singh"),
            song(7, "Pritam", "213157068", "pritam"),
        )
        val ranked = ChartArtistRanking.rank(songs)
        assertEquals(listOf("Karan Aujla", "Anirudh Ravichander", "Pritam", "Arijit Singh"), ranked.map { it.name })
        assertEquals(2, ranked[0].songCount)
        assertEquals(1, ranked[0].bestPosition)
        assertEquals(1, ranked.first { it.name == "Arijit Singh" }.featuredOn)
        assertEquals(listOf("s1", "s4"), ranked[0].songs.map { it.id })
    }

    @Test
    fun `featured-only names never become entries of their own`() {
        val songs = listOf(
            song(1, "Anirudh Ravichander & Super Subu", "480976476", "anirudh-ravichander"),
            song(2, "Anirudh Ravichander, Super Subu & M.S Krsna", "480976476", "anirudh-ravichander"),
        )
        val ranked = ChartArtistRanking.rank(songs)
        assertEquals(listOf("Anirudh Ravichander"), ranked.map { it.name })
        assertEquals(0, ranked.single().featuredOn)
    }

    @Test
    fun `a lead without an apple id still groups by name`() {
        val songs = listOf(
            song(3, "Shubh", null, null),
            song(9, "SHUBH", null, null),
            song(1, "Zed", null, null),
        )
        val ranked = ChartArtistRanking.rank(songs)
        assertEquals("Shubh", ranked[0].name)
        assertEquals(2, ranked[0].songCount)
        assertEquals("name:shubh", ranked[0].key)
    }

    @Test
    fun `title features count as featured credits`() {
        val songs = listOf(
            song(1, "Shreya Ghoshal", "1", "shreya-ghoshal"),
            song(2, "ARJN", "2", "arjn", title = "KALYANI (with Shreya Ghoshal) [Remix]"),
        )
        assertEquals(1, ChartArtistRanking.rank(songs).first { it.name == "Shreya Ghoshal" }.featuredOn)
    }

    @Test
    fun `ties break stably by position, then by name`() {
        val songs = listOf(song(4, "B", "b", "b"), song(2, "A", "a", "a"), song(9, "C", "c", "c"))
        val first = ChartArtistRanking.rank(songs).map { it.name }
        assertEquals(listOf("A", "B", "C"), first)
        assertEquals(first, ChartArtistRanking.rank(songs.reversed()).map { it.name })
    }

    @Test
    fun `nothing in, nothing out`() {
        assertTrue(ChartArtistRanking.rank(emptyList()).isEmpty())
    }
}
