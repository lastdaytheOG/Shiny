package com.shiny.music.ui.liquid

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shiny.music.ui.component.backdrop.Backdrop
import com.shiny.music.ui.component.backdrop.backdrops.LayerBackdrop
import com.shiny.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.shiny.music.ui.component.backdrop.drawBackdrop
import com.shiny.music.ui.component.backdrop.effects.blur
import com.shiny.music.ui.component.backdrop.effects.colorControls
import com.shiny.music.ui.component.backdrop.effects.lens
import com.shiny.music.ui.component.backdrop.highlight.Highlight
import com.shiny.music.ui.component.backdrop.highlight.HighlightStyle
import com.shiny.music.ui.component.backdrop.shadow.Shadow
import com.shiny.music.ui.liquid.appearance.GlassFinish
import com.shiny.music.ui.liquid.appearance.LocalShinyAppearance
import kotlinx.coroutines.launch

/**
 * The content that Liquid Glass surfaces refract.
 *
 * Provided at the app root with the NavHost's recorded layer, and re-provided by any
 * screen or panel that floats glass over its own content (the player, a detail page's
 * top bar). A glass element must never sample a backdrop that contains itself, which is
 * why in-screen glass uses a screen-local backdrop rather than the app one.
 */
val LocalLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** The three materials Liquid Glass comes in. */
enum class GlassKind {
    /** Floating controls and bars over ordinary content: tab bar, mini player, buttons. */
    Regular,

    /** Controls over media, where the image should stay legible through the glass. */
    Clear,

    /** Large panels that must stay readable over anything: sheets, menus, search fields. */
    Thick,
}

/** RenderEffect (blur) needs API 31; the lens shader needs RuntimeShader, API 33. */
val GlassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
private val LensSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private data class GlassRecipe(
    val saturation: Float,
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val dispersion: Boolean,
    /** Multiplies the surface tint's alpha. */
    val tint: Float = 1f,
)

// Adaptive — Shiny's recipe, chosen per surface: clear over media, regular over pages,
// thick for panels.
private val AdaptiveRegular = GlassRecipe(saturation = 1.7f, blur = 12.dp, lensHeight = 14.dp, lensAmount = 26.dp, dispersion = true)
private val AdaptiveClear = GlassRecipe(saturation = 1.35f, blur = 3.dp, lensHeight = 12.dp, lensAmount = 22.dp, dispersion = true)
private val AdaptiveThick = GlassRecipe(saturation = 1.5f, blur = 26.dp, lensHeight = 0.dp, lensAmount = 0.dp, dispersion = false)

// Clear — thinner everywhere, with a stronger lens so the rim still reads as glass.
// Panels keep most of their blur and tint: a menu has to stay legible over anything.
private val ClearRegular = GlassRecipe(saturation = 1.5f, blur = 5.dp, lensHeight = 16.dp, lensAmount = 30.dp, dispersion = true, tint = 0.65f)
private val ClearClear = GlassRecipe(saturation = 1.3f, blur = 1.dp, lensHeight = 13.dp, lensAmount = 24.dp, dispersion = true, tint = 0.8f)
private val ClearThick = GlassRecipe(saturation = 1.45f, blur = 20.dp, lensHeight = 0.dp, lensAmount = 0.dp, dispersion = false, tint = 0.9f)

// Frosted — heavier blur and a calmer lens without dispersion: a quiet, diffused material.
private val FrostedRegular = GlassRecipe(saturation = 1.45f, blur = 22.dp, lensHeight = 10.dp, lensAmount = 16.dp, dispersion = false, tint = 1.12f)
private val FrostedClear = GlassRecipe(saturation = 1.25f, blur = 9.dp, lensHeight = 10.dp, lensAmount = 16.dp, dispersion = false, tint = 1.35f)
private val FrostedThick = GlassRecipe(saturation = 1.4f, blur = 34.dp, lensHeight = 0.dp, lensAmount = 0.dp, dispersion = false, tint = 1.05f)

private fun recipe(kind: GlassKind, finish: GlassFinish) = when (finish) {
    GlassFinish.Adaptive -> when (kind) {
        GlassKind.Regular -> AdaptiveRegular
        GlassKind.Clear -> AdaptiveClear
        GlassKind.Thick -> AdaptiveThick
    }
    GlassFinish.Clear -> when (kind) {
        GlassKind.Regular -> ClearRegular
        GlassKind.Clear -> ClearClear
        GlassKind.Thick -> ClearThick
    }
    GlassFinish.Frosted -> when (kind) {
        GlassKind.Regular -> FrostedRegular
        GlassKind.Clear -> FrostedClear
        GlassKind.Thick -> FrostedThick
    }
}

