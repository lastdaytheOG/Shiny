

package com.shiny.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.shiny.music.ui.liquid.LocalLiquidColors
import com.shiny.music.ui.liquid.ShinyRoseDark
import com.shiny.music.ui.liquid.ShinyRoseLight
import com.shiny.music.ui.liquid.appearance.AppearanceColor
import com.shiny.music.ui.liquid.appearance.ProvideShinyAppearance
import com.shiny.music.ui.liquid.liquidAmoledColors
import com.shiny.music.ui.liquid.liquidDarkColors
import com.shiny.music.ui.liquid.liquidLightColors
import com.shiny.music.ui.liquid.liquidMaterialColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)

/**
 * The corner scale.
 *
 * Material's defaults (4/8/12/16/28dp) were drawn for a flatter, squarer language than
 * the one this app uses, and leaving them in place is why stock M3 dialogs and cards
 * read as borrowed next to Shiny's own surfaces. Every step is opened up so that a
 * component picking up a theme shape lands on the same curvature as the hand-rolled
 * ones around it — the radius stops being a per-component decision.
 *
 * `extraSmall` stays at 24dp because the app leans on it for pill-shaped chips and
 * small controls, where a smaller radius would flatten them back into rectangles.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(24.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * The shape of a piece of artwork.
 *
 * Artwork is the one element whose corner scales with it — 8% of its shorter edge — so a
 * 104dp shelf tile, a 128dp tile and a full-width grid cell are one shape at three sizes
 * rather than one fixed radius that looks sharp on the large ones and soft on the small.
 *
 * It stays deliberately tighter than the surface scale above. A cover is an object resting
 * on a surface; given a surface's 24dp radius, every shelf turns into a row of cards.
 */
val ArtworkShape = RoundedCornerShape(percent = 8)

/**
 * Alpha of the hairline drawn around artwork, applied to `onSurface`.
 *
 * It exists for covers that are close to the page colour — a white sleeve on a light
 * theme, a black one on a dark theme — whose edge otherwise dissolves into the page. On
 * any cover with its own contrast it is effectively invisible.
 */
const val ArtworkEdgeAlpha = 0.08f

/**
 * The app theme.
 *
 * Since the Liquid redesign the grounds are the fixed iOS neutrals — they no longer move
 * with the artwork — and only the tint comes from [themeColor]: the user's chosen colour,
 * or Shiny Rose when they have not chosen one. Material components read the same palette
 * through [liquidMaterialColorScheme], so screens that have not been rebuilt still sit on
 * the new grounds with the new type.
 */
@Composable
fun ShinyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val accent = when {
        themeColor == DefaultThemeColor -> if (darkTheme) ShinyRoseDark else ShinyRoseLight
        themeColor.toArgb() == AppearanceColor.MonochromeTint ->
            Color(if (darkTheme) AppearanceColor.MonochromeDark else AppearanceColor.MonochromeLight)
        else -> themeColor
    }
    val amoled = darkTheme && pureBlack
    val liquid = remember(darkTheme, amoled, accent) {
        when {
            amoled -> liquidAmoledColors(accent)
            darkTheme -> liquidDarkColors(accent)
            else -> liquidLightColors(accent)
        }
    }
    val colorScheme = remember(liquid, amoled) {
        liquidMaterialColorScheme(liquid).pureBlack(amoled)
    }

    CompositionLocalProvider(LocalLiquidColors provides liquid) {
        ProvideShinyAppearance(amoled = amoled) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = rememberLiquidTypography(),
                shapes = AppShapes,
                content = content,
            )
        }
    }
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

/**
 * AMOLED for the screens still drawn with Material. The Liquid dark scheme is already
 * black at the ground; this lowers the container tiers — sheets, menus, dialogs — to
 * near-black steps so large panels stop lighting pixels, while each tier still sits
 * visibly above the one below it.
 */
fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0B),
        surfaceContainer = Color(0xFF111113),
        surfaceContainerHigh = Color(0xFF18181A),
        surfaceContainerHighest = Color(0xFF1C1C1E),
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
