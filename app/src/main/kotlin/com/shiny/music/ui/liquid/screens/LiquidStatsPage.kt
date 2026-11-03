package com.shiny.music.ui.liquid.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shiny.music.LocalDatabase
import com.shiny.music.R
import com.shiny.music.stats.GenreShare
import com.shiny.music.stats.ListeningStats
import com.shiny.music.stats.StatsGenres
import com.shiny.music.stats.StatsKind
import com.shiny.music.stats.StatsRange
import com.shiny.music.stats.StatsReport
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.liquidBottomPadding
import com.shiny.music.ui.liquid.rememberLiquidActions
import com.shiny.music.ui.liquid.statusBarHeight
import com.shiny.music.ui.liquid.stats.GenreColors
import com.shiny.music.ui.liquid.stats.GenreDonut
import com.shiny.music.ui.liquid.stats.ListeningLine
import com.shiny.music.ui.liquid.stats.RankList
import com.shiny.music.ui.liquid.stats.RankRow
import com.shiny.music.ui.liquid.stats.StatsAccent
import com.shiny.music.ui.liquid.stats.StatsEmphasized
import com.shiny.music.ui.liquid.stats.StatsInk
import com.shiny.music.ui.liquid.stats.StatsInkMuted
import com.shiny.music.ui.liquid.stats.StatsInkSecondary
import com.shiny.music.ui.liquid.stats.StatsRuler
import com.shiny.music.ui.liquid.stats.WeekdayBars
import com.shiny.music.ui.liquid.stats.donutSlices
import com.shiny.music.ui.liquid.stats.rulerLabel
import com.shiny.music.ui.liquid.stats.shortDuration
import com.shiny.music.ui.utils.resize
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val StatsCard = Color(0xFF0F0F0F)
private val ChipBg = Color(0xFF2A2A2A)
private val PagePadding = 20.dp
private val CardKeys = listOf("total", "genre", "artist", "song", "weekday", "hour")