/**
 * Draws this element as Liquid Glass: the backdrop beneath it saturated, blurred and
 * refracted through a lens at the rim, a specular highlight on the edge, a soft shadow,
 * and a thin tint so labels on it stay legible.
 *
 * Falls back to a solid translucent fill with a hairline rim wherever the RenderEffect
 * pipeline or a backdrop is unavailable, so a call site never has to branch.
 *
 * [pressProgress] (0..1) lets an interactive element brighten as it is pressed — the
 * "light gathering in the glass" response iOS gives a touched control.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    kind: GlassKind = GlassKind.Regular,
    tint: Color = Color.Unspecified,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
    shadow: Boolean = true,
    pressProgress: (() -> Float)? = null,
): Modifier {
    val colors = Liquid.colors
    // Dark panels take their colour from the palette's raised grounds (#1C1C1E / #2C2C2E
    // in Dark), so AMOLED's deeper steps reach the glass too.
    val baseTint = when {
        tint.isSpecified -> tint
        kind == GlassKind.Clear -> colors.clearGlassTint
        kind == GlassKind.Thick -> if (colors.isDark) colors.secondaryBackground.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.72f)
        else -> colors.glassTint
    }

    val glassOn = com.shiny.music.ui.component.LocalGlassEffectConfig.current.glassAvailable
    if (!GlassSupported || backdrop == null || !glassOn) {
        val fallback = when (kind) {
            GlassKind.Clear -> Color.White.copy(alpha = if (colors.isDark) 0.16f else 0.3f)
            else -> if (colors.isDark) colors.tertiaryBackground.copy(alpha = 0.94f) else Color(0xFFF9F9F9).copy(alpha = 0.95f)
        }
        return this
            .background(fallback, shape)
            .border(0.5.dp, colors.glassRim, shape)
    }

    val density = LocalDensity.current
    val r = recipe(kind, LocalShinyAppearance.current.glassFinish)
    val surfaceTint = if (r.tint == 1f) baseTint else baseTint.copy(alpha = (baseTint.alpha * r.tint).coerceIn(0f, 1f))
    // Blur hides upscaling, so heavier materials record their backdrop at lower resolution.
    val scale = when {
        r.blur >= 16.dp -> 0.33f
        r.blur >= 8.dp -> 0.5f
        else -> 1f
    }
    val blurPx = with(density) { r.blur.toPx() } * scale
    val lensHeightPx = with(density) { r.lensHeight.toPx() } * scale
    val lensAmountPx = with(density) { r.lensAmount.toPx() } * scale
    val highlight = remember(colors.isDark, kind) {
        Highlight(
            width = 0.75.dp,
            alpha = if (colors.isDark) 0.55f else 0.9f,
            style = HighlightStyle.Default(angle = 60f, falloff = 1.4f),
        )
    }
    val dropShadow = remember(colors.isDark) {
        Shadow(
            radius = 18.dp,
            color = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.10f),
        )
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(
                brightness = if (colors.isDark) -0.02f else 0.04f,
                saturation = r.saturation,
            )
            if (blurPx > 0f) blur(blurPx)
            if (LensSupported && lensHeightPx > 0f) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = r.dispersion,
                )
            }
        },
        highlight = if (kind == GlassKind.Thick) null else ({ highlight }),
        shadow = if (shadow) ({ dropShadow }) else null,
        onDrawSurface = {
            drawRect(surfaceTint)
            val p = pressProgress?.invoke() ?: 0f
            if (p > 0f) {
                drawRect(Color.White.copy(alpha = 0.14f * p), blendMode = BlendMode.Plus)
            }
        },
        backdropScale = scale,
        // Drawn into the parent with a native rounded clip, not an offscreen layer of its own:
        // the backdrop under it is opaque and nothing here masks, so the pixels are the same.
        isolated = false,
    )
}

/**
 * Tracks a press on [interactionSource] as a 0..1 spring, for glass that swells and
 * brightens under the finger. Read it in a draw or layer lambda, never in composition.
 */
@Stable
class GlassPress internal constructor(internal val animatable: Animatable<Float, *>) {
    val value: Float get() = animatable.value
}

@Composable
fun rememberGlassPress(interactionSource: InteractionSource): GlassPress {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            launch {
                when (interaction) {
                    is PressInteraction.Press -> animatable.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 900f))
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        animatable.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 380f))
                }
            }
        }
    }
    return remember(animatable) { GlassPress(animatable) }
}

/** Scales a glass element up slightly while pressed, like iOS 26 controls. */
fun Modifier.glassPressScale(press: GlassPress, amount: Float = 0.08f): Modifier =
    graphicsLayer {
        val s = 1f + amount * press.value
        scaleX = s
        scaleY = s
    }

/**
 * A backdrop that records whatever it is applied to over [background], for glass that
 * floats over a screen's own scrolling content.
 */
@Composable
fun rememberLiquidBackdrop(background: Color): LayerBackdrop =
    rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }

/**
 * iOS 26's "scroll edge effect": content passing under the status bar and floating
 * buttons is blurred and dimmed progressively, strongest at the very top, instead of
 * running into a hard-edged bar.
 *
 * [alpha] lets a page reveal the effect only once content has actually scrolled under it.
 */
@Composable
fun ScrollEdgeEffect(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    background: Color = Liquid.colors.background,
    alpha: () -> Float = { 1f },
) {
    val density = LocalDensity.current
    val blurPx = with(density) { 18.dp.toPx() } * 0.33f
    val sizeModifier = Modifier.then(modifier)
    if (!GlassSupported) {
        Box(
            sizeModifier
                .graphicsLayer { this.alpha = alpha() }
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.96f),
                        0.6f to background.copy(alpha = 0.7f),
                        1f to background.copy(alpha = 0f),
                    )
                )
        )
        return
    }
    Box(
        sizeModifier
            .graphicsLayer { this.alpha = alpha() }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    colorControls(saturation = 1.3f)
                    blur(blurPx)
                },
                highlight = null,
                shadow = null,
                backdropScale = 0.33f,
                onDrawSurface = {
                    drawRect(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0.86f),
                            0.55f to background.copy(alpha = 0.5f),
                            1f to background.copy(alpha = 0.0f),
                        )
                    )
                },
                onDrawFront = {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            0.55f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            )
    )
}
