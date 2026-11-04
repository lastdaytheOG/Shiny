package com.shiny.music.ui.liquid.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.liquid.Artwork
import com.shiny.music.ui.liquid.customize.PlayerStylePrefs
import com.shiny.music.ui.liquid.player.LiquidScrubber
import com.shiny.music.ui.utils.resize
import com.shiny.music.utils.rememberPreference
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NightInk = Color(0xFFB0322A)
private val DayInk = Color.White.copy(alpha = 0.88f)
private val TileFill = Color(0xFF141416)

private data class BedsideTime(val time: String, val date: String, val night: Boolean)

@Composable
private fun rememberBedsideTime(): BedsideTime {
    val h24 by rememberPreference(PlayerStylePrefs.Bedside24h, false)
    val seconds by rememberPreference(PlayerStylePrefs.BedsideSeconds, false)
    fun read(): BedsideTime {
        val now = LocalDateTime.now()
        val pattern = when {
            h24 && seconds -> "HH:mm:ss"
            h24 -> "HH:mm"
            seconds -> "h:mm:ss"
            else -> "h:mm"
        }
        return BedsideTime(
            time = now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())),
            date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
            night = now.hour >= 22 || now.hour < 6,
        )
    }
    var time by remember(h24, seconds) { mutableStateOf(read()) }
    LaunchedEffect(h24, seconds) {
        while (true) {
            time = read()

            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    return time
}

@Composable
private fun bedsideInk(time: BedsideTime): Color {
    val nightPref by rememberPreference(PlayerStylePrefs.BedsideNight, true)
    return if (nightPref && time.night) NightInk else DayInk
}

@Composable
private fun KeepAwake(expanded: Boolean) {
    val awake by rememberPreference(PlayerStylePrefs.BedsideAwake, true)
    val context = LocalContext.current
    DisposableEffect(expanded, awake) {
        val window = (context as? android.app.Activity)?.window
        val on = expanded && awake
        if (on) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (on) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
internal fun BedsidePlayer(song: StyleSong, expanded: Boolean) {
    KeepAwake(expanded)
    val time = rememberBedsideTime()
    val ink = bedsideInk(time)
    val pager = rememberPagerState { 2 }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val size = (maxWidth.value * if (time.time.length > 5) 0.2f else 0.27f).coerceAtMost(maxHeight.value * 0.4f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(time.date, color = ink.copy(alpha = 0.6f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(
                            time.time,
                            color = ink,
                            fontSize = size.sp,
                            lineHeight = size.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-0.03 * size).sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (song.metadata != null) {
                            Text(
                                "${song.title}  ·  ${song.artist}",
                                color = ink.copy(alpha = 0.42f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            } else {
                MusicTile(song, ink = DayInk, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp))
            }
        }
        StyleTopBar(onClose = song.actions.close, onMore = song.actions.more, modifier = Modifier.padding(top = 8.dp))

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp + LocalStyleBottomReserve.current),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            repeat(2) { i ->
                Box(Modifier.size(6.dp).background(ink.copy(alpha = if (pager.currentPage == i) 0.7f else 0.22f), CircleShape))
            }
        }
    }
}

@Composable
internal fun BedsideTilesPlayer(song: StyleSong, expanded: Boolean) {
    KeepAwake(expanded)
    val clockTile by rememberPreference(PlayerStylePrefs.BedsideClockTile, true)
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        StyleTopBar(onClose = song.actions.close, onMore = song.actions.more, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        val tiles: @Composable (Modifier, Modifier) -> Unit = { first, second ->
            MusicTile(song, ink = DayInk, modifier = first.clip(RoundedCornerShape(28.dp)).background(TileFill))
            Box(second.clip(RoundedCornerShape(28.dp)).background(TileFill)) {
                if (clockTile) {
                    ClockTile(Modifier.fillMaxSize())
                } else {
                    StageWords(song, Modifier.fillMaxSize().padding(22.dp), size = 30f)
                }
            }
        }
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp + LocalStyleBottomReserve.current),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) { tiles(Modifier.weight(1f).fillMaxSize(), Modifier.weight(1f).fillMaxSize()) }
        } else {
            Column(
                Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp + LocalStyleBottomReserve.current),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { tiles(Modifier.weight(1.15f).fillMaxWidth(), Modifier.weight(0.85f).fillMaxWidth()) }
        }
    }
}

@Composable
private fun ClockTile(modifier: Modifier) {
    val time = rememberBedsideTime()
    val ink = bedsideInk(time)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = (maxWidth.value * if (time.time.length > 5) 0.19f else 0.26f).coerceAtMost(maxHeight.value * 0.5f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time.date, color = ink.copy(alpha = 0.55f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                time.time,
                color = ink,
                fontSize = size.sp,
                lineHeight = size.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.03 * size).sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun MusicTile(song: StyleSong, ink: Color, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cover = minOf(maxWidth * 0.42f, maxHeight * 0.36f, 180.dp)
        val px = with(LocalDensity.current) { cover.roundToPx() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(
                model = song.artwork?.resize(px, px),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(cover),
            )
            Spacer(Modifier.height(14.dp))
            Text(song.title, color = ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(song.artist, color = ink.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            LiquidScrubber(
                positionProvider = { song.clock.positionMs },
                durationProvider = { song.clock.durationMs },
                onSeek = song.actions.seek,
            )
            StyleTransport(
                isPlaying = song.isPlaying,
                canPrevious = song.canPrevious,
                canNext = song.canNext,
                actions = song.actions,
                size = 54.dp,
                gap = 24.dp,
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}
