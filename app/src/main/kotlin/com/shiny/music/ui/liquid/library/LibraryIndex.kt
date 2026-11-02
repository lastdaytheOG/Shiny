package com.shiny.music.ui.liquid.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiny.music.ui.liquid.Liquid
import com.shiny.music.ui.liquid.PageMargin
import kotlinx.coroutines.launch
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

data class IndexSection(val group: Int, val rank: Int, val label: String) {
    val key: String get() = "$group/$rank"
}

private const val LatinGroup = 0
private const val SymbolGroup = 1

private val KanaRows = listOf("あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ")
private val HangulInitials = listOf("ㄱ", "ㄱ", "ㄴ", "ㄷ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅂ", "ㅅ", "ㅅ", "ㅇ", "ㅈ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")
private val HangulRank = listOf(0, 0, 1, 2, 2, 3, 4, 5, 5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13)

private fun kanaRank(c: Char): Int {
    val h = if (c in 'ァ'..'ヶ') c - 0x60 else c
    return when (h) {
        in 'ぁ'..'お' -> 0
        in 'か'..'ご' -> 1
        in 'さ'..'ぞ' -> 2
        in 'た'..'ど' -> 3
        in 'な'..'の' -> 4
        in 'は'..'ぽ' -> 5
        in 'ま'..'も' -> 6
        in 'ゃ'..'よ' -> 7
        in 'ら'..'ろ' -> 8
        in 'ゎ'..'ん' -> 9
        else -> 0
    }
}

// latin titles go under their letter without the accent, kana by row, hangul by first consonant, anything else by its first letter
fun indexSectionOf(title: String): IndexSection {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return IndexSection(SymbolGroup, 0, "#")
    val first = Normalizer.normalize(String(Character.toChars(trimmed.codePointAt(0))), Normalizer.Form.NFKC)
    if (first.isEmpty()) return IndexSection(SymbolGroup, 0, "#")
    val cp = first.codePointAt(0)
    val script = runCatching { Character.UnicodeScript.of(cp) }.getOrNull() ?: return IndexSection(SymbolGroup, 0, "#")
    return when (script) {
        Character.UnicodeScript.LATIN -> {
            val base = Normalizer.normalize(first, Normalizer.Form.NFD).first().uppercaseChar()
            if (base in 'A'..'Z') IndexSection(LatinGroup, base - 'A', base.toString()) else IndexSection(SymbolGroup, 0, "#")
        }
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> {
            val rank = kanaRank(first[0])
            IndexSection(2 + script.ordinal, rank, KanaRows[rank])
        }
        Character.UnicodeScript.HANGUL -> {
            val c = first[0]
            val initial = if (c in '가'..'힣') (c - '가') / 588 else 11
            IndexSection(2 + script.ordinal, HangulRank[initial], HangulInitials[initial])
        }
        // kanji/hanzi share one section, one section per character was hundreds of tiny ones
        Character.UnicodeScript.HAN -> IndexSection(2 + script.ordinal, 0, "漢")
        Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED, Character.UnicodeScript.UNKNOWN ->
            IndexSection(SymbolGroup, 0, "#")
        else -> {
            val letter = first.uppercase(Locale.ROOT).first()
            IndexSection(2 + script.ordinal, letter.code, letter.toString())
        }
    }
}

fun <T> indexSections(items: List<T>, nameOf: (T) -> String): List<Pair<IndexSection, List<T>>> {
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    return items
        .groupBy { indexSectionOf(nameOf(it)) }
        .entries
        .sortedWith(compareBy({ it.key.group }, { it.key.rank }))
        .map { (section, entries) -> section to entries.sortedWith(compareBy(collator) { nameOf(it).trim() }) }
}

private class RailSlot(val label: String, val target: String?, val tag: Boolean)

private val RailStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
private val RailTagStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 9.sp)

@Composable
fun BoxScope.LibraryIndexRail(
    sections: List<IndexSection>,
    onSelect: (String) -> Unit,
    topInset: Dp,
    bottomInset: Dp,
) {
    if (sections.size < 2) return
    val colors = Liquid.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)

    val slots = remember(sections) {
        val present = sections.associateBy { it.key }
        buildList {
            ('A'..'Z').forEachIndexed { i, letter ->
                add(RailSlot(letter.lowercase(), "$LatinGroup/$i".takeIf { it in present }, false))
            }
            add(RailSlot("#", "$SymbolGroup/0".takeIf { it in present }, false))
            sections.filter { it.group > SymbolGroup }
                .groupBy { it.group }
                .forEach { (_, group) -> add(RailSlot(group.first().label, group.first().key, true)) }
        }
    }
    val order = remember(slots) { slots.mapNotNull { it.target } }

    var height by remember { mutableFloatStateOf(0f) }
    var touchY by remember { mutableFloatStateOf(-1f) }
    var last by remember { mutableStateOf<String?>(null) }
    val presence = remember { Animatable(0f) }
    val sigma = with(density) { 30.dp.toPx() }
    val swing = with(density) { 36.dp.toPx() }

    // a letter with nothing under it jumps to the next one that has something, then the one before
    fun resolve(index: Int): String? {
        val slot = slots.getOrNull(index) ?: return null
        slot.target?.let { return it }
        for (i in index + 1 until slots.size) slots[i].target?.let { return it }
        for (i in index - 1 downTo 0) slots[i].target?.let { return it }
        return order.firstOrNull()
    }

    fun handle(y: Float) {
        touchY = y
        if (height <= 0f) return
        val index = ((y / height) * slots.size).toInt().coerceIn(0, slots.lastIndex)
        val key = resolve(index)
        if (key != null && key != last) {
            last = key
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            select(key)
        }
        if (presence.targetValue != 1f) scope.launch { presence.animateTo(1f, tween(150)) }
    }

    fun release() {
        last = null
        scope.launch { presence.animateTo(0f, tween(340, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))) }
    }

    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(top = topInset, bottom = bottomInset)
            .fillMaxHeight()
            .width(160.dp)
            .graphicsLayer { alpha = presence.value }
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.6f to colors.background.copy(alpha = 0.6f),
                    1f to colors.background.copy(alpha = 0.9f),
                ),
            ),
    )

    Layout(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(top = topInset, bottom = bottomInset, end = 2.dp)
            .fillMaxHeight()
            .width(PageMargin + 4.dp)
            .onSizeChanged { height = it.height.toFloat() }
            .pointerInput(slots) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    handle(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        handle(change.position.y)
                    }
                    release()
                }
            },
        content = {
            slots.forEachIndexed { index, slot ->
                Text(
                    text = slot.label,
                    style = if (slot.tag) RailTagStyle else RailStyle,
                    color = if (slot.tag) colors.accent else colors.label,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        val unit = if (slots.isNotEmpty()) height / slots.size else 0f
                        val center = (index + 0.5f) * unit
                        val d = center - touchY
                        val near = exp(-(d / sigma) * (d / sigma)) * presence.value
                        // letters near the finger spread apart, grow and swing left, far ones stay put
                        translationY = 1.2f * d * near
                        translationX = -swing * near
                        val s = 1f + 1.4f * near
                        scaleX = s
                        scaleY = s
                        val rest = if (slot.target != null) 0.6f else 0.22f
                        alpha = rest + (1f - rest) * near
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val unit = if (placeables.isNotEmpty()) constraints.maxHeight / placeables.size.toFloat() else 0f
            placeables.forEachIndexed { i, p ->
                p.place((constraints.maxWidth - p.width) / 2, ((i + 0.5f) * unit - p.height / 2f).roundToInt())
            }
        }
    }
}
