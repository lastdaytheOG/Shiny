

package com.shiny.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.rememberRowHighlight

/**
 * A group of menu actions, drawn as an iOS inset-grouped section: one rounded panel,
 * hairlines between rows starting under the text, label on the left and the glyph on the
 * right — the arrangement of a context menu, not a stack of separate cards.
 */
@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>,
    modifier: Modifier = Modifier
) {
    val colors = Liquid.colors
    val panel = if (colors.isDark) colors.tertiaryBackground else colors.secondaryGroupedBackground
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(panel)
            .animateContentSize(),
    ) {
        items.forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (item.onClick != null) {
                            Modifier.clickable(
                                interactionSource = interaction,
                                indication = rememberRowHighlight(),
                                onClick = item.onClick,
                            )
                        } else {
                            Modifier
                        }
                    ),
            ) {
                if (item.customComposable != null) {
                    item.customComposable.invoke()
                } else {
                    Material3MenuItemRow(item = item)
                }
                if (index != items.lastIndex) {
                    Hairline(Modifier.align(Alignment.BottomStart), startIndent = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun Material3MenuItemRow(
    item: Material3MenuItemData
) {
    val colors = Liquid.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // With a trailing control (a switch, a value) the glyph moves to the leading
        // edge, as in Settings; otherwise it sits at the end, as in a context menu.
        if (item.trailingContent != null) {
            item.icon?.let { icon ->
                CompositionLocalProvider(LocalContentColor provides colors.label) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides colors.label) {
                ProvideTextStyle(LiquidTypography.body.copy(color = colors.label)) {
                    item.title?.invoke()
                }
            }
            item.description?.let { desc ->
                ProvideTextStyle(LiquidTypography.footnote.copy(color = colors.secondaryLabel)) {
                    desc()
                }
            }
        }
        if (item.trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            item.trailingContent.invoke()
        } else {
            item.icon?.let { icon ->
                Spacer(modifier = Modifier.width(12.dp))
                CompositionLocalProvider(LocalContentColor provides colors.label) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
                }
            }
        }
    }
}

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: (@Composable () -> Unit)? = null,
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val customComposable: (@Composable () -> Unit)? = null
)
