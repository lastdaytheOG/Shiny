package com.shiny.music.ui.liquid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Smart Shuffle's mark: the shuffle arrows with two sparkles in the space above and below
 * the crossing — "shuffle, but it knows you". The arrows take [arrows]; the sparkles keep
 * their own warm gradient, so draw it with an Image (an Icon tint would flatten them).
 */
fun smartShuffleGlyph(arrows: Color): ImageVector {
    val sparkle = Brush.linearGradient(
        colors = listOf(Color(0xFFFFE27A), Color(0xFFFF8FB1), Color(0xFFB794FF)),
        start = Offset(3f, 0f),
        end = Offset(14f, 15f),
    )
    return ImageVector.Builder(
        name = "SmartShuffle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        // Material "shuffle": the two crossing arrows.
        pathData = PathParser().parsePathString(
            "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5z" +
                "m0.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z",
        ).toNodes(),
        fill = SolidColor(arrows),
    ).addPath(
        // The large sparkle, in the gap above the first arrow.
        pathData = PathParser().parsePathString(
            "M9.7 0.6 Q10.25 2.95 12.6 3.5 Q10.25 4.05 9.7 6.4 Q9.15 4.05 6.8 3.5 Q9.15 2.95 9.7 0.6Z",
        ).toNodes(),
        fill = sparkle,
    ).addPath(
        // A small one, below the crossing.
        pathData = PathParser().parsePathString(
            "M4.9 10.6 Q5.2 11.9 6.5 12.2 Q5.2 12.5 4.9 13.8 Q4.6 12.5 3.3 12.2 Q4.6 11.9 4.9 10.6Z",
        ).toNodes(),
        fill = sparkle,
    ).build()
}
