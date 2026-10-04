package com.shiny.music.ui.liquid.player

import android.os.Build
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** A sung word. */
private val Sung = Color.White

/** A word not yet sung: the dim text the voice uncovers. */
private val Unsung = Color.White.copy(alpha = UnsungAlpha)

/** The far end of the glow's reveal: white with no opacity, so the edge never greys. */
private val Clear = Color.White.copy(alpha = 0f)

/** Past any glyph: the open side of a clip. */
private const val Far = 100_000f

/** How soft the glow round a sung word is. */
private val GlowRadius = 5.dp

/**
 * A soft reveal edge: [left] colour up to [edge], [right] beyond it, over ±[feather]. The
 * gradient is made once per row; a frame only moves it, by its local matrix.
 */
private class RevealBrush(
    private val left: Color,
    private val right: Color,
    private val feather: Float,
) : ShaderBrush() {
    private val matrix = android.graphics.Matrix()
    private var shader: Shader? = null

    var edge: Float = Float.NaN
        set(value) {
            if (value == field) return
            field = value
            matrix.setTranslate(value, 0f)
            shader?.setLocalMatrix(matrix)
        }

    override fun createShader(size: Size): Shader =
        LinearGradientShader(Offset(-feather, 0f), Offset(feather, 0f), listOf(left, right)).also {
            it.setLocalMatrix(matrix)
            shader = it
        }
}

/**
 * One line of lyrics laid out for drawing in any state: sung, not yet sung, or part way.
 *
 * The line is set as ordinary text at the page's width — the words wrap as text does, keep
 * their shaping and kerning, and right-to-left lines run right to left — then each visual row
 * is laid out on its own, so a row is drawn with its own glyphs only: a descender or a matra
 * reaching into the next row is never cut between two inks. Every state draws these same row
 * layouts, so a line passing from sung to still never shifts by a pixel.
 *
 * Each timed piece owns a strip of its row, split at the middle of the gap to its neighbours
 * (between syllables of one word, where they meet); the strips tile the row. Part way through
 * a line a row is drawn as at most three clipped draws — the pieces already sung, the piece
 * being sung with a soft moving edge, the pieces to come.
 */
