package com.shiny.music.ui.liquid.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.R
import com.shiny.music.stats.HistoryDay
import com.shiny.music.stats.HistoryPlay
import com.shiny.music.stats.PeriodTotal
import com.shiny.music.stats.StatsPeriod
import com.shiny.music.ui.liquid.InterDisplay
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.SongRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private val HeroShape = RoundedCornerShape(32.dp)
private val CardShape = RoundedCornerShape(26.dp)

@Composable
internal fun StatsAmbient(scrolled: () -> Float, modifier: Modifier = Modifier) {
    val c = Liquid.colors
    val accent = c.accent
    val dark = c.isDark
    val background = c.background
    Box(
        modifier
            .fillMaxWidth()
            .height(620.dp)
            .graphicsLayerFade(scrolled)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = if (dark) 0.42f else 0.26f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.10f),
                        radius = size.width * 1.05f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(StatsViolet.copy(alpha = if (dark) 0.30f else 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.98f, size.height * 0.34f),
                        radius = size.width * 0.85f,
                    ),
                )
                drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to background))
            },
    )
}

private fun Modifier.graphicsLayerFade(scrolled: () -> Float): Modifier =
    graphicsLayer {
        val px = scrolled()
        translationY = -px * 0.35f
        alpha = (1f - px / (size.height * 0.9f)).coerceIn(0f, 1f)
    }

@Composable
internal fun TodayCard(total: PeriodTotal, modifier: Modifier = Modifier) {
    val c = Liquid.colors
    val minutes = rememberCountUp(total.minutes)
    val songs = rememberCountUp(total.songs.toLong())
    val description = stringResource(R.string.stats_today) + ", " +
        pluralStringResource(R.plurals.stats_n_minutes, total.minutes.toInt(), total.minutes.toInt()) + ", " +
        pluralStringResource(R.plurals.stats_n_songs, total.songs, total.songs)
    Column(
        modifier
            .fillMaxWidth()
            .statsGlass(HeroShape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.width * 0.9f,
                    ),
                )
            }
            .clearAndSetSemantics { contentDescription = description }
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 22.dp),
    ) {
        Text(
            stringResource(R.string.stats_today),
            style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = c.secondaryLabel,
        )
        Text(
            count(minutes()),
            style = TextStyle(
                fontFamily = InterDisplay,
                fontSize = 76.sp,
                lineHeight = 84.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2.4).sp,
                fontFeatureSettings = Tabular,
            ),
            color = c.label,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            stringResource(R.string.stats_minutes_today),
            style = LiquidTypography.body,
            color = c.secondaryLabel,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(c.label.copy(alpha = if (c.isDark) 0.09f else 0.06f))
                .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = c.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                pluralStringResource(R.plurals.stats_n_songs, songs().toInt(), songs().toInt()),
                style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = Tabular),
                color = c.label,
            )
        }
    }
}

@Composable
internal fun PeriodsCard(totals: List<PeriodTotal>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .statsGlass(CardShape)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        totals.forEach { PeriodRow(it) }
    }
}

@Composable
private fun PeriodRow(total: PeriodTotal) {
    val c = Liquid.colors
    val label = stringResource(
        when (total.period) {
            StatsPeriod.Today -> R.string.stats_today
            StatsPeriod.Week -> R.string.stats_this_week
            StatsPeriod.Year -> R.string.stats_this_year
            StatsPeriod.All -> R.string.stats_all_time
        },
    )
    val minutes = rememberCountUp(total.minutes)
    val songs = rememberCountUp(total.songs.toLong())
    val description = label + ", " +
        pluralStringResource(R.plurals.stats_n_minutes, total.minutes.toInt(), total.minutes.toInt()) + ", " +
        pluralStringResource(R.plurals.stats_n_songs, total.songs, total.songs)
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = LiquidTypography.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.stats_n_songs, songs().toInt(), songs().toInt()),
                style = LiquidTypography.footnote.copy(fontFeatureSettings = Tabular),
                color = c.secondaryLabel,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                count(minutes()),
                style = TextStyle(
                    fontFamily = InterDisplay,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.8).sp,
                    fontFeatureSettings = Tabular,
                ),
                color = c.label,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.stats_min),
                style = LiquidTypography.footnote.copy(fontWeight = FontWeight.Medium),
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
internal fun HistoryTitle(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.stats_history),
        style = LiquidTypography.title2,
        color = Liquid.colors.label,
        modifier = modifier
            .padding(start = PageMargin, end = PageMargin, top = 38.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun DayHeader(day: HistoryDay, today: LocalDate, modifier: Modifier = Modifier) {
    val c = Liquid.colors
    val label = when (day.date) {
        today -> stringResource(R.string.stats_today)
        today.minusDays(1) -> stringResource(R.string.stats_yesterday)
        else -> remember(day.date, today) { dayLabel(day.date, today) }
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = PageMargin, end = PageMargin, top = 22.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = LiquidTypography.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            pluralStringResource(R.plurals.stats_n_minutes, day.minutes.toInt(), day.minutes.toInt()),
            style = LiquidTypography.footnote.copy(fontFeatureSettings = Tabular),
            color = c.secondaryLabel,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String {
    val locale = Locale.getDefault()
    return when {
        date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(DayStyle.FULL, locale)
        date.year == today.year -> DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"),
            locale,
        ).format(date)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)
    }
}

private val TimeFormat: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

@Composable
internal fun HistoryRow(play: HistoryPlay, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val time = remember(play.at) { TimeFormat.format(play.at) }
    SongRow(
        title = play.song.title,
        subtitle = play.song.artists.joinToString { it.name },
        artwork = play.song.thumbnailUrl,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        explicit = play.song.song.explicit,
        showSeparator = false,
        artworkSize = 48.dp,
        songId = play.song.id,
        trailing = {
            Text(
                time,
                style = LiquidTypography.footnote.copy(fontFeatureSettings = Tabular),
                color = Liquid.colors.tertiaryLabel,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
        },
    )
}
