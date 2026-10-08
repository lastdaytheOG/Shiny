package com.shiny.music.ui.liquid

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

/**
 * The panel language: the one raised surface option sheets and the Settings index are built
 * from, and the colour a sheet takes from the artwork it is about.
 *
 * A panel is a fill and a rim, nothing else. In the dark themes the fill is translucent, so
 * the ground under it shows through, and the rim is the light its upper edge catches. Nothing
 * here blurs, samples a backdrop or runs per frame: it is drawn once and stays drawn.
 */

/** Corner of a grouped panel. */
val PanelRadius = 22.dp
val PanelShape = RoundedCornerShape(PanelRadius)

/** Corner of a quick-action tile: a step tighter than a panel, in proportion to its height. */
val TileShape = RoundedCornerShape(18.dp)

/** From the edge of the screen or sheet to a panel. */
val PanelMargin = 16.dp

/** From a panel's edge to the content of its rows. */
val PanelPadding = 16.dp

/** The glyph column of a panel row. Every glyph in these rows is drawn in this box. */
val PanelGlyph = 24.dp

/** Between a row's leading glyph and its text. */
val PanelGlyphGap = 16.dp

private val PanelRimWidth = 0.5.dp

/** Draws this element as a grouped panel and clips its content to [shape]. */
@Composable
fun Modifier.liquidPanel(shape: Shape = PanelShape, fill: Color = Color.Unspecified): Modifier {
    val colors = Liquid.colors
    val rim = colors.panelRim
    val edge = remember(rim) {
        if (rim.alpha == 0f) {
            null
        } else {
            Brush.verticalGradient(
                0f to rim,
                0.5f to rim.copy(alpha = rim.alpha * 0.35f),
                1f to rim.copy(alpha = rim.alpha * 0.45f),
            )
        }
    }
    val filled = clip(shape).background(if (fill.isSpecified) fill else colors.panel)
    return if (edge == null) filled else filled.border(PanelRimWidth, edge, shape)
}

/**
 * The colour an option sheet takes from the artwork it is about: light falling from the
 * cover at the head of the sheet, in the cover's own tones, and dying away towards the foot.
 *
 * The sheet owns one of these and draws it (see [sheetAtmosphere]); whichever header is
 * showing names the artwork (see [SheetAtmosphereEffect]). The tones come from the same tiny
 * decode and cache the collection pages use, so a cover is read once. It is one gradient,
 * built when the artwork changes and kept: nothing is blurred or layered, a change of artwork
 * recomposes nothing, and a sheet that is not redrawing does not draw it.
 */
@Stable
class SheetAtmosphere {
    /** The artwork the sheet is about, or null for a sheet with no colour of its own. */
    var artwork: String? by mutableStateOf(null)

    private var tones: ArtworkTones? by mutableStateOf(null)
    private val presence = Animatable(0f)

    /** Keeps the tones in step with [artwork]. Runs for as long as the sheet exists. */
    suspend fun follow(context: Context) {
        snapshotFlow { artwork }.collectLatest { url ->
            if (url == null) {
                presence.snapTo(0f)
                tones = null
                return@collectLatest
            }
            val known = cachedArtworkTones(url)
            if (known != null) {
                // Already read: the sheet arrives wearing its colour.
                tones = known
                presence.snapTo(1f)
                return@collectLatest
            }
            presence.snapTo(0f)
            tones = loadArtworkTones(context, url) ?: return@collectLatest
            presence.animateTo(1f, tween(durationMillis = 420))
        }
    }

    /**
     * The gradient, in the sheet's own coordinates, or null while there is nothing to draw.
     *
     * It is centred on the cover in the menu's header and measured in dp, not from the
     * element drawing it, so the pieces of a sheet can each draw their slice of one picture.
     *
     * In the dark themes the two colours are held to a luminance (0.05 under the rows, 0.065
     * behind the header) at which secondary text keeps 4.5:1, on the bare sheet and on a
     * panel raised off it. A cover with no colour to give would only turn the sheet a
     * lighter grey, so it gives half as much.
     */
    internal fun brush(density: Density, dark: Boolean): Brush? {
        val tones = tones ?: return null
        val strength = presence.value
        if (strength <= 0f) return null
        val wash: Color
        val light: Color
        val peak: Float
        if (dark) {
            wash = lerp(tones.deep, tones.vivid, 0.5f).heldBelow(0.05f)
            light = lerp(tones.deep, tones.vivid, 0.8f).heldBelow(0.065f)
            val colour = (wash.saturation() / 0.45f).coerceIn(0f, 1f)
            peak = (0.36f + 0.38f * colour) * strength
        } else {
            wash = lerp(tones.deep, tones.vivid, 0.5f)
            light = tones.vivid
            peak = 0.14f * strength
        }
        return with(density) {
            Brush.radialGradient(
                0f to light.copy(alpha = peak),
                0.12f to wash.copy(alpha = peak * 0.9f),
                0.3f to wash.copy(alpha = peak * 0.78f),
                0.62f to wash.copy(alpha = peak * 0.5f),
                1f to wash.copy(alpha = peak * 0.22f),
                center = Offset(60.dp.toPx(), 72.dp.toPx()),
                radius = 880.dp.toPx(),
            )
        }
    }
}

/** Past an element's sides, so a sheet inset for a cutout is coloured to its edges. */
private val AtmosphereBleed = 96.dp

/**
 * Draws behind this element the slice of [atmosphere] that starts [top] down the sheet.
 * [below] carries the slice past the element's foot, over the sheet's bottom inset.
 */
fun Modifier.sheetAtmosphere(
    atmosphere: SheetAtmosphere,
    dark: Boolean,
    top: Dp = 0.dp,
    below: Dp = 0.dp,
): Modifier = drawWithCache {
    val brush = atmosphere.brush(this, dark)
    val topPx = top.toPx()
    val bleed = AtmosphereBleed.toPx()
    val area = Size(size.width + bleed * 2f, size.height + below.toPx())
    onDrawBehind {
        if (brush != null) {
            translate(top = -topPx) {
                drawRect(brush = brush, topLeft = Offset(-bleed, topPx), size = area)
            }
        }
    }
}

/** Darkens a colour, keeping its hue, until it is no brighter than [luminance]. */
private fun Color.heldBelow(luminance: Float): Color {
    var c = this
    var guard = 0
    while (c.luminance() > luminance && guard < 10) {
        c = lerp(c, Color.Black, 0.14f)
        guard++
    }
    return c
}

/** How far a colour is from grey, 0..1: the spread of its channels against its brightest. */
private fun Color.saturation(): Float {
    val max = maxOf(red, green, blue)
    return if (max <= 0f) 0f else (max - minOf(red, green, blue)) / max
}

/** The atmosphere of the sheet this content is shown in, when it is shown in one. */
val LocalSheetAtmosphere = staticCompositionLocalOf<SheetAtmosphere?> { null }

/** Names [artwork] as what the enclosing sheet is about, for as long as the caller is shown. */
@Composable
fun SheetAtmosphereEffect(artwork: String?) {
    val atmosphere = LocalSheetAtmosphere.current ?: return
    DisposableEffect(atmosphere, artwork) {
        atmosphere.artwork = artwork
        onDispose {
            if (atmosphere.artwork == artwork) atmosphere.artwork = null
        }
    }
}
