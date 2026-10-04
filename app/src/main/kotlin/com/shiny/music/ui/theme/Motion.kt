package com.shiny.music.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * Shiny Music's motion language.
 *
 * Every spec here is a spring, never a duration-based curve. That is the whole point:
 * a tween has to finish the arc it committed to when it started, so interrupting one
 * either snaps or queues. A spring carries its velocity across the interruption, so a
 * sheet grabbed mid-flight follows the finger from wherever it actually is. That
 * continuity — not the easing curve — is what reads as "expensive" in iOS, and it is
 * what a duration cannot buy.
 *
 * Damping ratios sit at or just under 1. Anything visibly bouncier than [Expressive]
 * starts to feel like a toy rather than a physical surface.
 */
object AppMotion {

    /** Taps, toggles, selection changes — settles fast enough to feel instantaneous. */
    val Snappy: AnimationSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessHigh,
    )

    /** The default. Content swaps, expansions, most state changes. */
    val Standard: AnimationSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Large surfaces — sheets, the player, anything the size of the screen. */
    val Gentle: AnimationSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = 260f,
    )

    /** A single, deliberate overshoot. For moments that should feel rewarding. */
    val Expressive: AnimationSpec<Float> = spring(
        dampingRatio = 0.6f,
        stiffness = 480f,
    )
}

/**
 * Scales the composable down while [interactionSource] reports a press.
 *
 * The press must come from the real `clickable`, which is why the source is passed in
 * rather than created here — a modifier that owned its own source would animate on
 * touches the click handler never sees, and drift out of sync during a drag-off.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = AppMotion.Snappy,
        label = "pressScale",
    )
    scale(scale)
}

/** Convenience for call sites that only need the animated value. */
@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = AppMotion.Snappy,
        label = "pressScale",
    )
    return scale
}
