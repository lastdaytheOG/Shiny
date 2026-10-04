package com.shiny.music.ui.liquid.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.liquid.LiquidIcons
import com.shiny.music.ui.screens.Screens

/** Gap between the bottom of the chrome and the system navigation bar. */
val LiquidChromeBottomMargin = 6.dp

/** The glyph each top-level destination wears in the tab bar. */
fun liquidTabIcon(screen: Screens): ImageVector = when (screen) {
    Screens.Home -> Icons.Rounded.Home
    Screens.Explore -> LiquidIcons.Grid
    Screens.Library -> LiquidIcons.Library
    Screens.ListenTogether -> LiquidIcons.Waves
    else -> Icons.Rounded.Search
}