private fun statsText(size: Float, weight: FontWeight = FontWeight.Bold, color: Color = StatsInk, tracking: Float = -0.02f * size) =
    TextStyle(fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

private fun monthShort(m: Int) = Month.of(m).getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
private fun monthFull(m: Int) = Month.of(m).getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
private fun dayShort(d: Int) = DayOfWeek.of(d).getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
private fun hourLabel(h: Int): String = LocalTime.of(h, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private sealed interface GenreState {
    data object Loading : GenreState
    data object Unavailable : GenreState
    class Ready(val shares: List<GenreShare>) : GenreState
}

// what you've listened to: today, this week, a month or a year off the ruler, or all time.
// one big number, then genres, your artist and song, and when you like to listen
@Composable
fun LiquidStatsScreen(navController: NavController) {
    val actions = rememberLiquidActions(navController) ?: return
    val database = LocalDatabase.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val today = remember { LocalDate.now() }
    var kind by rememberSaveable { mutableStateOf(StatsKind.Month) }
    var year by rememberSaveable { mutableStateOf(today.year) }
    var month by rememberSaveable { mutableStateOf(today.monthValue) }
    val range = when (kind) {
        StatsKind.Month -> StatsRange(kind, year, month)
        StatsKind.Year -> StatsRange(kind, year)
        else -> StatsRange(kind)
    }
    var firstDay by remember { mutableStateOf<LocalDate?>(null) }
    var stats by remember { mutableStateOf<ListeningStats?>(null) }
    LaunchedEffect(range) {
        val (s, first) = StatsReport.load(database, range)
        stats = s
        firstDay = first ?: today
    }
    var genres by remember { mutableStateOf<GenreState>(GenreState.Loading) }
    LaunchedEffect(stats) {
        val s = stats ?: return@LaunchedEffect
        genres = GenreState.Loading
        genres = StatsGenres.shares(context, s.topArtists)?.let { GenreState.Ready(it) } ?: GenreState.Unavailable
    }
    val start = firstDay ?: today
    val available = remember(kind, start, today) {
        if (kind == StatsKind.Month) {
            generateSequence(YearMonth.from(start)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(today)) }
                .map { StatsRange(StatsKind.Month, it.year, it.monthValue) }.toSet()
        } else {
            (start.year..today.year).map { StatsRange(StatsKind.Year, it) }.toSet()
        }
    }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val allOpen = CardKeys.all { expanded[it] == true }
    val listState = rememberLazyListState()
    val topInset = statusBarHeight()
    val collapse by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / with(density) { 90.dp.toPx() }).coerceIn(0f, 1f) } }

    val sToday = stringResource(R.string.stats_today)
    val sWeek = stringResource(R.string.stats_week)
    val sMonth = stringResource(R.string.stats_month)
    val sYear = stringResource(R.string.stats_year)
    val sAll = stringResource(R.string.stats_all)
    val lead = when (kind) {
        StatsKind.Day -> sToday
        StatsKind.Week -> sWeek
        StatsKind.Month -> if (range.isCurrent) sMonth else stringResource(R.string.stats_in, monthFull(month))
        StatsKind.Year -> if (range.isCurrent) sYear else stringResource(R.string.stats_in, "$year")
        StatsKind.All -> sAll
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topInset + 64.dp, bottom = liquidBottomPadding(120.dp)),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Row(
                    Modifier
                        .padding(horizontal = PagePadding)
                        .graphicsLayer { alpha = 1f - collapse },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(stringResource(R.string.stats_title), style = statsText(44f, tracking = -1.6f))
                    Spacer(Modifier.width(8.dp))
                    Text("$year", style = statsText(22f, color = StatsInkMuted, tracking = -0.6f), modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            item(key = "periods") {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = PagePadding, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(StatsKind.Day to sToday, StatsKind.Week to sWeek, StatsKind.Month to sMonth, StatsKind.Year to sYear, StatsKind.All to sAll).forEach { (k, label) ->
                        PeriodPill(label, selected = kind == k) {
                            kind = k
                            if (k == StatsKind.Month || k == StatsKind.Year) {
                                year = today.year
                                month = today.monthValue
                            }
                        }
                    }
                }
            }
            if (kind == StatsKind.Month || kind == StatsKind.Year) {
                item(key = "ruler") {
                    StatsRuler(selected = range, available = available, today = today, onSelect = { p ->
                        year = p.year
                        if (p.kind == StatsKind.Month) month = p.month
                    })
                }
            }
            item(key = "expandAll") {
                Row(
                    Modifier
                        .padding(start = PagePadding, top = 4.dp, bottom = 14.dp)
                        .clip(CircleShape)
                        .background(ChipBg)
                        .clickable {
                            val open = !allOpen
                            CardKeys.forEach { expanded[it] = open }
                        }
                        .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(if (allOpen) R.string.stats_collapse_all else R.string.stats_expand_all), style = statsText(13f, FontWeight.SemiBold, tracking = 0f))
                    Spacer(Modifier.width(4.dp))
                    Chevron(open = allOpen, size = 18)
                }
            }
            val s = stats
            if (s == null) {
                item(key = "loading") { Spacer(Modifier.height(400.dp)) }
            } else if (s.isEmpty) {
                item(key = "empty") {
                    StatsCardBox {
                        Text(stringResource(R.string.stats_nothing, lead.lowercase()), style = statsText(17f))
                        Text(stringResource(R.string.stats_nothing_hint), style = statsText(13f, FontWeight.Medium, StatsInkMuted, 0f), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                item(key = "total_${s.range}") { TotalCard(s, lead, expanded) }
                item(key = "genre") { GenreCard(genres, expanded) }
                s.topArtists.firstOrNull()?.let { top ->
                    item(key = "artist") {
                        StatsCardBox(
                            expandKey = "artist",
                            expanded = expanded,
                            detail = {
                                RankList(
                                    rows = s.topArtists.take(10).map { a ->
                                        RankRow(a.name, "${shortDuration(a.playMs)} · ${pluralStringResource(R.plurals.stats_n_plays, a.plays, a.plays)}", a.playMs, a.photo, onClick = { navController.navigate("artist/${a.id}") })
                                    },
                                    circle = true,
                                )
                            },
                        ) {
                            Text(stringResource(R.string.stats_love_artist), style = statsText(15f))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                                Artwork(model = top.photo?.resize(240, 240), shape = CircleShape, hairline = false, modifier = Modifier.size(80.dp))
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(top.name, style = statsText(24f, tracking = -0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(R.string.stats_artist_detail, shortDuration(top.playMs), top.plays, top.name),
                                        style = statsText(12f, FontWeight.SemiBold, StatsInkMuted, 0f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                s.topSongs.firstOrNull()?.let { top ->
                    item(key = "song") {
                        val songs = s.topSongs.map { it.song }
                        val queueTitle = stringResource(R.string.stats_title)
                        StatsCardBox(
                            expandKey = "song",
                            expanded = expanded,
                            detail = {
                                RankList(
                                    rows = s.topSongs.mapIndexed { i, song ->
                                        RankRow(
                                            song.title,
                                            "${song.artist} · ${pluralStringResource(R.plurals.stats_n_plays, song.plays, song.plays)} · ${shortDuration(song.playMs)}",
                                            song.plays.toLong(),
                                            song.cover,
                                            onClick = { actions.playSongs(queueTitle, songs, i) },
                                        )
                                    },
                                    circle = false,
                                )
                            },
                        ) {
                            Text(stringResource(R.string.stats_top_song), style = statsText(15f))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                                Artwork(model = top.cover?.resize(240, 240), shape = RoundedCornerShape(12.dp), hairline = false, modifier = Modifier.size(78.dp))
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(stringResource(R.string.stats_song_from, top.title, top.artist), style = statsText(20f, tracking = -0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.stats_song_detail, shortDuration(top.playMs), top.plays), style = statsText(12f, FontWeight.SemiBold, StatsInkMuted, 0f))
                                }
                            }
                        }
                    }
                }
                item(key = "often") {
                    Text(stringResource(R.string.stats_how_often), style = statsText(16f), modifier = Modifier.padding(start = PagePadding, end = PagePadding, top = 18.dp, bottom = 12.dp))
                }
                if (kind != StatsKind.Day) {
                    item(key = "weekday") {
                        val best = s.weekdayMs.indices.maxByOrNull { s.weekdayMs[it] } ?: 0
                        StatsCardBox(
                            expandKey = "weekday",
                            expanded = expanded,
                            detail = { WeekdayBars(values = s.weekdayMs, labels = DayOfWeek.entries.map { it.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()) }) },
                        ) {
                            Text(stringResource(R.string.stats_weekday_lead, lead), style = statsText(13f, FontWeight.SemiBold))
                            Text(DayOfWeek.of(best + 1).getDisplayName(JavaTextStyle.FULL, Locale.getDefault()), style = statsText(32f, tracking = -1.2f), modifier = Modifier.padding(vertical = 2.dp))
                            Text(stringResource(R.string.stats_totaling_hours, hoursText(s.weekdayMs[best])), style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f))
                        }
                    }
                }
                item(key = "hour") {
                    val best = s.hourMs.indices.maxByOrNull { s.hourMs[it] } ?: 0
                    val part = stringResource(
                        when (best) {
                            in 5..11 -> R.string.stats_morning
                            in 12..16 -> R.string.stats_afternoon
                            in 17..20 -> R.string.stats_evening
                            else -> R.string.stats_night
                        },
                    )
                    StatsCardBox(
                        expandKey = "hour",
                        expanded = expanded,
                        detail = { ListeningLine(values = s.hourMs, labelAt = { h -> if (h % 6 == 0) hourLabel(h) else null }, nameOf = { h -> hourLabel(h) }) },
                    ) {
                        Text(stringResource(R.string.stats_hour_lead, part), style = statsText(13f, FontWeight.SemiBold))
                        Text(hourLabel(best), style = statsText(32f, tracking = -1.2f), modifier = Modifier.padding(vertical = 2.dp))
                        Text(stringResource(R.string.stats_hour_total, hoursText(s.hourMs[best])), style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f))
                    }
                }
                item(key = "privacy") {
                    Text(stringResource(R.string.stats_privacy), style = statsText(11f, FontWeight.SemiBold, StatsInkMuted, 0f), modifier = Modifier.padding(horizontal = PagePadding + 4.dp, vertical = 10.dp))
                }
            }
        }

        // the bar: a fade behind it, and "Stats" coming in beside the back button once the title has gone
        Box(
            Modifier
                .fillMaxWidth()
                .height(topInset + 64.dp)
                .graphicsLayer { alpha = collapse }
                .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent))),
        )
        val titleGone by remember { derivedStateOf { collapse > 0.5f } }
        val morph by animateFloatAsState(if (titleGone) 1f else 0f, tween(600, easing = StatsEmphasized), label = "statsBar")
        Row(
            Modifier.padding(start = 16.dp, top = topInset + 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidBackButton(onClick = { navController.navigateUp() })
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.graphicsLayer {
                    alpha = morph
                    translationX = (1f - morph) * 12.dp.toPx()
                },
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(stringResource(R.string.stats_title), style = statsText(28f, tracking = -1f))
                Spacer(Modifier.width(6.dp))
                Text(if (kind == StatsKind.Month || kind == StatsKind.Year) rulerLabel(range) else lead, style = statsText(15f, color = StatsInkMuted, tracking = -0.3f), modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

private fun hoursText(ms: Long): String = String.format(Locale.getDefault(), "%.1f", ms / 3_600_000.0)

@Composable
private fun PeriodPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else ChipBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = statsText(13f, FontWeight.SemiBold, if (selected) Color.Black else StatsInk, 0f))
    }
}

