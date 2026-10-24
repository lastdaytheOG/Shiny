package com.shiny.music.ui.liquid.player

import androidx.media3.common.Player
import com.shiny.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueEntriesTest {
    @Test
    fun `played current and upcoming keep play order and roles`() {
        val entries = queueEntries(listOf("a", "b"), "c", listOf("d", "e"), upcomingLimit = 50)
        assertEquals(listOf("a", "b", "c", "d", "e"), entries.map { it.item })
        assertEquals(
            listOf(QueueRole.Played, QueueRole.Played, QueueRole.Current, QueueRole.Upcoming, QueueRole.Upcoming),
            entries.map { it.role },
        )
    }

    @Test
    fun `only the rendered page of upcoming songs is listed`() {
        val upcoming = (1..120).map { "u$it" }
        val entries = queueEntries(listOf("p"), "now", upcoming, upcomingLimit = 50)
        assertEquals(52, entries.size)
        assertEquals("u50", entries.last().item)
        assertTrue(entries.drop(2).all { it.role == QueueRole.Upcoming })
    }

    @Test
    fun `no current song lists upcoming only`() {
        val entries = queueEntries(emptyList(), null, listOf("x", "y"), upcomingLimit = 50)
        assertEquals(listOf(QueueRole.Upcoming, QueueRole.Upcoming), entries.map { it.role })
    }

    @Test
    fun `a negative limit shows no upcoming songs`() {
        val entries = queueEntries(listOf("p"), "c", listOf("u"), upcomingLimit = -1)
        assertEquals(listOf("p", "c"), entries.map { it.item })
    }

    @Test
    fun `repeat label names the mode`() {
        assertEquals(R.string.liquid_repeat, repeatLabel(Player.REPEAT_MODE_OFF))
        assertEquals(R.string.liquid_repeat_all, repeatLabel(Player.REPEAT_MODE_ALL))
        assertEquals(R.string.liquid_repeat_one, repeatLabel(Player.REPEAT_MODE_ONE))
    }
}
