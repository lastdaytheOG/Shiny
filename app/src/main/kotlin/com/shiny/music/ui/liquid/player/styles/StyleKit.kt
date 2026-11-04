package com.shiny.music.ui.liquid.player.styles

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.db.entities.LyricsEntity
import com.shiny.music.lyrics.LyricsEntry
import com.shiny.music.lyrics.LyricsUtils
import com.shiny.music.models.MediaMetadata
import com.shiny.music.ui.liquid.ArtworkTones
import com.shiny.music.ui.liquid.rememberArtworkTones
import com.shiny.music.ui.liquid.player.RequestLyricsIfMissing
import com.shiny.music.ui.liquid.player.formatTime
import com.shiny.music.ui.liquid.rememberGlassPress
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal val StyleInk = Color.White
internal val StyleInkSoft = Color.White.copy(alpha = 0.64f)
internal val StyleInkFaint = Color.White.copy(alpha = 0.32f)

@Immutable
internal data class StyleTones(val light: Color, val dark: Color, val vivid: Color)

@Composable
internal fun rememberStyleTones(artwork: String?): StyleTones {
    val tones = rememberArtworkTones(artwork?.resize(120, 120), ArtworkTones(Color(0xFF2C2C30), Color(0xFF55555C)))
    return remember(tones) {
        StyleTones(
            light = lerp(tones.deep, tones.vivid, 0.55f),
            dark = lerp(tones.deep, Color.Black, 0.55f),
            vivid = tones.vivid,
        )
    }
}

internal class StyleClock(
    private val position: androidx.compose.runtime.MutableLongState,
    private val duration: androidx.compose.runtime.MutableLongState,
) {
    val positionMs: Long get() = position.longValue
    val durationMs: Long get() = duration.longValue
    val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    fun moved(to: Long) {
        position.longValue = to
    }
}

@Composable
internal fun rememberStyleClock(expanded: Boolean, isPlaying: Boolean, metadata: MediaMetadata?, fast: Boolean): StyleClock {
    val playerConnection = LocalPlayerConnection.current
    val position = remember { mutableLongStateOf(0L) }
    val duration = remember { mutableLongStateOf(0L) }
    LaunchedEffect(expanded, isPlaying, metadata?.id, fast) {
        val player = playerConnection?.player ?: return@LaunchedEffect
        while (isActive) {
            position.longValue = player.currentPosition
            duration.longValue = player.duration.takeIf { it > 0 } ?: ((metadata?.duration ?: 0) * 1000L)
            delay(if (!expanded) 500L else if (fast) 80L else 200L)
        }
    }
    return remember { StyleClock(position, duration) }
}

@Composable
internal fun rememberPlayedText(clock: StyleClock): State<String> =
    remember(clock) { derivedStateOf { formatTime((clock.positionMs / 1000L) * 1000L) } }

@Composable
internal fun rememberSyncedLines(metadata: MediaMetadata?): List<LyricsEntry> {
    val playerConnection = LocalPlayerConnection.current ?: return emptyList()
    val entity by playerConnection.currentLyrics.collectAsState(initial = null)
    RequestLyricsIfMissing(metadata, entity)
    val raw = entity?.lyrics?.trim()
    return remember(raw) {
        if (raw == null || raw == LyricsEntity.LYRICS_NOT_FOUND || !raw.startsWith("[")) {
            emptyList()
        } else {
            LyricsUtils.parseLyrics(raw).filter { it.text.isNotBlank() }.sortedBy { it.time }
        }
    }
}

@Composable
internal fun rememberLineIndex(lines: List<LyricsEntry>, clock: StyleClock, offsetMs: Long = 0L): State<Int> =
    remember(lines, clock, offsetMs) {
        derivedStateOf {
            val t = clock.positionMs + offsetMs + 250L
            var lo = 0
            var hi = lines.lastIndex
            var found = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (lines[mid].time <= t) {
                    found = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            found
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StyleGlyphButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 26.dp,
    fill: Color = Color.White.copy(alpha = 0.12f),
    tint: Color = StyleInk,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    Box(
        modifier
            .size(size)
            .glassPressScale(press, 0.08f)
            .background(fill, CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
internal fun StyleTopBar(onClose: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.size(43.dp))
    }
}

@Composable
internal fun StyleTransport(
    isPlaying: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    actions: StyleActions,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    gap: Dp = 22.dp,
    vertical: Boolean = false,
) {
    val buttons: @Composable () -> Unit = {
        StyleGlyphButton(Icons.Rounded.SkipPrevious, "Previous", actions.previous, size = size * 0.8f, iconSize = size * 0.46f)
        StyleGlyphButton(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (isPlaying) "Pause" else "Play",
            actions.playPause,
            size = size,
            iconSize = size * 0.56f,
            fill = Color.White.copy(alpha = 0.9f),
            tint = Color.Black,
        )
        StyleGlyphButton(Icons.Rounded.SkipNext, "Next", actions.next, size = size * 0.8f, iconSize = size * 0.46f, enabled = canNext)
    }
    if (vertical) {
        androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) { buttons() }
    }
}

@Immutable
internal class StyleActions(
    val previous: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val seek: (Long) -> Unit,
    val close: () -> Unit,
    val more: () -> Unit,
)

@Immutable
internal class StyleSong(
    val metadata: MediaMetadata?,
    val isPlaying: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val clock: StyleClock,
    val tones: StyleTones,
    val actions: StyleActions,
) {
    val title: String get() = metadata?.title.orEmpty()
    val artist: String get() = metadata?.artists?.joinToString { it.name }.orEmpty()
    val artwork: String? get() = metadata?.thumbnailUrl
}

internal fun totalText(clock: StyleClock): String = formatTime(clock.durationMs)
