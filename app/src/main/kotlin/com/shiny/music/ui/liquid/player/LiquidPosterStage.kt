package com.shiny.music.ui.liquid.player

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.shiny.music.ui.liquid.appearance.Atmosphere
import com.shiny.music.ui.liquid.appearance.PosterMelt
import com.shiny.music.ui.liquid.appearance.posterDepthRichness
import com.shiny.music.ui.liquid.appearance.posterFootScrim
import com.shiny.music.ui.liquid.appearance.posterMeltAt
import com.shiny.music.ui.liquid.appearance.posterSaturation
import com.shiny.music.ui.liquid.appearance.posterScrimAt
import com.shiny.music.ui.liquid.appearance.posterTitleScrim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * The Poster presentation of Now Playing: the whole cover, as wide as the screen, hung from
 * the top, never cropped and never stretched; its foot goes out of focus into a field made
 * of its own colours, and that field carries on down the screen as the cover's reflection,
 * under the title and the controls.
 *
 * It is three still pictures and no effects:
 *
 *   ground  the cover blurred, standing on its own blurred mirror image, darkened towards
 *           the foot. It fills the screen under everything and is what the glass refracts.
 *   cover   the real artwork, square, on top of the ground's first screen-width.
 *   veil    a strip of the same ground with an alpha ramp baked in, laid over the cover's
 *           foot. Where it thickens, the sharp picture gives way to its own blurred copy
 *           exactly beneath it, which is what "going out of focus" is.
 *
 * Nothing here blurs, masks or composites at draw time: the ground and the veil are baked
 * once per song at about a hundred pixels across and drawn as two textured quads. The cover
 * is never masked, so its own layer (the living artwork's, the video's) is untouched.
 */

/** How wide the poster's pictures are baked, in pixels. They hold no detail, only colour. */
private const val PosterColumns = 96

/** Rows in a [PosterSource] at the least: enough for the tallest phone, whatever the cover's shape. */
private const val PosterMinRows = PosterColumns * 5 / 2

/** Box-blur radii at [PosterColumns]: soft is a picture out of focus, deep is only its colours. */
private const val PosterSoftRadius = 5
private const val PosterDeepRadius = 14
private const val PosterBlurPasses = 3

/** Rows the veil runs on past the cover's bottom edge, fading out, so its own edge never shows. */
private const val VeilFeatherRows = 4

/** The stage of a song without colour to give (Atmosphere off), before the scrim. */
private const val NeutralGround = 0xFF1B1B20.toInt()

/**
 * The cover standing on its own reflection, blurred, as pixels: [columns] across, top to
 * bottom the cover ([coverRows] of it: as many as its shape gives) and then the cover upside
 * down, as still water would show it. The blur deepens down the cover's lower half and is at
 * its deepest from the fold on.
 */
internal class PosterSource(val columns: Int, val coverRows: Int, val rows: Int, val pixels: IntArray)

private val posterSources = object : LinkedHashMap<String, PosterSource>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PosterSource>): Boolean = size > 6
}

/**
 * The [PosterSource] for [artworkUrl], made off the main thread and kept for the last few
 * covers. While the next one is being made the previous one stays, so the stage never falls
 * back to nothing between songs. Does no work at all unless [enabled].
 */
@Composable
internal fun rememberPosterSource(artworkUrl: String?, enabled: Boolean): State<PosterSource?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<PosterSource?>(null) }
    LaunchedEffect(artworkUrl, enabled) {
        if (!enabled) {
            state.value = null
            return@LaunchedEffect
        }
        if (artworkUrl == null) return@LaunchedEffect
        synchronized(posterSources) { posterSources[artworkUrl] }?.let {
            state.value = it
            return@LaunchedEffect
        }
        val made = withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context)
                .data(artworkUrl)
                // Within this box, keeping its shape: a portrait cover comes back portrait.
                .size(PosterColumns * 2, PosterColumns * 2)
                .allowHardware(false)
                .build()
            val bitmap = runCatching { context.imageLoader.execute(request) }.getOrNull()?.image?.toBitmap()
                ?: return@withContext null
            runCatching { posterSource(bitmap) }.getOrNull()
        } ?: return@LaunchedEffect
        synchronized(posterSources) { posterSources[artworkUrl] = made }
        state.value = made
    }
    return state
}

