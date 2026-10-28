package com.shiny.music.search

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.shiny.music.discover.ChartMatcher
import com.shiny.music.discover.ChartText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Tier { None, Related, Partial, Phrase, Strong, TitleArtist, Exact }

data class Relevance(
    val tier: Tier,
    val score: Double,
    val coverage: Double,
    val title: Double,
    val artist: Double,
    val album: Double,
    val precision: Double,
    val versions: Double,
    val coreMatched: Boolean,
    val otherScript: Boolean,
) {
    companion object {
        val NONE = Relevance(Tier.None, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, coreMatched = false, otherScript = false)
    }
}

object SearchText {
    private val PHRASES = listOf("speed up" to "sped up", "lo fi" to "lofi", "a capella" to "a cappella", "acapella" to "a cappella")
    private val WORDS = mapOf(
        "rmx" to "remix", "ft" to "feat", "featuring" to "feat", "spedup" to "sped up", "speedup" to "sped up", "reverbed" to "reverb",
    )
    private val MISSPELT_VERSIONS = listOf("slowed", "reverb", "nightcore", "daycore", "acoustic", "instrumental", "karaoke", "unplugged", "orchestral")

    val STOP = setOf("the", "a", "an", "of", "or", "to", "in", "on", "at", "by", "for", "with", "from", "feat", "x", "vs", "de", "la", "el", "le")
    val NOISE = setOf(
        "official", "video", "audio", "lyrics", "lyric", "music", "song", "full", "hd", "hq", "4k", "mv", "visualizer",
        "original", "version", "ver", "edit", "radio", "explicit", "clean", "remaster", "remastered",
    )

    private val VERSIONS: List<List<String>> =
        (ChartMatcher.HARD_TAGS + ChartMatcher.PERFORMANCE_TAGS + listOf("super", "ultra", "daycore"))
            .map(::canonical)
            .filter { it.isNotEmpty() }
            .distinct()
            .map { it.split(' ') }

    private val SPACELESS = setOf(
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI, Character.UnicodeScript.LAO, Character.UnicodeScript.KHMER,
        Character.UnicodeScript.MYANMAR, Character.UnicodeScript.TIBETAN,
    )

    fun canonical(value: String): String {
        var text = " " + ChartText.normalize(value) + " "
        for ((from, to) in PHRASES) text = text.replace(" $from ", " $to ")
        return text.split(' ')
            .filter { it.isNotEmpty() && it != "and" }
            .joinToString(" ") { WORDS[it] ?: it }
    }

    fun tokens(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split(' ')

    fun versionMask(tokens: List<String>): BooleanArray {
        val mask = BooleanArray(tokens.size)
        for (words in VERSIONS) {
            for (i in 0..tokens.size - words.size) {
                if (words.indices.all { tokens[i + it] == words[it] }) for (j in words.indices) mask[i + j] = true
            }
        }
        return mask
    }

    fun versions(tokens: List<String>): Set<String> {
        val found = HashSet<String>()
        for (words in VERSIONS) {
            for (i in 0..tokens.size - words.size) {
                if (words.indices.all { tokens[i + it] == words[it] }) found += words.joinToString(" ")
            }
        }
        return found
    }

    fun correctVersions(tokens: List<String>): List<String> {
        val mask = versionMask(tokens)
        val corrected = tokens.mapIndexed { i, token -> if (mask[i]) token else misspeltVersion(token) ?: token }
        return if (contentTokens(corrected).isEmpty()) tokens else corrected
    }

    private fun misspeltVersion(token: String): String? {
        if (token.length < 5 || token.any(Char::isDigit) || token in STOP || token in NOISE) return null
        return MISSPELT_VERSIONS.firstOrNull { word ->
            val limit = if (min(token.length, word.length) >= 8) 2 else 1
            token.regionMatches(0, word, 0, 2) &&
                (token.startsWith(word) && token.length - word.length <= 2 || distance(token, word, limit) <= limit)
        }
    }

    fun distance(a: String, b: String, limit: Int): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    fun weight(token: String, version: Boolean): Double = when {
        version -> 0.5
        token in STOP || token in NOISE -> 0.25
        else -> 1.0
    }

    fun contentTokens(tokens: List<String>): List<String> {
        val mask = versionMask(tokens)
        return tokens.filterIndexed { i, t -> weight(t, mask[i]) == 1.0 }
    }

    fun scripts(text: String): Set<Character.UnicodeScript> {
        val out = HashSet<Character.UnicodeScript>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetter(cp)) {
                val script = Character.UnicodeScript.of(cp)
                if (script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED) out += script
            }
            i += Character.charCount(cp)
        }
        return out
    }

    fun spaceless(scripts: Set<Character.UnicodeScript>) = scripts.any { it in SPACELESS }
}

