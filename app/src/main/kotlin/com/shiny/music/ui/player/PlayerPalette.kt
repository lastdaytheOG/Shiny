package com.shiny.music.ui.player

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.shiny.music.constants.PlayerBackgroundStyle
import com.shiny.music.ui.theme.PlayerColorExtractor
import com.shiny.music.ui.liquid.appearance.AppearanceColor
import com.shiny.music.ui.liquid.appearance.logAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the player surfaces derive from the current artwork, produced once.
 *
 * This began as two identical copies of one extraction — the full player and the mini
 * player each ran the same Coil request and the same `Palette.from(bitmap)` — and the
 * previous phase collapsed them into a single `rememberPlayerGradientColors`. What that
 * left behind was still only half the story: the *colours* came from one place, but the
 * blurred colour field every image-based background style renders was being produced
 * independently, per style, per frame, by a full-screen `Modifier.blur`. Six of the eight
 * styles wanted the same soft version of the same artwork and none of them shared it.
 *
 * So the unit is no longer "the gradient colours". It is [PlayerBackdrop]: one decode of
 * one artwork yielding one palette, one pre-blurred field and one arrangement phase, which
 * every player background style then composes differently. One artwork in, one cached
 * answer out, and no style may go back to the image for a second opinion.
 *
 * The extractors elsewhere in the app — album pages, lyrics cards, the ambient screen —
 * are deliberately untouched. They are not player surfaces and they answer different
 * questions; folding them in here would couple things that only look similar.
 */

/**
 * The artwork, as the player background needs it.
 *
 * [key] is the identity the background transition crosses on, and it is the artwork's URL
 * rather than the song's id on purpose: two consecutive tracks from the same album share
 * their sleeve, and a background that re-dissolved into an identical image would be motion
 * with nothing behind it.
 */
@Immutable
data class PlayerBackdrop(
    val key: String,
    /**
     * The artwork reduced to a soft colour field, blurred once at [FieldSize] and drawn
     * scaled up. See [blurField] for why this is a bitmap rather than a render effect.
     */
    val field: ImageBitmap?,
    /** Three stops, dominant to black. Consumed by the gradient style. */
    val gradient: List<Color>,
    /** Up to six distinct swatches. Consumed by the glow style. */
    val glow: List<Color>,
    /**
     * A stable 0..1 derived from [key].
     *
     * The styles that used to drift — the glow field's blobs, the mesh's rotated copies —
     * take their arrangement from this instead of from a clock. It is fixed for as long as
     * the artwork is, so it costs nothing while the player sits idle, and it is different
     * for the next track, so the field genuinely rearranges on the one event that means
     * something.
     */
    val phase: Float,
    /**
     * The light this artwork throws around itself on the stage: its most vivid swatch,
     * held to a mid lightness ([AppearanceColor.glowTone]). Null when the palette found
     * nothing to use, in which case no glow is drawn at all.
     */
    val halo: Color? = null,
) {
    companion object {
        val Empty = PlayerBackdrop(
            key = "",
            field = null,
            gradient = emptyList(),
            glow = emptyList(),
            phase = 0f,
        )
    }
}

/** The colour list [style] draws with, or empty for the styles that do not use one. */
fun PlayerBackdrop.colorsFor(style: PlayerBackgroundStyle): List<Color> = when (style) {
    PlayerBackgroundStyle.GLOW_ANIMATED -> glow
    PlayerBackgroundStyle.DEFAULT -> emptyList()
    else -> gradient
}

/**
 * The backdrop for [thumbnailUrl], or [PlayerBackdrop.Empty] when the style does not need
 * one.
 *
 * On a cache hit this resolves on the composition immediately after the track change, so
 * the background's crossfade starts in the same visual event as the artwork's. On a miss
 * it holds the *previous* backdrop until the new one is ready rather than clearing to
 * nothing — a background that blanks and then refills reads as two events, and the second
 * one is a flash of the sheet's base colour.
 */
