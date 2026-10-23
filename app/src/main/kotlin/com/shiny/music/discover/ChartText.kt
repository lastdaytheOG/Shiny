package com.shiny.music.discover

import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

object ChartText {
    private val MARKS = Regex("[\\u0300-\\u036f]+")
    private val NON_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")
    private val SPACES = Regex("\\s+")
    private val AND_WORD = Regex("\\band\\b")
    private val APOSTROPHES = Regex("['’ʼ`´]")
    private val STYLED_S = Regex("""(?<=\p{L})\$|\$(?=\p{L})""")
    private val STYLED_I = Regex("""(?<=\p{L})!(?=\p{L})""")
    private val STYLED_A = Regex("""(?<=\p{L})@(?=\p{L})""")
    private val SEPARATOR = Regex("""\s*(?:,|、|&|\bfeat\.|\bfeat\b|\bft\.|\bft\b|\bfeaturing\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)
    private val BRACKETED = Regex("""\(([^()]*)\)|\[([^\[\]]*)]|【([^【】]*)】""")
    private val DASH_SUFFIX = Regex("""\s+[-–—]\s+(.+)$""")
    private val FEAT_SUFFIX = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.+$""", RegexOption.IGNORE_CASE)
    private val FEATURE_IN_TITLE = Regex("""^(?:feat\.?|ft\.?|featuring|with)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("""^(\d{4})""")

    private val FOLDS = mapOf('ø' to "o", 'Ø' to "o", 'æ' to "ae", 'Æ' to "ae", 'ß' to "ss", 'ł' to "l", 'Ł' to "l", 'đ' to "d", 'Đ' to "d", 'œ' to "oe", 'Œ' to "oe")

    private val VARIOUS = setOf(
        "various artists", "various", "varios artistas", "varios", "artistes divers", "verschiedene interpreten",
        "varios interpretes", "artisti vari", "varios artistes", "vari artisti",
    )

    fun normalize(value: String): String {
        val styled = value
            .replace(STYLED_S, "s")
            .replace(STYLED_I, "i")
            .replace(STYLED_A, "a")
            .replace(APOSTROPHES, "")
        val folded = buildString(styled.length) {
            for (c in Normalizer.normalize(styled, Normalizer.Form.NFKD)) append(FOLDS[c] ?: c)
        }.replace(MARKS, "")
        return folded.lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(NON_WORD, " ")
            .trim()
            .replace(SPACES, " ")
    }

    fun nameKey(value: String): String = normalize(value).replace(AND_WORD, " ").trim().replace(SPACES, " ")

    fun sameName(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b || a.removePrefix("the ") == b.removePrefix("the ")) return true
        return a.replace(" ", "") == b.replace(" ", "")
    }

    fun isVarious(credit: String?): Boolean = credit != null && nameKey(credit) in VARIOUS

    fun splitCredits(credit: String): List<String> =
        credit.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    private val WIDE = mapOf('（' to '(', '）' to ')', '［' to '[', '］' to ']', '　' to ' ', '－' to '-')

    private fun widthFolded(title: String) = buildString(title.length) { for (c in title) append(WIDE[c] ?: c) }

    fun qualifiers(title: String): List<String> {
        val folded = widthFolded(title)
        val inBrackets = BRACKETED.findAll(folded).map { m -> m.groupValues.drop(1).joinToString("").trim() }.toList()
        val unbracketed = BRACKETED.replace(folded, " ")
        val dash = DASH_SUFFIX.find(unbracketed)?.groupValues?.get(1)?.trim()
        val feat = FEAT_SUFFIX.find(DASH_SUFFIX.replace(unbracketed, ""))?.value?.trim()
        return (inBrackets + listOfNotNull(dash, feat)).filter { it.isNotEmpty() }
    }

    fun displayCoreTitle(title: String): String {
        val core = DASH_SUFFIX.replace(BRACKETED.replace(widthFolded(title), " "), "")
            .replace(FEAT_SUFFIX, "")
            .trim()
            .replace(SPACES, " ")
        return core.ifEmpty { title.trim() }
    }

    fun coreTitle(title: String): String =
        normalize(displayCoreTitle(title)).ifEmpty { normalize(title) }.ifEmpty { title.trim().lowercase(Locale.ROOT) }

    fun featuredInTitle(title: String): List<String> = qualifiers(title).flatMap { q ->
        FEATURE_IN_TITLE.matchEntire(q)?.groupValues?.get(1)?.let(::splitCredits).orEmpty()
    }

    fun year(date: String?): Int? = date?.let { YEAR.find(it.trim()) }?.groupValues?.get(1)?.toIntOrNull()

    fun leadArtist(credit: String, artistUrl: String?): String {
        val trimmed = credit.trim()
        val credits = splitCredits(trimmed)
        val first = credits.firstOrNull() ?: trimmed
        val slug = artistUrl?.trimEnd('/')?.split('/')?.dropLast(1)?.lastOrNull()
            ?.takeIf { it.isNotBlank() && it != "artist" }
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: return first
        val target = nameKey(slug.replace('-', ' '))
        if (target.isEmpty()) return first
        val prefixes = SEPARATOR.findAll(trimmed).map { trimmed.substring(0, it.range.first).trim() } + sequenceOf(trimmed)
        return prefixes.firstOrNull { nameKey(it) == target }
            ?: credits.firstOrNull { sameName(nameKey(it), target) }
            ?: first
    }
}
