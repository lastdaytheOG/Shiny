package com.shiny.music.discover

import kotlin.math.abs

enum class CandidateKind { Song, OfficialVideo, Album, Other }

data class MatchCandidate(
    val title: String,
    val artists: List<String>,
    val explicit: Boolean = false,
    val kind: CandidateKind = CandidateKind.Song,
    val year: Int? = null,
)

data class MatchTarget(
    val title: String,
    val credit: String,
    val artistUrl: String? = null,
    val explicit: Boolean = false,
    val year: Int? = null,
    val isAlbum: Boolean = false,
) {
    companion object {
        fun song(entry: ChartEntry) = MatchTarget(entry.title, entry.artist, entry.artistUrl, entry.explicit, ChartText.year(entry.releaseDate))
        fun album(entry: ChartEntry) = song(entry).copy(isAlbum = true)
    }
}

enum class Fit { Exact, Alternative, Partial, None }

data class Assessment(val fit: Fit, val score: Double, val artistKnown: Boolean = true)

data class MatchDecision(val index: Int, val fit: Fit, val score: Double)

object ChartMatcher {
    const val CONFIDENT = 0.86
    const val PARTIAL = 0.55

    private val HARD_TAGS = listOf(
        "remix", "rmx", "mix", "cover", "karaoke", "instrumental", "acoustic", "sped up", "speed up",
        "slowed", "reverb", "lofi", "lo fi", "8d", "nightcore", "reprise", "mashup", "tribute",
        "originally performed", "in the style of", "bass boosted", "female version", "male version", "demo",
        "extended mix", "extended version", "piano version", "orchestral", "a cappella", "acapella",
        "taylors version", "chopped", "screwed", "backing track", "minus one", "tutorial", "reaction", "lesson",
        "long version", "short version", "take",
    )
    private val PERFORMANCE_TAGS = listOf("live", "sessions", "session", "unplugged", "in concert", "concert", "tour", "performance")
    private val SOFT_TAGS = listOf(
        "original mix", "album version", "album ver", "single version", "radio version", "radio edit", "original version",
        "original ver", "explicit version", "clean version", "deluxe version", "remastered", "remaster", "deluxe",
        "extended album", "extended edition", "expanded", "anniversary", "special edition", "edition",
        "bonus track version", "bonus track", "mono", "stereo", "mixed",
    )
    private val LANGUAGES = setOf(
        "hindi", "tamil", "telugu", "kannada", "malayalam", "marathi", "punjabi", "bengali", "bhojpuri", "gujarati",
        "urdu", "english", "spanish", "korean", "japanese", "chinese", "mandarin", "cantonese", "french", "german",
        "portuguese", "italian", "arabic", "turkish", "russian",
    )
    private val CONTEXT = Regex("""^(?:from\b|feat\b|ft\b|featuring\b|with\b)|soundtrack|motion picture|\bost\b""")
    private val FEATURING = Regex("""^(?:feat|ft|featuring|with)\b""")
    private val VERSION_WORD = Regex("""\b(?:version|ver)\b""")

    data class Tags(
        val hard: Set<String>,
        val stage: Set<String>,
        val soft: Set<String>,
        val languages: Set<String> = emptySet(),
        val variants: Set<String> = emptySet(),
    ) {
        val performance: Boolean get() = stage.isNotEmpty()
    }

    private fun has(text: String, phrase: String) = " $phrase " in " $text "

    fun tags(title: String): Tags {
        val considered = ChartText.qualifiers(title).map(ChartText::normalize).filterNot { FEATURING.containsMatchIn(it) }
        val stage = considered.flatMapTo(HashSet()) { q ->
            PERFORMANCE_TAGS.filter { has(q, it) } + listOfNotNull(q.takeIf { it.startsWith("at ") })
        }
        val soft = HashSet<String>()
        val hard = HashSet<String>()
        val languages = HashSet<String>()
        val variants = HashSet<String>()
        for (q in considered) {
            val words = q.split(' ')
            if (words.all { it in LANGUAGES }) {
                languages += words
                continue
            }
            if (CONTEXT.containsMatchIn(q)) continue
            var text = q
            for (tag in SOFT_TAGS) {
                if (has(text, tag)) {
                    soft += tag
                    text = " $text ".replace(" $tag ", " ").trim()
                }
            }
            val found = HARD_TAGS.filter { has(text, it) }
            hard += found
            val isPerformance = text.startsWith("at ") || PERFORMANCE_TAGS.any { has(text, it) }
            if (found.isEmpty() && !isPerformance && VERSION_WORD.containsMatchIn(text)) {
                text.replace(VERSION_WORD, " ").trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { variants += it }
            }
        }
        return Tags(hard, stage, soft, languages, variants)
    }

