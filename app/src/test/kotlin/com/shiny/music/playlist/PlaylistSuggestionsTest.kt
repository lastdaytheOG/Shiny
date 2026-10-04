package com.shiny.music.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaylistSuggestionsTest {
    private fun merge(related: List<List<String>>, inPlaylist: Set<String> = emptySet(), limit: Int = 24) =
        PlaylistSuggestions.merge(related, inPlaylist, { it }, limit)

    @Test
    fun `seeds are a few distinct songs of the playlist`() {
        val ids = List(40) { "song$it" }
        val seeds = PlaylistSuggestions.seeds(ids + ids, Random(7))
        assertEquals(PlaylistSuggestions.MAX_SEEDS, seeds.size)
        assertEquals(seeds.size, seeds.toSet().size)
        assertTrue(ids.containsAll(seeds))
    }

    @Test
    fun `a short playlist uses every song and an empty one none`() {
        assertEquals(setOf("a", "b"), PlaylistSuggestions.seeds(listOf("a", "b", "a")).toSet())
        assertEquals(emptyList<String>(), PlaylistSuggestions.seeds(emptyList()))
    }

    @Test
    fun `seeds are not always the first songs`() {
        val ids = List(200) { "song$it" }
        val picks = (0 until 20).flatMap { PlaylistSuggestions.seeds(ids, Random(it)) }
        assertTrue(picks.any { it !in ids.take(PlaylistSuggestions.MAX_SEEDS) })
    }

    @Test
    fun `seeds take turns`() {
        val merged = merge(listOf(listOf("a1", "a2", "a3"), listOf("b1"), listOf("c1", "c2")))
        assertEquals(listOf("a1", "b1", "c1", "a2", "c2", "a3"), merged)
    }

    @Test
    fun `songs already in the playlist are left out`() {
        val merged = merge(listOf(listOf("x", "a1"), listOf("b1", "y")), inPlaylist = setOf("x", "y"))
        assertEquals(listOf("b1", "a1"), merged)
    }

    @Test
    fun `a song related to two seeds is suggested once, at its first place`() {
        val merged = merge(listOf(listOf("same", "a2"), listOf("same", "b2")))
        assertEquals(listOf("same", "a2", "b2"), merged)
    }

    @Test
    fun `the result is capped`() {
        val merged = merge(listOf(List(30) { "a$it" }, List(30) { "b$it" }), limit = 5)
        assertEquals(listOf("a0", "b0", "a1", "b1", "a2"), merged)
    }

    @Test
    fun `nothing related gives nothing`() {
        assertEquals(emptyList<String>(), merge(emptyList()))
        assertEquals(emptyList<String>(), merge(listOf(emptyList(), emptyList())))
    }
}
