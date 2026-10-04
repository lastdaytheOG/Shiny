package com.shiny.music.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shiny.music.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The heart of a like button. Turning a song from not liked to liked makes it swell and settle
 * while a ring of dots flies out; nothing moves when it first appears or when the like is removed.
 *
 * The animation is read in the draw phase only, so it costs no recomposition.
 */
@Composable
fun LikeGlyph(
    liked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val progress = remember { Animatable(LikeBurst.REST) }
    val memory = remember { LikeMemory() }
    LaunchedEffect(liked) {
        val burst = LikeBurst.startsOn(memory.last, liked)
        memory.last = liked
        if (burst) {
            progress.snapTo(0f)
            progress.animateTo(LikeBurst.REST, tween(LikeBurst.DURATION_MS, easing = LinearEasing))
        } else {
            progress.snapTo(LikeBurst.REST)
        }
    }

    val accent = Liquid.colors.accent
    Icon(
        painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
        contentDescription = null,
        tint = if (liked) accent else LocalContentColor.current,
        modifier = modifier
            .size(size)
            .drawBehind {
                val t = progress.value
                if (t >= LikeBurst.REST) return@drawBehind
                val unit = this.size.minDimension
                val reach = LikeBurst.reach(t) * unit
                val dot = LikeBurst.dotRadius(t) * unit
                val color = accent.copy(alpha = LikeBurst.dotAlpha(t))
                repeat(LikeBurst.DOTS) { index ->
                    val angle = LikeBurst.angle(index)
                    drawCircle(color, dot, center + Offset(cos(angle) * reach, sin(angle) * reach))
                }
            }
            .graphicsLayer {
                val scale = LikeBurst.scale(progress.value)
                scaleX = scale
                scaleY = scale
            },
    )
}

/** What the glyph showed last, kept outside the snapshot system: only the effect reads it. */
private class LikeMemory {
    var last: Boolean? = null
}

/** The shape of the like animation as plain numbers. `t` runs from 0 (just liked) to 1 (at rest). */
internal object LikeBurst {
    const val REST = 1f
    const val DURATION_MS = 460
    const val DOTS = 7

    /** Where the swell peaks. */
    private const val PEAK_AT = 0.28f
    private const val SWELL = 0.3f

    /** Only a change from "not liked" to "liked" animates; a first appearance never does. */
    fun startsOn(previous: Boolean?, liked: Boolean): Boolean = previous == false && liked

    /** The heart's size: 1 at both ends, larger in between, dipping just under 1 as it settles. */
    fun scale(t: Float): Float {
        if (t <= 0f || t >= REST) return 1f
        if (t < PEAK_AT) return 1f + SWELL * sin(PI.toFloat() / 2f * (t / PEAK_AT))
        val settle = (t - PEAK_AT) / (1f - PEAK_AT)
        return 1f + SWELL * (1f - settle) * (1f - settle) * cos(settle * PI.toFloat() * 1.5f)
    }

    /** How far the dots are from the centre, in glyph sizes. They slow down as they go. */
    fun reach(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 0.45f + 0.55f * (1f - (1f - c) * (1f - c))
    }

    /** The dots stay solid at first and are gone by the end. */
    fun dotAlpha(t: Float): Float = ((1f - t) / 0.6f).coerceIn(0f, 1f)

    /** The dots shrink as they travel, in glyph sizes. */
    fun dotRadius(t: Float): Float = 0.07f * (1f - 0.6f * t.coerceIn(0f, 1f))

    /** Dots are spread evenly round the heart, the first one straight up. */
    fun angle(index: Int): Float = (-PI / 2 + 2 * PI * index / DOTS).toFloat()
}