    fun similarity(target: String, candidate: String): Double {
        val a = ChartText.coreTitle(target)
        val b = ChartText.coreTitle(candidate)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.replace(" ", "") == b.replace(" ", "")) return 0.97
        val ta = a.split(' ')
        val tb = b.split(' ')
        val (short, long) = if (ta.size <= tb.size) ta to tb else tb to ta
        if (short.size >= 2 && long.take(short.size) == short) return 0.8
        val union = ta.toSet().union(tb.toSet()).size.toDouble()
        val overlap = ta.toSet().intersect(tb.toSet()).size / union
        if (overlap >= 0.75) return overlap * 0.8
        if (long.take(short.size) == short) return 0.5
        if (overlap >= 0.5) return 0.45
        return 0.0
    }

    fun artistScore(credit: String, artistUrl: String?, candidateArtists: List<String>): Double? {
        if (ChartText.isVarious(credit)) return null
        val named = candidateArtists.filterNot(ChartText::isVarious)
        if (named.isEmpty()) return null
        val wanted = ChartText.splitCredits(credit).map(ChartText::nameKey).filter { it.isNotEmpty() }
        val lead = ChartText.nameKey(ChartText.leadArtist(credit, artistUrl))
        val offered = named.flatMap { listOf(it) + ChartText.splitCredits(it) }
            .map(ChartText::nameKey).filter { it.isNotEmpty() }.toSet()
        if (offered.isEmpty()) return 0.0
        if (offered.any { ChartText.sameName(it, lead) }) return 1.0
        if (wanted.any { w -> offered.any { ChartText.sameName(it, w) } }) return 0.7
        if (candidateArtists.size != named.size) return null
        return 0.0
    }

    fun assess(target: MatchTarget, candidate: MatchCandidate): Assessment {
        val none = Assessment(Fit.None, 0.0)
        if (candidate.kind == CandidateKind.Other) return none
        val wanted = tags(target.title)
        val offered = tags(candidate.title)
        if (wanted.hard != offered.hard) return none
        if (offered.performance && !wanted.performance) return none
        if (wanted.languages.isNotEmpty() && offered.languages.isNotEmpty() && wanted.languages.none { it in offered.languages }) return none
        val studioForLive = wanted.performance &&
            !(offered.stage.containsAll(wanted.stage) || (offered.performance && wanted.stage.containsAll(offered.stage)))
        val otherVariant = wanted.variants != offered.variants
        val sim = similarity(target.title, candidate.title)
        if (sim == 0.0) return none
        val softDiff = (wanted.soft union offered.soft).minus(wanted.soft intersect offered.soft).size +
            if (wanted.languages != offered.languages) 1 else 0
        val softPenalty = 0.03 * softDiff
        val yearsKnown = target.year != null && candidate.year != null
        val yearsAgree = yearsKnown && abs(target.year!! - candidate.year!!) <= 1

        val artist = artistScore(target.credit, target.artistUrl, candidate.artists)
        if (artist == null) {
            if (!target.isAlbum || sim < 1.0) return if (sim >= 0.8) Assessment(Fit.Partial, 0.6, false) else none
            val fullTitlesEqual = ChartText.normalize(target.title) == ChartText.normalize(candidate.title)
            val corroborated = if (yearsKnown) yearsAgree else fullTitlesEqual
            return if (corroborated && !studioForLive && !otherVariant) {
                Assessment(Fit.Exact, 0.9 - softPenalty, false)
            } else {
                Assessment(Fit.Partial, 0.6, false)
            }
        }
        if (artist == 0.0) return none

        var score = 0.6 * sim + 0.4 * artist - softPenalty
        if (candidate.explicit == target.explicit) score += 0.005
        if (candidate.kind == CandidateKind.Song) score += 0.01
        if (yearsKnown && !yearsAgree) score -= 0.08
        return when {
            score >= CONFIDENT && otherVariant -> Assessment(Fit.Partial, score - 0.3)
            score >= CONFIDENT -> Assessment(if (studioForLive) Fit.Alternative else Fit.Exact, score)
            score >= PARTIAL -> Assessment(Fit.Partial, score)
            else -> none
        }
    }

    fun decide(target: MatchTarget, candidates: List<MatchCandidate>): MatchDecision? {
        val assessed = candidates.map { assess(target, it) }
        val unknownExact = assessed.indices.filter { assessed[it].fit == Fit.Exact && !assessed[it].artistKnown }
        val ambiguousUnknown = unknownExact
            .map { candidates[it].artists.map(ChartText::nameKey).sorted() }
            .distinct().size > 1
        fun best(fit: Fit) = assessed.indices
            .filter { assessed[it].fit == fit && !(ambiguousUnknown && it in unknownExact) }
            .maxWithOrNull(compareBy<Int> { assessed[it].score }.thenByDescending { it })
            ?.let { MatchDecision(it, fit, assessed[it].score) }
        return best(Fit.Exact) ?: best(Fit.Alternative)
    }

    fun partials(target: MatchTarget, candidates: List<MatchCandidate>): List<Int> {
        val assessed = candidates.map { assess(target, it) }
        val decided = decide(target, candidates)?.index
        return assessed.indices
            .filter { it != decided && (assessed[it].fit == Fit.Partial || (assessed[it].fit == Fit.Exact && !assessed[it].artistKnown)) }
            .sortedWith(compareByDescending<Int> { assessed[it].score }.thenBy { it })
    }

    fun sameArtist(name: String, candidate: String): Boolean =
        ChartText.sameName(ChartText.nameKey(name), ChartText.nameKey(candidate))
}