@Composable
private fun StatsCardBox(
    expandKey: String? = null,
    expanded: MutableMap<String, Boolean>? = null,
    detail: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val open = expandKey != null && expanded?.get(expandKey) == true
    Column(
        Modifier
            .padding(horizontal = PagePadding, vertical = 7.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(StatsCard)
            .then(if (expandKey != null && expanded != null) Modifier.clickable { expanded[expandKey] = !open } else Modifier)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
    ) {
        Box {
            Column(Modifier.padding(end = if (detail != null) 30.dp else 0.dp)) { content() }
            if (detail != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(ChipBg),
                    contentAlignment = Alignment.Center,
                ) { Chevron(open = open, size = 18) }
            }
        }
        if (detail != null) {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(tween(420, easing = StatsEmphasized)) + fadeIn(tween(300, 120)),
                exit = shrinkVertically(tween(320, easing = StatsEmphasized)) + fadeOut(tween(160)),
            ) {
                Column(Modifier.padding(top = 18.dp, end = 4.dp)) { detail() }
            }
        }
    }
}

@Composable
private fun Chevron(open: Boolean, size: Int) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(320, easing = StatsEmphasized), label = "chevron")
    Icon(Icons.Rounded.ExpandMore, null, tint = Color.White, modifier = Modifier.size(size.dp).graphicsLayer { rotationZ = turn })
}

