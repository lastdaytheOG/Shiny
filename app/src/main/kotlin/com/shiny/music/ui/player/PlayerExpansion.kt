package com.shiny.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.shiny.music.ui.component.BottomSheetState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The choreography of the mini-to-full player expansion, expressed as bands of the sheet's
 * own 0..1 progress.
 *
 * There is deliberately no timing here. The sheet's [BottomSheetState] is the only clock:
 * under a finger it is the finger, after a fling it is the fling's spring. Everything
 * below is a *shape* applied to whatever that clock reports, which is what makes the
 * collapse the exact inverse of the expansion rather than a second animation that happens
 * to run backwards.
 *
 * The bands do not overlap where overlapping would read as a crossfade. The mini player's
 * chrome is gone at 0.14, before the full player's metadata starts at 0.30 — the previous
 * curves ran 0..0.25 against 0.15..0.40, and that 0.15..0.25 overlap is precisely where
 * two sets of the same text were visible on top of each other.
 */
object ExpansionCurve {

    /** Smoothstep over [start]..[end]; flat outside it. */
    fun ramp(progress: Float, start: Float, end: Float): Float {
        if (end <= start) return if (progress >= end) 1f else 0f
        val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Mini player title, artist and transport. Resolved out before anything else arrives. */
    fun miniChrome(progress: Float): Float = 1f - ramp(progress, 0f, 0.14f)

    /** The player surface and its background. Primary: establishes the environment early. */
    fun surface(progress: Float): Float = ramp(progress, 0f, 0.30f)

    /** Title and artist. Secondary: resolves once the surface is unmistakably there. */
    fun metadata(progress: Float): Float = ramp(progress, 0.30f, 0.62f)

    /** Transport controls and the seek bar. Secondary, a beat behind the metadata. */
    fun controls(progress: Float): Float = ramp(progress, 0.38f, 0.72f)

    /** Supporting actions and labels. Tertiary. */
    fun accents(progress: Float): Float = ramp(progress, 0.50f, 0.85f)
}

/**
 * How far the secondary tiers travel while they resolve.
 *
 * Small on purpose. The distance that carries the transition is the artwork's, and text
 * that flies further than the object it belongs to stops looking attached to it.
 */
val MetadataRise = 14.dp
val ControlsRise = 10.dp

/** Which end of the transition a reported artwork rect belongs to. */
enum class ArtworkAnchor { Mini, Full }

/**
 * How the full player finishes its artwork, so the travelling copy can land on it exactly
 * rather than approximately.
 */
enum class ArtworkTreatment {
    /** A plain rounded rectangle. Every layout except the Apple Music background. */
    Plain,

