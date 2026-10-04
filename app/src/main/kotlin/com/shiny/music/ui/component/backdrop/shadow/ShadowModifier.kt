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
 * Modified by the Shiny Project in 2026 (Apache-2.0 section 4(b) notice): the shadow is drawn
 * straight onto the canvas and cut out with clipOutOutline() instead of a Clear-blended
 * offscreen layer. The git history records each change and its date.
 */
package com.shiny.music.ui.component.backdrop.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.shiny.music.ui.component.backdrop.internal.ShapeProvider
import com.shiny.music.ui.component.backdrop.internal.blur
import com.shiny.music.ui.component.backdrop.internal.clipOutOutline

internal class ShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> Shadow?
) : ModifierNodeElement<ShadowNode>() {

    override fun create(): ShadowNode {
        return ShadowNode(shapeProvider, shadow)
    }

    override fun update(node: ShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShadowElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (shadow != other.shadow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

internal class ShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> Shadow?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private val paint = Paint()
    private var clipPath: Path? = null

    override fun ContentDrawScope.draw() {
        val shadow = shadow() ?: return drawContent()

        // Shiny change: drawn straight onto the canvas; the library drew it into an offscreen
        // layer. The shape is clipped out rather than erased with a Clear-blended draw, and the
        // layer's alpha and blend mode go on the paint. It is one draw either way, so the pixels
        // are the same; what goes is a framebuffer per glass control per frame, which was the
        // player's main GPU cost (PERF_PASS_HANDOFF.md §11, §13).
        val outline = shapeProvider.shape.createOutline(size, layoutDirection, this)
        val path = if (outline is Outline.Rounded) clipPath ?: Path().also { clipPath = it } else null
        paint.color = shadow.color.copy(alpha = shadow.color.alpha * shadow.alpha)
        paint.blendMode = shadow.blendMode
        paint.blur(shadow.radius.toPx())
        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipOutOutline(outline, path)
        canvas.translate(shadow.offset.x.toPx(), shadow.offset.y.toPx())
        canvas.drawOutline(outline, paint)
        canvas.restore()

        drawContent()
    }

    override fun onDetach() {
        clipPath = null
    }
}
