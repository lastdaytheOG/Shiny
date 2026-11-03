package com.shiny.music.intelligence

import org.junit.Assert.assertEquals
import org.junit.Test

class UpNextOrderTest {
    private fun songs(vararg ids: String) = ids.map { it to listOf("artist_$it") }

    @Test
    fun keepsYouTubesOrderUntouched() {
        val list = songs("e", "a", "d", "b", "c")
        assertEquals(listOf(0, 1, 2, 3, 4), UpNextOrder.keep(list))
    }

    @Test
    fun dropsOnlyRepeatsAndBlankIds() {
        val list = songs("a", "b", "a", "", "c", "b")
        assertEquals(listOf(0, 1, 4), UpNextOrder.keep(list))
    }

    @Test
    fun dropsWhatTheListenerTurnedDownAndKeepsTheRestInOrder() {
        val list = songs("a", "b", "c", "d")
        val kept = UpNextOrder.keep(list) { id, artists -> id == "b" || "artist_d" in artists }
        assertEquals(listOf(0, 2), kept)
    }

    @Test
    fun limitTakesTheFirstSongsNotTheBest() {
        val list = songs("z", "y", "x", "w")
        assertEquals(listOf(0, 1), UpNextOrder.keep(list, limit = 2))
        assertEquals(emptyList<Int>(), UpNextOrder.keep(list, limit = 0))
    }
}
