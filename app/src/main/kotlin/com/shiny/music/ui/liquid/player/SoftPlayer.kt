package com.shiny.music.ui.liquid.player

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.shiny.music.ui.liquid.appearance.Atmosphere
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import com.shiny.music.ui.liquid.glassPressScale
import com.shiny.music.ui.liquid.rememberGlassPress
import com.shiny.music.ui.player.PlayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** The soft look's whites: brighter than Shiny's, since its ground is lighter. */
internal object SoftInk {
    val title = Color.White.copy(alpha = 0.94f)
    val artist = Color.White.copy(alpha = 0.58f)
    val time = Color.White.copy(alpha = 0.66f)
    val icon = Color.White.copy(alpha = 0.84f)
    val track = Color.White.copy(alpha = 0.20f)
    val fill = Color.White.copy(alpha = 0.88f)
}

/** Four colours read off the cover: its lower left, lower right, the whole of it, and its top. */
internal class SoftTones(val left: Int, val right: Int, val deep: Int, val top: Int)

internal fun softTones(bitmap: Bitmap, atmosphere: Atmosphere): SoftTones {
    val w = bitmap.width
    val h = bitmap.height
    val px = IntArray(w * h)
    bitmap.getPixels(px, 0, w, 0, 0, w, h)
    fun average(x0: Int, x1: Int, y0: Int, y1: Int): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = px[y * w + x]
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            n++
        }
        if (n == 0) return 0xFF202024.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
    val boost = when (atmosphere) {
        Atmosphere.Soft -> 0.7f
        Atmosphere.Immersive -> 1.3f
        else -> 1f
    }
    val lower = (h * 0.6f).toInt()
    val upper = (h * 0.4f).toInt().coerceAtLeast(1)
    val mid = w / 2
    return SoftTones(
        left = tone(average(0, mid, lower, h), 0.16f, 0.44f, 1.05f * boost),
        right = tone(average(mid, w, lower, h), 0.16f, 0.44f, 1.05f * boost),
        deep = tone(average(0, w, 0, h), 0.10f, 0.28f, 0.9f * boost),
        top = tone(average(0, w, 0, upper), 0.18f, 0.44f, 1.2f * boost),
    )
}

// calmer than the cover and never too light for white type over it
private fun tone(argb: Int, minLight: Float, maxLight: Float, saturation: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = (hsl[1] * saturation).coerceAtMost(0.6f)
    hsl[2] = hsl[2].coerceIn(minLight, maxLight)
    return ColorUtils.HSLToColor(hsl)
}

/** One loop of the pools' drift. */
private const val SoftLoopMs = 100_000f

/**
 * The soft look's ground: the cover's colours as four big pools blended into one smooth
 * field, drifting slowly while music plays. Painted into a tiny bitmap and drawn scaled up,
 * so a step of the drift costs a few thousand multiplications and one small upload.
 */
@Composable
internal fun SoftBackground(
    backdrop: State<PlayerBackdrop>,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalShinyAppearance.current
    val atmosphere = appearance.atmosphere
    val field = backdrop.value.field
    val tones = remember(field, atmosphere) {
        if (atmosphere == Atmosphere.Off || field == null) null else runCatching { softTones(field.asAndroidBitmap(), atmosphere) }.getOrNull()
    }
    // one clock for both sides of a song change's crossfade
    val drift = remember { mutableFloatStateOf(0f) }
    val moving = playing && tones != null
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (isActive) {
            // stepped at ~25 fps: the pools move too slowly for the steps to show
            delay(40)
            val now = withFrameMillis { it }
            drift.floatValue = (drift.floatValue + (now - last) / SoftLoopMs) % 1f
            last = now
        }
    }
    val ground = if (appearance.amoled && tones == null) Color.Black else SoftNeutral
    Box(modifier.background(ground)) {
        Crossfade(targetState = tones, animationSpec = tween(700), label = "softGround") { t ->
            if (t != null) SoftField(t, drift = { drift.floatValue }, modifier = Modifier.fillMaxSize())
        }
    }
}

private val SoftNeutral = lerp(Color(0xFF2A2A30), Color.Black, 0.35f)

@Composable
private fun SoftField(tones: SoftTones, drift: () -> Float, modifier: Modifier) {
    val canvas = remember { SoftCanvas() }
    Spacer(
        modifier
            // a layer of its own, so nothing else on the stage makes it repaint
            .graphicsLayer { }
            .drawBehind {
                canvas.paint(tones, drift(), size.height / size.width.coerceAtLeast(1f))
                drawImage(
                    canvas.image,
                    dstSize = IntSize(ceil(size.width).toInt(), ceil(size.height).toInt()),
                    filterQuality = FilterQuality.Low,
                )
            },
    )
}

private const val FieldW = 28
private const val FieldH = 60

private class SoftCanvas {
    private val bitmap: Bitmap = Bitmap.createBitmap(FieldW, FieldH, Bitmap.Config.ARGB_8888)
    val image = bitmap.asImageBitmap()
    private val pixels = IntArray(FieldW * FieldH)
    private var lastT = Float.NaN
    private var lastAspect = Float.NaN
    private var lastTones: SoftTones? = null