internal class LineInk private constructor(
    val width: Int,
    val height: Int,
    private val rows: Array<TextLayoutResult>,
    private val rowX: FloatArray,
    private val rowY: FloatArray,
    private val rowRtl: BooleanArray,
    /** The pieces starting on each row: `rowFirst[r] until rowEnd[r]`. */
    private val rowFirst: IntArray,
    private val rowEnd: IntArray,
    private val pieceRow: IntArray,
    /** Each piece's strip of its row, in the row's coordinates. */
    private val clipLo: FloatArray,
    private val clipHi: FloatArray,
    /** Each piece's glyphs, left to right. */
    private val inkLo: FloatArray,
    private val inkHi: FloatArray,
    private val feather: Float,
) {
    private val fill = Array(rows.size) { r ->
        if (rowRtl[r]) RevealBrush(Unsung, Sung, feather) else RevealBrush(Sung, Unsung, feather)
    }
    private val glowFill = Array(rows.size) { r ->
        if (rowRtl[r]) RevealBrush(Clear, Sung, feather) else RevealBrush(Sung, Clear, feather)
    }

    /** The line as still white text; its layer dims it. */
    fun drawStatic(scope: DrawScope) = with(scope) {
        for (r in rows.indices) translate(rowX[r], rowY[r]) { drawText(rows[r], color = Sung) }
    }

    /** The line being sung at [t]: sung words white, the word being sung revealed, the rest dim. */
    fun drawLive(scope: DrawScope, line: LyricLineTiming, t: Double) = with(scope) {
        val q = line.startedAt(t) - 1
        for (r in rows.indices) {
            val first = rowFirst[r]
            val end = rowEnd[r]
            translate(rowX[r], rowY[r]) {
                when {
                    // A row no piece starts on continues the piece before it.
                    first == end -> {
                        val before = first - 1
                        val sung = before >= 0 && (before < q || (before == q && line.progress(q, t) >= 1f))
                        drawText(rows[r], color = if (sung) Sung else Unsung)
                    }
                    q < first -> drawText(rows[r], color = Unsung)
                    q >= end -> drawText(rows[r], color = Sung)
                    else -> drawRowPartWay(r, first, end, q, line.progress(q, t))
                }
            }
        }
    }

    private fun DrawScope.drawRowPartWay(r: Int, first: Int, end: Int, q: Int, p: Float) {
        val row = rows[r]
        val rtl = rowRtl[r]
        val lo = clipLo[q]
        val hi = clipHi[q]
        if (q > first) {
            if (rtl) clipRect(hi, -Far, Far, Far) { drawText(row, color = Sung) }
            else clipRect(-Far, -Far, lo, Far) { drawText(row, color = Sung) }
        }
        clipRect(lo, -Far, hi, Far) {
            when {
                p >= 1f -> drawText(row, color = Sung)
                p <= 0f -> drawText(row, color = Unsung)
                else -> {
                    val brush = fill[r]
                    brush.edge = revealEdge(q, rtl, p)
                    drawText(row, brush, alpha = 1f)
                }
            }
        }
        if (q < end - 1) {
            if (rtl) clipRect(-Far, -Far, lo, Far) { drawText(row, color = Unsung) }
            else clipRect(hi, -Far, Far, Far) { drawText(row, color = Unsung) }
        }
    }

    /**
     * Where the soft edge is when piece [q] is [p] through: it enters from wholly before the
     * glyphs and leaves wholly past them, so the piece is fully dim at 0 and fully lit at 1.
     */
    private fun revealEdge(q: Int, rtl: Boolean, p: Float): Float =
        if (rtl) inkHi[q] + feather - (inkHi[q] - inkLo[q] + 2f * feather) * p
        else inkLo[q] - feather + (inkHi[q] - inkLo[q] + 2f * feather) * p

    /** Whether any word of [line] glows at [t]. */
    fun hasGlow(line: LyricLineTiming, t: Double): Boolean {
        val started = line.startedAt(t)
        for (i in 0 until started) if (line.glow(i, t) > GlowFloor) return true
        return false
    }

    /**
     * The glow's source: the lit part of each glowing word at its glow strength, for the
     * caller to blur. Nothing round letters not yet sung.
     */
    fun drawGlow(scope: DrawScope, line: LyricLineTiming, t: Double) = with(scope) {
        val started = line.startedAt(t)
        for (i in 0 until started) {
            val g = line.glow(i, t)
            if (g <= GlowFloor) continue
            val r = pieceRow[i]
            val p = line.progress(i, t)
            translate(rowX[r], rowY[r]) {
                clipRect(clipLo[i], -Far, clipHi[i], Far) {
                    if (p >= 1f) {
                        drawText(rows[r], color = Sung.copy(alpha = g))
                    } else {
                        val brush = glowFill[r]
                        brush.edge = revealEdge(i, rowRtl[r], p)
                        drawText(rows[r], brush, alpha = g)
                    }
                }
            }
        }
    }

    companion object {
        private const val GlowFloor = 0.004f

        fun build(measurer: TextMeasurer, line: LyricLineTiming, style: TextStyle, width: Int): LineInk {
            val text = line.text
            val constraints = Constraints(maxWidth = width)
            val full = measurer.measure(text, style, overflow = TextOverflow.Visible, constraints = constraints)
            val rowCount = full.lineCount.coerceAtLeast(1)
            val rowStart = IntArray(rowCount)
            val rows = Array(rowCount) { r ->
                val s = if (text.isEmpty()) 0 else full.getLineStart(r)
                val e = if (text.isEmpty()) 0 else full.getLineEnd(r, visibleEnd = true).coerceAtLeast(s)
                rowStart[r] = s
                measurer.measure(
                    text.substring(s, e),
                    style,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                    maxLines = 1,
                    constraints = constraints,
                )
            }
            val rowX = FloatArray(rowCount) { r -> if (text.isEmpty()) 0f else full.getLineLeft(r) - rows[r].getLineLeft(0) }
            val rowY = FloatArray(rowCount) { r -> if (text.isEmpty()) 0f else full.getLineBaseline(r) - rows[r].firstBaseline }
            val rowRtl = BooleanArray(rowCount) { r ->
                rows[r].layoutInput.text.isNotEmpty() && rows[r].getParagraphDirection(0) == ResolvedTextDirection.Rtl
            }

            val n = line.pieceCount
            val pieceRow = IntArray(n)
            val inkLo = FloatArray(n)
            val inkHi = FloatArray(n)
            for (i in 0 until n) {
                val from = line.pieceFrom[i]
                val r = if (text.isEmpty()) 0 else full.getLineForOffset(from.coerceAtMost(text.length - 1))
                pieceRow[i] = r
                val row = rows[r]
                val length = row.layoutInput.text.length
                val a = (from - rowStart[r]).coerceIn(0, length)
                val b = (line.pieceTo[i] - rowStart[r]).coerceIn(a, length)
                if (b > a) {
                    var lo = Float.POSITIVE_INFINITY
                    var hi = Float.NEGATIVE_INFINITY
                    for (c in a until b) {
                        val box = row.getBoundingBox(c)
                        if (box.left < lo) lo = box.left
                        if (box.right > hi) hi = box.right
                    }
                    inkLo[i] = lo
                    inkHi[i] = hi
                } else {
                    val x = if (length == 0) 0f else row.getHorizontalPosition(a, usePrimaryDirection = true)
                    inkLo[i] = x
                    inkHi[i] = x
                }
            }

            val rowFirst = IntArray(rowCount)
            val rowEnd = IntArray(rowCount)
            var cursor = 0
            for (r in 0 until rowCount) {
                rowFirst[r] = cursor
                while (cursor < n && pieceRow[cursor] <= r) cursor++
                rowEnd[r] = cursor
            }

            // Each piece's strip: from the middle of the gap before it to the middle of the gap
            // after it, the first and last open to the row's ends; kept in order so the strips
            // tile the row and every glyph is drawn exactly once.
            val clipLo = FloatArray(n)
            val clipHi = FloatArray(n)
            for (r in 0 until rowCount) {
                val first = rowFirst[r]
                val end = rowEnd[r]
                if (first == end) continue
                if (!rowRtl[r]) {
                    var boundary = -Far
                    for (i in first until end) {
                        clipLo[i] = boundary
                        boundary = if (i == end - 1) Far else maxOf(boundary, (inkHi[i] + inkLo[i + 1]) / 2f)
                        clipHi[i] = boundary
                    }
                } else {
                    var boundary = Far
                    for (i in first until end) {
                        clipHi[i] = boundary
                        boundary = if (i == end - 1) -Far else minOf(boundary, (inkLo[i] + inkHi[i + 1]) / 2f)
                        clipLo[i] = boundary
                    }
                }
            }

            val rowHeight = if (rowCount > 0) rows[0].size.height.toFloat() else full.size.height.toFloat()
            return LineInk(
                width = width,
                height = full.size.height,
                rows = rows,
                rowX = rowX,
                rowY = rowY,
                rowRtl = rowRtl,
                rowFirst = rowFirst,
                rowEnd = rowEnd,
                pieceRow = pieceRow,
                clipLo = clipLo,
                clipHi = clipHi,
                inkLo = inkLo,
                inkHi = inkHi,
                feather = rowHeight * 0.45f,
            )
        }
    }
}

