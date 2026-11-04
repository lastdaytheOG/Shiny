package com.shiny.music.ui.liquid.player.styles.ring

import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.shiny.music.LocalPlayerConnection
import com.shiny.music.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

internal val RingFont = FontFamily(Font(R.font.inter_variable))

data class RubyToken(val start: Int, val end: Int, val text: String)

const val MinInterludeGapMs = 4_000L

enum class LyricsAlignment { START, CENTER, END }

internal val LyricsHeaderArtX = 36.dp

internal const val LyricsRecenterDurationMs = 950

internal val LyricsRecenterEasing = androidx.compose.animation.core.CubicBezierEasing(0.5f, 0.45f, 0f, 1f)

internal fun splitsIntoLetters(text: String, start: Int, end: Int): Boolean {
    var i = start
    while (i < end) {
        val cp = text.codePointAt(i)
        when (Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.ARMENIAN,
            Character.UnicodeScript.GEORGIAN,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED -> Unit
            else -> return false
        }
        i += Character.charCount(cp)
    }
    return true
}

object BlurEffectCache {
    private val cache = ConcurrentHashMap<Int, RenderEffect>()

    fun get(radius: Float, tileMode: Shader.TileMode = Shader.TileMode.CLAMP): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) return null
        val effective = if (Build.VERSION.SDK_INT <= 34) radius.coerceAtMost(54f) else radius
        val quant = when {
            effective <= 16f -> 0.5f
            effective <= 32f -> 1f
            effective <= 64f -> 2f
            else -> 4f
        }
        val step = (effective / quant).roundToInt().coerceAtLeast(1)
        val key = step * 100 + (quant * 10).toInt() + tileMode.ordinal
        return cache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect.createBlurEffect(r, r, tileMode).asComposeRenderEffect()
        }
    }

    fun element(radius: Float, tileMode: Shader.TileMode = Shader.TileMode.CLAMP): RenderEffect? =
        if (com.shiny.music.ui.liquid.customize.CustomizeLook.elementBlur) get(radius, tileMode) else null
}

val LocalStyleInteractive = compositionLocalOf<() -> Boolean> { { true } }

val LocalRingShowing = compositionLocalOf<() -> Boolean> { { true } }

@Composable
internal fun rememberStyleShowing(): () -> Boolean = LocalRingShowing.current

@Stable
class LivePosition internal constructor(private val read: () -> Long, private val tick: () -> Long) {
    private var readAt = Long.MIN_VALUE
    private var value = 0L

    fun positionMs(): Long {
        val now = tick()
        if (now != readAt) {
            readAt = now
            value = read()
        }
        return value
    }

    fun shifted(shiftMs: () -> Long) = LivePosition(read = { read() + shiftMs() }, tick = tick)
}

private class SmoothClock(private val player: () -> Player?) : () -> Long {
    private var lastRaw = Long.MIN_VALUE
    private var base = 0L
    private var baseAt = 0L
    private var shown = 0L

    override fun invoke(): Long {
        val p = player() ?: return shown
        val raw = p.currentPosition
        val now = SystemClock.elapsedRealtime()
        if (raw != lastRaw) {
            lastRaw = raw
            base = raw
            baseAt = now
        }
        val next = if (p.isPlaying) {
            base + ((now - baseAt) * p.playbackParameters.speed).toLong().coerceAtMost(600L)
        } else {
            raw
        }
        shown = if (p.isPlaying && next < shown && shown - next < 400L) shown else next
        return shown
    }
}

@Composable
fun LivePosition.withLyricsOffset(): LivePosition {
    val connection = LocalPlayerConnection.current ?: return this
    val song by connection.currentSong.collectAsState()
    val offset by rememberUpdatedState((song?.song?.lyricsOffset ?: 0).toLong())
    return remember(this, connection) { shifted { offset } }
}

@Composable
fun rememberLivePosition(animated: Boolean = true): LivePosition {
    val connection = LocalPlayerConnection.current
    val tick = remember { mutableLongStateOf(0L) }
    val showing = rememberStyleShowing()
    LaunchedEffect(connection, animated, showing) {
        if (connection == null) return@LaunchedEffect
        combine(connection.isPlaying, snapshotFlow { showing() }) { playing, shown -> playing to shown }
            .collectLatest { (playing, shown) ->
                if (!shown) return@collectLatest
                tick.longValue++
                while (playing && animated) withFrameMillis { tick.longValue = it }
            }
    }
    return remember(connection) {
        val clock = SmoothClock { runCatching { connection?.player }.getOrNull() }
        LivePosition(read = clock, tick = { tick.longValue })
    }
}

@Immutable
class Responsive(val shortSideDp: Float) {
    fun dp(fraction: Float, min: Dp, max: Dp): Dp = (shortSideDp * fraction).dp.coerceIn(min, max)
    fun sp(fraction: Float, min: Float, max: Float): TextUnit = (shortSideDp * fraction).coerceIn(min, max).sp
}