private fun posterSource(cover: Bitmap): PosterSource {
    val w = PosterColumns
    // As many rows as the cover's own shape gives: square for a sleeve, taller for portrait art.
    val c = (w.toFloat() * cover.height / cover.width.coerceAtLeast(1)).toInt().coerceIn(w / 2, w * 2)
    val h = maxOf(PosterMinRows, c * 2)
    val scaled = if (cover.width == w && cover.height == c) cover else Bitmap.createScaledBitmap(cover, w, c, true)
    val picture = IntArray(w * c)
    scaled.getPixels(picture, 0, w, 0, 0, w, c)
    if (scaled !== cover) scaled.recycle()

    // Down the cover, back up it, and its top row from there on.
    val stacked = IntArray(w * h)
    for (y in 0 until h) {
        val row = if (y < c) y else (2 * c - 1 - y).coerceAtLeast(0)
        System.arraycopy(picture, row * w, stacked, y * w, w)
    }
    val soft = boxBlur(stacked, w, h, PosterSoftRadius)
    val deep = boxBlur(stacked, w, h, PosterDeepRadius)

    val out = IntArray(w * h)
    for (y in 0 until h) {
        // Soft over the cover, deepening through its foot and on past the fold: just under
        // the cover the reflection still has the picture's shapes in it, as a polished floor
        // would, and further down it is only colour.
        val t = ((y - c * 0.6f) / (c * 0.9f)).coerceIn(0f, 1f)
        val k = ((t * t * (3f - 2f * t)) * 256f).toInt()
        val line = y * w
        for (x in 0 until w) out[line + x] = mix(soft[line + x], deep[line + x], k)
    }
    return PosterSource(w, c, h, out)
}

