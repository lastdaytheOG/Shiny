

package com.shiny.music.ui.component

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

/**
 * A group of menu actions, drawn as one panel: hairlines between rows starting under the
 * text, the label on the left and the glyph on the right — the arrangement of a context
 * menu, not a stack of separate cards. [glyphs] moves the glyphs to the leading edge for a
 * group that is mostly places to go.
 */
@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>,
    modifier: Modifier = Modifier,
    glyphs: MenuGlyphs = MenuGlyphs.Trailing,
) {
    val colors = Liquid.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidPanel()
            .animateContentSize(),
    ) {
        items.forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            val glyphLeads = item.leadsWithGlyph(glyphs)
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
                    Material3MenuItemRow(item = item, glyphLeads = glyphLeads)
                }
                if (index != items.lastIndex) {
                    Hairline(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(end = PanelPadding),
                        startIndent = if (glyphLeads && item.icon != null) {
                            PanelPadding + PanelGlyph + PanelGlyphGap
                        } else {
                            PanelPadding
                        },
                        color = colors.panelSeparator,
                    )
                }
            }
        }
    }
}

/**
 * With something at the trailing edge (a switch, a value, a chevron) the glyph moves to the
 * leading edge, as in Settings; otherwise it sits where the group puts its glyphs.
 */
private fun Material3MenuItemData.leadsWithGlyph(glyphs: MenuGlyphs): Boolean =
    glyphs == MenuGlyphs.Leading || trailingContent != null || chevron

@Composable
private fun Material3MenuItemRow(
    item: Material3MenuItemData,
    glyphLeads: Boolean,
) {
    val colors = Liquid.colors
    val ink = if (item.destructive) colors.destructive else colors.label
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MenuRowMinHeight)
            .padding(horizontal = PanelPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyphLeads) {
            item.icon?.let { icon ->
                CompositionLocalProvider(LocalContentColor provides ink) {
                    Box(Modifier.size(PanelGlyph), contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(modifier = Modifier.width(PanelGlyphGap))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides ink) {
                ProvideTextStyle(LiquidTypography.body.copy(color = ink)) {
                    item.title?.invoke()
                }
            }
            item.description?.let { desc ->
                ProvideTextStyle(LiquidTypography.footnote.copy(color = colors.secondaryLabel)) {
                    desc()
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

            !glyphLeads -> item.icon?.let { icon ->
                Spacer(modifier = Modifier.width(12.dp))
                CompositionLocalProvider(LocalContentColor provides ink) {
                    Box(Modifier.size(PanelGlyph), contentAlignment = Alignment.Center) { icon() }
                }
            }
        }
    }
}

/** A row with one line of text; a second line grows it. */
private val MenuRowMinHeight = 56.dp

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: (@Composable () -> Unit)? = null,
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val cardColors: CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val customComposable: (@Composable () -> Unit)? = null,
    /** The row opens another page: it ends in a chevron, and its glyph leads. */
    val chevron: Boolean = false,
    /** The row removes something for good: its label and glyph are drawn in the warning colour. */
    val destructive: Boolean = false,
)
