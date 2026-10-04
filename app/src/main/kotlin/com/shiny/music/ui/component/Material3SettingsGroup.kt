package com.shiny.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.rememberRowHighlight

/**
 * The grouped rows used by the settings screens that have not been rebuilt on
 * [com.shiny.music.ui.liquid.settings.SettingsSection] yet.
 *
 * Rewritten to Shiny's settings language: no card, no coloured glyph plate per row. A
 * small tracked label names the group, the rows sit on the page's own ground, and a
 * hairline separates them. Any glyph a caller passes is drawn once, monochrome, at the
 * weight of secondary text — it identifies the row, it does not decorate it.
 *
 * Rows carry no horizontal gutter of their own: every caller already insets the group,
 * and adding a second inset here is what used to push settings text a third of the way
 * across the screen.
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    compact: Boolean = false,
    items: List<Material3SettingsItem>
) {
    val colors = Liquid.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title == null) {
            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
        } else {
            Text(
                text = title.uppercase(),
                style = LiquidTypography.caption1.copy(fontWeight = FontWeight.SemiBold),
                letterSpacing = 0.9.sp,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(
                    bottom = 9.dp,
                    top = if (compact) 16.dp else 26.dp,
                )
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                Material3SettingsItemRow(
                    item = item,
                    showSeparator = index != items.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem,
    showSeparator: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = item.enabled && item.onClick != null
    val colors = Liquid.colors
    val glyphTint = if (item.enabled) colors.secondaryLabel else colors.tertiaryLabel

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRowHighlight(),
                enabled = clickable,
                onClick = { item.onClick?.invoke() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.customIcon != null) {
                Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides glyphTint
                    ) {
                        item.customIcon.invoke()
                    }
                }
            } else item.icon?.let { icon ->
                Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                    if (item.tintIcon) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = glyphTint,
                            modifier = Modifier.size(21.dp)
                        )
                    } else {
                        Image(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(item.iconShape ?: CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                ProvideTextStyle(
                    LiquidTypography.body.copy(
                        color = if (!item.enabled) colors.tertiaryLabel else colors.label
                    )
                ) {
                    item.title()
                }
                item.description?.let { desc ->
                    ProvideTextStyle(
                        LiquidTypography.footnote.copy(
                            color = if (!item.enabled) colors.tertiaryLabel else colors.secondaryLabel
                        )
                    ) {
                        desc()
                    }
                }
            }

            if (item.showBadge) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.destructive)
                )
            }

            if (item.trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                item.trailingContent.invoke()
            } else if (clickable) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showSeparator) {
            Hairline(modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

data class Material3SettingsItem(
    val icon: Painter? = null,
    val customIcon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val tintIcon: Boolean = true,
    val iconShape: Shape? = null,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null
)
