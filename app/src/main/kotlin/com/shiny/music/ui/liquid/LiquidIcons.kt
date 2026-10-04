package com.shiny.music.ui.liquid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Glyphs drawn in the SF Symbols idiom — rounded terminals, filled masses, even stroke —
 * for the places where a Material icon reads unmistakably Android: the tab bar and the
 * player's utility row. Everything else uses Material Rounded, which is close enough.
 */
object LiquidIcons {

    /** "square.stack.fill" — the Library tab. */
    val Library: ImageVector by lazy {
        ImageVector.Builder("LiquidLibrary", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // front card
                moveTo(6.2f, 8.0f)
                lineTo(17.8f, 8.0f)
                arcTo(2.6f, 2.6f, 0f, false, true, 20.4f, 10.6f)
                lineTo(20.4f, 19.2f)
                arcTo(2.6f, 2.6f, 0f, false, true, 17.8f, 21.8f)
                lineTo(6.2f, 21.8f)
                arcTo(2.6f, 2.6f, 0f, false, true, 3.6f, 19.2f)
                lineTo(3.6f, 10.6f)
                arcTo(2.6f, 2.6f, 0f, false, true, 6.2f, 8.0f)
                close()
            }
            path(fill = SolidColor(Color.Black), fillAlpha = 0.75f) {
                moveTo(6.4f, 4.9f)
                lineTo(17.6f, 4.9f)
                arcTo(0.8f, 0.8f, 0f, false, true, 17.6f, 6.5f)
                lineTo(6.4f, 6.5f)
                arcTo(0.8f, 0.8f, 0f, false, true, 6.4f, 4.9f)
                close()
            }
            path(fill = SolidColor(Color.Black), fillAlpha = 0.5f) {
                moveTo(8.4f, 2.0f)
                lineTo(15.6f, 2.0f)
                arcTo(0.8f, 0.8f, 0f, false, true, 15.6f, 3.6f)
                lineTo(8.4f, 3.6f)
                arcTo(0.8f, 0.8f, 0f, false, true, 8.4f, 2.0f)
                close()
            }
        }.build()
    }

    /** "square.grid.2x2.fill" — the New tab. */
    val Grid: ImageVector by lazy {
        ImageVector.Builder("LiquidGrid", 24.dp, 24.dp, 24f, 24f).apply {
            fun square(x: Float, y: Float) = path(fill = SolidColor(Color.Black)) {
                val s = 8.6f
                val r = 2.4f
                moveTo(x + r, y)
                lineTo(x + s - r, y)
                arcTo(r, r, 0f, false, true, x + s, y + r)
                lineTo(x + s, y + s - r)
                arcTo(r, r, 0f, false, true, x + s - r, y + s)
                lineTo(x + r, y + s)
                arcTo(r, r, 0f, false, true, x, y + s - r)
                lineTo(x, y + r)
                arcTo(r, r, 0f, false, true, x + r, y)
                close()
            }
            square(2.6f, 2.6f)
            square(12.8f, 2.6f)
            square(2.6f, 12.8f)
            square(12.8f, 12.8f)
        }.build()
    }

    /** "dot.radiowaves.left.and.right" — the Together tab. */
    val Waves: ImageVector by lazy {
        ImageVector.Builder("LiquidWaves", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 9.6f)
                arcTo(2.4f, 2.4f, 0f, true, true, 11.99f, 9.6f)
                close()
            }
            val stroke = SolidColor(Color.Black)
            path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(8.2f, 8.2f)
                arcTo(5.4f, 5.4f, 0f, false, false, 8.2f, 15.8f)
                moveTo(15.8f, 8.2f)
                arcTo(5.4f, 5.4f, 0f, false, true, 15.8f, 15.8f)
            }
            path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5.2f, 5.2f)
                arcTo(9.6f, 9.6f, 0f, false, false, 5.2f, 18.8f)
                moveTo(18.8f, 5.2f)
                arcTo(9.6f, 9.6f, 0f, false, true, 18.8f, 18.8f)
            }
        }.build()
    }

    /** "quote.bubble" — lyrics. */
    val Lyrics: ImageVector by lazy {
        ImageVector.Builder("LiquidLyrics", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 3.6f)
                lineTo(17.5f, 3.6f)
                arcTo(3.4f, 3.4f, 0f, false, true, 20.9f, 7.0f)
                lineTo(20.9f, 13.4f)
                arcTo(3.4f, 3.4f, 0f, false, true, 17.5f, 16.8f)
                lineTo(10.6f, 16.8f)
                lineTo(6.4f, 20.4f)
                lineTo(6.4f, 16.8f)
                arcTo(3.4f, 3.4f, 0f, false, true, 3.1f, 13.4f)
                lineTo(3.1f, 7.0f)
                arcTo(3.4f, 3.4f, 0f, false, true, 6.5f, 3.6f)
                close()
            }
            fun quote(x: Float) {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(x, 8.6f)
                    arcTo(1.5f, 1.5f, 0f, true, true, x - 0.01f, 8.6f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(x + 1.5f, 10.1f)
                    curveTo(x + 1.5f, 11.8f, x + 0.5f, 12.9f, x - 0.9f, 13.4f)
                    curveTo(x - 0.3f, 12.7f, x + 0.1f, 11.9f, x + 0.1f, 11.2f)
                    close()
                }
            }
            quote(9.3f)
            quote(14.2f)
        }.build()
    }

    /** "airplayaudio" — output device. */
    val Output: ImageVector by lazy {
        ImageVector.Builder("LiquidOutput", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.2f, 16.4f)
                arcTo(8.2f, 8.2f, 0f, true, true, 17.8f, 16.4f)
                moveTo(8.8f, 13.9f)
                arcTo(4.4f, 4.4f, 0f, true, true, 15.2f, 13.9f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14.2f)
                lineTo(16.6f, 21.2f)
                lineTo(7.4f, 21.2f)
                close()
            }
        }.build()
    }

    /** "list.bullet" — queue. */
    val Queue: ImageVector by lazy {
        ImageVector.Builder("LiquidQueue", 24.dp, 24.dp, 24f, 24f).apply {
            val stroke = SolidColor(Color.Black)
            path(stroke = stroke, strokeLineWidth = 2.0f, strokeLineCap = StrokeCap.Round) {
                moveTo(9.2f, 6.5f); lineTo(20.4f, 6.5f)
                moveTo(9.2f, 12f); lineTo(20.4f, 12f)
                moveTo(9.2f, 17.5f); lineTo(20.4f, 17.5f)
            }
            fun dot(y: Float) = path(fill = stroke) {
                moveTo(4.6f, y - 1.35f)
                arcTo(1.35f, 1.35f, 0f, true, true, 4.59f, y - 1.35f)
                close()
            }
            dot(6.5f)
            dot(12f)
            dot(17.5f)
        }.build()
    }
}
