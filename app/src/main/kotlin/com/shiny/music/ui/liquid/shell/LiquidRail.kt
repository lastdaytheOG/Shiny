package com.shiny.music.ui.liquid.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.screens.Screens
import com.shiny.music.ui.theme.pressScale

/**
 * The tab bar turned on its side for landscape: the same glyphs in a slim capsule at the
 * leading edge, the selected tab in the tint on a soft lozenge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidRail(
    items: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Liquid.colors
    Box(
        modifier
            .fillMaxHeight()
            .width(88.dp)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(CircleShape)
                .background(if (colors.isDark) colors.secondaryBackground else colors.secondaryBackground)
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { screen ->
                val selected = currentRoute == screen.route || currentRoute?.startsWith("${screen.route}/") == true
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(54.dp)
                        .pressScale(interaction, 0.9f)
                        .clip(CircleShape)
                        .background(if (selected) colors.fill else androidx.compose.ui.graphics.Color.Transparent)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onItemClick(screen, selected) },
                            onLongClick = if (screen == Screens.Search) onSearchLongClick else null,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = liquidTabIcon(screen),
                        contentDescription = stringResource(screen.titleId),
                        tint = if (selected) colors.accent else colors.label,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
    }
}