/**
 * Draws a synced line: laid out once, then painted each frame from [motion] — still white when
 * nothing about it depends on the moment, word by word while it is being sung. Only a line whose
 * ink depends on the exact time reads the clock, in its draw, so a frame redraws that line and
 * nothing else; no state is read in composition and nothing recomposes as the song plays.
 */
internal fun Modifier.lyricInk(
    line: LyricLineTiming,
    index: Int,
    motion: LyricsMotion,
    measurer: TextMeasurer,
    style: TextStyle,
    glow: Boolean,
): Modifier = this then LyricInkElement(line, index, motion, measurer, style, glow)

private data class LyricInkElement(
    val line: LyricLineTiming,
    val index: Int,
    val motion: LyricsMotion,
    val measurer: TextMeasurer,
    val style: TextStyle,
    val glow: Boolean,
) : ModifierNodeElement<LyricInkNode>() {
    override fun create() = LyricInkNode(line, index, motion, measurer, style, glow)

    override fun update(node: LyricInkNode) = node.update(line, index, motion, measurer, style, glow)
}

private class LyricInkNode(
    private var line: LyricLineTiming,
    private var index: Int,
    private var motion: LyricsMotion,
    private var measurer: TextMeasurer,
    private var style: TextStyle,
    private var glow: Boolean,
) : Modifier.Node(), LayoutModifierNode, DrawModifierNode {

    private var ink: LineInk? = null

    /** Whether this line's ink depends on the exact time: changes a few times a line. */
    private var live: State<Boolean> = liveState()

    private var glowLayer: GraphicsLayer? = null
    private var glowEffect: RenderEffect? = null
    private var glowRadius = 0f
    private var glowMargin = 0
    private var glowTime = 0.0

    /** Records the glow's source into its layer; one object for the node's life. */
    private val recordGlow: DrawScope.() -> Unit = {
        val current = ink
        if (current != null) {
            val m = glowMargin.toFloat()
            translate(m, m) { current.drawGlow(this, line, glowTime) }
        }
    }

    private fun liveState(): State<Boolean> {
        val m = motion
        val i = index
        return derivedStateOf { m.isLive(i) }
    }

    fun update(
        line: LyricLineTiming,
        index: Int,
        motion: LyricsMotion,
        measurer: TextMeasurer,
        style: TextStyle,
        glow: Boolean,
    ) {
        val relayout = line !== this.line || measurer !== this.measurer || style != this.style
        val reread = index != this.index || motion !== this.motion
        this.line = line
        this.index = index
        this.motion = motion
        this.measurer = measurer
        this.style = style
        this.glow = glow
        if (reread) live = liveState()
        if (relayout) {
            ink = null
            invalidateMeasurement()
        }
        invalidateDraw()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        var current = ink
        if (current == null || current.width != width) {
            current = LineInk.build(measurer, line, style, width)
            ink = current
            invalidateDraw()
        }
        val placeable = measurable.measure(Constraints.fixed(width, current.height))
        return layout(width, current.height) { placeable.place(0, 0) }
    }

    override fun ContentDrawScope.draw() {
        val current = ink
        if (current != null) {
            if (!live.value) {
                current.drawStatic(this)
            } else {
                val t = motion.time
                if (glow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && current.hasGlow(line, t)) {
                    drawGlowLayer(current, t)
                }
                current.drawLive(this, line, t)
            }
        }
        drawContent()
    }

    /**
     * The glow: the lit words, blurred once on the GPU in one small layer per line, drawn
     * behind the text. The effect object is made once; a frame only re-records the source.
     */
    private fun ContentDrawScope.drawGlowLayer(current: LineInk, t: Double) {
        val radius = GlowRadius.toPx()
        if (radius != glowRadius) {
            glowRadius = radius
            glowEffect = BlurEffect(radius, radius, TileMode.Decal)
            glowMargin = ceil(radius * 2f).toInt()
            glowLayer?.renderEffect = glowEffect
        }
        val layer = glowLayer ?: requireGraphicsContext().createGraphicsLayer().also {
            it.renderEffect = glowEffect
            glowLayer = it
        }
        glowTime = t
        layer.topLeft = IntOffset(-glowMargin, -glowMargin)
        layer.record(IntSize(current.width + 2 * glowMargin, current.height + 2 * glowMargin), recordGlow)
        drawLayer(layer)
    }

    override fun onDetach() {
        glowLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        glowLayer = null
    }
}
