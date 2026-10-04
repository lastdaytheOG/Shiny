package com.shiny.music.ui.liquid.player

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.shiny.music.LocalDatabase
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import com.shiny.music.constants.LyricsRomanizeChineseKey
import com.shiny.music.constants.LyricsRomanizeHindiKey
import com.shiny.music.constants.LyricsRomanizeJapaneseKey
import com.shiny.music.constants.LyricsRomanizeKoreanKey
import com.shiny.music.constants.LyricsRomanizePunjabiKey
import com.shiny.music.constants.LyricsRomanizeRussianKey
import com.shiny.music.db.entities.LyricsEntity
import com.shiny.music.lyrics.LyricsEntry
import com.shiny.music.lyrics.LyricsUtils
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.liquid.ActivityIndicator
import com.shiny.music.ui.liquid.GlassCapsule
import com.shiny.music.ui.liquid.GlassKind
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Synced lyrics in the Music app's idiom: heavy, large, left-aligned lines; the sung line
 * at full white — filling word by word, from the provider's word timings or an estimate —
 * its neighbours dimmed by distance, the page gliding to keep the current line in the upper
 * third. Scrolling the list hands control to the finger — every line readable, the page
 * stays where it is put — and a Sync lyrics capsule brings the sung line back. Romanised
 * pronunciation appears under a line when the user has it enabled for that script.
 *
 * The motion is one engine ([LyricsMotion]) driven by the playback position: see
 * LyricTimeline for the timing, LyricInk for how a line is drawn.
 */
