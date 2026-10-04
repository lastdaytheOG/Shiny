package com.shiny.music.ui.liquid.shell

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.shiny.music.ui.liquid.appearance.PageTransitions
import com.shiny.music.ui.screens.Screens

/**
 * Page transitions in the UIKit idiom.
 *
 * A push is the new page sliding in from the right edge over the old one, which drifts a
 * third of its width to the left and dims; a pop is the exact inverse. The curve is the
 * fast-out, long-settle shape of UINavigationController — most of the distance in the
 * first 40% — rather than Material's symmetric ease.
 *
 * That is [PageTransitions.Slide], the default. [PageTransitions.Fade] dissolves every
 * page change the way tab switches already do, and [PageTransitions.Instant] cuts. All
 * three keep predictive back working: it scrubs whichever pair is chosen.
 */
object LiquidNav {
    private val IosEase = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
    private const val PushMillis = 420
    private const val TabMillis = 160
    private const val FadeMillis = 200

    val pushEnter: EnterTransition =
        slideInHorizontally(tween(PushMillis, easing = IosEase)) { it }

    val pushExit: ExitTransition =
        slideOutHorizontally(tween(PushMillis, easing = IosEase)) { -it / 3 } +
            fadeOut(tween(PushMillis, easing = IosEase), targetAlpha = 0.55f)

    val popEnter: EnterTransition =
        slideInHorizontally(tween(PushMillis, easing = IosEase)) { -it / 3 } +
            fadeIn(tween(PushMillis, easing = IosEase), initialAlpha = 0.55f)

    val popExit: ExitTransition =
        slideOutHorizontally(tween(PushMillis, easing = IosEase)) { it }

    val tabEnter: EnterTransition = fadeIn(tween(TabMillis))
    val tabExit: ExitTransition = fadeOut(tween(TabMillis))

    private val fadeEnter: EnterTransition = fadeIn(tween(FadeMillis))
    private val fadeExit: ExitTransition = fadeOut(tween(FadeMillis))

    fun enter(style: PageTransitions, tabSwitch: Boolean): EnterTransition = when (style) {
        PageTransitions.Slide -> if (tabSwitch) tabEnter else pushEnter
        PageTransitions.Fade -> if (tabSwitch) tabEnter else fadeEnter
        PageTransitions.Instant -> EnterTransition.None
    }

    fun exit(style: PageTransitions, tabSwitch: Boolean): ExitTransition = when (style) {
        PageTransitions.Slide -> if (tabSwitch) tabExit else pushExit
        PageTransitions.Fade -> if (tabSwitch) tabExit else fadeExit
        PageTransitions.Instant -> ExitTransition.None
    }

    fun popEnter(style: PageTransitions, tabSwitch: Boolean): EnterTransition = when (style) {
        PageTransitions.Slide -> if (tabSwitch) tabEnter else popEnter
        PageTransitions.Fade -> if (tabSwitch) tabEnter else fadeEnter
        PageTransitions.Instant -> EnterTransition.None
    }

    fun popExit(style: PageTransitions, tabSwitch: Boolean): ExitTransition = when (style) {
        PageTransitions.Slide -> if (tabSwitch) tabExit else popExit
        PageTransitions.Fade -> if (tabSwitch) tabExit else fadeExit
        PageTransitions.Instant -> ExitTransition.None
    }
}

/** True when both ends of a navigation are top-level tabs. */
fun isTabSwitch(from: String?, to: String?, tabs: List<Screens>): Boolean {
    val routes = tabs.map { it.route }
    return from in routes && to in routes
}