// the number counts up to itself each time the period changes
@Composable
private fun rememberRoll(key: Any?): () -> Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(1400, easing = StatsEmphasized)) }
    return { a.value }
}

@Composable
private fun TotalCard(s: ListeningStats, lead: String, expanded: MutableMap<String, Boolean>) {
    val minutes = s.totalMs / 60_000L
    val roll = rememberRoll(s)
    val big = statsText(52f, tracking = -2.4f)
    val unit = statsText(22f, tracking = -0.5f)
    val chartTitle = when (s.range.kind) {
        StatsKind.Day -> stringResource(R.string.stats_every_hour)
        StatsKind.Week -> stringResource(R.string.stats_every_day_week)
        StatsKind.Month -> stringResource(R.string.stats_every_day_of, monthFull(s.range.month))
        StatsKind.Year -> stringResource(R.string.stats_every_month_of, "${s.range.year}")
        StatsKind.All -> stringResource(R.string.stats_every_year)
    }
    StatsCardBox(
        expandKey = "total",
        expanded = expanded,
        detail = {
            Text(chartTitle, style = statsText(13f, FontWeight.SemiBold, StatsInkMuted, 0f), modifier = Modifier.padding(bottom = 8.dp))
            when (s.range.kind) {
                StatsKind.Day -> ListeningLine(s.buckets, labelAt = { h -> if (h % 6 == 0) hourLabel(h) else null }, nameOf = { h -> hourLabel(h) })
                StatsKind.Week -> WeekdayBars(s.buckets, DayOfWeek.entries.map { it.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()) })
                StatsKind.Month -> ListeningLine(s.buckets, labelAt = { i -> if (i == 0 || (i + 1) % 7 == 0) "${i + 1}" else null }, nameOf = { i -> "${monthShort(s.range.month)} ${i + 1}" })
                StatsKind.Year -> ListeningLine(s.buckets, labelAt = { i -> if (i % 3 == 0) monthShort(i + 1) else null }, nameOf = { i -> monthFull(i + 1) })
                StatsKind.All -> ListeningLine(s.buckets, labelAt = { i -> "${s.bucketStart + i}" }, nameOf = { i -> "${s.bucketStart + i}" })
            }
        },
    ) {
        Text(stringResource(R.string.stats_listening_for, lead), style = statsText(15f))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            val shown = (minutes * roll()).toLong()
            Text("${shown / 60}", style = big)
            Text("h", style = unit, modifier = Modifier.padding(bottom = 8.dp))
            Text(":", style = big)
            Text((shown % 60).toString().padStart(2, '0'), style = big)
            Text("m", style = unit, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF3A3A3A)),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Fact(pluralStringResource(R.plurals.stats_n_songs, s.songCount, s.songCount))
            Fact(pluralStringResource(R.plurals.stats_n_artists, s.artistCount, s.artistCount))
            Fact(pluralStringResource(R.plurals.stats_n_albums, s.albumCount, s.albumCount))
        }
    }
}