@Composable
fun LiquidLyricsPane(
    metadata: MediaMetadata?,
    positionProvider: () -> Long,
    onSeek: (Long) -> Unit,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 28.dp,
    /**
     * How much of the page, top and bottom, lines fade across as they near its edges. The
     * pane fades its own lines: a mask over it re-rendered the whole page offscreen on every
     * frame a word moved, which is what made the lyrics stutter.
     */
    fadeTop: Float = 0.10f,
    fadeBottom: Float = 0.12f,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState()
    val raw = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }

    // The per-song correction the user has saved for this track, if any. Stored metadata,
    // not a guess, and zero unless they set it; the legacy lyrics pane honoured it and
    // this one silently did not, so a correction made there had no effect here.
    val songOffsetMs = (currentSong?.song?.lyricsOffset ?: 0).toLong()

    // A safety net only: MusicService fetches lyrics when the song starts, so by the time
    // the pane opens the row usually exists. This covers the song that was already playing
    // when the preference was turned on. There is no debounce — the guard below is the
    // database row, and re-entering the pane cannot start a second fetch.
    LaunchedEffect(metadata?.id, lyricsEntity) {
        if (metadata != null && lyricsEntity == null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    if (database.lyrics(metadata.id).firstOrNull() != null) return@launch
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.shiny.music.di.LyricsHelperEntryPoint::class.java,
                    )
                    val fetched = entryPoint.lyricsHelper().getLyrics(metadata)
                    database.query {
                        upsert(LyricsEntity(metadata.id, fetched.lyrics ?: "", fetched.providerName))
                    }
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            raw == null -> {
                ActivityIndicator(
                    color = NowPlayingInk.secondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            raw == LyricsEntity.LYRICS_NOT_FOUND || raw.isBlank() -> {
                Text(
                    text = stringResource(R.string.liquid_no_lyrics),
                    style = LiquidTypography.title3,
                    color = NowPlayingInk.secondary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            else -> {
                val synced = remember(raw) { raw.startsWith("[") && Regex("\\[\\d+:\\d+").containsMatchIn(raw) }
                val lines = remember(raw) {
                    if (synced) {
                        LyricsUtils.parseLyrics(raw).filter { it.text.isNotBlank() }.sortedBy { it.time }
                    } else {
                        raw.lines().filter { it.isNotBlank() }.map { LyricsEntry(0L, it) }
                    }
                }
                LyricsList(
                    lines = lines,
                    synced = synced,
                    positionProvider = positionProvider,
                    songOffsetMs = songOffsetMs,
                    onSeek = onSeek,
                    contentPadding = contentPadding,
                    nestedScrollConnection = nestedScrollConnection,
                    horizontalPadding = horizontalPadding,
                    fadeTop = fadeTop,
                    fadeBottom = fadeBottom,
                )
            }
        }
    }
}

@Composable
private fun LyricsList(
    lines: List<LyricsEntry>,
    synced: Boolean,
    positionProvider: () -> Long,
    songOffsetMs: Long,
    onSeek: (Long) -> Unit,
    contentPadding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection,
    horizontalPadding: Dp,
    fadeTop: Float,
    fadeBottom: Float,
) {
    val listState = rememberLazyListState()
    // The page's soft top and bottom edges, as each line's own opacity: read in the line's
    // layer, so it costs a lookup per visible line and only on frames where the page moves.
    val edgeFade: (Int) -> Float = remember(listState, fadeTop, fadeBottom) {
        { index ->
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            val item = if (items.isEmpty()) null else items.getOrNull(index - items[0].index)?.takeIf { it.index == index }
            if (item == null) {
                1f
            } else {
                val top = info.viewportStartOffset
                val height = (info.viewportEndOffset - top).toFloat().coerceAtLeast(1f)
                val center = item.offset + item.size / 2f - top
                smoothstep((center / (height * fadeTop)).coerceIn(0f, 1f)) *
                    smoothstep(((height - center) / (height * fadeBottom)).coerceIn(0f, 1f))
            }
        }
    }
    val romanization = rememberRomanizationSettings()

    if (!synced) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        ) {
            itemsIndexed(lines) { index, line ->
                PlainLyricLine(line.text, romanization, horizontalPadding, edgeFade = { edgeFade(index) })
            }
        }
        return
    }

    val playerConnection = LocalPlayerConnection.current
    val isPlaying by (playerConnection?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) })
    // Whether the page follows the sung line. A finger that scrolls the lyrics takes the page
    // and it stays where it is put — no timer pulls it back mid-read, and a paused song never
    // did — until the Sync button, a tapped line or a seek hands it back. A new song follows.
    val following = remember(lines) { mutableStateOf(true) }
    val wordPref by rememberPreference(com.shiny.music.ui.liquid.LiquidPrefs.WordByWord, true)
    val timeline = remember(lines) { buildTimeline(lines) }
    // Resolved on every frame rather than once: crossfade swaps the service's player for a
    // new instance, and a clock holding the old one would read a player that has stopped.
    val playerProvider: () -> Player? = remember(playerConnection) {
        {
            try {
                playerConnection?.player
            } catch (e: IllegalStateException) {
                null
            }
        }
    }
    val motion = rememberLyricsMotion(
        timeline = timeline,
        words = wordPref,
        playerProvider = playerProvider,
        positionProvider = positionProvider,
        playing = isPlaying,
        songOffsetMs = songOffsetMs,
    )
    // Lines being read by hand are all readable; the depth returns with the sync. One value
    // for the whole page, read in the lines' layers.
    val browse = animateFloatAsState(
        targetValue = if (following.value) 0f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 170f),
        label = "lyricsBrowse",
    )
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val glow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Where the line begins, undoing the per-song offset, so tapping a line lands exactly on
    // its first word rather than somewhere inside it (the line lead makes it current a beat
    // before that, never after).
    val onSeekLine: (Int) -> Unit = remember(timeline, songOffsetMs, onSeek) {
        { index -> onSeek((timeline.lines[index].start - songOffsetMs).coerceAtLeast(0L)) }
    }

    val touchSlop = LocalViewConfiguration.current.touchSlop
    LaunchedEffect(listState, following) {
        // Only a finger counts as the user taking over; the page's own glide does not.
        var startIndex = 0
        var startOffset = 0
        var wasFollowing = true
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    startIndex = listState.firstVisibleItemIndex
                    startOffset = listState.firstVisibleItemScrollOffset
                    wasFollowing = following.value
                    following.value = false
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    // A drag the list never moved for — the player sheet took it, or it was
                    // a nudge at the end of the lyrics — leaves the page following.
                    val moved = listState.firstVisibleItemIndex != startIndex ||
                        abs(listState.firstVisibleItemScrollOffset - startOffset) > touchSlop
                    if (wasFollowing && !moved) following.value = true
                }
            }
        }
    }
    LaunchedEffect(listState, motion, following) { followSungLine(listState, motion, following) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        ) {
            items(timeline.size, key = { it }) { index ->
                SyncedLyricLine(
                    line = timeline.lines[index],
                    index = index,
                    motion = motion,
                    measurer = measurer,
                    glow = glow,
                    browse = browse,
                    romanization = romanization,
                    horizontalPadding = horizontalPadding,
                    edgeFade = edgeFade,
                    onSeekLine = onSeekLine,
                )
            }
        }
        ResyncCapsule(
            following = following,
            listState = listState,
            motion = motion,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        )
    }
}

