@file:OptIn(ExperimentalTextApi::class)

package com.shiny.music.ui.liquid

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.shiny.music.R

/**
 * Shiny's Liquid design system.
 *
 * A ground-up replacement for the Material-derived look: iOS-style neutral grounds that
 * never move with the artwork, one tint colour that always means "tappable", an SF-like
 * type ramp set in Inter, and Liquid Glass for every piece of floating chrome.
 *
 * The neutral values follow the iOS system palette (label / secondaryLabel / separator /
 * fill tiers, grouped backgrounds), because that palette is what makes an Apple screen
 * read as calm: every grey on the screen is one of about eight, and they stack by alpha.
 */
@Immutable
data class LiquidColors(
    val isDark: Boolean,
    val background: Color,
    val secondaryBackground: Color,
    val tertiaryBackground: Color,
    val groupedBackground: Color,
    val secondaryGroupedBackground: Color,
    val tertiaryGroupedBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
    val separator: Color,
    val opaqueSeparator: Color,
    val fill: Color,
    val secondaryFill: Color,
    val tertiaryFill: Color,
    val quaternaryFill: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val green: Color,
    val gray: Color,
    val gray2: Color,
    val gray3: Color,
    val gray4: Color,
    val gray5: Color,
    val gray6: Color,
    /** Surface tint laid over refracted content on glass chrome. */
    val glassTint: Color,
    /** Tint used by glass that sits directly over media (artwork, video). */
    val clearGlassTint: Color,
    /** Hairline drawn on glass when the RenderEffect pipeline is unavailable. */
    val glassRim: Color,
    /** Ground of a modal sheet: deeper than a grouped cell, so panels can be raised off it. */
    val sheetBackground: Color,
    /**
     * A grouped panel (menu sections, Settings groups). Translucent in the dark themes, so
     * whatever colour the ground carries shows through it; opaque white in Light.
     */
    val panel: Color,
    /** The light a panel's upper edge catches. Fades out down the panel's sides. */
    val panelRim: Color,
    /** Hairline between rows inside a panel: quieter than [separator], which sits on a ground. */
    val panelSeparator: Color,
)

/** Shiny Rose — the single interactive tint. iOS systemPink family, not Apple Music red. */
val ShinyRoseLight = Color(0xFFFF2D55)
val ShinyRoseDark = Color(0xFFFF375F)

fun liquidLightColors(accent: Color = ShinyRoseLight) = LiquidColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    secondaryBackground = Color(0xFFF2F2F7),
    tertiaryBackground = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    secondaryGroupedBackground = Color(0xFFFFFFFF),
    tertiaryGroupedBackground = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    quaternaryLabel = Color(0x2E3C3C43),
    separator = Color(0x4A3C3C43),
    opaqueSeparator = Color(0xFFC6C6C8),
    fill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    tertiaryFill = Color(0x1F767680),
    quaternaryFill = Color(0x14747480),
    accent = accent,
    onAccent = Color.White,
    destructive = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    gray = Color(0xFF8E8E93),
    gray2 = Color(0xFFAEAEB2),
    gray3 = Color(0xFFC7C7CC),
    gray4 = Color(0xFFD1D1D6),
    gray5 = Color(0xFFE5E5EA),
    gray6 = Color(0xFFF2F2F7),
    glassTint = Color(0xFFFFFFFF).copy(alpha = 0.7f),
    clearGlassTint = Color(0xFFFFFFFF).copy(alpha = 0.16f),
    glassRim = Color(0x33000000),
    sheetBackground = Color(0xFFF2F2F7),
    panel = Color(0xFFFFFFFF),
    panelRim = Color(0x00000000),
    panelSeparator = Color(0x333C3C43),
)

