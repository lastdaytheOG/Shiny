/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
/*
 * Modified by the Shiny Project in 2026 (Apache-2.0 section 4(b) notice): adds
 * clipOutOutline(). The git history records each change and its date.
 */
package com.shiny.music.ui.component.backdrop.internal

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path

internal fun Canvas.clipOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)
        is Outline.Rounded -> {
            path!!.rewind()
            path.addRoundRect(outline.roundRect)
            clipPath(path)
        }

        is Outline.Generic -> clipPath(outline.path)
    }
}

/**
 * Shiny addition: clips [outline] *out*, so what is drawn next shows only around the shape.
 * The same cut the shadow used to make with a Clear-blended draw in an offscreen layer.
 */
internal fun Canvas.clipOutOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect, ClipOp.Difference)
        is Outline.Rounded -> {
            path!!.rewind()
            path.addRoundRect(outline.roundRect)
            clipPath(path, ClipOp.Difference)
        }
        is Outline.Generic -> clipPath(outline.path, ClipOp.Difference)
    }
}
