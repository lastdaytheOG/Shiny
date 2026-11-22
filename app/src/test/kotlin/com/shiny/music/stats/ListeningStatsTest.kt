package com.shiny.music.stats

import com.shiny.music.db.entities.Event
import com.shiny.music.db.entities.EventWithSong
import com.shiny.music.db.entities.Song
import com.shiny.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ListeningStatsTest {
    private var now = 0L
    private var threshold = 30_000L
    private val saved = mutableListOf<Pair<String, Long>>()
    private val finished = mutableListOf<String>()
    private val counter = PlayCounter<Unit>({ now }, { threshold }, true, { p, ms -> saved += p.songId to ms }, { finished += it.songId })

    private fun hear(ms: Long) {
        now += ms
        counter.checkpoint()
    }

    private fun begin() {
        counter.start("a", Unit)
        counter.setPlaying(true)
    }

    @Test
    fun countsWhilePlaying() {
        begin()
        hear(30_000)
        assertEquals(listOf("a" to 30_000L), saved)
        assertEquals(emptyList<String>(), finished)
    }

    @Test
    fun pauseSavesAndPausedTimeIsIgnored() {
        begin()
        now += 45_000
        counter.setPlaying(false)
        hear(600_000)
        assertEquals(listOf("a" to 45_000L), saved)
        counter.setPlaying(true)
        now += 15_000
        counter.finish()
        assertEquals("a" to 60_000L, saved.last())
    }

    @Test
    fun shortPlayIsNotCounted() {
        begin()
        now += 29_999
        counter.finish()
        assertEquals(emptyList<Pair<String, Long>>(), saved)
    }

    @Test
    fun eachSongAndRepeatHasItsOwnTime() {
        begin()
        now += 100_000
        counter.start("b", Unit)
        now += 40_000
        counter.start("b", Unit)
        now += 50_000
        counter.finish()
        assertEquals(listOf("a" to 100_000L, "b" to 40_000L, "b" to 50_000L), saved)
        assertEquals(listOf("a", "b", "b"), finished)
    }

    @Test
    fun pausedHistoryAddsNothing() {
        begin()
        now += 40_000
        counter.setAllowed(false)
        hear(90_000)
        counter.setAllowed(true)
        now += 20_000
        counter.finish()
        assertEquals("a" to 60_000L, saved.last())
    }

    @Test
    fun zeroLimitStillNeedsSomePlayback() {
        threshold = 0
        begin()
        counter.finish()
        assertEquals(emptyList<Pair<String, Long>>(), saved)
    }

    @Test
    fun totalsUseLiveTimeAndKeepEveryOldPlay() {
        val today = LocalDate.of(2026, 11, 22)
        fun play(id: Long, song: String, daysAgo: Long, ms: Long) = EventWithSong(
            Event(id, song, today.minusDays(daysAgo).atTime(12, 0), ms),
            Song(SongEntity(id = song, title = song), artists = emptyList()),
        )
        val events = listOf(play(1, "a", 0, 30_000), play(2, "a", 0, 60_000), play(3, "b", 800, 120_000), play(4, "c", -1, 60_000))
        val totals = StatsReport.summarise(events, today, mapOf(1L to 180_000L)).totals.associateBy { it.period }
        assertEquals(4L, totals.getValue(StatsPeriod.Today).minutes)
        assertEquals(1, totals.getValue(StatsPeriod.Today).songs)
        assertEquals(7L, totals.getValue(StatsPeriod.All).minutes)
        assertEquals(3, totals.getValue(StatsPeriod.All).songs)
    }
}