fun liquidDarkColors(accent: Color = ShinyRoseDark) = LiquidColors(
    isDark = true,
    background = Color(0xFF000000),
    secondaryBackground = Color(0xFF1C1C1E),
    tertiaryBackground = Color(0xFF2C2C2E),
    groupedBackground = Color(0xFF000000),
    secondaryGroupedBackground = Color(0xFF1C1C1E),
    tertiaryGroupedBackground = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    quaternaryLabel = Color(0x2EEBEBF5),
    separator = Color(0x99545458),
    opaqueSeparator = Color(0xFF38383A),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    tertiaryFill = Color(0x3D767680),
    quaternaryFill = Color(0x2E767680),
    accent = accent,
    onAccent = Color.White,
    destructive = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    gray = Color(0xFF8E8E93),
    gray2 = Color(0xFF636366),
    gray3 = Color(0xFF48484A),
    gray4 = Color(0xFF3A3A3C),
    gray5 = Color(0xFF2C2C2E),
    gray6 = Color(0xFF1C1C1E),
    glassTint = Color(0xFF1C1C1E).copy(alpha = 0.6f),
    clearGlassTint = Color(0xFFFFFFFF).copy(alpha = 0.10f),
    glassRim = Color(0x33FFFFFF),
    sheetBackground = Color(0xFF131315),
    panel = Color(0xFFFFFFFF).copy(alpha = 0.075f),
    panelRim = Color(0xFFFFFFFF).copy(alpha = 0.13f),
    panelSeparator = Color(0xFFFFFFFF).copy(alpha = 0.09f),
)

/**
 * Dark, for OLED panels. The dark palette's grounds are already true black, so what AMOLED
 * changes is everything raised off them: the secondary and tertiary grounds that sheets,
 * menus and grouped cells sit on step down to near-black, and glass is tinted from the
 * same darker step. The hierarchy survives — each tier is still a visible step above the
 * last — but a full-screen menu no longer lights the panel grey. Fills, labels and
 * separators are untouched, so controls read exactly as they do in Dark.
 */
fun liquidAmoledColors(accent: Color = ShinyRoseDark) = liquidDarkColors(accent).copy(
    secondaryBackground = Color(0xFF111113),
    tertiaryBackground = Color(0xFF1C1C1E),
    secondaryGroupedBackground = Color(0xFF111113),
    tertiaryGroupedBackground = Color(0xFF1C1C1E),
    glassTint = Color(0xFF0E0E10).copy(alpha = 0.62f),
    sheetBackground = Color(0xFF0A0A0B),
)

val LocalLiquidColors = staticCompositionLocalOf { liquidDarkColors() }

/** Accessor in the style of `MaterialTheme`, so call sites read `Liquid.colors.label`. */
object Liquid {
    val colors: LiquidColors
        @Composable @ReadOnlyComposable get() = LocalLiquidColors.current

    val type: LiquidTypography get() = LiquidTypography
}

private val InterWeights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black,
)

/**
 * Inter pinned to one point on its optical-size axis (14..32).
 *
 * Inter's `opsz` axis is the same Text/Display split SF Pro ships as two families: the
 * display cut tightens spacing and sharpens the forms for large titles, the text cut opens
 * them up so a 13sp footnote stays legible.
 */
private fun interFamily(opticalSize: TextUnit) = FontFamily(
    InterWeights.map { weight ->
        Font(
            resId = R.font.inter_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.opticalSizing(opticalSize),
            ),
        )
    }
)

val InterDisplay = interFamily(32.sp)
val InterTitle = interFamily(22.sp)
val InterText = interFamily(17.sp)
val InterCaption = interFamily(14.sp)

/**
 * The iOS Dynamic Type ramp at the default "Large" size, one sp per point.
 *
 * Tracking follows Inter's dynamic-metrics curve, slightly relaxed at the top because the
 * display optical size is already tighter than the formula assumes.
 */
object LiquidTypography {
    val largeTitle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.6).sp)
    val title1 = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp)
    val title2 = TextStyle(fontFamily = InterTitle, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp)
    val title3 = TextStyle(fontFamily = InterTitle, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.35).sp)
    val headline = TextStyle(fontFamily = InterText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.25).sp)
    val body = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.25).sp)
    val callout = TextStyle(fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp)
    val subheadline = TextStyle(fontFamily = InterCaption, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.15).sp)
    val footnote = TextStyle(fontFamily = InterCaption, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.05).sp)
    val caption1 = TextStyle(fontFamily = InterCaption, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
    val caption2 = TextStyle(fontFamily = InterCaption, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.05.sp)

    /** The tab bar's 10pt label. */
    val tabLabel = TextStyle(fontFamily = InterCaption, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.1.sp)

    /** Synced lyrics: large, heavy, set tight, like the Music app's karaoke view. */
    val lyrics = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp)
}

