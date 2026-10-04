package com.shiny.music.ui.liquid.together

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.shiny.music.together.TogetherMember
import com.shiny.music.together.TogetherReaction
import com.shiny.music.together.TogetherReactions
import com.shiny.music.together.TogetherSession
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.LiquidTypography
import kotlin.math.absoluteValue
import kotlin.math.sin
import kotlin.random.Random

/** The app's one Listen Together session, provided by MainActivity. */
val LocalTogether = staticCompositionLocalOf<TogetherSession?> { null }

/** Contact colours, picked by name so a person keeps theirs across screens and phones. */
private val PersonColors = listOf(
    Color(0xFFFF375F), Color(0xFFFF9F0A), Color(0xFFFFCC00), Color(0xFF30D158), Color(0xFF64D2FF),
    Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFBF5AF2), Color(0xFFFF6482), Color(0xFF66D4CF),
)

fun personColor(name: String): Color = PersonColors[name.hashCode().absoluteValue % PersonColors.size]

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/** A person as a coloured disc with their initials; dimmed while they're away. */
@Composable
fun Monogram(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    away: Boolean = false,
    ring: Color? = null,
) {
    val color = personColor(name)
    Box(
        modifier
            .size(size)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .padding(if (ring != null) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(color, androidx.compose.ui.graphics.lerp(color, Color.Black, 0.28f))
                )
            )
            .graphicsLayer { alpha = if (away) 0.45f else 1f },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            color = Color.White,
            style = LiquidTypography.headline.copy(
                fontSize = (size.value * 0.38f).sp,
                lineHeight = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
    }
}

/** Up to [max] overlapping monograms, each ringed in [ring] so the overlap reads. */
@Composable
fun AvatarStack(
    members: List<TogetherMember>,
    ring: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    max: Int = 5,
) {
    val shown = members.take(max)
    val overlap = size * 0.34f
    Box(modifier.width(size + (size - overlap) * (shown.size - 1).coerceAtLeast(0)).height(size)) {
        shown.forEachIndexed { index, member ->
            Monogram(
                name = member.name,
                size = size,
                away = !member.connected,
                ring = ring,
                modifier = Modifier.offset(x = (size - overlap) * index),
            )
        }
    }
}

/**
 * The eight reactions as a row of round keys. A press pops the key and sends; the reaction
 * then rises through [TogetherReactionsLayer] on every phone in the session, this one too.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReactionBar(
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyColor: Color = Liquid.colors.quaternaryFill,
    keySize: Dp = 42.dp,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TogetherReactions.forEach { emoji ->
            val scale = remember { Animatable(1f) }
            var pops by remember { mutableStateOf(0) }
            LaunchedEffect(pops) {
                if (pops == 0) return@LaunchedEffect
                scale.snapTo(1.28f)
                scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
            }
            Box(
                Modifier
                    .size(keySize)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .clip(CircleShape)
                    .background(keyColor)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pops++
                            onReact(emoji)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = (keySize.value * 0.5f).sp, textAlign = TextAlign.Center)
            }
        }
    }
}

private class Floater(val reaction: TogetherReaction, val startX: Float, val sway: Float, val phase: Float)

/**
 * Reactions rising like bubbles: each drifts up from the bottom of this layer with a small
 * sway, grows in, and fades near the top, the sender's name under it. The layer only
 * animates while a reaction is in flight — an empty layer draws nothing and runs nothing.
 */
@Composable
fun TogetherReactionsLayer(
    session: TogetherSession,
    modifier: Modifier = Modifier,
    showNames: Boolean = true,
    nameColor: Color = Color.White.copy(alpha = 0.85f),
) {
    val floaters = remember { mutableStateListOf<Floater>() }
    LaunchedEffect(session) {
        session.reactions.collect { reaction ->
            if (floaters.size > 24) floaters.removeAt(0)
            floaters += Floater(
                reaction = reaction,
                startX = Random.nextFloat() * 0.6f + 0.2f,
                sway = Random.nextFloat() * 0.06f + 0.03f,
                phase = Random.nextFloat() * 6.28f,
            )
        }
    }
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        floaters.toList().forEach { floater ->
            key(floater.reaction.key) {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    progress.animateTo(1f, tween(durationMillis = 2600, easing = LinearEasing))
                    floaters.remove(floater)
                }
                val grow = remember { Animatable(0.4f) }
                LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f)) }
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        val p = progress.value
                        val x = widthPx * (floater.startX + floater.sway * sin(floater.phase + p * 7f))
                        translationX = x - 28.dp.toPx()
                        translationY = heightPx * (1f - FastOutSlowInEasing.transform(p) * 0.92f) - 70.dp.toPx()
                        val s = grow.value * (1f + 0.15f * p)
                        scaleX = s
                        scaleY = s
                        alpha = when {
                            p < 0.08f -> p / 0.08f
                            p > 0.7f -> (1f - p) / 0.3f
                            else -> 1f
                        }
                    }.width(56.dp),
                ) {
                    Text(floater.reaction.emoji, fontSize = 34.sp)
                    if (showNames && floater.reaction.from.isNotBlank()) {
                        Text(
                            text = if (floater.reaction.mine) "You" else floater.reaction.from,
                            style = LiquidTypography.caption2,
                            color = nameColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The invite as a QR code, drawn module by module with softened corners so it sits in
 * the design rather than looking pasted in. Any phone's camera opens the link.
 */
@Composable
fun InviteQrCode(
    text: String,
    modifier: Modifier = Modifier,
    dark: Color = Color.Black,
    light: Color = Color.White,
) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        }.getOrNull()
    }
    Canvas(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(light)
            .padding(14.dp)
    ) {
        val m = matrix ?: return@Canvas
        val cells = m.width
        val cell = size.minDimension / cells
        val radius = CornerRadius(cell * 0.32f, cell * 0.32f)
        for (y in 0 until cells) {
            for (x in 0 until cells) {
                if (m.get(x, y)) {
                    drawRoundRect(
                        color = dark,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell * 1.02f, cell * 1.02f),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/**
 * Six cells for a session code. The field underneath takes any typing or pasting, keeps
 * only letters and digits, and upper-cases them; the cells show where you are.
 */
@Composable
fun CodeCells(
    code: String,
    onCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: Boolean = false,
    length: Int = 6,
) {
    val colors = Liquid.colors
    BasicTextField(
        value = code,
        onValueChange = { raw ->
            val clean = (TogetherSession.normalizeCode(raw) ?: raw.filter { it.isLetterOrDigit() }.uppercase()).take(length)
            onCode(clean)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
        ),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                // The real field stays in the tree (for focus and IME) but out of sight.
                Box(Modifier.graphicsLayer { alpha = 0f }) { inner() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(length) { index ->
                        val char = code.getOrNull(index)
                        val active = index == code.length.coerceAtMost(length - 1) && enabled
                        Box(
                            Modifier
                                .weight(1f)
                                .height(58.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.tertiaryFill)
                                .border(
                                    width = if (active || error) 2.dp else 0.dp,
                                    color = when {
                                        error -> colors.destructive
                                        active -> colors.accent
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = char?.toString().orEmpty(),
                                style = LiquidTypography.title1.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                                color = colors.label,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** "ABC 234": a code set in two halves so it can be read out loud. */
fun spacedCode(code: String): String = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code
