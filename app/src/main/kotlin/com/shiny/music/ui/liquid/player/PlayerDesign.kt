package com.shiny.music.ui.liquid.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.shiny.music.R
import com.shiny.music.ui.liquid.LiquidTypography

internal object PlayerSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

internal object PlayerMotion {
    const val Press = 120
    const val Small = 180
    const val Queue = 300
    const val Artwork = 380

    val modeSpring: SpringSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 280f)
    fun <T> small(): TweenSpec<T> = tween(Small, easing = FastOutSlowInEasing)
    fun <T> queue(): TweenSpec<T> = tween(Queue, easing = FastOutSlowInEasing)
}

internal object QueueMetrics {
    val rowMinHeight = 68.dp
    val artwork = 52.dp
    val target = 48.dp
    val modeTileHeight = 60.dp
}

internal object PlayerType {
    val stageTitle = LiquidTypography.title2.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
    val stageArtist = LiquidTypography.title3.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val section = LiquidTypography.title2
    val rowTitle = LiquidTypography.callout.copy(fontWeight = FontWeight.SemiBold)
    val rowMeta = LiquidTypography.subheadline
    val label = LiquidTypography.footnote.copy(fontWeight = FontWeight.SemiBold)
}

internal enum class QueueRole { Played, Current, Upcoming }

internal data class QueueEntry<T>(val item: T, val role: QueueRole)

internal fun <T> queueEntries(played: List<T>, current: T?, upcoming: List<T>, upcomingLimit: Int): List<QueueEntry<T>> {
    val shown = upcoming.size.coerceAtMost(upcomingLimit.coerceAtLeast(0))
    return ArrayList<QueueEntry<T>>(played.size + 1 + shown).apply {
        played.forEach { add(QueueEntry(it, QueueRole.Played)) }
        current?.let { add(QueueEntry(it, QueueRole.Current)) }
        for (i in 0 until shown) add(QueueEntry(upcoming[i], QueueRole.Upcoming))
    }
}

internal fun repeatLabel(repeatMode: Int): Int = when (repeatMode) {
    Player.REPEAT_MODE_ALL -> R.string.liquid_repeat_all
    Player.REPEAT_MODE_ONE -> R.string.liquid_repeat_one
    else -> R.string.liquid_repeat
}
