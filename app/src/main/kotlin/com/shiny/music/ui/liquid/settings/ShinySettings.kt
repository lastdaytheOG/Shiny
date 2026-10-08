package com.shiny.music.ui.liquid.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.shiny.music.ui.liquid.Hairline
import com.shiny.music.ui.liquid.LargeTitlePage
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidBackButton
import com.shiny.music.ui.liquid.LiquidSwitch
import com.shiny.music.ui.liquid.LiquidTypography
import com.shiny.music.ui.liquid.PageMargin
import com.shiny.music.ui.liquid.rememberRowHighlight
import kotlin.math.roundToInt

/**
 * Shiny's settings language.
 *
 * Deliberately *not* the platform settings idiom: no inset cards floating on a grey
 * ground, no coloured glyph plate beside every row. A settings screen here is a quiet
 * page of type — a small tracked section label, rows of plain text on the page's own
 * ground, hairlines between them that start where the text starts, and the accent spent
 * only on the things you can change (a switch, a tick, a slider's fill).
 *
 * The hierarchy carries the design, so the page reads the same in light, dark and AMOLED,
 * and an added row never needs a colour chosen for it.
 *
 * The index (`SettingsHomeScreen`) is the one exception: a list of places to go rather than
 * of things to change, it is set in panels with a plate behind each glyph.
 */

/** Left/right page gutter for every settings row. */
val SettingsGutter = PageMargin

/** Where hairlines begin: flush with the text column, never the screen edge. */
private val SeparatorIndent = PageMargin

private val RowMinHeight = 52.dp

/** A settings destination: large title, back button, page ground. */
@Composable
fun SettingsPage(
    title: String,
    navController: NavController,
    state: LazyListState = rememberLazyListState(),
    background: Color = Liquid.colors.background,
    content: LazyListScope.() -> Unit,
) {
    LargeTitlePage(
        title = title,
        state = state,
        background = background,
        navigationButton = { LiquidBackButton(onClick = { navController.navigateUp() }) },
        content = content,
    )
}

/**
 * A named block of rows.
 *
 * The label is the only decoration: 12sp, tracked, secondary. Groups are separated by
 * space rather than by boxes, which is what keeps a long settings page calm.
 */
@Composable
fun SettingsSection(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            SettingsSectionLabel(
                title = title,
                modifier = Modifier.padding(start = SettingsGutter, end = SettingsGutter, top = 30.dp, bottom = 9.dp),
            )
        } else {
            Spacer(Modifier.height(24.dp))
        }
        content()
        if (footer != null) {
            Text(
                text = footer,
                style = LiquidTypography.footnote,
                color = Liquid.colors.secondaryLabel,
                modifier = Modifier.padding(start = SettingsGutter, end = SettingsGutter, top = 9.dp),
            )
        }
    }
}

/** The label over a block of rows: small, tracked, secondary. */
@Composable
fun SettingsSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = LiquidTypography.caption1.copy(fontWeight = FontWeight.SemiBold),
        letterSpacing = 0.9.sp,
        color = Liquid.colors.secondaryLabel,
        modifier = modifier,
    )
}

/**
 * The one row shape every setting is built from: title, optional supporting line, an
 * optional trailing control, and a hairline under it.
 *
 * [glyph] is optional on purpose — only the index page uses one, so that scanning ten
 * destinations is fast, while a page of switches stays unornamented.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    glyph: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    highlighted: Boolean = false,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    val interaction = remember { MutableInteractionSource() }
    val clickable = enabled && onClick != null
    val titleColor = when {
        !enabled -> colors.tertiaryLabel
        destructive -> colors.destructive
        else -> colors.label
    }

    Box(
        modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(colors.accent.copy(alpha = 0.10f)) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = rememberRowHighlight(),
                enabled = clickable,
                onClick = { onClick?.invoke() },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .padding(horizontal = SettingsGutter, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) { glyph() }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LiquidTypography.body,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = LiquidTypography.footnote,
                        color = if (enabled) colors.secondaryLabel else colors.tertiaryLabel,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = value,
                    style = LiquidTypography.body,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Hugs the trailing edge. A weight here split the row in half, which
                    // parked short values mid-row and wrapped subtitles early.
                    modifier = Modifier.widthIn(max = 170.dp),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (divider) {
            Hairline(Modifier.align(Alignment.BottomStart), startIndent = SeparatorIndent)
        }
    }
}

/** A row that opens another page. */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    glyph: (@Composable () -> Unit)? = null,
    badge: Boolean = false,
    divider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        value = value,
        glyph = glyph,
        modifier = modifier,
        chevron = true,
        divider = divider,
        trailing = if (badge) {
            {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Liquid.colors.destructive)
                )
            }
        } else null,
        onClick = onClick,
    )
}

/** A row whose whole surface toggles a switch. */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        enabled = enabled,
        divider = divider,
        trailing = {
            LiquidSwitch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}

/**
 * A choice that opens in place.
 *
 * Tapping the row unfolds its options underneath it with a tick against the current one —
 * no dialog, no second surface, and the page never loses its position.
 */
