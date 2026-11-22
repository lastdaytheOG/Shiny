package com.shiny.music.stats

import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.EventWithSong
import com.shiny.music.db.entities.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class StatsPeriod { Today, Week, Year, All }

class PeriodTotal(val period: StatsPeriod, val ms: Long, val songs: Int) {
    val minutes get() = ms / 60_000
}

class HistoryPlay(val eventId: Long, val song: Song, val at: LocalDateTime)

class HistoryDay(val date: LocalDate, val ms: Long, val plays: List<HistoryPlay>) {
    val minutes get() = ms / 60_000
}

class ListeningSummary(val totals: List<PeriodTotal>, val days: List<HistoryDay>) {
    val isEmpty get() = days.isEmpty()
}

object StatsReport {
    private const val REFRESH_MS = 5_000L

    fun summary(database: MusicDatabase): Flow<ListeningSummary> =
        combine(database.events().conflate(), clock()) { events, (today, live) -> summarise(events, today, live) }
            .flowOn(Dispatchers.Default)

    private fun clock(): Flow<Pair<LocalDate, Map<Long, Long>>> = flow {
        while (true) {
            emit(LocalDate.now() to LiveListens.heard())
            delay(REFRESH_MS)
        }
    }.distinctUntilChanged()

    fun summarise(events: List<EventWithSong>, today: LocalDate, live: Map<Long, Long> = emptyMap()): ListeningSummary {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
        val starts = mapOf(
            StatsPeriod.Today to today,
            StatsPeriod.Week to weekStart,
            StatsPeriod.Year to today.withDayOfYear(1),
            StatsPeriod.All to LocalDate.MIN,
        )
        val ms = HashMap<StatsPeriod, Long>()
        val songs = HashMap<StatsPeriod, HashSet<String>>()
        val days = LinkedHashMap<LocalDate, MutableList<HistoryPlay>>()
        val dayMs = HashMap<LocalDate, Long>()

        events.sortedByDescending { it.event.timestamp }.forEach { e ->
            val date = e.event.timestamp.toLocalDate()
            val played = maxOf(e.event.playTime, live[e.event.id] ?: 0L, 0L)
            starts.forEach { (period, start) ->
                if (period == StatsPeriod.All || (!date.isBefore(start) && !date.isAfter(today))) {
                    ms.merge(period, played, Long::plus)
                    songs.getOrPut(period) { HashSet() }.add(e.song.id)
                }
            }
            days.getOrPut(date) { ArrayList() }.add(HistoryPlay(e.event.id, e.song, e.event.timestamp))
            dayMs.merge(date, played, Long::plus)
        }

        return ListeningSummary(
            totals = StatsPeriod.entries.map { PeriodTotal(it, ms[it] ?: 0L, songs[it]?.size ?: 0) },
            days = days.map { (date, plays) -> HistoryDay(date, dayMs[date] ?: 0L, plays) },
        )
    }
}