class SearchQuery(raw: String, val typeahead: Boolean = false) {
    val text: String = SearchText.correctVersions(SearchText.tokens(SearchText.canonical(raw))).joinToString(" ")
    val compact = text.replace(" ", "")
    val tokens: List<String> = SearchText.tokens(text)
    val weights: List<Double> = SearchText.versionMask(tokens).let { mask -> tokens.mapIndexed { i, t -> SearchText.weight(t, mask[i]) } }
    val versions = SearchText.versions(tokens)
    val contentWords = weights.count { it == 1.0 }
    val scripts = SearchText.scripts(text)
    val core: Set<String> =
        if (ChartText.qualifiers(raw).isEmpty()) {
            emptySet()
        } else {
            SearchText.contentTokens(SearchText.correctVersions(SearchText.tokens(SearchText.canonical(ChartText.displayCoreTitle(raw))))).toSet()
        }
    internal val joined: Set<String> = tokens.zipWithNext { a, b -> a + b }.toSet()

    val isBlank: Boolean get() = tokens.isEmpty()
}

object SearchRelevance {
    const val MIN_COVERAGE = 0.5
    private const val FOUND = 0.9
    private const val TYPEAHEAD_PREFIX = 0.9
    private const val PREFIX = 0.6
    private const val FUZZY = 0.7
    private const val ALBUM_FACTOR = 0.8
    private const val UPLOADER_FACTOR = 0.5

    private class Fields(item: YTItem) {
        val titleText = SearchText.canonical(item.title)
        val titleTokens = SearchText.tokens(titleText)
        val titleSet = titleTokens.toSet()
        val titleJoined = titleTokens.zipWithNext { a, b -> a + b }.toSet()
        val titleCompact = titleText.replace(" ", "")
        val coreText = SearchText.canonical(ChartText.displayCoreTitle(item.title))
        val artistNames = artistsOf(item).map { SearchText.canonical(it.name) }.filter { it.isNotEmpty() }
        val artistTokens = artistNames.flatMap(SearchText::tokens).toSet()
        val artistJoined = artistNames.flatMap { name -> SearchText.tokens(name).zipWithNext { a, b -> a + b } + name.replace(" ", "") }.toSet()
        val albumTokens = (item as? SongItem)?.album?.name?.let { SearchText.tokens(SearchText.canonical(it)) }.orEmpty().toSet()
        val versions = SearchText.versions(titleTokens)
        val scripts = SearchText.scripts(titleText)
    }

    fun score(query: SearchQuery, item: YTItem, kind: SearchKind = item.searchKind()): Relevance {
        if (query.isBlank) return Relevance.NONE
        val f = Fields(item)
        if (f.titleTokens.isEmpty()) return Relevance.NONE

        val t = values(query, f.titleSet, f.titleJoined)
        val a = values(query, f.artistTokens, f.artistJoined)
        val l = values(query, f.albumTokens, emptySet())
        val uploader = if (kind.needsTitleEvidence) UPLOADER_FACTOR else 1.0
        val best = DoubleArray(query.tokens.size) { maxOf(t[it], a[it] * uploader, l[it] * ALBUM_FACTOR) }

        val coverage = coverage(query, best)
        val titleCoverage = coverage(query, t)
        val artistCoverage = coverage(query, a)
        val albumCoverage = coverage(query, l)

        val required = query.weights.indices.filter { query.weights[it] >= 0.5 }.ifEmpty { query.weights.indices.toList() }
        val allFound = required.all { max(t[it], if (kind.needsTitleEvidence) 0.0 else a[it]) >= FOUND }
        val coreMatched = (query.core.isEmpty() || query.tokens.indices.any { query.tokens[it] in query.core && best[it] > 0.0 }) &&
            (query.contentWords == 0 || query.tokens.indices.any { query.weights[it] == 1.0 && best[it] > 0.0 })

        val exact = f.titleText == query.text || f.titleCompact == query.compact
        val titleArtist = !exact && titleWithArtist(query, f)
        val phrase = " ${query.text} " in " ${f.titleText} " ||
            (SearchText.spaceless(query.scripts) && query.compact.isNotEmpty() && query.compact in f.titleCompact) ||
            insideQuery(query, f.titleText) || insideQuery(query, f.coreText)

        val precision = precision(query, f, kind)
        val versions = jaccard(query.versions, f.versions)
        val score = 0.5 * coverage + 0.3 * precision + 0.15 * versions + 0.05 * kind.weight
        val otherScript = query.scripts.isNotEmpty() && f.scripts.isNotEmpty() && query.scripts.none { it in f.scripts }

        val tier = when {
            kind.needsTitleEvidence && titleCoverage == 0.0 -> Tier.None
            exact -> Tier.Exact
            titleArtist -> Tier.TitleArtist
            allFound -> Tier.Strong
            !coreMatched -> Tier.None
            phrase -> Tier.Phrase
            coverage >= MIN_COVERAGE -> Tier.Partial
            else -> Tier.None
        }
        return Relevance(tier, score, coverage, titleCoverage, artistCoverage, albumCoverage, precision, versions, coreMatched, otherScript)
    }