@Composable
fun <T> SettingsChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    optionSubtitle: ((T) -> String?)? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = Liquid.colors
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "choiceChevron",
    )

    Column(modifier.fillMaxWidth()) {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            value = label(selected),
            enabled = enabled,
            divider = divider && !expanded,
            trailing = {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            },
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(),
        ) {
            Column(Modifier.background(colors.quaternaryFill)) {
                options.forEachIndexed { index, option ->
                    val isSelected = option == selected
                    SettingsRow(
                        title = label(option),
                        subtitle = optionSubtitle?.invoke(option),
                        divider = divider || index != options.lastIndex,
                        trailing = {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (isSelected) colors.accent else Color.Transparent,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** A labelled value with a slider under it. */
@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueLabel: String? = null,
    steps: Int = 0,
    subtitle: String? = null,
    divider: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsGutter)
                .padding(top = 12.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = LiquidTypography.body,
                    color = colors.label,
                    modifier = Modifier.weight(1f),
                )
                if (valueLabel != null) {
                    Text(
                        text = valueLabel,
                        style = LiquidTypography.body,
                        color = colors.secondaryLabel,
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = LiquidTypography.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            SettingsSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
            )
        }
        if (divider) {
            Hairline(Modifier.align(Alignment.BottomStart), startIndent = SeparatorIndent)
        }
    }
}

/**
 * Shiny's slider: a hairline-thin track that fills with the accent, and a small knob that
 * grows while it is held. Drawn rather than composed, so dragging it costs no recomposition.
 */
@Composable
fun SettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    var dragging by remember { mutableStateOf(false) }
    val knobSize by animateDpAsState(
        targetValue = if (dragging) 22.dp else 17.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "sliderKnob",
    )
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun emit(xFraction: Float) {
        val raw = valueRange.start + xFraction.coerceIn(0f, 1f) * span
        val snapped = if (steps > 0) {
            val stepSize = span / (steps + 1)
            valueRange.start + (((raw - valueRange.start) / stepSize).roundToInt() * stepSize)
        } else {
            raw
        }
        onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(valueRange, steps) {
                detectTapGestures(
                    onPress = { offset ->
                        dragging = true
                        emit(offset.x / size.width)
                        tryAwaitRelease()
                        dragging = false
                        onValueChangeFinished?.invoke()
                    },
                )
            }
            .pointerInput(valueRange, steps) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        emit(offset.x / size.width)
                    },
                    onDragEnd = {
                        dragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    emit(change.position.x / size.width)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val trackHeight = 4.dp.toPx()
            val knobPx = knobSize.toPx()
            val usable = size.width - knobPx
            val centerY = size.height / 2f
            val knobX = knobPx / 2f + usable * fraction

            drawRoundRect(
                color = colors.fill,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawRoundRect(
                color = colors.accent,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(knobX, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawCircle(
                color = colors.accent,
                radius = knobPx / 2f,
                center = Offset(knobX, centerY),
            )
        }
    }
}

/** A read-only fact: a label on the left, its value on the right. */
@Composable
fun SettingsValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        value = value,
        modifier = modifier,
        divider = divider,
    )
}

/** A row that performs an action; [destructive] paints it in the warning colour. */
@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        destructive = destructive,
        enabled = enabled,
        divider = divider,
        onClick = onClick,
    )
}

/** The quiet monochrome glyph used on index rows. */
@Composable
fun settingsGlyph(icon: ImageVector): @Composable () -> Unit = {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Liquid.colors.secondaryLabel,
        modifier = Modifier.size(21.dp),
    )
}

/** The same, for the drawable-backed glyphs the project already ships. */
@Composable
fun settingsGlyph(painter: Painter): @Composable () -> Unit = {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = Liquid.colors.secondaryLabel,
        modifier = Modifier.size(21.dp),
    )
}

/** A short paragraph of explanation, used under a section that needs one. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LiquidTypography.footnote,
        color = Liquid.colors.secondaryLabel,
        modifier = modifier.padding(horizontal = SettingsGutter, vertical = 8.dp),
    )
}

/**
 * The header a settings page can carry above its first section: a line of type that says
 * what the page is for, in place of an illustration.
 */
@Composable
fun SettingsIntro(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LiquidTypography.subheadline,
        color = Liquid.colors.secondaryLabel,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SettingsGutter, end = SettingsGutter, top = 2.dp, bottom = 4.dp),
    )
}

/** A segmented row of choices, for a setting with two or three equal options. */
@Composable
fun <T> SettingsSegmentedRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = true,
) {
    val colors = Liquid.colors
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsGutter)
                .padding(top = 12.dp, bottom = 14.dp),
        ) {
            Text(text = title, style = LiquidTypography.body, color = colors.label)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = LiquidTypography.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.quaternaryFill)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) colors.accent else Color.Transparent)
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onSelect(option) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label(option),
                            style = LiquidTypography.subheadline.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = if (isSelected) colors.onAccent else colors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (divider) {
            Hairline(Modifier.align(Alignment.BottomStart), startIndent = SeparatorIndent)
        }
    }
}

/** Space between the last section and the bottom of a page. */
@Composable
fun SettingsTailSpace(height: Dp = 28.dp) = Spacer(Modifier.height(height))