/** The Sync button's slot: shown while the reader has the page, and only this reads that. */
@Composable
private fun ResyncCapsule(
    following: MutableState<Boolean>,
    listState: LazyListState,
    motion: LyricsMotion,
    modifier: Modifier,
) {
    AnimatedVisibility(
        visible = !following.value,
        enter = fadeIn(tween(180)) +
            scaleIn(spring(dampingRatio = 0.62f, stiffness = 520f), initialScale = 0.82f) +
            slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 2 },
        exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.9f),
        modifier = modifier,
    ) {
        ResyncButton(
            listState = listState,
            currentIndex = { motion.line },
            onClick = { following.value = true },
        )
    }
}

/**
 * The way back to the sung line once a finger has taken the lyrics: a small capsule of
 * clear glass floating low over the page, only there while the page is not following. Its
 * arrow points to where the sung line is — up the page or down it — and turns as the
 * reader scrolls past it.
 */
@Composable
private fun ResyncButton(
    listState: LazyListState,
    currentIndex: () -> Int,
    onClick: () -> Unit,
) {
    // Where the sung line sits against the point the page holds it at (a quarter down).
    // Derived, so the scroll frames only recompose this when the answer flips.
    val lineAbove by remember(listState) {
        derivedStateOf {
            val current = currentIndex()
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val item = visible.firstOrNull { it.index == current }
            when {
                current < 0 || visible.isEmpty() -> true
                item != null -> item.offset < (info.viewportEndOffset - info.viewportStartOffset) * 0.24f
                else -> current < visible.first().index
            }
        }
    }
    val rotation by animateFloatAsState(
        targetValue = if (lineAbove) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "resyncArrow",
    )
    GlassCapsule(
        kind = GlassKind.Clear,
        onClick = onClick,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        modifier = Modifier.height(40.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = NowPlayingInk.primary,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = rotation },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.liquid_lyrics_resync),
            style = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = NowPlayingInk.primary,
            maxLines = 1,
        )
    }
}

/**
 * One synced line. Its parameters never change while the song plays, so it composes once when
 * it scrolls into view and never again: its brightness, its trail behind the page's glide and
 * its ink are all evaluated from [motion] in its layer and its draw. There is no scale or lift —
 * the words stay exactly where they were set.
 */
