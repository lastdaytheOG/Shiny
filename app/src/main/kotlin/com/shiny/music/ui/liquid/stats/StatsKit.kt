package com.shiny.music.ui.liquid.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.appearance.rememberReducedMotion
import java.text.NumberFormat
import java.util.Locale

internal val StatsEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
internal val StatsViolet = Color(0xFFAF52DE)
internal const val Tabular = "tnum"
private const val FreshWindowMs = 900L

internal class StatsClock(val openedAt: Long) {
    val fresh: Boolean get() = System.currentTimeMillis() - openedAt < FreshWindowMs
}

internal val LocalStatsClock = staticCompositionLocalOf { StatsClock(0L) }

@Composable
internal fun rememberStatsClock(): StatsClock = remember { StatsClock(System.currentTimeMillis()) }

internal fun count(n: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

@Composable
internal fun Modifier.statsGlass(shape: RoundedCornerShape): Modifier {
    val dark = Liquid.colors.isDark
    val fill = if (dark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.62f)
    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.24f else 0.95f),
            Color.White.copy(alpha = if (dark) 0.02f else 0.30f),
        ),
    )
    return this
        .then(if (dark) Modifier else Modifier.shadow(20.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1F1D2A55)))
        .clip(shape)
        .background(fill)
        .border(BorderStroke(0.8.dp, rim), shape)
}

@Composable
internal fun Rise(delayMs: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduce = rememberReducedMotion()
    val clock = LocalStatsClock.current
    val animate = remember { !reduce && clock.fresh }
    if (!animate) {
        Box(modifier) { content() }
        return
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        t.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 240f))
    }
    Box(
        modifier.graphicsLayer {
            val v = t.value
            alpha = v.coerceIn(0f, 1f)
            translationY = (1f - v) * 28.dp.toPx()
            val s = 0.965f + 0.035f * v
            scaleX = s
            scaleY = s
        },
    ) { content() }
}

@Composable
internal fun rememberCountUp(target: Long): () -> Long {
    val reduce = rememberReducedMotion()
    val clock = LocalStatsClock.current
    val value = remember { Animatable(if (reduce || !clock.fresh) target.toFloat() else 0f) }
    LaunchedEffect(target) {
        if (reduce) {
            value.snapTo(target.toFloat())
        } else {
            val far = value.value < target * 0.5f
            value.animateTo(target.toFloat(), tween(if (far) 1100 else 520, easing = StatsEase))
        }
    }
    val v by remember { derivedStateOf { value.value.toLong() } }
    return { v }
}
