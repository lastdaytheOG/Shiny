package com.shiny.music.ui.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The ground of the current page, so bars can match it (grouped grey on Settings). */
val LocalLiquidPageGround = staticCompositionLocalOf { Color.Unspecified }

/**
 * An iOS navigation bar with Material's `TopAppBar` signature, so the screens that still
 * build their own bar move over with an import alias: the title centred in the headline
 * style, the back chevron and actions in the tint colour, on the page's own ground.
 * `colors`, `windowInsets`, `expandedHeight` and `scrollBehavior` are accepted and ignored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = 52.dp,
    windowInsets: WindowInsets? = null,
    colors: TopAppBarColors? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val c = Liquid.colors
    val ground = LocalLiquidPageGround.current.takeIf { it.isSpecified } ?: c.background
    Box(
        modifier
            .fillMaxWidth()
            .background(ground.copy(alpha = 0.97f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(52.dp),
    ) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides c.accent) { navigationIcon() }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 76.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides c.label) {
                ProvideTextStyle(LiquidTypography.headline.copy(color = c.label)) { title() }
            }
        }
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides c.accent) { actions() }
        }
    }
}