@Composable
private fun SyncedLyricLine(
    line: LyricLineTiming,
    index: Int,
    motion: LyricsMotion,
    measurer: TextMeasurer,
    glow: Boolean,
    browse: State<Float>,
    romanization: RomanizationSettings,
    horizontalPadding: Dp,
    edgeFade: (Int) -> Float,
    onSeekLine: (Int) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val romanized by produceState<String?>(initialValue = null, line.text, romanization) {
        value = romanization.romanize(line.text)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(interactionSource = interaction, indication = null, onClick = { onSeekLine(index) })
            .padding(horizontal = horizontalPadding, vertical = 11.dp)
            .graphicsLayer {
                val cursor = motion.cursor
                val live = motion.isLive(index)
                // Only a line stepping back before an interlude needs the exact time here.
                val t = if (live && line.interlude) motion.time else motion.timeNow
                alpha = lineAlpha(line, index, cursor, live, t, motion.words, browse.value) * edgeFade(index)
                translationY = motion.velocity * waveLag(index - cursor)
                // Alpha applied to each drawing rather than to a buffer: the line is plain
                // text, so the look is the same and no offscreen pass is made per line.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        Spacer(
            Modifier
                .lyricInk(line, index, motion, measurer, LiquidTypography.lyrics, glow)
                .clearAndSetSemantics { contentDescription = line.text },
        )
        val sub = romanized
        if (!sub.isNullOrBlank() && sub != line.text) {
            Text(
                text = sub,
                style = LiquidTypography.title3.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .graphicsLayer {
                        // The line's layer stays opaque while its words carry the dimming; the
                        // pronunciation has no words of its own, so it takes the brightness the
                        // line would have as plain text, and brightens through the hand-over.
                        val cursor = motion.cursor
                        val live = motion.isLive(index)
                        if (live) {
                            val t = if (line.interlude) motion.time else motion.timeNow
                            val layer = lineAlpha(line, index, cursor, true, t, motion.words, browse.value)
                            val plain = lineAlpha(line, index, cursor, false, t, motion.words, browse.value)
                            alpha = if (layer > 0.001f) (plain / layer).coerceAtMost(1f) else 0f
                        } else {
                            alpha = 1f
                        }
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
            )
        }
        if (motion.words && line.interlude) Interlude(line, index, motion)
    }
}

/** A line of lyrics without timing: plain text, all of it readable. */
@Composable
private fun PlainLyricLine(
    text: String,
    romanization: RomanizationSettings,
    horizontalPadding: Dp,
    edgeFade: () -> Float,
) {
    val romanized by produceState<String?>(initialValue = null, text, romanization) {
        value = romanization.romanize(text)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp)
            .graphicsLayer {
                alpha = 0.92f * edgeFade()
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        Text(text = text, style = LiquidTypography.title2.copy(fontWeight = FontWeight.Bold), color = Color.White)
        val sub = romanized
        if (!sub.isNullOrBlank() && sub != text) {
            Text(
                text = sub,
                style = LiquidTypography.title3.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Apple's interlude: three dots under the last line that breathe through an instrumental
 * gap and fill one by one as the next line approaches, then fold away as it arrives.
 */
@Composable
private fun Interlude(line: LyricLineTiming, index: Int, motion: LyricsMotion) {
    val start = line.end + 300L
    val end = line.nextStart - 150L
    // Only the line just sung can be in its gap; the rest never read the clock here.
    val inGap by remember(line, index, motion) {
        derivedStateOf {
            val k = motion.line
            (k == index || k == index + 1) && motion.time.let { it >= start && it <= end }
        }
    }
    AnimatedVisibility(
        visible = inGap,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Canvas(
            Modifier
                .padding(top = 14.dp, bottom = 4.dp)
                .size(width = 64.dp, height = 18.dp)
        ) {
            val now = motion.time
            val f = ((now - start) / (end - start).coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
            val breathe = 1f + 0.12f * sin((now / 1000.0 * 2.0 * PI / 1.6).toFloat())
            val r = size.height * 0.34f
            for (i in 0 until 3) {
                val fill = ((f * 3f) - i).coerceIn(0f, 1f)
                val cx = r + i * (size.width - 2 * r) / 2f
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f + 0.65f * fill),
                    radius = r * breathe * (0.85f + 0.15f * fill),
                    center = Offset(cx, size.height / 2f),
                )
            }
        }
    }
}

/** The user's romanisation choices, applied per line off the main thread. */
@Immutable
private data class RomanizationSettings(
    val japanese: Boolean,
    val korean: Boolean,
    val chinese: Boolean,
    val hindi: Boolean,
    val punjabi: Boolean,
    val cyrillic: Boolean,
) {
    suspend fun romanize(text: String): String? = runCatching {
        when {
            japanese && LyricsUtils.isJapanese(text) && !LyricsUtils.isChinese(text) -> LyricsUtils.romanizeJapanese(text)
            korean && LyricsUtils.isKorean(text) -> LyricsUtils.romanizeKorean(text)
            chinese && LyricsUtils.isChinese(text) -> LyricsUtils.romanizeChinese(text)
            hindi && LyricsUtils.isHindi(text) -> LyricsUtils.romanizeHindi(text)
            punjabi && LyricsUtils.isPunjabi(text) -> LyricsUtils.romanizePunjabi(text)
            cyrillic && (
                LyricsUtils.isRussian(text) || LyricsUtils.isUkrainian(text) || LyricsUtils.isSerbian(text) ||
                    LyricsUtils.isBulgarian(text) || LyricsUtils.isBelarusian(text) || LyricsUtils.isKyrgyz(text) ||
                    LyricsUtils.isMacedonian(text)
                ) -> LyricsUtils.romanizeCyrillic(text)
            else -> null
        }
    }.getOrNull()
}

@Composable
private fun rememberRomanizationSettings(): RomanizationSettings {
    val japanese by rememberPreference(LyricsRomanizeJapaneseKey, true)
    val korean by rememberPreference(LyricsRomanizeKoreanKey, true)
    val chinese by rememberPreference(LyricsRomanizeChineseKey, true)
    val hindi by rememberPreference(LyricsRomanizeHindiKey, true)
    val punjabi by rememberPreference(LyricsRomanizePunjabiKey, true)
    val cyrillic by rememberPreference(LyricsRomanizeRussianKey, true)
    return remember(japanese, korean, chinese, hindi, punjabi, cyrillic) {
        RomanizationSettings(japanese, korean, chinese, hindi, punjabi, cyrillic)
    }
}