@Composable
private fun Fact(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFD9D9D9)),
        )
        Spacer(Modifier.width(5.dp))
        Text(text, style = statsText(13f, FontWeight.SemiBold, StatsInkSecondary, 0f), maxLines = 1)
    }
}

@Composable
private fun GenreCard(state: GenreState, expanded: MutableMap<String, Boolean>) {
    val shares = (state as? GenreState.Ready)?.shares
    StatsCardBox(
        expandKey = if (shares != null) "genre" else null,
        expanded = expanded,
        detail = shares?.let {
            {
                RankList(
                    rows = it.mapIndexed { i, g ->
                        RankRow(g.name, "${(g.fraction * 100).toInt()}% · ${shortDuration(g.playMs)}", g.playMs, swatch = GenreColors.getOrElse(i) { com.shiny.music.ui.liquid.stats.GenreOther }, noImage = true)
                    },
                    circle = true,
                )
            }
        },
    ) {
        Text(stringResource(R.string.stats_dominant_genre), style = statsText(15f))
        when (state) {
            GenreState.Loading -> Text(stringResource(R.string.stats_genre_loading), style = statsText(14f, FontWeight.Medium, StatsInkMuted, 0f), modifier = Modifier.padding(vertical = 30.dp))
            GenreState.Unavailable -> Text(stringResource(R.string.stats_genre_unavailable), style = statsText(14f, FontWeight.Medium, StatsInkMuted, 0f), modifier = Modifier.padding(vertical = 18.dp))
            is GenreState.Ready -> {
                val top = state.shares.first()
                Text(top.name, style = statsText(24f, tracking = -0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    GenreDonut(state.shares)
                    Box(
                        Modifier
                            .padding(horizontal = 14.dp)
                            .width(34.dp)
                            .height(1.dp)
                            .background(StatsInkSecondary),
                    )
                    Column {
                        Text("${(top.fraction * 100).toInt()}%", style = statsText(22f))
                        Text(top.name, style = statsText(12f, FontWeight.SemiBold, StatsInkSecondary, 0f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // never by colour alone: the top five are named under the ring
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                    donutSlices(state.shares).take(5).forEachIndexed { i, (_, color) ->
                        val name = state.shares.getOrNull(i)?.name ?: return@forEachIndexed
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(name, style = statsText(11f, FontWeight.Medium, StatsInkMuted, 0f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(52.dp))
                        }
                    }
                }
            }
        }
    }
}
