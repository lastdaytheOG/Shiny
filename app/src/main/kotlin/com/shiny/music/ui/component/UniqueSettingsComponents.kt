package com.shiny.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tile colours for a settings row, derived from the live colour scheme.
 *
 * The settings screens previously hard-coded nine unrelated stock gradients
 * (`#00C6FF→#0072FF`, `#A855F7→#EC4899`, `#FF512F→#DD2476`, …). Hue therefore carried no
 * meaning — Storage was amber for no reason — and none of it belonged to Shiny's own
 * palette, which is the whole point of deriving a scheme from album artwork. A rainbow
 * of borrowed gradients is also the single most recognisable "stock settings screen"
 * signal there is.
 *
 * These tiles ride the real scheme instead, and separate by **tone** rather than hue:
 * three scheme anchors rotate so neighbouring rows stay distinguishable, while every
 * tile remains inside one family. The screen now shifts with what you're playing.
 *
 * @param step the row's index within the screen; callers pass a stable ordinal.
 */
@Composable
fun settingsTileColors(step: Int): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val anchors = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    val base = anchors[step.mod(anchors.size)]
    // Deepen the second stop slightly per lap so a long list doesn't repeat exactly.
    val depth = 0.20f + 0.07f * ((step / anchors.size).mod(3))
    return listOf(base, lerp(base, Color.Black, depth))
}

/**
 * Modern Liquid Gradient Icon Badge with rich dual-tone gradient background
 * and subtle floating depth effect.
 */
@Composable
fun LiquidGradientIconBadge(
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    customIcon: (@Composable () -> Unit)? = null,
    tint: Color = Color.White,
    showBadge: Boolean = false,
    badgeColor: Color = MaterialTheme.colorScheme.error,
    shape: Shape = RoundedCornerShape(14.dp),
    size: Dp = 42.dp,
    iconSize: Dp = 22.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = gradientColors.first().copy(alpha = 0.35f),
                spotColor = gradientColors.last().copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(
                brush = Brush.linearGradient(colors = gradientColors)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (customIcon != null) {
            customIcon()
        } else icon?.let {
            if (showBadge) {
                BadgedBox(
                    badge = {
                        Badge(containerColor = badgeColor)
                    }
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(iconSize)
                    )
                }
            } else {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

/**
 * Unique Settings Section Group Card with floating glassmorphic container style,
 * smooth item dividers, and spring animation touch response.
 */
@Composable
fun UniqueSettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    items: List<UniqueSettingsItemData>
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        title?.let {
            // Was `.uppercase()` at +1.2sp tracking in `primary`. Two problems: a tracked
            // all-caps eyebrow over every group is the stock-settings tell, and DESIGN.md
            // reserves accent colour for things that can be *tapped* — a group heading
            // can't. Sentence case in the supporting-copy role reads calmer and lets the
            // row titles below it own the hierarchy.
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 10.dp, top = 20.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, itemData ->
                    UniqueSettingsItemRow(item = itemData)
                    if (index != items.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            modifier = Modifier.padding(start = 74.dp, end = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Settings Row with spring tactile press feedback, gradient icon badge,
 * title, description, and modern chevron indicator.
 */
@Composable
fun UniqueSettingsItemRow(
    item: UniqueSettingsItemData,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.972f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pressSpringScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = item.enabled && item.onClick != null,
                onClick = { item.onClick?.invoke() }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge
            if (item.customIconBadge != null) {
                item.customIconBadge.invoke()
                Spacer(modifier = Modifier.width(16.dp))
            } else if (item.icon != null) {
                LiquidGradientIconBadge(
                    gradientColors = item.gradientColors,
                    icon = item.icon,
                    showBadge = item.showBadge,
                    badgeColor = item.badgeColor
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            // Title & Description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (!item.enabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    item.title()
                }

                item.description?.let { desc ->
                    Spacer(modifier = Modifier.height(2.dp))
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyMedium.copy(
                            color = if (!item.enabled)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    ) {
                        desc()
                    }
                }
            }

            // Trailing Content or Chevron
            if (item.trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                item.trailingContent.invoke()
            } else if (item.showChevron && item.onClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.shiny.music.R.drawable.navigate_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Data structure holding properties for a unique settings row item.
 */
data class UniqueSettingsItemData(
    val icon: Painter? = null,
    val customIconBadge: (@Composable () -> Unit)? = null,
    val gradientColors: List<Color> = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showChevron: Boolean = true,
    val showBadge: Boolean = false,
    val badgeColor: Color = Color(0xFFEF4444),
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null
)
