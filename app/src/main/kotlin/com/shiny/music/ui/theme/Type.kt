package com.shiny.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Shiny Music's type ramp.
 *
 * Two things separate this from the stock Material 3 scale it replaced, and both are
 * the difference between type that was *set* and type that was merely sized:
 *
 * 1. **Optical sizing.** Each tier draws from a family pinned to its own point on the
 *    variable font's `opsz` axis ([DisplayFontFamily] / [TitleFontFamily] /
 *    [TextFontFamily]), so a 57sp title and a 12sp caption are genuinely different
 *    drawings of the letterform rather than one drawing at two scales.
 *
 * 2. **Optical tracking.** Letter-spacing tightens as size grows — roughly -0.026em at
 *    display sizes easing to zero at caption sizes. Material's defaults run the other
 *    way (bodyLarge shipped at +0.5sp), which is what makes stock Android type read
 *    loose and airy where this reads set and deliberate.
 *
 * Line heights are held at the Material 3 values on purpose: the ramp is a drop-in for
 * every `MaterialTheme.typography` call site in the app, and vertical rhythm is the one
 * dimension that existing layouts measure against.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.1).sp
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.85).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.75).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.45).sp
    ),
    titleLarge = TextStyle(
        fontFamily = TitleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = TitleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.25).sp
    ),
    titleSmall = TextStyle(
        fontFamily = TitleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.15).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp
    ),
    bodySmall = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.05).sp
    ),
    labelLarge = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp
    ),
    labelMedium = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.05.sp
    )
)

/**
 * Scripts the bundled variable font actually covers.
 *
 * `inter_variable` ships as a Latin subset: Basic Latin, Latin-1,
 * most of Latin Extended-A and Vietnamese. It has no Cyrillic, Greek, CJK, Arabic,
 * Devanagari, Thai or Hebrew.
 */
private val LatinScriptLanguages = setOf(
    "en", "es", "pt", "fr", "de", "it", "nl", "nn", "nb", "no", "da", "sv", "fi", "is",
    "pl", "cs", "sk", "sl", "hr", "bs", "hu", "ro", "et", "lt", "lv", "tr", "az", "sq",
    "ca", "eu", "gl", "ga", "cy", "af", "sw", "id", "in", "ms", "fil", "tl", "vi", "mfe",
    "wae", "mt", "lb",
)

/**
 * Picks the family the ramp is built on for the current locale.
 *
 * Android falls back per *glyph* when a typeface lacks one, so nothing ever renders as
 * tofu — but in a Hindi or Japanese UI that fallback fires on nearly every character,
 * leaving the interface in a mix of two unrelated typefaces with only the Latin half
 * carrying the optical sizing. Handing those locales a single consistent family is the
 * better trade: they keep the ramp's weights, tracking and line heights, and lose only
 * the variable-font refinement that was never going to reach them anyway.
 *
 * This does not fix mixed scripts *within* a locale — an English UI showing a Japanese
 * song title still falls back for that title. Only a font with the glyphs can fix that.
 */
private fun familyFor(language: String, tier: FontFamily): FontFamily =
    if (language.lowercase(Locale.ROOT) in LatinScriptLanguages) tier else FontFamily.Default

/**
 * The app's typography, resolved for the active locale.
 *
 * Prefer this over [AppTypography] at theme level; the plain value stays for previews and
 * any non-composable use.
 */
@Composable
fun rememberAppTypography(): Typography {
    val configuration = LocalConfiguration.current
    val language = configuration.locales[0]?.language ?: "en"
    return remember(language) {
        if (language.lowercase(Locale.ROOT) in LatinScriptLanguages) {
            AppTypography
        } else {
            val display = familyFor(language, DisplayFontFamily)
            val title = familyFor(language, TitleFontFamily)
            val text = familyFor(language, TextFontFamily)
            AppTypography.copy(
                displayLarge = AppTypography.displayLarge.copy(fontFamily = display),
                displayMedium = AppTypography.displayMedium.copy(fontFamily = display),
                displaySmall = AppTypography.displaySmall.copy(fontFamily = display),
                headlineLarge = AppTypography.headlineLarge.copy(fontFamily = display),
                headlineMedium = AppTypography.headlineMedium.copy(fontFamily = display),
                headlineSmall = AppTypography.headlineSmall.copy(fontFamily = display),
                titleLarge = AppTypography.titleLarge.copy(fontFamily = title),
                titleMedium = AppTypography.titleMedium.copy(fontFamily = title),
                titleSmall = AppTypography.titleSmall.copy(fontFamily = title),
                bodyLarge = AppTypography.bodyLarge.copy(fontFamily = text),
                bodyMedium = AppTypography.bodyMedium.copy(fontFamily = text),
                bodySmall = AppTypography.bodySmall.copy(fontFamily = text),
                labelLarge = AppTypography.labelLarge.copy(fontFamily = text),
                labelMedium = AppTypography.labelMedium.copy(fontFamily = text),
                labelSmall = AppTypography.labelSmall.copy(fontFamily = text),
            )
        }
    }
}

/** Languages whose scripts Inter covers: Latin, Cyrillic and Greek. */
private val InterScriptLanguages = LatinScriptLanguages + setOf(
    "ru", "uk", "be", "bg", "sr", "mk", "kk", "ky", "mn", "tg", "el",
)

/**
 * The Liquid type ramp (Inter, iOS proportions) for the active locale. Locales whose
 * script Inter lacks keep the ramp's sizes and weights on the system family, rather than
 * mixing two typefaces glyph by glyph.
 */
@Composable
fun rememberLiquidTypography(): Typography {
    val configuration = LocalConfiguration.current
    val language = configuration.locales[0]?.language ?: "en"
    return remember(language) {
        val base = com.shiny.music.ui.liquid.LiquidMaterialTypography
        if (language.lowercase(Locale.ROOT) in InterScriptLanguages) {
            base
        } else {
            val d = FontFamily.Default
            base.copy(
                displayLarge = base.displayLarge.copy(fontFamily = d),
                displayMedium = base.displayMedium.copy(fontFamily = d),
                displaySmall = base.displaySmall.copy(fontFamily = d),
                headlineLarge = base.headlineLarge.copy(fontFamily = d),
                headlineMedium = base.headlineMedium.copy(fontFamily = d),
                headlineSmall = base.headlineSmall.copy(fontFamily = d),
                titleLarge = base.titleLarge.copy(fontFamily = d),
                titleMedium = base.titleMedium.copy(fontFamily = d),
                titleSmall = base.titleSmall.copy(fontFamily = d),
                bodyLarge = base.bodyLarge.copy(fontFamily = d),
                bodyMedium = base.bodyMedium.copy(fontFamily = d),
                bodySmall = base.bodySmall.copy(fontFamily = d),
                labelLarge = base.labelLarge.copy(fontFamily = d),
                labelMedium = base.labelMedium.copy(fontFamily = d),
                labelSmall = base.labelSmall.copy(fontFamily = d),
            )
        }
    }
}
