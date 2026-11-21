package com.shiny.music.ui.liquid.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shiny.music.R
import com.shiny.music.db.MusicDatabase
import com.shiny.music.db.entities.Song
import com.shiny.music.stats.PeriodTotal
import com.shiny.music.stats.StatsPeriod
import com.shiny.music.stats.StatsReport
import com.shiny.music.ui.liquid.EmptyState
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.PageMargin
import java.time.LocalDate

private fun rowDelay(order: Int) = 180 + 40 * order.coerceAtMost(9)

private val NoTotals = StatsPeriod.entries.map { PeriodTotal(it, 0L, 0) }

@Composable
fun StatsPage(
    database: MusicDatabase,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onMenu: (Song) -> Unit,
) {
    val summary by remember(database) { StatsReport.summary(database) }.collectAsState(initial = null)
    val today = remember(summary) { LocalDate.now() }
    val state = rememberLazyListState()
    val clock = rememberStatsClock()
    val totals = summary?.totals ?: NoTotals

    CompositionLocalProvider(LocalStatsClock provides clock) {
        LargeTitlePage(
            title = stringResource(R.string.stats_title),
            state = state,
            navigationButton = { LiquidBackButton(onClick = onBack) },
            behind = {
                StatsAmbient(
                    scrolled = {
                        if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else 100_000f
                    },
                )
            },
        ) {
            item(key = "today", contentType = "today") {
                Rise(0, Modifier.padding(horizontal = PageMargin).padding(top = 14.dp)) {
                    TodayCard(totals.first { it.period == StatsPeriod.Today })
                }
            }
            item(key = "periods", contentType = "periods") {
                Rise(70, Modifier.padding(horizontal = PageMargin).padding(top = 14.dp)) {
                    PeriodsCard(totals.filter { it.period != StatsPeriod.Today })
                }
            }

            val loaded = summary ?: return@LargeTitlePage
            if (loaded.isEmpty) {
                item(key = "empty", contentType = "empty") {
                    Rise(140) {
                        EmptyState(
                            icon = Icons.Rounded.History,
                            title = stringResource(R.string.stats_empty),
                            message = stringResource(R.string.stats_empty_hint),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                return@LargeTitlePage
            }

            item(key = "history", contentType = "history_title") { Rise(140) { HistoryTitle() } }
            var order = 0
            loaded.days.forEach { day ->
                val dayOrder = order++
                item(key = "d-${day.date}", contentType = "day") {
                    Rise(rowDelay(dayOrder)) { DayHeader(day, today) }
                }
                val queue = day.plays.map { it.song }
                day.plays.forEachIndexed { i, play ->
                    val rowOrder = order++
                    item(key = "p-${play.eventId}", contentType = "play") {
                        Rise(rowDelay(rowOrder)) {
                            HistoryRow(
                                play = play,
                                onClick = { onPlay(queue, i) },
                                onLongClick = { onMenu(play.song) },
                            )
                        }
                    }
                }
            }
            item(key = "end", contentType = "end") { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}