    /** Full-bleed, with the vertical fade that dissolves the panel into the page. */
    BottomFade,
}

/**
 * Everything the mini player and the full player need to agree on to behave as one object.
 *
 * The rects are stored in **window** coordinates and at **rest** — that is, with the
 * sheet's own translation removed. That distinction is the whole trick: the flight path
 * runs between where the artwork sits when the sheet is closed and where it sits when the
 * sheet is open, and neither endpoint may drift as the sheet moves between them. The mini
 * rect is therefore only recorded while the sheet is at the bottom, and the full rect is
 * normalised by the sheet's current translation, which [BottomSheetState] already knows
 * exactly.
 *
 * Nothing here animates. [progress] is read straight off the sheet, so a drag is a drag
 * and a fling is a fling; there is no second animator to fall out of step with it.
 */
@Stable
class PlayerExpansionState internal constructor(
    private val sheet: BottomSheetState,
) {
    /** The sheet's own expansion, 0 at the mini player and 1 at the full player. */
    val progress: Float
        get() = sheet.progress.coerceIn(0f, 1f)

    /**
     * True only between the two anchors. Every term is a derived boolean, so reading this
     * in composition costs one recomposition per gesture rather than one per frame.
     */
    val inTransit: Boolean
        get() = !sheet.isCollapsed && !sheet.isExpanded && !sheet.isDismissed

    var miniBounds by mutableStateOf<Rect?>(null)
        private set
    var fullBounds by mutableStateOf<Rect?>(null)
        private set

    var miniCornerPx by mutableFloatStateOf(0f)
        private set
    var fullCornerPx by mutableFloatStateOf(0f)
        private set

    var miniModel by mutableStateOf<String?>(null)
        private set
    var fullModel by mutableStateOf<String?>(null)
        private set

    var treatment by mutableStateOf(ArtworkTreatment.Plain)
        private set
    var cropArtwork by mutableStateOf(true)
        private set

    /**
     * Whether the full player is currently showing artwork at all.
     *
     * Lyrics mode, full-screen mode and the hidden-thumbnail preference all remove the
     * target, and a stale rect would otherwise fly the artwork to a place where nothing is
     * drawn. The flag is set from the target's own composition, so it answers "is there
     * something to land on" rather than "was there once".
     */
    var targetPresent by mutableStateOf(false)
        private set

    private var targetCount = 0

    /**
     * The artwork travels only when both ends are known and neither end is currently
     * drawing it. Read from draw and layer lambdas, never from composition.
     */
    fun isFlying(): Boolean {
        if (!targetPresent) return false
        val mini = miniBounds ?: return false
        val full = fullBounds ?: return false
        if (mini.isEmpty || full.isEmpty) return false
        val p = progress
        return p > FlightEpsilon && p < 1f - FlightEpsilon
    }

    internal fun reportMini(bounds: Rect, cornerPx: Float, model: String?) {
        // Only while the sheet is down. Anywhere else the mini player is itself in motion
        // — the docked accessory slides out of the tab bar as the sheet rises — and
        // recording it there would bake that slide into the flight's origin.
        if (progress > MiniRestThreshold) return
        if (bounds.isEmpty) return
        miniBounds = bounds
        miniCornerPx = cornerPx
        miniModel = model
    }

    internal fun reportFull(
        bounds: Rect,
        cornerPx: Float,
        model: String?,
        treatment: ArtworkTreatment,
        cropArtwork: Boolean,
        sheetTranslationPx: Float,
    ) {
        if (bounds.isEmpty) return
        // The sheet is a graphics-layer translation over a fixed layout, so subtracting it
        // yields the rect the artwork will occupy once the sheet has finished opening —
        // available from the first frame of the first expansion rather than only after one
        // has already completed.
        fullBounds = bounds.translate(0f, -sheetTranslationPx)
        fullCornerPx = cornerPx
        fullModel = model
        this.treatment = treatment
        this.cropArtwork = cropArtwork
    }

    /**
     * Counted rather than set, because a track change swaps one target composable for
     * another and the order of the two callbacks is not something to depend on. A boolean
     * would occasionally settle on `false` with a target on screen.
     */
    internal fun registerTarget() {
        targetCount++
        targetPresent = targetCount > 0
    }

    internal fun unregisterTarget() {
        targetCount = (targetCount - 1).coerceAtLeast(0)
        targetPresent = targetCount > 0
    }

    /** The sheet's current downward offset, in pixels. */
    internal fun sheetTranslationPx(density: Density): Float =
        with(density) { (sheet.expandedBound - sheet.value).toPx() }.coerceAtLeast(0f)

    private companion object {
        /**
         * Below this the artwork is within a pixel or two of an anchor, so the real one is
         * allowed to draw and the copy stands down. Wide enough that the handover never
         * lands on a frame where both are visible.
         */
        const val FlightEpsilon = 0.004f
        const val MiniRestThreshold = 0.02f
    }
}

@Composable
fun rememberPlayerExpansionState(sheet: BottomSheetState): PlayerExpansionState =
    remember(sheet) { PlayerExpansionState(sheet) }

/**
 * Null wherever the player is not in the tree, which is why every consumer treats it as
 * optional rather than requiring a provider.
 */
val LocalPlayerExpansion = staticCompositionLocalOf<PlayerExpansionState?> { null }

/**
 * The layout rect of a node in window space, *unclipped*.
 *
 * `boundsInWindow` would be the obvious call and is the wrong one: it intersects with
 * every ancestor's clip, so the full player's artwork reports an empty rect for as long as
 * the sheet holding it is still below the bottom of the screen — which is exactly when the
 * flight needs to know where it is going.
 */
private fun LayoutCoordinates.layoutRectInWindow(): Rect {
    val position = positionInWindow()
    return Rect(position, Size(size.width.toFloat(), size.height.toFloat()))
}

/**
 * Marks a composable as one end of the artwork transition.
 *
 * Reports its rect, and hides it while the travelling copy has the artwork. The hide is a
 * layer alpha rather than a conditional, so the node keeps its place in layout and the
 * handover costs no measure pass.
 *
 * [cornerRadius] of `null` means a circle, resolved to half the measured size — the mini
 * player's round thumbnail cannot state its radius in dp because it depends on the size it
 * is given.
 */
@Composable
fun Modifier.playerArtworkAnchor(
    anchor: ArtworkAnchor,
    model: String?,
    cornerRadius: Dp?,
    treatment: ArtworkTreatment = ArtworkTreatment.Plain,
    cropArtwork: Boolean = true,
    followsSheet: Boolean = true,
    enabled: Boolean = true,
): Modifier {
    val expansion = LocalPlayerExpansion.current
    val density = LocalDensity.current
    val lastCoordinates = remember { AnchorCoordinates() }

    if (anchor == ArtworkAnchor.Full && expansion != null && enabled) {
        DisposableEffect(expansion) {
            expansion.registerTarget()
            onDispose { expansion.unregisterTarget() }
        }
    }

    if (expansion == null || !enabled) return this

    val report: (LayoutCoordinates) -> Unit = { coordinates ->
        val bounds = coordinates.layoutRectInWindow()
        val cornerPx = cornerRadius
            ?.let { with(density) { it.toPx() } }
            ?: (bounds.minDimension / 2f)
        when (anchor) {
            ArtworkAnchor.Mini -> expansion.reportMini(bounds, cornerPx, model)
            ArtworkAnchor.Full -> expansion.reportFull(
                bounds = bounds,
                cornerPx = cornerPx,
                model = model,
                treatment = treatment,
                cropArtwork = cropArtwork,
                // Only the sheet's own content rides the sheet. The player background is
                // a full-screen sibling that never translates, so normalising it by a
                // translation it does not have would place its rect a screen too high and
                // fly the artwork off the top.
                sheetTranslationPx = if (followsSheet) {
                    expansion.sheetTranslationPx(density)
                } else {
                    0f
                },
            )
        }
    }

    // A track change swaps the URL without moving anything, so no layout pass runs and
    // the callback below never fires again. Re-reporting from the coordinates already
    // recorded is what stops a mini player that changed track while collapsed handing the
    // flight the previous track's artwork.
    LaunchedEffect(model, cornerRadius, treatment, cropArtwork, followsSheet) {
        lastCoordinates.value?.takeIf { it.isAttached }?.let(report)
    }

    return this
        .onGloballyPositioned { coordinates ->
            if (!coordinates.isAttached) return@onGloballyPositioned
            lastCoordinates.value = coordinates
            report(coordinates)
        }
        .graphicsLayer { alpha = if (expansion.isFlying()) 0f else 1f }
}

/**
 * The last coordinates an anchor reported from.
 *
 * Deliberately not snapshot state: it is written from every layout pass and read only
 * from an effect, and a `MutableState` here would put a snapshot write on the layout hot
 * path for a value nothing composes against.
 */
private class AnchorCoordinates {
    var value: LayoutCoordinates? = null
}

/**
 * The single artwork that travels between the mini player and the full player.
 *
 * Rendered at the root of the app rather than inside either player, because every path
 * between them is clipped: the sheet clips to its rounded top, the full player's carousel
 * clips to its viewport, the mini pill clips to its own bounds — and in the default
 * configuration the mini player is not even inside the sheet, it is docked in the floating
 * tab bar, which the sheet does not contain.
 *
 * The interpolation happens entirely in the draw phase. `progress` is read inside
 * [drawWithCache]'s draw block, so a drag invalidates one draw call and recomposes
 * nothing; the ten-per-second position updates elsewhere in the player never reach it and
 * it never reaches them. The rect is re-cropped every frame rather than being scaled as a
 * whole, so a square thumbnail growing into a full-bleed panel reveals more of the image
 * instead of stretching what it already had.
 */
@Composable
fun PlayerArtworkFlight(modifier: Modifier = Modifier) {
    val expansion = LocalPlayerExpansion.current ?: return
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInWindow() },
    ) {
        val mini = expansion.miniBounds
        val full = expansion.fullBounds
        val visible = expansion.inTransit &&
            expansion.targetPresent &&
            mini != null && !mini.isEmpty &&
            full != null && !full.isEmpty

        if (visible) {
            TravellingArtwork(
                expansion = expansion,
                mini = mini.translate(-rootOrigin.x, -rootOrigin.y),
                full = full.translate(-rootOrigin.x, -rootOrigin.y),
            )
        }
    }
}

