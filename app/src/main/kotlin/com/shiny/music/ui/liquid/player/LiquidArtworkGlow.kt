package com.shiny.music.ui.liquid.player

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import com.shiny.music.ui.liquid.appearance.ArtworkGlow
import com.shiny.music.ui.player.PlayerBackdrop
import kotlinx.coroutines.flow.collectLatest

/**
 * The glow's colour for the playing artwork, eased over 700ms whenever the artwork — or
 * the Glow setting — changes, and transparent while Glow is off.
 *
 * Collected from a snapshot flow rather than read in composition, so a song change
 * animates the draw pass of the one node that paints the glow and recomposes nothing.
 */
@Composable
internal fun rememberArtworkGlowColor(backdrop: State<PlayerBackdrop>, glow: ArtworkGlow): State<Color> {
    val color = remember { Animatable(Color.Transparent) }
    LaunchedEffect(backdrop, glow) {
        snapshotFlow { if (glow == ArtworkGlow.Off) Color.Transparent else backdrop.value.halo ?: Color.Transparent }
            .collectLatest { color.animateTo(it, tween(700)) }
    }
    return color.asState()
}

/**
 * Light bleeding from behind a piece of artwork: a soft radial halo in [color], centred on
 * this node and [spread] times its shorter side across.
 *
 * Place it *before* the artwork's clipping `graphicsLayer` in the modifier chain: it draws
 * in the parent's pass, so it spills past the artwork's rounded edge, and the artwork's
 * own layer — living motion, video — stays a separate render node that never re-records
 * this. The gradient is rebuilt only when the size or colour changes; [strength] (0..1)
 * and [sizeFactor] are read at draw time, so the pause settle and the mode flight only redraw.
 * Nothing is drawn while [strength] is zero or the colour is transparent.
 */
internal fun Modifier.artworkGlow(
    color: State<Color>,
    strength: () -> Float,
    sizeFactor: () -> Float = { 1f },
    spread: Float = 1.6f,
): Modifier = drawWithCache {
    val c = color.value
    val middle = size.center
    val radius = size.minDimension * 0.5f * spread
    val brush = if (c.alpha > 0f && radius > 0f) {
        Brush.radialGradient(
            0f to c,
            0.55f to c.copy(alpha = c.alpha * 0.82f),
            0.8f to c.copy(alpha = c.alpha * 0.28f),
            1f to Color.Transparent,
            center = middle,
            radius = radius,
        )
    } else {
        null
    }
    onDrawBehind {
        val a = strength().coerceIn(0f, 1f)
        if (brush != null && a > 0.004f) {
            val s = sizeFactor()
            scale(s, s, pivot = middle) {
                drawCircle(brush = brush, radius = radius, center = middle, alpha = a)
            }
        }
    }
}
