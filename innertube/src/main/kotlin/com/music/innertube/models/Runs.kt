package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Runs(
    val runs: List<Run>?,
)

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint?,
)

/**
 * The count in a label such as "653K subscribers" or "月間リスナー 12.3万人": the number with its
 * magnitude unit ("653K", "12.3万"), wherever the label puts it. Null when there is no number.
 */
fun Runs?.extractCountText(): String? {
    val label = this?.runs?.joinToString(separator = "") { it.text } ?: return null
    return countIn(label)
}

/** Latin magnitude letters. One only counts when it ends its word: "1.2M", not the m of "monthly". */
private const val LATIN_UNITS = "KkMmBbTt"

/** 千 万 萬 億 亿 兆 and 천 만 억. These are written straight against the number and what follows. */
private const val EAST_ASIAN_UNITS = "千万萬億亿兆천만억"

private const val DIGIT_SEPARATORS = ".,．，"

internal fun countIn(label: String): String? {
    val start = label.indexOfFirst { it.isDigit() }
    if (start < 0) return null

    // The number: digits, with separators or single spaces only between two digits.
    var end = start + 1
    while (end < label.length) {
        val c = label[end]
        val joinsDigits = (c in DIGIT_SEPARATORS || c.isWhitespace()) && label.getOrNull(end + 1)?.isDigit() == true
        if (c.isDigit() || joinsDigits) end++ else break
    }
    val number = label.substring(start, end)

    // The unit, which may be set off from the number by spaces.
    var unitStart = end
    while (unitStart < label.length && label[unitStart].isWhitespace()) unitStart++
    var unitEnd = unitStart
    while (unitEnd < label.length && label[unitEnd] in EAST_ASIAN_UNITS) unitEnd++
    if (unitEnd == unitStart && label.getOrNull(unitStart)?.let { it in LATIN_UNITS } == true &&
        label.getOrNull(unitStart + 1)?.isLetter() != true
    ) {
        unitEnd = unitStart + 1
    }
    return number + label.substring(unitStart, unitEnd)
}

fun List<Run>.splitBySeparator(): List<List<Run>> {
    val res = mutableListOf<List<Run>>()
    var tmp = mutableListOf<Run>()
    forEach { run ->
        if (run.text == " • ") {
            res.add(tmp)
            tmp = mutableListOf()
        } else {
            tmp.add(run)
        }
    }
    res.add(tmp)
    return res
}

fun List<List<Run>>.clean(): List<List<Run>> =
    if (getOrNull(0)?.getOrNull(0)?.navigationEndpoint != null ||
        (getOrNull(0)?.getOrNull(0)?.text?.contains(regex = Regex("[&,]"))) != false
    ) {
        this
    } else {
        this.drop(1)
    }

fun List<Run>.oddElements() =
    filterIndexed { index, _ ->
        index % 2 == 0
    }