@Composable
fun rememberPlayerBackdrop(
    thumbnailUrl: String?,
    style: PlayerBackgroundStyle,
    fallbackColor: Int = MaterialTheme.colorScheme.surface.toArgb(),
): State<PlayerBackdrop> {
    val context = LocalContext.current
    val backdrop = remember { mutableStateOf(PlayerBackdrop.Empty) }

    LaunchedEffect(thumbnailUrl, style, fallbackColor) {
        if (style == PlayerBackgroundStyle.DEFAULT || thumbnailUrl == null) {
            backdrop.value = PlayerBackdrop.Empty
            return@LaunchedEffect
        }

        val key = BackdropKey(thumbnailUrl, fallbackColor)
        cached(key)?.let {
            backdrop.value = it
            return@LaunchedEffect
        }

        val extracted = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .size(SourceSize, SourceSize)
                .allowHardware(false)
                .build()

            val bitmap = runCatching { context.imageLoader.execute(request) }
                .getOrNull()
                ?.image
                ?.toBitmap()
                ?: return@withContext null

            val source = cachedSource(thumbnailUrl) ?: withContext(Dispatchers.Default) {
                ArtworkSource(
                    palette = Palette.from(bitmap)
                        .maximumColorCount(8)
                        .resizeBitmapArea(100 * 100)
                        .generate(),
                    field = runCatching { blurField(bitmap) }.getOrNull(),
                ).also { putSource(thumbnailUrl, it) }
            }

            PlayerBackdrop(
                key = thumbnailUrl,
                field = source.field,
                gradient = PlayerColorExtractor.extractGradientColors(
                    palette = source.palette,
                    fallbackColor = fallbackColor,
                ),
                glow = listOfNotNull(
                    Color(source.palette.getVibrantColor(fallbackColor)),
                    Color(source.palette.getLightVibrantColor(fallbackColor)),
                    Color(source.palette.getDarkVibrantColor(fallbackColor)),
                    Color(source.palette.getMutedColor(fallbackColor)),
                    Color(source.palette.getLightMutedColor(fallbackColor)),
                    Color(source.palette.getDarkMutedColor(fallbackColor)),
                ).distinct(),
                phase = phaseOf(thumbnailUrl),
                halo = (source.palette.vibrantSwatch ?: source.palette.lightVibrantSwatch ?: source.palette.dominantSwatch)
                    ?.rgb
                    ?.let { Color(AppearanceColor.glowTone(it)) },
            ).also { put(key, it) }
        } ?: return@LaunchedEffect

        logAppearance("Artwork palette updated")
        backdrop.value = extracted
    }

    return backdrop
}

/**
 * The fallback colour is part of the key, not just the artwork.
 *
 * It is what a palette with no matching swatch returns, and the two player surfaces pass
 * different ones — so keying on the artwork alone would let whichever composed first
 * decide the other's colours. The *style* deliberately is not part of it: every style now
 * reads from the same answer, which is the point.
 */
private data class BackdropKey(
    val artworkUrl: String,
    val fallbackColor: Int,
)

/** The half of the work that depends only on the artwork, so it is cached only on that. */
private class ArtworkSource(
    val palette: Palette,
    val field: ImageBitmap?,
)

/** Decode size. Large enough for Palette's own resize, small enough to be free. */
private const val SourceSize = 96

/**
 * The blurred field's resolution.
 *
 * It is drawn full-screen, so this is a ~17x upscale on a 1080p device. That is fine
 * precisely because the thing being upscaled has no detail left in it: bilinear
 * magnification of an already-smooth image is indistinguishable from the blur it stands
 * in for, and it costs one textured quad instead of a full-screen render effect.
 */
private const val FieldSize = 64

/**
 * Chosen to land near the 150dp render-effect blur this replaces.
 *
 * Three box passes of radius r approximate a Gaussian of sigma = sqrt(3 * ((2r+1)^2 - 1) / 12),
 * so r = 10 gives sigma ~= 10.4 on a 64px field: about a sixth of the image, or roughly
 * 175px once magnified to a phone screen.
 */
private const val FieldBlurRadius = 10
private const val FieldBlurPasses = 3