/**
 * The same ramp expressed as a Material [Typography], so every screen still drawn with
 * `MaterialTheme.typography` picks up Inter and iOS proportions instead of the old ramp.
 */
val LiquidMaterialTypography = Typography(
    displayLarge = LiquidTypography.largeTitle.copy(fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-1.2).sp),
    displayMedium = LiquidTypography.largeTitle.copy(fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-0.9).sp),
    displaySmall = LiquidTypography.largeTitle.copy(fontSize = 36.sp, lineHeight = 43.sp, letterSpacing = (-0.7).sp),
    headlineLarge = LiquidTypography.largeTitle,
    headlineMedium = LiquidTypography.title1,
    headlineSmall = LiquidTypography.title2.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = LiquidTypography.title2,
    titleMedium = LiquidTypography.headline,
    titleSmall = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = LiquidTypography.body,
    bodyMedium = LiquidTypography.subheadline,
    bodySmall = LiquidTypography.footnote,
    labelLarge = LiquidTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = LiquidTypography.caption1.copy(fontWeight = FontWeight.Medium),
    labelSmall = LiquidTypography.caption2,
)

/**
 * Projects the Liquid palette onto a Material [ColorScheme].
 *
 * Every screen and menu that has not been rebuilt yet still reads `MaterialTheme`, and
 * mapping it here is what stops those screens from carrying a second, artwork-seeded
 * palette next to the new ones: grounds become the iOS neutrals, containers become the
 * grouped greys, and `primary` is the one tint.
 */
fun liquidMaterialColorScheme(c: LiquidColors): ColorScheme {
    val secondaryLabelOpaque = c.secondaryLabel.compositeOver(c.background)
    val accentContainer = lerp(c.background, c.accent, if (c.isDark) 0.28f else 0.16f)
    return if (c.isDark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = c.label,
            inversePrimary = ShinyRoseLight,
            secondary = c.accent,
            onSecondary = c.onAccent,
            secondaryContainer = c.gray5,
            onSecondaryContainer = c.label,
            tertiary = c.accent,
            onTertiary = c.onAccent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = c.label,
            background = c.background,
            onBackground = c.label,
            surface = c.background,
            onSurface = c.label,
            surfaceVariant = c.gray5,
            onSurfaceVariant = secondaryLabelOpaque,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFFF2F2F7),
            inverseOnSurface = Color(0xFF000000),
            error = c.destructive,
            onError = Color.White,
            errorContainer = lerp(c.background, c.destructive, 0.28f),
            onErrorContainer = c.label,
            outline = c.gray3,
            outlineVariant = c.opaqueSeparator,
            scrim = Color.Black,
            surfaceBright = c.gray5,
            surfaceDim = c.background,
            surfaceContainerLowest = c.background,
            surfaceContainerLow = Color(0xFF111113),
            surfaceContainer = c.gray6,
            surfaceContainerHigh = Color(0xFF242426),
            surfaceContainerHighest = c.gray5,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = c.label,
            inversePrimary = ShinyRoseDark,
            secondary = c.accent,
            onSecondary = c.onAccent,
            secondaryContainer = c.gray5,
            onSecondaryContainer = c.label,
            tertiary = c.accent,
            onTertiary = c.onAccent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = c.label,
            background = c.background,
            onBackground = c.label,
            surface = c.background,
            onSurface = c.label,
            surfaceVariant = c.gray6,
            onSurfaceVariant = secondaryLabelOpaque,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFF1C1C1E),
            inverseOnSurface = Color.White,
            error = c.destructive,
            onError = Color.White,
            errorContainer = lerp(c.background, c.destructive, 0.14f),
            onErrorContainer = c.label,
            outline = c.gray3,
            outlineVariant = c.opaqueSeparator,
            scrim = Color.Black,
            surfaceBright = c.background,
            surfaceDim = c.gray5,
            surfaceContainerLowest = c.background,
            surfaceContainerLow = Color(0xFFF8F8FA),
            surfaceContainer = c.gray6,
            surfaceContainerHigh = Color(0xFFEBEBF0),
            surfaceContainerHighest = c.gray5,
        )
    }
}
