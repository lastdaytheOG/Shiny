@file:OptIn(ExperimentalTextApi::class)

package com.shiny.music.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.shiny.music.R

/**
 * Weights we instantiate off the variable font. Anything the UI asks for outside this
 * set is resolved by Compose to the nearest entry, so keeping the list tight keeps the
 * number of live `Typeface` instances (one per weight per optical size) small.
 */
private val FlexWeights = listOf(
    FontWeight.Normal,     // 400
    FontWeight.Medium,     // 500
    FontWeight.SemiBold,   // 600
    FontWeight.Bold,       // 700
    FontWeight.ExtraBold,  // 800
)

/**
 * Builds a [FontFamily] off `inter_variable` pinned to one point on the optical size
 * (`opsz`) axis.
 *
 * The face is a 6-axis variable font, and `opsz` is the axis that makes type read as
 * *designed for its size* rather than scaled to it: low optical sizes open the counters
 * and thicken the thin strokes so small text stays legible, high optical sizes tighten
 * apertures and sharpen the contrast so large text looks drawn rather than inflated.
 * This is the same Text/Display split Apple ships in SF Pro, and it is the reason the
 * type ramp below hands each tier its own family instead of scaling a single one.
 */
private fun flexFamily(opticalSize: TextUnit) = FontFamily(
    FlexWeights.map { weight ->
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

/** Optical size for the display and headline tiers — tight, high-contrast, poster-like. */
val DisplayFontFamily = flexFamily(40.sp)

/** Optical size for titles and section headers — the bridge between display and text. */
val TitleFontFamily = flexFamily(22.sp)

/** Optical size for body copy, list rows and labels — open counters, built to be read. */
val TextFontFamily = flexFamily(16.sp)