@Composable
fun rememberResponsive(): Responsive {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val short = minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat()
    return remember(short) { Responsive(short) }
}

@Immutable
data class StyleTones(
    val ink: Color = Color(0xFFF1ECEC),
    val inkSoft: Color = Color(0xFFF1ECEC).copy(alpha = 0.62f),
    val chip: Color = Color.White.copy(alpha = 0.22f),
)

private val PillFill = Color.White.copy(alpha = 0.08f)
private val ActiveFill = Color.White.copy(alpha = 0.32f)
internal const val ChevronMenuDim = 0.72f

@Composable
fun StyleCircle(
    icon: ImageVector,
    tones: StyleTones,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    enabled: Boolean = true,
    pill: Boolean = false,
    active: Boolean = false,
) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
                alpha = if (enabled) 1f else 0.4f
            }
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    active -> ActiveFill
                    pill -> PillFill
                    else -> tones.chip
                }
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        scope.launch { press.animateTo(0.86f, spring(dampingRatio = 0.6f, stiffness = 900f)) }
                        tryAwaitRelease()
                        scope.launch { press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                    },
                    onTap = { currentOnClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = when {
            active -> Color.White
            pill -> Color.White.copy(alpha = 0.9f)
            else -> tones.ink
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (pill) 22.dp else size * 0.5f))
    }
}

private class MenuItem(val label: String, val icon: ImageVector, val active: Boolean = false, val onClick: () -> Unit)

@Composable
fun BoxScope.StyleMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    tones: StyleTones,
    onOtherMenu: () -> Unit,
    onEditPlayer: () -> Unit,
    end: Dp,
    onLyrics: (() -> Unit)? = null,
    lyricsActive: Boolean = false,
    onQueue: (() -> Unit)? = null,
    queueActive: Boolean = false,
    edge: Dp,
    buttonSize: Dp = 30.dp,
    pill: Boolean = false,
    showRepeat: Boolean = true,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(open) {
        reveal.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 420f))
    }
    if (reveal.value > 0.001f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = ChevronMenuDim * reveal.value.coerceIn(0f, 1f))) }
                .pointerInput(Unit) { detectTapGestures { onOpenChange(false) } },
        )
        val connection = LocalPlayerConnection.current
        val shuffle = connection?.shuffleModeEnabled?.collectAsState()?.value == true
        val repeat = connection?.repeatMode?.collectAsState()?.value ?: Player.REPEAT_MODE_OFF
        val rows = buildList {
            if (onLyrics != null) add(MenuItem("Lyrics", Icons.Rounded.Lyrics, active = lyricsActive) { onOpenChange(false); onLyrics() })
            if (onQueue != null) add(MenuItem("Queue", Icons.AutoMirrored.Rounded.QueueMusic, active = queueActive) { onOpenChange(false); onQueue() })
            if (connection != null) {
                add(MenuItem("Shuffle", Icons.Rounded.Shuffle, active = shuffle) {
                    connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
                })
                if (showRepeat) {
                    add(
                        MenuItem(
                            label = if (repeat == Player.REPEAT_MODE_ONE) "Repeat one" else "Repeat",
                            icon = if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            active = repeat != Player.REPEAT_MODE_OFF,
                        ) {
                            connection.player.repeatMode = when (connection.player.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    )
                }
            }
            add(MenuItem("Other menu", Icons.Rounded.MoreHoriz) { onOpenChange(false); onOtherMenu() })
            add(MenuItem("Edit player", Icons.Rounded.Tune) { onOpenChange(false); onEditPlayer() })
        }
        val itemGap = 10.dp
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = end - 3.dp, top = edge + buttonSize + itemGap),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(itemGap),
        ) {
            rows.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.graphicsLayer {
                        val delay = index * 0.12f
                        val p = ((reveal.value - delay) / (1f - delay)).coerceIn(0f, 1.2f)
                        alpha = p.coerceIn(0f, 1f)
                        translationY = -(1f - p) * 28.dp.toPx()
                        val s = 0.7f + 0.3f * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                    },
                ) {
                    Text(
                        text = item.label,
                        color = if (pill) Color.White else tones.ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.05).em,
                        fontFamily = RingFont,
                    )
                    StyleCircle(icon = item.icon, tones = tones, size = buttonSize + 6.dp, onClick = item.onClick, pill = pill, active = item.active)
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = end, top = edge),
    ) {
        val turn = remember { Animatable(if (open) 1f else 0f) }
        LaunchedEffect(open) { turn.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
        StyleCircle(
            icon = Icons.Rounded.ExpandLess,
            tones = tones,
            size = buttonSize,
            pill = pill,
            onClick = { onOpenChange(!open) },
            modifier = Modifier.graphicsLayer { rotationZ = 180f + turn.value * 180f },
        )
    }
}