    fun paint(tones: SoftTones, t: Float, aspect: Float) {
        if (t == lastT && aspect == lastAspect && tones === lastTones) return
        lastT = t
        lastAspect = aspect
        lastTones = tones
        val a = t * 2f * Math.PI.toFloat()
        // pool centres in width units, y stretched by the screen's shape so the pools stay round
        val cx = floatArrayOf(
            0.50f + 0.20f * sin(a + 0.6f),
            0.20f + 0.14f * sin(2f * a + 1.1f),
            0.80f + 0.13f * cos(a + 1.3f),
            0.48f + 0.22f * cos(3f * a + 2.1f),
        )
        val cy = floatArrayOf(
            (0.16f + 0.05f * cos(2f * a)) * aspect,
            (0.60f + 0.07f * cos(a + 0.3f)) * aspect,
            (0.64f + 0.08f * sin(2f * a + 0.7f)) * aspect,
            (0.98f + 0.05f * sin(a + 1.9f)) * aspect,
        )
        val spread = floatArrayOf(3.0f, 3.2f, 3.2f, 2.6f)
        val colours = intArrayOf(tones.top, tones.left, tones.right, tones.deep)
        val r = FloatArray(4) { ((colours[it] shr 16) and 0xFF).toFloat() }
        val g = FloatArray(4) { ((colours[it] shr 8) and 0xFF).toFloat() }
        val b = FloatArray(4) { (colours[it] and 0xFF).toFloat() }
        for (j in 0 until FieldH) {
            val v = (j + 0.5f) / FieldH
            val y = v * aspect
            // a touch darker toward the foot, under the controls
            val shade = 1f - 0.16f * smooth((v - 0.6f) / 0.4f)
            for (i in 0 until FieldW) {
                val x = (i + 0.5f) / FieldW
                var sr = 0f
                var sg = 0f
                var sb = 0f
                // the deep tone always has a little say, so far corners never go black
                var sum = 0.002f
                sr += r[3] * 0.002f
                sg += g[3] * 0.002f
                sb += b[3] * 0.002f
                for (k in 0 until 4) {
                    val dx = x - cx[k]
                    val dy = y - cy[k]
                    val wgt = exp(-(dx * dx + dy * dy) * spread[k])
                    sr += r[k] * wgt
                    sg += g[k] * wgt
                    sb += b[k] * wgt
                    sum += wgt
                }
                val cr = (sr / sum * shade).toInt().coerceIn(0, 255)
                val cg = (sg / sum * shade).toInt().coerceIn(0, 255)
                val cb = (sb / sum * shade).toInt().coerceIn(0, 255)
                pixels[j * FieldW + i] = (0xFF shl 24) or (cr shl 16) or (cg shl 8) or cb
            }
        }
        bitmap.setPixels(pixels, 0, FieldW, 0, 0, FieldW, FieldH)
    }

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}

/**
 * A frosted round button face: a light white fill, a thin rim that catches the light at
 * two corners, and a soft shadow around it, drawn only outside so the fill stays clean.
 * Static, so it costs nothing until it is pressed.
 */
internal fun Modifier.frostDisc(press: () -> Float = { 0f }, fill: Float = 0.12f): Modifier = drawWithCache {
    val r = size.minDimension / 2f
    val spread = 12.dp.toPx()
    val centre = Offset(size.width / 2f, size.height / 2f)
    val shadowCentre = centre + Offset(0f, 3.dp.toPx())
    val shadowRadius = r + spread
    val edge = (r / shadowRadius).coerceIn(0f, 1f)
    val shadow = Brush.radialGradient(
        0f to Color.Black.copy(alpha = 0.20f),
        edge to Color.Black.copy(alpha = 0.20f),
        (edge + (1f - edge) * 0.35f) to Color.Black.copy(alpha = 0.09f),
        1f to Color.Transparent,
        center = shadowCentre,
        radius = shadowRadius,
    )
    val hole = Path().apply { addOval(Rect(centre, r)) }
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.55f),
        0.42f to Color.White.copy(alpha = 0.10f),
        0.62f to Color.White.copy(alpha = 0.10f),
        1f to Color.White.copy(alpha = 0.36f),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val rimWidth = 1.dp.toPx()
    onDrawBehind {
        clipPath(hole, ClipOp.Difference) { drawCircle(shadow, shadowRadius, shadowCentre) }
        drawCircle(Color.White.copy(alpha = fill + 0.08f * press().coerceIn(0f, 1f)), r)
        drawCircle(rim, r - rimWidth / 2f, style = Stroke(rimWidth))
    }
}

/** The subdued circular actions beside the alternate song title. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FrostIconButton(
    icon: ImageVector,
    label: String,
    iconSize: Dp,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    Box(
        Modifier
            .size(maxOf(size, QueueMetrics.target))
            .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .glassPressScale(press, 0.09f)
                .background(
                    color = Color(0xFF48464B).copy(alpha = 0.92f + 0.08f * press.value),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(iconSize))
        }
    }
}