/** [a] to [b] by [k] of 256, per channel; the result is opaque. */
private fun mix(a: Int, b: Int, k: Int): Int {
    val ar = (a shr 16) and 0xFF
    val ag = (a shr 8) and 0xFF
    val ab = a and 0xFF
    val r = ar + ((((b shr 16) and 0xFF) - ar) * k shr 8)
    val g = ag + ((((b shr 8) and 0xFF) - ag) * k shr 8)
    val bl = ab + (((b and 0xFF) - ab) * k shr 8)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

/** Three separable box passes of [radius] over a [w] by [h] image, edges clamped: near enough a Gaussian. */
private fun boxBlur(source: IntArray, w: Int, h: Int, radius: Int): IntArray {
    var a = source.copyOf()
    var b = IntArray(source.size)
    repeat(PosterBlurPasses) {
        blurLines(a, b, lines = h, length = w, step = 1, lineStep = w, radius = radius)
        blurLines(b, a, lines = w, length = h, step = w, lineStep = 1, radius = radius)
    }
    return a
}

private fun blurLines(src: IntArray, dst: IntArray, lines: Int, length: Int, step: Int, lineStep: Int, radius: Int) {
    val window = radius * 2 + 1
    val last = length - 1
    for (line in 0 until lines) {
        val origin = line * lineStep
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val pixel = src[origin + offset.coerceIn(0, last) * step]
            red += (pixel shr 16) and 0xFF
            green += (pixel shr 8) and 0xFF
            blue += pixel and 0xFF
        }
        for (index in 0..last) {
            dst[origin + index * step] = (0xFF shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val leaving = src[origin + (index - radius).coerceIn(0, last) * step]
            val entering = src[origin + (index + radius + 1).coerceIn(0, last) * step]
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

/**
 * Where the poster's parts fall on this screen, in pixels from the top of the player: its
 * size, where the cover's dissolve starts and ends, and where the title begins.
 */
internal data class PosterGeometry(
    val widthPx: Int,
    val heightPx: Int,
    /** Where the cover ends: its width down for a square one, further for portrait art. */
    val coverBottomPx: Float,
    val meltStartPx: Float,
    val meltEndPx: Float,
    val titleTopPx: Float,
)

/** The ground and the veil for one cover on one screen, as pixels ready to show. */
internal class PosterLayers(
    val columns: Int,
    val groundRows: Int,
    val ground: IntArray,
    val veilTopRow: Int,
    val veilRows: Int,
    val veil: IntArray,
) {
    fun sameShapeAs(other: PosterLayers): Boolean =
        columns == other.columns && groundRows == other.groundRows &&
            veilTopRow == other.veilTopRow && veilRows == other.veilRows
}

internal fun bakePosterLayers(
    source: PosterSource,
    geometry: PosterGeometry,
    atmosphere: Atmosphere,
    amoled: Boolean,
): PosterLayers {
    val columns = source.columns
    val rowPx = geometry.widthPx / columns.toFloat()
    val groundRows = ceil(geometry.heightPx / rowPx).toInt().coerceAtLeast(1)
    val melt = PosterMelt(geometry.meltStartPx, geometry.meltEndPx)
    // With Atmosphere off the cover gives the stage no colour: it dissolves into the plain
    // dark stage instead, and into true black on AMOLED.
    val flat: Int? = if (atmosphere == Atmosphere.Off) (if (amoled) 0xFF000000.toInt() else NeutralGround) else null

    // Blurring averages colour towards grey; the ground is given back what the blur took.
    val vivid = if (flat != null) source.pixels else saturated(source.pixels, posterSaturation(atmosphere))

    val titleRow = (geometry.titleTopPx / rowPx).toInt().coerceIn(0, groundRows - 1)
    val brightness = if (flat != null) {
        android.graphics.Color.luminance(flat)
    } else {
        brightEnd(vivid, columns, source.rows, titleRow, groundRows)
    }
    val titleScrim = posterTitleScrim(atmosphere, brightness)
    val footScrim = posterFootScrim(titleScrim)

    val ground = IntArray(columns * groundRows)
    for (y in 0 until groundRows) {
        val shade = posterScrimAt(
            y = (y + 0.5f) * rowPx,
            meltStart = melt.start,
            titleTop = geometry.titleTopPx,
            bottom = geometry.heightPx.toFloat(),
            titleScrim = titleScrim,
            footScrim = footScrim,
        )
        val k = ((1f - shade) * 256f).toInt().coerceIn(0, 256)
        // A colour that is only darkened goes to mud. The deeper the shade, the more of its
        // colour the ground is given back, so the foot of the stage is a rich dark, not a grey one.
        val richness = ((1f + posterDepthRichness(atmosphere) * shade) * 256f).toInt()
        val from = y.coerceAtMost(source.rows - 1) * columns
        val to = y * columns
        for (x in 0 until columns) {
            val c = if (flat != null) flat else enriched(vivid[from + x], richness)
            ground[to + x] = (0xFF shl 24) or
                ((((c shr 16) and 0xFF) * k shr 8) shl 16) or
                ((((c shr 8) and 0xFF) * k shr 8) shl 8) or
                ((c and 0xFF) * k shr 8)
        }
    }

    // The veil: the ground's own rows from just above the dissolve to just past the cover's
    // bottom edge. Its alpha rises through the dissolve, holds over whatever is left of the
    // cover, and falls away again below it, where it lies on identical ground.
    val veilTop = (floor(melt.start / rowPx).toInt() - 1).coerceIn(0, groundRows - 1)
    val fold = (geometry.coverBottomPx / rowPx).roundToInt()
    val veilBottom = (fold + VeilFeatherRows).coerceIn(veilTop + 1, groundRows)
    val veilRows = (veilBottom - veilTop).coerceAtLeast(1)
    val veil = IntArray(columns * veilRows)
    for (r in 0 until veilRows) {
        val y = veilTop + r
        var a = posterMeltAt((y + 0.5f) * rowPx, melt)
        if (y >= fold) a *= 1f - (y - fold + 1f) / VeilFeatherRows
        val alpha = (a.coerceIn(0f, 1f) * 255f).roundToInt()
        val from = y * columns
        val to = r * columns
        for (x in 0 until columns) veil[to + x] = (alpha shl 24) or (ground[from + x] and 0x00FFFFFF)
    }
    return PosterLayers(columns, groundRows, ground, veilTop, veilRows, veil)
}

/**
 * How bright the bright end of the ground is under the title and the controls: the 90th
 * percentile of its relative luminance there, not the average, because one pale patch behind
 * a word is enough to lose it.
 */
private fun brightEnd(pixels: IntArray, columns: Int, rows: Int, fromRow: Int, toRow: Int): Float {
    val first = fromRow.coerceIn(0, rows - 1)
    val last = toRow.coerceIn(first + 1, rows)
    val bins = IntArray(64)
    var count = 0
    for (i in first * columns until last * columns) {
        val l = android.graphics.Color.luminance(pixels[i])
        bins[(l * 63f).toInt().coerceIn(0, 63)]++
        count++
    }
    if (count == 0) return 0f
    var seen = 0
    val wanted = (count * 0.9f).toInt()
    for (bin in bins.indices) {
        seen += bins[bin]
        if (seen >= wanted) return (bin + 1) / 64f
    }
    return 1f
}

/** [pixels] with their colour pushed away from grey by [amount] (1 leaves them alone). Opaque. */
private fun saturated(pixels: IntArray, amount: Float): IntArray {
    if (amount == 1f) return pixels
    val k = (amount * 256f).toInt()
    return IntArray(pixels.size) { i ->
        val c = pixels[i]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val grey = (r * 54 + g * 183 + b * 19) shr 8
        (0xFF shl 24) or
            ((grey + ((r - grey) * k shr 8)).coerceIn(0, 255) shl 16) or
            ((grey + ((g - grey) * k shr 8)).coerceIn(0, 255) shl 8) or
            (grey + ((b - grey) * k shr 8)).coerceIn(0, 255)
    }
}

/**
 * [c] with its colour pushed away from grey by [k] of 256, less so the more colour it already
 * has: a pale pink gains a great deal, a pure red almost nothing, and nothing is driven
 * into the corners of the cube where hues shift.
 */
private fun enriched(c: Int, k: Int): Int {
    if (k <= 256) return c
    val r = (c shr 16) and 0xFF
    val g = (c shr 8) and 0xFF
    val b = c and 0xFF
    val high = maxOf(r, g, b)
    if (high == 0) return c
    val low = minOf(r, g, b)
    // 256 for a grey, 0 for a colour with one channel at nothing.
    val room = 256 - ((high - low) * 256 / high)
    val gain = 256 + ((k - 256) * room shr 8)
    val grey = (r * 54 + g * 183 + b * 19) shr 8
    return (0xFF shl 24) or
        ((grey + ((r - grey) * gain shr 8)).coerceIn(0, 255) shl 16) or
        ((grey + ((g - grey) * gain shr 8)).coerceIn(0, 255) shl 8) or
        (grey + ((b - grey) * gain shr 8)).coerceIn(0, 255)
}

/**
 * What the Poster stage draws with: the ground and the veil now on screen.
 *
 * A change of song eases from one cover's pictures to the next by mixing their pixels — a few
 * thousand of them — for as long as the change lasts. That is an exact crossfade, and it needs
 * neither a second pair of pictures on screen nor an offscreen layer to blend them in.
 */
@Stable
internal class PosterStage {
    private var shown: PosterLayers? = null
    private var groundPixels = IntArray(0)
    private var veilPixels = IntArray(0)
    private var groundBitmap: Bitmap? = null
    private var veilBitmap: Bitmap? = null
    private var groundImage: ImageBitmap? = null
    private var veilImage: ImageBitmap? = null

    /** Bumped whenever the pixels change; the draw calls read it, so only they run again. */
    private val version = mutableIntStateOf(0)

    /** Whether there is anything to draw yet. */
    var ready by mutableStateOf(false)
        private set

    /** True from the first mixed frame of a change of cover until its last. */
    private var easing = false

    suspend fun show(target: PosterLayers) {
        val current = shown
        if (current === target && !easing) return
        if (current == null || !current.sameShapeAs(target)) {
            // The first cover, or a different screen: there is nothing to ease from.
            easing = false
            adopt(target)
            return
        }
        // From whatever is on screen now, which may itself be part-way between two covers.
        val fromGround = groundPixels.copyOf()
        val fromVeil = veilPixels.copyOf()
        shown = target
        easing = true
        animate(0f, 1f, animationSpec = tween(PosterBlendMillis, easing = FastOutSlowInEasing)) { t, _ ->
            val k = (t * 256f).toInt().coerceIn(0, 256)
            for (i in groundPixels.indices) groundPixels[i] = mix(fromGround[i], target.ground[i], k)
            // The two veils share their alpha (same screen, same dissolve); only colour moves.
            for (i in veilPixels.indices) {
                veilPixels[i] = (target.veil[i] and 0xFF000000.toInt()) or (mix(fromVeil[i], target.veil[i], k) and 0x00FFFFFF)
            }
            push()
        }
        easing = false
    }

    private fun adopt(target: PosterLayers) {
        shown = target
        groundPixels = target.ground.copyOf()
        veilPixels = target.veil.copyOf()
        groundBitmap = Bitmap.createBitmap(target.columns, target.groundRows, Bitmap.Config.ARGB_8888)
        veilBitmap = Bitmap.createBitmap(target.columns, target.veilRows, Bitmap.Config.ARGB_8888)
        groundImage = groundBitmap?.asImageBitmap()
        veilImage = veilBitmap?.asImageBitmap()
        push()
        ready = true
    }

    private fun push() {
        val layers = shown ?: return
        groundBitmap?.setPixels(groundPixels, 0, layers.columns, 0, 0, layers.columns, layers.groundRows)
        veilBitmap?.setPixels(veilPixels, 0, layers.columns, 0, 0, layers.columns, layers.veilRows)
        version.intValue++
    }

    fun clear() {
        shown = null
        groundImage = null
        veilImage = null
        groundBitmap = null
        veilBitmap = null
        ready = false
        version.intValue++
    }

    /** The ground, over the whole player. */
    fun DrawScope.drawPosterGround(alpha: Float) {
        version.intValue
        val layers = shown ?: return
        val image = groundImage ?: return
        if (alpha <= 0.003f) return
        val rowPx = size.width / layers.columns
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), (layers.groundRows * rowPx).roundToInt()),
            alpha = alpha.coerceAtMost(1f),
            filterQuality = FilterQuality.Low,
        )
    }

    /** The veil, over the cover's foot. */
    fun DrawScope.drawPosterVeil(alpha: Float) {
        version.intValue
        val layers = shown ?: return
        val image = veilImage ?: return
        if (alpha <= 0.003f) return
        val rowPx = size.width / layers.columns
        drawImage(
            image = image,
            dstOffset = IntOffset(0, (layers.veilTopRow * rowPx).roundToInt()),
            dstSize = IntSize(size.width.roundToInt(), (layers.veilRows * rowPx).roundToInt()),
            alpha = alpha.coerceAtMost(1f),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** How long one cover's colours take to become the next one's. */
private const val PosterBlendMillis = 650

/**
 * The Poster stage for [artworkUrl] on a screen laid out as [geometry]. With [enabled] false,
 * or before the screen has been measured, it holds nothing and does nothing.
 */
@Composable
internal fun rememberPosterStage(
    artworkUrl: String?,
    geometry: PosterGeometry?,
    atmosphere: Atmosphere,
    amoled: Boolean,
    enabled: Boolean,
): PosterStage {
    val stage = remember { PosterStage() }
    val source by rememberPosterSource(artworkUrl, enabled)
    val target = remember(source, geometry, atmosphere, amoled) {
        val s = source
        if (s != null && geometry != null) bakePosterLayers(s, geometry, atmosphere, amoled) else null
    }
    LaunchedEffect(target, enabled) {
        if (!enabled) stage.clear() else if (target != null) stage.show(target)
    }
    return stage
}