@Composable
private fun TravellingArtwork(
    expansion: PlayerExpansionState,
    mini: Rect,
    full: Rect,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Laid out once, over the union of the two anchors, and never re-measured. The union
    // is also what bounds the offscreen buffer the bottom-fade treatment needs; without it
    // that buffer would be the whole window.
    val union = Rect(
        left = min(mini.left, full.left),
        top = min(mini.top, full.top),
        right = max(mini.right, full.right),
        bottom = max(mini.bottom, full.bottom),
    )

    // Two layers of the same picture, not a crossfade between two pictures: the mini
    // player's copy is already in Coil's memory cache and shows through until the full
    // player's larger copy has decoded. On every expansion after the first, both are
    // cached and the lower one is never seen.
    val fallbackPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(expansion.miniModel)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
    )
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(expansion.fullModel ?: expansion.miniModel)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
    )

    val treatment = expansion.treatment
    val crop = expansion.cropArtwork

    Box(
        modifier = Modifier
            .absoluteOffset {
                IntOffset(union.left.roundToInt(), union.top.roundToInt())
            }
            .size(
                width = with(density) { union.width.toDp() },
                height = with(density) { union.height.toDp() },
            )
            .graphicsLayer {
                compositingStrategy = if (treatment == ArtworkTreatment.BottomFade) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
            }
            .drawWithCache {
                val clip = Path()
                val localMini = mini.translate(-union.left, -union.top)
                val localFull = full.translate(-union.left, -union.top)

                onDrawBehind {
                    val p = expansion.progress
                    val dst = Rect(
                        left = lerp(localMini.left, localFull.left, p),
                        top = lerp(localMini.top, localFull.top, p),
                        right = lerp(localMini.right, localFull.right, p),
                        bottom = lerp(localMini.bottom, localFull.bottom, p),
                    )
                    if (dst.isEmpty) return@onDrawBehind

                    val corner = lerp(expansion.miniCornerPx, expansion.fullCornerPx, p)
                        .coerceAtMost(dst.minDimension / 2f)

                    clip.reset()
                    clip.addRoundRect(RoundRect(dst, CornerRadius(corner, corner)))

                    clipPath(clip) {
                        drawArtwork(fallbackPainter, dst, crop)
                        drawArtwork(painter, dst, crop)
                    }

                    if (treatment == ArtworkTreatment.BottomFade) {
                        // The Apple Music panel dissolves into the page over its bottom
                        // quarter. Ramped in rather than switched on, so the hard edge of
                        // a small thumbnail softens on the way up instead of snapping soft
                        // on arrival.
                        val strength = ExpansionCurve.ramp(p, 0.35f, 1f)
                        if (strength > 0.001f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.00f to Color.Black,
                                        0.75f to Color.Black,
                                        0.92f to Color.Black.copy(alpha = lerp(1f, 0.4f, strength)),
                                        1.00f to Color.Black.copy(alpha = lerp(1f, 0f, strength)),
                                    ),
                                    startY = dst.top,
                                    endY = dst.bottom,
                                ),
                                topLeft = dst.topLeft,
                                size = dst.size,
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    }
                }
            },
    )
}

/**
 * Draws [painter] into [dst] with the same fit the two anchors use.
 *
 * Doing the cover maths here rather than handing the painter a `ContentScale` is what
 * keeps the crop honest at intermediate sizes: the source rectangle is recomputed for the
 * frame's actual destination, so the artwork reveals and conceals as it grows and shrinks
 * rather than deforming.
 */
internal fun DrawScope.drawArtwork(
    painter: Painter,
    dst: Rect,
    crop: Boolean,
) {
    val intrinsic = painter.intrinsicSize
    if (intrinsic.isUnspecified || intrinsic.width <= 0f || intrinsic.height <= 0f) {
        translate(dst.left, dst.top) {
            with(painter) { draw(dst.size) }
        }
        return
    }

    val scale = if (crop) {
        max(dst.width / intrinsic.width, dst.height / intrinsic.height)
    } else {
        min(dst.width / intrinsic.width, dst.height / intrinsic.height)
    }
    val drawnWidth = intrinsic.width * scale
    val drawnHeight = intrinsic.height * scale
    translate(
        left = dst.center.x - drawnWidth / 2f,
        top = dst.center.y - drawnHeight / 2f,
    ) {
        with(painter) { draw(Size(drawnWidth, drawnHeight)) }
    }
}