    private fun values(query: SearchQuery, tokens: Set<String>, joined: Set<String>): DoubleArray {
        val out = DoubleArray(query.tokens.size)
        if (tokens.isEmpty()) return out
        val last = query.tokens.lastIndex
        for (i in query.tokens.indices) {
            val prefix = if (query.typeahead && i == last) TYPEAHEAD_PREFIX else PREFIX
            out[i] = match(query.tokens[i], tokens, joined, prefix)
        }
        for (i in 0 until last) {
            if (query.tokens[i] + query.tokens[i + 1] in tokens) {
                out[i] = 1.0
                out[i + 1] = 1.0
            }
        }
        return out
    }

    private fun match(q: String, tokens: Set<String>, joined: Set<String>, prefix: Double): Double {
        if (q in tokens || q in joined) return 1.0
        val minPrefix = if (prefix == TYPEAHEAD_PREFIX) 2 else 3
        if (q.length >= minPrefix && tokens.any { it.length > q.length && it.startsWith(q) }) return prefix
        if (q.length >= 5 && q.none(Char::isDigit)) {
            val limit = if (q.length >= 8) 2 else 1
            if (tokens.any { abs(it.length - q.length) <= limit && it.none(Char::isDigit) && SearchText.distance(q, it, limit) <= limit }) return FUZZY
        }
        return 0.0
    }

    private fun coverage(query: SearchQuery, values: DoubleArray): Double {
        val total = query.weights.sum()
        if (total == 0.0) return 0.0
        return values.indices.sumOf { query.weights[it] * values[it] } / total
    }

    private fun titleWithArtist(query: SearchQuery, f: Fields): Boolean {
        val names = if (f.artistNames.size > 1) f.artistNames + f.artistNames.joinToString(" ") else f.artistNames
        for (name in names) {
            for (title in setOf(f.titleText, f.coreText)) {
                if (title.isEmpty()) continue
                for (combined in listOf("$title $name", "$name $title")) {
                    if (combined == query.text || combined.replace(" ", "") == query.compact) return true
                }
            }
        }
        return false
    }

    private fun insideQuery(query: SearchQuery, title: String): Boolean {
        if (title.isEmpty() || " $title " !in " ${query.text} ") return false
        val content = SearchText.contentTokens(SearchText.tokens(title))
        return content.isNotEmpty() && content.size * 2 >= query.contentWords
    }

    private fun precision(query: SearchQuery, f: Fields, kind: SearchKind): Double {
        val own = f.titleTokens.filter {
            it !in SearchText.STOP && it !in SearchText.NOISE && (kind == SearchKind.Artist || it !in f.artistTokens)
        }
        if (own.isEmpty()) return 1.0
        val asked = query.tokens.toSet()
        return own.count { token ->
            token in asked || token in query.joined || query.tokens.any { it.length >= 3 && token.startsWith(it) }
        }.toDouble() / own.size
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        return (a intersect b).size.toDouble() / (a union b).size
    }
}

internal fun artistsOf(item: YTItem) = when (item) {
    is SongItem -> item.artists
    is AlbumItem -> item.artists.orEmpty()
    is PlaylistItem -> listOfNotNull(item.author)
    is ArtistItem -> emptyList()
}
