package com.shiny.music.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The iOS switch: a 51x31 capsule that turns green, a white knob that springs across and
 * stretches while it is held. Signature-compatible with Material's `Switch`, so screens
 * switch over with an import alias; `thumbContent` and `colors` are accepted and ignored
 * because the iOS control carries neither.
 */
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val c = Liquid.colors
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "switchPosition",
    )
    val stretch by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 900f),
        label = "switchStretch",
    )
    val track by animateColorAsState(
        // Shiny's accent, not the platform green: a switch that is on should say Shiny.
        targetValue = if (checked) c.accent else c.fill,
        animationSpec = spring(stiffness = 500f),
        label = "switchTrack",
    )
    Canvas(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = source,
                        indication = null,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        val h = size.height
        drawRoundRect(color = track, size = size, cornerRadius = CornerRadius(h / 2f))
        val inset = 2.dp.toPx()
        val knob = h - inset * 2
        val knobWidth = knob + 7.dp.toPx() * stretch
        val travel = size.width - inset * 2 - knobWidth
        val x = inset + travel * position
        // A soft shadow under the knob, then the knob.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.12f),
            topLeft = Offset(x, inset + 1.5.dp.toPx()),
            size = Size(knobWidth, knob),
            cornerRadius = CornerRadius(knob / 2f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(x, inset),
            size = Size(knobWidth, knob),
            cornerRadius = CornerRadius(knob / 2f),
        )
    }
}
