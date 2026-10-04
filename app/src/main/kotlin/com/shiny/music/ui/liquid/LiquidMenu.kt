package com.shiny.music.ui.liquid

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** One line of a [LiquidPullDownMenu]. */
sealed interface MenuEntry

/** A choice. [checked] puts the iOS checkmark in front of it; [icon] sits at the trailing edge. */
class MenuAction(
    val title: String,
    val icon: ImageVector? = null,
    val checked: Boolean = false,
    val destructive: Boolean = false,
    val subtitle: String? = null,
    val onClick: () -> Unit,
) : MenuEntry

/** The thick band iOS draws between groups of a menu. */
data object MenuDivider : MenuEntry

/** A small grey caption at the head of a group. */
class MenuHeader(val title: String) : MenuEntry

/**
 * iOS's pull-down menu: a rounded, frosted panel that springs out of the button that
 * opened it and dismisses on any tap outside. Place it next to its anchor — inside the
 * same `Box` as the button — and it positions itself below (or above, when there is no
 * room) aligned to the anchor's leading or trailing edge.
 *
 * It lives in its own window, where it cannot sample the page behind it, so the material
 * is the thick near-opaque one iOS uses for menus rather than refracting glass.
 */
@Composable
fun LiquidPullDownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    entries: List<MenuEntry>,
    alignEnd: Boolean = true,
    width: androidx.compose.ui.unit.Dp = 250.dp,
) {
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = expanded
    if (!visible.currentState && !visible.targetState) return

    val density = LocalDensity.current
    val provider = remember(alignEnd, density) {
        PullDownPositionProvider(
            alignEnd = alignEnd,
            gapPx = with(density) { 8.dp.roundToPx() },
            marginPx = with(density) { 12.dp.roundToPx() },
        )
    }
    val colors = Liquid.colors
    val surface = if (colors.isDark) Color(0xFF252527) else Color(0xFFF7F7F8)
    val band = if (colors.isDark) Color.Black.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.07f)

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val transition = rememberTransition(visible, label = "pullDown")
        val progress by transition.animateFloat(
            transitionSpec = { if (targetState) spring(dampingRatio = 0.74f, stiffness = 480f) else tween(130) },
            label = "pullDownProgress",
        ) { if (it) 1f else 0f }
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .width(width)
                .heightIn(max = 460.dp)
                .graphicsLayer {
                    val p = progress
                    val scale = 0.35f + 0.65f * p
                    scaleX = scale
                    scaleY = scale
                    alpha = p.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (provider.openedFromRight) 1f else 0f,
                        pivotFractionY = if (provider.opensUp) 1f else 0f,
                    )
                    this.shape = shape
                    clip = true
                    shadowElevation = 22.dp.toPx() * p.coerceIn(0f, 1f)
                }
                .background(surface)
                .verticalScroll(rememberScrollState()),
        ) {
            var previousWasItem = false
            entries.forEach { entry ->
                when (entry) {
                    is MenuHeader -> {
                        Text(
                            entry.title,
                            style = LiquidTypography.footnote,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                        )
                        previousWasItem = false
                    }
                    MenuDivider -> {
                        Box(Modifier.fillMaxWidth().height(8.dp).background(band))
                        previousWasItem = false
                    }
                    is MenuAction -> {
                        if (previousWasItem) Hairline()
                        MenuRow(entry, onDismiss)
                        previousWasItem = true
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(action: MenuAction, onDismiss: () -> Unit) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val tint = if (action.destructive) Color(0xFFFF3B30) else colors.label
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable(interactionSource = interaction, indication = rememberRowHighlight()) {
                onDismiss()
                action.onClick()
            }
            .padding(start = 12.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.CenterStart) {
            if (action.checked) Icon(Icons.Rounded.Check, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(action.title, style = LiquidTypography.body, color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!action.subtitle.isNullOrBlank()) {
                Text(action.subtitle, style = LiquidTypography.footnote, color = colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (action.icon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(action.icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Below the anchor when it fits, above it otherwise; edge-aligned to the anchor and kept
 * inside the window's margins. It records which way it went so the panel can grow out
 * of the right corner.
 */
private class PullDownPositionProvider(
    private val alignEnd: Boolean,
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {
    var opensUp = false
        private set
    var openedFromRight = alignEnd
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val fromRight = alignEnd != (layoutDirection == LayoutDirection.Rtl)
        openedFromRight = fromRight
        val rawX = if (fromRight) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = rawX.coerceIn(marginPx, maxX)
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - gapPx - popupContentSize.height
        opensUp = below + popupContentSize.height > windowSize.height - marginPx && above >= marginPx
        val y = if (opensUp) above else below.coerceAtMost((windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx))
        return IntOffset(x, y)
    }
}

/**
 * An iOS alert: a centred, rounded panel with a bold title, a short message and two
 * capsule buttons — Cancel on the left, the action on the right (red when it destroys
 * something). It pops in slightly large and settles, as iOS alerts do.
 */
@Composable
fun LiquidAlert(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissLabel: String = androidx.compose.ui.res.stringResource(android.R.string.cancel),
    destructive: Boolean = false,
) {
    val colors = Liquid.colors
    val surface = if (colors.isDark) Color(0xFF2C2C2E) else Color(0xFFF9F9F9)
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f))
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .width(300.dp)
                .graphicsLayer {
                    val p = appear.value
                    val s = 1.12f - 0.12f * p
                    scaleX = s
                    scaleY = s
                    alpha = p.coerceIn(0f, 1f)
                    shape = RoundedCornerShape(30.dp)
                    clip = true
                }
                .background(surface)
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = LiquidTypography.headline,
                color = colors.label,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = LiquidTypography.subheadline,
                    color = colors.secondaryLabel,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                AlertButton(dismissLabel, colors.fill, colors.label, Modifier.weight(1f), onDismiss)
                AlertButton(
                    confirmLabel,
                    if (destructive) Color(0xFFFF3B30) else colors.accent,
                    Color.White,
                    Modifier.weight(1f),
                ) {
                    onDismiss()
                    onConfirm()
                }
            }
        }
    }
}

@Composable
private fun AlertButton(label: String, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(48.dp)
            .graphicsLayer {
                shape = androidx.compose.foundation.shape.CircleShape
                clip = true
            }
            .background(container)
            .clickable(interactionSource = interaction, indication = rememberRowHighlight(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = LiquidTypography.headline, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