private val sourceCache = LruMap<String, ArtworkSource>(maxSize = 8)
private val backdropCache = LruMap<BackdropKey, PlayerBackdrop>(maxSize = 16)

private fun cached(key: BackdropKey): PlayerBackdrop? =
    synchronized(backdropCache) { backdropCache[key] }

private fun put(key: BackdropKey, value: PlayerBackdrop) {
    synchronized(backdropCache) { backdropCache[key] = value }
}

private fun cachedSource(url: String): ArtworkSource? =
    synchronized(sourceCache) { sourceCache[url] }

private fun putSource(url: String, value: ArtworkSource) {
    synchronized(sourceCache) { sourceCache[url] = value }
}

/**
 * Bounded, unlike the map this replaces.
 *
 * Colour lists were small enough to leak quietly forever; a field bitmap is 16KB, and a
 * long shuffle session is thousands of tracks.
 */
private class LruMap<K, V>(private val maxSize: Int) :
    LinkedHashMap<K, V>(maxSize, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
        size > maxSize
}

private fun phaseOf(key: String): Float =
    ((key.hashCode() and 0x7FFFFFFF) % 997) / 997f

/**
 * The artwork as a small, heavily blurred bitmap.
 *
 * `Modifier.blur` would be the obvious way to get this and is the wrong one for a
 * background. It is a `RenderEffect` on a full-screen layer, which means it is re-run on
 * every frame in which anything within its bounds is damaged — and a full-screen layer is
 * damaged by *everything*, including the once-a-second position tick on the seek bar. The
 * player therefore paid for a full-screen Gaussian blur at the display's refresh rate for
 * as long as it was open, whether or not the artwork had changed. (It is also a no-op
 * below API 31, so those devices were being shown a stretched, unblurred thumbnail.)
 *
 * Blurring once, into a bitmap the size of a postage stamp, costs about a fifth of a
 * millisecond on a background thread and nothing at all thereafter.
 */
private fun blurField(source: Bitmap): ImageBitmap {
    val scaled = if (source.width == FieldSize && source.height == FieldSize) {
        source
    } else {
        Bitmap.createScaledBitmap(source, FieldSize, FieldSize, true)
    }

    val pixels = IntArray(FieldSize * FieldSize)
    scaled.getPixels(pixels, 0, FieldSize, 0, 0, FieldSize, FieldSize)
    if (scaled !== source) scaled.recycle()

    val scratch = IntArray(pixels.size)
    repeat(FieldBlurPasses) {
        blurAxis(pixels, scratch, stride = 1, lineStride = FieldSize)
        blurAxis(scratch, pixels, stride = FieldSize, lineStride = 1)
    }

    return Bitmap.createBitmap(pixels, FieldSize, FieldSize, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

/**
 * One separable box pass over [src] into [dst], with edge clamping and a running sum.
 *
 * [stride] steps along the axis being blurred and [lineStride] between lines, so the same
 * body serves rows and columns without transposing the buffer. Alpha is written as opaque:
 * the field is a background, and artwork that decoded with a transparent border should not
 * punch a hole in it.
 */
private fun blurAxis(src: IntArray, dst: IntArray, stride: Int, lineStride: Int) {
    val radius = FieldBlurRadius
    val window = radius * 2 + 1
    val last = FieldSize - 1

    for (line in 0 until FieldSize) {
        val origin = line * lineStride
        var red = 0
        var green = 0
        var blue = 0

        for (offset in -radius..radius) {
            val pixel = src[origin + offset.coerceIn(0, last) * stride]
            red += (pixel shr 16) and 0xFF
            green += (pixel shr 8) and 0xFF
            blue += pixel and 0xFF
        }

        for (index in 0..last) {
            dst[origin + index * stride] = (0xFF shl 24) or
                ((red / window) shl 16) or
                ((green / window) shl 8) or
                (blue / window)

            val leaving = src[origin + (index - radius).coerceIn(0, last) * stride]
            val entering = src[origin + (index + radius + 1).coerceIn(0, last) * stride]
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
