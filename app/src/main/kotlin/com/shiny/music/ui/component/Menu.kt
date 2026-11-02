

package com.shiny.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.theme.pressScale
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PanelGlyph
import com.shiny.music.ui.liquid.PanelGlyphGap
import com.shiny.music.ui.liquid.PanelPadding
import com.shiny.music.ui.liquid.liquidPanel
import com.shiny.music.ui.liquid.rememberRowHighlight

/** Where the glyphs of a group of menu rows sit. */
enum class MenuGlyphs {
    /** At the trailing edge, the label leading: the arrangement of a context menu. */
    Trailing,

    /** At the leading edge, the trailing edge kept for a chevron: a list of places to go. */
    Leading,
}

@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>,
    modifier: Modifier = Modifier,
    glyphs: MenuGlyphs = MenuGlyphs.Leading,
    descriptions: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item -> MenuPillRow(item, descriptions) }
    }
}

// every option is its own pill now, no shared panel and no hairlines between rows
@Composable
private fun MenuPillRow(item: Material3MenuItemData, descriptions: Boolean) {
    val colors = Liquid.colors
    val enabled = item.onClick != null || item.customComposable != null || item.trailingContent != null
    val base = if (item.destructive) colors.destructive else colors.label
    val ink = if (enabled) base else base.copy(alpha = 0.35f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(MenuPillShape)
            .background(menuRowSurface())
            .then(
                if (item.onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = item.onClick)
                } else {
                    Modifier
                }
            ),
    ) {
        if (item.customComposable != null) {
            item.customComposable.invoke()
            return@Box
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.icon?.let { icon ->
                CompositionLocalProvider(LocalContentColor provides ink) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(LocalContentColor provides ink) {
                    ProvideTextStyle(TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink)) {
                        item.title?.invoke()
                    }
                }
                // menus stay one line per option, sheets that explain choices turn this on
                if (descriptions) {
                    item.description?.let { desc ->
                        ProvideTextStyle(TextStyle(fontSize = 12.sp, color = colors.label.copy(alpha = 0.55f))) {
                            desc()
                        }
                    }
                }
            }
            when {
                item.trailingContent != null -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    item.trailingContent.invoke()
                }
                item.chevron -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = colors.tertiaryLabel,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private val MenuPillShape = RoundedCornerShape(16.dp)

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: (@Composable () -> Unit)? = null,
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val customComposable: (@Composable () -> Unit)? = null,
    val chevron: Boolean = false,
    val destructive: Boolean = false,
)
